package de.bauloo.styles;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Windows media session of the Spotify app (what the volume flyout shows): title, artist,
 * play state and the real position, so seeking and a game restart mid-song show the right time.
 * WinRT is called through JNA, which Minecraft ships; nothing is started or downloaded.
 */
final class SpotifySmtc {

    /** what Windows knows about the Spotify session right now */
    record Info(String title, String artist, boolean playing, long positionMs, long lengthMs) {
    }

    private static final String ASYNC_INFO = "{00000036-0000-0000-C000-000000000046}";
    private static final String MANAGER_STATICS = "{2050c4ee-11a0-57de-aed7-c97c70338245}";
    private static final String RANDOM_ACCESS_STREAM = "{905A0FE1-BC53-11DF-8C49-001E4FC686DA}";
    private static final String INPUT_STREAM = "{905A0FE2-BC53-11DF-8C49-001E4FC686DA}";
    private static final String DATA_READER_FACTORY = "{d7527847-57da-4e15-914c-06806699a098}";
    private static final int STATUS_PLAYING = 4;
    private static final long EPOCH_DIFF_MS = 11644473600000L; // 1601 -> 1970

    private static NativeLibrary combase;
    private static Pointer manager;

    private SpotifySmtc() {
    }

    /** Background thread only. null = no Spotify session (or Windows can't tell). */
    static synchronized Info read() throws Exception {
        List<Pointer> held = new ArrayList<>();
        try {
            Pointer session = spotifySession(held);
            return session == null ? null : info(session, held);
        } finally {
            for (Pointer p : held) {
                release(p);
            }
        }
    }

    /**
     * The cover Spotify hands to Windows (PNG/JPEG bytes) together with the title it belongs to,
     * or null. Background thread only.
     */
    static synchronized Object[] cover() throws Exception {
        List<Pointer> held = new ArrayList<>();
        try {
            Pointer session = spotifySession(held);
            if (session == null) {
                return null;
            }
            PointerByReference propsOp = new PointerByReference();
            check(call(session, 7, propsOp), "TryGetMediaPropertiesAsync");
            held.add(propsOp.getValue());
            Pointer props = await(propsOp.getValue(), held);
            PointerByReference title = new PointerByReference();
            check(call(props, 6, title), "Title");
            String songTitle = take(title.getValue());
            PointerByReference thumbnail = new PointerByReference();
            check(call(props, 15, thumbnail), "Thumbnail");
            if (thumbnail.getValue() == null) {
                return null;
            }
            held.add(thumbnail.getValue());
            PointerByReference openOp = new PointerByReference();
            check(call(thumbnail.getValue(), 6, openOp), "OpenReadAsync");
            held.add(openOp.getValue());
            Pointer stream = await(openOp.getValue(), held);
            PointerByReference random = new PointerByReference();
            check(call(stream, 0, guid(RANDOM_ACCESS_STREAM), random), "QueryInterface IRandomAccessStream");
            held.add(random.getValue());
            LongByReference size = new LongByReference();
            check(call(random.getValue(), 6, size), "Size");
            if (size.getValue() <= 0 || size.getValue() > 8_000_000) {
                return null;
            }
            PointerByReference input = new PointerByReference();
            check(call(stream, 0, guid(INPUT_STREAM), input), "QueryInterface IInputStream");
            held.add(input.getValue());
            PointerByReference factory = new PointerByReference();
            activationFactory("Windows.Storage.Streams.DataReader", DATA_READER_FACTORY, factory);
            held.add(factory.getValue());
            PointerByReference reader = new PointerByReference();
            check(call(factory.getValue(), 6, input.getValue(), reader), "CreateDataReader");
            held.add(reader.getValue());
            PointerByReference loadOp = new PointerByReference();
            check(call(reader.getValue(), 29, (int) size.getValue(), loadOp), "LoadAsync");
            held.add(loadOp.getValue());
            waitFor(loadOp.getValue());
            IntByReference loaded = new IntByReference();
            check(call(loadOp.getValue(), 8, loaded), "LoadAsync result");
            com.sun.jna.Memory bytes = new com.sun.jna.Memory(Math.max(1, loaded.getValue()));
            check(call(reader.getValue(), 14, loaded.getValue(), bytes), "ReadBytes");
            return new Object[]{songTitle, bytes.getByteArray(0, loaded.getValue())};
        } finally {
            for (Pointer p : held) {
                release(p);
            }
        }
    }

    /** The Spotify app's session, added to held; null when Spotify doesn't talk to Windows. */
    private static Pointer spotifySession(List<Pointer> held) throws Exception {
        {
            if (manager == null) {
                manager = openManager();
            }
            PointerByReference sessions = new PointerByReference();
            check(call(manager, 7, sessions), "GetSessions");
            held.add(sessions.getValue());
            IntByReference size = new IntByReference();
            check(call(sessions.getValue(), 7, size), "Size");
            for (int i = 0; i < size.getValue(); i++) {
                PointerByReference session = new PointerByReference();
                check(call(sessions.getValue(), 6, i, session), "GetAt");
                held.add(session.getValue());
                PointerByReference app = new PointerByReference();
                check(call(session.getValue(), 6, app), "SourceAppUserModelId");
                if (take(app.getValue()).toLowerCase(Locale.ROOT).startsWith("spotify")) {
                    return session.getValue();
                }
            }
            return null;
        }
    }

    private static Info info(Pointer session, List<Pointer> held) throws Exception {
        PointerByReference propsOp = new PointerByReference();
        check(call(session, 7, propsOp), "TryGetMediaPropertiesAsync");
        held.add(propsOp.getValue());
        Pointer props = await(propsOp.getValue(), held);
        PointerByReference title = new PointerByReference();
        PointerByReference artist = new PointerByReference();
        check(call(props, 6, title), "Title");
        check(call(props, 9, artist), "Artist");

        PointerByReference timeline = new PointerByReference();
        check(call(session, 8, timeline), "GetTimelineProperties");
        held.add(timeline.getValue());
        LongByReference end = new LongByReference();
        LongByReference position = new LongByReference();
        LongByReference updated = new LongByReference();
        check(call(timeline.getValue(), 7, end), "EndTime");
        check(call(timeline.getValue(), 10, position), "Position");
        check(call(timeline.getValue(), 11, updated), "LastUpdatedTime");

        PointerByReference playback = new PointerByReference();
        check(call(session, 9, playback), "GetPlaybackInfo");
        held.add(playback.getValue());
        IntByReference status = new IntByReference();
        check(call(playback.getValue(), 7, status), "PlaybackStatus");

        boolean playing = status.getValue() == STATUS_PLAYING;
        long lengthMs = end.getValue() / 10000;
        long positionMs = position.getValue() / 10000;
        if (playing) { // Spotify only updates the position on changes: add the time since then
            positionMs += Math.max(0, System.currentTimeMillis() - (updated.getValue() / 10000 - EPOCH_DIFF_MS));
        }
        if (lengthMs > 0) {
            positionMs = Math.min(positionMs, lengthMs);
        }
        return new Info(take(title.getValue()), take(artist.getValue()), playing, positionMs, lengthMs);
    }

    private static void activationFactory(String name, String iid, PointerByReference out) {
        if (combase == null) {
            combase = NativeLibrary.getInstance("combase");
        }
        combase.getFunction("RoInitialize").invokeInt(new Object[]{1}); // multithreaded, once per thread is fine
        PointerByReference hName = new PointerByReference();
        check(combase.getFunction("WindowsCreateString").invokeInt(new Object[]{new WString(name), name.length(), hName}),
                "WindowsCreateString");
        try {
            check(combase.getFunction("RoGetActivationFactory").invokeInt(new Object[]{hName.getValue(), guid(iid), out}),
                    "RoGetActivationFactory " + name);
        } finally {
            combase.getFunction("WindowsDeleteString").invokeInt(new Object[]{hName.getValue()});
        }
    }

    private static Pointer openManager() throws Exception {
        PointerByReference statics = new PointerByReference();
        activationFactory("Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager", MANAGER_STATICS, statics);
        List<Pointer> held = new ArrayList<>();
        try {
            PointerByReference op = new PointerByReference();
            check(call(statics.getValue(), 6, op), "RequestAsync");
            held.add(op.getValue());
            return await(op.getValue(), null);
        } finally {
            release(statics.getValue());
            for (Pointer p : held) {
                release(p);
            }
        }
    }

    /** Waits for an IAsyncOperation; the result is added to held unless held is null (kept). */
    private static Pointer await(Pointer operation, List<Pointer> held) throws Exception {
        waitFor(operation);
        PointerByReference result = new PointerByReference();
        check(call(operation, 8, result), "GetResults");
        if (held != null) {
            held.add(result.getValue());
        }
        return result.getValue();
    }

    private static void waitFor(Pointer operation) throws Exception {
        PointerByReference info = new PointerByReference();
        check(call(operation, 0, guid(ASYNC_INFO), info), "QueryInterface IAsyncInfo");
        try {
            IntByReference status = new IntByReference();
            for (int i = 0; i < 300; i++) {
                check(call(info.getValue(), 7, status), "Status");
                if (status.getValue() != 0) {
                    break;
                }
                Thread.sleep(10);
            }
            if (status.getValue() != 1) {
                throw new IllegalStateException("async status " + status.getValue());
            }
        } finally {
            release(info.getValue());
        }
    }

    private static int call(Pointer object, int slot, Object... args) {
        Pointer table = object.getPointer(0);
        Function function = Function.getFunction(table.getPointer((long) slot * Native.POINTER_SIZE));
        Object[] all = new Object[args.length + 1];
        all[0] = object;
        System.arraycopy(args, 0, all, 1, args.length);
        return function.invokeInt(all);
    }

    private static void release(Pointer object) {
        if (object != null) {
            call(object, 2);
        }
    }

    /** Reads an HSTRING and frees it. */
    private static String take(Pointer hstring) {
        if (hstring == null) {
            return "";
        }
        IntByReference length = new IntByReference();
        Pointer raw = (Pointer) combase.getFunction("WindowsGetStringRawBuffer")
                .invoke(Pointer.class, new Object[]{hstring, length});
        String value = raw == null ? "" : new String(raw.getCharArray(0, length.getValue()));
        combase.getFunction("WindowsDeleteString").invokeInt(new Object[]{hstring});
        return value;
    }

    /** Passed as a structure, so JNA keeps it alive for the call and hands over its address. */
    private static Guid.GUID guid(String text) {
        return Guid.GUID.fromString(text);
    }

    private static void check(int hresult, String what) {
        if (hresult < 0) {
            throw new IllegalStateException(what + " failed 0x" + Integer.toHexString(hresult));
        }
    }
}

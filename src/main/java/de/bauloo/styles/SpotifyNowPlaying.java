package de.bauloo.styles;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What the Spotify desktop app is playing, read from its window title ("Artist - Song").
 * Paused, the title is just "Spotify ..." without the dash, so nothing shows.
 * Only reads window titles; it starts nothing and talks to nothing.
 */
public final class SpotifyNowPlaying {

    /** false in the clean download: that one has no Spotify card (no JNA). */
    public static final boolean AVAILABLE = true;

    /** null while nothing plays */
    public static volatile String artist;
    public static volatile String title;
    /** System.currentTimeMillis() of the last song change */
    public static volatile long changedAt;
    /** true once the Spotify window was found at all */
    public static volatile boolean appOpen;
    /** true when we saw the song start, so the play time counts from its beginning */
    public static volatile boolean knownStart;
    /** Windows told us the real position (so seeking and restarts are right) */
    public static volatile boolean timeline;
    private static volatile long timelinePosMs;
    private static volatile long timelineAt;
    public static volatile long timelineLengthMs;
    private static boolean smtcBroken;

    private static String songKey;
    private static long playedMs;
    private static long playingSince;
    private static boolean playing;

    private static final Map<Integer, Boolean> SPOTIFY_PIDS = new HashMap<>();
    private static Thread thread;

    private SpotifyNowPlaying() {
    }

    /** Starts the background reader once; it only works while the setting is on. */
    public static synchronized void start() {
        if (thread != null || !Platform.isWindows()) {
            return;
        }
        thread = new Thread(SpotifyNowPlaying::loop, "Bauloo Styles Spotify");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loop() {
        while (true) {
            try {
                StylesUiConfig.load();
                if (StylesUiConfig.spotifyHud) {
                    read();
                } else {
                    set(null, null, false);
                }
                Thread.sleep(StylesUiConfig.spotifyHud ? 1000 : 3000);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable e) {
                BaulooStylesClient.LOGGER.warn("Spotify now playing stopped: {}", e.toString());
                return; // no JNA or no Windows: the feature just stays empty
            }
        }
    }

    private static void read() {
        if (!smtcBroken) {
            try {
                SpotifySmtc.Info info = SpotifySmtc.read();
                if (info != null) {
                    if (!info.playing() || info.title().isEmpty()) {
                        timeline = false;
                        set(null, null, true);
                    } else {
                        set(info.artist(), info.title(), true);
                        timelinePosMs = info.positionMs();
                        timelineAt = System.currentTimeMillis();
                        timelineLengthMs = info.lengthMs();
                        timeline = info.lengthMs() > 0;
                    }
                    return;
                }
            } catch (Throwable e) {
                smtcBroken = true; // older Windows or something odd: fall back to the window title
                BaulooStylesClient.LOGGER.info("Spotify: Windows media info not available ({}), using the window title", e.toString());
            }
        }
        timeline = false;
        String[] found = new String[1];
        boolean[] open = new boolean[1];
        char[] buffer = new char[512];
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            int length = User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
            if (length <= 0 || !isSpotify(hwnd)) {
                return true;
            }
            open[0] = true;
            String text = new String(buffer, 0, length);
            if (text.contains(" - ")) {
                found[0] = text;
                return false;
            }
            return true;
        }, null);
        if (found[0] == null) {
            set(null, null, open[0]);
            return;
        }
        int dash = found[0].indexOf(" - ");
        set(found[0].substring(0, dash).trim(), found[0].substring(dash + 3).trim(), true);
    }

    private static boolean isSpotify(WinDef.HWND hwnd) {
        IntByReference pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
        int id = pid.getValue();
        if (SPOTIFY_PIDS.size() > 256) {
            SPOTIFY_PIDS.clear();
        }
        return SPOTIFY_PIDS.computeIfAbsent(id, key -> ProcessHandle.of(key)
                .flatMap(handle -> handle.info().command())
                .map(command -> Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT).startsWith("spotify."))
                .orElse(false));
    }

    private static synchronized void set(String newArtist, String newTitle, boolean open) {
        appOpen = open;
        long now = System.currentTimeMillis();
        if (newTitle == null) { // paused or closed: stop the clock, keep the song
            if (playing) {
                playedMs += now - playingSince;
                playing = false;
            }
            artist = null;
            title = null;
            return;
        }
        String key = newArtist + "\n" + newTitle;
        if (!key.equals(songKey)) {
            knownStart = songKey != null; // the very first song may already have been running
            songKey = key;
            playedMs = 0;
            playingSince = now;
            playing = true;
            changedAt = now;
            SpotifyCover.request(newArtist, newTitle);
        } else if (!playing) {
            playing = true;
            playingSince = now;
        }
        artist = newArtist;
        title = newTitle;
    }

    /** The real position from Windows, moving on between the one-second reads. */
    public static long timelineMs() {
        return Math.min(timelineLengthMs, timelinePosMs + (System.currentTimeMillis() - timelineAt));
    }

    /** How long the current song has been playing, in ms. */
    public static synchronized long elapsedMs() {
        return playedMs + (playing ? System.currentTimeMillis() - playingSince : 0);
    }
}

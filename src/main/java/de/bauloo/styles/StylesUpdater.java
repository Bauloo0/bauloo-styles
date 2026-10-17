package de.bauloo.styles;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Auto update for Bauloo Styles, built into the mod (switch "Auto update" in Extras).
 *
 * Minecraft keeps every mod file locked while it runs, so an update takes two steps:
 * this start checks for a new version and downloads it next to the mods; the next start
 * puts it in place before the game has opened the mod files. It only ever downloads the
 * Bauloo Styles jar from bauloo's GitHub and checks its SHA-256 - nothing else is run.
 *
 * Made by bauloo.
 */
public class StylesUpdater implements PreLaunchEntrypoint {

    public static final Logger LOGGER = LoggerFactory.getLogger("Bauloo Styles Updater");
    static final String STYLES_ID = "bauloo_styles";
    static final String VERSION_URL =
            "https://raw.githubusercontent.com/Bauloo0/bauloo-styles-cloud/main/release/version.json";
    /** early access (VIP): dev builds from bauloo's worker, only with a VIP key on this PC */
    static final String WORKER = "https://bauloo-styles-cloud.bauloo-styles-cloud-worker.workers.dev";
    /** the Minecraft version this build is for, as the dev channel names it */
    static final String DEV_MC = "1.21.11";

    /** set when a new version waits for the next start, for the message in game */
    public static volatile String readyVersion;
    /** set when auto update is off and a newer version exists - the Styles menu asks about it */
    public static volatile String availableVersion;
    /** what is new in the newer version, from version.json "notes": { tag, text } rows (may be empty) */
    public static volatile java.util.List<String[]> availableNotes = java.util.List.of();
    /** the version that is running now */
    public static volatile String installedVersion;

    @Override
    public void onPreLaunch() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("bauloo-updater");
        if (!enabled()) {
            LOGGER.info("[Updater] auto update is off, only checking for a new version");
            Thread look = new Thread(() -> check(dir, false), "Bauloo Updater");
            look.setDaemon(true);
            look.start();
            return;
        }
        try {
            Files.createDirectories(dir);
            installPending(dir);
        } catch (Exception e) {
            LOGGER.warn("[Updater] could not install the waiting update: {}", e.toString());
        }
        Thread check = new Thread(() -> check(dir, true), "Bauloo Updater");
        check.setDaemon(true);
        check.start();
    }

    /** Auto update was just turned on in game: fetch the new version right away. */
    public static void downloadNow() {
        if (!StylesUpdateLauncher.AVAILABLE) {
            return;
        }
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("bauloo-updater");
        Thread check = new Thread(() -> {
            try {
                Files.createDirectories(dir);
            } catch (IOException ignored) {
                return;
            }
            check(dir, true);
        }, "Bauloo Updater");
        check.setDaemon(true);
        check.start();
    }

    /** "notes": [["NEW", "..."], ...] from version.json, at most 6 rows, short text only */
    private static java.util.List<String[]> notes(JsonObject json) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try {
            if (json.has("notes") && json.get("notes").isJsonArray()) {
                for (var e : json.getAsJsonArray("notes")) {
                    var row = e.getAsJsonArray();
                    String tag = row.get(0).getAsString().toUpperCase(java.util.Locale.ROOT);
                    String text = row.get(1).getAsString();
                    if (tag.matches("NEW|FIX|GONE") && text.length() <= 60 && out.size() < 6) {
                        out.add(new String[]{tag, text});
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // no notes
        }
        return out;
    }

    /** The "Early access" switch (on unless turned off), read from the settings file like "Auto update". */
    private static boolean earlyAccess() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles-ui.properties");
        java.util.Properties p = new java.util.Properties();
        try (java.io.Reader in = Files.newBufferedReader(file)) {
            p.load(in);
        } catch (IOException e) {
            return true;
        }
        return Boolean.parseBoolean(p.getProperty("earlyAccess", "true"));
    }

    /** { key, device } of this PC's Bauloo key, or null - read from the files, the game is not loaded yet */
    private static String[] vipAuth() {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".bauloo-styles");
            Path key = dir.resolve("publish-token.txt");
            Path device = dir.resolve("device-id.txt");
            if (!Files.exists(key) || !Files.exists(device)) {
                return null;
            }
            String k = Files.readString(key).trim();
            String d = Files.readString(device).trim();
            return k.length() >= 16 && d.matches("[0-9a-f]{32,128}") ? new String[]{k, d} : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Reads the "Auto update" switch straight from the settings file (the game is not loaded yet). */
    private static boolean enabled() {
        if (!StylesUpdateLauncher.AVAILABLE) {
            return false; // the download without the updater never installs anything
        }
        Path file = FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles-ui.properties");
        java.util.Properties p = new java.util.Properties();
        try (java.io.Reader in = Files.newBufferedReader(file)) {
            p.load(in);
        } catch (IOException e) {
            return defaultOn();
        }
        return Boolean.parseBoolean(p.getProperty("autoUpdate", String.valueOf(defaultOn())));
    }

    /**
     * The "without auto update" download carries a marker file; there the switch starts off.
     * Either way it can be turned on or off in Extras.
     */
    public static boolean defaultOn() {
        return StylesUpdater.class.getResource("/assets/bauloo_styles/no-auto-update") == null;
    }

    // ---------------------------------------------------------------- step 2: next start

    /** Puts a downloaded update in place of the current Bauloo Styles jar. */
    private static void installPending(Path dir) throws IOException {
        Path pending = dir.resolve("pending.jar");
        if (!Files.exists(pending)) {
            return;
        }
        Optional<Path> current = stylesJar();
        if (current.isEmpty()) {
            LOGGER.info("[Updater] Bauloo Styles is not installed, nothing to replace");
            return;
        }
        Path target = current.get();
        // never install something that is not newer than what runs now (an old download)
        Path pendingInfo = dir.resolve("pending.txt");
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(STYLES_ID);
        if (Files.exists(pendingInfo) && mod.isPresent()) {
            try {
                Version waiting = SemanticVersion.parse(Files.readString(pendingInfo).trim());
                if (waiting.compareTo(mod.get().getMetadata().getVersion()) <= 0) {
                    Files.deleteIfExists(pending);
                    Files.deleteIfExists(pendingInfo);
                    LOGGER.info("[Updater] dropped an old download ({}), this version is newer", waiting.getFriendlyString());
                    return;
                }
            } catch (Exception ignored) {
                // unreadable version: install it, it passed the checksum when it was downloaded
            }
        }
        try {
            Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(pendingInfo);
            LOGGER.info("[Updater] installed the update into {} - it is active from the next start", target.getFileName());
        } catch (IOException locked) {
            // Windows keeps the mod file locked while the game runs: finish it after the game closes
            LOGGER.info("[Updater] {} is in use, the update goes in when the game closes", target.getFileName());
            StylesUpdateLauncher.installOnExit(dir, pending, pendingInfo, target);
        }
    }

    private static Optional<Path> stylesJar() {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(STYLES_ID);
        if (mod.isEmpty()) {
            return Optional.empty();
        }
        return mod.get().getOrigin().getPaths().stream()
                .filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(".jar"))
                .findFirst();
    }

    // ---------------------------------------------------------------- step 1: this start

    private static void check(Path dir, boolean download) {
        try {
            Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(STYLES_ID);
            if (mod.isEmpty()) {
                return;
            }
            Version installed = mod.get().getMetadata().getVersion();
            installedVersion = installed.getFriendlyString();
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> info = http.send(HttpRequest.newBuilder(URI.create(VERSION_URL + "?t=" + System.currentTimeMillis() / 60_000))
                    .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonObject json = info.statusCode() == 200 ? JsonParser.parseString(info.body()).getAsJsonObject() : null;
            if (json == null) {
                LOGGER.info("[Updater] version check answered {}", info.statusCode());
            }
            // early access: a VIP key on this PC may get a newer dev build from the worker
            String[] auth = earlyAccess() ? vipAuth() : null;
            JsonObject dev = null;
            if (auth != null) {
                try {
                    HttpResponse<String> d = http.send(HttpRequest.newBuilder(URI.create(WORKER + "/vip/update?mc=" + DEV_MC))
                            .header("Authorization", "Bearer " + auth[0]).header("X-Device", auth[1])
                            .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
                    if (d.statusCode() == 200) {
                        JsonObject o = JsonParser.parseString(d.body()).getAsJsonObject();
                        if (o.has("version") && !o.get("version").isJsonNull()) {
                            dev = o;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.info("[Updater] early access check skipped: {}", e.toString());
                }
            }
            boolean useDev = dev != null && (json == null || SemanticVersion.parse(dev.get("version").getAsString())
                    .compareTo(SemanticVersion.parse(json.get("version").getAsString())) > 0);
            if (useDev) {
                json = dev;
            }
            if (json == null) {
                return;
            }
            String latestText = json.get("version").getAsString();
            Version latest = SemanticVersion.parse(latestText);
            if (latest.compareTo(installed) <= 0) {
                LOGGER.info("[Updater] Bauloo Styles {} is up to date", installed.getFriendlyString());
                return;
            }
            availableNotes = notes(json);
            if (!download) {
                availableVersion = latestText;
                LOGGER.info("[Updater] Bauloo Styles {} is out (you have {}), auto update is off",
                        latestText, installed.getFriendlyString());
                return;
            }
            Path pending = dir.resolve("pending.jar");
            Path pendingInfo = dir.resolve("pending.txt");
            if (Files.exists(pending) && Files.exists(pendingInfo)
                    && Files.readString(pendingInfo).trim().equals(latestText)) {
                readyVersion = latestText;
                return; // already downloaded, waiting for the next start
            }
            String sha = json.get("sha256").getAsString().toLowerCase();
            HttpRequest.Builder get;
            if (useDev) {
                // the dev build comes only from bauloo's worker, with the same key
                get = HttpRequest.newBuilder(URI.create(WORKER + "/vip/devjar?mc=" + DEV_MC))
                        .header("Authorization", "Bearer " + auth[0]).header("X-Device", auth[1]);
                LOGGER.info("[Updater] early access: dev build {}", latestText);
            } else {
                String url = json.get("url").getAsString();
                if (!url.startsWith("https://raw.githubusercontent.com/Bauloo0/")) {
                    LOGGER.warn("[Updater] refusing a download from {}", url);
                    return;
                }
                get = HttpRequest.newBuilder(URI.create(url));
            }
            LOGGER.info("[Updater] downloading Bauloo Styles {} (you have {})", latestText, installed.getFriendlyString());
            HttpResponse<byte[]> jar = http.send(get.timeout(Duration.ofMinutes(2)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            byte[] data = jar.body();
            if (jar.statusCode() != 200 || data.length > 50_000_000) {
                LOGGER.warn("[Updater] download failed: {}", jar.statusCode());
                return;
            }
            String got = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
            if (!got.equals(sha)) {
                LOGGER.warn("[Updater] the download does not match its checksum, ignoring it");
                return;
            }
            Path tmp = dir.resolve("pending.jar.part");
            Files.write(tmp, data);
            Files.move(tmp, pending, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(pendingInfo, latestText);
            readyVersion = latestText;
            // put it in when this game closes, so one restart is enough (not two)
            Optional<Path> current = stylesJar();
            if (current.isPresent() && StylesUpdateLauncher.AVAILABLE) {
                StylesUpdateLauncher.installOnExit(dir, pending, pendingInfo, current.get());
            }
            LOGGER.info("[Updater] Bauloo Styles {} is ready and goes in when the game closes", latestText);
        } catch (Exception e) {
            LOGGER.info("[Updater] update check skipped: {}", e.toString());
        }
    }
}

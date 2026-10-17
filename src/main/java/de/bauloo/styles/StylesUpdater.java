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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Version check: asks bauloo's GitHub whether a newer Bauloo Styles is out, so the Styles menu can
 * say so (with a button to the website). It never downloads, writes or replaces a file - the
 * auto updater was removed in 1.6.0 (virus scanners flagged it as a downloader).
 *
 * Made by bauloo.
 */
public class StylesUpdater implements PreLaunchEntrypoint {

    public static final Logger LOGGER = LoggerFactory.getLogger("Bauloo Styles Updater");
    static final String STYLES_ID = "bauloo_styles";
    static final String VERSION_URL =
            "https://raw.githubusercontent.com/Bauloo0/bauloo-styles-cloud/main/release/version.json";

    /** set when a newer version exists - the Styles menu tells you about it */
    public static volatile String availableVersion;
    /** what is new in the newer version, from version.json "notes": { tag, text } rows (may be empty) */
    public static volatile java.util.List<String[]> availableNotes = java.util.List.of();
    /** the version that is running now */
    public static volatile String installedVersion;

    @Override
    public void onPreLaunch() {
        Thread look = new Thread(StylesUpdater::check, "Bauloo Version Check");
        look.setDaemon(true);
        look.start();
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

    private static void check() {
        try {
            Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(STYLES_ID);
            if (mod.isEmpty()) {
                return;
            }
            Version installed = mod.get().getMetadata().getVersion();
            installedVersion = installed.getFriendlyString();
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> info = http.send(HttpRequest.newBuilder(URI.create(VERSION_URL + "?t=" + System.currentTimeMillis() / 60_000))
                    .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (info.statusCode() != 200) {
                LOGGER.info("[Updater] version check answered {}", info.statusCode());
                return;
            }
            JsonObject json = JsonParser.parseString(info.body()).getAsJsonObject();
            String latestText = json.get("version").getAsString();
            if (SemanticVersion.parse(latestText).compareTo(installed) > 0) {
                availableNotes = notes(json);
                availableVersion = latestText;
                LOGGER.info("[Updater] Bauloo Styles {} is out (you have {})", latestText, installed.getFriendlyString());
            } else {
                LOGGER.info("[Updater] Bauloo Styles {} is up to date", installed.getFriendlyString());
            }
        } catch (Exception e) {
            LOGGER.info("[Updater] version check skipped: {}", e.toString());
        }
    }
}

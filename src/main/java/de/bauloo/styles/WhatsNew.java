package de.bauloo.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * "What's new" after an update: the notes ship with each version (assets/bauloo_styles/whatsnew.json)
 * and show once, the first time the Styles menu opens on a version this PC hasn't run before.
 */
public final class WhatsNew {

    public record Notes(String from, String to, String heroCape, String heroTitle, String heroText, String open,
                        List<String[]> items) {
    }

    private WhatsNew() {
    }

    public static String installed() {
        return FabricLoader.getInstance().getModContainer(BaulooStylesClient.MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString().split("\\+")[0]).orElse("");
    }

    /** The notes to show now, or null. Marks this version as seen either way. */
    public static Notes due() {
        StylesUiConfig.load();
        String now = installed();
        String last = StylesUiConfig.lastVersion;
        boolean audit = Boolean.getBoolean("bauloo.audit");   // dev check: always show it
        if (!audit && (now.isEmpty() || now.equals(last))) {
            return null;
        }
        // a brand-new install has no settings file yet: nothing to announce
        boolean existingUser = audit || StylesUiConfig.hadFile();
        StylesUiConfig.lastVersion = now;
        StylesUiConfig.save();
        try (InputStream in = WhatsNew.class.getResourceAsStream("/assets/bauloo_styles/whatsnew.json")) {
            if (in == null) {
                return null;
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // a small fix release keeps the same notes: they show only once
            String fingerprint = Integer.toHexString(text.replaceAll("\\s+", "").hashCode());
            boolean seen = fingerprint.equals(StylesUiConfig.lastNotes);
            StylesUiConfig.lastNotes = fingerprint;
            StylesUiConfig.save();
            if (!existingUser || (seen && !audit)) {
                return null;
            }
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonObject hero = root.getAsJsonObject("hero");
            List<String[]> items = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("items")) {
                JsonArray a = e.getAsJsonArray();
                items.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString()});
            }
            // "cape" is optional (no picture), "open" picks the page of the main button
            String cape = hero.has("cape") ? hero.get("cape").getAsString() : "";
            String open = root.has("open") ? root.get("open").getAsString() : "cosmetics";
            return new Notes(last, now, cape, hero.get("title").getAsString(),
                    hero.get("text").getAsString(), open, items);
        } catch (Exception e) {
            BaulooStylesClient.LOGGER.warn("What's new could not be read: {}", e.toString());
            return null;
        }
    }
}

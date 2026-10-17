package de.bauloo.styles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bauloo.styles.BaulooStylesClient.Tab;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Presets: a saved set of styles, one per collection, applied with one click.
 * Kept in config/bauloo-styles-presets.json.
 */
public final class StylesPresets {

    public static final int MAX_NAME = 20;

    /**
     * Collection -> folder; a collection that is missing means vanilla.
     * extras = the Extras settings (colours, sizes ...), empty for presets saved before they existed.
     */
    public record Preset(String name, Map<Tab, String> styles, Map<String, String> extras, String author, boolean locked) {
        public Preset(String name, Map<Tab, String> styles, Map<String, String> extras) {
            this(name, styles, extras, null, false);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Preset> PRESETS = new ArrayList<>();
    private static boolean loaded;

    private StylesPresets() {
    }

    public static List<Preset> all() {
        load();
        return PRESETS;
    }

    public static void add(String name, Map<Tab, String> styles, Map<String, String> extras) {
        add(name, styles, extras, null, false);
    }

    /**
     * author: who made it, for a preset redeemed from a code; locked: it can be used but not
     * shared on (no SHARE, no new code).
     */
    public static void add(String name, Map<Tab, String> styles, Map<String, String> extras, String author, boolean locked) {
        load();
        String clean = name.strip();
        if (clean.isEmpty()) {
            clean = "Set " + (PRESETS.size() + 1);
        }
        if (clean.length() > MAX_NAME) {
            clean = clean.substring(0, MAX_NAME);
        }
        final String finalName = clean;
        PRESETS.removeIf(p -> p.name().equalsIgnoreCase(finalName));
        PRESETS.add(new Preset(clean, new EnumMap<>(styles), new java.util.TreeMap<>(extras), author, locked));
        save();
    }

    public static void remove(Preset preset) {
        load();
        PRESETS.remove(preset);
        save();
    }

    public static String nextName() {
        load();
        int n = PRESETS.size() + 1;
        while (true) {
            String name = "Set " + n;
            if (PRESETS.stream().noneMatch(p -> p.name().equalsIgnoreCase(name))) {
                return name;
            }
            n++;
        }
    }

    // ---------------------------------------------------------------- storage

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles-presets.json");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) {
            return;
        }
        try {
            JsonArray list = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("presets");
            for (JsonElement element : list) {
                JsonObject o = element.getAsJsonObject();
                Map<Tab, String> styles = new EnumMap<>(Tab.class);
                JsonObject s = o.getAsJsonObject("styles");
                for (Tab tab : Tab.values()) {
                    if (s.has(tab.prefix) && s.get(tab.prefix).isJsonPrimitive()) {
                        styles.put(tab, s.get(tab.prefix).getAsString());
                    }
                }
                String author = o.has("author") && o.get("author").isJsonPrimitive() ? o.get("author").getAsString() : null;
                boolean locked = o.has("locked") && o.get("locked").getAsBoolean();
                PRESETS.add(new Preset(o.get("name").getAsString(), styles, readExtras(o), author, locked));
            }
        } catch (IOException | RuntimeException e) {
            BaulooStylesClient.LOGGER.warn("Could not read presets: {}", e.toString());
        }
    }

    /** The "extras" object of a preset, empty when it has none. */
    static Map<String, String> readExtras(JsonObject o) {
        Map<String, String> extras = new java.util.TreeMap<>();
        if (o.has("extras") && o.get("extras").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : o.getAsJsonObject("extras").entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    extras.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }
        return extras;
    }

    private static void save() {
        JsonArray list = new JsonArray();
        for (Preset preset : PRESETS) {
            JsonObject o = new JsonObject();
            o.addProperty("name", preset.name());
            JsonObject s = new JsonObject();
            preset.styles().forEach((tab, folder) -> s.addProperty(tab.prefix, folder));
            o.add("styles", s);
            JsonObject e = new JsonObject();
            preset.extras().forEach(e::addProperty);
            o.add("extras", e);
            if (preset.author() != null) {
                o.addProperty("author", preset.author());
            }
            if (preset.locked()) {
                o.addProperty("locked", true);
            }
            list.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("presets", list);
        try {
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BaulooStylesClient.LOGGER.warn("Could not save presets: {}", e.toString());
        }
    }
}

package de.bauloo.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extra collections that come from the cloud index ("categories" in index.json) instead of the code,
 * so bauloo can add a new kind of style (shields, beds, ...) without a mod update. Each one says
 * which files of a pack belong to it (rules: a regex on the path and where the file goes), how its
 * preview is drawn and which free menu slot (1-8) it takes. Broken entries are skipped; the built-in
 * collections never depend on this list.
 *
 * Example entry:
 * {"id": "shield", "slot": 1, "name": "Shields", "icon": "minecraft:shield",
 *  "files": [{"match": "^assets/minecraft/textures/entity/shield/(.+\\.png)$",
 *             "to": "assets/minecraft/textures/entity/shield/$1"}],
 *  "detect": "textures/entity/shield/base\\.png$",
 *  "preview": {"type": "item", "texture": "assets/minecraft/textures/item/shield.png"}}
 *
 * No Minecraft classes in here: Pack Preview runs this file outside the game.
 */
public final class CloudCategories {

    public static final int SLOTS = 8;
    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9]{1,15}$");
    private static final Pattern ICON = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final Pattern PATH = Pattern.compile("^assets/[a-z0-9_./$-]+$");

    /** The collections built into the code; a cloud entry can never replace one of them. */
    public static final Set<String> BUILT_IN = Set.of("crystal", "anchor", "armor", "fire", "sky", "obsidian", "sword",
            "pickaxe", "glowstone", "gap", "pearl", "hotbar", "axe", "totem", "stone", "deepslate", "snow", "grass",
            "inventory", "cosmetics", "extra", "upload", "cloud", "mypack");

    public record Rule(Pattern match, String to) {
    }

    /**
     * previewType: "item" (one flat texture) or "block" (a cube from top and side). crop: optional
     * {x, y, w, h} as parts of the texture (0-1), for textures that hold more than the picture,
     * like the shield's unfolded model.
     */
    /**
     * early: only for early access (VIP with the switch on) - shown and hidden right away when it flips.
     * admin: only for bauloo's admin key - these come from "adminCategories" in the index, which older
     * versions do not read, so nobody else ever sees them (and their styles are skipped there too).
     */
    public record Def(String id, int slot, String name, String icon, List<Rule> rules, String detect,
                      String previewType, String previewTop, String previewSide, double[] crop, boolean early,
                      boolean admin) {
    }

    private static volatile List<Def> defs = List.of();

    private CloudCategories() {
    }

    public static List<Def> all() {
        return defs;
    }

    /** the collections this player sees: early ones only with early access on, admin ones only for bauloo */
    public static List<Def> visible() {
        boolean early = VipPacks.earlyActive();
        boolean admin = VipPacks.admin();
        List<Def> out = new ArrayList<>();
        for (Def d : defs) {
            if ((!d.early() || early) && (!d.admin() || admin)) {
                out.add(d);
            }
        }
        return out;
    }

    public static Def get(String id) {
        for (Def d : defs) {
            if (d.id().equals(id)) {
                return d;
            }
        }
        return null;
    }

    public static boolean known(String category) {
        return BUILT_IN.contains(category) || get(category) != null;
    }

    /** Reads the "categories" and "adminCategories" arrays of the index. Returns true when the list changed. */
    public static boolean read(JsonObject root) {
        List<Def> parsed = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Integer> slots = new HashSet<>();
        for (String key : new String[]{"categories", "adminCategories"}) {
            if (!root.has(key) || !root.get(key).isJsonArray()) {
                continue;
            }
            for (JsonElement e : root.getAsJsonArray(key)) {
                try {
                    Def d = parse(e.getAsJsonObject(), key.equals("adminCategories"));
                    if (d != null && ids.add(d.id()) && slots.add(d.slot())) {
                        parsed.add(d);
                    }
                } catch (RuntimeException ignored) {
                    // a broken entry is skipped, the rest still loads
                }
            }
        }
        parsed.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
        boolean changed = !ids(parsed).equals(ids(defs));
        defs = List.copyOf(parsed);
        return changed;
    }

    /** For Pack Preview: the same list from the index file on disk. */
    public static void readJson(String json) {
        read(JsonParser.parseString(json).getAsJsonObject());
    }

    private static List<String> ids(List<Def> list) {
        List<String> out = new ArrayList<>();
        for (Def d : list) {
            out.add(d.slot() + ":" + d.id() + ":" + d.name() + ":" + d.icon());
        }
        return out;
    }

    private static Def parse(JsonObject o, boolean admin) {
        String id = str(o, "id");
        String name = str(o, "name");
        int slot = o.has("slot") ? o.get("slot").getAsInt() : -1;
        if (id == null || !ID.matcher(id).matches() || BUILT_IN.contains(id) || slot < 1 || slot > SLOTS
                || name == null || name.isBlank() || name.length() > 20) {
            return null;
        }
        String icon = str(o, "icon");
        if (icon == null || !ICON.matcher(icon).matches()) {
            icon = "minecraft:paper";
        }
        JsonArray files = o.has("files") && o.get("files").isJsonArray() ? o.getAsJsonArray("files") : new JsonArray();
        List<Rule> rules = new ArrayList<>();
        for (JsonElement f : files) {
            if (rules.size() >= 32 || !f.isJsonObject()) {
                break;
            }
            String match = str(f.getAsJsonObject(), "match");
            String to = str(f.getAsJsonObject(), "to");
            if (match == null || to == null || match.length() > 300 || !PATH.matcher(to).matches() || to.contains("..")) {
                continue;
            }
            rules.add(new Rule(Pattern.compile(match), to));
        }
        if (rules.isEmpty()) {
            return null;
        }
        String detect = str(o, "detect");
        String type = "item";
        String top = null;
        String side = null;
        double[] crop = null;
        if (o.has("preview") && o.get("preview").isJsonObject()) {
            JsonObject p = o.getAsJsonObject("preview");
            type = "block".equals(str(p, "type")) ? "block" : "item";
            top = str(p, type.equals("block") ? "top" : "texture");
            side = str(p, "side");
            if (p.has("crop") && p.get("crop").isJsonArray() && p.getAsJsonArray("crop").size() == 4) {
                JsonArray c = p.getAsJsonArray("crop");
                double[] r = {c.get(0).getAsDouble(), c.get(1).getAsDouble(), c.get(2).getAsDouble(), c.get(3).getAsDouble()};
                if (r[0] >= 0 && r[1] >= 0 && r[2] > 0 && r[3] > 0 && r[0] + r[2] <= 1.0001 && r[1] + r[3] <= 1.0001) {
                    crop = r;
                }
            }
        }
        boolean early = o.has("early") && o.get("early").isJsonPrimitive() && o.get("early").getAsBoolean();
        return new Def(id, slot, name.trim(), icon, List.copyOf(rules), detect, type, clean(top), clean(side), crop, early, admin);
    }

    private static String clean(String path) {
        return path != null && PATH.matcher(path.toLowerCase(Locale.ROOT)).matches() && !path.contains("..")
                ? path.toLowerCase(Locale.ROOT) : null;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    /** Where a pack entry goes for this cloud collection, or null. A rule has to match the whole (lower case) path. */
    public static String target(String lowerPath, String category) {
        Def d = get(category);
        if (d == null) {
            return null;
        }
        for (Rule r : d.rules()) {
            Matcher m = r.match().matcher(lowerPath);
            if (m.matches()) {
                try {
                    // the rule matches the whole path, so this is exactly "to" with its $1, $2 filled in
                    String to = m.replaceFirst(r.to());
                    if (PATH.matcher(to).matches() && !to.contains("..")) {
                        return to;
                    }
                } catch (RuntimeException ignored) {
                    // a bad group reference in the rule: this file is not taken
                }
            }
        }
        return null;
    }
}

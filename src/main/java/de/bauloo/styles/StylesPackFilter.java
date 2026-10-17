package de.bauloo.styles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Takes a whole texture pack and keeps only one part of it (crystal, anchor,
 * armor, fire, sky, obsidian, sword, pickaxe, axe, glowstone, totem, ...). Old 1.8 paths are renamed to the 1.21 layout on the way.
 */
public final class StylesPackFilter {

    public static final String[] CATEGORIES = {"crystal", "anchor", "armor", "fire", "sky", "obsidian", "sword", "pickaxe", "glowstone", "gap", "pearl", "hotbar", "axe", "totem", "stone", "deepslate", "snow", "inventory"};

    private static final String TEX = "assets/minecraft/textures/";
    private static final long MAX_UNPACKED = 400L * 1024 * 1024;
    private static final String META = "{\"pack\": {\"description\": \"Bauloo uploaded style - by bauloo\", "
            + "\"pack_format\": 75, \"min_format\": 65, \"max_format\": 9999}}\n";

    private static final Pattern OLD_ARMOR = Pattern.compile(
            "^" + TEX + "models/armor/(leather|chainmail|iron|gold|diamond|netherite|turtle)_layer_([12])(_overlay)?\\.png$");
    private static final Pattern ARMOR_ITEM = Pattern.compile(
            "^(leather|chainmail|iron|gold|golden|diamond|netherite|turtle)_(helmet|chestplate|leggings|boots)\\.png$");

    private static final Pattern TOOL_ITEM = Pattern.compile(
            "^(wood|wooden|stone|iron|gold|golden|diamond|netherite|copper)_(sword|pickaxe|axe)\\.png$");

    /** stone and the ores that sit in it - their background is the stone texture */
    private static final Pattern STONE = Pattern.compile(
            "^(stone|coal_ore|iron_ore|gold_ore|copper_ore|diamond_ore|emerald_ore|lapis_ore|redstone_ore)\\.png$");
    /** every deepslate block: plain, top, cobbled, bricks, tiles, ores, reinforced, ... */
    private static final Pattern DEEPSLATE = Pattern.compile("^[a-z_]*deepslate[a-z_]*\\.png$");

    private static final Pattern HOTBAR = Pattern.compile("^" + TEX + "gui/sprites/hud/hotbar"
            + "(_selection|_offhand_left|_offhand_right|_attack_indicator_background|_attack_indicator_progress)?\\.png$");

    private StylesPackFilter() {
    }

    /** The built-in categories plus the ones the cloud index adds (CloudCategories). */
    public static String[] categories() {
        java.util.List<String> all = new java.util.ArrayList<>(java.util.List.of(CATEGORIES));
        for (CloudCategories.Def d : CloudCategories.all()) {
            all.add(d.id());
        }
        return all.toArray(new String[0]);
    }

    /** Where an entry of the source pack ends up for this category, or null if it does not belong. */
    static String target(String rawName, String category) {
        String name = rawName.replace('\\', '/');
        if (name.startsWith("/") || name.contains("..")) {
            return null;
        }
        // Animation files follow the texture they belong to.
        if (name.endsWith(".png.mcmeta")) {
            String png = target(name.substring(0, name.length() - ".mcmeta".length()), category);
            return png == null ? null : png + ".mcmeta";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        switch (category) {
            case "crystal" -> {
                if (lower.startsWith(TEX + "entity/end_crystal/")) {
                    return name;
                }
                if (lower.equals(TEX + "entity/endercrystal/endercrystal.png")) {
                    return TEX + "entity/end_crystal/end_crystal.png";
                }
                if (lower.equals(TEX + "entity/endercrystal/endercrystal_beam.png")) {
                    return TEX + "entity/end_crystal/end_crystal_beam.png";
                }
                if (lower.equals(TEX + "item/end_crystal.png") || lower.equals(TEX + "items/end_crystal.png")) {
                    return TEX + "item/end_crystal.png";
                }
            }
            case "anchor" -> {
                if (lower.startsWith(TEX + "block/respawn_anchor_")) {
                    return name;
                }
            }
            case "armor" -> {
                if (lower.startsWith(TEX + "entity/equipment/humanoid/")
                        || lower.startsWith(TEX + "entity/equipment/humanoid_leggings/")) {
                    return name;
                }
                Matcher old = OLD_ARMOR.matcher(lower);
                if (old.matches()) {
                    String material = switch (old.group(1)) {
                        case "turtle" -> "turtle_scute";
                        default -> old.group(1);
                    };
                    String folder = "1".equals(old.group(2)) ? "humanoid" : "humanoid_leggings";
                    String overlay = old.group(3) == null ? "" : "_overlay";
                    return TEX + "entity/equipment/" + folder + "/" + material + overlay + ".png";
                }
                for (String items : new String[]{TEX + "item/", TEX + "items/"}) {
                    if (lower.startsWith(items)) {
                        String file = lower.substring(items.length());
                        if (ARMOR_ITEM.matcher(file).matches()) {
                            return TEX + "item/" + file.replace("gold_", "golden_");
                        }
                        if (file.startsWith("empty_armor_slot_")) {
                            return TEX + "item/" + file;
                        }
                    }
                }
            }
            case "fire" -> {
                if (lower.equals(TEX + "block/fire_0.png") || lower.equals(TEX + "block/fire_1.png")) {
                    return name;
                }
                if (lower.equals(TEX + "blocks/fire_layer_0.png")) {
                    return TEX + "block/fire_0.png";
                }
                if (lower.equals(TEX + "blocks/fire_layer_1.png")) {
                    return TEX + "block/fire_1.png";
                }
            }
            case "sky" -> {
                if (lower.startsWith("assets/minecraft/optifine/sky/")) {
                    return name;
                }
                if (lower.startsWith("assets/minecraft/mcpatcher/sky/")) {
                    return "assets/minecraft/optifine/sky/" + name.substring("assets/minecraft/mcpatcher/sky/".length());
                }
            }
            case "obsidian" -> {
                for (String blocks : new String[]{TEX + "block/", TEX + "blocks/"}) {
                    if (lower.equals(blocks + "obsidian.png") || lower.equals(blocks + "crying_obsidian.png")) {
                        return TEX + "block/" + lower.substring(blocks.length());
                    }
                }
            }
            case "glowstone" -> {
                for (String blocks : new String[]{TEX + "block/", TEX + "blocks/"}) {
                    if (lower.equals(blocks + "glowstone.png")) {
                        return TEX + "block/glowstone.png";
                    }
                }
            }
            case "gap" -> {
                for (String items : new String[]{TEX + "item/", TEX + "items/"}) {
                    if (lower.equals(items + "golden_apple.png") || lower.equals(items + "apple_golden.png")) {
                        return TEX + "item/golden_apple.png";
                    }
                    if (lower.equals(items + "enchanted_golden_apple.png")) {
                        return TEX + "item/enchanted_golden_apple.png";
                    }
                }
            }
            case "hotbar" -> {
                // the 1.20.2+ HUD sprites: the bar, the selection frame, the offhand slots, the attack indicator
                if (HOTBAR.matcher(lower).matches()) {
                    return name;
                }
            }
            case "pearl" -> {
                for (String items : new String[]{TEX + "item/", TEX + "items/"}) {
                    if (lower.equals(items + "ender_pearl.png")) {
                        return TEX + "item/ender_pearl.png";
                    }
                }
            }
            case "stone", "deepslate" -> {
                for (String blocks : new String[]{TEX + "block/", TEX + "blocks/"}) {
                    if (lower.startsWith(blocks)) {
                        String file = lower.substring(blocks.length());
                        if (("stone".equals(category) ? STONE : DEEPSLATE).matcher(file).matches()) {
                            return TEX + "block/" + file;
                        }
                    }
                }
            }
            case "snow" -> {
                for (String blocks : new String[]{TEX + "block/", TEX + "blocks/"}) {
                    if (lower.equals(blocks + "snow.png") || lower.equals(blocks + "powder_snow.png")) {
                        return TEX + "block/" + lower.substring(blocks.length());
                    }
                    if (lower.equals(blocks + "grass_block_snow.png") || lower.equals(blocks + "grass_side_snowed.png")) {
                        return TEX + "block/grass_block_snow.png";
                    }
                }
            }
            case "inventory" -> {
                // every container screen: inventory, chests, furnace ... and their 1.20.2+ sprites (pictures only)
                if ((lower.startsWith(TEX + "gui/container/") || lower.startsWith(TEX + "gui/sprites/container/"))
                        && lower.endsWith(".png")) {
                    return name;
                }
            }
            case "totem" -> {
                // the totem item (1.11/1.12 packs call it items/totem.png); its animation file follows it
                for (String items : new String[]{TEX + "item/", TEX + "items/"}) {
                    if (lower.equals(items + "totem_of_undying.png") || lower.equals(items + "totem.png")) {
                        return TEX + "item/totem_of_undying.png";
                    }
                }
            }
            case "sword", "pickaxe", "axe" -> {
                for (String items : new String[]{TEX + "item/", TEX + "items/"}) {
                    if (lower.startsWith(items)) {
                        Matcher tool = TOOL_ITEM.matcher(lower.substring(items.length()));
                        if (tool.matches() && tool.group(2).equals(category)) {
                            String material = switch (tool.group(1)) {
                                case "gold" -> "golden";
                                case "wood" -> "wooden";
                                default -> tool.group(1);
                            };
                            return TEX + "item/" + material + "_" + category + ".png";
                        }
                    }
                }
            }
            default -> {
                // a collection from the cloud index: its own rules say where the file goes
                return CloudCategories.target(lower, category);
            }
        }
        return null;
    }

    /** How many files of the pack belong to each category. */
    public static Map<String, Integer> analyze(Path zip) throws IOException {
        Map<String, Integer> found = new LinkedHashMap<>();
        String[] categories = categories();
        for (String category : categories) {
            found.put(category, 0);
        }
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                for (String category : categories) {
                    String t = target(entry.getName(), category);
                    if (t != null && t.endsWith(".png")) {
                        found.merge(category, 1, Integer::sum);
                    }
                }
            }
        }
        return found;
    }

    /** Writes a small pack with only this category. Returns how many textures were kept. */
    public static int extract(Path zip, String category, Path destination) throws IOException {
        long unpacked = 0;
        int textures = 0;
        Set<String> written = new HashSet<>();
        byte[] buffer = new byte[64 * 1024];
        Path temp = Files.createTempFile(destination.getParent(), "filter-", ".zip");
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String t = target(entry.getName(), category);
                if (t == null || !written.add(t.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                out.putNextEntry(new ZipEntry(t));
                int read;
                while ((read = in.read(buffer)) > 0) {
                    unpacked += read;
                    if (unpacked > MAX_UNPACKED) {
                        throw new IOException("Pack is too big once unpacked");
                    }
                    out.write(buffer, 0, read);
                }
                out.closeEntry();
                if (t.endsWith(".png")) {
                    textures++;
                }
            }
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write(META.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        if (textures == 0) {
            Files.deleteIfExists(temp);
            return 0;
        }
        Files.move(temp, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return textures;
    }
}

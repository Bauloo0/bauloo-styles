package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;

/**
 * Grass color: the tint lives in GrassColorMixin. The colour comes from the active style in the
 * Grass collection (the Extras "Own color" is gone since 1.5.3). This keeps that colour up to date
 * and redraws the world when it changes.
 */
public final class GrassColor {

    /** the colour of the active Grass style, or -1 for none */
    public static volatile int packColor = -1;

    private static final java.util.Map<String, Integer> PACKS = java.util.Map.ofEntries(
            java.util.Map.entry("grass_lush", 0x6CC349), java.util.Map.entry("grass_dark", 0x3E7A2C),
            java.util.Map.entry("grass_lime", 0xA6E05A), java.util.Map.entry("grass_white", 0xF2F2F2),
            java.util.Map.entry("grass_black", 0x3A3A40), java.util.Map.entry("grass_blue", 0x5A8CFF),
            java.util.Map.entry("grass_ice", 0x9EDCF5), java.util.Map.entry("grass_red", 0xE05050),
            java.util.Map.entry("grass_pink", 0xF4A0C8), java.util.Map.entry("grass_purple", 0xB07CFF),
            java.util.Map.entry("grass_lavender", 0xC8B8F0));

    private static String state = "";
    /** how often GrassColorMixin ran - 0 means something else colours the grass */
    public static volatile long mixinCalls;

    /** The colour grass gets right now, or -1 to leave it to the biome. */
    public static int current() {
        return packColor;
    }

    /** The final grass colour (opaque), or -1 to leave it alone. */
    public static int shown() {
        int color = current();
        if (color < 0) {
            return -1;
        }
        color |= 0xFF000000;
        return color;
    }

    private GrassColor() {
    }

    public static void applyLive() {
        StylesUiConfig.load();
        int pack = -1;
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            for (String id : client.getResourcePackManager().getEnabledIds()) {
                int colon = id.indexOf(':');
                Integer color = PACKS.get(colon < 0 ? id : id.substring(colon + 1));
                if (color != null) {
                    pack = color;
                }
            }
        } catch (Throwable ignored) {
            // pack list not ready yet
        }
        packColor = pack;
        String now = String.valueOf(pack);
        boolean changed = !now.equals(state);
        boolean first = state.isEmpty();
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean reload = changed && !first && mc.worldRenderer != null && mc.world != null;
        if (!changed) {
            return;
        }
        state = now;
        if (reload) {
            mc.worldRenderer.reload(); // biome colours are baked into the chunks
        }
    }
}

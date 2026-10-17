package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;

/** Block Outline: the lines around the block you look at, solid, thicker and in your colour. */
public final class BlockOutline {

    private BlockOutline() {
    }

    public static boolean active() {
        return StylesUiConfig.blockOutlineCustom;
    }

    /** ARGB, fully solid (vanilla draws 40 % black). */
    public static int color() {
        return 0xFF000000 | (StylesUiConfig.blockOutlineColor & 0xFFFFFF);
    }

    /** Thickness 1 is the vanilla line; every step adds one more of it. */
    public static float width() {
        float base = MinecraftClient.getInstance().getWindow().getMinimumLineWidth();
        return base * Math.max(1, Math.min(6, StylesUiConfig.blockOutlineWidth));
    }
}

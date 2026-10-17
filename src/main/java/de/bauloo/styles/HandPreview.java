package de.bauloo.styles;

import net.minecraft.item.ItemStack;

/** The item shown in the main hand while a hand slider is dragged; null = normal hands. */
public final class HandPreview {

    private static volatile ItemStack stack;

    private HandPreview() {
    }

    public static ItemStack stack() {
        return stack;
    }

    public static void show(ItemStack item) {
        stack = item;
    }

    public static void clear() {
        stack = null;
    }
}

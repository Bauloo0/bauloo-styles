package de.bauloo.styles;

import net.minecraft.client.render.model.ModelBaker;

/** Fire Color: both fire sprites (screen overlay, burning mobs, fire blocks) in your colour, live. */
public final class FireColor {

    private static boolean applied;

    private FireColor() {
    }

    /** Render thread. */
    public static void applyLive() {
        StylesUiConfig.load();
        if (!StylesUiConfig.fireCustom && !applied) {
            return;
        }
        int color = StylesUiConfig.fireCustom ? StylesUiConfig.fireColor : -1;
        try {
            LiveRecolor.apply(ModelBaker.FIRE_0, color);
            LiveRecolor.apply(ModelBaker.FIRE_1, color);
            applied = StylesUiConfig.fireCustom;
        } catch (RuntimeException e) {
            BaulooStylesClient.LOGGER.warn("Fire colour could not be applied: {}", e.toString());
        }
    }

    /** A new atlas starts from the pack's fire again. */
    public static void atlasRebuilt() {
        applied = false;
        applyLive();
    }
}

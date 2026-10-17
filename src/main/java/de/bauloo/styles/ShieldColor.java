package de.bauloo.styles;

import net.minecraft.client.render.model.ModelBaker;

/** Shield Color: the shield (with and without a banner) in your colour, live. */
public final class ShieldColor {

    private static boolean applied;

    private ShieldColor() {
    }

    /** Render thread. */
    public static void applyLive() {
        StylesUiConfig.load();
        if (!StylesUiConfig.shieldCustom && !applied) {
            return;
        }
        // bright grey texture; the colour itself is the tint the shield is drawn with (strong, like ShieldStatus)
        int color = StylesUiConfig.shieldCustom ? LiveRecolor.GRAY : -1;
        try {
            LiveRecolor.apply(ModelBaker.SHIELD_BASE, color);
            LiveRecolor.apply(ModelBaker.SHIELD_BASE_NO_PATTERN, color);
            applied = StylesUiConfig.shieldCustom;
        } catch (RuntimeException e) {
            BaulooStylesClient.LOGGER.warn("Shield colour could not be applied: {}", e.toString());
        }
    }

    public static void atlasRebuilt() {
        applied = false;
        applyLive();
    }
}

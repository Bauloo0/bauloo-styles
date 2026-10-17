package de.bauloo.styles;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Draws the crosshair in the chosen colour, or as a left-to-right gradient between two colours. */
public final class StylesCrosshair {

    private StylesCrosshair() {
    }

    public static void draw(DrawContext ctx, Identifier texture, int x, int y, int w, int h) {
        int from = 0xFF000000 | StylesUiConfig.crosshairColor;
        if (!StylesUiConfig.crosshairGradient) {
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, w, h, from);
            return;
        }
        int to = 0xFF000000 | StylesUiConfig.crosshairColor2;
        // one column at a time, each with its own mixed colour
        for (int i = 0; i < w; i++) {
            float t = w <= 1 ? 0 : i / (float) (w - 1);
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture, w, h, i, 0, x + i, y, 1, h, mix(from, to, shaped(t)));
        }
    }

    /** Gradient strength: pushes the fade towards the middle so each colour shows more. */
    public static float shaped(float t) {
        float strength = StylesUiConfig.gradientStrength / 100.0F;
        return Math.max(0, Math.min(1, 0.5F + (t - 0.5F) * strength));
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}

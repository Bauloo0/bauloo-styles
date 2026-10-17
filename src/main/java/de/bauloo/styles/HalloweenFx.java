package de.bauloo.styles;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * The Halloween theme's decorations and animations in the Style Archive.
 * Every moving thing is a pure function of the clock (no state, nothing allocated per frame),
 * so it runs the same at 60 or 300 FPS and costs only a few dozen textured quads.
 */
public final class HalloweenFx {

    private static final Identifier PUMPKIN = tex("halloween_pumpkin");
    private static final Identifier BAT = tex("halloween_bat");
    private static final Identifier WEB = tex("halloween_web");
    private static final Identifier GLOW = tex("halloween_moon");
    private static final Identifier LEAF = tex("halloween_leaf");
    private static final Identifier GHOST = tex("halloween_ghost");
    private static final Identifier GRAVE = tex("halloween_grave");
    private static final Identifier SPIDER = tex("halloween_spider");

    private static final int LEAVES = 14;
    private static final int EMBERS = 10;
    private static final int[] LEAF_COLORS = {0xE07A2B, 0xC0392B, 0x8E5A2B, 0xF2B53A};
    /** x share, y offset, size, phase */
    private static final float[][] BATS = {{0.10F, 10, 22, 0.0F}, {0.30F, 4, 15, 1.7F}, {0.46F, 16, 12, 3.1F},
            {0.70F, 7, 24, 4.4F}, {0.88F, 2, 16, 5.6F}};

    private HalloweenFx() {
    }

    private static Identifier tex(String name) {
        return Identifier.of(BaulooStylesClient.MOD_ID, "textures/gui/" + name + ".png");
    }

    /** seconds, smooth (not tick based) */
    private static float time() {
        return (System.nanoTime() / 1_000_000L % 3_600_000L) / 1000.0F;
    }

    /** a fixed pseudo random number in [0,1) for particle i, value k */
    private static float rand(int i, int k) {
        double v = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }

    private static int argb(float alpha, int rgb) {
        return Math.max(0, Math.min(255, Math.round(alpha * 255))) << 24 | (rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ header

    /** Moon glow behind the logo and a half moon over the header, clipped to it. */
    public static void moon(DrawContext ctx, int x, int y, int w, int headerH) {
        ctx.enableScissor(x + 1, y + 1, x + w - 1, y + headerH);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GLOW, x - 26, y - 34, 0, 0, 96, 96, 256, 256, 256, 256, 0x66FF9A3C);
        int mx = x + w * 58 / 100;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GLOW, mx - 70, y - 70, 0, 0, 140, 140, 256, 256, 256, 256, 0x40FF7A1E);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GLOW, mx - 34, y - 40, 0, 0, 68, 68, 256, 256, 256, 256, 0x8CFFB060);
        ctx.disableScissor();
    }

    public static void pumpkin(DrawContext ctx, int x, int y, int size) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, PUMPKIN, x, y, 0, 0, size, size, 128, 128, 128, 128);
    }

    /** Bats drifting and flapping between left and right. */
    public static void bats(DrawContext ctx, int left, int right, int top) {
        if (right - left < 80) {
            return;
        }
        float t = time();
        Matrix3x2fStack m = ctx.getMatrices();
        for (float[] b : BATS) {
            float x = left + (right - left) * b[0] + (float) Math.sin(t * 0.35F + b[3]) * 18;
            float y = top + b[1] + (float) Math.sin(t * 1.2F + b[3]) * 2.5F;
            float flap = 0.45F + 0.55F * Math.abs((float) Math.sin(t * 8.0F + b[3]));
            int w = Math.round(b[2]);
            int h = Math.max(2, w / 2);
            m.pushMatrix();
            m.translate(x, y);
            m.scale(1.0F, flap);
            // a faint orange halo, then the dark bat
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, BAT, -w / 2 - 1, -h / 2 - 1, 0, 0, w + 2, h + 2,
                    128, 64, 128, 64, 0x55FF8A1F);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, BAT, -w / 2, -h / 2, 0, 0, w, h, 128, 64, 128, 64, 0xF0170A1C);
            m.popMatrix();
        }
    }

    // ------------------------------------------------------------- behind content

    /** Candle light from below, purple glow under the header, graveyard, pumpkins and the ghost. */
    public static void behind(DrawContext ctx, int fx, int fy, int fw, int headerH, int mainX, int mainW, int footerY) {
        float t = time();
        ctx.fillGradient(fx + 1, fy + headerH, fx + fw - 1, fy + headerH + 80, 0x1A9B5CFF, 0x009B5CFF);
        ctx.fillGradient(fx + 1, footerY - 120, fx + fw - 1, footerY, 0x00FF6E14, 0x24FF6E14);

        // graveyard along the bottom of the list, fog over it
        int gh = Math.max(16, Math.min(30, mainW / 30));
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GRAVE, mainX, footerY - gh, 0, 0, mainW, gh, 1024, 96, 1024, 96);
        float[] at = {0.21F, 0.53F, 0.81F};
        int[] sizes = {10, 13, 9};
        for (int i = 0; i < at.length; i++) {
            int s = sizes[i];
            int px = mainX + Math.round(mainW * at[i]);
            float flicker = 0.78F + 0.22F * (float) Math.sin(t * 3.1F + i * 1.9F) * (float) Math.sin(t * 7.3F + i);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GLOW, px - s, footerY - s - s, 0, 0, s * 3, s * 3,
                    256, 256, 256, 256, argb(0.35F * flicker, 0xFF8A1F));
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, PUMPKIN, px, footerY - s - 2, 0, 0, s, s, 128, 128, 128, 128,
                    argb(flicker, 0xFFFFFF));
        }
        ctx.fillGradient(mainX, footerY - 60, mainX + mainW, footerY, 0x009B5CFF, 0x2E9B5CFF);

        // the ghost floats through every 26 seconds and fades in and out
        float cycle = t % 26.0F;
        if (cycle < 11.0F) {
            float p = cycle / 11.0F;
            float alpha = 0.6F * (float) Math.sin(Math.PI * p);
            float gx = mainX + mainW * (0.12F + 0.70F * p);
            float gy = footerY - 62 + (float) Math.sin(t * 1.8F) * 4;
            Matrix3x2fStack m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(gx, gy);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GHOST, -15, -17, 0, 0, 30, 35, 96, 112, 96, 112, argb(alpha, 0xFFFFFF));
            m.popMatrix();
        }
    }

    // ---------------------------------------------------------------- in front

    /** Orange rim, cobwebs, the spider, falling leaves and rising embers. */
    public static void front(DrawContext ctx, int fx, int fy, int fw, int fh, int headerH, int footerY,
                             boolean side, int sideX, int sideW) {
        float t = time();
        int rim = 0x59FF8A1F;
        // the round Glass panel: the rim stops before the corners
        ctx.fill(fx + 5, fy, fx + fw - 5, fy + 1, rim);
        ctx.fill(fx + 5, fy + fh - 1, fx + fw - 5, fy + fh, rim);
        ctx.fill(fx, fy + 5, fx + 1, fy + fh - 5, rim);
        ctx.fill(fx + fw - 1, fy + 5, fx + fw, fy + fh - 5, rim);

        int web = Math.min(80, fh / 5);
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(fx + fw - 1, fy + 1);
        m.scale(-1.0F, 1.0F);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, WEB, 0, 0, 0, 0, web, web, 256, 256, 256, 256, 0x59FFFFFF);
        m.popMatrix();
        int small = web * 3 / 4;
        m.pushMatrix();
        m.translate(fx + 1, fy + fh - 1);
        m.scale(1.0F, -1.0F);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, WEB, 0, 0, 0, 0, small, small, 256, 256, 256, 256, 0x3AFFFFFF);
        m.popMatrix();
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, WEB, fx + 1, fy + 1, 0, 0, small * 2 / 3, small * 2 / 3,
                256, 256, 256, 256, 0x2EFFFFFF);

        // a spider slowly going up and down on its thread at the right edge
        if (side) {
            float sx = sideX + sideW - 16;
            float sy = footerY - 140 + (float) Math.sin(t * 0.7F) * 35;
            int top = fy + headerH;
            ctx.fill(Math.round(sx), top, Math.round(sx) + 1, Math.round(sy) - 4, 0x55F1E4FF);
            m.pushMatrix();
            m.translate(sx + 0.5F, sy);
            m.rotate((float) Math.sin(t * 1.4F) * 0.15F);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, SPIDER, -8, -6, 0, 0, 16, 16, 64, 64, 64, 64);
            m.popMatrix();
        }

        // leaves falling with a gentle sway, wrapping around
        int areaTop = fy + headerH;
        int areaH = footerY - areaTop;
        if (areaH > 40) {
            for (int i = 0; i < LEAVES; i++) {
                float speed = 14 + rand(i, 1) * 16;
                float span = areaH + 30;
                float y = areaTop - 15 + (t * speed + rand(i, 2) * span) % span;
                float baseX = fx + 10 + rand(i, 3) * (fw - 20);
                float x = baseX + (float) Math.sin(t * (0.6F + rand(i, 4) * 0.5F) + i) * 16;
                float rot = (float) Math.sin(t * 1.3F + i * 2.1F) * 0.9F + t * (rand(i, 5) - 0.5F);
                int size = 6 + Math.round(rand(i, 6) * 5);
                float fade = Math.min(1.0F, (footerY - y) / 30.0F);
                if (fade <= 0) {
                    continue;
                }
                m.pushMatrix();
                m.translate(x, y);
                m.rotate(rot);
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LEAF, -size / 2, -size / 2, 0, 0, size, size, 64, 64, 64, 64,
                        argb(0.8F * fade, LEAF_COLORS[i % LEAF_COLORS.length]));
                m.popMatrix();
            }
        }

        // embers rising from the bottom and fading out
        for (int i = 0; i < EMBERS; i++) {
            float speed = 10 + rand(i, 7) * 12;
            float rise = Math.min(180, areaH * 0.6F);
            float p = ((t * speed + rand(i, 8) * rise) % rise) / rise;
            float x = fx + 20 + rand(i, 9) * (fw - 40) + (float) Math.sin(t * 1.7F + i) * 6;
            float y = footerY - 6 - p * rise;
            int s = 3 + (i % 2);
            m.pushMatrix();
            m.translate(x, y);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GLOW, -s * 2, -s * 2, 0, 0, s * 4, s * 4, 256, 256, 256, 256,
                    argb(0.9F * (1 - p), 0xFFB24D));
            m.popMatrix();
        }
    }
}

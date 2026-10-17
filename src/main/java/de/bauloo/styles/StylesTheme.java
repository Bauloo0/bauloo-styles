package de.bauloo.styles;

/**
 * Colors of the Style Archive. Everything is derived from one accent color and
 * a background mode, so a preset or a custom color recolors the whole menu.
 */
public final class StylesTheme {

    /**
     * The colours under the Glass menu (since 1.5.3 the only look: centred, rounded, see-through).
     * GLASS = neutral, TINTED = tinted by the accent, BLACK = Mono, HALLOWEEN = plum-black plus the
     * decorations. NAVY is the old Ice look and only kept so old settings still load (as GLASS).
     */
    public enum Background { NAVY, TINTED, BLACK, GLASS, HALLOWEEN }

    public record Preset(String name, int accent, Background background) {
    }

    /** Glass is the default; the others are the same menu in other colours. */
    public static final Preset[] PRESETS = {
            new Preset("Glass", 0x75D4FF, Background.GLASS),
            new Preset("Halloween", 0xFF8A1F, Background.HALLOWEEN),
            new Preset("Crimson", 0xFF5A6E, Background.TINTED),
            new Preset("Amethyst", 0xB388FF, Background.TINTED),
            new Preset("Amber", 0xFFB547, Background.TINTED),
            new Preset("Sakura", 0xFF9CCF, Background.TINTED),
            new Preset("Mono", 0xE8E8EA, Background.BLACK),
    };

    public static final int[] SWATCHES = {
            0x75D4FF, 0x4F8CFF, 0xB388FF, 0xFF9CCF, 0xFF5A6E, 0xFFB547, 0xFFE45C, 0xE8E8EA
    };

    public final int backdrop;
    public final int frame;
    public final int header;
    public final int rail;
    public final int footer;
    public final int base;
    public final int surface;
    public final int surface2;
    public final int surfaceHover;
    public final int border;
    public final int borderDim;
    public final int borderStrong;
    public final int accent;
    public final int accentBright;
    public final int accentDark;
    public final int accentTint;
    public final int onAccent;
    public final int text;
    public final int textSoft;
    public final int textDim;
    public final int good = 0xFF73E0B0;
    public final int onGood = 0xFF06140E;
    public final int gold = 0xFFFFCF5A;
    /** the Halloween look: the menu adds pumpkin, bats, cobwebs and fog */
    public final boolean spooky;

    private StylesTheme(int accentRgb, Background background, int opacityPercent) {
        spooky = background == Background.HALLOWEEN;
        int accentColor = accentRgb & 0xFFFFFF;
        int bg0;
        int frameRgb;
        int headerRgb;
        int railRgb;
        int footerRgb;
        int surfaceRgb;
        int surface2Rgb;
        int lineRgb;
        int lineDimRgb;
        int textRgb;
        int softRgb;
        int dimRgb;
        switch (background) {
            case HALLOWEEN -> {
                bg0 = 0x0B0610;
                frameRgb = 0x130B17;
                headerRgb = 0x1A0F1E;
                railRgb = 0x110A15;
                footerRgb = 0x0F0913;
                surfaceRgb = 0x1A1020;
                surface2Rgb = 0x23152A;
                lineRgb = 0x3B2440;
                lineDimRgb = 0x26172B;
                textRgb = 0xFFF3E6;
                softRgb = 0xD6BFCF;
                dimRgb = 0x957D92;
            }
            case GLASS, NAVY -> {
                bg0 = 0x0B0C10;
                frameRgb = 0x101116;
                headerRgb = 0x101116;
                railRgb = 0x16171C;
                footerRgb = 0x101116;
                surfaceRgb = 0x1B1C22;
                surface2Rgb = 0x25262D;
                lineRgb = 0x34363F;
                lineDimRgb = 0x23242B;
                textRgb = 0xF4F5F7;
                softRgb = 0xBCC0CA;
                dimRgb = 0x7C808C;
            }
            case BLACK -> {
                bg0 = 0x08080A;
                frameRgb = 0x0F0F12;
                headerRgb = 0x151518;
                railRgb = 0x0C0C0E;
                footerRgb = 0x0B0B0D;
                surfaceRgb = 0x131316;
                surface2Rgb = 0x1A1A1F;
                lineRgb = 0x33333A;
                lineDimRgb = 0x222228;
                textRgb = 0xF0F0F3;
                softRgb = 0xB5B5BD;
                dimRgb = 0x74747E;
            }
            default -> {
                float[] hsv = toHsv(accentColor);
                float h = hsv[0];
                // A grey accent should give a neutral background, not a red one.
                float s = Math.min(1.0F, hsv[1] * 1.1F);
                bg0 = hsv(h, 0.45F * s, 0.055F);
                frameRgb = hsv(h, 0.40F * s, 0.085F);
                headerRgb = hsv(h, 0.38F * s, 0.11F);
                railRgb = hsv(h, 0.40F * s, 0.07F);
                footerRgb = hsv(h, 0.40F * s, 0.065F);
                surfaceRgb = hsv(h, 0.36F * s, 0.10F);
                surface2Rgb = hsv(h, 0.34F * s, 0.14F);
                lineRgb = hsv(h, 0.38F * s, 0.27F);
                lineDimRgb = hsv(h, 0.36F * s, 0.17F);
                textRgb = hsv(h, 0.05F * s, 0.98F);
                softRgb = hsv(h, 0.16F * s, 0.78F);
                dimRgb = hsv(h, 0.22F * s, 0.54F);
            }
        }

        int alpha = Math.max(60, Math.min(100, opacityPercent)) * 255 / 100;
        // see-through: the blurred world shows behind the panel
        alpha = alpha * 82 / 100;
        backdrop = argb(0x50, bg0);
        frame = argb(alpha, frameRgb);
        header = argb(alpha, headerRgb);
        rail = argb(alpha, railRgb);
        footer = argb(alpha, footerRgb);
        base = argb(0xFF, bg0);
        surface = argb(0xFF, surfaceRgb);
        surface2 = argb(0xFF, surface2Rgb);
        surfaceHover = argb(0xFF, mix(surfaceRgb, textRgb, 0.06F));
        border = argb(0xFF, lineRgb);
        borderDim = argb(0xFF, lineDimRgb);
        borderStrong = argb(0xFF, mix(lineRgb, accentColor, 0.25F));
        accent = argb(0xFF, accentColor);
        accentBright = argb(0xFF, mix(accentColor, 0xFFFFFF, 0.45F));
        accentDark = argb(0xFF, mix(accentColor, bg0, 0.55F));
        accentTint = argb(0x2E, accentColor);
        onAccent = argb(0xFF, mix(bg0, 0x000000, 0.3F));
        text = argb(0xFF, textRgb);
        textSoft = argb(0xFF, softRgb);
        textDim = argb(0xFF, dimRgb);
    }

    public static StylesTheme of(int accentRgb, Background background, int opacityPercent) {
        return new StylesTheme(accentRgb, background, opacityPercent);
    }

    // ----------------------------------------------------------------- colors

    public static int argb(int alpha, int rgb) {
        return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    public static int withAlpha(int color, int alpha) {
        return argb(alpha, color);
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    public static int hsv(float h, float s, float v) {
        float hue = ((h % 1.0F) + 1.0F) % 1.0F * 6.0F;
        int sector = (int) Math.floor(hue);
        float f = hue - sector;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r;
        float g;
        float b;
        switch (sector % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    public static float[] toHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) {
                h = ((g - b) / d) % 6;
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h /= 6;
            if (h < 0) {
                h += 1;
            }
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }
}

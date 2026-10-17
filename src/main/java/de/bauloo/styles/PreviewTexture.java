package de.bauloo.styles;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Vorschaubilder im Menue: auf die Groesse verkleinert, in der sie gezeigt werden,
 * und ohne Weichzeichner gezeichnet. Sonst sehen die Kacheln matschig aus.
 */
public final class PreviewTexture extends NativeImageBackedTexture {

    /** Groesser als das wird kein Vorschaubild gehalten. */
    public static final int SIZE = 512;

    public PreviewTexture(Identifier id, NativeImage image) {
        super(id::toString, image);
        this.sampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST, false);
    }

    /**
     * Verkleinert ein Bild auf SIZE x SIZE. Jeder neue Pixel ist der Mittelwert
     * des Blocks, aus dem er entsteht - das bleibt scharf, ohne zu flimmern.
     */
    public static NativeImage shrink(NativeImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= SIZE && h <= SIZE) {
            return source;
        }
        NativeImage out = new NativeImage(SIZE, SIZE, false);
        for (int y = 0; y < SIZE; y++) {
            int y0 = y * h / SIZE;
            int y1 = Math.max(y0 + 1, (y + 1) * h / SIZE);
            for (int x = 0; x < SIZE; x++) {
                int x0 = x * w / SIZE;
                int x1 = Math.max(x0 + 1, (x + 1) * w / SIZE);
                long a = 0, r = 0, g = 0, b = 0;
                int n = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int c = source.getColorArgb(sx, sy);
                        a += c >>> 24;
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                        n++;
                    }
                }
                out.setColorArgb(x, y, (int) ((a / n) << 24 | (r / n) << 16 | (g / n) << 8 | (b / n)));
            }
        }
        source.close();
        return out;
    }
}

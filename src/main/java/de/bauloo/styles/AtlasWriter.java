package de.bauloo.styles;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;

/** Overwrites one sprite in an atlas on the GPU, with its border and every mipmap level. */
final class AtlasWriter {

    private AtlasWriter() {
    }

    /** The atlas behind a texture id like textures/atlas/blocks.png (the manager keys by definition id). */
    static SpriteAtlasTexture atlas(Identifier textureId) {
        SpriteAtlasTexture[] found = new SpriteAtlasTexture[1];
        net.minecraft.client.MinecraftClient.getInstance().getAtlasManager().acceptAtlasTextures((id, atlas) -> {
            if (atlas.getId().equals(textureId) || id.equals(textureId)) {
                found[0] = atlas;
            }
        });
        if (found[0] == null) {
            BaulooStylesClient.LOGGER.warn("No atlas found for {}", textureId);
        }
        return found[0];
    }

    /** Render thread. Skips sprites whose size differs (animated or other resolution). */
    static void write(SpriteAtlasTexture atlas, Identifier spriteId, BufferedImage image) {
        Sprite sprite = atlas.getSprite(spriteId);
        if (sprite == null || sprite == atlas.getMissingSprite()) {
            return;
        }
        int w = sprite.getContents().getWidth();
        int h = sprite.getContents().getHeight();
        if (image.getWidth() != w || image.getHeight() != h) {
            image = scaled(image, w, h);
        }
        GpuTexture texture = atlas.getGlTexture();
        if (texture == null || texture.isClosed()) {
            return;
        }
        // the sprite sits at x/y with a border of copied edge pixels around it
        int innerX = Math.round(sprite.getMinU() * texture.getWidth(0));
        int pad = Math.max(0, innerX - sprite.getX());
        int pw = w + 2 * pad;
        int ph = h + 2 * pad;
        NativeImage level = new NativeImage(pw, ph, false);
        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) {
                int sx = Math.min(w - 1, Math.max(0, x - pad));
                int sy = Math.min(h - 1, Math.max(0, y - pad));
                level.setColorArgb(x, y, image.getRGB(sx, sy));
            }
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (int mip = 0; mip < texture.getMipLevels(); mip++) {
            int lw = pw >> mip;
            int lh = ph >> mip;
            if (lw < 1 || lh < 1) {
                break;
            }
            if (mip > 0) {
                NativeImage smaller = half(level);
                level.close();
                level = smaller;
            }
            encoder.writeToTexture(texture, level, mip, 0, sprite.getX() >> mip, sprite.getY() >> mip, lw, lh, 0, 0);
        }
        level.close();
    }

    /** Nearest-neighbour resize, so pixel art stays sharp. */
    static BufferedImage scaled(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, src.getRGB(x * src.getWidth() / w, y * src.getHeight() / h));
            }
        }
        return out;
    }

    static NativeImage toNative(BufferedImage image) {
        NativeImage out = new NativeImage(image.getWidth(), image.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                out.setColorArgb(x, y, image.getRGB(x, y));
            }
        }
        return out;
    }

    /** Next mipmap level: the average of every 2x2 block. */
    static NativeImage half(NativeImage src) {
        int w = Math.max(1, src.getWidth() / 2);
        int h = Math.max(1, src.getHeight() / 2);
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = 0, r = 0, g = 0, b = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int c = src.getColorArgb(Math.min(src.getWidth() - 1, x * 2 + dx), Math.min(src.getHeight() - 1, y * 2 + dy));
                        a += c >>> 24;
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                    }
                }
                out.setColorArgb(x, y, ((a / 4) << 24) | ((r / 4) << 16) | ((g / 4) << 8) | (b / 4));
            }
        }
        return out;
    }
}

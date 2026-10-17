package de.bauloo.styles;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * An animated cape: a list of frames played on the outside of the cape. The texture is smooth
 * (linear filtering with mipmaps, so thin lines don't flicker when the cape is small on screen).
 * Frames are read in the background the first time the cape is worn, and can be released again.
 */
public final class AnimatedCape {

    /** Where the frames come from (jar resources, a folder, ...). */
    public interface FrameSource {
        int count();

        BufferedImage read(int index) throws IOException;
    }

    private static final int UNIT = 32;                 // texture pixels per vanilla cape pixel
    private static final int TEX_W = 64 * UNIT, TEX_H = 32 * UNIT;
    private static final int FACE_W = 10 * UNIT, FACE_H = 16 * UNIT;
    private static final int MIPS = 6;                  // 2048 -> 64; the face offsets stay whole numbers

    private final Identifier id;
    private final FrameSource source;
    private final long frameMs;
    private final boolean tintable;
    private int tint = -1;
    private volatile NativeImage[] frames;
    private boolean loading;
    private Texture texture;
    private int shown = -1;

    public AnimatedCape(Identifier id, FrameSource source, int fps) {
        this(id, source, fps, false);
    }

    /** tintable: the cape takes the cosmetics colour when "Recolor cape" is on (hue swapped, light kept) */
    public AnimatedCape(Identifier id, FrameSource source, int fps, boolean tintable) {
        this.id = id;
        this.source = source;
        this.frameMs = 1000L / Math.max(1, fps);
        this.tintable = tintable;
    }

    private static NativeImage recolored(NativeImage src, int rgb) {
        float[] target = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        float[] hsb = new float[3];
        NativeImage out = new NativeImage(src.getWidth(), src.getHeight(), false);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int c = src.getColorArgb(x, y);
                java.awt.Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, hsb);
                // only coloured pixels change; greys and whites stay as they are
                float s = hsb[1] < 0.08F ? hsb[1] : Math.min(1F, hsb[1] * 0.4F + target[1] * 0.6F);
                out.setColorArgb(x, y, (c & 0xFF000000) | (java.awt.Color.HSBtoRGB(hsb[1] < 0.08F ? hsb[0] : target[0], s, hsb[2]) & 0xFFFFFF));
            }
        }
        return out;
    }

    /** The texture to draw right now, or null while the frames are still loading. Render thread only. */
    public Identifier current() {
        NativeImage[] ready = frames;
        if (ready == null) {
            startLoading();
            return null;
        }
        if (texture == null) {
            texture = new Texture(id);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            texture.writeStatic(ready[0]);
        }
        int want = tintable && StylesUiConfig.cosCapeTint ? StylesUiConfig.cosColor & 0xFFFFFF : -1;
        if (want != tint) {
            tint = want;
            shown = -1;
        }
        int frame = (int) ((System.currentTimeMillis() / frameMs) % ready.length);
        if (frame != shown) {
            shown = frame;
            if (tint < 0) {
                texture.writeRegion(ready[frame], UNIT, UNIT);
            } else {
                NativeImage tinted = recolored(ready[frame], tint);
                texture.writeRegion(tinted, UNIT, UNIT);
                tinted.close();
            }
        }
        return id;
    }

    /** Frees the frames and the texture (render thread); the next current() loads them again. */
    public synchronized void release() {
        NativeImage[] old = frames;
        frames = null;
        loading = false;
        shown = -1;
        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
            texture = null;
        }
        if (old != null) {
            for (NativeImage n : old) {
                if (n != null) {
                    n.close();
                }
            }
        }
    }

    private synchronized void startLoading() {
        if (loading) {
            return;
        }
        loading = true;
        Thread t = new Thread(() -> {
            try {
                int count = source.count();
                if (count <= 0) {
                    return;
                }
                NativeImage[] out = new NativeImage[count];
                for (int i = 0; i < count; i++) {
                    BufferedImage img = source.read(i);
                    if (img == null) {
                        BaulooStylesClient.LOGGER.warn("Animated cape {}: frame {} missing", id, i);
                        return;
                    }
                    NativeImage n = new NativeImage(FACE_W, FACE_H, false);
                    for (int y = 0; y < FACE_H; y++) {
                        for (int x = 0; x < FACE_W; x++) {
                            n.setColorArgb(x, y, 0xFF000000 | img.getRGB(x * img.getWidth() / FACE_W, y * img.getHeight() / FACE_H));
                        }
                    }
                    out[i] = n;
                }
                frames = out;
            } catch (Exception e) {
                BaulooStylesClient.LOGGER.warn("Animated cape {} could not load: {}", id, e.toString());
            }
        }, "Bauloo Animated Cape");
        t.setDaemon(true);
        t.start();
    }

    /** A cape-sized GPU texture with its own mipmaps and smooth sampling. */
    private static final class Texture extends AbstractTexture {

        Texture(Identifier id) {
            GpuDevice device = RenderSystem.getDevice();
            this.glTexture = device.createTexture(id::toString, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, TEX_W, TEX_H, 1, MIPS);
            this.glTextureView = device.createTextureView(this.glTexture);
            this.sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, true);
        }

        /** Everything that doesn't move: dark edges, the darker inside, the elytra part. Once. */
        void writeStatic(NativeImage face) {
            NativeImage all = new NativeImage(TEX_W, TEX_H, true);
            int edge = 0xFF101014;
            for (int y = 0; y < TEX_H; y++) {
                for (int x = 0; x < TEX_W; x++) {
                    all.setColorArgb(x, y, edge);
                }
            }
            // inside of the cape (12,1): a darker still frame
            for (int y = 0; y < FACE_H; y++) {
                for (int x = 0; x < FACE_W; x++) {
                    int c = face.getColorArgb(x, y);
                    all.setColorArgb(12 * UNIT + x, UNIT + y, 0xFF000000
                            | (((c >> 16) & 0xFF) * 5 / 10) << 16 | (((c >> 8) & 0xFF) * 5 / 10) << 8 | (c & 0xFF) * 5 / 10);
                }
            }
            // elytra part (22,0) 24x22: the frame stretched over it
            int ew = 24 * UNIT, eh = 22 * UNIT;
            for (int y = 0; y < eh; y++) {
                for (int x = 0; x < ew; x++) {
                    all.setColorArgb(22 * UNIT + x, y, face.getColorArgb(x * FACE_W / ew, y * FACE_H / eh));
                }
            }
            writeRegion(all, 0, 0);
            all.close();
        }

        /** Writes an image and its mipmaps at (x, y) of the texture. */
        void writeRegion(NativeImage image, int x, int y) {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            NativeImage level = image;
            for (int mip = 0; mip < MIPS; mip++) {
                if (mip > 0) {
                    NativeImage smaller = AtlasWriter.half(level);
                    if (level != image) {
                        level.close();
                    }
                    level = smaller;
                }
                encoder.writeToTexture(this.glTexture, level, mip, 0, x >> mip, y >> mip, level.getWidth(), level.getHeight(), 0, 0);
            }
            if (level != image) {
                level.close();
            }
        }
    }
}

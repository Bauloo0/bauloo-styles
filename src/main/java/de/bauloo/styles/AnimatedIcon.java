package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;

/** A small (80x128) animated picture of a cape for the menu card; frames are read in the background. */
public final class AnimatedIcon {

    private static final int W = 80, H = 128;

    private final Identifier id;
    private final AnimatedCape.FrameSource source;
    private final long frameMs;
    private volatile NativeImage[] frames;
    private boolean loading;
    private NativeImageBackedTexture texture;
    private int shown = -1;

    public AnimatedIcon(Identifier id, AnimatedCape.FrameSource source, int fps) {
        this.id = id;
        this.source = source;
        this.frameMs = 1000L / Math.max(1, fps);
    }

    /** The texture for this moment, or null until the frames are ready. Render thread only. */
    public Identifier current() {
        NativeImage[] ready = frames;
        if (ready == null) {
            startLoading();
            return null;
        }
        if (texture == null) {
            texture = new NativeImageBackedTexture(id::toString, W, H, false);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        }
        int frame = (int) ((System.currentTimeMillis() / frameMs) % ready.length);
        if (frame != shown && texture.getImage() != null) {
            shown = frame;
            texture.getImage().copyFrom(ready[frame]);
            texture.upload();
        }
        return id;
    }

    private synchronized void startLoading() {
        if (loading) {
            return;
        }
        loading = true;
        Thread t = new Thread(() -> {
            try {
                int count = source.count();
                NativeImage[] out = new NativeImage[count];
                for (int i = 0; i < count; i++) {
                    BufferedImage img = source.read(i);
                    if (img == null) {
                        return;
                    }
                    NativeImage n = new NativeImage(W, H, false);
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            // average a small block so the little picture stays smooth
                            int x0 = x * img.getWidth() / W, x1 = Math.max(x0 + 1, (x + 1) * img.getWidth() / W);
                            int y0 = y * img.getHeight() / H, y1 = Math.max(y0 + 1, (y + 1) * img.getHeight() / H);
                            int r = 0, g = 0, b = 0, c = 0;
                            for (int yy = y0; yy < y1; yy += 2) {
                                for (int xx = x0; xx < x1; xx += 2) {
                                    int p = img.getRGB(xx, yy);
                                    r += (p >> 16) & 0xFF;
                                    g += (p >> 8) & 0xFF;
                                    b += p & 0xFF;
                                    c++;
                                }
                            }
                            n.setColorArgb(x, y, 0xFF000000 | (r / c) << 16 | (g / c) << 8 | (b / c));
                        }
                    }
                    out[i] = n;
                }
                frames = out;
            } catch (Exception e) {
                BaulooStylesClient.LOGGER.warn("Animated icon {} could not load: {}", id, e.toString());
            }
        }, "Bauloo Cape Icon");
        t.setDaemon(true);
        t.start();
    }
}

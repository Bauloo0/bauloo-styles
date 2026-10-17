package de.bauloo.styles;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import de.bauloo.styles.mixin.SpriteAnimatorAccessor;
import de.bauloo.styles.mixin.SpriteContentsAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Recolours atlas sprites live on the GPU (fire, shield). The sprite's own pixels stay
 * untouched on the CPU side, so switching the colour off just writes them back.
 * Animated sprites (fire) keep one texture per frame; those are recoloured too.
 */
public final class LiveRecolor {

    /** colour value for apply(): a bright grey texture, the colour comes from the tint when drawing */
    public static final int GRAY = -2;
    /** colour value for apply(): plain grey at the same brightness, a bit darker (Monochrome) */
    public static final int MONO = -3;

    /** filled by the mixin when an atlas builds the animation of a sprite */
    public static final Map<SpriteContents, SpriteContents.Animator> ANIMATORS = new WeakHashMap<>();

    private LiveRecolor() {
    }

    /** Render thread. color < 0 = the pack's own pixels. */
    public static void apply(SpriteIdentifier id, int color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SpriteAtlasTexture atlas = AtlasWriter.atlas(id.getAtlasId());
        if (atlas == null) {
            return;
        }
        Sprite sprite = atlas.getSprite(id.getTextureId());
        if (sprite == null || sprite == atlas.getMissingSprite()) {
            BaulooStylesClient.LOGGER.info("[LiveRecolor] {} missing in {}", id.getTextureId(), id.getAtlasId());
            return;
        }
        SpriteContents contents = sprite.getContents();
        NativeImage source = ((SpriteContentsAccessor) contents).baulooStyles$image();
        int w = contents.getWidth();
        int h = contents.getHeight();
        if (source == null || source.getWidth() < w || source.getHeight() < h) {
            BaulooStylesClient.LOGGER.info("[LiveRecolor] {} has no pixels", id.getTextureId());
            return;
        }
        SpriteContents.Animator animator = ANIMATORS.get(contents);
        if (animator == null) {
            AtlasWriter.write(atlas, id.getTextureId(), frame(source, 0, 0, w, h, color));
            return;
        }
        int columns = Math.max(1, source.getWidth() / w);
        Int2ObjectMap<GpuTextureView> frames = ((SpriteAnimatorAccessor) animator).baulooStyles$frames();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (Int2ObjectMap.Entry<GpuTextureView> entry : frames.int2ObjectEntrySet()) {
            int index = entry.getIntKey();
            int fx = (index % columns) * w;
            int fy = (index / columns) * h;
            if (fy + h > source.getHeight()) {
                continue;
            }
            GpuTexture texture = entry.getValue().texture();
            if (texture.isClosed()) {
                continue;
            }
            NativeImage level = AtlasWriter.toNative(frame(source, fx, fy, w, h, color));
            for (int mip = 0; mip < texture.getMipLevels(); mip++) {
                if ((w >> mip) < 1 || (h >> mip) < 1) {
                    break;
                }
                if (mip > 0) {
                    NativeImage smaller = AtlasWriter.half(level);
                    level.close();
                    level = smaller;
                }
                encoder.writeToTexture(texture, level, mip, 0, 0, 0, w >> mip, h >> mip, 0, 0);
            }
            level.close();
        }
    }

    /**
     * Writes a finished image into a sprite, frame by frame when the sprite is animated.
     * The image must have the sprite's own layout (one frame, or the whole film strip).
     */
    public static void applyImage(SpriteAtlasTexture atlas, Identifier spriteId, BufferedImage image) {
        Sprite sprite = atlas.getSprite(spriteId);
        if (sprite == null || sprite == atlas.getMissingSprite()) {
            return;
        }
        SpriteContents contents = sprite.getContents();
        int w = contents.getWidth();
        int h = contents.getHeight();
        SpriteContents.Animator animator = ANIMATORS.get(contents);
        if (animator == null) {
            AtlasWriter.write(atlas, spriteId, image);
            return;
        }
        int columns = Math.max(1, image.getWidth() / Math.max(1, w));
        Int2ObjectMap<GpuTextureView> frames = ((SpriteAnimatorAccessor) animator).baulooStyles$frames();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (Int2ObjectMap.Entry<GpuTextureView> entry : frames.int2ObjectEntrySet()) {
            int index = entry.getIntKey();
            int fx = (index % columns) * w;
            int fy = (index / columns) * h;
            if (fx + w > image.getWidth() || fy + h > image.getHeight()) {
                continue;
            }
            GpuTexture texture = entry.getValue().texture();
            if (texture.isClosed()) {
                continue;
            }
            NativeImage level = AtlasWriter.toNative(image.getSubimage(fx, fy, w, h));
            for (int mip = 0; mip < texture.getMipLevels(); mip++) {
                if ((w >> mip) < 1 || (h >> mip) < 1) {
                    break;
                }
                if (mip > 0) {
                    NativeImage smaller = AtlasWriter.half(level);
                    level.close();
                    level = smaller;
                }
                encoder.writeToTexture(texture, level, mip, 0, 0, 0, w >> mip, h >> mip, 0, 0);
            }
            level.close();
        }
    }

    /** One frame of the sprite, recoloured: keeps the brightness, takes our hue and saturation. */
    private static BufferedImage frame(NativeImage source, int fx, int fy, int w, int h, int color) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float[] target = color < 0 ? null
                : java.awt.Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = source.getColorArgb(fx + x, fy + y);
                if (color == MONO && (c >>> 24) > 0) {
                    float lum = 0.299F * ((c >> 16) & 0xFF) + 0.587F * ((c >> 8) & 0xFF) + 0.114F * (c & 0xFF);
                    int v = Math.round(lum * 0.87F);
                    c = (c & 0xFF000000) | (v << 16) | (v << 8) | v;
                } else if (color == GRAY && (c >>> 24) > 0) {
                    float lum = (0.3F * ((c >> 16) & 0xFF) + 0.59F * ((c >> 8) & 0xFF) + 0.11F * (c & 0xFF)) / 255.0F;
                    int v = Math.round(Math.min(1.0F, 0.35F + lum * 1.3F) * 255);
                    c = (c & 0xFF000000) | (v << 16) | (v << 8) | v;
                } else if (target != null && (c >>> 24) > 0) {
                    float[] hsb = java.awt.Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
                    int rgb = java.awt.Color.HSBtoRGB(target[0], target[1] * (0.35F + 0.65F * hsb[1]),
                            Math.max(hsb[2], target[2] * 0.3F));
                    c = (c & 0xFF000000) | (rgb & 0xFFFFFF);
                }
                out.setRGB(x, y, c);
            }
        }
        return out;
    }
}

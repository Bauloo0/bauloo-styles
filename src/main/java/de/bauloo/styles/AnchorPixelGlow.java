package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Anchor "Glowing pixels": the anchor models (in the mod) have a second, full-bright layer.
 * Its textures start empty; this fills them with just the bright, coloured pixels of the
 * active anchor style, so those pixels glow in the dark. Off = the layer stays empty.
 * Everything is written straight into the block atlas, no reload.
 */
public final class AnchorPixelGlow {

    private static boolean applied;

    private AnchorPixelGlow() {
    }

    /** Render thread. */
    public static void applyLive() {
        StylesUiConfig.load();
        if (!StylesUiConfig.anchorPixelGlow && !applied) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            SpriteAtlasTexture atlas = AtlasWriter.atlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            if (atlas == null) {
                return;
            }
            boolean on = StylesUiConfig.anchorPixelGlow;
            BufferedImage empty = AnchorGlow.read(mc, 0);
            BufferedImage full = AnchorGlow.read(mc, 4);
            for (int level = 0; level <= 4; level++) {
                BufferedImage side = level > 0 && StylesUiConfig.anchorShowFull && full != null ? full : AnchorGlow.read(mc, level);
                if (side != null && level > 0 && StylesUiConfig.anchorGlowCustom && empty != null) {
                    side = AnchorGlow.recolor(empty, side, StylesUiConfig.anchorGlowColor);
                }
                write(atlas, "anchor_glow_side" + level, side, on);
            }
            write(atlas, "anchor_glow_bottom", read(mc, "respawn_anchor_bottom"), on);
            write(atlas, "anchor_glow_top_off", read(mc, "respawn_anchor_top_off"), on);
            applied = on;
        } catch (IOException | RuntimeException e) {
            BaulooStylesClient.LOGGER.warn("Anchor pixel glow could not be applied: {}", e.toString());
        }
    }

    private static void write(SpriteAtlasTexture atlas, String sprite, BufferedImage source, boolean on) {
        BufferedImage out = new BufferedImage(source == null ? 16 : source.getWidth(),
                source == null ? 16 : source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        if (on && source != null) {
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int c = source.getRGB(x, y);
                    if (glows(c)) {
                        out.setRGB(x, y, c | 0xFF000000);
                    }
                }
            }
        }
        LiveRecolor.applyImage(atlas, Identifier.of(BaulooStylesClient.MOD_ID, "block/" + sprite), out);
    }

    /** The small bright, coloured pixels (crying obsidian specks, charge dots) - not the dark stone. */
    static boolean glows(int argb) {
        if ((argb >>> 24) < 128) {
            return false;
        }
        float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        return (hsb[1] > 0.35F && hsb[2] > 0.38F) || hsb[2] > 0.8F;
    }

    private static BufferedImage read(MinecraftClient mc, String name) throws IOException {
        List<Resource> all = mc.getResourceManager().getAllResources(Identifier.ofVanilla("textures/block/" + name + ".png"));
        for (int i = all.size() - 1; i >= 0; i--) {
            Resource resource = all.get(i);
            if (resource.getPackId().contains(AnchorGlow.FOLDER)) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                return ImageIO.read(in);
            }
        }
        return null;
    }
}

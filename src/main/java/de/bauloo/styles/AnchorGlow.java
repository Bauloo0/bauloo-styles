package de.bauloo.styles;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Anchor Charge Color: the glowing charge dots on the respawn anchor in your own colour.
 * It works with any anchor style: the charged side textures (side1-4) are compared with
 * the empty one (side0); only the pixels that differ are the charge glow, and just those
 * are recoloured and written straight into the block atlas (after every reload again).
 * "Look full": charges 1-3 already look like 4 charges, like the Manos anchor.
 */
public final class AnchorGlow {

    public static final String FOLDER = "bauloo_anchor_glow";
    public static final String PACK_ID = "file/" + FOLDER;
    private static final String TEX = "textures/block/respawn_anchor_side";

    /** the atlas holds our colours right now */
    private static boolean applied;
    private static Identifier previewId;
    private static long previewStamp = -1;
    /** width and height of the preview texture (packs can be 16x, 32x ...) */
    public static int previewW = 16, previewH = 16;

    private AnchorGlow() {
    }

    private static Path packDir() {
        return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(FOLDER);
    }

    private static Path previewFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles/anchor_glow_preview.png");
    }

    /** Writes the charge colour straight into the block atlas - no resource reload, no loading screen. */
    public static void apply() {
        applyLive();
        AnchorPixelGlow.applyLive();
    }

    /** Render thread. Also puts the normal charge back once it was changed and is now off. */
    public static void applyLive() {
        StylesUiConfig.load();
        if (!active() && !applied) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            SpriteAtlasTexture atlas = AtlasWriter.atlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            if (atlas == null) {
                return;
            }
            BufferedImage empty = read(mc, 0);
            if (empty == null) {
                return;
            }
            BufferedImage last = null;
            BufferedImage full = read(mc, 4);
            for (int level = 1; level <= 4; level++) {
                BufferedImage charged = StylesUiConfig.anchorShowFull && full != null ? full : read(mc, level);
                if (charged == null) {
                    continue;
                }
                BufferedImage out = StylesUiConfig.anchorGlowCustom
                        ? recolor(empty, charged, StylesUiConfig.anchorGlowColor) : charged;
                LiveRecolor.applyImage(atlas, Identifier.ofVanilla("block/respawn_anchor_side" + level), out);
                last = out;
            }
            applied = active();
            if (last != null && active()) {
                Files.createDirectories(previewFile().getParent());
                ImageIO.write(last, "png", previewFile().toFile());
            }
        } catch (IOException | RuntimeException e) {
            BaulooStylesClient.LOGGER.warn("Anchor glow could not be applied: {}", e.toString());
        }
    }

    /** Old versions wrote a resource pack; it is only dropped now. */
    static void prepare(MinecraftClient mc, List<String> enabled) {
        enabled.remove(PACK_ID);
    }

    public static boolean needsRebuild() {
        return active();
    }

    private static boolean active() {
        return StylesUiConfig.anchorGlowCustom || StylesUiConfig.anchorShowFull;
    }

    /** The texture as the other packs provide it, ignoring our own glow pack. */
    static BufferedImage read(MinecraftClient mc, int level) throws IOException {
        List<Resource> all = mc.getResourceManager().getAllResources(Identifier.ofVanilla(TEX + level + ".png"));
        for (int i = all.size() - 1; i >= 0; i--) {
            Resource resource = all.get(i);
            if (resource.getPackId().contains(FOLDER)) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                return ImageIO.read(in);
            }
        }
        return null;
    }

    /**
     * The charge glow takes our hue and keeps its brightness.
     *
     * The glow is what differs from the empty anchor AND hangs together with a bright spot of
     * it. Some packs also change loose specks between empty and charged (Bauloo Private's purple
     * dots); those are not the glow and keep their own colour. Animated textures (frames stacked
     * downwards) are looked at frame by frame.
     */
    static BufferedImage recolor(BufferedImage empty, BufferedImage charged, int color) {
        int w = charged.getWidth();
        int h = charged.getHeight();
        int frame = Math.min(w, h);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float[] target = java.awt.Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        int ew = Math.max(1, empty.getWidth());
        int eh = Math.max(1, Math.min(empty.getWidth(), empty.getHeight()));
        boolean[] anders = new boolean[w * h];
        boolean[] glow = new boolean[w * h];
        java.util.ArrayDeque<Integer> offen = new java.util.ArrayDeque<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = charged.getRGB(x, y);
                int e = empty.getRGB(x * ew / w, (y % frame) * eh / frame);
                if (!differs(c, e)) {
                    continue;
                }
                anders[y * w + x] = true;
                float[] hsb = java.awt.Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
                if (hsb[2] >= 0.7F && (c >>> 24) >= 128) {
                    glow[y * w + x] = true;
                    offen.add(y * w + x);
                }
            }
        }
        // from the bright spots outwards, through changed pixels only, never across a frame edge
        while (!offen.isEmpty()) {
            int i = offen.poll();
            int x = i % w;
            int y = i / w;
            int[][] nachbarn = {{x - 1, y}, {x + 1, y}, {x, y - 1}, {x, y + 1}};
            for (int[] n : nachbarn) {
                if (n[0] < 0 || n[0] >= w || n[1] < 0 || n[1] >= h || n[1] / frame != y / frame) {
                    continue;
                }
                int j = n[1] * w + n[0];
                if (anders[j] && !glow[j]) {
                    glow[j] = true;
                    offen.add(j);
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = charged.getRGB(x, y);
                if (glow[y * w + x]) {
                    float[] hsb = java.awt.Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
                    // keep each pixel's own brightness, so the glow keeps its shape (dark rim, bright
                    // core); only the hue changes. The brightest core stays a little paler, like a light.
                    float sat = target[1] * Math.min(1F, 0.35F + (1F - hsb[2]) * 1.3F);
                    int rgb = java.awt.Color.HSBtoRGB(target[0], sat, hsb[2]);
                    out.setRGB(x, y, (c & 0xFF000000) | (rgb & 0xFFFFFF));
                } else {
                    out.setRGB(x, y, c);
                }
            }
        }
        return out;
    }

    private static boolean differs(int a, int b) {
        int d = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF))
                + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                + Math.abs((a & 0xFF) - (b & 0xFF));
        return d > 30;
    }

    /** The fully charged side for the Extras preview; null until a colour was applied once. */
    public static Identifier preview() {
        Path file = previewFile();
        try {
            if (!Files.exists(file)) {
                return null;
            }
            long stamp = Files.getLastModifiedTime(file).toMillis();
            if (stamp != previewStamp) {
                Identifier id = Identifier.of(BaulooStylesClient.MOD_ID, "anchor_glow/" + Long.toHexString(stamp));
                NativeImage image = NativeImage.read(Files.readAllBytes(file));
                previewW = image.getWidth();
                previewH = image.getHeight();
                MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                        new NativeImageBackedTexture(id::toString, image));
                previewId = id;
                previewStamp = stamp;
            }
            return previewId;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}

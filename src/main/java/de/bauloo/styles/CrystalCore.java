package de.bauloo.styles;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Crystal Core Color: recolours only the core (the inner cube) of the end crystal
 * texture of whatever crystal style is active. In the 64x32 layout the core sits at
 * u 32-64, v 0-16. The texture is swapped in the texture manager, no reload.
 */
public final class CrystalCore {

    public static final String FOLDER = "bauloo_crystal_core";
    public static final String PACK_ID = "file/" + FOLDER;
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/end_crystal/end_crystal.png");

    private static boolean swapped;

    private CrystalCore() {
    }

    private static Path packDir() {
        return BaulooStylesClient.stylePacksDir().resolve(FOLDER);
    }

    public static boolean needsRebuild() {
        StylesUiConfig.load();
        return StylesUiConfig.coreCustom;
    }

    /** Swaps the crystal texture directly - no resource reload, no loading screen. */
    public static void apply() {
        applyLive();
    }

    /** Render thread. Puts the pack texture back once the colour is turned off. */
    public static void applyLive() {
        StylesUiConfig.load();
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            if (StylesUiConfig.coreCustom) {
                BufferedImage source = read(mc);
                if (source == null) {
                    return;
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(recolor(source, StylesUiConfig.coreColor), "png", bytes);
                mc.getTextureManager().registerTexture(TEXTURE,
                        new NativeImageBackedTexture(TEXTURE::toString, NativeImage.read(bytes.toByteArray())));
                swapped = true;
            } else if (swapped) {
                // Die Textur des Packs selbst wieder einsetzen. Nur destroyTexture wuerde
                // ein leeres Bild hinterlassen, und das Zeichnen stuerzt dann ab.
                BufferedImage source = read(mc);
                if (source == null) {
                    return;
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(source, "png", bytes);
                mc.getTextureManager().registerTexture(TEXTURE,
                        new NativeImageBackedTexture(TEXTURE::toString, NativeImage.read(bytes.toByteArray())));
                swapped = false;
            }
        } catch (IOException | RuntimeException e) {
            BaulooStylesClient.LOGGER.warn("Crystal core could not be applied: {}", e.toString());
        }
    }

    /** Old versions wrote a resource pack; it is only dropped now. */
    static void prepare(MinecraftClient mc, List<String> enabled) {
        enabled.remove(PACK_ID);
    }

    /** The crystal texture as the other packs provide it, ignoring our own core pack. */
    private static BufferedImage read(MinecraftClient mc) throws IOException {
        List<Resource> all = mc.getResourceManager().getAllResources(TEXTURE);
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

    /** Core pixels keep their brightness and take our hue and saturation. */
    private static BufferedImage recolor(BufferedImage src, int color) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float[] target = java.awt.Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        int x0 = w / 2;
        int y1 = h / 2;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = src.getRGB(x, y);
                if (x >= x0 && y < y1 && (c >>> 24) > 0) {
                    float[] hsb = java.awt.Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
                    int rgb = java.awt.Color.HSBtoRGB(target[0], target[1], Math.max(hsb[2], target[2] * 0.35F));
                    out.setRGB(x, y, (c & 0xFF000000) | (rgb & 0xFFFFFF));
                } else {
                    out.setRGB(x, y, c);
                }
            }
        }
        return out;
    }
}

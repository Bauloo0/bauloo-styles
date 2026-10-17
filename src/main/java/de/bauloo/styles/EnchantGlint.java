package de.bauloo.styles;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

/**
 * Enchant Glint Color: a white glint (bundled, from the "White Enchantment Glint" pack)
 * tinted in one colour, a fade between two colours, or a rainbow across the texture.
 * The glint textures are swapped directly in the texture manager - no resource reload,
 * so no loading screen. After any other reload the swap is done again.
 */
public final class EnchantGlint {

    /** Old versions wrote a resource pack; it is only removed now. */
    public static final String PACK_ID = "file/bauloo_glint";
    private static final Identifier[] TEXTURES = {
            Identifier.ofVanilla("textures/misc/enchanted_glint_item.png"),
            Identifier.ofVanilla("textures/misc/enchanted_glint_armor.png"),
    };

    private static boolean swapped;

    private EnchantGlint() {
    }

    /** The glint texture: repeating and smooth, like the vanilla one. */
    private static final class GlintTexture extends NativeImageBackedTexture {
        GlintTexture(Identifier id, NativeImage image) {
            super(id::toString, image);
            this.sampler = RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR);
        }
    }

    /** Re-applies the colour after every resource reload (the reload brings the pack glint back). */
    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.of(BaulooStylesClient.MOD_ID, "enchant_glint");
                    }

                    @Override
                    public Collection<Identifier> getFabricDependencies() {
                        return List.of(ResourceReloadListenerKeys.TEXTURES);
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        swapped = false;
                        MinecraftClient.getInstance().send(EnchantGlint::applyLive);
                    }
                });
    }

    /** Only drops the pack older versions used. */
    static void prepare(MinecraftClient mc, List<String> enabled) {
        enabled.remove(PACK_ID);
    }

    /** Swaps the glint textures for the current settings, or puts the normal ones back. Render thread. */
    public static void applyLive() {
        StylesUiConfig.load();
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            if (StylesUiConfig.glintCustom) {
                byte[] png = tintedPng();
                if (png == null) {
                    return;
                }
                for (Identifier id : TEXTURES) {
                    mc.getTextureManager().registerTexture(id, new GlintTexture(id, NativeImage.read(png)));
                }
                swapped = true;
            } else if (swapped) {
                // wie beim Crystal Core: immer ein fertiges Bild einsetzen, nie nur loeschen
                byte[] plain = plainPng();
                if (plain == null) {
                    return;
                }
                for (Identifier id : TEXTURES) {
                    mc.getTextureManager().registerTexture(id, new GlintTexture(id, NativeImage.read(plain)));
                }
                swapped = false;
            }
        } catch (IOException | RuntimeException e) {
            BaulooStylesClient.LOGGER.warn("Enchant glint could not be applied: {}", e.toString());
        }
    }

    /** Der Glint, wie ihn das Pack liefert, ohne eigene Farbe. */
    private static byte[] plainPng() throws IOException {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (Identifier id : TEXTURES) {
            var resource = mc.getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().getInputStream()) {
                    return in.readAllBytes();
                }
            }
        }
        return null;
    }

    private static byte[] tintedPng() throws IOException {
        try (InputStream in = EnchantGlint.class.getResourceAsStream("/assets/bauloo_styles/glint/white_glint.png")) {
            if (in == null) {
                return null;
            }
            BufferedImage out = tint(ImageIO.read(in));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        }
    }

    /** The white glint's brightness times the colour at that spot. */
    private static BufferedImage tint(BufferedImage base) {
        int w = base.getWidth();
        int h = base.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = base.getRGB(x, y);
                float light = Math.max((c >> 16) & 0xFF, Math.max((c >> 8) & 0xFF, c & 0xFF)) / 255.0F;
                int color = colorAt(x, y, w, h);
                int r = Math.min(255, Math.round(((color >> 16) & 0xFF) * light * 1.15F));
                int g = Math.min(255, Math.round(((color >> 8) & 0xFF) * light * 1.15F));
                int b = Math.min(255, Math.round((color & 0xFF) * light * 1.15F));
                out.setRGB(x, y, (c & 0xFF000000) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    /** Colour for a spot: plain, a diagonal fade between two colours, or a diagonal rainbow. */
    public static int colorAt(int x, int y, int w, int h) {
        float t = (x + y) / (float) Math.max(1, w + h - 2);
        if (StylesUiConfig.glintRainbow) {
            return java.awt.Color.HSBtoRGB(t, 0.8F, 1.0F) & 0xFFFFFF;
        }
        if (StylesUiConfig.glintGradient) {
            // there and back, so the texture tiles without a hard edge
            float wave = t < 0.5F ? t * 2 : (1 - t) * 2;
            return StylesCrosshair.mix(0xFF000000 | StylesUiConfig.glintColor, 0xFF000000 | StylesUiConfig.glintColor2, wave)
                    & 0xFFFFFF;
        }
        return StylesUiConfig.glintColor & 0xFFFFFF;
    }
}

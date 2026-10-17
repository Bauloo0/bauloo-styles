package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.io.InputStream;

/**
 * Looks at the active end crystal texture after every resource reload: does it have half-clear
 * pixels (a see-through style)? Then CrystalSeeThroughMixin draws crystals translucent. Every reload
 * makes a new resource manager, so a new one means "look again" - no other class has to call this.
 */
public final class CrystalSeeThrough {

    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/end_crystal/end_crystal.png");
    private static final RenderLayer LAYER = RenderLayers.entityTranslucent(TEXTURE);
    private static volatile boolean active;
    private static Object checkedFor;

    private CrystalSeeThrough() {
    }

    /** Render thread. */
    public static boolean active() {
        Object manager = MinecraftClient.getInstance().getResourceManager();
        if (manager != checkedFor) {
            checkedFor = manager;
            check();
        }
        return active;
    }

    public static RenderLayer layer() {
        return LAYER;
    }

    private static void check() {
        boolean found = false;
        try {
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(TEXTURE);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().getInputStream(); NativeImage image = NativeImage.read(in)) {
                    int half = 0;
                    for (int y = 0; y < image.getHeight() && !found; y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            int a = (image.getColorArgb(x, y) >>> 24) & 0xFF;
                            // a few soft edge pixels do not make a see-through crystal
                            if (a > 8 && a < 247 && ++half > 16) {
                                found = true;
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            BaulooStylesClient.LOGGER.warn("See-through check failed: {}", e.toString());
        }
        if (found != active) {
            BaulooStylesClient.LOGGER.info("Crystal texture is {}", found ? "see-through: drawn translucent" : "solid");
        }
        active = found;
    }
}

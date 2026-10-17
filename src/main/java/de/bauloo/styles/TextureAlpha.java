package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Whether a texture of the active packs has clear holes in a part of it (a see-through shield, ...).
 * The answer is kept until the next resource reload, which makes a new resource manager.
 */
public final class TextureAlpha {

    private static final Map<String, Boolean> CACHE = new HashMap<>();
    private static Object cachedFor;

    private TextureAlpha() {
    }

    /**
     * True when more than a few pixels inside the box (parts of the texture, 0-1) are fully clear.
     * Render thread.
     */
    public static boolean hasHoles(Identifier texture, double x, double y, double w, double h) {
        Object manager = MinecraftClient.getInstance().getResourceManager();
        if (manager != cachedFor) {
            cachedFor = manager;
            CACHE.clear();
        }
        String key = texture + "|" + x + "|" + y + "|" + w + "|" + h;
        Boolean known = CACHE.get(key);
        if (known != null) {
            return known;
        }
        boolean holes = false;
        try {
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(texture);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().getInputStream(); NativeImage image = NativeImage.read(in)) {
                    int x0 = (int) (x * image.getWidth());
                    int y0 = (int) (y * image.getHeight());
                    int x1 = Math.min(image.getWidth(), (int) ((x + w) * image.getWidth()));
                    int y1 = Math.min(image.getHeight(), (int) ((y + h) * image.getHeight()));
                    int clear = 0;
                    for (int py = y0; py < y1 && !holes; py++) {
                        for (int px = x0; px < x1; px++) {
                            if (((image.getColorArgb(px, py) >>> 24) & 0xFF) < 16 && ++clear > 8) {
                                holes = true;
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            BaulooStylesClient.LOGGER.warn("Alpha check of {} failed: {}", texture, e.toString());
        }
        CACHE.put(key, holes);
        return holes;
    }

    /** The plain shield's front plate has clear parts (a see-through shield style). */
    public static boolean shieldHasHoles() {
        // the front plate in the shield texture: from (1, 1), 12 x 22 pixels of 64 x 64
        return hasHoles(Identifier.ofVanilla("textures/entity/shield_base_nopattern.png"),
                1 / 64.0, 1 / 64.0, 12 / 64.0, 22 / 64.0);
    }
}

package de.bauloo.styles;

import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The animated capes that ship with the mod: 36 frames each (320x512 JPGs under
 * textures/cosmetics/capes/<key>/) at 12 fps. Only the cape being worn keeps its frames in memory.
 */
public final class BuiltInCapes {

    /** { key, label, recolorable } in menu order */
    public static final String[][] LIST = {
            {"tornado_night", "Tornado Night", "yes"},
            {"crimson", "Crimson", "yes"},
            {"vortex", "Vortex", "yes"},
            {"daydream", "Daydream", "yes"},
            {"snake_blade", "Snake Blade", "no"},
            {"haunted_night", "Haunted Night", "no"},
            {"pumpkin_king", "Pumpkin King", "no"},
    };

    private static final int FRAMES = 36;
    private static final int FPS = 12;
    private static final Map<String, AnimatedCape> CAPES = new HashMap<>();
    private static final Map<String, AnimatedIcon> ICONS = new HashMap<>();
    private static String active;

    private BuiltInCapes() {
    }

    public static boolean has(String key) {
        for (String[] e : LIST) {
            if (e[0].equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static AnimatedCape.FrameSource frames(String key) {
        return new AnimatedCape.FrameSource() {
            @Override
            public int count() {
                return FRAMES;
            }

            @Override
            public BufferedImage read(int index) throws IOException {
                String path = String.format("/assets/bauloo_styles/textures/cosmetics/capes/%s/f%02d.jpg", key, index);
                try (InputStream in = BuiltInCapes.class.getResourceAsStream(path)) {
                    return in == null ? null : ImageIO.read(in);
                }
            }
        };
    }

    /** The texture for this moment, or null while it loads. Render thread only. */
    public static Identifier current(String key) {
        if (active != null && !active.equals(key)) {
            AnimatedCape old = CAPES.remove(active);
            if (old != null) {
                old.release();
            }
        }
        active = key;
        boolean tint = true;
        for (String[] e : LIST) {
            if (e[0].equals(key)) {
                tint = "yes".equals(e[2]);
            }
        }
        boolean tintable = tint;
        return CAPES.computeIfAbsent(key, k -> new AnimatedCape(
                Identifier.of(BaulooStylesClient.MOD_ID, "cosmetics/builtin/" + k), frames(k), FPS, tintable)).current();
    }

    /** The moving picture for the menu card, or null while it loads. */
    public static Identifier icon(String key) {
        return ICONS.computeIfAbsent(key, k -> new AnimatedIcon(
                Identifier.of(BaulooStylesClient.MOD_ID, "cosmetics/builtin_icon/" + k), frames(k), FPS)).current();
    }
}

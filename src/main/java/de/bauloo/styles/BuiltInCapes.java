package de.bauloo.styles;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The built-in animated capes: 36 frames each (320x512 JPGs) at 12 fps. The frames are not in the jar:
 * the first time a cape (or its menu card) is shown, capes/<key>.zip comes from the Styles cloud, is checked
 * against the SHA-256 below and unpacked to config/bauloo-styles/capes/<key>/. Only the cape being worn
 * keeps its frames in memory.
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

    /** key -> { SHA-256, size } of capes/<key>.zip in the cloud repo; a download that differs is thrown away */
    private static final Map<String, String[]> ZIPS = Map.of(
            "tornado_night", new String[]{"2a23961df066728029462013ec2682372156df59fae746f79d2f95de1663eacd", "379398"},
            "crimson", new String[]{"2d5c3e59880f24fee28f6a3f3311aa602a4f89e9f06bee5e3bd0843a715c4b72", "210876"},
            "vortex", new String[]{"0d37752971c627d5da7378767c7925ef434c83adbb160cec96c64a1399f929c5", "648954"},
            "daydream", new String[]{"d7c8f1211b9fe3736ca3bbbc558b3ed99556568bf1c8ba04f3f1ed777ac075c5", "275923"},
            "snake_blade", new String[]{"035478dd453d19489b029375a7a194523d569939d90b0255789e24aa2de73790", "662290"},
            "haunted_night", new String[]{"1bf229cfb873876172a6badb94e7ebdc4980e58af7666582f8bd50652a0024af", "650717"},
            "pumpkin_king", new String[]{"6a9cbc676cb7e31037a0540beccdadc10625e28e7ab4a637c9e7b06bb97dcb56", "756183"});
    private static final long RETRY_MS = 30_000L;
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();
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
                if (!downloaded(key)) {
                    return null;
                }
                Path file = folder(key).resolve(String.format("f%02d.jpg", index));
                return Files.exists(file) ? ImageIO.read(file.toFile()) : null;
            }
        };
    }

    private static Path folder(String key) {
        return FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles/capes").resolve(key);
    }

    /** The cape's frames are on disk (downloads them the first time). Loader threads only, never the render thread. */
    private static synchronized boolean downloaded(String key) {
        String[] zip = ZIPS.get(key);
        if (zip == null) {
            return false;
        }
        Path folder = folder(key);
        try {
            Path marker = folder.resolve("sha256");
            if (Files.exists(marker) && Files.readString(marker).trim().equals(zip[0])) {
                return true;
            }
        } catch (IOException ignored) {
        }
        Long failed = FAILED.get(key);
        if (failed != null && System.currentTimeMillis() - failed < RETRY_MS) {
            return false;
        }
        BaulooStylesClient.LOGGER.info("[Capes] downloading {}", key);
        if (StylesCloud.fetchCape(key, zip[0], Long.parseLong(zip[1]), folder)) {
            FAILED.remove(key);
            BaulooStylesClient.LOGGER.info("[Capes] {} ready", key);
            return true;
        }
        FAILED.put(key, System.currentTimeMillis());
        return false;
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

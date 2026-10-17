package de.bauloo.styles;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Animated capes that live only on this PC: every folder in config/bauloo-styles/animated-capes with frame
 * images (f00.jpg, f01.jpg, ... or .png, in name order) becomes a cape in Cosmetics. An optional
 * fps.txt sets the speed (default 12); a file named tint makes the cape follow the cosmetics colour.
 * Nothing here is uploaded or shared.
 */
public final class LocalCapes {

    public static final String PREFIX = "local_";
    private static final long RESCAN_MS = 3000;

    private record Entry(String key, String label, List<Path> frames, int fps, boolean tint) {
    }

    private static Map<String, Entry> entries = Map.of();
    private static String[][] list = new String[0][];
    private static long scanned;
    private static final Map<String, AnimatedCape> CAPES = new HashMap<>();
    private static final Map<String, AnimatedIcon> ICON_ANIMS = new HashMap<>();
    private static String active;

    private LocalCapes() {
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles").resolve("animated-capes");
    }

    /** { key, label } for every local cape, rescanned every few seconds. */
    public static String[][] list() {
        long now = System.currentTimeMillis();
        if (now - scanned > RESCAN_MS) {
            scanned = now;
            scan();
        }
        return list;
    }

    private static void scan() {
        Map<String, Entry> found = new HashMap<>();
        List<String[]> rows = new ArrayList<>();
        Path root = dir();
        if (Files.isDirectory(root)) {
            try (Stream<Path> folders = Files.list(root).sorted()) {
                for (Path folder : (Iterable<Path>) folders::iterator) {
                    if (!Files.isDirectory(folder)) {
                        continue;
                    }
                    List<Path> frames;
                    try (Stream<Path> files = Files.list(folder)) {
                        frames = files.filter(f -> {
                            String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                            return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png");
                        }).sorted().toList();
                    }
                    if (frames.isEmpty()) {
                        continue;
                    }
                    String label = folder.getFileName().toString();
                    String key = PREFIX + label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
                    int fps = 12;
                    Path fpsFile = folder.resolve("fps.txt");
                    if (Files.exists(fpsFile)) {
                        try {
                            fps = Math.max(1, Math.min(60, Integer.parseInt(Files.readString(fpsFile).trim())));
                        } catch (IOException | NumberFormatException ignored) {
                            // keep 12
                        }
                    }
                    boolean tint = Files.exists(folder.resolve("tint")) || Files.exists(folder.resolve("tint.txt"));
                    found.put(key, new Entry(key, label, frames, fps, tint));
                    rows.add(new String[]{key, label});
                }
            } catch (IOException e) {
                BaulooStylesClient.LOGGER.warn("Local capes: could not read {}: {}", root, e.toString());
            }
        }
        entries = found;
        list = rows.toArray(new String[0][]);
    }

    /** The animated texture for a local cape; only one stays in memory at a time. Render thread only. */
    public static Identifier current(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (active != null && !active.equals(key)) {
            AnimatedCape old = CAPES.remove(active);
            if (old != null) {
                old.release();
            }
        }
        active = key;
        AnimatedCape cape = CAPES.computeIfAbsent(key, k -> new AnimatedCape(
                Identifier.of(BaulooStylesClient.MOD_ID, "cosmetics/local/" + k.substring(PREFIX.length())),
                new AnimatedCape.FrameSource() {
                    @Override
                    public int count() {
                        return entry.frames().size();
                    }

                    @Override
                    public BufferedImage read(int index) throws IOException {
                        return ImageIO.read(entry.frames().get(index).toFile());
                    }
                }, entry.fps(), entry.tint()));
        return cape.current();
    }

    /** A small animated picture for the menu card (80x128). */
    public static Identifier icon(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        AnimatedIcon icon = ICON_ANIMS.computeIfAbsent(key, k -> new AnimatedIcon(
                Identifier.of(BaulooStylesClient.MOD_ID, "cosmetics/local_icon/" + k.substring(PREFIX.length())),
                new AnimatedCape.FrameSource() {
                    @Override
                    public int count() {
                        return entry.frames().size();
                    }

                    @Override
                    public BufferedImage read(int index) throws IOException {
                        return ImageIO.read(entry.frames().get(index).toFile());
                    }
                }, entry.fps()));
        return icon.current();
    }
}

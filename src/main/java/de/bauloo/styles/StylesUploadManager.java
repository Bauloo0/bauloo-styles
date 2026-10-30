package de.bauloo.styles;

import net.fabricmc.loader.api.FabricLoader;

import javax.swing.JOptionPane;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Local-only importer for packs the player owns. Nothing is uploaded. */
public final class StylesUploadManager {
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles/uploads");

    private StylesUploadManager() {}

    /** Opens the local folder; this is reliable even when Minecraft hides native dialogs. */
    public static void openFolder() {
        try {
            Files.createDirectories(ROOT);
        } catch (IOException ignored) {
        }
        // Minecraft's own "open folder" helper, the same one the resource pack screen uses
        net.minecraft.util.Util.getOperatingSystem().open(ROOT);
    }

    public static void open() {
        String[] actions = {"Add Files", "View My Uploads"};
        int action = JOptionPane.showOptionDialog(null, "Manage your local style uploads.", "Bauloo Styles - My Uploads",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]);
        if (action == 1) {
            StringBuilder list = new StringBuilder("Your local uploads:\n\n");
            for (String category : new String[]{"Crystal", "Anchor", "Armor", "Fire", "Sky"}) {
                List<Path> packs = list(category);
                list.append(category).append(" ( ").append(packs.size()).append(" )\n");
                for (Path pack : packs) list.append("  ").append(pack.getFileName()).append('\n');
            }
            JOptionPane.showMessageDialog(null, list.toString(), "Bauloo Styles - My Uploads", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (action != 0) return;
        FileDialog chooser = new FileDialog((Frame) null, "Bauloo Styles - Add Files", FileDialog.LOAD);
        chooser.setFilenameFilter((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".zip"));
        chooser.setMultipleMode(true);
        chooser.setAlwaysOnTop(true);
        chooser.setVisible(true);
        Path[] files = java.util.Arrays.stream(chooser.getFiles())
                .map(java.io.File::toPath).toArray(Path[]::new);
        if (files.length == 0) return;
        String[] categories = {"Crystal", "Anchor", "Armor", "Fire", "Sky"};
        String category = (String) JOptionPane.showInputDialog(null,
                "What do you want to copy?\nChoose the category for the selected files.",
                "Bauloo Styles - Import", JOptionPane.QUESTION_MESSAGE, null, categories, "Sky");
        if (category == null) return;
        String prefix = category.toLowerCase(Locale.ROOT) + "_";
        int copied = 0;
        try {
            Path target = ROOT.resolve(prefix.substring(0, prefix.length() - 1));
            Files.createDirectories(target);
            for (Path file : files) {
                Path destination = target.resolve(file.getFileName().toString());
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
            JOptionPane.showMessageDialog(null, copied + " file(s) copied to My Uploads / " + category + ".",
                    "Bauloo Styles", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not copy the selected files: " + e.getMessage(),
                    "Bauloo Styles", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static List<Path> list(String category) {
        Path folder = ROOT.resolve(category.toLowerCase(Locale.ROOT));
        try (var stream = Files.exists(folder) ? Files.list(folder) : java.util.stream.Stream.<Path>empty()) {
            return stream.filter(p -> Files.isDirectory(p) || p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")).sorted().toList();
        } catch (IOException e) { return List.of(); }
    }

    // ------------------------------------------------------------------------------------------------
    // Changes from the upload web page, and preview pictures next to the uploaded zips.

    private static volatile int version;
    private static final java.util.Map<String, Path> SOURCES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, net.minecraft.util.Identifier> PREVIEWS = new java.util.HashMap<>();

    public static Path root() {
        return ROOT;
    }

    /** Bumped when an upload is added or removed, so an open menu rebuilds. */
    public static int version() {
        return version;
    }

    public static void changed() {
        version++;
    }

    static String packIdFor(String category, Path file) {
        String safe = file.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        return "bauloo_upload_" + category.toLowerCase(Locale.ROOT) + "_"
                + safe.replaceFirst("\\.zip$", "")
                + (category.equalsIgnoreCase("sky") ? "_skyonly" : "")
                + (Files.isDirectory(file) ? "_folder" : "");
    }

    /** The picture uploaded together with a pack (same name, .png), loaded as a texture. Render thread only. */
    public static net.minecraft.util.Identifier preview(String id) {
        Path source = SOURCES.get(id);
        if (source == null) {
            return null;
        }
        Path png = source.resolveSibling(source.getFileName().toString().replaceFirst("\\.zip$", "") + ".png");
        try {
            if (!Files.exists(png)) {
                autoPreview(source, png);
                return null;
            }
            String key = id + "@" + Files.getLastModifiedTime(png).toMillis();
            net.minecraft.util.Identifier cached = PREVIEWS.get(key);
            if (cached != null) {
                return cached;
            }
            net.minecraft.util.Identifier texture = net.minecraft.util.Identifier.of(BaulooStylesClient.MOD_ID,
                    "upload/" + Integer.toHexString(key.hashCode()));
            net.minecraft.client.texture.NativeImage image = PreviewTexture.shrink(
                    net.minecraft.client.texture.NativeImage.read(Files.readAllBytes(png)));
            net.minecraft.client.MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(texture, new PreviewTexture(texture, image));
            PREVIEWS.put(key, texture);
            return texture;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static final java.util.Set<Path> AUTO_TRIED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Uploads without a picture (older ones, or packs dropped into the folder) get one drawn
     * from their own textures, once, in the background. The menu picks it up when it exists.
     */
    private static void autoPreview(Path source, Path png) {
        if (!AUTO_TRIED.add(source)) {
            return;
        }
        String category = source.getParent().getFileName().toString().toLowerCase(Locale.ROOT);
        Thread thread = new Thread(() -> {
            Path temp = null;
            try {
                Path zip = source;
                if (Files.isDirectory(source)) {
                    temp = Files.createTempFile("bauloo-preview-", ".zip");
                    zipFolder(source, temp);
                    zip = temp;
                }
                if ("sky".equals(category)) {
                    StylesUploadServer.skyPreview(zip, png);
                } else {
                    StylesAutoPreview.render(zip, category, png);
                }
            } catch (IOException | RuntimeException e) {
                BaulooStylesClient.LOGGER.warn("Auto preview for {} failed: {}", source.getFileName(), e.toString());
            } finally {
                if (temp != null) {
                    try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
                }
            }
        }, "Bauloo Styles preview");
        thread.setDaemon(true);
        thread.start();
    }

    private static void zipFolder(Path folder, Path zip) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip));
             var paths = Files.walk(folder)) {
            for (Path path : paths.toList()) {
                if (Files.isDirectory(path)) continue;
                out.putNextEntry(new ZipEntry(folder.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, out);
                out.closeEntry();
            }
        }
    }

    /** Removes an upload, its picture and the copy Minecraft loads. */
    public static boolean delete(String category, String fileName) {
        if (!fileName.matches("[^/\\\\]+\\.zip")) {
            return false;
        }
        Path folder = ROOT.resolve(category.toLowerCase(Locale.ROOT));
        Path zip = folder.resolve(fileName);
        if (!zip.normalize().startsWith(folder.normalize())) {
            return false;
        }
        try {
            String id = packIdFor(category, zip);
            boolean removed = Files.deleteIfExists(zip);
            Files.deleteIfExists(folder.resolve(fileName.replaceFirst("\\.zip$", "") + ".png"));
            try {
                Files.deleteIfExists(BaulooStylesClient.stylePacksDir().resolve(id + ".zip"));
            } catch (IOException ignored) {
                // still loaded by the game - it disappears with the next restart
            }
            changed();
            return removed;
        } catch (IOException e) {
            return false;
        }
    }

    /** Mirrors uploads into Minecraft's normal pack directory so ResourcePackManager can load them. */
    public static List<String[]> sync(String category) {
        List<String[]> result = new java.util.ArrayList<>();
        Path targetRoot = BaulooStylesClient.stylePacksDir();
        try {
            Files.createDirectories(targetRoot);
        } catch (IOException e) {
            return result;
        }
        for (Path file : list(category)) {
            String id = packIdFor(category, file);
            SOURCES.put(id, file);
            try {
                if (Files.isDirectory(file)) {
                    copyFolder(file, targetRoot.resolve(id), category);
                    result.add(new String[]{id, file.getFileName().toString()});
                    continue;
                }
                Path mirrored = targetRoot.resolve(id + ".zip");
                // Only when the upload is new or changed: the copy in use is locked on Windows,
                // and an 80 MB sky atlas should not be rewritten every time the tab opens.
                if (!Files.exists(mirrored)
                        || Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(mirrored)) > 0) {
                    normalizePack(file, mirrored, category);
                }
            } catch (IOException e) {
                BaulooStylesClient.LOGGER.warn("Upload {} could not be prepared: {}", file.getFileName(), e.toString());
                if (Files.isDirectory(file) || !Files.exists(targetRoot.resolve(id + ".zip"))) {
                    continue;
                }
                // the older copy is still there (probably in use): keep showing it
            }
            result.add(new String[]{id, file.getFileName().toString().replaceFirst("\\.zip$", "")});
        }
        return result;
    }

    /** Same as the cloud packs: loads on every Minecraft version the mod runs on. */
    private static final String PACK_META =
            "{\"pack\": {\"description\": \"Bauloo uploaded style - by bauloo\", \"pack_format\": 75, \"min_format\": 65, \"max_format\": 9999}}\n";

    private static void normalizePack(Path source, Path destination, String category) throws IOException {
        Path temp = Files.createTempFile("bauloo-upload-", ".zip");
        boolean metadata = false;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(source));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (category.equalsIgnoreCase("sky")
                        && !entry.getName().equalsIgnoreCase("pack.mcmeta")
                        && !entry.getName().startsWith("assets/minecraft/optifine/sky/")) continue;
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().equalsIgnoreCase("pack.mcmeta")) {
                    metadata = true;
                    out.write(PACK_META.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    in.transferTo(out);
                }
                out.closeEntry();
            }
            if (!metadata) {
                out.putNextEntry(new ZipEntry("pack.mcmeta"));
                out.write(PACK_META.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copyFolder(Path source, Path destination, String category) throws IOException {
        if (Files.exists(destination)) {
            try (var old = Files.walk(destination)) {
                old.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                if (Files.isDirectory(path)) continue;
                Path relative = source.relativize(path);
                String name = relative.toString().replace('\\', '/');
                if (category.equalsIgnoreCase("sky")
                        && !name.equalsIgnoreCase("pack.mcmeta")
                        && !name.startsWith("assets/minecraft/optifine/sky/")) continue;
                Path out = destination.resolve(relative);
                Files.createDirectories(out.getParent());
                Files.copy(path, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path meta = destination.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            Files.writeString(meta, PACK_META);
        }
    }
}

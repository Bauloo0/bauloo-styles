package de.bauloo.styles;

import de.bauloo.styles.BaulooStylesClient.Tab;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * EXPORT on a preset: all its styles merged into one normal texture pack in resourcepacks/, so it
 * works without the mod too. Only textures - Extras (colours, outlines ...) are the mod's own and
 * can't be put into a pack. Styles from Bauloo's Packs (VIP) are left out, or VIP keys would be
 * worth nothing.
 */
public final class PresetExport {

    private static final String UPLOAD = "bauloo_upload_";
    private static final String MYPACK = "mypack:";
    private static final String VIPPACK = "vippack:";

    public static volatile boolean busy;
    /** the resourcepacks folder after a successful export, so the menu can open it */
    public static volatile Path lastFolder;

    private PresetExport() {
    }

    /** Runs in the background; the future gives the line to show. */
    public static CompletableFuture<String> export(StylesPresets.Preset preset) {
        busy = true;
        return CompletableFuture.supplyAsync(() -> {
            try {
                return run(preset);
            } catch (Exception e) {
                BaulooStylesClient.LOGGER.warn("[PresetExport] {} failed: {}", preset.name(), e.toString());
                return "Export failed: " + e.getMessage();
            } finally {
                busy = false;
            }
        });
    }

    private static String run(StylesPresets.Preset preset) throws Exception {
        lastFolder = null;
        Path packs = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
        String name = preset.name().replaceAll("[^A-Za-z0-9 _()-]", "").trim();
        if (name.isEmpty()) {
            name = "Preset";
        }
        Path out = packs.resolve(name + " (Bauloo Styles).zip");
        Map<Tab, Path> uploads = PresetShare.localSources(preset);
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(BaulooStylesClient.MOD_ID);
        Set<String> written = new HashSet<>();
        int exported = 0;
        int vip = 0;
        int drawings = 0;
        int failed = 0;
        Path temp = Files.createTempFile("bauloo-export-", ".zip");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                String meta = "{\"pack\": {\"description\": \"" + name.replace("\"", "") + " - exported from Bauloo Styles\", "
                        + "\"pack_format\": 75, \"min_format\": 65, \"max_format\": 9999}}\n";
                zip.putNextEntry(new ZipEntry("pack.mcmeta"));
                zip.write(meta.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                for (Tab tab : Tab.values()) {
                    String folder = preset.styles().get(tab);
                    if (folder == null) {
                        continue;
                    }
                    if (folder.startsWith(VIPPACK)) {
                        vip++;
                        continue;
                    }
                    if (DrawScreen.isDrawingFolder(folder)) {
                        drawings++;
                        continue;
                    }
                    boolean ok = false;
                    if (folder.startsWith(StylesCloud.FOLDER_PREFIX)) {
                        StylesCloud.Style s = StylesCloud.byFolder(folder);
                        if (s != null && StylesCloud.download(s).get(3, TimeUnit.MINUTES)) {
                            ok = copyZip(packs.resolve(folder + ".zip"), zip, written);
                        }
                    } else if (folder.startsWith(UPLOAD)) {
                        Path source = uploads.get(tab);
                        if (source != null) {
                            ok = Files.isDirectory(source) ? copyTree(source, zip, written) : copyZip(source, zip, written);
                        }
                    } else if (folder.startsWith(MYPACK)) {
                        MyPacks.Style s = MyPacks.byId(folder.substring(MYPACK.length()));
                        if (s != null) {
                            Path cut = Files.createTempFile("bauloo-export-cut-", ".zip");
                            try {
                                Files.delete(cut);
                                ok = StylesPackFilter.extract(packs.resolve(s.pack()), s.category(), cut) > 0
                                        && copyZip(cut, zip, written);
                            } finally {
                                Files.deleteIfExists(cut);
                            }
                        }
                    } else if (mod.isPresent()) {
                        // built-in: inside the mod under resourcepacks/<folder>/
                        Optional<Path> root = mod.get().findPath("resourcepacks/" + folder);
                        ok = root.isPresent() && copyTree(root.get(), zip, written);
                    }
                    if (ok) {
                        exported++;
                    } else {
                        failed++;
                        BaulooStylesClient.LOGGER.info("[PresetExport] {}: {} could not be exported", tab.prefix, folder);
                    }
                }
            }
            if (exported == 0) {
                return vip > 0 ? "VIP styles can't be exported" : drawings > 0 ? "Drawings stay on this PC - nothing to export"
                        : "Nothing to export in this preset";
            }
            Files.createDirectories(packs);
            Files.move(temp, out, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        lastFolder = packs;
        // two lines for the menu: where it went, and how to turn it on
        StringBuilder msg = new StringBuilder("Saved \"" + out.getFileName() + "\" in your resourcepacks folder\n"
                + "Turn it on in Options > Resource Packs");
        if (failed > 0) {
            msg.append(" - ").append(failed).append(failed == 1 ? " style is" : " styles are").append(" not on this PC");
        }
        if (vip > 0) {
            msg.append(" - ").append(vip).append(" VIP left out");
        }
        if (drawings > 0) {
            msg.append(" - ").append(drawings).append(drawings == 1 ? " drawing" : " drawings").append(" left out");
        }
        return msg.toString();
    }

    /** Only files under assets/; a pack's own pack.mcmeta and pack.png are not taken over. */
    private static boolean wanted(String name) {
        return name.startsWith("assets/") && !name.contains("..") && !name.endsWith("/");
    }

    private static boolean copyZip(Path source, ZipOutputStream zip, Set<String> written) throws IOException {
        if (!Files.isRegularFile(source)) {
            return false;
        }
        boolean any = false;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(source))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !wanted(name)) {
                    continue;
                }
                any = true;
                if (written.add(name.toLowerCase(java.util.Locale.ROOT))) {
                    zip.putNextEntry(new ZipEntry(name));
                    in.transferTo(zip);
                    zip.closeEntry();
                }
            }
        }
        return any;
    }

    private static boolean copyTree(Path root, ZipOutputStream zip, Set<String> written) throws IOException {
        if (!Files.isDirectory(root)) {
            return false;
        }
        boolean any = false;
        List<Path> files;
        try (var walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        for (Path file : files) {
            String name = root.relativize(file).toString().replace('\\', '/');
            if (!wanted(name)) {
                continue;
            }
            any = true;
            if (written.add(name.toLowerCase(java.util.Locale.ROOT))) {
                zip.putNextEntry(new ZipEntry(name));
                try (InputStream in = Files.newInputStream(file)) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
        return any;
    }
}

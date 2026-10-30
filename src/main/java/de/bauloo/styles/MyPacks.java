package de.bauloo.styles;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * "My Packs": every part of every pack in your resourcepacks folder (an anchor, a crystal, a sky ...)
 * as its own style. The scan runs in the background and is remembered per pack file, so only new or
 * changed packs are read again. Applying one copies just that part into your local uploads.
 */
public final class MyPacks {

    /** true would show My Packs to everyone; now only bauloo's PC, players with an uploader/admin key and VIPs */
    private static final boolean PUBLIC = false;

    public record Style(String id, String category, String name, String pack, String hash) {
    }

    private static final Path CACHE = FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles/mypacks");
    private static final Path INDEX = CACHE.resolve("index.json");
    private static final Path PREVIEWS = CACHE.resolve("previews");

    private static volatile List<Style> styles = List.of();
    private static volatile int version;
    private static volatile boolean scanning;
    private static volatile int scanned;
    private static volatile int total;
    private static volatile int packCount;
    private static boolean started;
    private static volatile Boolean allowed;
    private static volatile long checkedAt;
    private static volatile boolean checking;

    private static final Map<String, net.minecraft.util.Identifier> TEXTURES = new HashMap<>();
    private static final Set<String> NO_PICTURE = Collections.synchronizedSet(new java.util.HashSet<>());

    private MyPacks() {
    }

    public static boolean shown() {
        if (PUBLIC || Boolean.getBoolean("bauloo.audit") || StylesPresence.isVip()) {
            return true;
        }
        // asks the worker in the background now and then; hidden until the answer says yes
        if ((allowed == null || System.currentTimeMillis() - checkedAt > 5 * 60_000) && !checking) {
            checking = true;
            Thread thread = new Thread(() -> {
                try {
                    // only a key the worker confirms: a github-token.txt alone was enough before,
                    // and anyone could create that file (2026-10-14)
                    allowed = StylesUploadServer.hasKeyRole();
                } catch (Exception e) {
                    allowed = false;
                } finally {
                    checkedAt = System.currentTimeMillis();
                    checking = false;
                }
            }, "Bauloo Styles key check");
            thread.setDaemon(true);
            thread.start();
        }
        return Boolean.TRUE.equals(allowed);
    }

    /** a key was added or removed: ask again right away instead of within 5 minutes */
    public static void recheck() {
        checkedAt = 0;
        allowed = null;
    }

    public static List<Style> styles() {
        return styles;
    }

    public static int version() {
        return version;
    }

    public static boolean scanning() {
        return scanning;
    }

    public static int scanned() {
        return scanned;
    }

    public static int total() {
        return total;
    }

    public static int packCount() {
        return packCount;
    }

    public static Style byId(String id) {
        for (Style s : styles) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** what the last scan saw: the packs (names, sizes, dates) and the collections there were */
    private static volatile String lastSignature = "";

    /**
     * Scans once per game start, and again whenever the folder changed (a pack added, removed or
     * replaced) or new collections came (a mod update, a new cloud collection). RESCAN forces it.
     */
    public static synchronized void ensureScanned() {
        if (!started || (!scanning && !signature().equals(lastSignature))) {
            rescan();
        }
    }

    private static String signature() {
        StringBuilder sb = new StringBuilder(String.join(",", StylesPackFilter.categories())).append('|');
        Path folder = BaulooStylesClient.packsDir();
        try (var list = Files.list(folder)) {
            list.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".zip") && !n.startsWith("bauloo_");
            }).sorted().forEach(p -> {
                try {
                    sb.append(p.getFileName()).append(':').append(Files.size(p)).append(':')
                            .append(Files.getLastModifiedTime(p).toMillis()).append(';');
                } catch (IOException ignored) {
                    // gone while listing
                }
            });
        } catch (IOException ignored) {
            // no folder: nothing to scan
        }
        return sb.toString();
    }

    public static synchronized void rescan() {
        if (scanning) {
            return;
        }
        started = true;
        scanning = true;
        Thread thread = new Thread(MyPacks::scan, "Bauloo Styles pack scan");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    // ------------------------------------------------------------------ scanning

    private static void scan() {
        lastSignature = signature();
        // cached parts only count when they were found with the same collections as now
        String cats = String.join(",", StylesPackFilter.categories());
        try {
            Path folder = BaulooStylesClient.packsDir();
            List<Path> zips = new ArrayList<>();
            if (Files.isDirectory(folder)) {
                try (var list = Files.list(folder)) {
                    list.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".zip") && !n.startsWith("bauloo_") && Files.isRegularFile(p);
                    }).sorted().forEach(zips::add);
                }
            }
            total = zips.size();
            scanned = 0;
            JsonObject cache = readIndex();
            if (!cats.equals(string(cache, "_cats"))) {
                cache = new JsonObject();
            }
            JsonObject next = new JsonObject();
            next.addProperty("_cats", cats);
            // one style per category and texture set: the same anchor in five packs shows once
            Map<String, Style> found = new TreeMap<>();
            for (Path zip : zips) {
                String key = zip.getFileName().toString();
                String stamp = Files.size(zip) + ":" + Files.getLastModifiedTime(zip).toMillis();
                JsonObject entry = cache.has(key) && cache.get(key).isJsonObject() ? cache.getAsJsonObject(key) : null;
                Map<String, String> parts;
                if (entry != null && stamp.equals(string(entry, "stamp"))) {
                    parts = new TreeMap<>();
                    for (Map.Entry<String, JsonElement> e : entry.getAsJsonObject("parts").entrySet()) {
                        parts.put(e.getKey(), e.getValue().getAsString());
                    }
                } else {
                    parts = read(zip);
                }
                JsonObject out = new JsonObject();
                out.addProperty("stamp", stamp);
                JsonObject partsJson = new JsonObject();
                parts.forEach(partsJson::addProperty);
                out.add("parts", partsJson);
                next.add(key, out);
                for (Map.Entry<String, String> part : parts.entrySet()) {
                    String id = part.getKey() + "_" + part.getValue().substring(0, 12);
                    found.putIfAbsent(id, new Style(id, part.getKey(), cleanName(key), key, part.getValue()));
                }
                scanned++;
                if (scanned % 5 == 0) {
                    publish(found, zips.size());
                }
            }
            publish(found, zips.size());
            writeIndex(next);
            previews(folder);
        } catch (Exception e) {
            BaulooStylesClient.LOGGER.warn("[MyPacks] scan failed: {}", e.toString());
        } finally {
            scanning = false;
            version++;
        }
    }

    private static void publish(Map<String, Style> found, int packs) {
        List<Style> list = new ArrayList<>(found.values());
        list.sort((a, b) -> {
            int c = Integer.compare(order(a.category()), order(b.category()));
            return c != 0 ? c : a.name().compareToIgnoreCase(b.name());
        });
        styles = List.copyOf(list);
        packCount = packs;
        version++;
    }

    /** menu order of the collections, so the sections follow the rail */
    private static int order(String category) {
        BaulooStylesClient.Tab[] tabs = BaulooStylesClient.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i].prefix.equals(category)) {
                return i;
            }
        }
        return tabs.length;
    }

    /** category -> fingerprint of that part's textures, for one pack */
    private static Map<String, String> read(Path zip) {
        Map<String, String> parts = new TreeMap<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Map<String, List<ZipEntry>> byCategory = new HashMap<>();
            for (ZipEntry entry : Collections.list(file.entries())) {
                if (entry.isDirectory()) {
                    continue;
                }
                for (String category : StylesPackFilter.categories()) {
                    String target = StylesPackFilter.target(entry.getName(), category);
                    if (target != null && target.endsWith(".png")) {
                        byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
                    }
                }
            }
            for (Map.Entry<String, List<ZipEntry>> e : byCategory.entrySet()) {
                List<ZipEntry> entries = e.getValue();
                entries.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                for (ZipEntry entry : entries) {
                    md.update(StylesPackFilter.target(entry.getName(), e.getKey()).getBytes(StandardCharsets.UTF_8));
                    try (InputStream in = file.getInputStream(entry)) {
                        md.update(in.readAllBytes());
                    }
                }
                parts.put(e.getKey(), HexFormat.of().formatHex(md.digest()));
            }
        } catch (Exception e) {
            BaulooStylesClient.LOGGER.warn("[MyPacks] could not read {}: {}", zip.getFileName(), e.toString());
        }
        return parts;
    }

    /** pictures for every style that has none yet, drawn from its pack */
    private static void previews(Path folder) throws IOException {
        Files.createDirectories(PREVIEWS);
        for (Style s : styles) {
            Path png = PREVIEWS.resolve(s.id() + ".png");
            if (Files.exists(png) || NO_PICTURE.contains(s.id())) {
                continue;
            }
            Path zip = folder.resolve(s.pack());
            boolean made = "sky".equals(s.category())
                    ? StylesUploadServer.skyPreview(zip, png)
                    : StylesAutoPreview.render(zip, s.category(), png);
            if (!made) {
                NO_PICTURE.add(s.id());
            }
            version++;
        }
    }

    // ------------------------------------------------------------------ menu side

    /** The style's picture as a texture, or null while it has none. Render thread only. */
    public static net.minecraft.util.Identifier preview(String id) {
        net.minecraft.util.Identifier ready = TEXTURES.get(id);
        if (ready != null) {
            return ready;
        }
        Path png = PREVIEWS.resolve(id + ".png");
        if (!Files.exists(png)) {
            return null;
        }
        try {
            net.minecraft.util.Identifier texture = net.minecraft.util.Identifier.of(BaulooStylesClient.MOD_ID,
                    "mypacks/" + id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
            net.minecraft.client.texture.NativeImage image = PreviewTexture.shrink(
                    net.minecraft.client.texture.NativeImage.read(Files.readAllBytes(png)));
            net.minecraft.client.MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(texture, new PreviewTexture(texture, image));
            TEXTURES.put(id, texture);
            return texture;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Copies only this part of the pack into your local uploads (once) and returns the folder id the
     * menu applies, or null. Runs off the render thread: big packs take a moment.
     */
    public static String use(Style s) {
        try {
            Path folder = BaulooStylesClient.packsDir();
            Path target = StylesUploadManager.root().resolve(s.category());
            Files.createDirectories(target);
            String base = s.name().isEmpty() ? s.category() : s.name();
            Path zip = target.resolve(base + ".zip");
            if (!Files.exists(zip) || StylesPackFilter.missingModels(folder.resolve(s.pack()), zip, s.category())) {
                if (StylesPackFilter.extract(folder.resolve(s.pack()), s.category(), zip) == 0) {
                    return null;
                }
                Path png = PREVIEWS.resolve(s.id() + ".png");
                if (Files.exists(png)) {
                    Files.copy(png, target.resolve(base + ".png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                StylesUploadManager.changed();
            }
            StylesUploadManager.sync(s.category());
            return StylesUploadManager.packIdFor(s.category(), zip);
        } catch (IOException e) {
            BaulooStylesClient.LOGGER.warn("[MyPacks] could not use {}: {}", s.pack(), e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------ helpers

    static String cleanName(String file) {
        String n = file.replaceFirst("(?i)\\.zip$", "").replaceAll("§.", "").replace('_', ' ');
        n = n.replaceAll("^[\\s!#.\\-]+", "").replaceAll("\\s+", " ").trim();
        n = n.replaceAll("[\\\\/:*?\"<>|]", "");
        return n.length() > 40 ? n.substring(0, 40).trim() : n;
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private static JsonObject readIndex() {
        try {
            if (Files.exists(INDEX)) {
                return JsonParser.parseString(Files.readString(INDEX)).getAsJsonObject();
            }
        } catch (Exception ignored) {
        }
        return new JsonObject();
    }

    private static void writeIndex(JsonObject index) {
        try {
            Files.createDirectories(CACHE);
            Files.writeString(INDEX, new Gson().toJson(index));
        } catch (IOException e) {
            BaulooStylesClient.LOGGER.warn("[MyPacks] could not save the scan: {}", e.toString());
        }
    }
}

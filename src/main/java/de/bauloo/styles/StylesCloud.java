package de.bauloo.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Styles published by bauloo for everyone. The list and the packs live in a
 * public GitHub repository; only its owner can push to it. The mod only ever
 * reads: it fetches index.json, downloads a pack the first time it is applied,
 * checks the SHA-256 and keeps nothing but textures from the zip.
 */
public final class StylesCloud {

    /** GitHub account that owns the repository. Only this account can publish. */
    public static final String OWNER = "Bauloo0";
    public static final String REPO = "bauloo-styles-cloud";
    public static final String FOLDER_PREFIX = "bauloo_cloud_";

    private static final String BASE = "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/main/";
    /**
     * The same files on other roads, for players whose internet blocks raw.githubusercontent.com:
     * jsDelivr (a free CDN that mirrors the repository) and bauloo's worker (fetches from GitHub itself).
     * Packs are checked by their SHA-256 either way, so a mirror can't hand out anything else.
     */
    private static final String[] MIRRORS = {
            BASE,
            "https://cdn.jsdelivr.net/gh/" + OWNER + "/" + REPO + "@main/",
            "https://bauloo-styles-cloud.bauloo-styles-cloud-worker.workers.dev/raw/",
    };
    private static final String[] MIRROR_NAMES = {"GitHub", "jsDelivr", "the Bauloo server"};
    /** the road that worked last; tried first next time */
    private static volatile int mirror;

    /**
     * GET a file of the repository, trying the roads in turn until one gives an answer ok() accepts.
     * Returns the last answer (or null when no road could be reached at all).
     */
    static <T> HttpResponse<T> getFile(String path, HttpResponse.BodyHandler<T> handler, Duration timeout,
            java.util.function.Predicate<HttpResponse<T>> ok) {
        HttpResponse<T> last = null;
        int start = mirror;
        for (int k = 0; k < MIRRORS.length; k++) {
            int m = (start + k) % MIRRORS.length;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(MIRRORS[m] + path))
                        .header("User-Agent", "bauloo-styles-mod").timeout(timeout).GET().build();
                HttpResponse<T> response = HTTP.send(request, handler);
                last = response;
                if (ok.test(response)) {
                    if (m != mirror) {
                        BaulooStylesClient.LOGGER.info("[Cloud] using {} for the cloud files", MIRROR_NAMES[m]);
                        mirror = m;
                    }
                    return response;
                }
            } catch (Exception e) {
                BaulooStylesClient.LOGGER.info("[Cloud] {} not reachable for {}: {}", MIRROR_NAMES[m], path, e.toString());
            }
        }
        return last;
    }
    private static final long MAX_PACK_BYTES = 95L * 1024 * 1024;
    private static final long MAX_UNPACKED_BYTES = 400L * 1024 * 1024;
    private static final long MAX_PREVIEW_BYTES = 2L * 1024 * 1024;
    /** Opening the menu refetches the list after this long; 60 s keeps us under GitHub's 60 requests per hour. */
    private static final long REFRESH_MS = 60 * 1000L;
    private static final int NEW_DAYS = 7;

    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9]{1,15}_[a-z0-9_]{1,40}$");
    private static final Pattern SHA = Pattern.compile("^[0-9a-f]{64}$");
    private static final String META = "{\"pack\": {\"description\": \"Bauloo cloud style - by bauloo\", "
            + "\"pack_format\": 75, \"min_format\": 65, \"max_format\": 9999}}\n";

    /** early: shown only with early access on (like early collections) */
    public record Style(String id, String category, String name, String sha256, long size, boolean hasPreview,
            String added, boolean early) {
        public String folder() {
            return FOLDER_PREFIX + id;
        }

        public boolean isNew() {
            try {
                return !LocalDate.parse(added).isBefore(LocalDate.now().minusDays(NEW_DAYS));
            } catch (Exception e) {
                return false;
            }
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern WORKER = Pattern.compile("^https://[a-z0-9-]+\\.[a-z0-9-]+\\.workers\\.dev$");

    private static volatile List<Style> styles = List.of();
    /** Where friends with a key publish to. Comes from index.json, so it can change without a new mod version. */
    private static volatile String publishUrl;
    private static volatile int version;
    private static volatile long lastFetch;
    private static volatile boolean fetching;
    private static volatile boolean cacheLoaded;
    private static final Map<String, CompletableFuture<Boolean>> DOWNLOADS = new ConcurrentHashMap<>();
    private static final Set<String> PREVIEWS_READY = ConcurrentHashMap.newKeySet();
    private static final Set<String> PREVIEWS_LOADING = ConcurrentHashMap.newKeySet();

    private StylesCloud() {
    }

    public static boolean enabled() {
        return !"OWNER_NOT_SET".equals(OWNER);
    }

    /** Bumped whenever the list changes, so an open menu knows to rebuild. */
    public static int version() {
        return version;
    }

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles/cloud");
    }

    private static Path packFile(Style s) {
        return BaulooStylesClient.stylePacksDir().resolve(s.folder() + ".zip");
    }

    // ------------------------------------------------------------------ index

    public static void refresh(boolean force) {
        if (!enabled()) {
            return;
        }
        if (!cacheLoaded) {
            loadCache();
        }
        long now = System.currentTimeMillis();
        if (fetching || (!force && now - lastFetch < REFRESH_MS)) {
            return;
        }
        fetching = true;
        // The API answers with the newest commit right away; raw.githubusercontent can lag
        // up to five minutes. The API allows 60 calls an hour per IP, so raw stays the fallback.
        HttpRequest api = HttpRequest.newBuilder(URI.create(
                        "https://api.github.com/repos/" + OWNER + "/" + REPO + "/contents/index.json?ref=main"))
                .header("Accept", "application/vnd.github.raw+json")
                .header("User-Agent", "bauloo-styles-mod")
                .timeout(Duration.ofSeconds(15)).GET().build();
        HTTP.sendAsync(api, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .exceptionally(error -> null)
                .thenApplyAsync(response -> response != null && response.statusCode() == 200
                        ? response
                        : getFile("index.json", HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
                                Duration.ofSeconds(15), r -> r.statusCode() == 200 && parse(r.body()) != null))
                .thenAccept(response -> {
                    if (response == null || response.statusCode() != 200) {
                        return;
                    }
                    List<Style> parsed = parse(response.body());
                    if (parsed == null) {
                        return;
                    }
                    try {
                        Files.createDirectories(dir());
                        Files.writeString(dir().resolve("index.json"), response.body());
                    } catch (IOException ignored) {
                    }
                    publish(parsed);
                })
                .whenComplete((ignored, error) -> {
                    lastFetch = System.currentTimeMillis();
                    fetching = false;
                });
    }

    private static void loadCache() {
        cacheLoaded = true;
        Path cached = dir().resolve("index.json");
        if (!Files.exists(cached)) {
            return;
        }
        try {
            List<Style> parsed = parse(Files.readString(cached));
            if (parsed != null) {
                publish(parsed);
            }
        } catch (IOException ignored) {
        }
    }

    private static void publish(List<Style> parsed) {
        if (!parsed.equals(styles)) {
            styles = List.copyOf(parsed);
            version++;
        }
    }

    public static String publishUrl() {
        if (!cacheLoaded && enabled()) {
            loadCache();
        }
        return publishUrl;
    }

    /** Returns null when the document is not a valid index at all. */
    private static List<Style> parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String url = string(root, "publishUrl");
            publishUrl = url != null && WORKER.matcher(url).matches() ? url : null;
            if (CloudCategories.read(root)) {
                // the menu slots follow the new list on the render thread
                net.minecraft.client.MinecraftClient.getInstance().send(BaulooStylesClient.Tab::bindCloudSlots);
            }
            JsonArray array = root.getAsJsonArray("styles");
            List<Style> result = new ArrayList<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject o = element.getAsJsonObject();
                String id = string(o, "id");
                String category = string(o, "category");
                String name = string(o, "name");
                String sha = string(o, "sha256");
                String added = string(o, "added");
                long size = o.has("size") ? o.get("size").getAsLong() : -1;
                if (id == null || category == null || name == null || sha == null
                        || !ID.matcher(id).matches() || !id.startsWith(category + "_") || !CloudCategories.known(category)
                        || !SHA.matcher(sha).matches() || size <= 0 || size > MAX_PACK_BYTES
                        || name.isBlank() || name.length() > 40) {
                    continue;
                }
                boolean preview = o.has("preview") && ("previews/" + id + ".png").equals(string(o, "preview"));
                boolean early = o.has("early") && o.get("early").isJsonPrimitive() && o.get("early").getAsBoolean();
                result.add(new Style(id, category, name.trim(), sha, size, preview, added == null ? "" : added, early));
            }
            result.sort(Comparator.comparing(Style::added).reversed());
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    public static List<Style> of(String category) {
        if (!cacheLoaded && enabled()) {
            loadCache();
        }
        List<Style> result = new ArrayList<>();
        boolean early = VipPacks.earlyActive();
        for (Style s : styles) {
            if (s.category().equals(category) && (early || !s.early())) {
                result.add(s);
            }
        }
        return result;
    }

    public static Style byFolder(String folder) {
        if (folder == null || !folder.startsWith(FOLDER_PREFIX)) {
            return null;
        }
        String id = folder.substring(FOLDER_PREFIX.length());
        for (Style s : styles) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    // --------------------------------------------------------------- download

    public static boolean isReady(Style s) {
        try {
            Path marker = dir().resolve("packs").resolve(s.id() + ".v2.sha");
            return Files.exists(packFile(s)) && Files.exists(marker)
                    && Files.readString(marker).trim().equals(s.sha256());
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isDownloading(Style s) {
        return DOWNLOADS.containsKey(s.id());
    }

    public static CompletableFuture<Boolean> download(Style s) {
        if (isReady(s)) {
            return CompletableFuture.completedFuture(true);
        }
        return DOWNLOADS.computeIfAbsent(s.id(), id -> CompletableFuture
                .supplyAsync(() -> fetchPack(s))
                .whenComplete((ok, error) -> DOWNLOADS.remove(id)));
    }

    private static boolean fetchPack(Style s) {
        Path temp = null;
        try {
            Files.createDirectories(dir().resolve("packs"));
            temp = Files.createTempFile("bauloo-cloud-", ".zip");
            final Path file = temp;
            HttpResponse<Path> response = getFile("packs/" + s.id() + ".zip", HttpResponse.BodyHandlers.ofFile(temp),
                    Duration.ofMinutes(3), r -> r.statusCode() == 200 && matches(file, s));
            if (response == null || response.statusCode() != 200 || Files.size(temp) != s.size()) {
                BaulooStylesClient.LOGGER.warn("Cloud style {}: download failed (status {}, {} bytes)",
                        s.id(), response == null ? "no connection" : response.statusCode(), Files.size(temp));
                return false;
            }
            if (!sha256(temp).equals(s.sha256())) {
                BaulooStylesClient.LOGGER.warn("Cloud style {}: checksum does not match, ignored", s.id());
                return false;
            }
            Path target = packFile(s);
            Files.createDirectories(target.getParent());
            Path cleaned = Files.createTempFile("bauloo-cloud-clean-", ".zip");
            if (!copyTexturesOnly(temp, cleaned, s.category())) {
                Files.deleteIfExists(cleaned);
                BaulooStylesClient.LOGGER.warn("Cloud style {}: pack rejected", s.id());
                return false;
            }
            Files.move(cleaned, target, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(dir().resolve("packs").resolve(s.id() + ".v2.sha"), s.sha256());
            return true;
        } catch (Exception e) {
            BaulooStylesClient.LOGGER.warn("Cloud style {}: {}", s.id(), e.toString());
            return false;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * The frames of a built-in animated cape (capes/<key>.zip). The zip must have exactly the size and SHA-256
     * the mod expects; only the frame pictures (f00.jpg ... f99.jpg) are unpacked into folder.
     */
    static boolean fetchCape(String key, String sha256, long size, Path folder) {
        Path temp = null;
        try {
            temp = Files.createTempFile("bauloo-cape-", ".zip");
            final Path file = temp;
            HttpResponse<Path> response = getFile("capes/" + key + ".zip", HttpResponse.BodyHandlers.ofFile(temp),
                    Duration.ofMinutes(2), r -> r.statusCode() == 200 && sameFile(file, size, sha256));
            if (response == null || response.statusCode() != 200 || !sameFile(temp, size, sha256)) {
                BaulooStylesClient.LOGGER.warn("[Capes] {}: download failed (status {})", key,
                        response == null ? "no connection" : response.statusCode());
                return false;
            }
            Files.createDirectories(folder);
            int kept = 0;
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(temp))) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.matches("f[0-9]{2}\\.jpg")) {
                        continue;
                    }
                    Files.copy(in, folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    kept++;
                }
            }
            if (kept == 0) {
                BaulooStylesClient.LOGGER.warn("[Capes] {}: no frames in the download", key);
                return false;
            }
            Files.writeString(folder.resolve("sha256"), sha256);
            return true;
        } catch (Exception e) {
            BaulooStylesClient.LOGGER.warn("[Capes] {}: {}", key, e.toString());
            return false;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static boolean sameFile(Path file, long size, String sha256) {
        try {
            return Files.size(file) == size && sha256(file).equals(sha256);
        } catch (Exception e) {
            return false;
        }
    }

    /** Keeps only textures and models - a resource pack never needs anything else. */
    private static boolean copyTexturesOnly(Path source, Path destination, String category) throws IOException {
        long unpacked = 0;
        int kept = 0;
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(source));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(destination))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || name.startsWith("/") || name.contains("..")) {
                    continue;
                }
                boolean allowed = "sky".equals(category)
                        ? name.startsWith("assets/minecraft/optifine/sky/")
                        : name.startsWith("assets/") || name.equals("pack.png");
                if (!allowed) {
                    continue;
                }
                out.putNextEntry(new ZipEntry(name));
                int read;
                while ((read = in.read(buffer)) > 0) {
                    unpacked += read;
                    if (unpacked > MAX_UNPACKED_BYTES) {
                        return false;
                    }
                    out.write(buffer, 0, read);
                }
                out.closeEntry();
                kept++;
            }
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write(META.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return kept > 0;
    }

    /** the downloaded file is exactly the pack the index names (size and SHA-256) */
    private static boolean matches(Path file, Style s) {
        try {
            return Files.size(file) == s.size() && sha256(file).equals(s.sha256());
        } catch (Exception e) {
            return false;
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    // ---------------------------------------------------------------- preview

    /** The downloaded preview, or null while it is not there (yet). */
    public static Identifier preview(Style s) {
        Identifier id = Identifier.of(BaulooStylesClient.MOD_ID, "cloud/" + s.id() + "_" + s.sha256().substring(0, 8));
        if (PREVIEWS_READY.contains(id.getPath())) {
            return id;
        }
        if (s.hasPreview() && PREVIEWS_LOADING.add(id.getPath())) {
            CompletableFuture.runAsync(() -> loadPreview(s, id));
        }
        return null;
    }

    /** how long a downloaded preview is used before it is fetched again */
    private static final long PREVIEW_MAX_AGE_MS = 6 * 60 * 60 * 1000L;

    private static void loadPreview(Style s, Identifier id) {
        try {
            Path cached = dir().resolve("previews").resolve(id.getPath().substring("cloud/".length()) + ".png");
            byte[] bytes = null;
            // a preview can be replaced in the cloud, so a copy older than PREVIEW_MAX_AGE is fetched again
            boolean fresh = Files.exists(cached) && System.currentTimeMillis()
                    - Files.getLastModifiedTime(cached).toMillis() < PREVIEW_MAX_AGE_MS;
            if (!fresh) {
                try {
                    HttpResponse<byte[]> response = getFile("previews/" + s.id() + ".png", HttpResponse.BodyHandlers.ofByteArray(),
                            Duration.ofSeconds(20), r -> r.statusCode() == 200 && r.body().length > 8);
                    if (response != null && response.statusCode() == 200 && response.body().length <= MAX_PREVIEW_BYTES) {
                        bytes = response.body();
                        Files.createDirectories(cached.getParent());
                        try (OutputStream out = Files.newOutputStream(cached)) {
                            out.write(bytes);
                        }
                    }
                } catch (IOException e) {
                    // offline: fall back to the copy on disk below
                }
            }
            if (bytes == null) {
                if (!Files.exists(cached)) {
                    PREVIEWS_LOADING.remove(id.getPath());
                    return;
                }
                bytes = Files.readAllBytes(cached);
            }
            final byte[] png = bytes;
            MinecraftClient.getInstance().send(() -> {
                try {
                    NativeImage image = PreviewTexture.shrink(NativeImage.read(png));
                    MinecraftClient.getInstance().getTextureManager()
                            .registerTexture(id, new PreviewTexture(id, image));
                    PREVIEWS_READY.add(id.getPath());
                } catch (IOException e) {
                    BaulooStylesClient.LOGGER.warn("Cloud style {}: preview unreadable", s.id());
                }
            });
        } catch (Exception e) {
            PREVIEWS_LOADING.remove(id.getPath());
        }
    }
}

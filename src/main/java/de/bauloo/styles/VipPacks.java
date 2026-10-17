package de.bauloo.styles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Bauloo's Packs": bauloo's own pack collection, cut into styles, for VIPs. The list, the previews
 * and the style zips come from the worker and only with a VIP (or higher) key on this PC. Using one
 * puts it into your local uploads, like My Packs does.
 */
public final class VipPacks {

    /** preview: a short hash of the picture (a new picture gets a new one), "" when unknown */
    public record Style(String id, String category, String name, String sha256, long size, String preview) {
    }

    private static final Set<String> ROLES = Set.of("vip", "media", "uploader", "admin");
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles/vip");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static volatile List<Style> styles = List.of();
    private static volatile int version;
    private static volatile boolean loading;
    private static volatile long loadedAt;
    private static volatile String status = "";
    private static volatile Boolean allowed;
    private static volatile long checkedAt;
    private static volatile boolean checking;

    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static final Set<String> PREVIEW_LOADING = ConcurrentHashMap.newKeySet();

    private VipPacks() {
    }

    /** Shown to VIPs (and higher): asks the worker about the key now and then, hidden until it says yes. */
    public static boolean shown() {
        if ((allowed == null || System.currentTimeMillis() - checkedAt > 5 * 60_000) && !checking) {
            checking = true;
            run(() -> {
                try {
                    String role = StylesUploadServer.keyRole();
                    keyRole = role;
                    allowed = role != null && ROLES.contains(role);
                } catch (Exception e) {
                    allowed = false;
                } finally {
                    checkedAt = System.currentTimeMillis();
                    checking = false;
                }
            });
        }
        return Boolean.TRUE.equals(allowed);
    }

    private static volatile String keyRole;

    /** early access is on and this PC has a VIP (or higher) key: early styles and collections show */
    public static boolean earlyActive() {
        return StylesUiConfig.earlyAccess && shown();
    }

    /** bauloo's own admin key on this PC (known after shown() asked once) */
    public static boolean admin() {
        shown();
        return "admin".equals(keyRole);
    }

    public static List<Style> styles() {
        return styles;
    }

    public static int version() {
        return version;
    }

    public static boolean loading() {
        return loading;
    }

    public static String status() {
        return status;
    }

    public static Style byId(String id) {
        for (Style s : styles) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** Loads the list (again after 10 minutes, or when forced). */
    public static void refresh(boolean force) {
        // an empty list counts as loaded too, else every redraw would ask again
        if (loading || (!force && System.currentTimeMillis() - loadedAt < 10 * 60_000)) {
            return;
        }
        loading = true;
        run(() -> {
            try {
                HttpResponse<String> r = get("/vip/index", HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) {
                    status = message(r.body(), "Bauloo's Packs are not reachable right now");
                    return;
                }
                List<Style> out = new ArrayList<>();
                for (JsonElement e : JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("styles")) {
                    JsonObject o = e.getAsJsonObject();
                    String id = o.get("id").getAsString();
                    if (!id.matches("[a-z0-9_]{1,80}")) {
                        continue;
                    }
                    out.add(new Style(id, o.get("category").getAsString(), o.get("name").getAsString(),
                            o.get("sha256").getAsString(), o.get("size").getAsLong(),
                            o.has("preview") ? o.get("preview").getAsString().replaceAll("[^0-9a-f]", "") : ""));
                }
                styles = List.copyOf(out);
                status = "";
                loadedAt = System.currentTimeMillis();
                version++;
            } catch (Exception e) {
                status = "Bauloo's Packs are not reachable right now";
            } finally {
                // also after a failure: try again in 10 minutes or when the page is opened again
                loadedAt = System.currentTimeMillis();
                loading = false;
            }
        });
    }

    /** The preview picture, loaded in the background (and kept on disk); null until it is there. */
    public static Identifier preview(String id) {
        Identifier ready = TEXTURES.get(id);
        if (ready != null) {
            return ready;
        }
        Style s = byId(id);
        if (s == null || !PREVIEW_LOADING.add(id)) {
            return null;
        }
        run(() -> {
            try {
                String stamp = s.preview().isEmpty() ? s.sha256().substring(0, 8) : s.preview();
                Path png = DIR.resolve("previews").resolve(id + "_" + stamp + ".png");
                byte[] bytes;
                if (Files.exists(png)) {
                    bytes = Files.readAllBytes(png);
                } else {
                    HttpResponse<byte[]> r = get("/vip/preview/" + id, HttpResponse.BodyHandlers.ofByteArray());
                    if (r.statusCode() != 200 || r.body().length < 8) {
                        return;   // stays in PREVIEW_LOADING: no picture for this one, no asking again
                    }
                    bytes = r.body();
                    Files.createDirectories(png.getParent());
                    Files.write(png, bytes);
                }
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        Identifier texture = Identifier.of(BaulooStylesClient.MOD_ID, "vip/" + id);
                        NativeImage image = PreviewTexture.shrink(NativeImage.read(bytes));
                        MinecraftClient.getInstance().getTextureManager().registerTexture(texture, new PreviewTexture(texture, image));
                        TEXTURES.put(id, texture);
                    } catch (IOException | RuntimeException ignored) {
                        // a broken picture just shows the stand-in icon
                    }
                });
            } catch (Exception e) {
                PREVIEW_LOADING.remove(id);
            }
        });
        return null;
    }

    /**
     * Downloads the style (once, checked by its SHA-256), cuts it out again with the filter and adds it
     * to your local uploads. Returns the id the menu applies, or null. Background thread.
     */
    public static String use(Style s) {
        try {
            Path zip = DIR.resolve("packs").resolve(s.id() + ".zip");
            if (!Files.exists(zip) || !sha256(Files.readAllBytes(zip)).equals(s.sha256())) {
                status = "Downloading " + s.name() + " ...";
                HttpResponse<byte[]> r = get("/vip/pack/" + s.id(), HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() != 200 || !sha256(r.body()).equals(s.sha256())) {
                    status = r.statusCode() == 403 ? "VIP only" : "Download failed";
                    return null;
                }
                Files.createDirectories(zip.getParent());
                Files.write(zip, r.body());
            }
            Path folder = StylesUploadManager.root().resolve(s.category());
            Files.createDirectories(folder);
            String clean = s.name().replaceAll("[^A-Za-z0-9 ._()-]", "").trim();
            Path target = folder.resolve((clean.isEmpty() ? s.id() : clean) + ".zip");
            if (!Files.exists(target)) {
                if (StylesPackFilter.extract(zip, s.category(), target) == 0) {
                    status = "Nothing usable in this style";
                    return null;
                }
                StylesUploadManager.changed();
            }
            StylesUploadManager.sync(s.category());
            status = "";
            return StylesUploadManager.packIdFor(s.category(), target);
        } catch (Exception e) {
            BaulooStylesClient.LOGGER.warn("[VipPacks] {} could not be used: {}", s.id(), e.toString());
            status = "Download failed";
            return null;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static <T> HttpResponse<T> get(String path, HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
        String base = StylesCloud.publishUrl();
        if (base == null) {
            throw new IOException("no server");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Authorization", "Bearer " + StylesUploadServer.savedKey())
                .header("X-Device", StylesUploadServer.deviceId())
                .timeout(Duration.ofSeconds(60)).GET().build();
        return HTTP.send(request, handler);
    }

    private static String message(String body, String fallback) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            return o.has("message") ? o.get("message").getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)).toLowerCase(Locale.ROOT);
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static void run(Runnable task) {
        Thread t = new Thread(task, "Bauloo Styles VIP packs");
        t.setDaemon(true);
        t.start();
    }
}

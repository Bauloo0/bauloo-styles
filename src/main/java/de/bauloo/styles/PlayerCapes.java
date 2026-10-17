package de.bauloo.styles;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Player capes: a cape bauloo gives one player (by Minecraft name), shown on that player only on
 * screens with an admin key. The list and the frames come from the worker's admin-only routes;
 * nobody else downloads or sees them.
 */
public final class PlayerCapes {

    private record Info(String id, int fps) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final long REFRESH_MS = 10 * 60 * 1000L;

    private static volatile Map<String, Info> list = Map.of();
    private static volatile long loadedAt;
    private static volatile boolean loading;
    private static final Map<String, AnimatedCape> CAPES = new HashMap<>();

    private PlayerCapes() {
    }

    /** The cape texture for this player name, or null. Render thread only. */
    public static Identifier texture(String name) {
        if (name == null || !StylesPresence.isAdmin()) {
            return null;
        }
        refresh();
        Info info = list.get(name.toLowerCase(Locale.ROOT));
        if (info == null) {
            return null;
        }
        AnimatedCape cape = CAPES.computeIfAbsent(info.id(), id -> new AnimatedCape(
                Identifier.of(BaulooStylesClient.MOD_ID, "cosmetics/player/" + id), frames(id), info.fps()));
        return cape.current();
    }

    private static void refresh() {
        if (loading || System.currentTimeMillis() - loadedAt < REFRESH_MS) {
            return;
        }
        loading = true;
        Thread t = new Thread(() -> {
            try {
                HttpResponse<String> r = get("/admin/pcapes", HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200) {
                    Map<String, Info> next = new HashMap<>();
                    JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                    for (String player : o.keySet()) {
                        JsonObject e = o.getAsJsonObject(player);
                        String id = e.get("id").getAsString();
                        if (id.matches("[a-z0-9_]{1,80}")) {
                            next.put(player.toLowerCase(Locale.ROOT), new Info(id, e.has("fps") ? e.get("fps").getAsInt() : 12));
                        }
                    }
                    list = next;
                }
            } catch (Exception e) {
                BaulooStylesClient.LOGGER.warn("[PlayerCapes] list failed: {}", e.toString());
            } finally {
                loadedAt = System.currentTimeMillis();
                loading = false;
            }
        }, "Bauloo player capes");
        t.setDaemon(true);
        t.start();
    }

    /** All frames of one cape in one zip (f00.png, f01.png ... in name order), downloaded once. */
    private static AnimatedCape.FrameSource frames(String id) {
        return new AnimatedCape.FrameSource() {
            private List<BufferedImage> images;

            private synchronized List<BufferedImage> images() throws IOException {
                if (images == null) {
                    List<String> names = new ArrayList<>();
                    Map<String, BufferedImage> byName = new HashMap<>();
                    try {
                        HttpResponse<byte[]> r = get("/admin/pcape/" + id, HttpResponse.BodyHandlers.ofByteArray());
                        if (r.statusCode() != 200) {
                            throw new IOException("cape " + id + ": " + r.statusCode());
                        }
                        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(r.body()))) {
                            ZipEntry e;
                            while ((e = zip.getNextEntry()) != null) {
                                String n = e.getName().toLowerCase(Locale.ROOT);
                                if (n.endsWith(".png") || n.endsWith(".jpg")) {
                                    byName.put(n, ImageIO.read(new ByteArrayInputStream(zip.readAllBytes())));
                                    names.add(n);
                                }
                            }
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IOException(ex);
                    }
                    names.sort(null);
                    List<BufferedImage> out = new ArrayList<>();
                    for (String n : names) {
                        out.add(byName.get(n));
                    }
                    images = out;
                }
                return images;
            }

            @Override
            public int count() {
                try {
                    return images().size();
                } catch (IOException e) {
                    BaulooStylesClient.LOGGER.warn("[PlayerCapes] {}", e.toString());
                    return 0;
                }
            }

            @Override
            public BufferedImage read(int index) throws IOException {
                return images().get(index);
            }
        };
    }

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
}

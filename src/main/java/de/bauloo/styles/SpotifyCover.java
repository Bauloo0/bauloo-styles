package de.bauloo.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cover and song length for the Spotify card. The Spotify window only gives artist and title,
 * so both are looked up in the public Deezer and iTunes searches (no account, no key).
 */
public final class SpotifyCover {

    public static final Identifier TEXTURE = Identifier.of(BaulooStylesClient.MOD_ID, "spotify_cover");
    public static final int SIZE = 300;

    /** artist + title the texture shows right now, null = no cover */
    public static volatile String coverKey;
    /** a bright version of the cover's main colour (0xRRGGBB), -1 = none yet */
    public static volatile int color = -1;
    /** song length in ms, 0 = unknown */
    public static volatile long durationMs;
    private static volatile String wanted;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Bauloo Styles Spotify cover");
        thread.setDaemon(true);
        return thread;
    });

    private SpotifyCover() {
    }

    public static void request(String artist, String title) {
        String key = artist + "\n" + title;
        wanted = key;
        durationMs = 0;
        WORKER.submit(() -> fetch(artist, title, key));
    }

    private static void fetch(String artist, String title, String key) {
        try {
            if (fromSpotify(title, key)) {
                return;
            }
            // Deezer knows a lot of smaller artists, iTunes is the fallback; each also with a cleaned title
            String clean = title.replaceAll("\\s*[(\\[](feat|ft|with|prod)[^)\\]]*[)\\]]", "")
                    .replaceAll("\\s+-\\s+.*$", "").trim();
            String[] found = deezer(artist, title);
            if (found == null && !clean.equals(title)) {
                found = deezer(artist, clean);
            }
            if (found == null) {
                found = itunes(artist, title);
            }
            if (found == null && !clean.equals(title)) {
                found = itunes(artist, clean);
            }
            if (found == null || !key.equals(wanted)) {
                BaulooStylesClient.LOGGER.info("No cover found for {} - {}", artist, title);
                return;
            }
            durationMs = Long.parseLong(found[1]);
            HttpResponse<byte[]> art = HTTP.send(HttpRequest.newBuilder(URI.create(found[0]))
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofByteArray());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(art.body()));
            if (image == null || !key.equals(wanted)) {
                return;
            }
            NativeImage cover = rounded(image);
            int main = mainColor(image);
            MinecraftClient.getInstance().send(() -> {
                if (!key.equals(wanted)) {
                    cover.close();
                    return;
                }
                MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE,
                        new NativeImageBackedTexture(TEXTURE::toString, cover));
                coverKey = key;
                color = main;
            });
        } catch (Exception e) {
            BaulooStylesClient.LOGGER.info("No cover for {} - {}: {}", artist, title, e.toString());
        }
    }

    /**
     * The cover straight from the Spotify app through Windows: always the right one, also for the
     * DJ and songs no search knows. Spotify updates it a moment after the title, hence the waits.
     */
    private static boolean fromSpotify(String title, String key) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Thread.sleep(attempt == 0 ? 800 : 1500);
                if (!key.equals(wanted)) {
                    return true; // song changed meanwhile, nothing to do
                }
                Object[] cover = SpotifySmtc.cover();
                if (cover == null || !title.equals(cover[0])) {
                    continue;
                }
                BufferedImage image = ImageIO.read(new ByteArrayInputStream((byte[]) cover[1]));
                if (image == null) {
                    continue;
                }
                show(image, key);
                return true;
            } catch (Throwable e) {
                return false; // no Windows media info: use the searches
            }
        }
        return false;
    }

    private static void show(BufferedImage image, String key) {
        NativeImage cover = rounded(image);
        int main = mainColor(image);
        MinecraftClient.getInstance().send(() -> {
            if (!key.equals(wanted)) {
                cover.close();
                return;
            }
            MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE,
                    new NativeImageBackedTexture(TEXTURE::toString, cover));
            coverKey = key;
            color = main;
        });
    }

    /** {cover url, length in ms} or null. The first hit whose artist matches wins. */
    private static String[] deezer(String artist, String title) throws Exception {
        JsonObject answer = get("https://api.deezer.com/search?limit=5&q="
                + URLEncoder.encode(artist + " " + title, StandardCharsets.UTF_8));
        JsonArray data = answer.getAsJsonArray("data");
        if (data == null) {
            return null;
        }
        for (var element : data) {
            JsonObject song = element.getAsJsonObject();
            String name = song.getAsJsonObject("artist").get("name").getAsString();
            if (sameArtist(artist, name) && song.getAsJsonObject("album").has("cover_big")) {
                return new String[]{song.getAsJsonObject("album").get("cover_big").getAsString(),
                        String.valueOf(song.get("duration").getAsLong() * 1000)};
            }
        }
        return null;
    }

    private static String[] itunes(String artist, String title) throws Exception {
        JsonObject answer = get("https://itunes.apple.com/search?media=music&entity=song&limit=5&term="
                + URLEncoder.encode(artist + " " + title, StandardCharsets.UTF_8));
        JsonArray results = answer.getAsJsonArray("results");
        if (results == null) {
            return null;
        }
        for (var element : results) {
            JsonObject song = element.getAsJsonObject();
            if (sameArtist(artist, song.get("artistName").getAsString()) && song.has("artworkUrl100")) {
                return new String[]{song.get("artworkUrl100").getAsString().replace("100x100bb", SIZE + "x" + SIZE + "bb"),
                        String.valueOf(song.has("trackTimeMillis") ? song.get("trackTimeMillis").getAsLong() : 0)};
            }
        }
        return null;
    }

    /** Spotify lists "A, B" for several artists; one of them matching is enough. */
    private static boolean sameArtist(String spotify, String found) {
        String f = found.toLowerCase(java.util.Locale.ROOT);
        for (String part : spotify.toLowerCase(java.util.Locale.ROOT).split(",")) {
            String p = part.trim();
            if (!p.isEmpty() && (f.contains(p) || p.contains(f))) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject get(String url) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    /** Average colour of the cover, pushed bright and a bit more saturated so it reads on any sky. */
    private static int mainColor(BufferedImage image) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int y = 0; y < image.getHeight(); y += 8) {
            for (int x = 0; x < image.getWidth(); x += 8) {
                int c = image.getRGB(x, y);
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
                n++;
            }
        }
        float[] hsb = java.awt.Color.RGBtoHSB((int) (r / n), (int) (g / n), (int) (b / n), null);
        return java.awt.Color.HSBtoRGB(hsb[0], Math.min(1.0F, hsb[1] * 1.4F + 0.15F), Math.max(hsb[2], 0.9F)) & 0xFFFFFF;
    }

    /** Scaled to SIZE with soft round corners. */
    private static NativeImage rounded(BufferedImage source) {
        NativeImage out = new NativeImage(SIZE, SIZE, false);
        float radius = SIZE * 0.14F;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int rgb = source.getRGB(x * source.getWidth() / SIZE, y * source.getHeight() / SIZE);
                float cx = Math.max(radius - x - 0.5F, Math.max(0, x + 0.5F - (SIZE - radius)));
                float cy = Math.max(radius - y - 0.5F, Math.max(0, y + 0.5F - (SIZE - radius)));
                float outside = (float) Math.sqrt(Math.max(0, cx) * Math.max(0, cx) + Math.max(0, cy) * Math.max(0, cy)) - radius;
                int alpha = cx > 0 && cy > 0 ? Math.round(Math.max(0, Math.min(1, 0.5F - outside)) * 255) : 255;
                out.setColorArgb(x, y, (alpha << 24) | (rgb & 0xFFFFFF));
            }
        }
        return out;
    }
}

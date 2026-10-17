package de.bauloo.styles;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Player count for the upload page. Every 10 minutes while the game runs, the mod sends a
 * SHA-256 hash of this PC's random id and the Minecraft name to bauloo's publish server.
 * Only bauloo sees the names (online list, admin key); the server stores no IP.
 */
public final class StylesStats {

    private static final long EVERY_MS = 10 * 60 * 1000L;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static volatile long nextPing;

    private StylesStats() {
    }

    /** Called every client tick; sends a ping in the background when one is due. */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (now < nextPing) {
            return;
        }
        String base = StylesCloud.publishUrl();
        if (base == null) {
            nextPing = now + 30_000; // cloud list not loaded yet
            return;
        }
        nextPing = now + EVERY_MS;
        Thread thread = new Thread(() -> ping(base), "Bauloo Styles stats");
        thread.setDaemon(true);
        thread.start();
    }

    /** SHA-256 of this PC's random id; sent with the Minecraft name, nothing else. */
    public static String deviceHash() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(("bauloo-styles:" + StylesUploadServer.deviceId()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void ping(String base) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("id", deviceHash());
            // the Minecraft name: only bauloo sees it (online list with his admin key)
            body.addProperty("name", net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername());
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/ping"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            HTTP.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // offline or server down - the count just misses this PC for now
        }
    }

    /** { online (last 15 min), today, total } from the publish server, or null. */
    public static JsonObject fetch() {
        String base = StylesCloud.publishUrl();
        if (base == null) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/stats"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? JsonParser.parseString(response.body()).getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

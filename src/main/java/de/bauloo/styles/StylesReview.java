package de.bauloo.styles;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The FEEDBACK button: anonymous. Sends only the hashed PC id (one review per PC), never anything
 * about the Minecraft account, then opens the review page with a one-hour link.
 */
public final class StylesReview {

    private static final String PAGE = "https://baulooprac.xyz/review.html#t=";
    /** false keeps the button on bauloo's PC only (for trying out changes) */
    private static final boolean PUBLIC = true;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static volatile boolean busy;
    private static volatile String status;
    private static volatile long statusUntil;
    private static Boolean ownerPc;

    private StylesReview() {
    }

    public static boolean shown() {
        if (PUBLIC || Boolean.getBoolean("bauloo.audit")) {
            return true;
        }
        if (ownerPc == null) {
            ownerPc = StylesUploadServer.ownerPc();
        }
        return ownerPc;
    }

    public static boolean busy() {
        return busy;
    }

    /** a short line for the menu footer, or null */
    public static String status() {
        return System.currentTimeMillis() < statusUntil ? status : null;
    }

    private static void say(String text, long ms) {
        status = text;
        statusUntil = System.currentTimeMillis() + ms;
    }

    public static void open() {
        if (busy) {
            return;
        }
        busy = true;
        say("Opening feedback ...", 30_000);
        Thread thread = new Thread(() -> {
            try {
                String token = verify();
                MinecraftClient.getInstance().execute(() ->
                        net.minecraft.util.Util.getOperatingSystem().open(URI.create(PAGE + token)));
                say("Opened in your browser", 6_000);
            } catch (Failure f) {
                say(f.getMessage(), 8_000);
            } catch (Exception e) {
                BaulooStylesClient.LOGGER.warn("[Review] failed: {}", e.toString());
                say("Feedback is not reachable right now", 8_000);
            } finally {
                busy = false;
            }
        }, "Bauloo Styles review");
        thread.setDaemon(true);
        thread.start();
    }

    private static String verify() throws Exception {
        String base = StylesCloud.publishUrl();
        if (base == null) {
            throw new Failure("Feedback is not reachable right now");
        }
        JsonObject body = new JsonObject();
        // only the same hashed PC id as the player count: one review per PC, nothing about the account
        body.addProperty("device", StylesStats.deviceHash());
        JsonObject answer = post(base + "/review/anon", body.toString());
        if (!answer.has("token")) {
            throw new Failure(answer.has("message") ? answer.get("message").getAsString() : "Feedback is not reachable right now");
        }
        return answer.get("token").getAsString();
    }

    private static JsonObject post(String url, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject answer = JsonParser.parseString(response.body()).getAsJsonObject();
        if (response.statusCode() != 200 && answer.has("message")) {
            throw new Failure(answer.get("message").getAsString());
        }
        return answer;
    }

    /** a message for the player, no stack trace */
    private static final class Failure extends Exception {
        Failure(String message) {
            super(message);
        }
    }
}

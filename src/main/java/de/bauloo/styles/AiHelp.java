package de.bauloo.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * "Ask Bauloo AI": the help chat in the menu. The question goes to the cloud worker, which asks
 * Workers AI with a description of the mod. Only the question, the last few turns and the hashed
 * device id (for the daily limit) are sent. The chat is kept in config/bauloo-styles/ai_chat.json
 * (the last 60 messages), so it is still there after a restart; CLEAR empties it.
 */
public final class AiHelp {

    /** the chat so far: {"me" or "ai", text} */
    public static final List<String[]> MESSAGES = new ArrayList<>();
    private static final int KEEP = 60;
    private static boolean loaded;
    public static final int PER_DAY = 15;
    public static final String[] QUICK = {"Style not loading?", "How do I get VIP?", "How do I upload a pack?"};

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile boolean waiting;
    /** questions left today, -1 = not known yet */
    private static volatile int left = -1;

    private AiHelp() {
    }

    private static java.nio.file.Path file() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles/ai_chat.json");
    }

    /** Reads the saved chat once (render thread). */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            java.nio.file.Path f = file();
            if (!java.nio.file.Files.exists(f)) {
                return;
            }
            JsonObject o = JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
            for (var e : o.getAsJsonArray("messages")) {
                JsonObject m = e.getAsJsonObject();
                String who = m.get("who").getAsString();
                if (who.equals("me") || who.equals("ai")) {
                    MESSAGES.add(new String[]{who, m.get("text").getAsString()});
                }
            }
            // the questions left only count for the day they were seen
            if (o.has("left") && o.has("day") && o.get("day").getAsString().equals(java.time.LocalDate.now().toString())) {
                left = o.get("left").getAsInt();
            }
        } catch (Exception e) {
            BaulooStylesClient.LOGGER.warn("[AI] could not read the saved chat: {}", e.toString());
        }
    }

    private static void save() {
        while (MESSAGES.size() > KEEP) {
            MESSAGES.remove(0);
        }
        try {
            JsonObject o = new JsonObject();
            JsonArray list = new JsonArray();
            for (String[] m : MESSAGES) {
                JsonObject e = new JsonObject();
                e.addProperty("who", m[0]);
                e.addProperty("text", m[1]);
                list.add(e);
            }
            o.add("messages", list);
            o.addProperty("left", left);
            o.addProperty("day", java.time.LocalDate.now().toString());
            java.nio.file.Files.createDirectories(file().getParent());
            java.nio.file.Files.writeString(file(), o.toString());
        } catch (Exception e) {
            BaulooStylesClient.LOGGER.warn("[AI] could not save the chat: {}", e.toString());
        }
    }

    private static long lastLeftCheck;

    /** Asks the worker how many questions are left today (when the chat opens, at most every 30 s). */
    public static void refreshLeft() {
        long now = System.currentTimeMillis();
        String base = StylesCloud.publishUrl();
        if (base == null || waiting || now - lastLeftCheck < 30_000) {
            return;
        }
        lastLeftCheck = now;
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/ai/left?device=" + StylesStats.deviceHash()))
                .timeout(Duration.ofSeconds(15)).GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
            try {
                JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                if (o.has("left")) {
                    int value = o.get("left").getAsInt();
                    MinecraftClient.getInstance().execute(() -> left = value);
                }
            } catch (RuntimeException ignored) {
                // an old worker or no internet: the counter shows what it knew
            }
        });
    }

    /** CLEAR: an empty chat (the daily limit stays). */
    public static void clear() {
        if (!waiting) {
            MESSAGES.clear();
            save();
        }
    }

    public static boolean waiting() {
        return waiting;
    }

    public static int left() {
        return left;
    }

    /** Sends a question (render thread). The answer is added to MESSAGES when it comes. */
    public static void ask(String question) {
        String q = question.trim();
        if (waiting || q.length() < 2) {
            return;
        }
        load();
        String base = StylesCloud.publishUrl();
        MESSAGES.add(new String[]{"me", q});
        if (base == null) {
            MESSAGES.add(new String[]{"ai", "The AI can't be reached right now. Try again later or open a ticket in the Discord."});
            save();
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("question", q);
        body.addProperty("device", StylesStats.deviceHash());
        // the turns before this one, so a follow-up question makes sense
        JsonArray history = new JsonArray();
        for (int i = Math.max(0, MESSAGES.size() - 7); i + 1 < MESSAGES.size() - 1; i++) {
            if ("me".equals(MESSAGES.get(i)[0]) && "ai".equals(MESSAGES.get(i + 1)[0])) {
                JsonObject turn = new JsonObject();
                turn.addProperty("q", MESSAGES.get(i)[1]);
                turn.addProperty("a", MESSAGES.get(i + 1)[1]);
                history.add(turn);
            }
        }
        body.add("history", history);
        waiting = true;
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/ai/ask"))
                .timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((r, error) ->
                MinecraftClient.getInstance().execute(() -> {
                    waiting = false;
                    String text;
                    if (error != null || r == null) {
                        text = "The AI can't be reached right now. Try again in a moment.";
                    } else {
                        try {
                            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                            if (o.has("left")) {
                                left = o.get("left").getAsInt();
                            }
                            text = o.has("answer") ? o.get("answer").getAsString()
                                    : o.has("message") ? o.get("message").getAsString() : "Something went wrong.";
                        } catch (RuntimeException e) {
                            text = "Something went wrong. Try again in a moment.";
                        }
                    }
                    MESSAGES.add(new String[]{"ai", text});
                    save();
                }));
    }
}

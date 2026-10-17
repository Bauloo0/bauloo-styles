package de.bauloo.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bauloo.styles.BaulooStylesClient.Tab;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Public presets: presets anyone can share with everyone, shown with the Minecraft name
 * of whoever shared them. Stored on bauloo's publish server; local uploads are left out,
 * because nobody else has those.
 */
public final class StylesPublicPresets {

    /** local = styles from the sharer's PC, downloaded when the preset is used */
    public record Shared(int id, String name, String author, Map<Tab, String> styles, Map<String, String> extras,
            boolean mine, Map<Tab, PresetShare.LocalFile> local) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static volatile List<Shared> list = List.of();
    private static volatile boolean loading;
    private static volatile String status = "";
    private static volatile int version;

    private StylesPublicPresets() {
    }

    /** bauloo's PC (admin key): may remove every shared preset, not only its own */
    private static volatile boolean admin;

    public static boolean admin() {
        return admin;
    }

    public static List<Shared> all() {
        return list;
    }

    public static boolean loading() {
        return loading;
    }

    /** a message from elsewhere (codes, downloads) in the same status line */
    public static void say(String text) {
        status = text;
    }

    /** Last message for the menu (shared, removed, error). */
    public static String status() {
        return status;
    }

    public static int version() {
        return version;
    }

    /** Loads the list in the background. */
    public static void refresh() {
        String base = StylesCloud.publishUrl();
        if (base == null || loading) {
            return;
        }
        loading = true;
        run(() -> admin = StylesUploadServer.isAdmin());
        run(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/presets?device=" + StylesStats.deviceHash()))
                        .timeout(Duration.ofSeconds(10)).GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    List<Shared> result = new ArrayList<>();
                    JsonArray presets = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("presets");
                    for (JsonElement element : presets) {
                        JsonObject o = element.getAsJsonObject();
                        Map<Tab, String> styles = new EnumMap<>(Tab.class);
                        JsonObject s = o.getAsJsonObject("styles");
                        for (Tab tab : Tab.values()) {
                            if (s.has(tab.prefix)) {
                                styles.put(tab, s.get(tab.prefix).getAsString());
                            }
                        }
                        result.add(new Shared(o.get("id").getAsInt(), o.get("name").getAsString(),
                                o.get("author").getAsString(), styles, StylesPresets.readExtras(o), o.has("mine") && o.get("mine").getAsBoolean(),
                                PresetShare.readLocal(o.has("local") && o.get("local").isJsonObject() ? o.getAsJsonObject("local") : null)));
                    }
                    list = result;
                    version++;
                }
            } catch (Exception e) {
                status = "Could not load public presets";
            } finally {
                loading = false;
            }
        });
    }

    /**
     * Shares one of your presets with everyone, under your Minecraft name. Styles from your PC
     * (My Packs, local uploads) are uploaded with it, so everyone can use them too.
     */
    public static void share(StylesPresets.Preset preset) {
        String base = StylesCloud.publishUrl();
        if (base == null) {
            status = "Sharing is not set up yet";
            return;
        }
        status = "Sharing...";
        run(() -> {
            try {
                send(base, "POST", PresetShare.body(preset, sayer()));
            } catch (Exception e) {
                status = e.getMessage() == null ? "Could not reach the server" : e.getMessage();
            }
        });
    }

    private static java.util.function.Consumer<String> sayer() {
        return text -> status = text;
    }

    public static void remove(Shared shared) {
        String base = StylesCloud.publishUrl();
        if (base == null) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("id", shared.id());
        body.addProperty("device", StylesStats.deviceHash());
        send(base, "DELETE", body);
    }

    private static void send(String base, String method, JsonObject body) {
        status = method.equals("POST") ? "Sharing..." : "Removing...";
        run(() -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/presets"));
                String key = StylesUploadServer.savedKey();
                if (!key.isEmpty()) {
                    builder.header("Authorization", "Bearer " + key);
                }
                HttpRequest request = builder
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .method(method, HttpRequest.BodyPublishers.ofString(body.toString())).build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject answer = JsonParser.parseString(response.body()).getAsJsonObject();
                status = answer.has("message") ? answer.get("message").getAsString() : "";
            } catch (Exception e) {
                status = "Could not reach the server";
            }
            refresh();
        });
    }

    private static void run(Runnable task) {
        Thread thread = new Thread(task, "Bauloo Styles presets");
        thread.setDaemon(true);
        thread.start();
    }
}

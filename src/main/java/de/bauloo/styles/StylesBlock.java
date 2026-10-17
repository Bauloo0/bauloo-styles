package de.bauloo.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * bauloo can shut single accounts out of Bauloo Styles (someone who got the mod passed on
 * without his OK). The list is blocked.json in his cloud repo: Mojang UUIDs, optionally names.
 *
 * A blocked account gets no menu, and its Styles resource packs are switched off. Nothing is
 * deleted and nothing else on the PC is touched - the mod simply does not work for that account.
 * The last list is kept, so it also holds without internet.
 */
public final class StylesBlock {

    private static final String URL =
            "https://raw.githubusercontent.com/Bauloo0/bauloo-styles-cloud/main/blocked.json";

    private static volatile List<String> ids = List.of();
    private static volatile List<String> names = List.of();
    private static boolean packsCleared;

    private StylesBlock() {
    }

    private static Path cache() {
        return FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles").resolve("blocked.json");
    }

    /** Reads the kept list, then fetches the current one in the background. */
    public static void start() {
        try {
            if (Files.exists(cache())) {
                read(Files.readString(cache()));
            }
        } catch (Exception ignored) {
            // no kept list - wait for the download
        }
        Thread t = new Thread(() -> {
            try {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(URL + "?t=" + System.currentTimeMillis() / 60000))
                        .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200) {
                    read(r.body());
                    Files.createDirectories(cache().getParent());
                    Files.writeString(cache(), r.body());
                } else if (r.statusCode() == 404) {
                    read("{}");
                }
            } catch (Exception ignored) {
                // offline - the kept list stays
            }
            MinecraftClient.getInstance().send(StylesBlock::enforce);
        }, "Bauloo Styles block list");
        t.setDaemon(true);
        t.start();
    }

    private static void read(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        ids = list(o.get("uuids"), true);
        names = list(o.get("names"), false);
    }

    private static List<String> list(JsonElement e, boolean uuid) {
        List<String> out = new ArrayList<>();
        if (e instanceof JsonArray a) {
            for (JsonElement x : a) {
                String s = x.getAsString().toLowerCase(Locale.ROOT);
                out.add(uuid ? s.replace("-", "") : s);
            }
        }
        return List.copyOf(out);
    }

    /** Is the account playing right now on the list? */
    public static boolean isBlocked() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSession() == null) {
            return false;
        }
        UUID uuid = mc.getSession().getUuidOrNull();
        if (uuid != null && ids.contains(uuid.toString().replace("-", "").toLowerCase(Locale.ROOT))) {
            return true;
        }
        String name = mc.getSession().getUsername();
        return name != null && names.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Main thread: a blocked account loses the Styles resource packs (once per start). */
    public static void enforce() {
        if (!isBlocked() || packsCleared) {
            return;
        }
        packsCleared = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        ResourcePackManager packs = mc.getResourcePackManager();
        List<String> enabled = new ArrayList<>(packs.getEnabledIds());
        boolean changed = enabled.removeIf(id -> id.startsWith(BaulooStylesClient.MOD_ID + ":")
                || id.startsWith("file/" + StylesCloud.FOLDER_PREFIX) || id.startsWith("file/bauloo_upload_"));
        if (changed) {
            packs.setEnabledProfiles(enabled);
            mc.options.refreshResourcePacks(packs);
            mc.reloadResources();
        }
        BaulooStylesClient.LOGGER.info("Bauloo Styles is not available for this account");
    }
}

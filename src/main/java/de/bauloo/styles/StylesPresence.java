package de.bauloo.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tab icon: a tornado left of the name of everyone who plays with Bauloo Styles.
 * White = has the mod, cool blue = has an upload key, red = has an admin key.
 *
 * A few seconds after joining a server (or switching account), and then every few minutes,
 * the mod tells the cloud worker "this Minecraft account plays with Bauloo Styles" (switch
 * "Show me in tab" in Extras; off removes you). The role comes only from a key the worker
 * checked. Every 30 seconds (every 4 while the tab list is open) it asks which of the
 * listed players use the mod.
 */
public final class StylesPresence {

    private static final Identifier TAB_FONT = Identifier.of(BaulooStylesClient.MOD_ID, "tab");
    private static final int RED = 0xFF5555;
    private static final int BLUE = 0x4DB8FF;
    private static final int GOLD = 0xFFC23D;
    private static final int PINK = 0xFF6FD8;
    private static final int WHITE = 0xFFFFFF;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    /** uuid -> "admin" / "uploader" / "vip" / "media" / "user" for players seen with the mod */
    private static final Map<UUID, String> ROLES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ASKED = new ConcurrentHashMap<>();
    private static final long PING_EVERY = 4 * 60_000;
    private static final long LOOKUP_EVERY = 30_000;
    private static volatile long lastPing;
    private static volatile long lastLookup;
    /** the connection and the UUID the server gave us - a new one means joined a server or switched account */
    private static Object lastConnection;
    private static UUID lastId;

    private StylesPresence() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(StylesPresence::tick);
    }

    /** "Show me in tab" was switched: tell the worker right away. */
    public static void settingChanged() {
        lastPing = 0;
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null || StylesCloud.publishUrl() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID id = mc.player.getUuid();
        if (mc.getNetworkHandler() != lastConnection || !id.equals(lastId)) {
            // joined a server or switched account (IAS): tell the worker in 3 s, ask about the others in 5 s
            lastConnection = mc.getNetworkHandler();
            lastId = id;
            lastPing = now - PING_EVERY + 3_000;
            lastLookup = now - LOOKUP_EVERY + 5_000;
            ASKED.clear();
        }
        if (now - lastPing > PING_EVERY) {
            lastPing = now;
            ping(mc);
        }
        boolean tabOpen = mc.options.playerListKey.isPressed();
        if (now - lastLookup > (tabOpen ? 4_000 : LOOKUP_EVERY)) {
            lastLookup = now;
            lookup(mc);
        }
    }

    private static HttpRequest.Builder request(String path, JsonObject body) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(StylesCloud.publishUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return b;
    }

    private static void ping(MinecraftClient mc) {
        // the UUID the server gave us - the tab list of other players shows that one, not the login's
        // (they differ for offline accounts and on servers with their own login)
        UUID me = mc.player.getUuid();
        boolean show = StylesUiConfig.showInTab;
        JsonObject body = new JsonObject();
        body.addProperty("uuid", me.toString());
        body.addProperty("show", show);
        HttpRequest.Builder b = request("/presence", body);
        String key = StylesUploadServer.savedKey();
        if (!key.isEmpty()) {
            b.header("Authorization", "Bearer " + key).header("X-Device", StylesUploadServer.deviceId());
        }
        HTTP.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
            try {
                JsonElement role = JsonParser.parseString(r.body()).getAsJsonObject().get("role");
                myRole = role != null && !role.isJsonNull() ? role.getAsString() : null;
                if (show && role != null && !role.isJsonNull()) {
                    ROLES.put(me, role.getAsString());
                } else {
                    ROLES.remove(me);
                }
            } catch (RuntimeException ignored) {
                // the worker is not reachable - try again at the next ping
            }
        });
    }

    private static void lookup(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        List<UUID> ask = new ArrayList<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            UUID id = entry.getProfile().id();
            Long when = ASKED.get(id);
            if (when == null || now - when > LOOKUP_EVERY) {
                ask.add(id);
                if (ask.size() == 100) {
                    break;
                }
            }
        }
        if (ask.isEmpty()) {
            return;
        }
        JsonArray list = new JsonArray();
        for (UUID id : ask) {
            list.add(id.toString());
            ASKED.put(id, now);
        }
        JsonObject body = new JsonObject();
        body.add("uuids", list);
        HTTP.sendAsync(request("/presence/lookup", body).build(), HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
            try {
                JsonObject players = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("players");
                for (UUID id : ask) {
                    JsonElement role = players.get(id.toString().replace("-", ""));
                    if (role == null) {
                        ROLES.remove(id);
                    } else {
                        ROLES.put(id, role.getAsString());
                    }
                }
            } catch (RuntimeException ignored) {
                // keep what we knew
            }
        });
    }

    /** your own role as the worker sent it last ("vip", "admin" ...), also when you hide from the tab list */
    private static volatile String myRole;

    /** bauloo's admin key on this PC (as the worker sent it last) */
    public static boolean isAdmin() {
        return "admin".equals(myRole);
    }

    /** VIP (or more): unlocks extras like My Packs. */
    public static boolean isVip() {
        String r = myRole;
        return "vip".equals(r) || "media".equals(r) || "uploader".equals(r) || "admin".equals(r);
    }

    /** The tornado for a player's tab entry, or null when they do not use the mod. */
    public static Text icon(UUID id) {
        String role = ROLES.get(id);
        if (role == null) {
            return null;
        }
        int color = "admin".equals(role) ? RED : "uploader".equals(role) ? BLUE : "vip".equals(role) ? GOLD : "media".equals(role) ? PINK : WHITE;
        return Text.literal("").styled(style -> style.withFont(new StyleSpriteSource.Font(TAB_FONT)).withColor(color));
    }
}

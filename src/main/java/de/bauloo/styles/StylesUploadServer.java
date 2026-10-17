package de.bauloo.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The upload page. A tiny web server that only listens on 127.0.0.1, only while
 * it is needed, and only answers requests that carry a random token. The page
 * lets a player drop a whole texture pack and keeps only the chosen part.
 */
public final class StylesUploadServer {

    private static final long MAX_UPLOAD = 300L * 1024 * 1024;
    private static final long MAX_IMAGE = 8L * 1024 * 1024;
    private static final long MAX_JSON = 64L * 1024;
    private static final long IDLE_MS = 30 * 60 * 1000L;

    private static final class Pending {
        final Path zip;
        final String fileName;
        Path preview;
        /** pictures drawn from the pack itself, per category */
        final Map<String, Path> autoPreviews = new ConcurrentHashMap<>();
        /** true once the player picked a picture himself - that always wins */
        boolean customPreview;

        Pending(Path zip, String fileName) {
            this.zip = zip;
            this.fileName = fileName;
        }
    }

    private static ServerSocket server;
    private static String token;
    private static volatile long lastUse;
    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    private StylesUploadServer() {
    }

    public static synchronized void open() {
        try {
            if (server == null || server.isClosed()) {
                start();
            }
            lastUse = System.currentTimeMillis();
            Util.getOperatingSystem().open(URI.create("http://127.0.0.1:" + server.getLocalPort() + "/?t=" + token));
        } catch (IOException e) {
            BaulooStylesClient.LOGGER.warn("Upload page could not start: {}", e.toString());
        }
    }

    private static void start() throws IOException {
        server = new ServerSocket(0, 20, InetAddress.getLoopbackAddress());
        server.setSoTimeout(60_000);
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        token = HexFormat.of().formatHex(bytes);
        Thread thread = new Thread(StylesUploadServer::acceptLoop, "Bauloo Styles upload page");
        thread.setDaemon(true);
        thread.start();
    }

    private static void acceptLoop() {
        ServerSocket socket = server;
        while (!socket.isClosed()) {
            try {
                Socket client = socket.accept();
                Thread worker = new Thread(() -> handle(client), "Bauloo Styles upload request");
                worker.setDaemon(true);
                worker.start();
            } catch (SocketTimeoutException timeout) {
                if (System.currentTimeMillis() - lastUse > IDLE_MS) {
                    stop();
                }
            } catch (IOException e) {
                return;
            }
        }
    }

    private static synchronized void stop() {
        try {
            if (server != null) {
                server.close();
            }
        } catch (IOException ignored) {
        }
        for (Pending pending : PENDING.values()) {
            deleteQuietly(pending.zip);
            deleteQuietly(pending.preview);
            pending.autoPreviews.values().forEach(StylesUploadServer::deleteQuietly);
        }
        PENDING.clear();
    }

    private static Path tempDir() throws IOException {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles/upload-tmp");
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * bauloo's PC publishes straight to GitHub through its API (GitHubPublisher) once his
     * GitHub token is saved - like git, up to 95 MB, but without starting any program.
     * Everyone else publishes through the publish server with a key.
     */
    private static boolean canPublish() {
        return GitHubPublisher.hasToken(keyFile());
    }

    /** true when this PC holds a valid publish key (uploader or admin). Network call: never on the render thread. */
    public static boolean hasKeyRole() {
        return role(false) != null;
    }

    /** bauloo's own PC: the only one that can publish straight to GitHub */
    public static boolean ownerPc() {
        return canPublish();
    }

    // ------------------------------------------------------ publish key (friends)

    private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10)).build();
    private static volatile String cachedRole;
    private static volatile String cachedName;
    private static volatile long cachedAt;
    private static volatile String lastKeyError;

    /** Random id of this PC. A friend key gets tied to it the first time it is used. */
    static String deviceId() {
        Path file = keyFile().resolveSibling("device-id.txt");
        try {
            if (Files.exists(file)) {
                String id = Files.readString(file).trim();
                if (id.matches("[0-9a-f]{64}")) {
                    return id;
                }
            }
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            String id = HexFormat.of().formatHex(bytes);
            Files.createDirectories(file.getParent());
            Files.writeString(file, id);
            return id;
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * The key a friend got from bauloo. Kept in the user folder, not in any
     * Minecraft instance, so sharing a modpack never shares the key.
     */
    private static Path keyFile() {
        return Path.of(System.getProperty("user.home"), ".bauloo-styles", "publish-token.txt");
    }

    private static String readKey() {
        try {
            Path file = keyFile();
            return Files.exists(file) ? Files.readString(file).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static java.net.http.HttpResponse<String> worker(String method, String path, String key,
            String contentType, byte[] body) throws IOException, InterruptedException {
        String base = StylesCloud.publishUrl();
        if (base == null) {
            throw new IOException("Online publishing is not set up yet");
        }
        java.net.http.HttpRequest.Builder request = java.net.http.HttpRequest.newBuilder(URI.create(base + path))
                .timeout(java.time.Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + key)
                .header("X-Device", deviceId());
        if (body == null) {
            request.method(method, java.net.http.HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", contentType)
                    .method(method, java.net.http.HttpRequest.BodyPublishers.ofByteArray(body));
        }
        return HTTP.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    /** True when this PC holds bauloo's admin key (asks the worker; call off the render thread). */
    public static boolean isAdmin() {
        return "admin".equals(role(false));
    }

    /** The role of this PC's key ("vip", "admin" ...) or null; asks the worker, call off the render thread. */
    public static String keyRole() {
        return role(false);
    }

    /** The saved publish key, or "" - sent along so the admin key can remove anyone's preset. */
    public static String savedKey() {
        return readKey();
    }

    /** Asks the worker who a key belongs to. Cached for a minute. Returns null for no or unknown key. */
    private static String role(boolean fresh) {
        String key = readKey();
        if (key.isEmpty() || StylesCloud.publishUrl() == null) {
            return null;
        }
        if (!fresh && System.currentTimeMillis() - cachedAt < 60_000) {
            return cachedRole;
        }
        try {
            var response = worker("GET", "/whoami", key, null, null);
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            boolean ok = response.statusCode() == 200 && body.get("ok").getAsBoolean();
            cachedRole = ok ? str(body, "role") : null;
            cachedName = ok ? str(body, "name") : null;
            lastKeyError = ok ? null : str(body, "message");
        } catch (Exception e) {
            cachedRole = null;
            cachedName = null;
            lastKeyError = "Could not reach the publish server";
        }
        cachedAt = System.currentTimeMillis();
        return cachedRole;
    }

    /** Forwards a small JSON call to the worker and passes its answer back to the page. */
    private static void relay(OutputStream out, String method, String path, JsonObject body) throws IOException {
        try {
            var response = worker(method, path, readKey(), "application/json",
                    body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8));
            JsonObject answer = JsonParser.parseString(response.body()).getAsJsonObject();
            json(out, response.statusCode() == 200 ? 200 : 400, answer);
        } catch (Exception e) {
            json(out, 502, message(false, "Could not reach the publish server"));
        }
    }

    private static byte[] multipart(String boundary, Map<String, String> fields, Path pack, Path preview) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                    + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"pack\"; filename=\"pack.zip\"\r\n"
                + "Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(Files.readAllBytes(pack));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        if (preview != null && Files.exists(preview)) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"preview\"; filename=\"preview.png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(Files.readAllBytes(preview));
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    // -------------------------------------------------------------------- http

    private static void handle(Socket socket) {
        try (socket) {
            socket.setSoTimeout(120_000);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            String requestLine = readLine(in);
            if (requestLine == null) {
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return;
            }
            String method = parts[0];
            String target = parts[1];
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
                }
            }
            lastUse = System.currentTimeMillis();

            int port = socket.getLocalPort();
            String host = headers.getOrDefault("host", "");
            if (!host.equals("127.0.0.1:" + port) && !host.equals("localhost:" + port)) {
                send(out, 403, "text/plain", "Forbidden");
                return;
            }
            String origin = headers.get("origin");
            if (origin != null && !origin.equals("http://127.0.0.1:" + port) && !origin.equals("http://localhost:" + port)) {
                send(out, 403, "text/plain", "Forbidden");
                return;
            }

            String path = target;
            Map<String, String> query = new HashMap<>();
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                for (String pair : target.substring(q + 1).split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        query.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                                URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                    }
                }
            }
            String given = query.getOrDefault("t", "");
            if (!MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
                send(out, 403, "text/plain", "This link has expired. Open the upload page from the styles menu again.");
                return;
            }

            long length = 0;
            try {
                length = Long.parseLong(headers.getOrDefault("content-length", "0"));
            } catch (NumberFormatException ignored) {
            }
            if (length < 0 || length > MAX_UPLOAD) {
                send(out, 413, "text/plain", "Too big");
                return;
            }

            route(method, path, query, length, in, out);
        } catch (Exception e) {
            BaulooStylesClient.LOGGER.warn("Upload page request failed: {}", e.toString());
        }
    }

    private static void route(String method, String path, Map<String, String> query, long length,
            InputStream in, OutputStream out) throws IOException {
        if ("GET".equals(method) && "/".equals(path)) {
            try (InputStream page = StylesUploadServer.class.getResourceAsStream("/assets/bauloo_styles/upload/index.html")) {
                if (page == null) {
                    send(out, 500, "text/plain", "Page missing");
                    return;
                }
                String html = new String(page.readAllBytes(), StandardCharsets.UTF_8);
                sendBytes(out, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
            }
            return;
        }
        if ("GET".equals(method) && path.matches("/img/[a-z0-9_]+\\.png")) {
            try (InputStream image = StylesUploadServer.class.getResourceAsStream(
                    "/assets/bauloo_styles/textures/preview/" + path.substring(5))) {
                if (image == null) {
                    send(out, 404, "text/plain", "Not found");
                } else {
                    sendBytes(out, 200, "image/png", image.readAllBytes());
                }
            }
            return;
        }
        if ("GET".equals(method) && "/api/icon".equals(path)) {
            Pending pending = PENDING.get(query.getOrDefault("id", ""));
            Path picture = pending == null ? null : picture(pending, query.getOrDefault("category", ""));
            if (picture == null || !Files.exists(picture)) {
                send(out, 404, "text/plain", "Not found");
            } else {
                sendBytes(out, 200, "image/png", Files.readAllBytes(picture));
            }
            return;
        }
        if ("GET".equals(method) && "/api/state".equals(path)) {
            JsonObject state = new JsonObject();
            String role = role(false);
            state.addProperty("canPublish", canPublish() || (role != null && !"vip".equals(role) && !"media".equals(role)));
            state.addProperty("git", canPublish());
            state.addProperty("online", StylesCloud.publishUrl() != null);
            if (role != null) {
                state.addProperty("role", role);
                state.addProperty("who", cachedName);
            }
            state.addProperty("accent", String.format("#%06X", StylesUiConfig.accent & 0xFFFFFF));
            JsonArray cloudCategories = new JsonArray();
            for (CloudCategories.Def d : CloudCategories.all()) {
                JsonObject c = new JsonObject();
                c.addProperty("id", d.id());
                c.addProperty("name", d.name());
                cloudCategories.add(c);
            }
            state.add("categories", cloudCategories);
            JsonArray uploads = new JsonArray();
            for (String category : StylesPackFilter.categories()) {
                for (Path file : StylesUploadManager.list(category)) {
                    String name = file.getFileName().toString();
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        continue;
                    }
                    JsonObject o = new JsonObject();
                    o.addProperty("category", category);
                    o.addProperty("file", name);
                    o.addProperty("name", name.substring(0, name.length() - 4));
                    uploads.add(o);
                }
            }
            state.add("uploads", uploads);
            json(out, 200, state);
            return;
        }
        if (!"POST".equals(method)) {
            send(out, 404, "text/plain", "Not found");
            return;
        }
        switch (path) {
            case "/api/analyze" -> analyze(query, length, in, out);
            case "/api/preview" -> preview(query, length, in, out);
            case "/api/add" -> add(readJson(in, length), out, false);
            case "/api/publish" -> add(readJson(in, length), out, true);
            case "/api/key" -> {
                JsonObject body = readJson(in, length);
                String key = body == null ? "" : str(body, "key").trim();
                if (key.length() < 16 || key.length() > 200 || !key.matches("[\\x21-\\x7E]+")) {
                    json(out, 400, message(false, "That does not look like a key"));
                    return;
                }
                Path file = keyFile();
                String previous = readKey();
                Files.createDirectories(file.getParent());
                Files.writeString(file, key);
                String role = role(true);
                if (role == null) {
                    if (previous.isEmpty()) {
                        Files.deleteIfExists(file);
                    } else {
                        Files.writeString(file, previous);
                    }
                    cachedAt = 0;
                    String reason = StylesCloud.publishUrl() == null ? "Online publishing is not set up yet"
                            : lastKeyError == null || lastKeyError.isEmpty() || "Unknown key".equals(lastKeyError)
                            ? "This key is not valid" : lastKeyError;
                    json(out, 401, message(false, reason));
                    return;
                }
                JsonObject result = message(true, "Key saved - welcome, " + cachedName);
                result.addProperty("role", role);
                // My Packs and the tab icon follow the new key at once
                MyPacks.recheck();
                StylesPresence.settingChanged();
                json(out, 200, result);
            }
            case "/api/github" -> {
                JsonObject body = readJson(in, length);
                String gh = body == null ? "" : str(body, "token").trim();
                if (!gh.matches("[A-Za-z0-9_]{20,255}")) {
                    json(out, 400, message(false, "That does not look like a GitHub token"));
                    return;
                }
                GitHubPublisher.Result check = GitHubPublisher.check(gh);
                if (check.ok()) {
                    Path file = GitHubPublisher.tokenFile(keyFile());
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, gh);
                }
                json(out, check.ok() ? 200 : 400, message(check.ok(), check.message()));
            }
            case "/api/github/clear" -> {
                Files.deleteIfExists(GitHubPublisher.tokenFile(keyFile()));
                json(out, 200, message(true, "GitHub token removed from this PC"));
            }
            case "/api/stats" -> {
                JsonObject stats = StylesStats.fetch();
                if (stats == null) {
                    json(out, 502, message(false, "No stats"));
                } else {
                    json(out, 200, stats);
                }
            }
            case "/api/online" -> {
                if (!"admin".equals(role(false))) {
                    json(out, 403, message(false, "Only bauloo can see who plays"));
                    return;
                }
                relay(out, "GET", "/admin/online", null);
            }
            case "/api/key/clear" -> {
                Files.deleteIfExists(keyFile());
                cachedRole = null;
                cachedName = null;
                cachedAt = 0;
                MyPacks.recheck();
                StylesPresence.settingChanged();
                json(out, 200, message(true, "Key removed from this PC"));
            }
            case "/api/keys/list", "/api/keys/create", "/api/keys/revoke" -> {
                JsonObject body = readJson(in, length);
                if (!"admin".equals(role(false))) {
                    json(out, 403, message(false, "Only bauloo can manage keys"));
                    return;
                }
                String workerMethod = switch (path) {
                    case "/api/keys/list" -> "GET";
                    case "/api/keys/create" -> "POST";
                    default -> "DELETE";
                };
                relay(out, workerMethod, "/admin/keys", "GET".equals(workerMethod) ? null : body);
            }
            case "/api/cloud/list" -> cloudList(out);
            case "/api/cloud/remove" -> cloudRemove(readJson(in, length), out);
            case "/api/delete" -> {
                JsonObject body = readJson(in, length);
                boolean ok = body != null && StylesUploadManager.delete(str(body, "category"), str(body, "file"));
                json(out, ok ? 200 : 400, message(ok, ok ? "Removed" : "Could not remove that upload"));
            }
            default -> send(out, 404, "text/plain", "Not found");
        }
    }

    // ----------------------------------------------------------- cloud styles

    /** The published styles, fresh from GitHub, marked with what this PC may remove. */
    private static void cloudList(OutputStream out) throws IOException {
        String role = role(false);
        boolean git = canPublish();
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        JsonArray list = new JsonArray();
        try {
            var request = java.net.http.HttpRequest.newBuilder(URI.create("https://api.github.com/repos/"
                            + StylesCloud.OWNER + "/" + StylesCloud.REPO + "/contents/index.json?ref=main"))
                    .header("Accept", "application/vnd.github.raw+json")
                    .header("User-Agent", "bauloo-styles-mod")
                    .timeout(java.time.Duration.ofSeconds(15)).GET().build();
            var response = HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                json(out, 502, message(false, "Could not load the published styles"));
                return;
            }
            JsonObject index = JsonParser.parseString(response.body()).getAsJsonObject();
            for (var element : index.getAsJsonArray("styles")) {
                JsonObject s = element.getAsJsonObject();
                String by = str(s, "by");
                boolean mine = "admin".equals(role) || (role != null && !by.isEmpty() && by.equals(cachedName))
                        || (git && role == null);
                JsonObject o = new JsonObject();
                o.addProperty("id", str(s, "id"));
                o.addProperty("category", str(s, "category"));
                o.addProperty("name", str(s, "name"));
                o.addProperty("by", by.isEmpty() ? "bauloo" : by);
                o.addProperty("added", str(s, "added"));
                o.addProperty("canRemove", mine);
                list.add(o);
            }
        } catch (Exception e) {
            json(out, 502, message(false, "Could not load the published styles"));
            return;
        }
        result.add("styles", list);
        json(out, 200, result);
    }

    /** Removes a published style for everyone - through the key if there is one, else through git. */
    private static void cloudRemove(JsonObject body, OutputStream out) throws IOException {
        String id = body == null ? "" : str(body, "id");
        String category = id.contains("_") ? id.substring(0, id.indexOf('_')) : "";
        if (!id.matches("[a-z][a-z0-9]{1,15}_[a-z0-9_]{1,40}") || !CloudCategories.known(category)) {
            json(out, 400, message(false, "Unknown style"));
            return;
        }
        if (canPublish()) {
            GitHubPublisher.Result result = GitHubPublisher.remove(keyFile(), id);
            if (result.ok()) {
                StylesCloud.refresh(true);
            }
            json(out, result.ok() ? 200 : 400, message(result.ok(), result.message()));
            return;
        }
        if (role(false) != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("id", id);
            try {
                var response = worker("POST", "/remove", readKey(), "application/json",
                        payload.toString().getBytes(StandardCharsets.UTF_8));
                JsonObject answer = JsonParser.parseString(response.body()).getAsJsonObject();
                if (response.statusCode() == 200) {
                    StylesCloud.refresh(true);
                }
                json(out, response.statusCode() == 200 ? 200 : 400, answer);
            } catch (Exception e) {
                json(out, 502, message(false, "Could not reach the publish server"));
            }
            return;
        }
        json(out, 403, message(false, "Removing needs a key from bauloo"));
    }

    // --------------------------------------------------------------- actions

    private static void analyze(Map<String, String> query, long length, InputStream in, OutputStream out) throws IOException {
        String fileName = query.getOrDefault("name", "pack.zip");
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            json(out, 400, message(false, "Please drop a .zip texture pack"));
            return;
        }
        byte[] idBytes = new byte[8];
        new SecureRandom().nextBytes(idBytes);
        String id = HexFormat.of().formatHex(idBytes);
        Path zip = tempDir().resolve(id + ".zip");
        copyExactly(in, zip, length);
        Map<String, Integer> found;
        try {
            found = StylesPackFilter.analyze(zip);
        } catch (IOException e) {
            deleteQuietly(zip);
            json(out, 400, message(false, "That file is not a readable zip"));
            return;
        }
        Pending pending = new Pending(zip, fileName);
        PENDING.put(id, pending);
        // The pack's own icon is the default picture until the player picks another one.
        Path icon = tempDir().resolve(id + ".png");
        if (packIcon(zip, icon)) {
            pending.preview = icon;
        }
        // A picture drawn from the pack itself for every part it contains - no need to ask for one.
        JsonArray auto = new JsonArray();
        for (String category : StylesPackFilter.categories()) {
            if (found.getOrDefault(category, 0) == 0) {
                continue;
            }
            Path picture = tempDir().resolve(id + "-" + category + ".png");
            boolean made = "sky".equals(category)
                    ? skyPreview(zip, picture)
                    : StylesAutoPreview.render(zip, category, picture);
            if (made) {
                pending.autoPreviews.put(category, picture);
                auto.add(category);
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("icon", pending.preview != null);
        result.add("auto", auto);
        result.addProperty("id", id);
        result.addProperty("name", cleanName(fileName.substring(0, fileName.length() - 4)));
        JsonObject counts = new JsonObject();
        found.forEach(counts::addProperty);
        result.add("found", counts);
        json(out, 200, result);
    }

    /** Copies pack.png out of the zip as a 256x256 square. Small pixel icons are scaled without blur. */
    private static boolean packIcon(Path zip, Path target) {
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (!"pack.png".equalsIgnoreCase(entry.getName())) {
                    continue;
                }
                byte[] data = in.readNBytes((int) MAX_IMAGE + 1);
                if (data.length > MAX_IMAGE) {
                    return false;
                }
                BufferedImage source = ImageIO.read(new java.io.ByteArrayInputStream(data));
                if (source == null) {
                    return false;
                }
                writeSquare(source, target);
                return true;
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
        return false;
    }

    /**
     * Cuts the front face (middle of the lower row) out of the pack's sky cube map.
     * Only that region is decoded, scaled down on the way, so a 6144x4096 texture stays cheap.
     */
    static boolean skyPreview(Path zip, Path target) {
        try (java.util.zip.ZipFile file = new java.util.zip.ZipFile(zip.toFile())) {
            java.util.zip.ZipEntry best = null;
            long bestScore = 0;
            for (java.util.zip.ZipEntry entry : java.util.Collections.list(file.entries())) {
                String name = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                boolean skyTexture = (name.startsWith("assets/minecraft/optifine/sky/")
                        || name.startsWith("assets/minecraft/mcpatcher/sky/")) && name.endsWith(".png");
                if (entry.isDirectory() || !skyTexture) {
                    continue;
                }
                int[] size = imageSize(file, entry);
                // Cube maps are laid out 3 wide, 2 high.
                if (size == null || size[0] * 2 != size[1] * 3 || size[0] < 300) {
                    continue;
                }
                String base = name.substring(name.lastIndexOf('/') + 1);
                boolean overlay = base.contains("star") || base.contains("night") || base.contains("flare")
                        || base.contains("rainbow") || base.contains("moon") || base.contains("sun");
                long score = (long) size[0] * size[1] + Math.min(entry.getSize(), Integer.MAX_VALUE) / 1024;
                if (overlay) {
                    score /= 16;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = entry;
                }
            }
            if (best == null) {
                return false;
            }
            try (InputStream in = file.getInputStream(best);
                 javax.imageio.stream.ImageInputStream stream = ImageIO.createImageInputStream(in)) {
                java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) {
                    return false;
                }
                javax.imageio.ImageReader reader = readers.next();
                try {
                    reader.setInput(stream);
                    int face = reader.getWidth(0) / 3;
                    javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
                    param.setSourceRegion(new java.awt.Rectangle(face, face, face, face));
                    int step = Math.max(1, face / 512);
                    param.setSourceSubsampling(step, step, 0, 0);
                    writeSquare(reader.read(0, param), target);
                    return true;
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static int[] imageSize(java.util.zip.ZipFile file, java.util.zip.ZipEntry entry) {
        try (InputStream in = file.getInputStream(entry);
             javax.imageio.stream.ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void writeSquare(BufferedImage source, Path target) throws IOException {
        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage square = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, side < 256
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, 256, 256,
                (source.getWidth() - side) / 2, (source.getHeight() - side) / 2,
                (source.getWidth() + side) / 2, (source.getHeight() + side) / 2, null);
        g.dispose();
        ImageIO.write(square, "png", target.toFile());
    }

    /** Own picture first, then the sky cut-out for sky styles, then the pack icon. */
    private static Path picture(Pending pending, String category) {
        Path auto = pending.autoPreviews.get(category);
        if (!pending.customPreview && auto != null) {
            return auto;
        }
        return pending.preview;
    }

    private static void preview(Map<String, String> query, long length, InputStream in, OutputStream out) throws IOException {
        Pending pending = PENDING.get(query.getOrDefault("id", ""));
        if (pending == null || length > MAX_IMAGE) {
            json(out, 400, message(false, "Picture could not be used"));
            return;
        }
        byte[] data = readExact(in, length);
        BufferedImage source = ImageIO.read(new java.io.ByteArrayInputStream(data));
        if (source == null) {
            json(out, 400, message(false, "Use a PNG or JPG picture"));
            return;
        }
        Path png = tempDir().resolve(query.get("id") + ".png");
        writeSquare(source, png);
        pending.preview = png;
        pending.customPreview = true;
        json(out, 200, message(true, "Picture ready"));
    }

    private static void add(JsonObject body, OutputStream out, boolean publish) throws IOException {
        if (body == null) {
            json(out, 400, message(false, "Bad request"));
            return;
        }
        Pending pending = PENDING.get(str(body, "id"));
        String category = str(body, "category");
        String name = cleanName(str(body, "name"));
        if (pending == null || !java.util.List.of(StylesPackFilter.categories()).contains(category)) {
            json(out, 400, message(false, "Drop the pack again - the upload expired"));
            return;
        }
        if (name.isEmpty()) {
            json(out, 400, message(false, "Give the style a name"));
            return;
        }
        boolean viaGit = publish && canPublish();
        if (publish && !viaGit && (role(false) == null || "vip".equals(role(false)) || "media".equals(role(false)))) {
            json(out, 403, message(false, "Publishing for everyone needs an upload key from bauloo"));
            return;
        }

        Path folder = publish ? tempDir().resolve("publish") : StylesUploadManager.root().resolve(category);
        Files.createDirectories(folder);
        String base = name;
        int n = 2;
        while (!publish && Files.exists(folder.resolve(base + ".zip"))) {
            base = name + " (" + n++ + ")";
        }
        Path zip = folder.resolve(base + ".zip");
        int textures;
        try {
            textures = StylesPackFilter.extract(pending.zip, category, zip);
        } catch (IOException e) {
            json(out, 400, message(false, "The pack could not be read: " + e.getMessage()));
            return;
        }
        if (textures == 0) {
            json(out, 400, message(false, "This pack has no " + category + " textures"));
            return;
        }
        Path png = folder.resolve(base + ".png");
        Path picture = picture(pending, category);
        if (picture != null && Files.exists(picture)) {
            Files.copy(picture, png, StandardCopyOption.REPLACE_EXISTING);
        }

        if (!publish) {
            StylesUploadManager.changed();
            JsonObject result = message(true, "Added \"" + base + "\" with " + textures + " textures");
            result.addProperty("textures", textures);
            json(out, 200, result);
            return;
        }

        if (!viaGit) {
            try {
                String boundary = "----bauloo" + Long.toHexString(new SecureRandom().nextLong());
                Map<String, String> fields = new java.util.LinkedHashMap<>();
                fields.put("category", category);
                fields.put("name", name);
                var response = worker("POST", "/publish", readKey(), "multipart/form-data; boundary=" + boundary,
                        multipart(boundary, fields, zip, png));
                JsonObject answer = JsonParser.parseString(response.body()).getAsJsonObject();
                if (response.statusCode() == 200) {
                    StylesCloud.refresh(true);
                }
                json(out, response.statusCode() == 200 ? 200 : 400, answer);
            } catch (Exception e) {
                json(out, 502, message(false, "Could not reach the publish server"));
            } finally {
                deleteQuietly(zip);
                deleteQuietly(png);
            }
            return;
        }

        try {
            GitHubPublisher.Result result = GitHubPublisher.publish(keyFile(), category, name, zip, png);
            if (result.ok()) {
                StylesCloud.refresh(true);
            }
            json(out, result.ok() ? 200 : 400, message(result.ok(), result.message()));
        } finally {
            deleteQuietly(zip);
            deleteQuietly(png);
        }
    }

    // --------------------------------------------------------------- helpers

    private static String cleanName(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").replace('_', ' ').trim();
        return cleaned.length() > 40 ? cleaned.substring(0, 40).trim() : cleaned;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static JsonObject message(boolean ok, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", ok);
        o.addProperty("message", text);
        return o;
    }

    private static JsonObject readJson(InputStream in, long length) {
        try {
            if (length > MAX_JSON) {
                return null;
            }
            byte[] data = readExact(in, length);
            return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /** Reads exactly the body - waiting for more would hang until the browser gives up. */
    private static byte[] readExact(InputStream in, long length) throws IOException {
        byte[] data = new byte[(int) length];
        int offset = 0;
        while (offset < data.length) {
            int read = in.read(data, offset, data.length - offset);
            if (read < 0) {
                throw new IOException("Upload ended early");
            }
            offset += read;
        }
        return data;
    }

    private static void copyExactly(InputStream in, Path file, long length) throws IOException {
        try (OutputStream fileOut = Files.newOutputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            long left = length;
            while (left > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, left));
                if (read < 0) {
                    throw new IOException("Upload ended early");
                }
                fileOut.write(buffer, 0, read);
                left -= read;
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                line.write(b);
            }
            if (line.size() > 8192) {
                throw new IOException("Header too long");
            }
        }
        if (b == -1 && line.size() == 0) {
            return null;
        }
        return line.toString(StandardCharsets.UTF_8);
    }

    private static void json(OutputStream out, int status, JsonObject body) throws IOException {
        sendBytes(out, status, "application/json", body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void send(OutputStream out, int status, String type, String body) throws IOException {
        sendBytes(out, status, type + "; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(OutputStream out, int status, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Error") + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Referrer-Policy: no-referrer\r\n"
                + "Content-Security-Policy: default-src 'self'; img-src 'self' blob: data:; "
                + "style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}

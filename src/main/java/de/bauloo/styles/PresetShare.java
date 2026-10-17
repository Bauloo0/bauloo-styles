package de.bauloo.styles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bauloo.styles.BaulooStylesClient.Tab;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Sharing presets with styles that only exist on one PC (My Packs, local uploads), and one-time
 * preset codes. The files of those styles go up with the preset; whoever uses the preset gets them
 * downloaded, cut out again by the filter and added to their own local uploads.
 */
public final class PresetShare {

    /** a style file on the server: content hash and a readable name */
    public record LocalFile(String sha, String name) {
    }

    private static final String UPLOAD = "bauloo_upload_";
    private static final long MAX_FILE = 4L * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private PresetShare() {
    }

    /** The collections of a preset that use a style from this PC, with the file behind each. */
    public static Map<Tab, Path> localSources(StylesPresets.Preset preset) {
        Map<Tab, Path> out = new EnumMap<>(Tab.class);
        preset.styles().forEach((tab, folder) -> {
            if (folder != null && folder.startsWith(UPLOAD)) {
                for (Path file : StylesUploadManager.list(tab.prefix)) {
                    // drawings stay on this PC: never uploaded with a shared preset or a key
                    if (DrawScreen.isDrawing(file)) {
                        continue;
                    }
                    if (StylesUploadManager.packIdFor(tab.prefix, file).equals(folder)) {
                        out.put(tab, file);
                        break;
                    }
                }
            }
        });
        return out;
    }

    public static int localCount(StylesPresets.Preset preset) {
        return localSources(preset).size();
    }

    /**
     * The request body for a share or a code: cloud/built-in styles by name, styles from this PC
     * uploaded first and named by their hash. Network, background thread only.
     */
    public static JsonObject body(StylesPresets.Preset preset, Consumer<String> say) throws IOException, InterruptedException {
        String base = base();
        JsonObject styles = new JsonObject();
        preset.styles().forEach((tab, folder) -> {
            if (folder != null && !folder.startsWith(UPLOAD)) {
                styles.addProperty(tab.prefix, folder);
            }
        });
        JsonObject local = new JsonObject();
        Map<Tab, Path> sources = localSources(preset);
        int n = 0;
        for (Map.Entry<Tab, Path> e : sources.entrySet()) {
            n++;
            say.accept("Uploading your styles " + n + "/" + sources.size() + " ...");
            byte[] zip = zipOf(e.getValue());
            if (zip.length > MAX_FILE) {
                say.accept(e.getKey().title + " style is over 4 MB - left out");
                continue;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/lfile"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/zip")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(zip)).build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject answer = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() != 200 || !answer.has("sha")) {
                throw new IOException(answer.has("message") ? answer.get("message").getAsString() : "Upload failed");
            }
            JsonObject file = new JsonObject();
            file.addProperty("sha", answer.get("sha").getAsString());
            file.addProperty("name", e.getValue().getFileName().toString().replaceFirst("\\.zip$", ""));
            local.add(e.getKey().prefix, file);
        }
        JsonObject body = new JsonObject();
        body.addProperty("name", preset.name());
        body.addProperty("author", MinecraftClient.getInstance().getSession().getUsername());
        body.addProperty("device", StylesStats.deviceHash());
        body.add("styles", styles);
        body.add("local", local);
        JsonObject extras = new JsonObject();
        preset.extras().forEach(extras::addProperty);
        body.add("extras", extras);
        return body;
    }

    /** Makes a new one-time code for a preset. Background thread. Returns the code. */
    public static String makeCode(StylesPresets.Preset preset, Consumer<String> say) throws IOException, InterruptedException {
        JsonObject answer = post("/presets/code", body(preset, say));
        if (!answer.has("code")) {
            throw new IOException(answer.has("message") ? answer.get("message").getAsString() : "Could not make a code");
        }
        return answer.get("code").getAsString();
    }

    /**
     * Redeems a code: the preset is gone from the server afterwards. Its styles from the sender's PC
     * are downloaded and added to your uploads; the preset is saved locked (no sharing it on).
     */
    public static String redeem(String code, Consumer<String> say) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        JsonObject answer = post("/presets/redeem", body);
        if (!answer.has("preset")) {
            throw new IOException(answer.has("message") ? answer.get("message").getAsString() : "Code not found");
        }
        JsonObject p = answer.getAsJsonObject("preset");
        Map<Tab, String> styles = readStyles(p.getAsJsonObject("styles"));
        styles.putAll(install(readLocal(p.getAsJsonObject("local")), say));
        String name = p.get("name").getAsString();
        String author = p.get("author").getAsString();
        StylesPresets.add(name, styles, StylesPresets.readExtras(p), author, true);
        return name;
    }

    public static Map<Tab, String> readStyles(JsonObject s) {
        Map<Tab, String> styles = new EnumMap<>(Tab.class);
        if (s != null) {
            for (Tab tab : Tab.values()) {
                if (s.has(tab.prefix) && s.get(tab.prefix).isJsonPrimitive()) {
                    styles.put(tab, s.get(tab.prefix).getAsString());
                }
            }
        }
        return styles;
    }

    public static Map<Tab, LocalFile> readLocal(JsonObject o) {
        Map<Tab, LocalFile> local = new EnumMap<>(Tab.class);
        if (o != null) {
            for (Tab tab : Tab.values()) {
                JsonElement e = o.get(tab.prefix);
                if (e != null && e.isJsonObject() && e.getAsJsonObject().has("sha") && e.getAsJsonObject().has("name")) {
                    local.put(tab, new LocalFile(e.getAsJsonObject().get("sha").getAsString(),
                            e.getAsJsonObject().get("name").getAsString()));
                }
            }
        }
        return local;
    }

    /**
     * Downloads styles from another PC and adds them to your uploads; returns collection -> style id.
     * Every file is checked against its hash and cut out by the filter again, so only textures land.
     */
    public static Map<Tab, String> install(Map<Tab, LocalFile> files, Consumer<String> say) throws IOException, InterruptedException {
        Map<Tab, String> out = new EnumMap<>(Tab.class);
        if (files.isEmpty()) {
            return out;
        }
        String base = base();
        int n = 0;
        for (Map.Entry<Tab, LocalFile> e : files.entrySet()) {
            n++;
            say.accept("Downloading styles " + n + "/" + files.size() + " ...");
            Tab tab = e.getKey();
            LocalFile file = e.getValue();
            if (!file.sha().matches("[0-9a-f]{64}")) {
                continue;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/lfile/" + file.sha()))
                    .timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || !sha256(response.body()).equals(file.sha())) {
                BaulooStylesClient.LOGGER.warn("[PresetShare] {} ({}): status {}, {} bytes", tab.prefix, file.sha(),
                        response.statusCode(), response.body().length);
                say.accept(tab.title + " style could not be downloaded");
                continue;
            }
            Path folder = StylesUploadManager.root().resolve(tab.prefix);
            Files.createDirectories(folder);
            String clean = file.name().replaceAll("[^A-Za-z0-9 ._()-]", "").trim();
            if (clean.isEmpty()) {
                clean = tab.prefix;
            }
            Path temp = Files.createTempFile("bauloo-shared-", ".zip");
            try {
                Files.write(temp, response.body());
                Path target = folder.resolve(clean + ".zip");
                for (int k = 2; Files.exists(target) && !sameFile(target, temp, tab.prefix); k++) {
                    target = folder.resolve(clean + " " + k + ".zip");
                }
                if (!Files.exists(target) && StylesPackFilter.extract(temp, tab.prefix, target) == 0) {
                    BaulooStylesClient.LOGGER.warn("[PresetShare] {} ({}): no textures for this collection", tab.prefix, file.name());
                    say.accept(tab.title + " style has no textures");
                    continue;
                }
                BaulooStylesClient.LOGGER.info("[PresetShare] installed {} as {}", file.name(), target.getFileName());
                StylesUploadManager.changed();
                StylesUploadManager.sync(tab.prefix);
                out.put(tab, StylesUploadManager.packIdFor(tab.prefix, target));
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        return out;
    }

    /** the same style is already in your uploads under this name (filtered the same way) */
    private static boolean sameFile(Path existing, Path downloaded, String category) {
        try {
            Path probe = Files.createTempFile(existing.getParent(), "probe-", ".zip");
            try {
                StylesPackFilter.extract(downloaded, category, probe);
                return Files.exists(probe) && Files.mismatch(probe, existing) == -1;
            } finally {
                Files.deleteIfExists(probe);
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static JsonObject post(String path, JsonObject body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static String base() throws IOException {
        String base = StylesCloud.publishUrl();
        if (base == null) {
            throw new IOException("Sharing is not set up yet");
        }
        return base;
    }

    /** a local upload as zip bytes (folders are zipped on the fly) */
    private static byte[] zipOf(Path source) throws IOException {
        if (!Files.isDirectory(source)) {
            return Files.readAllBytes(source);
        }
        var bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes); var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                if (Files.isDirectory(path)) {
                    continue;
                }
                out.putNextEntry(new ZipEntry(source.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, out);
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)).toLowerCase(Locale.ROOT);
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }
}

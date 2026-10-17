package de.bauloo.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Publishes to the cloud repository straight through the GitHub API (the same steps git
 * does: blobs, tree, commit, move the branch) - no other program is started. Only works
 * with bauloo's own GitHub token, saved on his PC; up to 95 MB per pack, like git.
 */
public final class GitHubPublisher {

    public static final long MAX_PACK = 95L * 1024 * 1024;
    private static final String API = "https://api.github.com/repos/" + StylesCloud.OWNER + "/" + StylesCloud.REPO;
    private static final String BRANCH = "main";
    /** keeps bauloo's real e-mail out of the public history */
    private static final String AUTHOR_NAME = "Bauloo0";
    private static final String AUTHOR_MAIL = "329405819+Bauloo0@users.noreply.github.com";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private GitHubPublisher() {
    }

    /** Result of a publish or remove, shown on the upload page. */
    public record Result(boolean ok, String message) {
    }

    private interface Change {
        /** edits the index; returns the files to write (null bytes = delete) or throws with a message */
        List<FileChange> apply(JsonObject index) throws IOException;
    }

    private record FileChange(String path, byte[] bytes) {
    }

    // ------------------------------------------------------------------ token

    public static Path tokenFile(Path keyFile) {
        return keyFile.resolveSibling("github-token.txt");
    }

    public static boolean hasToken(Path keyFile) {
        return readToken(keyFile) != null;
    }

    private static String readToken(Path keyFile) {
        try {
            Path file = tokenFile(keyFile);
            if (!Files.exists(file)) {
                return null;
            }
            String token = Files.readString(file).trim();
            return token.matches("[A-Za-z0-9_]{20,255}") ? token : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Checks the token can write to the cloud repository before it is saved. */
    public static Result check(String token) {
        try {
            HttpResponse<String> response = send(token, "GET", "", null);
            if (response.statusCode() != 200) {
                return new Result(false, "GitHub did not accept this token (" + response.statusCode() + ")");
            }
            JsonObject repo = JsonParser.parseString(response.body()).getAsJsonObject();
            boolean push = repo.has("permissions") && repo.getAsJsonObject("permissions").get("push").getAsBoolean();
            return push ? new Result(true, "GitHub token saved - big packs publish directly")
                    : new Result(false, "This token cannot write to " + StylesCloud.REPO);
        } catch (Exception e) {
            return new Result(false, "Could not reach GitHub");
        }
    }

    // ---------------------------------------------------------------- actions

    public static Result publish(Path keyFile, String category, String name, Path zip, Path png) {
        String token = readToken(keyFile);
        if (token == null) {
            return new Result(false, "No GitHub token on this PC");
        }
        try {
            byte[] pack = Files.readAllBytes(zip);
            if (pack.length == 0 || pack.length > MAX_PACK) {
                return new Result(false, "Pack must be under 95 MB");
            }
            byte[] preview = png != null && Files.exists(png) ? Files.readAllBytes(png) : null;
            String id = category + "_" + slug(name);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pack));
            return commit(token, "Publish " + category + " style: " + name, index -> {
                JsonArray styles = index.getAsJsonArray("styles");
                JsonObject existing = find(styles, id);
                JsonObject entry = new JsonObject();
                entry.addProperty("id", id);
                entry.addProperty("category", category);
                entry.addProperty("name", name);
                entry.addProperty("sha256", sha);
                entry.addProperty("size", pack.length);
                entry.addProperty("added", existing != null && existing.has("added")
                        ? existing.get("added").getAsString() : LocalDate.now().toString());
                List<FileChange> files = new ArrayList<>();
                files.add(new FileChange("packs/" + id + ".zip", pack));
                if (preview != null) {
                    entry.addProperty("preview", "previews/" + id + ".png");
                    files.add(new FileChange("previews/" + id + ".png", preview));
                } else if (existing != null && existing.has("preview")) {
                    entry.add("preview", existing.get("preview"));
                }
                if (existing != null && existing.has("by")) {
                    entry.add("by", existing.get("by"));
                }
                if (existing != null) {
                    styles.remove(existing);
                }
                styles.add(entry);
                return files;
            }, "Published \"" + name + "\" for everyone");
        } catch (Exception e) {
            return new Result(false, "Publishing failed: " + e.getMessage());
        }
    }

    public static Result remove(Path keyFile, String id) {
        String token = readToken(keyFile);
        if (token == null) {
            return new Result(false, "No GitHub token on this PC");
        }
        try {
            return commit(token, "Remove style " + id, index -> {
                JsonArray styles = index.getAsJsonArray("styles");
                JsonObject existing = find(styles, id);
                if (existing == null) {
                    throw new IOException("No such style");
                }
                styles.remove(existing);
                List<FileChange> files = new ArrayList<>();
                files.add(new FileChange("packs/" + id + ".zip", null));
                if (existing.has("preview")) {
                    files.add(new FileChange(existing.get("preview").getAsString(), null));
                }
                return files;
            }, "Removed for everyone");
        } catch (Exception e) {
            return new Result(false, "Removing failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- git data

    /** Reads index.json, applies the change and writes everything as one commit (3 tries). */
    private static Result commit(String token, String message, Change change, String success) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            JsonObject ref = get(token, "/git/ref/heads/" + BRANCH);
            String head = ref.getAsJsonObject("object").get("sha").getAsString();
            String baseTree = get(token, "/git/commits/" + head).getAsJsonObject("tree").get("sha").getAsString();

            HttpResponse<String> current = send(token, "GET", "/contents/index.json?ref=" + head, null, true);
            JsonObject index;
            if (current.statusCode() == 200) {
                index = JsonParser.parseString(current.body()).getAsJsonObject();
            } else {
                index = new JsonObject();
                index.addProperty("version", 1);
                index.add("styles", new JsonArray());
            }

            List<FileChange> files;
            try {
                files = change.apply(index);
            } catch (IOException e) {
                return new Result(false, e.getMessage());
            }
            sortNewestFirst(index);

            JsonArray tree = new JsonArray();
            for (FileChange file : files) {
                JsonObject item = entry(file.path());
                if (file.bytes() == null) {
                    item.add("sha", com.google.gson.JsonNull.INSTANCE);
                } else {
                    JsonObject blob = new JsonObject();
                    blob.addProperty("content", blobText(file.bytes()));
                    blob.addProperty("encoding", "base64");
                    item.addProperty("sha", post(token, "/git/blobs", blob, 201).get("sha").getAsString());
                }
                tree.add(item);
            }
            JsonObject indexItem = entry("index.json");
            indexItem.addProperty("content", new com.google.gson.GsonBuilder().setPrettyPrinting()
                    .disableHtmlEscaping().create().toJson(index) + "\n");
            tree.add(indexItem);

            JsonObject treeBody = new JsonObject();
            treeBody.addProperty("base_tree", baseTree);
            treeBody.add("tree", tree);
            String newTree = post(token, "/git/trees", treeBody, 201).get("sha").getAsString();

            JsonObject person = new JsonObject();
            person.addProperty("name", AUTHOR_NAME);
            person.addProperty("email", AUTHOR_MAIL);
            JsonObject commitBody = new JsonObject();
            commitBody.addProperty("message", message);
            commitBody.addProperty("tree", newTree);
            JsonArray parents = new JsonArray();
            parents.add(head);
            commitBody.add("parents", parents);
            commitBody.add("author", person);
            commitBody.add("committer", person);
            String newCommit = post(token, "/git/commits", commitBody, 201).get("sha").getAsString();

            JsonObject move = new JsonObject();
            move.addProperty("sha", newCommit);
            move.addProperty("force", false);
            HttpResponse<String> update = send(token, "PATCH", "/git/refs/heads/" + BRANCH, move.toString());
            if (update.statusCode() == 200) {
                return new Result(true, success);
            }
            if (update.statusCode() != 422) {
                throw new IOException("GitHub update " + update.statusCode());
            }
            // someone else pushed at the same moment - read again and retry
        }
        return new Result(false, "Someone else published at the same moment - try again");
    }

    private static JsonObject entry(String path) {
        JsonObject item = new JsonObject();
        item.addProperty("path", path);
        item.addProperty("mode", "100644");
        item.addProperty("type", "blob");
        return item;
    }

    private static JsonObject find(JsonArray styles, String id) {
        for (JsonElement element : styles) {
            JsonObject s = element.getAsJsonObject();
            if (s.has("id") && id.equals(s.get("id").getAsString())) {
                return s;
            }
        }
        return null;
    }

    private static void sortNewestFirst(JsonObject index) {
        List<JsonElement> list = new ArrayList<>();
        index.getAsJsonArray("styles").forEach(list::add);
        list.sort((a, b) -> added(b).compareTo(added(a)));
        JsonArray sorted = new JsonArray();
        list.forEach(sorted::add);
        index.add("styles", sorted);
    }

    private static String added(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        return o.has("added") ? o.get("added").getAsString() : "";
    }

    static String slug(String text) {
        String s = text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    // ------------------------------------------------------------------- http

    private static JsonObject get(String token, String path) throws Exception {
        HttpResponse<String> response = send(token, "GET", path, null);
        if (response.statusCode() != 200) {
            throw new IOException("GitHub " + path + " " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static JsonObject post(String token, String path, JsonObject body, int expected) throws Exception {
        HttpResponse<String> response = send(token, "POST", path, body.toString());
        if (response.statusCode() != expected) {
            throw new IOException("GitHub " + path + " " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static HttpResponse<String> send(String token, String method, String path, String body) throws Exception {
        return send(token, method, path, body, false);
    }

    /** raw = the file itself instead of the JSON wrapper around it (for index.json). */
    private static HttpResponse<String> send(String token, String method, String path, String body, boolean raw) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(API + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", raw ? "application/vnd.github.raw+json" : "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "bauloo-styles-mod")
                .timeout(Duration.ofMinutes(5));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static final char[] BLOB_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    /** GitHub takes blob bytes as text in its standard 64-character form (RFC 4648). */
    private static String blobText(byte[] data) {
        StringBuilder out = new StringBuilder((data.length + 2) / 3 * 4);
        for (int i = 0; i < data.length; i += 3) {
            int b0 = data[i] & 0xFF;
            int b1 = i + 1 < data.length ? data[i + 1] & 0xFF : 0;
            int b2 = i + 2 < data.length ? data[i + 2] & 0xFF : 0;
            out.append(BLOB_CHARS[b0 >> 2]);
            out.append(BLOB_CHARS[((b0 & 3) << 4) | (b1 >> 4)]);
            out.append(i + 1 < data.length ? BLOB_CHARS[((b1 & 15) << 2) | (b2 >> 6)] : '=');
            out.append(i + 2 < data.length ? BLOB_CHARS[b2 & 63] : '=');
        }
        return out.toString();
    }
}

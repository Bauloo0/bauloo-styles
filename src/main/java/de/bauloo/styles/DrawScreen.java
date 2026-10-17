package de.bauloo.styles;

import de.bauloo.styles.BaulooStylesClient.Tab;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Draw your own: paint a 16x16 texture pixel by pixel. A drawing is saved as a private upload
 * named drawn_*.zip, so it shows up in its collection like any style - but it never leaves this PC:
 * preset sharing, preset keys and EXPORT all leave drawings out (bauloo's wish).
 */
public class DrawScreen extends Screen {

    /** What can be drawn: the collection, its label and where the texture goes. */
    private record Target(Tab tab, String label, String texture) {
    }

    private static final List<Target> TARGETS = List.of(
            new Target(Tab.CRYSTAL, "Crystal", "textures/entity/end_crystal/end_crystal.png"),
            new Target(Tab.OBSIDIAN, "Obsidian", "textures/block/obsidian.png"),
            new Target(Tab.ANCHOR, "Anchor", "textures/block/respawn_anchor_side0.png"),
            new Target(Tab.TOTEM, "Totem", "textures/item/totem_of_undying.png"),
            new Target(Tab.PEARL, "Pearl", "textures/item/ender_pearl.png"),
            new Target(Tab.GAP, "Gap", "textures/item/golden_apple.png"),
            new Target(Tab.SWORD, "Sword", "textures/item/netherite_sword.png"));

    public static final String DRAWN = "drawn_";
    private static final String META =
            "{\"pack\": {\"description\": \"Drawn with Bauloo Styles\", \"pack_format\": 75, \"min_format\": 65, \"max_format\": 9999}}\n";
    /** the six core faces of the crystal (vanilla 128x64 layout), all get the drawing */
    private static final int[][] CORE = {{80, 0}, {96, 0}, {64, 16}, {80, 16}, {96, 16}, {112, 16}};

    private enum Tool { PENCIL, ERASER, FILL, PICK }

    private final Screen parent;
    private int target = 3;
    private Tool tool = Tool.PENCIL;
    private boolean mirror;
    private final int[] px = new int[256];
    private int[] base = new int[256];
    /** the texture as it is in the game now (crystal: the full sheet, scaled to 128x64) */
    private BufferedImage full;
    /** anchor: the five charge levels as 16x16, so the drawing keeps the charge marks */
    private final List<int[]> anchorSides = new ArrayList<>();
    private final ArrayDeque<int[]> undo = new ArrayDeque<>();
    private final ArrayDeque<int[]> redo = new ArrayDeque<>();
    private int color = 0xFF9B5CFF;
    private float hue = 0.74F;
    private float value = 1.0F;
    private String name = "";
    private boolean nameFocused;
    private String message = "";
    private boolean painting;
    private int dragBar; // 0 none, 1 hue, 2 brightness

    // layout, set every frame
    private int canvasX;
    private int canvasY;
    private int cell;
    private int barX;
    private int barW;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    public DrawScreen(Screen parent) {
        super(Text.literal("Draw your own"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (full == null) {
            load();
        }
    }

    // ---------------------------------------------------------------- textures

    private Target current() {
        return TARGETS.get(target);
    }

    private static BufferedImage read(String path) {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            var resource = mc.getResourceManager().getResource(Identifier.ofVanilla(path));
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream in = resource.get().getInputStream()) {
                return ImageIO.read(in);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The image scaled to w x h by picking pixels (keeps the pixel look of HD packs). */
    private static BufferedImage scaled(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, src.getRGB(x * src.getWidth() / w, y * src.getHeight() / h));
            }
        }
        return out;
    }

    private static int[] grid(BufferedImage img, int ox, int oy) {
        int[] g = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                g[y * 16 + x] = img.getRGB(ox + x, oy + y);
            }
        }
        return g;
    }

    /** Starts from the texture you see in the game right now. */
    private void load() {
        Target t = current();
        anchorSides.clear();
        BufferedImage img = read(t.texture());
        if (img == null) {
            message = "Could not load the " + t.label().toLowerCase(Locale.ROOT) + " texture";
            base = new int[256];
        } else if (t.tab() == Tab.CRYSTAL) {
            full = scaled(img, 128, 64);
            base = grid(full, 80, 16);
        } else {
            // animated textures are tall strips: only the first frame
            full = scaled(img.getSubimage(0, 0, img.getWidth(), Math.min(img.getHeight(), img.getWidth())), 16, 16);
            base = grid(full, 0, 0);
            if (t.tab() == Tab.ANCHOR) {
                for (int i = 0; i <= 4; i++) {
                    BufferedImage side = read("textures/block/respawn_anchor_side" + i + ".png");
                    anchorSides.add(side == null ? base.clone() : grid(scaled(side.getSubimage(0, 0, side.getWidth(),
                            Math.min(side.getHeight(), side.getWidth())), 16, 16), 0, 0));
                }
            }
        }
        System.arraycopy(base, 0, px, 0, 256);
        undo.clear();
        redo.clear();
    }

    // ---------------------------------------------------------------- editing

    private void remember() {
        undo.push(px.clone());
        if (undo.size() > 60) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void set(int x, int y) {
        int value = tool == Tool.ERASER ? base[y * 16 + x] : color;
        px[y * 16 + x] = value;
        if (mirror) {
            px[y * 16 + 15 - x] = tool == Tool.ERASER ? base[y * 16 + 15 - x] : color;
        }
    }

    private void fill(int x, int y) {
        int from = px[y * 16 + x];
        if (from == color) {
            return;
        }
        ArrayDeque<int[]> todo = new ArrayDeque<>();
        todo.add(new int[]{x, y});
        while (!todo.isEmpty()) {
            int[] p = todo.poll();
            if (p[0] < 0 || p[0] > 15 || p[1] < 0 || p[1] > 15 || px[p[1] * 16 + p[0]] != from) {
                continue;
            }
            px[p[1] * 16 + p[0]] = color;
            todo.add(new int[]{p[0] + 1, p[1]});
            todo.add(new int[]{p[0] - 1, p[1]});
            todo.add(new int[]{p[0], p[1] + 1});
            todo.add(new int[]{p[0], p[1] - 1});
        }
    }

    private void paintAt(double mx, double my, boolean start) {
        int x = (int) Math.floor((mx - canvasX) / cell);
        int y = (int) Math.floor((my - canvasY) / cell);
        if (x < 0 || x > 15 || y < 0 || y > 15) {
            return;
        }
        if (tool == Tool.PICK) {
            if ((px[y * 16 + x] >>> 24) != 0) {
                setColor(px[y * 16 + x]);
            }
            tool = Tool.PENCIL;
            return;
        }
        if (start) {
            remember();
        }
        if (tool == Tool.FILL) {
            if (start) {
                fill(x, y);
            }
        } else {
            set(x, y);
        }
    }

    private void setColor(int argb) {
        color = argb | 0xFF000000;
        float[] hsv = StylesTheme.toHsv(color & 0xFFFFFF);
        hue = hsv[0];
        value = hsv[2];
    }

    private void colorFromBars() {
        color = 0xFF000000 | StylesTheme.hsv(hue, value > 0.98F ? 0.75F : 0.85F, Math.max(0.08F, value));
    }

    /** the colours in the drawing, most used first, at most 12 */
    private List<Integer> used() {
        java.util.Map<Integer, Integer> count = new java.util.HashMap<>();
        for (int p : px) {
            if ((p >>> 24) != 0) {
                count.merge(p, 1, Integer::sum);
            }
        }
        List<Integer> out = new ArrayList<>(count.keySet());
        out.sort((a, b) -> count.get(b) - count.get(a));
        return out.size() > 12 ? out.subList(0, 12) : out;
    }

    // ---------------------------------------------------------------- saving

    private String slug() {
        String s = name.trim().replaceAll("[^A-Za-z0-9 _-]", "").replace(' ', '_');
        return s.isEmpty() ? "" : s.substring(0, Math.min(24, s.length()));
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage image16(int[] g) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 256; i++) {
            img.setRGB(i % 16, i / 16, g[i]);
        }
        return img;
    }

    private void save() {
        String slug = slug();
        if (slug.isEmpty()) {
            message = "Give your drawing a name first";
            nameFocused = true;
            return;
        }
        Target t = current();
        try {
            java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<>();
            String dir = "assets/minecraft/";
            if (t.tab() == Tab.CRYSTAL) {
                BufferedImage sheet = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
                sheet.getGraphics().drawImage(full, 0, 0, null);
                for (int[] f : CORE) {
                    for (int i = 0; i < 256; i++) {
                        sheet.setRGB(f[0] + i % 16, f[1] + i / 16, px[i]);
                    }
                }
                files.put(dir + t.texture(), png(sheet));
            } else if (t.tab() == Tab.ANCHOR) {
                // every charge level: the drawing, plus the pixels that show the charge in that level
                int[] empty = anchorSides.isEmpty() ? base : anchorSides.get(0);
                for (int level = 0; level <= 4; level++) {
                    int[] side = anchorSides.isEmpty() ? base : anchorSides.get(level);
                    int[] out = px.clone();
                    for (int i = 0; i < 256; i++) {
                        if (side[i] != empty[i]) {
                            out[i] = side[i];
                        }
                    }
                    files.put(dir + "textures/block/respawn_anchor_side" + level + ".png", png(image16(out)));
                }
            } else {
                files.put(dir + t.texture(), png(image16(px)));
            }
            Path folder = StylesUploadManager.root().resolve(t.tab().prefix);
            Files.createDirectories(folder);
            Path zip = folder.resolve(DRAWN + slug + ".zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new ZipEntry("pack.mcmeta"));
                out.write(META.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
                for (var e : files.entrySet()) {
                    out.putNextEntry(new ZipEntry(e.getKey()));
                    out.write(e.getValue());
                    out.closeEntry();
                }
            }
            // the picture next to the zip is the preview in the menu
            Files.write(folder.resolve(DRAWN + slug + ".png"), png(scaled(image16(px), 128, 128)));
            StylesUploadManager.changed();
            StylesUploadManager.sync(t.tab().prefix);
            count = -1;
            message = "Saved - pick it in " + t.tab().title + " (it is marked LOCAL)";
        } catch (IOException e) {
            message = "Could not save: " + e.getMessage();
        }
    }

    // ---------------------------------------------------------------- count for the menu

    private static int count = -1;
    private static int countVersion = -1;

    /** How many drawings are on this PC (for the My Drawings row). */
    public static int count() {
        if (count < 0 || countVersion != StylesUploadManager.version()) {
            int n = 0;
            for (Target t : TARGETS) {
                for (Path p : StylesUploadManager.list(t.tab().prefix)) {
                    if (p.getFileName().toString().startsWith(DRAWN)) {
                        n++;
                    }
                }
            }
            count = n;
            countVersion = StylesUploadManager.version();
        }
        return count;
    }

    /** A drawing never leaves this PC: sharing, keys and export skip it. */
    public static boolean isDrawing(Path upload) {
        return upload != null && upload.getFileName().toString().startsWith(DRAWN);
    }

    public static boolean isDrawingFolder(String folder) {
        return folder != null && folder.startsWith("bauloo_upload_") && folder.contains("_" + DRAWN);
    }

    // ---------------------------------------------------------------- drawing the screen

    private static void box(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + 1, color);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void button(DrawContext ctx, int x, int y, int w, int h, String label, boolean on, boolean primary,
                        int mx, int my, Runnable action) {
        StylesTheme c = StylesUiConfig.theme();
        boolean hover = in(mx, my, x, y, w, h);
        int fill = primary ? (hover ? c.accentBright : c.accent) : on ? c.accentTint : hover ? c.surfaceHover : c.surface2;
        box(ctx, x, y, w, h, fill);
        if (on) {
            outline(ctx, x, y, w, h, c.accent);
        }
        int tc = primary ? c.onAccent : on ? c.accentBright : hover ? c.text : c.textSoft;
        ctx.drawText(textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2, y + (h - 8) / 2, tc, false);
        hits.add(new Hit(x, y, w, h, action));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        StylesTheme c = StylesUiConfig.theme();
        hits.clear();
        ctx.fill(0, 0, width, height, c.backdrop);
        int x0 = 8;
        int y0 = 8;
        int x1 = width - 8;
        int y1 = height - 8;
        box(ctx, x0, y0, x1 - x0, y1 - y0, c.frame);

        ctx.drawText(textRenderer, "Draw your own", x0 + 10, y0 + 9, c.text, false);
        String sub = "Paint a texture pixel by pixel. Your drawings stay on this PC - only you can use them.";
        ctx.drawText(textRenderer, textRenderer.trimToWidth(sub, x1 - x0 - 90), x0 + 10, y0 + 21, c.textDim, false);
        button(ctx, x1 - 60, y0 + 8, 50, 16, "BACK", false, false, mx, my, this::close);

        // what to draw
        int cx = x0 + 10;
        for (int i = 0; i < TARGETS.size(); i++) {
            String label = TARGETS.get(i).label();
            int w = textRenderer.getWidth(label) + 14;
            final int index = i;
            button(ctx, cx, y0 + 36, w, 16, label, i == target, false, mx, my, () -> {
                if (index != target) {
                    target = index;
                    full = null;
                    load();
                    message = "";
                }
            });
            cx += w + 4;
        }

        int top = y0 + 60;
        int bottom = y1 - 10;
        // tools
        int tx = x0 + 10;
        int tw = 54;
        String[] names = {"Pencil", "Eraser", "Fill", "Pick"};
        int ty = top;
        for (int i = 0; i < names.length; i++) {
            Tool t = Tool.values()[i];
            button(ctx, tx, ty, tw, 18, names[i], tool == t, false, mx, my, () -> tool = t);
            ty += 22;
        }
        button(ctx, tx, ty, tw, 18, "Mirror", mirror, false, mx, my, () -> mirror = !mirror);
        ty += 22;
        button(ctx, tx, ty, tw, 18, "Undo", false, false, mx, my, () -> {
            if (!undo.isEmpty()) {
                redo.push(px.clone());
                System.arraycopy(undo.pop(), 0, px, 0, 256);
            }
        });
        ty += 22;
        button(ctx, tx, ty, tw, 18, "Redo", false, false, mx, my, () -> {
            if (!redo.isEmpty()) {
                undo.push(px.clone());
                System.arraycopy(redo.pop(), 0, px, 0, 256);
            }
        });

        // canvas
        int sideW = Math.max(150, Math.min(220, (x1 - x0) / 3));
        int room = Math.min(bottom - top, x1 - 10 - sideW - 12 - (tx + tw + 10));
        cell = Math.max(4, room / 16);
        int size = cell * 16;
        canvasX = tx + tw + 10;
        canvasY = top;
        box(ctx, canvasX - 3, canvasY - 3, size + 6, size + 6, c.base);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int p = px[y * 16 + x];
                int px0 = canvasX + x * cell;
                int py0 = canvasY + y * cell;
                if ((p >>> 24) < 255) {
                    // checker under see-through pixels
                    int check = ((x + y) % 2 == 0) ? 0xFF2A2230 : 0xFF1C1622;
                    ctx.fill(px0, py0, px0 + cell, py0 + cell, check);
                }
                if ((p >>> 24) != 0) {
                    ctx.fill(px0, py0, px0 + cell, py0 + cell, p);
                }
            }
        }
        if (cell >= 8) {
            for (int k = 1; k < 16; k++) {
                ctx.fill(canvasX + k * cell, canvasY, canvasX + k * cell + 1, canvasY + size, 0x14FFFFFF);
                ctx.fill(canvasX, canvasY + k * cell, canvasX + size, canvasY + k * cell + 1, 0x14FFFFFF);
            }
        }
        if (mirror) {
            ctx.fill(canvasX + size / 2, canvasY, canvasX + size / 2 + 1, canvasY + size, 0x88FFCF5A);
        }
        if (in(mx, my, canvasX, canvasY, size, size)) {
            int hx = canvasX + (mx - canvasX) / cell * cell;
            int hy = canvasY + (my - canvasY) / cell * cell;
            outline(ctx, hx - 1, hy - 1, cell + 2, cell + 2, c.gold);
        }

        // side: colour, preview, name, save
        int sx = canvasX + size + 12;
        int sw = x1 - 10 - sx;
        int sy = top;
        box(ctx, sx, sy, sw, 92, c.surface);
        ctx.drawText(textRenderer, "COLOUR", sx + 8, sy + 7, c.textDim, false);
        box(ctx, sx + 8, sy + 19, 18, 18, color);
        ctx.drawText(textRenderer, String.format("#%06X", color & 0xFFFFFF), sx + 32, sy + 24, c.text, false);
        int barW = sw - 16;
        this.barX = sx + 8;
        this.barW = barW;
        int hueY = sy + 42;
        for (int i = 0; i < barW; i++) {
            ctx.fill(sx + 8 + i, hueY, sx + 9 + i, hueY + 6, 0xFF000000 | StylesTheme.hsv(i / (float) barW, 0.8F, 1.0F));
        }
        int hk = sx + 8 + Math.round(hue * (barW - 1));
        ctx.fill(hk - 1, hueY - 2, hk + 1, hueY + 8, 0xFFFFFFFF);
        int valY = hueY + 10;
        for (int i = 0; i < barW; i++) {
            ctx.fill(sx + 8 + i, valY, sx + 9 + i, valY + 6, 0xFF000000 | StylesTheme.hsv(hue, 0.85F, 0.08F + 0.92F * i / barW));
        }
        int vk = sx + 8 + Math.round(value * (barW - 1));
        ctx.fill(vk - 1, valY - 2, vk + 1, valY + 8, 0xFFFFFFFF);
        hits.add(new Hit(sx + 8, hueY - 3, barW, 10, () -> dragBar = 1));
        hits.add(new Hit(sx + 8, valY - 3, barW, 10, () -> dragBar = 2));
        int px0 = sx + 8;
        int py0 = valY + 12;
        List<Integer> used = used();
        int sz = Math.max(8, Math.min(12, (barW - 11 * 3) / 12));
        for (int i = 0; i < used.size(); i++) {
            int col = used.get(i);
            int bx = px0 + i * (sz + 3);
            box(ctx, bx, py0, sz, sz, col);
            hits.add(new Hit(bx, py0, sz, sz, () -> setColor(col)));
        }

        int prevY = sy + 98;
        int prevH = Math.min(70, Math.max(40, bottom - prevY - 120));
        box(ctx, sx, prevY, sw, prevH, c.surface);
        ctx.drawText(textRenderer, "PREVIEW", sx + 8, prevY + 7, c.textDim, false);
        int pc = Math.max(1, (prevH - 22) / 16);
        int pvx = sx + sw - 10 - pc * 16;
        for (int i = 0; i < 256; i++) {
            if ((px[i] >>> 24) != 0) {
                int x = pvx + i % 16 * pc;
                int y = prevY + 16 + i / 16 * pc;
                ctx.fill(x, y, x + pc, y + pc, px[i]);
            }
        }
        ctx.drawText(textRenderer, "Updates while", sx + 8, prevY + 22, c.textSoft, false);
        ctx.drawText(textRenderer, "you draw", sx + 8, prevY + 32, c.textSoft, false);

        int ny = prevY + prevH + 6;
        box(ctx, sx, ny, sw, 62, c.surface);
        ctx.drawText(textRenderer, "NAME", sx + 8, ny + 7, c.textDim, false);
        box(ctx, sx + 8, ny + 18, sw - 16, 16, c.base);
        if (nameFocused) {
            outline(ctx, sx + 8, ny + 18, sw - 16, 16, c.accent);
        }
        String shown = name.isEmpty() && !nameFocused ? "Name your drawing" : name
                + (nameFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        ctx.drawText(textRenderer, shown, sx + 12, ny + 22, name.isEmpty() && !nameFocused ? c.textDim : c.text, false);
        hits.add(new Hit(sx + 8, ny + 18, sw - 16, 16, () -> nameFocused = true));
        int half = (sw - 20) / 2;
        button(ctx, sx + 8, ny + 40, half, 16, "START OVER", false, false, mx, my, () -> {
            remember();
            System.arraycopy(base, 0, px, 0, 256);
        });
        button(ctx, sx + 12 + half, ny + 40, half, 16, "SAVE", false, true, mx, my, this::save);

        int ly = ny + 68;
        if (ly + 26 <= bottom) {
            box(ctx, sx, ly, sw, 26, 0xFF1E0F16);
            for (String line : wrap("Only for you. Drawings are never sent anywhere: presets, keys and exports leave them out.", sw - 12)) {
                ctx.drawText(textRenderer, line, sx + 6, ly + 4, 0xFFFFC2CB, false);
                ly += 10;
                if (ly > ny + 68 + 16) {
                    break;
                }
            }
        }
        if (!message.isEmpty()) {
            ctx.drawText(textRenderer, textRenderer.trimToWidth(message, x1 - x0 - 20), x0 + 10, y1 - 14, c.good, false);
        }
    }

    private List<String> wrap(String text, int w) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (textRenderer.getWidth(next) > w && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        if (in(mx, my, canvasX, canvasY, cell * 16, cell * 16)) {
            nameFocused = false;
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                int x = (int) ((mx - canvasX) / cell);
                int y = (int) ((my - canvasY) / cell);
                if ((px[y * 16 + x] >>> 24) != 0) {
                    setColor(px[y * 16 + x]);
                }
                return true;
            }
            painting = true;
            paintAt(mx, my, true);
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (in(mx, my, h.x(), h.y(), h.w(), h.h())) {
                nameFocused = false;
                h.action().run();
                if (dragBar != 0) {
                    dragBars(mx);
                }
                return true;
            }
        }
        nameFocused = false;
        return super.mouseClicked(click, doubled);
    }

    private void dragBars(double mx) {
        float t = (float) Math.max(0, Math.min(1, (mx - barX) / Math.max(1, barW - 1)));
        if (dragBar == 1) {
            hue = t;
        } else {
            value = t;
        }
        colorFromBars();
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (painting && tool != Tool.FILL && tool != Tool.PICK) {
            paintAt(click.x(), click.y(), false);
            return true;
        }
        if (dragBar != 0) {
            dragBars(click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        painting = false;
        dragBar = 0;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (nameFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !name.isEmpty()) {
                name = name.substring(0, name.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER) {
                nameFocused = false;
                save();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                nameFocused = false;
                return true;
            }
        }
        if (ctrl && key == GLFW.GLFW_KEY_Z && !undo.isEmpty()) {
            redo.push(px.clone());
            System.arraycopy(undo.pop(), 0, px, 0, 256);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y && !redo.isEmpty()) {
            undo.push(px.clone());
            System.arraycopy(redo.pop(), 0, px, 0, 256);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!nameFocused || !input.isValidChar()) {
            return false;
        }
        String typed = input.asString();
        if (typed.matches("[A-Za-z0-9 _-]") && name.length() < 20) {
            name += typed;
        }
        return true;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

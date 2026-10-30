package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Arrays;

/**
 * The turning 3D model in the draw menu: an item as thick as in your hand, or a block as a cube.
 * Drawn by a tiny software renderer into one texture, so it shows every pixel the moment you paint it.
 * Drag turns it, the mouse wheel zooms, a double click puts it back. It also remembers which side
 * and pixel is under every screen pixel, so you can paint right on the model.
 */
public final class DrawPreview3D {

    private static final Identifier ID = Identifier.of(BaulooStylesClient.MOD_ID, "draw_preview_3d");
    private static final int MAX = 384;
    private static final float START_YAW = -0.6F;
    private static final float START_PITCH = 0.35F;

    private NativeImageBackedTexture texture;
    private int size;
    private int[] color = new int[0];
    private float[] depth = new float[0];
    /** per screen pixel: side * 256 + texture pixel, plus 1 (0 = nothing there) */
    private int[] pick = new int[0];
    /** the id the next quads get, see pick */
    private int quadId;
    private int lastX;
    private int lastY;
    private int lastBox = 1;

    private float yaw = START_YAW;
    private float pitch = START_PITCH;
    private float zoom = 1;
    private long lastTouch;
    private long lastFrame;

    /** Back to the start view. */
    public void reset() {
        yaw = START_YAW;
        pitch = START_PITCH;
        zoom = 1;
        lastTouch = 0;
    }

    public void drag(double dx, double dy) {
        yaw += (float) dx * 0.025F;
        pitch = Math.max(-1.45F, Math.min(1.45F, pitch + (float) dy * 0.025F));
        lastTouch = System.currentTimeMillis();
    }

    public void scroll(double amount) {
        zoom = Math.max(0.5F, Math.min(5, zoom * (float) Math.pow(1.18, amount)));
        lastTouch = System.currentTimeMillis();
    }

    /**
     * Draws the model into x, y, w, h.
     *
     * @param tex   item: one 16x16 ARGB drawing; cube: front, right, back, left, top, bottom
     * @param ids   which paintable side each of those is (what {@link #pickAt} hands back)
     * @param item  true: a flat item with thickness, false: a cube
     * @param hover side * 256 + pixel to light up (the one under the mouse), or -1
     */
    public void render(DrawContext ctx, int x, int y, int w, int h, int[][] tex, int[] ids, boolean item, int hover) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000F);
        lastFrame = now;
        // turns by itself until you grab it, and again a few seconds after you let go
        if (now - lastTouch > 3000) {
            yaw += dt * 0.8F;
        }

        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        int box = Math.min(w, h);
        int want = (int) Math.max(32, Math.min(MAX, box * scale));
        ensure(want);

        Arrays.fill(color, 0);
        Arrays.fill(depth, Float.NEGATIVE_INFINITY);
        Arrays.fill(pick, 0);
        shadow(item);
        if (item) {
            item(tex[0], ids[0]);
        } else {
            cube(tex, ids);
        }
        if (hover >= 0) {
            // the pixel under the mouse, lit up on every side it shows on
            for (int i = 0; i < color.length; i++) {
                if (pick[i] == hover + 1) {
                    int c = color[i];
                    int r = ((c >> 16) & 0xFF) + (255 - ((c >> 16) & 0xFF)) * 11 / 20;
                    int g = ((c >> 8) & 0xFF) + (255 - ((c >> 8) & 0xFF)) * 11 / 20;
                    int b = (c & 0xFF) + (255 - (c & 0xFF)) * 11 / 20;
                    color[i] = 0xFF000000 | r << 16 | g << 8 | b;
                }
            }
        }
        var image = texture.getImage();
        if (image != null) {
            for (int py = 0; py < size; py++) {
                for (int px = 0; px < size; px++) {
                    image.setColorArgb(px, py, color[py * size + px]);
                }
            }
            texture.upload();
        }
        int ox = x + (w - box) / 2;
        int oy = y + (h - box) / 2;
        lastX = ox;
        lastY = oy;
        lastBox = box;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ID, ox, oy, 0, 0, box, box, size, size, size, size);
    }

    /** side * 256 + texture pixel under this mouse position (last frame), or -1 */
    public int pickAt(double mx, double my) {
        if (size == 0) {
            return -1;
        }
        int px = (int) ((mx - lastX) * size / lastBox);
        int py = (int) ((my - lastY) * size / lastBox);
        if (px < 0 || py < 0 || px >= size || py >= size) {
            return -1;
        }
        return pick[py * size + px] - 1;
    }

    private void ensure(int want) {
        if (texture != null && size == want) {
            return;
        }
        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);
        }
        size = want;
        color = new int[size * size];
        depth = new float[size * size];
        pick = new int[size * size];
        texture = new NativeImageBackedTexture(ID::toString, size, size, false);
        MinecraftClient.getInstance().getTextureManager().registerTexture(ID, texture);
    }

    /** Frees the texture when the menu closes. */
    public void close() {
        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);
            texture = null;
            size = 0;
        }
    }

    // ------------------------------------------------------------------ models

    private void item(int[] px, int id) {
        float t = 1 / 16F;
        float h = 0.5F * t;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = px[y * 16 + x];
                if (!solid(c)) {
                    continue;
                }
                float x0 = x * t - 0.5F, x1 = x0 + t;
                float y0 = 0.5F - y * t, y1 = y0 - t;
                quadId = id * 256 + y * 16 + x;
                quad(c, 0, 0, 1, x0, y0, h, x1, y0, h, x1, y1, h, x0, y1, h);
                quad(c, 0, 0, -1, x1, y0, -h, x0, y0, -h, x0, y1, -h, x1, y1, -h);
                if (x == 15 || !solid(px[y * 16 + x + 1])) {
                    quad(c, 1, 0, 0, x1, y0, h, x1, y0, -h, x1, y1, -h, x1, y1, h);
                }
                if (x == 0 || !solid(px[y * 16 + x - 1])) {
                    quad(c, -1, 0, 0, x0, y0, -h, x0, y0, h, x0, y1, h, x0, y1, -h);
                }
                if (y == 0 || !solid(px[(y - 1) * 16 + x])) {
                    quad(c, 0, 1, 0, x0, y0, -h, x1, y0, -h, x1, y0, h, x0, y0, h);
                }
                if (y == 15 || !solid(px[(y + 1) * 16 + x])) {
                    quad(c, 0, -1, 0, x0, y1, h, x1, y1, h, x1, y1, -h, x0, y1, -h);
                }
            }
        }
    }

    private void cube(int[][] tex, int[] ids) {
        // origin, then the step for one pixel to the right (u) and one down (v)
        face(tex[0], ids[0], -0.5F, 0.5F, 0.5F, 1, 0, 0, 0, -1, 0, 0, 0, 1);
        face(tex[1], ids[1], 0.5F, 0.5F, 0.5F, 0, 0, -1, 0, -1, 0, 1, 0, 0);
        face(tex[2], ids[2], 0.5F, 0.5F, -0.5F, -1, 0, 0, 0, -1, 0, 0, 0, -1);
        face(tex[3], ids[3], -0.5F, 0.5F, -0.5F, 0, 0, 1, 0, -1, 0, -1, 0, 0);
        face(tex[4], ids[4], -0.5F, 0.5F, -0.5F, 1, 0, 0, 0, 0, 1, 0, 1, 0);
        face(tex[5], ids[5], -0.5F, -0.5F, 0.5F, 1, 0, 0, 0, 0, -1, 0, -1, 0);
    }

    private void face(int[] px, int id, float ox, float oy, float oz, float ux, float uy, float uz,
                      float vx, float vy, float vz, float nx, float ny, float nz) {
        float t = 1 / 16F;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = px[y * 16 + x];
                if (!solid(c)) {
                    continue;
                }
                quadId = id * 256 + y * 16 + x;
                float ax = ox + (ux * x + vx * y) * t, ay = oy + (uy * x + vy * y) * t, az = oz + (uz * x + vz * y) * t;
                quad(c, nx, ny, nz,
                        ax, ay, az,
                        ax + ux * t, ay + uy * t, az + uz * t,
                        ax + (ux + vx) * t, ay + (uy + vy) * t, az + (uz + vz) * t,
                        ax + vx * t, ay + vy * t, az + vz * t);
            }
        }
    }

    private static boolean solid(int c) {
        return (c >>> 24) >= 16;
    }

    // ------------------------------------------------------------------ the renderer

    /** A soft dark ellipse under the model, so it stands somewhere. */
    private void shadow(boolean item) {
        float cx = size * 0.5F;
        float cy = size * (0.5F + 0.36F * Math.min(1.4F, zoom));
        float rx = size * 0.30F * zoom * (item ? 0.8F : 1);
        float ry = rx * 0.22F;
        for (int py = (int) Math.max(0, cy - ry); py < Math.min(size, cy + ry); py++) {
            for (int px = (int) Math.max(0, cx - rx); px < Math.min(size, cx + rx); px++) {
                float dx = (px - cx) / rx, dy = (py - cy) / ry;
                float d = dx * dx + dy * dy;
                if (d < 1) {
                    color[py * size + px] = ((int) (90 * (1 - d) * (1 - d)) << 24);
                }
            }
        }
    }

    private final float[] tmp = new float[12];

    private void quad(int c, float nx, float ny, float nz, float... p) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        // turn the normal; faces looking away are skipped
        float rnx = nx * cy + nz * sy;
        float rnz0 = -nx * sy + nz * cy;
        float rny = ny * cp - rnz0 * sp;
        float rnz = ny * sp + rnz0 * cp;
        if (rnz <= 0.001F) {
            return;
        }
        float s = size * 0.62F * zoom;
        for (int i = 0; i < 4; i++) {
            float x = p[i * 3], y = p[i * 3 + 1], z = p[i * 3 + 2];
            float x1 = x * cy + z * sy;
            float z1 = -x * sy + z * cy;
            float y1 = y * cp - z1 * sp;
            float z2 = y * sp + z1 * cp;
            tmp[i * 3] = size * 0.5F + x1 * s;
            tmp[i * 3 + 1] = size * 0.5F - y1 * s;
            tmp[i * 3 + 2] = z2;
        }
        // light from the top left and the front, like the game's GUI items
        float lit = 0.5F + 0.5F * Math.max(0, -0.35F * rnx + 0.62F * rny + 0.7F * rnz);
        int r = Math.min(255, (int) (((c >> 16) & 0xFF) * lit));
        int g = Math.min(255, (int) (((c >> 8) & 0xFF) * lit));
        int b = Math.min(255, (int) ((c & 0xFF) * lit));
        int shaded = 0xFF000000 | r << 16 | g << 8 | b;
        triangle(shaded, 0, 1, 2);
        triangle(shaded, 0, 2, 3);
    }

    private void triangle(int c, int a, int b, int d) {
        float ax = tmp[a * 3], ay = tmp[a * 3 + 1], az = tmp[a * 3 + 2];
        float bx = tmp[b * 3], by = tmp[b * 3 + 1], bz = tmp[b * 3 + 2];
        float dx = tmp[d * 3], dy = tmp[d * 3 + 1], dz = tmp[d * 3 + 2];
        float area = (bx - ax) * (dy - ay) - (by - ay) * (dx - ax);
        if (Math.abs(area) < 1e-6F) {
            return;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, dx))));
        int maxX = Math.min(size - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, dx))));
        int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, dy))));
        int maxY = Math.min(size - 1, (int) Math.ceil(Math.max(ay, Math.max(by, dy))));
        float inv = 1 / area;
        for (int py = minY; py <= maxY; py++) {
            float fy = py + 0.5F;
            for (int px = minX; px <= maxX; px++) {
                float fx = px + 0.5F;
                float w0 = ((bx - fx) * (dy - fy) - (by - fy) * (dx - fx)) * inv;
                float w1 = ((dx - fx) * (ay - fy) - (dy - fy) * (ax - fx)) * inv;
                float w2 = 1 - w0 - w1;
                // a hair of slack so neighbouring pixels leave no gaps
                if (w0 < -0.002F || w1 < -0.002F || w2 < -0.002F) {
                    continue;
                }
                float z = w0 * az + w1 * bz + w2 * dz;
                int i = py * size + px;
                if (z > depth[i]) {
                    depth[i] = z;
                    color[i] = c;
                    pick[i] = quadId + 1;
                }
            }
        }
    }
}

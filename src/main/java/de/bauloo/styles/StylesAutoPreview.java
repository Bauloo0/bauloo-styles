package de.bauloo.styles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Draws a preview picture from a pack's own textures: crystal, anchor and armor
 * as small 3D scenes, fire flat like the built-in fire previews. Pure Java, no
 * Minecraft rendering, so it can run on the upload page's thread.
 */
public final class StylesAutoPreview {

    private static final int SIZE = 512;
    /** All scene numbers below were tuned for a 256 picture; this scales them to SIZE. */
    private static final double K = SIZE / 256.0;
    private static final String T = "assets/minecraft/textures/";

    private StylesAutoPreview() {
    }

    /** Writes a 512x512 PNG for the category; false when the pack has nothing to draw. */
    public static boolean render(Path zip, String category, Path target) {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Map<String, ZipEntry> entries = new HashMap<>();
            for (ZipEntry e : java.util.Collections.list(file.entries())) {
                entries.put(e.getName().replace('\\', '/').toLowerCase(Locale.ROOT), e);
            }
            Textures tex = new Textures(file, entries);
            BufferedImage image = switch (category) {
                case "crystal" -> crystal(tex);
                case "anchor" -> anchor(tex);
                case "armor" -> armor(tex);
                case "fire" -> fire(tex);
                case "obsidian" -> obsidian(tex);
                case "glowstone" -> glowstone(tex);
                case "stone" -> block(tex.get(true, T + "block/stone.png", T + "blocks/stone.png"), null,
                        0x22242A, 0xB4BCD0);
                case "deepslate" -> block(tex.get(true, T + "block/deepslate_top.png", T + "blocks/deepslate_top.png"),
                        tex.get(true, T + "block/deepslate.png", T + "blocks/deepslate.png"), 0x1A1A22, 0x8C96B4);
                case "snow" -> block(tex.get(true, T + "block/snow.png", T + "blocks/snow.png"),
                        tex.get(true, T + "block/grass_block_snow.png", T + "blocks/grass_side_snowed.png"), 0x22283A, 0xD0E4FF);
                case "sword", "pickaxe", "axe" -> tool(tex, category);
                case "totem" -> flat(tex.get(true, T + "item/totem_of_undying.png", T + "items/totem_of_undying.png",
                        T + "items/totem.png"), 0x2E2A12, 0xF2D25A);
                case "hotbar" -> hotbar(tex);
                case "inventory" -> inventory(tex);
                case "gap" -> flat(tex.get(true, T + "item/golden_apple.png", T + "items/golden_apple.png",
                        T + "items/apple_golden.png"), 0x3A2A10, 0xFFC850);
                case "pearl" -> flat(tex.get(true, T + "item/ender_pearl.png", T + "items/ender_pearl.png"),
                        0x10302C, 0x40E0C0);
                default -> cloud(tex, category);
            };
            if (image == null) {
                return false;
            }
            return ImageIO.write(image, "png", target.toFile());
        } catch (IOException | RuntimeException e) {
            BaulooStylesClient.LOGGER.warn("Auto preview for {} failed: {}", category, e.toString());
            return false;
        }
    }

    // ---------------------------------------------------------------- textures

    /** ARGB pixels of one texture. */
    private record Tex(int w, int h, int[] argb) {
        static Tex of(BufferedImage img) {
            return new Tex(img.getWidth(), img.getHeight(), img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth()));
        }

        Tex firstFrame() {
            if (h > w && h % w == 0) {
                int[] frame = new int[w * w];
                System.arraycopy(argb, 0, frame, 0, w * w);
                return new Tex(w, w, frame);
            }
            return this;
        }
    }

    private record Textures(ZipFile file, Map<String, ZipEntry> entries) {
        Tex get(boolean frame, String... candidates) {
            for (String c : candidates) {
                ZipEntry e = entries.get(c.toLowerCase(Locale.ROOT));
                if (e == null) {
                    continue;
                }
                try (InputStream in = file.getInputStream(e)) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null && img.getWidth() <= 4096 && img.getHeight() <= 4096) {
                        Tex t = Tex.of(img);
                        return frame ? t.firstFrame() : t;
                    }
                } catch (IOException ignored) {
                }
            }
            return null;
        }
    }

    private static Tex solid(int rgb) {
        int[] px = new int[16 * 16];
        java.util.Arrays.fill(px, 0xFF000000 | rgb);
        return new Tex(16, 16, px);
    }

    // ------------------------------------------------------------------- scene

    private static double[][] rotY(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
    }

    private static double[][] rotX(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}};
    }

    private static double[][] rotAxis(double[] axis, double a) {
        double n = Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
        double x = axis[0] / n, y = axis[1] / n, z = axis[2] / n;
        double c = Math.cos(a), s = Math.sin(a), t = 1 - c;
        return new double[][]{
                {t * x * x + c, t * x * y - s * z, t * x * z + s * y},
                {t * x * y + s * z, t * y * y + c, t * y * z - s * x},
                {t * x * z - s * y, t * y * z + s * x, t * z * z + c}};
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
            }
        }
        return r;
    }

    private static double[][] scale(double[][] m, double k) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i][j] = m[i][j] * k;
            }
        }
        return r;
    }

    private static double[] apply(double[][] m, double x, double y, double z) {
        return new double[]{m[0][0] * x + m[0][1] * y + m[0][2] * z,
                m[1][0] * x + m[1][1] * y + m[1][2] * z,
                m[2][0] * x + m[2][1] * y + m[2][2] * z};
    }

    private record Face(double depth, double[][] pts, double[][] uv, Tex tex, double shade) {
    }

    private static final class Scene {
        final List<Face> faces = new ArrayList<>();
        final double camDist, fov, screenY;
        final double[][] view;
        final double[] center;

        Scene(double camDist, double pitch, double yaw, double fov, double[] center, double screenY) {
            this.camDist = camDist;
            this.fov = fov;
            this.view = mul(rotX(-pitch), rotY(yaw));
            this.center = center;
            this.screenY = screenY;
        }

        double[] project(double[] p) {
            double[] v = apply(view, p[0] - center[0], p[1] - center[1], p[2] - center[2]);
            double z = v[2] + camDist;
            double f = fov * K / z;
            return new double[]{SIZE / 2.0 + v[0] * f, SIZE / 2.0 + screenY * K - v[1] * f, z};
        }

        void add(Tex tex, double[][] pts3, double[][] uv, double shade) {
            if (tex == null) {
                return;
            }
            double[][] pts2 = new double[4][];
            double depth = 0;
            for (int i = 0; i < 4; i++) {
                double[] p = project(pts3[i]);
                pts2[i] = new double[]{p[0], p[1]};
                depth += p[2] / 4;
            }
            faces.add(new Face(depth, pts2, uv, tex, shade));
        }

        /** Whole texture on a quad (corners: top-left, top-right, bottom-right, bottom-left). */
        void quad(Tex tex, double[][] pts3, double shade) {
            if (tex == null) {
                return;
            }
            add(tex, pts3, new double[][]{{0, 0}, {tex.w, 0}, {tex.w, tex.h}, {0, tex.h}}, shade);
        }

        /** Minecraft cuboid with the usual box UV layout on a 64x32 texture. */
        void box(Tex tex, double u, double v, double[] origin, double[] size, double[][] model, double[] pos,
                double inflate, boolean mirror) {
            if (tex == null) {
                return;
            }
            double kx = tex.w / 64.0, ky = tex.h / 32.0;
            double w = size[0], h = size[1], d = size[2];
            double x0 = origin[0] - inflate, y0 = origin[1] - inflate, z0 = origin[2] - inflate;
            double x1 = x0 + w + 2 * inflate, y1 = y0 + h + 2 * inflate, z1 = z0 + d + 2 * inflate;
            java.util.function.Function<double[], double[]> P = c -> {
                double[] r = apply(model, c[0], c[1], c[2]);
                return new double[]{pos[0] + r[0], pos[1] + r[1], pos[2] + r[2]};
            };
            double[][][] quads = {
                    {{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}},
                    {{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}},
                    {{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}},
                    {{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}},
                    {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}},
                    {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}}};
            double[][] rects = {{u + d, v + d, w, h}, {u + d + w + d, v + d, w, h}, {u, v + d, d, h},
                    {u + d + w, v + d, d, h}, {u + d, v, w, d}, {u + d + w, v, w, d}};
            double[] shades = {0.8, 0.8, 0.65, 0.65, 1.0, 0.5};
            for (int f = 0; f < 6; f++) {
                double[][] pts = new double[4][];
                for (int i = 0; i < 4; i++) {
                    pts[i] = P.apply(quads[f][i]);
                }
                double ru = rects[f][0] * kx, rv = rects[f][1] * ky, rw = rects[f][2] * kx, rh = rects[f][3] * ky;
                double[][] uv = {{ru, rv}, {ru + rw, rv}, {ru + rw, rv + rh}, {ru, rv + rh}};
                if (mirror) {
                    uv = new double[][]{uv[1], uv[0], uv[3], uv[2]};
                }
                add(tex, pts, uv, shades[f]);
            }
        }

        void blockFaces(Tex top, Tex side, double[] shades) {
            double x = -8, y = -16, z = -8, s = 16, x1 = x + s, y1 = y + s, z1 = z + s;
            quad(top, new double[][]{{x, y1, z}, {x1, y1, z}, {x1, y1, z1}, {x, y1, z1}}, shades[0]);
            quad(side, new double[][]{{x1, y1, z}, {x, y1, z}, {x, y, z}, {x1, y, z}}, shades[1]);
            quad(side, new double[][]{{x, y1, z1}, {x1, y1, z1}, {x1, y, z1}, {x, y, z1}}, shades[4]);
            quad(side, new double[][]{{x, y1, z}, {x, y1, z1}, {x, y, z1}, {x, y, z}}, shades[3]);
            quad(side, new double[][]{{x1, y1, z1}, {x1, y1, z}, {x1, y, z}, {x1, y, z1}}, shades[2]);
        }

        void render(int[] buf) {
            faces.sort(Comparator.comparingDouble(f -> -f.depth()));
            for (Face f : faces) {
                raster(buf, f.pts[0], f.pts[1], f.pts[2], f.uv[0], f.uv[1], f.uv[2], f.tex, f.shade);
                raster(buf, f.pts[0], f.pts[2], f.pts[3], f.uv[0], f.uv[2], f.uv[3], f.tex, f.shade);
            }
        }
    }

    private static void raster(int[] buf, double[] p0, double[] p1, double[] p2, double[] t0, double[] t1, double[] t2,
            Tex tex, double shade) {
        int minX = Math.max(0, (int) Math.floor(Math.min(p0[0], Math.min(p1[0], p2[0]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(p0[1], Math.min(p1[1], p2[1]))));
        int maxX = Math.min(SIZE - 1, (int) Math.ceil(Math.max(p0[0], Math.max(p1[0], p2[0]))));
        int maxY = Math.min(SIZE - 1, (int) Math.ceil(Math.max(p0[1], Math.max(p1[1], p2[1]))));
        double den = (p1[1] - p2[1]) * (p0[0] - p2[0]) + (p2[0] - p1[0]) * (p0[1] - p2[1]);
        if (Math.abs(den) < 1e-6) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5, py = y + 0.5;
                double a = ((p1[1] - p2[1]) * (px - p2[0]) + (p2[0] - p1[0]) * (py - p2[1])) / den;
                double b = ((p2[1] - p0[1]) * (px - p2[0]) + (p0[0] - p2[0]) * (py - p2[1])) / den;
                double c = 1 - a - b;
                if (a < -1e-4 || b < -1e-4 || c < -1e-4) {
                    continue;
                }
                int u = (int) Math.max(0, Math.min(tex.w - 1, a * t0[0] + b * t1[0] + c * t2[0]));
                int v = (int) Math.max(0, Math.min(tex.h - 1, a * t0[1] + b * t1[1] + c * t2[1]));
                int col = tex.argb[v * tex.w + u];
                double alpha = ((col >>> 24) & 0xFF) / 255.0;
                if (alpha <= 0) {
                    continue;
                }
                int idx = y * SIZE + x;
                int dst = buf[idx];
                int r = (int) (((col >> 16) & 0xFF) * shade * alpha + ((dst >> 16) & 0xFF) * (1 - alpha));
                int g = (int) (((col >> 8) & 0xFF) * shade * alpha + ((dst >> 8) & 0xFF) * (1 - alpha));
                int bl = (int) ((col & 0xFF) * shade * alpha + (dst & 0xFF) * (1 - alpha));
                buf[idx] = 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, bl);
            }
        }
    }

    // --------------------------------------------------------------- backdrop

    /** Vertical gradient plus a soft round glow (stands in for a blurred ellipse). */
    private static int[] backdrop(int top, int bottom, int glowRgb, int glowAlpha, double cx, double cy, double rx, double ry) {
        cx *= K;
        cy *= K;
        rx *= K;
        ry *= K;
        int[] buf = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            double t = y / (double) SIZE;
            for (int x = 0; x < SIZE; x++) {
                double r = ((top >> 16) & 0xFF) * (1 - t) + ((bottom >> 16) & 0xFF) * t;
                double g = ((top >> 8) & 0xFF) * (1 - t) + ((bottom >> 8) & 0xFF) * t;
                double b = (top & 0xFF) * (1 - t) + (bottom & 0xFF) * t;
                double dx = (x - cx) / rx, dy = (y - cy) / ry;
                double d = Math.sqrt(dx * dx + dy * dy);
                double k = d >= 1.3 ? 0 : Math.pow(Math.max(0, 1 - d / 1.3), 2) * glowAlpha / 255.0;
                r = r * (1 - k) + ((glowRgb >> 16) & 0xFF) * k;
                g = g * (1 - k) + ((glowRgb >> 8) & 0xFF) * k;
                b = b * (1 - k) + (glowRgb & 0xFF) * k;
                buf[y * SIZE + x] = 0xFF000000 | (int) r << 16 | (int) g << 8 | (int) b;
            }
        }
        return buf;
    }

    private static void shadow(int[] buf, double cx, double cy, double rx, double ry, int alpha) {
        cx *= K;
        cy *= K;
        rx *= K;
        ry *= K;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double dx = (x - cx) / (rx * 1.25), dy = (y - cy) / (ry * 1.6);
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d >= 1) {
                    continue;
                }
                double k = (1 - d) * (1 - d) * alpha / 255.0;
                int c = buf[y * SIZE + x];
                buf[y * SIZE + x] = 0xFF000000 | (int) (((c >> 16) & 0xFF) * (1 - k)) << 16
                        | (int) (((c >> 8) & 0xFF) * (1 - k)) << 8 | (int) ((c & 0xFF) * (1 - k));
            }
        }
    }

    private static BufferedImage image(int[] buf) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, SIZE, SIZE, buf, 0, SIZE);
        return img;
    }

    // --------------------------------------------------------------- previews

    private static BufferedImage crystal(Textures t) {
        Tex tex = t.get(false, T + "entity/end_crystal/end_crystal.png", T + "entity/endercrystal/endercrystal.png");
        if (tex == null) {
            return null;
        }
        Tex obsidian = t.get(true, T + "block/obsidian.png", T + "blocks/obsidian.png");
        if (obsidian == null) {
            obsidian = vanilla("obsidian", 0x14101E);
        }
        int[] buf = backdrop(0x221A34, 0x08070E, 0xBE50E6, 80, 128, 98, 90, 90);
        Scene sc = new Scene(90, 0.34, 0.62, 430, new double[]{0, 7, 0}, 6);
        sc.blockFaces(obsidian, obsidian, new double[]{1.0, 0.75, 0.6, 0.6, 0.75});
        double s45 = Math.sin(Math.PI / 4);
        double[] axis = {s45, 0, s45};
        double spin = 0.5;
        double[][] outer = mul(rotY(spin), rotAxis(axis, Math.toRadians(60)));
        double[][] inner = mul(mul(outer, rotY(spin * 1.3)), rotAxis(axis, Math.toRadians(60)));
        double[][] core = mul(mul(inner, rotY(spin)), rotAxis(axis, Math.toRadians(60)));
        double[] pos = {0, 14, 0};
        double[] o = {-4, -4, -4}, size = {8, 8, 8};
        sc.box(tex, 32, 0, o, size, scale(core, 1.53 * 0.765), pos, 0, false);
        sc.box(tex, 0, 0, o, size, scale(inner, 1.53 * 0.875), pos, 0, false);
        sc.box(tex, 0, 0, o, size, scale(outer, 1.53), pos, 0, false);
        sc.render(buf);
        return image(buf);
    }

    private static BufferedImage anchor(Textures t) {
        Tex top = t.get(true, T + "block/respawn_anchor_top.png", T + "block/respawn_anchor_top_off.png");
        Tex side = t.get(true, T + "block/respawn_anchor_side4.png", T + "block/respawn_anchor_side0.png");
        if (top == null || side == null) {
            return null;
        }
        int[] buf = backdrop(0x241C34, 0x0A0812, 0x965AFF, 60, 128, 120, 80, 80);
        shadow(buf, 128, 220, 80, 16, 150);
        Scene sc = new Scene(60, 0.5, 0.62, 520, new double[]{0, -8, 0}, 6);
        sc.blockFaces(top, side, new double[]{1.0, 0.78, 0.6, 0.6, 0.78});
        sc.render(buf);
        return image(buf);
    }

    private static BufferedImage armor(Textures t) {
        List<Tex[]> figures = new ArrayList<>();
        for (String m : new String[]{"netherite", "diamond", "iron", "gold", "chainmail"}) {
            Tex body = t.get(false, T + "entity/equipment/humanoid/" + m + ".png", T + "models/armor/" + m + "_layer_1.png");
            Tex legs = t.get(false, T + "entity/equipment/humanoid_leggings/" + m + ".png", T + "models/armor/" + m + "_layer_2.png");
            if (body != null) {
                figures.add(new Tex[]{body, legs});
            }
            if (figures.size() == 2) {
                break;
            }
        }
        if (figures.isEmpty()) {
            return null;
        }
        int[] buf = backdrop(0x1C2432, 0x080B10, 0x5AAAFF, 50, 128, 115, 88, 85);
        Scene sc = new Scene(100, 0.16, 0.45, 470, new double[]{0, 16, 0}, 4);
        double[][] id = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        double[] zero = {0, 0, 0};
        double[] offsets = figures.size() == 1 ? new double[]{0} : new double[]{-11, 11};
        for (int i = 0; i < figures.size(); i++) {
            Tex body = figures.get(i)[0];
            Tex legs = figures.get(i)[1];
            double ox = offsets[i];
            shadow(buf, 128 + ox * 5.2, 226, 34, 8, 120);
            if (legs != null) {
                sc.box(legs, 16, 16, new double[]{ox - 4, 12, -2}, new double[]{8, 12, 4}, id, zero, 0.3, false);
                sc.box(legs, 0, 16, new double[]{ox - 4, 0, -2}, new double[]{4, 12, 4}, id, zero, 0.3, false);
                sc.box(legs, 0, 16, new double[]{ox, 0, -2}, new double[]{4, 12, 4}, id, zero, 0.3, true);
            }
            sc.box(body, 0, 0, new double[]{ox - 4, 24, -4}, new double[]{8, 8, 8}, id, zero, 0.7, false);
            sc.box(body, 16, 16, new double[]{ox - 4, 12, -2}, new double[]{8, 12, 4}, id, zero, 0.6, false);
            sc.box(body, 40, 16, new double[]{ox - 8, 12, -2}, new double[]{4, 12, 4}, id, zero, 0.6, false);
            sc.box(body, 40, 16, new double[]{ox + 4, 12, -2}, new double[]{4, 12, 4}, id, zero, 0.6, true);
            sc.box(body, 0, 16, new double[]{ox - 4, 0, -2}, new double[]{4, 12, 4}, id, zero, 0.6, false);
            sc.box(body, 0, 16, new double[]{ox, 0, -2}, new double[]{4, 12, 4}, id, zero, 0.6, true);
        }
        sc.render(buf);
        return image(buf);
    }

    /** Flat like the built-in fire previews: the first frame on the shared dark background. */
    private static BufferedImage fire(Textures t) throws IOException {
        Tex flame = t.get(true, T + "block/fire_0.png", T + "blocks/fire_layer_0.png");
        if (flame == null) {
            return null;
        }
        BufferedImage out;
        try (InputStream in = StylesAutoPreview.class.getResourceAsStream("/assets/bauloo_styles/upload/fire_background.png")) {
            if (in == null) {
                return null;
            }
            BufferedImage bg = ImageIO.read(in);
            out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
            out.getGraphics().drawImage(bg, 0, 0, SIZE, SIZE, null);
        }
        int size = (int) (SIZE * 0.92);
        int left = (SIZE - size) / 2;
        int top = (int) (SIZE * 0.80) - size;
        for (int y = 0; y < size; y++) {
            int dy = top + y;
            if (dy < 0 || dy >= SIZE) {
                continue;
            }
            for (int x = 0; x < size; x++) {
                int col = flame.argb[(y * flame.h / size) * flame.w + (x * flame.w / size)];
                double a = ((col >>> 24) & 0xFF) / 255.0;
                if (a <= 0) {
                    continue;
                }
                int dst = out.getRGB(left + x, dy);
                int r = (int) (((col >> 16) & 0xFF) * a + ((dst >> 16) & 0xFF) * (1 - a));
                int g = (int) (((col >> 8) & 0xFF) * a + ((dst >> 8) & 0xFF) * (1 - a));
                int b = (int) ((col & 0xFF) * a + (dst & 0xFF) * (1 - a));
                out.setRGB(left + x, dy, 0xFF000000 | r << 16 | g << 8 | b);
            }
        }
        return out;
    }

    private static BufferedImage obsidian(Textures t) {
        Tex obsidian = t.get(true, T + "block/obsidian.png", T + "blocks/obsidian.png");
        if (obsidian == null) {
            return null;
        }
        int[] buf = backdrop(0x221A34, 0x08070E, 0x8C50E6, 60, 128, 120, 80, 80);
        shadow(buf, 128, 220, 80, 16, 150);
        Scene sc = new Scene(60, 0.5, 0.62, 520, new double[]{0, -8, 0}, 6);
        sc.blockFaces(obsidian, obsidian, new double[]{1.0, 0.78, 0.6, 0.6, 0.78});
        sc.render(buf);
        return image(buf);
    }

    /** Cuts a part out of a texture (parts 0-1) and centres it on a clear square, so it is not stretched. */
    private static Tex cropSquare(Tex t, double[] c) {
        int x0 = (int) Math.round(c[0] * t.w);
        int y0 = (int) Math.round(c[1] * t.h);
        int w = Math.max(1, Math.min(t.w - x0, (int) Math.round(c[2] * t.w)));
        int h = Math.max(1, Math.min(t.h - y0, (int) Math.round(c[3] * t.h)));
        int s = Math.max(w, h);
        int[] px = new int[s * s];
        int ox = (s - w) / 2;
        int oy = (s - h) / 2;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[(oy + y) * s + ox + x] = t.argb[(y0 + y) * t.w + x0 + x];
            }
        }
        return new Tex(s, s, px);
    }

    /** A collection from the cloud index: a cube or a flat item, as its entry says. */
    private static BufferedImage cloud(Textures t, String category) {
        CloudCategories.Def d = CloudCategories.get(category);
        if (d == null || d.previewTop() == null) {
            return null;
        }
        Tex top = t.get(true, d.previewTop());
        if (top != null && d.crop() != null) {
            top = cropSquare(top, d.crop());
        }
        if ("block".equals(d.previewType())) {
            return block(top, d.previewSide() == null ? null : t.get(true, d.previewSide()), 0x221A34, 0xB48CFF);
        }
        return flat(top, 0x221A34, 0xB48CFF);
    }

    /** One block on a dark background: top and sides (sides = top when there is no own side texture). */
    private static BufferedImage block(Tex top, Tex side, int backTop, int glow) {
        if (top == null && side == null) {
            return null;
        }
        Tex up = top != null ? top : side;
        Tex around = side != null ? side : up;
        int[] buf = backdrop(backTop, 0x08070E, glow, 60, 128, 120, 80, 80);
        shadow(buf, 128, 220, 80, 16, 150);
        Scene sc = new Scene(60, 0.5, 0.62, 520, new double[]{0, -8, 0}, 6);
        sc.blockFaces(up, around, new double[]{1.0, 0.8, 0.62, 0.62, 0.8});
        sc.render(buf);
        return image(buf);
    }

    private static BufferedImage glowstone(Textures t) {
        Tex glow = t.get(true, T + "block/glowstone.png", T + "blocks/glowstone.png");
        if (glow == null) {
            return null;
        }
        int[] buf = backdrop(0x2A2212, 0x0A0806, 0xFFC850, 60, 128, 120, 80, 80);
        shadow(buf, 128, 220, 80, 16, 150);
        Scene sc = new Scene(60, 0.5, 0.62, 520, new double[]{0, -8, 0}, 6);
        sc.blockFaces(glow, glow, new double[]{1.0, 0.85, 0.7, 0.7, 0.85});
        sc.render(buf);
        return image(buf);
    }

    /** The netherite (else diamond) sword or pickaxe, big and flat on a dark background. */
    private static BufferedImage tool(Textures t, String kind) {
        Tex item = t.get(true, T + "item/netherite_" + kind + ".png", T + "item/diamond_" + kind + ".png",
                T + "items/netherite_" + kind + ".png", T + "items/diamond_" + kind + ".png");
        return flat(item, 0x1C2432, 0x5AAAFF);
    }

    /** The hotbar across the picture, the selection frame on the first slot. */
    private static BufferedImage hotbar(Textures t) {
        Tex bar = t.get(false, T + "gui/sprites/hud/hotbar.png");
        if (bar == null) {
            return null;
        }
        Tex sel = t.get(false, T + "gui/sprites/hud/hotbar_selection.png");
        int[] buf = backdrop(0x1E2A22, 0x080B10, 0x7AD9A0, 40, 128, 128, 110, 60);
        int w = (int) (SIZE * 0.92);
        int h = Math.max(1, w * 22 / 182);
        int left = (SIZE - w) / 2;
        int top = (SIZE - h) / 2;
        blit(buf, bar, left, top, w, h);
        if (sel != null) {
            int sw = w * 24 / 182;
            int sh = sw * sel.h / Math.max(1, sel.w);
            blit(buf, sel, left - sw / 24, top - sw / 24, sw, sh);
        }
        return image(buf);
    }

    /** The player inventory screen (the 176 x 166 part of inventory.png), big on a dark background. */
    private static BufferedImage inventory(Textures t) {
        Tex inv = t.get(false, T + "gui/container/inventory.png");
        if (inv == null) {
            return null;
        }
        int k = Math.max(1, inv.w / 256);
        int cw = Math.min(inv.w, 176 * k), ch = Math.min(inv.h, 166 * k);
        int[] px = new int[cw * ch];
        for (int y = 0; y < ch; y++) {
            System.arraycopy(inv.argb, y * inv.w, px, y * cw, cw);
        }
        Tex part = new Tex(cw, ch, px);
        int[] buf = backdrop(0x1E2230, 0x080B10, 0x9AA8FF, 40, 128, 128, 110, 90);
        int w = (int) (SIZE * 0.86);
        int h = w * ch / cw;
        blit(buf, part, (SIZE - w) / 2, (SIZE - h) / 2, w, h);
        return image(buf);
    }

    /** Draws a texture stretched into a box, with its alpha. */
    private static void blit(int[] buf, Tex tex, int x0, int y0, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = x0 + x, py = y0 + y;
                if (px < 0 || py < 0 || px >= SIZE || py >= SIZE) {
                    continue;
                }
                int col = tex.argb[(y * tex.h / h) * tex.w + (x * tex.w / w)];
                double a = ((col >>> 24) & 0xFF) / 255.0;
                if (a <= 0) {
                    continue;
                }
                int i = py * SIZE + px;
                int dst = buf[i];
                int r = (int) (((col >> 16) & 0xFF) * a + ((dst >> 16) & 0xFF) * (1 - a));
                int g = (int) (((col >> 8) & 0xFF) * a + ((dst >> 8) & 0xFF) * (1 - a));
                int b = (int) ((col & 0xFF) * a + (dst & 0xFF) * (1 - a));
                buf[i] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
    }

    /** One item texture, big and flat on a dark background with a soft glow. */
    private static BufferedImage flat(Tex item, int backTop, int glow) {
        if (item == null) {
            return null;
        }
        int[] buf = backdrop(backTop, 0x080B10, glow, 50, 128, 128, 90, 90);
        int size = (int) (SIZE * 0.7);
        int left = (SIZE - size) / 2;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int col = item.argb[(y * item.h / size) * item.w + (x * item.w / size)];
                double a = ((col >>> 24) & 0xFF) / 255.0;
                if (a <= 0) {
                    continue;
                }
                int i = (left + y) * SIZE + left + x;
                int dst = buf[i];
                int r = (int) (((col >> 16) & 0xFF) * a + ((dst >> 16) & 0xFF) * (1 - a));
                int g = (int) (((col >> 8) & 0xFF) * a + ((dst >> 8) & 0xFF) * (1 - a));
                int b = (int) ((col & 0xFF) * a + (dst & 0xFF) * (1 - a));
                buf[i] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
        return image(buf);
    }

    /** A vanilla block texture from the running game, or a flat color if that fails. */
    private static Tex vanilla(String block, int fallbackRgb) {
        try {
            var id = net.minecraft.util.Identifier.of("minecraft", "textures/block/" + block + ".png");
            var resource = net.minecraft.client.MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().getInputStream()) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null) {
                        return Tex.of(img).firstFrame();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return solid(fallbackRgb);
    }
}

package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws the head cosmetic and the aura on your own player. Sizes below are in skin
 * pixels (1/16 block); in this model space y points down, the head sits at y -8..0 and the
 * feet at y 24.
 */
public class CosmeticsFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final float PX = 1 / 16.0F;
    private static final int FULL_BRIGHT = 0xF000F0;

    public CosmeticsFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || state.id != mc.player.getId() || state.invisible) {
            return;
        }
        try {
            String head = Cosmetics.current(Cosmetics.Slot.HEAD);
            if (!"none".equals(head)) {
                head(matrices, queue, light, state, head);
            }
            String aura = Cosmetics.current(Cosmetics.Slot.AURA);
            if (!"none".equals(aura)) {
                aura(matrices, queue, state, aura);
            }
        } catch (RuntimeException e) {
            // a cosmetic must never break the player rendering
        }
    }

    private static void vertex(MatrixStack.Entry entry, VertexConsumer vc, float x, float y, float z, float u, float v,
                               int color, int light, float nx, float ny, float nz) {
        vc.vertex(entry, x, y, z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(entry, nx, ny, nz);
    }

    // ------------------------------------------------------------------ head

    private void head(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                      PlayerEntityRenderState state, String key) {
        matrices.push();
        getContextModel().head.applyTransform(matrices);
        switch (key) {
            case "halo" -> {
                float bob = (float) Math.sin(state.age * 0.08F) * 0.5F;
                matrices.translate(0, (-11.5F + bob) * PX, 0);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.age * 2));
                int c = 0xFF000000 | Cosmetics.color();
                queue.submitCustom(matrices, RenderLayers.entityTranslucentEmissive(Cosmetics.WHITE), (entry, vc) -> {
                    ring(entry, vc, 3.4F * PX, 4.6F * PX, 0, c, FULL_BRIGHT);
                    ring(entry, vc, 4.6F * PX, 5.6F * PX, 0, (0x50 << 24) | (c & 0xFFFFFF), FULL_BRIGHT);
                });
            }
            case "crown" -> queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(Cosmetics.WHITE), (entry, vc) -> {
                int gold = 0xFFFFC83C;
                int dark = 0xFFC88C1E;
                float r = 4.35F;
                // band around the head
                box(entry, vc, -r, -9.6F, -r, r, -8.0F, -r + 0.6F, gold, light);
                box(entry, vc, -r, -9.6F, r - 0.6F, r, -8.0F, r, gold, light);
                box(entry, vc, -r, -9.6F, -r, -r + 0.6F, -8.0F, r, gold, light);
                box(entry, vc, r - 0.6F, -9.6F, -r, r, -8.0F, r, gold, light);
                // points: corners and the middle of each side
                float[][] points = {{-r, -r}, {r - 0.9F, -r}, {-r, r - 0.9F}, {r - 0.9F, r - 0.9F},
                        {-0.45F, -r}, {-0.45F, r - 0.9F}, {-r, -0.45F}, {r - 0.9F, -0.45F}};
                for (float[] p : points) {
                    box(entry, vc, p[0], -11.4F, p[1], p[0] + 0.9F, -9.6F, p[1] + 0.9F, dark, light);
                }
                // gems on the front and back
                box(entry, vc, -0.6F, -9.2F, -r - 0.2F, 0.6F, -8.4F, -r + 0.2F, 0xFFE0305A, light);
                box(entry, vc, -0.6F, -9.2F, r - 0.2F, 0.6F, -8.4F, r + 0.2F, 0xFF4AA0FF, light);
            });
            case "horns" -> queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(Cosmetics.WHITE), (entry, vc) -> {
                for (int side = -1; side <= 1; side += 2) {
                    float[][] parts = {{3.0F, -8.4F, 1.8F}, {3.7F, -9.9F, 1.4F}, {4.5F, -11.2F, 1.1F}, {5.4F, -12.2F, 0.8F}, {6.2F, -12.8F, 0.5F}};
                    for (int i = 0; i < parts.length; i++) {
                        float[] p = parts[i];
                        float h = p[2] / 2;
                        int col = 0xFF000000 | StylesTheme.mix(0xFF8A1424, 0xFF1E0408, i / (float) (parts.length - 1));
                        box(entry, vc, side * p[0] - h, p[1] - h, -1.5F - h, side * p[0] + h, p[1] + h, -1.5F + h, col, light);
                    }
                }
            });
            case "ears" -> queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(Cosmetics.WHITE), (entry, vc) -> {
                int fur = 0xFF000000 | Cosmetics.color();
                int dark = 0xFF000000 | StylesTheme.mix(fur, 0xFF000000, 0.35F);
                for (int side = -1; side <= 1; side += 2) {
                    float x = side * 2.6F;
                    box(entry, vc, x - 1.5F, -9.4F, -0.5F, x + 1.5F, -8.0F, 0.5F, dark, light);
                    box(entry, vc, x - 1.0F, -10.6F, -0.45F, x + 1.0F, -9.4F, 0.45F, fur, light);
                    box(entry, vc, x - 0.5F, -11.5F, -0.4F, x + 0.5F, -10.6F, 0.4F, fur, light);
                    box(entry, vc, x - 0.7F, -10.3F, -0.55F, x + 0.7F, -8.8F, -0.45F, 0xFFFF9EC4, light);
                }
            });
            case "pumpkin_head" -> {
                float r = 4.7F;
                queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(Cosmetics.WHITE), (entry, vc) -> {
                    int orange = 0xFFF07A16;
                    int ridge = 0xFFC9600F;
                    box(entry, vc, -r, -9.2F, -r, r, 0.4F, r, orange, light);
                    for (float x : new float[]{-2.4F, 2.4F}) {
                        box(entry, vc, x - 0.35F, -9.15F, -r - 0.05F, x + 0.35F, 0.35F, -r + 0.2F, ridge, light);
                        box(entry, vc, x - 0.35F, -9.15F, r - 0.2F, x + 0.35F, 0.35F, r + 0.05F, ridge, light);
                        box(entry, vc, -r - 0.05F, -9.15F, x - 0.35F, -r + 0.2F, 0.35F, x + 0.35F, ridge, light);
                        box(entry, vc, r - 0.2F, -9.15F, x - 0.35F, r + 0.05F, 0.35F, x + 0.35F, ridge, light);
                    }
                    box(entry, vc, -0.7F, -11.0F, -0.7F, 0.7F, -9.2F, 0.7F, 0xFF5F8A32, light);
                    // the carved face on the front: dark holes
                    pumpkinFace(entry, vc, -r - 0.08F, -r + 0.02F, 0.0F, 0xFF2A0E00, light);
                });
                // candle light inside the holes, flickering
                float flick = 0.82F + 0.18F * (float) Math.sin(state.age * 0.31F) * (float) Math.sin(state.age * 0.77F);
                int glowColor = (Math.round(flick * 255) << 24) | 0xFFC84A;
                queue.submitCustom(matrices, RenderLayers.entityTranslucentEmissive(Cosmetics.WHITE), (entry, vc) ->
                        pumpkinFace(entry, vc, -r - 0.11F, -r - 0.07F, 0.25F, glowColor, FULL_BRIGHT));
            }
            case "witch_hat" -> queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(Cosmetics.WHITE), (entry, vc) -> {
                int hat = 0xFF2A1438;
                box(entry, vc, -6.8F, -8.7F, -6.8F, 6.8F, -8.1F, 6.8F, 0xFF3A1C4C, light);
                // the cone in steps, its tip bending to the side
                float[][] cone = {{-8.7F, -10.8F, 3.9F, 0}, {-10.8F, -12.6F, 3.0F, 0.3F}, {-12.6F, -14.2F, 2.2F, 0.9F},
                        {-14.2F, -15.5F, 1.5F, 1.7F}, {-15.5F, -16.4F, 0.9F, 2.6F}, {-16.4F, -17.0F, 0.5F, 3.4F}};
                for (float[] c : cone) {
                    box(entry, vc, c[3] - c[2], c[1], -c[2], c[3] + c[2], c[0], c[2], hat, light);
                }
                box(entry, vc, -4.05F, -10.0F, -4.05F, 4.05F, -8.8F, 4.05F, 0xFF8CFF46, light);
                box(entry, vc, -0.8F, -9.9F, -4.25F, 0.8F, -8.9F, -4.0F, 0xFFFFD25A, light);
            });
            case "tophat" -> queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(Cosmetics.WHITE), (entry, vc) -> {
                int black = 0xFF18161E;
                int band = 0xFF000000 | Cosmetics.color();
                box(entry, vc, -5.5F, -8.7F, -5.5F, 5.5F, -8.0F, 5.5F, black, light);
                box(entry, vc, -3.6F, -15.2F, -3.6F, 3.6F, -8.7F, 3.6F, black, light);
                box(entry, vc, -3.75F, -10.2F, -3.75F, 3.75F, -8.9F, 3.75F, band, light);
            });
            default -> {
            }
        }
        matrices.pop();
    }

    /** Eyes, nose and a toothy mouth on the front of the pumpkin head, between z0 and z1; shrink makes them smaller. */
    private static void pumpkinFace(MatrixStack.Entry e, VertexConsumer vc, float z0, float z1, float shrink, int color, int light) {
        float s = shrink;
        for (int side = -1; side <= 1; side += 2) {
            float cx = side * 2.3F;
            box(e, vc, cx - 1.1F + s, -5.6F + s, z0, cx + 1.1F - s, -4.6F - s, z1, color, light);
            box(e, vc, cx - 0.5F + s * 0.5F, -6.4F + s, z0, cx + 0.5F - s * 0.5F, -5.6F, z1, color, light);
        }
        box(e, vc, -0.5F + s * 0.5F, -4.2F + s, z0, 0.5F - s * 0.5F, -3.5F, z1, color, light);
        box(e, vc, -3.3F + s, -2.7F + s, z0, 3.3F - s, -1.6F - s, z1, color, light);
        for (float x : new float[]{-2.2F, 0.0F, 2.2F}) {
            box(e, vc, x - 0.4F + s * 0.4F, -3.2F + s, z0, x + 0.4F - s * 0.4F, -2.7F, z1, color, light);
        }
        for (float x : new float[]{-1.1F, 1.1F}) {
            box(e, vc, x - 0.4F + s * 0.4F, -1.6F, z0, x + 0.4F - s * 0.4F, -1.1F - s, z1, color, light);
        }
    }

    /** A box between two corners given in skin pixels, white texture tinted by color. */
    private static void box(MatrixStack.Entry e, VertexConsumer vc, float x0, float y0, float z0,
                            float x1, float y1, float z1, int color, int light) {
        x0 *= PX; y0 *= PX; z0 *= PX; x1 *= PX; y1 *= PX; z1 *= PX;
        int shade = (color & 0xFF000000) | StylesTheme.mix(color, 0xFF000000, 0.18F);
        // -z / +z
        face(e, vc, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, color, light, 0, 0, -1);
        face(e, vc, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, color, light, 0, 0, 1);
        // -x / +x
        face(e, vc, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, shade, light, -1, 0, 0);
        face(e, vc, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, shade, light, 1, 0, 0);
        // top (-y) / bottom (+y)
        face(e, vc, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, color, light, 0, -1, 0);
        face(e, vc, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, shade, light, 0, 1, 0);
    }

    private static void face(MatrixStack.Entry e, VertexConsumer vc,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             int color, int light, float nx, float ny, float nz) {
        vertex(e, vc, ax, ay, az, 0, 0, color, light, nx, ny, nz);
        vertex(e, vc, bx, by, bz, 1, 0, color, light, nx, ny, nz);
        vertex(e, vc, cx, cy, cz, 1, 1, color, light, nx, ny, nz);
        vertex(e, vc, dx, dy, dz, 0, 1, color, light, nx, ny, nz);
    }

    /** A flat ring in the x-z plane at height y. */
    private static void ring(MatrixStack.Entry e, VertexConsumer vc, float r0, float r1, float y, int color, int light) {
        int seg = 32;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            face(e, vc, c0 * r0, y, s0 * r0, c1 * r0, y, s1 * r0, c1 * r1, y, s1 * r1, c0 * r1, y, s0 * r1,
                    color, light, 0, -1, 0);
        }
    }

    // ------------------------------------------------------------------ aura

    private void aura(MatrixStack matrices, OrderedRenderCommandQueue queue, PlayerEntityRenderState state, String key) {
        int rgb = Cosmetics.color();
        RenderLayer glow = RenderLayers.entityTranslucentEmissive(Cosmetics.WHITE);
        float age = state.age;
        switch (key) {
            case "orbit" -> queue.submitCustom(matrices, glow, (entry, vc) -> {
                for (int i = 0; i < 3; i++) {
                    double a = age * 0.09 + i * Math.PI * 2 / 3;
                    float x = (float) Math.cos(a) * 11;
                    float z = (float) Math.sin(a) * 11;
                    float y = 11 + (float) Math.sin(age * 0.07 + i * 2) * 3;
                    box(entry, vc, x - 0.8F, y - 0.8F, z - 0.8F, x + 0.8F, y + 0.8F, z + 0.8F, 0xFF000000 | rgb, FULL_BRIGHT);
                    box(entry, vc, x - 1.6F, y - 1.6F, z - 1.6F, x + 1.6F, y + 1.6F, z + 1.6F, 0x40000000 | rgb, FULL_BRIGHT);
                }
            });
            case "ring" -> queue.submitCustom(matrices, glow, (entry, vc) -> {
                float pulse = (float) (0.6 + 0.4 * Math.sin(age * 0.1));
                ring(entry, vc, 9.0F * PX, 10.0F * PX, 23.8F * PX, (Math.round(pulse * 230) << 24) | rgb, FULL_BRIGHT);
                float k = (age % 40) / 40.0F;
                float r = 6 + k * 10;
                ring(entry, vc, r * PX, (r + 0.6F) * PX, 23.7F * PX, (Math.round((1 - k) * 160) << 24) | rgb, FULL_BRIGHT);
            });
            case "sparkles" -> queue.submitCustom(matrices, glow, (entry, vc) -> {
                for (int i = 0; i < 16; i++) {
                    float seed = i * 12.9898F;
                    float phase = (float) ((age * 0.012 + (Math.sin(seed) * 0.5 + 0.5)) % 1.0);
                    double a = seed * 7.13;
                    float rad = 7 + (float) (Math.sin(seed * 3.1) * 0.5 + 0.5) * 6;
                    float x = (float) Math.cos(a) * rad;
                    float z = (float) Math.sin(a) * rad;
                    float y = 24 - phase * 30;
                    float s = 0.5F * (1 - phase) + 0.2F;
                    int alpha = Math.round(255 * (float) Math.sin(phase * Math.PI));
                    box(entry, vc, x - s, y - s, z - s, x + s, y + s, z + s, (alpha << 24) | rgb, FULL_BRIGHT);
                }
            });
            case "bats" -> {
                // three bats circling the head, flapping
                for (int i = 0; i < 3; i++) {
                    double a = age * 0.06 + i * Math.PI * 2 / 3;
                    float x = (float) Math.cos(a) * 12;
                    float z = (float) Math.sin(a) * 12;
                    float y = -3 + (float) Math.sin(age * 0.09 + i * 2) * 2.5F;
                    float tip = (float) Math.sin(age * 0.9 + i * 1.3) * 1.4F;
                    matrices.push();
                    matrices.translate(x * PX, y * PX, z * PX);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) -a));
                    queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(Cosmetics.WHITE), (entry, vc) -> {
                        int body = 0xFF1A0C1F;
                        box(entry, vc, -0.6F, -0.6F, -0.9F, 0.6F, 0.6F, 0.9F, body, FULL_BRIGHT);
                        for (int side = -1; side <= 1; side += 2) {
                            float in0 = side * 0.6F;
                            float in1 = side * 2.4F;
                            float out1 = side * 4.2F;
                            box(entry, vc, Math.min(in0, in1), -0.2F + tip * 0.4F, -0.7F, Math.max(in0, in1), 0.15F + tip * 0.4F, 0.7F,
                                    body, FULL_BRIGHT);
                            box(entry, vc, Math.min(in1, out1), -0.2F + tip, -0.5F, Math.max(in1, out1), 0.15F + tip, 0.5F,
                                    body, FULL_BRIGHT);
                        }
                    });
                    queue.submitCustom(matrices, glow, (entry, vc) -> {
                        box(entry, vc, -0.45F, -0.35F, -0.98F, -0.15F, -0.05F, -0.9F, 0xFFFF3B3B, FULL_BRIGHT);
                        box(entry, vc, 0.15F, -0.35F, -0.98F, 0.45F, -0.05F, -0.9F, 0xFFFF3B3B, FULL_BRIGHT);
                    });
                    matrices.pop();
                }
            }
            case "souls" -> queue.submitCustom(matrices, glow, (entry, vc) -> {
                // pale wisps spiralling up around the player and fading out
                for (int i = 0; i < 10; i++) {
                    float seed = i * 12.9898F;
                    float phase = (float) ((age * 0.008 + (Math.sin(seed) * 0.5 + 0.5)) % 1.0);
                    double a = seed * 7.13 + phase * Math.PI * 1.5;
                    float rad = 9 - phase * 3;
                    float x = (float) Math.cos(a) * rad;
                    float z = (float) Math.sin(a) * rad;
                    float y = 22 - phase * 34;
                    float s = 0.7F * (1 - phase) + 0.25F;
                    int alpha = Math.round(230 * (float) Math.sin(phase * Math.PI));
                    box(entry, vc, x - s, y - s, z - s, x + s, y + s, z + s, (alpha << 24) | 0xD8FFFF, FULL_BRIGHT);
                    box(entry, vc, x - s * 2.2F, y - s * 2.2F, z - s * 2.2F, x + s * 2.2F, y + s * 2.2F, z + s * 2.2F,
                            (Math.round(alpha * 0.25F) << 24) | 0x7FF5FF, FULL_BRIGHT);
                }
            });
            default -> {
            }
        }
    }
}

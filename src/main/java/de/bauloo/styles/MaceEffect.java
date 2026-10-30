package de.bauloo.styles;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mace Effect: something happens where your mace smash lands (a hit with the mace while falling,
 * the hit that gets the fall bonus). The higher you fell, the bigger it is. Only on your screen, drawn
 * by the mod itself, so it shows even with particles off. Each effect takes its buffers one after the
 * other, never two at once (two at once crashed some games, see KillEffect).
 */
public final class MaceEffect {

    /** the effects you can pick, in menu order: key -> label */
    public static final String[][] EFFECTS = {
            {"ring", "Shockwave Ring"}, {"crater", "Crater"}, {"lightning", "Lightning Slam"},
            {"anime", "Anime Impact"}, {"meteor", "Meteor Impact"}, {"shatter", "Glass Shatter"},
            {"gravity", "Gravity Pulse"}, {"damage", "Damage Number"}};

    private static final List<Fx> FX = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private MaceEffect() {
    }

    public static boolean valid(String effect) {
        for (String[] e : EFFECTS) {
            if (e[0].equals(effect)) {
                return true;
            }
        }
        return false;
    }

    /** You hit something (render thread): a mace smash plays the effect on the one you hit. */
    public static void onAttack(Entity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || !valid(StylesUiConfig.maceEffect) || !player.getMainHandStack().isOf(Items.MACE)) {
            return;
        }
        // the same test the game uses for the smash bonus
        double fall = player.fallDistance;
        if (fall <= 1.5 || player.isGliding()) {
            return;
        }
        play(StylesUiConfig.maceEffect, new Vec3d(target.getX(), target.getY(), target.getZ()), fall,
                target.getHeight());
    }

    /** The extra damage of a smash after falling this far (vanilla mace rules, before armor and Density). */
    static float smashBonus(double fall) {
        if (fall <= 3) {
            return (float) (4 * fall);
        }
        if (fall <= 8) {
            return (float) (12 + 2 * (fall - 3));
        }
        return (float) (22 + (fall - 8));
    }

    /** For the TEST button: a smash from 10 blocks a few blocks in front of you. */
    public static void test() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !valid(StylesUiConfig.maceEffect)) {
            return;
        }
        Vec3d look = mc.player.getRotationVector().multiply(1, 0, 1).normalize().multiply(4);
        play(StylesUiConfig.maceEffect, new Vec3d(mc.player.getX() + look.x, mc.player.getY(), mc.player.getZ() + look.z), 10, 1.8F);
    }

    public static void play(String effect, Vec3d at, double fall, float height) {
        if ("lightning".equals(effect)) {
            // a real (silent, harmless) bolt from the kill effect, plus our own blue flash ring
            KillEffect.play("lightning", at, "", 0);
        }
        if (FX.size() > 16) {
            FX.remove(0);
        }
        FX.add(new Fx(effect, at, fall, height, StylesUiConfig.maceColor & 0xFFFFFF));
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (FX.isEmpty()) {
                return;
            }
            try {
                MatrixStack matrices = context.matrices();
                VertexConsumerProvider consumers = context.consumers();
                Vec3d cam = context.worldState().cameraRenderState.pos;
                Quaternionf look = context.worldState().cameraRenderState.orientation;
                if (matrices != null && consumers != null && cam != null && look != null) {
                    render(matrices, consumers, cam, look);
                }
            } catch (Throwable e) {
                FX.clear(); // never break world rendering over an effect
            }
        });
    }

    /** one running effect */
    private static final class Fx {
        final String type;
        final double x, y, z;
        final long start = System.currentTimeMillis();
        /** 0.4 (small hop) .. 1.6 (huge fall) */
        final float power;
        final float damage;
        final float height;
        final int color;
        /** pieces, meaning depends on the effect */
        final float[][] bits;

        Fx(String type, Vec3d at, double fall, float height, int color) {
            this.type = type;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.power = (float) Math.max(0.4, Math.min(1.6, fall / 8));
            this.damage = smashBonus(fall);
            this.height = height;
            this.color = color;
            this.bits = makeBits();
        }

        long life() {
            return switch (type) {
                case "ring" -> 1200;
                case "crater" -> 1900;
                case "lightning" -> 700;
                case "anime" -> 650;
                case "meteor" -> 1500;
                case "shatter" -> 1500;
                case "gravity" -> 1300;
                default -> 1700; // damage
            };
        }

        float[][] makeBits() {
            int n = switch (type) {
                case "ring" -> 14;      // cracks: {angle, length, wiggle seed}
                case "crater" -> 34;    // chunks: {x, y, z, vx, vy, vz, size, shade, spin}
                case "anime" -> 26;     // speed lines: {angle, length, width, delay}
                case "meteor" -> 40;    // embers: {dir x, y, z, speed, size}
                case "shatter" -> 30;   // shards: {dir x, y, z, speed, size, spin, tilt}
                default -> 0;
            };
            float[][] out = new float[n][];
            for (int i = 0; i < n; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                out[i] = switch (type) {
                    case "ring" -> new float[]{(float) (i * Math.PI * 2 / n + RANDOM.nextGaussian() * 0.15),
                            2.0F + RANDOM.nextFloat() * 2.2F, RANDOM.nextFloat() * 100};
                    case "crater" -> {
                        double speed = 1.5 + RANDOM.nextDouble() * 3.5;
                        yield new float[]{(float) (Math.cos(a) * 0.5), 0.05F, (float) (Math.sin(a) * 0.5),
                                (float) (Math.cos(a) * speed), (float) (3.5 + RANDOM.nextDouble() * 4.5), (float) (Math.sin(a) * speed),
                                0.1F + RANDOM.nextFloat() * 0.16F, RANDOM.nextFloat(), (float) (RANDOM.nextGaussian() * 600)};
                    }
                    case "anime" -> new float[]{(float) a, 1.2F + RANDOM.nextFloat() * 1.6F,
                            0.02F + RANDOM.nextFloat() * 0.05F, RANDOM.nextFloat() * 0.25F};
                    case "meteor", "shatter" -> {
                        Vector3f d = new Vector3f((float) RANDOM.nextGaussian(), Math.abs((float) RANDOM.nextGaussian()) + 0.3F,
                                (float) RANDOM.nextGaussian()).normalize();
                        yield new float[]{d.x, d.y, d.z, 3 + RANDOM.nextFloat() * 4, 0.05F + RANDOM.nextFloat() * 0.12F,
                                (float) (RANDOM.nextGaussian() * 720), RANDOM.nextFloat() * 6.28F};
                    }
                    default -> new float[0];
                };
            }
            return out;
        }
    }

    private static void render(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam, Quaternionf look) {
        long now = System.currentTimeMillis();
        FX.removeIf(fx -> now - fx.start > fx.life());
        Vector3f right = new Vector3f(1, 0, 0).rotate(look);
        Vector3f up = new Vector3f(0, 1, 0).rotate(look);
        for (Fx fx : FX) {
            float sec = (now - fx.start) / 1000F;
            float t = (now - fx.start) / (float) fx.life();
            matrices.push();
            matrices.translate(fx.x - cam.x, fx.y - cam.y, fx.z - cam.z);
            switch (fx.type) {
                case "ring" -> ring(matrices, consumers, fx, t);
                case "crater" -> crater(matrices, consumers, fx, sec, t);
                case "lightning" -> lightning(matrices, consumers, fx, t);
                case "anime" -> anime(matrices, consumers, fx, t, right, up);
                case "meteor" -> meteor(matrices, consumers, fx, sec, right, up);
                case "shatter" -> shatter(matrices, consumers, fx, sec, t, right, up);
                case "gravity" -> gravity(matrices, consumers, fx, t);
                default -> damage(matrices, consumers, fx, t, look);
            }
            matrices.pop();
        }
    }

    // ------------------------------------------------------------------ the effects

    /** Two rings race over the ground, and cracks run out from where you landed. */
    private static void ring(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float t) {
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        float p = fx.power;
        for (int r = 0; r < 2; r++) {
            float k = clamp((t - r * 0.15F) / 0.75F);
            if (k <= 0 || k >= 1) {
                continue;
            }
            float radius = 0.3F + (r == 0 ? 5.5F : 3.8F) * p * easeOut(k);
            float width = (0.2F + 0.6F * (1 - k)) * p;
            int bright = argb((float) Math.pow(1 - k, 1.3), r == 0 ? fx.color : lighten(fx.color, 0.55F));
            int none = argb(0, fx.color);
            annulus(glow, m, 0.03F, Math.max(0, radius - width), radius, none, bright);
            annulus(glow, m, 0.03F, radius, radius + width * 0.6F, bright, none);
            wall(glow, m, radius, 0.02F, 0.6F * p * (1 - k), bright, none);
        }
        // cracks: glowing zigzags that light up fast and fade slowly
        float grow = easeOut(clamp(t / 0.18F));
        float fade = 1 - clamp((t - 0.35F) / 0.65F);
        for (float[] c : fx.bits) {
            float len = c[1] * p * grow;
            float a = c[0];
            float px = 0, pz = 0;
            int steps = 6;
            for (int i = 1; i <= steps; i++) {
                float f = i / (float) steps;
                float wig = (float) Math.sin(c[2] + i * 2.3) * 0.35F;
                float nx = (float) Math.cos(a + wig * 0.4F) * len * f;
                float nz = (float) Math.sin(a + wig * 0.4F) * len * f;
                float w = 0.06F * p * (1 - f * 0.7F);
                int col = argb(fade * (1 - f * 0.5F), lighten(fx.color, 0.3F));
                ribbonFlat(glow, m, px, pz, nx, nz, w, 0.035F, col);
                px = nx;
                pz = nz;
            }
        }
    }

    /** The ground bursts: chunks of dirt and stone fly up, and a dusty ring rolls out. */
    private static void crater(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec, float t) {
        float gravity = 18;
        float fade = sec > 1.3F ? 1 - (sec - 1.3F) / 0.6F : 1;
        VertexConsumer solid = consumers.getBuffer(RenderLayers.debugQuads());
        Matrix4f m = matrices.peek().getPositionMatrix();
        int[] shades = {0x6B4A2E, 0x7A7A7A, 0x5A3C22, 0x8C8C8C, 0x4A3220};
        for (float[] b : fx.bits) {
            float land = landTime(b[1], b[4] * fx.power, gravity);
            float ft = Math.min(sec, land);
            float px = b[0] + b[3] * ft * 0.55F * fx.power;
            float pz = b[2] + b[5] * ft * 0.55F * fx.power;
            float py = Math.max(0, b[1] + b[4] * fx.power * ft - 0.5F * gravity * ft * ft);
            float size = b[6] * (0.7F + 0.5F * fx.power) * fade;
            if (size <= 0.01F) {
                continue;
            }
            matrices.push();
            matrices.translate(px, py + size / 2, pz);
            matrices.multiply(new Quaternionf().rotationXYZ(b[8] * ft * 0.017F, b[8] * ft * 0.012F, 0));
            int col = 0xFF000000 | shades[(int) (b[7] * shades.length) % shades.length];
            box(solid, matrices.peek().getPositionMatrix(), size / 2, size / 2, size / 2, col);
            matrices.pop();
        }
        // then the dust, a soft light ring that rolls out and rises a little
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        m = matrices.peek().getPositionMatrix();
        float k = easeOut(clamp(t / 0.7F));
        float radius = (0.5F + 3.2F * k) * fx.power;
        float a = 0.35F * (1 - clamp(t));
        annulus(glow, m, 0.05F, radius * 0.4F, radius, argb(0, 0xD8C8A8), argb(a, 0xD8C8A8));
        annulus(glow, m, 0.05F, radius, radius * 1.25F, argb(a, 0xD8C8A8), argb(0, 0xD8C8A8));
        wall(glow, m, radius, 0.05F, 0.9F * fx.power * (1 - k * 0.5F), argb(a * 0.7F, 0xD8C8A8), argb(0, 0xD8C8A8));
    }

    /** Next to the real bolt: a quick blue flash on the ground and an electric ring. */
    private static void lightning(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float t) {
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        float flash = (float) Math.pow(1 - clamp(t / 0.4F), 2);
        annulus(glow, m, 0.04F, 0, 1.8F * fx.power, argb(flash * 0.9F, 0xDDEEFF), argb(0, 0x6AA8FF));
        float k = easeOut(t);
        float radius = (0.5F + 4F * k) * fx.power;
        int c = argb(1 - t, 0x8AC8FF);
        annulus(glow, m, 0.04F, radius - 0.15F, radius, argb(0, 0x8AC8FF), c);
        annulus(glow, m, 0.04F, radius, radius + 0.1F, c, argb(0, 0x8AC8FF));
    }

    /** Anime style: white speed lines burst out around the target and a flash star pops. */
    private static void anime(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float t,
                              Vector3f right, Vector3f up) {
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        float cy = fx.height * 0.55F;
        // the flash: a bright star in the camera plane
        float flash = (float) Math.pow(1 - clamp(t / 0.35F), 1.5);
        if (flash > 0) {
            float size = (1.2F + 1.6F * fx.power) * (0.6F + 0.4F * (1 - flash));
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 4;
                Vector3f d = new Vector3f(right).mul((float) Math.cos(a)).add(new Vector3f(up).mul((float) Math.sin(a)));
                Vector3f n = new Vector3f(right).mul((float) -Math.sin(a)).add(new Vector3f(up).mul((float) Math.cos(a)));
                float w = i % 2 == 0 ? 0.12F : 0.06F;
                spike(glow, m, 0, cy, 0, d, n, size * (i % 2 == 0 ? 1 : 0.6F), w, argb(flash, 0xFFFFFF));
            }
            disc(glow, m, 0, cy, 0, right, up, 0.9F * fx.power * flash + 0.2F, argb(flash * 0.8F, lighten(fx.color, 0.6F)), argb(0, fx.color));
        }
        // speed lines: thin streaks shooting outwards
        for (float[] l : fx.bits) {
            float k = clamp((t - l[3]) / 0.5F);
            if (k <= 0 || k >= 1) {
                continue;
            }
            Vector3f d = new Vector3f(right).mul((float) Math.cos(l[0])).add(new Vector3f(up).mul((float) Math.sin(l[0])));
            Vector3f n = new Vector3f(right).mul((float) -Math.sin(l[0])).add(new Vector3f(up).mul((float) Math.cos(l[0])));
            float inner = (0.6F + 2.2F * easeOut(k)) * fx.power;
            float len = l[1] * fx.power * (1 - k * 0.5F);
            Vector3f a = new Vector3f(d).mul(inner);
            Vector3f b = new Vector3f(d).mul(inner + len);
            int c0 = argb(0, 0xFFFFFF);
            int c1 = argb((1 - k) * 0.95F, 0xFFFFFF);
            float w = l[2];
            quad(glow, m, a.x + n.x * w * 0.2F, cy + a.y + n.y * w * 0.2F, a.z + n.z * w * 0.2F, c1,
                    a.x - n.x * w * 0.2F, cy + a.y - n.y * w * 0.2F, a.z - n.z * w * 0.2F, c1,
                    b.x - n.x * w, cy + b.y - n.y * w, b.z - n.z * w, c0,
                    b.x + n.x * w, cy + b.y + n.y * w, b.z + n.z * w, c0);
        }
    }

    /** A fireball drops out of the sky onto the target and blows up into embers. */
    private static void meteor(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec,
                               Vector3f right, Vector3f up) {
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        float fallTime = 0.12F;
        if (sec < fallTime) {
            // the falling ball with its tail
            float k = sec / fallTime;
            float h = 9 * (1 - k);
            disc(glow, m, 0, h + 0.3F, 0, right, up, 0.7F, argb(1, 0xFFE0A0), argb(0, 0xFF6A1A));
            tube(glow, m, 0.25F, h + 0.3F, h + 5F, argb(0.8F, 0xFF8A2A), argb(0, 0xFF3A0A));
            return;
        }
        float bt = (sec - fallTime) / 1.3F;
        // the blast: a hot ball that swells and cools, a ring on the ground
        float swell = easeOut(clamp(bt / 0.35F));
        float cool = 1 - clamp(bt / 0.6F);
        if (cool > 0) {
            disc(glow, m, 0, 0.8F, 0, right, up, (0.6F + 2.4F * swell) * fx.power,
                    argb(cool, blend(0xFFFFFF, 0xFF8A2A, 1 - cool)), argb(0, 0xFF3A0A));
        }
        float ring = (0.4F + 4.5F * easeOut(clamp(bt / 0.8F))) * fx.power;
        float rf = 1 - clamp(bt / 0.8F);
        annulus(glow, m, 0.04F, ring * 0.8F, ring, argb(0, 0xFF6A1A), argb(rf, 0xFF8A2A));
        annulus(glow, m, 0.04F, ring, ring * 1.08F, argb(rf, 0xFF8A2A), argb(0, 0xFF6A1A));
        // embers flying out and falling
        float es = sec - fallTime;
        for (float[] b : fx.bits) {
            float px = b[0] * b[3] * es * fx.power;
            float pz = b[2] * b[3] * es * fx.power;
            float py = 0.5F + b[1] * b[3] * es * fx.power - 6 * es * es;
            if (py < 0) {
                continue;
            }
            float life = 1 - clamp(es / 1.2F);
            disc(glow, m, px, py, pz, right, up, b[4], argb(life, 0xFFB050), argb(0, 0xFF5A10));
        }
    }

    /** The air around the target breaks like glass: a crack star, then shards fly and fall. */
    private static void shatter(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec, float t,
                                Vector3f right, Vector3f up) {
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        float cy = fx.height * 0.55F;
        float crack = 1 - clamp(t / 0.3F);
        if (crack > 0) {
            for (int i = 0; i < 9; i++) {
                double a = i * Math.PI * 2 / 9 + 0.3;
                Vector3f d = new Vector3f(right).mul((float) Math.cos(a)).add(new Vector3f(up).mul((float) Math.sin(a)));
                Vector3f n = new Vector3f(right).mul((float) -Math.sin(a)).add(new Vector3f(up).mul((float) Math.cos(a)));
                spike(glow, m, 0, cy, 0, d, n, (1.0F + 0.8F * (i % 3) / 2F) * fx.power, 0.025F, argb(crack, 0xE8F8FF));
            }
        }
        for (float[] b : fx.bits) {
            float speed = b[3] * 0.6F * fx.power;
            float px = b[0] * speed * sec;
            float pz = b[2] * speed * sec;
            float py = cy + b[1] * speed * sec - 7 * sec * sec;
            if (py < 0.02F) {
                py = 0.02F;
            }
            float life = 1 - clamp((sec - 0.6F) / 0.9F);
            if (life <= 0) {
                continue;
            }
            float s = b[4] * 1.6F;
            double spin = Math.toRadians(b[5] * sec) + b[6];
            // a flat triangle that tumbles: two corners turn around the third
            Vector3f d1 = new Vector3f(right).mul((float) Math.cos(spin) * s).add(new Vector3f(up).mul((float) Math.sin(spin) * s));
            Vector3f d2 = new Vector3f(right).mul((float) Math.cos(spin + 2.2) * s * 0.7F)
                    .add(new Vector3f(up).mul((float) Math.sin(spin + 2.2) * s * 0.7F));
            int edge = argb(life * 0.9F, 0xF0FAFF);
            int fill = argb(life * 0.35F, 0x9ADCFF);
            quad(glow, m, px, py, pz, edge, px + d1.x, py + d1.y, pz + d1.z, fill,
                    px + d2.x, py + d2.y, pz + d2.z, fill, px + d2.x, py + d2.y, pz + d2.z, fill);
        }
    }

    /** Rings pull in to the target, then everything is pushed out in one hard pulse. */
    private static void gravity(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float t) {
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        int col = fx.color;
        float pull = 0.3F;
        if (t < pull) {
            float k = t / pull;
            for (int i = 0; i < 3; i++) {
                float kk = clamp(k * 1.3F - i * 0.15F);
                float r = (4.2F - 3.9F * easeIn(kk)) * fx.power;
                float a = (float) Math.sin(Math.PI * kk) * 0.8F;
                annulus(glow, m, 0.05F + i * 0.02F, Math.max(0, r - 0.12F), r, argb(0, col), argb(a, lighten(col, 0.4F)));
                annulus(glow, m, 0.05F + i * 0.02F, r, r + 0.12F, argb(a, lighten(col, 0.4F)), argb(0, col));
            }
            // a dark-to-bright core gathering in the middle
            tube(glow, m, 0.15F + 0.2F * k, 0, fx.height * k, argb(0.6F * k, col), argb(0, col));
            return;
        }
        float k = (t - pull) / (1 - pull);
        float r = (0.3F + 6F * easeOut(k)) * fx.power;
        int bright = argb((float) Math.pow(1 - k, 1.2), lighten(col, 0.35F));
        annulus(glow, m, 0.05F, Math.max(0, r - 0.5F * fx.power), r, argb(0, col), bright);
        annulus(glow, m, 0.05F, r, r + 0.25F, bright, argb(0, col));
        wall(glow, m, r, 0.05F, 1.6F * fx.power * (1 - k), bright, argb(0, col));
        float flash = 1 - clamp(k / 0.25F);
        tube(glow, m, 0.4F * fx.power, 0, 3.5F * fx.power, argb(flash, lighten(col, 0.6F)), argb(0, col));
    }

    /** A big number pops over the target: the smash damage, coloured by how far you fell. */
    private static void damage(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float t, Quaternionf look) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        float ms = t * fx.life();
        float pop = ms < 160 ? 1.8F - 0.8F * easeOut(ms / 160F) : 1;
        float fade = t > 0.7F ? 1 - (t - 0.7F) / 0.3F : 1;
        matrices.push();
        matrices.translate(0, fx.height + 0.6F + 0.8F * easeOut(t), 0);
        matrices.multiply(look);
        float s = 0.05F * pop * (0.85F + 0.25F * fx.power);
        matrices.scale(s, -s, s);
        int rgb = fx.damage < 12 ? 0xFFE45A : fx.damage < 22 ? 0xFF9A2A : 0xFF3A3A;
        String value = fx.damage == Math.floor(fx.damage) ? String.valueOf((int) fx.damage)
                : String.format(java.util.Locale.ROOT, "%.1f", fx.damage);
        label(font, matrices, consumers, Text.literal("+" + value).formatted(Formatting.BOLD), 0, rgb, fade);
        matrices.scale(0.5F, 0.5F, 0.5F);
        label(font, matrices, consumers, Text.literal("SMASH").formatted(Formatting.BOLD), 20, 0xFFFFFF, fade);
        matrices.pop();
    }

    private static void label(TextRenderer font, MatrixStack matrices, VertexConsumerProvider consumers,
                              Text text, float y, int rgb, float fade) {
        int alpha = Math.max(5, Math.round(fade * 255));
        float x = -font.getWidth(text) / 2F;
        font.draw(text, x + 1, y + 1, (Math.max(5, alpha / 2) << 24) | ((rgb >> 2) & 0x3F3F3F), false,
                matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
        matrices.push();
        matrices.translate(0, 0, -0.02F);
        font.draw(text, x, y, (alpha << 24) | rgb, false, matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
        matrices.pop();
    }

    // ------------------------------------------------------------------ drawing helpers

    /** a thin flat band on the ground from (x0, z0) to (x1, z1) */
    private static void ribbonFlat(VertexConsumer b, Matrix4f m, float x0, float z0, float x1, float z1, float w, float y, int c) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4F) {
            return;
        }
        float nx = -dz / len * w, nz = dx / len * w;
        quad(b, m, x0 + nx, y, z0 + nz, c, x0 - nx, y, z0 - nz, c, x1 - nx, y, z1 - nz, c, x1 + nx, y, z1 + nz, c);
    }

    /** a pointed streak from the centre outwards along d (n = sideways), bright at the root */
    private static void spike(VertexConsumer b, Matrix4f m, float x, float y, float z, Vector3f d, Vector3f n,
                              float len, float w, int c) {
        int none = c & 0xFFFFFF;
        quad(b, m, x + n.x * w, y + n.y * w, z + n.z * w, c, x - n.x * w, y - n.y * w, z - n.z * w, c,
                x + d.x * len, y + d.y * len, z + d.z * len, none, x + d.x * len, y + d.y * len, z + d.z * len, none);
        quad(b, m, x + n.x * w, y + n.y * w, z + n.z * w, c, x - n.x * w, y - n.y * w, z - n.z * w, c,
                x - d.x * len * 0.15F, y - d.y * len * 0.15F, z - d.z * len * 0.15F, none,
                x - d.x * len * 0.15F, y - d.y * len * 0.15F, z - d.z * len * 0.15F, none);
    }

    /** a round glow facing the camera: centre colour in the middle, edge colour at radius r */
    private static void disc(VertexConsumer b, Matrix4f m, float x, float y, float z, Vector3f right, Vector3f up,
                             float r, int center, int edge) {
        int seg = 20;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0) * r, s0 = (float) Math.sin(a0) * r;
            float c1 = (float) Math.cos(a1) * r, s1 = (float) Math.sin(a1) * r;
            quad(b, m, x, y, z, center, x, y, z, center,
                    x + right.x * c1 + up.x * s1, y + right.y * c1 + up.y * s1, z + right.z * c1 + up.z * s1, edge,
                    x + right.x * c0 + up.x * s0, y + right.y * c0 + up.y * s0, z + right.z * c0 + up.z * s0, edge);
        }
    }

    /** flat ring at height y from r1 to r2, colour c1 at the inner edge and c2 at the outer edge */
    private static void annulus(VertexConsumer b, Matrix4f m, float y, float r1, float r2, int c1, int c2) {
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float cc = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(b, m, c0 * r1, y, s0 * r1, c1, cc * r1, y, s1 * r1, c1, cc * r2, y, s1 * r2, c2, c0 * r2, y, s0 * r2, c2);
        }
    }

    /** standing ring wall of radius r from y1 (colour c1) to y2 (colour c2) */
    private static void wall(VertexConsumer b, Matrix4f m, float r, float y1, float y2, int c1, int c2) {
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
            float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            quad(b, m, x0, y1, z0, c1, x1, y1, z1, c1, x1, y2, z1, c2, x0, y2, z0, c2);
        }
    }

    /** upright tube of radius r from y0 (colour bottom) to y1 (colour top) */
    private static void tube(VertexConsumer b, Matrix4f m, float r, float y0, float y1, int bottom, int top) {
        int seg = 12;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
            float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            quad(b, m, x0, y0, z0, bottom, x1, y0, z1, bottom, x1, y1, z1, top, x0, y1, z0, top);
        }
    }

    /** a box around the current origin with half sizes hx, hy, hz */
    private static void box(VertexConsumer b, Matrix4f m, float hx, float hy, float hz, int c) {
        int side = shade(c, 0.8F);
        int bottom = shade(c, 0.55F);
        quad(b, m, -hx, -hy, -hz, side, hx, -hy, -hz, side, hx, hy, -hz, side, -hx, hy, -hz, side);
        quad(b, m, -hx, -hy, hz, side, hx, -hy, hz, side, hx, hy, hz, side, -hx, hy, hz, side);
        quad(b, m, -hx, -hy, -hz, side, -hx, -hy, hz, side, -hx, hy, hz, side, -hx, hy, -hz, side);
        quad(b, m, hx, -hy, -hz, side, hx, -hy, hz, side, hx, hy, hz, side, hx, hy, -hz, side);
        quad(b, m, -hx, hy, -hz, c, hx, hy, -hz, c, hx, hy, hz, c, -hx, hy, hz, c);
        quad(b, m, -hx, -hy, -hz, bottom, hx, -hy, -hz, bottom, hx, -hy, hz, bottom, -hx, -hy, hz, bottom);
    }

    private static int shade(int argb, float k) {
        int r = Math.round(((argb >> 16) & 255) * k), g = Math.round(((argb >> 8) & 255) * k), b = Math.round((argb & 255) * k);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** one quad, drawn from both sides so it never disappears when seen from behind */
    private static void quad(VertexConsumer b, Matrix4f m,
                             float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2,
                             float x3, float y3, float z3, int c3, float x4, float y4, float z4, int c4) {
        b.vertex(m, x1, y1, z1).color(c1);
        b.vertex(m, x2, y2, z2).color(c2);
        b.vertex(m, x3, y3, z3).color(c3);
        b.vertex(m, x4, y4, z4).color(c4);
        b.vertex(m, x4, y4, z4).color(c4);
        b.vertex(m, x3, y3, z3).color(c3);
        b.vertex(m, x2, y2, z2).color(c2);
        b.vertex(m, x1, y1, z1).color(c1);
    }

    /** when a piece thrown from height y0 with upward speed v lands on y = 0 */
    private static float landTime(float y0, float v, float g) {
        return (float) ((v + Math.sqrt(v * v + 2 * g * y0)) / g);
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int lighten(int rgb, float k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r += Math.round((255 - r) * k);
        g += Math.round((255 - g) * k);
        b += Math.round((255 - b) * k);
        return (r << 16) | (g << 8) | b;
    }

    private static int blend(int a, int b, float k) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    private static float easeOut(float t) {
        float k = 1 - clamp(t);
        return 1 - k * k * k;
    }

    private static float easeIn(float t) {
        float k = clamp(t);
        return k * k;
    }

    private static float clamp(float t) {
        return Math.max(0, Math.min(1, t));
    }
}

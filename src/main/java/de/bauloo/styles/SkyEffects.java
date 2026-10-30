package de.bauloo.styles;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sky effects: shooting stars, meteor showers, an aurora, the milky way, twinkling stars, a comet,
 * constellations and a blood moon - drawn by this mod on top of the night sky, only on your screen.
 * Everything sits on a big sphere around the camera (closer than the far terrain fog), so hills and
 * trees still cover it. Glowing parts add their light; nothing shows in daylight or heavy rain.
 */
public final class SkyEffects {

    /** { key, label } in menu order */
    public static final String[][] EFFECTS = {
            {"shooting", "Shooting Stars"}, {"shower", "Meteor Shower"}, {"aurora", "Aurora"}, {"milky", "Milky Way"},
            {"twinkle", "Twinkle"}, {"comet", "Comet"}, {"constellations", "Constellations"}, {"blood", "Blood Moon"}};

    private SkyEffects() {
    }

    public static boolean on(String key) {
        return switch (key) {
            case "shooting" -> StylesUiConfig.skyShooting;
            case "shower" -> StylesUiConfig.skyShower;
            case "aurora" -> StylesUiConfig.skyAurora;
            case "milky" -> StylesUiConfig.skyMilky;
            case "twinkle" -> StylesUiConfig.skyTwinkle;
            case "comet" -> StylesUiConfig.skyComet;
            case "constellations" -> StylesUiConfig.skyConstellations;
            case "blood" -> StylesUiConfig.skyBlood;
            default -> false;
        };
    }

    public static void toggle(String key) {
        switch (key) {
            case "shooting" -> StylesUiConfig.skyShooting = !StylesUiConfig.skyShooting;
            case "shower" -> StylesUiConfig.skyShower = !StylesUiConfig.skyShower;
            case "aurora" -> StylesUiConfig.skyAurora = !StylesUiConfig.skyAurora;
            case "milky" -> StylesUiConfig.skyMilky = !StylesUiConfig.skyMilky;
            case "twinkle" -> StylesUiConfig.skyTwinkle = !StylesUiConfig.skyTwinkle;
            case "comet" -> StylesUiConfig.skyComet = !StylesUiConfig.skyComet;
            case "constellations" -> StylesUiConfig.skyConstellations = !StylesUiConfig.skyConstellations;
            case "blood" -> StylesUiConfig.skyBlood = !StylesUiConfig.skyBlood;
            default -> {
                return;
            }
        }
        StylesUiConfig.save();
    }

    /** how many effects are switched on, for the tile */
    public static int count() {
        int n = 0;
        for (String[] e : EFFECTS) {
            if (on(e[0])) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ preview

    private static long previewUntil;
    private static int timeBefore = Integer.MIN_VALUE;

    /** PREVIEW button: night for a few seconds, with a shower and some shooting stars right away */
    public static void preview() {
        previewUntil = System.currentTimeMillis() + 12000;
        if (timeBefore == Integer.MIN_VALUE) {
            timeBefore = StylesUiConfig.timeOverride;
        }
        StylesUiConfig.timeOverride = 18000;
        for (int i = 0; i < 3; i++) {
            spawnShooting(0.25F * i);
        }
        showerStart = System.currentTimeMillis();
        // the aurora flares up right away
        stormStart = System.currentTimeMillis() - 2500;
        nextStorm = System.currentTimeMillis() + 60000;
    }

    public static long previewLength() {
        return 12500;
    }

    // ------------------------------------------------------------------ moving things

    /** one streak over the sky: start direction, motion in degrees per second, life in seconds */
    private record Streak(long born, float az, float el, float daz, float del, float life, float width, int head, int tail) {
    }

    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static long nextShooting;
    private static long nextShowerMeteor;
    /** when the current meteor shower began (they come every few minutes and last half a minute) */
    private static long showerStart;
    private static final long SHOWER_EVERY = 180_000;
    private static final long SHOWER_LENGTH = 30_000;
    private static void spawnShooting(float delay) {
        float az = RANDOM.nextFloat() * 360;
        float el = 25 + RANDOM.nextFloat() * 45;
        double dir = RANDOM.nextDouble() * Math.PI * 2;
        float speed = 28 + RANDOM.nextFloat() * 22;
        STREAKS.add(new Streak(System.currentTimeMillis() + (long) (delay * 1000), az, el,
                (float) Math.cos(dir) * speed, -Math.abs((float) Math.sin(dir)) * speed * 0.8F - 6,
                0.7F + RANDOM.nextFloat() * 0.5F, 1.0F, 0xFFFFFF, 0xBFE0FF));
    }

    /** the shower comes from one point in the sky; every meteor flies away from it */
    private static final float RADIANT_AZ = 65;
    private static final float RADIANT_EL = 58;

    private static void spawnShowerMeteor() {
        double ang = RANDOM.nextDouble() * Math.PI * 2;
        float off = 6 + RANDOM.nextFloat() * 22;
        float daz = (float) Math.cos(ang);
        float del = (float) Math.sin(ang);
        float speed = 35 + RANDOM.nextFloat() * 30;
        boolean warm = RANDOM.nextFloat() < 0.6F;
        STREAKS.add(new Streak(System.currentTimeMillis(), RADIANT_AZ + daz * off, RADIANT_EL + del * off,
                daz * speed, del * speed, 0.5F + RANDOM.nextFloat() * 0.5F, 0.8F,
                0xFFFFFF, warm ? 0xFFC27A : 0x9ADCFF));
    }

    private static void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (timeBefore != Integer.MIN_VALUE && now > previewUntil) {
            if (StylesUiConfig.timeOverride == 18000) {
                StylesUiConfig.timeOverride = timeBefore;
            }
            timeBefore = Integer.MIN_VALUE;
        }
        STREAKS.removeIf(s -> now - s.born() > (s.life() + 0.5F) * 1000);
        if (mc.world == null || night(mc.world, 1) < 0.25F) {
            return;
        }
        boolean preview = now < previewUntil;
        if ((StylesUiConfig.skyShooting || preview) && now > nextShooting) {
            if (nextShooting != 0) {
                spawnShooting(0);
            }
            nextShooting = now + (preview ? 900 : 6000 + RANDOM.nextInt(12000));
        }
        if (StylesUiConfig.skyShower || preview) {
            if (showerStart == 0 || now - showerStart > SHOWER_EVERY) {
                showerStart = now;
            }
            if (now - showerStart < SHOWER_LENGTH && now > nextShowerMeteor && STREAKS.size() < 40) {
                spawnShowerMeteor();
                nextShowerMeteor = now + 200 + RANDOM.nextInt(500);
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SkyEffects::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            try {
                render(context.matrices(), context.consumers());
            } catch (Throwable e) {
                // never break world rendering over the sky
                BaulooStylesClient.LOGGER.warn("Sky effect could not be drawn: {}", e.toString());
            }
        });
    }

    /** the sky angle like vanilla: 0 at noon, 0.5 at midnight */
    private static float skyAngle(ClientWorld world) {
        double d = ((world.getTimeOfDay() % 24000L) / 24000.0 - 0.25) % 1.0;
        if (d < 0) {
            d += 1;
        }
        double e = 0.5 - Math.cos(d * Math.PI) / 2;
        return (float) ((d * 2 + e) / 3);
    }

    /** 0 by day, 1 in the dark night, less in rain */
    private static float night(ClientWorld world, float tick) {
        float sun = (float) Math.cos(skyAngle(world) * Math.PI * 2);
        float n = Math.max(0, Math.min(1, (0.12F - sun) / 0.38F));
        return n * (1 - world.getRainGradient(tick) * 0.9F);
    }

    private static void render(MatrixStack matrices, VertexConsumerProvider consumers) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null || matrices == null || consumers == null
                || world.getDimension().skybox() != net.minecraft.world.dimension.DimensionType.Skybox.OVERWORLD) {
            return;
        }
        boolean preview = System.currentTimeMillis() < previewUntil;
        if (count() == 0 && !preview) {
            return;
        }
        float tick = mc.getRenderTickCounter().getTickProgress(false);
        float night = night(world, tick);
        if (night <= 0.01F) {
            return;
        }
        float r = Math.max(60, Math.min(180, mc.options.getClampedViewDistance() * 16 * 0.75F));
        float time = (System.currentTimeMillis() % 3_600_000L) / 1000F;
        float angle = skyAngle(world) * 360;
        if (StylesUiConfig.skyBlood) {
            bloodMoon(matrices, consumers, r, angle, night);
        }
        // the star sky turns like the vanilla stars
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
        Matrix4f stars = new Matrix4f(matrices.peek().getPositionMatrix());
        matrices.pop();
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        if (StylesUiConfig.skyMilky) {
            milkyWay(glow, stars, r, night);
        }
        if (StylesUiConfig.skyTwinkle) {
            twinkle(glow, stars, r, night, time);
        }
        if (StylesUiConfig.skyConstellations) {
            constellations(glow, stars, r, night);
        }
        if (StylesUiConfig.skyComet) {
            comet(glow, m, r, night, time);
        }
        if (StylesUiConfig.skyAurora || preview) {
            aurora(glow, m, r, night, time);
        }
        streaks(glow, m, r, night);
    }

    /** direction for an azimuth (0 = north, 90 = east) and elevation in degrees */
    private static Vector3f dir(float az, float el) {
        double a = Math.toRadians(az);
        double e = Math.toRadians(el);
        return new Vector3f((float) (Math.sin(a) * Math.cos(e)), (float) Math.sin(e), (float) (-Math.cos(a) * Math.cos(e)));
    }

    // ---- shooting stars and meteors

    private static void streaks(VertexConsumer b, Matrix4f m, float r, float night) {
        long now = System.currentTimeMillis();
        for (Streak s : STREAKS) {
            float t = (now - s.born()) / 1000F;
            if (t < 0 || t > s.life()) {
                continue;
            }
            float fade = (float) Math.sin(Math.PI * t / s.life()) * night;
            int seg = 14;
            Vector3f prev = null;
            for (int k = 0; k <= seg; k++) {
                float tt = Math.max(0, t - k * 0.022F);
                Vector3f p = dir(s.az() + s.daz() * tt, s.el() + s.del() * tt).mul(r);
                if (prev != null) {
                    float f0 = 1 - (k - 1) / (float) seg;
                    float f1 = 1 - k / (float) seg;
                    int c0 = argb(fade * f0, k == 1 ? s.head() : s.tail());
                    int c1 = argb(fade * f1 * f1, s.tail());
                    ribbon(b, m, prev, p, r * 0.0042F * s.width() * f0, r * 0.0042F * s.width() * f1, c0, c1);
                }
                prev = p;
            }
            // a small glow at the head
            Vector3f head = dir(s.az() + s.daz() * t, s.el() + s.del() * t).mul(r);
            billboard(b, m, head, r * 0.012F, argb(0.6F * fade, s.head()), argb(0, s.tail()));
        }
    }

    // ---- aurora: curtains of light with rays, waving slowly

    /** a substorm: now and then the aurora flares up for a few seconds and its rays dance faster */
    private static long stormStart;
    private static long nextStorm;
    private static long lastAuroraMs;
    /** where the rays are: runs faster during a substorm */
    private static float rayPhase;

    private static float substorm() {
        long now = System.currentTimeMillis();
        if (nextStorm == 0) {
            nextStorm = now + 15000;
        }
        if (now > nextStorm) {
            stormStart = now;
            nextStorm = now + 45000 + RANDOM.nextInt(45000);
        }
        float t = (now - stormStart) / 10000F;
        return t < 0 || t > 1 ? 0 : (float) Math.sin(Math.PI * t);
    }

    private static void aurora(VertexConsumer b, Matrix4f m, float r, float night, float time) {
        float storm = substorm();
        long now = System.currentTimeMillis();
        float dt = lastAuroraMs == 0 ? 0 : Math.min(0.2F, (now - lastAuroraMs) / 1000F);
        lastAuroraMs = now;
        rayPhase += dt * (1 + 3 * storm);
        corona(b, m, r, night, time, storm);
        float[][] curtains = {
                // az from, az to, base elevation, height, phase, strength, colour set - all the way round the sky
                {-90, 20, 11, 34, 0.0F, 1.0F, 0}, {-5, 110, 14, 30, 1.3F, 0.95F, 1}, {85, 200, 12, 35, 2.6F, 0.95F, 0},
                {175, 290, 15, 29, 3.9F, 0.95F, 2}, {260, 375, 11, 33, 5.2F, 0.95F, 1}, {-40, 70, 26, 30, 0.7F, 0.5F, 2}};
        // up the curtain: a thin pink fringe, a white-hot edge, bright green, softer green, teal, purple, magenta at the top
        float[] at = {0, 0.02F, 0.06F, 0.14F, 0.3F, 0.5F, 0.75F, 1};
        // about half as bright as before: the curtains overlap and add up
        float[] alpha = {0, 0.3F, 0.55F, 0.5F, 0.34F, 0.2F, 0.1F, 0};
        int[][] sets = {
                {0xFF4ABF, 0xFF6AD0, 0x9AFFC8, 0x4AF08A, 0x2AD08A, 0x22B090, 0xB0304A, 0xE83A5A},   // classic green, red tops
                {0xFF3A8A, 0xFF7AC8, 0xC8FFE0, 0x5AF0A8, 0x48C8B8, 0x3A8AE8, 0xC04AE8, 0xE84AC0},   // green into pink
                {0xA05AFF, 0xC88AFF, 0xB8F0FF, 0x5AD8F0, 0x30B0E8, 0x3A6AE0, 0x7A4AE8, 0xB04AE8}};  // ice blue
        for (int n = curtains.length - 1; n >= 0; n--) {
            float[] c = curtains[n];
            int[] col = sets[(int) c[6]];
            float cr = r * (0.86F + 0.02F * n);
            auroraHaze(b, m, c, cr * 1.01F, night);
            // two sheets a little apart give the curtain depth; the back one is dimmer and higher
            for (int sheet = 1; sheet >= 0; sheet--) {
                float sr = cr * (sheet == 0 ? 1 : 1.006F);
                float dEl = sheet * 1.6F;
                float dAz = sheet * 0.8F;
                float dim = sheet == 0 ? 1 : 0.35F;
                int cols = 240;
                float span = c[1] - c[0];
                Vector3f[] lo = null;
                float[] lastI = null;
                for (int i = 0; i <= cols; i++) {
                    float u = i / (float) cols;
                    float az = c[0] + span * u + dAz;
                    double ra = Math.toRadians(az);
                    // the lower edge folds in big slow S-curves
                    double f1 = ra * 3 + time * 0.08F + c[4];
                    double f2 = ra * 7 - time * 0.13F + c[4] * 2;
                    double f3 = ra * 17 + time * 0.21F + c[4];
                    float base = c[2] + dEl + 5 * (float) Math.sin(f1) + 2.5F * (float) Math.sin(f2) + 1.1F * (float) Math.sin(f3);
                    float slope = (float) Math.abs(5 * 3 * Math.cos(f1) + 2.5 * 7 * Math.cos(f2) + 1.1 * 17 * Math.cos(f3)) / 40F;
                    // where it folds towards you it glows more
                    float fold = 0.8F + 0.35F * Math.min(1, slope);
                    // fine silky rays, in soft bundles
                    float fine = 0.5F + 0.5F * (float) Math.sin(ra * 160 + rayPhase * 0.6F + 2 * Math.sin(ra * 9 + time * 0.2F));
                    float bundle = 0.5F + 0.5F * (float) Math.sin(ra * 41 - rayPhase * 0.35F + c[4]);
                    // a quick fine shimmer on top
                    float shimmer = 0.88F + 0.12F * (float) Math.sin(ra * 230 + rayPhase * 4.5F + c[4]);
                    float ray = (0.55F + 0.45F * fine * (0.4F + 0.6F * bundle)) * shimmer;
                    // bright waves running sideways along the curtain
                    double ph = (az / 360.0 * 3 - time * 0.035 + c[4]) % 1.0;
                    if (ph < 0) {
                        ph += 1;
                    }
                    float pulse = (float) Math.exp(-(ph - 0.5) * (ph - 0.5) * 70);
                    float h = c[3] * (0.75F + 0.2F * (float) Math.sin(ra * 2 + time * 0.05F + c[4]) + 0.2F * ray);
                    float edge = Math.min(1, Math.min(u, 1 - u) * 5);
                    float k = night * c[5] * dim * edge * edge * (3 - 2 * edge) * fold * (0.85F + 0.3F * pulse) * (1 + 0.65F * storm);
                    Vector3f[] pts = new Vector3f[at.length];
                    float[] in = new float[at.length];
                    for (int j = 0; j < at.length; j++) {
                        pts[j] = dir(az, base + h * at[j]).mul(sr);
                        in[j] = Math.min(1, k * alpha[j] * (j >= 3 ? ray : 0.75F + 0.25F * ray));
                    }
                    if (lo != null) {
                        for (int j = 0; j < at.length - 1; j++) {
                            quad(b, m, lo[j], argb(lastI[j], col[j]), pts[j], argb(in[j], col[j]),
                                    pts[j + 1], argb(in[j + 1], col[j + 1]), lo[j + 1], argb(lastI[j + 1], col[j + 1]));
                        }
                    }
                    lo = pts;
                    lastI = in;
                }
            }
        }
    }

    /** the corona: thin rays high above you that all run together towards the top of the sky */
    private static void corona(VertexConsumer b, Matrix4f m, float r, float night, float time, float storm) {
        float a = night * (0.35F + 0.65F * storm);
        if (a < 0.02F) {
            return;
        }
        float cr = r * 0.84F;
        int rays = 220;
        for (int i = 0; i < rays; i++) {
            float az = i * 360F / rays + (float) Math.sin(i * 7.31) * 1.5F;
            double ra = Math.toRadians(az);
            float glow = 0.5F + 0.5F * (float) Math.sin(ra * 13 + rayPhase * 0.7F + Math.sin(i * 3.7) * 3);
            glow *= 0.5F + 0.5F * (float) Math.sin(ra * 37 - rayPhase * 1.3F);
            glow *= glow;
            float lo = 46 + 6 * (float) Math.sin(ra * 3 + time * 0.1F);
            float hi = 82 + 4 * (float) Math.sin(ra * 5 - time * 0.07F);
            // soft at both ends, brightest a third of the way up
            Vector3f p0 = dir(az, lo).mul(cr);
            Vector3f p1 = dir(az, lo + (hi - lo) * 0.3F).mul(cr);
            Vector3f p2 = dir(az, hi).mul(cr);
            float k = a * glow * 0.16F;
            ribbon(b, m, p0, p1, r * 0.016F, r * 0.012F, argb(0, 0x3AE08A), argb(k, 0x4AF0A0));
            ribbon(b, m, p1, p2, r * 0.012F, r * 0.003F, argb(k, 0x4AF0A0), argb(0, 0x8A4AE8));
        }
    }

    /** a soft green glow behind a curtain and a faint purple one high above it */
    private static void auroraHaze(VertexConsumer b, Matrix4f m, float[] c, float r, float night) {
        float span = c[1] - c[0];
        Vector3f[] haze = null;
        for (int i = 0; i <= 40; i++) {
            float az = c[0] + span * i / 40F;
            Vector3f[] hp = {dir(az, c[2] - 7).mul(r), dir(az, c[2] + c[3] * 0.5F).mul(r), dir(az, c[2] + c[3] * 1.6F).mul(r)};
            float e = (float) Math.sin(Math.PI * i / 40F);
            float g = 0.08F * night * c[5] * e;
            if (haze != null) {
                quad(b, m, haze[0], argb(0, 0x3AE08A), hp[0], argb(0, 0x3AE08A), hp[1], argb(g, 0x3AE08A), haze[1], argb(g, 0x3AE08A));
                quad(b, m, haze[1], argb(g * 0.6F, 0x7A4AD8), hp[1], argb(g * 0.6F, 0x7A4AD8), hp[2], argb(0, 0xD83A9A), haze[2], argb(0, 0xD83A9A));
            }
            haze = hp;
        }
    }

    // ---- milky way

    private static float[] milky;

    private static void milkyWay(VertexConsumer b, Matrix4f m, float r, float night) {
        if (milky == null) {
            Random rnd = new Random(42);
            int n = 2600;
            milky = new float[n * 5];
            for (int i = 0; i < n; i++) {
                // around a great circle, thicker in the middle
                double a = rnd.nextDouble() * Math.PI * 2;
                double off = (rnd.nextGaussian() * 0.11) * (0.6 + 0.4 * Math.cos(a * 2));
                Vector3f p = new Vector3f((float) Math.cos(a), (float) off, (float) Math.sin(a)).normalize();
                p.rotateX((float) Math.toRadians(62)).rotateZ((float) Math.toRadians(28));
                milky[i * 5] = p.x;
                milky[i * 5 + 1] = p.y;
                milky[i * 5 + 2] = p.z;
                milky[i * 5 + 3] = (float) (0.25 + 0.75 * Math.pow(rnd.nextDouble(), 3));
                milky[i * 5 + 4] = rnd.nextFloat();
            }
        }
        for (int i = 0; i < milky.length; i += 5) {
            Vector3f p = new Vector3f(milky[i], milky[i + 1], milky[i + 2]).mul(r);
            float bright = milky[i + 3];
            int c = milky[i + 4] < 0.2F ? 0xFFD6F0 : milky[i + 4] < 0.45F ? 0xCFE0FF : 0xFFFFFF;
            billboard(b, m, p, r * (0.0016F + 0.0022F * bright), argb(night * (0.35F + 0.6F * bright), c), argb(0, c));
        }
        // a soft glow along the band
        for (int i = 0; i < 64; i++) {
            double a = i / 64.0 * Math.PI * 2;
            Vector3f p = new Vector3f((float) Math.cos(a), 0, (float) Math.sin(a));
            p.rotateX((float) Math.toRadians(62)).rotateZ((float) Math.toRadians(28)).mul(r * 1.01F);
            int c = i % 3 == 0 ? 0x7A5AD8 : 0x5A7AD8;
            billboard(b, m, p, r * 0.09F, argb(night * 0.06F, c), argb(0, c));
        }
    }

    // ---- twinkling stars

    private static float[] twinkles;

    private static void twinkle(VertexConsumer b, Matrix4f m, float r, float night, float time) {
        if (twinkles == null) {
            Random rnd = new Random(7);
            twinkles = new float[110 * 6];
            for (int i = 0; i < 110; i++) {
                Vector3f p = new Vector3f((float) rnd.nextGaussian(), (float) rnd.nextGaussian(), (float) rnd.nextGaussian()).normalize();
                twinkles[i * 6] = p.x;
                twinkles[i * 6 + 1] = p.y;
                twinkles[i * 6 + 2] = p.z;
                twinkles[i * 6 + 3] = 1 + rnd.nextFloat() * 3;
                twinkles[i * 6 + 4] = rnd.nextFloat() * 6.28F;
                twinkles[i * 6 + 5] = rnd.nextFloat();
            }
        }
        int own = Cosmetics.color();
        for (int i = 0; i < twinkles.length; i += 6) {
            Vector3f p = new Vector3f(twinkles[i], twinkles[i + 1], twinkles[i + 2]).mul(r);
            float s = 0.5F + 0.5F * (float) Math.sin(time * twinkles[i + 3] + twinkles[i + 4]);
            s = s * s;
            int c = twinkles[i + 5] < 0.3F ? own : 0xFFFFFF;
            float a = night * (0.35F + 0.65F * s);
            billboard(b, m, p, r * 0.0035F, argb(a, 0xFFFFFF), argb(a * 0.6F, c));
            if (twinkles[i + 5] < 0.55F) {
                sparkle(b, m, p, r * (0.006F + 0.01F * s), r * 0.0009F, argb(a * 0.9F, c));
            }
        }
    }

    // ---- comet

    /** where the comet is on its way over the sky: 0 rising in the east, 1 setting in the west */
    private static Vector3f cometPath(float u) {
        return dir(75 + 170 * u, -4 + 76 * (float) Math.sin(Math.PI * u));
    }

    /**
     * A big comet like in "Your Name": it crosses the whole sky in a minute and a half, its tail reaches
     * far behind it, and halfway a second piece breaks off with its own pink tail.
     */
    private static void comet(VertexConsumer b, Matrix4f m, float r, float night, float time) {
        // it stands still high in the sky, already broken in two
        float u = 0.55F;
        float show = night;
        cometTail(b, m, r, show, u, 0, 60, 1, time);
        // the piece that breaks off and falls away on its own, thin and pink-white
        if (u > 0.45F) {
            float split = (u - 0.45F) / 0.55F;
            cometTail(b, m, r, show * Math.min(1, split * 4), u - 0.003F, -0.28F * split, 34, 0.35F, time);
        }
    }

    /** sideways from the comet's way at u (to the left of where it flies) */
    private static Vector3f cometSide(float u) {
        Vector3f a = cometPath(u);
        Vector3f along = cometPath(u + 0.01F).sub(a);
        return new Vector3f(a).cross(along).normalize();
    }

    /** the fine streaks a comet tail is made of: { place across the tail (-1..1), brightness, thickness } */
    private static float[][] filaments;
    /** glittering dust around the tail: { along (0-1), across, speed, phase } */
    private static float[][] dust;

    /**
     * One comet: a bright head and a wide tail of many fine streaks, white and light blue on one side,
     * deep blue at that edge and pink to purple on the other, with glitter around it.
     * off pushes it sideways off the way (radians), size makes it smaller.
     */
    private static void cometTail(VertexConsumer b, Matrix4f m, float r, float a, float u, float off, int steps, float size,
                                  float time) {
        if (filaments == null) {
            Random rnd = new Random(11);
            filaments = new float[30][];
            for (int i = 0; i < filaments.length; i++) {
                float across = -1 + 2 * i / (float) (filaments.length - 1) + (float) rnd.nextGaussian() * 0.03F;
                filaments[i] = new float[]{across, 0.3F + 0.7F * rnd.nextFloat(), 0.6F + rnd.nextFloat() * 1.2F};
            }
            dust = new float[140][];
            for (int i = 0; i < dust.length; i++) {
                dust[i] = new float[]{rnd.nextFloat(), (float) rnd.nextGaussian() * 0.9F, 0.4F + rnd.nextFloat(), rnd.nextFloat() * 6.28F};
            }
        }
        Vector3f[] pts = new Vector3f[steps + 1];
        Vector3f[] sides = new Vector3f[steps + 1];
        for (int k = 0; k <= steps; k++) {
            float uk = u - k * 0.0085F * Math.max(0.5F, size);
            Vector3f p = cometPath(uk);
            sides[k] = cometSide(uk);
            if (off != 0) {
                // the broken piece falls away more and more
                float f = k / (float) steps;
                p.add(new Vector3f(sides[k]).mul(off * (1 - f * 0.7F)));
            }
            pts[k] = p.normalize().mul(r);
        }
        float spread = r * 0.22F * size;
        // a soft haze under the streaks: blue on one side, pink on the other
        for (int k = steps; k >= 2; k -= 2) {
            float f = k / (float) steps;
            float w = spread * (float) Math.pow(f, 0.85);
            float fade = (1 - f);
            Vector3f blue = new Vector3f(pts[k]).add(new Vector3f(sides[k]).mul(w * 0.45F));
            Vector3f pink = new Vector3f(pts[k]).sub(new Vector3f(sides[k]).mul(w * 0.55F));
            glowDisc(b, m, blue.mul(1.01F), w * 0.8F + r * 0.01F, argb(a * 0.1F * fade, 0x3A6AFF), argb(0, 0x2A3AD8));
            glowDisc(b, m, pink.mul(1.01F), w * 0.65F + r * 0.01F, argb(a * 0.09F * fade, 0xD04AD8), argb(0, 0x8A2AD8));
        }
        // the streaks, each a hair closer than the last so overlapping ones never flicker
        for (int fi = 0; fi < filaments.length; fi++) {
            float[] fl = filaments[fi];
            float depth = 1 - fi * 0.0009F;
            float across = fl[0];
            int col = across > 0.55F ? 0x4A7AFF : across > 0.1F ? 0x7AD8FF : across > -0.35F ? 0xF0FCFF : across > -0.7F ? 0xFF7AD8 : 0xB05AFF;
            float bright = fl[1] * (across > -0.35F && across < 0.1F ? 1.3F : 1);
            for (int k = 0; k < steps; k++) {
                float f0 = k / (float) steps;
                float f1 = (k + 1) / (float) steps;
                Vector3f p0 = new Vector3f(pts[k]).add(new Vector3f(sides[k]).mul(spread * (float) Math.pow(f0, 0.85) * across));
                Vector3f p1 = new Vector3f(pts[k + 1]).add(new Vector3f(sides[k + 1]).mul(spread * (float) Math.pow(f1, 0.85) * across));
                float a0 = a * bright * (float) Math.pow(1 - f0, 1.3) * 0.75F;
                float a1 = a * bright * (float) Math.pow(1 - f1, 1.3) * 0.75F;
                float w0 = r * size * 0.0018F * fl[2] * (1 + f0 * 2.5F);
                float w1 = r * size * 0.0018F * fl[2] * (1 + f1 * 2.5F);
                ribbon(b, m, p0.mul(depth), p1.mul(depth), w0, w1, argb(a0, col), argb(a1, col));
            }
        }
        // glitter around the tail, twinkling and drifting back
        int count = size < 1 ? dust.length / 3 : dust.length;
        for (int i = 0; i < count; i++) {
            float[] d = dust[i];
            float f = (d[0] + time * 0.015F * d[2]) % 1;
            int k = Math.min(steps, Math.round(f * steps));
            float w = spread * (float) Math.pow(f, 0.85);
            Vector3f p = new Vector3f(pts[k]).add(new Vector3f(sides[k]).mul(w * d[1]));
            float tw = 0.5F + 0.5F * (float) Math.sin(time * 3 * d[2] + d[3]);
            int c = d[1] > 0.3F ? 0xBFE6FF : d[1] < -0.3F ? 0xFFC8F0 : 0xFFFFFF;
            billboard(b, m, p, r * size * 0.0026F, argb(a * tw * (1 - f), 0xFFFFFF), argb(0, c));
        }
        if (size >= 1) {
            ionTail(b, m, r, a, u, pts[0], time);
        }
        // the head: a green-cyan cloud around it, a white glow and a bright core whose star glints pulse
        float beat = 0.85F + 0.15F * (float) Math.sin(time * 2.2F);
        glowDisc(b, m, pts[0], r * size * 0.15F, argb(0.16F * a, 0x5AFFD0), argb(0, 0x2AA0C8));
        glowDisc(b, m, pts[0], r * size * 0.07F, argb(0.35F * a, 0x8AF0FF), argb(0, 0x3A6AFF));
        glowDisc(b, m, pts[0], r * size * 0.026F, argb(0.85F * a, 0xEAF8FF), argb(0, 0x9ADCFF));
        glowDisc(b, m, pts[0], r * size * 0.009F, argb(a, 0xFFFFFF), argb(0.8F * a, 0xFFFFFF));
        sparkle(b, m, pts[0], r * size * 0.11F * beat, r * size * 0.0019F, argb(0.95F * a, 0xFFFFFF));
        // a second, smaller glint turned half way, so the core looks like a star
        Vector3f view = new Vector3f(pts[0]).normalize();
        Vector3f right = new Vector3f(view).cross(0, 1, 0).normalize();
        Vector3f up = new Vector3f(right).cross(view).normalize();
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            Vector3f d = new Vector3f(right).add(new Vector3f(up).mul(sgn)).normalize().mul(r * size * 0.045F * (2 - beat));
            Vector3f q0 = new Vector3f(pts[0]).sub(d);
            Vector3f q1 = new Vector3f(pts[0]).add(d);
            ribbon(b, m, q0, pts[0], r * size * 0.0003F, r * size * 0.0012F, argb(0, 0xFFFFFF), argb(0.7F * a, 0xDFF4FF));
            ribbon(b, m, pts[0], q1, r * size * 0.0012F, r * size * 0.0003F, argb(0.7F * a, 0xDFF4FF), argb(0, 0xFFFFFF));
        }
    }

    /** the ion tail: straight, thin and electric blue, a little off the curved dust tail */
    private static void ionTail(VertexConsumer b, Matrix4f m, float r, float a, float u, Vector3f head, float time) {
        Vector3f h = new Vector3f(head).normalize();
        Vector3f back = cometPath(u - 0.02F).sub(cometPath(u)).normalize();
        Vector3f side = cometSide(u);
        // turned about 14 degrees off the way the comet came
        Vector3f dir = new Vector3f(back).mul(0.97F).add(new Vector3f(side).mul(0.24F)).normalize();
        int steps = 40;
        float len = 0.95F;
        Vector3f[] pts = new Vector3f[steps + 1];
        for (int k = 0; k <= steps; k++) {
            pts[k] = new Vector3f(h).add(new Vector3f(dir).mul(len * k / steps)).normalize().mul(r * 0.995F);
        }
        Vector3f across = new Vector3f(h).cross(dir).normalize();
        for (int k = steps; k >= 1; k -= 2) {
            float f = k / (float) steps;
            glowDisc(b, m, pts[k], r * (0.01F + 0.03F * f), argb(a * 0.09F * (1 - f), 0x3A8AFF), argb(0, 0x2A4AD8));
        }
        float[] lanes = {-1, -0.5F, 0, 0.5F, 1};
        for (int li = 0; li < lanes.length; li++) {
            float lane = lanes[li];
            float wave = (float) Math.sin(time * 1.5F + li * 1.3F) * 0.15F;
            for (int k = 0; k < steps; k++) {
                float f0 = k / (float) steps;
                float f1 = (k + 1) / (float) steps;
                Vector3f p0 = new Vector3f(pts[k]).add(new Vector3f(across).mul(r * 0.012F * f0 * (lane + wave * f0)));
                Vector3f p1 = new Vector3f(pts[k + 1]).add(new Vector3f(across).mul(r * 0.012F * f1 * (lane + wave * f1)));
                float k0 = a * (lane == 0 ? 0.9F : 0.5F) * (1 - f0);
                float k1 = a * (lane == 0 ? 0.9F : 0.5F) * (1 - f1);
                ribbon(b, m, p0, p1, r * 0.0012F * (1 + f0), r * 0.0012F * (1 + f1),
                        argb(k0, lane == 0 ? 0xCFF0FF : 0x5AB8FF), argb(k1, 0x3A7AFF));
            }
        }
    }

    // ---- constellations: a sword, an end crystal and a creeper

    private static final float[][][] PICTURES = {
            // sword: blade, guard, handle (degrees on a small patch of sky)
            {{0, 0}, {6, 6}, {12, 12}, {15, 15}, {11, 17}, {17, 11}, {17, 17}},
            // end crystal: a diamond with a centre
            {{0, 8}, {6, 14}, {0, 20}, {-6, 14}, {0, 14}},
            // creeper face: eyes and mouth
            {{0, 0}, {4, 0}, {12, 0}, {16, 0}, {6, 6}, {10, 6}, {6, 12}, {10, 12}, {2, 14}, {14, 14}}};
    private static final int[][][] LINES = {
            {{0, 1}, {1, 2}, {2, 3}, {4, 5}, {3, 6}},
            {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {0, 4}, {4, 2}},
            {{0, 1}, {2, 3}, {4, 5}, {4, 6}, {5, 7}, {6, 8}, {7, 9}}};
    private static final float[][] PLACES = {{40, 35}, {150, 55}, {250, 40}};

    private static void constellations(VertexConsumer b, Matrix4f stars, float r, float night) {
        float a = night * night;
        for (int s = 0; s < PICTURES.length; s++) {
            float[][] pts = PICTURES[s];
            Vector3f[] p = new Vector3f[pts.length];
            for (int i = 0; i < pts.length; i++) {
                p[i] = dir(PLACES[s][0] + pts[i][0], PLACES[s][1] + pts[i][1]).mul(r);
            }
            for (int[] l : LINES[s]) {
                ribbon(b, stars, p[l[0]], p[l[1]], r * 0.0012F, r * 0.0012F, argb(0.22F * a, 0x9ABEFF), argb(0.22F * a, 0x9ABEFF));
            }
            for (Vector3f q : p) {
                billboard(b, stars, q, r * 0.005F, argb(a, 0xFFFFFF), argb(0.3F * a, 0xBFD8FF));
                sparkle(b, stars, q, r * 0.01F, r * 0.0008F, argb(0.6F * a, 0xFFFFFF));
            }
        }
    }

    // ---- blood moon

    private static void bloodMoon(MatrixStack matrices, VertexConsumerProvider consumers, float r, float angle, float night) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
        float d = r * 0.97F;
        float s = r * 0.17F;
        // a round deep red moon over the normal one: lighter in the middle, darker to the rim
        VertexConsumer vc = consumers.getBuffer(RenderLayers.entityTranslucentEmissive(Cosmetics.WHITE));
        MatrixStack.Entry e = matrices.peek();
        disk(e, vc, 0, -d, 0, s, 0xFFC8452E, 0xFF7A1A14);
        // the glow: strong around the moon, fading far out
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        Vector3f centre = new Vector3f(0, -d * 1.005F, 0);
        glowDisc(glow, m, centre, s * 1.5F, argb(0.5F * night, 0xFF5A30), argb(0, 0xFF3A20));
        glowDisc(glow, m, centre, s * 3.2F, argb(0.32F * night, 0xE8301E), argb(0, 0xC8201A));
        glowDisc(glow, m, centre, s * 6.5F, argb(0.14F * night, 0xC8201A), argb(0, 0x8A1010));
        matrices.pop();
        // the night sky only a little red (terrain hides the lower half)
        m = matrices.peek().getPositionMatrix();
        float big = r * 0.99F;
        int tint = argb(0.045F * night, 0xFF2A1A);
        int[][] faces = {{-1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1}, {-1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1},
                {-1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1}, {-1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1},
                {1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1}};
        for (int[] f : faces) {
            quad(glow, m, new Vector3f(f[0], f[1], f[2]).mul(big), tint, new Vector3f(f[3], f[4], f[5]).mul(big), tint,
                    new Vector3f(f[6], f[7], f[8]).mul(big), tint, new Vector3f(f[9], f[10], f[11]).mul(big), tint);
        }
    }

    /** a flat round disk at height y (facing down/up), lit full bright: colour inside, colour at the rim */
    private static void disk(MatrixStack.Entry e, VertexConsumer vc, float cx, float y, float cz, float rad, int inner, int rim) {
        int seg = 32;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            float x0 = cx + (float) Math.cos(a0) * rad;
            float z0 = cz + (float) Math.sin(a0) * rad;
            float x1 = cx + (float) Math.cos(a1) * rad;
            float z1 = cz + (float) Math.sin(a1) * rad;
            float[][] p = {{cx, cz}, {x0, z0}, {x1, z1}, {cx, cz}};
            int[] c = {inner, rim, rim, inner};
            // one side only: two copies in the same place flicker
            for (int k = 0; k < 4; k++) {
                vc.vertex(e, p[k][0], y, p[k][1]).color(c[k]).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0).normal(e, 0, 1, 0);
            }
        }
    }

    // ------------------------------------------------------------------ glow helpers

    /** a soft square facing the camera: centre colour c, rim colour edge */
    private static void billboard(VertexConsumer b, Matrix4f m, Vector3f p, float s, int c, int edge) {
        Vector3f view = new Vector3f(p).normalize();
        Vector3f right = new Vector3f(view).cross(0, 1, 0);
        if (right.lengthSquared() < 1.0E-6F) {
            right.set(1, 0, 0);
        }
        right.normalize().mul(s);
        Vector3f up = new Vector3f(right).cross(view).normalize().mul(s);
        Vector3f a = new Vector3f(p).sub(right).sub(up);
        Vector3f bb = new Vector3f(p).add(right).sub(up);
        Vector3f cc = new Vector3f(p).add(right).add(up);
        Vector3f d = new Vector3f(p).sub(right).add(up);
        // four triangles from the centre so the middle is brightest
        quad(b, m, p, c, a, edge, bb, edge, p, c);
        quad(b, m, p, c, bb, edge, cc, edge, p, c);
        quad(b, m, p, c, cc, edge, d, edge, p, c);
        quad(b, m, p, c, d, edge, a, edge, p, c);
    }

    /** a soft round glow facing the camera: colour c in the middle, edge at the rim */
    private static void glowDisc(VertexConsumer b, Matrix4f m, Vector3f p, float rad, int c, int edge) {
        Vector3f view = new Vector3f(p).normalize();
        Vector3f right = new Vector3f(view).cross(0, 1, 0);
        if (right.lengthSquared() < 1.0E-6F) {
            right.set(1, 0, 0);
        }
        right.normalize();
        Vector3f up = new Vector3f(right).cross(view).normalize();
        int seg = 16;
        Vector3f prev = null;
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            Vector3f q = new Vector3f(p).add(new Vector3f(right).mul((float) Math.cos(a) * rad))
                    .add(new Vector3f(up).mul((float) Math.sin(a) * rad));
            if (prev != null) {
                quad(b, m, p, c, prev, edge, q, edge, p, c);
            }
            prev = q;
        }
    }

    /** a thin plus through p: two crossing streaks */
    private static void sparkle(VertexConsumer b, Matrix4f m, Vector3f p, float len, float w, int c) {
        Vector3f view = new Vector3f(p).normalize();
        Vector3f right = new Vector3f(view).cross(0, 1, 0);
        if (right.lengthSquared() < 1.0E-6F) {
            right.set(1, 0, 0);
        }
        right.normalize();
        Vector3f up = new Vector3f(right).cross(view).normalize();
        for (Vector3f axis : new Vector3f[]{right, up}) {
            Vector3f a = new Vector3f(p).sub(new Vector3f(axis).mul(len));
            Vector3f e = new Vector3f(p).add(new Vector3f(axis).mul(len));
            ribbon(b, m, a, p, w * 0.2F, w, c & 0x00FFFFFF, c);
            ribbon(b, m, p, e, w, w * 0.2F, c, c & 0x00FFFFFF);
        }
    }

    /** a flat strip from a to b facing the camera, w0/w1 half widths, colours at both ends */
    private static void ribbon(VertexConsumer b, Matrix4f m, Vector3f a, Vector3f e, float w0, float w1, int c0, int c1) {
        Vector3f along = new Vector3f(e).sub(a);
        Vector3f side = along.cross(a, new Vector3f());
        if (side.lengthSquared() < 1.0E-8F) {
            return;
        }
        side.normalize();
        quad(b, m, new Vector3f(a).add(new Vector3f(side).mul(w0)), c0, new Vector3f(a).sub(new Vector3f(side).mul(w0)), c0,
                new Vector3f(e).sub(new Vector3f(side).mul(w1)), c1, new Vector3f(e).add(new Vector3f(side).mul(w1)), c1);
    }

    /** one quad, drawn from both sides */
    private static void quad(VertexConsumer b, Matrix4f m, Vector3f p1, int c1, Vector3f p2, int c2, Vector3f p3, int c3, Vector3f p4, int c4) {
        b.vertex(m, p1.x, p1.y, p1.z).color(c1);
        b.vertex(m, p2.x, p2.y, p2.z).color(c2);
        b.vertex(m, p3.x, p3.y, p3.z).color(c3);
        b.vertex(m, p4.x, p4.y, p4.z).color(c4);
        b.vertex(m, p4.x, p4.y, p4.z).color(c4);
        b.vertex(m, p3.x, p3.y, p3.z).color(c3);
        b.vertex(m, p2.x, p2.y, p2.z).color(c2);
        b.vertex(m, p1.x, p1.y, p1.z).color(c1);
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}

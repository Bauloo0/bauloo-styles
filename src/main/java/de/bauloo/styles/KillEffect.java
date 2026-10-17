package de.bauloo.styles;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Kill Effect: something happens where a player dies. Lightning uses a real (silent) bolt; all
 * other effects are drawn by this mod itself, so they show even with particles turned off.
 * Only on your screen: nothing is sent to the server, and nothing makes a sound.
 */
public final class KillEffect {

    /** the effects you can pick, in menu order: key -> label */
    public static final String[][] EFFECTS = {
            {"lightning", "Lightning"}, {"shockwave", "Shockwave"}, {"pillar", "Light Pillar"},
            {"popup", "Kill Popup"}, {"soul", "Soul Rise"}};   // "shatter" is gone: it crashed some games

    /** how long after your hit a death still counts as your kill */
    private static final long MY_KILL_MS = 5000;

    /** entity id -> time you last hit it */
    private static final Map<Integer, Long> HITS = new HashMap<>();
    /** player name -> time you last hit them (the entity can be gone when the death message comes) */
    private static final Map<String, Long> HIT_NAMES = new HashMap<>();
    /** when you last clicked a respawn anchor (charging or blowing it up) */
    private static long lastAnchorUse;
    /** an anchor death only counts as yours this soon after you used an anchor */
    private static final long ANCHOR_KILL_MS = 3000;
    /** our own lightning bolts: they stay silent */
    public static final Set<Entity> SILENT = Collections.newSetFromMap(new WeakHashMap<>());
    private static int nextId = -1_000_000;

    /** kills since you last died, for the popup */
    private static int streak;

    private KillEffect() {
    }

    public static boolean valid(String effect) {
        for (String[] e : EFFECTS) {
            if (e[0].equals(effect)) {
                return true;
            }
        }
        return false;
    }

    /** You hit something (render thread). */
    public static void onAttack(Entity target) {
        if (HITS.size() > 128) {
            HITS.clear();
        }
        HITS.put(target.getId(), System.currentTimeMillis());
        if (target instanceof PlayerEntity player) {
            if (HIT_NAMES.size() > 128) {
                HIT_NAMES.clear();
            }
            HIT_NAMES.put(player.getName().getString(), System.currentTimeMillis());
        }
    }

    /** entity id -> when its effect last played, so status and health don't both fire */
    private static final Map<Integer, Long> PLAYED = new HashMap<>();

    /** A living entity died on the client (render thread). */
    public static void onDeath(Entity entity) {
        StylesUiConfig.load();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(entity instanceof PlayerEntity) || entity == mc.player) {
            return;
        }
        Long hit = HITS.get(entity.getId());
        if (!valid(StylesUiConfig.killEffect) && !StylesUiConfig.killSound) {
            return;
        }
        boolean mine = hit != null && System.currentTimeMillis() - hit <= MY_KILL_MS;
        if (StylesUiConfig.killOnlyMine && !mine) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = PLAYED.get(entity.getId());
        if (last != null && now - last < 3000) {
            return;
        }
        if (PLAYED.size() > 64) {
            PLAYED.clear();
        }
        PLAYED.put(entity.getId(), now);
        Vec3d now2 = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        String name = entity.getName().getString();
        Vec3d at = beforeDeath(name, now2);
        if (mine) {
            streak++;
        }
        if (mine) {
            killSound();
        }
        if (valid(StylesUiConfig.killEffect)) {
            play(StylesUiConfig.killEffect, at, name, mine ? streak : 0);
        }
    }

    /** player name -> positions over the last ticks, for every player near you */
    private static final Map<String, java.util.ArrayDeque<Vec3d>> TRAIL = new HashMap<>();
    private static final int TRAIL_TICKS = 6;

    /**
     * Where the player stood just before dying. Servers often move or remove the dead player in
     * the same moment (respawn, teleport back), so the current position can be far away or gone.
     */
    private static Vec3d beforeDeath(String name, Vec3d now) {
        java.util.ArrayDeque<Vec3d> trail = TRAIL.get(name);
        if (trail == null || trail.isEmpty()) {
            return now;
        }
        Vec3d[] seen = trail.toArray(new Vec3d[0]);
        // newest last; go back a few ticks, but stop before a jump (that is the respawn)
        Vec3d pick = seen[seen.length - 1];
        for (int i = seen.length - 1; i > 0 && i >= seen.length - 4; i--) {
            if (seen[i].squaredDistanceTo(seen[i - 1]) > 9.0) {
                return seen[i - 1];
            }
            pick = seen[i - 1];
        }
        return now == null || now.squaredDistanceTo(pick) > 9.0 ? pick : now;
    }

    private static void track(MinecraftClient client) {
        java.util.Set<String> near = new java.util.HashSet<>();
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player || player.squaredDistanceTo(client.player) > 64 * 64) {
                continue;
            }
            String name = player.getName().getString();
            near.add(name);
            java.util.ArrayDeque<Vec3d> trail = TRAIL.computeIfAbsent(name, k -> new java.util.ArrayDeque<>());
            trail.addLast(new Vec3d(player.getX(), player.getY(), player.getZ()));
            while (trail.size() > TRAIL_TICKS) {
                trail.removeFirst();
            }
        }
        // keep a player who just vanished a little longer: the death message may come after
        if (TRAIL.size() > 64) {
            TRAIL.keySet().retainAll(near);
        }
    }

    /** The TEST button: the effect three blocks in front of you. */
    private static final net.minecraft.util.Identifier KILL_SOUND =
            net.minecraft.util.Identifier.of(BaulooStylesClient.MOD_ID, "kill");

    /**
     * The kill sound, when it is switched on. Not positional: you hear it the same everywhere.
     * Plays on the Master volume only: many PvP players turn UI and Players sounds far down.
     */
    public static void killSound() {
        if (StylesUiConfig.killSound) {
            MinecraftClient.getInstance().getSoundManager().play(new net.minecraft.client.sound.PositionedSoundInstance(
                    KILL_SOUND, net.minecraft.sound.SoundCategory.MASTER, StylesUiConfig.killVolume / 100.0F, 1.0F,
                    net.minecraft.client.sound.SoundInstance.createRandom(), false, 0,
                    net.minecraft.client.sound.SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true));
        }
    }

    public static void test() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3d look = mc.player.getRotationVec(1.0F);
        Vec3d at = mc.player.getEntityPos().add(look.x * 3.5, 0, look.z * 3.5);
        String effect = valid(StylesUiConfig.killEffect) ? StylesUiConfig.killEffect : "shockwave";
        killSound();
        play(effect, at, "Test Dummy", Math.max(1, streak + 1));
    }

    /** lightning waves still running: {x, y, z, ticks left} */
    private static final List<double[]> RUNNING = new ArrayList<>();
    private static final int WAVES = 10;

    /**
     * Third way to notice a kill, for servers that don't send the death to other players:
     * the death message ("X was slain by Y"). Vanilla sends it as a "death.*" translation whose
     * first argument is the one who died.
     */
    private static void onMessage(Text message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return;
        }
        String me = mc.player.getName().getString();
        String victim = null;
        boolean mine = false;
        boolean otherKiller = false;
        if (message.getContent() instanceof net.minecraft.text.TranslatableTextContent t && t.getKey().startsWith("death.")) {
            Object[] args = t.getArgs();
            if (args.length == 0) {
                return;
            }
            victim = argText(args[0]);
            for (int i = 1; i < args.length; i++) {
                String arg = argText(args[i]);
                if (arg.equals(me)) {
                    mine = true;
                } else if (i == 1 && !arg.isEmpty() && !arg.startsWith("[")) {
                    otherKiller = true; // someone else is named as the killer
                }
            }
        } else {
            String plain = message.getString();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^(\\w{1,16}) (?:was|got|has been) .* by (\\w{1,16})").matcher(plain);
            if (!m.find()) {
                return;
            }
            victim = m.group(1);
            mine = m.group(2).equals(me);
            otherKiller = !mine;
        }
        if (victim == null) {
            return;
        }
        if (victim.equals(me)) {
            streak = 0;
            return;
        }
        // Anchor and bed deaths name no killer ("[Intentional Game Design]"). Being close is not enough:
        // it counts as yours only if you hit them just before, or you used an anchor right next to them.
        if (!mine && !otherKiller) {
            long now = System.currentTimeMillis();
            Long hit = HIT_NAMES.get(victim);
            if (hit != null && now - hit <= MY_KILL_MS) {
                mine = true;
            } else if (now - lastAnchorUse <= ANCHOR_KILL_MS) {
                Vec3d near = beforeDeath(victim, null);
                mine = near != null && near.squaredDistanceTo(mc.player.getEntityPos()) < 12 * 12;
            }
        }
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.getName().getString().equals(victim)) {
                Long lastHit = HITS.get(player.getId());
                if (mine && (lastHit == null || System.currentTimeMillis() - lastHit > MY_KILL_MS)) {
                    HITS.put(player.getId(), System.currentTimeMillis()); // the killer is named, so it counts as yours
                }
                if (otherKiller) {
                    HITS.remove(player.getId()); // you hit them, but someone else got the kill
                }
                onDeath(player);
                return;
            }
        }
        // already gone: where that player was last seen
        if (TRAIL.containsKey(victim) && (mine || !StylesUiConfig.killOnlyMine)) {
            Vec3d at = beforeDeath(victim, null);
            playOnce(-victim.hashCode(), at, victim, mine);
        }
    }

    private static String argText(Object arg) {
        return arg instanceof Text text ? text.getString() : String.valueOf(arg);
    }

    private static void playOnce(int id, Vec3d at, String name, boolean mine) {
        StylesUiConfig.load();
        if (!valid(StylesUiConfig.killEffect) && !StylesUiConfig.killSound) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = PLAYED.get(id);
        if (last != null && now - last < 3000) {
            return;
        }
        PLAYED.put(id, now);
        if (mine) {
            streak++;
        }
        if (mine) {
            killSound();
        }
        if (valid(StylesUiConfig.killEffect)) {
            play(StylesUiConfig.killEffect, at, name, mine ? streak : 0);
        }
    }

    public static void register() {
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player == MinecraftClient.getInstance().player && hit != null
                    && world.getBlockState(hit.getBlockPos()).isOf(net.minecraft.block.Blocks.RESPAWN_ANCHOR)) {
                lastAnchorUse = System.currentTimeMillis();
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                try {
                    onMessage(message);
                } catch (RuntimeException e) {
                    BaulooStylesClient.LOGGER.warn("Kill effect: could not read a message: {}", e.toString());
                }
            }
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null && client.player != null) {
                track(client);
                if (client.player.isDead()) {
                    streak = 0;
                }
            }
            if (RUNNING.isEmpty()) {
                return;
            }
            if (client.world == null) {
                RUNNING.clear();
                return;
            }
            for (java.util.Iterator<double[]> it = RUNNING.iterator(); it.hasNext(); ) {
                double[] run = it.next();
                int left = (int) run[3];
                lightningWave(run[0], run[1], run[2], WAVES - left);
                if (left <= 1) {
                    it.remove();
                } else {
                    run[3] = left - 1;
                }
            }
        });
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

    /** at = the feet of the one who died. name and streak are for the popup (streak 0 = not yours). */
    public static void play(String effect, Vec3d at, String name, int kills) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) {
            return;
        }
        if ("lightning".equals(effect)) {
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setId(nextId--);
                bolt.refreshPositionAfterTeleport(at.x, at.y, at.z);
                bolt.setCosmetic(true);
                SILENT.add(bolt);
                world.addEntity(bolt);
            }
            lightningWave(at.x, at.y, at.z, 0);
            RUNNING.add(new double[]{at.x, at.y, at.z, WAVES - 1});
            return;
        }
        if (FX.size() > 24) {
            FX.remove(0);
        }
        if ("shatter".equals(effect)) {
            return; // removed effect, an old setting or preset may still name it
        }
        FX.add(new Fx(effect, at, name, kills, StylesUiConfig.killColor & 0xFFFFFF));
    }

    // ------------------------------------------------------------------ lightning (particles)

    /**
     * One particle, built directly and handed to the particle manager. That skips the usual
     * entrance, which "Particles: Minimal" and mods like Sodium Extra shut, so the sparks
     * still show when you have particles off.
     */
    private static void spawn(ClientWorld world, net.minecraft.particle.ParticleEffect effect,
                              double x, double y, double z, double vx, double vy, double vz) {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            net.minecraft.client.particle.Particle particle = ((de.bauloo.styles.mixin.ParticleManagerInvoker) mc.particleManager)
                    .baulooStyles$create(effect, x, y, z, vx, vy, vz);
            if (particle != null) {
                mc.particleManager.addParticle(particle);
                return;
            }
        } catch (RuntimeException ignored) {
            // fall back to the normal way below
        }
        world.addParticleClient(effect, true, true, x, y, z, vx, vy, vz);
    }

    private static void lightningWave(double x, double y, double z, int n) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        Random r = world.getRandom();
        boolean first = n == 0;
        for (int i = 0; i < (first ? 50 : 12); i++) {
            spawn(world, ParticleTypes.ELECTRIC_SPARK, x + r.nextGaussian() * 0.5, y + r.nextDouble() * 2.2,
                    z + r.nextGaussian() * 0.5, r.nextGaussian() * 0.3, r.nextDouble() * 0.3, r.nextGaussian() * 0.3);
        }
        // small white glow streaks; the old size-3 dust turned into big coloured blocks with many packs
        for (int i = 0; i < (first ? 24 : 4); i++) {
            spawn(world, ParticleTypes.END_ROD, x + r.nextGaussian() * 0.4, y + r.nextDouble() * 2.0, z + r.nextGaussian() * 0.4,
                    r.nextGaussian() * 0.05, r.nextDouble() * 0.08, r.nextGaussian() * 0.05);
        }
    }

    // ------------------------------------------------------------------ custom effects (own drawing)

    /** one running custom effect */
    private static final class Fx {
        final String type;
        final double x, y, z;
        final long start = System.currentTimeMillis();
        final String name;
        final int streak;
        final int color;
        /** shatter pieces: {x, y, z, vx, vy, vz, size, shade, spin} relative to the kill spot */
        float[][] bits;

        Fx(String type, Vec3d at, String name, int streak, int color) {
            this.type = type;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.name = name;
            this.streak = streak;
            this.color = color;
        }

        long life() {
            return switch (type) {
                case "shockwave" -> 1100;
                case "pillar" -> 1600;
                case "popup" -> 2400;
                case "shatter" -> 2600;
                default -> 2200; // soul
            };
        }

        void makeBits(Random r) {
            bits = new float[70][];
            for (int i = 0; i < bits.length; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double speed = 1.2 + r.nextDouble() * 3.2;
                bits[i] = new float[]{
                        (float) (r.nextGaussian() * 0.25), (float) (0.2 + r.nextDouble() * 1.6), (float) (r.nextGaussian() * 0.25),
                        (float) (Math.cos(a) * speed), (float) (2.5 + r.nextDouble() * 4.5), (float) (Math.sin(a) * speed),
                        (float) (0.07 + r.nextDouble() * 0.11), (float) r.nextDouble(), (float) (r.nextGaussian() * 720)};
            }
        }
    }

    private static final List<Fx> FX = new ArrayList<>();

    private static void render(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam, Quaternionf look) {
        long now = System.currentTimeMillis();
        FX.removeIf(fx -> now - fx.start > fx.life());
        for (Fx fx : FX) {
            float t = (now - fx.start) / (float) fx.life();
            float ms = now - fx.start;
            matrices.push();
            matrices.translate(fx.x - cam.x, fx.y - cam.y, fx.z - cam.z);
            switch (fx.type) {
                case "shockwave" -> shockwave(matrices, consumers, fx, ms);
                case "pillar" -> pillar(matrices, consumers, fx, t);
                case "popup" -> popup(matrices, consumers, fx, ms, look);
                case "shatter" -> shatter(matrices, consumers, fx, ms / 1000F);
                default -> soul(matrices, consumers, fx, t, look);
            }
            matrices.pop();
        }
    }

    /** Two glowing rings race outwards over the ground, each with a short fading wall. */
    private static void shockwave(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float ms) {
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        for (int ring = 0; ring < 2; ring++) {
            float t = (ms - ring * 180) / 900F;
            if (t <= 0 || t >= 1) {
                continue;
            }
            float radius = 0.4F + (ring == 0 ? 5.0F : 3.6F) * easeOut(t);
            float fade = (float) Math.pow(1 - t, 1.4);
            float width = 0.18F + 0.5F * (1 - t);
            int bright = argb(fade, ring == 0 ? fx.color : lighten(fx.color, 0.5F));
            int none = argb(0, fx.color);
            // the flat band: bright in the middle, soft to both sides
            annulus(glow, m, 0.03F, radius - width, radius, none, bright);
            annulus(glow, m, 0.03F, radius, radius + width * 0.6F, bright, none);
            // the wall: rises out of the band and fades upwards
            wall(glow, m, radius, 0.03F, 0.03F + 0.9F * (1 - t), bright, none);
        }
        // a bright flash at the centre in the first moment
        float flash = 1 - ms / 250F;
        if (flash > 0) {
            annulus(glow, m, 0.04F, 0, 1.6F, argb(flash, 0xFFFFFF), argb(0, fx.color));
        }
    }

    /** A beacon-like beam shoots up out of the ground, then thins out and fades. */
    private static void pillar(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float t) {
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        float grow = easeOut(Math.min(1, t / 0.12F));
        float height = 2 + 40 * grow;
        float fade = t < 0.25F ? 1 : 1 - (t - 0.25F) / 0.75F;
        fade = fade * fade;
        float thin = 1 - 0.6F * t;
        // spin the beam slowly so its edges shimmer
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * 240));
        m = matrices.peek().getPositionMatrix();
        tube(glow, m, 0.9F * thin, height, argb(fade * 0.25F, fx.color), argb(0, fx.color));
        tube(glow, m, 0.45F * thin, height, argb(fade * 0.7F, fx.color), argb(0, fx.color));
        tube(glow, m, 0.16F * thin, height, argb(fade, 0xFFFFFF), argb(fade * 0.2F, 0xFFFFFF));
        matrices.pop();
        m = matrices.peek().getPositionMatrix();
        // a glowing pool on the ground
        annulus(glow, m, 0.03F, 0, 1.8F * thin, argb(fade * 0.8F, fx.color), argb(0, fx.color));
        // a ring that jumps off the ground when the beam starts
        if (t < 0.35F) {
            float k = t / 0.35F;
            float r = 0.5F + 2.5F * easeOut(k);
            annulus(glow, m, 0.05F + k * 0.3F, r - 0.15F, r + 0.15F, argb(1 - k, 0xFFFFFF), argb(0, fx.color));
        }
    }

    /** "+1 KILL", the name and your streak float up above the body and fade. */
    private static void popup(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float ms, Quaternionf look) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        float t = ms / fx.life();
        float pop = ms < 180 ? 1.6F - 0.6F * easeOut(ms / 180F) : 1;
        float fade = t > 0.75F ? 1 - (t - 0.75F) / 0.25F : Math.min(1, ms / 120F);
        float rise = 2.3F + 0.9F * easeOut(t);
        matrices.push();
        matrices.translate(0, rise, 0);
        matrices.multiply(look);
        float s = 0.045F * pop;
        matrices.scale(s, -s, s);
        Text big = Text.literal(fx.streak > 0 ? "+1 KILL" : "KILL").formatted(Formatting.BOLD);
        label(font, matrices, consumers, big, 0, fx.color, fade);
        matrices.scale(0.55F, 0.55F, 0.55F);
        label(font, matrices, consumers, Text.literal(fx.name), 20, 0xFFFFFF, fade);
        if (fx.streak >= 2) {
            Text row = Text.literal(fx.streak + "x STREAK").formatted(Formatting.BOLD);
            label(font, matrices, consumers, row, 32, lighten(fx.color, 0.45F), fade);
        }
        matrices.pop();
    }

    private static void label(TextRenderer font, MatrixStack matrices, VertexConsumerProvider consumers,
                              Text text, float y, int rgb, float fade) {
        int alpha = Math.max(5, Math.round(fade * 255));
        float x = -font.getWidth(text) / 2F;
        Matrix4f m = matrices.peek().getPositionMatrix();
        // dark copy one pixel down as a shadow, then the text itself
        font.draw(text, x + 1, y + 1, (Math.max(5, alpha / 2) << 24) | darken(rgb), false, m, consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
        matrices.push();
        matrices.translate(0, 0, -0.02F);
        font.draw(text, x, y, (alpha << 24) | rgb, false, matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
        matrices.pop();
    }

    /** The body bursts into little cubes that fly apart, bounce once and sink away. */
    private static void shatter(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec) {
        VertexConsumer solid = consumers.getBuffer(RenderLayers.debugQuads());
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        float gravity = 16;
        float fade = sec > 1.9F ? 1 - (sec - 1.9F) / 0.7F : 1;
        for (float[] b : fx.bits) {
            // fly until the piece reaches the ground (y = 0), then lie there
            float land = landTime(b[1], b[4], gravity);
            float ft = Math.min(sec, land);
            float px = b[0] + b[3] * ft * 0.6F;
            float pz = b[2] + b[5] * ft * 0.6F;
            float py = Math.max(0, b[1] + b[4] * ft - 0.5F * gravity * ft * ft);
            float size = b[6] * (sec > 1.9F ? fade : 1);
            if (size <= 0.005F) {
                continue;
            }
            int rgb = b[7] < 0.25F ? 0xFFFFFF : b[7] < 0.6F ? fx.color : darken(fx.color);
            matrices.push();
            matrices.translate(px, py + size / 2, pz);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(b[8] * ft));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(b[8] * 0.7F * ft));
            Matrix4f m = matrices.peek().getPositionMatrix();
            cube(solid, m, size / 2, argb(fade, rgb));
            if (sec < land) {
                cube(glow, m, size * 0.9F, argb(0.25F * fade, fx.color)); // glow while flying
            }
            matrices.pop();
        }
        // short burst of light where the body was
        if (sec < 0.3F) {
            Matrix4f m = matrices.peek().getPositionMatrix();
            float k = sec / 0.3F;
            annulus(glow, m, 0.03F, 0, 1.2F + k * 1.5F, argb(1 - k, 0xFFFFFF), argb(0, fx.color));
        }
    }

    /** A see-through copy of the player floats up with arms spread, then fades away. */
    private static void soul(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float t, Quaternionf look) {
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        float rise = 3.2F * easeIn(t);
        float fade = t < 0.1F ? t / 0.1F : 1 - (t - 0.1F) / 0.9F;
        float sway = (float) Math.sin(t * Math.PI * 3) * 6;
        // face the camera: turn only around the vertical axis
        org.joml.Vector3f forward = new org.joml.Vector3f(0, 0, -1).rotate(look);
        float yaw = (float) Math.toDegrees(Math.atan2(forward.x, forward.z));
        matrices.push();
        matrices.translate(0, rise, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw + 180 + sway));
        float px = 1 / 16F * 0.94F;
        int body = argb(0.55F * fade, fx.color);
        int shell = argb(0.18F * fade, lighten(fx.color, 0.4F));
        float arms = 20 + 110 * easeOut(Math.min(1, t * 2));
        // head, body, legs
        part(matrices, glow, 0, 28, 0, 4, 4, 4, px, body, shell);
        part(matrices, glow, 0, 18, 0, 4, 6, 2, px, body, shell);
        part(matrices, glow, -2, 6, 0, 2, 6, 2, px, body, shell);
        part(matrices, glow, 2, 6, 0, 2, 6, 2, px, body, shell);
        // arms swing up and out from the shoulders
        for (int side = -1; side <= 1; side += 2) {
            matrices.push();
            matrices.translate(side * 6 * px, 23 * px, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * arms));
            part(matrices, glow, 0, -5, 0, 2, 6, 2, px, body, shell);
            matrices.pop();
        }
        matrices.pop();
        // a soft glow left on the ground
        Matrix4f m = matrices.peek().getPositionMatrix();
        annulus(glow, m, 0.03F, 0, 1.3F, argb(0.6F * (1 - t), fx.color), argb(0, fx.color));
    }

    /** one body part: centre and half sizes in skin pixels, plus a slightly bigger glowing shell */
    private static void part(MatrixStack matrices, VertexConsumer glow, float cx, float cy, float cz,
                             float hx, float hy, float hz, float px, int body, int shell) {
        matrices.push();
        matrices.translate(cx * px, cy * px, cz * px);
        Matrix4f m = matrices.peek().getPositionMatrix();
        box(glow, m, hx * px, hy * px, hz * px, body);
        box(glow, m, (hx + 0.6F) * px, (hy + 0.6F) * px, (hz + 0.6F) * px, shell);
        matrices.pop();
    }

    // ------------------------------------------------------------------ drawing helpers

    /** flat ring at height y from r1 to r2, colour c1 at the inner edge and c2 at the outer edge */
    private static void annulus(VertexConsumer b, Matrix4f m, float y, float r1, float r2, int c1, int c2) {
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float cc = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(b, m, c0 * r1, y, s0 * r1, c1, cc * r1, y, s1 * r1, c1,
                    cc * r2, y, s1 * r2, c2, c0 * r2, y, s0 * r2, c2);
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

    /** upright tube from the ground to height h, colour bottom at the foot and top at the tip */
    private static void tube(VertexConsumer b, Matrix4f m, float r, float h, int bottom, int top) {
        int seg = 12;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
            float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            quad(b, m, x0, 0, z0, bottom, x1, 0, z1, bottom, x1, h, z1, top, x0, h, z0, top);
        }
    }

    /** a box around the current origin with half sizes hx, hy, hz */
    private static void box(VertexConsumer b, Matrix4f m, float hx, float hy, float hz, int c) {
        quad(b, m, -hx, -hy, -hz, c, hx, -hy, -hz, c, hx, hy, -hz, c, -hx, hy, -hz, c);
        quad(b, m, -hx, -hy, hz, c, hx, -hy, hz, c, hx, hy, hz, c, -hx, hy, hz, c);
        quad(b, m, -hx, -hy, -hz, c, -hx, -hy, hz, c, -hx, hy, hz, c, -hx, hy, -hz, c);
        quad(b, m, hx, -hy, -hz, c, hx, -hy, hz, c, hx, hy, hz, c, hx, hy, -hz, c);
        quad(b, m, -hx, hy, -hz, c, hx, hy, -hz, c, hx, hy, hz, c, -hx, hy, hz, c);
        quad(b, m, -hx, -hy, -hz, c, hx, -hy, -hz, c, hx, -hy, hz, c, -hx, -hy, hz, c);
    }

    private static void cube(VertexConsumer b, Matrix4f m, float h, int c) {
        box(b, m, h, h, h, c);
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

    private static int darken(int rgb) {
        return (rgb >> 1) & 0x7F7F7F;
    }

    private static float easeOut(float t) {
        float k = 1 - Math.max(0, Math.min(1, t));
        return 1 - k * k * k;
    }

    private static float easeIn(float t) {
        float k = Math.max(0, Math.min(1, t));
        return k * k;
    }
}

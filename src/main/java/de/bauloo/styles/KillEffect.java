package de.bauloo.styles;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
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
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
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
            {"popup", "Kill Popup"}, {"soul", "Soul Rise"},
            {"ufo", "UFO"}, {"blackhole", "Black Hole"}, {"meteor", "Meteor"}, {"grave", "Gravestone"},
            {"snap", "Snap"}, {"rocket", "Rocket"}, {"anvil", "Anvil"}, {"tornado", "Tornado"}, {"freeze", "Freeze"}};
    // "shatter" is gone: it crashed some games (it took two buffers at once; the new effects never do)

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
            if (!SKINS.containsKey(name)) {
                Skin skin = skinOf(player);
                if (skin != null) {
                    SKINS.put(name, skin);
                }
            }
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
        if (SKINS.size() > 64) {
            SKINS.keySet().retainAll(near);
        }
    }

    /** a player's skin, for the effects that show the one who died (UFO, Black Hole) */
    private record Skin(net.minecraft.util.Identifier texture, boolean slim) {
    }

    /** player name -> their skin, kept while they are near you (the entity is often gone at the death) */
    private static final Map<String, Skin> SKINS = new HashMap<>();

    private static Skin skinOf(PlayerEntity player) {
        if (player instanceof AbstractClientPlayerEntity client) {
            return skinOf(client.getSkin());
        }
        return null;
    }

    private static Skin skinOf(SkinTextures textures) {
        if (textures == null || textures.body() == null) {
            return null;
        }
        return new Skin(textures.body().texturePath(), textures.model() == PlayerSkinType.SLIM);
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

    /** how long the TEST button keeps the menu closed, so the whole effect can be seen */
    public static long testLength() {
        if (!valid(StylesUiConfig.killEffect) || "lightning".equals(StylesUiConfig.killEffect)) {
            return 2500;
        }
        return Math.max(2500, new Fx(StylesUiConfig.killEffect, Vec3d.ZERO, "", 0, 0).life() + 300);
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
        Fx fx = new Fx(effect, at, name, kills, StylesUiConfig.killColor & 0xFFFFFF);
        Skin skin = mc.player != null && name.equals("Test Dummy") ? skinOf(mc.player) : SKINS.get(name);
        fx.skin = skin != null ? skin : skinOf(net.minecraft.client.util.DefaultSkinHelper.getSteve());
        if (mc.player != null) {
            // the gravestone faces you
            fx.yaw = (float) Math.toDegrees(Math.atan2(-(mc.player.getX() - at.x), -(mc.player.getZ() - at.z)));
        }
        fx.makeBits(world.getRandom());
        FX.add(fx);
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
        /** pieces: {x, y, z, vx, vy, vz, size, shade, spin} relative to the kill spot (meteor, gravestone) */
        float[][] bits;
        /** the one who died, drawn by the UFO and Black Hole */
        Skin skin;
        /** which way the gravestone faces (towards you) */
        float yaw;

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
                case "ufo" -> 3000;
                case "blackhole" -> 2800;
                case "meteor" -> 2400;
                case "grave" -> 3900;
                case "snap" -> 2500;
                case "rocket" -> 2500;
                case "anvil" -> 2500;
                case "tornado" -> 2900;
                case "freeze" -> 2400;
                default -> 2200; // soul
            };
        }

        void makeBits(Random r) {
            switch (type) {
                case "meteor" -> {
                    // rock chunks thrown out of the crater
                    bits = new float[30][];
                    for (int i = 0; i < bits.length; i++) {
                        double a = r.nextDouble() * Math.PI * 2;
                        double speed = 2.0 + r.nextDouble() * 4.5;
                        bits[i] = new float[]{
                                (float) (r.nextGaussian() * 0.3), (float) (0.1 + r.nextDouble() * 0.4), (float) (r.nextGaussian() * 0.3),
                                (float) (Math.cos(a) * speed), (float) (4.0 + r.nextDouble() * 5.0), (float) (Math.sin(a) * speed),
                                (float) (0.12 + r.nextDouble() * 0.16), (float) r.nextDouble(), (float) (r.nextGaussian() * 600)};
                    }
                }
                case "grave" -> {
                    // dirt clumps pushed up by the stone
                    bits = new float[22][];
                    for (int i = 0; i < bits.length; i++) {
                        double a = r.nextDouble() * Math.PI * 2;
                        double speed = 0.8 + r.nextDouble() * 1.6;
                        bits[i] = new float[]{
                                (float) (Math.cos(a) * 0.45), 0.05F, (float) (Math.sin(a) * 0.3),
                                (float) (Math.cos(a) * speed), (float) (2.5 + r.nextDouble() * 2.5), (float) (Math.sin(a) * speed),
                                (float) (0.07 + r.nextDouble() * 0.08), (float) r.nextDouble(), (float) (r.nextGaussian() * 500)};
                    }
                }
                case "blackhole" -> {
                    // sparks that spiral in: {radius, angle, height, speed, offset, size}
                    bits = new float[48][];
                    for (int i = 0; i < bits.length; i++) {
                        bits[i] = new float[]{
                                (float) (3.0 + r.nextDouble() * 3.5), (float) (r.nextDouble() * Math.PI * 2),
                                (float) (r.nextGaussian() * 0.9), (float) (0.55 + r.nextDouble() * 0.6),
                                (float) r.nextDouble(), (float) (0.02 + r.nextDouble() * 0.03)};
                    }
                }
                case "snap" -> {
                    // every pixel on the front of the skin becomes a flake:
                    // {x, y, z (skin pixels), u, v, when it lets go, vx, vy, vz, swirl}
                    List<float[]> flakes = new ArrayList<>();
                    int aw = skin != null && skin.slim() ? 3 : 4;
                    float[][] parts = {{-4, 24, 8, 8, -4, 0, 0, 8}, {-4, 12, 8, 12, -2, 16, 16, 4},
                            {0, 0, 4, 12, -2, 0, 16, 4}, {-4, 0, 4, 12, -2, 16, 48, 4},
                            {4, 12, aw, 12, -2, 40, 16, 4}, {-4 - aw, 12, aw, 12, -2, 32, 48, 4}};
                    for (float[] q : parts) {
                        for (int i = 0; i < q[2]; i++) {
                            for (int j = 0; j < q[3]; j++) {
                                float x = q[0] + q[2] - i - 0.5F;
                                float y = q[1] + q[3] - j - 0.5F;
                                flakes.add(new float[]{x, y, q[4] + 0.5F, q[5] + q[7] + i, q[6] + q[7] + j,
                                        0.15F + 0.55F * (x + 8) / 16F + 0.35F * r.nextFloat(),
                                        -(0.6F + r.nextFloat() * 1.2F), 0.1F + r.nextFloat() * 0.5F,
                                        (float) r.nextGaussian() * 0.3F, r.nextFloat() * 6});
                            }
                        }
                    }
                    bits = flakes.toArray(new float[0][]);
                }
                case "rocket" -> {
                    // firework sparks: {direction x, y, z, speed, colour}
                    bits = new float[80][];
                    for (int i = 0; i < bits.length; i++) {
                        org.joml.Vector3f d = new org.joml.Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(),
                                (float) r.nextGaussian()).normalize();
                        bits[i] = new float[]{d.x, d.y, d.z, 3.0F + r.nextFloat() * 2.5F, r.nextFloat()};
                    }
                }
                case "tornado" -> {
                    // dirt and stones flying around the funnel: {angle, height (0-1), speed, size, shade}
                    bits = new float[30][];
                    for (int i = 0; i < bits.length; i++) {
                        bits[i] = new float[]{(float) (r.nextDouble() * Math.PI * 2), r.nextFloat(),
                                0.6F + r.nextFloat() * 0.8F, 0.08F + r.nextFloat() * 0.12F, r.nextFloat()};
                    }
                }
                case "freeze" -> {
                    // ice shards when it breaks
                    bits = new float[36][];
                    for (int i = 0; i < bits.length; i++) {
                        double a = r.nextDouble() * Math.PI * 2;
                        double speed = 1.5 + r.nextDouble() * 2.5;
                        bits[i] = new float[]{
                                (float) (r.nextGaussian() * 0.2), (float) (0.2 + r.nextDouble() * 1.8), (float) (r.nextGaussian() * 0.2),
                                (float) (Math.cos(a) * speed), (float) (2.0 + r.nextDouble() * 3.0), (float) (Math.sin(a) * speed),
                                (float) (0.08 + r.nextDouble() * 0.14), (float) r.nextDouble(), (float) (r.nextGaussian() * 700)};
                    }
                }
                default -> bits = null;
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
                case "ufo" -> ufo(matrices, consumers, fx, ms / 1000F, look);
                case "blackhole" -> blackHole(matrices, consumers, fx, ms / 1000F, look);
                case "meteor" -> meteor(matrices, consumers, fx, ms / 1000F);
                case "grave" -> grave(matrices, consumers, fx, ms / 1000F, look);
                case "snap" -> snap(matrices, consumers, fx, ms / 1000F);
                case "rocket" -> rocket(matrices, consumers, fx, ms / 1000F, look);
                case "anvil" -> anvil(matrices, consumers, fx, ms / 1000F, look);
                case "tornado" -> tornado(matrices, consumers, fx, ms / 1000F);
                case "freeze" -> freeze(matrices, consumers, fx, ms / 1000F, look);
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

    // ------------------------------------------------------------------ special effects
    // They take one buffer at a time and never write to it again after asking for another one:
    // holding two at once is what made the old "shatter" effect crash.

    /** A UFO swoops down, beams the player up into it and shoots off into the sky. */
    private static void ufo(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec, Quaternionf look) {
        float hover = 5.5F;
        float ux;
        float uy;
        float tilt;
        if (sec < 0.12F) {
            float k = easeOut(sec / 0.12F);
            ux = 0;
            uy = hover + 2.5F * (1 - k);
            tilt = 0;
        } else if (sec < 2.3F) {
            ux = 0;
            uy = hover + (float) Math.sin(sec * 4) * 0.12F;
            tilt = (float) Math.sin(sec * 3) * 4;
        } else {
            float k = easeIn((sec - 2.3F) / 0.6F);
            ux = 16 * k;
            uy = hover + 24 * k;
            tilt = 30 * k;
        }
        float beam = Math.min(clamp((sec - 0.04F) / 0.08F), clamp((2.3F - sec) / 0.2F));

        // the player rises up the beam, spinning with arms up, and shrinks into the UFO
        float lift = clamp((sec - 0.05F) / 1.8F);
        if (lift < 1) {
            float k = lift * lift * (3 - 2 * lift);
            float s = 1 - 0.65F * k;
            matrices.push();
            matrices.translate(0, (uy - 0.4F - 1.9F * s) * k, 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(faceYaw(look) + sec * 200));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.sin(sec * 5) * 8));
            matrices.scale(s, s, s);
            figure(matrices, consumers, fx.skin, 25 + 130 * easeOut(clamp(sec / 0.4F)));
            matrices.pop();
        }

        // the saucer: metal hull, a little green pilot under a glass dome, chasing rim lights
        matrices.push();
        matrices.translate(ux, uy, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tilt));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sec * 140));
        VertexConsumer vc = consumers.getBuffer(RenderLayers.entityCutoutNoCull(Cosmetics.WHITE));
        MatrixStack.Entry e = matrices.peek();
        frustum(e, vc, -0.34F, 1.1F, -0.12F, 1.75F, 24, 0xFF4A525C, 0, 0xFF3A4048);
        frustum(e, vc, -0.12F, 1.75F, 0.06F, 2.4F, 24, 0xFF8D97A4, 0, 0);
        frustum(e, vc, 0.06F, 2.4F, 0.2F, 2.2F, 24, 0xFFA9B2BE, 0, 0);
        frustum(e, vc, 0.2F, 2.2F, 0.4F, 1.15F, 24, 0xFFC3CBD5, 0xFFB4BCC6, 0);
        ebox(e, vc, -0.17F, 0.4F, -0.15F, 0.17F, 0.72F, 0.15F, 0xFF7BD86B);
        ebox(e, vc, -0.13F, 0.53F, -0.165F, -0.03F, 0.61F, -0.14F, 0xFF101010);
        ebox(e, vc, 0.03F, 0.53F, -0.165F, 0.13F, 0.61F, -0.14F, 0xFF101010);
        vc = consumers.getBuffer(RenderLayers.entityTranslucentEmissive(Cosmetics.WHITE));
        e = matrices.peek();
        int chase = (int) (sec * 12);
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            float lx = (float) Math.cos(a) * 2.42F;
            float lz = (float) Math.sin(a) * 2.42F;
            int col = (i + chase) % 4 == 0 ? 0xFF000000 | lighten(fx.color, 0.25F) : 0xD0FFF0B0;
            ebox(e, vc, lx - 0.08F, 0.02F, lz - 0.08F, lx + 0.08F, 0.14F, lz + 0.08F, col);
        }
        frustum(e, vc, -0.36F, 0.85F, -0.35F, 0.85F, 24, 0, 0, argb(0.4F + 0.6F * beam, lighten(fx.color, 0.4F)));
        int glass = 0x8C9FE8FF;
        frustum(e, vc, 0.4F, 1.0F, 0.62F, 0.95F, 24, glass, 0, 0);
        frustum(e, vc, 0.62F, 0.95F, 0.82F, 0.75F, 24, glass, 0, 0);
        frustum(e, vc, 0.82F, 0.75F, 0.95F, 0.45F, 24, glass, glass, 0);
        matrices.pop();

        // the tractor beam with rings running up it
        if (beam > 0) {
            float flick = beam * (0.85F + 0.15F * (float) Math.sin(sec * 37));
            float top = uy - 0.35F;
            matrices.push();
            matrices.translate(ux, 0, 0);
            Matrix4f m = matrices.peek().getPositionMatrix();
            VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
            cone(glow, m, 1.7F, 0.75F, top, argb(0.35F * flick, fx.color), argb(0.12F * flick, fx.color));
            cone(glow, m, 0.9F, 0.4F, top, argb(0.5F * flick, lighten(fx.color, 0.5F)), argb(0.2F * flick, 0xFFFFFF));
            for (int i = 0; i < 4; i++) {
                float f = (sec * 0.9F + i / 4F) % 1;
                float r = 1.7F - 0.95F * f;
                annulus(glow, m, f * top, r - 0.1F, r + 0.06F, argb(0.9F * flick * (1 - f), 0xFFFFFF), argb(0, fx.color));
            }
            annulus(glow, m, 0.03F, 0, 2.2F, argb(0.7F * flick, fx.color), argb(0, fx.color));
            matrices.pop();
        }
    }

    /** A black hole opens above the body, pulls the player in stretched thin and collapses with a flash. */
    private static void blackHole(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec, Quaternionf look) {
        float cy = 2.4F;
        float size = sec < 0.08F ? 0.8F * easeOut(sec / 0.08F)
                : sec < 2.1F ? 0.8F : 0.8F * (1 - easeIn((sec - 2.1F) / 0.3F));

        float pull = clamp(sec / 1.9F);
        if (pull < 1) {
            float k = easeIn(pull);
            float s = 1 - k;
            matrices.push();
            matrices.translate(0, 0.94F + (cy - 0.94F) * k, 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(faceYaw(look) + sec * sec * 260));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(k * 70));
            matrices.scale(s * (1 - 0.5F * k), s * (1 + 2.2F * k), s * (1 - 0.5F * k));
            matrices.translate(0, -0.94F, 0);
            figure(matrices, consumers, fx.skin, 20 + 140 * k);
            matrices.pop();
        }

        matrices.push();
        matrices.translate(0, cy, 0);
        if (size > 0.01F) {
            VertexConsumer vc = consumers.getBuffer(RenderLayers.entityCutoutNoCull(Cosmetics.WHITE));
            sphere(matrices.peek(), vc, size, 0xFF000000);
            // the glowing disk swirling around it, a little tilted
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(18));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-sec * 260));
            VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
            swirl(glow, matrices.peek().getPositionMatrix(), size * 1.25F, size * 3.4F, fx.color);
            matrices.pop();
            // a bright rim that always faces you
            matrices.push();
            matrices.multiply(look);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            Matrix4f m = matrices.peek().getPositionMatrix();
            annulus(glow, m, 0, size * 0.98F, size * 1.12F, argb(0.9F, lighten(fx.color, 0.6F)), argb(0.6F, fx.color));
            annulus(glow, m, 0, size * 1.12F, size * 1.9F, argb(0.6F, fx.color), argb(0, fx.color));
            matrices.pop();
            // sparks spiralling in
            if (sec < 2.2F) {
                for (float[] b : fx.bits) {
                    float p = (sec * b[3] + b[4]) % 1;
                    float r = b[0] * (1 - p) * (1 - p) + size;
                    float a = b[1] + p * 7;
                    float fade = Math.min(1, p * 5) * Math.min(1, sec * 5) * Math.min(1, (2.2F - sec) * 3);
                    matrices.push();
                    matrices.translate(Math.cos(a) * r, b[2] * (1 - p), Math.sin(a) * r);
                    cube(glow, matrices.peek().getPositionMatrix(), b[5] * (1 - 0.5F * p),
                            argb(fade * 0.8F, lighten(fx.color, 0.2F + 0.6F * p)));
                    matrices.pop();
                }
            }
        }
        // it collapses: a ring of light and a flash
        if (sec > 2.35F) {
            float k = clamp((sec - 2.35F) / 0.4F);
            VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
            float r = 0.3F + 3.2F * easeOut(k);
            matrices.push();
            matrices.multiply(look);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            Matrix4f m = matrices.peek().getPositionMatrix();
            annulus(glow, m, 0, r - 0.35F, r, argb(0, fx.color), argb(1 - k, lighten(fx.color, 0.6F)));
            annulus(glow, m, 0, 0, 0.2F + 1.5F * (1 - k), argb(1 - k, 0xFFFFFF), argb(0, fx.color));
            matrices.pop();
        }
        matrices.pop();
    }

    /** A burning rock falls out of the sky onto the body: flash, shockwave, flying rocks, a glowing crater. */
    private static void meteor(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec) {
        float fall = 0.13F;
        float sx = 5;
        float sy = 13;
        float sz = -4;
        if (sec < fall) {
            VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
            // the fire trail: older positions, smaller and redder
            for (int i = 12; i >= 1; i--) {
                float k = Math.max(0, sec - i * 0.006F) / fall;
                float f = i / 12F;
                matrices.push();
                matrices.translate(sx * (1 - k), 0.5F + sy * (1 - k), sz * (1 - k));
                cube(glow, matrices.peek().getPositionMatrix(), 0.55F * (1 - f * 0.7F), argb(0.75F * (1 - f), blend(0xFFE070, 0xFF3010, f)));
                matrices.pop();
            }
            float k = sec / fall;
            matrices.push();
            matrices.translate(sx * (1 - k), 0.5F + sy * (1 - k), sz * (1 - k));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sec * 500));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sec * 340));
            VertexConsumer vc = consumers.getBuffer(RenderLayers.entityCutoutNoCull(Cosmetics.WHITE));
            MatrixStack.Entry e = matrices.peek();
            ebox(e, vc, -0.45F, -0.45F, -0.45F, 0.45F, 0.45F, 0.45F, 0xFF2E1E18);
            ebox(e, vc, -0.3F, 0.4F, -0.35F, 0.25F, 0.55F, 0.2F, 0xFF3C2A20);
            ebox(e, vc, -0.55F, -0.2F, -0.1F, -0.4F, 0.25F, 0.3F, 0xFF241812);
            ebox(e, vc, 0.1F, -0.55F, -0.3F, 0.4F, -0.4F, 0.1F, 0xFFE8661E);
            glow = consumers.getBuffer(RenderLayers.lightning());
            cube(glow, matrices.peek().getPositionMatrix(), 0.62F, argb(0.55F, 0xFF7A20));
            matrices.pop();
            return;
        }
        float t = sec - fall;
        shockwave(matrices, consumers, fx, t * 1000);
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        float heat = 1 - clamp(t / 1.9F);
        annulus(glow, matrices.peek().getPositionMatrix(), 0.04F, 0, 1.7F, argb(0.8F * heat, 0xFF8A2A), argb(0, 0xFF3010));
        if (t < 0.2F) {
            float k = t / 0.2F;
            matrices.push();
            matrices.translate(0, 0.6F, 0);
            cube(glow, matrices.peek().getPositionMatrix(), 0.4F + 0.5F * easeOut(k), argb(0.6F * (1 - k) * (1 - k), 0xFFC050));
            matrices.pop();
        }
        VertexConsumer vc = consumers.getBuffer(RenderLayers.entityCutoutNoCull(Cosmetics.WHITE));
        for (float[] b : fx.bits) {
            float size = b[6] * (t > 1.4F ? Math.max(0, 1 - (t - 1.4F) / 0.6F) : 1);
            if (size > 0.01F) {
                int col = b[7] < 0.3F ? 0xFFE8661E : b[7] < 0.65F ? 0xFF3A2A22 : 0xFF5A5048;
                flyingBox(matrices, vc, b, t, 18, size, col);
            }
        }
    }

    /** A gravestone with the name on it rises out of the ground, a ghost floats up, then it sinks away. */
    private static void grave(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec, Quaternionf look) {
        float rise = sec < 0.15F ? easeOut(sec / 0.15F) : sec > 3.3F ? 1 - easeIn((sec - 3.3F) / 0.6F) : 1;
        float shake = sec < 0.25F ? (float) Math.sin(sec * 90) * 0.04F * (1 - sec / 0.25F) : 0;
        if (sec > 0.1F && sec < 2.5F) {
            soul(matrices, consumers, fx, (sec - 0.1F) / 2.4F, look);
        }
        if (sec < 1.6F) {
            VertexConsumer vc = consumers.getBuffer(RenderLayers.entityCutoutNoCull(Cosmetics.WHITE));
            for (float[] b : fx.bits) {
                float size = b[6] * (sec > 1.0F ? Math.max(0, 1 - (sec - 1.0F) / 0.6F) : 1);
                if (size > 0.01F) {
                    int col = b[7] < 0.2F ? 0xFF5E8A3A : b[7] < 0.6F ? 0xFF6B4A2E : 0xFF4E3520;
                    flyingBox(matrices, vc, b, sec, 16, size, col);
                }
            }
        }
        matrices.push();
        matrices.translate(shake, -1.35F * (1 - rise), 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(fx.yaw));
        VertexConsumer vc = consumers.getBuffer(RenderLayers.entityCutoutNoCull(Cosmetics.WHITE));
        MatrixStack.Entry e = matrices.peek();
        int stone = 0xFF9C9CA0;
        ebox(e, vc, -0.55F, 0, -0.32F, 0.55F, 0.16F, 0.32F, 0xFF6E6E70);
        ebox(e, vc, -0.4F, 0.16F, -0.11F, 0.4F, 1.12F, 0.11F, stone);
        ebox(e, vc, -0.33F, 1.12F, -0.11F, 0.33F, 1.22F, 0.11F, stone);
        ebox(e, vc, -0.2F, 1.22F, -0.11F, 0.2F, 1.28F, 0.11F, stone);
        // moss
        ebox(e, vc, -0.41F, 0.16F, -0.125F, -0.16F, 0.3F, 0.0F, 0xFF5C7A36);
        ebox(e, vc, 0.18F, 0.16F, -0.125F, 0.41F, 0.22F, 0.0F, 0xFF4F6B2E);
        ebox(e, vc, -0.21F, 1.24F, -0.08F, 0.02F, 1.3F, 0.08F, 0xFF5C7A36);
        // the name cut into the front
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        matrices.push();
        matrices.translate(0, 0.9F, -0.113F);
        float s = 0.014F;
        matrices.scale(-s, -s, -s);
        engrave(font, matrices, consumers, Text.literal("R.I.P").formatted(Formatting.BOLD), 0x303034);
        matrices.translate(0, 14, 0);
        Text name = Text.literal(fx.name);
        float fit = Math.min(0.62F, 48F / Math.max(1, font.getWidth(name)));
        matrices.scale(fit, fit, fit);
        engrave(font, matrices, consumers, name, 0x2A2A2E);
        matrices.pop();
        matrices.pop();
    }

    /** a piece thrown from the kill spot that flies, lands and lies there: b = {x, y, z, vx, vy, vz, size, shade, spin} */
    private static void flyingBox(MatrixStack matrices, VertexConsumer vc, float[] b, float sec, float gravity, float size, int col) {
        float land = landTime(b[1], b[4], gravity);
        float ft = Math.min(sec, land);
        float px = b[0] + b[3] * ft * 0.6F;
        float pz = b[2] + b[5] * ft * 0.6F;
        float py = Math.max(0, b[1] + b[4] * ft - 0.5F * gravity * ft * ft);
        matrices.push();
        matrices.translate(px, py + size / 2, pz);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(b[8] * ft));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(b[8] * 0.7F * ft));
        float h = size / 2;
        ebox(matrices.peek(), vc, -h, -h, -h, h, h, h, col);
        matrices.pop();
    }

    private static void engrave(TextRenderer font, MatrixStack matrices, VertexConsumerProvider consumers, Text text, int rgb) {
        font.draw(text, -font.getWidth(text) / 2F, 0, 0xFF000000 | rgb, false, matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, 0xF000F0);
    }

    /** turn that makes a figure facing -z look at the camera */
    private static float faceYaw(Quaternionf look) {
        org.joml.Vector3f forward = new org.joml.Vector3f(0, 0, -1).rotate(look);
        return (float) Math.toDegrees(Math.atan2(forward.x, forward.z));
    }

    /** The player crumbles into flakes of their own skin, which the wind blows away. */
    private static void snap(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec) {
        if (fx.skin == null || fx.bits == null) {
            return;
        }
        float p = 1 / 16F * 0.94F;
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(fx.yaw));
        VertexConsumer vc = consumers.getBuffer(RenderLayers.entityTranslucent(fx.skin.texture()));
        // the body fades into the flakes, the flakes then let go one by one
        float body = 1 - clamp((sec - 0.05F) / 0.5F);
        if (body > 0.01F) {
            figureParts(matrices, vc, fx.skin, 6, argb(body, 0xFFFFFF));
        }
        MatrixStack.Entry e = matrices.peek();
        for (float[] b : fx.bits) {
            float age = sec - b[5];
            float x = b[0] * p;
            float y = b[1] * p;
            float z = b[2] * p;
            float half = p * 0.5F;
            float alpha = 1 - body;
            if (age > 0) {
                float swirl = (float) Math.sin(age * 6 + b[9]) * 0.15F;
                x += b[6] * age + swirl;
                y += b[7] * age + 0.4F * age * age;
                z += b[8] * age + swirl * 0.5F;
                alpha *= 1 - clamp(age / 1.3F);
                half *= 1 - 0.6F * clamp(age / 1.3F);
            }
            if (alpha > 0.02F) {
                pixelBox(e, vc, x, y, z, half, b[3], b[4], argb(alpha, 0xFFFFFF));
            }
        }
        matrices.pop();
    }

    /** The player rides a firework rocket into the sky and goes off as a firework. */
    private static void rocket(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec, Quaternionf look) {
        float up = 1.1F;
        float top = 10;
        if (sec < up) {
            float ry = top * easeIn(sec / up);
            matrices.push();
            matrices.translate(0, ry, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(sec * 30) * 3 * (sec / up)));
            matrices.push();
            matrices.translate(0, 0.95F, 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(fx.yaw + sec * 120));
            figure(matrices, consumers, fx.skin, 150);
            matrices.pop();
            VertexConsumer vc = consumers.getBuffer(RenderLayers.entityCutoutNoCull(Cosmetics.WHITE));
            frustum(matrices.peek(), vc, 0.0F, 0.24F, 0.95F, 0.24F, 12, 0xFFC8302C, 0xFFE8E0D0, 0xFF3A3A3A);
            frustum(matrices.peek(), vc, 0.4F, 0.25F, 0.55F, 0.25F, 12, 0xFFF2EEE6, 0, 0);
            for (int i = 0; i < 4; i++) {
                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * 90 + 45));
                ebox(matrices.peek(), vc, 0.2F, 0.0F, -0.03F, 0.45F, 0.35F, 0.03F, 0xFF8A1E1A);
                matrices.pop();
            }
            // flames out of the bottom
            VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
            for (int i = 0; i < 6; i++) {
                float flick = 0.8F + 0.4F * (float) Math.abs(Math.sin(sec * 47 + i * 1.7));
                matrices.push();
                matrices.translate(0, -0.12F - i * 0.2F, 0);
                cube(glow, matrices.peek().getPositionMatrix(), (0.2F - i * 0.025F) * flick,
                        argb(0.9F * (1 - i / 6F), blend(0xFFF0A0, 0xFF4010, i / 5F)));
                matrices.pop();
            }
            matrices.pop();
            // a smoke trail where the rocket was a moment ago
            VertexConsumer smoke = consumers.getBuffer(RenderLayers.entityTranslucentEmissive(Cosmetics.WHITE));
            for (int i = 1; i <= 14; i++) {
                float tau = sec - i * 0.06F;
                if (tau < 0) {
                    break;
                }
                float py = top * easeIn(tau / up) - 0.4F;
                float h = 0.18F + i * 0.05F;
                ebox(matrices.peek(), smoke, -h, py - h, -h, h, py + h, h, argb(0.45F * (1 - i / 15F), 0xC8C8C8));
            }
            // dust ring at the launch
            if (sec < 0.5F) {
                float k = sec / 0.5F;
                float r = 0.4F + 2.2F * easeOut(k);
                glow = consumers.getBuffer(RenderLayers.lightning());
                annulus(glow, matrices.peek().getPositionMatrix(), 0.04F, r - 0.4F, r, argb(0, 0xFFFFFF), argb(0.6F * (1 - k), 0xFFD8A0));
            }
            return;
        }
        // the firework
        float t = sec - up;
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        matrices.push();
        matrices.translate(0, top + 1.4F, 0);
        if (t < 0.2F) {
            matrices.push();
            matrices.multiply(look);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            float k = t / 0.2F;
            annulus(glow, matrices.peek().getPositionMatrix(), 0, 0, 0.5F + 2.0F * k, argb(1 - k, 0xFFFFFF), argb(0, fx.color));
            matrices.pop();
        }
        float fade = 1 - clamp(t / 1.3F);
        for (float[] b : fx.bits) {
            int col = b[4] < 0.45F ? fx.color : b[4] < 0.75F ? lighten(fx.color, 0.6F) : 0xFFE6A0;
            for (int k = 0; k < 3; k++) {
                float tt = Math.max(0, t - k * 0.05F);
                float d = b[3] * (1 - (float) Math.exp(-tt * 3));
                matrices.push();
                matrices.translate(b[0] * d, b[1] * d - 1.2F * tt * tt, b[2] * d);
                cube(glow, matrices.peek().getPositionMatrix(), 0.07F * (1 - k * 0.3F), argb(fade * (1 - k * 0.3F), col));
                matrices.pop();
            }
        }
        matrices.pop();
    }

    /** An anvil drops on the player, squashes them flat and stars circle above it. */
    private static void anvil(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec, Quaternionf look) {
        float hit = 0.14F;
        float sink = sec > 2.0F ? 1.4F * easeIn((sec - 2.0F) / 0.5F) : 0;
        matrices.push();
        matrices.translate(0, -sink, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(fx.yaw));
        if (sec < hit) {
            figure(matrices, consumers, fx.skin, 160);
        } else {
            matrices.push();
            matrices.scale(1.45F, 0.11F, 1.45F);
            figure(matrices, consumers, fx.skin, 85);
            matrices.pop();
        }
        float ay;
        if (sec < hit) {
            float k = sec / hit;
            ay = 0.2F + 9 * (1 - k * k);
        } else {
            float b = (sec - hit) / 0.28F;
            ay = 0.2F + (b < 1 ? 0.35F * (float) Math.sin(Math.PI * b) : 0);
        }
        matrices.push();
        matrices.translate(0, ay, 0);
        VertexConsumer vc = consumers.getBuffer(RenderLayers.entityCutoutNoCull(Cosmetics.WHITE));
        MatrixStack.Entry e = matrices.peek();
        ebox(e, vc, -0.4F, 0, -0.3F, 0.4F, 0.22F, 0.3F, 0xFF3E3E42);
        ebox(e, vc, -0.2F, 0.22F, -0.18F, 0.2F, 0.5F, 0.18F, 0xFF36363A);
        ebox(e, vc, -0.5F, 0.5F, -0.32F, 0.5F, 0.85F, 0.32F, 0xFF505056);
        matrices.pop();
        matrices.pop();
        // the rest without the turn, so the stars can face the camera
        matrices.push();
        matrices.translate(0, -sink, 0);
        VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
        // dust ring on impact
        if (sec > hit && sec < hit + 0.5F) {
            float k = (sec - hit) / 0.5F;
            float r = 0.5F + 2.0F * easeOut(k);
            annulus(glow, matrices.peek().getPositionMatrix(), 0.04F, r - 0.35F, r, argb(0, 0xFFFFFF), argb(0.7F * (1 - k), 0xFFFFFF));
        }
        // stars circling above
        if (sec > hit) {
            float a = clamp((sec - hit) / 0.15F) * (sec > 1.9F ? 1 - clamp((sec - 1.9F) / 0.3F) : 1);
            for (int i = 0; i < 4; i++) {
                double ang = sec * 5 + i * Math.PI / 2;
                matrices.push();
                matrices.translate(Math.cos(ang) * 0.6, ay + 1.15F + 0.06F * Math.sin(sec * 9 + i), Math.sin(ang) * 0.6);
                matrices.multiply(look);
                star(glow, matrices.peek().getPositionMatrix(), 0.2F, argb(a, i % 2 == 0 ? 0xFFE040 : lighten(fx.color, 0.3F)));
                matrices.pop();
            }
        }
        matrices.pop();
    }

    /** A tornado grows out of the ground, whirls the player up around it and throws them out of the top. */
    private static void tornado(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec) {
        float grow = easeOut(clamp(sec / 0.15F)) * (sec > 2.4F ? 1 - easeIn(clamp((sec - 2.4F) / 0.45F)) : 1);
        float height = 7 * grow;
        // the player
        if (sec < 2.6F) {
            float lift = clamp(sec / 2.0F);
            float y = 6 * (float) Math.pow(lift, 1.4);
            float r = Math.min(1, sec / 0.3F) * (funnel(y) + 0.25F);
            double ang = sec * 8;
            float s = 1;
            if (sec > 2.0F) {
                float k = (sec - 2.0F) / 0.6F;
                r += 10 * k;
                y += 4 * k;
                s = 1 - k;
            }
            org.joml.Vector3f off = twist(sec, y, height);
            matrices.push();
            matrices.translate(off.x + Math.cos(ang) * r, y, off.z + Math.sin(ang) * r);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sec * 300));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sec * 260));
            matrices.scale(s, s, s);
            matrices.translate(0, -0.94F, 0);
            figure(matrices, consumers, fx.skin, 120);
            matrices.pop();
        }
        // dirt and stones
        VertexConsumer vc = consumers.getBuffer(RenderLayers.entityCutoutNoCull(Cosmetics.WHITE));
        for (float[] b : fx.bits) {
            float y = (b[1] + sec * 0.3F) % 1 * height;
            double ang = b[0] + sec * b[2] * 5;
            float r = funnel(y) + 0.2F;
            org.joml.Vector3f off = twist(sec, y, height);
            float h = b[3] * grow / 2;
            int col = b[4] < 0.3F ? 0xFF5E8A3A : b[4] < 0.7F ? 0xFF6B4A2E : 0xFF7A7A7A;
            matrices.push();
            matrices.translate(off.x + Math.cos(ang) * r, y, off.z + Math.sin(ang) * r);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sec * 400 + b[0] * 50));
            ebox(matrices.peek(), vc, -h, -h, -h, h, h, h, col);
            matrices.pop();
        }
        // the funnel: see-through bands that wobble and spin
        if (height > 0.05F) {
            VertexConsumer air = consumers.getBuffer(RenderLayers.entityTranslucentEmissive(Cosmetics.WHITE));
            int bands = 14;
            int grey = blend(0xC8CED6, fx.color, 0.25F);
            for (int j = 0; j < bands; j++) {
                float y0 = j * height / bands;
                float y1 = y0 + height / bands * 0.8F;
                org.joml.Vector3f off = twist(sec, (y0 + y1) / 2, height);
                matrices.push();
                matrices.translate(off.x, 0, off.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sec * 600 + j * 25));
                frustum(matrices.peek(), air, y0, funnel(y0), y1, funnel(y1), 16, argb(0.16F + 0.08F * (j % 2), grey), 0, 0);
                frustum(matrices.peek(), air, y0, funnel(y0) * 0.7F, y1, funnel(y1) * 0.7F, 16, argb(0.12F, lighten(grey, 0.5F)), 0, 0);
                matrices.pop();
            }
            VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
            annulus(glow, matrices.peek().getPositionMatrix(), 0.04F, 0, 2.0F, argb(0.45F * grow, fx.color), argb(0, fx.color));
        }
    }

    /** funnel radius at height y */
    private static float funnel(float y) {
        return 0.35F + 0.28F * y;
    }

    /** how far the funnel leans at height y: it snakes a little */
    private static org.joml.Vector3f twist(float sec, float y, float height) {
        float k = 0.25F * y / Math.max(1, height);
        return new org.joml.Vector3f((float) Math.sin(sec * 2.5 + y * 0.6) * k, 0, (float) Math.cos(sec * 2.1 + y * 0.5) * k);
    }

    /** The player is frozen in a block of ice, it shivers and bursts into shards. */
    private static void freeze(MatrixStack matrices, VertexConsumerProvider consumers, Fx fx, float sec, Quaternionf look) {
        float crack = 1.15F;
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(fx.yaw));
        if (sec < crack) {
            float form = easeOut(clamp(sec / 0.12F));
            float shiver = sec > 0.85F ? (float) Math.sin(sec * 80) * 0.025F : 0;
            matrices.translate(shiver, 0, 0);
            figure(matrices, consumers, fx.skin, 35, 0xFF9CC8F0);
            VertexConsumer ice = consumers.getBuffer(RenderLayers.entityTranslucentEmissive(Cosmetics.WHITE));
            MatrixStack.Entry e = matrices.peek();
            float h = 2.05F * form;
            ebox(e, ice, -0.5F, 0, -0.38F, 0.5F, h, 0.38F, argb(0.42F, 0xA8D8FF));
            int edge = argb(0.75F, 0xE6F6FF);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    float ex = sx * 0.49F;
                    float ez = sz * 0.37F;
                    ebox(e, ice, ex - 0.03F, 0, ez - 0.03F, ex + 0.03F, h, ez + 0.03F, edge);
                }
            }
            ebox(e, ice, -0.52F, h - 0.04F, -0.4F, 0.52F, h + 0.02F, 0.4F, edge);
            // a glint sliding over the front
            float g = (sec * 1.6F) % 1;
            float gy = g * h;
            ebox(e, ice, -0.5F, gy, -0.4F, 0.5F, Math.min(h, gy + 0.12F), -0.385F, argb(0.5F * (1 - g), 0xFFFFFF));
        } else {
            float t = sec - crack;
            VertexConsumer vc = consumers.getBuffer(RenderLayers.entityCutoutNoCull(Cosmetics.WHITE));
            for (float[] b : fx.bits) {
                float size = b[6] * (t > 0.8F ? Math.max(0, 1 - (t - 0.8F) / 0.45F) : 1);
                if (size > 0.01F) {
                    int col = b[7] < 0.3F ? 0xFFFFFFFF : b[7] < 0.7F ? 0xFFCDEBFF : 0xFF8EC8F0;
                    flyingBox(matrices, vc, b, t, 18, size, col);
                }
            }
            if (t < 0.3F) {
                float k = t / 0.3F;
                VertexConsumer glow = consumers.getBuffer(RenderLayers.lightning());
                annulus(glow, matrices.peek().getPositionMatrix(), 0.04F, 0, 0.6F + 2.2F * easeOut(k),
                        argb(0.8F * (1 - k), 0xE6F6FF), argb(0, fx.color));
                matrices.push();
                matrices.translate(0, 1.0F, 0);
                matrices.multiply(look);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                annulus(glow, matrices.peek().getPositionMatrix(), 0, 0, 0.5F + 1.2F * k, argb(1 - k, 0xFFFFFF), argb(0, 0xA8D8FF));
                matrices.pop();
            }
        }
        matrices.pop();
    }

    /** a five-pointed star in the x-y plane (turn it to face the camera first) */
    private static void star(VertexConsumer b, Matrix4f m, float r, int c) {
        float ri = r * 0.45F;
        for (int i = 0; i < 5; i++) {
            double tip = -Math.PI / 2 + i * Math.PI * 2 / 5;
            double a0 = tip - Math.PI / 5;
            double a1 = tip + Math.PI / 5;
            quad(b, m, 0, 0, 0, c,
                    (float) Math.cos(a0) * ri, (float) -Math.sin(a0) * ri, 0, c,
                    (float) Math.cos(tip) * r, (float) -Math.sin(tip) * r, 0, c,
                    (float) Math.cos(a1) * ri, (float) -Math.sin(a1) * ri, 0, c);
        }
    }

    /**
     * The player who died, in their own skin: feet at the origin, facing -z, right side at +x.
     * arms = how far both arms are lifted out to the sides (degrees).
     */
    private static void figure(MatrixStack matrices, VertexConsumerProvider consumers, Skin skin, float arms) {
        figure(matrices, consumers, skin, arms, 0xFFFFFFFF);
    }

    /** the same, with the skin tinted by a colour (fully opaque) */
    private static void figure(MatrixStack matrices, VertexConsumerProvider consumers, Skin skin, float arms, int tint) {
        if (skin == null) {
            return;
        }
        figureParts(matrices, consumers.getBuffer(RenderLayers.entityCutoutNoCull(skin.texture())), skin, arms, tint);
    }

    private static void figureParts(MatrixStack matrices, VertexConsumer vc, Skin skin, float arms, int tint) {
        float p = 1 / 16F * 0.94F;
        int aw = skin.slim() ? 3 : 4;
        MatrixStack.Entry e = matrices.peek();
        // legs, body and head, each with its outer layer
        limb(e, vc, p, 0, 0, -2, 4, 12, 4, 0, 16, 0, 32, 0.25F, tint);
        limb(e, vc, p, -4, 0, -2, 4, 12, 4, 16, 48, 0, 48, 0.25F, tint);
        limb(e, vc, p, -4, 12, -2, 8, 12, 4, 16, 16, 16, 32, 0.25F, tint);
        limb(e, vc, p, -4, 24, -4, 8, 8, 8, 0, 0, 32, 0, 0.5F, tint);
        // arms, turned up and out at the shoulders
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side > 0;
            matrices.push();
            matrices.translate(side * (4 + aw / 2F) * p, 22 * p, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * arms));
            limb(matrices.peek(), vc, p, -aw / 2F, -10, -2, aw, 12, 4,
                    right ? 40 : 32, right ? 16 : 48, right ? 40 : 48, right ? 32 : 48, 0.25F, tint);
            matrices.pop();
        }
    }

    /** one body part from (x, y, z) with size w, h, d in skin pixels; its texture at (u, v), its outer layer at (ou, ov) */
    private static void limb(MatrixStack.Entry e, VertexConsumer vc, float p, float x, float y, float z, float w, float h, float d,
                             float u, float v, float ou, float ov, float grow, int tint) {
        skinBox(e, vc, x * p, y * p, z * p, (x + w) * p, (y + h) * p, (z + d) * p, u, v, w, h, d, tint);
        skinBox(e, vc, (x - grow) * p, (y - grow) * p, (z - grow) * p, (x + w + grow) * p, (y + h + grow) * p, (z + d + grow) * p,
                ou, ov, w, h, d, tint);
    }

    /** a box with the usual Minecraft skin layout: front at -z, the right side (+x) left of the front in the texture */
    private static void skinBox(MatrixStack.Entry e, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                                float u, float v, float w, float h, float d, int c) {
        tface(e, vc, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, u + d, v + d, u + d + w, v + d + h, c, 0, 0, -1);
        tface(e, vc, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h, c, 0, 0, 1);
        tface(e, vc, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, u, v + d, u + d, v + d + h, c, 1, 0, 0);
        tface(e, vc, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, u + d + w, v + d, u + 2 * d + w, v + d + h, c, -1, 0, 0);
        tface(e, vc, x1, y1, z1, x0, y1, z1, x0, y1, z0, x1, y1, z0, u + d, v, u + d + w, v + d, c, 0, 1, 0);
        tface(e, vc, x1, y0, z0, x0, y0, z0, x0, y0, z1, x1, y0, z1, u + d + w, v, u + d + 2 * w, v + d, c, 0, -1, 0);
    }

    /** a little cube that shows one skin pixel (u, v) on every side */
    private static void pixelBox(MatrixStack.Entry e, VertexConsumer vc, float x, float y, float z, float h, float u, float v, int c) {
        float u0 = u + 0.25F;
        float v0 = v + 0.25F;
        float u1 = u + 0.75F;
        float v1 = v + 0.75F;
        float x0 = x - h, x1 = x + h, y0 = y - h, y1 = y + h, z0 = z - h, z1 = z + h;
        tface(e, vc, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, u0, v0, u1, v1, c, 0, 0, -1);
        tface(e, vc, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, u0, v0, u1, v1, c, 0, 0, 1);
        tface(e, vc, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, u0, v0, u1, v1, c, 1, 0, 0);
        tface(e, vc, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, u0, v0, u1, v1, c, -1, 0, 0);
        tface(e, vc, x1, y1, z1, x0, y1, z1, x0, y1, z0, x1, y1, z0, u0, v0, u1, v1, c, 0, 1, 0);
        tface(e, vc, x1, y0, z0, x0, y0, z0, x0, y0, z1, x1, y0, z1, u0, v0, u1, v1, c, 0, -1, 0);
    }

    /** a skin face: corners top-left, top-right, bottom-right, bottom-left; texture rectangle in skin pixels */
    private static void tface(MatrixStack.Entry e, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                              float cx, float cy, float cz, float dx, float dy, float dz,
                              float u0, float v0, float u1, float v1, int c, float nx, float ny, float nz) {
        float k = 1 / 64F;
        ev(e, vc, ax, ay, az, u0 * k, v0 * k, c, nx, ny, nz);
        ev(e, vc, bx, by, bz, u1 * k, v0 * k, c, nx, ny, nz);
        ev(e, vc, cx, cy, cz, u1 * k, v1 * k, c, nx, ny, nz);
        ev(e, vc, dx, dy, dz, u0 * k, v1 * k, c, nx, ny, nz);
    }

    // ------------------------------------------------------------------ drawing helpers (entity layers: lit, solid)

    private static void ev(MatrixStack.Entry e, VertexConsumer vc, float x, float y, float z, float u, float v, int color,
                           float nx, float ny, float nz) {
        vc.vertex(e, x, y, z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(e, nx, ny, nz);
    }

    private static void eface(MatrixStack.Entry e, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                              float cx, float cy, float cz, float dx, float dy, float dz, int color, float nx, float ny, float nz) {
        ev(e, vc, ax, ay, az, 0, 0, color, nx, ny, nz);
        ev(e, vc, bx, by, bz, 1, 0, color, nx, ny, nz);
        ev(e, vc, cx, cy, cz, 1, 1, color, nx, ny, nz);
        ev(e, vc, dx, dy, dz, 0, 1, color, nx, ny, nz);
    }

    private static void ebox(MatrixStack.Entry e, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        eface(e, vc, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, c, 0, 0, -1);
        eface(e, vc, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, c, 0, 0, 1);
        eface(e, vc, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, c, -1, 0, 0);
        eface(e, vc, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, c, 1, 0, 0);
        eface(e, vc, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, c, 0, 1, 0);
        eface(e, vc, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, c, 0, -1, 0);
    }

    /** a round slice from (y0, radius r0) up to (y1, radius r1); a colour of 0 leaves that part out */
    private static void frustum(MatrixStack.Entry e, VertexConsumer vc, float y0, float r0, float y1, float r1, int seg,
                                int side, int top, int bottom) {
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float nx = (float) Math.cos((a0 + a1) / 2), nz = (float) Math.sin((a0 + a1) / 2);
            if (side != 0) {
                eface(e, vc, c0 * r0, y0, s0 * r0, c1 * r0, y0, s1 * r0, c1 * r1, y1, s1 * r1, c0 * r1, y1, s0 * r1, side, nx, 0, nz);
            }
            if (top != 0) {
                eface(e, vc, 0, y1, 0, 0, y1, 0, c1 * r1, y1, s1 * r1, c0 * r1, y1, s0 * r1, top, 0, 1, 0);
            }
            if (bottom != 0) {
                eface(e, vc, 0, y0, 0, 0, y0, 0, c1 * r0, y0, s1 * r0, c0 * r0, y0, s0 * r0, bottom, 0, -1, 0);
            }
        }
    }

    private static void sphere(MatrixStack.Entry e, VertexConsumer vc, float r, int color) {
        int rings = 10;
        int seg = 18;
        for (int j = 0; j < rings; j++) {
            double p0 = Math.PI * j / rings - Math.PI / 2;
            double p1 = Math.PI * (j + 1) / rings - Math.PI / 2;
            float y0 = (float) Math.sin(p0) * r, y1 = (float) Math.sin(p1) * r;
            float q0 = (float) Math.cos(p0) * r, q1 = (float) Math.cos(p1) * r;
            float ny = (float) Math.sin((p0 + p1) / 2);
            for (int i = 0; i < seg; i++) {
                double a0 = Math.PI * 2 * i / seg;
                double a1 = Math.PI * 2 * (i + 1) / seg;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                eface(e, vc, c0 * q0, y0, s0 * q0, c1 * q0, y0, s1 * q0, c1 * q1, y1, s1 * q1, c0 * q1, y1, s0 * q1,
                        color, c0, ny, s0);
            }
        }
    }

    // ------------------------------------------------------------------ drawing helpers (glow)

    /** a cone around the y axis from radius r0 on the ground to r1 at height h */
    private static void cone(VertexConsumer b, Matrix4f m, float r0, float r1, float h, int bottom, int top) {
        int seg = 24;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(b, m, c0 * r0, 0, s0 * r0, bottom, c1 * r0, 0, s1 * r0, bottom, c1 * r1, h, s1 * r1, top, c0 * r1, h, s0 * r1, top);
        }
    }

    /** a flat glowing disk from r1 to r2 with two spiral arms that curl outwards */
    private static void swirl(VertexConsumer b, Matrix4f m, float r1, float r2, int rgb) {
        int seg = 48;
        int bands = 6;
        for (int j = 0; j < bands; j++) {
            float fa = j / (float) bands, fb = (j + 1) / (float) bands;
            float ra = r1 + (r2 - r1) * fa, rb = r1 + (r2 - r1) * fb;
            for (int i = 0; i < seg; i++) {
                double a0 = Math.PI * 2 * i / seg;
                double a1 = Math.PI * 2 * (i + 1) / seg;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                quad(b, m, c0 * ra, 0, s0 * ra, swirlColor(a0, fa, rgb), c1 * ra, 0, s1 * ra, swirlColor(a1, fa, rgb),
                        c1 * rb, 0, s1 * rb, swirlColor(a1, fb, rgb), c0 * rb, 0, s0 * rb, swirlColor(a0, fb, rgb));
            }
        }
    }

    private static int swirlColor(double angle, float out, int rgb) {
        float arm = (float) (0.5 + 0.5 * Math.cos(2 * angle + out * 5));
        float alpha = (0.25F + 0.75F * arm) * (1 - out) * (1 - out);
        return argb(alpha, lighten(rgb, 0.7F * (1 - out)));
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

    private static float clamp(float t) {
        return Math.max(0, Math.min(1, t));
    }

    /** from colour a (k = 0) to colour b (k = 1) */
    private static int blend(int a, int b, float k) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    private static float easeIn(float t) {
        float k = Math.max(0, Math.min(1, t));
        return k * k;
    }
}

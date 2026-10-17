package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Cosmetics: capes, something on your head and an aura. Only on your own player and
 * only on your screen - nothing is sent anywhere, other players see your normal skin.
 */
public final class Cosmetics {

    /**
     * The pages of the menu. CAPE and MC_CAPE are two pages for the same thing on your back:
     * picking on one of them replaces the other.
     */
    public enum Slot {
        CAPE("Capes"), MC_CAPE("MC Capes"), HEAD("Head"), AURA("Aura");

        public final String title;

        Slot(String title) {
            this.title = title;
        }
    }

    /** { key, label } per slot, in menu order. "color" cosmetics follow the colour you pick. */
    public static final String[][] CAPES = {
            {"tornado_night", "Tornado Night"}, {"crimson", "Crimson"}, {"vortex", "Vortex"},
            {"daydream", "Daydream"}, {"snake_blade", "Snake Blade"},
            {"haunted_night", "Haunted Night"}, {"pumpkin_king", "Pumpkin King"}};
    /** Mojang's own capes: { key, label, texture hash }. Loaded from textures.minecraft.net when picked. */
    public static final String[][] MC_CAPES = {
            {"minecon2011", "MINECON 2011", "953cac8b779fe41383e675ee2b86071a71658f2180f56fbce8aa315ea70e2ed6"},
            {"minecon2012", "MINECON 2012", "a2e8d97ec79100e90a75d369d1b3ba81273c4f82bc1b737e934eed4a854be1b6"},
            {"minecon2013", "MINECON 2013", "153b1a0dfcbae953cdeb6f2c2bf6bf79943239b1372780da44bcbb29273131da"},
            {"minecon2015", "MINECON 2015", "b0cc08840700447322d953a02b965f1d65a13a603bf64b17c803c21446fe1635"},
            {"minecon2016", "MINECON 2016", "e7dfea16dc83c97df01a12fabbd1216359c0cd0ea42f9999b6e97c584963e980"},
            {"migrator", "Migrator", "2340c0e03dd24a11b15a8b33c2a7e9e32abb2051b2481d0ba7defd635ca7a933"},
            {"vanilla", "Vanilla", "f9a76537647989f9a0b6d001e320dac591c359e9e61a31f4ce11c88f207f0ad4"},
            {"cherry", "Cherry Blossom", "afd553b39358a24edfe3b8a9a939fa5fa4faa4d9a9c3d6af8eafb377fa05c2bb"},
            {"anniversary", "15th Anniversary", "cd9d82ab17fd92022dbd4a86cde4c382a7540e117fae7b9a2853658505a80625"},
            {"pan", "Pan", "28de4a81688ad18b49e735a273e086c18f1e3966956123ccb574034c06f5d336"},
            {"common", "Common", "5ec930cdd2629c8771655c60eebeb867b4b6559b0e6d3bc71c40c96347fa03f0"},
            {"purpleheart", "Purple Heart", "cb40a92e32b57fd732a00fc325e7afb00a7ca74936ad50d8e860152e482cfbde"},
            {"follower", "Follower's", "569b7f2a1d00d26f30efe3f9ab9ac817b1e6d35f4f3cfb0324ef2d328223d350"},
            {"mcc", "MCC 15th Year", "56c35628fe1c4d59dd52561a3d03bfa4e1a76d397c8b9c476c2f77cb6aebb1df"},
            {"founder", "Founder's", "99aba02ef05ec6aa4d42db8ee43796d6cd50e4b2954ab29f0caeb85f96bf52a1"},
            {"menace", "Menace", "dbc21e222528e30dc88445314f7be6ff12d3aeebc3c192054fba7e3b3f8c77b1"},
            {"home", "Home", "1de21419009db483900da6298a1e6cbf9f1bc1523a0dcdc16263fab150693edd"},
            {"yearn", "Yearn", "308b32a9e303155a0b4262f9e5483ad4a22e3412e84fe8385a0bdd73dc41fa89"},
            {"copper", "Copper", "5e6f3193e74cd16cdd6637d9bae5484e3a37ff2a14c2d157c659a07810b1bdca"},
            {"zombiehorse", "Zombie Horse", "a3f6e4f14801f3ea55e3d95b9b4ef3b5e8802d947f669de93d6ec4b9354a436b"},
            {"builder", "Builder", "2c579968c64c1719740fd8c2a451461879b238002574fce48f7d1a7c36a1c7d4"},
            {"experience", "Minecraft Experience", "7658c5025c77cfac7574aab3af94a46a8886e3b7722a895255fbf22ab8652434"},
    };
    /** saved keys of Mojang capes carry this prefix, so they share the one cape setting */
    public static final String MC = "mc_";

    public static final String[][] HEADS = {
            {"halo", "Halo"}, {"tophat", "Top Hat"}, {"pumpkin_head", "Pumpkin Head"}, {"witch_hat", "Witch Hat"}};
    public static final String[][] AURAS = {
            {"sparkles", "Sparkles"}, {"bats", "Bats"}, {"souls", "Souls"}};

    /** Hovering a card in the menu tries it on for a moment; null = show what is saved. */
    private static final Map<Slot, String> TRY_ON = new HashMap<>();

    private Cosmetics() {
    }

    private static String[][] merged = CAPES;
    private static String[][] mergedLocal;

    /** the built-in capes, with this PC's own animated capes (config/bauloo-styles/animated-capes) in front */
    private static String[][] withLocal() {
        String[][] local = LocalCapes.list();
        if (local != mergedLocal) {
            mergedLocal = local;
            String[][] all = new String[local.length + CAPES.length][];
            System.arraycopy(local, 0, all, 0, local.length);
            System.arraycopy(CAPES, 0, all, local.length, CAPES.length);
            merged = all;
        }
        return merged;
    }

    public static String[][] of(Slot slot) {
        return switch (slot) {
            case CAPE -> withLocal();
            case MC_CAPE -> MC_CAPES;
            case HEAD -> HEADS;
            case AURA -> AURAS;
        };
    }

    public static String saved(Slot slot) {
        StylesUiConfig.load();
        return switch (slot) {
            case CAPE -> StylesUiConfig.cosCape.startsWith(MC) ? "none" : StylesUiConfig.cosCape;
            case MC_CAPE -> StylesUiConfig.cosCape.startsWith(MC) ? StylesUiConfig.cosCape.substring(MC.length()) : "none";
            case HEAD -> StylesUiConfig.cosHead;
            case AURA -> StylesUiConfig.cosAura;
        };
    }

    public static void save(Slot slot, String key) {
        switch (slot) {
            case CAPE -> StylesUiConfig.cosCape = key;
            case MC_CAPE -> StylesUiConfig.cosCape = "none".equals(key) ? "none" : MC + key;
            case HEAD -> StylesUiConfig.cosHead = key;
            case AURA -> StylesUiConfig.cosAura = key;
        }
        StylesUiConfig.save();
    }

    /** The Bauloo cape this player wears on your screen, or null. */
    public static Identifier capeFor(net.minecraft.entity.PlayerLikeEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return null;
        }
        return player == mc.player ? currentCapeTexture() : PlayerCapes.texture(player.getName().getString());
    }

    /**
     * The skin textures with the Bauloo cape put in: your own cape on you, a cape bauloo gave
     * another player (PlayerCapes, admin screens only). Returns old when nothing changes.
     * Used by the render state (vanilla) and by getSkin() itself, so cape mods that read the
     * player directly (Wavey Capes) show it too.
     */
    public static net.minecraft.entity.player.SkinTextures withCape(net.minecraft.entity.PlayerLikeEntity player,
            net.minecraft.entity.player.SkinTextures old) {
        Identifier texture = old == null ? null : capeFor(player);
        if (texture == null) {
            return old;
        }
        boolean me = player == MinecraftClient.getInstance().player;
        if (old.cape() != null && texture.equals(old.cape().texturePath())
                && (!me || !StylesUiConfig.cosCapeElytra || old.elytra() == old.cape())) {
            return old;
        }
        net.minecraft.util.AssetInfo.TextureAsset cape = new net.minecraft.util.AssetInfo.TextureAssetInfo(texture, texture);
        return new net.minecraft.entity.player.SkinTextures(old.body(), cape,
                me && StylesUiConfig.cosCapeElytra ? cape : old.elytra(), old.model(), old.secure());
    }

    /** What the player wears right now: the tried-on one while hovering, else the saved one. */
    public static String current(Slot slot) {
        String trying = TRY_ON.get(slot);
        String key = trying != null ? trying : saved(slot);
        return valid(slot, key) ? key : "none";
    }

    /** The texture on your back right now (own or Mojang cape), or null for your normal one. */
    public static Identifier currentCapeTexture() {
        String own = TRY_ON.get(Slot.CAPE);
        String mc = TRY_ON.get(Slot.MC_CAPE);
        if (own == null && mc == null) {
            String saved = saved(Slot.CAPE);
            String savedMc = saved(Slot.MC_CAPE);
            if (valid(Slot.MC_CAPE, savedMc)) {
                return mcCape(savedMc);
            }
            return valid(Slot.CAPE, saved) ? capeTexture(saved) : null;
        }
        if (mc != null) {
            return valid(Slot.MC_CAPE, mc) ? mcCape(mc) : null;
        }
        return valid(Slot.CAPE, own) ? capeTexture(own) : null;
    }

    public static void tryOn(Slot slot, String key) {
        TRY_ON.clear();
        if (slot != null) {
            TRY_ON.put(slot, key);
        }
    }

    public static boolean valid(Slot slot, String key) {
        if (key == null) {
            return false;
        }
        for (String[] entry : of(slot)) {
            if (entry[0].equals(key)) {
                return true;
            }
        }
        return false;
    }

    public static String label(Slot slot, String key) {
        for (String[] entry : of(slot)) {
            if (entry[0].equals(key)) {
                return entry[1];
            }
        }
        return "None";
    }

    /** The colour for tinted cosmetics: the picked one, or a slow rainbow. */
    public static int color() {
        StylesUiConfig.load();
        if (StylesUiConfig.cosRainbow) {
            float hue = (System.currentTimeMillis() % 6000L) / 6000.0F;
            return StylesTheme.hsv(hue, 0.55F, 1.0F) & 0xFFFFFF;
        }
        return StylesUiConfig.cosColor & 0xFFFFFF;
    }

    public static Identifier icon(Slot slot, String key) {
        if (slot == Slot.MC_CAPE) {
            return mcCape(key);
        }
        String prefix = switch (slot) {
            case CAPE, MC_CAPE -> "cape_";
            case HEAD -> "head_";
            case AURA -> "aura_";
        };
        if (slot == Slot.CAPE && "color".equals(key)) {
            return colorCape();
        }
        if (slot == Slot.CAPE && BuiltInCapes.has(key)) {
            Identifier anim = BuiltInCapes.icon(key);
            return anim != null ? anim : WHITE;
        }
        if (slot == Slot.CAPE && key.startsWith(LocalCapes.PREFIX)) {
            Identifier local = LocalCapes.icon(key);
            return local != null ? local : WHITE;
        }
        return Identifier.of(BaulooStylesClient.MOD_ID, "textures/cosmetics/icon/" + prefix + key + ".png");
    }

    public static final Identifier WHITE = Identifier.of(BaulooStylesClient.MOD_ID, "textures/cosmetics/white.png");

    /** The cape texture for a key, or null for none. */
    public static Identifier capeTexture(String key) {
        if ("none".equals(key)) {
            return null;
        }
        if ("color".equals(key)) {
            return colorCape();
        }
        if (BuiltInCapes.has(key)) {
            try {
                Identifier built = BuiltInCapes.current(key);
                if (built != null) {
                    return built;
                }
            } catch (Throwable t) {
                BaulooStylesClient.LOGGER.warn("Cape {} failed: {}", key, t.toString());
            }
            return Identifier.of(BaulooStylesClient.MOD_ID, "textures/cosmetics/cape/bauloo.png");
        }
        if (key.startsWith(LocalCapes.PREFIX)) {
            try {
                Identifier local = LocalCapes.current(key);
                if (local != null) {
                    return local;
                }
            } catch (Throwable t) {
                BaulooStylesClient.LOGGER.warn("Local cape {} failed: {}", key, t.toString());
            }
            return Identifier.of(BaulooStylesClient.MOD_ID, "textures/cosmetics/cape/bauloo.png");
        }
        return Identifier.of(BaulooStylesClient.MOD_ID, "textures/cosmetics/cape/" + key + ".png");
    }

    // ---------------------------------------------------------------- Mojang capes

    private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
    private static final Map<String, Identifier> MC_READY = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> MC_LOADING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** texture width per key; Mojang capes are 64x32 or bigger, the menu needs the scale */
    public static final Map<String, Integer> MC_WIDTH = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A Mojang cape: from the disk cache, else downloaded once from textures.minecraft.net
     * (the same place the game loads everybody's capes from). Null while it is on its way.
     */
    public static Identifier mcCape(String key) {
        Identifier ready = MC_READY.get(key);
        if (ready != null || !MC_LOADING.add(key)) {
            return ready;
        }
        String hash = null;
        for (String[] entry : MC_CAPES) {
            if (entry[0].equals(key)) {
                hash = entry[2];
            }
        }
        if (hash == null) {
            return null;
        }
        final String h = hash;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                        .resolve("bauloo-styles/capes/" + h + ".png");
                byte[] png;
                if (java.nio.file.Files.exists(file)) {
                    png = java.nio.file.Files.readAllBytes(file);
                } else {
                    var request = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create("https://textures.minecraft.net/texture/" + h))
                            .timeout(java.time.Duration.ofSeconds(20)).GET().build();
                    var response = HTTP.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() != 200 || response.body().length > 2_000_000) {
                        MC_LOADING.remove(key);
                        return;
                    }
                    png = response.body();
                    java.nio.file.Files.createDirectories(file.getParent());
                    java.nio.file.Files.write(file, png);
                }
                MinecraftClient.getInstance().send(() -> {
                    try {
                        NativeImage image = NativeImage.read(png);
                        Identifier id = Identifier.of(BaulooStylesClient.MOD_ID, "cosmetics/mc_" + key);
                        MC_WIDTH.put(key, image.getWidth());
                        MinecraftClient.getInstance().getTextureManager()
                                .registerTexture(id, new NativeImageBackedTexture(id::toString, image));
                        MC_READY.put(key, id);
                    } catch (java.io.IOException e) {
                        MC_LOADING.remove(key);
                    }
                });
            } catch (Exception e) {
                MC_LOADING.remove(key);
            }
        });
        return null;
    }

    // ---------------------------------------------------------------- the "Your Color" cape

    private static int colorCapeRgb = -1;
    private static final Identifier COLOR_CAPE = Identifier.of(BaulooStylesClient.MOD_ID, "cosmetics/color_cape");

    /** A cape painted at runtime from your colour: light at the top, deep at the bottom, a thin line. */
    private static Identifier colorCape() {
        int rgb = StylesUiConfig.cosColor & 0xFFFFFF;
        if (rgb != colorCapeRgb) {
            colorCapeRgb = rgb;
            NativeImage img = new NativeImage(128, 64, true);
            int light = StylesTheme.mix(0xFF000000 | rgb, 0xFFFFFFFF, 0.25F);
            int deep = StylesTheme.mix(0xFF000000 | rgb, 0xFF000000, 0.6F);
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 128; x++) {
                    // the face runs from y 2 to 34, the elytra part from 0 to 44
                    float t = x >= 44 ? y / 44.0F : Math.max(0, Math.min(1, (y - 2) / 32.0F));
                    img.setColorArgb(x, y, 0xFF000000 | StylesTheme.mix(light, deep, t));
                }
            }
            for (int x = 2; x < 22; x++) {
                img.setColorArgb(x, 26, 0xFFFFFFFF);
            }
            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(COLOR_CAPE, new NativeImageBackedTexture(COLOR_CAPE::toString, img));
        }
        return COLOR_CAPE;
    }
}

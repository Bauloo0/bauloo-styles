package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Player Outline: a neon glow around players, in your own colour.
 * Other players only get it while you can actually see them, so it never
 * shows anyone through walls. Invisible players with armor get it around the armor. Settings live in StylesUiConfig.
 */
public final class PlayerOutline {

    private PlayerOutline() {
    }

    /** While a colour or thickness slider is dragged you always see your own outline. */
    public static volatile boolean previewSelf;

    /** Share of the colour bar taken by the white block (left) and the black block (right). */
    private static final float EDGE = 0.08F;

    /** True when this entity should get the outline. Server glowing stays vanilla. */
    public static boolean outlines(Entity entity) {
        if (previewSelf && entity == MinecraftClient.getInstance().player) {
            return true;
        }
        StylesUiConfig.load();
        if (!StylesUiConfig.outline || !(entity instanceof PlayerEntity) || entity.isGlowing()) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return false;
        }
        if (entity == client.player) {
            return StylesUiConfig.outlineSelf;
        }
        if (entity.isInvisible() && !wearsArmor((PlayerEntity) entity)) {
            return false; // nothing to outline
        }
        return StylesUiConfig.outlineOthers && canSee(client, entity);
    }

    /** entity id -> {world tick, 1 = visible} */
    private static final java.util.Map<Integer, long[]> SIGHT = new java.util.HashMap<>();

    /**
     * canSee is a ray through the world and the renderer asks several times per frame for every
     * player; once per tick per player is plenty.
     */
    private static boolean canSee(MinecraftClient client, Entity entity) {
        long tick = client.world == null ? 0 : client.world.getTime();
        long[] cached = SIGHT.get(entity.getId());
        if (cached != null && cached[0] == tick) {
            return cached[1] == 1;
        }
        if (SIGHT.size() > 256) {
            SIGHT.clear();
        }
        boolean visible = client.player.canSee(entity);
        SIGHT.put(entity.getId(), new long[]{tick, visible ? 1 : 0});
        return visible;
    }

    /** Invisible players still show their armor; the outline then goes around the armor. */
    private static boolean wearsArmor(PlayerEntity player) {
        for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{
                net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}) {
            if (!player.getEquippedStack(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Colour at a spot of the colour bar: white, then the rainbow, then black. */
    public static int sliderColor(float t) {
        if (t < EDGE) {
            return 0xFFFFFF;
        }
        if (t > 1.0F - EDGE) {
            return 0x000000;
        }
        return StylesTheme.hsv((t - EDGE) / (1.0F - 2 * EDGE), 0.65F, 1.0F);
    }

    /** Where a colour sits on the colour bar, for the knob. */
    public static float sliderPos(int rgb) {
        rgb &= 0xFFFFFF;
        float[] hsv = StylesTheme.toHsv(rgb);
        if (hsv[2] < 0.15F) {
            return 1.0F - EDGE / 2;
        }
        if (hsv[1] < 0.15F) {
            return EDGE / 2;
        }
        return EDGE + hsv[0] * (1.0F - 2 * EDGE);
    }

    /**
     * The "Neon Glow" post effect for the chosen thickness (a soft halo outside the player plus a
     * thin bright edge); with Player Outline off the vanilla glow stays.
     */
    public static net.minecraft.util.Identifier postEffect(net.minecraft.util.Identifier vanilla) {
        StylesUiConfig.load();
        if (!StylesUiConfig.outline && !previewSelf) {
            return vanilla;
        }
        int width = Math.max(1, Math.min(6, StylesUiConfig.outlineWidth));
        return net.minecraft.util.Identifier.of(BaulooStylesClient.MOD_ID, "neon_" + width);
    }

    /** Outline colour as 0xRRGGBB. */
    public static int color() {
        if (StylesUiConfig.outlineRainbow) {
            float hue = (System.currentTimeMillis() % 4000L) / 4000.0F;
            return java.awt.Color.HSBtoRGB(hue, 0.7F, 1.0F) & 0xFFFFFF;
        }
        return StylesUiConfig.outlineColor & 0xFFFFFF;
    }
}

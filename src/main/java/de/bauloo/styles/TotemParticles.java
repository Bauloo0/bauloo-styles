package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/**
 * Totem Pop extra: the particle cloud when a totem pops, in your own look. Only changes what you
 * see on your screen (amount, size, colour, how long it stays, glitter or pixel plus signs).
 */
public final class TotemParticles {

    /** textures/particle/totem_plus.png - the particle atlas picks up every texture in that folder */
    private static final SpriteIdentifier PLUS = new SpriteIdentifier(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE,
            Identifier.of(BaulooStylesClient.MOD_ID, "totem_plus"));
    private static final float[] KEEP = {0.0F, 0.25F, 0.5F, 1.0F};

    private TotemParticles() {
    }

    /** the Totem Pop switch; off = everything vanilla */
    public static boolean on() {
        StylesUiConfig.load();
        return StylesUiConfig.totemPart;
    }

    /** false = this particle is left out (Amount: Off / Less / Mid) */
    public static boolean keep() {
        return !on() || java.util.concurrent.ThreadLocalRandom.current().nextFloat()
                < KEEP[Math.max(0, Math.min(3, StylesUiConfig.totemPartAmount))];
    }

    public static boolean plus() {
        return on() && StylesUiConfig.totemPartStyle == 1;
    }

    public static Sprite plusSprite() {
        return MinecraftClient.getInstance().getAtlasManager().getSprite(PLUS);
    }

    public static float sizeFactor() {
        return StylesUiConfig.totemPartSize / 100.0F;
    }

    public static float lifeFactor() {
        return StylesUiConfig.totemPartShort ? 0.5F : 1.0F;
    }

    /** the colour for one particle as 0xRRGGBB, or -1 to keep vanilla's green and yellow */
    public static int color(Random random) {
        if (StylesUiConfig.totemPartRainbow) {
            return StylesTheme.hsv(random.nextFloat(), 0.75F, 1.0F) & 0xFFFFFF;
        }
        if (!StylesUiConfig.totemPartCustom) {
            return -1;
        }
        // a little brightness spread, so the cloud does not look flat
        float shade = 0.75F + random.nextFloat() * 0.25F;
        int rgb = StylesUiConfig.totemPartColor;
        int r = Math.round(((rgb >> 16) & 0xFF) * shade);
        int g = Math.round(((rgb >> 8) & 0xFF) * shade);
        int b = Math.round((rgb & 0xFF) * shade);
        return r << 16 | g << 8 | b;
    }
}

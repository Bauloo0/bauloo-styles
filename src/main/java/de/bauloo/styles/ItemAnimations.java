package de.bauloo.styles;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads Sodium Extra's "Animations" switch (without depending on Sodium Extra). When it is off, no
 * texture animates at all - ItemAnimationMixin then still animates the item textures.
 */
public final class ItemAnimations {

    private static final boolean SODIUM_EXTRA = FabricLoader.getInstance().isModLoaded("sodium-extra");
    private static Method options;
    private static Field settings;
    private static Field animation;
    private static boolean broken;

    private ItemAnimations() {
    }

    /** true when Sodium Extra is there and has every animation switched off */
    public static boolean sodiumExtraBlocksThem() {
        if (!SODIUM_EXTRA || broken) {
            return false;
        }
        try {
            if (options == null) {
                Class<?> mod = Class.forName("me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod");
                options = mod.getMethod("options");
            }
            Object o = options.invoke(null);
            if (settings == null) {
                settings = o.getClass().getField("animationSettings");
            }
            Object s = settings.get(o);
            if (animation == null) {
                animation = s.getClass().getField("animation");
            }
            return !animation.getBoolean(s);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // another Sodium Extra version: leave animations to it
            broken = true;
            BaulooStylesClient.LOGGER.info("[Styles] Sodium Extra animation switch not readable: {}", e.toString());
            return false;
        }
    }
}

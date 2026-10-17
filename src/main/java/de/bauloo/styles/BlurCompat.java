package de.bauloo.styles;

import java.lang.reflect.Field;

/**
 * "Clear background" also has to reach the Blur+ mod: it blurs inventories on its own, switched by
 * its static setting BlurConfig.blurContainers. While Clear background is on, that setting is off in
 * memory; turning Clear background off puts back what the player had. Blur+'s own config file is
 * never written. Without Blur+ this does nothing.
 */
public final class BlurCompat {

    private static Field field;
    private static boolean looked;
    private static Boolean original;

    private BlurCompat() {
    }

    public static void apply() {
        Field f = field();
        if (f == null) {
            return;
        }
        try {
            if (StylesUiConfig.invClearBackground) {
                if (original == null) {
                    original = f.getBoolean(null);
                }
                f.setBoolean(null, false);
            } else if (original != null) {
                f.setBoolean(null, original);
                original = null;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            BaulooStylesClient.LOGGER.warn("Could not switch Blur+ container blur: {}", e.toString());
        }
    }

    private static Field field() {
        if (!looked) {
            looked = true;
            try {
                field = Class.forName("eu.midnightdust.blur.config.BlurConfig").getField("blurContainers");
            } catch (ReflectiveOperationException | RuntimeException e) {
                field = null;
            }
        }
        return field;
    }
}

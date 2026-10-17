package de.bauloo.styles;

/**
 * Crystal Spin keeps the crystal's real animation age next to the sped-up one,
 * so the up-and-down bob can use the real age while only the turning changes.
 */
public interface CrystalAge {

    float baulooStyles$realAge();

    void baulooStyles$setRealAge(float age);

    /** real age minus the age that was handed to the renderer; added back for the bob */
    final class Offset {
        public static volatile float current;

        private Offset() {
        }
    }

    static float factor() {
        StylesUiConfig.load();
        return Math.max(0.001F, StylesUiConfig.crystalSpin / 100.0F);
    }
}

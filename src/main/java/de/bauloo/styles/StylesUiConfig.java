package de.bauloo.styles;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Look of the Style Archive and the favorites. Kept in
 * config/bauloo-styles-ui.properties, next to the hotkey file.
 */
public final class StylesUiConfig {

    public static final int DEFAULT_ACCENT = 0x75D4FF;
    public static final StylesTheme.Background DEFAULT_BACKGROUND = StylesTheme.Background.GLASS;
    public static final int DEFAULT_OPACITY = 95;

    public static int accent = DEFAULT_ACCENT;
    public static StylesTheme.Background background = DEFAULT_BACKGROUND;
    public static int opacity = DEFAULT_OPACITY;
    public static boolean glow = true;
    public static boolean brackets = true;
    public static boolean hints = true;
    /** true = the right column shows THEMES instead of INSPECTOR */
    public static boolean themesTab;

    /** Player Outline: the glow outline around players, in its own colour. */
    public static boolean outline = false;
    public static boolean outlineSelf = true;
    public static boolean outlineOthers = false;
    public static boolean outlineRainbow = false;
    public static int outlineColor = 0xFF5AC8;
    /** Glow thickness 1-6; 2 is the vanilla glow. */
    public static int outlineWidth = 2;
    /** Shield in your own hand: 100 = normal, lower = see-through. */
    public static int shieldOpacity = 100;
    /** Size of the totem that pops up on screen, in percent of vanilla. */
    public static int totemSize = 100;
    /** Size of the shield in your hand (first person), in percent. */
    public static int shieldSize = 100;
    /** Size of the golden apples (gaps) in your hand (first person), in percent. */
    public static int gapSize = 100;
    /** How fast end crystals spin, in percent of vanilla; 0 = still. */
    public static int crystalSpin = 100;
    /** Size of the totem that pops up on screen when one is used, in percent. */
    public static int totemPopSize = 100;
    /** Totem Pop switch: off = the vanilla cloud, the settings below stay saved */
    public static boolean totemPart = true;
    /** Sky effects (Extras): each one on its own */
    public static boolean skyShooting = true;
    /** Server Packs (Extras): styles above a server resource pack, and the banner when one covers them */
    public static boolean stylesOverServer = true;
    public static boolean serverPackWarning = true;
    public static boolean skyShower = false;
    public static boolean skyAurora = false;
    public static boolean skyMilky = false;
    public static boolean skyTwinkle = false;
    public static boolean skyComet = false;
    public static boolean skyConstellations = false;
    public static boolean skyBlood = false;
    /** Totem Pop: the particle cloud when a totem pops. 0 = vanilla glitter, 1 = pixel plus signs */
    public static int totemPartStyle = 0;
    /** how many particles: 0 off, 1 less (25%), 2 mid (50%), 3 normal */
    public static int totemPartAmount = 3;
    /** particle size in percent */
    public static int totemPartSize = 100;
    public static boolean totemPartCustom = false;
    public static int totemPartColor = 0x6CFF8A;
    public static boolean totemPartRainbow = false;
    /** the cloud vanishes about twice as fast */
    public static boolean totemPartShort = false;
    /** Hitbox (F3+B) outline in its own colour instead of white. */
    public static boolean hitboxCustom = false;
    public static int hitboxColor = 0xB388FF;
    /** Crosshair in its own colour instead of the inverted white one. */
    public static boolean crosshairCustom = false;
    public static int crosshairColor = 0xB388FF;
    /** Block Outline: solid lines around the targeted block, own colour and thickness. */
    public static boolean blockOutlineCustom = false;
    /** Kill Effect: off, lightning, sparks, blood, souls, totem. */
    public static String killEffect = "off";
    /** what plays where your mace smash lands (MaceEffect), "off" = nothing */
    public static String maceEffect = "off";
    public static int maceColor = 0xFF8A2A;
    public static boolean killOnlyMine = true;
    /** a short sound when you get a kill */
    public static boolean killSound = false;
    /** kill sound volume in percent, 5..100 */
    public static int killVolume = 100;
    /** the Cosmetics page was opened once: its NEW badge is gone */
    public static boolean cosmeticsSeen = false;
    /** crystal and anchor explosions make no sound */
    public static boolean noExplosionSound = false;
    public static int killColor = 0xB388FF;
    public static int blockOutlineColor = 0x000000;
    public static int blockOutlineWidth = 3;
    /** Crosshair fades from crosshairColor (left) to crosshairColor2 (right). */
    public static boolean crosshairGradient = false;
    public static int crosshairColor2 = 0xFF5AC8;
    /** 100 = even fade; higher = both colours stay pure longer and meet in a sharp middle. */
    public static int gradientStrength = 100;
    /** The outline around crystals while the Invisible crystal style is on; white by default. */
    public static boolean crystalBoxCustom = false;
    public static int crystalBoxColor = 0xB388FF;
    /** The glowing charge dots on respawn anchors in their own colour. */
    public static boolean anchorGlowCustom = false;
    public static int anchorGlowColor = 0xB388FF;
    /** Any charged anchor (1-3 glowstone) looks fully charged, like the Manos anchor. */
    public static boolean anchorShowFull = false;
    /** The bright (purple) pixels of the anchor really glow in the dark. */
    public static boolean anchorPixelGlow = false;
    /** Crystal core (inner cube) in its own colour. */
    public static boolean coreCustom = false;
    public static int coreColor = 0xFF5AC8;
    /** The damage flash on entities in its own colour, strength and optional rainbow. */
    public static boolean hitCustom = false;
    public static int hitColor = 0xB388FF;
    public static int hitStrength = 55;
    /** Armor flashes with the hit colour too (vanilla never flashes armor). */
    public static boolean hitArmor = true;
    /** Enchant glint in its own colour, a fade between two colours, or a rainbow. */
    public static boolean glintCustom = false;
    public static int glintColor = 0xB388FF;
    public static boolean glintGradient = false;
    public static int glintColor2 = 0x75D4FF;
    public static boolean glintRainbow = false;
    public static boolean hitRainbow = false;
    /** Fire on your screen: 100 = vanilla height, lower = pushed down so you see more. */
    public static int fireHeight = 100;
    /** Spotify: shows the song the Spotify app plays in the top left corner. */
    public static boolean spotifyHud = false;
    /** Spotify: only for a few seconds when the song changes. */
    public static boolean spotifyPopup = false;
    /** Watermark: "bauloo styles" in the corner while the inventory is open. */
    public static boolean watermark = true;
    /** Watermark look: 1 = logo with glow, 2 = badge. */
    public static int watermarkStyle = 1;
    /** Social badge on the recipe book button: off, youtube, tiktok, twitch, instagram, x, kick. */
    public static String socialLogo = "off";
    public static String socialName = "";
    /** Spotify card position as a share of the free screen space; -1 = top left corner. */
    public static float spotifyX = -1;
    public static float spotifyY = -1;
    /** size of the music card, 0.5 to 1.5 */
    public static float spotifyScale = 1.0F;
    /** Fire (screen, burning mobs and fire blocks) in its own colour. */
    public static boolean fireCustom = false;
    public static int fireColor = 0x75D4FF;
    /** Custom Fog: no fog, fog distance in percent (100 = normal), own fog colour */
    public static boolean fogOff = false;
    /** VIP early access: early styles and collections from the cloud show */
    public static boolean earlyAccess = true;
    /** Inventory tab: inventory and chests open without the dark veil and blur behind them */
    public static boolean invClearBackground = false;
    /** a white box around every cobweb near you (switch in the Cobwebs tab) */
    public static boolean cobwebOutline = false;
    /** time changer: your own time of day on your screen (ticks), -1 = the server's */
    public static int timeOverride = -1;
    /** thickness of the cobweb frame in quarter texture pixels: 1 = 0.25 px ... 16 = 4 px */
    public static int cobwebOutlineWidth = 2;
    /** tab icon: tell others (tornado in tab) that you play with Bauloo Styles */
    public static boolean showInTab = true;
    /** the first-time tour of the menu was seen (or skipped) */
    public static boolean tourSeen;
    /** Halloween 2026: the Halloween menu theme was switched on once for everyone (they can switch back) */
    public static boolean halloween2026;
    /** "World" menu theme: you from the front, the menu on the right, two blocks of the collection beside you */
    public static boolean worldMenu;
    /** the new version the update popup was already answered for, so it asks once per version */
    public static String updateSeen = "";
    /** the Bauloo Styles version that last ran on this PC (for "What's new") */
    public static String lastVersion = "";
    /** fingerprint of the last "What's new" notes shown, so the same notes never show twice */
    public static String lastNotes = "";
    private static boolean hadFile;
    /** Cosmetics (only on your own player): key per slot or "none", their colour, cape on elytra */
    public static String cosCape = "none";
    public static String cosHead = "none";
    public static String cosAura = "none";
    public static String cosPet = "none";
    public static int cosColor = 0xB388FF;
    public static boolean cosRainbow = false;
    public static boolean cosCapeElytra = true;
    /** recolourable capes follow the cosmetics colour */
    public static boolean cosCapeTint = false;
    public static int fogDistance = 100;
    public static boolean fogCustom = false;
    public static int fogColor = 0xB388FF;
    /** Grass color: grass, tall grass and ferns in one colour instead of the biome colour */
    public static boolean grassCustom = false;
    /** off (default): clicks only mark styles and one APPLY reloads everything at once */
    public static boolean applyInstantly = false;
    public static int grassColor = 0x91BD59;
    /** Shield (wood and rim) in its own colour. */
    public static boolean shieldCustom = false;
    public static int shieldColor = 0xB388FF;
    /** What a preset with saved Extras does: ask, with (always apply them) or without (never). */
    public static String presetExtras = "with";
    /** Back up your own Extras before a preset replaces them. */
    public static boolean presetBackup = true;

    /**
     * Settings that belong to you, never part of a preset's Extras: the menu look, where your
     * Spotify card sits and how big it is, and your cosmetics. A preset used to carry the card's
     * position, so using one moved the card back.
     */
    private static final Set<String> MENU_KEYS = Set.of("accent", "background", "opacity", "glow", "brackets",
            "hints", "themesTab", "favorites", "presetExtras", "presetBackup",
            "spotifyX", "spotifyY", "spotifyScale", "earlyAccess", "timeOverride", "updateSeen", "lastVersion", "invClearBackground", "showInTab", "tourSeen", "halloween2026", "worldMenu",
            "cosCape", "cosHead", "cosAura", "cosPet", "cosColor", "cosRainbow", "cosCapeElytra", "cosCapeTint",
            // how big the totem pops is your own feel: most presets only carry the default 100
            "totemPopSize");

    private static final Set<String> FAVORITES = new LinkedHashSet<>();
    private static boolean loaded;
    private static StylesTheme cached;
    private static String cachedKey = "";

    private StylesUiConfig() {
    }

    public static StylesTheme theme() {
        load();
        String key = accent + ":" + background + ":" + opacity;
        if (cached == null || !key.equals(cachedKey)) {
            cached = StylesTheme.of(accent, background, opacity);
            cachedKey = key;
        }
        return cached;
    }

    public static boolean isFavorite(String folder) {
        load();
        return folder != null && FAVORITES.contains(folder);
    }

    public static void toggleFavorite(String folder) {
        load();
        if (folder == null) {
            return;
        }
        if (!FAVORITES.remove(folder)) {
            FAVORITES.add(folder);
        }
        save();
    }

    public static void resetTheme() {
        accent = DEFAULT_ACCENT;
        background = DEFAULT_BACKGROUND;
        opacity = DEFAULT_OPACITY;
        glow = true;
        brackets = true;
        hints = true;
        save();
    }

    // ---------------------------------------------------------------- storage

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles-ui.properties");
    }

    /** true when a settings file existed before this start, i.e. not a brand-new install */
    public static boolean hadFile() {
        load();
        return hadFile;
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        loadFile();
        halloweenOnce();
    }

    /** Around Halloween 2026 everyone gets the Halloween menu theme once; picking another theme after that sticks. */
    private static void halloweenOnce() {
        if (halloween2026) {
            return;
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        if (today.isBefore(java.time.LocalDate.of(2026, 10, 25)) || today.isAfter(java.time.LocalDate.of(2026, 11, 3))) {
            return;
        }
        halloween2026 = true;
        for (StylesTheme.Preset preset : StylesTheme.PRESETS) {
            if (preset.background() == StylesTheme.Background.HALLOWEEN) {
                accent = preset.accent();
                background = preset.background();
            }
        }
        save();
    }

    private static void loadFile() {
        Path f = file();
        if (!Files.exists(f)) {
            return;
        }
        hadFile = true;
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(f)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        try {
            accent = Integer.parseInt(p.getProperty("accent", Integer.toHexString(DEFAULT_ACCENT)), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            accent = DEFAULT_ACCENT;
        }
        try {
            background = StylesTheme.Background.valueOf(p.getProperty("background", DEFAULT_BACKGROUND.name()));
        } catch (IllegalArgumentException ignored) {
            background = DEFAULT_BACKGROUND;
        }
        if (background == StylesTheme.Background.NAVY) {
            // the old Ice look is gone (1.5.3): it is the neutral Glass now
            background = StylesTheme.Background.GLASS;
        }
        try {
            opacity = Math.max(60, Math.min(100, Integer.parseInt(p.getProperty("opacity", "" + DEFAULT_OPACITY))));
        } catch (NumberFormatException ignored) {
            opacity = DEFAULT_OPACITY;
        }
        glow = Boolean.parseBoolean(p.getProperty("glow", "true"));
        brackets = Boolean.parseBoolean(p.getProperty("brackets", "true"));
        hints = Boolean.parseBoolean(p.getProperty("hints", "true"));
        themesTab = Boolean.parseBoolean(p.getProperty("themesTab", "false"));
        outline = Boolean.parseBoolean(p.getProperty("outline", "false"));
        outlineSelf = Boolean.parseBoolean(p.getProperty("outlineSelf", "true"));
        outlineOthers = Boolean.parseBoolean(p.getProperty("outlineOthers", "false"));
        outlineRainbow = Boolean.parseBoolean(p.getProperty("outlineRainbow", "false"));
        try {
            outlineWidth = Math.max(1, Math.min(6, Integer.parseInt(p.getProperty("outlineWidth", "2"))));
        } catch (NumberFormatException e) {
            outlineWidth = 2;
        }
        try {
            outlineColor = Integer.parseInt(p.getProperty("outlineColor", "FF5AC8"), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            outlineColor = 0xFF5AC8;
        }
        try {
            shieldOpacity = Math.max(15, Math.min(100, Integer.parseInt(p.getProperty("shieldOpacity", "100"))));
        } catch (NumberFormatException ignored) {
            shieldOpacity = 100;
        }
        try {
            totemSize = Math.max(20, Math.min(150, Integer.parseInt(p.getProperty("totemSize", "100"))));
        } catch (NumberFormatException ignored) {
            totemSize = 100;
        }
        try {
            shieldSize = Math.max(20, Math.min(150, Integer.parseInt(p.getProperty("shieldSize", "100"))));
        } catch (NumberFormatException ignored) {
            shieldSize = 100;
        }
        try {
            gapSize = Math.max(20, Math.min(150, Integer.parseInt(p.getProperty("gapSize", "100"))));
        } catch (NumberFormatException ignored) {
            gapSize = 100;
        }
        try {
            crystalSpin = Math.max(0, Math.min(1000, Integer.parseInt(p.getProperty("crystalSpin", "100"))));
        } catch (NumberFormatException ignored) {
            crystalSpin = 100;
        }
        try {
            totemPopSize = Math.max(20, Math.min(150, Integer.parseInt(p.getProperty("totemPopSize", "100"))));
        } catch (NumberFormatException ignored) {
            totemPopSize = 100;
        }
        totemPart = Boolean.parseBoolean(p.getProperty("totemPart", "true"));
        skyShooting = Boolean.parseBoolean(p.getProperty("skyShooting", "true"));
        stylesOverServer = Boolean.parseBoolean(p.getProperty("stylesOverServer", "true"));
        serverPackWarning = Boolean.parseBoolean(p.getProperty("serverPackWarning", "true"));
        skyShower = Boolean.parseBoolean(p.getProperty("skyShower", "false"));
        skyAurora = Boolean.parseBoolean(p.getProperty("skyAurora", "false"));
        skyMilky = Boolean.parseBoolean(p.getProperty("skyMilky", "false"));
        skyTwinkle = Boolean.parseBoolean(p.getProperty("skyTwinkle", "false"));
        skyComet = Boolean.parseBoolean(p.getProperty("skyComet", "false"));
        skyConstellations = Boolean.parseBoolean(p.getProperty("skyConstellations", "false"));
        skyBlood = Boolean.parseBoolean(p.getProperty("skyBlood", "false"));
        totemPartCustom = Boolean.parseBoolean(p.getProperty("totemPartCustom", "false"));
        totemPartRainbow = Boolean.parseBoolean(p.getProperty("totemPartRainbow", "false"));
        totemPartShort = Boolean.parseBoolean(p.getProperty("totemPartShort", "false"));
        try {
            totemPartStyle = Math.max(0, Math.min(1, Integer.parseInt(p.getProperty("totemPartStyle", "0"))));
            totemPartAmount = Math.max(0, Math.min(3, Integer.parseInt(p.getProperty("totemPartAmount", "3"))));
            totemPartSize = Math.max(50, Math.min(200, Integer.parseInt(p.getProperty("totemPartSize", "100"))));
            totemPartColor = Integer.parseInt(p.getProperty("totemPartColor", "6CFF8A"), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            totemPartStyle = 0;
            totemPartAmount = 3;
            totemPartSize = 100;
            totemPartColor = 0x6CFF8A;
        }
        hitboxCustom = Boolean.parseBoolean(p.getProperty("hitboxCustom", "false"));
        crosshairCustom = Boolean.parseBoolean(p.getProperty("crosshairCustom", "false"));
        blockOutlineCustom = Boolean.parseBoolean(p.getProperty("blockOutlineCustom", "false"));
        killEffect = p.getProperty("killEffect", "off");
        maceEffect = p.getProperty("maceEffect", "off");
        try {
            maceColor = Integer.parseInt(p.getProperty("maceColor", "FF8A2A"), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            maceColor = 0xFF8A2A;
        }
        if ("shatter".equals(killEffect)) {
            killEffect = "off"; // Shatter was removed (it crashed some games)
        }
        killOnlyMine = Boolean.parseBoolean(p.getProperty("killOnlyMine", "true"));
        killSound = Boolean.parseBoolean(p.getProperty("killSound", "false"));
        cosmeticsSeen = Boolean.parseBoolean(p.getProperty("cosmeticsSeen", "false"));
        try {
            killVolume = Math.max(5, Math.min(100, Integer.parseInt(p.getProperty("killVolume", "100"))));
        } catch (NumberFormatException ignored) {
            killVolume = 100;
        }
        noExplosionSound = Boolean.parseBoolean(p.getProperty("noExplosionSound", "false"));
        try {
            killColor = Integer.parseInt(p.getProperty("killColor", "B388FF"), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            killColor = 0xB388FF;
        }
        try {
            blockOutlineColor = Integer.parseInt(p.getProperty("blockOutlineColor", "000000"), 16) & 0xFFFFFF;
            blockOutlineWidth = Math.max(1, Math.min(6, Integer.parseInt(p.getProperty("blockOutlineWidth", "3"))));
        } catch (NumberFormatException e) {
            blockOutlineColor = 0x000000;
            blockOutlineWidth = 3;
        }
        crosshairGradient = Boolean.parseBoolean(p.getProperty("crosshairGradient", "false"));
        crystalBoxCustom = Boolean.parseBoolean(p.getProperty("crystalBoxCustom", "false"));
        anchorGlowCustom = Boolean.parseBoolean(p.getProperty("anchorGlowCustom", "false"));
        anchorShowFull = Boolean.parseBoolean(p.getProperty("anchorShowFull", "false"));
        anchorPixelGlow = Boolean.parseBoolean(p.getProperty("anchorPixelGlow", "false"));
        coreCustom = Boolean.parseBoolean(p.getProperty("coreCustom", "false"));
        hitCustom = Boolean.parseBoolean(p.getProperty("hitCustom", "false"));
        hitRainbow = Boolean.parseBoolean(p.getProperty("hitRainbow", "false"));
        hitArmor = Boolean.parseBoolean(p.getProperty("hitArmor", "true"));
        glintCustom = Boolean.parseBoolean(p.getProperty("glintCustom", "false"));
        glintGradient = Boolean.parseBoolean(p.getProperty("glintGradient", "false"));
        glintRainbow = Boolean.parseBoolean(p.getProperty("glintRainbow", "false"));
        try {
            glintColor = Integer.parseInt(p.getProperty("glintColor", "B388FF"), 16) & 0xFFFFFF;
            glintColor2 = Integer.parseInt(p.getProperty("glintColor2", "75D4FF"), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            glintColor = 0xB388FF;
            glintColor2 = 0x75D4FF;
        }
        try {
            coreColor = Integer.parseInt(p.getProperty("coreColor", "FF5AC8"), 16) & 0xFFFFFF;
            hitColor = Integer.parseInt(p.getProperty("hitColor", "B388FF"), 16) & 0xFFFFFF;
            hitStrength = Math.max(20, Math.min(100, Integer.parseInt(p.getProperty("hitStrength", "55"))));
        } catch (NumberFormatException ignored) {
            coreColor = 0xFF5AC8;
            hitColor = 0xB388FF;
            hitStrength = 55;
        }
        try {
            anchorGlowColor = Integer.parseInt(p.getProperty("anchorGlowColor", "B388FF"), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            anchorGlowColor = 0xB388FF;
        }
        try {
            crystalBoxColor = Integer.parseInt(p.getProperty("crystalBoxColor", "B388FF"), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            crystalBoxColor = 0xB388FF;
        }
        try {
            crosshairColor2 = Integer.parseInt(p.getProperty("crosshairColor2", "FF5AC8"), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            crosshairColor2 = 0xFF5AC8;
        }
        try {
            gradientStrength = Math.max(50, Math.min(400, Integer.parseInt(p.getProperty("gradientStrength", "100"))));
        } catch (NumberFormatException ignored) {
            gradientStrength = 100;
        }
        try {
            hitboxColor = Integer.parseInt(p.getProperty("hitboxColor", "B388FF"), 16) & 0xFFFFFF;
            crosshairColor = Integer.parseInt(p.getProperty("crosshairColor", "B388FF"), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            hitboxColor = 0xB388FF;
            crosshairColor = 0xB388FF;
        }
        spotifyHud = Boolean.parseBoolean(p.getProperty("spotifyHud", "false"));
        spotifyPopup = Boolean.parseBoolean(p.getProperty("spotifyPopup", "false"));
        watermark = Boolean.parseBoolean(p.getProperty("watermark", "true"));
        watermarkStyle = "2".equals(p.getProperty("watermarkStyle", "1")) ? 2 : 1;
        socialLogo = p.getProperty("socialLogo", "off");
        socialName = p.getProperty("socialName", "");
        try {
            spotifyX = Float.parseFloat(p.getProperty("spotifyX", "-1"));
            spotifyY = Float.parseFloat(p.getProperty("spotifyY", "-1"));
            spotifyScale = Math.max(0.5F, Math.min(1.5F, Float.parseFloat(p.getProperty("spotifyScale", "1"))));
        } catch (NumberFormatException e) {
            spotifyX = -1;
            spotifyY = -1;
            spotifyScale = 1.0F;
        }
        try {
            fireHeight = Math.max(10, Math.min(100, Integer.parseInt(p.getProperty("fireHeight", "100"))));
            fireColor = Integer.parseInt(p.getProperty("fireColor", "75D4FF"), 16) & 0xFFFFFF;
            shieldColor = Integer.parseInt(p.getProperty("shieldColor", "B388FF"), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            fireHeight = 100;
            fireColor = 0x75D4FF;
            shieldColor = 0xB388FF;
        }
        fireCustom = Boolean.parseBoolean(p.getProperty("fireCustom", "false"));
        fogOff = Boolean.parseBoolean(p.getProperty("fogOff", "false"));
        earlyAccess = Boolean.parseBoolean(p.getProperty("earlyAccess", "true"));
        updateSeen = p.getProperty("updateSeen", "");
        lastVersion = p.getProperty("lastVersion", "");
        lastNotes = p.getProperty("lastNotes", "");
        invClearBackground = Boolean.parseBoolean(p.getProperty("invClearBackground", "false"));
        cobwebOutline = Boolean.parseBoolean(p.getProperty("cobwebOutline", "false"));
        try {
            timeOverride = Math.max(-1, Math.min(23999, Integer.parseInt(p.getProperty("timeOverride", "-1"))));
        } catch (NumberFormatException ignored) {
            timeOverride = -1;
        }
        try {
            cobwebOutlineWidth = Math.max(1, Math.min(16, Integer.parseInt(p.getProperty("cobwebOutlineQuarters", "2"))));
        } catch (NumberFormatException ignored) {
            cobwebOutlineWidth = 2;
        }
        showInTab = Boolean.parseBoolean(p.getProperty("showInTab", "true"));
        tourSeen = Boolean.parseBoolean(p.getProperty("tourSeen", "false"));
        halloween2026 = Boolean.parseBoolean(p.getProperty("halloween2026", "false"));
        worldMenu = Boolean.parseBoolean(p.getProperty("worldMenu", "false"));
        cosCape = p.getProperty("cosCape", "none");
        cosHead = p.getProperty("cosHead", "none");
        cosAura = p.getProperty("cosAura", "none");
        cosPet = p.getProperty("cosPet", "none");
        cosRainbow = Boolean.parseBoolean(p.getProperty("cosRainbow", "false"));
        cosCapeElytra = Boolean.parseBoolean(p.getProperty("cosCapeElytra", "true"));
        cosCapeTint = Boolean.parseBoolean(p.getProperty("cosCapeTint", "false"));
        try {
            cosColor = Integer.parseInt(p.getProperty("cosColor", "B388FF"), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            cosColor = 0xB388FF;
        }
        fogCustom = Boolean.parseBoolean(p.getProperty("fogCustom", "false"));
        grassCustom = Boolean.parseBoolean(p.getProperty("grassCustom", "false"));
        applyInstantly = Boolean.parseBoolean(p.getProperty("applyInstantly", "false"));
        try {
            grassColor = Integer.parseInt(p.getProperty("grassColor", "91BD59"), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            grassColor = 0x91BD59;
        }
        try {
            fogDistance = Math.max(10, Math.min(100, Integer.parseInt(p.getProperty("fogDistance", "100"))));
            fogColor = Integer.parseInt(p.getProperty("fogColor", "B388FF"), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            fogDistance = 100;
            fogColor = 0xB388FF;
        }
        shieldCustom = Boolean.parseBoolean(p.getProperty("shieldCustom", "false"));
        presetExtras = p.getProperty("presetExtras", "with");
        if (!presetExtras.equals("ask") && !presetExtras.equals("without")) {
            presetExtras = "with";
        }
        presetBackup = Boolean.parseBoolean(p.getProperty("presetBackup", "true"));
        for (String folder : p.getProperty("favorites", "").split(",")) {
            if (!folder.isBlank()) {
                FAVORITES.add(folder.trim());
            }
        }
    }

    public static void save() {
        // never write the defaults over the file before it was read (that lost the Spotify position)
        load();
        Properties p = toProperties();
        try (Writer out = Files.newBufferedWriter(file())) {
            p.store(out, "Bauloo Styles - menu look and favorites");
        } catch (IOException ignored) {
        }
    }

    // ---------------------------------------------------------------- extras in presets

    /** All Extras settings (colours, sizes, glint ...) as plain text, for a preset. */
    public static java.util.Map<String, String> extras() {
        Properties p = toProperties();
        java.util.Map<String, String> result = new java.util.TreeMap<>();
        for (String key : p.stringPropertyNames()) {
            if (!MENU_KEYS.contains(key)) {
                result.put(key, p.getProperty(key));
            }
        }
        return result;
    }

    /** Takes over a preset's Extras; settings it doesn't have go back to default. */
    public static void applyExtras(java.util.Map<String, String> extras) {
        Properties p = new Properties();
        Properties current = toProperties();
        for (String key : MENU_KEYS) {
            if (current.getProperty(key) != null) {
                p.setProperty(key, current.getProperty(key));
            }
        }
        extras.forEach((key, value) -> {
            if (!MENU_KEYS.contains(key) && !key.startsWith("_")) {
                p.setProperty(key, value);
            }
        });
        try (Writer out = Files.newBufferedWriter(file())) {
            p.store(out, "Bauloo Styles - menu look and favorites");
        } catch (IOException ignored) {
            return;
        }
        FAVORITES.clear();
        loaded = false;
        load();
    }

    private static Path backupFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles-extras-backup.properties");
    }

    /**
     * Applies a preset's Extras. With the backup on, your own Extras are saved first - but not
     * when the current ones only came from the last preset, so the backup stays yours.
     */
    public static void applyPresetExtras(java.util.Map<String, String> extras) {
        String applied = readBackup().getProperty("_applied", "");
        boolean own = !String.valueOf(extras().toString().hashCode()).equals(applied);
        if (presetBackup && own) {
            backupExtras();
        }
        applyExtras(extras);
        if (hasBackup()) {
            Properties p = readBackup();
            p.setProperty("_applied", String.valueOf(extras().toString().hashCode()));
            writeBackup(p);
        }
    }

    private static Properties readBackup() {
        Properties p = new Properties();
        if (Files.exists(backupFile())) {
            try (Reader in = Files.newBufferedReader(backupFile())) {
                p.load(in);
            } catch (IOException ignored) {
            }
        }
        return p;
    }

    private static void writeBackup(Properties p) {
        try (Writer out = Files.newBufferedWriter(backupFile())) {
            p.store(out, "Bauloo Styles - your Extras before a preset replaced them");
        } catch (IOException ignored) {
        }
    }

    public static void backupExtras() {
        Properties p = new Properties();
        p.putAll(extras());
        try (Writer out = Files.newBufferedWriter(backupFile())) {
            p.store(out, "Bauloo Styles - your Extras before a preset replaced them");
        } catch (IOException ignored) {
        }
    }

    public static boolean hasBackup() {
        return Files.exists(backupFile());
    }

    /** Puts the backed up Extras back; true when it worked. */
    public static boolean restoreBackup() {
        if (!hasBackup()) {
            return false;
        }
        Properties p = readBackup();
        java.util.Map<String, String> extras = new java.util.TreeMap<>();
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith("_")) {
                extras.put(key, p.getProperty(key));
            }
        }
        applyExtras(extras);
        // the restored Extras are yours again: the next preset backs them up
        p.remove("_applied");
        writeBackup(p);
        return true;
    }

    private static Properties toProperties() {
        load();
        Properties p = new Properties();
        p.setProperty("accent", String.format("%06X", accent & 0xFFFFFF));
        p.setProperty("background", background.name());
        p.setProperty("opacity", String.valueOf(opacity));
        p.setProperty("glow", String.valueOf(glow));
        p.setProperty("brackets", String.valueOf(brackets));
        p.setProperty("hints", String.valueOf(hints));
        p.setProperty("themesTab", String.valueOf(themesTab));
        p.setProperty("outline", String.valueOf(outline));
        p.setProperty("outlineSelf", String.valueOf(outlineSelf));
        p.setProperty("outlineOthers", String.valueOf(outlineOthers));
        p.setProperty("outlineRainbow", String.valueOf(outlineRainbow));
        p.setProperty("outlineColor", String.format("%06X", outlineColor & 0xFFFFFF));
        p.setProperty("outlineWidth", String.valueOf(outlineWidth));
        p.setProperty("shieldOpacity", String.valueOf(shieldOpacity));
        p.setProperty("totemSize", String.valueOf(totemSize));
        p.setProperty("shieldSize", String.valueOf(shieldSize));
        p.setProperty("gapSize", String.valueOf(gapSize));
        p.setProperty("crystalSpin", String.valueOf(crystalSpin));
        p.setProperty("totemPopSize", String.valueOf(totemPopSize));
        p.setProperty("totemPart", String.valueOf(totemPart));
        p.setProperty("skyShooting", String.valueOf(skyShooting));
        p.setProperty("stylesOverServer", String.valueOf(stylesOverServer));
        p.setProperty("serverPackWarning", String.valueOf(serverPackWarning));
        p.setProperty("skyShower", String.valueOf(skyShower));
        p.setProperty("skyAurora", String.valueOf(skyAurora));
        p.setProperty("skyMilky", String.valueOf(skyMilky));
        p.setProperty("skyTwinkle", String.valueOf(skyTwinkle));
        p.setProperty("skyComet", String.valueOf(skyComet));
        p.setProperty("skyConstellations", String.valueOf(skyConstellations));
        p.setProperty("skyBlood", String.valueOf(skyBlood));
        p.setProperty("totemPartStyle", String.valueOf(totemPartStyle));
        p.setProperty("totemPartAmount", String.valueOf(totemPartAmount));
        p.setProperty("totemPartSize", String.valueOf(totemPartSize));
        p.setProperty("totemPartCustom", String.valueOf(totemPartCustom));
        p.setProperty("totemPartColor", String.format("%06X", totemPartColor & 0xFFFFFF));
        p.setProperty("totemPartRainbow", String.valueOf(totemPartRainbow));
        p.setProperty("totemPartShort", String.valueOf(totemPartShort));
        p.setProperty("hitboxCustom", String.valueOf(hitboxCustom));
        p.setProperty("hitboxColor", String.format("%06X", hitboxColor & 0xFFFFFF));
        p.setProperty("crosshairCustom", String.valueOf(crosshairCustom));
        p.setProperty("blockOutlineCustom", String.valueOf(blockOutlineCustom));
        p.setProperty("killEffect", killEffect);
        p.setProperty("maceEffect", maceEffect);
        p.setProperty("maceColor", String.format("%06X", maceColor & 0xFFFFFF));
        p.setProperty("killOnlyMine", String.valueOf(killOnlyMine));
        p.setProperty("killSound", String.valueOf(killSound));
        p.setProperty("killVolume", String.valueOf(killVolume));
        p.setProperty("cosmeticsSeen", String.valueOf(cosmeticsSeen));
        p.setProperty("noExplosionSound", String.valueOf(noExplosionSound));
        p.setProperty("killColor", String.format("%06X", killColor & 0xFFFFFF));
        p.setProperty("blockOutlineColor", String.format("%06X", blockOutlineColor & 0xFFFFFF));
        p.setProperty("blockOutlineWidth", String.valueOf(blockOutlineWidth));
        p.setProperty("crosshairColor", String.format("%06X", crosshairColor & 0xFFFFFF));
        p.setProperty("crosshairGradient", String.valueOf(crosshairGradient));
        p.setProperty("crosshairColor2", String.format("%06X", crosshairColor2 & 0xFFFFFF));
        p.setProperty("gradientStrength", String.valueOf(gradientStrength));
        p.setProperty("crystalBoxCustom", String.valueOf(crystalBoxCustom));
        p.setProperty("anchorGlowCustom", String.valueOf(anchorGlowCustom));
        p.setProperty("anchorShowFull", String.valueOf(anchorShowFull));
        p.setProperty("anchorPixelGlow", String.valueOf(anchorPixelGlow));
        p.setProperty("coreCustom", String.valueOf(coreCustom));
        p.setProperty("coreColor", String.format("%06X", coreColor & 0xFFFFFF));
        p.setProperty("hitCustom", String.valueOf(hitCustom));
        p.setProperty("hitColor", String.format("%06X", hitColor & 0xFFFFFF));
        p.setProperty("hitStrength", String.valueOf(hitStrength));
        p.setProperty("hitRainbow", String.valueOf(hitRainbow));
        p.setProperty("hitArmor", String.valueOf(hitArmor));
        p.setProperty("glintCustom", String.valueOf(glintCustom));
        p.setProperty("glintColor", String.format("%06X", glintColor & 0xFFFFFF));
        p.setProperty("glintGradient", String.valueOf(glintGradient));
        p.setProperty("glintColor2", String.format("%06X", glintColor2 & 0xFFFFFF));
        p.setProperty("glintRainbow", String.valueOf(glintRainbow));
        p.setProperty("anchorGlowColor", String.format("%06X", anchorGlowColor & 0xFFFFFF));
        p.setProperty("crystalBoxColor", String.format("%06X", crystalBoxColor & 0xFFFFFF));
        p.setProperty("fireHeight", String.valueOf(fireHeight));
        p.setProperty("spotifyHud", String.valueOf(spotifyHud));
        p.setProperty("spotifyPopup", String.valueOf(spotifyPopup));
        p.setProperty("watermark", String.valueOf(watermark));
        p.setProperty("watermarkStyle", String.valueOf(watermarkStyle));
        p.setProperty("socialLogo", socialLogo);
        p.setProperty("socialName", socialName);
        p.setProperty("spotifyX", String.valueOf(spotifyX));
        p.setProperty("spotifyY", String.valueOf(spotifyY));
        p.setProperty("spotifyScale", String.valueOf(spotifyScale));
        p.setProperty("fireCustom", String.valueOf(fireCustom));
        p.setProperty("fogOff", String.valueOf(fogOff));
        p.setProperty("earlyAccess", String.valueOf(earlyAccess));
        p.setProperty("updateSeen", updateSeen);
        p.setProperty("lastVersion", lastVersion);
        p.setProperty("lastNotes", lastNotes);
        p.setProperty("invClearBackground", String.valueOf(invClearBackground));
        p.setProperty("cobwebOutline", String.valueOf(cobwebOutline));
        p.setProperty("timeOverride", String.valueOf(timeOverride));
        p.setProperty("cobwebOutlineQuarters", String.valueOf(cobwebOutlineWidth));
        p.setProperty("showInTab", String.valueOf(showInTab));
        p.setProperty("tourSeen", String.valueOf(tourSeen));
        p.setProperty("halloween2026", String.valueOf(halloween2026));
        p.setProperty("worldMenu", String.valueOf(worldMenu));
        p.setProperty("cosCape", cosCape);
        p.setProperty("cosHead", cosHead);
        p.setProperty("cosAura", cosAura);
        p.setProperty("cosPet", cosPet);
        p.setProperty("cosColor", String.format("%06X", cosColor & 0xFFFFFF));
        p.setProperty("cosRainbow", String.valueOf(cosRainbow));
        p.setProperty("cosCapeElytra", String.valueOf(cosCapeElytra));
        p.setProperty("cosCapeTint", String.valueOf(cosCapeTint));
        p.setProperty("fogDistance", String.valueOf(fogDistance));
        p.setProperty("fogCustom", String.valueOf(fogCustom));
        p.setProperty("fogColor", String.format("%06X", fogColor & 0xFFFFFF));
        p.setProperty("grassCustom", String.valueOf(grassCustom));
        p.setProperty("applyInstantly", String.valueOf(applyInstantly));
        p.setProperty("grassColor", String.format("%06X", grassColor & 0xFFFFFF));
        p.setProperty("fireColor", String.format("%06X", fireColor & 0xFFFFFF));
        p.setProperty("shieldCustom", String.valueOf(shieldCustom));
        p.setProperty("shieldColor", String.format("%06X", shieldColor & 0xFFFFFF));
        p.setProperty("presetExtras", presetExtras);
        p.setProperty("presetBackup", String.valueOf(presetBackup));
        p.setProperty("favorites", String.join(",", FAVORITES));
        return p;
    }
}

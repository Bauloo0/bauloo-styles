package de.bauloo.styles;

import de.bauloo.styles.BaulooStylesClient.Tab;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bauloo Style Archive. Keeps the original calm layout (collections, styles,
 * inspector) and adds a loadout strip, favorites, search and a theme tab.
 * Shapes are soft (cut corners, fills instead of frames) on purpose.
 */
public class BaulooStylesConfigScreen extends Screen {

    private static final String PREFIX = BaulooStylesClient.MOD_ID + ":";
    private static final String UPLOAD = "bauloo_upload_";
    private static final String CLOUD = StylesCloud.FOLDER_PREFIX;

    private static final int MARGIN = 12;
    private static final int HEADER_H = 38;
    private static final int FOOTER_H = 28;
    private static final int GAP = 8;
    private static final int LABEL_H = 17;

    private interface Action {
        void run(int button, double mouseX, double mouseY);
    }

    /** A clickable area. clipTop/clipBottom cut it off inside scrolled regions. */
    private record Hit(int x, int y, int w, int h, int clipTop, int clipBottom, Action action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipTop && my < clipBottom;
        }
    }

    private enum Drag { NONE, HUE, OPACITY, OUTLINE_HUE, OUTLINE_WIDTH, BLOCK_WIDTH, SHIELD, TOTEM, SHIELD_SIZE, GAP_SIZE, PICK_HUE, COBWEB_WIDTH, TIME, COS_TURN, CRYSTAL_SPIN, TOTEM_POP, GRADIENT, KILL_VOLUME }

    private static final class Layout {
        int fx, fy, fw, fh, bodyY, footerY;
        int railW, mainX, mainW, sideX, sideW;
        boolean side;
    }

    private final Screen parent;
    /** Sky styles work always: Skyboxify draws them when installed, else the mod itself (CustomSky) */
    private final boolean skyboxifyInstalled = true;

    /** {folder or null for vanilla, display name, "upload" or null} */
    private final List<String[]> entries = new ArrayList<>();
    private final List<Integer> visible = new ArrayList<>();
    private final Map<Tab, Integer> uploadCounts = new EnumMap<>(Tab.class);
    private final List<Hit> hits = new ArrayList<>();

    private Tab tab = Tab.CRYSTAL;
    private int focusedIndex;
    private int hoveredEntry = -1;
    private String query = "";
    private boolean searchFocused;
    private boolean favoritesOnly;
    private boolean hexFocused;
    /** typing into the social badge name */
    private boolean socialFocused;
    private String hexDraft = "";
    private boolean bindingKey;
    private boolean compactThemes;
    /** the main area shows Extras - opened with the EXTRAS tab next to COLLECTIONS */
    private static boolean extrasView;
    /** "My Packs": the styles found in your own resourcepacks folder */
    private static boolean myPacksView;
    private int myPacksVersion = -1;
    private static final String MYPACK = "mypack:";
    /** a style from bauloo's own packs (VIP), shown in the My Packs layout */
    private static final String VIPPACK = "vippack:";
    /** the My Packs page shows Bauloo's Packs instead */
    private static boolean vipView;
    private int vipVersion = -1;
    /** the Cosmetics page: capes, wings, head and aura on your own player */
    private static boolean cosmeticsView;
    private static Cosmetics.Slot cosSlot = Cosmetics.Slot.CAPE;
    private float cosYaw = 180;
    /** the slot the preview was turned for: a new slot turns it to its side again (capes: the back) */
    private Cosmetics.Slot cosYawSlot;
    private float cosDragYaw;
    private long cosLastFrame;
    /** the main area shows the presets everyone shared, as big cards */
    private static boolean publicView;
    /** after TEST POP the menu comes back by itself once the animation is over; 0 = not waiting */
    static volatile long reopenAtMs;
    /** the side panel (or the main area on small windows) shows the presets */
    private static boolean presetsView;
    private boolean presetNaming;
    /** typing a preset key (one-time code from a friend) */
    private boolean keyEntering;
    private String keyDraft = "";
    /** the share/code click that is waiting for a second click (styles from this PC go up) */
    private String shareConfirmKey;
    private long shareConfirmUntil;
    /** a preset with Extras waiting for WITH EXTRAS / STYLES ONLY */
    private StylesPresets.Preset pendingPreset;
    /** a newer version is out: asked once per version when the menu opens */
    private String updateAsk;
    private WhatsNew.Notes whatsNew;
    private boolean whatsNewChecked;
    /** the RESET button in the footer asks first */
    private boolean resetAsk;
    private static final String RESET_NAME = "Reset";
    private boolean pendingRemember;
    private String extrasStatus = "";
    private String presetDraft = "";
    private String zoomFolder;
    private String zoomName;
    private String tooltip;
    private int tooltipX, tooltipY;

    private double gridScroll;
    private int gridMaxScroll;
    private int gridTop, gridBottom, gridLeft, gridRight;
    private double themeScroll;
    private int themeMaxScroll;
    private int themeTop, themeBottom, themeLeft, themeRight;

    private Drag drag = Drag.NONE;
    private int dragX, dragW, dragY;
    /** hand sliders move from where they are, not from where you click */
    private double dragStartMouse;
    private float dragStartT;
    private int hotkeyX, hotkeyY, hotkeyW;

    private StylesTheme c;
    private Map<Tab, String> active = new EnumMap<>(Tab.class);
    /** A hover clip: frames laid out left to right, top to bottom in one sheet. */
    private record Clip(int frames, int fps, int size, int columns) {
    }

    private static final Map<String, java.util.Optional<Clip>> CLIPS = new java.util.HashMap<>();
    private static final long CLIP_DELAY_MS = 450;
    private int hoverTrack = -1;
    private long hoverSince;

    private int cloudVersion = -1;
    private int bindVersion = -1;
    /** early access and admin key as they were when the slots were bound; a change shows or hides them at once */
    private static String earlyBound;
    private int uploadVersion = -1;
    /** cloud style that is downloading and should be applied once it is there */
    private String pendingCloud;

    public BaulooStylesConfigScreen(Screen parent) {
        super(Text.literal("Bauloo Styles"));
        this.parent = parent;
        StylesUiConfig.load();
        StylesCloud.refresh(false);
        rebuild();
    }

    @Override
    protected void init() {
        super.init();
        if (StylesBlock.isBlocked() && client != null) {
            // bauloo shut this account out of Bauloo Styles: a plain notice instead of the menu
            StylesBlock.enforce();
            client.send(() -> client.setScreen(new net.minecraft.client.gui.screen.NoticeScreen(
                    () -> client.setScreen(parent),
                    Text.literal("Bauloo Styles"),
                    Text.literal("Bauloo Styles is not available for this account."))));
        }
    }

    private void rebuild() {
        cloudVersion = StylesCloud.version();
        bindVersion = Tab.bindVersion;
        uploadVersion = StylesUploadManager.version();
        entries.clear();
        if (MyPacks.shown()) {
            MyPacks.ensureScanned();   // starts with the menu, so My Packs is ready when you get there
        }
        if (myPacksView && vipView) {
            rebuildVip();
            return;
        }
        if (myPacksView) {
            rebuildMyPacks();
            return;
        }
        // the Tools collection shows swords and pickaxes together; each keeps its own choice
        for (Tab group : groupOf(tab)) {
            String g = group.name();
            entries.add(new String[]{null, "Vanilla", null, g});
            for (StylesCloud.Style style : StylesCloud.of(group.prefix)) {
                entries.add(new String[]{style.folder(), style.name(), "cloud", g});
            }
            for (String[] style : StylesUploadManager.sync(group.prefix)) {
                entries.add(new String[]{style[0], style[1], "upload", g});
            }
            for (String[] style : BaulooStylesClient.stylesOf(group)) {
                entries.add(new String[]{style[0], style[1], null, g});
            }
        }
        for (Tab value : Tab.values()) {
            uploadCounts.put(value, StylesUploadManager.list(value.title).size());
        }
        active = activeFolders();
        focusedIndex = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (isActive(i)) {
                focusedIndex = i;
                break;
            }
        }
        gridScroll = 0;
        applyFilter();
    }

    /** My Packs: every part found in your packs, grouped by collection; keeps the scroll while the scan adds more */
    private void rebuildMyPacks() {
        MyPacks.ensureScanned();
        myPacksVersion = MyPacks.version();
        for (MyPacks.Style style : MyPacks.styles()) {
            Tab group = tabOf(MYPACK + style.id());
            entries.add(new String[]{MYPACK + style.id(), style.name(), "mypack", group.name()});
        }
        active = activeFolders();
        focusedIndex = Math.min(focusedIndex, Math.max(0, entries.size() - 1));
        applyFilter();
    }

    /** Bauloo's Packs: bauloo's collection for VIPs, grouped by collection like My Packs */
    private void rebuildVip() {
        VipPacks.refresh(false);
        vipVersion = VipPacks.version();
        // in the menu's order (Crystal first), names A-Z inside each collection
        List<VipPacks.Style> sorted = new ArrayList<>(VipPacks.styles());
        sorted.sort(java.util.Comparator.<VipPacks.Style>comparingInt(st -> tabOf(VIPPACK + st.id()).ordinal())
                .thenComparing(st -> st.name().toLowerCase(Locale.ROOT)));
        for (VipPacks.Style style : sorted) {
            Tab group = tabOf(VIPPACK + style.id());
            entries.add(new String[]{VIPPACK + style.id(), style.name(), "vippack", group.name()});
        }
        active = activeFolders();
        focusedIndex = Math.min(focusedIndex, Math.max(0, entries.size() - 1));
        applyFilter();
    }

    /** "Bauloo's Packs": downloads the style (VIP key), puts it into your uploads, then applies it */
    private void useVipPack(String folder, Tab target) {
        VipPacks.Style style = VipPacks.byId(folder.substring(VIPPACK.length()));
        if (style == null || pendingMyPack) {
            return;
        }
        pendingMyPack = true;
        Thread thread = new Thread(() -> {
            String id = VipPacks.use(style);
            MinecraftClient.getInstance().execute(() -> {
                pendingMyPack = false;
                if (id != null && MinecraftClient.getInstance().currentScreen == this) {
                    applyAndRefresh(id, target);
                }
            });
        }, "Bauloo Styles VIP pack");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyFilter() {
        visible.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < entries.size(); i++) {
            String[] e = entries.get(i);
            if (favoritesOnly && (e[0] == null || !StylesUiConfig.isFavorite(e[0]))) {
                continue;
            }
            if (!q.isEmpty() && !e[1].toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            visible.add(i);
        }
    }

    private int favoriteCount() {
        int n = 0;
        for (String[] e : entries) {
            if (e[0] != null && StylesUiConfig.isFavorite(e[0])) {
                n++;
            }
        }
        return n;
    }

    /** Audit tour only: how many stops auditShow has (every collection, then Extras). */
    public static int auditStops() {
        return shownTabs().length + 2;
    }

    /** Audit tour only: open one collection with its first style in the preview, or Extras at the end. */
    public void auditShow(int stop) {
        whatsNew = null;   // the shots should show the menu, not the popup
        Tab[] tabs = shownTabs();
        if (stop < tabs.length) {
            switchTab(tabs[stop]);
            focusedIndex = Math.min(1, entries.size() - 1);
        } else if (stop == tabs.length) {
            extrasView = true;
        } else {
            extrasView = false;
            myPacksView = true;
            rebuild();
        }
    }

    private void switchTab(Tab value) {
        if (myPacksView) {
            myPacksView = false;
            tab = value;
            query = "";
            searchFocused = false;
            extrasView = false;
            publicView = false;
            cosmeticsView = false;
            rebuild();
            return;
        }
        extrasView = false;
        publicView = false;
        cosmeticsView = false;
        if (value == tab) {
            return;
        }
        tab = value;
        query = "";
        searchFocused = false;
        rebuild();
    }

    // ------------------------------------------------------------------ packs

    private static String packId(String folder) {
        if (folder.startsWith(UPLOAD)) {
            return folder.endsWith("_folder") ? "file/" + folder : "file/" + folder + ".zip";
        }
        if (folder.startsWith(CLOUD)) {
            return "file/" + folder + ".zip";
        }
        return PREFIX + folder;
    }

    private static boolean isPackOf(Tab value, String id) {
        return id.startsWith(PREFIX + value.prefix + "_") || id.startsWith("file/" + UPLOAD + value.prefix + "_")
                || id.startsWith("file/" + CLOUD + value.prefix + "_");
    }

    /** The folder of the active style per collection; missing = vanilla. */
    private static Map<Tab, String> activeFolders() {
        Map<Tab, String> map = new EnumMap<>(Tab.class);
        for (String id : MinecraftClient.getInstance().getResourcePackManager().getEnabledIds()) {
            for (Tab value : Tab.values()) {
                if (id.startsWith(PREFIX + value.prefix + "_")) {
                    map.put(value, id.substring(PREFIX.length()));
                } else if (id.startsWith("file/" + UPLOAD + value.prefix + "_")
                        || id.startsWith("file/" + CLOUD + value.prefix + "_")) {
                    String folder = id.substring("file/".length());
                    if (folder.endsWith(".zip")) {
                        folder = folder.substring(0, folder.length() - 4);
                    }
                    map.put(value, folder);
                }
            }
        }
        return map;
    }

    /** the collection an entry belongs to (in Tools that is Sword or Pickaxe) */
    private Tab tabOf(int index) {
        String[] e = entries.get(index);
        return e.length > 3 && e[3] != null ? Tab.valueOf(e[3]) : tab;
    }

    /** the collections with their own row in the menu: Pickaxe and Axe live inside Tools, Glowstone inside Anchor, Pearl inside Gaps */
    private static Tab[] shownTabs() {
        return java.util.Arrays.stream(Tab.values()).filter(t -> t != Tab.PICKAXE && t != Tab.AXE && t != Tab.GLOWSTONE && t != Tab.PEARL
                && (!t.isCloudSlot() || t.bound))
                .toArray(Tab[]::new);
    }

    /** what a collection shows: Tools = swords + pickaxes + axes, Anchor = anchors + glowstone, else itself */
    private static Tab[] groupOf(Tab value) {
        return switch (value) {
            case SWORD -> new Tab[]{Tab.SWORD, Tab.PICKAXE, Tab.AXE};
            case GAP -> new Tab[]{Tab.GAP, Tab.PEARL};
            case ANCHOR -> new Tab[]{Tab.ANCHOR, Tab.GLOWSTONE};
            default -> new Tab[]{value};
        };
    }

    private boolean isActive(int index) {
        String folder = entries.get(index)[0];
        String current = active.get(tabOf(index));
        return folder == null ? current == null : folder.equals(current);
    }

    private String activeName(Tab value) {
        if (value == Tab.SWORD) {
            String tools = oneName(Tab.SWORD).replace(" Sword", "") + " / " + oneName(Tab.PICKAXE).replace(" Pickaxe", "");
            return active.get(Tab.AXE) == null ? tools : tools + " / " + oneName(Tab.AXE).replace(" Axe", "");
        }
        if (value == Tab.GAP) {
            return oneName(Tab.GAP) + " / " + oneName(Tab.PEARL);
        }
        if (value == Tab.ANCHOR && active.get(Tab.GLOWSTONE) != null) {
            return oneName(Tab.ANCHOR) + " / " + oneName(Tab.GLOWSTONE);
        }
        return oneName(value);
    }

    private String oneName(Tab value) {
        String folder = active.get(value);
        if (folder == null) {
            return "Vanilla";
        }
        for (String[] style : BaulooStylesClient.stylesOf(value)) {
            if (style[0].equals(folder)) {
                return style[1];
            }
        }
        StylesCloud.Style cloud = StylesCloud.byFolder(folder);
        if (cloud != null) {
            return cloud.name();
        }
        String name = folder.startsWith(UPLOAD + value.prefix + "_")
                ? folder.substring((UPLOAD + value.prefix + "_").length()) : folder;
        return name.replace("_skyonly", "").replace("_folder", "");
    }

    private int styleCount(Tab value) {
        int count = BaulooStylesClient.stylesOf(value).length + uploadCounts.getOrDefault(value, 0)
                + StylesCloud.of(value.prefix).size();
        return value == Tab.SWORD ? count + styleCount(Tab.PICKAXE) + styleCount(Tab.AXE)
                : value == Tab.GAP ? count + styleCount(Tab.PEARL)
                : value == Tab.ANCHOR ? count + styleCount(Tab.GLOWSTONE) : count;
    }

    private static ItemStack icon(Tab value) {
        if (value.isCloudSlot()) {
            // the cloud names the item; an unknown id falls back to paper
            var id = Identifier.tryParse(value.icon);
            var item = id == null ? Items.PAPER : net.minecraft.registry.Registries.ITEM.get(id);
            return new ItemStack(item == Items.AIR ? Items.PAPER : item);
        }
        return new ItemStack(switch (value) {
            case CRYSTAL -> Items.END_CRYSTAL;
            case ANCHOR -> Items.RESPAWN_ANCHOR;
            case GLOWSTONE -> Items.GLOWSTONE;
            case ARMOR -> Items.NETHERITE_CHESTPLATE;
            case SWORD -> Items.NETHERITE_SWORD;
            case PICKAXE -> Items.NETHERITE_PICKAXE;
            case AXE -> Items.NETHERITE_AXE;
            case TOTEM -> Items.TOTEM_OF_UNDYING;
            case GAP -> Items.GOLDEN_APPLE;
            case PEARL -> Items.ENDER_PEARL;
            case HOTBAR -> Items.ITEM_FRAME;
            case INVENTORY -> Items.CHEST;
            case FIRE -> Items.FIRE_CHARGE;
            case SKY -> Items.ELYTRA;
            case DEEPSLATE -> Items.DEEPSLATE;
            case STONE -> Items.STONE;
            case SNOW -> Items.SNOW_BLOCK;
            case GRASS -> Items.GRASS_BLOCK;
            case EXTRA1, EXTRA2, EXTRA3, EXTRA4, EXTRA5, EXTRA6, EXTRA7, EXTRA8 -> Items.PAPER;
            case OBSIDIAN -> Items.OBSIDIAN;
        });
    }

    /**
     * Batch apply: styles picked but not applied yet, per collection. They stay while the menu
     * switches tabs; APPLY (or closing the menu) turns them on with a single resource reload.
     */
    private static final Map<Tab, String> PENDING = new EnumMap<>(Tab.class);
    /** marks "back to vanilla" in PENDING */
    private static final String VANILLA_PICK = "\u0000vanilla";
    private static final int PENDING_COLOR = 0xFFFFC23D;

    /** A click on a style: marks it (or applies it right away when "Apply instantly" is on). */
    private void choose(String folder, Tab target) {
        if (StylesUiConfig.applyInstantly && folder != null && folder.startsWith(MYPACK)) {
            useMyPack(folder, target);
            return;
        }
        if (StylesUiConfig.applyInstantly && folder != null && folder.startsWith(VIPPACK)) {
            useVipPack(folder, target);
            return;
        }
        if (StylesUiConfig.applyInstantly) {
            applyAndRefresh(folder, target);
            return;
        }
        if (target == Tab.SKY && folder != null && !skyboxifyInstalled) {
            return;
        }
        if (java.util.Objects.equals(folder, active.get(target))) {
            PENDING.remove(target); // picking the style that is on again: nothing to change
        } else {
            PENDING.put(target, folder == null ? VANILLA_PICK : folder);
        }
    }

    private boolean isPending(int index) {
        String picked = PENDING.get(tabOf(index));
        String folder = entries.get(index)[0];
        return picked != null && picked.equals(folder == null ? VANILLA_PICK : folder);
    }

    private static boolean pendingIn(Tab value) {
        for (Tab t : groupOf(value)) {
            if (PENDING.containsKey(t)) {
                return true;
            }
        }
        return false;
    }

    /** Turns every marked style on with one reload; downloads cloud styles first. */
    private void applyPending(boolean reopen) {
        applyPending(reopen, true);
    }

    /** download: false after the one download round - a style that still is not there is skipped. */
    private void applyPending(boolean reopen, boolean download) {
        if (PENDING.isEmpty()) {
            return;
        }
        // picks from My Packs or Bauloo's Packs become local uploads first (reading packs takes a moment)
        Map<Tab, String> convert = new EnumMap<>(Tab.class);
        PENDING.forEach((t, f) -> {
            if (f.startsWith(MYPACK) || f.startsWith(VIPPACK)) {
                convert.put(t, f);
            }
        });
        if (!convert.isEmpty()) {
            pendingCloud = "\u0000batch";
            Thread thread = new Thread(() -> {
                Map<Tab, String> ids = new EnumMap<>(Tab.class);
                convert.forEach((t, f) -> {
                    String id = null;
                    if (f.startsWith(MYPACK)) {
                        MyPacks.Style s = MyPacks.byId(f.substring(MYPACK.length()));
                        id = s == null ? null : MyPacks.use(s);
                    } else {
                        VipPacks.Style s = VipPacks.byId(f.substring(VIPPACK.length()));
                        id = s == null ? null : VipPacks.use(s);
                    }
                    ids.put(t, id == null ? "" : id);
                });
                MinecraftClient.getInstance().execute(() -> {
                    pendingCloud = null;
                    ids.forEach((t, id) -> {
                        if (id.isEmpty()) {
                            PENDING.remove(t);
                        } else {
                            PENDING.put(t, id);
                        }
                    });
                    applyPending(reopen && MinecraftClient.getInstance().currentScreen == this, download);
                });
            }, "Bauloo Styles pack picks");
            thread.setDaemon(true);
            thread.start();
            return;
        }
        List<java.util.concurrent.CompletableFuture<Boolean>> downloads = new ArrayList<>();
        for (Map.Entry<Tab, String> e : new ArrayList<>(PENDING.entrySet())) {
            StylesCloud.Style cloud = StylesCloud.byFolder(e.getValue());
            if (cloud != null && !StylesCloud.isReady(cloud)) {
                if (download) {
                    downloads.add(StylesCloud.download(cloud));
                } else {
                    // the download failed: leave this one out instead of trying forever
                    PENDING.remove(e.getKey());
                    BaulooStylesClient.LOGGER.warn("[Batch] {} could not be downloaded, skipped", cloud.id());
                    StylesPublicPresets.say("\"" + cloud.name() + "\" could not be downloaded");
                }
            }
        }
        if (!downloads.isEmpty()) {
            pendingCloud = "\u0000batch";
            java.util.concurrent.CompletableFuture.allOf(downloads.toArray(new java.util.concurrent.CompletableFuture[0]))
                    .whenComplete((ok, error) -> MinecraftClient.getInstance().send(() -> {
                        pendingCloud = null;
                        applyPending(reopen && MinecraftClient.getInstance().currentScreen == this, false);
                    }));
            return;
        }
        if (PENDING.isEmpty()) {
            return;
        }
        Map<Tab, String> todo = new EnumMap<>(PENDING);
        PENDING.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        ResourcePackManager packs = mc.getResourcePackManager();
        packs.scanPacks();
        List<String> enabled = new ArrayList<>(packs.getEnabledIds());
        for (Map.Entry<Tab, String> e : todo.entrySet()) {
            Tab value = e.getKey();
            if (value == Tab.SKY && !skyboxifyInstalled) {
                continue;
            }
            enabled.removeIf(id -> isPackOf(value, id));
            if (!VANILLA_PICK.equals(e.getValue()) && packs.getIds().contains(packId(e.getValue()))) {
                enabled.add(packId(e.getValue()));
            }
        }
        packs.setEnabledProfiles(enabled);
        mc.options.refreshResourcePacks(packs);
        mc.reloadResources();
        if (!reopen) {
            return;
        }
        BaulooStylesConfigScreen next = new BaulooStylesConfigScreen(parent);
        next.tab = tab;
        next.query = query;
        next.favoritesOnly = favoritesOnly;
        next.rebuild();
        next.gridScroll = gridScroll;
        mc.setScreen(next);
    }

    private void applyAndRefresh(String folder) {
        applyAndRefresh(folder, tab);
    }

    private void applyAndRefresh(String folder, Tab target) {
        if (target == Tab.SKY && folder != null && !skyboxifyInstalled) {
            return;
        }
        StylesCloud.Style cloud = StylesCloud.byFolder(folder);
        if (cloud != null && !StylesCloud.isReady(cloud)) {
            // First use: download, then apply - unless the player picked something else meanwhile.
            pendingCloud = folder;
            final Tab startedIn = tab;
            StylesCloud.download(cloud).thenAccept(ok -> MinecraftClient.getInstance().send(() -> {
                if (MinecraftClient.getInstance().currentScreen == this && folder.equals(pendingCloud)
                        && tab == startedIn) {
                    pendingCloud = null;
                    if (ok) {
                        applyAndRefresh(folder, target);
                    }
                }
            }));
            return;
        }
        pendingCloud = null;
        MinecraftClient mc = MinecraftClient.getInstance();
        ResourcePackManager packs = mc.getResourcePackManager();
        packs.scanPacks();

        List<String> enabled = new ArrayList<>(packs.getEnabledIds());
        enabled.removeIf(id -> isPackOf(target, id));
        if (folder != null) {
            String id = packId(folder);
            if (packs.getIds().contains(id)) {
                enabled.add(id);
            }
        }
        packs.setEnabledProfiles(enabled);
        mc.options.refreshResourcePacks(packs);
        // anchor charge and crystal core follow the new style by themselves after the reload
        mc.reloadResources();

        BaulooStylesConfigScreen next = new BaulooStylesConfigScreen(parent);
        next.tab = tab;
        next.query = query;
        next.favoritesOnly = favoritesOnly;
        next.rebuild();
        next.gridScroll = gridScroll;
        mc.setScreen(next);
    }

    /** The picture of a style, or null when it has none (local uploads, cloud styles without image). */
    private static Identifier preview(String folder) {
        StylesCloud.Style cloud = StylesCloud.byFolder(folder);
        if (cloud != null) {
            return StylesCloud.preview(cloud);
        }
        if (folder.startsWith(UPLOAD)) {
            return StylesUploadManager.preview(folder);
        }
        if (folder.startsWith(MYPACK)) {
            return MyPacks.preview(folder.substring(MYPACK.length()));
        }
        if (folder.startsWith(VIPPACK)) {
            return VipPacks.preview(folder.substring(VIPPACK.length()));
        }
        return builtIn(folder);
    }

    /** Vorschaubilder des Mods selbst, einmal scharf geladen (kein Weichzeichner). */
    private static final Map<String, Identifier> BUILT_IN = new java.util.HashMap<>();

    private static Identifier builtIn(String folder) {
        Identifier file = Identifier.of(BaulooStylesClient.MOD_ID, "textures/preview/" + folder + ".png");
        Identifier ready = BUILT_IN.get(folder);
        if (ready != null) {
            return ready;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            var resource = mc.getResourceManager().getResource(file);
            if (resource.isEmpty()) {
                BUILT_IN.put(folder, file);
                return file;
            }
            net.minecraft.client.texture.NativeImage image;
            try (java.io.InputStream in = resource.get().getInputStream()) {
                image = PreviewTexture.shrink(net.minecraft.client.texture.NativeImage.read(in.readAllBytes()));
            }
            Identifier sharp = Identifier.of(BaulooStylesClient.MOD_ID, "sharp_preview/" + folder);
            mc.getTextureManager().registerTexture(sharp, new PreviewTexture(sharp, image));
            BUILT_IN.put(folder, sharp);
            return sharp;
        } catch (java.io.IOException | RuntimeException e) {
            BUILT_IN.put(folder, file);
            return file;
        }
    }

    /** The hover clip of a built-in style, made from a recording by bauloo. Null when it has none. */
    private static Clip clip(String folder) {
        // only built-in styles have clips; anything else (My Packs ids have a ":") must never become a path
        if (folder == null || folder.startsWith(UPLOAD) || folder.startsWith(CLOUD) || !folder.matches("[a-z0-9_./-]+")) {
            return null;
        }
        return CLIPS.computeIfAbsent(folder, f -> {
            Identifier meta = Identifier.of(BaulooStylesClient.MOD_ID, "textures/preview_clip/" + f + ".json");
            try {
                var resource = MinecraftClient.getInstance().getResourceManager().getResource(meta);
                if (resource.isEmpty()) {
                    return java.util.Optional.empty();
                }
                try (var reader = resource.get().getReader()) {
                    var o = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    Clip clip = new Clip(o.get("frames").getAsInt(), o.get("fps").getAsInt(),
                            o.get("size").getAsInt(), o.get("columns").getAsInt());
                    boolean sane = clip.frames() > 1 && clip.fps() > 0 && clip.size() > 0 && clip.columns() > 0;
                    return sane ? java.util.Optional.of(clip) : java.util.Optional.empty();
                }
            } catch (Exception e) {
                return java.util.Optional.empty();
            }
        }).orElse(null);
    }

    /** True once the mouse rests long enough on this entry and it has a clip. */
    private boolean clipPlaying(int index) {
        return index == hoverTrack && System.currentTimeMillis() - hoverSince >= CLIP_DELAY_MS
                && clip(entries.get(index)[0]) != null;
    }

    /** Draws the current clip frame, cropped like drawPreview. */
    private void drawClip(DrawContext ctx, String folder, int x, int y, int w, int h) {
        Clip clip = clip(folder);
        if (clip == null || w <= 0 || h <= 0) {
            drawPreview(ctx, folder, x, y, w, h);
            return;
        }
        long elapsed = System.currentTimeMillis() - hoverSince - CLIP_DELAY_MS;
        int frame = (int) (Math.max(0, elapsed) * clip.fps() / 1000 % clip.frames());
        int s = clip.size();
        int rows = (clip.frames() + clip.columns() - 1) / clip.columns();
        int regionW = s;
        int regionH = s;
        float u = (frame % clip.columns()) * s;
        float v = (frame / clip.columns()) * s;
        if (w > h) {
            regionH = Math.max(1, s * h / w);
            v += (s - regionH) / 2.0F;
        } else if (h > w) {
            regionW = Math.max(1, s * w / h);
            u += (s - regionW) / 2.0F;
        }
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED,
                Identifier.of(BaulooStylesClient.MOD_ID, "textures/preview_clip/" + folder + ".png"),
                x, y, u, v, w, h, regionW, regionH, clip.columns() * s, rows * s);
    }

    /** Which collection a folder belongs to - used for the stand-in item icon. */
    private Tab tabOf(String folder) {
        StylesCloud.Style cloud = StylesCloud.byFolder(folder);
        String rest = cloud != null ? cloud.category()
                : folder.startsWith(UPLOAD) ? folder.substring(UPLOAD.length())
                : folder.startsWith(MYPACK) ? folder.substring(MYPACK.length())
                : folder.startsWith(VIPPACK) ? folder.substring(VIPPACK.length()) : folder;
        for (Tab value : Tab.values()) {
            if (rest.startsWith(value.prefix)) {
                return value;
            }
        }
        return tab;
    }

    // ----------------------------------------------------------------- layout

    private Layout layout() {
        Layout l = new Layout();
        // a centred panel with the blurred world around it
        l.fw = Math.max(300, Math.min(width - MARGIN * 2, Math.max(620, Math.round(width * 0.86F))));
        l.fh = Math.max(200, Math.min(height - MARGIN * 2, Math.max(340, Math.round(height * 0.92F))));
        l.fx = (width - l.fw) / 2;
        l.fy = Math.max(8, (height - l.fh) / 2);
        l.bodyY = l.fy + HEADER_H;
        l.footerY = l.fy + l.fh - FOOTER_H;
        l.side = l.fw >= 560;
        l.railW = clamp(l.fw / 5, 116, 160);
        l.sideW = l.side ? clamp(l.fw / 4, 158, 206) : 0;
        l.sideX = l.fx + l.fw - l.sideW - 10;
        l.mainX = l.fx + l.railW + 12;
        l.mainW = (l.side ? l.sideX - 8 : l.fx + l.fw - 12) - l.mainX;
        return l;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean inside(double x, double y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }

    private void hit(int x, int y, int w, int h, Action action) {
        hits.add(new Hit(x, y, w, h, hitClipTop, hitClipBottom, action));
    }

    /** limits new hits to the visible part of a scrolling area; MIN/MAX = no limit */
    private int hitClipTop = Integer.MIN_VALUE;
    private int hitClipBottom = Integer.MAX_VALUE;
    private double extrasScroll;
    private int extrasMaxScroll;
    private int extrasTop, extrasBottom, extrasLeft, extrasRight;

    // ---------------------------------------------------------- draw helpers

    private int textWidth(String value) {
        return textRenderer.getWidth(value);
    }

    private void text(DrawContext ctx, String value, int x, int y, int color) {
        ctx.drawText(textRenderer, value, x, y, color, true);
    }

    private void textRight(DrawContext ctx, String value, int right, int y, int color) {
        text(ctx, value, right - textWidth(value), y, color);
    }

    private void textCenter(DrawContext ctx, String value, int x, int w, int y, int color) {
        text(ctx, value, x + (w - textWidth(value)) / 2, y, color);
    }

    private void scaled(DrawContext ctx, String value, float x, float y, int color, float scale, boolean shadow) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        ctx.drawText(textRenderer, value, 0, 0, color, shadow);
        m.popMatrix();
    }

    /** Page title (Styles, My Packs ...), one and a half times as big. y is the line of a normal title. */
    private void pageTitle(DrawContext ctx, String value, int x, int y) {
        scaled(ctx, value, x, y - 4, c.text, 1.5F, true);
    }

    private int titleWidth(String value) {
        return scaledWidth(value, 1.5F);
    }

    private int scaledWidth(String value, float scale) {
        return Math.round(textWidth(value) * scale);
    }

    private String fitText(String value, int availableWidth) {
        if (availableWidth <= 0) {
            return "";
        }
        if (textWidth(value) <= availableWidth) {
            return value;
        }
        String shortValue = value;
        while (!shortValue.isEmpty() && textWidth(shortValue + "...") > availableWidth) {
            shortValue = shortValue.substring(0, shortValue.length() - 1);
        }
        return shortValue + "...";
    }

    /** how far each row of a corner is pulled in: small parts, cards, the panel */
    private static final int[] SMALL = {2, 1};
    private static final int[] CARD = {3, 1, 1};
    private static final int[] PANEL = {5, 3, 2, 1, 1};

    /** Filled box with round corners; rows never overlap, so see-through colours stay even. */
    private static void rounded(DrawContext ctx, int x, int y, int w, int h, int[] corner, int color) {
        int r = Math.min(corner.length, Math.min(w, h) / 2);
        for (int i = 0; i < r; i++) {
            int in = corner[i];
            ctx.fill(x + in, y + i, x + w - in, y + i + 1, color);
            ctx.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
        }
        ctx.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Paints the outside of round corners in the surrounding colour (for pictures). */
    private static void roundCut(DrawContext ctx, int x, int y, int w, int h, int[] corner, int background) {
        int r = Math.min(corner.length, Math.min(w, h) / 2);
        for (int i = 0; i < r; i++) {
            int in = corner[i];
            if (in <= 0) {
                continue;
            }
            ctx.fill(x, y + i, x + in, y + i + 1, background);
            ctx.fill(x + w - in, y + i, x + w, y + i + 1, background);
            ctx.fill(x, y + h - 1 - i, x + in, y + h - i, background);
            ctx.fill(x + w - in, y + h - 1 - i, x + w, y + h - i, background);
        }
    }

    /** Filled rectangle with the four corner pixels left out - reads as a soft corner. */
    private static void soft(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w >= 6 && h >= 6) {
            rounded(ctx, x, y, w, h, SMALL, color);
            return;
        }
        if (w < 3 || h < 3) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** One pixel ring without the corner pixels. */
    private static void softLine(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w >= 6 && h >= 6) {
            // the ring around a SMALL round box
            ctx.fill(x + 2, y, x + w - 2, y + 1, color);
            ctx.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
            ctx.fill(x, y + 2, x + 1, y + h - 2, color);
            ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
            ctx.fill(x + 1, y + 1, x + 2, y + 2, color);
            ctx.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
            ctx.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
            ctx.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
            return;
        }
        ctx.fill(x + 1, y, x + w - 1, y + 1, color);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Paints the corner pixels of an image in the surrounding color. */
    private static void cutCorners(DrawContext ctx, int x, int y, int w, int h, int background) {
        if (w >= 6 && h >= 6) {
            roundCut(ctx, x, y, w, h, SMALL, background);
            return;
        }
        ctx.fill(x, y, x + 1, y + 1, background);
        ctx.fill(x + w - 1, y, x + w, y + 1, background);
        ctx.fill(x, y + h - 1, x + 1, y + h, background);
        ctx.fill(x + w - 1, y + h - 1, x + w, y + h, background);
    }

    /**
     * Draws a square preview cropped to w x h so it never gets squashed. A style
     * without a picture gets its collection's item instead of someone else's image.
     */
    private void drawPreview(DrawContext ctx, String folder, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        Identifier picture = preview(folder);
        if (picture == null) {
            ctx.fill(x, y, x + w, y + h, c.surface2);
            if (w >= 18 && h >= 18) {
                ctx.drawItem(icon(tabOf(folder)), x + (w - 16) / 2, y + (h - 16) / 2);
            }
            return;
        }
        int tex = 256;
        int regionW = tex;
        int regionH = tex;
        float u = 0;
        float v = 0;
        if (w > h) {
            regionH = Math.max(1, tex * h / w);
            v = (tex - regionH) / 2.0F;
        } else if (h > w) {
            regionW = Math.max(1, tex * w / h);
            u = (tex - regionW) / 2.0F;
        }
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, picture, x, y, u, v, w, h, regionW, regionH, tex, tex);
        if (folder.startsWith("sword_") || folder.startsWith("pickaxe_")) {
            // tools shine like enchanted ones: 12 frames of glint over the item, in your glint colour
            int frame = (int) ((System.currentTimeMillis() / 90) % 12);
            Identifier glint = Identifier.of(BaulooStylesClient.MOD_ID, "textures/preview_glint/" + folder + "_" + frame + ".png");
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, glint, x, y, u, v, w, h, regionW, regionH, tex, tex, glintTint());
        }
    }

    /** The glint colour for the tool previews: your Enchant Glint colour, else the vanilla purple. */
    private static int glintTint() {
        if (StylesUiConfig.glintRainbow) {
            return StylesTheme.hsv((System.currentTimeMillis() % 4000L) / 4000.0F, 0.6F, 1.0F) | 0xC0000000;
        }
        int rgb = StylesUiConfig.glintCustom ? StylesUiConfig.glintColor & 0xFFFFFF : 0xA060FF;
        return 0xC0000000 | rgb;
    }

    /** Small square: the active style's picture, or the collection's item when vanilla. */
    private void drawThumb(DrawContext ctx, Tab value, int x, int y, int size, int background) {
        String folder = active.get(value);
        if (folder == null) {
            soft(ctx, x, y, size, size, c.surface2);
            ctx.drawItem(icon(value), x + (size - 16) / 2, y + (size - 16) / 2);
        } else {
            drawPreview(ctx, folder, x, y, size, size);
            cutCorners(ctx, x, y, size, size, background);
        }
    }

    private enum ButtonStyle { NORMAL, PRIMARY, DISABLED }

    private void button(DrawContext ctx, int x, int y, int w, int h, String label, boolean hover, ButtonStyle style) {
        int fill;
        int color;
        switch (style) {
            case PRIMARY -> {
                fill = hover ? c.accentBright : c.accent;
                color = c.onAccent;
            }
            case DISABLED -> {
                fill = c.surface;
                color = c.textDim;
            }
            default -> {
                fill = hover ? c.surfaceHover : c.surface2;
                color = hover ? c.text : c.textSoft;
            }
        }
        soft(ctx, x, y, w, h, fill);
        if (!label.isEmpty()) {
            String fitted = fitText(label, w - 8);
            ctx.drawText(textRenderer, fitted, x + (w - textWidth(fitted)) / 2, y + (h - 8) / 2, color,
                    style != ButtonStyle.PRIMARY);
        }
    }

    // ----------------------------------------------------------------- render

    // ----------------------------------------------------------------- always GUI scale 2

    /** The menu is laid out as if the GUI scale were this, whatever it really is. */
    private static final double UI_SCALE = 2.0;
    private int realWidth;
    private int realHeight;
    private boolean scaled;

    /** How much the whole menu is scaled on screen (1 at GUI scale 2, 0.667 at 3, 0.5 at 4). */
    private float uiFactor() {
        double real = MinecraftClient.getInstance().getWindow().getScaleFactor();
        return real <= 0 ? 1.0F : (float) (UI_SCALE / real);
    }

    /** width/height become the size the menu would have at GUI scale 2. */
    private void enterScale() {
        if (scaled) {
            return;
        }
        float f = uiFactor();
        realWidth = width;
        realHeight = height;
        width = Math.round(realWidth / f);
        height = Math.round(realHeight / f);
        scaled = true;
    }

    private void leaveScale() {
        if (scaled) {
            width = realWidth;
            height = realHeight;
            scaled = false;
        }
    }

    private Click scaledClick(Click click) {
        float f = uiFactor();
        return new Click(click.x() / f, click.y() / f, click.buttonInfo());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float f = uiFactor();
        enterScale();
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.scale(f, f);
        try {
            renderUi(ctx, Math.round(mouseX / f), Math.round(mouseY / f), delta);
        } finally {
            m.popMatrix();
            leaveScale();
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        enterScale();
        try {
            return clickedUi(scaledClick(click), doubled);
        } finally {
            leaveScale();
        }
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        float f = uiFactor();
        enterScale();
        try {
            return draggedUi(scaledClick(click), offsetX / f, offsetY / f);
        } finally {
            leaveScale();
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        enterScale();
        try {
            return releasedUi(scaledClick(click));
        } finally {
            leaveScale();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float f = uiFactor();
        enterScale();
        try {
            return scrolledUi(mouseX / f, mouseY / f, horizontalAmount, verticalAmount);
        } finally {
            leaveScale();
        }
    }

    private void renderUi(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (liveDrag()) {
            drawLiveSlider(ctx);
            return;
        }
        super.render(ctx, mouseX, mouseY, delta);
        String access = VipPacks.earlyActive() + "/" + VipPacks.admin();
        if (!access.equals(earlyBound)) {
            earlyBound = access;
            Tab.bindCloudSlots();   // early and admin collections in or out of the rail, right away
        }
        if (cloudVersion != StylesCloud.version() || uploadVersion != StylesUploadManager.version()
                || bindVersion != Tab.bindVersion) {
            double keepScroll = gridScroll;
            rebuild();
            gridScroll = keepScroll;
        }
        c = StylesUiConfig.theme();
        active = activeFolders();
        hits.clear();
        tooltip = null;
        Layout l = layout();
        if (!whatsNewChecked) {
            whatsNewChecked = true;
            whatsNew = WhatsNew.due();
        }
        if (updateAsk == null && whatsNew == null) {
            String v = StylesUpdater.availableVersion;
            if (v != null && !v.equals(StylesUiConfig.updateSeen)) {
                updateAsk = v;
            }
        }
        boolean modal = zoomFolder != null || pendingPreset != null || updateAsk != null || resetAsk || whatsNew != null;
        int mx = modal ? -10000 : mouseX;
        int my = modal ? -10000 : mouseY;

        ctx.fill(0, 0, width, height, c.backdrop);
        drawGlassPanel(ctx, l);
        if (c.spooky) {
            HalloweenFx.behind(ctx, l.fx, l.fy, l.fw, HEADER_H, l.mainX, l.mainW, l.footerY);
        }

        drawHeader(ctx, l, mx, my);
        drawRail(ctx, l, mx, my);
        hoveredEntry = -1;
        Cosmetics.tryOn(null, null);
        if (cosmeticsView && !publicView) {
            drawCosmetics(ctx, l, mx, my);
        } else if (publicView) {
            drawPublicView(ctx, l, mx, my);
        } else if (extrasView) {
            drawExtrasTiles(ctx, l, mx, my);
        } else if (!l.side && presetsView) {
            pageTitle(ctx, "Presets", l.mainX, l.bodyY + 11);
            drawPresets(ctx, l.mainX, l.bodyY + 30, l.mainW, l.footerY - 10, mx, my);
        } else if (!l.side && compactThemes) {
            pageTitle(ctx, "Themes", l.mainX, l.bodyY + 11);
            drawThemes(ctx, l.mainX, l.bodyY + 30, l.mainW, l.footerY - 10, mx, my, c.frame);
        } else {
            drawMain(ctx, l, mx, my);
        }
        if (l.side && !extrasView && !publicView && !cosmeticsView) {
            drawSide(ctx, l, mx, my);
        }
        drawFooter(ctx, l, mx, my);
        if (c.spooky) {
            HalloweenFx.front(ctx, l.fx, l.fy, l.fw, l.fh, HEADER_H, l.footerY, l.side, l.sideX, l.sideW);
        }

        if (tooltip != null) {
            int tw = textWidth(tooltip) + 10;
            int tx = Math.min(tooltipX, width - tw - 4);
            soft(ctx, tx, tooltipY, tw, 14, c.surface2);
            softLine(ctx, tx, tooltipY, tw, 14, c.borderStrong);
            text(ctx, tooltip, tx + 5, tooltipY + 3, c.text);
        }
        if (pendingPreset != null) {
            drawExtrasAsk(ctx, mouseX, mouseY);
        }
        if (updateAsk != null && pendingPreset == null) {
            drawUpdateAsk(ctx, mouseX, mouseY);
        }
        if (whatsNew != null && pendingPreset == null && updateAsk == null) {
            drawWhatsNew(ctx, mouseX, mouseY);
        }
        if (resetAsk) {
            drawResetAsk(ctx, mouseX, mouseY);
        }
        if (zoomFolder != null) {
            drawZoom(ctx);
        }
    }

    private static final Identifier TORNADO = Identifier.of(BaulooStylesClient.MOD_ID, "textures/gui/tornado.png");
    private void drawHeader(DrawContext ctx, Layout l, int mx, int my) {
        int x = l.fx;
        int y = l.fy;
        ctx.fill(x + 10, y + HEADER_H - 1, x + l.fw - 10, y + HEADER_H, c.borderDim);

        String version = StylesUpdater.installedVersion == null ? "" : "v" + StylesUpdater.installedVersion;
        int badgeEnd = x + 33 + Math.max(textWidth("Bauloo Styles"), scaledWidth(version, 0.8F));
        if (c.spooky) {
            // Halloween: moon glow, a pumpkin instead of the tornado, and a badge
            HalloweenFx.moon(ctx, x, y, l.fw, HEADER_H);
            HalloweenFx.pumpkin(ctx, x + 7, y + 6, 24);
            int bx = badgeEnd + 10;
            int bw = scaledWidth("HALLOWEEN", 0.75F) + 10;
            soft(ctx, bx, y + 13, bw, 12, c.accent);
            scaled(ctx, "HALLOWEEN", bx + 5, y + 16, c.onAccent, 0.75F, false);
            badgeEnd = bx + bw;
        } else {
            // the bauloo tornado (same as the launcher), tinted in the theme accent
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, TORNADO, x + 9, y + 8, 0, 0, 20, 20, 240, 240, 240, 240, c.accent);
        }
        text(ctx, "Bauloo Styles", x + 33, y + 11, c.text);
        scaled(ctx, version, x + 33, y + 23, c.textDim, 0.8F, false);

        // Loadout: what is active in every collection, click to jump there.
        Tab[] tabs = shownTabs();
        int size = 18;
        int step = size + 5;
        int stripW = tabs.length * step - 5;
        int sx = x + l.fw - 14 - stripW;
        int sy = y + 9;
        int left = badgeEnd + 20;
        if (c.spooky) {
            // bats in the free space between the badge and the loadout
            HalloweenFx.bats(ctx, left, sx - scaledWidth("LOADOUT", 0.75F) - 16, y + 6);
        }
        if (sx - scaledWidth("LOADOUT", 0.75F) - 8 < left) {
            return;
        }
        scaled(ctx, "LOADOUT", sx - scaledWidth("LOADOUT", 0.75F) - 8, sy + 7, c.textDim, 0.75F, false);
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int tx = sx + i * step;
            boolean hover = inside(mx, my, tx, sy, size, size + 5);
            drawThumb(ctx, t, tx, sy, size, c.header);
            if (t == tab) {
                soft(ctx, tx + 2, sy + size + 2, size - 4, 2, c.accent);
            } else if (hover) {
                soft(ctx, tx + 5, sy + size + 2, size - 10, 2, c.textSoft);
            }
            if (hover) {
                tooltip = t.title + ": " + activeName(t);
                tooltipX = tx - 20;
                tooltipY = sy + size + 7;
            }
            final Tab target = t;
            hit(tx, sy, size, size + 5, (b, px, py) -> switchTab(target));
        }
    }

    private void drawRail(DrawContext ctx, Layout l, int mx, int my) {
        int x = l.fx;
        int y = l.bodyY;
        int w = l.railW;
        ctx.fill(x + w, y + 8, x + w + 1, l.footerY - 8, c.borderDim);
        // COLLECTIONS and EXTRAS work like two tabs: click one to switch
        {
            int cw = textWidth("COLLECTIONS");
            boolean hover = inside(mx, my, x + 9, y + 8, cw + 6, 16);
            text(ctx, "COLLECTIONS", x + 12, y + 12, !extrasView ? c.accent : (hover ? c.text : c.textDim));
            if (!extrasView) {
                soft(ctx, x + 12, y + 23, cw, 2, c.accent);
            }
            hit(x + 9, y + 8, cw + 6, 16, (b, px, py) -> {
                extrasView = false;
                publicView = false;
                cosmeticsView = false;
            });
        }

        // EXTRAS as a plain text tab on the same line, like All / Favorites
        {
            String label = "EXTRAS";
            int lw = textWidth(label);
            int lx = x + w - 10 - lw;
            if (lx > x + 12 + textWidth("COLLECTIONS") + 6) {
                boolean hover = inside(mx, my, lx - 3, y + 8, lw + 6, 16);
                text(ctx, label, lx, y + 12, extrasView ? c.accent : (hover ? c.text : c.textSoft));
                if (extrasView) {
                    soft(ctx, lx, y + 23, lw, 2, c.accent);
                }
                hit(lx - 3, y + 8, lw + 6, 16, (b, px, py) -> {
                    extrasView = !extrasView;
                    publicView = false;
                    cosmeticsView = false;
                    searchFocused = false;
                });
            }
        }


        int rowY = y + 30;
        // Shrink the rows when the window is too short for all collections.
        // every collection plus Cosmetics (and its divider) has to fit above the footer
        // every collection, My Packs, Bauloo's Packs and Cosmetics (plus the divider) has to fit above the footer
        int rows = shownTabs().length + 2 + (MyPacks.shown() ? 1 : 0) + (VipPacks.shown() ? 1 : 0);
        int rowGap = (l.footerY - rowY - 26) / rows < 17 ? 1 : 4;   // short windows: rows closer together
        int rowH = Math.max(12, Math.min(30, (l.footerY - rowY - 26) / rows - rowGap));
        for (Tab t : shownTabs()) {
            int rx = x + 7;
            int rw = w - 14;
            boolean selected = t == tab && !extrasView && !cosmeticsView && !myPacksView;
            boolean hover = inside(mx, my, rx, rowY, rw, rowH);
            int rowBg = c.rail;
            if (selected) {
                soft(ctx, rx, rowY, rw, rowH, c.surface2);
                soft(ctx, rx, rowY + 5, 2, rowH - 10, c.accent);
                rowBg = c.surface2;
            } else if (hover) {
                soft(ctx, rx, rowY, rw, rowH, c.surface);
                rowBg = c.surface;
            }

            boolean compact = rowH < 30;
            int thumb = compact ? rowH - 6 : 20;
            drawThumb(ctx, t, rx + 7, rowY + (rowH - thumb) / 2, thumb, rowBg);

            String count = String.valueOf(styleCount(t));
            textRight(ctx, count, rx + rw - 7, rowY + (rowH - 8) / 2, selected ? c.accent : c.textDim);
            if (pendingIn(t)) {
                // something is selected in this collection but not applied yet
                int dx = rx + rw - 7 - textWidth(count) - 7;
                int dy = rowY + rowH / 2 - 2;
                ctx.fill(dx, dy, dx + 4, dy + 4, PENDING_COLOR);
            }
            int textW = rw - 34 - textWidth(count) - 12;
            if (compact) {
                text(ctx, fitText(t.title, textW), rx + 34, rowY + (rowH - 8) / 2, selected ? c.text : c.textSoft);
            } else {
                text(ctx, fitText(t.title, textW), rx + 34, rowY + 5, selected ? c.text : c.textSoft);
                boolean vanilla = active.get(t) == null;
                scaled(ctx, fitText(activeName(t), Math.round(textW / 0.8F)), rx + 34, rowY + 17,
                        vanilla ? c.textDim : (selected ? c.accentBright : c.textSoft), 0.8F, false);
            }

            final Tab target = t;
            hit(rx, rowY, rw, rowH, (b, px, py) -> switchTab(target));
            rowY += rowH + rowGap;
        }

        // My Packs and Cosmetics: their own pages, set apart from the collections by a line
        ctx.fill(x + 12, rowY + 1, x + w - 12, rowY + 2, c.borderDim);
        rowY += 6;
        if (MyPacks.shown()) {
            int rx = x + 7;
            int rw = w - 14;
            boolean selected = myPacksView && !vipView && !extrasView && !cosmeticsView && !publicView;
            boolean hover = inside(mx, my, rx, rowY, rw, rowH);
            if (selected) {
                soft(ctx, rx, rowY, rw, rowH, c.surface2);
                soft(ctx, rx, rowY + 5, 2, rowH - 10, c.accent);
            } else if (hover) {
                soft(ctx, rx, rowY, rw, rowH, c.surface);
            }
            int thumb = rowH < 30 ? rowH - 6 : 20;
            soft(ctx, rx + 7, rowY + (rowH - thumb) / 2, thumb, thumb, c.accentDark);
            ctx.drawItem(new ItemStack(Items.BOOKSHELF), rx + 7 + (thumb - 16) / 2, rowY + (rowH - 16) / 2);
            String count = MyPacks.scanning() ? "..." : String.valueOf(MyPacks.styles().size());
            textRight(ctx, count, rx + rw - 7, rowY + (rowH - 8) / 2, selected ? c.text : c.textDim);
            text(ctx, fitText("My Packs", rw - 34 - textWidth(count) - 14), rx + 34, rowY + (rowH - 8) / 2,
                    selected ? c.text : c.textSoft);
            hit(rx, rowY, rw, rowH, (b, px, py) -> {
                myPacksView = true;
                vipView = false;
                cosmeticsView = false;
                extrasView = false;
                publicView = false;
                searchFocused = false;
                query = "";
                favoritesOnly = false;
                gridScroll = 0;
                focusedIndex = 0;
                rebuild();
            });
            rowY += rowH + rowGap;
        }
        if (VipPacks.shown()) {
            // bauloo's own packs, for VIPs: the My Packs page with his collection
            int rx = x + 7;
            int rw = w - 14;
            boolean selected = myPacksView && vipView && !extrasView && !cosmeticsView && !publicView;
            boolean hover = inside(mx, my, rx, rowY, rw, rowH);
            if (selected) {
                soft(ctx, rx, rowY, rw, rowH, c.surface2);
                soft(ctx, rx, rowY + 5, 2, rowH - 10, 0xFFFFC23D);
            } else if (hover) {
                soft(ctx, rx, rowY, rw, rowH, c.surface);
            }
            int thumb = rowH < 30 ? rowH - 6 : 20;
            soft(ctx, rx + 7, rowY + (rowH - thumb) / 2, thumb, thumb, 0xFF5A4A1C);
            ctx.drawItem(new ItemStack(Items.ENCHANTED_BOOK), rx + 7 + (thumb - 16) / 2, rowY + (rowH - 16) / 2);
            VipPacks.refresh(false);   // the count is right before the page was opened (asks at most every 10 minutes)
            String count = VipPacks.loading() && VipPacks.styles().isEmpty() ? "..." : String.valueOf(VipPacks.styles().size());
            textRight(ctx, count, rx + rw - 7, rowY + (rowH - 8) / 2, selected ? c.text : c.textDim);
            int tagW = scaledWidth("VIP", 0.7F) + 6;
            int labelW = rw - 34 - textWidth(count) - 14 - tagW - 4;
            String label = fitText("Bauloo's Packs", labelW);
            text(ctx, label, rx + 34, rowY + (rowH - 8) / 2, selected ? c.text : c.textSoft);
            int tx = rx + 34 + textWidth(label) + 4;
            soft(ctx, tx, rowY + (rowH - 9) / 2, tagW, 9, 0xFFFFC23D);
            scaled(ctx, "VIP", tx + 3, rowY + (rowH - 9) / 2 + 2, 0xFF1A1300, 0.7F, false);
            hit(rx, rowY, rw, rowH, (b, px, py) -> {
                myPacksView = true;
                vipView = true;
                cosmeticsView = false;
                extrasView = false;
                publicView = false;
                searchFocused = false;
                query = "";
                favoritesOnly = false;
                gridScroll = 0;
                focusedIndex = 0;
                VipPacks.refresh(true);
                rebuild();
            });
            rowY += rowH + rowGap;
        }
        {
            int rx = x + 7;
            int rw = w - 14;
            boolean selected = cosmeticsView && !publicView;
            if (selected && !StylesUiConfig.cosmeticsSeen) {
                StylesUiConfig.cosmeticsSeen = true;
                StylesUiConfig.save();
            }
            boolean hover = inside(mx, my, rx, rowY, rw, rowH);
            if (selected) {
                soft(ctx, rx, rowY, rw, rowH, c.surface2);
                soft(ctx, rx, rowY + 5, 2, rowH - 10, c.accent);
            } else if (hover) {
                soft(ctx, rx, rowY, rw, rowH, c.surface);
            }
            int thumb = rowH < 30 ? rowH - 6 : 20;
            soft(ctx, rx + 7, rowY + (rowH - thumb) / 2, thumb, thumb, c.accentDark);
            ctx.drawItem(new ItemStack(Items.ELYTRA), rx + 7 + (thumb - 16) / 2, rowY + (rowH - 16) / 2);
            int worn = 0;
            for (Cosmetics.Slot slot : Cosmetics.Slot.values()) {
                if (Cosmetics.valid(slot, Cosmetics.saved(slot))) {
                    worn++;
                }
            }
            int bw = 0;
            if (!StylesUiConfig.cosmeticsSeen) {
                String badge = "NEW";
                bw = scaledWidth(badge, 0.7F) + 6;
                soft(ctx, rx + rw - 7 - bw, rowY + (rowH - 9) / 2, bw, 9, c.accent);
                scaled(ctx, badge, rx + rw - 4 - bw, rowY + (rowH - 9) / 2 + 2, c.onAccent, 0.7F, false);
            }
            int textW = rw - 34 - bw - 12;
            if (rowH < 30) {
                text(ctx, fitText("Cosmetics", textW), rx + 34, rowY + (rowH - 8) / 2, selected ? c.text : c.textSoft);
            } else {
                text(ctx, fitText("Cosmetics", textW), rx + 34, rowY + 5, selected ? c.text : c.textSoft);
                scaled(ctx, fitText(worn == 0 ? "Nothing worn" : worn + " worn", Math.round(textW / 0.8F)), rx + 34,
                        rowY + 17, worn == 0 ? c.textDim : (selected ? c.accentBright : c.textSoft), 0.8F, false);
            }
            hit(rx, rowY, rw, rowH, (b, px, py) -> {
                cosmeticsView = true;
                myPacksView = false;
                extrasView = false;
                publicView = false;
                searchFocused = false;
            });
            rowY += rowH + rowGap;
        }
        {
            // My Drawings: opens the drawing screen; drawings show up in their collection as LOCAL
            int rx = x + 7;
            int rw = w - 14;
            boolean hover = inside(mx, my, rx, rowY, rw, rowH);
            if (hover) {
                soft(ctx, rx, rowY, rw, rowH, c.surface);
            }
            int thumb = rowH < 30 ? rowH - 6 : 20;
            soft(ctx, rx + 7, rowY + (rowH - thumb) / 2, thumb, thumb, c.accentDark);
            ctx.drawItem(new ItemStack(Items.WRITABLE_BOOK), rx + 7 + (thumb - 16) / 2, rowY + (rowH - 16) / 2);
            String count = String.valueOf(DrawScreen.count());
            textRight(ctx, count, rx + rw - 7, rowY + (rowH - 8) / 2, c.textDim);
            text(ctx, fitText("My Drawings", rw - 34 - textWidth(count) - 14), rx + 34, rowY + (rowH - 8) / 2,
                    hover ? c.text : c.textSoft);
            hit(rx, rowY, rw, rowH, (b, px, py) -> MinecraftClient.getInstance().setScreen(new DrawScreen(this)));
            rowY += rowH + rowGap;
        }


        int uy = rowY + 8;
        if (uy + 34 < l.footerY) {
            ctx.fill(x + 12, uy, x + w - 12, uy + 1, c.borderDim);
            boolean hover = inside(mx, my, x + 7, uy + 6, w - 14, 28);
            if (hover) {
                soft(ctx, x + 7, uy + 6, w - 14, 28, c.surface);
            }
            int total = 0;
            for (int n : uploadCounts.values()) {
                total += n;
            }
            text(ctx, "PRIVATE UPLOADS", x + 12, uy + 10, hover ? c.textSoft : c.textDim);
            String line = hover ? "Upload a texture pack" : total + (total == 1 ? " pack on this PC" : " packs on this PC");
            text(ctx, fitText(line, w - 24), x + 12, uy + 22, hover ? c.accent : c.textDim);
            hit(x + 7, uy + 6, w - 14, 28, (b, px, py) -> StylesUploadServer.open());
        }
    }

    private void drawMain(DrawContext ctx, Layout l, int mx, int my) {
        int x = l.mainX;
        int y = l.bodyY;
        int w = l.mainW;

        if (myPacksView) {
            drawMyPacksMain(ctx, l, mx, my);
            return;
        }
        pageTitle(ctx, tab.title, x, y + 11);
        if (tab == Tab.SKY && !skyboxifyInstalled) {
            text(ctx, fitText("Skyboxify is required for Sky styles", w / 2), x, y + 24, c.accent);
        } else {
            String part1 = styleCount(tab) + " styles  -  ";
            text(ctx, part1, x, y + 24, c.textDim);
            text(ctx, fitText(activeName(tab), w / 3), x + textWidth(part1), y + 24,
                    active.get(tab) == null ? c.textSoft : c.good);
        }
        if (tab == Tab.INVENTORY) {
            // no dark veil / blur behind the inventory - a switch right under the title line
            headerToggle(ctx, x, y + 36, "CLEAR BACKGROUND", StylesUiConfig.invClearBackground, mx, my,
                    "No dark veil or blur behind your inventory and chests", () -> {
                        StylesUiConfig.invClearBackground = !StylesUiConfig.invClearBackground;
                        BlurCompat.apply();
                    });
        } else if (tab == Tab.SKY) {
            drawTimeChanger(ctx, x, y + 36, mx, my);
        } else if (cobwebTab()) {
            headerToggle(ctx, x, y + 36, "COBWEB OUTLINE", StylesUiConfig.cobwebOutline, mx, my,
                    "A white box around every cobweb near you",
                    () -> StylesUiConfig.cobwebOutline = !StylesUiConfig.cobwebOutline);
        }

        // All / Favorites, plain text tabs with a soft underline
        int right = x + w;
        String favCount = String.valueOf(favoriteCount());
        String favLabel = "Favorites";
        int favW = textWidth("★ ") + textWidth(favLabel) + 4 + textWidth(favCount);
        int allW = textWidth("All");
        int favX = right - favW;
        int allX = favX - 14 - allW;
        int ty = y + 14;

        boolean allHover = inside(mx, my, allX - 3, ty - 3, allW + 6, 16);
        text(ctx, "All", allX, ty, !favoritesOnly ? c.text : (allHover ? c.textSoft : c.textDim));
        if (!favoritesOnly) {
            soft(ctx, allX, ty + 11, allW, 2, c.accent);
        }
        hit(allX - 3, ty - 3, allW + 6, 16, (b, px, py) -> {
            favoritesOnly = false;
            gridScroll = 0;
            applyFilter();
        });

        boolean favHover = inside(mx, my, favX - 3, ty - 3, favW + 6, 16);
        text(ctx, "★", favX, ty, c.gold);
        int fx2 = favX + textWidth("★ ");
        text(ctx, favLabel, fx2, ty, favoritesOnly ? c.text : (favHover ? c.textSoft : c.textDim));
        text(ctx, favCount, fx2 + textWidth(favLabel) + 4, ty, favoritesOnly ? c.accent : c.textDim);
        if (favoritesOnly) {
            soft(ctx, favX, ty + 11, favW, 2, c.accent);
        }
        hit(favX - 3, ty - 3, favW + 6, 16, (b, px, py) -> {
            favoritesOnly = true;
            gridScroll = 0;
            applyFilter();
        });

        // search
        int titleW = Math.max(titleWidth(tab.title), textWidth(styleCount(tab) + " styles  -  " + activeName(tab)));
        int searchW = Math.min(130, allX - 16 - (x + titleW + 12));
        if (searchW >= 64) {
            int sx = allX - 16 - searchW;
            int sy = y + 10;
            boolean hover = inside(mx, my, sx, sy, searchW, 16);
            soft(ctx, sx, sy, searchW, 16, hover || searchFocused ? c.surface2 : c.surface);
            if (searchFocused) {
                soft(ctx, sx + 2, sy + 14, searchW - 4, 1, c.accent);
            }
            softLine(ctx, sx + 5, sy + 4, 6, 6, c.textDim);
            ctx.fill(sx + 10, sy + 10, sx + 12, sy + 12, c.textDim);
            int textX = sx + 16;
            int textW = searchW - 20;
            if (query.isEmpty() && !searchFocused) {
                text(ctx, fitText("Search", textW), textX, sy + 4, c.textDim);
            } else {
                boolean blink = searchFocused && (System.currentTimeMillis() / 500) % 2 == 0;
                String shown = query;
                while (!shown.isEmpty() && textWidth(shown + "_") > textW) {
                    shown = shown.substring(1);
                }
                text(ctx, shown + (blink ? "_" : ""), textX, sy + 4, c.text);
            }
            hit(sx, sy, searchW, 16, (b, px, py) -> searchFocused = true);
        }

        ctx.fill(x, y + 36, x + w, y + 37, c.borderDim);
        drawGrid(ctx, x, y + (tab == Tab.INVENTORY || tab == Tab.SKY || cobwebTab() ? 58 : 44), w, l.footerY - 10, mx, my);
    }

    /** My Packs: title, scan state, RESCAN and search, then the grid grouped by collection. */
    private void drawMyPacksMain(DrawContext ctx, Layout l, int mx, int my) {
        if (vipView) {
            drawVipMain(ctx, l, mx, my);
            return;
        }
        if (!MyPacks.shown()) {
            myPacksView = false;
            rebuild();
            return;
        }
        int x = l.mainX;
        int y = l.bodyY;
        int w = l.mainW;
        if (myPacksVersion != MyPacks.version()) {
            double keep = gridScroll;
            rebuild();
            gridScroll = keep;
        }
        pageTitle(ctx, "My Packs", x, y + 11);
        String state = MyPacks.scanning()
                ? "Scanning your packs  " + MyPacks.scanned() + " / " + MyPacks.total()
                : MyPacks.packCount() + " packs in your folder  -  " + MyPacks.styles().size() + " styles";
        text(ctx, fitText(state, w / 2), x, y + 24, MyPacks.scanning() ? c.accent : c.textDim);

        String label = MyPacks.scanning() ? "SCANNING ..." : "RESCAN";
        int bw = textWidth(label) + 16;
        int bx = x + w - bw;
        button(ctx, bx, y + 9, bw, 16, label, inside(mx, my, bx, y + 9, bw, 16),
                MyPacks.scanning() ? ButtonStyle.DISABLED : ButtonStyle.NORMAL);
        hit(bx, y + 9, bw, 16, (b, px, py) -> MyPacks.rescan());

        int titleW = Math.max(titleWidth("My Packs"), textWidth(state));
        int searchW = Math.min(130, bx - 16 - (x + Math.min(titleW, w / 2) + 12));
        if (searchW >= 64) {
            int sx = bx - 12 - searchW;
            int sy = y + 9;
            boolean hover = inside(mx, my, sx, sy, searchW, 16);
            soft(ctx, sx, sy, searchW, 16, hover || searchFocused ? c.surface2 : c.surface);
            if (searchFocused) {
                soft(ctx, sx + 2, sy + 14, searchW - 4, 1, c.accent);
            }
            softLine(ctx, sx + 5, sy + 4, 6, 6, c.textDim);
            ctx.fill(sx + 10, sy + 10, sx + 12, sy + 12, c.textDim);
            String shown = query.isEmpty() && !searchFocused ? "Search a pack" : query;
            text(ctx, fitText(shown, searchW - 20), sx + 16, sy + 4, query.isEmpty() ? c.textDim : c.text);
            hit(sx, sy, searchW, 16, (b, px, py) -> searchFocused = true);
        }
        ctx.fill(x, y + 36, x + w, y + 37, c.borderDim);
        if (entries.isEmpty()) {
            String why = MyPacks.scanning() ? "Looking through your resourcepacks folder ..."
                    : "No styles found. Put texture packs (.zip) into your resourcepacks folder, then RESCAN.";
            text(ctx, fitText(why, w), x, y + 52, c.textDim);
            return;
        }
        drawGrid(ctx, x, y + 44, w, l.footerY - 10, mx, my);
    }

    /** Bauloo's Packs page: title with the VIP tag, how many styles, search; the grid below */
    private void drawVipMain(DrawContext ctx, Layout l, int mx, int my) {
        if (!VipPacks.shown()) {
            myPacksView = false;
            vipView = false;
            rebuild();
            return;
        }
        int x = l.mainX;
        int y = l.bodyY;
        int w = l.mainW;
        if (vipVersion != VipPacks.version()) {
            double keep = gridScroll;
            rebuild();
            gridScroll = keep;
        }
        pageTitle(ctx, "Bauloo's Packs", x, y + 11);
        int tagW = scaledWidth("VIP", 0.75F) + 8;
        int tx = x + titleWidth("Bauloo's Packs") + 6;
        soft(ctx, tx, y + 10, tagW, 10, 0xFFFFC23D);
        scaled(ctx, "VIP", tx + 4, y + 12, 0xFF1A1300, 0.75F, false);
        String state = !VipPacks.status().isEmpty() ? VipPacks.status()
                : VipPacks.loading() && VipPacks.styles().isEmpty() ? "Loading bauloo's packs ..."
                : VipPacks.styles().size() + " styles from bauloo's own packs";
        text(ctx, fitText(state, w / 2), x, y + 24, VipPacks.status().isEmpty() ? c.textDim : 0xFFFFC23D);

        int titleW = Math.max(titleWidth("Bauloo's Packs") + tagW + 6, textWidth(state));
        int searchW = Math.min(150, x + w - 16 - (x + Math.min(titleW, w / 2) + 12));
        if (searchW >= 64) {
            int sx = x + w - searchW;
            int sy = y + 9;
            boolean hover = inside(mx, my, sx, sy, searchW, 16);
            soft(ctx, sx, sy, searchW, 16, hover || searchFocused ? c.surface2 : c.surface);
            if (searchFocused) {
                soft(ctx, sx + 2, sy + 14, searchW - 4, 1, c.accent);
            }
            softLine(ctx, sx + 5, sy + 4, 6, 6, c.textDim);
            ctx.fill(sx + 10, sy + 10, sx + 12, sy + 12, c.textDim);
            String shown = query.isEmpty() && !searchFocused ? "Search a pack" : query;
            text(ctx, fitText(shown, searchW - 20), sx + 16, sy + 4, query.isEmpty() ? c.textDim : c.text);
            hit(sx, sy, searchW, 16, (b, px, py) -> searchFocused = true);
        }
        ctx.fill(x, y + 36, x + w, y + 37, c.borderDim);
        if (entries.isEmpty()) {
            text(ctx, fitText(VipPacks.loading() ? "Loading ..." : "No styles here yet - bauloo is still adding them", w), x, y + 52, c.textDim);
            return;
        }
        drawGrid(ctx, x, y + 44, w, l.footerY - 10, mx, my);
    }

    /** "From your pack": copies just that part into your uploads (in the background), then applies it */
    private void useMyPack(String folder, Tab target) {
        MyPacks.Style style = MyPacks.byId(folder.substring(MYPACK.length()));
        if (style == null || pendingMyPack) {
            return;
        }
        pendingMyPack = true;
        Thread thread = new Thread(() -> {
            String id = MyPacks.use(style);
            MinecraftClient.getInstance().execute(() -> {
                pendingMyPack = false;
                if (id != null && MinecraftClient.getInstance().currentScreen == this) {
                    applyAndRefresh(id, target);
                }
            });
        }, "Bauloo Styles my pack");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean pendingMyPack;

    private void drawGrid(DrawContext ctx, int x, int top, int w, int bottom, int mx, int my) {
        gridLeft = x;
        gridRight = x + w;
        gridTop = top;
        gridBottom = bottom;
        int h = bottom - top;
        int count = visible.size();
        if (count == 0) {
            String msg = favoritesOnly && query.isEmpty()
                    ? "No favorites yet - hover a style and press F"
                    : "No styles match \"" + query + "\"";
            textCenter(ctx, fitText(msg, w), x, w, top + h / 2 - 4, c.textDim);
            gridMaxScroll = 0;
            return;
        }

        int labelH = LABEL_H;
        int baseCols = w >= 500 ? 4 : w >= 290 ? 3 : 2;
        int cols = baseCols;
        int cardW = 0;
        int cardH = 0;
        boolean fits = false;
        for (int tryCols = Math.min(baseCols, count); tryCols <= 8; tryCols++) {
            int cw = (w - (tryCols - 1) * GAP) / tryCols;
            int[][] spots = place(tryCols);
            int rows = spots[count - 1][0] + 1;
            int headersH = spots[count - 1][2] * GROUP_H;
            int fitH = Math.min(cw * 9 / 10, (h - headersH - (rows - 1) * GAP) / rows - labelH);
            if (fitH >= 30 && fitH >= cw * 0.55F) {
                cols = tryCols;
                cardW = cw;
                cardH = fitH;
                fits = true;
                break;
            }
        }
        if (!fits) {
            // too many to show at once (My Packs): small cards like a full collection, then scroll
            cols = myPacksView ? Math.max(baseCols, Math.min(7, w / 56)) : baseCols;
            cardW = (w - (cols - 1) * GAP) / cols;
            cardH = Math.max(34, myPacksView ? cardW * 7 / 10 : cardW * 3 / 4);
        }
        int step = cardH + labelH + GAP;
        int[][] spots = place(cols);
        int rows = spots[count - 1][0] + 1;
        int contentH = rows * step - GAP + spots[count - 1][2] * GROUP_H;
        gridMaxScroll = Math.max(0, contentH - h);
        gridScroll = Math.max(0, Math.min(gridScroll, gridMaxScroll));

        ctx.enableScissor(x - 2, top - 2, x + w + 2, bottom + 1);
        for (int k = 0; k < count; k++) {
            int index = visible.get(k);
            int cx = x + spots[k][1] * (cardW + GAP);
            int cy = top + spots[k][0] * step + spots[k][2] * GROUP_H - (int) gridScroll;
            if (spots[k][2] > 0 && (k == 0 || spots[k][2] != spots[k - 1][2])) {
                // group title above the first card of Swords / Pickaxes
                int hy = cy - GROUP_H + 1;
                if (hy + GROUP_H > top - 2 && hy < bottom) {
                    String title = switch (tabOf(index)) {
                        case PICKAXE -> "PICKAXES";
                        case AXE -> "AXES";
                        case TOTEM -> "TOTEMS";
                        case SWORD -> "SWORDS";
                        case GAP -> "GAPS";
                        case PEARL -> "PEARLS";
                        case GLOWSTONE -> "GLOWSTONE";
                        case CRYSTAL -> "CRYSTALS";
                        case OBSIDIAN -> "OBSIDIAN";
                        case ARMOR -> "ARMOR";
                        case HOTBAR -> "HOTBARS";
                        case FIRE -> "FIRE";
                        case SKY -> "SKIES";
                        default -> "ANCHORS";
                    };
                    text(ctx, title, x, hy, c.accent);
                    int lx = x + textWidth(title) + 6;
                    ctx.fill(lx, hy + 4, x + w, hy + 5, c.borderDim);
                }
            }
            if (cy + cardH + labelH < top - 2 || cy > bottom) {
                continue;
            }
            boolean hover = inside(mx, my, cx, cy, cardW, cardH + labelH) && my >= top && my < bottom;
            if (hover) {
                hoveredEntry = index;
                if (hoverTrack != index) {
                    hoverTrack = index;
                    hoverSince = System.currentTimeMillis();
                }
            }
            drawCard(ctx, index, cx, cy, cardW, cardH, hover);
            final int target = index;
            hits.add(new Hit(cx, cy, cardW, cardH + labelH, top, bottom, (b, px, py) -> {
                focusedIndex = target;
                String folder = entries.get(target)[0];
                if (b == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (folder != null) {
                        zoomFolder = folder;
                        zoomName = entries.get(target)[1];
                    }
                } else if (b == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    choose(folder, tabOf(target));
                }
            }));
        }
        ctx.disableScissor();
        if (hoveredEntry < 0) {
            hoverTrack = -1;
        }

        if (gridMaxScroll > 0) {
            int trackX = x + w + 4;
            soft(ctx, trackX, top, 2, h, c.borderDim);
            int thumbH = Math.max(16, h * h / contentH);
            int thumbY = top + (int) ((h - thumbH) * (gridScroll / gridMaxScroll));
            scrollBar(ctx, 1, trackX, top, h, thumbY, thumbH, mx, my);
        }
    }

    /** which scroll bar is being dragged: 0 none, 1 the grid, 2 Extras */
    private int scrollDrag;
    private int scrollTop;
    private int scrollH;
    private int scrollThumbH;
    private double scrollGrab;

    /** The thumb of a scroll bar, grabbable with the mouse; a click on the track jumps there. */
    private void scrollBar(DrawContext ctx, int which, int trackX, int top, int h, int thumbY, int thumbH, int mx, int my) {
        boolean hover = scrollDrag == which || inside(mx, my, trackX - 4, top, 10, h);
        soft(ctx, hover ? trackX - 1 : trackX, thumbY, hover ? 4 : 2, thumbH, c.accent);
        hit(trackX - 4, top, 10, h, (b, px, py) -> {
            scrollDrag = which;
            scrollTop = top;
            scrollH = h;
            scrollThumbH = thumbH;
            scrollGrab = py >= thumbY && py < thumbY + thumbH ? py - thumbY : thumbH / 2.0;
            dragScroll(py);
        });
    }

    private void dragScroll(double mouseY) {
        double free = Math.max(1, scrollH - scrollThumbH);
        double t = Math.max(0, Math.min(1, (mouseY - scrollGrab - scrollTop) / free));
        if (scrollDrag == 1) {
            gridScroll = t * gridMaxScroll;
        } else if (scrollDrag == 2) {
            extrasScroll = t * extrasMaxScroll;
        }
    }

    /** height of a group title (Swords / Pickaxes) in the grid */
    private static final int GROUP_H = 14;

    /**
     * Where every visible card goes: {row, column, group titles above}. In Tools each group
     * starts on a new row under its own title; everywhere else it is a plain grid.
     */
    private int[][] place(int cols) {
        boolean grouped = (myPacksView || groupOf(tab).length > 1) && !favoritesOnly && query.isBlank();
        int[][] out = new int[visible.size()][];
        int row = -1;
        int col = cols;
        int titles = 0;
        Tab last = null;
        for (int k = 0; k < visible.size(); k++) {
            Tab group = tabOf(visible.get(k));
            if (grouped && group != last) {
                row++;
                col = 0;
                titles++;
                last = group;
            } else if (col >= cols) {
                row++;
                col = 0;
            }
            out[k] = new int[]{row, col, titles};
            col++;
        }
        return out;
    }

    /**
     * The web as it looks right now (your cobweb style), with the frame around it in its
     * thickness and colour; dim while the outline is off.
     */
    private int drawCobwebPreview(DrawContext ctx, int px, int y, int pw) {
        int box = 76;
        soft(ctx, px, y, box, box, c.base);
        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.client.texture.Sprite sprite = mc.getAtlasManager()
                .getSprite(net.minecraft.client.render.TexturedRenderLayers.BLOCK_SPRITE_MAPPER.mapVanilla("cobweb"));
        int s = 64;
        int x0 = px + (box - s) / 2;
        int y0 = y + (box - s) / 2;
        ctx.drawSpriteStretched(RenderPipelines.GUI_TEXTURED, sprite, x0, y0, s, s);
        int t = Math.max(1, Math.round(s * StylesUiConfig.cobwebOutlineWidth / 64.0F));
        int color = (StylesUiConfig.cobwebOutline ? 0xF0000000 : 0x50000000) | CobwebOutline.color();
        ctx.fill(x0, y0, x0 + s, y0 + t, color);
        ctx.fill(x0, y0 + s - t, x0 + s, y0 + s, color);
        ctx.fill(x0, y0 + t, x0 + t, y0 + s - t, color);
        ctx.fill(x0 + s - t, y0 + t, x0 + s, y0 + s - t, color);
        int tx = px + box + 8;
        int tw = pw - box - 8;
        text(ctx, fitText("Color follows", tw), tx, y + 14, c.textDim);
        text(ctx, fitText("your cobweb style", tw), tx, y + 25, c.textDim);
        return y + box;
    }

    /** sky colour over the day for the time slider (0 = 06:00, like Minecraft's ticks) */
    private static final int[][] SKY_STOPS = {{0, 0xFFAA6E}, {1500, 0x82B4FF}, {6000, 0x6EAAFF}, {11000, 0x8CAAF0},
            {12500, 0xFF7850}, {13500, 0x3C285A}, {18000, 0x0C0E22}, {22500, 0x282850}, {23500, 0xFF9664}, {24000, 0xFFAA6E}};

    private static int skyColor(float ticks) {
        for (int i = 0; i < SKY_STOPS.length - 1; i++) {
            int a = SKY_STOPS[i][0], b = SKY_STOPS[i + 1][0];
            if (ticks >= a && ticks <= b) {
                float f = (ticks - a) / (float) (b - a);
                int ca = SKY_STOPS[i][1], cb = SKY_STOPS[i + 1][1];
                int r = Math.round(((ca >> 16) & 0xFF) + (((cb >> 16) & 0xFF) - ((ca >> 16) & 0xFF)) * f);
                int g = Math.round(((ca >> 8) & 0xFF) + (((cb >> 8) & 0xFF) - ((ca >> 8) & 0xFF)) * f);
                int bl = Math.round((ca & 0xFF) + ((cb & 0xFF) - (ca & 0xFF)) * f);
                return 0xFF000000 | r << 16 | g << 8 | bl;
            }
        }
        return 0xFF000000 | SKY_STOPS[0][1];
    }

    /** ticks as a clock, 0 = 06:00 */
    private static String clock(int ticks) {
        int m = (int) ((ticks / 1000.0 * 60 + 6 * 60) % (24 * 60));
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    /** the track's colours: the average of your own sky at each hour (or the plain sky colours), kept per width */
    private static int[] trackColors = new int[0];
    private static int trackKey;

    private static int[] trackColors(int w) {
        int key = w * 31 + (CustomSky.hasSky() ? CustomSky.layersVersion() : -1);
        if (trackColors.length == w && trackKey == key) {
            return trackColors;
        }
        int[] out = new int[w];
        for (int i = 0; i < w; i++) {
            int ticks = Math.round(i / (float) Math.max(1, w - 1) * 23999);
            int base = skyColor(ticks);
            if (!CustomSky.hasSky()) {
                out[i] = base;
                continue;
            }
            int[] px = CustomSky.preview(ticks, base);
            long r = 0, g = 0, b = 0;
            for (int c : px) {
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
            }
            out[i] = 0xFF000000 | (int) (r / px.length) << 16 | (int) (g / px.length) << 8 | (int) (b / px.length);
        }
        trackColors = out;
        trackKey = key;
        return out;
    }

    private static Identifier skyPreviewId;
    private static int skyPreviewTicks = Integer.MIN_VALUE;
    private static int skyPreviewKey;

    /** your sky at the chosen hour, as a little picture next to the slider */
    private void drawSkyPreview(DrawContext ctx, int x, int y, int size, int ticks) {
        int t = ticks >= 0 ? ticks : (MinecraftClient.getInstance().world != null
                ? (int) (MinecraftClient.getInstance().world.getTimeOfDay() % 24000L) : 6000);
        int key = CustomSky.hasSky() ? CustomSky.layersVersion() : -1;
        if (skyPreviewId == null || Math.abs(t - skyPreviewTicks) >= 100 || key != skyPreviewKey) {
            int n = CustomSky.THUMB;
            int[] px = CustomSky.hasSky() ? CustomSky.preview(t, skyColor(t)) : null;
            net.minecraft.client.texture.NativeImage image = new net.minecraft.client.texture.NativeImage(n, n, false);
            for (int py = 0; py < n; py++) {
                for (int pxx = 0; pxx < n; pxx++) {
                    image.setColorArgb(pxx, py, px != null ? px[py * n + pxx] : skyColor(t));
                }
            }
            Identifier id = Identifier.of(BaulooStylesClient.MOD_ID, "sky_time_preview");
            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(id, new net.minecraft.client.texture.NativeImageBackedTexture(id::toString, image));
            skyPreviewId = id;
            skyPreviewTicks = t;
            skyPreviewKey = key;
        }
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skyPreviewId, x, y, 0, 0, size, size, size, size);
        softLine(ctx, x - 1, y - 1, size + 2, size + 2, c.borderDim);
    }

    private void skyTrack(DrawContext ctx, int x, int y, int w, int h, int ticks) {
        int[] colors = trackColors(w);
        for (int i = 0; i < w; i++) {
            ctx.fill(x + i, y, x + i + 1, y + h, colors[i]);
        }
        if (ticks >= 0) {
            int kx = x + Math.round(ticks / 24000F * (w - 1));
            soft(ctx, kx - 2, y - 2, 5, h + 4, 0xFF000000);
            ctx.fill(kx - 1, y - 1, kx + 1, y + h + 1, 0xFFFFFFFF);
        }
    }

    /**
     * Time changer in the Sky tab: a slider over the colours of the day. Dragging hides the menu so
     * the sky shows live; SERVER gives the real time back. Only on your screen.
     */
    private void drawTimeChanger(DrawContext ctx, int x, int y, int mx, int my) {
        text(ctx, "TIME", x, y + 4, c.textDim);
        int tx = x + textWidth("TIME") + 8;
        int bw = textWidth("SERVER") + 14;
        int labelW = textWidth("Server time");
        int tw = Math.max(60, Math.min(160, layout().mainW - (tx - x) - labelW - bw - 24));
        int ticks = StylesUiConfig.timeOverride;
        skyTrack(ctx, tx, y + 5, tw, 6, ticks);
        final int trackX = tx;
        final int trackW = tw;
        hit(tx, y, tw, 16, (b, x2, y2) -> {
            drag = Drag.TIME;
            dragW = Math.min(260, width - 80);
            dragX = (width - dragW) / 2;
            dragY = height - 90;
            float start = (float) Math.max(0, Math.min(1, (x2 - trackX) / (double) Math.max(1, trackW - 1)));
            StylesUiConfig.timeOverride = Math.round(start * 23999);
            dragStartMouse = dragX + start * (dragW - 1);
            dragStartT = start;
        });
        text(ctx, ticks >= 0 ? clock(ticks) : "Server time", tx + tw + 8, y + 4, ticks >= 0 ? c.text : c.textDim);
        int bx = tx + tw + 8 + labelW + 10;
        boolean on = ticks < 0;
        boolean hover = inside(mx, my, bx, y, bw, 16);
        soft(ctx, bx, y, bw, 16, on ? c.accent : (hover ? c.textDim : c.borderDim));
        soft(ctx, bx + 1, y + 1, bw - 2, 14, on ? StylesTheme.withAlpha(c.accentDark, 0xFF) : (hover ? c.surfaceHover : c.surface2));
        text(ctx, "SERVER", bx + 7, y + 4, on ? c.text : (hover ? c.textSoft : c.textDim));
        hit(bx, y, bw, 16, (b, px, py) -> {
            StylesUiConfig.timeOverride = -1;
            StylesUiConfig.save();
        });
        drawSkyPreview(ctx, bx + bw + 10, y - 8, 30, ticks);
        if (hover) {
            tooltip = "The real time of the server";
            tooltipX = mx + 8;
            tooltipY = my + 10;
        }
    }

    /** the slider alone over the world while the time is dragged */
    private void drawLiveTime(DrawContext ctx) {
        thirdPerson(false);
        HandPreview.clear();
        int w = dragW;
        int x = dragX;
        int y = dragY;
        int ticks = Math.max(0, StylesUiConfig.timeOverride);
        soft(ctx, x - 8, y - 22, w + 16, 40, StylesTheme.withAlpha(c.base, 0xD0));
        text(ctx, "TIME OF DAY", x, y - 14, c.textDim);
        textRight(ctx, clock(ticks), x + w, y - 14, c.text);
        skyTrack(ctx, x, y, w, 8, ticks);
    }

    /** the Cobwebs collection from the cloud (it sits in one of the free slots) */
    private boolean cobwebTab() {
        return "cobweb".equals(tab.prefix);
    }

    /**
     * A switch for the whole collection, under the title line (Clear background, Cobweb outline):
     * a framed chip with its label and a toggle, lit up in the accent colour while on.
     */
    private void headerToggle(DrawContext ctx, int x, int y, String label, boolean on, int mx, int my,
            String tip, Runnable flip) {
        int w = textWidth(label) + 12 + 22;
        int h = 16;
        boolean hover = inside(mx, my, x, y, w, h);
        soft(ctx, x, y, w, h, on ? c.accent : (hover ? c.textDim : c.borderDim));
        soft(ctx, x + 1, y + 1, w - 2, h - 2, on ? StylesTheme.withAlpha(c.accentDark, 0xFF) : (hover ? c.surfaceHover : c.surface2));
        text(ctx, label, x + 6, y + 4, on ? c.text : (hover ? c.textSoft : c.textDim));
        int sx = x + w - 22;
        soft(ctx, sx, y + 4, 16, 8, on ? c.accent : c.base);
        soft(ctx, on ? sx + 9 : sx + 1, y + 5, 6, 6, on ? c.onAccent : (hover ? c.text : c.textDim));
        hit(x, y, w, h, (b, px, py) -> {
            flip.run();
            StylesUiConfig.save();
        });
        if (hover) {
            tooltip = tip;
            tooltipX = mx + 8;
            tooltipY = my + 10;
        }
    }

    private void drawCard(DrawContext ctx, int index, int cx, int cy, int cardW, int cardH, boolean hover) {
        drawGlassCard(ctx, index, cx, cy, cardW, cardH, hover);
    }

    /** The Glass panel: soft shadow, see-through rounded body and a faint light edge. */
    private void drawGlassPanel(DrawContext ctx, Layout l) {
        for (int s = 3; s >= 1; s--) {
            rounded(ctx, l.fx - s * 2, l.fy - s * 2 + 3, l.fw + s * 4, l.fh + s * 4, PANEL, 0x12000000);
        }
        rounded(ctx, l.fx, l.fy, l.fw, l.fh, PANEL, c.frame);
        int edge = 0x16FFFFFF;
        ctx.fill(l.fx + 5, l.fy, l.fx + l.fw - 5, l.fy + 1, edge);
        ctx.fill(l.fx + 5, l.fy + l.fh - 1, l.fx + l.fw - 5, l.fy + l.fh, edge);
        ctx.fill(l.fx, l.fy + 5, l.fx + 1, l.fy + l.fh - 5, edge);
        ctx.fill(l.fx + l.fw - 1, l.fy + 5, l.fx + l.fw, l.fy + l.fh - 5, edge);
    }

    /**
     * A Glass card: one rounded tile with the picture on top (inset, round corners) and the
     * name below it. Equipped = accent ring + EQUIPPED tag.
     */
    private void drawGlassCard(DrawContext ctx, int index, int cx, int cy, int cardW, int cardH, boolean hover) {
        String[] e = entries.get(index);
        String folder = e[0];
        boolean isActive = isActive(index);
        boolean upload = "upload".equals(e[2]);
        boolean marked = isPending(index);
        int fullH = cardH + LABEL_H;
        if (isActive || marked) {
            rounded(ctx, cx - 1, cy - 1, cardW + 2, fullH + 2, CARD, marked ? PENDING_COLOR : c.accent);
        }
        int box = hover ? c.surfaceHover : c.surface;
        rounded(ctx, cx, cy, cardW, fullH, CARD, box);

        int px = cx + 3;
        int py = cy + 3;
        int pw = cardW - 6;
        int ph = cardH - 3;
        if (folder == null) {
            soft(ctx, px, py, pw, ph, c.surface2);
            float k = Math.max(1.0F, Math.min(3.0F, (ph - 10) / 16.0F));
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(px + pw / 2.0F - 8 * k, py + ph / 2.0F - 8 * k);
            m.scale(k, k);
            ctx.drawItem(icon(tabOf(index)), 0, 0);
            m.popMatrix();
        } else {
            if (clipPlaying(index)) {
                drawClip(ctx, folder, px, py, pw, ph);
            } else {
                drawPreview(ctx, folder, px, py, pw, ph);
            }
            if (hover) {
                ctx.fill(px, py, px + pw, py + ph, 0x12FFFFFF);
            }
            roundCut(ctx, px, py, pw, ph, SMALL, box);
        }

        // tags on the picture: EQUIPPED / NEW / LOCAL on the left, SELECTED on the right
        String tag = isActive ? "EQUIPPED" : null;
        int tagFill = c.accent;
        int tagText = c.onAccent;
        StylesCloud.Style cloud = StylesCloud.byFolder(folder);
        if (tag == null && ph >= 30 && upload) {
            tag = "LOCAL";
            tagFill = 0xC0000000;
            tagText = c.textSoft;
        } else if (tag == null && ph >= 30 && cloud != null && cloud.isNew()) {
            tag = "NEW";
            tagFill = 0xF0FFFFFF;
            tagText = 0xFF0D0E12;
        }
        if (tag != null) {
            int tw = scaledWidth(tag, 0.7F) + 8;
            soft(ctx, px + 3, py + 3, tw, 10, tagFill);
            scaled(ctx, tag, px + 7, py + 5.5F, tagText, 0.7F, false);
        }
        if (marked) {
            int tw = scaledWidth("SELECTED", 0.7F) + 8;
            soft(ctx, px + pw - 3 - tw, py + 3, tw, 10, PENDING_COLOR);
            scaled(ctx, "SELECTED", px + pw - tw + 1, py + 5.5F, 0xFF1A1300, 0.7F, false);
        }
        if (cloud != null && (StylesCloud.isDownloading(cloud) || folder.equals(pendingCloud))) {
            soft(ctx, px, py, pw, ph, StylesTheme.withAlpha(c.base, 0xB0));
            String dots = ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
            textCenter(ctx, "Downloading" + dots, px, pw, py + ph / 2 - 4, c.accent);
        }

        // name row: name on the left, the favorite star on the right
        boolean fav = folder != null && StylesUiConfig.isFavorite(folder);
        int nameW = cardW - 12 - (fav ? 10 : 0);
        text(ctx, fitText(e[1], nameW), cx + 6, cy + cardH + 5,
                marked ? PENDING_COLOR : isActive ? c.text : (hover ? c.text : c.textSoft));
        if (fav) {
            text(ctx, "\u2605", cx + cardW - 12, cy + cardH + 5, c.gold);
        }
    }

    private void drawSide(DrawContext ctx, Layout l, int mx, int my) {
        int x = l.sideX;
        int y = l.bodyY + 8;
        int w = l.sideW;
        int bottom = l.footerY - 10;
        soft(ctx, x, y, w, bottom - y, c.surface);

        int px = x + 10;
        int pw = w - 20;
        int ty = y + 10;
        String[] labels = {"INSPECTOR", "THEMES"};
        int gap = 14;
        int tx = px;
        for (int i = 0; i < labels.length; i++) {
            boolean on = !presetsView && (i == 1) == StylesUiConfig.themesTab;
            int tw = textWidth(labels[i]);
            boolean hover = inside(mx, my, tx - 3, ty - 3, tw + 6, 16);
            text(ctx, labels[i], tx, ty, on ? c.text : (hover ? c.textSoft : c.textDim));
            if (on) {
                soft(ctx, tx, ty + 11, tw, 2, c.accent);
            }
            final int index = i;
            hit(tx - 3, ty - 3, tw + 6, 16, (b, px2, py2) -> {
                presetsView = false;
                presetNaming = false;
                StylesUiConfig.themesTab = index == 1;
                hexFocused = false;
                StylesUiConfig.save();
            });
            tx += tw + gap;
        }
        ctx.fill(px, ty + 15, px + pw, ty + 16, c.borderDim);

        int contentTop = ty + 23;
        if (presetsView) {
            drawPresets(ctx, px, contentTop, pw, bottom - 8, mx, my);

        } else if (StylesUiConfig.themesTab) {
            drawThemes(ctx, px, contentTop, pw, bottom - 8, mx, my, c.surface);
        } else {
            drawInspector(ctx, px, contentTop, pw, bottom - 10, mx, my);
        }
    }

    private void drawInspector(DrawContext ctx, int px, int top, int pw, int bottom, int mx, int my) {
        if (entries.isEmpty()) {
            // My Packs right after the game starts: the scan has not found anything yet
            String why = myPacksView && MyPacks.scanning() ? "Scanning your packs ..." : "Nothing to show yet";
            text(ctx, fitText(why, pw), px, top + 4, c.textDim);
            return;
        }
        int shown = hoveredEntry >= 0 ? hoveredEntry : Math.min(focusedIndex, entries.size() - 1);
        if (shown < 0 || shown >= entries.size()) {
            shown = 0;
        }
        String[] e = entries.get(shown);
        String folder = e[0];
        boolean isActive = isActive(shown);
        boolean upload = "upload".equals(e[2]);

        int smallY = bottom - 16;
        int applyY = smallY - 21;
        int infoH = 44;
        int size = Math.max(24, Math.min(pw, applyY - 10 - top - infoH));
        int previewX = px + (pw - size) / 2;
        if (folder == null) {
            soft(ctx, previewX, top, size, size, c.surface2);
            ctx.drawItem(icon(tabOf(shown)), previewX + (size - 16) / 2, top + size / 2 - 14);
            textCenter(ctx, e[1], previewX, size, top + size / 2 + 6, c.textDim);
        } else {
            if (clipPlaying(shown)) {
                drawClip(ctx, folder, previewX, top, size, size);
            } else {
                drawPreview(ctx, folder, previewX, top, size, size);
            }
            cutCorners(ctx, previewX, top, size, size, c.surface);
        }
        if (isActive) {
            softLine(ctx, previewX - 1, top - 1, size + 2, size + 2, c.accent);
        }

        int iy = top + size + 8;
        if (isActive) {
            int pillW = textWidth("ACTIVE") + 10;
            soft(ctx, px, iy, pillW, 12, StylesTheme.withAlpha(c.good, 0x33));
            text(ctx, "ACTIVE", px + 5, iy + 2, c.good);
            text(ctx, fitText(e[1], pw - pillW - 6), px + pillW + 6, iy + 2, c.text);
        } else {
            text(ctx, fitText(e[1], pw), px, iy + 2, c.text);
        }
        MyPacks.Style mine = folder != null && folder.startsWith(MYPACK) ? MyPacks.byId(folder.substring(MYPACK.length())) : null;
        VipPacks.Style vip = folder != null && folder.startsWith(VIPPACK) ? VipPacks.byId(folder.substring(VIPPACK.length())) : null;
        String kind = folder == null ? "Default look"
                : mine != null ? "From your pack: " + mine.pack()
                : vip != null ? "Bauloo's Packs - VIP"
                : upload ? "Local upload"
                : "cloud".equals(e[2]) ? "By bauloo - cloud" : tab.title + " style";
        text(ctx, fitText(kind, pw), px, iy + 16, c.textDim);
        if (!isActive) {
            String now = mine != null || vip != null ? "Only the " + tabOf(shown).title.toLowerCase(Locale.ROOT) + " part is used"
                    : "Now: " + activeName(tab);
            text(ctx, fitText(now, pw), px, iy + 28, c.textDim);
        }

        boolean skyLocked = tab == Tab.SKY && folder != null && !skyboxifyInstalled;
        if (isActive) {
            textCenter(ctx, fitText("Left-click a style to apply", pw), px, pw, applyY + 5, c.textDim);
        } else if (skyLocked) {
            button(ctx, px, applyY, pw, 17, "Requires Skyboxify", false, ButtonStyle.DISABLED);
        } else {
            boolean hover = inside(mx, my, px, applyY, pw, 17);
            String label = folder == null ? "USE VANILLA" : "APPLY";
            if (!StylesUiConfig.applyInstantly) {
                label = isPending(shown) ? "SELECTED" : (folder == null ? "SELECT VANILLA" : "SELECT");
            }
            button(ctx, px, applyY, pw, 17, label, hover, ButtonStyle.PRIMARY);
            final String target = folder;
            final Tab targetTab = tabOf(shown);
            hit(px, applyY, pw, 17, (b, x2, y2) -> choose(target, targetTab));
        }

        int halfW = (pw - 4) / 2;
        int favX = px + halfW + 4;
        int favW = pw - halfW - 4;
        if (folder == null) {
            button(ctx, px, smallY, halfW, 16, "ZOOM", false, ButtonStyle.DISABLED);
            button(ctx, favX, smallY, favW, 16, "FAVORITE", false, ButtonStyle.DISABLED);
            return;
        }
        boolean zoomHover = inside(mx, my, px, smallY, halfW, 16);
        button(ctx, px, smallY, halfW, 16, "ZOOM", zoomHover, ButtonStyle.NORMAL);
        final String target = folder;
        final String targetName = e[1];
        hit(px, smallY, halfW, 16, (b, x2, y2) -> {
            zoomFolder = target;
            zoomName = targetName;
        });

        boolean fav = StylesUiConfig.isFavorite(folder);
        boolean favHover = inside(mx, my, favX, smallY, favW, 16);
        button(ctx, favX, smallY, favW, 16, "", favHover, ButtonStyle.NORMAL);
        String label = fitText(fav ? "SAVED" : "FAVORITE", favW - 18);
        int lw = textWidth("★ ") + textWidth(label);
        int lx = favX + (favW - lw) / 2;
        text(ctx, "★", lx, smallY + 4, fav ? c.gold : c.textDim);
        text(ctx, label, lx + textWidth("★ "), smallY + 4, fav ? c.gold : (favHover ? c.text : c.textSoft));
        hit(favX, smallY, favW, 16, (b, x2, y2) -> toggleFavorite(target));
    }


    // ------------------------------------------------------------- Extras tiles

    /** One extra: a tile on the Extras page; its settings come from drawOutline (key = section). */
    private record ExtraTile(String key, String name, String group, net.minecraft.item.Item icon, String desc) {
    }

    private static final String[] EXTRA_GROUPS = {"All", "Combat", "World", "HUD & Menu"};
    private static String extraKey = "OUTLINE";
    private static int extraGroup;
    /** narrow windows: the settings of a tile replace the tiles until BACK */
    private static boolean extraOpen;
    private double tileScroll;
    private int tileMaxScroll;
    private int tileTop, tileBottom, tileLeft, tileRight;
    /** drawOutline draws only this extra's settings (null = all of them) */
    private String extraOnly;

    private boolean want(String key) {
        return extraOnly == null || extraOnly.equals(key);
    }

    private static List<ExtraTile> extraTiles() {
        List<ExtraTile> list = new ArrayList<>();
        list.add(new ExtraTile("OUTLINE", "Player Outline", "Combat", Items.ARMOR_STAND, "Glow around players"));
        list.add(new ExtraTile("CRYSTAL", "Crystal", "Combat", Items.END_CRYSTAL, "Spin speed and core color"));
        list.add(new ExtraTile("TOTEM", "Totem", "Combat", Items.TOTEM_OF_UNDYING, "Size in hand and pop"));
        list.add(new ExtraTile("SHIELD", "Shield", "Combat", Items.SHIELD, "See-through, size, color"));
        list.add(new ExtraTile("GAP", "Gap", "Combat", Items.GOLDEN_APPLE, "Size in hand"));
        list.add(new ExtraTile("ANCHOR", "Anchor Charge", "Combat", Items.RESPAWN_ANCHOR, "Glow color, look full"));
        list.add(new ExtraTile("KILL", "Kill Effect", "Combat", Items.NETHERITE_SWORD, "Effect and kill sound"));
        list.add(new ExtraTile("HITBOX", "Hitbox", "Combat", Items.TARGET, "F3+B box color"));
        list.add(new ExtraTile("CRYSTALBOX", "Crystal Box", "Combat", Items.GLASS, "Box of invisible crystals"));
        list.add(new ExtraTile("COBWEB", "Cobweb", "World", Items.COBWEB, "Outline around cobwebs"));
        list.add(new ExtraTile("FIRE", "Fire", "World", Items.BLAZE_POWDER, "Own fire color"));
        list.add(new ExtraTile("BLOCK", "Block Outline", "World", Items.GRASS_BLOCK, "Color and thickness"));
        list.add(new ExtraTile("CROSSHAIR", "Crosshair", "World", Items.SPYGLASS, "Color and gradient"));
        list.add(new ExtraTile("GLINT", "Enchant Glint", "World", Items.EXPERIENCE_BOTTLE, "Color, gradient, rainbow"));
        if (SpotifyNowPlaying.AVAILABLE) {
            list.add(new ExtraTile("SPOTIFY", "Spotify", "HUD & Menu", Items.MUSIC_DISC_CAT, "Now playing card"));
        }
        list.add(new ExtraTile("SOCIAL", "Social Badge", "HUD & Menu", Items.NAME_TAG, "Logo on the recipe book button"));
        list.add(new ExtraTile("TABICON", "Tab Icon", "HUD & Menu", Items.WRITABLE_BOOK, "Tornado next to your name"));
        list.add(new ExtraTile("MENU", "Menu", "HUD & Menu", Items.COMPARATOR, "Apply instantly or all at once"));
        return list;
    }

    /** on/off of a tile, or null when it shows a value instead (sizes, effects) */
    private static Boolean extraOn(String key) {
        return switch (key) {
            case "OUTLINE" -> StylesUiConfig.outline;
            case "FIRE" -> pickOn(PICK_FIRE);
            case "COBWEB" -> StylesUiConfig.cobwebOutline;
            case "HITBOX" -> pickOn(PICK_HITBOX);
            case "CRYSTALBOX" -> pickOn(PICK_CRYSTAL_BOX);
            case "ANCHOR" -> pickOn(PICK_ANCHOR);
            case "CROSSHAIR" -> pickOn(PICK_CROSSHAIR);
            case "BLOCK" -> pickOn(PICK_BLOCK);
            case "GLINT" -> pickOn(PICK_GLINT);
            case "SPOTIFY" -> StylesUiConfig.spotifyHud;
            case "TABICON" -> StylesUiConfig.showInTab;
            case "MENU" -> StylesUiConfig.applyInstantly;
            default -> null;
        };
    }

    private void toggleExtra(String key) {
        switch (key) {
            case "OUTLINE" -> StylesUiConfig.outline = !StylesUiConfig.outline;
            case "FIRE" -> setPick(PICK_FIRE, !pickOn(PICK_FIRE), null);
            case "COBWEB" -> StylesUiConfig.cobwebOutline = !StylesUiConfig.cobwebOutline;
            case "HITBOX" -> setPick(PICK_HITBOX, !pickOn(PICK_HITBOX), null);
            case "CRYSTALBOX" -> setPick(PICK_CRYSTAL_BOX, !pickOn(PICK_CRYSTAL_BOX), null);
            case "ANCHOR" -> setPick(PICK_ANCHOR, !pickOn(PICK_ANCHOR), null);
            case "CROSSHAIR" -> setPick(PICK_CROSSHAIR, !pickOn(PICK_CROSSHAIR), null);
            case "BLOCK" -> setPick(PICK_BLOCK, !pickOn(PICK_BLOCK), null);
            case "GLINT" -> setPick(PICK_GLINT, !pickOn(PICK_GLINT), null);
            case "SPOTIFY" -> StylesUiConfig.spotifyHud = !StylesUiConfig.spotifyHud;
            case "TABICON" -> {
                StylesUiConfig.showInTab = !StylesUiConfig.showInTab;
                StylesPresence.settingChanged();
            }
            case "MENU" -> {
                StylesUiConfig.applyInstantly = !StylesUiConfig.applyInstantly;
                PENDING.clear();
            }
            default -> {
                return;
            }
        }
        StylesUiConfig.save();
    }

    private static String percent(int value) {
        return value == 100 ? "Normal" : value + "%";
    }

    /** the value a tile without a switch shows */
    private static String extraValue(String key) {
        return switch (key) {
            case "CRYSTAL" -> StylesUiConfig.crystalSpin == 0 ? "Stopped" : percent(StylesUiConfig.crystalSpin);
            case "TOTEM" -> percent(StylesUiConfig.totemSize);
            case "SHIELD" -> StylesUiConfig.shieldOpacity >= 100 ? "Normal" : StylesUiConfig.shieldOpacity + "%";
            case "GAP" -> percent(StylesUiConfig.gapSize);
            case "SOCIAL" -> Watermark.validSocial(StylesUiConfig.socialLogo) ? "On" : "Off";
            case "KILL" -> {
                for (String[] effect : KillEffect.EFFECTS) {
                    if (effect[0].equals(StylesUiConfig.killEffect)) {
                        yield effect[1];
                    }
                }
                yield "Off";
            }
            default -> "";
        };
    }

    /** Extras page: group chips and tiles; the picked tile's settings on the right (or instead, when narrow). */
    private void drawExtrasTiles(DrawContext ctx, Layout l, int mx, int my) {
        int x = l.mainX;
        int y = l.bodyY;
        int full = l.fx + l.fw - 10 - x;
        boolean split = full >= 430;
        int setW = split ? clamp(full * 36 / 100, 190, 260) : full;
        int gridW = split ? full - setW - 12 : full;
        List<ExtraTile> all = extraTiles();
        ExtraTile picked = all.get(0);
        for (ExtraTile t : all) {
            if (t.key().equals(extraKey)) {
                picked = t;
            }
        }
        extraKey = picked.key();
        if (!split && extraOpen) {
            drawExtraSettings(ctx, picked, x, y + 8, full, l.footerY - 10, mx, my, true);
            return;
        }

        pageTitle(ctx, "Extras", x, y + 11);
        text(ctx, fitText("Click a tile to change it - the switch turns it on or off", gridW), x, y + 24, c.textDim);
        int chipX = x;
        int chipY = y + 38;
        for (int i = 0; i < EXTRA_GROUPS.length; i++) {
            String group = EXTRA_GROUPS[i];
            int n = 0;
            for (ExtraTile t : all) {
                if (i == 0 || t.group().equals(group)) {
                    n++;
                }
            }
            String count = String.valueOf(n);
            int cw = textWidth(group) + textWidth(count) + 18;
            boolean on = extraGroup == i;
            boolean hover = inside(mx, my, chipX, chipY, cw, 14);
            soft(ctx, chipX, chipY, cw, 14, on ? c.text : hover ? c.surfaceHover : c.surface);
            ctx.drawText(textRenderer, group, chipX + 7, chipY + 3, on ? c.base : (hover ? c.text : c.textSoft), false);
            ctx.drawText(textRenderer, count, chipX + 11 + textWidth(group), chipY + 3, on ? c.textDim : c.textDim, false);
            final int index = i;
            hit(chipX, chipY, cw, 14, (b, px, py) -> {
                extraGroup = index;
                tileScroll = 0;
            });
            chipX += cw + 5;
        }

        List<ExtraTile> shown = new ArrayList<>();
        for (ExtraTile t : all) {
            if (extraGroup == 0 || t.group().equals(EXTRA_GROUPS[extraGroup])) {
                shown.add(t);
            }
        }
        tileTop = y + 60;
        tileBottom = l.footerY - 8;
        tileLeft = x;
        tileRight = x + gridW;
        int gap = 7;
        int cols = Math.max(1, (gridW + gap) / 124);
        int tw = (gridW - (cols - 1) * gap) / cols;
        int th = 46;
        int rows = (shown.size() + cols - 1) / cols;
        int viewH = tileBottom - tileTop;
        tileMaxScroll = Math.max(0, rows * (th + gap) - gap + 2 - viewH);
        tileScroll = Math.max(0, Math.min(tileScroll, tileMaxScroll));
        ctx.enableScissor(tileLeft - 2, tileTop - 2, tileRight + 2, tileBottom);
        hitClipTop = tileTop;
        hitClipBottom = tileBottom;
        try {
            for (int k = 0; k < shown.size(); k++) {
                int tx = x + (k % cols) * (tw + gap);
                int ty = tileTop + 1 + (k / cols) * (th + gap) - (int) tileScroll;
                if (ty + th < tileTop || ty > tileBottom) {
                    continue;
                }
                drawExtraTile(ctx, shown.get(k), tx, ty, tw, th, mx, my);
            }
        } finally {
            hitClipTop = Integer.MIN_VALUE;
            hitClipBottom = Integer.MAX_VALUE;
            ctx.disableScissor();
        }
        if (tileMaxScroll > 0) {
            int thumbH = Math.max(16, viewH * viewH / (viewH + tileMaxScroll));
            int thumbY = tileTop + (int) ((viewH - thumbH) * (tileScroll / tileMaxScroll));
            soft(ctx, tileRight + 4, thumbY, 2, thumbH, c.accent);
        }
        if (split) {
            drawExtraSettings(ctx, picked, x + gridW + 12, y + 8, setW, l.footerY - 10, mx, my, false);
        }
    }

    private void drawExtraTile(DrawContext ctx, ExtraTile t, int tx, int ty, int tw, int th, int mx, int my) {
        boolean selected = t.key().equals(extraKey);
        boolean hover = inside(mx, my, tx, ty, tw, th) && my >= tileTop && my < tileBottom;
        if (selected) {
            rounded(ctx, tx - 1, ty - 1, tw + 2, th + 2, CARD, c.accent);
        }
        rounded(ctx, tx, ty, tw, th, CARD, selected ? c.surface2 : hover ? c.surfaceHover : c.surface);
        soft(ctx, tx + 5, ty + 5, 22, 22, c.base);
        ctx.drawItem(new ItemStack(t.icon()), tx + 8, ty + 8);
        int textX = tx + 32;
        text(ctx, fitText(t.name(), tw - 36), textX, ty + 7, selected || hover ? c.text : c.textSoft);
        scaled(ctx, fitText(t.desc(), Math.round((tw - 36) / 0.75F)), textX, ty + 18, c.textDim, 0.75F, false);
        scaled(ctx, t.group().toUpperCase(Locale.ROOT), tx + 6, ty + th - 10, c.textDim, 0.65F, false);
        final String key = t.key();
        hit(tx, ty, tw, th, (b, px, py) -> {
            if (!key.equals(extraKey)) {
                extrasScroll = 0;
            }
            extraKey = key;
            extraOpen = true;
        });
        Boolean on = extraOn(key);
        if (on != null) {
            int swW = 20;
            int swH = 10;
            int sx = tx + tw - swW - 6;
            int sy = ty + th - swH - 5;
            soft(ctx, sx, sy, swW, swH, on ? c.accentDark : c.base);
            softLine(ctx, sx, sy, swW, swH, on ? c.accent : c.borderDim);
            soft(ctx, on ? sx + swW - 8 : sx + 2, sy + 2, 6, 6, on ? c.accent : c.textDim);
            hit(sx - 3, sy - 3, swW + 6, swH + 6, (b, px, py) -> toggleExtra(key));
        } else {
            String value = extraValue(key);
            boolean changed = !value.equals("Normal") && !value.equals("Off");
            int vw = scaledWidth(value, 0.75F) + 8;
            int vx = tx + tw - vw - 6;
            int vy = ty + th - 14;
            soft(ctx, vx, vy, vw, 10, changed ? c.accentTint : c.base);
            scaled(ctx, value, vx + 4, vy + 2.5F, changed ? c.accentBright : c.textSoft, 0.75F, false);
        }
    }

    /** The settings of one extra: name, switch, description, then its controls (scrolls). */
    private void drawExtraSettings(DrawContext ctx, ExtraTile t, int x, int top, int w, int bottom, int mx, int my, boolean back) {
        soft(ctx, x, top, w, bottom - top, c.surface);
        int px = x + 10;
        int pw = w - 20;
        int y = top + 9;
        scaled(ctx, "EXTRAS  >  " + t.group().toUpperCase(Locale.ROOT), px, y, c.textDim, 0.7F, false);
        if (back) {
            int bw = textWidth("BACK") + 14;
            int bx = px + pw - bw;
            button(ctx, bx, y - 3, bw, 14, "BACK", inside(mx, my, bx, y - 3, bw, 14), ButtonStyle.NORMAL);
            hit(bx, y - 3, bw, 14, (b, px2, py2) -> extraOpen = false);
        }
        y += 10;
        scaled(ctx, fitText(t.name(), Math.round((pw - 28) / 1.4F)), px, y, c.text, 1.4F, true);
        Boolean on = extraOn(t.key());
        if (on != null && !back) {
            int swW = 22;
            int swH = 11;
            int sx = px + pw - swW;
            soft(ctx, sx, y, swW, swH, on ? c.accentDark : c.base);
            softLine(ctx, sx, y, swW, swH, on ? c.accent : c.borderDim);
            soft(ctx, on ? sx + swW - 9 : sx + 2, y + 2, 7, 7, on ? c.accent : c.textDim);
            final String key = t.key();
            hit(sx - 3, y - 3, swW + 6, swH + 6, (b, px2, py2) -> toggleExtra(key));
        }
        y += 15;
        scaled(ctx, fitText(t.desc(), Math.round(pw / 0.8F)), px, y, c.textDim, 0.8F, false);
        y += 11;
        ctx.fill(px, y, px + pw, y + 1, c.borderDim);
        y += 4;

        extrasTop = y;
        extrasBottom = bottom - 4;
        extrasLeft = x;
        extrasRight = x + w;
        int viewH = extrasBottom - extrasTop;
        int start = extrasTop + 6 - (int) extrasScroll;
        ctx.enableScissor(extrasLeft, extrasTop, extrasRight, extrasBottom);
        hitClipTop = extrasTop;
        hitClipBottom = extrasBottom;
        extraOnly = t.key();
        int end;
        try {
            end = drawOutline(ctx, px, start, pw, Integer.MAX_VALUE / 2, mx, my, 0, 0);
        } finally {
            extraOnly = null;
            hitClipTop = Integer.MIN_VALUE;
            hitClipBottom = Integer.MAX_VALUE;
            ctx.disableScissor();
        }
        extrasMaxScroll = Math.max(0, end - start + 6 - viewH);
        extrasScroll = Math.max(0, Math.min(extrasScroll, extrasMaxScroll));
        if (extrasMaxScroll > 0) {
            int trackX = extrasRight - 5;
            soft(ctx, trackX, extrasTop + 4, 2, viewH - 8, c.borderDim);
            int thumbH = Math.max(16, (viewH - 8) * viewH / (viewH + extrasMaxScroll));
            int thumbY = extrasTop + 4 + (int) ((viewH - 8 - thumbH) * (extrasScroll / extrasMaxScroll));
            scrollBar(ctx, 2, trackX, extrasTop + 4, viewH - 8, thumbY, thumbH, mx, my);
        }
    }

    private static final int[] OUTLINE_SWATCHES = {0xFF5AC8, 0xFF4A5A, 0xFFB547, 0x6CFF8A, 0x75D4FF, 0xB388FF, 0xFFFFFF, 0x000000};

    /**
     * Extras, sorted into four groups: Player Outline and Crystal on the left, Shield and
     * Totem on the right (secondX > 0). On narrow windows everything is one column.
     */
    private int drawOutline(DrawContext ctx, int px, int top, int pw, int bottom, int mx, int my, int secondX, int thirdX) {
        int y = top;

        if (want("OUTLINE")) {
            // ---- Player outline
            y = sectionHeader(ctx, px, y, pw, "PLAYER OUTLINE");
            y = switchRow(ctx, px, y, pw, "Outline on", StylesUiConfig.outline, mx, my,
                    () -> StylesUiConfig.outline = !StylesUiConfig.outline);
            y = switchRow(ctx, px, y, pw, "Myself (F5)", StylesUiConfig.outlineSelf, mx, my,
                    () -> StylesUiConfig.outlineSelf = !StylesUiConfig.outlineSelf);
            y = switchRow(ctx, px, y, pw, "Other players", StylesUiConfig.outlineOthers, mx, my,
                    () -> StylesUiConfig.outlineOthers = !StylesUiConfig.outlineOthers);
            text(ctx, fitText("Only while you can see them", pw), px, y - 4, c.textDim);
            y += 12;

            int hueH = 6;
            for (int i = 0; i < pw; i++) {
                ctx.fill(px + i, y, px + i + 1, y + hueH, 0xFF000000 | PlayerOutline.sliderColor(i / (float) Math.max(1, pw - 1)));
            }
            cutCorners(ctx, px, y, pw, hueH, c.surface);
            if (!StylesUiConfig.outlineRainbow) {
                int knobX = px + Math.round(PlayerOutline.sliderPos(StylesUiConfig.outlineColor) * (pw - 1));
                soft(ctx, knobX - 2, y - 2, 4, hueH + 4, 0xFF000000);
                ctx.fill(knobX - 1, y - 1, knobX + 1, y + hueH + 1, 0xFFFFFFFF);
            }
            final int trackX = px;
            hit(px, y - 4, pw, hueH + 8, (b, x2, y2) -> {
                drag = Drag.OUTLINE_HUE;
                // die Leiste sitzt beim Ziehen mittig unten, also zaehlt auch dort die Maus
                dragW = Math.min(260, width - 80);
                dragX = (width - dragW) / 2;
                dragY = height - 90;
                float start = (float) ((x2 - trackX) / (double) Math.max(1, pw - 1));
                StylesUiConfig.outlineColor = PlayerOutline.sliderColor(Math.max(0, Math.min(1, start)));
                StylesUiConfig.outlineRainbow = false;
                dragStartMouse = dragX + start * (dragW - 1);
                dragStartT = Math.max(0, Math.min(1, start));
            });
            y += hueH + 8;

            int sw = Math.max(10, (pw - (OUTLINE_SWATCHES.length - 1) * 4) / OUTLINE_SWATCHES.length);
            for (int i = 0; i < OUTLINE_SWATCHES.length; i++) {
                int sx = px + i * (sw + 4);
                int color = OUTLINE_SWATCHES[i];
                boolean selected = !StylesUiConfig.outlineRainbow && (StylesUiConfig.outlineColor & 0xFFFFFF) == color;
                soft(ctx, sx, y, sw, 10, 0xFF000000 | color);
                if (selected || inside(mx, my, sx, y, sw, 10)) {
                    softLine(ctx, sx - 1, y - 1, sw + 2, 12, selected ? c.text : c.textDim);
                }
                hit(sx, y, sw, 10, (b, x2, y2) -> {
                    StylesUiConfig.outlineColor = color;
                    StylesUiConfig.outlineRainbow = false;
                    StylesUiConfig.save();
                });
            }
            y += 16;
            y = switchRow(ctx, px, y, pw, "Rainbow", StylesUiConfig.outlineRainbow, mx, my,
                    () -> StylesUiConfig.outlineRainbow = !StylesUiConfig.outlineRainbow);
            int width = StylesUiConfig.outlineWidth;
            y = sliderRow(ctx, px, y, pw, "Thickness", width == 2 ? "Normal" : String.valueOf(width),
                    (width - 1) / 5.0F, Drag.OUTLINE_WIDTH, mx, my, () -> StylesUiConfig.outlineWidth = 2);
            y += 8;

        }
        if (want("CRYSTAL")) {
            // ---- Crystal (left column, under the outline)
            y = sectionHeader(ctx, px, y, pw, "CRYSTAL");
            int spin = StylesUiConfig.crystalSpin;
            y = sliderRow(ctx, px, y, pw, "Spin speed", spin == 100 ? "Normal" : spin == 0 ? "Stopped" : spin + "%",
                    spin / 1000.0F, Drag.CRYSTAL_SPIN, mx, my, () -> StylesUiConfig.crystalSpin = 100);
            int boxH = Math.min(84, bottom - y - 4); // bottom is far away while scrolling
            if (boxH >= 40) {
                int boxW = pw - 50;
                soft(ctx, px, y, boxW, boxH, c.base);
                if ("crystal_invisible".equals(active.get(Tab.CRYSTAL))) {
                    // the Invisible style hides the crystal itself; show its outline box instead
                    int boxColor = StylesUiConfig.crystalBoxCustom ? 0xFF000000 | StylesUiConfig.crystalBoxColor : 0xFFFFFFFF;
                    int cube = Math.min(boxW, boxH) - 16;
                    drawBoxPreview(ctx, px + (boxW - cube) / 2, y + 8, cube, boxColor);
                    textCenter(ctx, fitText("Invisible style: only the box", boxW - 6), px, boxW, y + boxH - 11, c.textDim);
                } else {
                    drawCrystalPreview(ctx, px, y, boxW, boxH);
                }
                int bx = px + boxW + 6;
                int bw = pw - boxW - 6;
                boolean stopHover = inside(mx, my, bx, y, bw, 14);
                button(ctx, bx, y, bw, 14, "STOP", stopHover, spin == 0 ? ButtonStyle.PRIMARY : ButtonStyle.NORMAL);
                hit(bx, y, bw, 14, (b, x2, y2) -> {
                    StylesUiConfig.crystalSpin = 0;
                    StylesUiConfig.save();
                });
                y += boxH + 10;
            }

            text(ctx, "Core color", px, y, c.textSoft);
            y += 12;
            y = colorPicker(ctx, px, y, pw, mx, my, PICK_CORE);
            y += 4;

        }
        if (want("FIRE")) {
            // ---- Fire
            y = sectionHeader(ctx, px, y, pw, "FIRE");
            y = colorPicker(ctx, px, y, pw, mx, my, PICK_FIRE);
            y += 4;

        }
        int leftEnd = y;
        if (secondX > 0) {
            px = secondX;
            y = top;
        }
        int middleEnd = 0;

        if (want("GAP")) {
            // ---- Gap (top of the second column)
            if (extraOnly == null) {
                Matrix3x2fStack gm = ctx.getMatrices();
                gm.pushMatrix();
                gm.translate(px + 6 + textWidth("GAP") + 4, y - 1);
                gm.scale(0.75F, 0.75F);
                ctx.drawItem(new ItemStack(Items.GOLDEN_APPLE), 0, 0);
                gm.popMatrix();
            }
            y = sectionHeader(ctx, px, y, pw, "GAP");
            int gap = StylesUiConfig.gapSize;
            y = sliderRow(ctx, px, y, pw, "Gap size", gap == 100 ? "Normal" : gap + "%",
                    (gap - 20) / 130.0F, Drag.GAP_SIZE, mx, my, () -> StylesUiConfig.gapSize = 100);
            y += 2;

        }
        if (want("COBWEB")) {
            // ---- Cobweb outline
            y = sectionHeader(ctx, px, y, pw, "COBWEB");
            y = switchRow(ctx, px, y, pw, "Outline", StylesUiConfig.cobwebOutline, mx, my,
                    () -> StylesUiConfig.cobwebOutline = !StylesUiConfig.cobwebOutline);
            int web = StylesUiConfig.cobwebOutlineWidth;
            String webPx = web % 4 == 0 ? String.valueOf(web / 4) : String.valueOf(web / 4.0F).replaceAll("0+$", "");
            y = sliderRow(ctx, px, y, pw, "Thickness", webPx + " px",
                    (web - 1) / 15.0F, Drag.COBWEB_WIDTH, mx, my, () -> StylesUiConfig.cobwebOutlineWidth = 2);
            y = drawCobwebPreview(ctx, px, y, pw);
            y += 6;

        }
        if (want("SHIELD")) {
            // ---- Shield
            y = sectionHeader(ctx, px, y, pw, "SHIELD");
            int opacity = StylesUiConfig.shieldOpacity;
            y = sliderRow(ctx, px, y, pw, "See-through", opacity >= 100 ? "Normal" : opacity + "%",
                    (opacity - 15) / 85.0F, Drag.SHIELD, mx, my, () -> StylesUiConfig.shieldOpacity = 100);
            int shieldSize = StylesUiConfig.shieldSize;
            y = sliderRow(ctx, px, y, pw, "Size in hand", shieldSize == 100 ? "Normal" : shieldSize + "%",
                    (shieldSize - 20) / 130.0F, Drag.SHIELD_SIZE, mx, my, () -> StylesUiConfig.shieldSize = 100);
            y = colorPicker(ctx, px, y, pw, mx, my, PICK_SHIELD);
            y += 2;

        }
        if (want("TOTEM")) {
            // ---- Totem
            y = sectionHeader(ctx, px, y, pw, "TOTEM");
            int totem = StylesUiConfig.totemSize;
            y = sliderRow(ctx, px, y, pw, "Size in hand", totem == 100 ? "Normal" : totem + "%",
                    (totem - 20) / 130.0F, Drag.TOTEM, mx, my, () -> StylesUiConfig.totemSize = 100);
            int pop = StylesUiConfig.totemPopSize;
            y = sliderRow(ctx, px, y, pw, "Pop size", pop == 100 ? "Normal" : pop + "%",
                    (pop - 20) / 130.0F, Drag.TOTEM_POP, mx, my, () -> StylesUiConfig.totemPopSize = 100);
            boolean testHover = inside(mx, my, px, y - 2, pw, 14);
            button(ctx, px, y - 2, pw, 14, "TEST POP", testHover, ButtonStyle.NORMAL);
            hit(px, y - 2, pw, 14, (b, x2, y2) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world != null) {
                    mc.setScreen(null);
                    mc.gameRenderer.showFloatingItem(new ItemStack(Items.TOTEM_OF_UNDYING));
                    reopenAtMs = System.currentTimeMillis() + 2200;
                }
            });
            y += 22;

        }
        if (want("HITBOX")) {
            // ---- Hitbox (F3+B) colours
            y = sectionHeader(ctx, px, y, pw, "HITBOX");
            y = colorPicker(ctx, px, y, pw, mx, my, PICK_HITBOX);
        }
        if (want("CRYSTALBOX")) {
            y = sectionHeader(ctx, px, y, pw, "INVISIBLE CRYSTAL BOX");
            y = colorPicker(ctx, px, y, pw, mx, my, PICK_CRYSTAL_BOX);

        }
        if (want("SPOTIFY")) {
            // ---- Spotify (not in the clean download)
            if (SpotifyNowPlaying.AVAILABLE) {
                y = sectionHeader(ctx, px, y, pw, "SPOTIFY");
                y = switchRow(ctx, px, y, pw, "Show what's playing", StylesUiConfig.spotifyHud, mx, my,
                        () -> StylesUiConfig.spotifyHud = !StylesUiConfig.spotifyHud);
                y = switchRow(ctx, px, y, pw, "Only when the song changes", StylesUiConfig.spotifyPopup, mx, my,
                        () -> StylesUiConfig.spotifyPopup = !StylesUiConfig.spotifyPopup);
                y = drawSpotifyPreview(ctx, px, y, pw, mx, my);
            }

        }
        if (want("TABICON")) {
            // ---- Tab icon
            y = sectionHeader(ctx, px, y, pw, "TAB ICON");
            y = switchRow(ctx, px, y, pw, "Show me in tab", StylesUiConfig.showInTab, mx, my,
                    () -> {
                        StylesUiConfig.showInTab = !StylesUiConfig.showInTab;
                        StylesPresence.settingChanged();
                    });
            scaled(ctx, fitText("A tornado in front of your name for others with Bauloo Styles", Math.round(pw / 0.8F)),
                    px, y - 4, c.textDim, 0.8F, false);
            y += 10;

        }
        if (want("MENU")) {
            // ---- Menu
            y = sectionHeader(ctx, px, y, pw, "MENU");
            y = switchRow(ctx, px, y, pw, "Apply instantly", StylesUiConfig.applyInstantly, mx, my,
                    () -> {
                        StylesUiConfig.applyInstantly = !StylesUiConfig.applyInstantly;
                        PENDING.clear();
                    });
            scaled(ctx, fitText(StylesUiConfig.applyInstantly ? "Every click reloads right away"
                            : "Clicks select styles, APPLY loads them all at once", Math.round(pw / 0.8F)),
                    px, y - 4, c.textDim, 0.8F, false);
            y += 10;

        }
        if (want("SOCIAL")) {
            // ---- Social badge
            y = sectionHeader(ctx, px, y, pw, "SOCIAL BADGE");
            String[] socials = Watermark.SOCIALS;
            int socialCount = socials.length + 1;
            int tile = Math.max(12, Math.min(22, (pw - (socialCount - 1) * 3) / socialCount));
            for (int i = 0; i < socialCount; i++) {
                int tx = px + i * (tile + 3);
                String value = i == 0 ? "off" : socials[i - 1];
                boolean chosen = value.equals(StylesUiConfig.socialLogo)
                        || (i == 0 && !Watermark.validSocial(StylesUiConfig.socialLogo));
                boolean hover = inside(mx, my, tx, y, tile, tile);
                if (i == 0) {
                    soft(ctx, tx, y, tile, tile, c.base);
                    textCenter(ctx, "Off", tx, tile, y + (tile - 8) / 2, chosen ? c.text : c.textDim);
                } else {
                    ctx.drawTexture(RenderPipelines.GUI_TEXTURED, Watermark.icon(value), tx, y, 0, 0, tile, tile,
                            128, 128, 128, 128, chosen || hover ? 0xFFFFFFFF : 0x99FFFFFF);
                }
                if (chosen || hover) {
                    softLine(ctx, tx - 1, y - 1, tile + 2, tile + 2, chosen ? c.accent : c.textDim);
                }
                hit(tx, y, tile, tile, (b, x2, y2) -> {
                    StylesUiConfig.socialLogo = value;
                    StylesUiConfig.save();
                });
            }
            y += tile + 6;
            boolean nameHover = inside(mx, my, px, y, pw, 15);
            soft(ctx, px, y, pw, 15, socialFocused || nameHover ? c.surface2 : c.base);
            if (socialFocused) {
                softLine(ctx, px, y, pw, 15, c.accent);
            }
            boolean blink = socialFocused && (System.currentTimeMillis() / 500) % 2 == 0;
            String shown = StylesUiConfig.socialName.isEmpty() && !socialFocused ? "Your name..." : StylesUiConfig.socialName + (blink ? "_" : "");
            text(ctx, fitText(shown, pw - 10), px + 5, y + 4, StylesUiConfig.socialName.isEmpty() && !socialFocused ? c.textDim : c.text);
            hit(px, y, pw, 15, (b, x2, y2) -> socialFocused = true);
            y += 19;
            text(ctx, fitText("Shows on the recipe book button", pw), px, y, c.textDim);
            y += 14;
        }
        if (thirdX > 0) {
            middleEnd = y;
            px = thirdX;
            y = top;
        }
        if (want("ANCHOR")) {
            y = sectionHeader(ctx, px, y, pw, "ANCHOR CHARGE");
            y = colorPicker(ctx, px, y, pw, mx, my, PICK_ANCHOR);
            y = switchRow(ctx, px, y, pw, "Look full at 1 charge", StylesUiConfig.anchorShowFull, mx, my, () -> {
                StylesUiConfig.anchorShowFull = !StylesUiConfig.anchorShowFull;
                StylesUiConfig.save();
                AnchorGlow.apply();
            });
            text(ctx, fitText("1 glowstone already looks full", pw), px, y - 4, c.textDim);
            y += 14;
            y = switchRow(ctx, px, y, pw, "Glowing pixels", StylesUiConfig.anchorPixelGlow, mx, my, () -> {
                StylesUiConfig.anchorPixelGlow = !StylesUiConfig.anchorPixelGlow;
                StylesUiConfig.save();
                AnchorPixelGlow.applyLive();
            });
            text(ctx, fitText("The small bright pixels glow in the dark", pw), px, y - 4, c.textDim);
            y += 14;
        }
        if (want("CROSSHAIR")) {
            y = sectionHeader(ctx, px, y, pw, "CROSSHAIR");
            y = colorPicker(ctx, px, y, pw, mx, my, PICK_CROSSHAIR);
            y = switchRow(ctx, px, y, pw, "Gradient", StylesUiConfig.crosshairGradient, mx, my, () -> {
                StylesUiConfig.crosshairGradient = !StylesUiConfig.crosshairGradient;
                if (StylesUiConfig.crosshairGradient) {
                    StylesUiConfig.crosshairCustom = true;
                }
            });
            if (StylesUiConfig.crosshairGradient) {
                text(ctx, "Fades to", px, y, c.textSoft);
                y += 12;
                int n = PICK_SWATCHES.length;
                int gw = Math.max(8, (pw - (n - 1) * 4) / n);
                for (int i = 0; i < n; i++) {
                    int sx = px + i * (gw + 4);
                    int swatch = PICK_SWATCHES[i];
                    boolean selected = (StylesUiConfig.crosshairColor2 & 0xFFFFFF) == swatch;
                    soft(ctx, sx, y, gw, 12, 0xFF000000 | swatch);
                    if (selected || inside(mx, my, sx, y, gw, 12)) {
                        softLine(ctx, sx - 1, y - 1, gw + 2, 14, selected ? c.text : c.textDim);
                    }
                    hit(sx, y, gw, 12, (b, x2, y2) -> {
                        StylesUiConfig.crosshairColor2 = swatch;
                        StylesUiConfig.save();
                    });
                }
                y += 18;
                // the fade itself, big enough to see
                for (int i = 0; i < pw; i++) {
                    ctx.fill(px + i, y, px + i + 1, y + 6, StylesCrosshair.mix(0xFF000000 | StylesUiConfig.crosshairColor,
                            0xFF000000 | StylesUiConfig.crosshairColor2, StylesCrosshair.shaped(i / (float) Math.max(1, pw - 1))));
                }
                cutCorners(ctx, px, y, pw, 6, c.surface);
                y += 14;
                int strength = StylesUiConfig.gradientStrength;
                y = sliderRow(ctx, px, y, pw, "Strength", strength == 100 ? "Normal" : strength + "%",
                        (strength - 50) / 350.0F, Drag.GRADIENT, mx, my, () -> StylesUiConfig.gradientStrength = 100);
            }
        }
        if (want("BLOCK")) {
            y = sectionHeader(ctx, px, y, pw, "BLOCK OUTLINE");
            y = colorPicker(ctx, px, y, pw, mx, my, PICK_BLOCK);
            int blockWidth = StylesUiConfig.blockOutlineWidth;
            y = sliderRow(ctx, px, y, pw, "Thickness", blockWidth == 1 ? "Thin" : String.valueOf(blockWidth),
                    (blockWidth - 1) / 5.0F, Drag.BLOCK_WIDTH, mx, my, () -> StylesUiConfig.blockOutlineWidth = 3);

        }
        if (want("KILL")) {
            // ---- Kill effect
            y = sectionHeader(ctx, px, y, pw, "KILL EFFECT");
            int chipX = px;
            int chipH = 12;
            String[][] effects = new String[KillEffect.EFFECTS.length + 1][];
            effects[0] = new String[]{"off", "Off"};
            System.arraycopy(KillEffect.EFFECTS, 0, effects, 1, KillEffect.EFFECTS.length);
            for (String[] effect : effects) {
                int cw = textRenderer.getWidth(effect[1]) + 10;
                if (chipX > px && chipX + cw > px + pw) {
                    chipX = px;
                    y += chipH + 4;
                }
                boolean chosen = effect[0].equals(StylesUiConfig.killEffect)
                        || (effect[0].equals("off") && !KillEffect.valid(StylesUiConfig.killEffect));
                boolean hover = inside(mx, my, chipX, y, cw, chipH);
                soft(ctx, chipX, y, cw, chipH, chosen ? c.accentDark : c.base);
                softLine(ctx, chipX, y, cw, chipH, chosen ? c.accent : hover ? c.textDim : c.borderDim);
                text(ctx, effect[1], chipX + 5, y + 2, chosen ? c.text : hover ? c.textSoft : c.textDim);
                final String key = effect[0];
                hit(chipX, y, cw, chipH, (b, x2, y2) -> {
                    StylesUiConfig.killEffect = key;
                    StylesUiConfig.save();
                });
                chipX += cw + 4;
            }
            y += chipH + 8;
            y = switchRow(ctx, px, y, pw, "Only my kills", StylesUiConfig.killOnlyMine, mx, my,
                    () -> StylesUiConfig.killOnlyMine = !StylesUiConfig.killOnlyMine);
            y = switchRow(ctx, px, y, pw, "cpvp.gg kill sound", StylesUiConfig.killSound, mx, my, () -> {
                StylesUiConfig.killSound = !StylesUiConfig.killSound;
                KillEffect.killSound();
            });
            if (StylesUiConfig.killSound) {
                int vol = StylesUiConfig.killVolume;
                y = sliderRow(ctx, px, y, pw, "Volume", vol + "%", (vol - 5) / 95.0F, Drag.KILL_VOLUME, mx, my, () -> {
                    StylesUiConfig.killVolume = 100;
                    KillEffect.killSound();
                });
            }
            y = switchRow(ctx, px, y, pw, "No explosion sounds", StylesUiConfig.noExplosionSound, mx, my,
                    () -> StylesUiConfig.noExplosionSound = !StylesUiConfig.noExplosionSound);
            if (KillEffect.valid(StylesUiConfig.killEffect) && !"lightning".equals(StylesUiConfig.killEffect)) {
                int killSw = Math.max(10, (pw - (PICK_SWATCHES.length - 1) * 4) / PICK_SWATCHES.length);
                for (int i = 0; i < PICK_SWATCHES.length; i++) {
                    int sx = px + i * (killSw + 4);
                    int swatch = PICK_SWATCHES[i];
                    boolean selected = (StylesUiConfig.killColor & 0xFFFFFF) == swatch;
                    soft(ctx, sx, y, killSw, 10, 0xFF000000 | swatch);
                    if (selected || inside(mx, my, sx, y, killSw, 10)) {
                        softLine(ctx, sx - 1, y - 1, killSw + 2, 12, selected ? c.text : c.textDim);
                    }
                    hit(sx, y, killSw, 10, (b, x2, y2) -> {
                        StylesUiConfig.killColor = swatch;
                        StylesUiConfig.save();
                    });
                }
                y += 16;
            }
            boolean killTestHover = inside(mx, my, px, y - 2, pw, 14);
            button(ctx, px, y - 2, pw, 14, "TEST", killTestHover, ButtonStyle.NORMAL);
            hit(px, y - 2, pw, 14, (b, x2, y2) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world != null) {
                    mc.setScreen(null);
                    KillEffect.test();
                    reopenAtMs = System.currentTimeMillis() + 2500;
                }
            });
            y += 22;
        }
        if (want("GLINT")) {
            y = sectionHeader(ctx, px, y, pw, "ENCHANT GLINT");
            y = colorPicker(ctx, px, y, pw, mx, my, PICK_GLINT);
            y = switchRow(ctx, px, y, pw, "Gradient (two colors)", StylesUiConfig.glintGradient, mx, my, () -> {
                StylesUiConfig.glintGradient = !StylesUiConfig.glintGradient;
                if (StylesUiConfig.glintGradient) {
                    StylesUiConfig.glintRainbow = false;
                    StylesUiConfig.glintCustom = true;
                }
                StylesUiConfig.save();
                EnchantGlint.applyLive();
            });
            if (StylesUiConfig.glintGradient) {
                text(ctx, "Fades to", px, y, c.textSoft);
                y += 12;
                int n = PICK_SWATCHES.length;
                int gw = Math.max(8, (pw - (n - 1) * 4) / n);
                for (int i = 0; i < n; i++) {
                    int sx = px + i * (gw + 4);
                    int swatch = PICK_SWATCHES[i];
                    boolean selected = (StylesUiConfig.glintColor2 & 0xFFFFFF) == swatch;
                    soft(ctx, sx, y, gw, 12, 0xFF000000 | swatch);
                    if (selected || inside(mx, my, sx, y, gw, 12)) {
                        softLine(ctx, sx - 1, y - 1, gw + 2, 14, selected ? c.text : c.textDim);
                    }
                    hit(sx, y, gw, 12, (b, x2, y2) -> {
                        StylesUiConfig.glintColor2 = swatch;
                        StylesUiConfig.save();
                        EnchantGlint.applyLive();
                    });
                }
                y += 18;
            }
            y = switchRow(ctx, px, y, pw, "Rainbow", StylesUiConfig.glintRainbow, mx, my, () -> {
                StylesUiConfig.glintRainbow = !StylesUiConfig.glintRainbow;
                if (StylesUiConfig.glintRainbow) {
                    StylesUiConfig.glintGradient = false;
                    StylesUiConfig.glintCustom = true;
                }
                StylesUiConfig.save();
                EnchantGlint.applyLive();
            });
            // the colours as they run across the glint
            for (int i = 0; i < pw; i++) {
                int color = StylesUiConfig.glintCustom ? EnchantGlint.colorAt(i, 0, pw, 1) : 0x8040FF;
                ctx.fill(px + i, y, px + i + 1, y + 6, 0xFF000000 | color);
            }
            cutCorners(ctx, px, y, pw, 6, c.surface);
            y += 16;
        }
        return Math.max(Math.max(leftEnd, middleEnd), y);
    }

    private static final int[] PICK_SWATCHES = {0xB388FF, 0xFF5AC8, 0xFF4A5A, 0xFFB547, 0x6CFF8A, 0x75D4FF, 0x4F8CFF, 0xFFFFFF};

    private static final int PICK_HITBOX = 0;
    private static final int PICK_CROSSHAIR = 1;
    private static final int PICK_CRYSTAL_BOX = 2;
    private static final int PICK_ANCHOR = 3;
    private static final int PICK_CORE = 4;
    private static final int PICK_GLINT = 6;
    private static final int PICK_FIRE = 7;
    private static final int PICK_SHIELD = 8;
    private static final int PICK_BLOCK = 9;
    /** block outline: black first, that is the look most people want */
    private static final int[] BLOCK_SWATCHES = {0x000000, 0xB388FF, 0xFF5AC8, 0xFF4A5A, 0xFFB547, 0x6CFF8A, 0x75D4FF, 0xFFFFFF};

    private static boolean pickOn(int kind) {
        return switch (kind) {
            case PICK_HITBOX -> StylesUiConfig.hitboxCustom;
            case PICK_CROSSHAIR -> StylesUiConfig.crosshairCustom;
            case PICK_ANCHOR -> StylesUiConfig.anchorGlowCustom;
            case PICK_CORE -> StylesUiConfig.coreCustom;
            case PICK_GLINT -> StylesUiConfig.glintCustom;
            case PICK_FIRE -> StylesUiConfig.fireCustom;
            case PICK_SHIELD -> StylesUiConfig.shieldCustom;
            case PICK_BLOCK -> StylesUiConfig.blockOutlineCustom;
            default -> StylesUiConfig.crystalBoxCustom;
        };
    }

    private static int pickColor(int kind) {
        return switch (kind) {
            case PICK_HITBOX -> StylesUiConfig.hitboxColor;
            case PICK_CROSSHAIR -> StylesUiConfig.crosshairColor;
            case PICK_ANCHOR -> StylesUiConfig.anchorGlowColor;
            case PICK_CORE -> StylesUiConfig.coreColor;
            case PICK_GLINT -> StylesUiConfig.glintColor;
            case PICK_FIRE -> StylesUiConfig.fireColor;
            case PICK_SHIELD -> StylesUiConfig.shieldColor;
            case PICK_BLOCK -> StylesUiConfig.blockOutlineColor;
            default -> StylesUiConfig.crystalBoxColor;
        };
    }

    /** True while a colour bar is dragged: the colour is set, the reload waits for the release. */
    private static boolean huePreview;
    /** Which picker's colour bar is dragged (Drag.PICK_HUE). */
    private int huePick;

    private static void setPick(int kind, Boolean on, Integer color) {
        switch (kind) {
            case PICK_HITBOX -> {
                if (on != null) StylesUiConfig.hitboxCustom = on;
                if (color != null) StylesUiConfig.hitboxColor = color;
            }
            case PICK_CROSSHAIR -> {
                if (on != null) StylesUiConfig.crosshairCustom = on;
                if (color != null) StylesUiConfig.crosshairColor = color;
            }
            case PICK_ANCHOR -> {
                if (on != null) StylesUiConfig.anchorGlowCustom = on;
                if (color != null) StylesUiConfig.anchorGlowColor = color;
                // the glow is a generated texture pack: rebuild it and reload
                if (!huePreview) {
                    StylesUiConfig.save();
                    AnchorGlow.apply();
                }
            }
            case PICK_CORE -> {
                if (on != null) StylesUiConfig.coreCustom = on;
                if (color != null) StylesUiConfig.coreColor = color;
                if (!huePreview) {
                    StylesUiConfig.save();
                    CrystalCore.apply();
                }
            }
            case PICK_GLINT -> {
                if (on != null) StylesUiConfig.glintCustom = on;
                if (color != null) {
                    StylesUiConfig.glintColor = color;
                    StylesUiConfig.glintRainbow = false;
                }
                if (!huePreview) {
                    StylesUiConfig.save();
                    EnchantGlint.applyLive();
                }
            }
            case PICK_FIRE -> {
                if (on != null) StylesUiConfig.fireCustom = on;
                if (color != null) StylesUiConfig.fireColor = color;
                if (!huePreview) {
                    StylesUiConfig.save();
                    FireColor.applyLive();
                }
            }
            case PICK_SHIELD -> {
                if (on != null) StylesUiConfig.shieldCustom = on;
                if (color != null) StylesUiConfig.shieldColor = color;
                if (!huePreview) {
                    StylesUiConfig.save();
                    ShieldColor.applyLive();
                }
            }
            case PICK_BLOCK -> {
                if (on != null) StylesUiConfig.blockOutlineCustom = on;
                if (color != null) StylesUiConfig.blockOutlineColor = color;
            }
            default -> {
                if (on != null) StylesUiConfig.crystalBoxCustom = on;
                if (color != null) StylesUiConfig.crystalBoxColor = color;
            }
        }
    }

    /** On/off, a live preview on the left and colour swatches on the right; returns the y below. */
    private int colorPicker(DrawContext ctx, int px, int y, int pw, int mx, int my, int kind) {
        boolean on = pickOn(kind);
        y = switchRow(ctx, px, y, pw, "Own color", on, mx, my, () -> setPick(kind, !pickOn(kind), null));
        int color = 0xFF000000 | pickColor(kind);
        int shown = on ? color : 0xFFFFFFFF;
        int box = kind == PICK_GLINT ? 118 : 76; // glint: bigger, so the armor shine is easy to see
        soft(ctx, px, y, box, box, c.base);
        if (kind == PICK_HITBOX) {
            drawHitboxPreview(ctx, px, y, box, shown);
        } else if (kind == PICK_CROSSHAIR) {
            drawCrosshairPreview(ctx, px, y, box, shown);
        } else if (kind == PICK_ANCHOR) {
            drawAnchorPreview(ctx, px, y, box, on);
        } else if (kind == PICK_CORE) {
            drawCrystalPreview(ctx, px, y, box, box);
        } else if (kind == PICK_GLINT) {
            drawGlintPreview(ctx, px, y, box);
        } else if (kind == PICK_FIRE) {
            drawFirePreview(ctx, px, y, box);
        } else if (kind == PICK_SHIELD) {
            drawShieldPreview(ctx, px, y, box);
        } else if (kind == PICK_BLOCK) {
            drawBlockOutlinePreview(ctx, px, y, box, on);
        } else {
            drawBoxPreview(ctx, px, y, box, shown);
        }
        int sx0 = px + box + 8;
        int perRow = 4;
        int sw = Math.max(10, (pw - box - 8 - (perRow - 1) * 4) / perRow);
        int[] swatches = kind == PICK_BLOCK ? BLOCK_SWATCHES : PICK_SWATCHES;
        for (int i = 0; i < swatches.length; i++) {
            int sx = sx0 + (i % perRow) * (sw + 4);
            int sy = y + 4 + (i / perRow) * 18;
            int swatch = swatches[i];
            boolean selected = on && (color & 0xFFFFFF) == swatch;
            soft(ctx, sx, sy, sw, 12, 0xFF000000 | swatch);
            if (selected || inside(mx, my, sx, sy, sw, 12)) {
                softLine(ctx, sx - 1, sy - 1, sw + 2, 14, selected ? c.text : c.textDim);
            }
            hit(sx, sy, sw, 12, (b, x2, y2) -> {
                setPick(kind, true, swatch);
                StylesUiConfig.save();
            });
        }

        // every colour, like the player outline: white, the rainbow, black
        int rows = (swatches.length + perRow - 1) / perRow;
        int barY = y + 4 + rows * 18 + 4;
        int barW = perRow * sw + (perRow - 1) * 4;
        int barH = 6;
        for (int i = 0; i < barW; i++) {
            ctx.fill(sx0 + i, barY, sx0 + i + 1, barY + barH,
                    0xFF000000 | PlayerOutline.sliderColor(i / (float) Math.max(1, barW - 1)));
        }
        cutCorners(ctx, sx0, barY, barW, barH, c.surface);
        if (on) {
            int knobX = sx0 + Math.round(PlayerOutline.sliderPos(color) * (barW - 1));
            soft(ctx, knobX - 2, barY - 2, 4, barH + 4, 0xFF000000);
            ctx.fill(knobX - 1, barY - 1, knobX + 1, barY + barH + 1, 0xFFFFFFFF);
        }
        final int barX = sx0;
        hit(sx0, barY - 4, barW, barH + 8, (b, x2, y2) -> {
            drag = Drag.PICK_HUE;
            huePick = kind;
            dragX = barX;
            dragW = barW;
            updateDrag(x2);
        });
        return y + box + 12;
    }

    /** The burning fire itself, as the block atlas shows it right now (recoloured live). */
    private void drawFirePreview(DrawContext ctx, int x, int y, int size) {
        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.client.texture.Sprite fire = mc.getAtlasManager().getSprite(net.minecraft.client.render.model.ModelBaker.FIRE_0);
        int s = size - 12;
        ctx.drawSpriteStretched(RenderPipelines.GUI_TEXTURED, fire, x + 6, y + 6, s, s);
    }

    private static Identifier shieldPreviewId;
    private static String shieldPreviewKey = "";

    /**
     * The front of the shield as its own small texture, rebuilt when the colour changes
     * (items in menus are drawn once and then reused, so an item preview would stay old).
     */
    private void drawShieldPreview(DrawContext ctx, int x, int y, int size) {
        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.client.texture.Sprite sprite = mc.getAtlasManager()
                .getSprite(net.minecraft.client.render.model.ModelBaker.SHIELD_BASE_NO_PATTERN);
        net.minecraft.client.texture.NativeImage source =
                ((de.bauloo.styles.mixin.SpriteContentsAccessor) sprite.getContents()).baulooStyles$image();
        if (source == null) {
            return;
        }
        String key = StylesUiConfig.shieldCustom + ":" + StylesUiConfig.shieldColor + ":" + System.identityHashCode(source);
        if (shieldPreviewId == null || !key.equals(shieldPreviewKey)) {
            // the plate's front face sits at 1,1 and is 12x22 in the 64x64 shield texture
            int unit = Math.max(1, sprite.getContents().getWidth() / 64);
            int w = 12 * unit;
            int h = 22 * unit;
            net.minecraft.client.texture.NativeImage image = new net.minecraft.client.texture.NativeImage(w, h, false);
            int color = StylesUiConfig.shieldColor;
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    int c = source.getColorArgb(unit + px, unit + py);
                    if (StylesUiConfig.shieldCustom) {
                        float lum = (0.3F * ((c >> 16) & 0xFF) + 0.59F * ((c >> 8) & 0xFF) + 0.11F * (c & 0xFF)) / 255.0F;
                        float v = Math.min(1.0F, 0.35F + lum * 1.3F);
                        int r = Math.round(((color >> 16) & 0xFF) * v);
                        int g = Math.round(((color >> 8) & 0xFF) * v);
                        int b = Math.round((color & 0xFF) * v);
                        c = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
                    }
                    image.setColorArgb(px, py, c);
                }
            }
            Identifier id = Identifier.of(BaulooStylesClient.MOD_ID, "shield_preview");
            mc.getTextureManager().registerTexture(id, new net.minecraft.client.texture.NativeImageBackedTexture(id::toString, image));
            shieldPreviewId = id;
            shieldPreviewKey = key;
        }
        int h = size - 12;
        int w = Math.round(h * 12 / 22.0F);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, shieldPreviewId, x + (size - w) / 2, y + 6, 0, 0, w, h, w, h);
    }

    /** You with the outline around you: flat silhouettes in the colour behind, you on top. */
    /** Draws you in a preview box. The name tag belongs over your head in the world, not in here. */
    private static void previewEntity(DrawContext ctx, int x1, int y1, int x2, int y2, int scale,
                                      float yOffset, float lookX, float lookY,
                                      net.minecraft.entity.LivingEntity entity) {
        OutlinePreview.hideName = true;
        try {
            int[] box = guiRect(ctx, x1, y1, x2, y2);
            int[] look = guiRect(ctx, Math.round(lookX), Math.round(lookY), Math.round(lookX), Math.round(lookY));
            net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(ctx, box[0], box[1], box[2], box[3],
                    Math.round(scale * guiScale(ctx)), yOffset, look[0], look[1], entity);
        } finally {
            OutlinePreview.hideName = false;
        }
    }

    /** You with your armor, big, so the enchant glint on it shows as it looks in game. */
    private void drawGlintPreview(DrawContext ctx, int x, int y, int size) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        int scale = Math.round(size * 0.40F);
        previewEntity(ctx, x + 2, y + 2, x + size - 2, y + size - 2,
                scale, 0.0625F, x + size / 2.0F, y + size * 0.45F, mc.player);
    }

    /** The fully charged anchor side with the recoloured glow (after the first apply). */
    private void drawAnchorPreview(DrawContext ctx, int x, int y, int size, boolean on) {
        Identifier glow = on || StylesUiConfig.anchorShowFull ? AnchorGlow.preview() : null;
        int s = size - 12;
        if (glow != null) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, glow, x + 6, y + 6, 0, 0, s, s,
                    AnchorGlow.previewW, AnchorGlow.previewH, AnchorGlow.previewW, AnchorGlow.previewH);
        } else {
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(x + size / 2.0F, y + size / 2.0F);
            m.scale(3.0F, 3.0F);
            ctx.drawItem(new ItemStack(Items.RESPAWN_ANCHOR), -8, -8);
            m.popMatrix();
        }
    }

    /** A block sunk in dirt with its outline, in the colour and thickness it gets in game. */
    private void drawBlockOutlinePreview(DrawContext ctx, int x, int y, int size, boolean on) {
        net.minecraft.client.texture.Sprite dirt = MinecraftClient.getInstance().getAtlasManager().getSprite(
                new net.minecraft.client.util.SpriteIdentifier(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
                        Identifier.ofVanilla("block/dirt")));
        int half = size / 2;
        for (int i = 0; i < 4; i++) {
            ctx.drawSpriteStretched(RenderPipelines.GUI_TEXTURED, dirt, x + (i % 2) * half, y + (i / 2) * half, half, half);
        }
        int color = on ? 0xFF000000 | StylesUiConfig.blockOutlineColor : 0x66000000;
        int t = on ? StylesUiConfig.blockOutlineWidth : 1;
        int s = size / 2;
        int d = size / 6;
        int fx = x + (size - s - d) / 2;
        int fy = y + (size - s - d) / 2 + d;
        // front face
        ctx.fill(fx, fy, fx + s, fy + t, color);
        ctx.fill(fx, fy + s - t, fx + s, fy + s, color);
        ctx.fill(fx, fy, fx + t, fy + s, color);
        ctx.fill(fx + s - t, fy, fx + s, fy + s, color);
        // the two back edges you can see
        ctx.fill(fx + d, fy - d, fx + d + s, fy - d + t, color);
        ctx.fill(fx + d + s - t, fy - d, fx + d + s, fy - d + s, color);
        // top-left, top-right and bottom-right corners going back
        for (int i = 0; i <= d; i++) {
            ctx.fill(fx + i, fy - i, fx + i + t, fy - i + t, color);
            ctx.fill(fx + s - t + i, fy - i, fx + s + i, fy - i + t, color);
            ctx.fill(fx + s - t + i, fy + s - t - i, fx + s + i, fy + s - i, color);
        }
    }

    /** The card as it shows in game (scaled to fit), with the song playing right now or a note why not. */
    private int drawSpotifyPreview(DrawContext ctx, int x, int y, int w, int mx, int my) {
        float scale = Math.min(1.0F, (w - 12) / (float) SpotifyHud.W);
        int h = Math.round(SpotifyHud.H * scale) + 12;
        soft(ctx, x, y, w, h, c.base);
        String title = SpotifyNowPlaying.title;
        if (!StylesUiConfig.spotifyHud) {
            text(ctx, fitText("Off", w - 12), x + 6, y + h / 2 - 4, c.textDim);
        } else if (title != null) {
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(x + (w - SpotifyHud.W * scale) / 2.0F, y + 6);
            m.scale(scale, scale);
            SpotifyHud.draw(ctx, textRenderer, 0, 0, title, SpotifyNowPlaying.artist, 1.0F);
            m.popMatrix();
        } else {
            String why = SpotifyNowPlaying.appOpen ? "Spotify is paused" : "Open the Spotify app and play a song";
            text(ctx, fitText(why, w - 12), x + 6, y + h / 2 - 4, c.textDim);
        }
        y += h + 4;
        boolean hover = inside(mx, my, x, y, w, 14);
        button(ctx, x, y, w, 14, "MOVE", hover, ButtonStyle.NORMAL);
        hit(x, y, w, 14, (b, x2, y2) -> MinecraftClient.getInstance().setScreen(new SpotifyMoveScreen(this)));
        y += 18;
        text(ctx, fitText("Spotify app only. Cover from Deezer / iTunes.", w), x, y, c.textDim);
        return y + 14;
    }

    /** A wireframe cube like the one drawn around invisible crystals. */
    private void drawBoxPreview(DrawContext ctx, int x, int y, int size, int color) {
        int s = size / 2;
        int d = size / 6;
        int fx = x + (size - s - d) / 2;
        int fy = y + (size - s - d) / 2 + d;
        softLine(ctx, fx + d, fy - d, s, s, StylesTheme.withAlpha(color, 0x99));
        softLine(ctx, fx, fy, s, s, color);
        for (int i = 0; i <= d; i++) {
            ctx.fill(fx + i, fy - i, fx + i + 1, fy - i + 1, color);
            ctx.fill(fx + s - 1 + i, fy - i, fx + s + i, fy - i + 1, color);
            ctx.fill(fx + i, fy + s - 1 - i, fx + i + 1, fy + s - i, color);
            ctx.fill(fx + s - 1 + i, fy + s - 1 - i, fx + s + i, fy + s - i, color);
        }
    }

    /** You in a small box with your hitbox drawn around you in the chosen colour. */
    private void drawHitboxPreview(DrawContext ctx, int x, int y, int size, int color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // player is 0.6 x 1.8 blocks; drawEntity centres it in the box
        int scale = Math.round(size * 0.36F);
        int hw = Math.round(0.6F * scale);
        int hh = Math.round(1.8F * scale);
        if (mc.player != null) {
            previewEntity(ctx, x + 2, y + 2, x + size - 2, y + size - 2,
                    scale, 0.0625F, x + size / 2.0F, y + size / 2.0F, mc.player);
        }
        softLine(ctx, x + (size - hw) / 2, y + (size - hh) / 2, hw, hh, color);
    }

    /** The crosshair, three times as big, in the chosen colour or gradient. */
    private void drawCrosshairPreview(DrawContext ctx, int x, int y, int size, int color) {
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x + size / 2.0F, y + size / 2.0F);
        m.scale(3.0F, 3.0F);
        if (StylesUiConfig.crosshairCustom) {
            StylesCrosshair.draw(ctx, Identifier.ofVanilla("hud/crosshair"), -8, -8, 15, 15);
        } else {
            ctx.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                    Identifier.ofVanilla("hud/crosshair"), -8, -8, 15, 15, color);
        }
        m.popMatrix();
    }

    /** Group title with an accent mark and a thin line; returns the y below it. */
    private int sectionHeader(DrawContext ctx, int px, int y, int pw, String title) {
        if (extraOnly != null) {
            return y;   // one extra in the settings panel: its name is the panel title
        }
        soft(ctx, px, y, 2, 9, c.accent);
        text(ctx, title, px + 6, y + 1, c.text);
        ctx.fill(px, y + 13, px + pw, y + 14, c.borderDim);
        return y + 20;
    }

    /**
     * Label, value and a small RESET on one line, the slider below. Sliders for things in
     * your hand (Drag.SHIELD, SHIELD_SIZE, TOTEM) move from their value and show the item live.
     */
    private int sliderRow(DrawContext ctx, int px, int y, int pw, String label, String value, float t,
            Drag kind, int mx, int my, Runnable reset) {
        int rw = 36;
        text(ctx, fitText(label, pw - rw - 60), px, y, c.textSoft);
        textRight(ctx, value, px + pw - rw - 6, y, c.text);
        boolean resetHover = inside(mx, my, px + pw - rw, y - 3, rw, 13);
        textCenter(ctx, "RESET", px + pw - rw, rw, y, resetHover ? c.accent : c.textDim);
        hit(px + pw - rw, y - 3, rw, 13, (b, x2, y2) -> {
            reset.run();
            StylesUiConfig.save();
        });
        y += 13;
        float clamped = Math.max(0, Math.min(1, t));
        int fill = px + Math.round(clamped * (pw - 1));
        soft(ctx, px, y + 1, pw, 4, c.base);
        soft(ctx, px, y + 1, Math.max(3, fill - px), 4, c.accentDark);
        soft(ctx, fill - 2, y - 2, 5, 10, c.accent);
        final int sliderX = px;
        final int sliderY = y;
        hit(px, y - 4, pw, 14, (b, x2, y2) -> {
            drag = kind;
            dragX = sliderX;
            dragW = pw;
            dragY = sliderY;
            dragStartMouse = x2;
            dragStartT = clamped;
            if (!liveDrag()) {
                updateDrag(x2);
            }
        });
        return y + 16;
    }

    private final net.minecraft.client.render.entity.state.EndCrystalEntityRenderState crystalState =
            new net.minecraft.client.render.entity.state.EndCrystalEntityRenderState();

    /** Draws a spinning end crystal with the active crystal style at the chosen speed. */
    private void drawCrystalPreview(DrawContext ctx, int x, int y, int w, int h) {
        crystalState.entityType = net.minecraft.entity.EntityType.END_CRYSTAL;
        // same as CrystalSpinMixin does in the world: sped-up age for turning, real age for the bob
        float realAge = (System.currentTimeMillis() % 1_000_000L) / 50.0F;
        ((CrystalAge) crystalState).baulooStyles$setRealAge(realAge);
        crystalState.age = realAge * CrystalAge.factor();
        crystalState.baseVisible = false;
        crystalState.beamOffset = null;
        crystalState.light = 0xF000F0;
        crystalState.width = 2.0F;
        crystalState.height = 2.0F;
        float scale = Math.min(w, h) * 0.38F;
        org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotateZ((float) Math.PI);
        org.joml.Quaternionf camera = new org.joml.Quaternionf().rotateX(0.35F);
        rotation.mul(camera);
        int[] box = guiRect(ctx, x + 1, y + 1, x + w - 1, y + h - 1);
        ctx.addEntity(crystalState, scale * guiScale(ctx), new org.joml.Vector3f(0.0F, 1.1F, 0.0F), rotation, camera,
                box[0], box[1], box[2], box[3]);
    }

    // ---------------------------------------------------------------- cosmetics page

    /**
     * Cosmetics: you, big and turning, on the left; slots and cards on the right. Hovering a
     * card tries it on right away, clicking wears it.
     */
    private void drawCosmetics(DrawContext ctx, Layout l, int mx, int my) {
        int left = l.mainX;
        int right = l.side ? l.sideX + l.sideW : l.mainX + l.mainW;
        int top = l.bodyY + 8;
        int bottom = l.footerY - 10;
        text(ctx, "Cosmetics", left, top + 3, c.text);
        scaled(ctx, "Only you see them - nothing is sent to the server", left, top + 15, c.textDim, 0.8F, false);
        int y0 = top + 30;
        int full = right - left;

        // ---- preview
        int pw = Math.max(110, Math.min(200, full * 36 / 100));
        int ph = bottom - y0;
        soft(ctx, left, y0, pw, ph, c.surface);
        ctx.fillGradient(left + 1, y0 + ph / 2, left + pw - 1, bottom - 1, 0x00000000, StylesTheme.withAlpha(c.accentDark, 0x90));
        boolean overPreview = inside(mx, my, left, y0, pw, ph);
        boolean backFirst = cosSlot == Cosmetics.Slot.CAPE || cosSlot == Cosmetics.Slot.MC_CAPE;
        if (cosYawSlot != cosSlot) {
            // capes show the back, everything else the front; it only turns when you drag it
            cosYawSlot = cosSlot;
            cosYaw = backFirst ? 0 : 180;
        }
        final int turnW = pw;
        hit(left, y0, pw, ph, (b, x2, y2) -> {
            drag = Drag.COS_TURN;
            dragStartMouse = x2;
            dragW = turnW;
            cosDragYaw = cosYaw;
        });
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            drawCosmeticPlayer(ctx, mc.player, left + 4, y0 + 4, left + pw - 4, bottom - 22, cosYaw);
        } else {
            textCenter(ctx, "Join a world to see yourself", left, pw, y0 + ph / 2 - 4, c.textDim);
        }
        scaled(ctx, drag == Drag.COS_TURN ? "Turning ..." : "Click and drag to turn", left + 8, bottom - 14,
                overPreview ? c.textSoft : c.textDim, 0.8F, false);

        // ---- slots
        int gx = left + pw + 12;
        int gw = right - gx;
        int sy = y0;
        int slots = Cosmetics.Slot.values().length;
        int slotW = (gw - (slots - 1) * 6) / slots;
        for (int i = 0; i < slots; i++) {
            Cosmetics.Slot slot = Cosmetics.Slot.values()[i];
            int sx = gx + i * (slotW + 6);
            boolean on = slot == cosSlot;
            boolean hover = inside(mx, my, sx, sy, slotW, 26);
            soft(ctx, sx, sy, slotW, 26, on ? c.surface2 : hover ? c.surface : c.base);
            if (on) {
                soft(ctx, sx, sy + 24, slotW, 2, c.accent);
            }
            text(ctx, fitText(slot.title.toUpperCase(Locale.ROOT), slotW - 8), sx + 6, sy + 4, on ? c.text : c.textSoft);
            String worn = Cosmetics.label(slot, Cosmetics.saved(slot));
            scaled(ctx, fitText(worn, Math.round((slotW - 8) / 0.75F)), sx + 6, sy + 15,
                    "None".equals(worn) ? c.textDim : c.accentBright, 0.75F, false);
            hit(sx, sy, slotW, 26, (b, px, py) -> cosSlot = slot);
        }

        // ---- cards: None first, then every cosmetic of the slot
        String[][] items = Cosmetics.of(cosSlot);
        int cardsTop = sy + 34;
        boolean capePage = cosSlot == Cosmetics.Slot.CAPE || cosSlot == Cosmetics.Slot.MC_CAPE;
        int optionsH = capePage ? 76 : 44;
        int cardsBottom = bottom - optionsH;
        int gap = 6;
        int cols = gw >= 420 ? 5 : gw >= 300 ? 4 : 3;
        int cw;
        int ch;
        // add columns until every card fits (the Mojang capes are many)
        while (true) {
            cw = (gw - (cols - 1) * gap) / cols;
            int rows = (items.length + 1 + cols - 1) / cols;
            ch = Math.min(cw - 10, (cardsBottom - cardsTop - (rows - 1) * gap) / rows - 11);
            if (ch >= 30 || cols >= 9) {
                break;
            }
            cols++;
        }
        ch = Math.max(24, ch);
        String saved = Cosmetics.saved(cosSlot);
        for (int i = 0; i <= items.length; i++) {
            String key = i == 0 ? "none" : items[i - 1][0];
            String label = i == 0 ? "None" : items[i - 1][1];
            int cx = gx + (i % cols) * (cw + gap);
            int cy = cardsTop + (i / cols) * (ch + 11 + gap);
            if (cy + ch > cardsBottom + 4) {
                break;
            }
            boolean hover = inside(mx, my, cx, cy, cw, ch + 11);
            boolean worn = key.equals(saved) || (i == 0 && !Cosmetics.valid(cosSlot, saved));
            soft(ctx, cx, cy, cw, ch, hover ? c.surfaceHover : c.surface);
            if (i == 0) {
                textCenter(ctx, "-", cx, cw, cy + ch / 2 - 4, c.textDim);
            } else {
                drawCosmeticIcon(ctx, cosSlot, key, cx + 3, cy + 3, cw - 6, ch - 6);
            }
            if (worn) {
                softLine(ctx, cx - 1, cy - 1, cw + 2, ch + 2, c.accent);
                soft(ctx, cx + 4, cy + 4, 5, 5, c.good);
            } else if (hover) {
                softLine(ctx, cx - 1, cy - 1, cw + 2, ch + 2, c.textSoft);
            }
            textCenter(ctx, fitText(label, cw), cx, cw, cy + ch + 2, worn ? c.accentBright : hover ? c.text : c.textSoft);
            if (hover) {
                Cosmetics.tryOn(cosSlot, key);
            }
            final Cosmetics.Slot slot = cosSlot;
            hit(cx, cy, cw, ch + 11, (b, px, py) -> Cosmetics.save(slot, key));
        }

        // ---- colour and options
        int oy = bottom - optionsH + 6;
        ctx.fill(gx, oy - 4, gx + gw, oy - 3, c.borderDim);
        text(ctx, "COLOR", gx, oy + 2, c.textDim);
        int swX = gx + textWidth("COLOR") + 10;
        int swW = Math.max(10, Math.min(18, (gw - (swX - gx) - 70 - 7 * 4) / 8));
        for (int i = 0; i < PICK_SWATCHES.length; i++) {
            int sx = swX + i * (swW + 4);
            int swatch = PICK_SWATCHES[i];
            boolean chosen = !StylesUiConfig.cosRainbow && (StylesUiConfig.cosColor & 0xFFFFFF) == swatch;
            soft(ctx, sx, oy, swW, 12, 0xFF000000 | swatch);
            if (chosen || inside(mx, my, sx, oy, swW, 12)) {
                softLine(ctx, sx - 1, oy - 1, swW + 2, 14, chosen ? c.text : c.textDim);
            }
            hit(sx, oy, swW, 12, (b, px, py) -> {
                StylesUiConfig.cosColor = swatch;
                StylesUiConfig.cosRainbow = false;
                StylesUiConfig.save();
            });
        }
        int rainX = swX + 8 * (swW + 4) + 6;
        int rainW = right - rainX;
        if (rainW >= 50) {
            switchRow(ctx, rainX, oy + 1, Math.min(90, rainW), "Rainbow", StylesUiConfig.cosRainbow, mx, my,
                    () -> StylesUiConfig.cosRainbow = !StylesUiConfig.cosRainbow);
        }
        scaled(ctx, "Tints halo, orbit, ring, sparkles, ears, hat band and recolorable capes",
                gx, oy + 18, c.textDim, 0.7F, false);
        if (capePage) {
            switchRow(ctx, gx, oy + 30, Math.min(gw, 200), "Cape on the elytra too", StylesUiConfig.cosCapeElytra, mx, my,
                    () -> StylesUiConfig.cosCapeElytra = !StylesUiConfig.cosCapeElytra);
            switchRow(ctx, gx, oy + 44, Math.min(gw, 200), "Recolor cape", StylesUiConfig.cosCapeTint, mx, my,
                    () -> StylesUiConfig.cosCapeTint = !StylesUiConfig.cosCapeTint);
        }
    }

    /** The card picture: cape front or the head/aura icon, fitted into the box. */
    private void drawCosmeticIcon(DrawContext ctx, Cosmetics.Slot slot, String key, int x, int y, int w, int h) {
        Identifier icon = Cosmetics.icon(slot, key);
        if (slot == Cosmetics.Slot.MC_CAPE) {
            if (icon == null) {
                textCenter(ctx, "...", x, w, y + h / 2 - 4, c.textDim);
                return;
            }
            // front of the cape: 1,1 to 11,17 in 64x32 units, scaled to the texture's real size
            int tw = Cosmetics.MC_WIDTH.getOrDefault(key, 64);
            int u = tw / 64;
            int ih = h;
            int iw = Math.round(ih * 10 / 16.0F);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, icon, x + (w - iw) / 2, y, u, u, iw, ih, 10 * u, 16 * u,
                    tw, tw / 2);
            return;
        }
        if (slot == Cosmetics.Slot.CAPE && "color".equals(key)) {
            // the live cape texture: its front sits at 2,2 and is 20 x 32
            int ih = h;
            int iw = Math.round(ih * 20 / 32.0F);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, icon, x + (w - iw) / 2, y, 2, 2, iw, ih, 20, 32, 128, 64);
            return;
        }
        int tw = slot == Cosmetics.Slot.CAPE ? 80 : 128;
        int th = 128;
        float k = Math.min(w / (float) tw, h / (float) th);
        int iw = Math.round(tw * k);
        int ih = Math.round(th * k);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, icon, x + (w - iw) / 2, y + (h - ih) / 2, 0, 0, iw, ih,
                tw, th, tw, th);
    }

    /** You in the preview box, turned by yaw degrees (180 = facing the viewer). */
    /**
     * DrawContext.addEntity ignores the GUI matrix, but this menu is scaled to look the same at every GUI
     * scale - so entity previews get their box in real screen coordinates (else they land off to the side).
     */
    private static int[] guiRect(DrawContext ctx, int x1, int y1, int x2, int y2) {
        org.joml.Vector2f a = ctx.getMatrices().transformPosition(x1, y1, new org.joml.Vector2f());
        org.joml.Vector2f b = ctx.getMatrices().transformPosition(x2, y2, new org.joml.Vector2f());
        return new int[]{Math.round(a.x), Math.round(a.y), Math.round(b.x), Math.round(b.y)};
    }

    private static float guiScale(DrawContext ctx) {
        org.joml.Matrix3x2f m = ctx.getMatrices();
        return (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
    }

    private static void drawCosmeticPlayer(DrawContext ctx, net.minecraft.entity.LivingEntity entity,
                                           int x1, int y1, int x2, int y2, float yaw) {
        var state = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity)
                .getAndUpdateRenderState(entity, 1.0F);
        state.light = 0xF000F0;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof net.minecraft.client.render.entity.state.LivingEntityRenderState living) {
            living.bodyYaw = yaw;
            living.relativeHeadYaw = 0;
            living.pitch = 0;
            living.width /= living.baseScale;
            living.height /= living.baseScale;
            living.baseScale = 1.0F;
        }
        float size = Math.min(x2 - x1, (y2 - y1) / 2.3F) * 0.95F;
        org.joml.Quaternionf camera = new org.joml.Quaternionf().rotateX(0.12F);
        org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotateZ((float) Math.PI).mul(camera);
        OutlinePreview.hideName = true;
        try {
            int[] box = guiRect(ctx, x1, y1, x2, y2);
            ctx.addEntity(state, size * guiScale(ctx), new org.joml.Vector3f(0.0F, state.height / 2 + 0.1F, 0.0F), rotation, camera,
                    box[0], box[1], box[2], box[3]);
        } finally {
            OutlinePreview.hideName = false;
        }
    }

    /** Label on the left, a small switch on the right; returns the next row's y. */
    private int switchRow(DrawContext ctx, int px, int y, int pw, String label, boolean on, int mx, int my, Runnable toggle) {
        int swW = 20;
        int swH = 10;
        int sx = px + pw - swW;
        boolean hover = inside(mx, my, px, y - 2, pw, 14);
        text(ctx, fitText(label, pw - swW - 6), px, y + 1, on || hover ? c.text : c.textSoft);
        soft(ctx, sx, y, swW, swH, on ? c.accentDark : c.base);
        softLine(ctx, sx, y, swW, swH, on ? c.accent : c.borderDim);
        soft(ctx, on ? sx + swW - 8 : sx + 2, y + 2, 6, 6, on ? c.accent : c.textDim);
        hit(px, y - 2, pw, 14, (b, x2, y2) -> {
            toggle.run();
            StylesUiConfig.save();
        });
        return y + 18;
    }

    private void savePreset() {
        StylesPresets.add(presetDraft, active, StylesUiConfig.extras());
        presetNaming = false;
        presetDraft = "";
    }

    private boolean presetIsActive(StylesPresets.Preset preset) {
        for (Tab value : Tab.values()) {
            if (!java.util.Objects.equals(preset.styles().get(value), active.get(value))) {
                return false;
            }
        }
        return true;
    }

    /** the presets panel shows the public list instead of your own */
    private static boolean presetsPublic;

    /** the last look used in this session, for the UNDO bar */
    private static String lastUsedLook;
    private static boolean publicAsked;

    /**
     * Presets, kept simple: MY LOOKS (save, use, share, delete) and PUBLIC (use what others
     * shared). Three numbered steps on top so it needs no explaining.
     */
    private void drawPresets(DrawContext ctx, int px, int top, int pw, int bottom, int mx, int my) {
        int y = top;
        String[] labels = {"MY LOOKS", "PUBLIC"};
        int tx0 = px;
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == presetsPublic;
            int lw = textWidth(labels[i]);
            boolean hover = inside(mx, my, tx0 - 3, y - 3, lw + 6, 16);
            text(ctx, labels[i], tx0, y, on ? c.accent : (hover ? c.text : c.textDim));
            if (on) {
                soft(ctx, tx0, y + 11, lw, 2, c.accent);
            }
            final boolean toPublic = i == 1;
            hit(tx0 - 3, y - 3, lw + 6, 16, (b, x2, y2) -> {
                presetsPublic = toPublic;
                presetNaming = false;
                if (toPublic) {
                    StylesPublicPresets.refresh();
                }
            });
            tx0 += lw + 16;
        }
        y += 20;
        if (!publicAsked) {
            // needed to know which of your looks are already public
            publicAsked = true;
            StylesPublicPresets.refresh();
        }
        if (presetsPublic) {
            drawPublicPresets(ctx, px, y, pw, bottom, mx, my);
            return;
        }

        // 1 pick, 2 save, 3 share
        String[] steps = {"Pick styles", "Save look", "Share it"};
        int stepW = (pw - 8) / 3;
        for (int i = 0; i < 3; i++) {
            int sx = px + i * (stepW + 4);
            soft(ctx, sx, y, stepW, 18, c.base);
            soft(ctx, sx + 3, y + 4, 10, 10, c.accent);
            scaled(ctx, String.valueOf(i + 1), sx + 6, y + 6, c.onAccent, 0.8F, false);
            scaled(ctx, fitText(steps[i], Math.round((stepW - 18) / 0.75F)), sx + 16, y + 6, c.textSoft, 0.75F, false);
        }
        y += 24;

        if (presetNaming) {
            scaled(ctx, "Name your look:", px, y, c.textDim, 0.8F, false);
            y += 10;
            soft(ctx, px, y, pw, 17, c.surface2);
            soft(ctx, px + 2, y + 15, pw - 4, 1, c.accent);
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            text(ctx, fitText(presetDraft + (blink ? "_" : ""), pw - 10), px + 5, y + 5, c.text);
            y += 21;
            int half = (pw - 4) / 2;
            boolean saveHover = inside(mx, my, px, y, half, 16);
            button(ctx, px, y, half, 16, "SAVE", saveHover, ButtonStyle.PRIMARY);
            hit(px, y, half, 16, (b, x2, y2) -> savePreset());
            boolean cancelHover = inside(mx, my, px + half + 4, y, pw - half - 4, 16);
            button(ctx, px + half + 4, y, pw - half - 4, 16, "CANCEL", cancelHover, ButtonStyle.NORMAL);
            hit(px + half + 4, y, pw - half - 4, 16, (b, x2, y2) -> presetNaming = false);
            y += 22;
        } else {
            boolean hover = inside(mx, my, px, y, pw, 20);
            button(ctx, px, y, pw, 20, "+  SAVE MY LOOK", hover, ButtonStyle.PRIMARY);
            hit(px, y, pw, 20, (b, x2, y2) -> {
                presetNaming = true;
                presetDraft = StylesPresets.nextName();
                searchFocused = false;
                hexFocused = false;
            });
            y += 26;
        }
        String status = StylesPublicPresets.status();
        if (!status.isEmpty()) {
            y += statusLines(ctx, status, px, y, pw, false);
        }

        // the UNDO bar sits at the bottom once you used a look
        int listBottom = bottom;
        if (lastUsedLook != null && StylesUiConfig.hasBackup()) {
            int barY = bottom - 20;
            soft(ctx, px, barY, pw, 20, StylesTheme.withAlpha(c.good, 0x30));
            int undoW = textWidth("UNDO") + 12;
            scaled(ctx, fitText("Using \"" + lastUsedLook + "\"", Math.round((pw - undoW - 12) / 0.8F)), px + 5, barY + 7,
                    c.good, 0.8F, false);
            boolean undoHover = inside(mx, my, px + pw - undoW - 3, barY + 3, undoW, 14);
            button(ctx, px + pw - undoW - 3, barY + 3, undoW, 14, "UNDO", undoHover, ButtonStyle.NORMAL);
            hit(px + pw - undoW - 3, barY + 3, undoW, 14, (b, x2, y2) -> {
                Map<String, String> before = StylesUiConfig.extras();
                if (StylesUiConfig.restoreBackup()) {
                    refreshExtras(before, true);
                }
                lastUsedLook = null;
            });
            listBottom = barY - 6;
        }

        List<StylesPresets.Preset> presets = StylesPresets.all();
        if (presets.isEmpty()) {
            text(ctx, fitText("No looks saved yet", pw), px, y + 4, c.textSoft);
            scaled(ctx, fitText("Pick your styles, then SAVE MY LOOK", Math.round(pw / 0.8F)), px, y + 17, c.textDim, 0.8F, false);
            return;
        }
        int cardH = 56;
        int btnW = (pw - 12 - 12) / 4;
        ctx.enableScissor(px - 2, y, px + pw + 2, listBottom);
        for (StylesPresets.Preset preset : presets) {
            if (y > listBottom) {
                break;
            }
            boolean wearing = presetIsActive(preset);
            StylesPublicPresets.Shared shared = sharedCopy(preset);
            soft(ctx, px, y, pw, cardH, wearing ? c.surface2 : c.base);
            if (wearing) {
                softLine(ctx, px, y, pw, cardH, c.accent);
            }
            int nameW = pw - 12 - (shared != null ? 40 : 0) - (wearing ? 50 : 0);
            String name = fitText(preset.name(), nameW);
            text(ctx, name, px + 6, y + 5, c.text);
            if (wearing) {
                scaled(ctx, "WEARING", px + 10 + textWidth(name), y + 6, c.good, 0.75F, false);
            }
            if (shared != null) {
                int bw = scaledWidth("PUBLIC", 0.75F) + 8;
                soft(ctx, px + pw - 6 - bw, y + 4, bw, 10, c.accentDark);
                scaled(ctx, "PUBLIC", px + pw - 2 - bw, y + 6, c.accentBright, 0.75F, false);
            }
            drawPresetThumbs(ctx, preset.styles(), px + 6, y + 17, px + pw - 6);
            int by = y + cardH - 18;
            final StylesPresets.Preset target = preset;
            // USE
            int ux = px + 6;
            boolean useHover = inside(mx, my, ux, by, btnW, 14) && my < listBottom;
            button(ctx, ux, by, btnW, 14, "USE", useHover, ButtonStyle.PRIMARY);
            hits.add(new Hit(ux, by, btnW, 14, top, listBottom, (b, x2, y2) -> askPreset(target)));
            // SHARE / SHARED (click again to make it private)
            int sx = ux + btnW + 4;
            boolean shareHover = inside(mx, my, sx, by, btnW, 14) && my < listBottom;
            String shareLabel = target.locked() ? "LOCKED" : shared == null ? "SHARE" : shareHover ? "UNSHARE" : "SHARED";
            button(ctx, sx, by, btnW, 14, shareLabel, shareHover, ButtonStyle.NORMAL);
            hits.add(new Hit(sx, by, btnW, 14, top, listBottom, (b, x2, y2) -> {
                if (target.locked()) {
                    StylesPublicPresets.say("A preset from a key can't be shared on");
                } else if (shared == null) {
                    confirmShare(target, false);
                } else {
                    StylesPublicPresets.remove(shared);
                }
            }));
            // EXPORT: the look as one texture pack in resourcepacks/ (VIP only)
            int ex = sx + btnW + 4;
            boolean exportHover = inside(mx, my, ex, by, btnW, 14) && my < listBottom;
            boolean vip = VipPacks.shown();
            button(ctx, ex, by, btnW, 14, PresetExport.busy ? "..." : "EXPORT", exportHover,
                    vip ? ButtonStyle.NORMAL : ButtonStyle.DISABLED);
            if (exportHover) {
                tooltip = vip ? "Saves this look as a texture pack in your resourcepacks folder"
                        : "VIP only: saves this look as a texture pack";
                tooltipX = mx + 8;
                tooltipY = my + 10;
            }
            hits.add(new Hit(ex, by, btnW, 14, top, listBottom, (b, x2, y2) -> exportPreset(target)));
            // DELETE
            int dx = ex + btnW + 4;
            boolean delHover = inside(mx, my, dx, by, btnW, 14) && my < listBottom;
            button(ctx, dx, by, btnW, 14, "DELETE", delHover, ButtonStyle.NORMAL);
            hits.add(new Hit(dx, by, btnW, 14, top, listBottom, (b, x2, y2) -> StylesPresets.remove(target)));
            y += cardH + 5;
        }
        ctx.disableScissor();
    }

    /** The preset status; an export message has two lines (where it went, how to turn it on). Returns its height. */
    private int statusLines(DrawContext ctx, String status, int x, int y, int w, boolean right) {
        String[] lines = status.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = fitText(lines[i], w);
            int color = i == 0 ? c.good : c.textSoft;
            if (right) {
                textRight(ctx, line, x + w, y + i * 11, color);
            } else {
                text(ctx, line, x, y + i * 11, color);
            }
        }
        return lines.length * 11 + 1;
    }

    private static void exportPreset(StylesPresets.Preset preset) {
        if (PresetExport.busy) {
            return;
        }
        if (!VipPacks.shown()) {
            StylesPublicPresets.say("Export is for VIPs - get VIP in bauloo's Discord");
            return;
        }
        StylesPublicPresets.say("Exporting \"" + preset.name() + "\" ...");
        PresetExport.export(preset).thenAccept(msg -> {
            StylesPublicPresets.say(msg);
            java.nio.file.Path folder = PresetExport.lastFolder;
            if (folder != null) {
                // show where it went: the resourcepacks folder opens with the new pack in it
                MinecraftClient.getInstance().execute(() -> net.minecraft.util.Util.getOperatingSystem().open(folder));
            }
        });
    }

    /** Your shared copy of a look, if you shared one with the same name. */
    private static StylesPublicPresets.Shared sharedCopy(StylesPresets.Preset preset) {
        for (StylesPublicPresets.Shared shared : StylesPublicPresets.all()) {
            if (shared.mine() && shared.name().equals(preset.name())) {
                return shared;
            }
        }
        return null;
    }

    private double publicScroll;
    private int publicMaxScroll;
    private int publicTop, publicBottom, publicLeft, publicRight;

    /** Big cards of the presets everyone shared: name, who made it, a picture per collection. */
    /** a labelled button on a preset card */
    private record CardButton(String label, ButtonStyle style, Runnable action) {
    }

    /**
     * The Presets page, full size: MY LOOKS (save, use, share, delete) and PUBLIC (looks other
     * players shared). Big cards with a picture per collection, buttons with words on them.
     */
    private void drawPublicView(DrawContext ctx, Layout l, int mx, int my) {
        int left = l.mainX;
        int right = l.side ? l.sideX + l.sideW : l.mainX + l.mainW;
        int y = l.bodyY + 8;
        if (!publicAsked) {
            publicAsked = true;
            StylesPublicPresets.refresh();
        }

        // title and the two tabs
        text(ctx, "Presets", left, y + 3, c.text);
        int tx = left + textWidth("Presets") + 18;
        String[] labels = {"MY LOOKS", "PUBLIC"};
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == presetsPublic;
            int lw = textWidth(labels[i]);
            boolean hover = inside(mx, my, tx - 3, y, lw + 6, 16);
            text(ctx, labels[i], tx, y + 3, on ? c.accent : (hover ? c.text : c.textDim));
            if (on) {
                soft(ctx, tx, y + 14, lw, 2, c.accent);
            }
            final boolean toPublic = i == 1;
            hit(tx - 3, y, lw + 6, 16, (b, x2, y2) -> {
                presetsPublic = toPublic;
                presetNaming = false;
                publicScroll = 0;
                StylesPublicPresets.refresh();
            });
            tx += lw + 16;
        }

        // top right: save your look (or name it)
        if (!presetsPublic) {
            int saveW = 150;
            int sx = right - saveW;
            if (keyEntering) {
                int fieldW = saveW + 60;
                int fx = right - fieldW - 2 * 58;
                soft(ctx, fx, y - 1, fieldW, 17, c.surface2);
                soft(ctx, fx + 2, y + 14, fieldW - 4, 1, c.accent);
                boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
                String shownKey = keyDraft.isEmpty() && !blink ? "Paste the key (Ctrl+V)" : keyDraft + (blink ? "_" : "");
                text(ctx, fitText(shownKey, fieldW - 10), fx + 5, y + 4, keyDraft.isEmpty() ? c.textDim : c.text);
                boolean addHover = inside(mx, my, right - 112, y - 1, 54, 17);
                button(ctx, right - 112, y - 1, 54, 17, "ADD", addHover, ButtonStyle.PRIMARY);
                hit(right - 112, y - 1, 54, 17, (b, x2, y2) -> redeemKey());
                boolean cancelHover = inside(mx, my, right - 54, y - 1, 54, 17);
                button(ctx, right - 54, y - 1, 54, 17, "CANCEL", cancelHover, ButtonStyle.NORMAL);
                hit(right - 54, y - 1, 54, 17, (b, x2, y2) -> keyEntering = false);
            } else if (presetNaming) {
                int fieldW = saveW + 60;
                int fx = right - fieldW - 2 * 58;
                soft(ctx, fx, y - 1, fieldW, 17, c.surface2);
                soft(ctx, fx + 2, y + 14, fieldW - 4, 1, c.accent);
                boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
                text(ctx, fitText(presetDraft + (blink ? "_" : ""), fieldW - 10), fx + 5, y + 4, c.text);
                boolean saveHover = inside(mx, my, right - 112, y - 1, 54, 17);
                button(ctx, right - 112, y - 1, 54, 17, "SAVE", saveHover, ButtonStyle.PRIMARY);
                hit(right - 112, y - 1, 54, 17, (b, x2, y2) -> savePreset());
                boolean cancelHover = inside(mx, my, right - 54, y - 1, 54, 17);
                button(ctx, right - 54, y - 1, 54, 17, "CANCEL", cancelHover, ButtonStyle.NORMAL);
                hit(right - 54, y - 1, 54, 17, (b, x2, y2) -> presetNaming = false);
            } else {
                boolean hover = inside(mx, my, sx, y - 2, saveW, 19);
                button(ctx, sx, y - 2, saveW, 19, "+  SAVE MY LOOK", hover, ButtonStyle.PRIMARY);
                hit(sx, y - 2, saveW, 19, (b, x2, y2) -> {
                    presetNaming = true;
                    presetDraft = StylesPresets.nextName();
                    searchFocused = false;
                    hexFocused = false;
                });
                // a friend's one-time code goes in here
                int kw = textWidth("PRESET KEY") + 20;
                int kx = sx - 6 - kw;
                boolean keyHover = inside(mx, my, kx, y - 2, kw, 19);
                button(ctx, kx, y - 2, kw, 19, "PRESET KEY", keyHover, ButtonStyle.NORMAL);
                hit(kx, y - 2, kw, 19, (b, x2, y2) -> {
                    keyEntering = true;
                    keyDraft = "";
                    searchFocused = false;
                    hexFocused = false;
                });
                if (keyHover) {
                    tooltip = "Add a preset a friend sent you as a key";
                    tooltipX = mx + 8;
                    tooltipY = my + 10;
                }
            }
        }
        y += 26;

        // how it works, in three steps (or what PUBLIC is)
        if (!presetsPublic) {
            String[] steps = {"Pick your styles", "SAVE MY LOOK", "SHARE it with everyone"};
            int sx = left;
            for (int i = 0; i < 3; i++) {
                int sw = textWidth(steps[i]) + 28;
                soft(ctx, sx, y, sw, 18, c.surface);
                soft(ctx, sx + 4, y + 4, 10, 10, c.accent);
                scaled(ctx, String.valueOf(i + 1), sx + 7, y + 6, c.onAccent, 0.8F, false);
                text(ctx, steps[i], sx + 19, y + 5, c.textSoft);
                sx += sw + 6;
            }
        } else {
            text(ctx, "Looks other players shared. Press USE to try one - your Extras are saved first.", left, y + 5, c.textDim);
        }
        String status = StylesPublicPresets.status();
        if (!status.isEmpty()) {
            int half = (right - left) / 2;
            statusLines(ctx, status, right - half, y + 2, half, true);
        }
        y += 26;

        // UNDO bar at the bottom after using a look
        int listBottom = l.footerY - 10;
        if (lastUsedLook != null && StylesUiConfig.hasBackup()) {
            int barY = listBottom - 20;
            soft(ctx, left, barY, right - left, 20, StylesTheme.withAlpha(c.good, 0x30));
            text(ctx, fitText("Using \"" + lastUsedLook + "\" - your own Extras are saved", right - left - 80),
                    left + 8, barY + 6, c.good);
            int undoW = 60;
            boolean undoHover = inside(mx, my, right - undoW - 4, barY + 3, undoW, 14);
            button(ctx, right - undoW - 4, barY + 3, undoW, 14, "UNDO", undoHover, ButtonStyle.NORMAL);
            hit(right - undoW - 4, barY + 3, undoW, 14, (b, x2, y2) -> {
                Map<String, String> before = StylesUiConfig.extras();
                if (StylesUiConfig.restoreBackup()) {
                    refreshExtras(before, true);
                }
                lastUsedLook = null;
            });
            listBottom = barY - 8;
        }

        // the cards
        List<String> names = new ArrayList<>();
        List<String> subs = new ArrayList<>();
        List<Map<Tab, String>> looks = new ArrayList<>();
        List<Boolean> mine = new ArrayList<>();
        List<List<CardButton>> buttons = new ArrayList<>();
        if (presetsPublic) {
            for (StylesPublicPresets.Shared shared : StylesPublicPresets.all()) {
                names.add(shared.name());
                subs.add("by " + shared.author());
                looks.add(shared.styles());
                mine.add(shared.mine());
                List<CardButton> row = new ArrayList<>();
                row.add(new CardButton("USE", ButtonStyle.PRIMARY, () -> useShared(shared)));
                if (shared.mine() || StylesPublicPresets.admin()) {
                    row.add(new CardButton("REMOVE", ButtonStyle.NORMAL, () -> StylesPublicPresets.remove(shared)));
                }
                buttons.add(row);
            }
        } else {
            for (StylesPresets.Preset preset : StylesPresets.all()) {
                StylesPublicPresets.Shared shared = sharedCopy(preset);
                boolean wearing = presetIsActive(preset);
                names.add(preset.name());
                String from = preset.author() != null ? "by " + preset.author() : null;
                subs.add(wearing ? "WEARING" + (shared != null ? "  -  PUBLIC" : from != null ? "  -  " + from : "")
                        : shared != null ? "PUBLIC" : from != null ? from : "Only on this PC");
                looks.add(preset.styles());
                mine.add(wearing);
                List<CardButton> row = new ArrayList<>();
                row.add(new CardButton("USE", ButtonStyle.PRIMARY, () -> askPreset(preset)));
                if (!preset.locked()) {
                    // a preset from a friend's key can be used, not passed on
                    row.add(shared == null
                            ? new CardButton("SHARE", ButtonStyle.NORMAL, () -> confirmShare(preset, false))
                            : new CardButton("UNSHARE", ButtonStyle.NORMAL, () -> StylesPublicPresets.remove(shared)));
                    row.add(new CardButton("KEY", ButtonStyle.NORMAL, () -> confirmShare(preset, true)));
                }
                row.add(new CardButton(PresetExport.busy ? "..." : "EXPORT",
                        VipPacks.shown() ? ButtonStyle.NORMAL : ButtonStyle.DISABLED, () -> exportPreset(preset)));
                row.add(new CardButton("DELETE", ButtonStyle.NORMAL, () -> StylesPresets.remove(preset)));
                buttons.add(row);
            }
        }

        publicTop = y;
        publicBottom = listBottom;
        publicLeft = left;
        publicRight = right;
        if (names.isEmpty()) {
            if (presetsPublic) {
                text(ctx, StylesPublicPresets.loading() ? "Loading..." : "Nothing shared yet", left, y + 6, c.textSoft);
            } else {
                text(ctx, "No looks saved yet", left, y + 6, c.textSoft);
                text(ctx, "Pick your styles on the left, then press SAVE MY LOOK", left, y + 20, c.textDim);
            }
            return;
        }
        int gap = 8;
        int cardW = 190;
        int cols = Math.max(1, (right - left + gap) / (cardW + gap));
        cardW = (right - left - gap * (cols - 1)) / cols;
        int thumb = 22;
        int cardH = 30 + 2 * (thumb + 4) + 24;
        int rows = (names.size() + cols - 1) / cols;
        publicMaxScroll = Math.max(0, rows * (cardH + gap) - (publicBottom - publicTop));
        publicScroll = Math.max(0, Math.min(publicScroll, publicMaxScroll));

        ctx.enableScissor(left, publicTop, right, publicBottom);
        hitClipTop = publicTop;
        hitClipBottom = publicBottom;
        for (int i = 0; i < names.size(); i++) {
            int cx = left + (i % cols) * (cardW + gap);
            int cy = publicTop - (int) publicScroll + (i / cols) * (cardH + gap);
            if (cy > publicBottom || cy + cardH < publicTop) {
                continue;
            }
            boolean highlight = mine.get(i);
            soft(ctx, cx, cy, cardW, cardH, highlight ? c.surface2 : c.surface);
            if (highlight) {
                softLine(ctx, cx, cy, cardW, cardH, c.accent);
            }
            text(ctx, fitText(names.get(i), cardW - 12), cx + 6, cy + 5, c.text);
            String sub = subs.get(i);
            int subColor = sub.startsWith("WEARING") ? c.good : sub.startsWith("PUBLIC") || sub.startsWith("by") && highlight
                    ? c.accentBright : c.textDim;
            scaled(ctx, fitText(sub, Math.round((cardW - 12) / 0.8F)), cx + 6, cy + 16, subColor, 0.8F, false);
            // one picture per collection, two rows
            Tab[] tabs = shownTabs();
            int perRow = (tabs.length + 1) / 2;
            for (int t = 0; t < tabs.length; t++) {
                int ttx = cx + 6 + (t % perRow) * (thumb + 4);
                int tty = cy + 30 + (t / perRow) * (thumb + 4);
                if (ttx + thumb > cx + cardW - 4) {
                    continue;
                }
                String folder = looks.get(i).get(tabs[t]);
                if (folder == null) {
                    soft(ctx, ttx, tty, thumb, thumb, c.base);
                } else {
                    drawPreview(ctx, folder, ttx, tty, thumb, thumb);
                    cutCorners(ctx, ttx, tty, thumb, thumb, highlight ? c.surface2 : c.surface);
                }
                if (inside(mx, my, ttx, tty, thumb, thumb) && my >= publicTop && my < publicBottom) {
                    tooltip = tabs[t].title + ": " + (folder == null ? "Vanilla" : folder);
                    tooltipX = mx + 8;
                    tooltipY = my + 8;
                }
            }
            List<CardButton> row = buttons.get(i);
            int by = cy + cardH - 20;
            int bw = (cardW - 12 - (row.size() - 1) * 4) / row.size();
            for (int k = 0; k < row.size(); k++) {
                CardButton cb = row.get(k);
                int bx = cx + 6 + k * (bw + 4);
                boolean hover = inside(mx, my, bx, by, bw, 15) && my >= publicTop && my < publicBottom;
                button(ctx, bx, by, bw, 15, cb.label(), hover, cb.style());
                hit(bx, by, bw, 15, (b, x2, y2) -> cb.action().run());
            }
        }
        hitClipTop = Integer.MIN_VALUE;
        hitClipBottom = Integer.MAX_VALUE;
        ctx.disableScissor();
    }

    /** Little pictures of every collection in a preset, empty square = vanilla. */
    private void drawPresetThumbs(DrawContext ctx, Map<Tab, String> styles, int x, int y, int maxX) {
        for (Tab value : Tab.values()) {
            if (x + 12 > maxX) {
                break;
            }
            String folder = styles.get(value);
            if (folder == null) {
                soft(ctx, x, y, 12, 12, c.surface);
            } else {
                drawPreview(ctx, folder, x, y, 12, 12);
            }
            x += 14;
        }
    }

    /** Presets shared by everyone, with the Minecraft name of whoever shared them. */
    private void drawPublicPresets(DrawContext ctx, int px, int top, int pw, int bottom, int mx, int my) {
        int y = top;
        scaled(ctx, fitText("Looks other players shared. Press USE to try one.", Math.round(pw / 0.8F)), px, y,
                c.textDim, 0.8F, false);
        y += 12;
        String status = StylesPublicPresets.status();
        if (!status.isEmpty()) {
            y += statusLines(ctx, status, px, y, pw, false);
        }
        y += 4;
        List<StylesPublicPresets.Shared> shared = StylesPublicPresets.all();
        if (shared.isEmpty()) {
            text(ctx, StylesPublicPresets.loading() ? "Loading..." : "Nothing shared yet", px, y, c.textSoft);
            scaled(ctx, fitText("Share yours with SHARE in MY LOOKS", Math.round(pw / 0.8F)), px, y + 13, c.textDim, 0.8F, false);
            return;
        }
        int cardH = 56;
        ctx.enableScissor(px - 2, y, px + pw + 2, bottom);
        for (StylesPublicPresets.Shared preset : shared) {
            if (y > bottom) {
                break;
            }
            soft(ctx, px, y, pw, cardH, c.base);
            text(ctx, fitText(preset.name(), pw - 12), px + 6, y + 5, c.text);
            scaled(ctx, fitText("by " + preset.author(), Math.round((pw - 12) / 0.8F)), px + 6, y + 16,
                    preset.mine() ? c.accent : c.textSoft, 0.8F, false);
            drawPresetThumbs(ctx, preset.styles(), px + 6, y + 27, px + pw - 60);
            final StylesPublicPresets.Shared target = preset;
            int useW = 44;
            int ux = px + pw - useW - 6;
            int uy = y + cardH - 20;
            boolean useHover = inside(mx, my, ux, uy, useW, 14) && my < bottom;
            button(ctx, ux, uy, useW, 14, "USE", useHover, ButtonStyle.PRIMARY);
            hits.add(new Hit(ux, uy, useW, 14, top, bottom,
                    (b, x2, y2) -> useShared(target)));
            if (preset.mine()) {
                int rw = textWidth("REMOVE") + 10;
                int rx = ux - rw - 4;
                boolean remHover = inside(mx, my, rx, uy, rw, 14) && my < bottom;
                button(ctx, rx, uy, rw, 14, "REMOVE", remHover, ButtonStyle.NORMAL);
                hits.add(new Hit(rx, uy, rw, 14, top, bottom, (b, x2, y2) -> StylesPublicPresets.remove(target)));
            }
            y += cardH + 5;
        }
        ctx.disableScissor();
    }

    /**
     * SHARE or KEY on one of your presets. With styles from this PC in it, the first click only
     * warns (they get uploaded), a second click within 8 seconds goes ahead.
     */
    private void confirmShare(StylesPresets.Preset preset, boolean code) {
        int locals = PresetShare.localCount(preset);
        String confirm = preset.name() + (code ? "#key" : "#share");
        long now = System.currentTimeMillis();
        if (locals > 0 && !(confirm.equals(shareConfirmKey) && now < shareConfirmUntil)) {
            shareConfirmKey = confirm;
            shareConfirmUntil = now + 8000;
            StylesPublicPresets.say(locals + (locals == 1 ? " style" : " styles")
                    + " from your PC get uploaded with it - click again");
            return;
        }
        shareConfirmKey = null;
        if (!code) {
            StylesPublicPresets.share(preset);
            return;
        }
        StylesPublicPresets.say("Making a key...");
        Thread thread = new Thread(() -> {
            try {
                String key = PresetShare.makeCode(preset, StylesPublicPresets::say);
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient.getInstance().keyboard.setClipboard(key);
                    StylesPublicPresets.say("Key " + key + " copied - works once, for 7 days");
                });
            } catch (Exception e) {
                StylesPublicPresets.say(e.getMessage() == null ? "Could not reach the server" : e.getMessage());
            }
        }, "Bauloo Styles preset key");
        thread.setDaemon(true);
        thread.start();
    }

    /** ADD in the key field: the preset lands in MY LOOKS, the key is used up. */
    private void redeemKey() {
        String key = keyDraft.trim();
        keyEntering = false;
        if (key.isEmpty()) {
            return;
        }
        StylesPublicPresets.say("Checking the key...");
        Thread thread = new Thread(() -> {
            try {
                String name = PresetShare.redeem(key, StylesPublicPresets::say);
                StylesPublicPresets.say("Added \"" + name + "\" to your looks");
            } catch (Exception e) {
                StylesPublicPresets.say(e.getMessage() == null ? "Could not reach the server" : e.getMessage());
            }
        }, "Bauloo Styles preset key");
        thread.setDaemon(true);
        thread.start();
    }

    /** USE on a public preset: styles from the sharer's PC are downloaded first. */
    private void useShared(StylesPublicPresets.Shared shared) {
        if (shared.local().isEmpty()) {
            askPreset(new StylesPresets.Preset(shared.name(), shared.styles(), shared.extras()));
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Map<Tab, String> styles = new java.util.EnumMap<>(Tab.class);
                styles.putAll(shared.styles());
                styles.putAll(PresetShare.install(shared.local(), StylesPublicPresets::say));
                StylesPublicPresets.say("");
                MinecraftClient.getInstance().execute(() -> {
                    if (MinecraftClient.getInstance().currentScreen == this) {
                        askPreset(new StylesPresets.Preset(shared.name(), styles, shared.extras()));
                    }
                });
            } catch (Exception e) {
                StylesPublicPresets.say(e.getMessage() == null ? "Could not reach the server" : e.getMessage());
            }
        }, "Bauloo Styles shared styles");
        thread.setDaemon(true);
        thread.start();
    }

    /** Switches every collection to the preset's style; cloud styles download first. */
    /** Presets with Extras ask first (or follow the remembered choice). */
    private void askPreset(StylesPresets.Preset preset) {
        extrasStatus = "";
        // a look is used as a whole; your own Extras are always saved first, UNDO brings them back
        StylesUiConfig.presetBackup = true;
        if (preset.extras().isEmpty()) {
            lastUsedLook = null;
            applyPreset(preset, false);
        } else {
            // the look has Extras too: ask once, with two plain choices
            pendingPreset = preset;
            pendingRemember = false;
        }
    }

    private void choosePreset(boolean withExtras) {
        StylesPresets.Preset preset = pendingPreset;
        pendingPreset = null;
        lastUsedLook = withExtras ? preset.name() : null;
        if (pendingRemember) {
            StylesUiConfig.presetExtras = withExtras ? "with" : "without";
            StylesUiConfig.save();
        }
        applyPreset(preset, withExtras);
    }

    private void drawExtrasAsk(DrawContext ctx, int mx, int my) {
        hits.clear();
        tooltip = null;
        ctx.fill(0, 0, width, height, 0xB0000000);
        int w = Math.min(300, width - 20);
        int h = 104;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        soft(ctx, x, y, w, h, c.surface);
        softLine(ctx, x, y, w, h, c.accent);
        text(ctx, fitText("Use \"" + pendingPreset.name() + "\"", w - 24), x + 12, y + 10, c.text);
        text(ctx, fitText("Copy its Extras too? (colors, outline, glint, ...)", w - 24), x + 12, y + 28, c.textSoft);
        scaled(ctx, fitText("Your own Extras are saved first - UNDO brings them back.", Math.round((w - 24) / 0.8F)),
                x + 12, y + 42, c.good, 0.8F, false);
        int bw = (w - 24 - 8) / 3;
        int by = y + 68;
        String[] labels = {"WITH EXTRAS", "STYLES ONLY", "CANCEL"};
        for (int i = 0; i < 3; i++) {
            int bx = x + 12 + i * (bw + 4);
            boolean hover = inside(mx, my, bx, by, bw, 20);
            button(ctx, bx, by, bw, 20, labels[i], hover, i == 0 ? ButtonStyle.PRIMARY : ButtonStyle.NORMAL);
            final int choice = i;
            hit(bx, by, bw, 20, (b, x2, y2) -> {
                if (choice == 2) {
                    pendingPreset = null;
                } else {
                    choosePreset(choice == 0);
                }
            });
        }
    }

    /** A newer version is out: download it from the website, or ask again next version. */
    private void drawUpdateAsk(DrawContext ctx, int mx, int my) {
        hits.clear();
        tooltip = null;
        ctx.fill(0, 0, width, height, 0xB0000000);
        int w = Math.min(300, width - 20);
        java.util.List<String[]> notes = StylesUpdater.availableNotes;
        int notesH = notes.isEmpty() ? 0 : 20 + notes.size() * 13 + 8;
        int h = 112 + notesH;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        soft(ctx, x, y, w, h, c.surface);
        softLine(ctx, x, y, w, h, c.accent);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, TORNADO, x + 12, y + 10, 0, 0, 20, 20, 240, 240, 240, 240, c.accent);
        text(ctx, fitText("New version of Bauloo Styles", w - 50), x + 38, y + 11, c.text);
        String have = (StylesUpdater.installedVersion == null ? "?" : StylesUpdater.installedVersion) + "  ->  ";
        text(ctx, have, x + 38, y + 23, c.textDim);
        text(ctx, updateAsk, x + 38 + textWidth(have), y + 23, c.accent);
        text(ctx, fitText("Get it from the website and put it into", w - 24), x + 12, y + 44, c.textSoft);
        text(ctx, fitText("your mods folder instead of the old one.", w - 24), x + 12, y + 56, c.textSoft);
        if (!notes.isEmpty()) {
            // what the new version brings, from version.json - so the popup always shows the newest notes
            soft(ctx, x + 10, y + 68, w - 20, notesH - 4, c.base);
            scaled(ctx, "WHAT'S NEW", x + 16, y + 73, c.accent, 1.3F, false);
            int ny = y + 92;
            for (String[] note : notes) {
                int chip = "FIX".equals(note[0]) ? c.gold : "NEW".equals(note[0]) ? c.accent : c.surface2;
                int cw = scaledWidth(note[0], 0.7F) + 8;
                soft(ctx, x + 14, ny + 1, cw, 9, chip);
                scaled(ctx, note[0], x + 18, ny + 2, "GONE".equals(note[0]) ? c.text : 0xFF0A0F14, 0.7F, false);
                scaled(ctx, fitText(note[1], Math.round((w - 40 - cw) / 0.85F)), x + 20 + cw, ny + 1.5F, c.text, 0.85F, false);
                ny += 13;
            }
        }
        int inner = w - 24;
        int small = (inner - 8) / 4;
        int by = y + 80 + notesH;
        int[] bw = {inner - 4 - small, small};
        String[] labels = {"DOWNLOAD", "LATER"};
        int[] choices = {1, 2};
        int bx = x + 12;
        for (int i = 0; i < labels.length; i++) {
            boolean hover = inside(mx, my, bx, by, bw[i], 20);
            button(ctx, bx, by, bw[i], 20, labels[i], hover, i == 0 ? ButtonStyle.PRIMARY : ButtonStyle.NORMAL);
            final int choice = choices[i];
            hit(bx, by, bw[i], 20, (b, x2, y2) -> answerUpdate(choice));
            bx += bw[i] + 4;
        }
    }

    /** Splits text into lines that fit a width (scaled text). */
    private java.util.List<String> wrap(String value, int width, float scale) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String line = "";
        for (String word : value.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (scaledWidth(next, scale) > width && !line.isEmpty()) {
                lines.add(line);
                line = word;
            } else {
                line = next;
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    /** "What's new" once after an update: version, one highlight with a moving cape, the change list. */
    private void drawWhatsNew(DrawContext ctx, int mx, int my) {
        hits.clear();
        tooltip = null;
        WhatsNew.Notes n = whatsNew;
        ctx.fill(0, 0, width, height, 0xB0000000);
        int w = Math.min(330, width - 20);
        boolean heroPic = !n.heroCape().isEmpty() && BuiltInCapes.icon(n.heroCape()) != null;
        int heroH = heroPic ? 70 : 24 + wrap(n.heroText(), w - 36, 0.8F).size() * 9;
        int rowH = 15;
        int h = 46 + heroH + 8 + 18 + n.items().size() * rowH + 34;
        int x = (width - w) / 2;
        int y = Math.max(6, (height - h) / 2);
        soft(ctx, x, y, w, h, c.surface);
        softLine(ctx, x, y, w, h, c.accent);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, TORNADO, x + 12, y + 11, 0, 0, 22, 22, 240, 240, 240, 240, c.accent);
        text(ctx, fitText("Bauloo Styles updated", w - 120), x + 42, y + 12, c.text);
        String from = n.from().isEmpty() ? "" : n.from() + "  ->  ";
        scaled(ctx, from, x + 42, y + 25, c.textDim, 0.8F, false);
        scaled(ctx, n.to(), x + 42 + scaledWidth(from, 0.8F), y + 25, c.accent, 0.8F, false);
        String badge = "INSTALLED";
        int bw = scaledWidth(badge, 0.7F) + 10;
        soft(ctx, x + w - 12 - bw, y + 13, bw, 11, c.good);
        scaled(ctx, badge, x + w - 7 - bw, y + 15, c.onGood, 0.7F, false);

        int hy = y + 42;
        soft(ctx, x + 10, hy, w - 20, heroH, c.base);
        Identifier cape = n.heroCape().isEmpty() ? null : BuiltInCapes.icon(n.heroCape());
        if (cape != null) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, cape, x + 16, hy + 6, 0, 0, 36, 58, 36, 58, 36, 58);
        }
        int tx = cape != null ? x + 60 : x + 18;
        int tw = x + w - 18 - tx;
        text(ctx, fitText(n.heroTitle(), tw), tx, hy + 7, c.text);
        int ly = hy + 20;
        for (String line : wrap(n.heroText(), tw, 0.8F)) {
            if (ly > hy + heroH - 9) {
                break;
            }
            scaled(ctx, line, tx, ly, c.textSoft, 0.8F, false);
            ly += 9;
        }

        scaled(ctx, "WHAT'S NEW", x + 14, hy + heroH + 8, c.accent, 1.3F, false);
        int ry = hy + heroH + 6 + 18;
        for (String[] item : n.items()) {
            ctx.fill(x + 10, ry - 2, x + w - 10, ry - 1, c.borderDim);
            int chipColor = switch (item[0]) {
                case "NEW" -> c.accent;
                case "FIX" -> c.gold;
                default -> c.surface2;
            };
            int chipText = "GONE".equals(item[0]) ? c.text : 0xFF0A0F14;
            int cw = scaledWidth(item[0], 0.7F) + 8;
            soft(ctx, x + 12, ry + 1, cw, 9, chipColor);
            scaled(ctx, item[0], x + 16, ry + 2, chipText, 0.7F, false);
            scaled(ctx, fitText(item[1], Math.round((w - 40 - cw) / 0.85F)), x + 18 + cw, ry + 1.5F, c.text, 0.85F, false);
            ry += rowH;
        }

        int by = y + h - 28;
        int half = (w - 24 - 4) / 2;
        boolean hoverOpen = inside(mx, my, x + 12, by, half, 20);
        boolean toExtras = "extras".equals(n.open());
        boolean toMyPacks = "mypacks".equals(n.open());
        // "open" can also name a collection, e.g. "gap" opens Gaps & Pearls
        Tab toTab = null;
        for (Tab t : shownTabs()) {
            if (t.prefix.equals(n.open())) {
                toTab = t;
            }
        }
        final Tab target = toTab;
        String openLabel = toMyPacks ? "OPEN MY PACKS"
                : target != null ? "OPEN " + target.title.toUpperCase(java.util.Locale.ROOT)
                : toExtras ? "OPEN EXTRAS" : "OPEN COSMETICS";
        button(ctx, x + 12, by, half, 20, fitText(openLabel, half - 8), hoverOpen, ButtonStyle.PRIMARY);
        hit(x + 12, by, half, 20, (b, x2, y2) -> {
            whatsNew = null;
            if (toMyPacks) {
                myPacksView = true;
                vipView = false;
                cosmeticsView = false;
                extrasView = false;
                publicView = false;
                gridScroll = 0;
                focusedIndex = 0;
                rebuild();
                return;
            }
            if (target != null) {
                cosmeticsView = false;
                switchTab(target);
                return;
            }
            cosmeticsView = !toExtras;
            extrasView = toExtras;
            publicView = false;
            searchFocused = false;
        });
        boolean hoverOk = inside(mx, my, x + 16 + half, by, half, 20);
        button(ctx, x + 16 + half, by, half, 20, "GOT IT", hoverOk, ButtonStyle.NORMAL);
        hit(x + 16 + half, by, half, 20, (b, x2, y2) -> whatsNew = null);
    }

    /** "Are you sure?" for the RESET button: two plain choices, cancel is the safe one. */
    private void drawResetAsk(DrawContext ctx, int mx, int my) {
        hits.clear();
        tooltip = null;
        ctx.fill(0, 0, width, height, 0xB0000000);
        int w = Math.min(300, width - 20);
        int h = 112;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        soft(ctx, x, y, w, h, c.surface);
        softLine(ctx, x, y, w, h, 0xFFFF5A6E);
        text(ctx, "Reset everything?", x + 12, y + 10, c.text);
        text(ctx, fitText("All styles go back to vanilla, every Extra", w - 24), x + 12, y + 28, c.textSoft);
        text(ctx, fitText("and cosmetic back to default.", w - 24), x + 12, y + 40, c.textSoft);
        scaled(ctx, fitText("Your saved looks, menu theme and hotkey stay.", Math.round((w - 24) / 0.8F)),
                x + 12, y + 55, c.good, 0.8F, false);
        scaled(ctx, fitText("UNDO brings your Extras back.", Math.round((w - 24) / 0.8F)),
                x + 12, y + 65, c.good, 0.8F, false);
        int bw = (w - 24 - 6) / 2;
        int by = y + 80;
        boolean hoverReset = inside(mx, my, x + 12, by, bw, 20);
        soft(ctx, x + 12, by, bw, 20, hoverReset ? 0xFFFF6E80 : 0xFFE0485C);
        textCenter(ctx, "RESET EVERYTHING", x + 12, bw, by + 6, 0xFFFFFFFF);
        hit(x + 12, by, bw, 20, (b, x2, y2) -> resetEverything());
        int cx = x + 12 + bw + 6;
        button(ctx, cx, by, bw, 20, "CANCEL", inside(mx, my, cx, by, bw, 20), ButtonStyle.NORMAL);
        hit(cx, by, bw, 20, (b, x2, y2) -> resetAsk = false);
    }

    /** Styles to vanilla, Extras and cosmetics to default; the Extras are backed up for UNDO first. */
    private void resetEverything() {
        resetAsk = false;
        StylesUiConfig.cosCape = "none";
        StylesUiConfig.cosHead = "none";
        StylesUiConfig.cosAura = "none";
        StylesUiConfig.cosColor = 0xB388FF;
        StylesUiConfig.cosRainbow = false;
        StylesUiConfig.cosCapeElytra = true;
        StylesUiConfig.presetBackup = true;
        StylesUiConfig.save();
        lastUsedLook = RESET_NAME;
        applyPreset(new StylesPresets.Preset(RESET_NAME, new java.util.EnumMap<>(Tab.class), java.util.Map.of()), true);
    }

    private void answerUpdate(int choice) {
        String version = updateAsk;
        updateAsk = null;
        StylesUiConfig.updateSeen = version;
        StylesUiConfig.save();
        if (choice == 1 && client != null) {
            net.minecraft.client.gui.screen.ConfirmLinkScreen.open(this, "https://baulooprac.xyz", true);
        }
    }

    private void checkbox(DrawContext ctx, int x, int y, String label, boolean on, int mx, int my, Runnable toggle) {
        int w = 14 + (int) (textWidth(label) * 0.8F);
        boolean hover = inside(mx, my, x - 2, y - 2, w + 4, 13);
        soft(ctx, x, y, 9, 9, on ? c.accent : c.surface2);
        if (!on) {
            softLine(ctx, x, y, 9, 9, hover ? c.accent : c.border);
        }
        scaled(ctx, label, x + 13, y + 1, hover ? c.text : c.textSoft, 0.8F, false);
        hit(x - 2, y - 2, w + 4, 13, (b, x2, y2) -> toggle.run());
    }

    /** Makes new Extras visible: hit colour, glint, and the generated packs if their settings changed. */
    private void refreshExtras(Map<String, String> before, boolean reloadPacks) {
        EnchantGlint.applyLive();
        FireColor.applyLive();
        ShieldColor.applyLive();
        Map<String, String> after = StylesUiConfig.extras();
        boolean packs = false;
        for (String key : List.of("anchorGlowCustom", "anchorGlowColor", "anchorShowFull", "coreCustom", "coreColor")) {
            packs |= !java.util.Objects.equals(before.get(key), after.get(key));
        }
        if (packs && reloadPacks) {
            GeneratedPacks.applyAll();
        }
    }

    /** Kurze Zeile im Chat, damit jeder weiss, wie er seine Extras zurueck bekommt. */
    private void tellExtras(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            String what = RESET_NAME.equals(name) ? "\u00a7bEverything is reset." : "\u00a7b" + name + " \u00a77is on.";
            mc.player.sendMessage(net.minecraft.text.Text.literal(what
                    + " \u00a7fUNDO \u00a77in MY LOOKS puts your Extras back."), false);
        }
    }

    private void applyPreset(StylesPresets.Preset preset, boolean withExtras) {
        List<java.util.concurrent.CompletableFuture<Boolean>> downloads = new ArrayList<>();
        for (String folder : preset.styles().values()) {
            StylesCloud.Style cloud = StylesCloud.byFolder(folder);
            if (cloud != null && !StylesCloud.isReady(cloud)) {
                downloads.add(StylesCloud.download(cloud));
            }
        }
        if (!downloads.isEmpty()) {
            java.util.concurrent.CompletableFuture.allOf(downloads.toArray(new java.util.concurrent.CompletableFuture[0]))
                    .whenComplete((ok, error) -> MinecraftClient.getInstance().send(() -> {
                        if (MinecraftClient.getInstance().currentScreen == this) {
                            applyPreset(preset, withExtras);
                        }
                    }));
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (withExtras) {
            // anchor charge and crystal core are written again after the reload below
            Map<String, String> before = StylesUiConfig.extras();
            StylesUiConfig.applyPresetExtras(preset.extras());
            refreshExtras(before, false);
            tellExtras(preset.name());
        }
        ResourcePackManager packs = mc.getResourcePackManager();
        packs.scanPacks();
        List<String> enabled = new ArrayList<>(packs.getEnabledIds());
        for (Tab value : Tab.values()) {
            if (value == Tab.SKY && !skyboxifyInstalled) {
                continue;
            }
            enabled.removeIf(id -> isPackOf(value, id));
            String folder = preset.styles().get(value);
            if (folder != null && packs.getIds().contains(packId(folder))) {
                enabled.add(packId(folder));
            }
        }
        packs.setEnabledProfiles(enabled);
        mc.options.refreshResourcePacks(packs);
        mc.reloadResources();

        BaulooStylesConfigScreen next = new BaulooStylesConfigScreen(parent);
        next.tab = tab;
        next.query = query;
        next.favoritesOnly = favoritesOnly;
        next.rebuild();
        mc.setScreen(next);
    }

    private void drawThemes(DrawContext ctx, int px, int top, int pw, int bottom, int mx, int my, int background) {
        themeLeft = px;
        themeRight = px + pw;
        themeTop = top;
        themeBottom = bottom;
        int viewH = bottom - top;
        int y0 = top - (int) themeScroll;
        int y = y0;
        int clipTop = top;
        int clipBottom = bottom;

        ctx.enableScissor(px - 2, top, px + pw + 2, bottom);

        text(ctx, "PRESET", px, y, c.textDim);
        y += 13;
        int presetW = (pw - 4) / 2;
        int presetH = 15;
        for (int i = 0; i < StylesTheme.PRESETS.length; i++) {
            StylesTheme.Preset preset = StylesTheme.PRESETS[i];
            int cx = px + (i % 2) * (presetW + 4);
            int cy = y + (i / 2) * (presetH + 3);
            boolean selected = (StylesUiConfig.accent & 0xFFFFFF) == preset.accent()
                    && StylesUiConfig.background == preset.background();
            boolean hover = inside(mx, my, cx, cy, presetW, presetH) && my >= clipTop && my < clipBottom;
            soft(ctx, cx, cy, presetW, presetH, selected ? c.surface2 : (hover ? c.surfaceHover : c.base));
            if (selected) {
                softLine(ctx, cx, cy, presetW, presetH, c.accentDark);
            }
            soft(ctx, cx + 4, cy + 4, 7, 7, 0xFF000000 | preset.accent());
            text(ctx, fitText(preset.name(), presetW - 18), cx + 15, cy + 4, selected ? c.text : c.textSoft);
            hits.add(new Hit(cx, cy, presetW, presetH, clipTop, clipBottom, (b, x2, y2) -> {
                StylesUiConfig.accent = preset.accent();
                StylesUiConfig.background = preset.background();
                hexFocused = false;
                StylesUiConfig.save();
            }));
        }
        y += (StylesTheme.PRESETS.length + 1) / 2 * (presetH + 3) + 8;

        text(ctx, "ACCENT", px, y, c.textDim);
        y += 13;
        int hueH = 6;
        for (int i = 0; i < pw; i++) {
            ctx.fill(px + i, y, px + i + 1, y + hueH, 0xFF000000 | StylesTheme.hsv(i / (float) Math.max(1, pw - 1), 0.65F, 1.0F));
        }
        cutCorners(ctx, px, y, pw, hueH, background);
        float hue = StylesTheme.toHsv(StylesUiConfig.accent)[0];
        int knobX = px + Math.round(hue * (pw - 1));
        soft(ctx, knobX - 2, y - 2, 4, hueH + 4, 0xFF000000);
        ctx.fill(knobX - 1, y - 1, knobX + 1, y + hueH + 1, 0xFFFFFFFF);
        final int hueTrackX = px;
        hits.add(new Hit(px, y - 4, pw, hueH + 8, clipTop, clipBottom, (b, x2, y2) -> {
            drag = Drag.HUE;
            dragX = hueTrackX;
            dragW = pw;
            updateDrag(x2);
        }));
        y += hueH + 7;

        boolean fieldHover = inside(mx, my, px, y, pw, 15) && my >= clipTop && my < clipBottom;
        soft(ctx, px, y, pw, 15, hexFocused || fieldHover ? c.surface2 : c.base);
        if (hexFocused) {
            soft(ctx, px + 2, y + 13, pw - 4, 1, c.accent);
        }
        soft(ctx, px + 4, y + 4, 7, 7, c.accent);
        boolean blink = hexFocused && (System.currentTimeMillis() / 500) % 2 == 0;
        String hex = hexFocused ? hexDraft + (blink ? "_" : "") : String.format("%06X", StylesUiConfig.accent & 0xFFFFFF);
        text(ctx, "#" + hex, px + 15, y + 4, hexFocused ? c.text : c.textSoft);
        hits.add(new Hit(px, y, pw, 15, clipTop, clipBottom, (b, x2, y2) -> {
            hexFocused = true;
            hexDraft = "";
        }));
        y += 23;

        text(ctx, "OPACITY", px, y, c.textDim);
        textRight(ctx, StylesUiConfig.opacity + "%", px + pw, y, c.textSoft);
        y += 14;
        float t = (StylesUiConfig.opacity - 60) / 40.0F;
        int fillX = px + Math.round(t * (pw - 1));
        soft(ctx, px, y + 1, pw, 4, c.base);
        soft(ctx, px, y + 1, Math.max(3, fillX - px), 4, c.accentDark);
        soft(ctx, fillX - 2, y - 2, 5, 10, c.accent);
        final int sliderX = px;
        hits.add(new Hit(px, y - 4, pw, 14, clipTop, clipBottom, (b, x2, y2) -> {
            drag = Drag.OPACITY;
            dragX = sliderX;
            dragW = pw;
            updateDrag(x2);
        }));
        y += 18;

        boolean resetHover = inside(mx, my, px, y, 50, 15) && my >= clipTop && my < clipBottom;
        button(ctx, px, y, 50, 15, "RESET", resetHover, ButtonStyle.NORMAL);
        hits.add(new Hit(px, y, 50, 15, clipTop, clipBottom, (b, x2, y2) -> {
            StylesUiConfig.resetTheme();
            hexFocused = false;
        }));
        y += 15;

        ctx.disableScissor();

        int contentH = y - y0;
        themeMaxScroll = Math.max(0, contentH - viewH);
        themeScroll = Math.max(0, Math.min(themeScroll, themeMaxScroll));
        if (themeMaxScroll > 0) {
            int trackX = px + pw + 4;
            soft(ctx, trackX, top, 2, viewH, c.borderDim);
            int thumbH = Math.max(16, viewH * viewH / contentH);
            int thumbY = top + (int) ((viewH - thumbH) * (themeScroll / themeMaxScroll));
            soft(ctx, trackX, thumbY, 2, thumbH, c.accent);
        }
    }

    private void drawFooter(DrawContext ctx, Layout l, int mx, int my) {
        int y = l.footerY;
        ctx.fill(l.fx + 10, y, l.fx + l.fw - 10, y + 1, c.borderDim);
        int by = y + 6;
        int bh = 16;

        int x = l.fx + 10;
        int uploadsW = textWidth("UPLOAD") + 16;
        button(ctx, x, by, uploadsW, bh, "UPLOAD", inside(mx, my, x, by, uploadsW, bh), ButtonStyle.NORMAL);
        hit(x, by, uploadsW, bh, (b, px, py) -> StylesUploadServer.open());
        x += uploadsW + 6;
        int presetsW = textWidth("PRESETS") + 16;
        button(ctx, x, by, presetsW, bh, "PRESETS", inside(mx, my, x, by, presetsW, bh),
                publicView ? ButtonStyle.PRIMARY : ButtonStyle.NORMAL);
        hit(x, by, presetsW, bh, (b, px, py) -> {
            publicView = !publicView;
            presetsView = false;
            presetNaming = false;
            compactThemes = false;
            hexFocused = false;
            if (publicView) {
                extrasView = false;
                cosmeticsView = false;
                StylesPublicPresets.refresh();
            }
        });
        x += presetsW + 6;
        if (!l.side) {
            String label = compactThemes ? "STYLES" : "THEMES";
            int tw = textWidth(label) + 16;
            button(ctx, x, by, tw, bh, label, inside(mx, my, x, by, tw, bh),
                    compactThemes ? ButtonStyle.PRIMARY : ButtonStyle.NORMAL);
            hit(x, by, tw, bh, (b, px, py) -> {
                compactThemes = !compactThemes;
                presetsView = false;
                hexFocused = false;
            });
            x += tw + 6;
        }
        int resetW = textWidth("RESET") + 16;
        button(ctx, x, by, resetW, bh, "RESET", inside(mx, my, x, by, resetW, bh), ButtonStyle.NORMAL);
        hit(x, by, resetW, bh, (b, px, py) -> resetAsk = true);
        x += resetW + 6;

        String key = bindingKey ? "PRESS A KEY" : "OPEN: " + StylesHotkey.label();
        hotkeyW = Math.max(70, textWidth(key) + 16);
        hotkeyX = l.fx + l.fw - 10 - hotkeyW;
        hotkeyY = by;
        button(ctx, hotkeyX, by, hotkeyW, bh, key, inside(mx, my, hotkeyX, by, hotkeyW, bh),
                bindingKey ? ButtonStyle.PRIMARY : ButtonStyle.NORMAL);
        hit(hotkeyX, by, hotkeyW, bh, (b, px, py) -> bindingKey = !bindingKey);

        // bauloo's Discord, always in view; a click asks first, then opens it in the browser
        String dc = "discord.gg/bauloo";
        int dcW = textWidth(dc) + 16;
        int dcX = hotkeyX - 6 - dcW;
        boolean dcShown = dcX > x + 4;
        if (dcShown) {
            boolean dcHover = inside(mx, my, dcX, by, dcW, bh);
            soft(ctx, dcX, by, dcW, bh, dcHover ? 0xFF6B77F5 : 0xFF5865F2);
            ctx.drawText(textRenderer, dc, dcX + 8, by + (bh - 8) / 2, 0xFFFFFFFF, true);
            hit(dcX, by, dcW, bh, (b, px, py) ->
                    net.minecraft.client.gui.screen.ConfirmLinkScreen.open(this, "https://discord.gg/bauloo", true));
        }

        // FEEDBACK right next to Discord, in star gold so it stands out next to the blurple
        int edge = dcShown ? dcX : hotkeyX;
        if (StylesReview.shown()) {
            String fb = StylesReview.busy() ? "★ FEEDBACK ..." : "★ FEEDBACK";
            int fbW = textWidth(fb) + 16;
            int fbX = edge - 6 - fbW;
            if (fbX > x + 4) {
                boolean fbHover = inside(mx, my, fbX, by, fbW, bh);
                soft(ctx, fbX, by, fbW, bh, fbHover ? 0xFFFFD27A : 0xFFF5B83D);
                ctx.drawText(textRenderer, fb, fbX + 8, by + (bh - 8) / 2, 0xFF1C1405, false);
                hit(fbX, by, fbW, bh, (b, px, py) -> StylesReview.open());
                edge = fbX;
                String note = StylesReview.status();
                if (note != null) {
                    int nw = scaledWidth(note, 0.8F);
                    if (fbX - 8 - nw > x + 4) {
                        scaled(ctx, note, fbX - 8 - nw, by + 5, c.textSoft, 0.8F, false);
                        edge = fbX - 8 - nw;
                    }
                }
            }
        }

        // early access switch in plain sight (only bauloo while he tests it): gold when on
        if (VipPacks.admin()) {
            boolean on = StylesUiConfig.earlyAccess;
            String ea = on ? "EARLY ACCESS ON" : "EARLY ACCESS OFF";
            int eaW = textWidth(ea) + 16;
            int eaX = edge - 6 - eaW;
            if (eaX > x + 4) {
                boolean eaHover = inside(mx, my, eaX, by, eaW, bh);
                if (on) {
                    soft(ctx, eaX, by, eaW, bh, eaHover ? 0xFF6A5520 : 0xFF4A3C16);
                    softLine(ctx, eaX, by, eaW, bh, 0xFFFFC23D);
                } else {
                    soft(ctx, eaX, by, eaW, bh, eaHover ? c.surfaceHover : c.surface);
                }
                ctx.drawText(textRenderer, ea, eaX + 8, by + (bh - 8) / 2, on ? 0xFFFFC23D : c.textDim, false);
                hit(eaX, by, eaW, bh, (b, px, py) -> {
                    StylesUiConfig.earlyAccess = !StylesUiConfig.earlyAccess;
                    StylesUiConfig.save();
                });
                if (eaHover) {
                    tooltip = on ? "Early styles show (VIP) - click to turn off" : "Click to see early styles (VIP)";
                    tooltipX = mx + 8;
                    tooltipY = my - 14;
                }
                edge = eaX;
            }
        }

        if (!PENDING.isEmpty()) {
            // batch apply: how many changes wait, CANCEL and APPLY
            int n = PENDING.size();
            String applyLabel = pendingCloud != null && pendingCloud.startsWith("\u0000") ? "DOWNLOADING ..."
                    : "APPLY " + n + (n == 1 ? " CHANGE" : " CHANGES");
            int aw = textWidth(applyLabel) + 16;
            int ax = edge - 6 - aw;
            boolean aHover = inside(mx, my, ax, by, aw, bh);
            soft(ctx, ax, by, aw, bh, aHover ? 0xFFFFD27A : PENDING_COLOR);
            ctx.drawText(textRenderer, applyLabel, ax + 8, by + (bh - 8) / 2, 0xFF1C1405, false);
            hit(ax, by, aw, bh, (b, px, py) -> applyPending(true));
            int cw = textWidth("CANCEL") + 16;
            int cxBtn = ax - 6 - cw;
            button(ctx, cxBtn, by, cw, bh, "CANCEL", inside(mx, my, cxBtn, by, cw, bh), ButtonStyle.NORMAL);
            hit(cxBtn, by, cw, bh, (b, px, py) -> PENDING.clear());
            String info = n + (n == 1 ? " change selected" : " changes selected");
            if (textWidth(info) < cxBtn - 10 - (x + 10)) {
                textRight(ctx, info, cxBtn - 10, by + 4, PENDING_COLOR);
            }
            return;
        }

        String[] hints = {StylesUiConfig.applyInstantly ? "LEFT-CLICK APPLY" : "LEFT-CLICK SELECT",
                "RIGHT-CLICK ZOOM", "F FAVORITE", "/ SEARCH"};
        int left = x + 10;
        int right = edge - 10;
        int shown = hints.length;
        String line = "";
        while (shown > 0) {
            line = String.join("  •  ", java.util.Arrays.copyOf(hints, shown));
            if (textWidth(line) <= right - left) {
                break;
            }
            shown--;
        }
        if (shown > 0) {
            textCenter(ctx, line, left, right - left, by + 4, c.textDim);
        }
    }

    private void drawZoom(DrawContext ctx) {
        ctx.fill(0, 0, width, height, StylesTheme.withAlpha(c.base, 0xEE));
        int size = (int) (Math.min(width, height - 30) * 0.80F);
        int x = (width - size) / 2;
        int y = Math.max(8, (height - size) / 2 - 12);
        drawPreview(ctx, zoomFolder, x, y, size, size);
        cutCorners(ctx, x, y, size, size, StylesTheme.withAlpha(c.base, 0xEE));
        softLine(ctx, x - 1, y - 1, size + 2, size + 2, c.accent);
        textCenter(ctx, fitText(zoomName, width - 16), 0, width, y + size + 8, c.text);
        textCenter(ctx, "Click anywhere to close", 0, width, y + size + 20, c.textDim);
    }

    // ------------------------------------------------------------------ input

    private void toggleFavorite(String folder) {
        StylesUiConfig.toggleFavorite(folder);
        if (favoritesOnly) {
            applyFilter();
        }
    }

    private void updateDrag(double mouseX) {
        if (drag == Drag.COS_TURN) {
            // a full width of dragging turns you once around; right turns you to the right
            cosYaw = ((cosDragYaw - (float) (mouseX - dragStartMouse) / Math.max(1, dragW) * 360) % 360 + 360) % 360;
            return;
        }
        float t = (float) Math.max(0, Math.min(1, (mouseX - dragX) / Math.max(1, dragW - 1)));
        if (liveDrag()) {
            t = (float) Math.max(0, Math.min(1, dragStartT + (mouseX - dragStartMouse) / Math.max(1, dragW - 1)));
        }
        if (drag == Drag.HUE) {
            float[] hsv = StylesTheme.toHsv(StylesUiConfig.accent);
            float s = hsv[1] < 0.35F ? 0.65F : hsv[1];
            float v = hsv[2] < 0.75F ? 1.0F : hsv[2];
            StylesUiConfig.accent = StylesTheme.hsv(t, s, v);
        } else if (drag == Drag.OPACITY) {
            StylesUiConfig.opacity = Math.round(60 + t * 40);
        } else if (drag == Drag.GRADIENT) {
            StylesUiConfig.gradientStrength = Math.round((50 + t * 350) / 10.0F) * 10;
        } else if (drag == Drag.KILL_VOLUME) {
            StylesUiConfig.killVolume = Math.round((5 + t * 95) / 5.0F) * 5;
        } else if (drag == Drag.TOTEM_POP) {
            StylesUiConfig.totemPopSize = Math.round((20 + t * 130) / 5.0F) * 5;
        } else if (drag == Drag.CRYSTAL_SPIN) {
            StylesUiConfig.crystalSpin = Math.round(t * 1000 / 10.0F) * 10;
        } else if (drag == Drag.SHIELD_SIZE) {
            StylesUiConfig.shieldSize = Math.round((20 + t * 130) / 5.0F) * 5;
        } else if (drag == Drag.TIME) {
            StylesUiConfig.timeOverride = Math.round(t * 23999);
        } else if (drag == Drag.COBWEB_WIDTH) {
            StylesUiConfig.cobwebOutlineWidth = 1 + Math.round(t * 15);
        } else if (drag == Drag.GAP_SIZE) {
            StylesUiConfig.gapSize = Math.round((20 + t * 130) / 5.0F) * 5;
        } else if (drag == Drag.TOTEM) {
            StylesUiConfig.totemSize = Math.round((20 + t * 130) / 5.0F) * 5;
        } else if (drag == Drag.SHIELD) {
            StylesUiConfig.shieldOpacity = Math.round(15 + t * 85);
        } else if (drag == Drag.PICK_HUE) {
            huePreview = true;
            setPick(huePick, true, PlayerOutline.sliderColor(t));
        } else if (drag == Drag.OUTLINE_HUE) {
            StylesUiConfig.outlineColor = PlayerOutline.sliderColor(t);
            StylesUiConfig.outlineRainbow = false;
        } else if (drag == Drag.BLOCK_WIDTH) {
            StylesUiConfig.blockOutlineWidth = 1 + Math.round(t * 5);
        } else if (drag == Drag.OUTLINE_WIDTH) {
            StylesUiConfig.outlineWidth = 1 + Math.round(t * 5);
        }
    }

    private void commitHex() {
        if (hexDraft.length() == 6) {
            try {
                StylesUiConfig.accent = Integer.parseInt(hexDraft, 16) & 0xFFFFFF;
                StylesUiConfig.save();
            } catch (NumberFormatException ignored) {
            }
        }
        hexFocused = false;
        hexDraft = "";
    }

    private boolean clickedUi(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (zoomFolder != null) {
            zoomFolder = null;
            zoomName = null;
            return true;
        }
        if (bindingKey) {
            if (!inside(mouseX, mouseY, hotkeyX, hotkeyY, hotkeyW, 16)) {
                StylesHotkey.set(InputUtil.Type.MOUSE.createFromCode(button));
            }
            bindingKey = false;
            return true;
        }

        searchFocused = false;
        socialFocused = false;
        if (hexFocused) {
            commitHex();
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mouseX, mouseY)) {
                h.action().run(button, mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!liveDrag()) {
            super.renderBackground(ctx, mouseX, mouseY, delta);
        }
    }

    /** Sliders for things in your hand: while dragging, the menu hides so you see the result. */
    private boolean liveDrag() {
        return drag == Drag.TOTEM || drag == Drag.SHIELD_SIZE || drag == Drag.SHIELD || drag == Drag.GAP_SIZE || drag == Drag.TIME
                || drag == Drag.OUTLINE_HUE || drag == Drag.OUTLINE_WIDTH;
    }

    /** Die Sicht vor dem Farbziehen, damit sie danach zurueck gesetzt wird. */
    private net.minecraft.client.option.Perspective outlineView;

    /** Beim Ziehen der Outline-Farbe geht die Sicht nach hinten, man sieht sich selbst. */
    private void thirdPerson(boolean on) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerOutline.previewSelf = on;
        if (on) {
            if (outlineView == null) {
                outlineView = mc.options.getPerspective();
                mc.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            }
        } else if (outlineView != null) {
            mc.options.setPerspective(outlineView);
            outlineView = null;
        }
    }

    /** Only the slider, so the world and the item in your hand stay visible while you drag. */
    private void drawLiveSlider(DrawContext ctx) {
        c = StylesUiConfig.theme();
        if (drag == Drag.OUTLINE_HUE) {
            drawLiveHue(ctx);
            return;
        }
        if (drag == Drag.OUTLINE_WIDTH) {
            drawLiveWidth(ctx);
            return;
        }
        if (drag == Drag.TIME) {
            drawLiveTime(ctx);
            return;
        }
        thirdPerson(false);
        HandPreview.show(new ItemStack(drag == Drag.TOTEM ? Items.TOTEM_OF_UNDYING
                : drag == Drag.GAP_SIZE ? Items.GOLDEN_APPLE : Items.SHIELD));
        String label;
        String value;
        float t;
        if (drag == Drag.SHIELD) {
            label = "SHIELD OPACITY";
            value = StylesUiConfig.shieldOpacity >= 100 ? "Normal" : StylesUiConfig.shieldOpacity + "%";
            t = (StylesUiConfig.shieldOpacity - 15) / 85.0F;
        } else {
            int size = drag == Drag.TOTEM ? StylesUiConfig.totemSize
                    : drag == Drag.GAP_SIZE ? StylesUiConfig.gapSize : StylesUiConfig.shieldSize;
            label = drag == Drag.TOTEM ? "TOTEM SIZE" : drag == Drag.GAP_SIZE ? "GAP SIZE" : "SHIELD SIZE";
            value = size == 100 ? "Normal" : size + "%";
            t = (size - 20) / 130.0F;
        }
        int w = dragW;
        int x = dragX;
        int y = dragY;
        soft(ctx, x - 8, y - 22, w + 16, 36, StylesTheme.withAlpha(c.base, 0xD0));
        text(ctx, label, x, y - 14, c.textDim);
        textRight(ctx, value, x + w, y - 14, c.text);
        int fill = x + Math.round(t * (w - 1));
        soft(ctx, x, y + 1, w, 4, c.surface);
        soft(ctx, x, y + 1, Math.max(3, fill - x), 4, c.accentDark);
        soft(ctx, fill - 2, y - 2, 5, 10, c.accent);
    }

    private boolean draggedUi(Click click, double offsetX, double offsetY) {
        if (scrollDrag != 0) {
            dragScroll(click.y());
            return true;
        }
        if (drag != Drag.NONE) {
            updateDrag(click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    /** Farbleiste statt Prozentbalken, dazu die Sicht von hinten auf dich selbst. */
    private void drawLiveHue(DrawContext ctx) {
        thirdPerson(true);
        HandPreview.clear();
        int w = dragW;
        int x = dragX;
        int y = dragY;
        soft(ctx, x - 8, y - 22, w + 16, 40, StylesTheme.withAlpha(c.base, 0xD0));
        text(ctx, "OUTLINE COLOR", x, y - 14, c.textDim);
        int shown = StylesUiConfig.outlineRainbow ? PlayerOutline.color() : StylesUiConfig.outlineColor;
        textRight(ctx, String.format("#%06X", shown & 0xFFFFFF), x + w, y - 14, 0xFF000000 | shown);
        for (int i = 0; i < w; i++) {
            ctx.fill(x + i, y, x + i + 1, y + 8,
                    0xFF000000 | PlayerOutline.sliderColor(i / (float) Math.max(1, w - 1)));
        }
        int knob = x + Math.round(PlayerOutline.sliderPos(StylesUiConfig.outlineColor) * (w - 1));
        soft(ctx, knob - 2, y - 3, 5, 14, 0xFF000000);
        ctx.fill(knob - 1, y - 2, knob + 1, y + 10, 0xFFFFFFFF);
    }

    /** Thickness slider alone on screen, with the view from behind so you see your own glow. */
    private void drawLiveWidth(DrawContext ctx) {
        thirdPerson(true);
        HandPreview.clear();
        int w = dragW;
        int x = dragX;
        int y = dragY;
        int width = StylesUiConfig.outlineWidth;
        float t = (width - 1) / 5.0F;
        soft(ctx, x - 8, y - 22, w + 16, 36, StylesTheme.withAlpha(c.base, 0xD0));
        text(ctx, "OUTLINE THICKNESS", x, y - 14, c.textDim);
        textRight(ctx, width == 2 ? "Normal" : String.valueOf(width), x + w, y - 14, c.text);
        int fill = x + Math.round(t * (w - 1));
        soft(ctx, x, y + 1, w, 4, c.surface);
        soft(ctx, x, y + 1, Math.max(3, fill - x), 4, c.accentDark);
        soft(ctx, fill - 2, y - 2, 5, 10, c.accent);
    }

    private boolean releasedUi(Click click) {
        if (scrollDrag != 0) {
            scrollDrag = 0;
            return true;
        }
        if (drag != Drag.NONE) {
            boolean volume = drag == Drag.KILL_VOLUME;
            if (drag == Drag.PICK_HUE) {
                huePreview = false;
                setPick(huePick, true, pickColor(huePick));
            }
            drag = Drag.NONE;
            thirdPerson(false);
            HandPreview.clear();
            StylesUiConfig.save();
            if (volume) {
                KillEffect.killSound(); // hear the new volume
            }
            return true;
        }
        return super.mouseReleased(click);
    }

    private boolean scrolledUi(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (zoomFolder != null) {
            return true;
        }
        if (publicView && mouseX >= publicLeft && mouseX < publicRight && mouseY >= publicTop && mouseY < publicBottom) {
            publicScroll = Math.max(0, Math.min(publicMaxScroll, publicScroll - verticalAmount * 18));
            return true;
        }
        if (extrasView && mouseX >= tileLeft && mouseX < tileRight + 8 && mouseY >= tileTop && mouseY < tileBottom
                && !(extraOpen && tileLeft == extrasLeft)) {
            tileScroll = Math.max(0, Math.min(tileMaxScroll, tileScroll - verticalAmount * 18));
            return true;
        }
        if (extrasView && mouseX >= extrasLeft && mouseX < extrasRight && mouseY >= extrasTop && mouseY < extrasBottom) {
            extrasScroll = Math.max(0, Math.min(extrasMaxScroll, extrasScroll - verticalAmount * 18));
            return true;
        }
        if (mouseX >= themeLeft && mouseX < themeRight + 8 && mouseY >= themeTop && mouseY < themeBottom
                && themeMaxScroll > 0 && ((StylesUiConfig.themesTab && !presetsView) || compactThemes)) {
            themeScroll = Math.max(0, Math.min(themeMaxScroll, themeScroll - verticalAmount * 18));
            return true;
        }
        if (mouseX >= gridLeft && mouseX < gridRight + 8 && mouseY >= gridTop && mouseY < gridBottom && gridMaxScroll > 0) {
            gridScroll = Math.max(0, Math.min(gridMaxScroll, gridScroll - verticalAmount * 24));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (bindingKey) {
            StylesHotkey.set(key == GLFW.GLFW_KEY_ESCAPE
                    ? InputUtil.UNKNOWN_KEY : InputUtil.Type.KEYSYM.createFromCode(key));
            bindingKey = false;
            return true;
        }
        if (zoomFolder != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                zoomFolder = null;
                zoomName = null;
            }
            return true;
        }
        if (pendingPreset != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                pendingPreset = null;
            }
            return true;
        }
        if (updateAsk != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                answerUpdate(2);
            }
            return true;
        }
        if (whatsNew != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                whatsNew = null;
            }
            return true;
        }
        if (resetAsk) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                resetAsk = false;
            }
            return true;
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (query.isEmpty()) {
                    searchFocused = false;
                } else {
                    query = "";
                    applyFilter();
                }
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                gridScroll = 0;
                applyFilter();
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB) {
                searchFocused = false;
            }
            return true;
        }
        if (keyEntering) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                keyEntering = false;
            } else if (input.isPaste()) {
                String pasted = MinecraftClient.getInstance().keyboard.getClipboard().replaceAll("[^A-Za-z0-9-]", "");
                keyDraft = (keyDraft + pasted).toUpperCase(Locale.ROOT);
                if (keyDraft.length() > 12) {
                    keyDraft = keyDraft.substring(0, 12);
                }
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !keyDraft.isEmpty()) {
                keyDraft = keyDraft.substring(0, keyDraft.length() - 1);
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                redeemKey();
            }
            return true;
        }
        if (presetNaming) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                presetNaming = false;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !presetDraft.isEmpty()) {
                presetDraft = presetDraft.substring(0, presetDraft.length() - 1);
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                savePreset();
            }
            return true;
        }
        if (socialFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                socialFocused = false;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !StylesUiConfig.socialName.isEmpty()) {
                StylesUiConfig.socialName = StylesUiConfig.socialName.substring(0, StylesUiConfig.socialName.length() - 1);
                StylesUiConfig.save();
            }
            return true;
        }
        if (hexFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                hexFocused = false;
                hexDraft = "";
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !hexDraft.isEmpty()) {
                hexDraft = hexDraft.substring(0, hexDraft.length() - 1);
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitHex();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_F) {
            int index = hoveredEntry >= 0 ? hoveredEntry : focusedIndex;
            if (index >= 0 && index < entries.size() && entries.get(index)[0] != null) {
                toggleFavorite(entries.get(index)[0]);
            }
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + Math.min(9, shownTabs().length)) {
            switchTab(shownTabs()[key - GLFW.GLFW_KEY_1]);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) {
            return false;
        }
        String typed = input.asString();
        if (searchFocused) {
            if (query.length() < 24) {
                query += typed;
                gridScroll = 0;
                applyFilter();
            }
            return true;
        }
        if (keyEntering) {
            if (typed.matches("[A-Za-z0-9-]") && keyDraft.length() < 12) {
                keyDraft += typed.toUpperCase(Locale.ROOT);
            }
            return true;
        }
        if (presetNaming) {
            if (presetDraft.length() < StylesPresets.MAX_NAME) {
                presetDraft += typed;
            }
            return true;
        }
        if (socialFocused) {
            if (StylesUiConfig.socialName.length() < 16) {
                StylesUiConfig.socialName += typed;
                StylesUiConfig.save();
            }
            return true;
        }
        if (hexFocused) {
            if (typed.matches("[0-9a-fA-F]") && hexDraft.length() < 6) {
                hexDraft += typed.toUpperCase(Locale.ROOT);
                if (hexDraft.length() == 6) {
                    commitHex();
                }
            }
            return true;
        }
        if ("/".equals(typed)) {
            searchFocused = true;
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        Cosmetics.tryOn(null, null);
        thirdPerson(false);
        HandPreview.clear();
        super.removed();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
            // selected but not applied yet: closing the menu applies it, nothing is lost
            applyPending(false);
        }
    }
}

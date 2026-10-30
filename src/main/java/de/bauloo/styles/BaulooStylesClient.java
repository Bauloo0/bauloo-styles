package de.bauloo.styles;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.InputUtil;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Bauloo Styles: crystal, anchor, armor, fire and sky looks in a single mod.
 * Every style is registered as a built-in resource pack; switching happens
 * in BaulooStylesConfigScreen.
 *
 * Made by bauloo.
 */
public class BaulooStylesClient implements ClientModInitializer {

	public static final String MOD_ID = "bauloo_styles";
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Bauloo Styles");

	/**
	 * The resourcepacks folder the game really reads. Launchers like Dawn move it away from
	 * gameDir/resourcepacks, and a pack written there is never found.
	 */
	public static java.nio.file.Path packsDir() {
		net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
		return mc != null ? mc.getResourcePackDir() : FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
	}

	/**
	 * Where the mod keeps its own packs (cloud styles, uploads, anchor glow, crystal core). The game reads
	 * this folder too (StylePacksMixin), with the same "file/..." ids as before, but launchers and other
	 * pack menus that list the resourcepacks folder don't see them.
	 */
	public static java.nio.file.Path stylePacksDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles/packs");
	}

	/** Our packs that are still in the normal resourcepacks folder (older versions put them there). */
	public static boolean isStylePack(String fileName) {
		return fileName.startsWith("bauloo_cloud_") || fileName.startsWith("bauloo_upload_")
				|| fileName.startsWith("bauloo_anchor_glow") || fileName.startsWith("bauloo_crystal_core");
	}

	/** The style categories shown in the menu. */
	public enum Tab {
		CRYSTAL("crystal", "Crystal"),
		/** obsidian + crying obsidian, right under Crystal; the styles come from the cloud */
		OBSIDIAN("obsidian", "Obsidian"),
		ANCHOR("anchor", "Anchor"),
		/** shown inside the Anchor collection, like Pickaxe inside Tools */
		GLOWSTONE("glowstone", "Glowstone"),
		ARMOR("armor", "Armor"),
		/** "Tools" in the menu: swords, pickaxes and axes side by side, one of each can be active */
		SWORD("sword", "Tools"),
		PICKAXE("pickaxe", "Pickaxe"),
		AXE("axe", "Axe"),
		/** "Gaps & Pearls" in the menu: golden apples and ender pearls side by side, like Tools */
		GAP("gap", "Gaps & Pearls"),
		PEARL("pearl", "Pearl"),
		/** totem textures; the styles come from uploads and the cloud */
		TOTEM("totem", "Totem"),
		/** the hotbar and its selection frame (more HUD parts can join this collection later) */
		HOTBAR("hotbar", "HUD"),
		INVENTORY("inventory", "Inventory"),
		FIRE("fire", "Fire"),
		SKY("sky", "Sky"),
		DEEPSLATE("deepslate", "Deepslate"),
		STONE("stone", "Stone"),
		SNOW("snow", "Snow"),
		/** one colour for all grass, right under Snow */
		GRASS("grass", "Grass"),
		/** free slots for collections that come from the cloud index (CloudCategories); hidden while unused */
		EXTRA1("extra1", "Extra 1"), EXTRA2("extra2", "Extra 2"), EXTRA3("extra3", "Extra 3"), EXTRA4("extra4", "Extra 4"),
		EXTRA5("extra5", "Extra 5"), EXTRA6("extra6", "Extra 6"), EXTRA7("extra7", "Extra 7"), EXTRA8("extra8", "Extra 8"),
		EXTRA9("extra9", "Extra 9"), EXTRA10("extra10", "Extra 10"), EXTRA11("extra11", "Extra 11"), EXTRA12("extra12", "Extra 12");

		/** fixed for the built-in collections; a cloud slot (EXTRA1-12) gets its own when the index names one */
		public String prefix;
		public String title;
		/** item id for the menu icon of a cloud slot */
		public String icon = "minecraft:paper";
		/** a cloud slot that the current index uses */
		public boolean bound;

		Tab(String prefix, String title) {
			this.prefix = prefix;
			this.title = title;
		}

		public boolean isCloudSlot() {
			return name().startsWith("EXTRA");
		}

		/** Gives the cloud slots the collections of the current index. Render thread. */
		/** goes up every time the cloud collections are bound to their slots */
		public static int bindVersion;

		public static void bindCloudSlots() {
			bindVersion++;
			for (Tab t : values()) {
				if (t.isCloudSlot()) {
					int slot = t.ordinal() - EXTRA1.ordinal() + 1;
					CloudCategories.Def d = null;
					for (CloudCategories.Def c : CloudCategories.visible()) {
						if (c.slot() == slot) {
							d = c;
						}
					}
					t.bound = d != null;
					t.prefix = d != null ? d.id() : "extra" + slot;
					t.title = d != null ? d.name() : "Extra " + slot;
					t.icon = d != null ? d.icon() : "minecraft:paper";
				}
			}
		}
	}

	/** { folder under resourcepacks/, display name } */
	public static final String[][] CRYSTAL = {
			{"crystal_invisible", "Invisible"},
			{"crystal_seethrough", "See-Through"},
			{"crystal_aqua", "Aqua"},
			{"crystal_blueberry", "Blueberry"},
			{"crystal_purple", "Marlow"},
			{"crystal_lavender", "Lavender"},
			{"crystal_evantii", "Evantii"},
			{"crystal_doge", "Doge"},
			{"crystal_sixuii", "Sixuii"},
	};

	public static final String[][] ANCHOR = {
			{"anchor_bauloo", "Bauloo Private"},
			{"anchor_manos", "Manos Purple Mashup"},
			{"anchor_xgomme", "xGomme Mashup"},
			{"anchor_k1rbekesu", "k1rbe & Kesu"},
			{"anchor_vanillaenh", "Vanilla Enhanced"},
			{"anchor_kesumain", "Kesu Main"},
			{"anchor_vaunta", "Vaunta"},
			{"anchor_lavander", "Lavander"},
			{"anchor_kesieoptimal", "Kesie Optimal"},
			{"anchor_faithful", "Faithful Overlay"},
			{"anchor_orchard", "Orchard"},
			{"anchor_purpedit", "Purp Edit"},
			{"anchor_kesielight", "Kesie BlueBerry Light"},
			{"anchor_clearvision", "Clear Vision"},
	};

	public static final String[][] ARMOR = {
			{"armor_old1192", "1.19.2 Vanilla"},
			{"armor_kesu", "Kesu Private"},
			{"armor_crey", "Crey Private"},
			{"armor_marlow", "Marlow"},
			{"armor_greendemon", "GreenDemon"},
			{"armor_sakura", "Sakura Tweaks"},
			{"armor_vanillaenh", "Vanilla Enhanced"},
			{"armor_legend", "The6Legend"},
	};

	/**
	 * Swords and pickaxes, from bauloo's "sword & pickaxe txtr" folder on 2026-09-24.
	 * Left out because they looked the same: "xpcqe edited doge Prv" (= Doge), the
	 * Kesie Optimal sword (= Doge) and the Kesie Optimal pickaxe (= Marlow).
	 */
	public static final String[][] SWORD = {
			{"sword_doge", "Doge"},
			{"sword_kesieblueberry", "Kesie BlueBerry"},
			{"sword_marlow", "Marlow"},
			{"sword_lavander", "Lavander"},
			{"sword_bauloo", "Bauloo Private"},
			{"sword_killas", "Killa's Lightmode"},
			{"sword_ggrav", "GGrav Private"},
	};

	/** Glowstone looks from bauloo's packs (2026-09-26), picked by him; shown under Anchor. */
	public static final String[][] GLOWSTONE = {
			{"glowstone_kesumain", "Kesu Main"},
			{"glowstone_mashup", "Mashup"},
			{"glowstone_taurt", "Taurt"},
			{"glowstone_purpleprivate", "Purple Private"},
			{"glowstone_aquaplus", "AquaPlus"},
			{"glowstone_kesieblueberry", "Kesie BlueBerry"},
			{"glowstone_manos", "Manos Purple Mashup"},
	};

	public static final String[][] PICKAXE = {
			{"pickaxe_doge", "Doge"},
			{"pickaxe_marlow", "Marlow"},
			{"pickaxe_dylan", "DYLAN Mashup"},
			{"pickaxe_clearvision", "Clear Vision"},
			{"pickaxe_daquavious", "Daquavious"},
			{"pickaxe_lavander", "Lavander"},
			{"pickaxe_bauloo", "Bauloo Private"},
	};

	/** Inventory and container looks from bauloo's packs (2026-09-24): see-through, lavender, dark. */
	public static final String[][] INVENTORY = {
			{"inventory_amaranth", "Amaranth Clear"},
			{"inventory_blossom", "Blossom"},
			{"inventory_doge", "Doge Private"},
	};

	/**
	 * Fire styles, taken from bauloo's "fire txt" collection on 2026-09-15.
	 * The older 13 fire styles were moved to bauloostyles-removed-styles/fire-2026-09-15.
	 */
	public static final String[][] FIRE = {
			{"fire_kesu", "Kesu Fire"},
			{"fire_ultralow", "Ultra Low Fire"},
			{"fire_cowarted", "Cowarted Low Fire"},
			{"fire_16x", "Fire 16x"},
			{"fire_32x", "Fire 32x"},
			{"fire_none", "No Fire"},
	};

	/**
	 * Skies are not built in: their textures made the jar 100 MB. Since 2026-09-15 all of
	 * them live in the cloud (StylesCloud) and download on first use; the old folders are
	 * in bauloostyles-removed-styles/sky-to-cloud-2026-09-15.
	 */
	public static final String[][] SKY = {};

	/** Deepslate recoloured from bauloo's "tostraightainttuff" pack; ores keep their colour. */
	public static final String[][] DEEPSLATE = {
			{"deepslate_lavender", "Lavender"},
			{"deepslate_blue", "Blue"},
			{"deepslate_ice", "Ice"},
			{"deepslate_red", "Red"},
			{"deepslate_pink", "Pink"},
			{"deepslate_purple", "Purple"},
			{"deepslate_black", "Black"},
			{"deepslate_white", "White"},
	};

	/** Stone recoloured from the white stone in "Walksy Default"; ores keep their colour. */
	public static final String[][] STONE = {
			{"stone_white", "White"},
			{"stone_lavender", "Lavender"},
			{"stone_blue", "Blue"},
			{"stone_ice", "Ice"},
			{"stone_red", "Red"},
			{"stone_pink", "Pink"},
			{"stone_purple", "Purple"},
			{"stone_black", "Black"},
	};

	/** Snow recoloured from bauloo's grey "snow overlay" pack (snow and snowy grass). */
	public static final String[][] SNOW = {
			{"snow_grey", "Grey"},
			{"snow_white", "White"},
			{"snow_black", "Black"},
			{"snow_blue", "Blue"},
			{"snow_ice", "Ice"},
			{"snow_red", "Red"},
			{"snow_pink", "Pink"},
			{"snow_purple", "Purple"},
			{"snow_lavender", "Lavender"},
	};

	/**
	 * Grass in one colour: each pack is a flat grass colormap. GrassColorMixin also reads the
	 * active one, so biomes with their own fixed grass colour (badlands, mushroom) follow it too.
	 */
	public static final String[][] GRASS = {
			{"grass_lush", "Lush Green"},
			{"grass_dark", "Dark Green"},
			{"grass_lime", "Lime"},
			{"grass_white", "White"},
			{"grass_black", "Black"},
			{"grass_blue", "Blue"},
			{"grass_ice", "Ice"},
			{"grass_red", "Red"},
			{"grass_pink", "Pink"},
			{"grass_purple", "Purple"},
			{"grass_lavender", "Lavender"},
	};

	/** Gap and pearl styles are uploads (and later the cloud). */
	public static final String[][] GAP = {};
	public static final String[][] PEARL = {};
	public static final String[][] HOTBAR = {};
	public static final String[][] AXE = {};
	/** cloud slots have no built-in styles */
	public static final String[][] NONE = {};
	public static final String[][] TOTEM = {};

	/** Obsidian styles all live in the Styles cloud. */
	public static final String[][] OBSIDIAN = {};

	public static String[][] stylesOf(Tab tab) {
		return switch (tab) {
			case CRYSTAL -> CRYSTAL;
			case ANCHOR -> ANCHOR;
			case GLOWSTONE -> GLOWSTONE;
			case ARMOR -> ARMOR;
			case SWORD -> SWORD;
			case PICKAXE -> PICKAXE;
			case AXE -> AXE;
			case TOTEM -> TOTEM;
			case GAP -> GAP;
			case PEARL -> PEARL;
			case HOTBAR -> HOTBAR;
			case INVENTORY -> INVENTORY;
			case FIRE -> FIRE;
			case SKY -> SKY;
			case DEEPSLATE -> DEEPSLATE;
			case STONE -> STONE;
			case SNOW -> SNOW;
			case GRASS -> GRASS;
			case EXTRA1, EXTRA2, EXTRA3, EXTRA4, EXTRA5, EXTRA6, EXTRA7, EXTRA8, EXTRA9, EXTRA10, EXTRA11, EXTRA12 -> NONE;
			case OBSIDIAN -> OBSIDIAN;
		};
	}

	/** so holding the key does not reopen the menu every tick */
	private boolean hotkeyDown;

	@Override
	public void onInitializeClient() {
		if (Boolean.getBoolean("bauloo.audit")) {
			// Dev check for the version ports: load every mixin target once, report, quit.
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
				try {
					org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
					LOGGER.info("[Audit] all mixins applied");
				} catch (Throwable t) {
					LOGGER.error("[Audit] mixin audit failed", t);
				}
				java.io.File skyTest = new java.io.File(String.valueOf(mc.runDirectory), "bauloo-skytest");
				if (skyTest.exists()) {
					DevSkyTest.start(skyTest);
					return;
				}
				// then draw the Styles menu for a few seconds, so its code runs once too
				int[] ticks = {0, -1};
				ClientTickEvents.END_CLIENT_TICK.register(c -> {
					ticks[0]++;
					if (ticks[1] < 0 && c.getOverlay() == null && c.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen) {
						// only once the title screen is up - it would replace our screen otherwise
						ticks[1] = ticks[0];
						c.setScreen(new BaulooStylesConfigScreen(null));
					} else if (ticks[1] >= 0 && ticks[0] == ticks[1] + 80) {
						LOGGER.info("[Audit] menu open: {}", c.currentScreen == null ? "none" : c.currentScreen.getClass().getSimpleName());
					} else if (ticks[1] >= 0 && ticks[0] > ticks[1] + 80) {
						// tour: every collection with a style in the preview, a screenshot of each (run/screenshots)
						int step = ticks[0] - ticks[1] - 80;
						int stop = step / 40;
						if (stop >= BaulooStylesConfigScreen.auditStops() || !(c.currentScreen instanceof BaulooStylesConfigScreen menu)) {
							LOGGER.info("[Audit] tour done");
							c.scheduleStop();
						} else if (step % 40 == 1) {
							menu.auditShow(stop);
						} else if (step % 40 == 30) {
							LOGGER.info("[Audit] screenshot {}", stop);
							net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, c.getFramebuffer(), message -> {
							});
						}
					} else if (ticks[0] == 1200) {
						LOGGER.info("[Audit] menu never opened");
						c.scheduleStop();
					}
				});
			});
		}
		StylesUiConfig.load();
		CrystalHitbox.register();
		CobwebOutline.register();
		CustomSky.register();
		EnchantGlint.register();
		GeneratedPacks.register();
		SpotifyHud.register();
		StylesPresence.register();
		KillEffect.register();
		MaceEffect.register();
		WorldMenu.register();
		Pets.register();
		SkyEffects.register();
		// cosmetics: head and aura on your own player
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
				(type, renderer, helper, context) -> {
					if (renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer<?> player) {
						helper.register(new CosmeticsFeature(player));
					}
				});
		net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(
				net.minecraft.block.Blocks.RESPAWN_ANCHOR, net.minecraft.client.render.BlockRenderLayer.CUTOUT);
		registerHotkey();
		// Load the list of bauloo's cloud styles in the background.
		StylesCloud.refresh(true);
		// accounts bauloo shut out of the mod (blocked.json in his cloud repo)
		StylesBlock.start();
		// after every mod is set up, so Blur+ has read its config before we switch its container blur
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED
				.register(client -> BlurCompat.apply());

		FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container -> {
			for (Tab tab : Tab.values()) {
				for (String[] style : stylesOf(tab)) {
					ResourceManagerHelper.registerBuiltinResourcePack(
							Identifier.of(MOD_ID, style[0]),
							container,
							Text.literal(style[1]),
							ResourcePackActivationType.NORMAL
					);
				}
			}
		});
	}

	/** opens the styles menu with the key set inside that menu */
	private void registerHotkey() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			StylesStats.tick();
			// back to the menu after a TEST POP
			long reopen = BaulooStylesConfigScreen.reopenAtMs;
			if (reopen > 0 && System.currentTimeMillis() >= reopen) {
				BaulooStylesConfigScreen.reopenAtMs = 0;
				if (client.currentScreen == null && client.player != null) {
					client.setScreen(new BaulooStylesConfigScreen(null));
				}
			}
			if (!StylesHotkey.isSet()) {
				hotkeyDown = false;
				return;
			}
			InputUtil.Key key = StylesHotkey.get();
			boolean down;
			if (key.getCategory() == InputUtil.Type.MOUSE) {
				down = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
						client.getWindow().getHandle(), key.getCode()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			} else {
				down = InputUtil.isKeyPressed(client.getWindow(), key.getCode());
			}
			if (down && !hotkeyDown && client.currentScreen == null && client.player != null) {
				client.setScreen(new BaulooStylesConfigScreen(null));
			}
			hotkeyDown = down;
		});
	}
}

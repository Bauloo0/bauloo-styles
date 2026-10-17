package de.bauloo.styles;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Textures the mod changes itself (anchor charge, crystal core, enchant glint). All of them are
 * written live, so changing a colour never shows the loading screen. Old versions used
 * generated resource packs; those are only switched off now.
 */
public final class GeneratedPacks {

    private GeneratedPacks() {
    }

    public static boolean anyActive() {
        return false;
    }

    /** Applies all live textures again. Render thread. */
    public static void applyAll() {
        AnchorGlow.applyLive();
        AnchorPixelGlow.applyLive();
        CrystalCore.applyLive();
        EnchantGlint.applyLive();
        FireColor.applyLive();
        ShieldColor.applyLive();
        GrassColor.applyLive();
    }

    /** After every reload the textures come from the packs again, so the colours are put back. */
    public static void register() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> dropOldPacks());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.of(BaulooStylesClient.MOD_ID, "live_textures");
                    }

                    @Override
                    public Collection<Identifier> getFabricDependencies() {
                        return List.of(ResourceReloadListenerKeys.TEXTURES);
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        MinecraftClient.getInstance().send(() -> {
                            CrystalCore.applyLive();
                            // the Grass collection is a pack: its colour is known only after the reload
                            GrassColor.applyLive();
                        });
                    }
                });
    }

    /** Switches off the packs older versions generated. */
    public static void dropOldPacks() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ResourcePackManager packs = mc.getResourcePackManager();
        List<String> enabled = new ArrayList<>(packs.getEnabledIds());
        int before = enabled.size();
        AnchorGlow.prepare(mc, enabled);
        CrystalCore.prepare(mc, enabled);
        EnchantGlint.prepare(mc, enabled);
        if (enabled.size() != before) {
            packs.setEnabledProfiles(enabled);
            mc.options.refreshResourcePacks(packs);
        }
    }
}

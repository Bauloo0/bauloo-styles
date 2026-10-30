package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;

import java.util.ArrayList;

/**
 * Server resource packs always go on top of every other pack, so a server that sends its own pack
 * (totems, armor, ...) covers the styles - for players it looks like "apply does not work".
 * "Styles on top" (Extras > Server Packs, on by default) moves the styles above the server pack
 * (StylesOverServerMixin); the server pack still loads for everything else. Only on your screen.
 */
public final class ServerPacks {

    private ServerPacks() {
    }

    /** a pack this mod put in: built-in styles, cloud styles, uploads */
    public static boolean ours(String id) {
        return id.startsWith(BaulooStylesClient.MOD_ID) || id.contains(StylesCloud.FOLDER_PREFIX) || id.contains("bauloo_upload_");
    }

    /** a server resource pack is loaded right now */
    public static boolean serverPackActive() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (ResourcePackProfile p : mc.getResourcePackManager().getEnabledProfiles()) {
            if (p.getSource() == ResourcePackSource.SERVER) {
                return true;
            }
        }
        return false;
    }

    private static boolean stylesActive() {
        for (String id : MinecraftClient.getInstance().getResourcePackManager().getEnabledIds()) {
            if (ours(id) && !id.equals(BaulooStylesClient.MOD_ID)) {
                return true;
            }
        }
        return false;
    }

    /** the server pack lies over your styles (and "Styles on top" is off) */
    public static boolean covering() {
        return !StylesUiConfig.stylesOverServer && serverPackActive() && stylesActive();
    }

    /** the address of the server you are on, for the banner */
    public static String serverName() {
        ServerInfo server = MinecraftClient.getInstance().getCurrentServerEntry();
        return server != null && server.address != null && !server.address.isBlank() ? server.address : "This server";
    }

    /** the banner's button: styles on top, right now */
    public static void putOnTop() {
        StylesUiConfig.stylesOverServer = true;
        StylesUiConfig.save();
        apply();
    }

    /** sorts the packs again (the mixin puts the styles where the setting says) and reloads */
    public static void apply() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ResourcePackManager packs = mc.getResourcePackManager();
        packs.setEnabledProfiles(new ArrayList<>(packs.getEnabledIds()));
        mc.reloadResources();
    }
}

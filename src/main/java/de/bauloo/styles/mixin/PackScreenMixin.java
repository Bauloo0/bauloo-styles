package de.bauloo.styles.mixin;

import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Locale;
import java.util.stream.Stream;

/** Keeps Bauloo's implementation packs out of Minecraft's generic pack picker. */
@Mixin(PackScreen.class)
public final class PackScreenMixin {
    @ModifyVariable(method = "filter", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Stream<ResourcePackOrganizer.Pack> baulooStyles$hideBuiltIns(Stream<ResourcePackOrganizer.Pack> packs) {
        // "(built-in: Bauloo Styles)" is only in the decorated description, so match the pack id too.
        return packs.filter(pack -> {
            String name = pack.getName().toLowerCase(Locale.ROOT);
            return !name.startsWith("bauloo_styles:")
                    && !name.contains("bauloo_upload_")
                    && !name.contains("bauloo_cloud_")
                    && !name.contains("bauloo_anchor_glow")
                    && !name.contains("bauloo_crystal_core")
                    && !name.contains("bauloo_glint")
                    && !pack.getDecoratedDescription().getString().toLowerCase(Locale.ROOT)
                            .contains("built-in: bauloo styles");
        });
    }
}

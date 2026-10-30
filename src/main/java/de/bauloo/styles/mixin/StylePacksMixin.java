package de.bauloo.styles.mixin;

import de.bauloo.styles.BaulooStylesClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.FileResourcePackProvider;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.path.SymlinkFinder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The game also reads config/bauloo-styles/packs, where the mod keeps its own packs. Before, they were
 * in the resourcepacks folder, so launchers and pack menus listed dozens of bauloo_cloud_... zips.
 * Packs from there keep their "file/..." id, so styles you picked stay on. On start the old ones move over.
 */
@Mixin(MinecraftClient.class)
public class StylePacksMixin {

	@Shadow @Final private Path resourcePackDir;
	@Shadow @Final private SymlinkFinder symlinkFinder;

	@ModifyArg(method = "<init>", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/resource/ResourcePackManager;<init>([Lnet/minecraft/resource/ResourcePackProvider;)V"))
	private ResourcePackProvider[] baulooStyles$ownPacks(ResourcePackProvider[] providers) {
		Path dir = BaulooStylesClient.stylePacksDir();
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			BaulooStylesClient.LOGGER.warn("[Packs] could not make {}: {}", dir, e.toString());
			return providers;
		}
		moveOld(dir);
		ResourcePackProvider[] out = Arrays.copyOf(providers, providers.length + 1);
		out[providers.length] = new FileResourcePackProvider(dir, ResourceType.CLIENT_RESOURCES, ResourcePackSource.NONE, symlinkFinder);
		return out;
	}

	/** Moves our packs out of the resourcepacks folder; one that is already in the new folder is just deleted. */
	private void moveOld(Path dir) {
		if (resourcePackDir == null || !Files.isDirectory(resourcePackDir)) {
			return;
		}
		List<Path> old;
		try (Stream<Path> list = Files.list(resourcePackDir)) {
			old = list.filter(p -> BaulooStylesClient.isStylePack(p.getFileName().toString())).toList();
		} catch (IOException e) {
			return;
		}
		int moved = 0;
		for (Path p : old) {
			Path target = dir.resolve(p.getFileName().toString());
			try {
				// half-written copies from older versions (*.tmp.zip) are only junk
				if (Files.exists(target) || p.getFileName().toString().endsWith(".tmp.zip")) {
					deleteAll(p);
				} else {
					Files.move(p, target);
				}
				moved++;
			} catch (IOException e) {
				BaulooStylesClient.LOGGER.warn("[Packs] could not move {}: {}", p.getFileName(), e.toString());
			}
		}
		if (moved > 0) {
			BaulooStylesClient.LOGGER.info("[Packs] moved {} Bauloo Styles packs out of the resourcepacks folder", moved);
		}
	}

	private static void deleteAll(Path p) throws IOException {
		if (!Files.isDirectory(p)) {
			Files.deleteIfExists(p);
			return;
		}
		try (Stream<Path> walk = Files.walk(p)) {
			for (Path q : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(q);
			}
		}
	}
}

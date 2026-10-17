package de.bauloo.styles;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Draws an outline around End Crystals - but only while the Invisible style is
 * active, because there the crystals cannot be seen at all.
 *
 * Made by bauloo.
 */
public final class CrystalHitbox {

	private static final int COLOR = 0xCCFFFFFF;
	private static final String INVISIBLE_PACK = BaulooStylesClient.MOD_ID + ":crystal_invisible";

	private CrystalHitbox() {
	}

	/** White, or the colour picked under Extras > Invisible crystal box. */
	private static int color() {
		StylesUiConfig.load();
		return StylesUiConfig.crystalBoxCustom ? 0xCC000000 | StylesUiConfig.crystalBoxColor : COLOR;
	}

	private static boolean invisibleActive;
	private static long invisibleCheckedAt;

	/** Asking the pack manager builds a new list each time, so the answer is kept for a second. */
	private static boolean invisibleStyleActive(MinecraftClient mc) {
		long now = System.currentTimeMillis();
		if (now - invisibleCheckedAt < 1000) {
			return invisibleActive;
		}
		invisibleCheckedAt = now;
		try {
			invisibleActive = mc.getResourcePackManager().getEnabledIds().contains(INVISIBLE_PACK);
		} catch (Throwable t) {
			invisibleActive = false;
		}
		return invisibleActive;
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			try {
				MinecraftClient mc = MinecraftClient.getInstance();
				if (mc.world == null || !invisibleStyleActive(mc)) {
					return;
				}
				MatrixStack matrices = context.matrices();
				VertexConsumerProvider consumers = context.consumers();
				if (matrices == null || consumers == null) {
					return;
				}

				Vec3d cam = context.worldState().cameraRenderState.pos;
				if (cam == null) {
					return;
				}
				VertexConsumer buffer = consumers.getBuffer(RenderLayers.LINES);

				for (Entity entity : mc.world.getEntities()) {
					if (!(entity instanceof EndCrystalEntity)) {
						continue;
					}
					Box box = entity.getBoundingBox();
					VertexRendering.drawOutline(
							matrices,
							buffer,
							VoxelShapes.cuboidUnchecked(
									box.minX, box.minY, box.minZ,
									box.maxX, box.maxY, box.maxZ),
							-cam.x, -cam.y, -cam.z,
							color(),
							1.0F);
				}
			} catch (Throwable ignored) {
				// never break world rendering over an outline
			}
		});
	}
}

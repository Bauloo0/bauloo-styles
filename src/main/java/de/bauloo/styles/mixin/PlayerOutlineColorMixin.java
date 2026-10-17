package de.bauloo.styles.mixin;

import de.bauloo.styles.PlayerOutline;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Player Outline: swaps the team colour for the chosen colour on outlined players. */
@Mixin(EntityRenderer.class)
public class PlayerOutlineColorMixin {

	@Redirect(method = "updateRenderState",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getTeamColorValue()I"))
	private int baulooStyles$outlineColor(Entity entity) {
		return PlayerOutline.outlines(entity) ? PlayerOutline.color() : entity.getTeamColorValue();
	}
}

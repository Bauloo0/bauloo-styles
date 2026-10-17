package de.bauloo.styles.mixin;

import de.bauloo.styles.StylesPresence;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Bauloo Styles tornado also in front of the name tag above a player's head (see StylesPresence). */
@Mixin(value = EntityRenderer.class, priority = 2000)
public class NametagIconMixin {

	@Inject(method = "updateRenderState", at = @At("TAIL"))
	private void baulooStyles$tornado(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
		if (state.displayName == null || !(entity instanceof PlayerEntity)) {
			return;
		}
		Text icon = StylesPresence.icon(entity.getUuid());
		if (icon != null) {
			state.displayName = Text.empty().append(icon).append(" ").append(state.displayName);
		}
	}
}

package de.bauloo.styles.mixin;

import de.bauloo.styles.KillEffect;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kill Effect: remembers who you hit, so "Only my kills" knows which deaths are yours. */
@Mixin(ClientPlayerInteractionManager.class)
public class KillEffectHitMixin {

	@Inject(method = "attackEntity", at = @At("HEAD"))
	private void baulooStyles$hit(PlayerEntity player, Entity target, CallbackInfo ci) {
		KillEffect.onAttack(target);
	}
}

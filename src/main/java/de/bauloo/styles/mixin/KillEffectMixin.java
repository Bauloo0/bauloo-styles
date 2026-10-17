package de.bauloo.styles.mixin;

import de.bauloo.styles.KillEffect;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kill Effect: the server tells every client with status 3 that a living entity died. */
@Mixin(LivingEntity.class)
public class KillEffectMixin {

	@Inject(method = "handleStatus", at = @At("HEAD"))
	private void baulooStyles$died(byte status, CallbackInfo ci) {
		if (status == 3) {
			KillEffect.onDeath((LivingEntity) (Object) this);
		}
	}
}

package de.bauloo.styles.mixin;

import de.bauloo.styles.KillEffect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kill Effect, second way to notice a death: the health the server sends drops to 0. */
@Mixin(LivingEntity.class)
public abstract class KillEffectHealthMixin {

	@Shadow @Final private static TrackedData<Float> HEALTH;

	@Shadow public abstract float getHealth();

	@Inject(method = "onTrackedDataSet", at = @At("TAIL"))
	private void baulooStyles$health(TrackedData<?> data, CallbackInfo ci) {
		if (HEALTH.equals(data) && getHealth() <= 0.0F) {
			KillEffect.onDeath((LivingEntity) (Object) this);
		}
	}
}

package de.bauloo.styles.mixin;

import de.bauloo.styles.TotemParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Totem Pop amount: Off / Less / Mid leave part of the totem particles out. Your screen only. */
@Mixin(ParticleManager.class)
public class TotemParticleAmountMixin {

	@Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
			at = @At("HEAD"), cancellable = true)
	private void baulooStyles$totemAmount(ParticleEffect effect, double x, double y, double z,
			double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
		if (effect != null && effect.getType() == ParticleTypes.TOTEM_OF_UNDYING && !TotemParticles.keep()) {
			cir.setReturnValue(null);
		}
	}
}

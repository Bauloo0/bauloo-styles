package de.bauloo.styles.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Always on, no switch: explosions (crystals, anchors, TNT) spawn no explosion particles.
 * Done here instead of with a texture pack, so another pack with its own explosion textures can't bring them back.
 */
@Mixin(ParticleManager.class)
public class ExplosionParticleMixin {

	@Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
			at = @At("HEAD"), cancellable = true)
	private void baulooStyles$noExplosion(ParticleEffect effect, double x, double y, double z,
			double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
		if (effect != null && (effect.getType() == ParticleTypes.EXPLOSION || effect.getType() == ParticleTypes.EXPLOSION_EMITTER)) {
			cir.setReturnValue(null);
		}
	}
}

package de.bauloo.styles.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Builds a particle without going through addParticle(effect, ...), which particle
 * settings and mods like Sodium Extra switch off. Used only for the kill effects.
 */
@Mixin(ParticleManager.class)
public interface ParticleManagerInvoker {

	@Invoker("createParticle")
	Particle baulooStyles$create(ParticleEffect effect, double x, double y, double z,
								 double velocityX, double velocityY, double velocityZ);
}

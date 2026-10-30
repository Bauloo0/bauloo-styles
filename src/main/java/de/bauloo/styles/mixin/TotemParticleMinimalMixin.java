package de.bauloo.styles.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.bauloo.styles.TotemParticles;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.ParticlesMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** With Totem Pop on, totem pops show their particles even with Particles: Minimal (everything else stays minimal). */
@Mixin(ClientWorld.class)
public class TotemParticleMinimalMixin {

	@WrapOperation(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;ZZDDDDDD)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getParticlesMode(Z)Lnet/minecraft/particle/ParticlesMode;"))
	private ParticlesMode baulooStyles$totemOnMinimal(ClientWorld self, boolean canSpawnOnMinimal, Operation<ParticlesMode> original,
			@Local(argsOnly = true) ParticleEffect effect) {
		if (effect != null && effect.getType() == ParticleTypes.TOTEM_OF_UNDYING && TotemParticles.on()) {
			return ParticlesMode.ALL;
		}
		return original.call(self, canSpawnOnMinimal);
	}
}

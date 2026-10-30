package de.bauloo.styles.mixin;

import de.bauloo.styles.TotemParticles;
import net.minecraft.client.particle.AnimatedParticle;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Animated particles pick their glitter frame every tick; a plus-sign totem particle keeps its plus. */
@Mixin(AnimatedParticle.class)
public abstract class TotemParticlePlusMixin extends BillboardParticle {

	protected TotemParticlePlusMixin(ClientWorld world, double x, double y, double z, Sprite sprite) {
		super(world, x, y, z, sprite);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void baulooStyles$keepPlus(CallbackInfo ci) {
		if ((Object) this instanceof TotemParticle && TotemParticles.plus()) {
			this.setSprite(TotemParticles.plusSprite());
		}
	}
}

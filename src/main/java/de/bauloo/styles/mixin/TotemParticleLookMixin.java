package de.bauloo.styles.mixin;

import de.bauloo.styles.TotemParticles;
import net.minecraft.client.particle.AnimatedParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totem Pop look: size, how long it stays, colour and the plus sign, set once per particle. */
@Mixin(TotemParticle.class)
public abstract class TotemParticleLookMixin extends AnimatedParticle {

	protected TotemParticleLookMixin(ClientWorld world, double x, double y, double z, SpriteProvider sprites, float gravity) {
		super(world, x, y, z, sprites, gravity);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void baulooStyles$look(ClientWorld world, double x, double y, double z, double vx, double vy, double vz,
			SpriteProvider sprites, CallbackInfo ci) {
		if (!TotemParticles.on()) {
			return;
		}
		this.scale *= TotemParticles.sizeFactor();
		this.maxAge = Math.max(4, Math.round(this.maxAge * TotemParticles.lifeFactor()));
		int rgb = TotemParticles.color(this.random);
		if (rgb >= 0) {
			this.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
		}
		if (TotemParticles.plus()) {
			this.setSprite(TotemParticles.plusSprite());
		}
	}
}

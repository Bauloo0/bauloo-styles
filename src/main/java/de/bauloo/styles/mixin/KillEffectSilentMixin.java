package de.bauloo.styles.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.bauloo.styles.KillEffect;
import net.minecraft.entity.LightningEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Kill Effect: our lightning bolt is only for the eyes, no thunder. */
@Mixin(LightningEntity.class)
public class KillEffectSilentMixin {

	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;playSoundClient(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZ)V"))
	private void baulooStyles$silent(World world, double x, double y, double z, SoundEvent sound, SoundCategory category,
									 float volume, float pitch, boolean distance, Operation<Void> original) {
		if (!KillEffect.SILENT.contains((Object) this)) {
			original.call(world, x, y, z, sound, category, volume, pitch, distance);
		}
	}
}

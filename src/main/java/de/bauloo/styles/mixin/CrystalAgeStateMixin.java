package de.bauloo.styles.mixin;

import de.bauloo.styles.CrystalAge;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every end crystal render state a slot for its real (not sped-up) age. */
@Mixin(EndCrystalEntityRenderState.class)
public class CrystalAgeStateMixin implements CrystalAge {

	@Unique
	private float baulooStyles$realAge = Float.NaN;

	@Override
	public float baulooStyles$realAge() {
		return baulooStyles$realAge;
	}

	@Override
	public void baulooStyles$setRealAge(float age) {
		baulooStyles$realAge = age;
	}
}

package de.bauloo.styles.mixin;

import de.bauloo.styles.CrystalAge;
import de.bauloo.styles.StylesUiConfig;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crystal Spin: only the turning of end crystals changes speed.
 * The animation age handed to the renderer is scaled (vanilla and mods like Custom End
 * Crystals turn the crystal from it). Before each crystal is drawn, the difference to the
 * real age is remembered and added back wherever the up-and-down bob is computed.
 * Priority 500 so this runs before other mods that take over crystal rendering.
 */
@Mixin(value = EndCrystalEntityRenderer.class, priority = 500)
public class CrystalSpinMixin {

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/decoration/EndCrystalEntity;Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;F)V",
			at = @At("TAIL"))
	private void baulooStyles$spinSpeed(EndCrystalEntity crystal, EndCrystalEntityRenderState state, float tickProgress,
			CallbackInfo ci) {
		((CrystalAge) state).baulooStyles$setRealAge(state.age);
		if (StylesUiConfig.crystalSpin != 100) {
			state.age *= CrystalAge.factor();
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
			at = @At("HEAD"))
	private void baulooStyles$rememberOffset(EndCrystalEntityRenderState state, MatrixStack matrices,
			OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
		float real = ((CrystalAge) state).baulooStyles$realAge();
		CrystalAge.Offset.current = Float.isNaN(real) ? 0.0F : real - state.age;
	}

	@ModifyVariable(method = "getYOffset", at = @At("HEAD"), argsOnly = true)
	private static float baulooStyles$normalBob(float age) {
		return age + CrystalAge.Offset.current;
	}
}

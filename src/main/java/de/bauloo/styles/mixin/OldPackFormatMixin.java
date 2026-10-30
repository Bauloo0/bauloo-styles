package de.bauloo.styles.mixin;

import com.mojang.serialization.DataResult;
import net.minecraft.resource.PackVersion;
import net.minecraft.util.dynamic.Range;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Many packs made for 1.21.9+ still write their pack.mcmeta the old way ("pack_format" or
 * "supported_formats" without "min_format"/"max_format"). Minecraft refuses those completely
 * ("Pack declares support for version newer than 64, but is missing mandatory fields"), so the pack
 * and every style in it disappear. Here such a pack is read as the old way says instead. A pack that has
 * min_format/max_format but still lists supported_formats is refused too; that one just uses min/max.
 */
@Mixin(PackVersion.Format.class)
public class OldPackFormatMixin {

	@Shadow @Final private Optional<PackVersion> min;
	@Shadow @Final private Optional<PackVersion> max;
	@Shadow @Final private Optional<Integer> format;
	@Shadow @Final private Optional<Range<Integer>> supported;

	@Inject(method = "validate", at = @At("RETURN"), cancellable = true)
	private void baulooStyles$acceptOldFormat(int lastOld, boolean a, boolean b, String c, String d,
											  CallbackInfoReturnable<DataResult<Range<PackVersion>>> cir) {
		if (!cir.getReturnValue().isError()) {
			return;
		}
		if (min.isPresent() && max.isPresent()) {
			// "supported_formats is deprecated starting from pack format 65": min/max say it all already
			try {
				cir.setReturnValue(DataResult.success(new Range<>(min.get(), max.get())));
			} catch (RuntimeException ignored) {
				// min above max: leave Minecraft's error
			}
			return;
		}
		if (min.isPresent() || max.isPresent()) {
			return;
		}
		if (supported.isPresent()) {
			Range<Integer> r = supported.get();
			int lo = Math.min(r.minInclusive(), r.maxInclusive());
			int hi = Math.max(r.minInclusive(), r.maxInclusive());
			cir.setReturnValue(DataResult.success(new Range<>(PackVersion.of(lo), PackVersion.of(hi))));
		} else if (format.isPresent()) {
			cir.setReturnValue(DataResult.success(new Range<>(PackVersion.of(format.get()))));
		}
	}
}

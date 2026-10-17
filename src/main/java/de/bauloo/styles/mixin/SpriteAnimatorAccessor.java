package de.bauloo.styles.mixin;

import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.Animator.class)
public interface SpriteAnimatorAccessor {

	@Accessor("textureViewsByFrame")
	Int2ObjectMap<GpuTextureView> baulooStyles$frames();
}

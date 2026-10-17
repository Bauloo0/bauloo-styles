package de.bauloo.styles.mixin;

import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Dev sky test: presses a button the same way on every game version. */
@Mixin(ButtonWidget.class)
public interface ButtonPressAccessor {

	@Accessor("onPress")
	ButtonWidget.PressAction baulooStyles$onPress();
}

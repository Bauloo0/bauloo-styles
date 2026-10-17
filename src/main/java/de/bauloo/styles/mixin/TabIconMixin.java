package de.bauloo.styles.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.bauloo.styles.StylesPresence;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tab icon: the Bauloo Styles tornado in front of the name (see StylesPresence).
 * Wraps the spot where the tab list takes the name to draw it, so it also works when another
 * mod (nick, tier tags) replaces the name itself.
 */
@Mixin(value = PlayerListHud.class, priority = 1500)
public class TabIconMixin {

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/hud/PlayerListHud;getPlayerName(Lnet/minecraft/client/network/PlayerListEntry;)Lnet/minecraft/text/Text;"))
	private Text baulooStyles$tornado(PlayerListHud self, PlayerListEntry entry, Operation<Text> original) {
		Text name = original.call(self, entry);
		Text icon = StylesPresence.icon(entry.getProfile().id());
		return icon == null ? name : Text.empty().append(icon).append(" ").append(name);
	}
}

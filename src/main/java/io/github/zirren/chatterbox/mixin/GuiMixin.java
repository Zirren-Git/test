package io.github.zirren.chatterbox.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;

import io.github.zirren.chatterbox.screen.ChatterBoxChatScreen;

/**
 * Routes every way of opening the chat screen to ChatterBox's chat screen so
 * the folder tabs are always present (T key, / key, keybinds, servers that
 * open chat with text pre-filled).
 *
 * <p>Deliberately free of {@code @Shadow} members and fully guarded: if
 * anything goes wrong (or another mod conflicts), the vanilla chat screen
 * opens exactly as before - ChatterBox never hard-crashes the game.</p>
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

	private static final Logger LOGGER = LoggerFactory.getLogger("ChatterBox-Mixin");

	@Inject(method = "openChatScreen", at = @At("HEAD"), cancellable = true, require = 0)
	private void chatterbox$openChatScreen(ChatComponent.ChatMethod chatMethod, CallbackInfo ci) {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null || client.gui == null) return;
			client.gui.hud.getChat().openScreen(chatMethod, ChatterBoxChatScreen::new);
			ci.cancel();
		} catch (Throwable t) {
			LOGGER.warn("ChatterBox could not open its chat screen, falling back to vanilla chat", t);
		}
	}
}

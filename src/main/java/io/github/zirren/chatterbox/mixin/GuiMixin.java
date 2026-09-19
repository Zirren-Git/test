package io.github.zirren.chatterbox.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;

import io.github.zirren.chatterbox.screen.ChatterBoxChatScreen;

/**
 * Routes every way of opening the chat screen to ChatterBox's chat screen so
 * the folder tabs are always present (T key, / key, keybinds, servers that
 * open chat with text pre-filled).
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

	@Shadow
	@Final
	public Hud hud;

	@Inject(method = "openChatScreen", at = @At("HEAD"), cancellable = true)
	private void chatterbox$openChatScreen(ChatComponent.ChatMethod chatMethod, CallbackInfo ci) {
		if (Minecraft.getInstance().player == null) return;
		this.hud.getChat().openScreen(chatMethod, ChatterBoxChatScreen::new);
		ci.cancel();
	}
}

package io.github.zirren.chatterbox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import io.github.zirren.chatterbox.chat.ChatStore;

/**
 * Intercepts the three public entry points of the vanilla chat hud. Every
 * message the client ever shows goes through here; ChatterBox classifies,
 * stores, logs and re-displays it with folder formatting instead.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

	/**
	 * Folder switching re-adds history through the vanilla pipeline; without
	 * this every refresh would spam the client log with duplicate [CHAT]
	 * lines.
	 */
	@Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
	private void chatterbox$suppressReaddLog(GuiMessage message, CallbackInfo ci) {
		if (ChatStore.INSTANCE.isRouting()) {
			ci.cancel();
		}
	}

	@Inject(method = "addClientSystemMessage", at = @At("HEAD"), cancellable = true)
	private void chatterbox$addClientSystemMessage(Component message, CallbackInfo ci) {
		if (ChatStore.INSTANCE.onVanillaAddMessage(message,
				net.minecraft.client.multiplayer.chat.GuiMessageSource.SYSTEM_CLIENT,
				GuiMessageTag.systemSinglePlayer())) {
			return;
		}
		ci.cancel();
	}

	@Inject(method = "addServerSystemMessage", at = @At("HEAD"), cancellable = true)
	private void chatterbox$addServerSystemMessage(Component message, CallbackInfo ci) {
		if (ChatStore.INSTANCE.onVanillaAddMessage(message,
				net.minecraft.client.multiplayer.chat.GuiMessageSource.SYSTEM_SERVER,
				GuiMessageTag.systemSinglePlayer())) {
			return;
		}
		ci.cancel();
	}

	@Inject(method = "addPlayerMessage", at = @At("HEAD"), cancellable = true)
	private void chatterbox$addPlayerMessage(Component message, @Nullable MessageSignature signature,
			@Nullable GuiMessageTag tag, CallbackInfo ci) {
		if (ChatStore.INSTANCE.onVanillaAddMessage(message,
				net.minecraft.client.multiplayer.chat.GuiMessageSource.PLAYER, tag)) {
			return;
		}
		ci.cancel();
	}
}

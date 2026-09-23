package io.github.zirren.chatterbox.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import io.github.zirren.chatterbox.chat.ChatStore;

/**
 * Intercepts the three public entry points of the vanilla chat hud. Every
 * message the client ever shows goes through here; ChatterBox classifies,
 * stores, logs and re-displays it with folder formatting instead.
 *
 * <p>All injections use {@code require = 0} and fail open: on any failure -
 * including {@link ChatStore} failing to class-load in a broken or hostile
 * modded environment - vanilla displays the message unmodified, so neither
 * other mods nor unexpected message shapes can crash the game.</p>
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

	private static final Logger LOGGER = LoggerFactory.getLogger("ChatterBox-Mixin");
	private static boolean chatterbox$routeBroken = false;

	@Inject(method = "addClientSystemMessage", at = @At("HEAD"), cancellable = true, require = 0)
	private void chatterbox$addClientSystemMessage(Component message, CallbackInfo ci) {
		if (chatterbox$route(message, GuiMessageSource.SYSTEM_CLIENT, GuiMessageTag.systemSinglePlayer())) {
			return;
		}
		ci.cancel();
	}

	@Inject(method = "addServerSystemMessage", at = @At("HEAD"), cancellable = true, require = 0)
	private void chatterbox$addServerSystemMessage(Component message, CallbackInfo ci) {
		if (chatterbox$route(message, GuiMessageSource.SYSTEM_SERVER, GuiMessageTag.systemSinglePlayer())) {
			return;
		}
		ci.cancel();
	}

	@Inject(method = "addPlayerMessage", at = @At("HEAD"), cancellable = true, require = 0)
	private void chatterbox$addPlayerMessage(Component message, @Nullable MessageSignature signature,
			@Nullable GuiMessageTag tag, CallbackInfo ci) {
		if (chatterbox$route(message, GuiMessageSource.PLAYER, tag)) {
			return;
		}
		ci.cancel();
	}

	/**
	 * Returns true when the caller should proceed (vanilla displays the
	 * message), false when ChatterBox consumed it. Fully guarded, including
	 * the {@code ChatStore.INSTANCE} access itself.
	 */
	private boolean chatterbox$route(Component message, GuiMessageSource source, @Nullable GuiMessageTag tag) {
		try {
			return ChatStore.INSTANCE.onVanillaAddMessage(message, source, tag);
		} catch (Throwable t) {
			if (!chatterbox$routeBroken) {
				chatterbox$routeBroken = true;
				LOGGER.error("ChatterBox: message routing is broken - chat falls back to vanilla behavior. Please report this together with your logs/latest.log", t);
			}
			return true; // fail open: show the message unmodified
		}
	}

	/**
	 * Folder switching re-adds history through the vanilla pipeline; without
	 * this every refresh would spam the client log with duplicate [CHAT]
	 * lines.
	 */
	@Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true, require = 0)
	private void chatterbox$suppressReaddLog(GuiMessage message, CallbackInfo ci) {
		try {
			if (ChatStore.INSTANCE.isRouting()) {
				ci.cancel();
			}
		} catch (Throwable t) {
			// never let log suppression crash the game
		}
	}
}

package io.github.zirren.chatterbox;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.zirren.chatterbox.chat.ChatDisplay;
import io.github.zirren.chatterbox.chat.ChatStore;
import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.screen.ChatSearchScreen;
import io.github.zirren.chatterbox.screen.ConfigScreen;

public class ChatterBoxClient implements ClientModInitializer {
	public static final String MOD_ID = "chatterbox";
	public static final Logger LOGGER = LoggerFactory.getLogger("ChatterBox");

	@Override
	public void onInitializeClient() {
		// touch config so it loads & defaults are created
		Config.get();
		ChatStore.INSTANCE.loadPersistedPartners();
		Keybinds.register();

		// --- receiving messages ---------------------------------------
		// Capture context (player chat vs system message, sender) so the
		// ChatComponent mixin can classify the message when it is displayed.
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
				ChatStore.INSTANCE.onEvent(message, true, sender, null, false));

		ClientReceiveMessageEvents.GAME.register((message, overlay) ->
				ChatStore.INSTANCE.onEvent(message, false, null, null, overlay));

		// --- sending ---------------------------------------------------
		ClientSendMessageEvents.COMMAND.register(command -> ChatStore.INSTANCE.noteCommandSent());
		ClientSendMessageEvents.CHAT.register(message -> ChatStore.INSTANCE.noteChatSent());

		// --- connection lifecycle ---------------------------------------
		ClientPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			net.minecraft.client.multiplayer.ServerData data = client.getCurrentServer();
			String info = data != null ? data.ip : "singleplayer";
			ChatStore.INSTANCE.onJoin(info);
			ChatDisplay.installFilter();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				ChatStore.INSTANCE.onDisconnect());

		// --- keybinds ----------------------------------------------------
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (Keybinds.SEARCH.consumeClick()) {
				client.gui.setScreen(new ChatSearchScreen(client.gui.screen()));
			}
			while (Keybinds.CONFIG.consumeClick()) {
				client.gui.setScreen(new ConfigScreen(client.gui.screen()));
			}
		});

		LOGGER.info("ChatterBox initialized");
	}

	/** Convenience accessor for the active minecraft client. */
	public static Minecraft mc() {
		return Minecraft.getInstance();
	}
}

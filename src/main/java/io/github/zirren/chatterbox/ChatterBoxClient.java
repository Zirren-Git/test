package io.github.zirren.chatterbox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.zirren.chatterbox.chat.ChatDisplay;
import io.github.zirren.chatterbox.chat.ChatFileLogger;
import io.github.zirren.chatterbox.chat.ChatStore;
import io.github.zirren.chatterbox.chat.Folder;
import io.github.zirren.chatterbox.chat.Instruments;
import io.github.zirren.chatterbox.chat.Shortcuts;
import io.github.zirren.chatterbox.chat.Sounds;
import io.github.zirren.chatterbox.chat.TunePlayer;
import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.config.MentionRule;
import io.github.zirren.chatterbox.config.Shortcut;
import io.github.zirren.chatterbox.screen.ChatSearchScreen;
import io.github.zirren.chatterbox.screen.ChatterBoxChatScreen;
import io.github.zirren.chatterbox.screen.ConfigScreen;
import io.github.zirren.chatterbox.screen.DmAddScreen;
import io.github.zirren.chatterbox.screen.FoldersScreen;
import io.github.zirren.chatterbox.screen.MentionRuleEditScreen;
import io.github.zirren.chatterbox.screen.MentionRulesScreen;
import io.github.zirren.chatterbox.screen.PartnersScreen;
import io.github.zirren.chatterbox.screen.ShortcutEditScreen;
import io.github.zirren.chatterbox.screen.ShortcutsScreen;
import io.github.zirren.chatterbox.screen.SoundPickerScreen;
import io.github.zirren.chatterbox.screen.TuneMakerScreen;

public class ChatterBoxClient implements ClientModInitializer {
	public static final String MOD_ID = "chatterbox";
	public static final Logger LOGGER = LoggerFactory.getLogger("ChatterBox");

	@Override
	public void onInitializeClient() {
		// touch config so it loads & defaults are created
		safe("config load", Config::get);
		safe("dm partners load", () -> ChatStore.INSTANCE.loadPersistedPartners());
		safe("keybinds", Keybinds::register);

		// --- receiving messages ---------------------------------------
		// Capture context (player chat vs system message, sender) so the
		// ChatComponent mixin can classify the message when it is displayed.
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
				safe("chat receive", () -> ChatStore.INSTANCE.onEvent(message, true, sender, null, false)));

		ClientReceiveMessageEvents.GAME.register((message, overlay) ->
				safe("game receive", () -> ChatStore.INSTANCE.onEvent(message, false, null, null, overlay)));

		// --- sending ---------------------------------------------------
		ClientSendMessageEvents.COMMAND.register(command ->
				safe("command send", ChatStore.INSTANCE::noteCommandSent));
		ClientSendMessageEvents.CHAT.register(message ->
				safe("chat send", ChatStore.INSTANCE::noteChatSent));

		// --- connection lifecycle ---------------------------------------
		ClientPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				safe("world join", () -> {
					net.minecraft.client.multiplayer.ServerData data = Minecraft.getInstance().getCurrentServer();
					String info = data != null ? data.ip : "singleplayer";
					ChatStore.INSTANCE.onJoin(info);
					ChatDisplay.installFilter();
				}));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				safe("disconnect", ChatStore.INSTANCE::onDisconnect));

		// --- keybinds + dev selftest -------------------------------------
		SelfTest selfTest = FabricLoader.getInstance().isDevelopmentEnvironment() ? new SelfTest() : null;
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			try {
				TunePlayer.tick(); // fire due melody notes
				while (Keybinds.SEARCH != null && Keybinds.SEARCH.consumeClick()) {
					client.gui.setScreen(new ChatSearchScreen(client.gui.screen()));
				}
				while (Keybinds.CONFIG != null && Keybinds.CONFIG.consumeClick()) {
					client.gui.setScreen(new ConfigScreen(client.gui.screen()));
				}
			} catch (Throwable t) {
				LOGGER.warn("ChatterBox keybind handling failed", t);
			}
			if (selfTest != null) {
				selfTest.tick(client);
			}
		});

		LOGGER.info("ChatterBox initialized");
	}

	/** Runs a task; a ChatterBox failure must never crash the game. */
	private static void safe(String what, Runnable r) {
		try {
			r.run();
		} catch (Throwable t) {
			LOGGER.error("ChatterBox: '{}' failed (ignored)", what, t);
		}
	}

	// ------------------------------------------------------------------
	// Development-only selftest. Runs when the game is launched from a dev
	// environment (CI smoke test) and exercises the whole message pipeline
	// and every screen, then logs a final verdict and quits the game.
	// ------------------------------------------------------------------

	private final class SelfTest {
		private int tick;
		private int failures;
		private boolean announcedResult;
		private final List<Consumer<Minecraft>> steps = new ArrayList<>();

		SelfTest() {
			steps.add(client -> stepFunnel(client));
			steps.add(client -> stepFolders());
			steps.add(client -> stepShortcuts());
			steps.add(client -> stepInstruments());
			steps.add(client -> stepTuneStart());
			steps.add(client -> stepTuneCheck());
			steps.add(client -> stepConfigJson());
			steps.add(client -> client.gui.setScreen(new ConfigScreen(null)));
			steps.add(client -> client.gui.setScreen(ConfigScreen.chatSettings(null)));
			steps.add(client -> client.gui.setScreen(ConfigScreen.layoutSettings(null)));
			steps.add(client -> client.gui.setScreen(ConfigScreen.dmSettings(null)));
			steps.add(client -> client.gui.setScreen(ConfigScreen.soundSettings(null)));
			steps.add(client -> client.gui.setScreen(ConfigScreen.loggingSettings(null)));
			steps.add(client -> client.gui.setScreen(new FoldersScreen(null)));
			steps.add(client -> client.gui.setScreen(new MentionRulesScreen(null)));
			steps.add(client -> client.gui.setScreen(
					new MentionRuleEditScreen(null, new MentionRule("test", "minecraft:block.note_block.pling", 1.0f, 1.0f), false)));
			steps.add(client -> client.gui.setScreen(new MentionRuleEditScreen(null, melodyRule(), false)));
			steps.add(client -> client.gui.setScreen(new TuneMakerScreen(null, melodyRule())));
			steps.add(client -> client.gui.setScreen(new ShortcutsScreen(null)));
			steps.add(client -> client.gui.setScreen(
					new ShortcutEditScreen(null, new Shortcut("test", "value"), false)));
			steps.add(client -> client.gui.setScreen(new PartnersScreen(null)));
			steps.add(client -> client.gui.setScreen(
					new SoundPickerScreen(null, "minecraft:block.note_block.pling", 1.0f, 1.0f, id -> { })));
			steps.add(client -> client.gui.setScreen(new ChatSearchScreen(null)));
			steps.add(client -> client.gui.setScreen(new DmAddScreen(new ChatterBoxChatScreen("", true))));
		}

		void tick(Minecraft client) {
			try {
				tick++;
				if (tick == 100) {
					LOGGER.info("CHATTERBOX SELFTEST: starting (dev environment detected)");
				}
				// one step every 15 ticks after the game has booted
				int index = (tick - 100) / 15;
				if (tick >= 100 && index < steps.size() && (tick - 100) % 15 == 0) {
					steps.get(index).accept(client);
				}
				// let the last screen render for a moment, then finish
				if (tick >= 100 + 15 * steps.size() + 60 && !announcedResult) {
					announcedResult = true;
					LOGGER.info("CHATTERBOX SELFTEST COMPLETE: FAILURES={}", failures);
					LOGGER.info("CHATTERBOX SELFTEST: stopping game");
					client.stop();
				}
				if (tick > 4000) { // absolute backstop
					LOGGER.info("CHATTERBOX SELFTEST COMPLETE: FAILURES={} (timeout)", failures + 1);
					client.stop();
				}
			} catch (Throwable t) {
				fail("selftest tick", t);
			}
		}

		private void stepFunnel(Minecraft client) {
			try {
				var chat = client.gui.hud.getChat();
				int before = ChatStore.INSTANCE.entries().size();
				chat.addClientSystemMessage(Component.literal("[selftest] client system message"));
				chat.addServerSystemMessage(Component.translatable("multiplayer.player.joined", Component.literal("SelfTester")));
				chat.addServerSystemMessage(Component.literal("Joiner left the game"));
				chat.addPlayerMessage(Component.literal("<Steve> hello world"), null, null);
				// decorated player chat without any recognizable format must still
				// land in the Chat folder (player-pipeline source, not heuristics)
				chat.addPlayerMessage(Component.literal("Steve » hello again"), null, null);
				chat.addPlayerMessage(Component.translatable("commands.message.display.incoming",
						Component.literal("Alex"), Component.literal("psst")), null, null);
				int after = ChatStore.INSTANCE.entries().size();
				check("captured 6 messages", after >= before + 6);
				check("dm partner detected", ChatStore.INSTANCE.hasDmPartner("Alex"));
				check("chat folder populated",
						ChatStore.INSTANCE.entries().stream().anyMatch(e -> e.folder == Folder.CHAT));
				check("player-pipeline chat classified as chat", ChatStore.INSTANCE.entries().stream()
						.anyMatch(e -> e.folder == Folder.CHAT && e.text.contains("hello again")));
				check("dm folder populated",
						ChatStore.INSTANCE.entries().stream().anyMatch(e -> e.folder == Folder.DM));
				check("server folder populated",
						ChatStore.INSTANCE.entries().stream().anyMatch(e -> e.folder == Folder.SERVER));
				check("joins folder populated (translation key)",
						ChatStore.INSTANCE.entries().stream()
								.anyMatch(e -> e.folder == Folder.JOINS && e.text.contains("SelfTester")));
				check("joins folder populated (regex)",
						ChatStore.INSTANCE.entries().stream()
								.anyMatch(e -> e.folder == Folder.JOINS && e.text.contains("Joiner")));
			} catch (Throwable t) {
				fail("funnel", t);
			}
		}

		private void stepFolders() {
			try {
				for (Folder folder : Folder.values()) {
					ChatStore.INSTANCE.switchView(folder, null);
				}
				for (String partner : ChatStore.INSTANCE.dmPartners()) {
					ChatStore.INSTANCE.switchView(Folder.DM, partner);
				}
				ChatStore.INSTANCE.switchView(Folder.ALL, null);
				check("folder switching survived", true);
				ChatFileLogger.readAll();
				check("log reading survived", true);
			} catch (Throwable t) {
				fail("folders", t);
			}
		}

		private void stepShortcuts() {
			try {
				String shrug = Shortcuts.expand("{shrug}");
				check("shortcut expansion", shrug.contains("¯"));
				check("unknown token untouched", Shortcuts.expand("{nope_token}").equals("{nope_token}"));
			} catch (Throwable t) {
				fail("shortcuts", t);
			}
		}

		/** A rule with a melody, for the edit-screen and tune-maker steps. */
		private MentionRule melodyRule() {
			MentionRule rule = new MentionRule("tunetest", "minecraft:block.note_block.pling", 1.0f, 1.0f);
			rule.tune = MentionRule.DEFAULT_MELODY.clone();
			rule.tuneInstrument = "minecraft:block.note_block.bell";
			rule.tuneTempo = 150;
			return rule;
		}

		private void stepInstruments() {
			try {
				int resolvable = 0;
				for (String id : Instruments.ORDERED) {
					if (Sounds.exists(id)) resolvable++;
				}
				check("note-block instruments resolve", resolvable >= 10);
				check("instrument list never empty", !Instruments.available().isEmpty());
				check("note names", Instruments.noteName(0).contains("3")
						&& Instruments.noteName(12).contains("4") && Instruments.noteName(24).contains("5"));
			} catch (Throwable t) {
				fail("instruments", t);
			}
		}

		private void stepTuneStart() {
			try {
				MentionRule rule = melodyRule();
				rule.tune = new int[] {12, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
				rule.tuneTempo = 100;
				TunePlayer.play(rule.tuneInstrument, rule.tune, 1.0f, rule.tuneTempo);
				check("tune scheduled", TunePlayer.isPlaying());
			} catch (Throwable t) {
				fail("tune start", t);
			}
		}

		/** Runs 15 ticks (750 ms) after stepTuneStart: both notes must have fired. */
		private void stepTuneCheck() {
			try {
				check("tune notes fired", TunePlayer.debugPlayedCount() >= 2);
				check("tune finished", !TunePlayer.isPlaying() || TunePlayer.progressSteps() >= 2);
			} catch (Throwable t) {
				fail("tune check", t);
			}
		}

		private void stepConfigJson() {
			try {
				com.google.gson.Gson gson = new com.google.gson.Gson();
				MentionRule rule = melodyRule();
				String json = gson.toJson(rule);
				MentionRule back = gson.fromJson(json, MentionRule.class);
				check("melody survives config round trip",
						back.hasTune() && back.tuneNoteCount() == rule.tuneNoteCount()
								&& back.tuneInstrument.equals(rule.tuneInstrument)
								&& back.tuneTempo == rule.tuneTempo);
				MentionRule plain = new MentionRule("p", "minecraft:block.note_block.pling", 1.0f, 1.0f);
				MentionRule plainBack = gson.fromJson(gson.toJson(plain), MentionRule.class);
				check("sound rule survives config round trip",
						!plainBack.hasTune() && plainBack.tune == null);
			} catch (Throwable t) {
				fail("config json", t);
			}
		}

		private void check(String name, boolean ok) {
			if (ok) {
				LOGGER.info("CHATTERBOX SELFTEST OK: {}", name);
			} else {
				failures++;
				LOGGER.error("CHATTERBOX SELFTEST FAILED: {}", name);
			}
		}

		private void fail(String name, Throwable t) {
			failures++;
			LOGGER.error("CHATTERBOX SELFTEST FAILED: {} ({})", name, t.toString(), t);
		}
	}
}

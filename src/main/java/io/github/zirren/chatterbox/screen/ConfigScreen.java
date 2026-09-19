package io.github.zirren.chatterbox.screen;

import java.util.List;
import java.util.function.Supplier;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import io.github.zirren.chatterbox.Lang;
import io.github.zirren.chatterbox.config.Config;

/**
 * ModMenu / keybind settings hub: vanilla-style categories (like the game's
 * own Options screen), each opening one focused settings page.
 */
public class ConfigScreen extends ChatterBoxScreen {

	public ConfigScreen(Screen parent) {
		super(Component.translatable("chatterbox.title"), parent);
	}

	@Override
	protected void init() {
		int col1 = this.width / 2 - 155;
		int col2 = this.width / 2 + 5;
		int y = 40;

		addRenderableWidget(hub("chatterbox.config.chat", () -> chatSettings(this), col1, y, 150));
		addRenderableWidget(hub("chatterbox.config.layout", () -> layoutSettings(this), col2, y, 150));
		y += 21;
		addRenderableWidget(hub("chatterbox.config.folders", () -> new FoldersScreen(this), col1, y, 150));
		addRenderableWidget(hub("chatterbox.config.dm", () -> dmSettings(this), col2, y, 150));
		y += 21;
		addRenderableWidget(hub("chatterbox.config.sounds", () -> soundSettings(this), col1, y, 150));
		addRenderableWidget(hub("chatterbox.config.shortcuts", () -> new ShortcutsScreen(this), col2, y, 150));
		y += 21;
		// odd count → last one centered, like vanilla option screens
		addRenderableWidget(hub("chatterbox.config.logging", () -> loggingSettings(this),
				this.width / 2 - 77, y, 154));

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.done"), b -> onClose())
				.pos(this.width / 2 - 75, this.height - 24).size(150, 20).build());
	}

	private Button hub(String key, Supplier<Screen> create, int x, int y, int width) {
		Button button = Button.builder(Component.literal(Lang.tr(key) + "…"), b -> {
			if (this.minecraft != null) {
				this.minecraft.gui.setScreen(create.get());
			}
		}).pos(x, y).size(width, 20).build();
		button.setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
		return button;
	}

	// ------------------------------------------------------------------
	// Category pages
	// ------------------------------------------------------------------

	/** Chat behaviour: repeats, drafts, multiline send, command handling. */
	public static SimpleOptionsScreen chatSettings(Screen parent) {
		Config cfg = Config.get();
		return new SimpleOptionsScreen("chatterbox.config.chat.title", parent, List.of(
				new SimpleOptionsScreen.Option("chatterbox.config.compress_repeats", "chatterbox.config.compress_repeats.tooltip",
						() -> onOff(cfg.compressRepeats), () -> cfg.compressRepeats = !cfg.compressRepeats),
				new SimpleOptionsScreen.Option("chatterbox.config.drafts", "chatterbox.config.drafts.tooltip",
						() -> onOff(cfg.drafts), () -> cfg.drafts = !cfg.drafts),
				new SimpleOptionsScreen.Option("chatterbox.config.queue_mode", "chatterbox.config.queue_mode.tooltip",
						() -> queueValue(), () -> cfg.joinQueuedLines = !cfg.joinQueuedLines),
				new SimpleOptionsScreen.Option("chatterbox.config.command_folder", "chatterbox.config.command_folder.tooltip",
						() -> onOff(cfg.commandFeedbackFolder), () -> cfg.commandFeedbackFolder = !cfg.commandFeedbackFolder),
				new SimpleOptionsScreen.Option("chatterbox.config.expand_in_commands", "chatterbox.config.expand_in_commands.tooltip",
						() -> onOff(cfg.expandShortcutsInCommands), () -> cfg.expandShortcutsInCommands = !cfg.expandShortcutsInCommands)),
				List.of());
	}

	/** Timestamps & layout. */
	public static SimpleOptionsScreen layoutSettings(Screen parent) {
		Config cfg = Config.get();
		return new SimpleOptionsScreen("chatterbox.config.layout.title", parent, List.of(
				new SimpleOptionsScreen.Option("chatterbox.config.timestamps", "chatterbox.config.timestamps.tooltip",
						() -> timestampValue(), ConfigScreen::cycleTimestamps),
				new SimpleOptionsScreen.Option("chatterbox.config.two_line", "chatterbox.config.two_line.tooltip",
						() -> onOff(cfg.twoLineLayout), () -> cfg.twoLineLayout = !cfg.twoLineLayout)),
				List.of());
	}

	/** Direct messages: colors, whisper command, partner list. */
	public static SimpleOptionsScreen dmSettings(Screen parent) {
		Config cfg = Config.get();
		return new SimpleOptionsScreen("chatterbox.config.dm.title", parent, List.of(
				new SimpleOptionsScreen.Option("chatterbox.config.dm_normal", "chatterbox.config.dm_normal.tooltip",
						() -> onOff(cfg.dmNormalColor), () -> cfg.dmNormalColor = !cfg.dmNormalColor),
				new SimpleOptionsScreen.Option("chatterbox.config.whisper_command", "chatterbox.config.whisper_command.tooltip",
						() -> "/" + cfg.whisperCommand, ConfigScreen::cycleWhisper)),
				List.of(new SimpleOptionsScreen.FooterButton("chatterbox.config.partners_entry",
						screen -> new PartnersScreen(screen))));
	}

	/** Notification sounds: picker scope + mention rule list. */
	public static SimpleOptionsScreen soundSettings(Screen parent) {
		Config cfg = Config.get();
		return new SimpleOptionsScreen("chatterbox.config.sounds.title", parent, List.of(
				new SimpleOptionsScreen.Option("chatterbox.config.sound_picker_all", "chatterbox.config.sound_picker_all.tooltip",
						() -> Lang.tr(cfg.soundPickerAllSounds
								? "chatterbox.config.sound_picker_all.all"
								: "chatterbox.config.sound_picker_all.noteblocks"),
						() -> cfg.soundPickerAllSounds = !cfg.soundPickerAllSounds)),
				List.of(new SimpleOptionsScreen.FooterButton("chatterbox.config.rules_entry",
						screen -> new MentionRulesScreen(screen))));
	}

	/** Logging & search. */
	public static SimpleOptionsScreen loggingSettings(Screen parent) {
		Config cfg = Config.get();
		return new SimpleOptionsScreen("chatterbox.config.logging.title", parent, List.of(
				new SimpleOptionsScreen.Option("chatterbox.config.chat_log", "chatterbox.config.chat_log.tooltip",
						() -> onOff(cfg.chatLog), () -> cfg.chatLog = !cfg.chatLog),
				new SimpleOptionsScreen.Option("chatterbox.config.cross_session", "chatterbox.config.cross_session.tooltip",
						() -> onOff(cfg.crossSessionSearch), () -> cfg.crossSessionSearch = !cfg.crossSessionSearch)),
				List.of());
	}

	// ------------------------------------------------------------------
	// Value helpers
	// ------------------------------------------------------------------

	static String onOff(boolean value) {
		return Lang.tr(value ? "chatterbox.on" : "chatterbox.off");
	}

	private static String timestampValue() {
		Config cfg = Config.get();
		if (!cfg.timestamps) return Lang.tr("chatterbox.config.timestamps.none");
		return Lang.tr(cfg.timestampSeconds ? "chatterbox.config.timestamps.long" : "chatterbox.config.timestamps.short");
	}

	private static void cycleTimestamps() {
		Config cfg = Config.get();
		if (!cfg.timestamps) {
			cfg.timestamps = true;
			cfg.timestampSeconds = false;
		} else if (!cfg.timestampSeconds) {
			cfg.timestampSeconds = true;
		} else {
			cfg.timestamps = false;
		}
	}

	private static String queueValue() {
		return Lang.tr(Config.get().joinQueuedLines ? "chatterbox.config.queue_mode.joined"
				: "chatterbox.config.queue_mode.lines");
	}

	private static void cycleWhisper() {
		Config cfg = Config.get();
		cfg.whisperCommand = switch (cfg.whisperCommand) {
			case "msg" -> "w";
			case "w" -> "tell";
			default -> "msg";
		};
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
		String version = "ChatterBox v" + FabricLoader.getInstance().getModContainer("chatterbox")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		g.text(this.font, version, 4, this.height - 10, 0xFF707070, false);
		String mc = Lang.tr("chatterbox.config.mc", "26.2 – 26.3");
		g.text(this.font, mc, this.width - this.font.width(mc) - 4, this.height - 10, 0xFF707070, false);
	}
}

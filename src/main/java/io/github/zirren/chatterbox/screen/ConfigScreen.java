package io.github.zirren.chatterbox.screen;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.I18n;

import io.github.zirren.chatterbox.config.Config;

/**
 * ModMenu / keybind settings screen: every ChatterBox option as vanilla-style
 * cycle buttons, plus entry buttons for the mention rules, shortcut editor,
 * folder visibility and DM partner lists.
 */
public class ConfigScreen extends ChatterBoxScreen {

	public ConfigScreen(net.minecraft.client.gui.screens.Screen parent) {
		super(Component.translatable("chatterbox.title"), parent);
	}

	@Override
	protected void init() {
		Config cfg = Config.get();

		int col1 = this.width / 2 - 155;
		int col2 = this.width / 2 + 5;
		int y = 26;

		// --- cycle rows (two columns) -----------------------------------
		List<CycleDef> defs = List.of(
				new CycleDef("chatterbox.config.timestamps", () -> timestampValue(), () -> cycleTimestamps()),
				new CycleDef("chatterbox.config.two_line", () -> onOff(cfg.twoLineLayout),
						() -> cfg.twoLineLayout = !cfg.twoLineLayout),
				new CycleDef("chatterbox.config.compress_repeats", () -> onOff(cfg.compressRepeats),
						() -> cfg.compressRepeats = !cfg.compressRepeats),
				new CycleDef("chatterbox.config.dm_normal", () -> onOff(cfg.dmNormalColor),
						() -> cfg.dmNormalColor = !cfg.dmNormalColor),
				new CycleDef("chatterbox.config.drafts", () -> onOff(cfg.drafts), () -> cfg.drafts = !cfg.drafts),
				new CycleDef("chatterbox.config.unread_badges", () -> onOff(cfg.unreadBadges),
						() -> cfg.unreadBadges = !cfg.unreadBadges),
				new CycleDef("chatterbox.config.queue_mode", () -> queueValue(), () -> cfg.joinQueuedLines = !cfg.joinQueuedLines),
				new CycleDef("chatterbox.config.whisper_command", () -> "/" + cfg.whisperCommand,
						() -> cycleWhisper()),
				new CycleDef("chatterbox.config.command_folder", () -> onOff(cfg.commandFeedbackFolder),
						() -> cfg.commandFeedbackFolder = !cfg.commandFeedbackFolder),
				new CycleDef("chatterbox.config.expand_in_commands", () -> onOff(cfg.expandShortcutsInCommands),
						() -> cfg.expandShortcutsInCommands = !cfg.expandShortcutsInCommands),
				new CycleDef("chatterbox.config.chat_log", () -> onOff(cfg.chatLog), () -> cfg.chatLog = !cfg.chatLog),
				new CycleDef("chatterbox.config.cross_session", () -> onOff(cfg.crossSessionSearch),
						() -> cfg.crossSessionSearch = !cfg.crossSessionSearch),
				new CycleDef("chatterbox.config.sound_picker_all", () -> I18n.get(Config.get().soundPickerAllSounds
						? "chatterbox.config.sound_picker_all.all"
						: "chatterbox.config.sound_picker_all.noteblocks"),
						() -> cfg.soundPickerAllSounds = !cfg.soundPickerAllSounds));

		for (int i = 0; i < defs.size(); i++) {
			int x = (i % 2 == 0) ? col1 : col2;
			if (i > 0 && i % 2 == 0) y += 21;
			CycleDef def = defs.get(i);
			addRenderableWidget(cycleButton(def, x, y));
		}

		// --- sub screens --------------------------------------------------
		int y2 = y + 30;
		addRenderableWidget(subButton("chatterbox.config.sounds", () -> open(new MentionRulesScreen(this)), col1, y2));
		addRenderableWidget(subButton("chatterbox.config.shortcuts", () -> open(new ShortcutsScreen(this)), col2, y2));
		addRenderableWidget(subButton("chatterbox.config.folders", () -> open(new FoldersScreen(this)), col1, y2 + 21));
		addRenderableWidget(subButton("chatterbox.config.dm", () -> open(new PartnersScreen(this)), col2, y2 + 21));

		// --- done ---------------------------------------------------------
		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.done"), b -> onClose())
				.pos(this.width / 2 - 75, this.height - 24).size(150, 20).build());
	}

	private Button cycleButton(CycleDef def, int x, int y) {
		Button b = Button.builder(cycleLabel(def.labelKey(), def.value().get()), btn -> {
			def.cycle().run();
			Config.get().save();
			// force redisplay of the whole chat with new formatting
			io.github.zirren.chatterbox.chat.ChatDisplay.refresh();
			btn.setMessage(cycleLabel(def.labelKey(), def.value().get()));
		}).pos(x, y).size(150, 20).build();
		return b;
	}

	private Button subButton(String key, Runnable open, int x, int y) {
		return Button.builder(Component.literal(I18n.get(key) + "…"), b -> open.run())
				.pos(x, y).size(150, 20).build();
	}

	private void open(net.minecraft.client.gui.screens.Screen screen) {
		this.minecraft.gui.setScreen(screen);
	}

	private static String onOff(boolean value) {
		return I18n.get(value ? "chatterbox.on" : "chatterbox.off");
	}

	private static String timestampValue() {
		Config cfg = Config.get();
		if (!cfg.timestamps) return I18n.get("chatterbox.config.timestamps.none");
		return I18n.get(cfg.timestampSeconds ? "chatterbox.config.timestamps.long" : "chatterbox.config.timestamps.short");
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
		return I18n.get(Config.get().joinQueuedLines ? "chatterbox.config.queue_mode.joined"
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

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
	}

	private record CycleDef(String labelKey, java.util.function.Supplier<String> value, Runnable cycle) {
	}
}

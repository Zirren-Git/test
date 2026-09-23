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
import io.github.zirren.chatterbox.chat.ChatDisplay;
import io.github.zirren.chatterbox.config.Config;

/**
 * ModMenu / keybind settings screen with a Sodium-style tab strip on the
 * left and the selected category's options on the right. Complex editors
 * (folders, shortcuts, mention rules, DM partners) open from footer buttons
 * inside their tab.
 */
public class ConfigScreen extends ChatterBoxScreen {

	/** One tab: strip label + the options and footer buttons of its page. */
	private record Tab(String labelKey, @org.jspecify.annotations.Nullable String tooltipKey,
			Supplier<List<SimpleOptionsScreen.Option>> options,
			Supplier<List<SimpleOptionsScreen.FooterButton>> footer) {
	}

	private static final List<Tab> TABS = List.of(
			new Tab("chatterbox.tab.chat", "chatterbox.config.chat.tooltip",
					ConfigScreen::chatOptions, ConfigScreen::noFooter),
			new Tab("chatterbox.tab.layout", "chatterbox.config.layout.tooltip",
					ConfigScreen::layoutOptions, ConfigScreen::noFooter),
			new Tab("chatterbox.tab.folders", "chatterbox.config.folders.tooltip",
					ConfigScreen::folderOptions,
					() -> List.of(new SimpleOptionsScreen.FooterButton("chatterbox.config.folders_open",
							screen -> new FoldersScreen(screen)))),
			new Tab("chatterbox.tab.dm", "chatterbox.config.dm.tooltip",
					ConfigScreen::dmOptions,
					() -> List.of(new SimpleOptionsScreen.FooterButton("chatterbox.config.partners_entry",
							screen -> new PartnersScreen(screen)))),
			new Tab("chatterbox.tab.groups", "chatterbox.config.groups.tooltip",
					ConfigScreen::noOptions,
					() -> List.of(new SimpleOptionsScreen.FooterButton("chatterbox.config.groups_open",
							screen -> new GroupsScreen(screen)))),
			new Tab("chatterbox.tab.sounds", "chatterbox.config.sounds.tooltip",
					ConfigScreen::soundOptions,
					() -> List.of(new SimpleOptionsScreen.FooterButton("chatterbox.config.rules_entry",
							screen -> new MentionRulesScreen(screen)))),
			new Tab("chatterbox.tab.shortcuts", "chatterbox.config.shortcuts.tooltip",
					ConfigScreen::noOptions,
					() -> List.of(new SimpleOptionsScreen.FooterButton("chatterbox.config.shortcuts_open",
							screen -> new ShortcutsScreen(screen)))),
			new Tab("chatterbox.tab.logging", "chatterbox.config.logging.tooltip",
					ConfigScreen::loggingOptions, ConfigScreen::noFooter));

	private static final int TAB_X = 6;
	private static final int TAB_W = 104;
	private static final int TAB_Y = 32;
	private static final int TAB_H = 21;

	private int activeTab;

	public ConfigScreen(Screen parent) {
		super(Component.translatable("chatterbox.title"), parent);
	}

	@Override
	protected void init() {
		// --- left tab strip -------------------------------------------
		for (int i = 0; i < TABS.size(); i++) {
			final int index = i;
			Tab tab = TABS.get(i);
			Button button = Button.builder(Component.literal(Lang.tr(tab.labelKey())), b -> {
				if (activeTab != index) {
					activeTab = index;
					this.rebuildWidgets();
				}
			}).pos(TAB_X, TAB_Y + i * TAB_H).size(TAB_W, 20).build();
			if (tab.tooltipKey() != null) {
				button.setTooltip(Tooltip.create(Component.translatable(tab.tooltipKey())));
			}
			addRenderableWidget(button);
		}

		// --- right content pane ----------------------------------------
		Tab tab = TABS.get(activeTab);
		int contentX = TAB_X + TAB_W + 12;
		int contentW = this.width - contentX - 6;
		boolean twoCols = contentW >= 312;
		int col1;
		int col2 = 0;
		if (twoCols) {
			col1 = this.width - 6 - 305;
			col2 = col1 + 155;
		} else {
			col1 = contentX + Math.max(0, (contentW - Math.min(310, contentW)) / 2);
		}

		List<SimpleOptionsScreen.Option> options = tab.options().get();
		int y = 34;
		for (int i = 0; i < options.size(); i++) {
			int x = twoCols ? (i % 2 == 0 ? col1 : col2) : col1;
			if (i > 0 && (twoCols ? i % 2 == 0 : true)) {
				y += 21;
			}
			addRenderableWidget(optionButton(options.get(i), x, y, twoCols ? 150 : Math.min(310, contentW)));
		}

		List<SimpleOptionsScreen.FooterButton> footer = tab.footer().get();
		if (!options.isEmpty() || !footer.isEmpty()) {
			y += 31;
		}
		for (int i = 0; i < footer.size(); i++) {
			int x = twoCols ? (i % 2 == 0 ? col1 : col2) : col1;
			if (i > 0 && (twoCols ? i % 2 == 0 : true)) {
				y += 21;
			}
			SimpleOptionsScreen.FooterButton entry = footer.get(i);
			addRenderableWidget(Button.builder(Component.literal(Lang.tr(entry.labelKey()) + "…"),
							b -> open(entry.factory().apply(this)))
					.pos(x, y).size(twoCols ? 150 : Math.min(310, contentW), 20).build());
		}

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.done"), b -> onClose())
				.pos(this.width / 2 - 75, this.height - 24).size(150, 20).build());
	}

	private void open(Screen screen) {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(screen);
		}
	}

	private Button optionButton(SimpleOptionsScreen.Option option, int x, int y, int width) {
		Button button = Button.builder(cycleLabel(option.labelKey(), option.value().get()), btn -> {
			option.cycle().run();
			Config.get().save();
			// force redisplay of the whole chat with the new formatting
			ChatDisplay.refresh();
			btn.setMessage(cycleLabel(option.labelKey(), option.value().get()));
		}).pos(x, y).size(width, 20).build();
		if (option.tooltipKey() != null) {
			button.setTooltip(Tooltip.create(Component.translatable(option.tooltipKey())));
		}
		return button;
	}

	// ------------------------------------------------------------------
	// Tab contents (also used by the standalone category screens)
	// ------------------------------------------------------------------

	private static List<SimpleOptionsScreen.Option> noOptions() {
		return List.of();
	}

	private static List<SimpleOptionsScreen.FooterButton> noFooter() {
		return List.of();
	}

	/** Chat behaviour: repeats, drafts, multiline send, command handling. */
	public static List<SimpleOptionsScreen.Option> chatOptions() {
		Config cfg = Config.get();
		return List.of(
				new SimpleOptionsScreen.Option("chatterbox.config.compress_repeats", "chatterbox.config.compress_repeats.tooltip",
						() -> onOff(cfg.compressRepeats), () -> cfg.compressRepeats = !cfg.compressRepeats),
				new SimpleOptionsScreen.Option("chatterbox.config.drafts", "chatterbox.config.drafts.tooltip",
						() -> onOff(cfg.drafts), () -> cfg.drafts = !cfg.drafts),
				new SimpleOptionsScreen.Option("chatterbox.config.queue_mode", "chatterbox.config.queue_mode.tooltip",
						() -> queueValue(), () -> cfg.joinQueuedLines = !cfg.joinQueuedLines),
				new SimpleOptionsScreen.Option("chatterbox.config.command_folder", "chatterbox.config.command_folder.tooltip",
						() -> onOff(cfg.commandFeedbackFolder), () -> cfg.commandFeedbackFolder = !cfg.commandFeedbackFolder),
				new SimpleOptionsScreen.Option("chatterbox.config.expand_in_commands", "chatterbox.config.expand_in_commands.tooltip",
						() -> onOff(cfg.expandShortcutsInCommands), () -> cfg.expandShortcutsInCommands = !cfg.expandShortcutsInCommands));
	}

	/** Timestamps, layout and message type tags. */
	public static List<SimpleOptionsScreen.Option> layoutOptions() {
		Config cfg = Config.get();
		return List.of(
				new SimpleOptionsScreen.Option("chatterbox.config.timestamps", "chatterbox.config.timestamps.tooltip",
						() -> timestampValue(), ConfigScreen::cycleTimestamps),
				new SimpleOptionsScreen.Option("chatterbox.config.two_line", "chatterbox.config.two_line.tooltip",
						() -> onOff(cfg.twoLineLayout), () -> cfg.twoLineLayout = !cfg.twoLineLayout),
				new SimpleOptionsScreen.Option("chatterbox.config.type_tags", "chatterbox.config.type_tags.tooltip",
						() -> onOff(cfg.typeTags), () -> cfg.typeTags = !cfg.typeTags));
	}

	/** Folder tabs: unread counters (+ the folder visibility editor). */
	public static List<SimpleOptionsScreen.Option> folderOptions() {
		Config cfg = Config.get();
		return List.of(
				new SimpleOptionsScreen.Option("chatterbox.config.unread_badges", "chatterbox.config.unread_badges.tooltip",
						() -> onOff(cfg.unreadBadges), () -> cfg.unreadBadges = !cfg.unreadBadges));
	}

	/** Direct messages: colors, whisper command. */
	public static List<SimpleOptionsScreen.Option> dmOptions() {
		Config cfg = Config.get();
		return List.of(
				new SimpleOptionsScreen.Option("chatterbox.config.dm_normal", "chatterbox.config.dm_normal.tooltip",
						() -> onOff(cfg.dmNormalColor), () -> cfg.dmNormalColor = !cfg.dmNormalColor),
				new SimpleOptionsScreen.Option("chatterbox.config.whisper_command", "chatterbox.config.whisper_command.tooltip",
						() -> "/" + cfg.whisperCommand, ConfigScreen::cycleWhisper));
	}

	/** Notification sounds: picker scope. */
	public static List<SimpleOptionsScreen.Option> soundOptions() {
		Config cfg = Config.get();
		return List.of(
				new SimpleOptionsScreen.Option("chatterbox.config.sound_picker_all", "chatterbox.config.sound_picker_all.tooltip",
						() -> Lang.tr(cfg.soundPickerAllSounds
								? "chatterbox.config.sound_picker_all.all"
								: "chatterbox.config.sound_picker_all.noteblocks"),
						() -> cfg.soundPickerAllSounds = !cfg.soundPickerAllSounds));
	}

	/** Logging & search. */
	public static List<SimpleOptionsScreen.Option> loggingOptions() {
		Config cfg = Config.get();
		return List.of(
				new SimpleOptionsScreen.Option("chatterbox.config.chat_log", "chatterbox.config.chat_log.tooltip",
						() -> onOff(cfg.chatLog), () -> cfg.chatLog = !cfg.chatLog),
				new SimpleOptionsScreen.Option("chatterbox.config.cross_session", "chatterbox.config.cross_session.tooltip",
						() -> onOff(cfg.crossSessionSearch), () -> cfg.crossSessionSearch = !cfg.crossSessionSearch));
	}

	// Standalone category screens (used by the dev selftest) --------------

	public static SimpleOptionsScreen chatSettings(Screen parent) {
		return new SimpleOptionsScreen("chatterbox.config.chat.title", parent, chatOptions(), noFooter());
	}

	public static SimpleOptionsScreen layoutSettings(Screen parent) {
		return new SimpleOptionsScreen("chatterbox.config.layout.title", parent, layoutOptions(), noFooter());
	}

	public static SimpleOptionsScreen dmSettings(Screen parent) {
		return new SimpleOptionsScreen("chatterbox.config.dm.title", parent, dmOptions(),
				List.of(new SimpleOptionsScreen.FooterButton("chatterbox.config.partners_entry",
						screen -> new PartnersScreen(screen))));
	}

	public static SimpleOptionsScreen soundSettings(Screen parent) {
		return new SimpleOptionsScreen("chatterbox.config.sounds.title", parent, soundOptions(),
				List.of(new SimpleOptionsScreen.FooterButton("chatterbox.config.rules_entry",
						screen -> new MentionRulesScreen(screen))));
	}

	public static SimpleOptionsScreen loggingSettings(Screen parent) {
		return new SimpleOptionsScreen("chatterbox.config.logging.title", parent, loggingOptions(), noFooter());
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

		// active-tab highlight: soft fill + bright left accent bar
		int y = TAB_Y + activeTab * TAB_H;
		g.fill(TAB_X - 2, y - 2, TAB_X + TAB_W + 2, y + 22, 0x35FFFFFF);
		g.fill(TAB_X - 2, y - 2, TAB_X, y + 22, 0xFFFFFFFF);

		// subtle divider between the tab strip and the content pane
		int divX = TAB_X + TAB_W + 6;
		g.fill(divX, TAB_Y - 8, divX + 1, this.height - 32, 0x28FFFFFF);

		String version = "ChatterBox v" + FabricLoader.getInstance().getModContainer("chatterbox")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		g.text(this.font, version, 4, this.height - 10, 0xFF707070, false);
		String mc = Lang.tr("chatterbox.config.mc", "26.2 – 26.3");
		g.text(this.font, mc, this.width - this.font.width(mc) - 4, this.height - 10, 0xFF707070, false);
	}
}

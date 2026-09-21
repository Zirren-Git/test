package io.github.zirren.chatterbox.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * ChatterBox configuration, stored as JSON in {@code config/chatterbox.json}.
 */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static Config instance;

	// --- Folders & tabs ---
	/** Folder keys (see {@code Folder#key}) that are hidden from the tab bar. */
	public Set<String> hiddenFolders = new LinkedHashSet<>();
	/** Show unread counters on tabs. */
	public boolean unreadBadges = true;

	// --- Timestamps ---
	/** Show timestamps in chat. */
	public boolean timestamps = true;
	/** Use HH:mm:ss instead of HH:mm. */
	public boolean timestampSeconds = false;
	/** Timestamp + sender on its own line; message indented below it. */
	public boolean twoLineLayout = false;
	/** Show colored type tags ([DM], [Sys], [Join]…) in the All folder. */
	public boolean typeTags = true;

	// --- Chat behaviour ---
	/** Compress consecutive identical messages into one entry with an (xN) counter. */
	public boolean compressRepeats = true;
	/** Render DMs with normal (white, non-italic) formatting inside the DM folder. */
	public boolean dmNormalColor = true;
	/** Keep the text you typed (and queued lines) when closing the chat screen. */
	public boolean drafts = true;
	/** Classify system messages arriving shortly after you run a command as command output. */
	public boolean commandFeedbackFolder = true;
	/** Command used when sending from a DM subfolder: msg, w or tell. */
	public String whisperCommand = "msg";
	/** Expand {shortcuts} inside commands starting with '/' as well. */
	public boolean expandShortcutsInCommands = true;
	/** Join queued lines into one message on Enter instead of sending them line by line. */
	public boolean joinQueuedLines = false;

	// --- Mention sounds ---
	/** Rules: word -> sound. */
	public List<MentionRule> mentionRules = defaultMentionRules();

	// --- Shortcuts ---
	/** Static text shortcuts ({shrug} etc.), user editable. */
	public List<Shortcut> shortcuts = ShortcutsDefaults.staticShortcuts();
	/** Enabled state of the dynamic (live value) shortcuts; key = token. */
	public Map<String, Boolean> dynamicShortcuts = ShortcutsDefaults.dynamicDefaults();

	// --- DM partners ---
	/** Known DM partners (auto-added on whispers, or manually). Order preserved. */
	public List<String> dmPartners = new ArrayList<>();

	// --- Sound picker ---
	/** Whether the sound picker lists all sounds instead of just note block instruments. */
	public boolean soundPickerAllSounds = false;

	// --- Logging & search ---
	/** Write chat history to logs/chatterbox/. */
	public boolean chatLog = true;
	/** Include previous sessions from the log files when searching. */
	public boolean crossSessionSearch = true;

	private static List<MentionRule> defaultMentionRules() {
		List<MentionRule> rules = new ArrayList<>();
		rules.add(new MentionRule("{you}", "minecraft:block.note_block.pling", 1.0f, 1.7f));
		return rules;
	}

	public static Config get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static Config load() {
		Path path = configPath();
		if (Files.exists(path)) {
			try {
				Config cfg = GSON.fromJson(Files.readString(path), Config.class);
				if (cfg != null) {
					cfg.sanitize();
					return cfg;
				}
			} catch (Exception e) {
				ChatterBoxLog.warn("Failed to read config, using defaults", e);
			}
		}
		Config cfg = new Config();
		cfg.sanitize();
		return cfg;
	}

	public void sanitize() {
		if (hiddenFolders == null) hiddenFolders = new LinkedHashSet<>();
		if (mentionRules == null) mentionRules = defaultMentionRules();
		if (shortcuts == null) shortcuts = ShortcutsDefaults.staticShortcuts();
		if (dynamicShortcuts == null) dynamicShortcuts = ShortcutsDefaults.dynamicDefaults();
		if (dmPartners == null) dmPartners = new ArrayList<>();
		if (whisperCommand == null) whisperCommand = "msg";
		whisperCommand = switch (whisperCommand.toLowerCase()) {
			case "w", "tell" -> whisperCommand.toLowerCase();
			default -> "msg";
		};
	}

	public void save() {
		try {
			Path path = configPath();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
		} catch (IOException e) {
			ChatterBoxLog.warn("Failed to save config", e);
		}
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("chatterbox.json");
	}

	public boolean isFolderHidden(String key) {
		return hiddenFolders.contains(key);
	}

	public void setFolderHidden(String key, boolean hidden) {
		if (hidden) {
			hiddenFolders.add(key);
		} else {
			hiddenFolders.remove(key);
		}
		save();
	}

	public void toggleDynamicShortcut(String token) {
		boolean now = !dynamicShortcuts.getOrDefault(token, Boolean.TRUE);
		dynamicShortcuts.put(token, now);
		save();
	}

	public boolean isDynamicShortcutEnabled(String token) {
		return dynamicShortcuts.getOrDefault(token, Boolean.TRUE);
	}

	private static final class ChatterBoxLog {
		static void warn(String msg, Exception e) {
			System.err.println("[ChatterBox] " + msg + ": " + e);
		}
	}
}

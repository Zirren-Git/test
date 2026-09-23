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
import org.jspecify.annotations.Nullable;

import io.github.zirren.chatterbox.chat.Folder;

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

	// --- Group chats ---
	/** Group chats with other ChatterBox players (see {@link GroupChat}). */
	public List<GroupChat> groups = new ArrayList<>();

	// --- Message sorting ---
	/** User-defined sorting rules; checked before the built-in detection. */
	public List<SortRule> sortRules = new ArrayList<>();

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
		if (groups == null) groups = new ArrayList<>();
		// normalize + dedupe groups by name (first one wins)
		List<GroupChat> cleanGroups = new ArrayList<>();
		for (GroupChat group : groups) {
			if (group == null) continue;
			group.name = GroupChat.normalizeName(group.name);
			if (group.name.isEmpty()) continue;
			if (group.members == null) group.members = new ArrayList<>();
			group.members.removeIf(m -> m == null || m.isBlank() || m.contains(","));
			boolean dup = false;
			for (GroupChat kept : cleanGroups) {
				if (kept.name.equals(group.name)) {
					dup = true;
					break;
				}
			}
			if (!dup) cleanGroups.add(group);
		}
		groups = cleanGroups;
		if (sortRules == null) sortRules = new ArrayList<>();
		sortRules.removeIf(r -> r == null || r.pattern == null || r.pattern.isBlank());
		for (SortRule rule : sortRules) {
			if (Folder.byKey(rule.folder) == null) rule.folder = "server";
		}
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

	// ------------------------------------------------------------------
	// Group chats
	// ------------------------------------------------------------------

	/** Finds a group by name (case-insensitive), or null. */
	public @Nullable GroupChat group(String name) {
		String key = GroupChat.normalizeName(name);
		for (GroupChat g : groups) {
			if (g.name.equals(key)) return g;
		}
		return null;
	}

	/** Inserts a group, or replaces the existing one with the same name. */
	public void putGroup(GroupChat group) {
		group.name = GroupChat.normalizeName(group.name);
		group.members.removeIf(m -> m == null || m.isBlank() || m.contains(","));
		for (int i = 0; i < groups.size(); i++) {
			if (groups.get(i).name.equals(group.name)) {
				groups.set(i, group);
				save();
				return;
			}
		}
		groups.add(group);
		save();
	}

	public void removeGroup(String name) {
		String key = GroupChat.normalizeName(name);
		groups.removeIf(g -> g.name.equals(key));
		save();
	}

	/**
	 * Makes sure a group with this name exists and has at least the sender as
	 * a member; when the incoming tag carried a roster, the roster wins (the
	 * sender's member list is the most recent one).
	 *
	 * @param roster member names without yourself (already filtered by the caller)
	 * @param sender who sent the tagged message (fallback when there is no roster)
	 */
	public GroupChat syncGroup(String name, List<String> roster, @Nullable String sender) {
		GroupChat group = group(name);
		if (group == null) {
			group = new GroupChat(name);
			groups.add(group);
		}
		List<String> wanted = new ArrayList<>();
		if (roster != null) {
			for (String m : roster) {
				if (m == null || m.isBlank()) continue;
				boolean dup = false;
				for (String w : wanted) {
					if (w.equalsIgnoreCase(m)) {
						dup = true;
						break;
					}
				}
				if (!dup) wanted.add(m);
			}
		}
		if (sender != null && !sender.isBlank()) {
			boolean has = false;
			for (String w : wanted) {
				if (w.equalsIgnoreCase(sender)) {
					has = true;
					break;
				}
			}
			if (!has) wanted.add(sender);
		}
		if (!wanted.isEmpty()) {
			group.members = wanted;
		} else if (group.members.isEmpty() && sender != null && !sender.isBlank()) {
			group.members.add(sender);
		}
		save();
		return group;
	}

	private static final class ChatterBoxLog {
		static void warn(String msg, Exception e) {
			System.err.println("[ChatterBox] " + msg + ": " + e);
		}
	}
}

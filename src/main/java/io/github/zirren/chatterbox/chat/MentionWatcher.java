package io.github.zirren.chatterbox.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;

import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.config.MentionRule;

/**
 * Watches incoming messages for mention rules and plays the mapped sounds.
 */
public final class MentionWatcher {
	private MentionWatcher() {
	}

	public static void check(ChatEntry entry) {
		Config config = Config.get();
		if (config.mentionRules.isEmpty()) return;

		String username = Minecraft.getInstance().getUser() != null
				? Minecraft.getInstance().getUser().getName() : null;

		List<MentionRule> matches = new ArrayList<>();
		for (MentionRule rule : config.mentionRules) {
			if (!rule.enabled || rule.word == null || rule.word.isEmpty()) continue;
			if (!watches(rule, entry.folder)) continue;
			String word = rule.word;
			if (word.equalsIgnoreCase("{you}") && username != null) {
				word = username;
			}
			if (matches(entry.text, word, rule.caseSensitive, rule.wholeWord)) {
				matches.add(rule);
			}
		}

		for (MentionRule rule : matches) {
			if (rule.hasTune()) {
				TunePlayer.play(rule.tuneInstrument, rule.tune, rule.volume,
						rule.tuneTempo <= 0 ? 200 : rule.tuneTempo);
			} else {
				Sounds.play(rule.sound, rule.pitch, rule.volume);
			}
		}
	}

	/** True if this rule listens to messages of the given folder. */
	private static boolean watches(MentionRule rule, Folder folder) {
		return switch (MentionRule.normalizeScope(rule.scope)) {
			case MentionRule.SCOPE_CHAT -> folder == Folder.CHAT;
			case MentionRule.SCOPE_DM -> folder == Folder.DM;
			case MentionRule.SCOPE_CHAT_DM -> folder == Folder.CHAT || folder == Folder.DM;
			default -> true;
		};
	}

	private static boolean matches(String text, String word, boolean caseSensitive, boolean wholeWord) {
		if (word.isEmpty()) return false;
		if (!caseSensitive) {
			text = text.toLowerCase(Locale.ROOT);
			word = word.toLowerCase(Locale.ROOT);
		}
		if (!text.contains(word)) return false;
		return !wholeWord || isWholeWord(text, word, caseSensitive);
	}

	private static boolean isWholeWord(String text, String word, boolean caseSensitive) {
		String t = caseSensitive ? text : text.toLowerCase(Locale.ROOT);
		String w = caseSensitive ? word : word.toLowerCase(Locale.ROOT);
		int idx = 0;
		while (true) {
			int i = t.indexOf(w, idx);
			if (i < 0) return false;
			boolean before = i == 0 || !isWordChar(t.charAt(i - 1));
			int end = i + w.length();
			boolean after = end >= t.length() || !isWordChar(t.charAt(end));
			if (before && after) return true;
			idx = i + 1;
		}
	}

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}
}

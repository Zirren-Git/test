package io.github.zirren.chatterbox.config;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.zirren.chatterbox.chat.Folder;

/**
 * A user-defined sorting rule: messages whose text matches {@code pattern}
 * go to {@code folder}. Rules are checked before the built-in detection, so
 * they can teach ChatterBox any server's custom formats; normal player chat
 * (the player-chat pipeline) is never re-routed.
 */
public class SortRule {

	/** Text to look for, or a regular expression when {@code regex} is true. */
	public String pattern = "";
	/** Target folder key (see {@link Folder#key}); unknown keys fall back to server. */
	public String folder = "server";
	/** Interpret the pattern as a case-insensitive regular expression. */
	public boolean regex = false;
	public boolean enabled = true;

	/** Compiled pattern cache (not persisted). */
	private transient Pattern compiled;

	public SortRule() {
	}

	public SortRule(String pattern, Folder folder, boolean regex) {
		this.pattern = pattern;
		this.folder = folder.key;
		this.regex = regex;
	}

	/** The target folder (server as the safe fallback for old/invalid configs). */
	public Folder folder() {
		Folder f = Folder.byKey(folder);
		return f == null ? Folder.SERVER : f;
	}

	/** True when this rule is active and matches the given message text. */
	public boolean matches(String text) {
		if (!enabled || pattern == null || pattern.isEmpty() || text == null) return false;
		if (!regex) {
			return text.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT));
		}
		try {
			if (compiled == null) {
				compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
			}
			return compiled.matcher(text).find();
		} catch (Throwable t) {
			// invalid pattern - never break message handling over it
			return false;
		}
	}

	/** @return a compile error message, or null when the pattern is valid. */
	public String regexError() {
		if (!regex) return null;
		try {
			Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
			return null;
		} catch (Throwable t) {
			return t.getMessage();
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof SortRule that)) return false;
		return Objects.equals(pattern, that.pattern)
				&& Objects.equals(folder, that.folder)
				&& regex == that.regex
				&& enabled == that.enabled;
	}

	@Override
	public int hashCode() {
		return Objects.hash(pattern, folder, regex, enabled);
	}
}

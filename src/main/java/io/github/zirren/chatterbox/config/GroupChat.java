package io.github.zirren.chatterbox.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A group chat: a named set of players, built on top of server whispers.
 *
 * <p>Sending to the group whispers every member individually, and each
 * message carries a small {@code [~name|Member1,Member2]} tag. Other
 * ChatterBox clients recognise the tag, hide it and thread the message into
 * the same group view — and the roster in the tag lets them join the group
 * automatically. Players without ChatterBox simply see the whisper with the
 * tag in front.</p>
 */
public class GroupChat {

	/** Tag prefix: {@code [~name]} or {@code [~name|roster]}. */
	private static final Pattern TAG = Pattern.compile("\\[~([a-z0-9_-]{1,16})(?:\\|([A-Za-z0-9_,-]{1,200}))?]");

	/** Group name: lowercase letters, digits, underscore and dash, at most 16 chars. */
	public String name = "";
	/** Member usernames — everyone except yourself. */
	public List<String> members = new ArrayList<>();

	public GroupChat() {
	}

	public GroupChat(String name) {
		this.name = normalizeName(name);
	}

	/** Lowercases and strips everything except [a-z0-9_-], capping at 16 chars. */
	public static String normalizeName(String name) {
		if (name == null) return "";
		String n = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
		return n.length() > 16 ? n.substring(0, 16) : n;
	}

	/** Quick check for a possible group tag (before running the regex). */
	public static boolean hasTag(String text) {
		return text != null && text.contains("[~");
	}

	/** A parsed {@code [~name|roster]} tag plus the remaining message text. */
	public record Tag(String name, List<String> roster, String text) {
	}

	/**
	 * Parses a leading group tag off a whisper text.
	 *
	 * @return the tag and the text after it, or null if the text has none.
	 */
	public static Tag parseTag(String content) {
		if (!hasTag(content) || content.charAt(0) != '[') return null;
		Matcher m = TAG.matcher(content);
		if (m.find() && m.start() == 0) {
			List<String> roster = new ArrayList<>();
			if (m.group(2) != null && !m.group(2).isEmpty()) {
				for (String s : m.group(2).split(",")) {
					if (!s.isEmpty()) roster.add(s);
				}
			}
			return new Tag(m.group(1), roster, content.substring(m.end()).trim());
		}
		return null;
	}

	/**
	 * Builds the tag prefix for an outgoing message. The roster is included
	 * when it keeps the tag compact, so recipients can join automatically.
	 */
	public static String buildTag(String name, List<String> roster) {
		String base = "[~" + normalizeName(name);
		if (roster == null || roster.isEmpty()) return base + "]";
		String joined = String.join(",", roster);
		if (base.length() + joined.length() + 2 <= 80) {
			return base + "|" + joined + "]";
		}
		return base + "]";
	}

	/** Replaces every group tag in a display string with a compact "#name". */
	public static String replaceTags(String text) {
		if (!hasTag(text)) return text;
		return TAG.matcher(text).replaceAll(" #$1");
	}

	@Override
	public String toString() {
		return "#" + name + " (" + members.size() + ")";
	}
}

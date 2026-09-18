package io.github.zirren.chatterbox.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default shortcut tables.
 *
 * <p>Static shortcuts are plain text replacements ({shrug} etc.). Dynamic
 * shortcuts evaluate live values ({pos}, {biome}, ...) and are resolved by
 * {@code Shortcuts}.</p>
 */
public final class ShortcutsDefaults {
	private ShortcutsDefaults() {
	}

	public static List<Shortcut> staticShortcuts() {
		List<Shortcut> list = new ArrayList<>();
		// kaomoji
		list.add(new Shortcut("shrug", "¯\\_(ツ)_/¯"));
		list.add(new Shortcut("tableflip", "(╯°□°)╯︵ ┻━┻"));
		list.add(new Shortcut("unflip", "┬─┬ ノ( ゜-゜ノ)"));
		list.add(new Shortcut("disapprove", "ಠ_ಠ"));
		list.add(new Shortcut("lenny", "( ͡° ͜ʖ ͡°)"));
		list.add(new Shortcut("cool", "(⌐■_■)"));
		list.add(new Shortcut("cry", "(╥﹏╥)"));
		list.add(new Shortcut("angry", "(╬ ಠ益ಠ)"));
		list.add(new Shortcut("bear", "ʕ•ᴥ•ʔ"));
		list.add(new Shortcut("cat", "(=^･ω･^=)"));
		list.add(new Shortcut("hug", "(づ｡◕‿‿◕｡)づ"));
		// unicode symbols
		list.add(new Shortcut("heart", "❤"));
		list.add(new Shortcut("star", "★"));
		list.add(new Shortcut("sparkle", "✦"));
		list.add(new Shortcut("skull", "☠"));
		list.add(new Shortcut("check", "✔"));
		list.add(new Shortcut("cross", "✘"));
		list.add(new Shortcut("music", "♪"));
		list.add(new Shortcut("note", "♫"));
		list.add(new Shortcut("bolt", "⚡"));
		list.add(new Shortcut("sun", "☀"));
		list.add(new Shortcut("moon", "☾"));
		list.add(new Shortcut("cloud", "☁"));
		list.add(new Shortcut("snow", "❄"));
		list.add(new Shortcut("flower", "✿"));
		list.add(new Shortcut("crown", "♛"));
		list.add(new Shortcut("diamond", "◆"));
		list.add(new Shortcut("sword", "⚔"));
		list.add(new Shortcut("pick", "⛏"));
		list.add(new Shortcut("up", "↑"));
		list.add(new Shortcut("down", "↓"));
		list.add(new Shortcut("left", "←"));
		list.add(new Shortcut("right", "→"));
		return list;
	}

	public static Map<String, Boolean> dynamicDefaults() {
		Map<String, Boolean> map = new LinkedHashMap<>();
		for (String token : DYNAMIC_TOKENS) {
			map.put(token, Boolean.TRUE);
		}
		return map;
	}

	/** Dynamic shortcut tokens and their descriptions (translation keys). */
	public static final String[] DYNAMIC_TOKENS = {
			"pos", "coords", "y", "dim", "facing", "biome", "hp", "food", "xp", "ping",
			"time", "date", "clock", "server", "player", "me", "held"
	};

	public static final Map<String, String> DYNAMIC_DESCRIPTIONS = descriptions();

	private static Map<String, String> descriptions() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("pos", "Your position [x, y, z]");
		map.put("coords", "Alias of {pos}");
		map.put("y", "Your y level");
		map.put("dim", "Your current dimension");
		map.put("facing", "The direction you are facing");
		map.put("biome", "The biome you are in");
		map.put("hp", "Your health");
		map.put("food", "Your hunger");
		map.put("xp", "Your experience level");
		map.put("ping", "Your latency");
		map.put("time", "The in-game time");
		map.put("date", "Today's date (real world)");
		map.put("clock", "The current time (real world)");
		map.put("server", "The server you are on");
		map.put("player", "Your username");
		map.put("me", "Alias of {player}");
		map.put("held", "The item you are holding");
		return map;
	}
}

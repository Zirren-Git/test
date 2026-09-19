package io.github.zirren.chatterbox.chat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.config.Shortcut;

/**
 * Expands {shortcut} tokens in outgoing text.
 *
 * <p>Static shortcuts come from the config; dynamic shortcuts evaluate live
 * values such as position or biome.</p>
 */
public final class Shortcuts {
	private static final Pattern TOKEN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

	private Shortcuts() {
	}

	/** Expands all enabled shortcuts in the text. Unknown tokens are left alone. */
	public static String expand(String text) {
		if (text == null || text.isEmpty() || !text.contains("{")) return text;
		Config config = Config.get();
		Map<String, String> statics = new java.util.HashMap<>();
		for (Shortcut shortcut : config.shortcuts) {
			if (shortcut.enabled && shortcut.token != null && !shortcut.token.isEmpty()) {
				statics.put(shortcut.token.toLowerCase(Locale.ROOT), shortcut.replacement);
			}
		}

		Matcher matcher = TOKEN.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String token = matcher.group(1).toLowerCase(Locale.ROOT);
			String replacement = statics.get(token);
			if (replacement == null && config.isDynamicShortcutEnabled(token)) {
				replacement = dynamic(token);
			}
			if (replacement == null) replacement = matcher.group(0);
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private static String dynamic(String token) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		switch (token) {
			case "pos", "coords" -> {
				if (player == null) return null;
				BlockPos pos = player.blockPosition();
				return "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
			}
			case "y" -> {
				if (player == null) return null;
				return String.valueOf(player.blockPosition().getY());
			}
			case "dim" -> {
				if (player == null) return null;
				Identifier id = player.level().dimension().identifier();
				return prettyDimension(id);
			}
			case "facing" -> {
				if (player == null) return null;
				Direction dir = player.getDirection();
				return dir.getName().toUpperCase(Locale.ROOT);
			}
			case "biome" -> {
				if (player == null || client.level == null) return null;
				BlockPos pos = player.blockPosition();
				var holder = client.level.getUncachedNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
				return holder.unwrapKey().map(key -> prettify(key.identifier().getPath())).orElse("unknown");
			}
			case "hp" -> {
				if (player == null) return null;
				return (int) Math.ceil(player.getHealth()) + "/" + (int) Math.ceil(player.getMaxHealth()) + " HP";
			}
			case "food" -> {
				if (player == null) return null;
				return player.getFoodData().getFoodLevel() + "/20 food";
			}
			case "xp" -> {
				if (player == null) return null;
				return "Lv " + player.experienceLevel;
			}
			case "ping" -> {
				if (player == null || client.getConnection() == null) return null;
				var info = client.getConnection().getPlayerInfo(player.getUUID());
				return info == null ? null : info.getLatency() + "ms";
			}
			case "time" -> {
				if (player == null) return null;
				long ticks = player.level().getLevelData().getDayTime() % 24000L;
				long hours = (ticks / 1000L + 6L) % 24L;
				long minutes = ticks % 1000L * 60L / 1000L;
				return String.format("%02d:%02d", hours, minutes);
			}
			case "date" -> {
				return LocalDateTime.now().format(DATE);
			}
			case "clock" -> {
				return LocalDateTime.now().format(CLOCK);
			}
			case "server" -> {
				if (client.getCurrentServer() != null) {
					return client.getCurrentServer().ip;
				}
				return player != null && player.level() != null && !player.level().isClientSide()
						? "local" : "singleplayer";
			}
			case "player", "me" -> {
				return client.getUser() != null ? client.getUser().getName() : null;
			}
			case "held" -> {
				if (player == null) return null;
				ItemStack held = player.getMainHandItem();
				return held.isEmpty() ? "(nothing)" : held.getHoverName().getString();
			}
			default -> {
				return null;
			}
		}
	}

	private static String prettyDimension(Identifier id) {
		if (id.getNamespace().equals("minecraft")) {
			return switch (id.getPath()) {
				case "overworld" -> "Overworld";
				case "the_nether" -> "Nether";
				case "the_end" -> "The End";
				default -> prettify(id.getPath());
			};
		}
		return id.toString();
	}

	private static String prettify(String path) {
		String[] parts = path.split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}
}

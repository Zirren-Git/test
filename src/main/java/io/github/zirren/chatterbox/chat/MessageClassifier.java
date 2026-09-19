package io.github.zirren.chatterbox.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.authlib.GameProfile;
import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Decides which folder an incoming message belongs to.
 *
 * <p>Messages that entered the chat hud through the player-chat pipeline are
 * always player chat (many servers decorate them beyond recognition, so the
 * pipeline origin is the only reliable signal). System messages are detected
 * via translation keys first, then regex heuristics for modded servers.</p>
 */
public final class MessageClassifier {
	// Fallback patterns for non-vanilla servers (Bukkit/Spigot/Paper etc.)
	private static final Pattern WHISPER_IN = Pattern.compile("^(.+?)\\s+whispers(?:\\s+to\\s+you)?:\\s*(.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern WHISPER_OUT = Pattern.compile("^You\\s+whisper(?:ed)?\\s+to\\s+(.+?):\\s*(.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern CHAT_LINE = Pattern.compile("^<([^>]{1,32})>\\s?(.*)$");
	private static final Pattern JOIN_LEAVE = Pattern.compile("^\\S{1,32}\\s+(joined|left|quit)\\s+the game", Pattern.CASE_INSENSITIVE);

	private static final Pattern[] DEATH_PATTERNS = {
			Pattern.compile("^\\S{1,32}\\s+(?:died|drowned|starved to death|froze to death|withered away|suffocated in a wall|experienced kinetic energy|went up in flames|went off with a bang|was struck by lightning|discovered the floor was lava|removed an elytra while flying|tried to swim in lava|walked into a cactus(?: while trying to escape .+)?|fell (?:from a high place|out of the water|into a patch of fire|into a magma block|too far and was finished by .+|between two blocks))", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^\\S{1,32}\\s+was (?:slain|shot|fireballed|pricked to death by a sweet berry bush|skewered by a falling stalactite|impaled on a stalagmite|stabbed to death by .+|squashed by .+|pummeled by .+|blown up by .+|burnt to a crisp(?: .*)?|killed by .+|doomed to fall(?: .*)?|smitten by .+|stung to death(?: .*)?|unable to resist the cut of .+|gutted by .+|expelled .+|finished off by .+|shot off a ladder by .+|blasted by .+|sniped by .+|squashed out of existence)", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^\\S{1,32}\\s+didn'?t want to live in the same world as .+", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^\\S{1,32}\\s+hit the ground too hard", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^\\S{1,32}\\s+fell out of the world", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^You (?:died|were slain|fell|drowned|froze|starved)", Pattern.CASE_INSENSITIVE)
	};

	private MessageClassifier() {
	}

	public record Result(Folder folder, @Nullable String dmPartner, @Nullable String sender, @Nullable String dmContent, boolean outgoing) {
	}

	/**
	 * @param component    the message component about to be displayed
	 * @param chatMessage  true if this arrived as a player chat message (signed chat event context)
	 * @param playerSource true if the chat hud received it through the player-chat pipeline
	 *                     (authoritative - the decorated component may not match any format)
	 * @param sender       sender profile for chat messages, if known
	 * @param commandSentAt epoch millis when the local player last sent a command, or 0
	 */
	public static Result classify(Component component, boolean chatMessage, boolean playerSource, @Nullable GameProfile sender,
			@Nullable String chatSenderName, long commandSentAt, boolean commandFeedbackEnabled) {
		String plain = component.getString();

		// 1. Player chat: signed-chat context or the hud's player pipeline.
		//    (Whisper keys are still checked - some servers route /msg through here.)
		if (chatMessage || playerSource) {
			if (component.getContents() instanceof TranslatableContents translatable
					&& translatable.getKey().startsWith("commands.message.display.")) {
				return whisper(translatable);
			}
			String name = sender != null ? sender.name()
					: (chatSenderName != null ? chatSenderName : chatLineName(plain));
			return new Result(Folder.CHAT, null, name, null, false);
		}

		// 2. Translation keys (vanilla / vanilla-like servers)
		if (component.getContents() instanceof TranslatableContents translatable) {
			String key = translatable.getKey();
			if (key.startsWith("commands.message.display.")) {
				return whisper(translatable);
			}
			if (key.startsWith("death.") || key.startsWith("multiplayer.player.died")) {
				return new Result(Folder.DEATH, null, argString(translatable, 0), null, false);
			}
			if (key.equals("multiplayer.player.joined") || key.equals("multiplayer.player.left")
					|| key.equals("multiplayer.player.quit")) {
				return new Result(Folder.JOINS, null, argString(translatable, 0), null, false);
			}
			if (key.startsWith("multiplayer.player.") || key.equals("chat.type.admin")
					|| key.equals("chat.type.announcement") || key.equals("chat.type.emote")
					|| key.startsWith("chat.type.advancement.")) {
				return new Result(Folder.SERVER, null, argString(translatable, 0), null, false);
			}
			if (key.startsWith("chat.type.")) {
				// disguised/broadcast player chat (e.g. say, /me on vanilla)
				return new Result(Folder.CHAT, null, argString(translatable, 0), null, false);
			}
		}

		// 3. Whisper regex fallback (non-vanilla servers)
		Matcher mIn = WHISPER_IN.matcher(plain);
		if (mIn.matches()) {
			return new Result(Folder.DM, mIn.group(1), mIn.group(1), mIn.group(2), false);
		}
		Matcher mOut = WHISPER_OUT.matcher(plain);
		if (mOut.matches()) {
			return new Result(Folder.DM, mOut.group(1), null, mOut.group(2), true);
		}

		// 4. "<Name> message" chat formatting - BEFORE join/death, so that
		//    "<Bob> left the game" stays player chat and not a join message
		Matcher mChat = CHAT_LINE.matcher(plain);
		if (mChat.matches()) {
			return new Result(Folder.CHAT, null, mChat.group(1), null, false);
		}

		// 5. Death regex fallback
		for (Pattern p : DEATH_PATTERNS) {
			if (p.matcher(plain).find()) {
				return new Result(Folder.DEATH, null, firstWord(plain), null, false);
			}
		}

		// 6. Join/leave
		if (JOIN_LEAVE.matcher(plain).find()) {
			return new Result(Folder.JOINS, null, firstWord(plain), null, false);
		}

		// 7. Command feedback: system message shortly after we ran a command
		if (commandFeedbackEnabled && commandSentAt > 0
				&& System.currentTimeMillis() - commandSentAt < 2500L) {
			return new Result(Folder.COMMAND, null, null, null, false);
		}

		// 8. Remaining system messages -> server folder
		return new Result(Folder.SERVER, null, null, null, false);
	}

	private static Result whisper(TranslatableContents translatable) {
		String key = translatable.getKey();
		boolean outgoing = key.endsWith("outgoing");
		String partner = argString(translatable, 0);
		String content = argString(translatable, 1);
		return new Result(Folder.DM, partner, outgoing ? null : partner, content, outgoing);
	}

	/** Extracts the name from a "<Name> …" string, or null. */
	private static String chatLineName(String plain) {
		Matcher m = CHAT_LINE.matcher(plain);
		return m.matches() ? m.group(1) : null;
	}

	private static String argString(TranslatableContents contents, int index) {
		Object[] args = contents.getArgs();
		if (args == null || index >= args.length || args[index] == null) return null;
		Object arg = args[index];
		if (arg instanceof Component c) return c.getString();
		return String.valueOf(arg);
	}

	private static String firstWord(String s) {
		s = s.trim();
		int i = s.indexOf(' ');
		return i <= 0 ? null : s.substring(0, i);
	}
}

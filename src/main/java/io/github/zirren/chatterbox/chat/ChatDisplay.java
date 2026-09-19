package io.github.zirren.chatterbox.chat;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FormattedText;

import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.mixin.ChatComponentAccessor;
import io.github.zirren.chatterbox.mixin.ChatComponentInvoker;

/**
 * Bridges {@link ChatStore} and the vanilla chat hud: builds display
 * components (timestamps, pin markers, repeat counters, DM re-coloring,
 * two-line layout) and re-adds them through the vanilla message pipeline so
 * the vanilla look is preserved. Also owns the visible-message filter that
 * implements folder switching.
 */
public final class ChatDisplay {
	/** Maps the exact component instance we pushed into the chat hud back to its entry. */
	static final Map<Component, ChatEntry> BY_CONTENT = new IdentityHashMap<>();

	private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
	private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
	private static final String INDENT = "  ";
	/** How many recent entries to re-add on a full view refresh. */
	private static final int REFRESH_LIMIT = 80;

	private ChatDisplay() {
	}

	public static ChatComponent chat() {
		return Minecraft.getInstance().gui.hud.getChat();
	}

	// ------------------------------------------------------------------
	// Filter (folder switching)
	// ------------------------------------------------------------------

	/**
	 * Installs the folder filter on the vanilla chat hud. Messages that we did
	 * not add ourselves are never hidden, so other mods keep working.
	 */
	public static void installFilter() {
		ChatStore store = ChatStore.INSTANCE;
		chat().setVisibleMessageFilter(message -> {
			ChatEntry entry = BY_CONTENT.get(message.content());
			return entry == null || store.isVisibleInView(entry, store.activeFolder(), store.activeDmPartner());
		});
	}

	// ------------------------------------------------------------------
	// Adding / refreshing
	// ------------------------------------------------------------------

	/** Adds a single newly received entry to the chat hud. */
	public static void display(ChatEntry entry) {
		ChatComponent chat = chat();
		Minecraft mc = Minecraft.getInstance();
		boolean dmView = ChatStore.INSTANCE.activeFolder() == Folder.DM
				&& ChatStore.INSTANCE.activeDmPartner() != null;
		int maxWidth = wrapWidth(mc.font);
		Component content = buildContent(mc.font, entry, dmView, maxWidth);
		BY_CONTENT.put(content, entry);
		boolean was = ChatStore.INSTANCE.beginRouting();
		try {
			((ChatComponentInvoker) chat).chatterbox$addMessage(content, null, entry.source, entry.tag);
		} finally {
			ChatStore.INSTANCE.endRouting(was);
		}
	}

	/** Rebuilds the whole visible chat hud for the current view. */
	public static void refresh() {
		ChatStore store = ChatStore.INSTANCE;
		ChatComponent chat = chat();
		Minecraft mc = Minecraft.getInstance();
		boolean was = store.beginRouting();
		try {
			ChatComponentAccessor access = (ChatComponentAccessor) chat;
			access.chatterbox$trimmedMessages().clear();
			access.chatterbox$allMessages().clear();
			BY_CONTENT.clear();

			List<ChatEntry> visible = new ArrayList<>();
			List<ChatEntry> all = store.entries();
			for (int i = all.size() - 1; i >= 0 && visible.size() < REFRESH_LIMIT; i--) {
				ChatEntry e = all.get(i);
				if (store.isVisibleInView(e, store.activeFolder(), store.activeDmPartner())) {
					visible.add(e);
				}
			}
			Collections.reverse(visible);
			boolean dmView = store.activeFolder() == Folder.DM && store.activeDmPartner() != null;
			int maxWidth = wrapWidth(mc.font);
			for (ChatEntry e : visible) {
				Component content = buildContent(mc.font, e, dmView, maxWidth);
				BY_CONTENT.put(content, e);
				((ChatComponentInvoker) chat).chatterbox$addMessage(content, null, e.source, e.tag);
			}
			chat.resetChatScroll();
		} finally {
			store.endRouting(was);
		}
	}

	/** Clears everything we put into the chat hud (disconnect / session reset). */
	public static void clear() {
		ChatComponent chat = chat();
		ChatComponentAccessor access = (ChatComponentAccessor) chat;
		access.chatterbox$trimmedMessages().clear();
		access.chatterbox$allMessages().clear();
		BY_CONTENT.clear();
	}

	// ------------------------------------------------------------------
	// Content building
	// ------------------------------------------------------------------

	private static int wrapWidth(Font font) {
		Minecraft mc = Minecraft.getInstance();
		double scale = mc.options.chatScale().get();
		if (scale < 0.05D) scale = 1.0D;
		return Math.max(40, (int) Math.floor(ChatComponent.getWidth(mc.options.chatWidth().get()) / scale));
	}

	/**
	 * Builds the component shown in chat for one entry in the current view.
	 * Two-line mode puts timestamp + sender on the first line and the message
	 * hanging-indented below it, so wrapped lines never slide under the name.
	 */
	static Component buildContent(Font font, ChatEntry entry, boolean dmView, int maxWidth) {
		Config cfg = Config.get();
		MutableComponent out = Component.empty();

		if (entry.pinned) {
			out.append(Component.literal("📌 ").withStyle(ChatFormatting.GOLD));
		}

		if (dmView && entry.folder == Folder.DM && entry.dmContent != null) {
			// DM folder: render like a normal chat message, not greyed out.
			String name = entry.outgoing
					? "[" + Minecraft.getInstance().getUser().getName() + " → " + entry.dmPartner + "]"
					: "<" + entry.dmPartner + ">";
			out.append(Component.literal(name + " ").withStyle(ChatFormatting.WHITE));
			out.append(buildBody(font, Component.literal(entry.dmContent), entry, maxWidth));
			return out;
		}

		if (cfg.twoLineLayout && entry.sender != null) {
			String time = timestamp(cfg, entry);
			MutableComponent header = Component.literal(time + "<" + entry.sender + "> ")
					.withStyle(ChatFormatting.GRAY);
			out.append(header);
			out.append(buildBody(font, entry.original, entry, maxWidth));
			return out;
		}

		String time = timestamp(cfg, entry);
		if (!time.isEmpty()) {
			out.append(Component.literal(time).withStyle(ChatFormatting.GRAY));
		}
		out.append(entry.original);
		if (entry.repeatCount > 1) {
			out.append(Component.literal(" (" + entry.repeatCount + ")").withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	/**
	 * Pre-wraps text so continuation lines are indented and never slide under
	 * the header. Only used for two-line layout and DM view.
	 */
	private static Component buildBody(Font font, FormattedText body, ChatEntry entry, int maxWidth) {
		int indentPx = font.width(INDENT);
		int bodyWidth = Math.max(30, maxWidth - indentPx);
		List<FormattedText> lines = font.getSplitter().splitLines(body, bodyWidth, Style.EMPTY);
		MutableComponent out = Component.empty();
		boolean first = true;
		for (FormattedText line : lines) {
			if (!first) out.append("\n" + INDENT);
			out.append(lineToComponent(line));
			first = false;
		}
		if (lines.isEmpty()) out.append("");
		if (entry.repeatCount > 1) {
			out.append(Component.literal(" (" + entry.repeatCount + ")").withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	private static Component lineToComponent(FormattedText line) {
		MutableComponent out = Component.empty();
		line.visit((style, part) -> {
			if (!part.isEmpty()) out.append(Component.literal(part).withStyle(style));
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static String timestamp(Config cfg, ChatEntry entry) {
		if (!cfg.timestamps) return "";
		DateTimeFormatter fmt = cfg.timestampSeconds ? HHMMSS : HHMM;
		return "[" + entry.timestamp.atZone(ZoneId.systemDefault()).format(fmt) + "] ";
	}

	// ------------------------------------------------------------------
	// Hit testing (pin hover, chat screen clicks)
	// ------------------------------------------------------------------

	/**
	 * Returns the entry displayed at the given screen coordinates, or null.
	 * Mirrors the vanilla chat render math.
	 */
	public static @Nullable ChatEntry entryAt(double mouseX, double mouseY, int guiScaledHeight) {
		Minecraft mc = Minecraft.getInstance();
		ChatComponent chat = chat();
		double scale = mc.options.chatScale().get();
		if (scale < 0.05D) return null;
		int width = ChatComponent.getWidth(mc.options.chatWidth().get());
		int maxWidth = (int) Math.ceil(width / scale);
		int chatBottom = (int) Math.floor((guiScaledHeight - 40) / scale);
		double lx = mouseX / scale - 4.0D;
		double ly = mouseY / scale;
		if (lx < -4.0D || lx > maxWidth + 8.0D) return null;

		double chatLineSpacing = mc.options.chatLineSpacing().get();
		int entryHeight = (int) (9 * (chatLineSpacing + 1.0D));
		if (entryHeight <= 0) return null;

		int scroll = ((ChatComponentAccessor) chat).chatterbox$getChatScrollbarPos();
		int total = ((ChatComponentAccessor) chat).chatterbox$trimmedMessages().size();
		int perPage = chat.getLinesPerPage();
		int count = Math.max(0, Math.min(total - scroll, perPage));
		int row = (int) Math.floor((chatBottom - ly) / entryHeight);
		if (row < 0 || row >= count) return null;
		int index = scroll + row;
		if (index < 0 || index >= total) return null;
		GuiMessage.Line line = ((ChatComponentAccessor) chat).chatterbox$trimmedMessages().get(index);
		return BY_CONTENT.get(line.parent().content());
	}

	// ------------------------------------------------------------------
	// Small drawing helpers shared by screens
	// ------------------------------------------------------------------

	/** Draws a subtle tooltip-ish text with background, used by the queue preview. */
	public static void drawBorderedText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
		g.fill(x - 2, y - 2, x + font.width(text) + 2, y + 10, 0x90202028);
		g.text(font, text, x, y, color, true);
	}
}

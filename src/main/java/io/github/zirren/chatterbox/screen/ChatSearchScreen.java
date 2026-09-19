package io.github.zirren.chatterbox.screen;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import io.github.zirren.chatterbox.chat.ChatEntry;
import io.github.zirren.chatterbox.chat.ChatFileLogger;
import io.github.zirren.chatterbox.chat.ChatStore;
import io.github.zirren.chatterbox.chat.Folder;
import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.Lang;

/**
 * Chat search screen: searches the current session and (optionally) the log
 * files of previous sessions, with separators between sessions.
 */
public class ChatSearchScreen extends Screen {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final DateTimeFormatter SESSION = DateTimeFormatter.ofPattern("MMM d HH:mm");

	private final @Nullable Screen parent;
	private EditBox searchBox;
	private ResultList list;
	private List<Row> rows = new ArrayList<>();
	private int resultCount;

	public ChatSearchScreen(@Nullable Screen parent) {
		super(Component.translatable("chatterbox.search.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		searchBox = new EditBox(this.font, this.width / 2 - 130, 14, 260, 16,
				Component.translatable("chatterbox.search.box"));
		searchBox.setHint(Component.translatable("chatterbox.search.box"));
		searchBox.setMaxLength(200);
		searchBox.setResponder(this::onQuery);
		this.addRenderableWidget(searchBox);
		this.setInitialFocus(searchBox);

		list = new ResultList(this.minecraft, this.width, this.height - 40 - 28, 40, 12);
		this.addRenderableWidget(list);

		this.addRenderableWidget(Button.builder(Component.translatable("chatterbox.config.back"), b -> onClose())
				.pos(this.width / 2 - 100, this.height - 24).size(200, 20).build());

		onQuery("");
	}

	private void onQuery(String query) {
		rows = search(query);
		resultCount = (int) rows.stream().filter(r -> r instanceof ResultRow).count();
		list.refresh(rows);
	}

	private List<Row> search(String query) {
		String q = query.trim().toLowerCase(Locale.ROOT);
		List<Result> ordered = new ArrayList<>();

		List<ChatEntry> live = ChatStore.INSTANCE.entries();
		// messages from the current session are already in `live`; do not show
		// their logged copies as well
		java.time.Instant cutoff = live.isEmpty() ? java.time.Instant.MAX : live.get(0).timestamp;
		if (Config.get().crossSessionSearch) {
			for (ChatFileLogger.ParsedLine line : ChatFileLogger.readAll()) {
				if (!line.timestamp().isBefore(cutoff)) continue;
				if (matches(line.text(), q)) {
					ordered.add(Result.of(line));
				}
			}
		}
		for (ChatEntry entry : live) {
			if (matches(entry.text, q)) {
				ordered.add(Result.of(entry));
			}
		}
		ordered.sort(Comparator.comparing(r -> r.timestamp));

		List<Row> out = new ArrayList<>();
		long lastSession = Long.MIN_VALUE;
		int shown = 0;
		for (Result r : ordered) {
			if (r.sessionId != lastSession) {
				if (r.isCurrentSession) {
					out.add(new HeaderRow(Component.translatable("chatterbox.search.current_session")));
				} else {
					out.add(new HeaderRow(Component.translatable("chatterbox.search.session", r.sessionTime)));
				}
				lastSession = r.sessionId;
			}
			out.add(new ResultRow(r));
			if (++shown > 400) break;
		}
		return out;
	}

	private static boolean matches(String text, String q) {
		return q.isEmpty() || text.toLowerCase(Locale.ROOT).contains(q);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 2, 0xFFFFFFFF, true);
		String count = Lang.tr("chatterbox.search.results", resultCount);
		graphics.text(this.font, count, this.width / 2 + 134, 15, 0xFFA0A0A0, false);
		if (rows.isEmpty()) {
			String empty = Lang.tr(searchBox != null && !searchBox.getValue().isEmpty()
					? "chatterbox.search.empty" : "chatterbox.search.type");
			graphics.text(this.font, empty, this.width / 2 - this.font.width(empty) / 2,
					this.height / 2 - 10, 0xFF808080, false);
		}
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(parent);
		}
	}

	// ------------------------------------------------------------------

	private record Result(java.time.Instant timestamp, String time, String sessionTime, long sessionId,
			boolean isCurrentSession, Folder folder, String dmPartner, String sender, String text) {
		static Result of(ChatEntry e) {
			var zoned = e.timestamp.atZone(ZoneId.systemDefault());
			return new Result(e.timestamp, zoned.format(TIME), zoned.format(SESSION),
					e.sessionId, true, e.folder, e.dmPartner, e.sender, e.text);
		}

		static Result of(ChatFileLogger.ParsedLine l) {
			var zoned = l.timestamp().atZone(ZoneId.systemDefault());
			return new Result(l.timestamp(), zoned.format(TIME), zoned.format(SESSION),
					-l.sessionId(), false, l.folder(), l.dmPartner(), l.sender(), l.text());
		}
	}

	private sealed interface Row permits ResultRow, HeaderRow {
	}

	private record HeaderRow(Component label) implements Row {
	}

	private record ResultRow(Result result) implements Row {
	}

	private class ResultList extends AbstractSelectionList<ResultList.Entry> {
		ResultList(Minecraft client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void refresh(List<Row> newRows) {
			clearEntries();
			for (Row row : newRows) {
				if (row instanceof HeaderRow h) {
					addEntry(new HeaderEntry(h));
				} else if (row instanceof ResultRow r) {
					addEntry(new ResultEntry(r));
				}
			}
		}

		@Override
		public int getRowWidth() {
			return Math.min(520, this.width - 24);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}

		@Override
		protected int scrollBarX() {
			return this.width / 2 + this.getRowWidth() / 2 + 2;
		}

		@Override
		public void setSelected(@Nullable Entry entry) {
			super.setSelected(entry);
			if (entry instanceof ResultEntry r) {
				Result res = r.result;
				switch (res.folder) {
					case PINNED -> ChatStore.INSTANCE.switchView(Folder.PINNED, null);
					case DM -> ChatStore.INSTANCE.switchView(Folder.DM, res.dmPartner);
					default -> ChatStore.INSTANCE.switchView(res.folder, null);
				}
				if (ChatSearchScreen.this.minecraft != null) {
					ChatSearchScreen.this.minecraft.gui.setScreen(new ChatterBoxChatScreen("", true));
				}
			}
		}

		abstract class Entry extends AbstractSelectionList.Entry<Entry> {
		}

		private class HeaderEntry extends Entry {
			private final Component label;

			HeaderEntry(HeaderRow row) {
				this.label = row.label();
			}


			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
				graphics.text(font, label, ResultList.this.getRowLeft() + 4, getContentY() + 2, 0xFF707070, false);
			}
		}

		private class ResultEntry extends Entry {
			private final Result result;

			ResultEntry(ResultRow row) {
				this.result = row.result();
			}


			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
				Font f = ChatSearchScreen.this.font;
				int left = ResultList.this.getRowLeft() + 2;
				int maxRight = left + ResultList.this.getRowWidth() - 8;
				int y = getContentY() + 2;

				if (hovered) {
					graphics.fill(left - 2, getContentY(), maxRight, getContentY() + 12, 0x25FFFFFF);
				}

				Component time = Component.literal("[" + result.time + "]");
				graphics.text(f, time, left, y, 0xFFA0A0A0, false);
				int x = left + f.width(time);

				String folderName = result.folder == Folder.DM && result.dmPartner != null
						? "DM/" + result.dmPartner
						: Component.translatable(result.folder.translationKey()).getString();
				Component folder = Component.literal(" [" + folderName + "]");
				graphics.text(f, folder, x, y, 0xFF55FFFF, false);
				x += f.width(folder) + 3;

				if (result.sender != null) {
					Component sender = Component.literal("<" + result.sender + "> ");
					graphics.text(f, sender, x, y, 0xFFFFFFFF, false);
					x += f.width(sender);
				}

				int remaining = maxRight - x;
				if (remaining > 8) {
					String clipped = f.plainSubstrByWidth(result.text, remaining);
					graphics.text(f, clipped, x, y, hovered ? 0xFFFFFFA0 : 0xFFD8D8D8, false);
				}
			}
		}
	}
}

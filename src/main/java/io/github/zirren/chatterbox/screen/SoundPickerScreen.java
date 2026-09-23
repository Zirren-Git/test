package io.github.zirren.chatterbox.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import io.github.zirren.chatterbox.chat.Sounds;
import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.Lang;

/**
 * Sound picker: lists sound events from the game's registry. Defaults to note
 * block instruments; can be toggled to show all registered sounds. Click a
 * sound to preview it, click again to select it.
 */
public class SoundPickerScreen extends Screen {
	private final Screen parent;
	private final Consumer<String> callback;
	private final float previewPitch;
	private final float previewVolume;

	private EditBox searchBox;
	private SoundList list;
	private List<String> allIds = new ArrayList<>();
	private List<String> shownIds = new ArrayList<>();
	private String pendingId;
	private Button scopeButton;

	public SoundPickerScreen(Screen parent, String currentId, float previewPitch, float previewVolume, Consumer<String> callback) {
		super(Component.translatable("chatterbox.sounds.title"));
		this.parent = parent;
		this.callback = callback;
		this.previewPitch = previewPitch;
		this.previewVolume = previewVolume;
		this.pendingId = currentId;
	}

	@Override
	protected void init() {
		collectIds();

		searchBox = new EditBox(this.font, this.width / 2 - 130, 14, 260, 16,
				Component.translatable("chatterbox.search.box"));
		searchBox.setHint(Component.translatable("chatterbox.search.box"));
		searchBox.setResponder(this::onQuery);
		this.addRenderableWidget(searchBox);
		this.setInitialFocus(searchBox);

		scopeButton = Button.builder(scopeLabel(), b -> {
			Config.get().soundPickerAllSounds = !Config.get().soundPickerAllSounds;
			Config.get().save();
			collectIds();
			scopeButton.setMessage(scopeLabel());
			refresh();
		}).pos(this.width / 2 - 130, 34).size(260, 18).build();
		this.addRenderableWidget(scopeButton);

		list = new SoundList(this.minecraft, this.width, this.height - 60 - 58, 60, 11);
		this.addRenderableWidget(list);

		this.addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.cancel"), b -> onClose())
				.pos(this.width / 2 - 130, this.height - 24).size(260, 20).build());

		refresh();
	}

	private Component scopeLabel() {
		return Component.translatable("chatterbox.config.sound_picker_all",
				Component.translatable(Config.get().soundPickerAllSounds
						? "chatterbox.config.sound_picker_all.all"
						: "chatterbox.config.sound_picker_all.noteblocks"));
	}

	private void collectIds() {
		boolean all = Config.get().soundPickerAllSounds;
		List<String> ids = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
			if (!all && !(id.getNamespace().equals("minecraft") && id.getPath().startsWith("block.note_block."))) {
				continue;
			}
			ids.add(id.toString());
		}
		ids.sort(Comparator.naturalOrder());
		allIds = ids;
	}

	private void onQuery(String query) {
		refresh();
	}

	private void refresh() {
		String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		shownIds = new ArrayList<>();
		for (String id : allIds) {
			if (q.isEmpty() || id.toLowerCase(Locale.ROOT).contains(q)) {
				shownIds.add(id);
			}
		}
		list.refresh();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 2, 0xFFFFFFFF, true);
		String selected = Component.translatable("chatterbox.sounds.selected", pendingId).getString();
		graphics.text(this.font, selected, this.width / 2 - this.font.width(selected) / 2, this.height - 34, 0xFFA0A0A0, false);
		String hint = Lang.tr("chatterbox.sounds.hint");
		graphics.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height - 44, 0xFF707070, false);
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(parent);
		}
	}

	private class SoundList extends AbstractSelectionList<SoundList.Entry> {
		private String lastPreviewed = null;

		SoundList(Minecraft client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void refresh() {
			clearEntries();
			for (String id : shownIds) {
				addEntry(new Row(id));
			}
		}

		@Override
		public int getRowWidth() {
			return Math.min(500, this.width - 24);
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
			if (!(entry instanceof Row row)) return;
			String id = row.id;
			if (id.equals(pendingId) || id.equals(lastPreviewed)) {
				callback.accept(id);
				SoundPickerScreen.this.onClose();
				return;
			}
			lastPreviewed = id;
			pendingId = id;
			Sounds.play(id, previewPitch, previewVolume);
		}

		abstract class Entry extends AbstractSelectionList.Entry<Entry> {
			// 26.x lists have no built-in click-to-select (see MentionRulesScreen)
			@Override
			public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
				if (event.button() == 0 && isMouseOver(event.x(), event.y())) {
					SoundList.this.setSelected(this);
					return true;
				}
				return false;
			}
		}

		private class Row extends Entry {
			private final String id;

			Row(String id) {
				this.id = id;
			}


			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
				Font f = SoundPickerScreen.this.font;
				int left = SoundList.this.getRowLeft() + 2;
				int y = getContentY() + 1;
				if (hovered) {
					graphics.fill(left - 2, getContentY(), left + SoundList.this.getRowWidth() - 4,
							getContentY() + 11, 0x25FFFFFF);
				}
				boolean isNote = Sounds.isNoteBlockInstrument(id);
				String display = (isNote ? "♪ " : "  ") + id;
				String clipped = f.plainSubstrByWidth(display, SoundList.this.getRowWidth() - 10);
				graphics.text(f, clipped, left, y, id.equals(pendingId) ? 0xFF55FF55 : (isNote ? 0xFFFFFFFF : 0xFFB8B8B8), false);
			}
		}
	}
}

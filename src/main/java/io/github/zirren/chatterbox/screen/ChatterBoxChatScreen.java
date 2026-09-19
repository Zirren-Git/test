package io.github.zirren.chatterbox.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import io.github.zirren.chatterbox.ChatterBoxClient;
import io.github.zirren.chatterbox.Keybinds;
import io.github.zirren.chatterbox.chat.ChatDisplay;
import io.github.zirren.chatterbox.chat.ChatEntry;
import io.github.zirren.chatterbox.chat.ChatStore;
import io.github.zirren.chatterbox.chat.Folder;
import io.github.zirren.chatterbox.chat.Sounds;
import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.Lang;
import io.github.zirren.chatterbox.mixin.ChatScreenAccessor;

/**
 * The chat screen with folder tabs, unread counters, DM sub-folders, draft
 * restore and the Shift+Enter send queue. Built on top of the vanilla
 * {@link ChatScreen} so the input, command suggestions, history and click
 * handling stay 100% vanilla.
 */
public class ChatterBoxChatScreen extends ChatScreen {

	private record Tab(int x1, int x2, int y1, int y2, @Nullable Folder folder, @Nullable String partner,
			boolean addDm, boolean search) {
		boolean hit(double mx, double my) {
			return mx >= x1 && mx < x2 && my >= y1 && my < y2;
		}
	}

	private final List<Tab> tabs = new ArrayList<>();
	private final List<String> queue = new ArrayList<>();
	/** Input + queue preserved across sub-screens (search, add-DM). */
	private @Nullable Stash stash;
	private int mouseX;
	private int mouseY;

	private record Stash(String input, List<String> queue) {
	}

	public ChatterBoxChatScreen(String initial, boolean isDraft) {
		super(initial, isDraft);
	}

	@Override
	protected void init() {
		super.init();
		try {
			ChatStore store = ChatStore.INSTANCE;
			if (stash != null) {
				if (!stash.input().isEmpty()) {
					input.setValue(stash.input());
				}
				queue.addAll(stash.queue());
				stash = null;
			} else if (Config.get().drafts && (initial == null || initial.isEmpty())) {
				String draft = store.getDraft(store.activeFolder(), store.activeDmPartner());
				if (!draft.isEmpty()) {
					input.setValue(draft);
				}
				queue.addAll(store.getQueuedLines(store.activeFolder(), store.activeDmPartner()));
			}
		} catch (Throwable t) {
			ChatterBoxClient.LOGGER.warn("ChatterBox draft restore failed", t);
		}
	}

	@Override
	public void removed() {
		try {
			if (exitReason != ChatScreen.ExitReason.DONE) {
				stash = new Stash(input.getValue(), new ArrayList<>(queue));
				if (Config.get().drafts) {
					ChatStore store = ChatStore.INSTANCE;
					store.setDraft(store.activeFolder(), store.activeDmPartner(), input.getValue());
					store.setQueuedLines(store.activeFolder(), store.activeDmPartner(), queue);
				}
			}
		} catch (Throwable t) {
			ChatterBoxClient.LOGGER.warn("ChatterBox draft saving failed", t);
		}
		super.removed();
	}

	// ------------------------------------------------------------------
	// Keyboard
	// ------------------------------------------------------------------

	@Override
	public boolean keyPressed(KeyEvent event) {
		try {
			if (chatterboxHandleKey(event)) {
				return true;
			}
		} catch (Throwable t) {
			ChatterBoxClient.LOGGER.warn("ChatterBox key handling failed; using vanilla behaviour", t);
		}
		return super.keyPressed(event);
	}

	/**
	 * ChatterBox's key handling. Returns true when the key was consumed.
	 * Any failure falls back to the vanilla behaviour instead of crashing.
	 */
	private boolean chatterboxHandleKey(KeyEvent event) {
		int key = event.key();
		ChatStore store = ChatStore.INSTANCE;

		// The suggestion popup gets the key first, exactly like vanilla, so
		// Tab/Enter completion and arrow navigation keep working.
		CommandSuggestions suggestions = ((ChatScreenAccessor) this).chatterbox$commandSuggestions();
		if (suggestions != null && suggestions.keyPressed(event)) {
			return true;
		}

		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			if (suggestions != null && !suggestions.hasAllowedInput()) {
				return true;
			}
			if (minecraft.hasShiftDown() && !input.getValue().startsWith("/")) {
				queueLine();
			} else {
				sendAll();
			}
			return true;
		}

		if (key == InputConstants.KEY_P && (minecraft.hasControlDown() || Keybinds.PIN.matches(event))) {
			togglePinHovered();
			return true;
		}

		if (minecraft.hasControlDown() && key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
			int index = key - InputConstants.KEY_1;
			List<Folder> folders = new ArrayList<>(store.visibleFolders());
			if (index < folders.size()) {
				switchTo(folders.get(index), null);
				return true;
			}
		}

		return false;
	}

	// ------------------------------------------------------------------
	// Sending
	// ------------------------------------------------------------------

	private void queueLine() {
		String line = input.getValue();
		if (line.isBlank()) return;
		queue.add(line);
		input.setValue("");
	}

	private void sendAll() {
		List<String> lines = new ArrayList<>(queue);
		String current = input.getValue();
		if (!current.isBlank()) lines.add(current);

		queue.clear();
		input.setValue("");

		if (lines.isEmpty()) {
			// vanilla closes the chat screen on empty Enter as well
			exitReason = ChatScreen.ExitReason.DONE;
			minecraft.gui.setScreen(null);
			return;
		}

		Config cfg = Config.get();
		ChatStore store = ChatStore.INSTANCE;
		boolean join = cfg.joinQueuedLines && lines.size() > 1;
		if (join) {
			sendLine(String.join("\n", lines));
		} else {
			for (String line : lines) {
				sendLine(line);
			}
		}

		if (cfg.drafts) {
			store.clearDraft(store.activeFolder(), store.activeDmPartner());
		}
		exitReason = ChatScreen.ExitReason.DONE;
		minecraft.gui.setScreen(null);
	}

	private void sendLine(String raw) {
		Config cfg = Config.get();
		String msg = raw.trim();
		if (msg.isEmpty()) return;

		boolean isCommand = msg.startsWith("/");
		if (!isCommand || cfg.expandShortcutsInCommands) {
			msg = io.github.zirren.chatterbox.chat.Shortcuts.expand(msg);
		}

		// Typing in a DM sub-folder auto-whispers to that person.
		if (!isCommand && ChatStore.INSTANCE.isInDmSubfolder()) {
			String partner = ChatStore.INSTANCE.activeDmPartner();
			String cmd = cfg.whisperCommand + " " + partner + " " + msg;
			handleChatInput("/" + cmd, true);
			return;
		}

		handleChatInput(msg, true);
	}

	// ------------------------------------------------------------------
	// Folders / views
	// ------------------------------------------------------------------

	private void switchTo(Folder folder, @Nullable String partner) {
		ChatStore store = ChatStore.INSTANCE;
		if (store.activeFolder() == folder && Objects.equals(store.activeDmPartner(), partner)) return;
		if (Config.get().drafts) {
			store.setDraft(store.activeFolder(), store.activeDmPartner(), input.getValue());
			store.setQueuedLines(store.activeFolder(), store.activeDmPartner(), queue);
		}
		queue.clear();
		store.switchView(folder, partner);
		if (Config.get().drafts) {
			input.setValue(store.getDraft(folder, partner));
			queue.addAll(store.getQueuedLines(folder, partner));
		}
	}

	void switchToPartner(String partner) {
		switchTo(Folder.DM, partner);
	}

	// ------------------------------------------------------------------
	// Pinning
	// ------------------------------------------------------------------

	private void togglePinHovered() {
		ChatEntry entry = ChatDisplay.entryAt(mouseX, mouseY, this.height);
		if (entry != null) {
			ChatStore.INSTANCE.togglePin(entry);
			Sounds.play("minecraft:ui.button.click", 1.0F, 0.5F);
		}
	}

	// ------------------------------------------------------------------
	// Mouse
	// ------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		try {
			for (Tab tab : tabs) {
				if (tab.hit(event.x(), event.y())) {
					if (tab.search()) {
						minecraft.gui.setScreen(new ChatSearchScreen(this));
					} else if (tab.addDm()) {
						minecraft.gui.setScreen(new DmAddScreen(this));
					} else {
						switchTo(tab.folder(), tab.partner());
					}
					return true;
				}
			}
		} catch (Throwable t) {
			ChatterBoxClient.LOGGER.warn("ChatterBox tab click failed; using vanilla behaviour", t);
		}
		return super.mouseClicked(event, doubleClick);
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		try {
			drawTabBar(g, this.font);
			drawQueue(g, this.font);
		} catch (Throwable t) {
			ChatterBoxClient.LOGGER.warn("ChatterBox tab bar rendering failed once; hidden for this session", t);
		}
	}

	private void drawTabBar(GuiGraphicsExtractor g, Font font) {
		tabs.clear();
		ChatStore store = ChatStore.INSTANCE;
		Config cfg = Config.get();

		// Main folder row
		int x = 2;
		int y = 2;
		boolean badges = cfg.unreadBadges;
		for (Folder folder : store.visibleFolders()) {
			String label = tabLabel(Lang.tr(folder.translationKey()), badges ? store.unread(folder) : 0);
			int w = font.width(label) + 8;
			if (x + w > this.width - 44 && folder != Folder.ALL) break;
			boolean active = store.activeFolder() == folder;
			drawTab(g, font, x, y, x + w, y + 11, label, active);
			tabs.add(new Tab(x, x + w, y, y + 11, folder, null, false, false));
			x += w + 1;
		}

		// Search button at the far right of the first row
		int sx = this.width - 42;
		drawTab(g, font, sx, y, this.width - 2, y + 11, "🔍", false);
		tabs.add(new Tab(sx, this.width - 2, y, y + 11, null, null, false, true));

		// DM sub-folder row
		if (store.activeFolder() == Folder.DM) {
			int dx = 2;
			int dy = 15;
			boolean allActive = store.activeDmPartner() == null;
			int w = font.width("All") + 8;
			drawTab(g, font, dx, dy, dx + w, dy + 11, "All", allActive);
			tabs.add(new Tab(dx, dx + w, dy, dy + 11, Folder.DM, null, false, false));
			dx += w + 1;
			for (String partner : store.dmPartners()) {
				int unread = badges ? store.dmUnread(partner) : 0;
				String label = tabLabel(partner, unread);
				w = font.width(label) + 8;
				if (dx + w > this.width - 20) break;
				boolean active = !allActive && partner.equalsIgnoreCase(store.activeDmPartner());
				drawTab(g, font, dx, dy, dx + w, dy + 11, label, active);
				tabs.add(new Tab(dx, dx + w, dy, dy + 11, Folder.DM, partner, false, false));
				dx += w + 1;
			}
			if (dx + 14 <= this.width - 2) {
				drawTab(g, font, dx, dy, dx + 14, dy + 11, "➕", false);
				tabs.add(new Tab(dx, dx + 14, dy, dy + 11, null, null, true, false));
			}
		}
	}

	private void drawTab(GuiGraphicsExtractor g, Font font, int x1, int y1, int x2, int y2, String label,
			boolean active) {
		int bg = active ? 0xE6383840 : 0x90202028;
		g.fill(x1, y1, x2, y2, bg);
		if (active) {
			g.fill(x1, y2 - 1, x2, y2, 0xFFFFD35C);
		}
		int color = active ? 0xFFFFFFFF : 0xFFB8B8C0;
		g.text(font, label, x1 + 4, y1 + 2, color, true);
	}

	private static String tabLabel(String base, int unread) {
		return unread > 0 ? base + " (" + unread + ")" : base;
	}

	private void drawQueue(GuiGraphicsExtractor g, Font font) {
		if (queue.isEmpty()) return;
		int y = this.height - 16;
		for (int i = queue.size() - 1; i >= 0; i--) {
			String line = "› " + queue.get(i);
			String clipped = font.plainSubstrByWidth(line, this.width - 12);
			ChatDisplay.drawBorderedText(g, font, clipped, 6, y, 0xFFE0E0E0);
			y -= 11;
			if (y < 40) break;
		}
		String hint = Lang.tr("chatterbox.queue.count", queue.size());
		ChatDisplay.drawBorderedText(g, font, hint, 6, y, 0xFF9A9AA0);
	}

}

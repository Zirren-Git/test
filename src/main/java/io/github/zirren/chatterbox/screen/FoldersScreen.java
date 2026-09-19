package io.github.zirren.chatterbox.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import io.github.zirren.chatterbox.chat.Folder;
import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.Lang;

/**
 * Show/hide individual folder tabs. "All" is always visible.
 */
public class FoldersScreen extends ChatterBoxScreen {

	public FoldersScreen(net.minecraft.client.gui.screens.Screen parent) {
		super(Component.translatable("chatterbox.config.folders"), parent);
	}

	@Override
	protected void init() {
		int y = 30;
		for (Folder folder : Folder.values()) {
			if (folder == Folder.ALL) continue;
			addRenderableWidget(folderButton(folder, y));
			y += 22;
		}

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.done"), b -> onClose())
				.pos(this.width / 2 - 75, Math.max(y + 4, this.height - 26)).size(150, 20).build());
	}

	private Button folderButton(Folder folder, int y) {
		Config cfg = Config.get();
		Button b = Button.builder(label(folder), btn -> {
			boolean hidden = !cfg.isFolderHidden(folder.key);
			cfg.setFolderHidden(folder.key, hidden);
			// never leave the active view on a hidden folder
			if (hidden && io.github.zirren.chatterbox.chat.ChatStore.INSTANCE.activeFolder() == folder) {
				io.github.zirren.chatterbox.chat.ChatStore.INSTANCE.switchView(Folder.ALL, null);
			}
			// the DM folder hosts the sub-tabs; leaving it is handled by switchView
			io.github.zirren.chatterbox.chat.ChatDisplay.refresh();
			// refresh this screen's labels
			this.rebuildWidgets();
			// folder buttons were recreated by rebuildWidgets; nothing else to do
		}).pos(this.width / 2 - 110, y).size(220, 20).build();
		return b;
	}

	private static Component label(Folder folder) {
		Config cfg = Config.get();
		return Component.literal(Lang.tr("chatterbox.config.folder_visible",
				Lang.tr(folder.translationKey()),
				Lang.tr(cfg.isFolderHidden(folder.key) ? "chatterbox.config.visible.hidden"
						: "chatterbox.config.visible.shown")));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
	}
}

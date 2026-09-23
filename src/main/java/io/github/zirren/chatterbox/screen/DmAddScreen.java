package io.github.zirren.chatterbox.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import io.github.zirren.chatterbox.chat.ChatStore;

/**
 * Small modal opened from the ➕ tab to add a DM sub-folder for a player name
 * manually.
 */
public class DmAddScreen extends Screen {

	private final ChatterBoxChatScreen parent;
	private EditBox name;

	public DmAddScreen(ChatterBoxChatScreen parent) {
		super(Component.translatable("chatterbox.dm.add.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		name = new EditBox(this.font, this.width / 2 - 100, 60, 200, 16, Component.translatable("chatterbox.dm.add.title"));
		name.setMaxLength(32);
		name.setHint(Component.translatable("chatterbox.dm.add.hint"));
		setInitialFocus(name);
		addRenderableWidget(name);

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.dm.add.add"), b -> add())
				.pos(this.width / 2 - 100, 90).size(95, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.cancel"), b -> onClose())
				.pos(this.width / 2 + 5, 90).size(95, 20).build());
	}

	private void add() {
		String player = name.getValue().trim();
		if (!player.isEmpty()) {
			ChatStore.INSTANCE.addDmPartner(player);
			parent.switchToPartner(ChatStore.INSTANCE.dmPartners().stream()
					.filter(p -> p.equalsIgnoreCase(player)).findFirst().orElse(player));
		}
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, this.width, this.height, 0xB0101018);
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 40, 0xFFFFFFFF, true);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}

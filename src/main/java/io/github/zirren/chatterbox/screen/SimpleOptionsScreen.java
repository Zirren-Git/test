package io.github.zirren.chatterbox.screen;

import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import io.github.zirren.chatterbox.Lang;
import io.github.zirren.chatterbox.chat.ChatDisplay;
import io.github.zirren.chatterbox.config.Config;

/**
 * One settings category: vanilla-style cycle buttons in two columns, each with
 * a tooltip, plus optional footer buttons that open sub-screens.
 */
public class SimpleOptionsScreen extends ChatterBoxScreen {

	/** One cycle-button option: label key, tooltip key, current value, cycle action. */
	public record Option(String labelKey, @Nullable String tooltipKey, Supplier<String> value, Runnable cycle) {
	}

	/** A footer button opening a sub-screen (receives this screen as the parent). */
	public record FooterButton(String labelKey, java.util.function.UnaryOperator<Screen> factory) {
	}

	private final List<Option> options;
	private final List<FooterButton> footer;

	public SimpleOptionsScreen(String titleKey, Screen parent, List<Option> options, List<FooterButton> footer) {
		super(Component.translatable(titleKey), parent);
		this.options = options;
		this.footer = footer;
	}

	@Override
	protected void init() {
		int col1 = this.width / 2 - 155;
		int col2 = this.width / 2 + 5;
		int y = 34;

		for (int i = 0; i < options.size(); i++) {
			int x = (i % 2 == 0) ? col1 : col2;
			if (i > 0 && i % 2 == 0) {
				y += 21;
			}
			addRenderableWidget(optionButton(options.get(i), x, y));
		}

		int y2 = y + 31;
		for (int i = 0; i < footer.size(); i++) {
			int x = (i % 2 == 0) ? col1 : col2;
			if (i > 0 && i % 2 == 0) {
				y2 += 21;
			}
			FooterButton entry = footer.get(i);
			addRenderableWidget(Button.builder(Component.literal(Lang.tr(entry.labelKey()) + "…"),
							b -> open(entry.factory().apply(this)))
					.pos(x, y2).size(150, 20).build());
		}

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.done"), b -> onClose())
				.pos(this.width / 2 - 75, this.height - 26).size(150, 20).build());
	}

	private void open(Screen screen) {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(screen);
		}
	}

	private Button optionButton(Option option, int x, int y) {
		Button button = Button.builder(cycleLabel(option.labelKey(), option.value().get()), btn -> {
			option.cycle().run();
			Config.get().save();
			// force redisplay of the whole chat with the new formatting
			ChatDisplay.refresh();
			btn.setMessage(cycleLabel(option.labelKey(), option.value().get()));
		}).pos(x, y).size(150, 20).build();
		if (option.tooltipKey() != null) {
			button.setTooltip(Tooltip.create(Component.translatable(option.tooltipKey())));
		}
		return button;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
	}
}

package io.github.zirren.chatterbox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

/** Gives ChatterBox access to the private {@code ChatComponent.addMessage}. */
@Mixin(ChatComponent.class)
public interface ChatComponentInvoker {

	@Invoker("addMessage")
	void chatterbox$addMessage(Component contents, @Nullable MessageSignature signature, GuiMessageSource source,
			@Nullable GuiMessageTag tag);
}

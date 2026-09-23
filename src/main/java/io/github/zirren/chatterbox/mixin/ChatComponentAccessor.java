package io.github.zirren.chatterbox.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;

/** Read/write access to private ChatComponent state used for view switching. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

	@Accessor("allMessages")
	List<GuiMessage> chatterbox$allMessages();

	@Accessor("trimmedMessages")
	List<GuiMessage.Line> chatterbox$trimmedMessages();

	@Accessor("chatScrollbarPos")
	int chatterbox$getChatScrollbarPos();
}

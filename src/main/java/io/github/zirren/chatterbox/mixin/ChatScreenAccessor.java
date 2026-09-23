package io.github.zirren.chatterbox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;

/** Access to the private command-suggestion popup of the vanilla chat screen. */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {

	@Accessor("commandSuggestions")
	CommandSuggestions chatterbox$commandSuggestions();
}

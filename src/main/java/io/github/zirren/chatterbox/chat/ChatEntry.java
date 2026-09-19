package io.github.zirren.chatterbox.chat;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;

/**
 * One stored chat message with all metadata used for folders, search,
 * compression and pinning.
 */
public final class ChatEntry {
	private static long nextId = 1L;

	public final long id;
	/** The original component as received. */
	public final Component original;
	/** The category folder this message belongs to (never ALL). */
	public final Folder folder;
	/** DM partner name for DM messages, null otherwise. */
	public final String dmPartner;
	/** Epoch millis. */
	public final Instant timestamp;
	/** Sender display name (without <>) or null. */
	public final String sender;
	/** The vanilla chat hud source this entry was received through. */
	public final GuiMessageSource source;
	/** The vanilla message tag (secure-chat indicator) to re-display with. */
	public final @Nullable GuiMessageTag tag;
	/** Plain text of the whole message (for search + compression). */
	public final String text;
	/** For DMs: the message body without the "whispers" wrapper. */
	public final String dmContent;
	/** True if this is an outgoing whisper sent by the local player. */
	public final boolean outgoing;
	/** Session id: changes on every world join; used for search separators. */
	public final long sessionId;

	public boolean pinned;
	/** How many times this message repeated consecutively (in its folder). */
	public int repeatCount = 1;

	ChatEntry(Component original, Folder folder, String dmPartner, Instant timestamp,
			String sender, String text, String dmContent, boolean outgoing, long sessionId,
			GuiMessageSource source, @Nullable GuiMessageTag tag) {
		this.id = nextId++;
		this.original = original;
		this.folder = folder;
		this.dmPartner = dmPartner;
		this.timestamp = timestamp;
		this.sender = sender;
		this.text = text;
		this.dmContent = dmContent;
		this.outgoing = outgoing;
		this.sessionId = sessionId;
		this.source = source;
		this.tag = tag;
	}

	public boolean isRepeatOf(ChatEntry other) {
		return other != null
				&& this.folder == other.folder
				&& java.util.Objects.equals(this.dmPartner, other.dmPartner)
				&& java.util.Objects.equals(this.sender, other.sender)
				&& this.text.equals(other.text);
	}

	public String senderOrDash() {
		return sender == null ? "-" : sender;
	}
}

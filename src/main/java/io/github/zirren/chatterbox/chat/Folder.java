package io.github.zirren.chatterbox.chat;

/**
 * Chat folders. {@link #ALL} shows everything; every message additionally
 * belongs to exactly one category folder.
 */
public enum Folder {
	ALL("all"),
	CHAT("chat"),
	DM("dm"),
	SERVER("server"),
	COMMAND("command"),
	DEATH("death"),
	PINNED("pinned");

	public final String key;

	Folder(String key) {
		this.key = key;
	}

	public String translationKey() {
		return "chatterbox.folder." + key;
	}

	/** All folders in tab order, excluding ALL (which is always first). */
	public static Folder[] categories() {
		return new Folder[]{CHAT, DM, SERVER, COMMAND, DEATH, PINNED};
	}

	public static Folder byKey(String key) {
		for (Folder f : values()) {
			if (f.key.equals(key)) return f;
		}
		return null;
	}
}

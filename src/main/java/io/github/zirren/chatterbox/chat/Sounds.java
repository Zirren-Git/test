package io.github.zirren.chatterbox.chat;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Plays vanilla/modded sound events from the sound registry (used for
 * mention notifications and the sound picker preview).
 */
public final class Sounds {
	private static final Map<String, Long> LAST_PLAYED = new HashMap<>();

	private Sounds() {
	}

	public static SoundEvent resolve(String id) {
		try {
			Identifier identifier = Identifier.parse(id.contains(":") ? id : "minecraft:" + id);
			return BuiltInRegistries.SOUND_EVENT.get(identifier);
		} catch (Exception e) {
			return null;
		}
	}

	public static boolean exists(String id) {
		return resolve(id) != null;
	}

	/** Plays a UI sound; rate-limits identical sounds to avoid spam. */
	public static void play(String id, float pitch, float volume) {
		SoundEvent event = resolve(id);
		if (event == null) return;

		long now = System.currentTimeMillis();
		String key = id + ":" + pitch + ":" + volume;
		Long last = LAST_PLAYED.get(key);
		if (last != null && now - last < 250L) return;
		LAST_PLAYED.put(key, now);

		Minecraft client = Minecraft.getInstance();
		client.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
	}

	public static void play(SoundEvent event, float pitch, float volume) {
		Minecraft client = Minecraft.getInstance();
		client.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
	}

	/** @return true if the id string is a sound event starting with block.note_block. */
	public static boolean isNoteBlockInstrument(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		return path.startsWith("block.note_block.");
	}
}

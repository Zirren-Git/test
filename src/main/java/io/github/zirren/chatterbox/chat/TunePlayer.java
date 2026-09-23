package io.github.zirren.chatterbox.chat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.sounds.SoundEvent;

/**
 * Plays short note-block melodies (alert tunes composed in the Tune Maker).
 * Notes are scheduled against wall-clock time and fired from the client tick,
 * so a tune keeps playing no matter where it was started from.
 */
public final class TunePlayer {

	/** Maximum number of steps a tune may have (grid width of the Tune Maker). */
	public static final int MAX_STEPS = 16;

	private static final class ScheduledNote {
		final SoundEvent sound;
		final float pitch;
		final float volume;
		final long dueMs;

		ScheduledNote(SoundEvent sound, float pitch, float volume, long dueMs) {
			this.sound = sound;
			this.pitch = pitch;
			this.volume = volume;
			this.dueMs = dueMs;
		}
	}

	private static final List<ScheduledNote> QUEUE = new ArrayList<>();

	private static int generation = 0;
	private static long startedMs = 0;
	private static int totalSteps = 0;
	private static int msPerStep = 200;
	private static int debugPlayed = 0;

	private TunePlayer() {
	}

	/**
	 * Schedules a melody for playback. {@code notes} holds one entry per step:
	 * 0–24 = note-block note, anything else = rest.
	 *
	 * @return a token usable with {@link #stop(int)} to cancel only this tune
	 */
	public static int play(String instrumentId, int[] notes, float volume, int msPerStep) {
		List<ScheduledNote> queue = new ArrayList<>();
		SoundEvent sound = Sounds.resolve(instrumentId);
		long now = System.currentTimeMillis();
		int step = Math.max(50, msPerStep);
		if (sound != null) {
			for (int i = 0; i < notes.length && i < MAX_STEPS; i++) {
				int note = notes[i];
				if (note < 0 || note > 24) {
					continue; // rest
				}
				queue.add(new ScheduledNote(sound, Instruments.notePitch(note),
						Math.max(0.05f, volume), now + (long) step * i));
			}
		}
		synchronized (QUEUE) {
			QUEUE.clear();
			QUEUE.addAll(queue);
			generation++;
			startedMs = now;
			totalSteps = Math.min(Math.max(notes.length, 1), MAX_STEPS);
			TunePlayer.msPerStep = step;
			debugPlayed = 0;
		}
		return generation;
	}

	/** Stops the currently playing tune (if any). */
	public static void stop() {
		synchronized (QUEUE) {
			QUEUE.clear();
			totalSteps = 0;
		}
	}

	/** Stops the tune started with this token, but only if it is still current. */
	public static void stop(int token) {
		synchronized (QUEUE) {
			if (token == generation) {
				QUEUE.clear();
				totalSteps = 0;
			}
		}
	}

	public static boolean isPlaying() {
		synchronized (QUEUE) {
			return !QUEUE.isEmpty();
		}
	}

	/**
	 * Progress of the current tune in steps (0 … totalSteps); -1 when idle.
	 * Used by the Tune Maker to draw the playhead.
	 */
	public static float progressSteps() {
		synchronized (QUEUE) {
			if (totalSteps <= 0) {
				return -1f;
			}
			if (QUEUE.isEmpty()) {
				return totalSteps;
			}
			return (System.currentTimeMillis() - startedMs) / (float) msPerStep;
		}
	}

	/** Fires every due note; called once per client tick. */
	public static void tick() {
		List<ScheduledNote> fire = null;
		synchronized (QUEUE) {
			if (QUEUE.isEmpty()) {
				return;
			}
			long now = System.currentTimeMillis();
			for (ScheduledNote note : QUEUE) {
				if (note.dueMs <= now) {
					if (fire == null) {
						fire = new ArrayList<>();
					}
					fire.add(note);
				}
			}
			if (fire != null) {
				QUEUE.removeAll(fire);
			}
		}
		if (fire != null) {
			for (ScheduledNote note : fire) {
				debugPlayed++;
				Sounds.playDirect(note.sound, note.pitch, note.volume);
			}
		}
	}

	/** Number of notes fired by the current tune (selftest only). */
	public static int debugPlayedCount() {
		synchronized (QUEUE) {
			return debugPlayed;
		}
	}
}

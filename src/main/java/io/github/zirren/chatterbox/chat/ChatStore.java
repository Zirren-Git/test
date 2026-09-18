package io.github.zirren.chatterbox.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.authlib.GameProfile;
import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;

import io.github.zirren.chatterbox.config.Config;

/**
 * Client-side chat store: keeps all messages with metadata, tracks the active
 * folder view, unread counters, DM partners and per-view drafts.
 */
public final class ChatStore {
	private static final int MAX_ENTRIES = 5000;

	public static final ChatStore INSTANCE = new ChatStore();

	private final List<ChatEntry> entries = new ArrayList<>();
	private final ChatFileLogger fileLogger = new ChatFileLogger();

	private Folder activeFolder = Folder.ALL;
	private @Nullable String activeDmPartner;

	private final Map<Folder, Integer> unread = new ConcurrentHashMap<>();
	private final Map<String, Integer> dmUnread = new ConcurrentHashMap<>();
	/** lower-case name -> display name */
	private final Map<String, String> dmPartners = new java.util.LinkedHashMap<>();
	private final Map<String, String> drafts = new ConcurrentHashMap<>();
	private final Map<String, List<String>> queuedLines = new ConcurrentHashMap<>();

	private long sessionId = 1;
	private long lastCommandSentAt;
	private long lastChatSentAt;

	/** Context captured from fabric message events, consumed by the addMessage mixin. */
	private @Nullable PendingContext pending;
	/** Guard: true while we ourselves push messages into the vanilla chat hud. */
	private boolean routing;

	private record PendingContext(Component component, boolean chatMessage, @Nullable GameProfile sender,
			@Nullable String chatSenderName) {
	}

	private ChatStore() {
	}

	// ------------------------------------------------------------------
	// Receiving
	// ------------------------------------------------------------------

	public void onEvent(Component component, boolean chatMessage, @Nullable GameProfile sender,
			@Nullable String chatSenderName, boolean overlay) {
		if (overlay) return;
		// Remember context; the vanilla chat hud will call onVanillaAddMessage
		// with the same component instance right after this event.
		this.pending = new PendingContext(component, chatMessage, sender, chatSenderName);
	}

	/**
	 * Called from the ChatComponent mixin when the vanilla chat hud is about to
	 * display a message. Returns true if the caller should proceed (we are
	 * routing our own content), false when the message was consumed by us.
	 */
	public boolean onVanillaAddMessage(Component message) {
		if (routing) return true;

		boolean chatMessage = false;
		GameProfile sender = null;
		String chatSenderName = null;
		PendingContext ctx = pending;
		if (ctx != null && ctx.component() == message) {
			chatMessage = ctx.chatMessage();
			sender = ctx.sender();
			chatSenderName = ctx.chatSenderName();
			pending = null;
		}

		handleIncoming(message, chatMessage, sender, chatSenderName);
		return false;
	}

	private void handleIncoming(Component message, boolean chatMessage, GameProfile sender, String chatSenderName) {
		MessageClassifier.Result result = MessageClassifier.classify(message, chatMessage, sender, chatSenderName,
				lastCommandSentAt, Config.get().commandFeedbackFolder);

		if (result.folder() == Folder.DM && result.dmPartner() != null) {
			addDmPartner(result.dmPartner());
		}

		ChatEntry entry = new ChatEntry(message, result.folder(), result.dmPartner(), Instant.now(),
				result.sender(), message.getString(), result.dmContent(), result.outgoing(), sessionId);

		// Repeat compression
		if (Config.get().compressRepeats && !entries.isEmpty()) {
			ChatEntry last = lastInFolder(result.folder(), result.dmPartner());
			if (entry.isRepeatOf(last)) {
				last.repeatCount++;
				if (Config.get().chatLog) fileLogger.append(last);
				notifyUi(entry, last);
				return;
			}
		}

		entries.add(entry);
		trim();
		if (Config.get().chatLog) fileLogger.append(entry);
		notifyUi(entry, null);
	}

	private void notifyUi(ChatEntry raw, ChatEntry repeated) {
		ChatEntry entry = repeated != null ? repeated : raw;
		ChatEntry changed = repeated != null ? repeated : raw;

		// Mention sound
		MentionWatcher.check(changed);

		// Unread counters
		if (!isVisibleInView(changed, activeFolder, activeDmPartner)) {
			bumpUnread(changed.folder());
			if (changed.folder() == Folder.DM && changed.dmPartner() != null) {
				dmUnread.merge(dmKey(changed.dmPartner()), 1, Integer::sum);
			}
		}

		// Display
		if (isVisibleInView(changed, activeFolder, activeDmPartner)) {
			ChatDisplay.display(changed, repeated != null);
		} else if (repeated != null && wasVisible(repeated)) {
			ChatDisplay.display(changed, true);
		}
	}

	private boolean wasVisible(ChatEntry entry) {
		// if the repeated entry was visible in any view we might be showing
		return isVisibleInView(entry, activeFolder, activeDmPartner);
	}

	private @Nullable ChatEntry lastInFolder(Folder folder, @Nullable String dmPartner) {
		for (int i = entries.size() - 1; i >= 0; i--) {
			ChatEntry e = entries.get(i);
			if (e.folder == folder && java.util.Objects.equals(dmKey(e.dmPartner), dmKey(dmPartner))) {
				return e;
			}
		}
		return null;
	}

	private void trim() {
		while (entries.size() > MAX_ENTRIES) {
			entries.remove(0);
		}
	}

	private void bumpUnread(Folder folder) {
		if (folder == Folder.ALL || folder == Folder.PINNED) return;
		unread.merge(folder, 1, Integer::sum);
	}

	// ------------------------------------------------------------------
	// Views
	// ------------------------------------------------------------------

	public Folder activeFolder() {
		return activeFolder;
	}

	public @Nullable String activeDmPartner() {
		return activeDmPartner;
	}

	public boolean isInDmSubfolder() {
		return activeFolder == Folder.DM && activeDmPartner != null;
	}

	public void switchView(Folder folder, @Nullable String dmPartner) {
		activeFolder = folder;
		activeDmPartner = dmPartner;
		if (folder != Folder.DM || dmPartner == null) {
			unread.remove(folder);
		}
		if (folder == Folder.DM && dmPartner != null) {
			dmUnread.remove(dmKey(dmPartner));
		}
		ChatDisplay.refresh();
	}

	public int unread(Folder folder) {
		return unread.getOrDefault(folder, 0);
	}

	public int dmUnread(String partner) {
		return dmUnread.getOrDefault(dmKey(partner), 0);
	}

	public boolean isVisibleInView(ChatEntry entry, Folder view, @Nullable String viewPartner) {
		return switch (view) {
			case ALL -> true;
			case PINNED -> entry.pinned;
			case DM -> entry.folder == Folder.DM
					&& (viewPartner == null || dmKey(entry.dmPartner).equals(dmKey(viewPartner)));
			default -> entry.folder == view;
		};
	}

	// ------------------------------------------------------------------
	// Entries / pinning
	// ------------------------------------------------------------------

	public List<ChatEntry> entries() {
		return entries;
	}

	public @Nullable ChatEntry byId(long id) {
		for (ChatEntry e : entries) {
			if (e.id == id) return e;
		}
		return null;
	}

	public void togglePin(ChatEntry entry) {
		entry.pinned = !entry.pinned;
		if (activeFolder == Folder.PINNED) {
			ChatDisplay.refresh();
		} else if (entry.pinned) {
			ChatDisplay.refresh(); // pin marker
		}
	}

	// ------------------------------------------------------------------
	// DM partners
	// ------------------------------------------------------------------

	public void addDmPartner(String name) {
		String key = dmKey(name);
		if (!dmPartners.containsKey(key)) {
			dmPartners.put(key, name);
			persistPartners();
		}
	}

	public boolean hasDmPartner(String name) {
		return dmPartners.containsKey(dmKey(name));
	}

	public void removeDmPartner(String name) {
		String key = dmKey(name);
		if (dmPartners.remove(key) != null) {
			persistPartners();
			if (isInDmSubfolder() && dmKey(activeDmPartner).equals(key)) {
				switchView(Folder.DM, null);
			}
		}
	}

	public List<String> dmPartners() {
		return new ArrayList<>(dmPartners.values());
	}

	public void loadPersistedPartners() {
		for (String name : Config.get().dmPartners) {
			if (name != null && !name.isBlank()) {
				dmPartners.putIfAbsent(dmKey(name), name);
			}
		}
	}

	private void persistPartners() {
		Config.get().dmPartners = new ArrayList<>(dmPartners.values());
		Config.get().save();
	}

	private static String dmKey(@Nullable String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT);
	}

	// ------------------------------------------------------------------
	// Drafts & queued lines
	// ------------------------------------------------------------------

	public String viewKey(Folder folder, @Nullable String partner) {
		return folder.key + (partner != null && folder == Folder.DM ? "/" + partner : "");
	}

	public String currentViewKey() {
		return viewKey(activeFolder, activeDmPartner);
	}

	public String getDraft(Folder folder, @Nullable String partner) {
		return drafts.getOrDefault(viewKey(folder, partner), "");
	}

	public void setDraft(Folder folder, @Nullable String partner, String text) {
		String key = viewKey(folder, partner);
		if (text == null || text.isEmpty()) {
			drafts.remove(key);
		} else {
			drafts.put(key, text);
		}
	}

	public List<String> getQueuedLines(Folder folder, @Nullable String partner) {
		return new ArrayList<>(queuedLines.getOrDefault(viewKey(folder, partner), List.of()));
	}

	public void setQueuedLines(Folder folder, @Nullable String partner, List<String> lines) {
		String key = viewKey(folder, partner);
		if (lines.isEmpty()) {
			queuedLines.remove(key);
		} else {
			queuedLines.put(key, new ArrayList<>(lines));
		}
	}

	public void clearDraft(Folder folder, @Nullable String partner) {
		drafts.remove(viewKey(folder, partner));
		queuedLines.remove(viewKey(folder, partner));
	}

	// ------------------------------------------------------------------
	// Sending
	// ------------------------------------------------------------------

	public void noteCommandSent() {
		this.lastCommandSentAt = System.currentTimeMillis();
	}

	public void noteChatSent() {
		this.lastChatSentAt = System.currentTimeMillis();
	}

	// ------------------------------------------------------------------
	// Session
	// ------------------------------------------------------------------

	public long sessionId() {
		return sessionId;
	}

	public void onJoin(String serverInfo) {
		sessionId++;
		clear();
		if (Config.get().chatLog) fileLogger.sessionStart(serverInfo);
		loadPersistedPartners();
	}

	public void onDisconnect() {
		pending = null;
	}

	public void clear() {
		entries.clear();
		unread.clear();
		dmUnread.clear();
		drafts.clear();
		queuedLines.clear();
		ChatDisplay.clear();
	}

	// ------------------------------------------------------------------
	// Routing guard (used by ChatDisplay)
	// ------------------------------------------------------------------

	public boolean beginRouting() {
		boolean was = routing;
		routing = true;
		return was;
	}

	public void endRouting(boolean was) {
		routing = was;
	}

	public Set<Folder> visibleFolders() {
		Set<Folder> out = new LinkedHashSet<>();
		for (Folder f : Folder.values()) {
			if (!Config.get().isFolderHidden(f.key)) {
				out.add(f);
			}
		}
		return out;
	}
}

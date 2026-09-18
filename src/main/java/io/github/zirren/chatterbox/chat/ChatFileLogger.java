package io.github.zirren.chatterbox.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Persists chat history to {@code logs/chatterbox/chat-YYYY-MM-DD.log} and
 * reads it back for cross-session search.
 *
 * <p>Line format (tab separated): {@code HH:mm:ss <TAB> FOLDER <TAB> sender-or-- <TAB> text}</p>
 * <p>Session separators: {@code ===== SESSION HH:mm:ss | server =====}</p>
 */
public final class ChatFileLogger {
	public static final String SESSION_PREFIX = "===== SESSION ";
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final DateTimeFormatter SESSION_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final Path dir;
	private Path currentFile;
	private LocalDate currentDate;

	public ChatFileLogger() {
		this.dir = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("chatterbox");
	}

	private Path fileFor(LocalDate date) {
		return dir.resolve("chat-" + date + ".log");
	}

	public void sessionStart(String serverInfo) {
		try {
			Files.createDirectories(dir);
			LocalDate today = LocalDate.now();
			Path file = fileFor(today);
			if (!Files.exists(file)) {
				Files.writeString(file,
						"# ChatterBox chat log v1\n"
								+ "# line format: HH:mm:ss<TAB>FOLDER<TAB>sender<TAB>text\n",
						StandardCharsets.UTF_8, StandardOpenOption.CREATE);
			}
			Files.writeString(file,
					SESSION_PREFIX + LocalDateTime.now().format(SESSION_TIME) + " | " + (serverInfo == null ? "unknown" : serverInfo) + " =====\n",
					StandardCharsets.UTF_8, StandardOpenOption.APPEND);
			currentFile = file;
			currentDate = today;
		} catch (IOException e) {
			// logging must never break the game
		}
	}

	public void append(ChatEntry entry) {
		try {
			LocalDate date = LocalDate.ofInstant(entry.timestamp, ZoneId.systemDefault());
			Path file = fileFor(date);
			if (!file.equals(currentFile) || currentFile == null) {
				Files.createDirectories(dir);
				if (!Files.exists(file)) {
					Files.writeString(file,
							"# ChatterBox chat log v1\n"
									+ "# line format: HH:mm:ss<TAB>FOLDER<TAB>sender<TAB>text\n",
							StandardCharsets.UTF_8, StandardOpenOption.CREATE);
				}
				currentFile = file;
				currentDate = date;
			}
			String time = LocalDateTime.ofInstant(entry.timestamp, ZoneId.systemDefault()).format(TIME);
			String line = time + "\t" + entry.folder.key
					+ (entry.dmPartner != null ? "/" + entry.dmPartner : "")
					+ "\t" + entry.senderOrDash()
					+ "\t" + escape(entry.text) + "\n";
			Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		} catch (IOException e) {
			// ignore
		}
	}

	/** A parsed log line used by the search screen. */
	public record ParsedLine(LocalDate date, Instant timestamp, Folder folder, String dmPartner,
			String sender, String text, long sessionId) {
	}

	/**
	 * Reads all log files (oldest first) and returns parsed lines with
	 * session ids assigned (new session on every SESSION marker).
	 */
	public static List<ParsedLine> readAll() {
		Path dir = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("chatterbox");
		List<ParsedLine> result = new ArrayList<>();
		if (!Files.isDirectory(dir)) return result;

		List<Path> files;
		try (Stream<Path> stream = Files.list(dir)) {
			files = stream
					.filter(p -> p.getFileName().toString().startsWith("chat-") && p.getFileName().toString().endsWith(".log"))
					.sorted(Comparator.comparing(p -> p.getFileName().toString()))
					.toList();
		} catch (IOException e) {
			return result;
		}

		long session = 0;
		for (Path file : files) {
			LocalDate fileDate;
			try {
				fileDate = LocalDate.parse(file.getFileName().toString().substring(5, 15));
			} catch (Exception e) {
				continue;
			}
			List<String> lines;
			try {
				lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			} catch (IOException e) {
				continue;
			}
			for (String raw : lines) {
				if (raw.startsWith("#")) continue;
				if (raw.startsWith(SESSION_PREFIX)) {
					session++;
					continue;
				}
				String[] parts = raw.split("\t", 4);
				if (parts.length < 4) continue;
				try {
					LocalDateTime time = LocalDateTime.of(fileDate, java.time.LocalTime.parse(parts[0]));
					String folderKey = parts[1];
					String dmPartner = null;
					if (folderKey.contains("/")) {
						int i = folderKey.indexOf('/');
						dmPartner = folderKey.substring(i + 1);
						folderKey = folderKey.substring(0, i);
					}
					Folder folder = Folder.byKey(folderKey);
					if (folder == null) continue;
					String sender = "-".equals(parts[2]) ? null : parts[2];
					result.add(new ParsedLine(fileDate, time.atZone(ZoneId.systemDefault()).toInstant(),
							folder, dmPartner, sender, unescape(parts[3]), session));
				} catch (Exception ignore) {
				}
			}
		}
		return result;
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t");
	}

	private static String unescape(String s) {
		return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
	}
}

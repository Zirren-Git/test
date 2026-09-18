# ChatterBox

A client-side Fabric mod for **Minecraft 26.2 & 26.3** that turns the chat into a
filing cabinet — folders, DM threads, mention sounds, search, pins, shortcuts and
more — while keeping the vanilla look.

> Works with Fabric Loader **0.19.3 – 0.19.5** and requires **Fabric API**.
> **Mod Menu** is optional (recommended for the settings screen).

## Features

### Chat folders
Every message is sorted into a folder, switchable with the tab bar at the **top of
the chat screen** (also `Ctrl+1`…`Ctrl+9`):

| Folder | Contents |
|---|---|
| **All** | everything, joined |
| **Chat** | normal player messages |
| **DMs** | whispers, with a sub-tab per conversation |
| **Server** | join/leave, admin broadcasts, announcements |
| **Commands** | command output/feedback |
| **Deaths** | death messages |
| **Pinned** | messages you pinned |

Which folders appear in the bar is configurable. Tabs show unread counters while
you're in another folder.

### DM threads
- Every player that has ever whispered with you gets their **own sub-folder**
  inside the DMs folder (plus a **+** button to add one manually).
- **Writing inside someone's sub-folder automatically whispers to them** — your
  text is sent as `/msg <player> …` (`/w`/`/tell` selectable in the settings), so
  nobody else can see it.
- Inside the DM folder, whispers are rendered with **normal chat colors** instead
  of the vanilla grey italics.

### Mention sounds
When a configured word or username is said in chat, a sound of your choice
plays. Rules are managed in the settings: each rule has a word (`{you}` matches
your own username), a sound, volume and pitch. The sound picker defaults to the
**note block instruments** (harp, pling, bass, bell…) and can be switched to list
**every registered sound event** in the game. Each entry has a **▶ preview**
button; the pitch slider covers **all notes in between** (note 0–24, like note
blocks).

### Search
Press **K** (rebindable) to search the chat. Search is client-side, case
insensitive, and — if enabled — also covers **previous sessions** from the chat
log, with separators between sessions. Click a result to jump to its folder.

### Chat log
Chat history is written to `logs/chatterbox/chat-<date>.log` (one file per day)
with session markers, e.g.:

```
===== SESSION 2026-09-18 19:03:45 | hypixel.net =====
19:03:46	CHAT	<Notch>	hello world
19:03:47	DM	Steve	psst, look at this
```

### Pins
Hover any message while the chat is open and press **Ctrl+P** (or the rebindable
*Pin Hovered Message* key) to pin or unpin it. Pinned messages carry a 📌 marker
and live in their own folder.

### Repeated messages
Consecutive identical messages are compressed into one entry with a grey
`(x3)`-style counter.

### Emojis & shortcuts (unicode)
Type `{shortcut}` in chat and it's expanded when sending. All text shortcuts are
editable (add/edit/remove) in the settings. Unknown `{tokens}` are left alone.

**Live-value shortcuts**

| Token | Expands to |
|---|---|
| `{pos}`, `{coords}` | `[x, y, z]` |
| `{y}` | y level |
| `{dim}` | dimension |
| `{facing}` | N/E/S/W |
| `{biome}` | biome |
| `{hp}` / `{food}` / `{xp}` | health / hunger / level |
| `{ping}` | your latency |
| `{time}` | in-game time |
| `{date}` / `{clock}` | real date / time |
| `{server}` | server address |
| `{player}`, `{me}` | your username |
| `{held}` | held item |

**Text shortcuts (kaomoji)**: `{shrug}` ¯\\_(ツ)\_/¯ · `{tableflip}` · `{unflip}` ·
`{disapprove}` ಠ_ಠ · `{lenny}` · `{cool}` · `{cry}` · `{angry}` · `{bear}` ·
`{cat}` · `{hug}`

**Text shortcuts (symbols)**: `{heart}` ❤ · `{star}` ★ · `{sparkle}` ✦ ·
`{skull}` ☠ · `{check}` ✔ · `{cross}` ✘ · `{music}` ♪ · `{note}` ♫ · `{bolt}` ⚡ ·
`{sun}` ☀ · `{moon}` ☾ · `{cloud}` ☁ · `{snow}` ❄ · `{flower}` ✿ · `{crown}` ♛ ·
`{diamond}` ◆ · `{sword}` ⚔ · `{pick}` ⛏ · `{up}` `{down}` `{left}` `{right}`

> **Font note:** most of these render in the vanilla font (via Unifont). A few
> newer emoji/codepoints may show as missing glyphs depending on your resource
> pack and font mods — all shortcuts are editable if your setup doesn't like one.

### Drafts & multiline
- Closing the chat without sending keeps your draft (per folder) — reopen the
  chat and it's still there.
- **Shift+Enter** queues another line (queued lines are shown above the input);
  **Enter** sends all lines in order.

### Timestamps
Optional `[HH:mm]` / `[HH:mm:ss]` prefixes, plus a **two-line layout** where the
timestamp and sender sit on their own line and the message is indented below —
wrapped lines never slide under the name or timestamp.

## Settings
Via **Mod Menu → ChatterBox → Configure**, or the *Open ChatterBox Settings*
keybind. Everything above is configurable: folder visibility, unread badges,
timestamps, compression, DM colors, mention rules, sound picker scope,
shortcuts, whisper command, log files, cross-session search, drafts, and
command-output classification.

## Version matrix

| Minecraft | Fabric Loader | Fabric API | Mod Menu |
|---|---|---|---|
| 26.3 | 0.19.3 – 0.19.5 | 0.161.0+26.3 | 21.0.0-beta.1+ |
| 26.2 | 0.19.3 – 0.19.5 | 0.161.0+26.2 | 20.0.2+ |

One jar per Minecraft version; it runs on any of the three Loader versions above.

## Building from source

```bash
./gradlew build                 # defaults to Minecraft 26.3
./gradlew build -Pminecraft_version=26.2 -Pminecraft_dep='~26.2' \
                -Pfabric_api_version=0.161.0+26.2 -Pmodmenu_version=20.0.2 \
                -Ploader_version=0.19.3
```

Requires Java 25. CI builds both versions on all three loaders.

## How it works (for the curious)

ChatterBox is client-side only. It intercepts messages as they reach the vanilla
chat HUD, classifies them (translation keys on vanilla servers, pattern matching
as fallback), stores them with metadata, and re-renders the active folder into
the vanilla chat component — so what you see is still rendered by the game
itself, vanilla look included.

## License

MIT

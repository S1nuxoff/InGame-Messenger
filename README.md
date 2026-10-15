<div align="center">

<img src="docs/images/banner.png" alt="InGame Messenger: a real messenger inside the Minecraft chat" width="100%">

**English** · [Українська](README.uk.md) · [Русский](README.ru.md)

[![Download](https://img.shields.io/github/v/release/S1nuxoff/InGame-Messenger?style=for-the-badge&label=Download&color=2EA043)](https://github.com/S1nuxoff/InGame-Messenger/releases/latest)

[![Build](https://github.com/S1nuxoff/InGame-Messenger/actions/workflows/build.yml/badge.svg)](https://github.com/S1nuxoff/InGame-Messenger/actions/workflows/build.yml)
[![Tested in game](https://img.shields.io/badge/tested-in%20game-2EA043)](https://s1nuxoff.github.io/InGame-Messenger-tests/)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

[Overview](#overview) · [Features](#features) · [Installation](#installation) · [Versions](#supported-versions) · [FAQ](#faq) · [Server guide](docs/server.md) · [Contributing](CONTRIBUTING.md)

<table><tr><td align="center" width="104"><img src="docs/images/platforms/minecraft.png" width="36" height="36" alt=""><br><sub><b>1.19.2&nbsp;–&nbsp;26.3</b></sub></td><td align="center" width="84"><img src="docs/images/platforms/fabric.png" width="36" height="36" alt=""><br><sub><b>Fabric</b></sub></td><td align="center" width="84"><img src="docs/images/platforms/quilt.png" width="36" height="36" alt=""><br><sub><b>Quilt</b></sub></td><td align="center" width="84"><img src="docs/images/platforms/neoforge.png" width="36" height="36" alt=""><br><sub><b>NeoForge</b></sub></td><td align="center" width="84"><img src="docs/images/platforms/forge.png" width="36" height="36" alt=""><br><sub><b>Forge</b></sub></td><td align="center" width="84"><img src="docs/images/platforms/paper.png" width="36" height="36" alt=""><br><sub><b>Paper</b></sub></td><td align="center" width="84"><img src="docs/images/platforms/spigot.svg" width="36" height="36" alt=""><br><sub><b>Spigot</b></sub></td><td align="center" width="84"><img src="docs/images/platforms/purpur.png" width="36" height="36" alt=""><br><sub><b>Purpur</b></sub></td><td align="center" width="84"><img src="docs/images/platforms/folia.png" width="36" height="36" alt=""><br><sub><b>Folia</b></sub></td></tr></table>

</div>

## Overview

InGame Messenger turns the Minecraft chat into a messenger, right where the chat is. The server's chat stays the place
where everyone talks and where commands are typed; it just gets bubbles with faces, stickers, voice, pictures,
reactions and replies, with private chats next to it. No accounts, no cloud: everything lives on your server.

<table>
<tr>
<td width="33%" valign="top">

**Players**<br>
Install the mod. On a server without it nothing changes: the chat stays the normal Minecraft chat.

</td>
<td width="33%" valign="top">

**Servers**<br>
Run the mod (NeoForge, Fabric, Forge) or the plugin (Paper, Spigot, Purpur, Folia). The client's and the server's
loader don't have to match.

</td>
<td width="33%" valign="top">

**Players without the mod**<br>
Can still join and chat. Stickers, voice messages, pictures and polls reach them as short text lines.

</td>
</tr>
</table>

## See it in action

https://github.com/user-attachments/assets/48dfbc2b-96dd-4af4-9ba5-352966471e95

## Features

### <img src="docs/images/icons/writable_book.png" width="24" height="24" align="top" alt=""> A chat that feels like a messenger

<table>
<tr>
<td width="50%" valign="middle">

- **Bubbles** with the sender's face, name and time; a player's messages are grouped, with a tail on the last one.
- **The server's own lines** (joins, deaths, announcements, command answers) come with its name and icon.
- **Names as the server shows them**: ranks and colors from the server or LuckPerms/Vault.
- **"Is typing..."**, date separators, smooth scrolling.

</td>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/conversation.png" width="420" alt=""></td>
</tr>
</table>

### <img src="docs/images/icons/cherry_sign.png" width="24" height="24" align="top" alt=""> Replies and reactions

<table>
<tr>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/msg-mia-reply.png" width="400" alt=""><br><br><img src="docs/images/features/msg-mine-lava.png" width="400" alt=""></td>
<td width="50%" valign="middle">

- **Reply** with a quote; click it to jump to the original, however far back.
- **React** with built-in reactions or the server's emoji. Double-click a message for a heart.
- Edit and delete your own messages.

</td>
</tr>
</table>

### <img src="docs/images/icons/bell.png" width="24" height="24" align="top" alt=""> Mentions

<table>
<tr>
<td width="50%" valign="middle">

- **@mentions** with autocompletion.
- Mentions of you are highlighted and announced above the hotbar, even with the chat closed.

</td>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/msg-mention-mia.png" width="400" alt=""></td>
</tr>
</table>

### <img src="docs/images/icons/name_tag.png" width="24" height="24" align="top" alt=""> Private chats

<table>
<tr>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/private-chat.png" width="420" alt=""></td>
<td width="50%" valign="middle">

- One-to-one chats in a list next to the server's chat, with unread counters and notifications.
- **Read receipts**: one check when sent, two when read.
- **Commands from anywhere**: typed in a private chat, the answer appears right there, visible only to you.

</td>
</tr>
</table>

### <img src="docs/images/icons/slime_ball.png" width="24" height="24" align="top" alt=""> Stickers and emoji

<table>
<tr>
<td width="50%" valign="middle">

- **Packs from the server**: PNG, JPG, animated GIF.
- A panel with recent, favourites and every pack; hold the chat key for a quick picker.
- The server's emoji right in the text and in reactions.

</td>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/sticker-panel.png" width="420" alt=""></td>
</tr>
</table>

### <img src="docs/images/icons/painting.png" width="24" height="24" align="top" alt=""> Pictures

<table>
<tr>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/msg-picture.png" width="360" alt=""></td>
<td width="50%" valign="middle">

- Up to four per message, with a caption.
- From a file, an in-game screenshot or the clipboard (Ctrl+V).
- Shared coordinates become a card; with JourneyMap a click makes a waypoint.

</td>
</tr>
</table>

### <img src="docs/images/icons/goat_horn.png" width="24" height="24" align="top" alt=""> Voice messages

<table>
<tr>
<td width="50%" valign="middle">

- **Hold the microphone button** to record, release to send.
- Waveform, 1x/1.5x/2x speed, and a mini player that keeps playing while you move.
- Opus, no other mod needed.

</td>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/msg-voice-mia.png" width="400" alt=""><br><br><img src="docs/images/features/msg-voice-mine.png" width="400" alt=""></td>
</tr>
</table>

### <img src="docs/images/icons/paper.png" width="24" height="24" align="top" alt=""> Polls

<table>
<tr>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/poll-window.png" width="380" alt=""><br><br><img src="docs/images/features/msg-poll.png" width="380" alt=""></td>
<td width="50%" valign="middle">

- One or several answers, results live.
- Written in a window right from the chat.

</td>
</tr>
</table>

### <img src="docs/images/icons/spyglass.png" width="24" height="24" align="top" alt=""> Search, settings, languages
- Search through every chat you can see (Ctrl+F); a result opens its chat at the message.
- Size and scale, sticker size, microphone, output device, gain, noise gate.
- 40 languages.

### <img src="docs/images/icons/command_block_minecart.png" width="24" height="24" align="top" alt=""> For server owners
- **Mutes** in the server's chat, in private chats or both, for a time or for good. They also stop `/msg` and `/me`;
  mutes from EssentialsX, LiteBans and AdvancedBan count too (plugin).
- **Chat plugins** such as EssentialsX Chat: each line shows once, with the rank and colors the server gives it.
- **Storage**: history in H2 by default (nothing to set up), or MySQL, MariaDB, PostgreSQL. Voice messages and pictures
  in the world folder or any S3-compatible storage; old files are cleaned up automatically.
- **Your server's name and icon** in the messenger (the MOTD and the server list icon by default).
- **One config file**, reloadable in game.

## Installation

### Players
1. Download the jar for your Minecraft version and loader from [Releases](https://github.com/S1nuxoff/InGame-Messenger/releases/latest).
2. Put it in your `mods` folder. On Fabric and Quilt, also install [Fabric API](https://modrinth.com/mod/fabric-api).
3. Join a server that has the mod or the plugin.

### Servers
1. **Modded server:** the mod's jar in `mods` (plus Fabric API on Fabric and Quilt).<br>
   **Paper, Spigot, Purpur, Folia:** the plugin's jar in `plugins`.
2. Start the server once. It creates `config/ingamemessenger/` (plugin: `plugins/InGameMessenger/`) with the config
   and the folders for sticker and emoji packs.
3. Add sticker packs if you like, then run `/messenger reload`.

The defaults need no changes. The [server guide](docs/server.md) covers every option, external databases, S3, sticker
packs, commands and permissions.

## Supported versions

<img src="docs/images/platforms/minecraft.png" width="20" height="20" alt=""> **22 Minecraft versions, from 1.19.2 to 26.3.**

| Platform | Versions |
|---|---|
| <img src="docs/images/platforms/fabric.png" width="18" height="18" alt=""> **Fabric** · <img src="docs/images/platforms/quilt.png" width="18" height="18" alt=""> **Quilt** | 1.19.2 · 1.20 · 1.20.1 · 1.20.3 · 1.20.4 · 1.20.5 · 1.20.6 · 1.21 · 1.21.1 · 1.21.4 – 1.21.11 · 26.1 – 26.3 |
| <img src="docs/images/platforms/neoforge.png" width="18" height="18" alt=""> **NeoForge** | 1.20.6 · 1.21 · 1.21.1 · 1.21.4 – 1.21.11 · 26.1 – 26.3 |
| <img src="docs/images/platforms/forge.png" width="18" height="18" alt=""> **Forge** | 1.19.2 · 1.20.1 · 1.20.4 · 1.20.6 · 1.21.1 · 1.21.4 · 1.21.5 · 1.21.8 · 1.21.10 · 1.21.11 · 26.1.2 · 26.2 · 26.3 |
| <img src="docs/images/platforms/paper.png" width="18" height="18" alt=""> **Paper** · <img src="docs/images/platforms/spigot.svg" width="18" height="18" alt=""> **Spigot** · <img src="docs/images/platforms/purpur.png" width="18" height="18" alt=""> **Purpur** | the plugin, 1.20.1 and newer |
| <img src="docs/images/platforms/folia.png" width="18" height="18" alt=""> **Folia** | the plugin, every Folia release from 1.21.4 |

<details>
<summary><b>Full table</b> (every version, loader and the Java it needs)</summary>

| Minecraft | <img src="docs/images/platforms/neoforge.png" width="16" height="16" alt=""><br>NeoForge | <img src="docs/images/platforms/fabric.png" width="16" height="16" alt=""><br>Fabric | <img src="docs/images/platforms/quilt.png" width="16" height="16" alt=""><br>Quilt | <img src="docs/images/platforms/forge.png" width="16" height="16" alt=""><br>Forge | <img src="docs/images/platforms/paper.png" width="16" height="16" alt=""> <img src="docs/images/platforms/spigot.svg" width="16" height="16" alt=""> <img src="docs/images/platforms/purpur.png" width="16" height="16" alt=""><br>Paper, Spigot, Purpur | <img src="docs/images/platforms/folia.png" width="16" height="16" alt=""><br>Folia | <img src="docs/images/platforms/java.svg" width="16" height="16" alt=""><br>Java |
|-----------|:--------:|:------:|:-----:|:-----:|:------------------------------:|:--------------:|:----:|
| 1.19.2    |    —     |   ✓    |   ✓   |   ✓   |               —                |       —        |  17  |
| 1.20      |    —     |   ✓    |   ✓   |   —   |               —                |       —        |  17  |
| 1.20.1    |    —     |   ✓    |   ✓   |   ✓   |               ✓                |       —        |  17  |
| 1.20.3    |    —     |   ✓    |   ✓   |   —   |               —                |       —        |  17  |
| 1.20.4    |    —     |   ✓    |   ✓   |   ✓   |               ✓                |       —        |  17  |
| 1.20.5    |    —     |   ✓    |   ✓   |   —   |               —                |       —        |  21  |
| 1.20.6    |    ✓     |   ✓    |   ✓   |   ✓   |               ✓                |       —        |  21  |
| 1.21      |    ✓     |   ✓    |   ✓   |   —   |               —                |       —        |  21  |
| 1.21.1    |    ✓     |   ✓    |   ✓   |   ✓   |               ✓                |       —        |  21  |
| 1.21.4    |    ✓     |   ✓    |   ✓   |   ✓   |               ✓                |       ✓        |  21  |
| 1.21.5    |    ✓     |   ✓    |   ✓   |   ✓   |               ✓                |       ✓        |  21  |
| 1.21.6    |    ✓     |   ✓    |   ✓   |   —   |               ✓                |       ✓        |  21  |
| 1.21.7    |    ✓     |   ✓    |   ✓   |   —   |               ✓                |       ✓        |  21  |
| 1.21.8    |    ✓     |   ✓    |   ✓   |   ✓   |               ✓                |       ✓        |  21  |
| 1.21.9    |    ✓     |   ✓    |   ✓   |   —   |               ✓                |       —        |  21  |
| 1.21.10   |    ✓     |   ✓    |   ✓   |   ✓   |               ✓                |       —        |  21  |
| 1.21.11   |    ✓     |   ✓    |   ✓   |   ✓   |               ✓                |       ✓        |  21  |
| 26.1      |    ✓     |   ✓    |   ✓   |   —   |               ✓                |       ✓        |  25  |
| 26.1.1    |    ✓     |   ✓    |   ✓   |   —   |               ✓                |       ✓        |  25  |
| 26.1.2    |    ✓     |   ✓    |   ✓   |   ✓   |               ✓                |       ✓        |  25  |
| 26.2      |    ✓     |   ✓    |   ✓   |   ✓   |               ✓                |       ✓        |  25  |
| 26.3      |    ✓     |   ✓    |   ✓   |   ✓   |               ✓                |       —        |  25  |

- Fabric builds need [Fabric API](https://modrinth.com/mod/fabric-api). On Quilt, use the Fabric jar with Fabric API.
- Some versions share a jar: the 1.20.1 Fabric jar also runs on 1.20, the 1.20.4 one on 1.20.3, the 1.20.6 one on
  1.20.5, the 1.21.1 Fabric and NeoForge jars on 1.21; one jar covers 1.21.6 – 1.21.8, another 1.21.9 – 1.21.10,
  another 26.1 – 26.1.2. Forge builds are made for one Minecraft release each, as Forge itself is.
- Each jar installs only on the versions it is made for. Not supported yet: 1.19.3, 1.19.4, 1.20.2, 1.21.2, 1.21.3.

</details>

> [!NOTE]
> Every version is tested in the game: the mod plays a scenario by itself against a test server (messages, stickers,
> pictures, voice, polls, private chats, mutes) and every step is screenshotted.
> [See the latest test runs.](https://s1nuxoff.github.io/InGame-Messenger-tests/)

## Controls

<details>
<summary>Show the controls</summary>

| Action | How |
|---|---|
| Open the chat | Tap the chat key (T) |
| Quick sticker picker | Hold the chat key, click a sticker, release |
| Sticker panel | Smiley button in the input; right-click it to pin the panel open |
| Favourite a sticker / preview it | Right-click it / press and hold it in the panel |
| React | Hover the message and click the reaction button, or double-click the message |
| Reply, copy, edit, delete | Right-click the message |
| Jump to the quoted message | Click the quote |
| Player menu (private chat, mention) | Click a player's face |
| Voice message | Hold the microphone button (shown when the input is empty) |
| Attach a picture, location, poll | Paper clip button |
| Paste a picture | Ctrl+V |
| Search | Ctrl+F, or the magnifier at the bottom of the chat list |
| Settings | Gear button at the bottom of the chat list |

</details>

## FAQ

<details>
<summary><b>Does every player need the mod?</b></summary>

No. Players without it join and chat as usual; stickers, voice messages, pictures and polls reach them as short text
lines. Only players with the mod see the messenger.
</details>

<details>
<summary><b>Do I need it on the server?</b></summary>

Yes: the mod on a modded server, or the plugin on Paper, Spigot, Purpur or Folia. On a server without it, the mod
leaves the chat alone.
</details>

<details>
<summary><b>Does it work with chat plugins, ranks and mutes?</b></summary>

Yes. Lines from chat plugins such as EssentialsX Chat show once, with the rank and colors the server gives them; ranks
come from the server or LuckPerms/Vault, and mutes from EssentialsX, LiteBans and AdvancedBan count too.
</details>

<details>
<summary><b>Velocity or BungeeCord?</b></summary>

Install it on the servers behind the proxy, nothing on the proxy. Each server keeps its own chat.
</details>

<details>
<summary><b>Who can read the messages?</b></summary>

Messages, private chats included, are stored on the server in plain form, so the server's operators can read them, as
on any chat platform. Voice messages and pictures are kept for a set number of days, then deleted.
</details>

## Contributing

Bug reports, ideas, translations and pull requests are welcome. To build it yourself or send a change, see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Free software under the [GNU General Public License v3.0](LICENSE), with an attribution term in
[NOTICE.md](NOTICE.md). Third-party assets and libraries are listed in [CREDITS.md](CREDITS.md).

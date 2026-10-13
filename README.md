<div align="center">

<img src="src/main/resources/ingamemessenger.png" alt="InGame Messenger" width="128">

# InGame Messenger

A messenger inside the Minecraft chat.

[![Build](https://github.com/S1nuxoff/InGame-Messenger/actions/workflows/build.yml/badge.svg)](https://github.com/S1nuxoff/InGame-Messenger/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.19.2%20to%2026.3-62B47A)](#supported-versions)
[![NeoForge](https://img.shields.io/badge/NeoForge-supported-E68C37)](#supported-versions)
[![Fabric](https://img.shields.io/badge/Fabric-supported-C6BBA5)](#supported-versions)
[![Quilt](https://img.shields.io/badge/Quilt-supported-8A5CF5)](#supported-versions)
[![Forge](https://img.shields.io/badge/Forge-supported-1E2D3D)](#supported-versions)
[![Paper](https://img.shields.io/badge/Paper%20%C2%B7%20Spigot%20%C2%B7%20Folia-plugin-F7CF0D)](#server-plugin)

[Features](#features) · [Installation](#installation) · [Server guide](docs/server.md) · [Building](#building-from-source) · [Contributing](CONTRIBUTING.md)

</div>

InGame Messenger replaces the vanilla chat with a full messenger: message bubbles with player faces, stickers and
custom emoji, reactions, replies, private chats, pictures, voice messages, polls and searchable history. It runs on
NeoForge, Fabric, Quilt and Forge, from Minecraft 1.19.2 to 26.3, and the server side also runs as a plugin on Paper,
Spigot, Purpur and Folia.

Players install the mod; the server runs either the mod or the plugin. Clients and servers on different loaders work
together.

## Features

**Chat**
- Messages are shown as bubbles with the sender's face, name and time. Consecutive messages from one player are
  grouped. Date separators between days.
- Replies with a quote of the original message. Click the quote to jump to it, even if it is far back in the history.
- Editing and deleting your own messages. Operators can delete any message.
- Reactions: eight built-in ones plus any emoji from the server's emoji packs. Double-click a message to react with a
  heart.
- @mentions with autocompletion. Mentions of you are highlighted and announced above the hotbar.
- "Player is typing..." indicator.
- Read receipts: one check when sent, two when someone has read it. Hover to see who.
- Smooth scrolling, new messages slide in, the chat can be placed on the right side of the screen.

**Private chats**
- One-to-one conversations in a chat list next to the main chat, with unread counters and notifications.
- Private messages are stored on the server like public ones and are only delivered to the two participants.

**Stickers and emoji**
- Sticker and emoji packs are provided by the server (PNG, JPG and animated GIF).
- Sticker panel with tabs for recent stickers, favourites, emoji and every pack.
- Hold the chat key to open a quick sticker picker without the text field.
- Emoji can be used inline in text and as reactions.

**Media**
- Pictures: up to four per message with a caption. Attach a file, a screenshot from the game, or paste an image from
  the clipboard with Ctrl+V. Pictures are compressed on the client before upload.
- Voice messages: hold the microphone button to record, release to send, slide left to cancel. Waveform, playback
  speed (1x, 1.5x, 2x), a mini player that keeps playing while you move around. Encoded with Opus, no other mod needed.
- Shared coordinates are shown as a card. With JourneyMap installed, clicking it creates a waypoint.

**More**
- Polls with single or multiple choice.
- Full-text search through the whole chat history (Ctrl+F).
- Optional private chat with Google Gemini, using the player's own free API key. Requests go directly from the
  player's game to Google, never through the server.
- Settings for chat size and scale, sticker size, microphone and output device, gain, noise gate and more.
- Translated into 40 languages: English, Arabic, Belarusian, Bulgarian, Chinese (Simplified and Traditional), Croatian,
  Czech, Danish, Dutch, Estonian, Finnish, French, German, Greek, Hebrew, Hungarian, Indonesian, Italian, Japanese,
  Kazakh, Korean, Latvian, Lithuanian, Norwegian, Polish, Portuguese (Brazil and Portugal), Romanian, Russian, Serbian,
  Slovak, Slovenian, Spanish (Spain and Mexico), Swedish, Thai, Turkish, Ukrainian and Vietnamese.

**For server owners**
- Runs as a mod on NeoForge, Fabric and Forge, or as a plugin on Paper, Spigot, Purpur and Folia.
- Players without the mod can still join: they see stickers, voice messages, pictures and polls as short text lines.
- History is stored in a database: H2 by default (a file in the world folder, nothing to configure), or MySQL,
  MariaDB and PostgreSQL. Drivers for external databases are downloaded automatically on first start.
- Voice messages and pictures are stored in the world folder or in any S3-compatible storage (AWS S3, Cloudflare R2,
  Backblaze B2, MinIO, Wasabi). Pictures can also be hosted on ImgBB or Catbox.
- Automatic cleanup of old voice messages and pictures.
- One configuration file, reloadable in game.

## Supported versions

| Minecraft          | NeoForge | Fabric | Quilt |    Forge     | Paper, Spigot, Purpur (plugin) | Folia (plugin) | Java |
|--------------------|:--------:|:------:|:-----:|:------------:|:------------------------------:|:--------------:|:----:|
| 1.19.2             |    —     |   ✓    |   ✓   |      ✓       |               —                |       —        |  17  |
| 1.20.1             |    —     |   ✓    |   ✓   |      ✓       |               ✓                |       —        |  17  |
| 1.21.1             |    ✓     |   ✓    |   ✓   |      ✓       |               ✓                |       —        |  21  |
| 1.21.4             |    ✓     |   ✓    |   ✓   |      ✓       |               ✓                |       ✓        |  21  |
| 1.21.5             |    ✓     |   ✓    |   ✓   |      ✓       |               ✓                |       ✓        |  21  |
| 1.21.6 – 1.21.8    |    ✓     |   ✓    |   ✓   | 1.21.8 only  |               ✓                |       ✓        |  21  |
| 1.21.9 – 1.21.10   |    ✓     |   ✓    |   ✓   | 1.21.10 only |               ✓                |       —        |  21  |
| 1.21.11            |    ✓     |   ✓    |   ✓   |      ✓       |               ✓                |       ✓        |  21  |
| 26.1 – 26.1.2      |    ✓     |   ✓    |   ✓   | 26.1.2 only  |               ✓                |       ✓        |  25  |
| 26.2               |    ✓     |   ✓    |   ✓   |      ✓       |               ✓                |       ✓        |  25  |
| 26.3               |    ✓     |   ✓    |   ✓   |      ✓       |               ✓                |       —        |  25  |

Fabric builds require [Fabric API](https://modrinth.com/mod/fabric-api).

On Quilt, use the Fabric jar together with Fabric API (not Quilted Fabric API, which stops at 1.21).

Forge builds are made for one Minecraft release each, as Forge itself is.

### Server plugin

One plugin jar covers every version in the table from 1.20.1. Folia is marked where Folia itself has a release. Players join a
server with the plugin with the mod on any loader.

Velocity and BungeeCord work too: install the mod or the plugin on the servers behind the proxy, nothing on the proxy.
Each server keeps its own chat. See [Proxies](docs/server.md#proxies-velocity-bungeecord).

Minecraft 1.18.2 and 1.16.5 are planned.

## Installation

### Players

1. Download the jar that matches your Minecraft version and mod loader.
2. Put it in the `mods` folder of your game. On Fabric and Quilt, also install Fabric API (Quilt uses the Fabric
   jar).
3. Join a server that has the mod or the plugin. Its loader doesn't have to match yours.

You can keep the mod installed everywhere: on servers without it the chat stays the normal Minecraft chat. Servers
with the mod let in players without it too; they see stickers, voice messages and pictures as short text lines.

### Servers

1. Modded server: put the mod's jar in the `mods` folder (plus Fabric API on Fabric and Quilt). Paper, Spigot, Purpur or Folia:
   put the plugin's jar in the `plugins` folder.
2. Start the server once. It creates `config/ingamemessenger/` (the plugin: `plugins/InGameMessenger/`) with the
   configuration file and the folders for sticker and emoji packs.
3. Add sticker packs if you want them, adjust the configuration if needed, then run `/messenger reload`.

The defaults work without any changes: history in an H2 database in the world folder, voice messages and pictures in
the world folder.

See the [server guide](docs/server.md) for the full configuration reference, external databases, S3 storage, sticker
packs and commands.

## Controls

| Action                         | How                                                                      |
|--------------------------------|--------------------------------------------------------------------------|
| Open the chat                  | Tap the chat key (T)                                                     |
| Quick sticker picker           | Hold the chat key, click a sticker, release the key                      |
| Sticker panel                  | Smiley button in the input field; right-click it to pin the panel open  |
| Add a sticker to favourites    | Right-click it in the panel                                              |
| Preview a sticker              | Press and hold it in the panel                                           |
| React to a message             | Hover the message and click the reaction button, or double-click it      |
| Reply, copy, edit, delete      | Right-click the message                                                  |
| Player menu                    | Click a player's face or name                                            |
| Voice message                  | Hold the microphone button (shown when the input is empty)               |
| Attach a picture, location, poll | Paper clip button                                                      |
| Paste a picture                | Ctrl+V with an image in the clipboard                                    |
| Search                         | Ctrl+F or the magnifier above the chat                                   |
| Settings                       | Gear button at the bottom of the chat list                               |

## Privacy

Messages, including private chats, are stored on the server in plain form. Server operators can read them, the same
way as with any other chat platform. Voice messages and pictures are kept for a configurable number of days and then
deleted.

The optional Gemini chat runs entirely on the player's computer: the API key and the conversation never reach the
server.

## Building from source

Requires JDK 21 or newer. JDK 25 is downloaded automatically for the 26.x builds.

```sh
./gradlew buildAll
```

Jars are written to `versions/<minecraft>-<loader>/build/libs/`. To build a single target, for example
`./gradlew :1.21.1-neoforge:build`. `./gradlew :plugin:build` builds the server plugin into `plugin/build/libs/`.
`./gradlew :core:test` runs the unit tests.

The project uses [Stonecutter](https://stonecutter.kikugie.dev/) to build every Minecraft version and loader from one
source tree. See [CONTRIBUTING.md](CONTRIBUTING.md) for how the code is organized.

## Contributing

Bug reports, feature requests, translations and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md)
first.

## License

InGame Messenger is free software, licensed under the [GNU General Public License v3.0](LICENSE), with an additional
attribution term described in [NOTICE.md](NOTICE.md).

Third-party assets and libraries are listed in [CREDITS.md](CREDITS.md).

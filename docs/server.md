# Server guide

This guide covers installing InGame Messenger on a server, every configuration option, external databases, S3
storage, sticker packs and commands.

## Contents

- [Installation](#installation)
- [Files](#files)
- [Configuration](#configuration)
- [Database](#database)
- [File storage](#file-storage)
- [Pictures](#pictures)
- [Sticker and emoji packs](#sticker-and-emoji-packs)
- [Players without the mod](#players-without-the-mod)
- [Proxies (Velocity, BungeeCord)](#proxies-velocity-bungeecord)
- [Commands](#commands)
- [Backups](#backups)
- [Troubleshooting](#troubleshooting)

## Installation

1. On a modded server, download the jar for your Minecraft version and loader (NeoForge, Fabric or Forge) and put it in
   `mods/`. On Fabric, Fabric API is required. Quilt runs the Fabric jar, also with Fabric API.

   On Paper, Spigot, Purpur or Folia, put the plugin's jar in `plugins/`. One jar covers 1.20.1, 1.20.4, 1.21.1,
   1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3 (on Folia,
   the versions marked in the README's table); it needs Java 17 or newer. On first start the server downloads the plugin's libraries (the H2 database and the Opus
   codec) from Maven Central.
2. Start the server. It creates `config/ingamemessenger/` with `server.toml` and the `stickers/` and `emoji/`
   folders. With the plugin this folder is `plugins/InGameMessenger/`; everything in it works the same.
3. Players install the same version of the mod on their client to get the messenger, on any loader: a NeoForge client
   works with a Fabric server or with the plugin, and so on. See
   [Players without the mod](#players-without-the-mod) for everyone else.

Nothing else is required. Without changes the server stores the history in an H2 database inside the world folder
and keeps voice messages and pictures next to it.

No extra ports are opened: all traffic goes through the normal Minecraft connection.

## Files

```
config/ingamemessenger/
  server.toml          server configuration
  stickers/            sticker packs, one folder per pack
  emoji/               emoji packs, one folder per pack
  libs/                database drivers downloaded on demand

<world>/ingamemessenger/
  chat.mv.db           H2 database (when database.type = "h2")
  voice/               voice messages (when storage.type = "local")
  images/              pictures (when storage.type = "local" and images.provider = "server")
```

With the plugin, `config/ingamemessenger/` is `plugins/InGameMessenger/`, and `<world>` is the main world's folder.

Players have their own settings in `config/ingamemessenger/client.json` on their computer.

## Configuration

`config/ingamemessenger/server.toml` is created on first start with comments for every option. Most changes apply
with `/messenger reload`. Changes to `[database]` and `[storage]` need a server restart.

When a newer version of the mod adds options, they are appended to the existing file with their default values.

### [server]

| Option | Default             | Description                                                                          |
|--------|---------------------|--------------------------------------------------------------------------------------|
| `name` | `""`                | The server's name in the messenger, over its lines and in the chat list. Empty: the first line of the MOTD. |
| `icon` | `"server-icon.png"` | Its icon: a PNG in the server's folder (the server list's icon by default, up to 256 KB). Empty: none. |

### [chat]

| Option   | Default | Description                                                                 |
|----------|---------|-----------------------------------------------------------------------------|
| `typing` | `true`  | Show "player is typing". Players can also hide it for themselves.           |

### [stickers]

| Option       | Default | Description                                                  |
|--------------|---------|--------------------------------------------------------------|
| `cooldownMs` | `700`   | Minimum time between two stickers from one player, in ms.    |

### [voice]

| Option       | Default | Description                                                         |
|--------------|---------|---------------------------------------------------------------------|
| `enabled`    | `true`  | Allow voice messages.                                               |
| `maxSeconds` | `60`    | Maximum length of a voice message, 5 to 300 seconds.                |
| `keepDays`   | `7`     | Delete voice messages after this many days. `0` keeps them forever. |

### [images]

| Option     | Default    | Description                                                                 |
|------------|------------|-----------------------------------------------------------------------------|
| `provider` | `"server"` | Where pictures are stored: `server`, `imgbb`, `catbox` or `off`. See [Pictures](#pictures). |
| `imgbbKey` | `""`       | ImgBB API key, used with `provider = "imgbb"`.                              |
| `keepDays` | `30`       | Delete pictures after this many days. `0` keeps them forever.               |
| `maxKb`    | `800`      | Maximum size of a picture after compression, 64 to 1000 KB.                |

### [withoutMod]

What players without the mod see. See [Players without the mod](#players-without-the-mod).

| Option         | Default | Description                                                                          |
|----------------|---------|--------------------------------------------------------------------------------------|
| `plainLines`   | `true`  | Show stickers, voice messages, pictures and polls as chat lines.                     |
| `installHint`  | `true`  | Tell them once, a few seconds after joining, that the server uses InGame Messenger. |
| `installText`  | `""`    | Your own text for that line. Empty uses the default.                                 |
| `stickerText`  | `""`    | Your own text for a sticker. `%s`: player.                                           |
| `voiceText`    | `""`    | Voice message. `%s`: player, seconds.                                                |
| `pictureText`  | `""`    | One picture. `%s`: player, caption.                                                  |
| `picturesText` | `""`    | Several pictures. `%s`: player, number, caption.                                     |
| `pollText`     | `""`    | Poll. `%s`: player, question.                                                        |

The texts take `&` color codes, for example `stickerText = "&e%s&7 sent a sticker"`.

### [database]

| Option        | Default             | Description                                                    |
|---------------|---------------------|----------------------------------------------------------------|
| `type`        | `"h2"`              | `h2`, `mysql`, `mariadb` or `postgresql`.                      |
| `host`        | `"localhost"`       | Database host.                                                 |
| `port`        | `0`                 | `0` uses the default: 3306 for MySQL/MariaDB, 5432 for PostgreSQL. |
| `name`        | `"ingamemessenger"` | Database name.                                                 |
| `user`        | `""`                | Database user.                                                 |
| `password`    | `""`                | Database password.                                             |
| `tablePrefix` | `"igm_"`            | Prefix for table names, useful when sharing a database.        |

### [storage]

| Option      | Default   | Description                                                        |
|-------------|-----------|--------------------------------------------------------------------|
| `type`      | `"local"` | `local` (world folder) or `s3`.                                    |
| `endpoint`  | `""`      | S3 endpoint URL.                                                   |
| `region`    | `"auto"`  | Bucket region. `auto` for Cloudflare R2.                           |
| `bucket`    | `""`      | Bucket name.                                                       |
| `accessKey` | `""`      | Access key ID.                                                     |
| `secretKey` | `""`      | Secret access key.                                                 |
| `pathStyle` | `true`    | `true` for `endpoint/bucket/key` URLs, `false` for `bucket.endpoint/key`. |
| `prefix`    | `""`      | Folder inside the bucket. Empty means the bucket root.             |

## Database

The chats (the server's chat and private chats), their history, reactions, poll votes, read marks, roles,
permissions and mutes are stored in a database.

### H2 (default)

An embedded database in `<world>/ingamemessenger/chat.mv.db`. It needs no setup and is the right choice for a single
server. It is backed up together with the world.

### MySQL and MariaDB

Create a database and a user:

```sql
CREATE DATABASE ingamemessenger CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'ingamemessenger'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON ingamemessenger.* TO 'ingamemessenger'@'%';
```

```toml
[database]
type = "mysql"          # or "mariadb"
host = "db.example.com"
port = 0
name = "ingamemessenger"
user = "ingamemessenger"
password = "change-me"
```

### PostgreSQL

```sql
CREATE USER ingamemessenger WITH PASSWORD 'change-me';
CREATE DATABASE ingamemessenger OWNER ingamemessenger;
```

```toml
[database]
type = "postgresql"
host = "db.example.com"
port = 0
name = "ingamemessenger"
user = "ingamemessenger"
password = "change-me"
```

### Drivers

The H2 driver is included in the mod. For MySQL, MariaDB and PostgreSQL the mod downloads the JDBC driver from Maven
Central on first start, verifies its SHA-256 checksum and stores it in `config/ingamemessenger/libs/`. If the server
has no internet access, download the jar yourself and put it there:

| Type       | File                                |
|------------|-------------------------------------|
| MySQL      | `mysql-connector-j-9.1.0.jar`       |
| MariaDB    | `mariadb-java-client-3.5.1.jar`     |
| PostgreSQL | `postgresql-42.7.4.jar`             |

Tables are created automatically. If the database cannot be reached at startup, the mod logs an error and stays
disabled until the next restart; the rest of the server runs normally.

All database access runs on a separate thread, so a slow database does not affect the server's tick rate.

## File storage

Voice messages and pictures (with `images.provider = "server"`) are stored by the file storage.

### Local

Files are kept in `<world>/ingamemessenger/voice/` and `images/`.

### S3-compatible

Any service with an S3 API works. Requests are signed with AWS Signature V4. Players download files through the
Minecraft server, so the bucket can stay private.

Cloudflare R2:

```toml
[storage]
type = "s3"
endpoint = "https://<account-id>.r2.cloudflarestorage.com"
region = "auto"
bucket = "ingamemessenger"
accessKey = "..."
secretKey = "..."
pathStyle = true
```

AWS S3:

```toml
[storage]
type = "s3"
endpoint = "https://s3.eu-central-1.amazonaws.com"
region = "eu-central-1"
bucket = "my-bucket"
accessKey = "..."
secretKey = "..."
pathStyle = false
```

Backblaze B2 uses an endpoint like `https://s3.eu-central-003.backblazeb2.com` with the bucket's region
(`eu-central-003`). MinIO and other self-hosted servers usually need `pathStyle = true`.

The access key needs permission to read, write and delete objects in the bucket.

Old files are deleted by the mod according to `voice.keepDays` and `images.keepDays`. You can also set a lifecycle
rule on the bucket.

## Pictures

| Provider | How it works                                                                                           |
|----------|--------------------------------------------------------------------------------------------------------|
| `server` | Pictures are uploaded to this server and kept in the file storage (local or S3). Recommended.         |
| `imgbb`  | The server uploads pictures to [ImgBB](https://imgbb.com) with your API key. Nothing is stored locally. The server also relays ImgBB pictures to players who cannot reach ImgBB. |
| `catbox` | Each player's game uploads pictures to [Catbox](https://catbox.moe) itself; the server only checks the links. Catbox rejects uploads from data centers, which is why the upload is done by the client. |
| `off`    | Pictures are disabled.                                                                                 |

Pictures are converted to JPEG on the client (at most 1600 pixels on the longer side) before upload. `images.maxKb`
limits the result.

## Sticker and emoji packs

Each folder inside `stickers/` is a sticker pack, each folder inside `emoji/` is an emoji pack. Emoji can be used
inline in messages and as reactions; stickers are sent as separate messages.

```
config/ingamemessenger/stickers/
  cats/
    pack.json
    01-hello.png
    02-dance.gif
  memes/
    ...
```

- Supported formats: PNG and JPG (still), GIF (animated).
- Recommended size: 512x512 for stickers, about 100x100 for emoji. Larger images are scaled down. Transparent PNGs look
  best.
- Files are sorted by name. The first file is the pack's icon.
- `pack.json` is optional and sets the pack's display name: `{"title": "Cats"}`. Without it the folder name is used.
- Up to 200 packs and 200 stickers per pack.

Packs are converted when the server starts or on `/messenger reload` and kept in memory. Clients download each sticker
from the server the first time it is shown.

Animated GIFs are limited to 64 frames; longer animations are sampled. Their frames are scaled down until the result
fits in 900 KB.

## Players without the mod

The mod and the plugin never keep anyone out. Players can always join, whatever they have installed. "Server with the
mod" below also means a server with the plugin.

| Client                         | Server with the mod                                   | Server without the mod |
|--------------------------------|-------------------------------------------------------|------------------------|
| With the mod, same version     | The messenger                                         | Normal chat            |
| With the mod, other version    | Normal chat and a note about the versions             | Normal chat            |
| Without the mod                | Normal chat and a note to install the mod             | Normal chat            |

When a client with the mod joins a server without it, the player sees the normal Minecraft chat and a one-line note
that the server doesn't use InGame Messenger. Players can turn these notes off with `"serverNotices": false` in their
`client.json`.

A few seconds after a player without the mod joins, they get one line saying the server uses InGame Messenger and
that installing it shows stickers, voice messages, pictures and polls.

The client and the server don't need the same loader: a NeoForge, Fabric or Forge client gets the messenger on a
NeoForge, Fabric or Forge server and on the plugin alike.

Players without the mod still see what others send through the messenger, as plain chat lines:

```
<Alex> [sticker]
<Alex> [voice message, 4 s]
<Alex> [picture] look at this
<Alex> [poll] Where do we build the base?
```

Private chats are messenger-only: players without the mod don't get them.

The wording of these lines and the install note can be changed or turned off in [`[withoutMod]`](#withoutmod).

Their own messages are ordinary chat messages, so players with the mod see them as usual.

On joining, the server and the client exchange their protocol versions. The messenger turns on only when they match;
the protocol changes only with releases that change the network format, so different releases often still work
together.

## Proxies (Velocity, BungeeCord)

Install the mod or the plugin on every server behind the proxy that should have the messenger. Nothing goes on the
proxy itself: it passes the messenger's packets through as they are.

Each server keeps its own chat, the way the normal chat works on a network: in the lobby players see the lobby's
chat, in a game only that game's chat. When a player moves to another server, their chat is cleared and the new
server's history is loaded. Sticker packs, settings and the database are also per server; copy `stickers/` and
`emoji/` to every server that should have the same packs. If several servers use one MySQL, MariaDB or PostgreSQL
database, give each its own `tablePrefix`.

A server behind the proxy can run the mod or the plugin, and players can move between servers on different loaders
and platforms.

NeoForge clients can't join a NeoForge server behind Velocity: NeoForge sends its own command argument types, which
Velocity can't read, and the connection drops. This happens without InGame Messenger too and needs a proxy
compatibility mod on the NeoForge server. Clients on other loaders join such a server normally.

## Commands

All commands require operator permission. With the plugin, `ingamemessenger.admin` grants all of them and
`ingamemessenger.mute` only the mute commands.

| Command                                   | Description                                                   |
|-------------------------------------------|---------------------------------------------------------------|
| `/messenger mute <player> [time] [where] [reason]` | Mute a player. Time: `10m`, `2h`, `3d` or `forever` (default). Where: `chat` (the server's chat, default), `dm` (private chats) or `all`. |
| `/messenger unmute <player> [where]`      | Lift a mute, everywhere by default.                           |
| `/messenger mutes`                        | List the current mutes.                                       |
| `/messenger reload`                       | Reload `server.toml` and the sticker and emoji packs.         |
| `/messenger typing <true\|false>`         | Turn the typing indicator on or off.                          |
| `/messenger voice <true\|false>`          | Turn voice messages on or off.                                |
| `/messenger voice length <seconds>`       | Maximum voice message length.                                 |
| `/messenger voice keep <days>`            | Days to keep voice messages, `0` for forever.                 |

Commands that change a setting also write it to `server.toml`.

Operators can delete any message from its context menu.

A muted player can't write where the mute holds, and their messenger says so in the input. A mute in private chats
also stops `/msg`, `/tell`, `/w`, `/r` and the like (EssentialsX's too), a chat mute stops `/me`. On the plugin, a mute from EssentialsX, LiteBans or AdvancedBan keeps the player out
of the server's chat too.

## Backups

- With H2 and local storage, everything is inside `<world>/ingamemessenger/` and is covered by a normal world backup.
  Copy `chat.mv.db` while the server is stopped, or use your usual world backup tool.
- With an external database, back it up with the database's own tools (`mysqldump`, `pg_dump`).
- With S3, use the provider's versioning or replication if you need backups of media.

## Troubleshooting

**"InGame Messenger is OFF: can't open the database"** in the log. The database could not be reached or the driver
could not be loaded. Check `[database]`, that the database server accepts connections from the Minecraft server, and
that the user has rights to create tables. Fix the config and restart.

**Driver download fails.** The server has no access to `repo1.maven.org`. Put the driver jar into
`config/ingamemessenger/libs/` manually (see [Drivers](#drivers)).

**Voice messages or pictures are not sent with S3.** Look for `Can't store a file` in the log; it includes the HTTP
status from the storage. 403 usually means a wrong key or missing permissions, 404 a wrong bucket name or endpoint.
Try switching `pathStyle`.

**A sticker pack does not show up.** Check the log for `Stickers: N packs, M stickers` after `/messenger reload` and
for lines starting with `Stickers: can't read`. Empty folders and unsupported files are skipped.

**A player with the mod sees the normal chat.** Look for `has InGame Messenger ..., the server ...` in the server log:
the client has a different version, install the server's version on it. If there is no such line, check that the
mod started on the server (`InGame Messenger is OFF` means the database could not be opened).

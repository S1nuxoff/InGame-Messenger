# Contributing

Thanks for helping. This document explains how the project is organized and what to keep in mind when changing it.

## Reporting bugs

Open an issue with:

- Minecraft version, mod loader and its version, InGame Messenger version;
- whether it happens on the client, the server or both;
- steps to reproduce;
- the relevant part of `logs/latest.log` (client and/or server) or the crash report.

## Translations

Language files are in `src/main/resources/assets/ingamemessenger/lang/`. To add a language, copy `en_us.json` to
`<code>.json`, where `<code>` is the Minecraft language code in lower case (`de_de`, `pt_br`, `zh_cn`...), and
translate the values. Keep the keys unchanged, and keep `%s` (a value inserted by the game) and `%%` (a percent sign)
in each string. Save the file as UTF-8.

When a new string is added to `en_us.json`, missing translations fall back to English automatically.

## Project layout

```
core/                     Plain Java, no Minecraft. Shared by every platform and covered by unit tests.
  protocol/               Packets and their wire format.
  server/                 The chat server: validation, delivery, private chats, uploads, sticker packs.
  storage/                Database (JDBC) and file storage (local, S3).
  audio/ image/ text/     Opus codec, picture processing, text rules.
  ai/ web/                Gemini client, HTTP helpers.

src/main/java/com/sinuxoff/ingamemessenger/
  *.java                  The Minecraft server side: platform glue, commands, config.
  client/                 Everything the player sees: chat rendering, input, panels, screens, audio.
  mixin/                  Mixins shared by all loaders.
  hooks/                  Mixins for HUD, typed characters and frame start, used on loaders without such events
                          (Fabric, Forge).
  neoforge/ fabric/ forge/  Entry points, networking and events for each loader.

versions/<mc>-<loader>/   One build target each, with its versions in gradle.properties.
plugin/                   The server side as a Bukkit plugin (Paper, Spigot, Purpur, Folia), built on core alone.
```

`core` must not depend on Minecraft or a mod loader. Anything another platform (the plugin, a future proxy) could
reuse belongs there.

Each loader package only wires events and networking to the shared code. A feature should be written once in the
shared code, not per loader.

## Building

```sh
./gradlew buildAll                 # every target
./gradlew :26.3-fabric:build       # one target (targets are <minecraft>-<neoforge|fabric|forge>)
./gradlew :core:test               # unit tests
```

JDK 21 is enough to run Gradle; the JDK 25 needed for the 26.x targets is downloaded through Gradle toolchains.
The first build of a Forge target takes several minutes per Minecraft version while ForgeGradle prepares the game;
later builds use the cache.

## Multiple Minecraft versions

All versions are built from the same sources with [Stonecutter](https://stonecutter.kikugie.dev/). The sources in git
are written for the *VCS version*, `1.21.1-neoforge`. Code that differs between versions is wrapped in conditional
comments:

```java
//? if >=1.21.9 {
/*KeyMapping.Category.MULTIPLAYER);
*///?} else {
"key.categories.multiplayer");
//?}
```

The active branch is plain code, inactive branches are commented out. Stonecutter swaps them when building each
target. Loader-specific code uses `//? if fabric {`, `//? if neoforge {` and `//? if forge {`.

Renames that apply to every file (for example `ResourceLocation` to `Identifier` in 1.21.11) are declared once as
replacements in `build.gradle` instead of conditions in each file.

To edit and run a specific version in your IDE, switch the active project with the Stonecutter Gradle task
`Set active project to <target>`. Switch back to `1.21.1-neoforge` before committing, so the sources in git stay in
the VCS version.

### Where version differences live

Most of the code never touches APIs that change between versions. The differences are concentrated in a few classes:

| Class                         | Covers                                                                      |
|-------------------------------|-----------------------------------------------------------------------------|
| `client/Gfx`                  | All drawing: transforms, layers, text, textures, shapes, scissor, blur.     |
| `client/Compat`               | Other client APIs: input, chat lines, textures, links, file dialogs.        |
| `client/BaseScreen`           | Screen rendering and input methods.                                         |
| `client/Glass` (`Pressable`)  | Widget rendering and click handling.                                        |
| `client/Mic`                  | Microphone capture.                                                         |
| `ServerCompat`                | Server-side APIs: profiles, operators, component serialization.             |
| `mixin/*`                     | Injection points, which follow Minecraft's internals.                       |

New features should go through these classes rather than calling version-specific APIs directly. That keeps a
feature to one implementation for all targets.

### Adding a Minecraft version

1. Create `versions/<mc>-<loader>/gradle.properties` based on the closest existing target and set the Minecraft,
   loader and API versions, the accepted version range and the Java version.
2. Register the target in `settings.gradle`.
3. Build it. Fix compile errors by adding conditions in the classes above, or a replacement in `build.gradle` for a
   plain rename.
4. Test in game: open the chat, send text, a sticker, a picture and a voice message, react, reply, open the settings
   and the sticker panel. Mixin failures only show up at runtime.

## Code style

- Java 21 language level in shared code.
- Four spaces, braces on every block, lines up to about 140 characters.
- Comments explain why, not what. Keep them short.
- Packet handlers run on the server thread. Anything that touches the database or file storage goes through
  `ChatServer.db(...)` or the storage's executors, and comes back with `ServerPlatform.execute(...)` to send packets.
- Validate everything that comes from a client: lengths, ids, keys, rates.
- When the wire format of a packet changes, bump `Protocol.VERSION`.

## Tests

`core` has unit tests for the protocol, the server logic, the database layer, audio and picture processing. Run them
with `./gradlew :core:test`. New server-side behaviour should come with a test in `core/src/test`.

The Minecraft side is tested in game. Please mention in the pull request which versions and loaders you tried.

## Pull requests

- Keep a pull request focused on one change.
- Make sure `./gradlew :core:test buildAll` passes.
- Describe what changed and how you tested it.

By contributing you agree that your contribution is licensed under the GPL-3.0, like the rest of the project.

<div align="center">

<img src="docs/images/banner.png" alt="InGame Messenger: настоящий мессенджер в чате Minecraft" width="100%">

[English](README.md) · [Українська](README.uk.md) · **Русский**

[![Download](https://img.shields.io/github/v/release/S1nuxoff/InGame-Messenger?style=for-the-badge&label=Download&color=2EA043)](https://github.com/S1nuxoff/InGame-Messenger/releases/latest) [![CurseForge](https://img.shields.io/curseforge/dt/1746328?style=for-the-badge&logo=curseforge&logoColor=white&label=CurseForge&color=F16436)](https://www.curseforge.com/minecraft/mc-mods/ingame-messenger)

[![Build](https://github.com/S1nuxoff/InGame-Messenger/actions/workflows/build.yml/badge.svg)](https://github.com/S1nuxoff/InGame-Messenger/actions/workflows/build.yml)
[![Tested in game](https://img.shields.io/badge/tested-in%20game-2EA043)](https://s1nuxoff.github.io/InGame-Messenger-tests/)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

[Обзор](#обзор) · [Возможности](#возможности) · [Установка](#установка) · [Версии](#поддерживаемые-версии) · [Вопросы](#частые-вопросы) · [Для серверов](docs/server.md) · [Помочь проекту](CONTRIBUTING.md)

<img src="docs/images/platforms-strip-v3.png" alt="Minecraft 1.19.2 – 26.3: Fabric, Quilt, NeoForge, Forge, Paper, Spigot, Purpur, Folia" width="855">

</div>

## Обзор

InGame Messenger превращает обычный чат Minecraft в полноценный мессенджер. Всё происходит в том же окне чата:
общий чат и команды на месте, просто сообщения теперь выглядят как в привычных мессенджерах — с аватарками,
стикерами, голосовыми, картинками, реакциями и ответами. А рядом — личные чаты. Никаких аккаунтов и облаков:
всё хранится на вашем сервере.

<table>
<tr>
<td width="33%" valign="top">

**Игрокам**<br>
Просто поставьте мод. На серверах без него чат останется обычным.

</td>
<td width="33%" valign="top">

**Серверам**<br>
Нужен мод (NeoForge, Fabric, Forge) или плагин (Paper, Spigot, Purpur, Folia). Лоадер у игрока и на сервере
может быть разным.

</td>
<td width="33%" valign="top">

**Игрокам без мода**<br>
Тоже могут играть и писать в чат. Стикеры, голосовые, картинки и опросы они увидят короткой строкой текста.

</td>
</tr>
</table>

## Как это выглядит

https://github.com/user-attachments/assets/48dfbc2b-96dd-4af4-9ba5-352966471e95

## Возможности

### <img src="docs/images/icons/writable_book.png" width="24" height="24" align="top" alt="Book and quill"> Чат как в мессенджере

<table>
<tr>
<td width="50%" valign="middle">

- **Сообщения в пузырях** — с аватаркой, ником и временем. Несколько сообщений подряд от одного игрока собираются в группу.
- **Сообщения сервера** (входы, смерти, объявления, ответы на команды) подписаны его названием и иконкой.
- **Ники с рангами и цветами** — так, как их показывает сервер или LuckPerms/Vault.
- **«Печатает…»**, даты между сообщениями, плавная прокрутка.

</td>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/conversation.png" width="420" alt="A conversation in the chat: server lines, messages from Kenji and Mia, a reaction and a reply"></td>
</tr>
</table>

### <img src="docs/images/icons/cherry_sign.png" width="24" height="24" align="top" alt="Cherry sign"> Ответы и реакции

<table>
<tr>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/msg-mia-reply.png" width="400" alt="Mia replies to a message with a quote"><br><br><img src="docs/images/features/msg-mine-lava.png" width="400" alt="Your own message with a reaction under it"></td>
<td width="50%" valign="middle">

- **Отвечайте с цитатой.** Нажмите на цитату — и чат перемотает к исходному сообщению, даже если оно было давно.
- **Ставьте реакции** — стандартные или эмодзи сервера. Двойной клик по сообщению — сердечко.
- Свои сообщения можно редактировать и удалять.

</td>
</tr>
</table>

### <img src="docs/images/icons/bell.png" width="24" height="24" align="top" alt="Bell"> Упоминания

<table>
<tr>
<td width="50%" valign="middle">

- **Упоминайте через @** — ник подставится сам.
- Если упомянули вас, сообщение подсветится, а над хотбаром появится уведомление — даже когда чат закрыт.

</td>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/msg-mention-mia.png" width="400" alt="Mia mentions you with @"></td>
</tr>
</table>

### <img src="docs/images/icons/name_tag.png" width="24" height="24" align="top" alt="Name tag"> Личные чаты

<table>
<tr>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/private-chat.png" width="420" alt="A private chat with Mia, with read checks"></td>
<td width="50%" valign="middle">

- Переписки один на один в списке рядом с общим чатом, со счётчиком непрочитанных и уведомлениями.
- **Галочки**: одна — отправлено, две — прочитано.
- **Команды работают из любого чата.** Ответ на команду из лички придёт туда же и будет виден только вам.

</td>
</tr>
</table>

### <img src="docs/images/icons/slime_ball.png" width="24" height="24" align="top" alt="Slimeball"> Стикеры и эмодзи

<table>
<tr>
<td width="50%" valign="middle">

- **Наборы стикеров добавляет сервер**: PNG, JPG и анимированные GIF.
- В панели — недавние, избранные и все наборы. Зажмите клавишу чата, чтобы быстро выбрать стикер.
- Эмодзи сервера можно вставлять прямо в текст и ставить как реакции.

</td>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/sticker-panel.png" width="420" alt="The sticker panel with recent stickers and packs"></td>
</tr>
</table>

### <img src="docs/images/icons/painting.png" width="24" height="24" align="top" alt="Painting"> Картинки

<table>
<tr>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/msg-picture.png" width="360" alt="A picture of a sunset sent with a caption"></td>
<td width="50%" valign="middle">

- До четырёх картинок в сообщении, можно с подписью.
- Из файла, из скриншота игры или вставкой из буфера (Ctrl+V).
- Координаты отправляются карточкой, а с JourneyMap по клику сразу ставится метка.

</td>
</tr>
</table>

### <img src="docs/images/icons/goat_horn.png" width="24" height="24" align="top" alt="Goat horn"> Голосовые сообщения

<table>
<tr>
<td width="50%" valign="middle">

- **Зажмите кнопку микрофона** и говорите, отпустите — сообщение уйдёт.
- Видно волну звука, слушать можно на 1x, 1.5x и 2x, а мини-плеер играет дальше, пока вы бегаете.
- Работает само по себе, другие моды не нужны.

</td>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/msg-voice-mia.png" width="400" alt="Mia's voice message with its waveform"><br><br><img src="docs/images/features/msg-voice-mine.png" width="400" alt="Your voice message"></td>
</tr>
</table>

### <img src="docs/images/icons/paper.png" width="24" height="24" align="top" alt="Paper"> Опросы

<table>
<tr>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/poll-window.png" width="380" alt="The window for a new poll"><br><br><img src="docs/images/features/msg-poll.png" width="380" alt="A poll in the chat with its results"></td>
<td width="50%" valign="middle">

- С одним или несколькими вариантами ответа, результаты обновляются сразу.
- Создаются в отдельном окне прямо из чата.

</td>
</tr>
</table>

### <img src="docs/images/icons/spyglass.png" width="24" height="24" align="top" alt="Spyglass"> Поиск, настройки, языки
- Поиск по всем вашим чатам (Ctrl+F) — нажмите на результат, и откроется нужное место.
- Настраивается размер чата и стикеров, микрофон, устройство вывода звука, громкость и шумоподавление.
- 40 языков.

### <img src="docs/images/icons/command_block_minecart.png" width="24" height="24" align="top" alt="Minecart with command block"> Для владельцев серверов
- **Муты** — в общем чате, в личных сообщениях или везде, на время или навсегда. Мут также блокирует `/msg` и `/me`.
  Муты из EssentialsX, LiteBans и AdvancedBan тоже работают (в плагине).
- **Дружит с чат-плагинами** вроде EssentialsX Chat: каждое сообщение показывается один раз, с рангом и цветами от сервера.
- **История** хранится в H2 — ничего настраивать не нужно. Можно подключить MySQL, MariaDB или PostgreSQL. Голосовые и
  картинки лежат в папке мира или в любом S3-хранилище, старые удаляются сами.
- В мессенджере видно **название и иконку вашего сервера** (по умолчанию берутся MOTD и иконка из списка серверов).
- **Все настройки в одном файле**, перезагрузить можно прямо в игре.

## Установка

### Игрокам
1. Скачайте jar под свою версию Minecraft и лоадер со страницы [Releases](https://github.com/S1nuxoff/InGame-Messenger/releases/latest) или с [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ingame-messenger).
2. Закиньте его в папку `mods`. Для Fabric и Quilt нужен ещё [Fabric API](https://modrinth.com/mod/fabric-api).
3. Заходите на сервер, где стоит мод или плагин.

### Серверам
1. **Сервер с модами:** jar мода — в `mods` (на Fabric и Quilt ещё Fabric API).<br>
   **Paper, Spigot, Purpur, Folia:** jar плагина — в `plugins`.
2. Запустите сервер один раз — он создаст папку `config/ingamemessenger/` (у плагина `plugins/InGameMessenger/`)
   с настройками и папками для стикеров и эмодзи.
3. Добавьте свои стикеры, если хотите, и выполните `/messenger reload`.

Настройки по умолчанию подходят сразу. Все параметры, внешние базы, S3, стикеры, команды и права описаны в
[руководстве для сервера](docs/server.md) (на английском).

## Поддерживаемые версии

<img src="docs/images/platforms/minecraft.png" width="20" height="20" alt="Minecraft"> **22 версии Minecraft — от 1.19.2 до 26.3.**

| Платформа | Версии |
|---|---|
| <img src="docs/images/platforms/fabric.png" width="18" height="18" alt="Fabric"> **Fabric** · <img src="docs/images/platforms/quilt.png" width="18" height="18" alt="Quilt"> **Quilt** | 1.19.2 · 1.20 · 1.20.1 · 1.20.3 · 1.20.4 · 1.20.5 · 1.20.6 · 1.21 · 1.21.1 · 1.21.4 – 1.21.11 · 26.1 – 26.3 |
| <img src="docs/images/platforms/neoforge.png" width="18" height="18" alt="NeoForge"> **NeoForge** | 1.20.6 · 1.21 · 1.21.1 · 1.21.4 – 1.21.11 · 26.1 – 26.3 |
| <img src="docs/images/platforms/forge.png" width="18" height="18" alt="Forge"> **Forge** | 1.19.2 · 1.20.1 · 1.20.4 · 1.20.6 · 1.21.1 · 1.21.4 · 1.21.5 · 1.21.8 · 1.21.10 · 1.21.11 · 26.1.2 · 26.2 · 26.3 |
| <img src="docs/images/platforms/paper.png" width="18" height="18" alt="Paper"> **Paper** · <img src="docs/images/platforms/spigot.svg" width="18" height="18" alt="Spigot"> **Spigot** · <img src="docs/images/platforms/purpur.png" width="18" height="18" alt="Purpur"> **Purpur** | плагин, 1.20.1 и новее |
| <img src="docs/images/platforms/folia.png" width="18" height="18" alt="Folia"> **Folia** | плагин, все версии Folia начиная с 1.21.4 |

<details>
<summary><b>Полная таблица</b> (все версии, лоадеры и нужная Java)</summary>

| Minecraft | <img src="docs/images/platforms/neoforge.png" width="16" height="16" alt="NeoForge"><br>NeoForge | <img src="docs/images/platforms/fabric.png" width="16" height="16" alt="Fabric"><br>Fabric | <img src="docs/images/platforms/quilt.png" width="16" height="16" alt="Quilt"><br>Quilt | <img src="docs/images/platforms/forge.png" width="16" height="16" alt="Forge"><br>Forge | <img src="docs/images/platforms/paper.png" width="16" height="16" alt="Paper"> <img src="docs/images/platforms/spigot.svg" width="16" height="16" alt="Spigot"> <img src="docs/images/platforms/purpur.png" width="16" height="16" alt="Purpur"><br>Paper, Spigot, Purpur | <img src="docs/images/platforms/folia.png" width="16" height="16" alt="Folia"><br>Folia | <img src="docs/images/platforms/java.svg" width="16" height="16" alt="Java"><br>Java |
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

- Для Fabric нужен [Fabric API](https://modrinth.com/mod/fabric-api). На Quilt ставьте Fabric-версию вместе с Fabric API.
- Некоторые версии используют один и тот же jar: Fabric-сборка для 1.20.1 подходит и для 1.20, для 1.20.4 — для 1.20.3,
  для 1.20.6 — для 1.20.5, сборки 1.21.1 для Fabric и NeoForge — для 1.21. Одна сборка покрывает 1.21.6 – 1.21.8, другая —
  1.21.9 – 1.21.10, третья — 26.1 – 26.1.2. Для Forge каждая сборка под одну версию — так устроен сам Forge.
- Каждый jar встаёт только на свои версии. Пока не поддерживаются: 1.19.3, 1.19.4, 1.20.2, 1.21.2, 1.21.3.

</details>

> [!NOTE]
> Каждую версию проверяем прямо в игре: мод сам проходит сценарий на тестовом сервере (сообщения, стикеры, картинки,
> голосовые, опросы, личка, муты), а каждый шаг сохраняется скриншотом.
> [Посмотреть последние прогоны.](https://s1nuxoff.github.io/InGame-Messenger-tests/)

## Управление

<details>
<summary>Показать</summary>

| Действие | Как |
|---|---|
| Открыть чат | Клавиша чата (T) |
| Быстро выбрать стикер | Зажать клавишу чата, кликнуть стикер, отпустить |
| Панель стикеров | Кнопка со смайликом в поле ввода; правый клик — закрепить панель |
| Стикер в избранное / посмотреть поближе | Правый клик / зажать в панели |
| Реакция | Навести на сообщение и нажать кнопку реакции или кликнуть дважды |
| Ответить, скопировать, изменить, удалить | Правый клик по сообщению |
| Перейти к исходному сообщению | Клик по цитате |
| Меню игрока (личка, упоминание) | Клик по аватарке |
| Голосовое | Зажать кнопку микрофона (она видна, когда поле ввода пустое) |
| Прикрепить картинку, координаты, опрос | Кнопка-скрепка |
| Вставить картинку | Ctrl+V |
| Поиск | Ctrl+F или лупа внизу списка чатов |
| Настройки | Шестерёнка внизу списка чатов |

</details>

## Частые вопросы

<details>
<summary><b>Нужно ли ставить мод всем игрокам?</b></summary>

Нет. Игроки без мода заходят и общаются как обычно, а стикеры, голосовые, картинки и опросы видят короткими
строками текста. Мессенджер видят только те, у кого стоит мод.
</details>

<details>
<summary><b>А на сервер его ставить нужно?</b></summary>

Да: мод — на сервер с модами, плагин — на Paper, Spigot, Purpur или Folia. Если на сервере его нет, мод просто не трогает чат.
</details>

<details>
<summary><b>Работает с чат-плагинами, рангами и мутами?</b></summary>

Да. Сообщения из чат-плагинов вроде EssentialsX Chat показываются один раз, с рангом и цветами от сервера.
Ранги берутся с сервера или из LuckPerms/Vault, муты из EssentialsX, LiteBans и AdvancedBan тоже работают.
</details>

<details>
<summary><b>Velocity или BungeeCord?</b></summary>

Ставьте на сервера за прокси, на сам прокси ничего не нужно. У каждого сервера свой чат.
</details>

<details>
<summary><b>Кто может читать сообщения?</b></summary>

Сообщения, в том числе личные, хранятся на сервере без шифрования, так что администрация сервера может их прочитать —
как и на любой другой платформе. Голосовые и картинки хранятся заданное число дней, потом удаляются.
</details>

## Помочь проекту

Будем рады баг-репортам, идеям, переводам и pull request’ам. Как собрать мод самому или отправить изменения — в
[CONTRIBUTING.md](CONTRIBUTING.md) (на английском).

## Лицензия

Свободное ПО под лицензией [GNU GPL v3.0](LICENSE) с условием об указании авторства в [NOTICE.md](NOTICE.md).
Сторонние ресурсы и библиотеки перечислены в [CREDITS.md](CREDITS.md).

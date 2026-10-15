<div align="center">

<img src="docs/images/banner.png" alt="InGame Messenger: справжній месенджер у чаті Minecraft" width="100%">

[English](README.md) · **Українська** · [Русский](README.ru.md)

[![Download](https://img.shields.io/github/v/release/S1nuxoff/InGame-Messenger?style=for-the-badge&label=Download&color=2EA043)](https://github.com/S1nuxoff/InGame-Messenger/releases/latest)

[![Build](https://github.com/S1nuxoff/InGame-Messenger/actions/workflows/build.yml/badge.svg)](https://github.com/S1nuxoff/InGame-Messenger/actions/workflows/build.yml)
[![Tested in game](https://img.shields.io/badge/tested-in%20game-2EA043)](https://s1nuxoff.github.io/InGame-Messenger-tests/)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

[Огляд](#огляд) · [Можливості](#можливості) · [Встановлення](#встановлення) · [Версії](#підтримувані-версії) · [Питання](#часті-питання) · [Для серверів](docs/server.md) · [Долучитися](CONTRIBUTING.md)

<img src="docs/images/platforms-strip-v3.png" alt="Minecraft 1.19.2 – 26.3: Fabric, Quilt, NeoForge, Forge, Paper, Spigot, Purpur, Folia" width="855">

</div>

## Огляд

InGame Messenger перетворює звичайний чат Minecraft на повноцінний месенджер. Усе відбувається в тому ж вікні чату:
загальний чат і команди на місці, просто повідомлення тепер виглядають як у звичних месенджерах — з аватарками,
стікерами, голосовими, картинками, реакціями та відповідями. А поруч — особисті чати. Жодних акаунтів і хмар:
усе зберігається на вашому сервері.

<table>
<tr>
<td width="33%" valign="top">

**Гравцям**<br>
Просто встановіть мод. На серверах без нього чат залишиться звичайним.

</td>
<td width="33%" valign="top">

**Серверам**<br>
Потрібен мод (NeoForge, Fabric, Forge) або плагін (Paper, Spigot, Purpur, Folia). Лоадер у гравця і на сервері
може бути різним.

</td>
<td width="33%" valign="top">

**Гравцям без мода**<br>
Теж можуть грати й писати в чат. Стікери, голосові, картинки й опитування вони побачать коротким рядком тексту.

</td>
</tr>
</table>

## Як це виглядає

https://github.com/user-attachments/assets/48dfbc2b-96dd-4af4-9ba5-352966471e95

## Можливості

### <img src="docs/images/icons/writable_book.png" width="24" height="24" align="top" alt="Book and quill"> Чат як у месенджері

<table>
<tr>
<td width="50%" valign="middle">

- **Повідомлення в бульбашках** — з аватаркою, ніком і часом. Кілька повідомлень поспіль від одного гравця збираються в групу.
- **Повідомлення сервера** (входи, смерті, оголошення, відповіді на команди) підписані його назвою та іконкою.
- **Ніки з рангами й кольорами** — так, як їх показує сервер або LuckPerms/Vault.
- **«Друкує…»**, дати між повідомленнями, плавне прокручування.

</td>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/conversation.png" width="420" alt="A conversation in the chat: server lines, messages from Kenji and Mia, a reaction and a reply"></td>
</tr>
</table>

### <img src="docs/images/icons/cherry_sign.png" width="24" height="24" align="top" alt="Cherry sign"> Відповіді та реакції

<table>
<tr>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/msg-mia-reply.png" width="400" alt="Mia replies to a message with a quote"><br><br><img src="docs/images/features/msg-mine-lava.png" width="400" alt="Your own message with a reaction under it"></td>
<td width="50%" valign="middle">

- **Відповідайте з цитатою.** Натисніть на цитату — і чат перемотає до початкового повідомлення, навіть якщо воно було давно.
- **Ставте реакції** — стандартні або емодзі сервера. Подвійний клік по повідомленню — сердечко.
- Свої повідомлення можна редагувати й видаляти.

</td>
</tr>
</table>

### <img src="docs/images/icons/bell.png" width="24" height="24" align="top" alt="Bell"> Згадки

<table>
<tr>
<td width="50%" valign="middle">

- **Згадуйте через @** — нік підставиться сам.
- Якщо згадали вас, повідомлення підсвітиться, а над хотбаром з’явиться сповіщення — навіть коли чат закритий.

</td>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/msg-mention-mia.png" width="400" alt="Mia mentions you with @"></td>
</tr>
</table>

### <img src="docs/images/icons/name_tag.png" width="24" height="24" align="top" alt="Name tag"> Особисті чати

<table>
<tr>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/private-chat.png" width="420" alt="A private chat with Mia, with read checks"></td>
<td width="50%" valign="middle">

- Листування віч-на-віч у списку поруч із загальним чатом, з лічильником непрочитаних і сповіщеннями.
- **Галочки**: одна — надіслано, дві — прочитано.
- **Команди працюють з будь-якого чату.** Відповідь на команду з особистого чату прийде туди ж і буде видна лише вам.

</td>
</tr>
</table>

### <img src="docs/images/icons/slime_ball.png" width="24" height="24" align="top" alt="Slimeball"> Стікери та емодзі

<table>
<tr>
<td width="50%" valign="middle">

- **Набори стікерів додає сервер**: PNG, JPG і анімовані GIF.
- У панелі — недавні, обрані та всі набори. Затисніть клавішу чату, щоб швидко вибрати стікер.
- Емодзі сервера можна вставляти прямо в текст і ставити як реакції.

</td>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/sticker-panel.png" width="420" alt="The sticker panel with recent stickers and packs"></td>
</tr>
</table>

### <img src="docs/images/icons/painting.png" width="24" height="24" align="top" alt="Painting"> Картинки

<table>
<tr>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/msg-picture.png" width="360" alt="A picture of a sunset sent with a caption"></td>
<td width="50%" valign="middle">

- До чотирьох картинок у повідомленні, можна з підписом.
- З файлу, зі скриншота гри або вставкою з буфера (Ctrl+V).
- Координати надсилаються карткою, а з JourneyMap по кліку одразу ставиться мітка.

</td>
</tr>
</table>

### <img src="docs/images/icons/goat_horn.png" width="24" height="24" align="top" alt="Goat horn"> Голосові повідомлення

<table>
<tr>
<td width="50%" valign="middle">

- **Затисніть кнопку мікрофона** і говоріть, відпустіть — повідомлення піде.
- Видно хвилю звуку, слухати можна на 1x, 1.5x і 2x, а міні-плеєр грає далі, поки ви бігаєте.
- Працює саме по собі, інші моди не потрібні.

</td>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/msg-voice-mia.png" width="400" alt="Mia's voice message with its waveform"><br><br><img src="docs/images/features/msg-voice-mine.png" width="400" alt="Your voice message"></td>
</tr>
</table>

### <img src="docs/images/icons/paper.png" width="24" height="24" align="top" alt="Paper"> Опитування

<table>
<tr>
<td width="50%" align="center" valign="middle"><img src="docs/images/features/poll-window.png" width="380" alt="The window for a new poll"><br><br><img src="docs/images/features/msg-poll.png" width="380" alt="A poll in the chat with its results"></td>
<td width="50%" valign="middle">

- З однією або кількома відповідями, результати оновлюються одразу.
- Створюються в окремому вікні прямо з чату.

</td>
</tr>
</table>

### <img src="docs/images/icons/spyglass.png" width="24" height="24" align="top" alt="Spyglass"> Пошук, налаштування, мови
- Пошук по всіх ваших чатах (Ctrl+F) — натисніть на результат, і відкриється потрібне місце.
- Налаштовується розмір чату й стікерів, мікрофон, пристрій виводу звуку, гучність і шумоподавлення.
- 40 мов.

### <img src="docs/images/icons/command_block_minecart.png" width="24" height="24" align="top" alt="Minecart with command block"> Для власників серверів
- **Мути** — у загальному чаті, в особистих повідомленнях або всюди, на час або назавжди. Мут також блокує `/msg` і `/me`.
  Мути з EssentialsX, LiteBans і AdvancedBan теж працюють (у плагіні).
- **Дружить із чат-плагінами** на кшталт EssentialsX Chat: кожне повідомлення показується один раз, з рангом і кольорами від сервера.
- **Історія** зберігається в H2 — нічого налаштовувати не треба. Можна підключити MySQL, MariaDB або PostgreSQL. Голосові й
  картинки лежать у теці світу або в будь-якому S3-сховищі, старі видаляються самі.
- У месенджері видно **назву та іконку вашого сервера** (за замовчуванням беруться MOTD та іконка зі списку серверів).
- **Усі налаштування в одному файлі**, перезавантажити можна прямо в грі.

## Встановлення

### Гравцям
1. Завантажте jar під свою версію Minecraft і лоадер зі сторінки [Releases](https://github.com/S1nuxoff/InGame-Messenger/releases/latest).
2. Закиньте його в теку `mods`. Для Fabric і Quilt потрібен ще [Fabric API](https://modrinth.com/mod/fabric-api).
3. Заходьте на сервер, де стоїть мод або плагін.

### Серверам
1. **Сервер із модами:** jar мода — у `mods` (на Fabric і Quilt ще Fabric API).<br>
   **Paper, Spigot, Purpur, Folia:** jar плагіна — у `plugins`.
2. Запустіть сервер один раз — він створить теку `config/ingamemessenger/` (у плагіна `plugins/InGameMessenger/`)
   з налаштуваннями й теками для стікерів та емодзі.
3. Додайте свої стікери, якщо хочете, і виконайте `/messenger reload`.

Налаштування за замовчуванням підходять одразу. Усі параметри, зовнішні бази, S3, стікери, команди й права описані в
[посібнику для сервера](docs/server.md) (англійською).

## Підтримувані версії

<img src="docs/images/platforms/minecraft.png" width="20" height="20" alt="Minecraft"> **22 версії Minecraft — від 1.19.2 до 26.3.**

| Платформа | Версії |
|---|---|
| <img src="docs/images/platforms/fabric.png" width="18" height="18" alt="Fabric"> **Fabric** · <img src="docs/images/platforms/quilt.png" width="18" height="18" alt="Quilt"> **Quilt** | 1.19.2 · 1.20 · 1.20.1 · 1.20.3 · 1.20.4 · 1.20.5 · 1.20.6 · 1.21 · 1.21.1 · 1.21.4 – 1.21.11 · 26.1 – 26.3 |
| <img src="docs/images/platforms/neoforge.png" width="18" height="18" alt="NeoForge"> **NeoForge** | 1.20.6 · 1.21 · 1.21.1 · 1.21.4 – 1.21.11 · 26.1 – 26.3 |
| <img src="docs/images/platforms/forge.png" width="18" height="18" alt="Forge"> **Forge** | 1.19.2 · 1.20.1 · 1.20.4 · 1.20.6 · 1.21.1 · 1.21.4 · 1.21.5 · 1.21.8 · 1.21.10 · 1.21.11 · 26.1.2 · 26.2 · 26.3 |
| <img src="docs/images/platforms/paper.png" width="18" height="18" alt="Paper"> **Paper** · <img src="docs/images/platforms/spigot.svg" width="18" height="18" alt="Spigot"> **Spigot** · <img src="docs/images/platforms/purpur.png" width="18" height="18" alt="Purpur"> **Purpur** | плагін, 1.20.1 і новіші |
| <img src="docs/images/platforms/folia.png" width="18" height="18" alt="Folia"> **Folia** | плагін, усі версії Folia починаючи з 1.21.4 |

<details>
<summary><b>Повна таблиця</b> (усі версії, лоадери й потрібна Java)</summary>

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

- Для Fabric потрібен [Fabric API](https://modrinth.com/mod/fabric-api). На Quilt ставте Fabric-версію разом із Fabric API.
- Деякі версії використовують той самий jar: Fabric-збірка для 1.20.1 підходить і для 1.20, для 1.20.4 — для 1.20.3,
  для 1.20.6 — для 1.20.5, збірки 1.21.1 для Fabric і NeoForge — для 1.21. Одна збірка покриває 1.21.6 – 1.21.8, інша —
  1.21.9 – 1.21.10, третя — 26.1 – 26.1.2. Для Forge кожна збірка під одну версію — так влаштований сам Forge.
- Кожен jar ставиться лише на свої версії. Поки не підтримуються: 1.19.3, 1.19.4, 1.20.2, 1.21.2, 1.21.3.

</details>

> [!NOTE]
> Кожну версію перевіряємо прямо в грі: мод сам проходить сценарій на тестовому сервері (повідомлення, стікери, картинки,
> голосові, опитування, особисті чати, мути), а кожен крок зберігається скриншотом.
> [Подивитися останні прогони.](https://s1nuxoff.github.io/InGame-Messenger-tests/)

## Керування

<details>
<summary>Показати</summary>

| Дія | Як |
|---|---|
| Відкрити чат | Клавіша чату (T) |
| Швидко вибрати стікер | Затиснути клавішу чату, клікнути стікер, відпустити |
| Панель стікерів | Кнопка зі смайликом у полі вводу; правий клік — закріпити панель |
| Стікер в обрані / подивитися ближче | Правий клік / затиснути в панелі |
| Реакція | Навести на повідомлення й натиснути кнопку реакції або клікнути двічі |
| Відповісти, скопіювати, змінити, видалити | Правий клік по повідомленню |
| Перейти до початкового повідомлення | Клік по цитаті |
| Меню гравця (особистий чат, згадка) | Клік по аватарці |
| Голосове | Затиснути кнопку мікрофона (її видно, коли поле вводу порожнє) |
| Прикріпити картинку, координати, опитування | Кнопка-скріпка |
| Вставити картинку | Ctrl+V |
| Пошук | Ctrl+F або лупа внизу списку чатів |
| Налаштування | Шестірня внизу списку чатів |

</details>

## Часті питання

<details>
<summary><b>Чи треба ставити мод усім гравцям?</b></summary>

Ні. Гравці без мода заходять і спілкуються як завжди, а стікери, голосові, картинки й опитування бачать короткими
рядками тексту. Месенджер бачать лише ті, у кого стоїть мод.
</details>

<details>
<summary><b>А на сервер його ставити треба?</b></summary>

Так: мод — на сервер із модами, плагін — на Paper, Spigot, Purpur або Folia. Якщо на сервері його немає, мод просто не чіпає чат.
</details>

<details>
<summary><b>Працює з чат-плагінами, рангами й мутами?</b></summary>

Так. Повідомлення з чат-плагінів на кшталт EssentialsX Chat показуються один раз, з рангом і кольорами від сервера.
Ранги беруться із сервера або LuckPerms/Vault, мути з EssentialsX, LiteBans і AdvancedBan теж працюють.
</details>

<details>
<summary><b>Velocity або BungeeCord?</b></summary>

Ставте на сервери за проксі, на сам проксі нічого не потрібно. У кожного сервера свій чат.
</details>

<details>
<summary><b>Хто може читати повідомлення?</b></summary>

Повідомлення, зокрема особисті, зберігаються на сервері без шифрування, тож адміністрація сервера може їх прочитати —
як і на будь-якій іншій платформі. Голосові й картинки зберігаються задану кількість днів, потім видаляються.
</details>

## Долучитися

Будемо раді баг-репортам, ідеям, перекладам і pull request’ам. Як зібрати мод самому чи надіслати зміни — у
[CONTRIBUTING.md](CONTRIBUTING.md) (англійською).

## Ліцензія

Вільне ПЗ під ліцензією [GNU GPL v3.0](LICENSE) з умовою про зазначення авторства в [NOTICE.md](NOTICE.md).
Сторонні ресурси й бібліотеки перелічені в [CREDITS.md](CREDITS.md).

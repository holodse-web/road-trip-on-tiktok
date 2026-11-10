# Road Trip on TikTok

**[🇷🇺 Русский](#-русский) · [🇬🇧 English](#-english)**

**Minecraft 26.3 · Fabric · 1.1**

[Скачать / Download](https://github.com/holodse-web/road-trip-on-tiktok/releases/latest) · [Сообщить об ошибке / Report an issue](https://github.com/holodse-web/road-trip-on-tiktok/issues)

<details open>
<summary><b>🇷🇺 Русский</b></summary>

## 🇷🇺 Русский

Автономная игра для TikTok LIVE: Стив и Алекс едут через 34 биома к цели **999 999 м**, а зрители помогают или мешают лайками, подарками и сообщениями. Машиной управляет мод.

| Событие | Что происходит |
|---|---|
| Лайки | Каждые 500 лайков дают +500 м с ускорением |
| Подарки | Каждый подарок — метры вперёд или назад. От 2 000 м — нитро, от 10 000 м машину забрасывает далеко вперёд; дорогие минусовые отбрасывают назад с кувырком |
| Ультраподарки | TikTok Universe: +999 999 м. Lion: −999 999 м |
| Чат | Имя моба на русском или английском вызывает его. Примерно 40% мобов помогают (корова толкает сзади), остальные мешают |
| Подписка | Машина перекрашивается в случайный цвет |

Подарки идут в очередь по приоритету: дорогие первыми и дольше держат экран, одинаковые дешёвые склеиваются («Роза ×12»). Подарок, которого нет в таблице, работает как подарок той же цены.

В **Подарки → Очередь подарков** можно задать паузу между эффектами: по умолчанию 0,3 с, диапазон 0–30 с. Подарки и метры не теряются; без очереди события обрабатываются сразу.

В **Мобы → Ограничения** задаётся общая пауза для сильных мобов: по умолчанию 30 с, диапазон 0–300 с. Обычные мобы и помощники проходят очередь без этой задержки; 0 отключает её.

Крупные минусовые подарки отбрасывают машину назад со звуком удара и дымным следом. Восстановление помогает только при застревании или перевороте.

### Что видят зрители
- **Последнее событие** под счётчиком: картинка подарка или моба, «ник · что сделал» и результат в метрах; зелёное толкает вперёд, красное — назад.
- **Баннер подарка** от 2 000 м: крупно ник, подарок и результат на месте подсказок.
- **Лента событий** под машиной хранит несколько предыдущих событий.
- **Лайки до награды**: полоска «320 / 500» показывает, сколько осталось до следующего рывка.
- Жёлтая линия под счётчиком — путь до цели.

### Требования
- Minecraft **26.3**, Fabric Loader **0.19.5+**, Fabric API **0.162.0+**
- Java 25 (идёт вместе с лаунчером Minecraft)

### Установка
1. Скачайте `rttt-1.1.jar` из [релизов](https://github.com/holodse-web/road-trip-on-tiktok/releases/latest), положите его и Fabric API в `.minecraft/mods`. Удалите предыдущую версию мода.
2. Создайте мир, в названии которого есть «Road Trip» или «RTTT» (подходят и старые слова «Алекс», «довези»). Игра запустится сама. В любом мире её включает клавиша **F6** или команда `/rttt start`.
3. Пульт управления: **F7** или <http://127.0.0.1:8765/>. Его можно добавить в OBS как «Док браузера».

### TikTok Live Studio / OBS: формат и качество
В пульте в **Настройки → Камера и машина** есть два режима:
- **9:16 · вписать в монитор** (также **F8**) — самое большое вертикальное окно, которое целиком помещается на текущем мониторе, с учётом рамки и панели задач. Фактические пиксели и FPS показаны рядом.
- **Full HD · захват игры** — настоящее окно **1080×1920**. На горизонтальном мониторе низ окна может выходить за экран. Для этого режима используйте источник **захвата игры Minecraft**, который захватывает кадр игры, а не изображение рабочего стола. Поддержку полного кадра проверьте в предпросмотре Live Studio.

В TikTok Live Studio выберите **вертикальный холст 1080×1920**, добавьте источник Minecraft, сбросьте старую обрезку и впишите источник в холст. Горизонтальные **1920×1080** и вертикальные **1080×1920** — разные форматы. Не растягивайте маленькое окно 608×1080 до Full HD, если нужна максимальная чёткость.

Разрешение вывода Live Studio должно соответствовать выбранному качеству; 60 FPS имеет смысл, когда игра и кодирование удерживают его стабильно. Мод задаёт размер кадра и показывает фактический FPS, а битрейт, кодировщик и качество передачи на TikTok настраиваются в Live Studio. Эти настройки мод не меняет.

Во вкладке **Интерфейс** пульта каждый блок на экране (счётчик метров, последнее событие, лайки, победы, подсказки, баннер подарка, лента событий, топ помощников, реплики) двигается мышкой прямо на макете кадра: перетаскивание меняет место, жёлтый кружок в углу — размер, справа настраиваются непрозрачность и видимость. Пока вкладка открыта, игра показывает все блоки с примерами. Переключатель **Зоны TikTok** накладывает места, которые у зрителей закрыты чатом и кнопками. Настройки сохраняются автоматически; блок не может выйти за край кадра.

На основной вкладке **Пульт**, рядом с погодой, кнопки **День**, **Ночь** и **Автоматически** выбирают время суток только в измерении дороги. Ручной режим сохраняется и фиксирует выбранное время, автоматический возобновляет цикл заданной длины. Кнопка **Скрыть подсказки / Показать подсказки** сразу управляет панельками для зрителей; повторный показ начинается с первой страницы без ожидания автоматической паузы.

### Подключение TikTok LIVE
- **TikFinity:** подключите свой LIVE в TikFinity, мод сам подключится к `ws://127.0.0.1:21213/`.
- **Встроенный мост:** нужен Node.js 22+ с npm. Во вкладке **TikTok** введите ник и нажмите **Запустить мост**. Зависимости устанавливаются в фоне; состояние и журнал видны в панели. При закрытии Minecraft мост останавливается. Для ручного запуска есть `bridge/start-bridge.bat`. Перед запуском освободите порт 21213, если его занимает TikFinity или другой мост.
- **HTTP:** `http://127.0.0.1:8765/api/event?type=gift&user=ник&name=Rose&count=1` (также `like`, `chat`, `follow`).

Пока подключения нет, игра работает в тестовом режиме, и все события можно проверить кнопками в пульте.

Мост не запрашивает необязательный полный каталог подарков: его отдельная подпись у провайдера может требовать Business-план. Название, стоимость и картинка подарка читаются из самого LIVE-события, если TikTok их передал; настроенные правила и иконки мода также сохраняются.

### Команды и клавиши
`F6` — старт/стоп · `F7` — пульт · `F8` — окно 9:16 в пределах монитора · `/rttt start | stop | panel | restart | window [w h] | mob <имя>`

### Производительность
- `install-optimization-mods.bat` скачивает с Modrinth Lithium, FerriteCore, ImmediatelyFast и ModernFix под 26.3.
- В настройках лаунчера дайте Minecraft 4–6 ГБ памяти: `-Xmx6G -XX:+UseZGC`. ZGC почти не даёт фризов при сборке мусора, поэтому на долгих стримах игра идёт ровнее.
- Мир дороги не сохраняется на диск, поэтому сейв не растёт.

### Сборка из исходников
На Windows запустите `build.bat --no-install`: результат — `build/rttt-1.1.jar`. Нужны Java 25, библиотеки Minecraft 26.3 и Fabric API в вашей `.minecraft`; сначала запустите игру хотя бы один раз. Без `--no-install` сборка также устанавливает JAR в `mods`. Gradle не нужен.

### Файлы и лицензии
- Шрифт Inter: SIL OFL 1.1 (`LICENSES/Inter-OFL.txt`).
- Звуки двигателя, тормозов, удара и клаксона: CC0, из [pmndrs/racing-game](https://github.com/pmndrs/racing-game). Остальные звуки синтезированы для мода или взяты из Minecraft.
- Иконки подарков принадлежат TikTok, поэтому их нет в репозитории. Мод скачивает их сам при первом запуске.

</details>

<details>
<summary><b>🇬🇧 English</b></summary>

## 🇬🇧 English

An autonomous game for TikTok LIVE: Steve and Alex drive through 34 biomes towards **999,999 m**, while viewers help or hinder them with likes, gifts and chat. The mod drives the car.

| Event | What happens |
|---|---|
| Likes | Every 500 likes give +500 m and a speed boost |
| Gifts | Each gift moves the car forward or back. From 2,000 m nitro, from 10,000 m the car is launched far forward; expensive negative gifts blast it back with a flip |
| Ultra gifts | TikTok Universe: +999,999 m. Lion: −999,999 m |
| Chat | Typing a mob name (English or Russian) spawns it. About 40% of mobs help (a cow pushes from behind), the rest hurt |
| Follow | The car gets a random new colour |

Gifts are queued by priority: expensive ones go first and stay on screen longer, identical cheap gifts merge ("Rose ×12"). A gift that is not in the table acts like a configured gift of the same price.

The panel lets you adjust the gift delay (0.3 s by default) and strong-mob cooldown (30 s). Large negative gifts knock the car back; recovery only helps when it gets stuck or flips over.

### What viewers see
- **Latest event** under the counter: a picture of the gift or mob, "user · what" and the result in metres; green pushed the car forward, red sent it back.
- **Gift banner** from 2,000 m: sender, gift and result in large type where the hint card normally sits.
- **Event feed** below the car keeps the few previous events.
- **Likes to reward**: a "320 / 500" bar shows how close the next push is.
- The gold line under the counter is progress to the goal.

### Requirements
- Minecraft **26.3**, Fabric Loader **0.19.5+**, Fabric API **0.162.0+**
- Java 25 (bundled with the Minecraft launcher)

### Install
1. Download `rttt-1.1.jar` from [Releases](https://github.com/holodse-web/road-trip-on-tiktok/releases/latest) and put it with Fabric API into `.minecraft/mods`. Remove the previous mod version.
2. Create a world whose name contains "Road Trip" or "RTTT" and the game starts by itself. In any world, press **F6** or run `/rttt start`.
3. Control panel: **F7** or <http://127.0.0.1:8765/>. You can add it to OBS as a browser dock.

### TikTok Live Studio / OBS
**F8** and the panel's **9:16** button fit the largest portrait window within the current monitor. The panel reports actual framebuffer pixels and FPS. **Full HD · захват игры** requests a real **1080×1920** client area, which can extend below a landscape monitor: use Minecraft **Game Capture**, and verify the complete frame in Live Studio's preview.

Set a portrait **1080×1920** canvas, reset the source's old cropping and fit the source to the canvas. Landscape 1920×1080 is a different aspect ratio. Bitrate, encoder and streaming output quality are configured in Live Studio. The mod does not alter them.

The panel's **Интерфейс** tab is a drag-and-drop layout editor: every on-screen block can be moved, resized, faded or hidden on a mock-up of the frame, with an optional overlay of the areas TikTok covers with chat and buttons. The dashboard provides **День / Ночь / Автоматически** for road-only daylight control and an immediate show/hide toggle for viewer hint cards.

### Connecting TikTok LIVE
- **TikFinity:** connect your LIVE in TikFinity; the mod connects to `ws://127.0.0.1:21213/` automatically.
- **Built-in bridge:** requires Node.js 22+ with npm. Enter your username in the panel's **TikTok** tab and click **Запустить мост**. Dependencies install in the background; status and logs appear in the panel. Closing Minecraft stops the bridge. For manual launch, use `bridge/start-bridge.bat`. Free port 21213 first if another relay or TikFinity is running.
- **HTTP:** `http://127.0.0.1:8765/api/event?type=gift&user=name&name=Rose&count=1` (also `like`, `chat`, `follow`).

Without a connection the game runs in test mode, and every event can be triggered from the panel.

### Keys and commands
`F6` start/stop · `F7` panel · `F8` 9:16 window · `/rttt start | stop | panel | restart | window [w h] | mob <name>`

### Performance
- `install-optimization-mods.bat` downloads Lithium, FerriteCore, ImmediatelyFast and ModernFix for 26.3 from Modrinth.
- Give Minecraft 4–6 GB in the launcher settings: `-Xmx6G -XX:+UseZGC`. ZGC barely causes garbage-collection stutters, so long streams run more smoothly.
- The road world is never saved to disk, so the save does not grow.

### Building from source
On Windows, run `build.bat --no-install` to create `build/rttt-1.1.jar`. You need Java 25, Minecraft 26.3 libraries and Fabric API in your `.minecraft`; launch the game at least once first. Without `--no-install`, the build also installs the JAR into `mods`. No Gradle needed.

### Assets and licences
- Inter font: SIL OFL 1.1 (`LICENSES/Inter-OFL.txt`).
- Engine, brake, crash and horn sounds: CC0, from [pmndrs/racing-game](https://github.com/pmndrs/racing-game). The other sounds were synthesised for the mod or come from Minecraft.
- Gift icons belong to TikTok, so they are not in the repository. The mod downloads them by itself on first launch.

</details>

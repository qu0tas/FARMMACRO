# Kaelix — руководство агенту по сопровождению и переносу

## 1. Сначала установить реальную исходную версию

Текущая линия: **2.1.0**, клиентский Fabric-мод Kaelix; mod id/package/config остаются `farmmacro`/`com.farmmacro`/`config/farmmacro`. Нельзя переименовывать их ради ребрендинга: сломаются настройки/бинды/ресурсы.

Прочитать `AGENTS.md`, этот файл, актуальную запись в разделе 6 «Журнал» `CONTEXT.md` (старый журнал — `docs/history/CONTEXT-JOURNAL.md`), затем релевантные исходники. Старые журнальные записи и старые валидаторы — история, а не описание текущего поведения. Особенно старые записи про B/автореконнект/послепанические автоответы не актуальны.

**Среда может быть восстановлена из старого снимка.** Перед правками сравнить `gradle.properties`, последние README/CHANGELOG, наличие классов функций и полный последний выданный ZIP. При расхождении восстановить полный `SOURCE-FILES.txt` последнего подтверждённого архива, сверив SHA256/CRC, и применить `REMOVED-FILES.txt`. Не выдавать старую alpha под новым номером. Git HEAD также может быть старше последних выданных архивов.

## 2. Подтверждённый стек — проверять перед каждым портом

- Minecraft **26.1.2**, официальные имена Mojang, без Yarn/remapping.
- Java **25**, Fabric Loader **0.19.5**, Fabric API **0.155.3+26.1.2**.
- Loom **1.18.3**, `net.fabricmc.fabric-loom`, non-remapping; Gradle **9.7.1**.
- Источник версий: `gradle.properties`, `build.gradle`, `gradle/wrapper/gradle-wrapper.properties`, `src/main/resources/fabric.mod.json`.
- Команды: `bash gradlew --no-daemon build`, затем при необходимости **короткая** `bash gradlew --no-daemon quickCheck`.
- JDK 25 Gradle находит сам (toolchain + foojay resolver скачивает его при отсутствии); путь локальной среды не вшивать в проект.

Не переносить 1.21/Yarn/Forge API по памяти. Проверять точные классы/методы целевой версии через `javap -classpath <cached-minecraft-jar> ...`; собирать после малых изменений. Ссылки для актуализации: https://docs.fabricmc.net/develop/ и https://docs.fabricmc.net/26.1.2/develop/porting . Перед следующим портом выбрать именно целевую версию документации.

## 3. Обязательные границы продукта

- Пользователь играет у ПК. Не превращать ручные помощники в unattended ботов.
- **Не запускать Minecraft, сервер, runClient/runServer, тяжёлые routeStress/configSweep/soundStress без отдельного запроса.** Только build и bounded quickCheck; явно указывать, что не проверено в игре.
- alpha.8: изолированный ручной Aim не создаёт панику. `CombatPanic.manualCameraOnly()`/`ManualAimPanicPolicy` исключают ручную камеру, но НЕ подавляют панику одновременно работающего автоматического модуля. Настройки автоматической паники сохраняются; menu/death/world/focus/Freecam gates тихо отпускают камеру. Не возвращать проверку оружия/телепорта/чата в manualTick.
- Новые ручные Aim: только камера. Ходьба, атаки, броски топора/лассо, REEL и предметы ручные. Никаких скрытых use/attack/slot/пакетов.
- Все повороты через `motion/MotionController`, политики — `MotionPolicy`, скорость — общий `MotionSettings.camera`. Нельзя писать отдельную packet rotation или второй несогласованный контроллер.
- При мире/смерти/меню/фокусе/Freecam/конкурирующей задаче/панике освобождать управление; не оставлять зажатые клавиши или старую цель. Новое ручное включение после локального stop.
- Паника alpha.5: без постоянной центральной B-плашки и обязательного подтверждения. Мягкие farm-события: ограниченная задержка350–750мс; Combat/критические/неизвестные — сразу. Не возвращать автоответ/автоповорот/авторезюм после стопа.
- Pests/Visitors/Bazaar/Loadouts/Experiments, Sessions/расписание, автореконнект и «человеческая реакция» удалены по решению пользователя (в 2.0.2 удалены и последние заглушки). Не возвращать; список файлов — `tools/removed-files.txt`.
- Не обещать отсутствие банов, разрешённость автоматизации или гарантированный урон/добычу.
- Без коммитов, push, публикации, стороннего сервера/авторизации без отдельного запроса. Пользователь получает JAR и полный ZIP исходников каждый раз.

## 4. Карта кода и точек риска

| Задача | Основные места |
|---|---|
| Инициализация, tick/frame/keys, passive GAME-message | `FarmMacroMod`, `mixin/*`, `farmmacro.mixins.json` |
| Конфиги/миграции | `config/ModConfig`, `CombatProfiles`, `ConfigCode`; не хранить runtime/активные клавиши/цель |
| Ручной/автоматический Aim | `combat/AimAssist`, `gui/CombatRows`, `ManualAimRows` |
| Slayer/Dungeon/Hunting names | `SlayerCatalog`, `DungeonTargets`, `BossTargets`, `MobCatalog`, `HuntingTargets`, `MobNames`, `ManualTargets` |
| Scythe manual prediction | `ProjectileLead`, `ManualScytheAim`, `ShotPath`;40бл/с — оценка, не закон сервера |
| Маршруты/«Начать с места» | `MacroManager`, `RouteStartHere`, `RouteWalker`; старт из середины только отдельной командой |
| Рыбалка | `fishing/*`, `FishingRows`, исходная атрибуция AutoFisher |
| Камера/ввод | `motion/*`, `camera/*`, `route/NavigationInput`, `SmoothTurn` |
| Защита/стоп | `panic/AutomationSafety`, `PanicDetector`, `FarmStopDelay`, `PanicHandoff` |
| HUD/GUI | `gui/*`, `hud/*`; предметы и модель26.1.2 требуют визуального теста пользователем |
| Короткие проверки | `src/stress/.../release/AttendedQuickCheck`, дочерние `*QuickCheck` |

26.1.2 rendering/inventory API уже менялся: не «чинить» дублирование HUD старой сигнатурой; проверять extraction/render state, flush и координаты. События/пакеты обрабатывать в client main thread; packet mixins должны проверять `isSameThread()`.

## 5. Известные ограничения, которые нельзя скрывать

### Ручные цели
Обычные игроки и ArmorStand-метки не тела целей. Данжевые NPC: консервативный UUIDv2 + полное известное имя/свежая связанная метка; неизвестные пропускаются. Владение боссом, настоящий Livid, иммунитет/фазы не определяются. Watcher без живого тела не цель; Thorn требует фазовое оружие. Hunting alpha.9: ручной hunting:nearby допускает любое живое видимое Mob-тело без фильтра имени (в том числе обычные животные); hunting:all — каталог или реальные aquatic/amphibian типы. Конкретные группы остаются каталогом. Только manualAllowed(body,name,c), не менять автоматический MobNames. Игроки/стойки/Display исключены; display-only/скрытые модели могут не определяться. Hunting acquires closest within range/FOV/LOS, then retains lock. ManualScytheAim.huntingPoint uses separate hunting tuning, no feet trick: bow/crossbow/Scythe by default, other items explicit opt-in. Speed40 is estimate, no gravity/drag solution; stale/teleport/reset/blocked prediction fallback remain.

### Удалённые ручные функции
В2.0.1 помощь по деревьям/Sweep удалена полностью, не возвращать из архивов без нового запроса. TreeAim/TreeAimPolicy/TreeGroup/TreeCutPolicy/SweepReport/SweepPlanner отсутствуют; passive GAME forwarding удалён. Старый префикс выбора foraging:* мигрирует в hunting:nearby, Aim не активируется. Остальные настройки сохраняются.

### Список режимов/целей
Rows.ListSelector открывает ChoicePickerScreen: EditBox поиск, FlatButton строки, scrollbar/колесо/↑↓/PageUp/PageDown/Enter, Esc/Назад без записи. Изменение конфига только при явном выборе. Новый экран не запускает автоматику. Используется в ManualAimRows и CombatRows; другие малые unrelatedselectors оставлены как были. Minecraft26.1.2 API EditBox/Screen проверен javap перед кодом.

### Проверки релиза 2.1.0
Выполнено: `./gradlew clean build compileStressJava routeStress` (Error Prone/NullAway без предупреждений, spotlessCheck, checkRemovedFiles, все JUnit-тесты включая старые quickCheck), `runClientGameTest` в настоящем Minecraft 26.1.2 под Xvfb (все экраны, мир, паника, миксины; скриншоты). Не проверялось: Hypixel, сеть, Discord-вебхук, звук. Новые ключи конфига с переименованием — только через шаг в `ConfigMigrator` с увеличением `CURRENT`.

### Проверки релиза 2.0.2
Выполнено: `./gradlew build` (включает `checkRemovedFiles` и `quickCheck`), `compileStressJava`, `routeStress` — без Minecraft/runClient/runServer. Игровая проверка не проводилась: это не live validation. Исторические validation-файлы (`docs/history/validation/`) относятся к прежним версиям.

## 6. Порядок переноса Minecraft/Fabric

1. Сохранить исходный полный ZIP/контрольные суммы и backup пользовательского config; делать работу в отдельной копии/ветке без публикации.
2. Уточнить target MC, loader, Java; «новая версия» может означать только Kaelix release, а не смену MC.
3. Проверить совместимый комплект MC/Loader/API/Loom/Gradle/JDK в документации, не обновлять зависимости «до latest» независимо друг от друга.
4. Изменить pins и диапазоны `fabric.mod.json`; сначала compileJava. Проверить все mixin targets, descriptors, injection points и accessors против target jar. Не заглушать обязательные injection ошибки через require0.
5. Восстановить compile ошибок по подсистемам: ввод/window, клиентский мир/трассировки, компоненты item/NBT, screen/widgets, HUD/render, сетевые hooks. Чистые алгоритмы не переписывать без причины.
6. Сравнить конфиги/миграции. Не сбрасывать профили/пути/бинды/HUD позиции, не включать выключенную автоматику. Новые параметры — ограниченные значения, безопасный fallback, runtime не сериализуется.
7. `clean build quickCheck` без игровых/тяжёлых tasks. Проверить удалённые файлы, class/resources в JAR, версию/лицензии, mixin count с обоснованием изменений. В 2.0.2 и 2.1.0 — 25 клиентских mixin, новых не добавляли (2.1.0: плагин `KaelixMixinPlugin` только считает применённые).
8. Передать пользователю короткий live-чеклист: запуск/GUI/HUD, input/stop/focus/freecam, маршруты/partial start, Aim/ручной hotbar, NPC/LOS, рыбалка. Нет live-проверки — не писать «полностью протестировано» и не переводить в stable по собственной инициативе.
9. Обновить README/CHANGELOG/CONTEXT/whatsnew/этот handoff и validation. Обновить версию архива в attribution notices. Закрепить точные источники механики и ссылки.

## 7. Упаковка и права

Проект GPL-3.0-only. Сохранить LICENSE, `third_party`, `src/main/resources/licenses` и pinned SHA/атрибуции Aether/AutoFisher/прочих. Публичный код ≠ разрешение копировать: проверять лицензию. Не копировать CC BY-NC-SA реализацию или тексты wiki в GPL исходники; механики реализовывать самостоятельно с краткой ссылкой на исследование.

JAR: `build/libs/kaelix-<version>.jar`, не sources-JAR и не dev-only. ZIP: полный соответствующий исходный проект, включая wrapper/settings/build/pins, `.agents` инструкции, ресурсы, лицензии, все source sets и docs. Не класть кеши/.git/config пользователя/секреты/локальные SDK. Опираясь на manifest последнего архива, добавлять новые файлы, явно перечислять удаления; бездумный `git ls-files` может потерять новые некоммиченные классы. Добавить `SOURCE-FILES.txt`, `REMOVED-FILES.txt`, инструкцию загрузки, build log и SHA256 JAR. Проверить CRC, идентичность исходников/ресурсов и встроенного JAR. GitHub загружает пользователь.

## 8. Перед финальной фиксацией

Рекомендация: feature freeze после ручного Aim и нужных исправлений, не бесконечное добавление макросов. Стабильная версия — только после пользовательского live-теста и устранения воспроизводимых багов. Честные незакрытые Alpha-функции либо остаются помеченными Alpha, либо выключаются по умолчанию/переносятся за пределы stable. Полезные небольшие улучшения: единая причина WAIT в HUD, export диагностик без личных данных, backup/restore конфига, список изменений и восстановление предыдущего JAR. Не обещать эти функции до реализации.

## Справочные источники
https://hypixelskyblock.minecraft.wiki/w/Hunting
Сохранённые лицензии и pinned references в third_party/resources/licenses. Исторические исследования удалённых функций не означают наличие этих функций в релизе.

# CONTEXT — Farm Macro

Единый источник правды о проекте. Читай его целиком перед любой работой,
обновляй после каждого изменения. Последнее обновление: 2026-10-09.

---

## 0. Правила ведения проекта (обязательно для людей и ИИ-агентов)

1. **Перед работой** прочитай этот файл целиком и `README.md`.
2. **После каждого изменения** в коде, сборке или ресурсах, в том же коммите:
   - обнови этот файл: раздел «2. Текущее состояние», «7. Журнал изменений» (новая запись сверху),
     «8. Что осталось сделать» и всё, что устарело (архитектура, версии, грабли);
   - обнови `README.md`, если изменилось то, что видит пользователь: версии, установка, клавиши,
     функции, настройки, файлы, сборка;
   - в записи журнала пиши: **что сделано**, **зачем**, **как проверено** (собрано / проверено в игре / не проверено),
     **что осталось или сломано**.
3. Пиши так, чтобы новый чат с ИИ мог продолжить работу, прочитав только `CONTEXT.md`:
   конкретные имена классов, методов, файлов, версий; никаких «как обсуждали выше».
4. Не помечай как «работает» то, что не проверено в игре — пиши «собрано, в игре не проверено».
5. Не добавляй в репозиторий архивы, бэкапы, `build/`, `.gradle/`, `.idea/` (см. `.gitignore`).
6. Соблюдай ограничения проекта из раздела «1. Назначение и границы».

---

## 1. Назначение и границы

- **Что это:** клиентский Fabric-мод: запись и повтор действий на ферме + аварийный стоп («паника»).
- **Зачем:** для видео. Используется в одиночной игре или на своём сервере.
- **Границы (согласовано с автором, не менять):**
  - паника = только **остановка** макроса + красный экран + звук;
  - в мод **не добавляются** «очеловечивание» (рандомизация, микропаузы, задержки — бывший `MacroHumanizer`)
    и ответные «живые» движения после паники (бывший `PanicMoveStorage` / `panic_moves`);
  - никаких функций для обхода античитов, проверок админов или правил чужих серверов.

---

## 2. Текущее состояние

- **Основная версия:** мод 1.1.0 для **Minecraft 26.1.2**. Собирается. **Проверено в игре на 26.2** (автор: «всё работает»);
  сборка под 26.1.2 — собрана, в игре ещё не проверена.
- **Известные баги:** в меню (`FarmMacroScreen`) часть кнопок не нажимается и элементы накладываются друг на друга
  (см. раздел 8).
- **Репозиторий:** https://github.com/qu0tas/FARMMACRO (публичный). Путь у автора: `C:\AIcheats\MODS\farmmacro\`.

---

## 3. Стек и версии

### Текущий (26.1.2)
| Компонент | Версия |
|---|---|
| Minecraft | 26.1.2 (игра не обфусцирована, имена Mojang) |
| Gradle-плагин | `net.fabricmc.fabric-loom` **1.18.3** (без remap: `implementation`, не `modImplementation`, нет строки `mappings`) |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.155.3+26.1.2 |
| Лицензия | MIT (`LICENSE`, `fabric.mod.json`) |
| Gradle | 9.7.1 |
| Java | 25 (`options.release = 25`, mixins `JAVA_25`) |

Версии лежат в `gradle.properties`; в `fabric.mod.json` подставляются через `processResources`
(`${version}`, `~${minecraft_version}`, `>=${loader_version}`, `>=${fabric_version}`).

### Вариант для 26.2
`minecraft_version=26.2`, `fabric_version=0.161.0+26.2`, остальное то же. Отличия в коде — раздел 6.

### Старый (1.21.11, мод 1.0.0)
Loom `fabric-loom` 1.13.6, Loader 0.18.1, Fabric API 0.141.3+1.21.11, Gradle 9.2.1, Java 21.
Сначала Yarn `1.21.11+build.4`, потом переведён на `loom.officialMojangMappings()`.

---

## 4. Архитектура

Пакет `com.farmmacro`, все классы — клиентские (`"environment": "client"`), точка входа `FarmMacroMod` (`ClientModInitializer`).

| Класс | Роль |
|---|---|
| `FarmMacroMod` | Регистрирует клавиши (`KeyMappingHelper`, своя категория `farmmacro:general`), HUD, и в `ClientTickEvents.END_CLIENT_TICK`: опрос клавиш → `tickRecord` → `PanicDetector.tick` (если играет) или `tickEffectsOnly` → `tickPlayback`. |
| `config/ModConfig` | Все настройки, Gson → `config/farmmacro.json`. Ошибки чтения/записи пишутся в лог. |
| `macro/MacroFrame` | Один тик записи: x/y/z, yaw/pitch, forward/back/left/right, jump, sneak, sprint, attack, use, слот. |
| `macro/MacroManager` | Запись, воспроизведение (по списку `frames`), цикл, детект застревания (`isBlockedByWall` + кеш XZ-клеток маршрута), сохранённое место остановки и возобновление (откат на 2 кадра), статистика сессии. Клавиши жмёт через `KeyMapping.set(KeyMappingHelper.getBoundKeyOf(key), …)` — по реальной привязке игрока. |
| `macro/MacroStorage` | Сохранение/загрузка/удаление макросов в `config/farmmacro_macros/*.json` (`{"name", "frames"}`). |
| `macro/StatsOverlay` | HUD статистики сессии. |
| `macro/SavedPositionRenderer` | HUD-стрелка и расстояние до сохранённого места остановки. |
| `gui/FarmMacroScreen` | Меню, 3 вкладки: «Паника» (детекторы и пороги), «Реакция» (звук, красный экран), «Сохранения» (цикл, HUD, список макросов). Свои хелперы `tog`/`inp`/`lbl`, ручная раскладка координатами. |
| `gui/SaveMacroScreen` | Окно ввода имени после остановки записи. |
| `panic/PanicDetector` | Детекторы А–З, урон, застревание; `triggerPanic` → `MacroManager.stopPlayback` + звук (Minecraft или системный через `javax.sound`, `config/farmmacro/panic.wav` или встроенный бип) + красный экран. |
| `panic/PanicOverlayRenderer` | HUD красного экрана. |
| `mixin/PanicBlockMixin` | `ClientLevel.setBlocksDirty` — твёрдый блок появился ближе `blockDetectRadius` к глазам. |
| `mixin/PanicGuiMixin` | Открытие экрана во время макроса (кроме `PauseScreen`). **26.1.2: `Minecraft.setScreen`; 26.2: `Gui.setScreen`.** |
| `mixin/PanicPacketMixin` | `ClientPacketListener.handleMovePlayer` (серверный поворот/телепорт), `handleUpdateMobEffect`, `handleRemoveMobEffect`. |

HUD-элементы регистрируются через `HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("farmmacro", …), …)`.

### Клавиши по умолчанию
R — запись, P — воспроизведение, Delete — очистить, Right Shift (344) — меню, O — продолжить, End — сбросить место.
Значения R/P/Delete/меню хранятся в `farmmacro.json` (`keyRecord`, `keyPlay`, `keyClear`, `keyOpenGui`).

---

## 5. Сборка и проверка

- `./gradlew build` → `build/libs/farmmacro-<mod_version>.jar`; `./gradlew runClient` — запуск игры.
- Нужна **JDK** 25 (не JRE: без `javac` будет ошибка «does not provide JAVA_COMPILER»).
- **Не писать** `org.gradle.java.home` в `gradle.properties` проекта — на GitHub Actions этого пути нет, сборка падает.
- CI: `.github/workflows/build.yml` (checkout/setup-java/wrapper-validation v5, Java 25). Автор его удалял — если нужен, вернуть.

---

## 6. Грабли и заметки по версиям

### 1.21.11 (Yarn, исторически)
- Yarn 1.21.11 несовместим с Loom < 1.13 («Unsupported unpick version»); Loom 1.6.5/1.9.2 не дружат с Gradle 9.2.1;
  рабочая связка: Loom 1.13.6 + Yarn build.4 + Loader 0.18.1.
- Категория клавиш — объект `KeyBinding.Category`, не строка.
- `PlayerInventory.selectedSlot` приватный → `getSelectedSlot()/setSelectedSlot()`.

### Перенос 1.21.11 → 26.x
1. Yarn → Mojang на 1.21.11: `./gradlew migrateMappings --mappings "net.minecraft:mappings:1.21.11"` (строки в `@Inject` тоже переименовались).
2. В 26.x:
   - `KeyBindingHelper` (`keybinding.v1`) → `KeyMappingHelper` (`keymapping.v1`);
   - `HudRenderCallback` → `HudElementRegistry`;
   - `GuiGraphics` → `GuiGraphicsExtractor`, `drawString` → `text`;
   - `Screen.render/renderBackground` → `extractRenderState/extractBackground`;
   - `player.displayClientMessage(msg, true)` → `player.sendOverlayMessage(msg)`.
3. **Только в 26.2:** `minecraft.setScreen(...)` / `minecraft.screen` → `minecraft.gui.setScreen(...)` / `minecraft.gui.screen()`,
   и `PanicGuiMixin` целится в `Gui.setScreen`. В 26.1.2 всё ещё в `Minecraft`.
- Версии 26.2.1 не существует (стабильные: 26.1, 26.1.1, 26.1.2, 26.2, 26.3).

Руководства: NeoForge primers [1.21.11→26.1](https://docs.neoforged.net/primer/docs/26.1/), [26.1→26.2](https://docs.neoforged.net/primer/docs/26.2/);
Fabric: [porting](https://docs.fabricmc.net/develop/porting), [example mod](https://github.com/FabricMC/fabric-example-mod).

---

## 7. Журнал изменений (новое сверху)

### 2026-10-09 — Лицензия MIT
- **Сделано:** `LICENSE` заменён с CC0 (из шаблона) на MIT (© 2026 Vadik), совпадает с `"license": "MIT"` в `fabric.mod.json`.
  В README добавлен раздел «Лицензия».
- **Проверено:** только документы, код не менялся.

### 2026-10-09 — README, CONTEXT, правила
- **Сделано:** README переписан под мод (установка, клавиши, паника, файлы, сборка). CONTEXT переписан целиком:
  правила ведения, границы, архитектура, грабли, журнал, план. Добавлены `AGENTS.md` и `CLAUDE.md` со ссылкой на правила.
- **Проверено:** только документация, код не менялся.

### 2026-10-09 — Сборка под 26.1.2
- **Сделано:** версии → Minecraft 26.1.2, Fabric API 0.155.3+26.1.2. Открытие экранов возвращено на `Minecraft.setScreen/screen`,
  `PanicGuiMixin` → `Minecraft.setScreen`. Остальные цели миксинов сверены с 26.1.2.
- **Проверено:** собрано. В игре не проверено.

### 2026-10-09 — Перенос на 26.2 (мод 1.1.0)
- **Сделано:** Yarn → Mojang на 1.21.11, затем Loom 1.18.3 / Java 25 / Gradle 9.7.1 / Fabric API 0.161.0+26.2 и правки API (раздел 6).
- **Проверено:** собрано; **автор проверил в игре — всё работает.**

### 2026-10-09 — Ревью и чистка (мод 1.0.0, 1.21.11)
- **Удалено:** `MacroHumanizer`, `HumanizeConfigScreen`, вкладка Humanize, `PanicMoveStorage` и движения после паники,
  пустой `ClientPlayerEntityMixin`, файлы шаблона `com.example`, `crop.rar`, неиспользуемый `cropCooldown`.
- **Исправлено:** макрос жал дефолтные клавиши вместо назначенных; конфликт Delete (очистить/сброс → End);
  меню на F3 → Right Shift; не записывались yaw/pitch (старые макросы — с нулями); «продолжить» после загрузки
  другого макроса; категория клавиш через рефлексию; `blockDetectRadius` не использовался; молчаливые ошибки конфига.
- **Сборка:** убран `org.gradle.java.home` (причина падения на GitHub); версии в `gradle.properties`;
  честные `depends` в `fabric.mod.json`; `*.zip`/`*.rar` в `.gitignore`.
- **Проверено:** собрано; автор проверил в игре — работает, кроме багов меню.

---

## 8. Что осталось сделать

1. **Меню (`FarmMacroScreen`)** — кнопки не нажимаются и накладываются. Гипотеза: при большом масштабе интерфейса
   ручная раскладка не влезает по высоте, элементы перекрывают друг друга и кнопки «Сохранить/Отмена».
   Нужно: скриншоты, масштаб интерфейса автора, список некнажимаемых кнопок → переделать раскладку / добавить прокрутку.
2. Проверить сборку 26.1.2 в игре.
3. Удалить с GitHub старые файлы: `MacroHumanizer.java`, `HumanizeConfigScreen.java`, `PanicMoveStorage.java`,
   `ClientPlayerEntityMixin.java`, `crop.rar`, `src/client/`. Залить актуальную версию под 26.1.2.
4. Решить, держать ли вариант под 26.2 (например, отдельной веткой `mc-26.2`).
5. Старые макросы записаны с yaw/pitch = 0 — проверка застревания для них неточная; перезаписать.

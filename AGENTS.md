# AGENTS.md

Сначала прочитай `UPDATING.md` (передача проекта и порт Minecraft/Fabric), затем `CONTEXT.md` перед правкой: правила (раздел 0), границы (раздел 1), архитектура и особенности API Minecraft 26.1.2.

Коротко:
- Сборка: `./gradlew build` (JDK 25; `build` = компиляция с Error Prone/NullAway, `spotlessCheck`, `checkRemovedFiles`, JUnit `test` со старыми `quickCheck`). Перед коммитом `./gradlew spotlessApply`. Расширенные проверки без игры: `./gradlew compileStressJava routeStress`. Настоящий Minecraft: `./gradlew runClientGameTest` (на Linux без экрана — `xvfb-run`), скриншоты в `build/run/clientGameTest/screenshots`.
- Новый код: интервалы — `util/Clock` (не `System.currentTimeMillis()`), запись файлов — `util/IoWorker`/`AtomicFiles`, новый ключ конфига с переименованием/сменой смысла — шаг в `config/ConfigMigrator` (+`CURRENT`), строки нового UI — `gui/Tr.t(ключ, русский текст)` + ключи в `en_us.json`/`ru_ru.json` (проверяет `LangFilesTest`).
- После каждого изменения — запись в `CONTEXT.md` → «Журнал» (что / зачем / как проверено / что осталось) и обновление README при изменениях для игрока. Старый журнал — `docs/history/CONTEXT-JOURNAL.md` (история, не правила).
- Паника: стоп + экран + звук, без B-плашки/обязательного подтверждения и без автоответов/автоповоротов/автопродолжения. Мягкие Farm-события — короткая ограниченная задержка, Combat/критические — сразу. Не возвращать удалённое поведение из старого журнала.
- Удалённые модули (Pests, Visitors, Stash → Bazaar, Loadouts, расписание, автореконнект, «человеческая реакция») не восстанавливать; файлы перечислены в `tools/removed-files.txt` — сборка упадёт, если они вернутся.
- Ручной запуск Minecraft (runClient/runServer) и тяжёлые симуляции (routeStress/configSweep) — только по запросу пользователя; автоматические game-тесты гоняет CI. Каждый выпуск: JAR (в GitHub Releases, не в git) + полный соответствующий source ZIP; релиз — тег `vX.Y.Z` (`release.yml`).
- Скилл для ИИ-агентов: `.agents/skills/minecraft-mod-dev/SKILL.md` (для Claude — ссылка `.claude/skills/minecraft-mod-dev/SKILL.md`). Правила проекта в его начале важнее общих примеров (1.21.x, NeoForge, блоки/datagen).

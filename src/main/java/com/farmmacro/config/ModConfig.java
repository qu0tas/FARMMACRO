package com.farmmacro.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Настройки мода (config/farmmacro.json). Все поля читает/пишет GUI напрямую;
 * после изменения достаточно вызвать {@link #save()}.
 * Неизвестные поля из старых версий Gson просто игнорирует, отсутствующие берутся по умолчанию,
 * после загрузки значения проходят {@link #sanitize()}.
 */
public class ModConfig {

    public static ModConfig INSTANCE = new ModConfig();

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Путь берётся лениво: класс можно грузить вне игры (проверки routeStress — старые конфиги, пресеты). */
    private static Path configPath() { return FabricLoader.getInstance().getConfigDir().resolve("farmmacro.json"); }

    // ── Детекторы паники (работают только пока макрос играет) ────────────────
    public boolean panicEnabled        = true;

    /** Камеру повернули мышью (макрос камеру не крутит, если не включён replayCamera). */
    public boolean detectRotation      = true;
    public float   yawThreshold        = 5.0f;
    public float   pitchThreshold      = 5.0f;

    /** Сервер сам сдвинул/повернул игрока (пакеты телепорта и поворота). */
    public boolean detectServerMove    = true;
    /** Минимальный сдвиг за один пакет, блоков (мелкие откаты от лагов игнорируются). */
    public double  serverMoveThreshold = 1.0;
    /** Минимальный поворот за один пакет, градусов. */
    public float   serverRotateThreshold = 2.0f;

    /** Рядом с игроком появился твёрдый блок (пакет от сервера, свои постановки не считаются). */
    public boolean detectBlockInFace   = true;
    /** На сколько блоков расширяется хитбокс игрока при проверке. */
    public double  blockDetectRadius   = 0.75;

    /**
     * Любой пакет телепорта/поворота от сервера, даже без сдвига (/tp @s ~ ~ ~, «проверка на реакцию»).
     * Откат античита («moved too quickly») тоже считается.
     */
    public boolean serverMoveAny       = true;
    /**
     * v1.11 «Синхронизация»: сервер поправил позицию/взгляд больше эпсилона (меньше порогов телепорта/поворота).
     * Ниже эпсилона — только строка в логе. «Любой пакет телепорта» — отдельный переключатель.
     */
    public boolean detectServerSync    = true;
    /** Эпсилон позиции, блоков. */
    public double  serverPosEpsilon    = 0.03;
    /** Эпсилон взгляда (yaw и pitch отдельно), градусов. */
    public float   serverRotEpsilon    = 0.05f;
    /** v1.11 «Параметры движения»: сервер изменил скорость, прыжок, гравитацию, шаг, размер, скорость ходьбы/полёта. */
    public boolean detectMoveAttrs     = true;
    /** Допуск изменения параметра движения, % от прежнего значения. */
    public double  attrEpsilon         = 1.0;
    /** v1.11 «Препятствие впереди»: по взгляду или по ходу автохода сервер поставил твёрдый блок. */
    public boolean detectObstacle      = true;
    /** Длина луча, блоков. */
    public double  obstacleRange       = 4.0;
    /** v1.11 «Аномалия скорости» (только маршрут по точкам): фактическая скорость разошлась с ожидаемой. */
    public boolean detectVelocity      = true;
    /** Допуск, блоков за тик (ходьба ≈ 0.22, бег ≈ 0.28). */
    public double  velocityAnomaly     = 0.25;
    /**
     * v1.11 «Подозрительность»: мелкие события ниже порогов дают очки с весом, очки затухают (полураспад);
     * сумма ≥ лимита — паника «Много мелких аномалий». Косвенное «внешнее наблюдение».
     */
    public boolean suspicionEnabled    = true;
    public double  suspicionLimit      = 10;
    /** Полураспад очков, секунд: через столько вклад события вдвое меньше. */
    public double  suspicionHalfLifeSec = 60;
    /** Веса: пакет телепорта/поворота ниже эпсилона (или без сдвига). */
    public double  suspWeightSync      = 1;
    /** Лаг-откат: сдвиг больше эпсилона, но меньше «Мин. сдвиг» (когда «Синхронизация» выкл). */
    public double  suspWeightRollback  = 2;
    /** Толчок слабее «Мин. толчок». */
    public double  suspWeightKnock     = 2;
    /** Новый игрок в прорисовке, но дальше радиуса «Игрок рядом» (раз за запуск на игрока). */
    public double  suspWeightPlayerFar = 2;
    /** Слово из списка в чате без ника (только если «Слова без ника — в подозрительность»). */
    public double  suspWeightChatWord  = 4;
    /** Новая надпись над хотбаром без ника/слова (цифры не различаются — таймеры не копятся). */
    public double  suspWeightActionBar = 0.5;
    /** Слово из списка без ника — очки подозрительности вместо мгновенной паники. Ник — всегда паника. */
    public boolean chatWordsSoft       = false;
    /** Полоска «ПОДОЗР. N/10» в HUD, пока играет макрос. */
    public boolean suspicionHud        = true;
    /** v1.11 «Журнал событий»: config/farmmacro/logs/events-ГГГГ-ММ-ДД.log (фоновый поток, ротация 5 МБ). */
    public boolean eventLogEnabled     = true;
    /** Сервер толкнул: пакет скорости игрока (отдача, удочка, плагины) или отдача взрыва. */
    public boolean detectKnockback     = true;
    /** Минимальная скорость толчка, блоков за тик. */
    public double  knockbackThreshold  = 0.05;
    /** Чат: сообщение с твоим ником или словом из списка (свои сообщения не считаются). */
    public boolean detectChat          = true;
    public boolean chatMentionName     = true;
    /** Слова через запятую, без учёта регистра. */
    public String  chatKeywords        = DEFAULT_KEYWORDS;
    public static final String DEFAULT_KEYWORDS = "макро, macro, бот, bot, афк, afk, ты тут, ответь, проверка, check, админ, "
            + ModConfig.V14_KEYWORDS;
    /** v1.14: staff-строки (ЗАГЛАВНЫМИ — с учётом регистра и целым словом), дописываются один раз и в старые конфиги. */
    public static final String V14_KEYWORDS = "ROTATED, TELEPORTED, BEDROCK CAGED, BANWAVE, DIRT CHECKED, COBWEB CHECKED, KNOCKED OUT";
    /** Служебное: 1 — слова v1.14 уже дописаны в chatKeywords (0 — старый конфиг; новый допишет без повторов). */
    public int     chatKeywordsRev     = 0;
    /** Титр/подзаголовок на экране (/title); actionbar — только с ником или словом из списка. */
    public boolean detectTitle         = true;
    /** Режим игры, полёт, респавн / смена мира, посадили на сущность. */
    public boolean detectGameMode      = true;
    /** Другой игрок подошёл ближе радиуса (невидимый тоже — сущность приходит). */
    public boolean detectPlayerNear    = true;
    public double  playerNearRadius    = 12;
    /** Игрок в списке (Tab) стал наблюдателем: его самого не видно, а смену режима — видно. */
    public boolean detectSpectator     = true;
    /** Игрок зашёл / вышел (выход из Tab = часто «ваниш»). */
    public boolean detectPlayerJoin    = true;
    /** Предмет в руке сменился или пропал сам (слот тот же; кончились семена/блоки — не считается). */
    public boolean detectHeldItem      = true;

    public boolean detectSlotChange    = true;
    public boolean detectGuiOpen       = true;
    public boolean detectDamage        = true;
    /** Новый эффект / смена уровня / досрочное снятие (обновление того же эффекта маяком — игнор). */
    public boolean detectPotionEffect  = true;

    /** Застревание: по записи игрок должен был пройти заметное расстояние, а реально стоит. */
    public boolean detectStuck         = true;
    public int     stuckThresholdTicks = 30;

    /** Сход с маршрута: траектория отклонилась от записанной больше чем на N блоков. */
    public boolean detectDrift         = false;
    /** Маршрут по точкам: «Не тот этаж» после спуска, «Нет пути» к точке на другом этаже. */
    public boolean detectFloor         = true;
    public double  driftThreshold      = 4.0;

    // ── v1.13 «THE BIG PANIC UPDATE» ─────────────────────────────────────────
    /** Лаги сервера (нет пакета времени > 1.3 с): «Застрял»/«Аномалия скорости» не паникуют, откаты — в подозрительность. */
    public boolean lagGuard            = true;
    /** Игрок загорелся (горел на старте — не считается, пока не потухнет). */
    public boolean detectFire          = true;
    /** Паутина, куст сладких ягод, рыхлый снег: сервер поставил рядом или игрок в нём застрял. */
    public boolean detectSticky        = true;
    public double  stickyRadius        = 8;
    /** Клетка: над головой и с ≥ 3 сторон бедрок/барьер, хотя бы один блок поставил сервер во время запуска. */
    public boolean detectCage          = true;
    /** Блок на ферме: сервер поставил полный блок на месте воздуха/урожая/воды → подозрительность; упёрся в него → паника. */
    public boolean detectFarmBlock     = true;
    public double  farmBlockRadius     = 16;
    public double  suspWeightFarmBlock = 3;
    /** Урожай не ломается: блоков в секунду по «рабочим» тикам (ЛКМ + ход) ниже порога. */
    public boolean detectBps           = false;
    public double  bpsMin              = 3;
    public double  bpsWindowSec        = 5;
    /** Рассинхрон: сломанные блоки через 1 с снова на месте (≥ N % из последних 20). */
    public boolean detectDesync        = true;
    public double  desyncPct           = 75;
    /** Полный инвентарь — паника (а не обычный стоп, как stopWhenInventoryFull). */
    public boolean panicInventoryFull  = false;
    /** Отключение от сервера во время макроса — сирена и запись в журнал. */
    public boolean detectDisconnect    = true;
    /** Перезагрузка / лимбо: «Server closing: 0:25» на табло (< 30 с), слова перезагрузки и лимбо в системном чате и титрах. */
    public boolean detectRestart       = true;
    /** Банвейв (Hypixel API, ключ — в config/farmmacro/hypixel-api-key.txt): банов за окно ≥ порога. */
    public boolean detectBanwave       = false;
    public int     banwaveThreshold    = 50;
    public int     banwaveWindowMin    = 15;
    public boolean banwaveWithStaff    = true;
    /** Skyblock Jacob's Contest: собрано ≥ лимита (по табло) — обычный стоп. 0 — без лимита. */
    public boolean detectJacob         = false;
    public int     jacobLimit          = 0;
    /** Skyblock: «Ник is visiting Your Garden/Island». */
    public boolean detectVisitor       = true;
    /** Откат назад: сервер вернул на место из последних ~3 с пути (< 4 бл) — подозрительность вместо паники. */
    public boolean lagbackSoft         = false;
    public double  suspWeightLagback   = 3;
    /** Плашка HUD: TPS сервера, BPS, баны (во время макроса). */
    public boolean serverHud           = true;

    // ── v1.13.1: расписание, выход, переподключение, анти-застревание, курсор, производительность ──
    /** Расписание: фармить N мин, потом перерыв M мин (оба ± разброс %), потом продолжить с места. */
    public boolean schedEnabled        = false;
    public int     schedFarmMin        = 60;
    public int     schedBreakMin       = 10;
    public int     schedJitterPct      = 20;
    /** Таймер выхода: через N мин после старта (перерывы считаются) — стоп и выход с сервера. 0 — выкл. */
    public int     leaveAfterMin       = 0;
    /** Переподключение после вылета (не после паники ≤ 60 с и не после выхода через меню). */
    public boolean autoReconnect       = false;
    public int     reconnectDelaySec   = 15;
    public int     reconnectTries      = 3;
    /** Команда после входа (например «/warp garden»), пусто — нет. */
    public String  reconnectCommand    = "";
    /** Продолжить макрос с точки остановки, когда игрок снова рядом с ней. */
    public boolean reconnectResume     = true;
    /** Анти-застревание (только маршрут по точкам): столько раз отойти назад с прыжком, потом — паника «Застрял». 0 — сразу паника. */
    public int     antiStuckTries      = 0;
    /** «Отпустить курсор» (клавиша U): курсор свободен, макрос дальше ломает и ходит; при старте — по настройке. */
    public boolean ungrabOnStart       = false;
    /** Режим производительности (F9): FPS-лимит, без облаков/теней, минимум частиц; при старте — по настройке. */
    public boolean perfOnStart         = false;
    public int     perfFps             = 30;
    /** Служебное: настройки графики до режима производительности (−1 / null — не сохранено). Возвращаются при запуске игры. */
    public int     perfSavedFps        = -1;
    public String  perfSavedClouds     = null;
    public String  perfSavedParticles  = null;
    public int     perfSavedShadows    = -1;
    public double  perfSavedEntityDist = -1;

    // ── v1.14: игроки (идеи blazebot), друзья, тишина сервера, скриншот, Discord, диагностика ──
    /** Staff рядом: имя начинается с [OWNER]/[ADMIN]/[GM]/[GAME MASTER]/[MOD]/[MODERATOR]/[HELPER]/[MOJANG] ([YOUTUBE] — нет). */
    public boolean detectStaffNear     = true;
    public double  staffRadius         = 30;
    /** …или такой игрок появился в Tab (кто был в Tab на старте — не считается). */
    public boolean staffTab            = true;
    /** Двойник: игрок с моим ником ближе N бл. */
    public boolean detectTwin          = true;
    public double  twinRadius          = 16;
    /** Массовый телепорт: ≥ N игроков появились ближе R бл за окно (не после своего телепорта/варпа и не в первые K с в мире). */
    public boolean detectMassTp        = true;
    public int     massTpMin           = 5;
    public double  massTpRadius        = 8;
    public int     massTpWindowMs      = 1000;
    public int     massTpWorldGraceSec = 10;
    /** Игрок без имени ближе R бл дольше T мс (невидимость от зелья с видимым ником — не считается). */
    public boolean detectNameless      = true;
    public double  namelessRadius      = 8;
    public int     namelessDwellMs     = 1000;
    /** Личное сообщение «From [RANK] Ник: …». */
    public boolean detectPm            = true;
    /** «You are AFK» в чате. */
    public boolean detectAfkMsg        = true;
    /** Бедрок очередью: ≥ N блоков бедрока ближе R бл за T мс. */
    public boolean detectBedrockBurst  = true;
    public int     bedrockBurstMin     = 2;
    public double  bedrockBurstRadius  = 8;
    public int     bedrockBurstMs      = 300;
    /** Клетка по числу блоков: ≥ N любых твёрдых блоков у ног или головы (≤ R бл) за T мс. */
    public boolean detectCageCount     = true;
    public int     cageCountMin        = 4;
    public double  cageCountRadius     = 1.5;
    public int     cageCountMs         = 1000;
    /** Тишина сервера: ни одного пакета N с — закрыть соединение и считать вылетом (дальше — переподключение). */
    public boolean detectSilence       = true;
    public int     silenceSec          = 30;
    /** Белый список ников (друзья), через запятую. */
    public String  friendNames         = "";
    public boolean friendsIgnoreNear   = true;
    public boolean friendsIgnoreJoin   = true;
    public boolean friendsIgnoreSpectator = true;
    public boolean friendsIgnoreChat   = true;
    public boolean friendsIgnorePm     = true;
    public boolean friendsIgnoreMass   = true;
    /** «Игрок рядом»: паника, только если чужой стоит в радиусе N с (0 — сразу). */
    public double  playerNearDelaySec  = 0;
    /** Скриншот при панике: config/farmmacro/screenshots/panic-ДАТА_ВРЕМЯ_причина.png. */
    public boolean panicScreenshot     = true;
    /** Discord-webhook при панике (адрес — в config/farmmacro/discord-webhook.txt, не в профилях). */
    public boolean webhookEnabled      = false;
    public boolean webhookScreenshot   = true;
    /** Диагностика (только журнал): «стоит без дела» — ни одной нажатой клавиши N с. */
    public boolean diagIdle            = true;
    public int     diagIdleSec         = 20;
    /** Диагностика (только журнал): табло не читается 60 с (от него зависят «Перезагрузка» и Jacob). */
    public boolean diagScoreboard      = true;
    /** Переподключение: каждая следующая попытка ждёт вдвое дольше (до 10 мин). */
    public boolean reconnectBackoff    = false;
    /** HUD: все плашки в одной общей панели. */
    public boolean hudMerged           = false;
    /** v1.15.1: ширина плашек HUD (узких; остальные на 20 шире), масштаб всего HUD, перенос длинного текста. */
    public int     hudWidth            = 190;
    /** v1.15.1: главное меню (Правый Shift) сдвинуто от центра на столько (тащить за заголовок, двойной клик — в центр). */
    public int     guiOffsetX          = 0, guiOffsetY = 0;
    public double  hudScale            = 1.0;
    public boolean hudWrap             = true;

    // ── v1.14.2-pre: реестр детекторов (panic/Detectors) и мастер первого запуска ──
    /** Своё действие детектора: id → "susp"/"log"/"panic" (нет записи — по умолчанию). Выкл — прежнее поле detectX. */
    public java.util.Map<String, String> detectorActions = new java.util.LinkedHashMap<>();
    /** Свой звук паники детектора: id → звук ("builtin:…", "file:…", "mc:…"); нет записи — общий. */
    public java.util.Map<String, String> detectorSounds = new java.util.LinkedHashMap<>();
    /** Вес очков для действия «Подозрительность»: id → 0–100 (нет записи — 3). */
    public java.util.Map<String, Double> detectorWeights = new java.util.LinkedHashMap<>();
    /** Мастер первого запуска пройден или пропущен (старые конфиги без поля — считаются пройденными). */
    public boolean welcomeDone         = false;
    /** Ответы мастера: где играешь (skyblock/friends/other) и набор (optimal/careful/soft). */
    public String  welcomeWhere        = "skyblock";
    public String  welcomePreset       = "optimal";
    /** HUD: места, масштаб и скрытие плашек (редактор HUD). Ключ — id плашки, "all" — общая панель. */
    public java.util.Map<String, HudSlot> hudSlots = new java.util.LinkedHashMap<>();

    // ── Реакция на панику ────────────────────────────────────────────────────
    public boolean panicSoundEnabled          = true;
    /** builtin:siren|klaxon|alarm|alert, mc:<id> или file:<имя файла в config/farmmacro/sounds> */
    public String  panicSound                 = "builtin:siren";
    /**
     * «Безопасный звук»: только через движок игры, без javax.sound и декодирования.
     * По умолчанию включён, пока системный режим не проверен в игре (v1.2.1).
     */
    public boolean panicSoundSafe             = true;
    /** true — играть через звуковую систему ОС (не зависит от громкости Minecraft). Работает при panicSoundSafe=false. */
    public boolean panicSoundSystem           = true;
    public float   panicSoundVolume           = 1.0f;
    public float   panicSoundPitch            = 1.0f;
    public int     panicSoundRepeats          = 2;
    public int     panicSoundRepeatDelayTicks = 30;

    public boolean panicRedScreenEnabled      = true;
    public int     panicRedScreenTicks        = 60;

    // ── Запуск и автоматизация ───────────────────────────────────────────────
    public boolean loopEnabled           = false;
    /** Сколько кругов пройти в режиме цикла (0 = бесконечно). */
    public int     loopLimit             = 0;
    /** Остановить после N минут (0 = без лимита). */
    public int     timeLimitMinutes      = 0;
    /** Остановить, когда в инвентаре не осталось пустых слотов. */
    public boolean stopWhenInventoryFull = false;
    /** Обратный отсчёт перед стартом, секунд (0 = сразу). */
    public int     startCountdownSeconds = 0;
    /** Предупреждать, если игрок дальше N блоков от точки старта записи. */
    public double  startPointWarnDistance = 2.0;
    /** Не запускать, если игрок далеко от точки старта. */
    public boolean requireStartPoint     = false;
    /** Повторять повороты камеры из записи (иначе камера остаётся как есть). */
    public boolean replayCamera          = false;
    /** Общий выключатель «Случайности» (у каждого макроса/маршрута своя, в ⚙). */
    public boolean humanMaster           = true;
    /** Брать «Случайность» из общих настроек (вкладка «Конфиги»), а не из ⚙ макроса/маршрута. */
    public boolean humanUseGlobal        = false;
    /** Общая «Случайность» (вкладка «Конфиги»), сохраняется в профилях. */
    public com.farmmacro.macro.HumanSettings humanGlobal = new com.farmmacro.macro.HumanSettings();
    /** v1.12.1: настройки (⚙), которые сразу получает каждый новый маршрут и каждая новая запись. */
    public com.farmmacro.macro.MacroSettings newItemSettings = com.farmmacro.macro.MacroSettings.optimalNew();
    /** Плавный поворот пресета (K): скорость ± N % каждый раз (0 — всегда одинаково). */
    public int     smoothTurnJitterPct   = 0;
    /** Включать блокировку мыши при старте макроса (после отсчёта). */
    public boolean mouseLockOnStart      = false;
    /** Снимать блокировку мыши и при обычном стопе (паника, End, выход из мира снимают всегда). */
    public boolean mouseUnlockOnStop     = false;
    /** v1.12 Freelook: true — пока зажата клавиша, false — вкл/выкл нажатием. */
    public boolean freelookHold          = false;
    /** v1.12: паника выключает freelook (вид снова от первого лица). */
    public boolean freelookOffOnPanic    = true;
    /** Low render: радиус от блока игрока, блоков (v1.12.2 — поблочно, 1–32). */
    public int     lowRenderRadius       = 3;
    /** Служебное: 1 — радиус уже переведён на поблочный режим 1.12.2 (старые 12 бл → 3). */
    public int     lowRenderRev          = 1;
    /** v1.12 «Продолжить с места» (N): дальше этого от линии маршрута — не стартует. */
    public double  resumeAnywhereMaxDist = 3.0;

    // ── v1.15 «Возврат на маршрут» (свой A* по блокам) ──
    /** Общий выключатель: дойти до маршрута по найденному пути (N издалека, точка старта, переподключение, сбили с пути). */
    public boolean returnEnabled         = true;
    /** Предел: маршрут дальше по горизонтали — не идём (обычный стоп с сообщением). */
    public double  returnMaxDist         = 64;
    /** Старт/«Продолжить» (O) дальше допуска точки старта — дойти самому (иначе — как раньше: предупреждение/отказ). */
    public boolean returnToStart         = true;
    /** Маршрут по точкам: сбили с линии (толчок, откат) дальше returnOffRoute — вернуться по пути (если это не паника). */
    public boolean returnAfterPush       = true;
    /** Сбили с пути: дальше этого от линии отрезка (или на другом этаже), блоков. */
    public double  returnOffRoute        = 2.0;
    /** После переподключения (и /warp) не у точки остановки — дойти до маршрута. */
    public boolean returnAfterReconnect  = true;
    /** Скорость поворота камеры при возврате (всегда «как мышь»), °/с. */
    public double  returnTurnSpeed       = 180;
    /** Сколько раз перестроить путь за один возврат (сбили, застрял, упал). */
    public int     returnReplans         = 5;
    /** v1.15.1 «Есть полёт» (предмет/способность): путь возврата может лететь (двойной прыжок — взлёт, Shift — вниз). */
    public boolean returnFly             = false;
    /** Полёт: не выше этого над стартом/целью, блоков. */
    public int     returnFlyCeil         = 12;

    // ── v1.15 «Задержки рук (3 слоя)»: база (гаусс) × усталость за сессию + редкие отвлечения ──
    public boolean delayLayers           = true;
    public int     delayPressMean = 95,   delayPressSd = 35,   delayPressMin = 35,   delayPressMax = 260;
    public int     delayReleaseMean = 75, delayReleaseSd = 30, delayReleaseMin = 25, delayReleaseMax = 220;
    public int     delaySlotMean = 170,   delaySlotSd = 60,    delaySlotMin = 70,    delaySlotMax = 450;
    /** Усталость: к концу разгона задержки длиннее на N %, разгон — минут. */
    public double  fatiguePct = 25, fatigueRampMin = 90;
    /** Дрейф: ± N % плавно, время изменения — минут. */
    public double  driftPct = 8, driftMin = 12;
    /** Отвлечение: шанс на событие, %, и добавка от–до, мс. */
    public double  distractChance = 1.5;
    public int     distractMinMs = 400, distractMaxMs = 2500;
    /** Оверлей HUD: последние задержки и разброс. */
    public boolean delayDebugHud         = false;
    /** v1.12 (не используется с 1.12.2): дальность 2 чанка на время Low render. Оставлено для чтения старых файлов. */
    public boolean lowRenderChunks       = false;
    /** Служебное: дальность до Low render (0 — не меняли); вернуть при запуске игры, если закрылась в режиме. */
    public int     lowRenderSavedChunks  = 0;
    /** Мягкий звук, когда макрос закончился сам (лимит/конец записи). */
    public boolean finishSoundEnabled    = true;

    // ── HUD ──────────────────────────────────────────────────────────────────
    public boolean statsHudEnabled       = true;
    public boolean navHudEnabled         = true;
    /** Маленькая стрелка к цели вокруг прицела (когда макрос не играет). */
    public boolean navCrosshairEnabled   = true;

    // ── Визуал: China Hat ────────────────────────────────────────────────────
    public boolean hatEnabled            = true;
    /** gradient | solid | rainbow */
    public String  hatStyle              = "gradient";
    /** id пресета из visual.HatColors */
    public String  hatColor1             = "purple";
    public String  hatColor2             = "orange";
    /** 0–100 % */
    public int     hatOpacity            = 45;
    /** Радиус полей, блоков. */
    public double  hatRadius             = 0.7;
    /** Высота конуса от полей до вершины, блоков. */
    public double  hatHeight             = 0.3;
    /** Подъём полей над макушкой, блоков. */
    public double  hatOffset             = 0.08;
    /** Скорость вращения/перелива (0 — стоит). */
    public double  hatSpeed              = 1.0;
    public boolean hatFirstPerson        = false;
    public int     hatSegments           = 48;
    public boolean hatAllPlayers         = false;

    // ── Визуал: маршрут макроса ──────────────────────────────────────────────
    public boolean routeEnabled          = true;
    /** always | playing */
    public String  routeMode             = "always";
    /** off | dim | full — видно ли сквозь блоки */
    public String  routeSeeThrough       = "dim";
    /** Толщина ленты, блоков. */
    public double  routeWidth            = 0.08;
    /** Радиус отрисовки вокруг игрока, блоков. */
    public int     routeRadius           = 48;
    public boolean routeArrows           = true;
    /** Шаг стрелок, блоков. */
    public double  routeArrowSpacing     = 4.0;
    /** Участки ЛКМ/ПКМ другим цветом, отметки прыжка и приседания. */
    public boolean routeShowActions      = true;
    /** Сдвигать маршрут на смещение точки запуска (как детектор «сход с маршрута»). */
    public boolean routeRelative         = true;
    /** Цвет ленты впереди (id пресета из visual.HatColors). */
    public String  routeColor            = "cyan";
    /** Цвета элементов маршрута и HUD: id пресета HatColors или "default" (как раньше). */
    public String  routePointColor       = "default";
    public String  routeLabelColor       = "default";
    public String  routeStartColor       = "default";
    public String  routeStopColor        = "default";
    public String  routeArrowColor       = "default";
    public String  hudAccentColor        = "default";
    public String  hudBgColor            = "default";
    /** Непрозрачность ленты, 10–100 %. */
    public int     routeOpacity          = 90;
    /** Мягкое свечение вокруг ленты (вторая, широкая и прозрачная). */
    public boolean routeGlow             = true;

    // ── Маршрут по точкам: редактор ──────────────────────────────────────────
    /** Как далеко редактор ставит/выбирает точки, блоков. */
    public double  routeEditReach        = 32;
    /** Сдвиг точки в блоке стрелками: шаг и мелкий шаг (с Shift), блоков. */
    public double  routeOffsetStep       = 0.05;
    public double  routeOffsetFineStep   = 0.01;
    /** Автоход: точка достигнута в этом радиусе по XZ, блоков. */
    public double  routeReachRadius      = 0.3;

    // ── v1.11 меню: простой/расширенный режим и свёрнутые разделы (не входят в профили «Конфиги») ──
    /** Показывать тонкие настройки (пороги, веса, цвета, служебные шаги). */
    public boolean uiAdvanced            = false;
    /** Раздел меню → развёрнут (true) / свёрнут (false); нет ключа — как задано по умолчанию. */
    public java.util.Map<String, Boolean> uiSections = new java.util.LinkedHashMap<>();

    // ── Камера: пресеты yaw/pitch ────────────────────────────────────────────
    public static class CamPreset {
        /** Постоянный номер (на него ссылаются привязки камеры в макросах и маршрутах), 0 — ещё не выдан. */
        public int    id    = 0;
        /** Имя для списка; пустое — «Пресет N». */
        public String name  = "";
        /** id культуры из camera.CropPresets.CROPS (подпись и «Запуск → Культура»). */
        public String crop  = "other";
        public float  yaw   = 0f;
        public float  pitch = 0f;
        public CamPreset() {}
        public CamPreset(String crop, float yaw, float pitch) { this.crop = crop; this.yaw = yaw; this.pitch = pitch; }
    }
    public static final int CAM_NAME_MAX = 48;
    public java.util.List<CamPreset> camPresets = new java.util.ArrayList<>();
    public int     camSelected           = 0;
    /** Следующий свободный id пресета. */
    public int     camNextId             = 1;
    /** v1.11: какие стандартные пресеты культур уже дописаны (camera.CropPresets.DEFAULTS_REV). Не входит в профили. */
    public int     camDefaultsRev        = 0;
    /** v1.11 «Запуск → Культура»: id культуры, чей пресет ставится при старте макроса/маршрута; "none" — не ставить. */
    public String  launchCrop            = "none";

    /** Новый пресет с постоянным id (в список не добавляет). */
    public CamPreset newCamPreset(String crop, float yaw, float pitch) {
        CamPreset p = new CamPreset(crop, yaw, pitch);
        p.id = camNextId++;
        p.name = "Пресет " + p.id;
        return p;
    }
    /** Плавный поворот к пресету (выкл — мгновенно). */
    public boolean camSmooth             = true;
    /** Скорость плавного поворота, градусов в секунду. */
    public double  camTurnSpeed          = 120;
    /**
     * v1.11.1: плавный поворот «как мышь» — по пикселям мыши через Entity.turn с учётом чувствительности, разгон и
     * торможение, небольшой перелёт и доводка; итоговые углы не ровные (≤ 0.2° от пресета). false — равномерный плавный.
     */
    public boolean camMouseLike          = false;
    /** «Как мышь»: перелёт до N % от поворота (0…3). */
    public double  camOvershootPct       = 2;

    // ── Клавиши по умолчанию (дальше их хранит меню «Управление» Minecraft) ──
    public int keyRecord  = 82;   // R
    public int keyPlay    = 80;   // P
    public int keyClear   = 261;  // Delete
    public int keyOpenGui = 344;  // Right Shift

    /** Приводит значения к допустимым диапазонам (на случай ручной правки json). */
    public void sanitize() {
        if (lowRenderRev < 1) { lowRenderRadius = 3; lowRenderChunks = false; lowRenderRev = 1; }   // 1.12 → 1.12.2
        lowRenderRadius       = Math.max(1, Math.min(32, lowRenderRadius));
        resumeAnywhereMaxDist = clamp(resumeAnywhereMaxDist, 0.5, 16);
        returnMaxDist         = clamp(returnMaxDist, 8, 256);
        returnOffRoute        = clamp(returnOffRoute, 0.5, 16);
        returnTurnSpeed       = clamp(returnTurnSpeed, 20, 720);
        returnReplans         = clamp(returnReplans, 0, 20);
        returnFlyCeil         = clamp(returnFlyCeil, 2, 64);
        delayPressMin   = clamp(delayPressMin, 0, 5000);   delayPressMax   = clamp(delayPressMax, delayPressMin, 5000);
        delayPressMean  = clamp(delayPressMean, delayPressMin, delayPressMax);       delayPressSd   = clamp(delayPressSd, 0, 2000);
        delayReleaseMin = clamp(delayReleaseMin, 0, 5000); delayReleaseMax = clamp(delayReleaseMax, delayReleaseMin, 5000);
        delayReleaseMean = clamp(delayReleaseMean, delayReleaseMin, delayReleaseMax); delayReleaseSd = clamp(delayReleaseSd, 0, 2000);
        delaySlotMin    = clamp(delaySlotMin, 0, 5000);    delaySlotMax    = clamp(delaySlotMax, delaySlotMin, 5000);
        delaySlotMean   = clamp(delaySlotMean, delaySlotMin, delaySlotMax);          delaySlotSd    = clamp(delaySlotSd, 0, 2000);
        fatiguePct      = clamp(fatiguePct, 0, 200);       fatigueRampMin  = clamp(fatigueRampMin, 5, 600);
        driftPct        = clamp(driftPct, 0, 50);          driftMin        = clamp(driftMin, 1, 120);
        distractChance  = clamp(distractChance, 0, 20);
        distractMinMs   = clamp(distractMinMs, 50, 20000); distractMaxMs   = clamp(distractMaxMs, distractMinMs, 20000);
        lowRenderSavedChunks  = Math.max(0, Math.min(64, lowRenderSavedChunks));
        yawThreshold          = clamp(yawThreshold, 0.001f, 180f);
        pitchThreshold        = clamp(pitchThreshold, 0.001f, 180f);
        serverMoveThreshold   = clamp(serverMoveThreshold, 0.001, 1024);
        serverRotateThreshold = clamp(serverRotateThreshold, 0.001f, 180f);
        blockDetectRadius     = clamp(blockDetectRadius, 0, 8);
        stuckThresholdTicks   = clamp(stuckThresholdTicks, 1, 12000);
        driftThreshold        = clamp(driftThreshold, 0.01, 1024);
        knockbackThreshold    = clamp(knockbackThreshold, 0.001, 10);
        playerNearRadius      = clamp(playerNearRadius, 1, 128);
        serverPosEpsilon      = clamp(serverPosEpsilon, 0.0001, 16);
        serverRotEpsilon      = clamp(serverRotEpsilon, 0.0001f, 180f);
        attrEpsilon           = clamp(attrEpsilon, 0.01, 100);
        obstacleRange         = clamp(obstacleRange, 1, 16);
        velocityAnomaly       = clamp(velocityAnomaly, 0.01, 5);
        suspicionLimit        = clamp(suspicionLimit, 0.1, 1000);
        suspicionHalfLifeSec  = clamp(suspicionHalfLifeSec, 1, 3600);
        suspWeightSync        = clamp(suspWeightSync, 0, 100);
        suspWeightRollback    = clamp(suspWeightRollback, 0, 100);
        suspWeightKnock       = clamp(suspWeightKnock, 0, 100);
        suspWeightPlayerFar   = clamp(suspWeightPlayerFar, 0, 100);
        suspWeightChatWord    = clamp(suspWeightChatWord, 0, 100);
        suspWeightActionBar   = clamp(suspWeightActionBar, 0, 100);
        stickyRadius          = clamp(stickyRadius, 1, 32);
        farmBlockRadius       = clamp(farmBlockRadius, 2, 64);
        suspWeightFarmBlock   = clamp(suspWeightFarmBlock, 0, 100);
        bpsMin                = clamp(bpsMin, 0.1, 40);
        bpsWindowSec          = clamp(bpsWindowSec, 2, 30);
        desyncPct             = clamp(desyncPct, 10, 100);
        banwaveThreshold      = clamp(banwaveThreshold, 1, 100_000);
        banwaveWindowMin      = clamp(banwaveWindowMin, 2, 60);
        jacobLimit            = clamp(jacobLimit, 0, 2_000_000_000);
        suspWeightLagback     = clamp(suspWeightLagback, 0, 100);
        schedFarmMin          = clamp(schedFarmMin, 1, 1440);
        schedBreakMin         = clamp(schedBreakMin, 1, 1440);
        schedJitterPct        = clamp(schedJitterPct, 0, 90);
        leaveAfterMin         = clamp(leaveAfterMin, 0, 10080);
        reconnectDelaySec     = clamp(reconnectDelaySec, 5, 600);
        reconnectTries        = clamp(reconnectTries, 1, 20);
        if (reconnectCommand == null) reconnectCommand = "";
        if (reconnectCommand.length() > 100) reconnectCommand = reconnectCommand.substring(0, 100);
        antiStuckTries        = clamp(antiStuckTries, 0, 5);
        perfFps               = clamp(perfFps, 10, 260);
        if (chatKeywords == null) chatKeywords = "";
        if (chatKeywordsRev < 1) {                      // v1.14: дописать staff-строки один раз (пустой список — не трогаем)
            if (!chatKeywords.isBlank()) {
                java.util.Set<String> have = new java.util.HashSet<>();
                for (String w : chatKeywords.split(",")) have.add(w.strip());
                StringBuilder b = new StringBuilder(chatKeywords.strip());
                for (String w : V14_KEYWORDS.split(",")) if (!have.contains(w.strip())) b.append(", ").append(w.strip());
                chatKeywords = b.toString();
            }
            chatKeywordsRev = 1;
        }
        staffRadius           = clamp(staffRadius, 1, 128);
        twinRadius            = clamp(twinRadius, 1, 128);
        massTpMin             = clamp(massTpMin, 2, 50);
        massTpRadius          = clamp(massTpRadius, 1, 64);
        massTpWindowMs        = clamp(massTpWindowMs, 100, 10_000);
        massTpWorldGraceSec   = clamp(massTpWorldGraceSec, 0, 120);
        namelessRadius        = clamp(namelessRadius, 1, 64);
        namelessDwellMs       = clamp(namelessDwellMs, 50, 30_000);
        bedrockBurstMin       = clamp(bedrockBurstMin, 1, 64);
        bedrockBurstRadius    = clamp(bedrockBurstRadius, 1, 64);
        bedrockBurstMs        = clamp(bedrockBurstMs, 50, 10_000);
        cageCountMin          = clamp(cageCountMin, 2, 26);
        cageCountRadius       = clamp(cageCountRadius, 0.5, 4);
        cageCountMs           = clamp(cageCountMs, 100, 10_000);
        silenceSec            = clamp(silenceSec, 10, 300);
        if (friendNames == null) friendNames = "";
        if (friendNames.length() > 1000) friendNames = friendNames.substring(0, 1000);
        playerNearDelaySec    = clamp(playerNearDelaySec, 0, 120);
        diagIdleSec           = clamp(diagIdleSec, 3, 3600);
        com.farmmacro.panic.Detectors.sanitize(this);
        if (!"skyblock".equals(welcomeWhere) && !"friends".equals(welcomeWhere) && !"other".equals(welcomeWhere)) welcomeWhere = "skyblock";
        if (!"optimal".equals(welcomePreset) && !"careful".equals(welcomePreset) && !"soft".equals(welcomePreset)) welcomePreset = "optimal";
        hudWidth = clamp(hudWidth, 120, 480);
        guiOffsetX = clamp(guiOffsetX, -4000, 4000); guiOffsetY = clamp(guiOffsetY, -4000, 4000);
        hudScale = clamp(hudScale, 0.5, 3.0);
        if (hudSlots == null) hudSlots = new java.util.LinkedHashMap<>();
        hudSlots.values().removeIf(java.util.Objects::isNull);
        if (hudSlots.size() > 64) hudSlots.clear();
        for (HudSlot sl : hudSlots.values()) sl.sanitize();
        if (chatKeywords.length() > 1000) chatKeywords = chatKeywords.substring(0, 1000);
        humanGlobal           = com.farmmacro.macro.HumanSettings.sanitize(humanGlobal);
        newItemSettings       = com.farmmacro.macro.MacroSettings.fromFile(
                newItemSettings == null ? com.farmmacro.macro.MacroSettings.optimalNew() : newItemSettings, null);
        panicSoundVolume      = clamp(panicSoundVolume, 0f, 1f);
        panicSoundPitch       = clamp(panicSoundPitch, 0.5f, 2f);
        panicSoundRepeats     = clamp(panicSoundRepeats, 1, 100);
        panicSoundRepeatDelayTicks = clamp(panicSoundRepeatDelayTicks, 1, 1200);
        panicRedScreenTicks   = clamp(panicRedScreenTicks, 1, 1200);
        loopLimit             = clamp(loopLimit, 0, 1000000);
        timeLimitMinutes      = clamp(timeLimitMinutes, 0, 7 * 24 * 60);
        startCountdownSeconds = clamp(startCountdownSeconds, 0, 600);
        smoothTurnJitterPct   = clamp(smoothTurnJitterPct, 0, 90);
        startPointWarnDistance = clamp(startPointWarnDistance, 0.01, 1024);
        if (panicSound == null || panicSound.isBlank()) panicSound = "builtin:siren";
        routeWidth            = clamp(routeWidth, 0.005, 1);
        routeRadius           = clamp(routeRadius, 1, 1024);
        routeArrowSpacing     = clamp(routeArrowSpacing, 0.5, 64);
        if (!"always".equals(routeMode) && !"playing".equals(routeMode)) routeMode = "always";
        if (!"off".equals(routeSeeThrough) && !"dim".equals(routeSeeThrough) && !"full".equals(routeSeeThrough)) routeSeeThrough = "dim";
        if (!com.farmmacro.visual.HatColors.isPreset(routeColor)) routeColor = "cyan";
        routePointColor   = colorOrDefault(routePointColor);
        routeLabelColor   = colorOrDefault(routeLabelColor);
        routeStartColor   = colorOrDefault(routeStartColor);
        routeStopColor    = colorOrDefault(routeStopColor);
        routeArrowColor   = colorOrDefault(routeArrowColor);
        hudAccentColor    = colorOrDefault(hudAccentColor);
        hudBgColor        = colorOrDefault(hudBgColor);
        routeOpacity          = clamp(routeOpacity, 1, 100);
        if (camPresets == null) camPresets = new java.util.ArrayList<>();
        camPresets.removeIf(java.util.Objects::isNull);
        while (camPresets.size() > 16) camPresets.remove(camPresets.size() - 1);
        int maxId = 0;
        for (CamPreset p : camPresets) maxId = Math.max(maxId, p.id);
        camNextId = Math.max(Math.max(1, camNextId), maxId + 1);
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (CamPreset p : camPresets) {
            if (p.id <= 0 || !ids.add(p.id)) { p.id = camNextId++; ids.add(p.id); }   // старые пресеты (до 1.6) — без id
            p.yaw = com.farmmacro.camera.CropPresets.yaw(p.yaw);        // 0.1°, −180…180
            p.pitch = com.farmmacro.camera.CropPresets.pitch(p.pitch);  // 0.1°, −90…90
            if (!com.farmmacro.camera.CropPresets.isCrop(p.crop)) p.crop = com.farmmacro.camera.CropPresets.OTHER;   // свёкла, ягоды (до 1.11) → «Другое»
            p.name = p.name == null ? "" : p.name.strip();
            if (p.name.length() > CAM_NAME_MAX) p.name = p.name.substring(0, CAM_NAME_MAX);
            if (p.name.isEmpty()) p.name = "Пресет " + p.id;
        }
        camSelected           = camPresets.isEmpty() ? 0 : clamp(camSelected, 0, camPresets.size() - 1);
        camTurnSpeed          = clamp(camTurnSpeed, 1, 3600);
        camOvershootPct       = clamp(camOvershootPct, 0, 3);
        if (!com.farmmacro.camera.CropPresets.NONE.equals(launchCrop) && !com.farmmacro.camera.CropPresets.isCrop(launchCrop)
                || com.farmmacro.camera.CropPresets.OTHER.equals(launchCrop)) launchCrop = com.farmmacro.camera.CropPresets.NONE;
        camDefaultsRev        = Math.max(0, camDefaultsRev);
        routeEditReach        = clamp(routeEditReach, 1, 128);
        routeOffsetStep       = clamp(routeOffsetStep, 0.001, 0.5);
        routeOffsetFineStep   = clamp(routeOffsetFineStep, 0.001, 0.5);
        routeReachRadius      = clamp(routeReachRadius, 0.01, 2);
        if (uiSections == null) uiSections = new java.util.LinkedHashMap<>();
        if (uiSections.size() > 200) uiSections.clear();
        hatOpacity            = clamp(hatOpacity, 0, 100);
        hatRadius             = clamp(hatRadius, 0.05, 3);
        hatHeight             = clamp(hatHeight, 0.01, 2);
        hatOffset             = clamp(hatOffset, -1, 2);
        hatSpeed              = clamp(hatSpeed, 0, 20);
        hatSegments           = clamp(hatSegments, 3, 256);
        if (!"gradient".equals(hatStyle) && !"solid".equals(hatStyle) && !"rainbow".equals(hatStyle)) hatStyle = "gradient";
        if (!com.farmmacro.visual.HatColors.isPreset(hatColor1)) hatColor1 = "purple";
        if (!com.farmmacro.visual.HatColors.isPreset(hatColor2)) hatColor2 = "orange";
    }

    /** Реальные пороги детекторов одной строкой (пишется в лог при загрузке и при старте макроса). */
    public String describeThresholds() {
        return String.format(java.util.Locale.ROOT,
                "паника=%s | поворот=%s yaw>%.2f° pitch>%.2f° | сервер=%s сдвиг>=%.2f бл поворот>=%.2f° | "
                        + "блок=%s %.2f бл | слот=%s окно=%s урон=%s эффекты=%s | застрял=%s %d т | сход=%s %.1f бл | "
                        + "повтор камеры=%s | звук=%s безопасный=%s системный=%s | любой телепорт=%s толчок=%s %.3f | "
                        + "чат=%s ник=%s титр=%s режим=%s игрок рядом=%s %.0f бл наблюдатель=%s вход/выход=%s предмет=%s | "
                        + "синхр=%s %.4f бл %.4f° | параметры=%s %.2f%% | препятствие=%s %.1f бл | скорость=%s %.3f бл/т | "
                        + "подозр=%s %.1f/%.0f с веса %.1f/%.1f/%.1f/%.1f/%.1f/%.1f слова мягко=%s | журнал=%s | "
                        + "культура=%s пресетов=%d | поворот камеры=%s %.0f°/с перелёт до %.1f%% | "
                        + "1.13: лаги=%s огонь=%s паутина=%s %.0f бл клетка=%s блок на ферме=%s %.0f бл вес %.1f | "
                        + "BPS=%s <%.1f за %.0f с рассинхрон=%s %.0f%% | инвентарь-паника=%s отключение=%s перезагрузка=%s | "
                        + "банвейв=%s %d/%d мин staff=%s | jacob=%s %d гость=%s откат назад=%s %.1f | "
                        + "1.13.1: расписание=%s %d/%d мин ±%d%% выход=%d мин переподкл=%s %d с ×%d рост=%s продолжить=%s анти-застр=%d | "
                        + "1.14: staff=%s %.0f бл tab=%s двойник=%s %.0f бл масс.тп=%s %d/%.0f бл/%d мс без имени=%s %.0f бл %d мс "
                        + "ЛС=%s AFK=%s бедрок=%s %d/%.0f бл/%d мс клетка-число=%s %d/%.1f бл/%d мс тишина=%s %d с | "
                        + "друзей=%d (рядом=%s вход=%s набл=%s чат=%s ЛС=%s масс=%s) задержка рядом=%.1f с | "
                        + "скриншот=%s discord=%s диагн=%s %d с/%s | 1.14.2: действия детекторов={%s} | "
                        + "1.15: возврат=%s %.0f бл старт=%s толчок=%s %.1f бл переподкл=%s %.0f°/с перепланов %d | "
                        + "задержки=%s нажать %d±%d (%d–%d) отпустить %d±%d (%d–%d) слот %d±%d (%d–%d) мс усталость +%.0f%%/%.0f мин "
                        + "дрейф ±%.0f%%/%.0f мин отвлечение %.1f%% %d–%d мс",
                panicEnabled, detectRotation, yawThreshold, pitchThreshold,
                detectServerMove, serverMoveThreshold, serverRotateThreshold,
                detectBlockInFace, blockDetectRadius, detectSlotChange, detectGuiOpen, detectDamage, detectPotionEffect,
                detectStuck, stuckThresholdTicks, detectDrift, driftThreshold,
                replayCamera, panicSound, panicSoundSafe, panicSoundSystem, serverMoveAny, detectKnockback, knockbackThreshold,
                detectChat, chatMentionName, detectTitle, detectGameMode, detectPlayerNear, playerNearRadius,
                detectSpectator, detectPlayerJoin, detectHeldItem,
                detectServerSync, serverPosEpsilon, serverRotEpsilon, detectMoveAttrs, attrEpsilon,
                detectObstacle, obstacleRange, detectVelocity, velocityAnomaly,
                suspicionEnabled, suspicionLimit, suspicionHalfLifeSec, suspWeightSync, suspWeightRollback, suspWeightKnock,
                suspWeightPlayerFar, suspWeightChatWord, suspWeightActionBar, chatWordsSoft, eventLogEnabled,
                launchCrop, camPresets == null ? 0 : camPresets.size(),
                !camSmooth ? "мгновенно" : camMouseLike ? "как мышь" : "плавно", camTurnSpeed, camOvershootPct,
                lagGuard, detectFire, detectSticky, stickyRadius, detectCage, detectFarmBlock, farmBlockRadius, suspWeightFarmBlock,
                detectBps, bpsMin, bpsWindowSec, detectDesync, desyncPct, panicInventoryFull, detectDisconnect, detectRestart,
                detectBanwave, banwaveThreshold, banwaveWindowMin, banwaveWithStaff, detectJacob, jacobLimit, detectVisitor,
                lagbackSoft, suspWeightLagback,
                schedEnabled, schedFarmMin, schedBreakMin, schedJitterPct, leaveAfterMin,
                autoReconnect, reconnectDelaySec, reconnectTries, reconnectBackoff, reconnectResume, antiStuckTries,
                detectStaffNear, staffRadius, staffTab, detectTwin, twinRadius, detectMassTp, massTpMin, massTpRadius, massTpWindowMs,
                detectNameless, namelessRadius, namelessDwellMs, detectPm, detectAfkMsg,
                detectBedrockBurst, bedrockBurstMin, bedrockBurstRadius, bedrockBurstMs,
                detectCageCount, cageCountMin, cageCountRadius, cageCountMs, detectSilence, silenceSec,
                com.farmmacro.panic.PlayerRules.parseNames(friendNames).size(), friendsIgnoreNear, friendsIgnoreJoin,
                friendsIgnoreSpectator, friendsIgnoreChat, friendsIgnorePm, friendsIgnoreMass, playerNearDelaySec,
                panicScreenshot, webhookEnabled, diagIdle, diagIdleSec, diagScoreboard, com.farmmacro.panic.Detectors.describe(this),
                returnEnabled, returnMaxDist, returnToStart, returnAfterPush, returnOffRoute, returnAfterReconnect, returnTurnSpeed, returnReplans,
                delayLayers, delayPressMean, delayPressSd, delayPressMin, delayPressMax, delayReleaseMean, delayReleaseSd, delayReleaseMin,
                delayReleaseMax, delaySlotMean, delaySlotSd, delaySlotMin, delaySlotMax, fatiguePct, fatigueRampMin, driftPct, driftMin,
                distractChance, distractMinMs, distractMaxMs);
    }

    private static String colorOrDefault(String id) {
        return com.farmmacro.visual.HatColors.isPreset(id) ? id : com.farmmacro.visual.HatColors.DEFAULT;
    }

    private static int    clamp(int v, int lo, int hi)          { return Math.max(lo, Math.min(hi, v)); }
    private static float  clamp(float v, float lo, float hi)    { return Float.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v)); }
    private static double clamp(double v, double lo, double hi) { return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v)); }

    // ── Сохранение / загрузка ─────────────────────────────────────────────────
    public static void load() {
        final Path CONFIG_PATH = configPath();
        if (!Files.exists(CONFIG_PATH)) {
            INSTANCE = new ModConfig();
            INSTANCE.ensureCamDefaults();
            save();
            return;
        }
        try {
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            ModConfig loaded = GSON.fromJson(json, ModConfig.class);
            INSTANCE = loaded != null ? loaded : new ModConfig();
            // v1.14.2-pre: конфиг старой версии (без поля) — мастер первого запуска не показываем, он есть в меню
            if (loaded != null && !json.contains("\"welcomeDone\"")) INSTANCE.welcomeDone = true;
        } catch (Exception e) {
            LOGGER.error("Не удалось прочитать farmmacro.json, беру настройки по умолчанию", e);
            INSTANCE = new ModConfig();
            // v1.11: ниже конфиг может пересохраниться (стандартные пресеты) — сначала сохранить битый файл рядом
            try {
                Path bak = CONFIG_PATH.resolveSibling("farmmacro.json.broken");
                Files.copy(CONFIG_PATH, bak, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warn("Битый конфиг сохранён как {}", bak);
            } catch (Exception e2) {
                LOGGER.error("Не удалось сохранить копию битого конфига", e2);
            }
        }
        float yaw0 = INSTANCE.yawThreshold, pitch0 = INSTANCE.pitchThreshold;
        INSTANCE.sanitize();
        if (yaw0 != INSTANCE.yawThreshold || pitch0 != INSTANCE.pitchThreshold)
            LOGGER.warn("Пороги поворота в {} были вне диапазона ({} / {}), исправлены", CONFIG_PATH, yaw0, pitch0);
        if (INSTANCE.ensureCamDefaults()) {
            save();
            LOGGER.info("Пресеты камеры: дописаны стандартные пресеты культур, всего {}", INSTANCE.camPresets.size());
        }
        LOGGER.info("Конфиг {} загружен: {}", CONFIG_PATH, INSTANCE.describeThresholds());
    }

    /**
     * v1.11: пусто → 7 стандартных пресетов культур; свои есть, а набор ещё не дописывался — дописать без дублей по имени.
     * @return true — список или ревизия изменились (нужно сохранить)
     */
    public boolean ensureCamDefaults() {
        int before = camPresets.size(), rev0 = camDefaultsRev;
        camDefaultsRev = com.farmmacro.camera.CropPresets.ensureDefaults(camPresets, camDefaultsRev,
                com.farmmacro.camera.CameraPresets.MAX, () -> camNextId++);
        return camPresets.size() != before || camDefaultsRev != rev0;
    }

    /** Пишет во временный файл и атомарно подменяет — конфиг не побьётся при вылете игры. */
    public static void save() {
        final Path CONFIG_PATH = configPath();
        try {
            Path tmp = CONFIG_PATH.resolveSibling("farmmacro.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, w);
            }
            try {
                Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicNotSupported) {
                Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Не удалось сохранить farmmacro.json", e);
        }
    }

    /** Папка мода: config/farmmacro/ */
    public static Path modDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("farmmacro");
    }
}

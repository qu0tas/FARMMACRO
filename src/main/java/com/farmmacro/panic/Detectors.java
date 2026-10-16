package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * v1.14.2-pre: единый реестр детекторов паники. Каждое срабатывание любой проверки (PanicDetector, WorldGuards,
 * PlayerGuards, маршрут, тишина, лаги) идёт через {@link #record}: здесь решается действие (паника / очки
 * подозрительности / только журнал / выкл), считается счётчик за сессию и хранятся последние записи.
 * Без Minecraft (проверка — DetectorsCheck). Вкл/выкл — прежние поля ModConfig (detectX), поэтому старые
 * группы меню работают как раньше; своё действие, звук и вес — словари ModConfig.detectorActions/Sounds/Weights.
 */
public final class Detectors {
    private Detectors() {}

    /** Что делать при срабатывании. OFF — детектор выключен (прежнее поле detectX = false). */
    public enum Action {
        PANIC("panic"), SUSP("susp"), LOG("log"), OFF("off");
        public final String key;
        Action(String key) { this.key = key; }
        public static Action of(String key) {
            for (Action a : values()) if (a.key.equals(key)) return a;
            return null;
        }
    }

    /**
     * TRIGGER — обычный детектор (может быть паникой). GUARD — защита/поправка без своего срабатывания-паники
     * («Лаги сервера»: только вкл/выкл и счётчик эпизодов). OWN — своё действие вместо паники
     * (тишина — закрыть соединение, Jacob — стоп по лимиту): «Паника» здесь = это действие.
     */
    public enum Kind { TRIGGER, GUARD, OWN }

    public record Def(String id, String group, String name, String hint, Kind kind, Action def, List<Action> allowed,
                      String ownLabel, String example, BooleanSupplier on, Consumer<Boolean> set) {
        public boolean enabled() { return on.getAsBoolean(); }
        public void setEnabled(boolean v) { set.accept(v); }
        /** Можно ли показать «как выглядит паника» (у защит — нет). */
        public boolean testable() { return kind != Kind.GUARD; }
        public boolean hasSound() { return kind == Kind.TRIGGER && allowed.contains(Action.PANIC); }
    }

    public static final String G_HANDS = "Камера и руки", G_TP = "Телепорт и толчки", G_MOVE = "Движение и маршрут",
            G_WORLD = "Блоки и мир", G_CHAT = "Чат", G_PLAYERS = "Игроки", G_SERVER = "Сервер и Skyblock",
            G_SUSP = "Подозрительность";
    public static final List<String> GROUPS = List.of(G_HANDS, G_TP, G_MOVE, G_WORLD, G_CHAT, G_PLAYERS, G_SERVER, G_SUSP);

    private static final List<Action> ALL4 = List.of(Action.PANIC, Action.SUSP, Action.LOG, Action.OFF);
    private static final List<Def> LIST = new ArrayList<>();
    private static final Map<String, Def> BY_ID = new LinkedHashMap<>();

    private static ModConfig c() { return ModConfig.INSTANCE; }

    private static void t(String id, String group, String name, String hint, String example, BooleanSupplier on, Consumer<Boolean> set) {
        add(new Def(id, group, name, hint, Kind.TRIGGER, Action.PANIC, ALL4, null, example, on, set));
    }

    private static void add(Def d) {
        if (BY_ID.put(d.id(), d) != null) throw new IllegalStateException("повтор id детектора: " + d.id());
        LIST.add(d);
    }

    static {
        // ── Камера и руки ──
        t("rotation", G_HANDS, "Поворот камеры", "Камера повернулась не макросом (мышь)", "Камера повернулась (yaw 6.200° > 5.000°)",
                () -> c().detectRotation, v -> c().detectRotation = v);
        t("safetyWeapon", G_HANDS, "Аварийный стоп: оружие/предмет", "Исчезновение/подмена предмета или чужая смена слота, в том числе при aim без макроса", "Glacial Scythe исчезла из руки — полный стоп",
                () -> c().detectAutomationWeapon, v -> c().detectAutomationWeapon = v);
        t("safetyTeleport", G_TP, "Аварийный стоп: телепорт", "Любой пакет позиции/респавн при автоматике, даже маленький откат; без мягкого режима лагов", "Сервер сменил позицию — полный стоп",
                () -> c().detectAutomationTeleport, v -> c().detectAutomationTeleport = v);
        t("slot", G_HANDS, "Смена слота", "Слот сменился сам или от сервера", "Слот сменился: 1 → 3",
                () -> c().detectSlotChange, v -> c().detectSlotChange = v);
        t("heldItem", G_HANDS, "Предмет в руке", "Предмет сменился или пропал сам (/give, /clear)", "Предмет в руке сменился: Мотыга → Палка",
                () -> c().detectHeldItem, v -> c().detectHeldItem = v);
        t("gui", G_HANDS, "Открылось окно", "Сервер открыл GUI (сундук, меню)", "Открылось окно: Chest (ContainerScreen)",
                () -> c().detectGuiOpen, v -> c().detectGuiOpen = v);
        t("damage", G_HANDS, "Урон", "Здоровье упало или событие урона", "Получен урон (−1.0 ❤)",
                () -> c().detectDamage, v -> c().detectDamage = v);
        // ── Телепорт и толчки ──
        t("serverMove", G_TP, "Телепорт / поворот от сервера", "Пакет телепорта или поворота (в т. ч. без сдвига)", "Сервер телепортировал (3.2 бл)",
                () -> c().detectServerMove, v -> c().detectServerMove = v);
        t("serverSync", G_TP, "Синхронизация", "Сервер поправил позицию/взгляд больше эпсилона", "Сервер поправил позицию: 0.120 бл > 0.030",
                () -> c().detectServerSync, v -> c().detectServerSync = v);
        t("knockback", G_TP, "Толчок", "Скорость от сервера: отдача, удочка, взрыв", "Сервер толкнул (скорость 0.40 бл/т)",
                () -> c().detectKnockback, v -> c().detectKnockback = v);
        add(new Def("lagback", G_TP, "Откат назад", "Вернули на место, где был за 3 с (< 4 бл)", Kind.TRIGGER, Action.SUSP,
                List.of(Action.SUSP, Action.LOG, Action.OFF), null, "откат назад: 1.40 бл",
                () -> c().lagbackSoft, v -> c().lagbackSoft = v));
        // ── Движение и маршрут ──
        t("stuck", G_MOVE, "Застрял", "Не продвигается по записи/маршруту", "Застрял: по записи 2.0 бл за 1.5 с, прошёл 0.05",
                () -> c().detectStuck, v -> c().detectStuck = v);
        t("drift", G_MOVE, "Сход с маршрута", "Отошёл от линии дальше порога", "Сошёл с маршрута на 2.4 бл",
                () -> c().detectDrift, v -> c().detectDrift = v);
        t("floor", G_MOVE, "Этажи", "Точка на другом этаже, не упал на спуске", "Нет пути: точка 5 на другом этаже (Δy 4.0 бл)",
                () -> c().detectFloor, v -> c().detectFloor = v);
        t("velocity", G_MOVE, "Аномалия скорости", "Маршрут: скорость разошлась с ожидаемой", "Аномалия скорости: 0.31 бл/т (ждал 0.22)",
                () -> c().detectVelocity, v -> c().detectVelocity = v);
        t("moveAttrs", G_MOVE, "Параметры движения", "Сервер изменил скорость, прыжок, гравитацию, размер", "Сервер изменил скорость: 0.1000 → 0.1300 (+30.0 %)",
                () -> c().detectMoveAttrs, v -> c().detectMoveAttrs = v);
        // ── Блоки и мир ──
        t("blockNear", G_WORLD, "Блок рядом", "Сервер поставил твёрдый блок вплотную", "Рядом появился блок: Камень 10 64 -3",
                () -> c().detectBlockInFace, v -> c().detectBlockInFace = v);
        t("obstacle", G_WORLD, "Препятствие впереди", "Твёрдый блок на луче взгляда / по ходу", "Препятствие впереди (по ходу): Камень в 2.1 бл",
                () -> c().detectObstacle, v -> c().detectObstacle = v);
        t("effects", G_WORLD, "Эффекты", "Новый эффект или снятие раньше срока", "Эффект: Медлительность 1",
                () -> c().detectPotionEffect, v -> c().detectPotionEffect = v);
        t("gameMode", G_WORLD, "Режим игры и полёт", "Режим, полёт, респавн, посадка на сущность", "Сервер сменил режим игры: creative",
                () -> c().detectGameMode, v -> c().detectGameMode = v);
        t("fire", G_WORLD, "Горение", "Игрок загорелся", "Игрок горит",
                () -> c().detectFire, v -> c().detectFire = v);
        t("sticky", G_WORLD, "Паутина и вязкие блоки", "Паутина, ягоды, рыхлый снег рядом или в хитбоксе", "Сервер поставил Паутина рядом (2.0 бл)",
                () -> c().detectSticky, v -> c().detectSticky = v);
        t("cage", G_WORLD, "Клетка", "Бедрок/барьер сверху и с 3–4 сторон", "Клетка из бедрока/барьера вокруг игрока (4 из 4 сторон и сверху)",
                () -> c().detectCage, v -> c().detectCage = v);
        t("cageCount", G_WORLD, "Клетка по числу блоков", "Несколько твёрдых блоков у ног/головы почти сразу", "Клетка: 4 блоков у ног/головы за 1.0 с (последний Камень)",
                () -> c().detectCageCount, v -> c().detectCageCount = v);
        t("bedrockBurst", G_WORLD, "Бедрок очередью", "Несколько блоков бедрока рядом почти сразу", "Бедрок очередью: 2 блоков ближе 8 бл за 300 мс",
                () -> c().detectBedrockBurst, v -> c().detectBedrockBurst = v);
        t("farmBlock", G_WORLD, "Блок на ферме", "Поставили блок на ферме — очки, упёрся — паника", "Упёрся в блок, поставленный сервером: Камень 4 70 9",
                () -> c().detectFarmBlock, v -> c().detectFarmBlock = v);
        // ── Чат ──
        t("nearbyChat", G_CHAT, "Обращение игрока рядом", "Приветствие/обращение от известного недавнего соседа: стоп и передача управления, без автоответа", "Alex (рядом): hi — управление тебе",
                () -> c().detectNearbyChat, v -> c().detectNearbyChat = v);
        t("chat", G_CHAT, "Чат: ник и слова", "Сообщение с ником или словом из списка", "Чат (ник) от Admin: «Steve, are you there?»",
                () -> c().detectChat, v -> c().detectChat = v);
        t("title", G_CHAT, "Титры", "/title на экране", "Титр на экране: «MACRO CHECK»",
                () -> c().detectTitle, v -> c().detectTitle = v);
        t("pm", G_CHAT, "Личные сообщения", "«From [RANK] Ник: …»", "Личное сообщение от Admin: «hi»",
                () -> c().detectPm, v -> c().detectPm = v);
        t("afk", G_CHAT, "«You are AFK»", "Сервер предупредил об AFK", "Сервер: «You are AFK»",
                () -> c().detectAfkMsg, v -> c().detectAfkMsg = v);
        // ── Игроки ──
        t("playerNear", G_PLAYERS, "Игрок рядом", "Подошёл другой игрок (не друг, не стартовый)", "Рядом игрок: Steve (5.2 бл)",
                () -> c().detectPlayerNear, v -> c().detectPlayerNear = v);
        t("spectator", G_PLAYERS, "Наблюдатель", "Игрок в Tab стал наблюдателем", "Игрок стал наблюдателем: Admin",
                () -> c().detectSpectator, v -> c().detectSpectator = v);
        t("playerJoin", G_PLAYERS, "Вход / выход", "Зашёл или пропал из Tab (ваниш)", "Зашёл игрок: Admin",
                () -> c().detectPlayerJoin, v -> c().detectPlayerJoin = v);
        t("staff", G_PLAYERS, "Staff рядом", "[ADMIN]/[MOD]/[HELPER]… рядом или в Tab", "Staff рядом: [ADMIN] Someone (12.0 бл)",
                () -> c().detectStaffNear, v -> c().detectStaffNear = v);
        t("twin", G_PLAYERS, "Двойник", "Игрок с твоим ником рядом", "Двойник: игрок с твоим ником в 3.0 бл",
                () -> c().detectTwin, v -> c().detectTwin = v);
        t("massTp", G_PLAYERS, "Массовый телепорт", "Сразу несколько игроков появились рядом", "Массовый телепорт: 5 игроков появились ближе 8 бл за 1.0 с",
                () -> c().detectMassTp, v -> c().detectMassTp = v);
        t("nameless", G_PLAYERS, "Игрок без имени", "Игрок без ника стоит рядом", "Игрок без имени ближе 8 бл дольше 1.0 с (сущность #123)",
                () -> c().detectNameless, v -> c().detectNameless = v);
        // ── Сервер и Skyblock ──
        add(new Def("lagGuard", G_SERVER, "Лаги сервера", "Защита: во время лага «Застрял» ждёт, поправки — в очки. Счётчик — эпизоды лага",
                Kind.GUARD, Action.PANIC, List.of(Action.PANIC, Action.OFF), "Учитывать", null,
                () -> c().lagGuard, v -> c().lagGuard = v));
        t("disconnect", G_SERVER, "Отключение от сервера", "Кикнули или оборвалась связь во время макроса", "Отключение от сервера",
                () -> c().detectDisconnect, v -> c().detectDisconnect = v);
        add(new Def("silence", G_SERVER, "Тишина сервера", "Нет пакетов N с — закрыть соединение (дальше как вылет)",
                Kind.OWN, Action.PANIC, List.of(Action.PANIC, Action.LOG, Action.OFF), "Закрыть связь", "сервер молчит 30 с",
                () -> c().detectSilence, v -> c().detectSilence = v));
        t("restart", G_SERVER, "Перезагрузка / лимбо", "Табло «Server closing», слова restart/limbo", "Сервер перезагружается через 20 с (табло)",
                () -> c().detectRestart, v -> c().detectRestart = v);
        t("bps", G_SERVER, "Урожай не ломается (BPS)", "Блоков в секунду меньше порога", "Урожай не ломается: 1.2 блоков/с < 3.0",
                () -> c().detectBps, v -> c().detectBps = v);
        t("farmingXp",G_SERVER,"Пропал текст Farming XP","Только после наблюдения XP в текущем непрерывном сборе; не чат и не доказательство ghost blocks",
                "Пропал ранее видимый текст Farming XP во время сбора",()->c().detectFarmingXp,v->c().detectFarmingXp=v);
        t("desync", G_SERVER, "Рассинхрон", "Сломанные блоки вернулись на место", "Рассинхрон: 15 из 20 сломанных блоков вернулись",
                () -> c().detectDesync, v -> c().detectDesync = v);
        t("banwave", G_SERVER, "Банвейв (Hypixel)", "Банов за окно больше порога (нужен API-ключ)", "Банвейв: 120 банов за 15 мин (порог 100)",
                () -> c().detectBanwave, v -> c().detectBanwave = v);
        add(new Def("jacob", G_SERVER, "Jacob's Contest: лимит", "Собрано ≥ лимита — обычный стоп", Kind.OWN, Action.PANIC,
                List.of(Action.PANIC, Action.LOG, Action.OFF), "Стоп", "Jacob's Contest: собрано 100000 ≥ 100000",
                () -> c().detectJacob, v -> c().detectJacob = v));
        t("visitor", G_SERVER, "Гость на ферме", "«Ник is visiting Your Garden»", "Гость на ферме: «Steve is visiting Your Garden»",
                () -> c().detectVisitor, v -> c().detectVisitor = v);
        // ── Подозрительность ──
        add(new Def("suspicion", G_SUSP, "Много мелких аномалий", "Сумма очков подозрительности дошла до лимита", Kind.TRIGGER, Action.PANIC,
                List.of(Action.PANIC, Action.LOG, Action.OFF), null, "Много мелких аномалий (10.4/10): толчок ×3, игрок за радиусом ×2",
                () -> c().suspicionEnabled, v -> c().suspicionEnabled = v));
    }

    public static List<Def> all() { return Collections.unmodifiableList(LIST); }
    public static Def get(String id) { return BY_ID.get(id); }

    public static String name(String id) {
        if("combatTeleport".equals(id))return "Combat: телепорт";
        if("combatWeapon".equals(id))return "Combat: потеря оружия";
        if("combatChat".equals(id))return "Combat: обращение в чате";
        if("combatRotation".equals(id))return "Combat: серверная камера";
        if("combatOverlap".equals(id))return "Combat: пересечение с игроком";
        Def d=BY_ID.get(id);return d!=null?d.name():id;
    }

    // ── Действие, звук, вес (настройки — в ModConfig) ────────────────────────

    /** Действие сейчас: выключен → OFF; иначе своё из настроек (если разрешено) или по умолчанию. */
    public static Action action(String id) {
        Def d = BY_ID.get(id);
        if (d == null) return Action.PANIC;
        if (!d.enabled()) return Action.OFF;
        Map<String, String> m = c().detectorActions;
        Action a = m != null ? Action.of(m.get(id)) : null;
        return a != null && a != Action.OFF && d.allowed().contains(a) ? a : d.def();
    }

    /** Выбрать действие: OFF — выключить детектор (прежнее поле), иначе включить и запомнить (по умолчанию — не хранить). */
    public static void setAction(String id, Action a) {
        Def d = BY_ID.get(id);
        if (d == null || !d.allowed().contains(a)) return;
        if (a == Action.OFF) { d.setEnabled(false); return; }
        d.setEnabled(true);
        if (a == d.def()) c().detectorActions.remove(id); else c().detectorActions.put(id, a.key);
    }

    /** Следующее разрешённое действие (клик по столбцу «Действие»). */
    public static Action next(String id, int dir) {
        Def d = BY_ID.get(id);
        List<Action> l = d.allowed();
        int i = l.indexOf(action(id));
        return l.get(Math.floorMod(i + dir, l.size()));
    }

    public static final double DEFAULT_WEIGHT = 3;

    /** Вес для действия «Подозрительность» (у «Отката назад» — прежнее поле suspWeightLagback). */
    public static double weight(String id) {
        if ("lagback".equals(id)) return c().suspWeightLagback;
        Double w = c().detectorWeights != null ? c().detectorWeights.get(id) : null;
        return w != null && Double.isFinite(w) ? Math.max(0, Math.min(100, w)) : DEFAULT_WEIGHT;
    }

    public static void setWeight(String id, double w) {
        if ("lagback".equals(id)) { c().suspWeightLagback = w; return; }
        if (Math.abs(w - DEFAULT_WEIGHT) < 1e-9) c().detectorWeights.remove(id); else c().detectorWeights.put(id, w);
    }

    /** Свой звук паники детектора или null — общий («Звук паники → Сигнал»). */
    public static String sound(String id) {
        if (id == null || c().detectorSounds == null) return null;
        String s = c().detectorSounds.get(id);
        return s == null || s.isBlank() ? null : s;
    }

    public static void setSound(String id, String sound) {
        if (sound == null || sound.isBlank()) c().detectorSounds.remove(id); else c().detectorSounds.put(id, sound);
    }

    public static String actionLabel(Def d, Action a) {
        return switch (a) {
            case PANIC -> d.ownLabel() != null ? d.ownLabel() : "Паника";
            case SUSP -> String.format(Locale.ROOT, "Подозр. +%s", trim(weight(d.id())));
            case LOG -> "Журнал";
            case OFF -> "Выкл";
        };
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    // ── Счётчики и последние записи (за сессию игры, не сохраняются) ─────────

    public record Entry(long ms, Action action, String text) {}

    private static final class Stat {
        int count;
        long lastSoftMs = Long.MIN_VALUE / 2;
        final ArrayDeque<Entry> recent = new ArrayDeque<>();
    }

    /** Повтор того же детектора в режиме «Подозрительность»/«Журнал» не чаще раза в … (условие держится каждый тик). */
    public static final long REPEAT_MS = 5000;
    public static final int RECENT = 8;
    private static final Map<String, Stat> STATS = new LinkedHashMap<>();

    /**
     * Срабатывание детектора. @return что сделать: PANIC — паника (или своё действие), SUSP — очки, LOG — только
     * журнал; null — ничего (выключен или повтор раньше {@link #REPEAT_MS} в мягком режиме).
     */
    public static synchronized Action record(String id, String text, long nowMs) {
        Action a = action(id);
        if (a == Action.OFF) return null;
        Stat s = STATS.computeIfAbsent(id, k -> new Stat());
        if (a != Action.PANIC) {
            if (nowMs - s.lastSoftMs < REPEAT_MS) return null;
            s.lastSoftMs = nowMs;
        }
        push(s, new Entry(nowMs, a, text));
        return a;
    }

    /**
     * Мелкое событие детектора, которое и раньше шло в подозрительность (слабый толчок, игрок за радиусом…).
     * Считается всегда; @return true — оставить очки (по умолчанию), false — только журнал (действие «Журнал»).
     */
    public static synchronized boolean soft(String id, String text, long nowMs) {
        Action a = action(id);
        Stat s = STATS.computeIfAbsent(id, k -> new Stat());
        Action shown = a == Action.LOG ? Action.LOG : Action.SUSP;
        push(s, new Entry(nowMs, shown, text));
        return a != Action.LOG;
    }

    private static void push(Stat s, Entry e) {
        s.count++;
        s.recent.addFirst(e);
        while (s.recent.size() > RECENT) s.recent.pollLast();
    }

    public static synchronized int count(String id) { Stat s = STATS.get(id); return s == null ? 0 : s.count; }

    public static synchronized List<Entry> recent(String id) {
        Stat s = STATS.get(id);
        return s == null ? List.of() : new ArrayList<>(s.recent);
    }

    public static synchronized int totalCount() { int n = 0; for (Stat s : STATS.values()) n += s.count; return n; }

    public static synchronized void resetStats() { STATS.clear(); }

    public static int enabledCount() { int n = 0; for (Def d : LIST) if (d.enabled()) n++; return n; }

    /** «Включить все» / «Выключить все» (кроме BPS, банвейва и Jacob — им нужны свои данные; при «вкл» не трогаем). */
    public static void setAll(boolean on) {
        for (Def d : LIST) {
            if (on && (d.id().equals("bps") || d.id().equals("farmingXp") || d.id().equals("banwave") || d.id().equals("jacob") || d.id().equals("drift")
                    || d.id().equals("lagback"))) continue;
            d.setEnabled(on);
        }
    }

    /** Словари настроек без мусора: только известные id и разрешённые значения. */
    public static void sanitize(ModConfig cfg) {
        if (cfg.detectorActions == null) cfg.detectorActions = new LinkedHashMap<>();
        if (cfg.detectorSounds == null) cfg.detectorSounds = new LinkedHashMap<>();
        if (cfg.detectorWeights == null) cfg.detectorWeights = new LinkedHashMap<>();
        cfg.detectorActions.entrySet().removeIf(e -> {
            Def d = BY_ID.get(e.getKey());
            Action a = Action.of(e.getValue());
            return d == null || a == null || a == Action.OFF || !d.allowed().contains(a) || a == d.def();
        });
        cfg.detectorSounds.entrySet().removeIf(e -> !BY_ID.containsKey(e.getKey()) || e.getValue() == null
                || e.getValue().isBlank() || e.getValue().length() > 200);
        cfg.detectorWeights.entrySet().removeIf(e -> !BY_ID.containsKey(e.getKey()) || e.getValue() == null || !Double.isFinite(e.getValue()));
        cfg.detectorWeights.replaceAll((k, v) -> Math.max(0, Math.min(100, v)));
    }

    /** Для строки «пороги» в логе: детекторы со своим действием/звуком. */
    public static String describe(ModConfig cfg) {
        StringBuilder b = new StringBuilder();
        if (cfg.detectorActions != null) cfg.detectorActions.forEach((k, v) -> b.append(k).append('=').append(v).append(' '));
        if (cfg.detectorSounds != null && !cfg.detectorSounds.isEmpty()) b.append("звуков=").append(cfg.detectorSounds.size());
        return b.toString().strip();
    }
}

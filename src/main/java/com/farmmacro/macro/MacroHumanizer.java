package com.farmmacro.macro;

import com.farmmacro.config.ModConfig;

import java.util.*;

/**
 * MacroHumanizer — рандомизирует макрос перед каждым запуском.
 *
 * Алгоритм:
 *  1. Определяем «критические точки» (повороты / спуски)
 *  2. На каждой критической точке вставляем замедление + паузу (случайная длина)
 *  3. Применяем смену стены — на каждом прямом отрезке между поворотами
 *     игрок смещается на ±offset по перпендикулярной оси
 *  4. Вставляем задержку старта в начало
 *  5. Jitter — лёгкое дрожание yaw на поворотах
 *  6. Микро-паузы — редкие (0.8%) вставки лишнего кадра
 */
public class MacroHumanizer {

    private static final Random RNG = new Random();

    /**
     * История комбинаций пауз — хранит строковые ключи последних N запусков.
     * Одна и та же комбинация не может повториться раньше чем через COMBO_COOLDOWN других.
     */
    private static final int COMBO_COOLDOWN = 4;
    private static final ArrayDeque<String> recentCombos = new ArrayDeque<>();

    /** Применяет humanize к копии списка кадров. Оригинал не трогает. */
    public static List<MacroFrame> humanize(List<MacroFrame> original) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.humanizeEnabled) return original;

        // Глубокое копирование
        List<MacroFrame> frames = deepCopy(original);

        List<Integer> critPoints = detectCriticalPoints(frames);

        // 1. Смена стены (до вставки задержек, чтобы индексы совпадали)
        if (c.humanizeWallShift) {
            List<int[]> segments = buildSegments(frames, critPoints);
            applyWallShift(frames, segments, c.humanizeWallOffset);
        }

        // 2. Задержка старта
        int startTicks = 0;
        if (c.humanizeStartDelayMax > 0) {
            float sMin = c.humanizeStartDelayMin;
            float sMax = c.humanizeStartDelayMax;
            float startSec = sMin + RNG.nextFloat() * (sMax - sMin);
            startTicks = Math.round(startSec * 20f); // SERVER_TPS = 20
            frames = addStartDelay(frames, startTicks);
        }

        // 3. Случайные паузы на поворотах
        if (!critPoints.isEmpty() && (c.humanizeDelayMax > 0 || c.humanizeSlowdownTicks > 0)) {
            // Сколько точек выбрать: 2–min(6, n)
            int count = 2 + RNG.nextInt(Math.max(1, Math.min(5, critPoints.size() - 1)));

            // Выбираем комбинацию с учётом cooldown — одна и та же комбинация
            // не должна повторяться раньше чем через COMBO_COOLDOWN других запусков.
            List<Integer> chosen = pickComboWithCooldown(critPoints, count);

            // Запоминаем эту комбинацию
            String comboKey = comboKey(chosen);
            recentCombos.addLast(comboKey);
            while (recentCombos.size() > COMBO_COOLDOWN) recentCombos.pollFirst();

            // Сдвигаем на задержку старта
            List<Integer> sorted = new ArrayList<>();
            for (int p : chosen) sorted.add(p + startTicks);

            // Вставляем от конца к началу (чтобы не сбивать индексы)
            sorted.sort(Collections.reverseOrder());

            for (int idx : sorted) {
                // Взвешенный рандом: короткие паузы выпадают чаще длинных.
                // Генерируем два числа и берём меньшее — это смещает распределение к минимуму.
                int range = Math.max(1, c.humanizeDelayMax - c.humanizeDelayMin + 1);
                int r1 = RNG.nextInt(range);
                int r2 = RNG.nextInt(range);
                int pause = c.humanizeDelayMin + Math.min(r1, r2);

                int slow = c.humanizeSlowdownTicks;
                if (slow > 0) {
                    frames = addSlowdown(frames, idx, slow);
                    idx += slow;
                }
                frames = addPause(frames, idx, pause);
            }
        }

        // 4. Микро-пауза (раз в N запусков)
        frames = applyMicroPause(frames);

        return frames;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Находим повороты и спуски */
    static List<Integer> detectCriticalPoints(List<MacroFrame> frames) {
        List<Integer> points = new ArrayList<>();
        double prevDx = 0, prevDz = 0;
        double prevY = frames.isEmpty() ? 0 : frames.get(0).y;

        for (int i = 1; i < frames.size(); i++) {
            MacroFrame cur  = frames.get(i);
            MacroFrame prev = frames.get(i - 1);
            double dx = cur.x - prev.x;
            double dz = cur.z - prev.z;
            double dy = cur.y - prevY;

            boolean turn = (Math.abs(prevDx) > 0.01 && Math.abs(dz) > 0.01) ||
                           (Math.abs(prevDz) > 0.01 && Math.abs(dx) > 0.01);
            if (turn)      points.add(i);
            if (dy < -0.3) points.add(i);

            prevDx = dx;
            prevDz = dz;
            prevY  = cur.y;
        }

        // Убираем точки ближе 30 кадров друг от друга
        List<Integer> unique = new ArrayList<>();
        int last = -100;
        for (int p : points) {
            if (p - last > 30) { unique.add(p); last = p; }
        }
        return unique;
    }

    /** Строит отрезки между поворотами: (startIdx, endIdx, isAxisX) */
    private static List<int[]> buildSegments(List<MacroFrame> frames, List<Integer> critPoints) {
        List<Integer> bounds = new ArrayList<>();
        bounds.add(0);
        bounds.addAll(critPoints);
        bounds.add(frames.size() - 1);

        List<int[]> segments = new ArrayList<>();
        for (int i = 0; i < bounds.size() - 1; i++) {
            int s = bounds.get(i);
            int e = bounds.get(i + 1);
            if (e - s < 5) continue;
            int clampedE = Math.min(e, frames.size() - 1);
            double dx = Math.abs(frames.get(clampedE).x - frames.get(s).x);
            double dz = Math.abs(frames.get(clampedE).z - frames.get(s).z);
            // axis: 0 = смещаем по X, 1 = смещаем по Z
            int axis = (dx < dz) ? 0 : 1;
            segments.add(new int[]{s, e, axis});
        }
        return segments;
    }

    /** Смещает кадры на каждом прямом отрезке к одной из стен */
    private static void applyWallShift(List<MacroFrame> frames, List<int[]> segments, float maxOffset) {
        for (int[] seg : segments) {
            int s = seg[0], e = seg[1], axis = seg[2];
            float offset = -maxOffset + RNG.nextFloat() * 2 * maxOffset;
            for (int i = s; i <= Math.min(e, frames.size() - 1); i++) {
                MacroFrame f = frames.get(i);
                if (axis == 0) f.x += offset;
                else           f.z += offset;
            }
        }
    }

    /** Вставляет idle-кадры в начало (задержка старта) */
    private static List<MacroFrame> addStartDelay(List<MacroFrame> frames, int ticks) {
        if (ticks <= 0 || frames.isEmpty()) return frames;
        MacroFrame idle = copyFrame(frames.get(0));
        idle.forward = idle.back = idle.left = idle.right = false;
        List<MacroFrame> result = new ArrayList<>(ticks + frames.size());
        for (int i = 0; i < ticks; i++) result.add(copyFrame(idle));
        result.addAll(frames);
        return result;
    }

    /**
     * Замедление: вставляет дубликаты кадров перед index,
     * имитируя постепенное торможение.
     */
    private static List<MacroFrame> addSlowdown(List<MacroFrame> frames, int index, int ticks) {
        if (index <= ticks || index >= frames.size()) return frames;
        for (int step = ticks - 1; step >= 0; step--) {
            int pos = index - ticks + step;
            if (pos >= 0 && pos < frames.size())
                frames.add(pos, copyFrame(frames.get(pos)));
        }
        return frames;
    }

    /** Вставляет паузу (idle-кадры) в позицию index */
    private static List<MacroFrame> addPause(List<MacroFrame> frames, int index, int ticks) {
        if (index >= frames.size()) return frames;
        MacroFrame idle = copyFrame(frames.get(index));
        idle.forward = idle.back = idle.left = idle.right = false;
        for (int i = 0; i < ticks; i++) frames.add(index, copyFrame(idle));
        return frames;
    }

    /**
     * Счётчик запусков с последней микро-паузы.
     * Микро-пауза срабатывает раз в randomized N запусков.
     */
    private static int runsUntilNextMicroPause = -1; // -1 = не инициализировано

    /**
     * Проверяет, нужно ли в этом запуске добавить микро-паузу,
     * и если да — вставляет одну паузу в случайную точку на прямом участке.
     */
    private static List<MacroFrame> applyMicroPause(List<MacroFrame> frames) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.humanizeMicroPauses) return frames;

        // Инициализация при первом запуске
        if (runsUntilNextMicroPause < 0) {
            runsUntilNextMicroPause = nextMicroPauseInterval(c);
        }

        runsUntilNextMicroPause--;
        if (runsUntilNextMicroPause > 0) return frames; // ещё не время

        // Сбрасываем счётчик на следующий интервал
        runsUntilNextMicroPause = nextMicroPauseInterval(c);

        if (frames.size() < 10) return frames;

        // Находим прямые участки (не у поворотов)
        Set<Integer> nearTurn = new HashSet<>();
        List<Integer> currentTurns = detectCriticalPoints(frames);
        for (int cp : currentTurns) {
            for (int d = -25; d <= 25; d++) {
                int idx = cp + d;
                if (idx >= 0) nearTurn.add(idx);
            }
        }

        // Собираем кандидатов — кадры в середине, не у поворотов
        List<Integer> candidates = new ArrayList<>();
        int quarter = frames.size() / 4;
        for (int i = quarter; i < frames.size() * 3 / 4; i++) {
            if (!nearTurn.contains(i)) candidates.add(i);
        }
        if (candidates.isEmpty()) return frames;

        // Выбираем случайный кадр для вставки
        int insertAt = candidates.get(RNG.nextInt(candidates.size()));

        // Длина паузы в тиках
        float durMin = Math.max(0.1f, c.humanizeMicroPauseDurMin);
        float durMax = Math.max(durMin, c.humanizeMicroPauseDurMax);
        int pauseTicks = Math.round((durMin + RNG.nextFloat() * (durMax - durMin)) * 20f);
        pauseTicks = Math.max(1, pauseTicks);

        MacroFrame idle = copyFrame(frames.get(insertAt));
        idle.attackPressed = false;
        idle.usePressed    = false;
        idle.forward = idle.back = idle.left = idle.right = false;

        for (int i = 0; i < pauseTicks; i++) frames.add(insertAt, copyFrame(idle));

        return frames;
    }

    /** Возвращает случайный интервал запусков до следующей микро-паузы */
    private static int nextMicroPauseInterval(ModConfig c) {
        int lo = Math.max(1, c.humanizeMicroPauseEveryMin);
        int hi = Math.max(lo, c.humanizeMicroPauseEveryMax);
        return lo + RNG.nextInt(hi - lo + 1);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static MacroFrame copyFrame(MacroFrame f) {
        return new MacroFrame(
                f.x, f.y, f.z, f.yaw, f.pitch,
                f.forward, f.back, f.left, f.right,
                f.jump, f.sneak, f.sprint,
                f.attackPressed, f.usePressed,
                f.selectedSlot
        );
    }

    private static List<MacroFrame> deepCopy(List<MacroFrame> src) {
        List<MacroFrame> copy = new ArrayList<>(src.size());
        for (MacroFrame f : src) copy.add(copyFrame(f));
        return copy;
    }

    /**
     * Выбирает комбинацию критических точек с учётом cooldown.
     * Пробует до 20 раз найти комбинацию которой нет в recentCombos.
     * Если все варианты в кулдауне — возвращает любую (кулдаун неизбежен при малом числе точек).
     */
    private static List<Integer> pickComboWithCooldown(List<Integer> critPoints, int count) {
        List<Integer> best = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            List<Integer> candidate = pickRandom(critPoints, count);
            String key = comboKey(candidate);
            if (!recentCombos.contains(key)) return candidate;
            if (best == null) best = candidate;
        }
        return best; // все варианты в кулдауне — возвращаем хоть что-то
    }

    /**
     * Строковый ключ комбинации — отсортированные индексы через запятую.
     * Используем относительные позиции (% 30) чтобы комбинации с одинаковым
     * «рисунком» считались одинаковыми даже при разном количестве кадров.
     */
    private static String comboKey(List<Integer> points) {
        List<Integer> normalized = new ArrayList<>(points);
        Collections.sort(normalized);
        StringBuilder sb = new StringBuilder();
        for (int p : normalized) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p / 30); // группируем по блокам из 30 кадров
        }
        return sb.toString();
    }

    private static List<Integer> pickRandom(List<Integer> list, int count) {
        List<Integer> shuffled = new ArrayList<>(list);
        Collections.shuffle(shuffled, RNG);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }
}

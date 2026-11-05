package com.farmmacro.macro;

import com.farmmacro.macro.MacroManager.State;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * 2.1.0: явная таблица переходов {@link MacroManager.State}. Всё, что меняет состояние, идёт через
 * {@code MacroManager.setState}; переход не из таблицы не блокируется (поведение прежнее), а один раз
 * пишется в журнал — так «застрял в состоянии» видно сразу.
 *
 * <pre>
 * IDLE ──► RECORDING ──► IDLE
 * IDLE ──► COUNTDOWN ──► PLAYING ──► IDLE
 *              └────────► IDLE
 * IDLE ──► PLAYING (без отсчёта)
 * </pre>
 */
@NullMarked
public final class MacroStates {
    private static final Map<State, Set<State>> NEXT = new EnumMap<>(State.class);

    static {
        NEXT.put(State.IDLE, EnumSet.of(State.RECORDING, State.COUNTDOWN, State.PLAYING));
        NEXT.put(State.RECORDING, EnumSet.of(State.IDLE));
        NEXT.put(State.COUNTDOWN, EnumSet.of(State.PLAYING, State.IDLE));
        NEXT.put(State.PLAYING, EnumSet.of(State.IDLE));
    }

    private MacroStates() {}

    /** Переход разрешён таблицей (остаться в том же состоянии — всегда можно). */
    public static boolean allowed(State from, State to) {
        return from == to || next(from).contains(to);
    }

    /** Куда можно перейти из {@code from}. */
    public static Set<State> next(State from) {
        Set<State> s = NEXT.get(from);
        return s == null ? EnumSet.noneOf(State.class) : EnumSet.copyOf(s);
    }
}

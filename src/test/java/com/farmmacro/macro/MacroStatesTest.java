package com.farmmacro.macro;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.farmmacro.macro.MacroManager.State;
import org.junit.jupiter.api.Test;

class MacroStatesTest {
    @Test
    void everyStateCanReturnToIdle() {
        for (State s : State.values()) assertTrue(MacroStates.allowed(s, State.IDLE), s + " → IDLE");
    }

    @Test
    void playbackStartsOnlyFromIdleOrCountdown() {
        assertTrue(MacroStates.allowed(State.IDLE, State.PLAYING));
        assertTrue(MacroStates.allowed(State.COUNTDOWN, State.PLAYING));
        assertFalse(MacroStates.allowed(State.RECORDING, State.PLAYING));
        assertFalse(MacroStates.allowed(State.RECORDING, State.COUNTDOWN));
        assertFalse(MacroStates.allowed(State.PLAYING, State.RECORDING));
        assertFalse(MacroStates.allowed(State.PLAYING, State.COUNTDOWN));
    }

    @Test
    void selfTransitionIsAlwaysAllowed() {
        for (State s : State.values()) assertTrue(MacroStates.allowed(s, s));
    }

    @Test
    void everyStateIsReachableFromIdle() {
        for (State s : State.values()) {
            if (s == State.IDLE) continue;
            boolean reachable = MacroStates.allowed(State.IDLE, s)
                    || MacroStates.next(State.IDLE).stream().anyMatch(mid -> MacroStates.allowed(mid, s));
            assertTrue(reachable, s + " недостижимо");
        }
    }
}

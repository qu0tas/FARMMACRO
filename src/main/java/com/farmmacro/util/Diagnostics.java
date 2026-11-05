package com.farmmacro.util;

import com.farmmacro.combat.AimAssist;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.*;
import com.farmmacro.panic.PanicHandoff;
import com.farmmacro.route.ChunkCache;
import com.farmmacro.route.NavigationInput;
import com.farmmacro.route.PathJob;
import com.farmmacro.util.mixin.MixinAudit;
import com.farmmacro.visual.PerformanceMode;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Memory-only fixed-domain facts. No raw chat/status, profile names, item lore, IDs, positions or exception messages. */
public final class Diagnostics {
    public enum NavEvent {
        SEARCH_STARTED,
        REPLAN,
        PATH_READY,
        SEARCH_FAILED,
        MANUAL_STOP,
        FAILED,
        STOPPED
    }

    public static void navigation(NavEvent e) {
        if (facts.size() == 128) facts.removeFirst();
        facts.addLast(new Fact((System.nanoTime() - origin) / 1_000_000, "NAV_EVENT", "-", e.name()));
        transitions++;
    }

    public enum Counter {
        ENTITY_SNAPSHOTS,
        ENTITY_LIMIT,
        NAME_SCANS,
        COMBAT_SCANS,
        BODY_PROBES,
        BODY_LIMIT,
        HUD_PREPARES,
        PATH_SNAPSHOT_LIMIT,
        PATH_SUBMITTED,
        PATH_FINISHED,
        PATH_CANCELLED,
        PATH_REJECTED,
        GUARD_FAILURES,
        CHUNK_SKIPPED
    }

    private static final LongAdder[] counters =
            Arrays.stream(Counter.values()).map(x -> new LongAdder()).toArray(LongAdder[]::new);
    private static final String[] domains = {"WORLD", "MACRO", "NAV", "COMBAT", "PANIC"};
    private static final String[] states = new String[domains.length];

    private record Fact(long at, String domain, String from, String to) {}

    private static final ArrayDeque<Fact> facts = new ArrayDeque<>(128);
    private static final long origin = System.nanoTime();
    private static long transitions;

    public static void add(Counter c) {
        counters[c.ordinal()].increment();
    }

    private static void state(int i, String value) {
        if (Objects.equals(states[i], value)) return;
        if (facts.size() == 128) facts.removeFirst();
        facts.addLast(new Fact(
                (System.nanoTime() - origin) / 1_000_000, domains[i], states[i] == null ? "?" : states[i], value));
        states[i] = value;
        transitions++;
    }
    /** Called in finally after the normal tick: early safety returns are observable, never resumed here. */
    public static void tick(Minecraft mc) {
        state(0, mc.level == null ? "NO_WORLD" : mc.player == null ? "NO_PLAYER" : "PRESENT");
        state(1, MacroManager.INSTANCE.getState().name());
        state(2, NavigationInput.technicalState());
        state(3, AimAssist.INSTANCE.technicalState());
        state(4, PanicHandoff.pending() ? "HANDOFF_PENDING" : "CLEAR");
    }

    public static String summary() {
        return "Переходов " + transitions + " (храним " + facts.size() + "/128); ошибки "
                + counters[Counter.GUARD_FAILURES.ordinal()].sum() + " • только технические факты";
    }

    public static String report(Minecraft mc) {
        tick(mc);
        var c = ModConfig.INSTANCE;
        String v = FabricLoader.getInstance()
                .getModContainer("farmmacro")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
        StringBuilder b = new StringBuilder("Kaelix diagnostics v1 / ")
                .append(v)
                .append("\nMC 26.1.2 / Fabric / Java ")
                .append(Runtime.version().feature())
                .append("\nProfile: ")
                .append("zealots".equals(c.combatProfileId) ? "zealots" : "custom (name omitted)");
        b.append("\nNo chat/names/UUID/server/coordinates/secrets; memory only; NOT a benchmark.\n");
        for (int i = 0; i < domains.length; i++)
            b.append(domains[i]).append('=').append(states[i]).append(' ');
        b.append("\nPERF=")
                .append(PerformanceMode.active())
                .append("; background cap=")
                .append(c.perfBackgroundFps)
                .append("; chunk cap=")
                .append(c.perfChunks)
                .append("; block particles off=")
                .append(c.perfBlockParticles)
                .append("; world volume%=")
                .append(c.perfWorldVolume);
        b.append("\nDiscovery snapshot=")
                .append(LoadedEntities.size())
                .append("/8192; truncated=")
                .append(LoadedEntities.truncated());
        b.append("\nPath worker: ").append(PathJob.technicalState());
        b.append("\nChunk IO: ").append(ChunkCache.technicalState());
        b.append("\nFile IO: ").append(IoWorker.technicalState());
        b.append("\nCounters (since process start; discovery calls, not FPS):");
        for (var x : Counter.values()) b.append(' ').append(x).append('=').append(counters[x.ordinal()].sum());
        b.append("\nGuard: ").append(Guard.diagnosticSummary());
        b.append("\nSlowest steps (avg/max ms × calls): ").append(StepProfiler.summary(8));
        b.append("\n").append(MixinAudit.summary(FabricLoader.getInstance()::isModLoaded));
        b.append("\nCombat selection (last search, not every tick):\n")
                .append(AimAssist.INSTANCE.safeTargetDiagnostics());
        b.append("\nTransitions (observed end-of-tick; short intermediate phases may be absent):");
        for (var f : facts)
            b.append("\n+")
                    .append(f.at)
                    .append("ms ")
                    .append(f.domain)
                    .append(' ')
                    .append(f.from)
                    .append(" -> ")
                    .append(f.to);
        return b.toString();
    }

    private Diagnostics() {}
}

package com.farmmacro.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Все короткие проверки без игры из {@code src/stress} как JUnit-тесты: у каждой проверки свой результат в отчёте,
 * а упавшая показывает точный стек. Раньше они шли одной цепочкой в {@code quickCheck}/{@code routeStress},
 * и первая ошибка скрывала остальные.
 */
class StressChecksTest {
    /** Проверки из quickCheck (AttendedQuickCheck) и routeStress (RouteStress), по одной на тест. */
    static final List<String> CHECKS = List.of(
            // release / quickCheck
            "com.farmmacro.release.MotionQuickCheck",
            "com.farmmacro.release.BindingQuickCheck",
            "com.farmmacro.release.TelemetryQuickCheck",
            "com.farmmacro.release.UiLayoutQuickCheck",
            "com.farmmacro.release.OwnershipQuickCheck",
            "com.farmmacro.release.ManualAimQuickCheck",
            "com.farmmacro.release.DungeonScytheQuickCheck",
            "com.farmmacro.release.HuntingQuickCheck",
            "com.farmmacro.release.ManualSlotQuickCheck",
            "com.farmmacro.release.CombatObservationQuickCheck",
            "com.farmmacro.release.FishingQuickCheck",
            "com.farmmacro.release.StartHereQuickCheck",
            "com.farmmacro.release.PanicStopQuickCheck",
            "com.farmmacro.release.ManualAimPanicQuickCheck",
            "com.farmmacro.gui.ModuleRetirementCheck",
            "com.farmmacro.release.AttendedQuickCheck",
            // routeStress
            "com.farmmacro.camera.CameraBindingCheck",
            "com.farmmacro.camera.CropPresetsCheck",
            "com.farmmacro.camera.TrackingTurnCheck",
            "com.farmmacro.combat.CombatRegressionCheck",
            "com.farmmacro.config.ConfigCodeCheck",
            "com.farmmacro.fun.CropFeverMediaCheck",
            "com.farmmacro.fun.FeverLightsCheck",
            "com.farmmacro.fun.FeverMotionCheck",
            "com.farmmacro.gui.NumberInputCheck",
            "com.farmmacro.gui.ThemeCheck",
            "com.farmmacro.hud.InventorySlotsCheck",
            "com.farmmacro.loading.LoadingLookCheck",
            "com.farmmacro.macro.DelayCheck",
            "com.farmmacro.macro.HoldCheck",
            "com.farmmacro.macro.HumanizeGaussCheck",
            "com.farmmacro.macro.HumanizerCheck",
            "com.farmmacro.macro.ViewCheck",
            "com.farmmacro.panic.BigPanicCheck",
            "com.farmmacro.panic.CorrectionWindowCheck",
            "com.farmmacro.panic.DetectorsCheck",
            "com.farmmacro.panic.PlayersCheck",
            "com.farmmacro.panic.SuspicionCheck",
            "com.farmmacro.panic.SyncCheck",
            "com.farmmacro.route.ChunkCacheCheck",
            "com.farmmacro.route.DropCheck",
            "com.farmmacro.route.FlyCheck",
            "com.farmmacro.route.GroundGuideCheck",
            "com.farmmacro.route.NavigationBoundaryCheck",
            "com.farmmacro.route.OffsetCheck",
            "com.farmmacro.route.PathCheck",
            "com.farmmacro.route.ReturnGuidedCheck",
            "com.farmmacro.route.RouteRandomCheck",
            "com.farmmacro.route.Release117Check",
            "com.farmmacro.visual.CheeksCheck",
            "com.farmmacro.window.WindowCheck");

    /** Классы в src/stress, которые проверками не являются (симуляторы/утилиты для ручных прогонов). */
    static final Set<String> NOT_CHECKS = Set.of(
            "com.farmmacro.route.VelocitySim",
            "com.farmmacro.route.WalkSim",
            "com.farmmacro.route.TestRoutes",
            "com.farmmacro.macro.ConfigSweep",
            "com.farmmacro.macro.ConfigSweepPresets",
            "com.farmmacro.panic.SoundDecodeStress",
            "com.farmmacro.visual.RouteStress");

    @TestFactory
    Stream<DynamicTest> stressChecks() {
        return CHECKS.stream().map(name -> DynamicTest.dynamicTest(simple(name), () -> invokeMain(name)));
    }

    /** Новая проверка в src/stress без строки в CHECKS молча не запускалась бы в CI — ловим это здесь. */
    @Test
    void everyStressCheckIsRegistered() throws IOException, URISyntaxException, ClassNotFoundException {
        Path root = Path.of(Class.forName("com.farmmacro.release.AttendedQuickCheck")
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".class")
                            && !p.getFileName().toString().contains("$"))
                    .map(p -> root.relativize(p)
                            .toString()
                            .replace('/', '.')
                            .replace('\\', '.')
                            .replaceAll("\\.class$", ""))
                    .forEach(found::add);
        }
        assertTrue(found.size() > 20, "stress classes not found in " + root);
        Set<String> unknown = new TreeSet<>(found);
        CHECKS.forEach(unknown::remove);
        NOT_CHECKS.forEach(unknown::remove);
        assertEquals(Set.of(), unknown, "добавь новые проверки в StressChecksTest.CHECKS (или в NOT_CHECKS)");
    }

    private static String simple(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static void invokeMain(String name) throws Throwable {
        Class<?> type = Class.forName(name);
        Method main = null;
        for (Method m : type.getDeclaredMethods()) {
            if (!m.getName().equals("main") || !Modifier.isStatic(m.getModifiers())) continue;
            if (m.getParameterCount() == 0) {
                main = m;
                break;
            }
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String[].class) main = m;
        }
        if (main == null) throw new AssertionError(name + ": нет static main()");
        main.setAccessible(true);
        try {
            if (main.getParameterCount() == 0) main.invoke(null);
            else main.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}

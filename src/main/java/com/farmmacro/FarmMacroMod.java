package com.farmmacro;

import com.farmmacro.camera.CameraPresets;
import com.farmmacro.camera.Freecam;
import com.farmmacro.camera.Freelook;
import com.farmmacro.camera.MouseLock;
import com.farmmacro.camera.Mousemat;
import com.farmmacro.camera.Ungrab;
import com.farmmacro.combat.AimAssist;
import com.farmmacro.combat.MobHighlightRenderer;
import com.farmmacro.config.ModConfig;
import com.farmmacro.fishing.AutoFish;
import com.farmmacro.fishing.FishingCommands;
import com.farmmacro.fishing.FishingMessages;
import com.farmmacro.fun.CropFever;
import com.farmmacro.fun.Foxy;
import com.farmmacro.gui.CustomUiAssets;
import com.farmmacro.gui.FarmMacroScreen;
import com.farmmacro.gui.WelcomeScreen;
import com.farmmacro.gui.WhatsNewScreen;
import com.farmmacro.hud.FarmHud;
import com.farmmacro.hud.HudEditorScreen;
import com.farmmacro.hud.StatsHud;
import com.farmmacro.loading.LoadingOptions;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.macro.MouseHold;
import com.farmmacro.notify.DiscordNotify;
import com.farmmacro.panic.AutomationSafety;
import com.farmmacro.panic.BanwaveWatch;
import com.farmmacro.panic.Detectors;
import com.farmmacro.panic.EventLog;
import com.farmmacro.panic.LagGuard;
import com.farmmacro.panic.PanicDetector;
import com.farmmacro.panic.PanicHandoff;
import com.farmmacro.panic.PanicOverlayRenderer;
import com.farmmacro.panic.PanicSound;
import com.farmmacro.panic.SilenceWatch;
import com.farmmacro.route.ChunkCache;
import com.farmmacro.route.RouteEditor;
import com.farmmacro.stats.StatsLedger;
import com.farmmacro.stats.Telemetry;
import com.farmmacro.util.Diagnostics;
import com.farmmacro.util.Guard;
import com.farmmacro.util.LoadedEntities;
import com.farmmacro.util.mixin.MixinAudit;
import com.farmmacro.visual.CheeksLayer;
import com.farmmacro.visual.ChinaHatRenderer;
import com.farmmacro.visual.LowRender;
import com.farmmacro.visual.NickHide;
import com.farmmacro.visual.PerformanceMode;
import com.farmmacro.visual.RouteRenderer;
import com.farmmacro.window.BackgroundWork;
import com.farmmacro.window.Pip;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FarmMacroMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("Kaelix");

    /** Своя категория в меню управления (с 1.21.9 категория — объект, а не строка). */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("farmmacro", "general"));

    public static KeyMapping keyPlay,
            keyOpenGui,
            keyResume,
            keyResetPos,
            keyCamApply,
            keyCamNext,
            keyCamSave,
            keyEditor,
            keyHold,
            keyMouseLock,
            keyFreecam,
            keyFreelook,
            keyLowRender,
            keyStartFrom,
            keyUngrab,
            keyPerf,
            keyHudEdit,
            keyPip,
            keyAim,
            keyFishing;

    /** Single catalog used by the in-mod editor and pending-panic start gate. */
    public static List<KeyMapping> bindings() {
        return Stream.of(
                        keyPlay,
                        keyOpenGui,
                        keyResume,
                        keyResetPos,
                        keyCamApply,
                        keyCamNext,
                        keyCamSave,
                        keyEditor,
                        keyHold,
                        keyMouseLock,
                        keyFreecam,
                        keyFreelook,
                        keyLowRender,
                        keyStartFrom,
                        keyUngrab,
                        keyPerf,
                        keyHudEdit,
                        keyPip,
                        keyAim,
                        keyFishing)
                .filter(Objects::nonNull)
                .toList();
    }

    private static KeyMapping makeKey(String id, int defaultCode) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(id, InputConstants.Type.KEYSYM, defaultCode, CATEGORY));
    }

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        // 2.1.0: конфиг пишется в фоне — при выходе дождаться записи (и на случай выхода мимо CLIENT_STOPPING)
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> ModConfig.flush());
        Runtime.getRuntime().addShutdownHook(new Thread(ModConfig::flush, "Kaelix-config-flush"));
        FishingMessages.load();
        FishingCommands.register();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            AutoFish.INSTANCE.message(message.getString(), overlay);
        });
        DiscordNotify.configured(); // Load local secret redaction before any portable export. No network.
        StatsLedger.init();
        LoadingOptions.clientReady();
        PanicSound.init();
        ModConfig cfg = ModConfig.INSTANCE;
        if (cfg.customUiImages) CustomUiAssets.reload();

        FarmHud.register();
        Foxy.register();
        CropFever.register();
        PanicOverlayRenderer.register();
        ChinaHatRenderer.register();
        MobHighlightRenderer.register();
        CheeksLayer.register();
        RouteRenderer.register();

        keyPlay = makeKey("key.farmmacro.play", cfg.keyPlay);
        keyOpenGui = makeKey("key.farmmacro.gui", cfg.keyOpenGui);
        keyResume = makeKey("key.farmmacro.resume", 79); // O
        keyResetPos = makeKey("key.farmmacro.resetpos", 269); // End
        keyCamApply = makeKey("key.farmmacro.cam_apply", 75); // K
        keyCamNext = makeKey("key.farmmacro.cam_next", 74); // J
        keyCamSave = makeKey("key.farmmacro.cam_save", 72); // H
        keyEditor = makeKey("key.farmmacro.editor", 66); // B
        keyHold = makeKey("key.farmmacro.hold", 76); // L
        keyMouseLock = makeKey("key.farmmacro.mouselock", 77); // M
        keyFreelook = makeKey("key.farmmacro.freelook", 295); // F6 (v1.12)
        keyLowRender = makeKey("key.farmmacro.lowrender", 296); // F7 (v1.12)
        keyFreecam = makeKey("key.farmmacro.freecam", -1); // assign inside GUI
        keyUngrab = makeKey("key.farmmacro.ungrab", 85); // U (v1.13.1) — отпустить курсор
        keyPerf = makeKey("key.farmmacro.perf", 298); // F9 (v1.13.1) — режим производительности
        keyHudEdit = makeKey("key.farmmacro.hudedit", 297); // F8 (v1.14) — редактор HUD
        keyPip = makeKey("key.farmmacro.pip", -1); // v1.16.3 — картинка в картинке (не назначена)
        keyAim = makeKey("key.farmmacro.aim", 301); // F12: hold/toggle center lock
        keyStartFrom = makeKey("key.farmmacro.startfrom", -1); // assign in GUI; explicit route-only start
        keyFishing = makeKey("key.farmmacro.fishing", -1); // explicit unbound attended toggle

        ClientTickEvents.END_CLIENT_TICK.register(FarmMacroMod::onTick);
        ChunkCache.register();
        // v1.13: отключение от сервера во время макроса — паника; новый сервер — сброс замера лагов
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            boolean fishingWasActive = AutoFish.INSTANCE.active();
            AutoFish.INSTANCE.stop("отключение");
            Guard.run("combat/disconnect", () -> AimAssist.INSTANCE.handoff("отключение"));
            Guard.run("panic/delayed-disconnect", () -> PanicDetector.INSTANCE.finishDelayed(client));
            AutomationSafety.INSTANCE.clear();
            CropFever.reset(client);
            boolean playing = fishingWasActive
                    || MacroManager.INSTANCE.playingOrJustLost()
                            && (client.screen == null || client.screen instanceof ChatScreen);
            Guard.run("panic/disconnect", () -> PanicDetector.INSTANCE.onDisconnect(client, playing));
            SilenceWatch.INSTANCE.reset();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LagGuard.INSTANCE.reset();
            SilenceWatch.INSTANCE.onJoin(System.currentTimeMillis());
        });
        LOGGER.info("Kaelix загружен");
    }

    private static boolean welcomeShown;

    /** Фоновые службы: каждый тик, по порядку, каждая под своим {@link Guard} (ошибка одной не роняет остальные). */
    private record Phase(String name, Consumer<Minecraft> step) {}

    private static final List<Phase> SERVICES = List.of(
            new Phase("window/background", BackgroundWork::tick),
            new Phase("camera/freecam", Freecam::tick),
            new Phase("stats/tick", Telemetry::tick),
            new Phase("route/cache", ChunkCache::tick),
            new Phase("panic/effects", mc -> PanicDetector.INSTANCE.tickEffects()),
            new Phase("panic/banwave", mc -> BanwaveWatch.tick()),
            new Phase("fun/foxy", Foxy::tick),
            new Phase("fun/crop_fever", CropFever::tick),
            new Phase("visual/perf", PerformanceMode::tick),
            new Phase("camera/ungrab", Ungrab::tick),
            new Phase(
                    "panic/always",
                    mc -> PanicDetector.INSTANCE.tickAlways(mc)), // лямбда: INSTANCE не трогаем при загрузке класса
            new Phase("panic/silence", FarmMacroMod::checkSilence));
    /** Визуальные мелочи после клавиши «картинка в картинке» (v1.16.3: PiP, скрытие ника, Squeaky Mousemat). */
    private static final List<Phase> VISUALS = List.of(
            new Phase("window/pip-tick", Pip::tick),
            new Phase("nick/tick", NickHide::tick),
            new Phase("camera/mousemat", Mousemat::tick),
            new Phase("util/mixin-audit", mc -> {
                if (mc.player != null) MixinAudit.tickInWorld();
            }));

    private static void onTick(Minecraft mc) {
        LoadedEntities.beginTick();
        if (mc.level == null || mc.player == null) StatsHud.clear();
        try {
            tickBody(mc);
        } finally {
            Guard.run("diagnostics/tick", () -> Diagnostics.tick(mc));
        }
    }

    /**
     * Порядок важен и не менялся: очередь аварийной остановки → службы → экраны первого запуска →
     * (нет игрока: всё гасим) → Freecam → клавиши → проверки безопасности → автоматика.
     * Каждый ранний return оставляет автоматику без нового ввода в этом тике.
     */
    private static void tickBody(Minecraft mc) {
        MacroManager macro = MacroManager.INSTANCE;
        if (!Guard.run("panic/delayed-stop", () -> PanicDetector.INSTANCE.tickDelayed(mc)))
            PanicDetector.INSTANCE.triggerPanic(mc, "Ошибка очереди остановки — аварийный стоп");
        runAll(SERVICES, mc);
        while (keyPip.consumeClick()) Guard.run("window/pip", () -> Pip.toggle(mc));
        runAll(VISUALS, mc);
        showFirstRunScreens(mc);
        if (mc.player == null) {
            tickWithoutPlayer(mc, macro);
            return;
        }
        if (PanicHandoff.blockStarts(mc)) return;
        if (tickFreecamKeys(mc, macro)) return;
        handleKeys(mc, macro);
        tickAutomation(mc, macro);
    }

    private static void runAll(List<Phase> phases, Minecraft mc) {
        for (Phase p : phases) Guard.run(p.name(), () -> p.step().accept(mc));
    }

    /** v1.14.2-pre: мастер первого запуска; v1.16.2: «Что нового» после обновления. Только в главном меню. */
    private static void showFirstRunScreens(Minecraft mc) {
        if (welcomeShown || !(mc.screen instanceof TitleScreen)) return;
        if (!ModConfig.INSTANCE.welcomeDone) {
            welcomeShown = true;
            ModConfig.INSTANCE.lastSeenVersion = WhatsNewScreen.VERSION; // новичку «Что нового» не нужно
            mc.setScreen(new WelcomeScreen(mc.screen));
        } else if (WhatsNewScreen.shouldShow()) {
            welcomeShown = true; // в главном меню макрос точно не играет
            mc.setScreen(new WhatsNewScreen(mc.screen));
        }
    }

    /** Нет игрока (меню, загрузка мира): каждая система сама выключается, ввод не применяется. */
    private static void tickWithoutPlayer(Minecraft mc, MacroManager macro) {
        AutoFish.INSTANCE.stop("нет игрока");
        macro.tickPlayback(mc); // сам остановится без игрока
        AimAssist.INSTANCE.tick(mc);
        MouseHold.INSTANCE.tick(mc); // без игрока — выключить зажим
        MouseLock.tick(mc); // без игрока — снять блокировку мыши
        Freelook.tick(mc);
        LowRender.tick(mc);
        if (macro.getState() == MacroManager.State.IDLE) MacroManager.clearTransient();
        RouteEditor.tick(mc); // сам выключится без игрока
    }

    /** Freecam: работают только выход (End), меню и сам Freecam, остальные нажатия съедаются. */
    private static boolean tickFreecamKeys(Minecraft mc, MacroManager macro) {
        while (keyFreecam.consumeClick()) Freecam.toggle(mc);
        if (!Freecam.active()) return false;
        while (keyResetPos.consumeClick()) {
            Freecam.off(mc);
            macro.resetAll(mc);
        }
        while (keyOpenGui.consumeClick()) {
            Freecam.off(mc);
            mc.setScreen(new FarmMacroScreen(null));
        }
        for (var key : bindings())
            if (key != keyFreecam && key != keyResetPos && key != keyOpenGui) while (key.consumeClick()) {}
        return true;
    }

    private static void handleKeys(Minecraft mc, MacroManager macro) {
        while (keyFishing.consumeClick()) AutoFish.INSTANCE.toggle(mc);
        while (keyPlay.consumeClick()) macro.togglePlayback(mc);
        while (keyStartFrom.consumeClick()) macro.startRouteHere(mc);
        while (keyResume.consumeClick()) macro.resumeFromSaved(mc);
        while (keyResetPos.consumeClick()) macro.resetAll(mc); // End — стоп и сброс всего временного
        while (keyEditor.consumeClick()) RouteEditor.toggle(mc);
        while (keyHold.consumeClick()) MouseHold.INSTANCE.toggleManual(mc);
        while (keyMouseLock.consumeClick()) MouseLock.toggle(mc);
        while (keyFreelook.consumeClick()) {
            if (!ModConfig.INSTANCE.freelookHold) Freelook.toggle(mc);
        }
        Guard.run("camera/freelook", () -> Freelook.tick(mc));
        while (keyLowRender.consumeClick()) LowRender.toggle(mc);
        Guard.run("visual/lowrender", () -> LowRender.tick(mc));
        while (keyUngrab.consumeClick()) Ungrab.toggle(mc);
        while (keyPerf.consumeClick()) PerformanceMode.toggle(mc);
        while (keyHudEdit.consumeClick()) {
            if (macro.isActive()) macro.stopPlayback(mc, "§e■ Остановлено: открыт редактор HUD");
            mc.setScreen(new HudEditorScreen(null));
        }
        Guard.run("editor/tick", () -> RouteEditor.tick(mc));
        while (keyCamApply.consumeClick()) CameraPresets.applySelected(mc);
        while (keyCamNext.consumeClick()) CameraPresets.next(mc);
        while (keyCamSave.consumeClick()) CameraPresets.saveCurrent(mc);
        while (keyOpenGui.consumeClick()) {
            // меню во время игры макроса — штатная остановка, а не паника
            if (macro.isActive()) macro.stopPlayback(mc, "§e■ Остановлено: открыто меню");
            mc.setScreen(new FarmMacroScreen(mc.screen));
        }
    }

    /** Сначала все проверки безопасности, и только потом новый ввод автоматики. Ошибка проверки = стоп. */
    private static void tickAutomation(Minecraft mc, MacroManager macro) {
        if (!Guard.run("panic/automation-safety", () -> AutomationSafety.INSTANCE.tick(mc)))
            PanicDetector.INSTANCE.triggerPanic(mc, "Ошибка аварийной проверки — автоматика остановлена");
        macro.tickRecord(mc);
        if ((AutoFish.INSTANCE.active() || macro.isPlaying())
                && !Guard.run("panic/detectors", () -> PanicDetector.INSTANCE.tick(mc)))
            PanicDetector.INSTANCE.triggerPanic(mc, "Ошибка детекторов — автоматика остановлена");
        if (PanicHandoff.pending()) return;
        if (!Guard.run("combat/aim-tick", () -> AimAssist.INSTANCE.tick(mc)))
            AimAssist.INSTANCE.handoff("ошибка Combat — ручной запуск заново");
        if (PanicHandoff.pending()) return;
        macro.tickPlayback(mc);
        if (!Guard.run("fishing/tick", () -> AutoFish.INSTANCE.tick(mc))) AutoFish.INSTANCE.stop("ошибка рыбалки");
        Guard.run("hold/tick", () -> MouseHold.INSTANCE.tick(mc));
    }

    /**
     * v1.14 «Тишина сервера»: ни одного пакета N с во время макроса — закрываем соединение сами.
     * Дальше это обычный вылет: DISCONNECT → паника «отключение» (автореконнект удалён).
     */
    private static void checkSilence(Minecraft mc) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.panicEnabled || !c.detectSilence || !AutoFish.INSTANCE.active() && !MacroManager.INSTANCE.isPlaying())
            return;
        if (mc.isSingleplayer() || mc.getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (!SilenceWatch.INSTANCE.shouldClose(now, c.silenceSec * 1000L)) return;
        // v1.14.2-pre: через реестр детекторов («Журнал» — только строка, связь не закрываем)
        var a = Detectors.record("silence", "сервер молчит " + c.silenceSec + " с", now);
        if (a == null) return;
        if (a == Detectors.Action.LOG) {
            EventLog.log("DETECT", "Молчание сервера (только журнал): сервер молчит " + c.silenceSec + " с");
            return;
        }
        EventLog.log("SILENCE", "сервер молчит " + c.silenceSec + " с — закрываю соединение");
        LOGGER.warn("Kaelix: сервер молчит {} с — закрываю соединение", c.silenceSec);
        mc.getConnection()
                .getConnection()
                .disconnect(Component.literal("Kaelix: сервер молчит " + c.silenceSec + " с"));
    }
}

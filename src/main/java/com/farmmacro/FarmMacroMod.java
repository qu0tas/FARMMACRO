package com.farmmacro;

import com.farmmacro.camera.CameraPresets;
import com.farmmacro.config.ModConfig;
import com.farmmacro.route.RouteEditor;
import com.farmmacro.gui.FarmMacroScreen;
import com.farmmacro.hud.FarmHud;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.macro.MouseHold;
import com.farmmacro.panic.PanicDetector;
import com.farmmacro.panic.PanicOverlayRenderer;
import com.farmmacro.panic.PanicSound;
import com.farmmacro.util.Guard;
import com.farmmacro.visual.ChinaHatRenderer;
import com.farmmacro.visual.RouteRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FarmMacroMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("farmmacro");

    /** Своя категория в меню управления (с 1.21.9 категория — объект, а не строка). */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("farmmacro", "general"));

    public static KeyMapping keyRecord, keyPlay, keyClear, keyOpenGui, keyResume, keyResetPos,
            keyCamApply, keyCamNext, keyCamSave, keyEditor, keyHold, keyMouseLock,
            keyFreecam, keyFreelook, keyLowRender, keyStartFrom, keyUngrab, keyPerf, keyHudEdit, keyPip, keyPest, keyAim, keyTakeControl, keyPestPause, keyPestFinish, keyPestTraps, keyLoadout, keyVisitors, keyStashSale;

    /** Single catalog used by the in-mod editor and pending-panic start gate. */
    public static java.util.List<KeyMapping> bindings(){
        return java.util.stream.Stream.of(keyRecord,keyPlay,keyClear,keyOpenGui,keyResume,keyResetPos,keyCamApply,keyCamNext,
            keyCamSave,keyEditor,keyHold,keyMouseLock,keyFreecam,keyFreelook,keyLowRender,keyStartFrom,keyUngrab,keyPerf,keyHudEdit,
            keyPip,keyPest,keyAim,keyTakeControl,keyPestPause,keyPestFinish,keyPestTraps,keyLoadout,keyVisitors,keyStashSale).filter(java.util.Objects::nonNull).toList();
    }

    private static KeyMapping makeKey(String id, int defaultCode) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(id, InputConstants.Type.KEYSYM, defaultCode, CATEGORY));
    }

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        com.farmmacro.notify.DiscordNotify.configured(); // Load local secret redaction before any portable export. No network.
        com.farmmacro.stats.StatsLedger.init();
        com.farmmacro.loading.LoadingOptions.clientReady();
        PanicSound.init();
        ModConfig cfg = ModConfig.INSTANCE;
        if(cfg.customUiImages)com.farmmacro.gui.CustomUiAssets.reload();

        FarmHud.register();
        com.farmmacro.fun.Foxy.register();
        com.farmmacro.fun.CropFever.register();
        PanicOverlayRenderer.register();
        ChinaHatRenderer.register();
        com.farmmacro.combat.MobHighlightRenderer.register();
        com.farmmacro.visual.CheeksLayer.register();
        RouteRenderer.register();

        keyPlay     = makeKey("key.farmmacro.play",     cfg.keyPlay);
        keyOpenGui  = makeKey("key.farmmacro.gui",      cfg.keyOpenGui);
        keyResume   = makeKey("key.farmmacro.resume",   79);   // O
        keyResetPos = makeKey("key.farmmacro.resetpos", 269);  // End
        keyCamApply = makeKey("key.farmmacro.cam_apply", 75);  // K
        keyCamNext  = makeKey("key.farmmacro.cam_next",  74);  // J
        keyCamSave  = makeKey("key.farmmacro.cam_save",  72);  // H
        keyEditor   = makeKey("key.farmmacro.editor",    66);  // B
        keyHold     = makeKey("key.farmmacro.hold",      76);  // L
        keyMouseLock = makeKey("key.farmmacro.mouselock", 77); // M
        keyFreelook  = makeKey("key.farmmacro.freelook", 295);  // F6 (v1.12)
        keyLowRender = makeKey("key.farmmacro.lowrender", 296); // F7 (v1.12)
        keyFreecam   = makeKey("key.farmmacro.freecam", -1); // assign inside GUI
        keyUngrab    = makeKey("key.farmmacro.ungrab", 85);     // U (v1.13.1) — отпустить курсор
        keyPerf      = makeKey("key.farmmacro.perf", 298);      // F9 (v1.13.1) — режим производительности
        keyHudEdit   = makeKey("key.farmmacro.hudedit", 297);   // F8 (v1.14) — редактор HUD
        keyPip       = makeKey("key.farmmacro.pip", -1);        // v1.16.3 — картинка в картинке (не назначена)
        keyAim=makeKey("key.farmmacro.aim",301); // F12: hold/toggle center lock
        keyTakeControl=makeKey("key.farmmacro.take_control",66); // B: acknowledgement only, never resume

        ClientTickEvents.END_CLIENT_TICK.register(FarmMacroMod::onTick);
        com.farmmacro.route.ChunkCache.register();
        // v1.13: отключение от сервера во время макроса — паника; новый сервер — сброс замера лагов
        // v1.13.1: при переподключении — не паника, а повторный вход (Reconnect)
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            com.farmmacro.market.StashSales.INSTANCE.cancel(client,"отключение");
            com.farmmacro.visitor.Visitors.INSTANCE.cancel(client,"отключение");
            com.farmmacro.loadout.GlobalLoadouts.INSTANCE.cancel(client,"отключение");
            Guard.run("combat/disconnect",()->com.farmmacro.combat.AimAssist.INSTANCE.handoff("отключение"));
            // Keep a pending panic until explicit B/GUI acknowledgement, including across disconnect.
            com.farmmacro.panic.AutomationSafety.INSTANCE.clear();
            com.farmmacro.fun.CropFever.reset(client);
            boolean playing = MacroManager.INSTANCE.playingOrJustLost()
                    && (client.screen == null || client.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
            Guard.run("panic/disconnect", () -> PanicDetector.INSTANCE.onDisconnect(client, playing));
            com.farmmacro.panic.SilenceWatch.INSTANCE.reset();
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            com.farmmacro.panic.LagGuard.INSTANCE.reset();
            com.farmmacro.panic.SilenceWatch.INSTANCE.onJoin(System.currentTimeMillis());
        });
        LOGGER.info("FarmMacro загружен");
    }

    private static boolean welcomeShown;

    private static void onTick(Minecraft mc) {
        com.farmmacro.util.LoadedEntities.beginTick();if(mc.level==null||mc.player==null)com.farmmacro.hud.StatsHud.clear();
        try { tickBody(mc); }
        finally { Guard.run("diagnostics/tick",()->com.farmmacro.util.Diagnostics.tick(mc)); }
    }
    private static void tickBody(Minecraft mc) {
        MacroManager macro = MacroManager.INSTANCE;
        Guard.run("window/background",()->com.farmmacro.window.BackgroundWork.tick(mc));
        Guard.run("camera/freecam",()->com.farmmacro.camera.Freecam.tick(mc));
        Guard.run("stats/tick",()->com.farmmacro.stats.Telemetry.tick(mc));
        Guard.run("route/cache",() -> com.farmmacro.route.ChunkCache.tick(mc));
        Guard.run("panic/effects", PanicDetector.INSTANCE::tickEffects);
        Guard.run("panic/banwave", com.farmmacro.panic.BanwaveWatch::tick);
        Guard.run("fun/foxy", () -> com.farmmacro.fun.Foxy.tick(mc));
        Guard.run("fun/crop_fever",()->com.farmmacro.fun.CropFever.tick(mc));
        Guard.run("visual/perf", () -> com.farmmacro.visual.PerformanceMode.tick(mc));
        Guard.run("camera/ungrab", () -> com.farmmacro.camera.Ungrab.tick(mc));
        Guard.run("panic/always", () -> PanicDetector.INSTANCE.tickAlways(mc));
        Guard.run("panic/silence", () -> checkSilence(mc));
        // v1.16.3: картинка в картинке (и в меню), скрытие ника, Squeaky Mousemat, человеческая реакция
        while (keyPip.consumeClick()) Guard.run("window/pip", () -> com.farmmacro.window.Pip.toggle(mc));
        Guard.run("window/pip-tick", () -> com.farmmacro.window.Pip.tick(mc));
        Guard.run("nick/tick", () -> com.farmmacro.visual.NickHide.tick(mc));
        Guard.run("camera/mousemat", () -> com.farmmacro.camera.Mousemat.tick(mc));
        Guard.run("panic/react-tick", () -> com.farmmacro.panic.HumanReaction.tick(mc));
        // v1.14.2-pre: мастер первого запуска — один раз, в главном меню
        if (!ModConfig.INSTANCE.welcomeDone && !welcomeShown && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            welcomeShown = true;
            ModConfig.INSTANCE.lastSeenVersion = com.farmmacro.gui.WhatsNewScreen.VERSION;   // новичку «Что нового» не нужно
            mc.setScreen(new com.farmmacro.gui.WelcomeScreen(mc.screen));
        } else if (!welcomeShown && com.farmmacro.gui.WhatsNewScreen.shouldShow() && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            // v1.16.2: после обновления — один раз «Что нового» (в главном меню, не в игре: макрос точно не играет)
            welcomeShown = true;
            mc.setScreen(new com.farmmacro.gui.WhatsNewScreen(mc.screen));
        }
        if (mc.player == null) {
            com.farmmacro.market.StashSales.INSTANCE.cancel(mc,"нет игрока");
            com.farmmacro.visitor.Visitors.INSTANCE.cancel(mc,"нет игрока");
            com.farmmacro.loadout.GlobalLoadouts.INSTANCE.cancel(mc,"нет игрока");
            com.farmmacro.pest.PestHunter.INSTANCE.abort(mc,"нет игрока",false);
            macro.tickPlayback(mc);          // сам остановится без игрока
            com.farmmacro.combat.AimAssist.INSTANCE.tick(mc);
            MouseHold.INSTANCE.tick(mc);     // без игрока — выключить зажим
            com.farmmacro.camera.MouseLock.tick(mc); // без игрока — снять блокировку мыши
            com.farmmacro.camera.Freelook.tick(mc);
            com.farmmacro.visual.LowRender.tick(mc);
            if (macro.getState() == MacroManager.State.IDLE) MacroManager.clearTransient();
            RouteEditor.tick(mc);            // сам выключится без игрока
            return;
        }

        if(com.farmmacro.panic.PanicHandoff.blockStarts(mc))return;
        while(keyFreecam.consumeClick())com.farmmacro.camera.Freecam.toggle(mc);
        if(com.farmmacro.camera.Freecam.active()){
            while(keyResetPos.consumeClick()) {com.farmmacro.camera.Freecam.off(mc);macro.resetAll(mc);}
            while(keyOpenGui.consumeClick()){com.farmmacro.camera.Freecam.off(mc);mc.setScreen(new FarmMacroScreen(null));}
            for(var key:bindings())if(key!=keyFreecam&&key!=keyResetPos&&key!=keyOpenGui)while(key.consumeClick()){}
            return;
        }
        while(keyTakeControl.consumeClick()){} // no action outside a pending panic
        while (keyPlay.consumeClick()) {com.farmmacro.pest.PestHunter.INSTANCE.abort(mc,"запуск макроса",false);macro.togglePlayback(mc);}
        while (keyResume.consumeClick())   macro.resumeFromSaved(mc);
        while (keyResetPos.consumeClick()) macro.resetAll(mc);   // End — стоп и сброс всего временного
        while (keyEditor.consumeClick())   RouteEditor.toggle(mc);
        while (keyHold.consumeClick())     MouseHold.INSTANCE.toggleManual(mc);
        while (keyMouseLock.consumeClick()) com.farmmacro.camera.MouseLock.toggle(mc);
        while (keyFreelook.consumeClick())  { if (!ModConfig.INSTANCE.freelookHold) com.farmmacro.camera.Freelook.toggle(mc); }
        Guard.run("camera/freelook", () -> com.farmmacro.camera.Freelook.tick(mc));
        while (keyLowRender.consumeClick()) com.farmmacro.visual.LowRender.toggle(mc);
        Guard.run("visual/lowrender", () -> com.farmmacro.visual.LowRender.tick(mc));
        while (keyUngrab.consumeClick()) com.farmmacro.camera.Ungrab.toggle(mc);
        while (keyPerf.consumeClick()) com.farmmacro.visual.PerformanceMode.toggle(mc);
        while (keyHudEdit.consumeClick()) {
            if (macro.isActive()) macro.stopPlayback(mc, "§e■ Остановлено: открыт редактор HUD");
            mc.setScreen(new com.farmmacro.hud.HudEditorScreen(null));
        }
        Guard.run("editor/tick", () -> RouteEditor.tick(mc));
        while (keyCamApply.consumeClick()) CameraPresets.applySelected(mc);
        while (keyCamNext.consumeClick())  CameraPresets.next(mc);
        while (keyCamSave.consumeClick())  CameraPresets.saveCurrent(mc);
        while (keyOpenGui.consumeClick()) {
            // меню во время игры макроса — штатная остановка, а не паника
            if (macro.isActive()) macro.stopPlayback(mc, "§e■ Остановлено: открыто меню");
            mc.setScreen(new FarmMacroScreen(mc.screen));
        }

        if(!Guard.run("panic/automation-safety",() -> com.farmmacro.panic.AutomationSafety.INSTANCE.tick(mc)))
            PanicDetector.INSTANCE.triggerPanic(mc,"Ошибка аварийной проверки — автоматика остановлена");
        macro.tickRecord(mc);
        // Read pre-action state for both farm and pests. Never apply their next input before pending alarms.
        if ((macro.isPlaying()||com.farmmacro.pest.PestHunter.INSTANCE.active()||com.farmmacro.visitor.Visitors.INSTANCE.active()||com.farmmacro.market.StashSales.INSTANCE.active()||com.farmmacro.macro.Scheduler.isBreak())
            &&!Guard.run("panic/detectors",()->PanicDetector.INSTANCE.tick(mc)))
            PanicDetector.INSTANCE.triggerPanic(mc,"Ошибка детекторов — автоматика остановлена");
        if(com.farmmacro.panic.PanicHandoff.pending())return;
        if(!Guard.run("combat/aim-tick",() -> com.farmmacro.combat.AimAssist.INSTANCE.tick(mc)))
            com.farmmacro.combat.AimAssist.INSTANCE.handoff("ошибка Combat — ручной запуск заново");
        if(com.farmmacro.panic.PanicHandoff.pending())return;
        macro.tickPlayback(mc);
        com.farmmacro.window.BackgroundWork.tick(mc);
        Guard.run("hold/tick", () -> MouseHold.INSTANCE.tick(mc));
    }

    /**
     * v1.14 «Тишина сервера»: ни одного пакета N с во время макроса — закрываем соединение сами.
     * Дальше это обычный вылет: DISCONNECT → Reconnect (если включён) или паника «отключение».
     */
    private static void checkSilence(Minecraft mc) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.panicEnabled || !c.detectSilence || !MacroManager.INSTANCE.isPlaying() && !com.farmmacro.pest.PestHunter.INSTANCE.active() && !com.farmmacro.visitor.Visitors.INSTANCE.active() && !com.farmmacro.market.StashSales.INSTANCE.active()) return;
        if (mc.isSingleplayer() || mc.getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (!com.farmmacro.panic.SilenceWatch.INSTANCE.shouldClose(now, c.silenceSec * 1000L)) return;
        // v1.14.2-pre: через реестр детекторов («Журнал» — только строка, связь не закрываем)
        var a = com.farmmacro.panic.Detectors.record("silence", "сервер молчит " + c.silenceSec + " с", now);
        if (a == null) return;
        if (a == com.farmmacro.panic.Detectors.Action.LOG) {
            com.farmmacro.panic.EventLog.log("DETECT", "Молчание сервера (только журнал): сервер молчит " + c.silenceSec + " с");
            return;
        }
        com.farmmacro.panic.EventLog.log("SILENCE", "сервер молчит " + c.silenceSec + " с — закрываю соединение");
        LOGGER.warn("FarmMacro: сервер молчит {} с — закрываю соединение", c.silenceSec);
        mc.getConnection().getConnection().disconnect(
                net.minecraft.network.chat.Component.literal("Kaelix: сервер молчит " + c.silenceSec + " с"));
    }
}

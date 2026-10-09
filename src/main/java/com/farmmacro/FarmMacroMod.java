package com.farmmacro;

import com.farmmacro.camera.CameraPresets;
import com.farmmacro.config.ModConfig;
import com.farmmacro.route.RouteEditor;
import com.farmmacro.gui.FarmMacroScreen;
import com.farmmacro.hud.FarmHud;
import com.farmmacro.macro.MacroManager;
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
            keyCamApply, keyCamNext, keyCamSave, keyEditor;

    private static KeyMapping makeKey(String id, int defaultCode) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(id, InputConstants.Type.KEYSYM, defaultCode, CATEGORY));
    }

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        PanicSound.init();
        ModConfig cfg = ModConfig.INSTANCE;

        FarmHud.register();
        PanicOverlayRenderer.register();
        ChinaHatRenderer.register();
        RouteRenderer.register();

        keyRecord   = makeKey("key.farmmacro.record",   cfg.keyRecord);
        keyPlay     = makeKey("key.farmmacro.play",     cfg.keyPlay);
        keyClear    = makeKey("key.farmmacro.clear",    cfg.keyClear);
        keyOpenGui  = makeKey("key.farmmacro.gui",      cfg.keyOpenGui);
        keyResume   = makeKey("key.farmmacro.resume",   79);   // O
        keyResetPos = makeKey("key.farmmacro.resetpos", 269);  // End
        keyCamApply = makeKey("key.farmmacro.cam_apply", 75);  // K
        keyCamNext  = makeKey("key.farmmacro.cam_next",  74);  // J
        keyCamSave  = makeKey("key.farmmacro.cam_save",  72);  // H
        keyEditor   = makeKey("key.farmmacro.editor",    66);  // B

        ClientTickEvents.END_CLIENT_TICK.register(FarmMacroMod::onTick);
        LOGGER.info("FarmMacro загружен");
    }

    private static void onTick(Minecraft mc) {
        MacroManager macro = MacroManager.INSTANCE;
        Guard.run("panic/effects", PanicDetector.INSTANCE::tickEffects);
        if (mc.player == null) {
            macro.tickPlayback(mc);          // сам остановится без игрока
            RouteEditor.tick(mc);            // сам выключится без игрока
            return;
        }

        while (keyRecord.consumeClick())   macro.toggleRecording(mc);
        while (keyPlay.consumeClick())     macro.togglePlayback(mc);
        while (keyClear.consumeClick())    macro.clearRecording(mc);
        while (keyResume.consumeClick())   macro.resumeFromSaved(mc);
        while (keyResetPos.consumeClick()) {
            macro.clearSavedPosition();
            mc.player.sendOverlayMessage(Component.literal("§8[§cFM§8] §7Точка остановки сброшена"));
        }
        while (keyEditor.consumeClick())   RouteEditor.toggle(mc);
        Guard.run("editor/tick", () -> RouteEditor.tick(mc));
        while (keyCamApply.consumeClick()) CameraPresets.applySelected(mc);
        while (keyCamNext.consumeClick())  CameraPresets.next(mc);
        while (keyCamSave.consumeClick())  CameraPresets.saveCurrent(mc);
        while (keyOpenGui.consumeClick()) {
            // меню во время игры макроса — штатная остановка, а не паника
            if (macro.isActive()) macro.stopPlayback(mc, "§e■ Остановлено: открыто меню");
            mc.setScreen(new FarmMacroScreen(mc.screen));
        }

        macro.tickRecord(mc);
        // Сначала детекторы (по состоянию ДО нового кадра), потом сам кадр.
        if (macro.isPlaying() && !Guard.run("panic/detectors", () -> PanicDetector.INSTANCE.tick(mc))) {
            // детекторы сломались — безопаснее остановить макрос, чем играть вслепую
            Guard.run("panic/stop-on-error", () -> macro.stopPlayback(mc, "§c⚠ Ошибка детекторов, макрос остановлен"));
        }
        macro.tickPlayback(mc);
    }
}

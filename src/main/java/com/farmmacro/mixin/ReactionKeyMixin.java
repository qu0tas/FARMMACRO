package com.farmmacro.mixin;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.camera.Freecam;
import com.farmmacro.camera.SmoothTurn;
import com.farmmacro.combat.AimAssist;
import com.farmmacro.combat.CombatSway;
import com.farmmacro.combat.SpecialZealotHunt;
import com.farmmacro.fishing.AutoFish;
import com.farmmacro.panic.AutomationSafety;
import com.farmmacro.panic.PanicDetector;
import com.farmmacro.route.FarmRuntime;
import com.farmmacro.route.NavigationInput;
import com.farmmacro.util.Guard;
import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ручное нажатие клавиши игроком: снимает отложенную остановку и отдаёт управление человеку
 * (javap 26.1.2: keyPress(JILKeyEvent;)). Ошибки не глотаются молча — пишутся в журнал через {@link Guard}.
 */
@Mixin(KeyboardHandler.class)
public class ReactionKeyMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void farmmacro$reactKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (action == 1) PanicDetector.INSTANCE.finishDelayed(mc);
            if (Freecam.active()) return;
            AutomationSafety.INSTANCE.manualKey(mc, window, action, event);
            CombatSway.INSTANCE.onKey(mc, action, event);
            if (action != 1) return;

            if (AutoFish.INSTANCE.active()
                    && (mc.options.keyUse.matches(event)
                            || mc.options.keyAttack.matches(event)
                            || Arrays.stream(mc.options.keyHotbarSlots).anyMatch(k -> k.matches(event))))
                AutoFish.INSTANCE.userInput();

            boolean aimKey = farmmacro$matches(FarmMacroMod.keyAim, event);
            if (mc.screen == null && mc.options != null && !aimKey && farmmacro$isMovementKey(mc, event)) {
                SmoothTurn.bindingUserInput();
                FarmRuntime.userInput();
                NavigationInput.userInput();
                if (!AimAssist.manualMode()) AimAssist.INSTANCE.handoff("ручное движение");
            }
            if (!aimKey) SpecialZealotHunt.INSTANCE.userInput();
        } catch (Throwable t) {
            Guard.report("mixin/key-input", t);
        }
    }

    private static boolean farmmacro$matches(KeyMapping key, KeyEvent event) {
        return key != null && key.matches(event);
    }

    private static boolean farmmacro$isMovementKey(Minecraft mc, KeyEvent e) {
        var o = mc.options;
        return o.keyUp.matches(e)
                || o.keyDown.matches(e)
                || o.keyLeft.matches(e)
                || o.keyRight.matches(e)
                || o.keyShift.matches(e)
                || o.keyJump.matches(e)
                || o.keySprint.matches(e);
    }
}

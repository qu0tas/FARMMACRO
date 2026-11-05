package com.farmmacro.mixin;

import com.farmmacro.FarmMacroMod;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ручной ввод мышью: движение, колесо и кнопки отдают управление игроку
 * (javap 26.1.2: onMove(JDD), onScroll(JDD), onButton(JMouseButtonInfo;I)).
 */
@Mixin(MouseHandler.class)
public class ReactionInputMixin {

    @Inject(method = "onMove", at = @At("HEAD"))
    private void farmmacro$reactMove(long window, double x, double y, CallbackInfo ci) {
        try {
            PanicDetector.INSTANCE.stopMouseMove(Minecraft.getInstance(), x, y);
            SmoothTurn.bindingMouseMove(x, y);
            FarmRuntime.onMouseMove(x, y);
            NavigationInput.onMouseMove(x, y);
            AimAssist.INSTANCE.onMouseMove(x, y);
        } catch (Throwable t) {
            Guard.report("mixin/mouse-move", t);
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void farmmacro$scrollBegin(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        boolean scrolled = horizontal != 0 || vertical != 0;
        if (scrolled) PanicDetector.INSTANCE.finishDelayed(mc);
        AutomationSafety.INSTANCE.manualScrollBegin(mc, window);
        if (scrolled) {
            AutoFish.INSTANCE.userInput();
            SmoothTurn.bindingUserInput();
        }
    }

    @Redirect(
            method = "onScroll",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void farmmacro$manualScrollSlot(Inventory inventory, int slot) {
        AutomationSafety.INSTANCE.selectLocalSlot(inventory, slot, true);
    }

    @Inject(method = "onScroll", at = @At("RETURN"))
    private void farmmacro$manualScrollDone(long window, double horizontal, double vertical, CallbackInfo ci) {
        AutomationSafety.INSTANCE.manualScrollEnd();
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void farmmacro$reactButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (action == 1) PanicDetector.INSTANCE.finishDelayed(mc);
            AutomationSafety.INSTANCE.manualMouseKey(mc, window, action, info);
            if (action != 1) return;

            MouseButtonEvent event = new MouseButtonEvent(0, 0, info);
            if (!farmmacro$matches(FarmMacroMod.keyFishing, event)) AutoFish.INSTANCE.userInput();
            if (!farmmacro$matches(FarmMacroMod.keyPlay, event)) SmoothTurn.bindingUserInput();
            if (mc.screen == null) FarmRuntime.userInput();
            NavigationInput.userInput();
            CombatSway.INSTANCE.onButton();
            SpecialZealotHunt.INSTANCE.userInput();
        } catch (Throwable t) {
            Guard.report("mixin/mouse-button", t);
        }
    }

    private static boolean farmmacro$matches(KeyMapping key, MouseButtonEvent event) {
        return key != null && key.matchesMouse(event);
    }
}

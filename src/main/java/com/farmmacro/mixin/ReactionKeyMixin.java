package com.farmmacro.mixin;

import com.farmmacro.panic.HumanReaction;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** v1.16.3: нажатие клавиши игроком прекращает «человеческую реакцию» (javap 26.1.2: keyPress(JILKeyEvent;)). */
@Mixin(KeyboardHandler.class)
public class ReactionKeyMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void farmmacro$reactKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        try { var mc=net.minecraft.client.Minecraft.getInstance();
            if(com.farmmacro.panic.PanicHandoff.onKey(mc,action,event)){ci.cancel();return;}
            if(com.farmmacro.camera.Freecam.active())return;
            com.farmmacro.panic.AutomationSafety.INSTANCE.manualKey(mc,window,action,event);
            com.farmmacro.combat.CombatSway.INSTANCE.onKey(mc,action,event);
            if (action == 1) {
            com.farmmacro.macro.Scheduler.manualInput();
            if(com.farmmacro.FarmMacroMod.keyStashSale==null||!com.farmmacro.FarmMacroMod.keyStashSale.matches(event))
                com.farmmacro.market.StashSales.INSTANCE.userInput();
            if(com.farmmacro.FarmMacroMod.keyVisitors==null||!com.farmmacro.FarmMacroMod.keyVisitors.matches(event))
                com.farmmacro.visitor.Visitors.INSTANCE.userInput();
            if(com.farmmacro.FarmMacroMod.keyLoadout==null||!com.farmmacro.FarmMacroMod.keyLoadout.matches(event))
                com.farmmacro.loadout.GlobalLoadouts.INSTANCE.userInput();
            if(mc.screen==null&&mc.options!=null&&(com.farmmacro.FarmMacroMod.keyAim==null||!com.farmmacro.FarmMacroMod.keyAim.matches(event))
                &&(mc.options.keyUp.matches(event)||mc.options.keyDown.matches(event)||mc.options.keyLeft.matches(event)
                ||mc.options.keyRight.matches(event)||mc.options.keyShift.matches(event)||mc.options.keyJump.matches(event)
                ||mc.options.keySprint.matches(event)))
                {com.farmmacro.camera.SmoothTurn.bindingUserInput();com.farmmacro.route.FarmRuntime.userInput();com.farmmacro.route.NavigationInput.userInput();
                if(!com.farmmacro.combat.AimAssist.manualMode())com.farmmacro.combat.AimAssist.INSTANCE.handoff("ручное движение");}
            HumanReaction.onUserInput("клавиша");
            if(com.farmmacro.FarmMacroMod.keyAim==null||!com.farmmacro.FarmMacroMod.keyAim.matches(event))com.farmmacro.combat.SpecialZealotHunt.INSTANCE.userInput();
            if(!com.farmmacro.pest.PestHunter.INSTANCE.controlKey(event)&&(com.farmmacro.FarmMacroMod.keyPest==null||!com.farmmacro.FarmMacroMod.keyPest.matches(event)))com.farmmacro.pest.PestHunter.INSTANCE.userInput();
        } } catch (Throwable ignored) {}
    }
}

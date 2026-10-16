package com.farmmacro.mixin;

import com.farmmacro.panic.HumanReaction;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** v1.16.3: любой ввод мыши игрока прекращает «человеческую реакцию» (javap 26.1.2: onMove(JDD), onButton(JMouseButtonInfo;I)). */
@Mixin(MouseHandler.class)
public class ReactionInputMixin {

    @Inject(method = "onMove", at = @At("HEAD"))
    private void farmmacro$reactMove(long window, double x, double y, CallbackInfo ci) {
        try { com.farmmacro.camera.SmoothTurn.bindingMouseMove(x,y);com.farmmacro.macro.Scheduler.mouseMove(x,y);com.farmmacro.route.FarmRuntime.onMouseMove(x,y);com.farmmacro.route.NavigationInput.onMouseMove(x,y);com.farmmacro.combat.AimAssist.INSTANCE.onMouseMove(x,y);HumanReaction.onMouseMove(x, y);com.farmmacro.pest.PestHunter.INSTANCE.onMouseMove(x,y); } catch (Throwable ignored) {}
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void farmmacro$loadoutScroll(long window,double horizontal,double vertical,CallbackInfo ci){
        com.farmmacro.panic.AutomationSafety.INSTANCE.manualScrollBegin(net.minecraft.client.Minecraft.getInstance(),window);
        if(horizontal!=0||vertical!=0){com.farmmacro.camera.SmoothTurn.bindingUserInput();com.farmmacro.macro.Scheduler.manualInput();com.farmmacro.market.StashSales.INSTANCE.userInput();com.farmmacro.visitor.Visitors.INSTANCE.userInput();com.farmmacro.loadout.GlobalLoadouts.INSTANCE.userInput();}
    }

    @Redirect(method="onScroll",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void farmmacro$manualScrollSlot(net.minecraft.world.entity.player.Inventory inventory,int slot){
        com.farmmacro.panic.AutomationSafety.INSTANCE.selectLocalSlot(inventory,slot,true);
    }
    @Inject(method="onScroll",at=@At("RETURN"))
    private void farmmacro$manualScrollDone(long window,double horizontal,double vertical,CallbackInfo ci){
        com.farmmacro.panic.AutomationSafety.INSTANCE.manualScrollEnd();
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void farmmacro$reactButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        try {
            var mc=net.minecraft.client.Minecraft.getInstance();
            if(com.farmmacro.panic.PanicHandoff.onButton(mc,action,info)){ci.cancel();return;}
            com.farmmacro.panic.AutomationSafety.INSTANCE.manualMouseKey(mc,window,action,info);
            if (action == 1) {
                if(com.farmmacro.FarmMacroMod.keyPlay==null||!com.farmmacro.FarmMacroMod.keyPlay.matchesMouse(new net.minecraft.client.input.MouseButtonEvent(0,0,info)))
                    com.farmmacro.camera.SmoothTurn.bindingUserInput();
                com.farmmacro.macro.Scheduler.manualInput();
                com.farmmacro.market.StashSales.INSTANCE.userInput();com.farmmacro.visitor.Visitors.INSTANCE.userInput();
                com.farmmacro.loadout.GlobalLoadouts.INSTANCE.userInput();
                if(mc.screen==null)com.farmmacro.route.FarmRuntime.userInput();
                com.farmmacro.route.NavigationInput.userInput();
                HumanReaction.onUserInput("кнопка мыши");
                com.farmmacro.combat.CombatSway.INSTANCE.onButton();
                com.farmmacro.combat.SpecialZealotHunt.INSTANCE.userInput();
                com.farmmacro.pest.PestHunter.INSTANCE.userInput();
            }
        } catch (Throwable ignored) {}
    }
}

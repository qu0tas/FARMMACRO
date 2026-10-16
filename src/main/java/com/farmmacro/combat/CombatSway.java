package com.farmmacro.combat;
import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import java.util.concurrent.ThreadLocalRandom;
/** Explicit opt-in raw Shift + randomized A/D. No terrain, support or drift gates. */
public final class CombatSway {
    public static final CombatSway INSTANCE=new CombatSway();
    private boolean owned,manualBlocked,physicalLeft,physicalRight,physicalShift,crouchOwned,pausedHold;
    private net.minecraft.client.multiplayer.ClientLevel holdWorld;
    public boolean holdingCrouch(){return crouchOwned;}
    private boolean canHold(Minecraft mc){return mc!=null&&mc.player!=null&&mc.level==holdWorld&&mc.player.isAlive()
        &&ModConfig.INSTANCE.aimAssistEnabled&&ModConfig.INSTANCE.aimWalkInPlace&&AimAssist.INSTANCE.contextFree(mc)
        &&!SpecialZealotHunt.INSTANCE.active()&&!SpecialZealotHunt.INSTANCE.pending();}
    /** Called immediately before vanilla samples input, not only at end-of-tick. */
    public boolean maintainInput(Minecraft mc){
        if(AimAssist.manualMode())return false;
        if(!crouchOwned)return false;
        if(!canHold(mc)||!pausedHold&&(!owned||!AimAssist.INSTANCE.keyActive()||manualBlocked)){stop(mc,"управление закончено");return false;}
        crouch(mc,true);return true;
    }
    public void stopWhenInactive(Minecraft mc,String why){if(pausedHold&&maintainInput(mc))return;stop(mc,why);}
    /** Release lateral movement but retain crouch until a deliberate handoff/rearm. */
    public void pauseForHandoff(Minecraft mc,String why){
        if(!owned&&!crouchOwned)return;
        mc.options.keyLeft.setDown(physicalLeft);mc.options.keyRight.setDown(physicalRight);
        owned=false;side=phaseTicks=0;manualBlocked=true;pausedHold=true;
        if(!maintainInput(mc))return;status="A/D остановлены: "+why+"; Shift удерживается до ручного ввода/передачи управления";
    }
    public void userTakeover(Minecraft mc){if(owned||crouchOwned){stop(mc,"ручной перехват");manualBlocked=true;}}
    private static String aimKey(){return com.farmmacro.FarmMacroMod.keyAim==null?"Aim":com.farmmacro.macro.MacroManager.keyName(com.farmmacro.FarmMacroMod.keyAim);}
    private boolean priorCrouch;
    private int side,lastSide=-1,phaseTicks;
    private String status="Ходить на месте: выключено";
    public boolean active(){return owned;}
    public String status(){return status;}
    public void onKey(Minecraft mc,int action,KeyEvent event){
        if(AimAssist.manualMode())return;
        if(mc.options==null)return;boolean down=action!=0;
        if(mc.options.keyLeft.matches(event))physicalLeft=down;
        if(mc.options.keyRight.matches(event))physicalRight=down;
        if(mc.options.keyShift.matches(event))physicalShift=down;
        if(action==1&&(owned||crouchOwned)&&(mc.options.keyUp.matches(event)||mc.options.keyDown.matches(event)||mc.options.keyLeft.matches(event)
            ||mc.options.keyRight.matches(event)||mc.options.keyShift.matches(event)||mc.options.keyJump.matches(event)||mc.options.keySprint.matches(event))){
            boolean shiftToggle=mc.options.keyShift.matches(event)&&mc.options.toggleCrouch().get();
            boolean before=mc.options.keyShift.isDown();
            stop(mc,"ручное движение — до нового включения "+aimKey());manualBlocked=true;
            // Preserve pre-event toggle state: vanilla will process this physical press next.
            if(shiftToggle)crouch(mc,before);
        }
    }
    public void onButton(){if(owned||crouchOwned){stop(Minecraft.getInstance(),"ручная кнопка мыши — до нового "+aimKey());manualBlocked=true;AimAssist.INSTANCE.handoff("ручная кнопка мыши");}}
    private void crouch(Minecraft mc,boolean wanted){
        var key=mc.options.keyShift;if(key.isDown()==wanted)return;
        // ToggleKeyMapping.setDown(true) toggles, and false is ignored in toggle mode.
        key.setDown(mc.options.toggleCrouch().get()?true:wanted);
    }
    public void stop(Minecraft mc,String why){
        if((owned||crouchOwned)&&mc!=null&&mc.options!=null){
            boolean valid=mc.player!=null&&mc.player.isAlive()&&mc.level==holdWorld&&mc.screen==null&&mc.isWindowActive();
            mc.options.keyLeft.setDown(valid&&physicalLeft);mc.options.keyRight.setDown(valid&&physicalRight);
            crouch(mc,valid&&(mc.options.toggleCrouch().get()?priorCrouch:physicalShift));
            if(!valid)physicalLeft=physicalRight=physicalShift=false;
        }
        owned=crouchOwned=pausedHold=false;holdWorld=null;side=phaseTicks=0;status="Ходить на месте: "+why;
    }
    public void tick(Minecraft mc,boolean allowed,boolean keyActive){
        var c=ModConfig.INSTANCE;
        if(pausedHold){
            if(!canHold(mc)){stop(mc,"окно/мир/контекст изменился");return;}
            if(!keyActive){maintainInput(mc);status="A/D на паузе; Shift удерживается — "+aimKey()+" заново или ручной ввод";return;}
            pausedHold=false;manualBlocked=false;
        }
        if(!keyActive)manualBlocked=false;
        if(!c.aimWalkInPlace||!allowed||mc.player==null||mc.level==null||!com.farmmacro.motion.MotionController.allowed(com.farmmacro.motion.MotionPolicy.Owner.COMBAT)){stop(mc,c.aimWalkInPlace?"пауза управления":"выключено");return;}
        if(manualBlocked){status="Ходить на месте: остановлено до повторного включения "+aimKey();return;}
        if(!owned){
            // Existing crouch is allowed; do not reject the player's initial Shift state.
            if(!crouchOwned)priorCrouch=mc.options.keyShift.isDown();
            physicalLeft=mc.options.keyLeft.isDown();physicalRight=mc.options.keyRight.isDown();
            if(!crouchOwned&&!mc.options.toggleCrouch().get())physicalShift=priorCrouch;
            owned=crouchOwned=true;holdWorld=mc.level;phaseTicks=0;side=0;
        }
        var random=ThreadLocalRandom.current();
        if(--phaseTicks<=0){
            if(side!=0){lastSide=side;side=0;phaseTicks=random.nextDouble()<.12?random.nextInt(5,10):random.nextInt(1,4);}
            else {
                side=random.nextDouble()<.78?-lastSide:lastSide;
                phaseTicks=random.nextInt(2,6); // 100–250ms on the nominal client tick rate.
            }
        }
        crouch(mc,true);
        mc.options.keyLeft.setDown(side<0);mc.options.keyRight.setDown(side>0);
        status=side==0?"Ходить на месте: Shift + случайная пауза (без проверок местности)":"Ходить на месте: Shift + "+(side<0?"A":"D")+" (без проверок местности)";
    }
    private CombatSway(){}
}

package com.farmmacro.panic;

import com.farmmacro.combat.*;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.pest.PestHunter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import java.util.Objects;

/** Focused immediate guards, independent of the legacy macro-only detector snapshot. */
public final class AutomationSafety {
    public static final AutomationSafety INSTANCE=new AutomationSafety();
    private record Hand(Item item,String skyId,String uuid,boolean stackable,String label) {}
    private record Identity(Item item,String skyId,String uuid) {}
    private final SlotSwitchIntents<Identity> manualKeys=new SlotSwitchIntents<>(),manualScroll=new SlotSwitchIntents<>();
    public void clearManualInput(){manualKeys.clear();manualScroll.clear();}
    private boolean manualInput(Minecraft mc,long window){
        return AimAssist.manualMode()&&ModConfig.INSTANCE.aimAssistEnabled&&AimAssist.INSTANCE.keyActive()
            &&watching()&&mc.player!=null&&mc.level!=null&&tracking&&world==mc.level&&combatTracking
            &&mc.screen==null&&mc.getOverlay()==null&&!mc.isPaused()&&mc.isWindowActive()
            &&mc.mouseHandler.isMouseGrabbed()&&!mc.player.isSpectator()
            &&!com.farmmacro.camera.Freecam.active()&&mc.getWindow().handle()==window;
    }
    private Identity identity(Hand value){return value==null?null:new Identity(value.item(),value.skyId(),value.uuid());}
    public void manualKey(Minecraft mc,long window,int action,net.minecraft.client.input.KeyEvent event){
        if(action!=1||!manualInput(mc,window))return;
        for(int i=0;i<9;i++)if(mc.options.keyHotbarSlots[i].matches(event))
            manualKeys.arm(i,identity(read(mc.player.getInventory().getItem(i))),System.nanoTime());
    }
    public void manualMouseKey(Minecraft mc,long window,int action,net.minecraft.client.input.MouseButtonInfo info){
        if(action!=1||!manualInput(mc,window))return;
        var event=new net.minecraft.client.input.MouseButtonEvent(0,0,info);
        for(int i=0;i<9;i++)if(mc.options.keyHotbarSlots[i].matchesMouse(event))
            manualKeys.arm(i,identity(read(mc.player.getInventory().getItem(i))),System.nanoTime());
    }
    /** Snapshot before vanilla scroll accumulation/sensitivity selects a slot. */
    public void manualScrollBegin(Minecraft mc,long window){
        manualScroll.clear();if(!manualInput(mc,window))return;
        long now=System.nanoTime();for(int i=0;i<9;i++)manualScroll.arm(i,identity(read(mc.player.getInventory().getItem(i))),now);
    }
    public void manualScrollEnd(){manualScroll.clear();}
    /** Only vanilla local hotbar selection call sites use this, never server packet handlers. */
    public void selectLocalSlot(net.minecraft.world.entity.player.Inventory inventory,int wanted,boolean scroll){
        var mc=Minecraft.getInstance();var intent=scroll?manualScroll:manualKeys;
        boolean accepted=false;
        if(manualInput(mc,mc.getWindow().handle())&&inventory==mc.player.getInventory()&&wanted>=0&&wanted<9){
            // A physical key must not excuse an already changed current hand/slot.
            boolean baseline=inventory.getSelectedSlot()==slot&&Objects.equals(identity(hand),identity(read(mc)));
            accepted=intent.consume(wanted,identity(read(inventory.getItem(wanted))),System.nanoTime())&&baseline;
        }
        inventory.setSelectedSlot(wanted); // exactly the original vanilla action, even when not accepted
        if(accepted)capture(mc);
    }
    private long lastCorrectionLog;
    private final CorrectionWindow corrections=new CorrectionWindow();
    private ClientLevel correctionWorld;
    public void clear(){reset();corrections.reset();correctionWorld=null;}
    /** Deliberate new run, not a server-driven inventory refresh. */
    public void beginRun(){clear();var mc=Minecraft.getInstance();if(mc.player!=null&&mc.level!=null)capture(mc);}
    private ClientLevel world;private boolean tracking,combatTracking;private int slot;private Hand hand;
    public static boolean watching(){return !PanicHandoff.pending()&&CombatPanic.enabled();}
    public static boolean manualHandoff(String id){return CombatPanic.isCombatId(id)||"nearbyChat".equals(id)||"safetyTeleport".equals(id)||"safetyWeapon".equals(id);}
    private void reset(){world=null;tracking=false;hand=null;clearManualInput();}
    private Hand read(Minecraft mc){
        return read(mc.player.getMainHandItem());
    }
    private Hand read(net.minecraft.world.item.ItemStack stack){
        if(stack.isEmpty())return null;
        var data=stack.get(DataComponents.CUSTOM_DATA);String id="",uuid="";
        if(data!=null){var extra=data.copyTag().getCompoundOrEmpty("ExtraAttributes");id=extra.getStringOr("id","");uuid=extra.getStringOr("uuid","");}
        return new Hand(stack.getItem(),id,uuid,stack.isStackable(),ServerText.clean(stack.getHoverName().getString()));
    }
    private void capture(Minecraft mc){world=mc.level;slot=mc.player.getInventory().getSelectedSlot();hand=read(mc);tracking=true;combatTracking=CombatPanic.context();}
    /** Called after a module explicitly selected a new hotbar slot; no grace for unrelated inventory changes. */
    public void expectSlot(int wanted){
        var mc=Minecraft.getInstance();
        if(tracking&&watching()&&mc.player!=null&&mc.level==world&&wanted!=slot&&mc.player.getInventory().getSelectedSlot()==wanted)capture(mc);
    }
    public boolean tick(Minecraft mc){
        if(com.farmmacro.market.MarketTrip.INSTANCE.waiting())return false;
        if(!watching()||mc.player==null||mc.level==null){clear();return false;}
        if(tracking&&combatTracking!=CombatPanic.context())reset();
        if(tracking&&world!=mc.level){
            reset();
            if(CombatPanic.teleport()&&CombatPanic.fire(mc,"safetyTeleport","Во время автоматики сменился мир — управление тебе"))return true;
        }
        // World tracking is independent of the weapon switch. Keep a fresh baseline even with it off.
        if(!CombatPanic.weapon()){capture(mc);return false;}
        if(!tracking){capture(mc);return false;}
        int currentSlot=mc.player.getInventory().getSelectedSlot();Hand current=read(mc);String reason=null;
        if(hand!=null){
            if(currentSlot!=slot)reason="Слот в руке сменился: "+(slot+1)+" → "+(currentSlot+1);
            else if(current==null&&!hand.stackable())reason="Предмет в руке исчез: "+hand.label();
            else if(current!=null&&(current.item()!=hand.item()
                ||!hand.skyId().isEmpty()&&!Objects.equals(current.skyId(),hand.skyId())
                ||!hand.uuid().isEmpty()&&!Objects.equals(current.uuid(),hand.uuid())))
                reason="Предмет в руке заменён: "+hand.label()+" → "+current.label();
        }
        if(reason!=null){
            boolean stopped=CombatPanic.fire(mc,"safetyWeapon",reason+" — управление тебе");
            if(stopped){reset();return true;}
        }
        // Count/durability/lore/mana are not compared; identity absent initially may become known later.
        capture(mc);return false;
    }
    public boolean serverMove(Minecraft mc,String kind,double dx,double dy,double dz,double rotation){
        if(!watching())return false;
        if(!Double.isFinite(dx)||!Double.isFinite(dy)||!Double.isFinite(dz)||!Double.isFinite(rotation))
            return CombatPanic.fire(mc,"safetyTeleport","Невалидная серверная позиция/камера — стоп");
        if(correctionWorld!=mc.level){corrections.reset();correctionWorld=mc.level;}
        boolean position="телепорта".equals(kind);
        boolean turn="поворота".equals(kind)||"взгляда (look at)".equals(kind);
        if(CombatPanic.context()&&(position||turn)&&CombatPanic.settings().serverRotation
            &&rotation>=CombatPanic.settings().serverRotationDegrees)
            return CombatPanic.fire(mc,"safetyRotation",String.format(java.util.Locale.ROOT,
                "Сервер изменил камеру на %.2f° (порог %.2f°) — управление тебе",
                rotation,CombatPanic.settings().serverRotationDegrees));
        if(!position||!CombatPanic.teleport())return false;
        double horizontal=Math.hypot(dx,dz),distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
        // Zero/sub-millimetre acknowledgement is not a movement; rotation was checked independently above.
        if(distance<=.001)return false;
        if(CombatPanic.context()){
            var c=CombatPanic.settings();
            if(c.smallCorrections&&horizontal<=c.correctionHorizontal&&Math.abs(dy)<=c.correctionVertical&&rotation<=10){
                long now=System.nanoTime();
                if(!corrections.accept(now,c.correctionWindowSec,c.correctionMaxPackets,c.correctionBudget,distance))
                    return CombatPanic.fire(mc,"safetyTeleport","Серия малых коррекций превысила окно/суммарный допуск — управление тебе (не доказательство проверки)");
                if(now-lastCorrectionLog>2_000_000_000L){lastCorrectionLog=now;
                    EventLog.log("COMBAT-CORRECTION",String.format(java.util.Locale.ROOT,"Малый пакет позиции принят без тревоги: горизонталь %.3f, высота %.3f, поворот %.2f°",horizontal,Math.abs(dy),rotation));}
                return false;
            }
        }
        return CombatPanic.fire(mc,"safetyTeleport",String.format(java.util.Locale.ROOT,
            "Неожиданное смещение от сервера (%.3f бл, по Y %.3f) — локальный стоп Combat/стоп фермы",distance,Math.abs(dy)));
    }
    public boolean respawn(Minecraft mc){
        return watching()&&CombatPanic.teleport()
            &&CombatPanic.fire(mc,"safetyTeleport","Респавн/смена мира во время автоматики — полный стоп");
    }
    private AutomationSafety(){}
}

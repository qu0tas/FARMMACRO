package com.farmmacro.camera;

import com.farmmacro.config.ModConfig.CamPreset;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Привязка пресетов камеры к макросу (по кадрам) или маршруту (по точкам). Хранится в файле макроса/маршрута.
 *  • {@code none}   — камеру не трогаем;
 *  • {@code whole}  — один пресет на весь проход (ставится при старте и в начале каждого круга);
 *  • {@code points} — «с элемента N действует пресет X» (ставится при старте и когда дошли до N).
 * Kaelix/recording snapshot remains instant. Aether routes can opt into a guarded smooth turn; the caller MUST gate input.
 * После каждой установки детектору сообщается новый поворот ({@link PanicDetector#expectRotation}).
 * В смене хранится копия yaw/pitch/имени: если пресет удалили, работают сохранённые значения.
 */
public class CameraBinding {
    public static final String NONE = "none", WHOLE = "whole", POINTS = "points";

    public String mode = NONE;
    /** В файле — по порядку from, без повторов. В режиме whole действует первая. */
    public List<Change> changes = new ArrayList<>();

    public static class Change {
        /** С какого элемента (0-based: индекс точки или кадра). */
        public int    from;
        public int    presetId;
        public String name = "";
        public float  yaw, pitch;

        public Change() {}

        public Change copy() {
            Change c = new Change();
            c.from = from; c.presetId = presetId; c.name = name; c.yaw = yaw; c.pitch = pitch;
            return c;
        }

        /** Взять значения у пресета (копия на случай его удаления). */
        public void take(CamPreset p) {
            presetId = p.id; name = p.name; yaw = p.yaw; pitch = p.pitch;
        }

        /** Действующие yaw/pitch: у живого пресета, иначе сохранённые. */
        public float yaw()   { CamPreset p = CameraPresets.byId(presetId); return p != null ? p.yaw : yaw; }
        public float pitch() { CamPreset p = CameraPresets.byId(presetId); return p != null ? p.pitch : pitch; }
        public boolean presetExists() { return CameraPresets.byId(presetId) != null; }
        public String label() {
            CamPreset p = CameraPresets.byId(presetId);
            return p != null ? p.name : (name == null || name.isEmpty() ? "?" : name) + " (удалён)";
        }
    }

    public boolean active() { return !NONE.equals(mode) && !changes.isEmpty(); }

    /** Смена, действующая на элементе index, или null (камеру не трогать). */
    public Change at(int index) {
        if (!active()) return null;
        if (WHOLE.equals(mode)) return changes.get(0);
        Change best = null;                       // список может быть не отсортирован, пока его правят в меню
        for (Change c : changes) if (c.from <= index && (best == null || c.from >= best.from)) best = c;
        return best;
    }

    public CameraBinding copy() {
        CameraBinding b = new CameraBinding();
        b.mode = mode;
        for (Change c : changes) b.changes.add(c.copy());
        return b;
    }

    /** Перед записью в файл: порядок по from и свежие копии значений у существующих пресетов. */
    public void refresh() {
        sort();
        for (Change c : changes) {
            CamPreset p = CameraPresets.byId(c.presetId);
            if (p != null) c.take(p);
        }
    }

    /** Порядок по from, без повторов (остаётся последняя). */
    public void sort() {
        changes.sort(Comparator.comparingInt(c -> c.from));
        for (int i = changes.size() - 1; i > 0; i--)
            if (changes.get(i).from == changes.get(i - 1).from) changes.remove(i - 1);
    }

    /** После чтения файла (старые файлы — без поля, null). */
    public static CameraBinding sanitize(CameraBinding b) {
        if (b == null) return new CameraBinding();
        if (!NONE.equals(b.mode) && !WHOLE.equals(b.mode) && !POINTS.equals(b.mode)) b.mode = NONE;
        if (b.changes == null) b.changes = new ArrayList<>();
        b.changes.removeIf(Objects::isNull);
        for (Change c : b.changes) {
            c.from = Math.max(0, c.from);
            if (c.name == null) c.name = "";
            c.yaw = Mth.wrapDegrees(Float.isFinite(c.yaw) ? c.yaw : 0f);
            c.pitch = Mth.clamp(Float.isFinite(c.pitch) ? c.pitch : 0f, -90f, 90f);
        }
        while (b.changes.size() > 256) b.changes.remove(b.changes.size() - 1);
        b.sort();
        if (b.changes.isEmpty()) b.mode = NONE;
        return b;
    }

    /**
     * Поставить камеру в пресет смены — мгновенно, и сообщить детектору, что это свой поворот.
     * Плавный поворот к пресету по клавише, если шёл, прекращается.
     */
    public static void apply(Minecraft mc, Change c, String where) {
        LocalPlayer p = mc.player;
        if (p == null || c == null || SmoothTurn.bindingBusy()) return;
        SmoothTurn.stop();
        var human = com.farmmacro.macro.Humanizer.ACTIVE;
        float[] j = human != null ? human.cameraJitter() : new float[]{0, 0};
        if(!com.farmmacro.motion.MotionController.absolute(com.farmmacro.macro.MacroManager.INSTANCE.isActive()?com.farmmacro.motion.MotionPolicy.Owner.FARM:com.farmmacro.motion.MotionPolicy.Owner.PRESET,c.yaw()+j[0],c.pitch()+j[1],true))return;
        p.sendOverlayMessage(Component.literal("§8[§9Kaelix§8] §b◎ " + c.label() + " §7(" + where + "): yaw "
                + CameraPresets.deg(c.yaw()) + "°, pitch " + CameraPresets.deg(c.pitch()) + "°"));
    }

    /** One tracker per source. Only routes explicitly opt into alpha29's gated smooth binding. */
    public static final class Tracker {
        private Change applied;
        private boolean any;
        private com.farmmacro.motion.CameraTurnLease.Ticket turn;
        private com.farmmacro.motion.BindingTurnGate gate;
        private Object player,world;
        private String failure;
        public void reset(){SmoothTurn.cancelBinding(turn);turn=null;gate=null;applied=null;any=false;failure=null;player=world=null;}
        public String failure(){return failure==null?"установка камеры прервана":failure;}
        public boolean waiting(){return turn!=null;}
        public boolean failed(){return failure!=null;}
        public boolean update(Minecraft mc,CameraBinding b,int index,String where){return update(mc,b,index,where,false);}
        public boolean update(Minecraft mc,CameraBinding b,int index,String where,boolean route){
            Change c=b==null?null:b.at(index);
            if(c==null||(any&&c==applied))return false;
            if(turn!=null){failure="новая привязка до завершения прежней";return false;}
            applied=c;any=true;
            var conf=com.farmmacro.config.ModConfig.INSTANCE;
            if(!route||!conf.motion.aether()||!conf.motion.smoothBindings){apply(mc,c,where);return true;}
            LocalPlayer p=mc.player;
            if(p==null||mc.level==null||!safeSupport(p)||SmoothTurn.isActive()
                ||com.farmmacro.camera.Freelook.active()||com.farmmacro.camera.MouseLock.isLocked()
                ||Mousemat.busy()||com.farmmacro.panic.HumanReaction.running()){
                failure="нет опоры или камера занята";return true;
            }
            float yaw=c.yaw(),pitch=Math.max(-89,Math.min(89,c.pitch()));
            turn=SmoothTurn.beginBinding(yaw,pitch,conf.motion.bindingSpeed);
            if(turn==null){failure="камера не получила управление";return true;}
            player=p;world=mc.level;
            gate=new com.farmmacro.motion.BindingTurnGate(turn,p.getX(),p.getY(),p.getZ(),System.nanoTime(),
                com.farmmacro.motion.BindingTurnGate.timeout(yaw-p.getYRot(),pitch-p.getXRot(),conf.motion.bindingSpeed,conf.motion));
            p.sendOverlayMessage(Component.literal("§8[§9Kaelix§8] §b◎ Поворот: "+c.label()+" §7("+where+")"));
            return true;
        }
        public com.farmmacro.motion.BindingTurnGate.Result poll(Minecraft mc){
            if(failure!=null)return com.farmmacro.motion.BindingTurnGate.Result.FAILED;
            if(turn==null)return com.farmmacro.motion.BindingTurnGate.Result.READY;
            LocalPlayer p=mc.player;
            boolean context=p!=null&&p==player&&mc.level==world&&mc.screen==null&&com.farmmacro.window.BackgroundWork.permitted(mc)&&!mc.isPaused()
                &&safeSupport(p)&&SmoothTurn.ownsBinding(turn)&&!com.farmmacro.route.NavigationInput.busy()
                &&com.farmmacro.config.ModConfig.INSTANCE.motion.aether()&&com.farmmacro.config.ModConfig.INSTANCE.motion.smoothBindings
                &&!com.farmmacro.panic.PanicHandoff.pending()&&!com.farmmacro.camera.Freelook.active()
                &&!com.farmmacro.camera.MouseLock.isLocked()&&!Mousemat.busy()&&!com.farmmacro.panic.HumanReaction.running();
            var state=p==null?com.farmmacro.motion.BindingTurnGate.Result.FAILED:gate.check(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot(),mc.options.sensitivity().get(),context,System.nanoTime());
            if(state==com.farmmacro.motion.BindingTurnGate.Result.READY){
                if(!SmoothTurn.acknowledgeBinding(turn))state=com.farmmacro.motion.BindingTurnGate.Result.FAILED;
                else {turn=null;gate=null;player=world=null;}
            }
            if(state==com.farmmacro.motion.BindingTurnGate.Result.FAILED){SmoothTurn.cancelBinding(turn);failure="поворот отменён, истёк срок или изменилась позиция/опора";}
            return state;
        }
        private static boolean safeSupport(LocalPlayer p){return p.onGround()&&!p.getAbilities().flying&&!p.isFallFlying()&&!p.onClimbable()&&!p.isInWater()&&!p.isInLava();}
    }
}

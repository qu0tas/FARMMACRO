package com.farmmacro.config;
import com.google.gson.*;
import java.lang.reflect.Field;
import java.util.*;
/** Explicit allowlisted snapshot, deep copies lists; active legacy fields remain the runtime view. */
public final class CombatProfiles {
    public static final String ZEALOTS="zealots";
    private static final Gson JSON=new Gson();
    private static final Set<String> KEYS=Set.of("aimRequireAttack","aimWalkRadius","aimChainRearWaitMs","aimAcquireSpeedPct","aimTrackSpeedPct","aimSpecialTurnSpeed","aimHumanShotTiming","aimEarlyShotChance","aimPitch","aimLimitLocation","aimTrackingMode","aimHumanStrength","aimZealotsOnly","aimAutoFire","aimUnnamedEndermen","aimAutoFireIntervalMs","aimScytheArea","aimNextAfterShot","aimSpecialHunt","aimScytheNearChance","aimIdleLook","aimIdleDelayMs","aimIdleYaw","aimIdleSpeed","aimSpeedVariationPct","aimReactionMinMs","aimReactionMaxMs","aimShotObservationMs","aimSpecialSearchSeconds","aimJitterEnabled","aimWalkInPlace","aimJitterDegrees","aimSelectedOnly","aimFrontOnly","aimYieldMouse","aimLoadedRange","aimZealotPriority","aimScytheOnly","aimHighlight","aimRange","aimFov","aimTurnSpeed","aimPreset","aimExtraNames","aimMobNames");
    private static final List<Field> FIELDS=Arrays.stream(ModConfig.class.getFields()).filter(f->KEYS.contains(f.getName())).toList();
    public static JsonObject snapshot(ModConfig c){
        JsonObject out=new JsonObject();
        for(Field f:FIELDS)try{out.add(f.getName(),JSON.toJsonTree(f.get(c)));}catch(IllegalAccessException e){throw new IllegalStateException(e);}
        return out;
    }
    private static CombatProfile zealots(ModConfig c){
        JsonObject j=snapshot(c);j.addProperty("aimZealotsOnly",true);j.addProperty("aimSelectedOnly",true);
        j.addProperty("aimPreset","zealots");j.addProperty("aimExtraNames","");j.addProperty("aimZealotPriority",true);
        j.add("aimMobNames",JSON.toJsonTree(List.of("Zealot","Zealot Bruiser","Special Zealot")));
        return new CombatProfile(ZEALOTS,"Зиалоты",j);
    }
    public static void ensure(ModConfig c){
        if(c.combatProfiles==null)c.combatProfiles=new ArrayList<>();
        Set<String> ids=new HashSet<>();
        c.combatProfiles=new ArrayList<>(c.combatProfiles.stream().filter(p->p!=null&&p.id!=null&&!p.id.isBlank()&&p.id.length()<=64&&ids.add(p.id)).limit(24).toList());
        for(var p:c.combatProfiles){if(p.settings==null)p.settings=new JsonObject();cameraDefaults(p.settings);p.name=p.name==null||p.name.isBlank()?"Мобы":p.name.strip();if(p.name.length()>40)p.name=p.name.substring(0,40);}
        if(c.combatProfiles.isEmpty()){
            var z=zealots(c);c.combatProfiles.add(z);
            if(c.aimZealotsOnly)c.combatProfileId=ZEALOTS;
            else {var old=new CombatProfile("migrated","Мобы — прежние настройки",snapshot(c));c.combatProfiles.add(old);c.combatProfileId=old.id;}
        }
        if(c.combatProfiles.stream().noneMatch(p->ZEALOTS.equals(p.id))){if(c.combatProfiles.size()>=24)c.combatProfiles.removeLast();c.combatProfiles.add(0,zealots(c));}
        for(var p:c.combatProfiles)if(ZEALOTS.equals(p.id)&&p.settings.get("aimZealotsOnly").getAsBoolean()&&"slayer_enderman".equals(p.settings.get("aimPreset").getAsString())){
            p.settings.addProperty("aimPreset","zealots");
            if(ZEALOTS.equals(c.combatProfileId)&&c.aimZealotsOnly&&"slayer_enderman".equals(c.aimPreset))c.aimPreset="zealots";
        }
        if(c.combatProfileId==null||c.combatProfiles.stream().noneMatch(p->p.id.equals(c.combatProfileId)))c.combatProfileId=c.combatProfiles.getFirst().id;
    }
    /** Complete independent values: missing/malformed fields never inherit the previous profile. */
    private static void cameraDefaults(JsonObject settings){
        JsonObject defaults=DefaultValues.DATA;
        for(Field f:FIELDS){String k=f.getName();JsonElement v=settings.get(k);
            if(!valid(f,v))settings.add(k,defaults.get(k).deepCopy());}
        settings.keySet().removeIf(k->!KEYS.contains(k));
        String group=settings.get("aimPreset").getAsString();
        if(com.farmmacro.combat.MobCatalog.GROUPS.stream().noneMatch(g->g.id().equals(group)))settings.addProperty("aimPreset","combat");
    }
    private static final class DefaultValues {private static final JsonObject DATA=snapshot(new ModConfig());}
    private static boolean valid(Field f,JsonElement v){
        if(v==null||v.isJsonNull())return false;
        if(f.getType()==boolean.class)return v.isJsonPrimitive()&&v.getAsJsonPrimitive().isBoolean();
        if(f.getType()==String.class)return v.isJsonPrimitive()&&v.getAsJsonPrimitive().isString()&&v.getAsString().length()<=1024;
        if(f.getType()==int.class||f.getType()==double.class){
            if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())return false;
            double n=v.getAsDouble();return Double.isFinite(n)&&(f.getType()!=int.class||n==Math.rint(n)&&n>=Integer.MIN_VALUE&&n<=Integer.MAX_VALUE);
        }
        if(!v.isJsonArray()||v.getAsJsonArray().size()>128)return false;
        for(var e:v.getAsJsonArray())if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString()||e.getAsString().length()>64)return false;
        return true;
    }
    private static void applyValues(ModConfig c,JsonObject settings){
        cameraDefaults(settings);
        for(Field f:FIELDS)try{if(!com.farmmacro.motion.SharedCameraSettings.FIELDS.contains(f.getName()))f.set(c,JSON.fromJson(settings.get(f.getName()),f.getGenericType()));}
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    public static boolean resetSelected(ModConfig c){
        var target=active(c);var fresh=new ModConfig();
        target.settings=ZEALOTS.equals(target.id)?zealots(fresh).settings:snapshot(fresh);
        if(!ZEALOTS.equals(target.id)){
            target.settings.addProperty("aimSelectedOnly",true);target.settings.addProperty("aimPreset","combat");
            target.settings.add("aimMobNames",new JsonArray());target.settings.addProperty("aimExtraNames","");
        }
        applyValues(c,target.settings);c.sanitize();captureActive(c);return true;
    }
    public static String exportSelected(ModConfig c){
        captureActive(c);var p=active(c);JsonObject out=new JsonObject();out.addProperty("kind","kaelix-combat");out.addProperty("version",1);
        out.addProperty("name",p.name);out.add("settings",p.settings.deepCopy());
        return "KAELIX-COMBAT1:"+ConfigCode.encode(out.toString()).substring(ConfigCode.PREFIX.length());
    }
    /** Add a NEW profile; never overwrite an existing id, binds, panic, permissions or global loadouts. */
    public static String importSelected(ModConfig c,String code){
        try{
            ensure(c);if(c.combatProfiles.size()>=24)return "Достигнут лимит 24 профилей";
            if(code==null||code.length()>65536)return "Пустая или слишком большая строка Combat";
            String text=code.replaceAll("\\s+","");String prefix="KAELIX-COMBAT1:";
            if(!text.startsWith(prefix))return "Нужна строка KAELIX-COMBAT1:, не полный конфиг";
            String json=ConfigCode.decode(ConfigCode.PREFIX+text.substring(prefix.length()));
            if(json.length()>32768)return "Combat-профиль слишком большой";
            var root=JsonParser.parseString(json).getAsJsonObject();
            if(!root.get("kind").getAsString().equals("kaelix-combat")||root.get("version").getAsInt()!=1)return "Неизвестный формат Combat";
            JsonObject data=root.getAsJsonObject("settings").deepCopy();
            for(var entry:data.entrySet()){
                var f=FIELDS.stream().filter(x->x.getName().equals(entry.getKey())).findFirst().orElse(null);
                if(f==null||!valid(f,entry.getValue()))return "Неизвестное или неверное поле: "+entry.getKey();
            }
            var clean=new ModConfig();clean.aimAetherRevision=1;applyValues(clean,data);clean.sanitize();
            String name=root.has("name")?root.get("name").getAsString().strip():"Импорт Combat";
            if(name.isBlank()||name.length()>40||name.codePoints().anyMatch(Character::isISOControl))return "Неверное имя профиля";
            captureActive(c);String id=UUID.randomUUID().toString();
            c.combatProfiles.add(new CombatProfile(id,name,snapshot(clean)));select(c,id);return null;
        }catch(RuntimeException e){return "Профиль не читается: "+e.getClass().getSimpleName();}
    }
    public static String summary(ModConfig c){
        return "Мобы: "+(c.aimZealotsOnly?"только зиалоты":c.aimSelectedOnly?c.aimMobNames.size()+" выбранных + свои имена":"все допустимые Mob")+
            " • "+com.farmmacro.combat.MobCatalog.get(c.aimPreset).title()+
            " • оружие: "+(c.aimScytheOnly?"Glacial Scythe":"камера с любым; выстрел только Glacial")+
            " • камера Motion: "+c.motion.camera.mode(c.motion)+", "+Math.round(c.motion.camera.aimTurnSpeed)+"°/с"+
            " • выстрел: "+(c.aimAutoFire?"вкл / "+c.aimAutoFireIntervalMs+"мс":"выкл")+
            " • A/D: "+(c.aimWalkInPlace?"вкл":"выкл");
    }
    public static CombatProfile active(ModConfig c){ensure(c);return c.combatProfiles.stream().filter(p->p.id.equals(c.combatProfileId)).findFirst().orElseThrow();}
    public static void captureActive(ModConfig c){active(c).settings=snapshot(c);}
    public static boolean select(ModConfig c,String id){
        ensure(c);var target=c.combatProfiles.stream().filter(p->p.id.equals(id)).findFirst().orElse(null);if(target==null)return false;
        captureActive(c); // Selecting current must not resurrect an older snapshot.
        c.combatProfileId=id;
        applyValues(c,target.settings);
        c.sanitize();captureActive(c);return true;
    }
    public static boolean duplicate(ModConfig c,boolean fromZealots){
        captureActive(c);if(c.combatProfiles.size()>=24)return false;
        var source=fromZealots?c.combatProfiles.stream().filter(p->ZEALOTS.equals(p.id)).findFirst().orElseThrow():active(c);
        JsonObject settings=source.settings.deepCopy();if(fromZealots){settings.addProperty("aimZealotsOnly",false);settings.addProperty("aimSpecialHunt",false);
        settings.addProperty("aimZealotPriority",false);settings.addProperty("aimSelectedOnly",true);
        settings.addProperty("aimPreset","combat");settings.addProperty("aimExtraNames","");settings.add("aimMobNames",new JsonArray());}
        String id=UUID.randomUUID().toString();c.combatProfiles.add(new CombatProfile(id,"Мои мобы "+c.combatProfiles.size(),settings));return select(c,id);
    }
    public static boolean remove(ModConfig c){
        if(ZEALOTS.equals(c.combatProfileId))return false;String id=c.combatProfileId;
        select(c,ZEALOTS);return c.combatProfiles.removeIf(p->p.id.equals(id));
    }
    private CombatProfiles(){}
}

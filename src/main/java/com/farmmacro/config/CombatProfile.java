package com.farmmacro.config;
import com.google.gson.JsonObject;
/** Independent Combat preset. Activation/keybind and shared panic are deliberately not copied. */
public final class CombatProfile {
    public String id="",name="";
    public JsonObject settings=new JsonObject();
    public CombatProfile(){}
    public CombatProfile(String id,String name,JsonObject settings){this.id=id;this.name=name;this.settings=settings;}
}

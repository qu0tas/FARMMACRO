package com.farmmacro.stats;
/** Passive telemetry only. None of these switches authorize game input, purchases or remote commands. */
public final class StatsSettings {
 public boolean enabled=true,hud=true,compact=true,graph=true,save=false,bazaar=false;
 public String skill="AUTO";public boolean skillHud=true,petHud=true;
 public String priceMode="NPC",theme="SLATE";
 public int farmingCap=50;public double petPrice=-1;public String petPriceFor="";
 public boolean combat=true,farms=true,pests=true,navigation=true,general=true;
 public void sanitize(){if(skill==null||!skill.equals("AUTO")&&SkillCatalog.get(skill)==null)skill="AUTO";bazaar=false;priceMode="NPC";if(priceMode==null||!java.util.Set.of("NPC","BAZAAR").contains(priceMode))priceMode="NPC";if(theme==null||!java.util.Set.of("SLATE","GLASS","PAPER").contains(theme))theme="SLATE";farmingCap=Math.clamp(farmingCap,0,60);petPrice=Double.isFinite(petPrice)?Math.clamp(petPrice,-1,1e15):-1;if(petPriceFor==null)petPriceFor="";if(petPriceFor.length()>80)petPriceFor=petPriceFor.substring(0,80);}
}

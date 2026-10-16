package com.farmmacro.pest;
import com.farmmacro.panic.ServerText;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.regex.Pattern;
public final class PestItems {
    public static final List<String> TYPES=List.of("Praying Mantis","Lunar Moth","Field Mouse","Earthworm","Mosquito","Dragonfly","Firefly","Cricket","Locust","Beetle","Mite","Moth","Slug","Fly","Rat");
    public static String id(ItemStack s){var d=s.get(DataComponents.CUSTOM_DATA);return d==null?"":d.copyTag().getCompoundOrEmpty("ExtraAttributes").getStringOr("id","");}
    public static String uuid(ItemStack s){var d=s.get(DataComponents.CUSTOM_DATA);return d==null?"":d.copyTag().getCompoundOrEmpty("ExtraAttributes").getStringOr("uuid","");}
    public static String lore(ItemStack s){var l=s.get(DataComponents.LORE);return l==null?"":ServerText.clean(String.join(" ",l.lines().stream().map(x->x.getString()).toList()));}
    public static String name(ItemStack s){return ServerText.clean(s.getHoverName().getString());}
    public static String type(String s){if(s==null)return null;String p=ServerText.clean(s).toLowerCase(Locale.ROOT);for(String t:TYPES)if(Pattern.compile("(?<![a-z])"+Pattern.quote(t.toLowerCase(Locale.ROOT))+"(?![a-z])").matcher(p).find())return t;return null;}
    public static boolean lasso(ItemStack s){return Set.of("ABYSMAL_LASSO","VINERIP_LASSO","ENTANGLER_LASSO","EVERSTRETCH_LASSO").contains(id(s))&&lore(s).contains("Throw")&&lore(s).contains("Reel In");}
    public static int lassoSlot(net.minecraft.client.Minecraft mc){for(int i=8;i>=0;i--)if(lasso(mc.player.getInventory().getItem(i)))return i;return -1;}
    public static double dps(ItemStack s){var m=Pattern.compile("(?i)dealing ([0-9,]+(?:\\.[0-9]+)?) damage per second").matcher(lore(s));if(!m.find())return Double.NaN;try{double d=Double.parseDouble(m.group(1).replace(",",""));return d>=1&&d<=10000?d:Double.NaN;}catch(NumberFormatException e){return Double.NaN;}}
    private PestItems(){}
}

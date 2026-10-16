package com.farmmacro.pest;

import com.farmmacro.panic.ServerText;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import java.util.Locale;
import java.util.regex.Pattern;

/** Server item metadata first, exact known vacuum names as a compatibility fallback. */
public record VacuumInfo(String name,double range) {
    private static final Pattern RANGE=Pattern.compile("(?i)(?:within|range[: ]*)\\s*(\\d+(?:\\.\\d+)?)\\s*blocks");
    public static VacuumInfo read(ItemStack stack) {
        if(stack==null||stack.isEmpty())return null;
        var data=stack.get(DataComponents.CUSTOM_DATA);
        String id=data==null?"":data.copyTag().getCompoundOrEmpty("ExtraAttributes").getStringOr("id","");
        String name=ServerText.clean(stack.getHoverName().getString());
        String n=name.toLowerCase(Locale.ROOT);double range;
        if(id.equals("INFINI_VACUUM_HOOVERIUS")||n.contains("infinivacuum")&&n.contains("hooverius"))range=15;
        else if(id.equals("INFINI_VACUUM")||n.contains("infinivacuum"))range=12.5;
        else if(id.equals("SKYMART_HYPER_VACUUM")||n.contains("skymart hyper vacuum"))range=10;
        else if(id.equals("SKYMART_TURBO_VACUUM")||n.contains("skymart turbo vacuum"))range=7.5;
        else if(id.equals("SKYMART_VACUUM")||n.contains("skymart vacuum"))range=5;
        else return null;
        if(!id.isEmpty()&&!java.util.Set.of("INFINI_VACUUM_HOOVERIUS","INFINI_VACUUM","SKYMART_HYPER_VACUUM","SKYMART_TURBO_VACUUM","SKYMART_VACUUM").contains(id))return null;
        var lore=stack.get(DataComponents.LORE);
        if(lore!=null){
            String text=ServerText.clean(String.join(" ",lore.lines().stream().map(l->l.getString()).toList()));
            int start=text.toLowerCase(Locale.ROOT).indexOf("ability: vacuum");
            if(start>=0){String ability=text.substring(start+15);int next=ability.toLowerCase(Locale.ROOT).indexOf("ability:");if(next>=0)ability=ability.substring(0,next);
                var m=RANGE.matcher(ability);if(m.find())try{double v=Double.parseDouble(m.group(1));if(v>=3&&v<=20)range=v;}catch(NumberFormatException ignored){}
            }
        }
        return new VacuumInfo(name,range);
    }
}

package com.farmmacro.release;
import com.farmmacro.panic.SlotSwitchIntents;
/** Fixed tiny ticket table: no game, event injection, simulated macros or network. */
public final class ManualSlotQuickCheck {
 private record Identity(String item,String id,String uuid){}
 private static void ok(boolean x){if(!x)throw new IllegalStateException("manual slot evidence regression");}
 public static void main(){
  var q=new SlotSwitchIntents<Identity>();long now=2_000_000_000L;
  Identity bow=new Identity("bow","JUJU_SHORTBOW","bow-1"),heal=new Identity("stick","WAND_OF_ATONEMENT","heal-1"),scythe=new Identity("hoe","GLACIAL_SCYTHE","scythe-1");
  ok(!q.consume(1,bow,now));
  for(var item:java.util.List.of(bow,heal,scythe)){
   q.arm(1,item,now);ok(!q.consume(2,item,now+1));ok(q.consume(1,item,now+1));ok(!q.consume(1,item,now+2));
   q.arm(1,item,now);ok(!q.consume(1,new Identity(item.item(),item.id(),"replaced"),now+1));
   q.arm(1,item,now);ok(!q.consume(1,new Identity("other",item.id(),item.uuid()),now+1));
   q.arm(1,item,now);ok(!q.consume(1,new Identity(item.item(),"other",item.uuid()),now+1));
   q.arm(1,item,now);ok(!q.consume(1,null,now+1));
   q.arm(1,item,now);ok(!q.consume(1,item,now+SlotSwitchIntents.MAX_AGE_NS+1));
   q.arm(1,item,now);ok(q.consume(1,item,now+SlotSwitchIntents.MAX_AGE_NS));
   q.arm(1,item,now);ok(!q.consume(1,item,now-1));
  }
  q.arm(0,null,now);ok(q.consume(0,null,now+1));
  q.arm(1,bow,now);q.clear();ok(!q.consume(1,bow,now+1));
  q.arm(-1,bow,now);q.arm(9,bow,now);ok(!q.consume(-1,bow,now+1)&&!q.consume(9,bow,now+1));
  q.arm(1,bow,now);q.arm(2,heal,now);ok(q.consume(1,bow,now+1)&&q.consume(2,heal,now+2));
  q.arm(1,bow,now);q.arm(1,scythe,now+1);ok(q.consume(1,scythe,now+2));
  System.out.println("ManualSlotQuickCheck: PASS (exact slots/item-ID/UUID, one-shot, expiry, empty, lifecycle, bow/heal/scythe; no game)");
 }
 private ManualSlotQuickCheck(){}
}

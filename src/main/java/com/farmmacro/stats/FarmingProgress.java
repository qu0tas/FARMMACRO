package com.farmmacro.stats;
/** Compatibility facade; durable ledger remains Farming-only. */
public final class FarmingProgress {
 public static final double[] TOTAL=SkillCatalog.get("FARMING").totals().stream().mapToDouble(Double::doubleValue).toArray();
 final SkillProgress delegate=new SkillProgress(SkillCatalog.get("FARMING"));
 public double observe(String text,Object w,long now){return delegate.observe(text,w,now);}
 public double total(){return delegate.total();}public boolean fresh(Object w){return delegate.fresh(w);}public double gained(){return delegate.gained();}
 public int level(){return delegate.level();}public double remaining(int cap){return delegate.remaining(cap);}public double progress(){return delegate.progress();}
 public void reset(){delegate.reset();}public void contextLost(){delegate.contextLost();}
}

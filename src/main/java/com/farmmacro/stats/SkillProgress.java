package com.farmmacro.stats;
import java.util.regex.*;
/** Absolute observations only. Repeated +XP banners never create gain. */
public final class SkillProgress {
 public static final String NUMBER="(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?[kKmMbB]?";
 private final SkillCatalog.Skill skill;private final Pattern pattern;
 private double total=Double.NaN,gained;private long at,observedNanos;private Object world;private boolean observed;
 public SkillProgress(SkillCatalog.Skill s){skill=java.util.Objects.requireNonNull(s);pattern=Pattern.compile("(?<![A-Za-z])"+Pattern.quote(s.name())+"(?:\\s+(?:XP|EXP))?\\s*\\(("+NUMBER+")\\s*/\\s*("+NUMBER+")\\)");}
 public static double number(String text){try{String s=text.strip().replace(",","");char last=s.charAt(s.length()-1);double multiplier=switch(last){case 'k','K'->1e3;case 'm','M'->1e6;case 'b','B'->1e9;default->1;};if(multiplier!=1)s=s.substring(0,s.length()-1);double v=Double.parseDouble(s)*multiplier;return Double.isFinite(v)&&v>=0?v:Double.NaN;}catch(RuntimeException e){return Double.NaN;}}
 public double observe(String text,Object w,long now){if(text==null||text.length()>4096||w==null)return 0;var m=pattern.matcher(text);if(!m.find())return 0;double current=number(m.group(1)),max=number(m.group(2));if(m.find()||!Double.isFinite(current)||!Double.isFinite(max)||current>max||max<=0)return 0;
  int level=-1;for(int i=0;i<skill.maxLevel();i++)if(Math.abs(skill.total(i+1)-skill.total(i)-max)<.01){if(level!=-1)return 0;level=i;}if(level<0)return 0;
  double value=skill.total(level)+current,delta=0;boolean continuous=world==w&&at!=0&&now>=at&&now-at<=15_000_000_000L&&Double.isFinite(total);
  if(continuous&&value<total)return 0;
  if(continuous&&value-total<=100000){delta=value-total;observedNanos+=now-at;}
  total=value;world=w;at=now;observed=true;gained+=delta;return delta;
 }
 public SkillCatalog.Skill skill(){return skill;}public double total(){return total;}
 public boolean fresh(Object w){return fresh(w,System.nanoTime());}public boolean fresh(Object w,long now){return world==w&&at!=0&&now>=at&&now-at<60_000_000_000L;}
 public long lastAt(){return at;}public double gained(){return observed?gained:Double.NaN;}
 public double perHour(){return observed&&observedNanos>=60_000_000_000L?gained*3_600_000_000_000d/observedNanos:Double.NaN;}
 public double eta(int cap){double rem=remaining(cap),rate=perHour();return rem==0?0:rate>0?rem/rate*3600:Double.NaN;}
 public int level(){if(!Double.isFinite(total))return -1;int i=0;while(i<skill.maxLevel()&&total>=skill.total(i+1))i++;return i;}
 public double remaining(int cap){return Double.isFinite(total)?Math.max(0,skill.total(cap)-total):Double.NaN;}
 public double progress(){int l=level();return l<0?Double.NaN:l>=skill.maxLevel()?1:Math.clamp((total-skill.total(l))/(skill.total(l+1)-skill.total(l)),0,1);}
 public void reset(){contextLost();gained=0;observedNanos=0;}
 public void contextLost(){total=Double.NaN;at=0;world=null;observed=false;}
}

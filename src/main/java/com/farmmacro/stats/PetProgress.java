package com.farmmacro.stats;
import java.util.*;
import java.util.regex.*;
/** An explicitly dated server-menu snapshot, not a live pet XP estimate. */
public record PetProgress(double total,double current,double required,int level,boolean max,boolean approximate,Object world,long at) {
 private static final Pattern MARKER=Pattern.compile("^Progress to Level ([0-9]{1,3}):(?:\\s*[0-9]+(?:\\.[0-9]+)?%)?$");
 private static final Pattern RATIO=Pattern.compile("^\\s*(?:[-|\\[\\]]{2,}\\s+)?("+SkillProgress.NUMBER+")\\s*/\\s*("+SkillProgress.NUMBER+")\\s*$");
 public static PetProgress parse(List<String> lines,int level,double total,Object world,long now){
  if(level<1||level>200||lines.size()>100)return null;double t=Double.isFinite(total)&&total>=0&&total<=1e15?total:Double.NaN;
  if(lines.stream().anyMatch(s->s.strip().equals("MAX LEVEL")))return new PetProgress(t,Double.NaN,Double.NaN,level,true,false,world,now);
  double cur=Double.NaN,req=Double.NaN;boolean approx=false,found=false;
  for(int i=0;i<lines.size();i++){String line=lines.get(i).strip();if(line.length()>256)continue;var marker=MARKER.matcher(line);if(!marker.matches()||Integer.parseInt(marker.group(1))!=level+1)continue;
   for(int j=i+1;j<Math.min(lines.size(),i+4);j++){String s=lines.get(j).strip();if(s.length()>256)break;var m=RATIO.matcher(s);if(!m.matches()){if(!s.isBlank())break;continue;}double c=SkillProgress.number(m.group(1)),r=SkillProgress.number(m.group(2));if(!Double.isFinite(c)||!Double.isFinite(r)||r<=0||c>r||found)return null;cur=c;req=r;approx=m.group(1).matches(".*[kKmMbB].*")||m.group(2).matches(".*[kKmMbB].*");found=true;break;}
  }
  return found||Double.isFinite(t)?new PetProgress(t,cur,req,level,false,approx,world,now):null;
 }
 public boolean fresh(Object w,long now){return world==w&&at!=0&&now>=at&&now-at<60_000_000_000L;}
 public double remaining(){return max?0:Double.isFinite(required)?Math.max(0,required-current):Double.NaN;}
 public double progress(){return max?1:Double.isFinite(required)?current/required:Double.NaN;}
 public long ageSeconds(long now){return Math.max(0,(now-at)/1_000_000_000L);}
}

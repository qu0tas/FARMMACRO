package com.farmmacro.motion;
/** Client-thread ownership ticket, pure and testable. A stopped turn is NEVER successful arrival. */
public final class CameraTurnLease {
 public enum State { WAITING, ARRIVED, CANCELLED }
 public static final class Ticket {
  private State state=State.WAITING;
  public final double yaw,pitch;
  private Ticket(double yaw,double pitch){this.yaw=yaw;this.pitch=pitch;}
  public State state(){return state;}
 }
 private Ticket current;
 public boolean busy(){return current!=null;}
 public boolean owns(Ticket t){return t!=null&&current==t;}
 public Ticket acquire(double yaw,double pitch){
  if(busy()||!Double.isFinite(yaw)||!Double.isFinite(pitch)||pitch< -89||pitch>89)return null;
  current=new Ticket(yaw,pitch);return current;
 }
 public void arrive(){if(current!=null&&current.state==State.WAITING)current.state=State.ARRIVED;}
 public void cancel(){if(current!=null)current.state=State.CANCELLED;current=null;}
 public boolean release(Ticket ticket){if(!owns(ticket)||ticket.state!=State.ARRIVED)return false;current=null;return true;}
}

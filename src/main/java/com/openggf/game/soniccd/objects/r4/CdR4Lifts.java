package com.openggf.game.soniccd.objects.r4;

import com.openggf.game.soniccd.player.CdPlayerMath;
import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;

/** Native r4/elevator, switch_elevator, pressure_elevator, platform_chain.asm semantic leaves.
 * Pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. No ordinary loop or live ownership. */
public final class CdR4Lifts {
    private CdR4Lifts() {}
    public enum Mapping { ELEVATOR, SWITCH_ELEVATOR, PRESSURE_ELEVATOR, PLATFORM_CHAIN }
    public interface Ports extends CdR4MechanismServices {
        int mapping(Mapping mapping);
        int timeZone();
        int act();
        int cameraX();
        /** loc_20CA88: source saved-state clear uses unmasked time_zone, then DeleteObject. */
        void elevatorDespawn(CdR4MechanismImage actor);
        CdPlayerMath.SineCosine sineCosine(int angleByte);
        void sound(int nativeWord);
    }
    private static void init(CdR4MechanismImage a,Ports p,int tile,Mapping mapping) {
        a.ab(0x24,2); a.b(1,a.b(1)|4); a.w(2,tile); a.b(0x18,3); a.l(4,p.mapping(mapping));
    }
    public static void elevator(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole);
        switch(a.b(0x24)) {
            case 0 -> { init(a,p,0x435C,Mapping.ELEVATOR); a.b(0x19,16); a.b(0x16,16); }
            case 2 -> {
                if(a.sb(1)<0 && p.solid(a,SOLID_OBJECT)) {
                    a.ab(0x24,2); int era=p.timeZone()&3;
                    if(era>2) throw new IllegalStateException("Unowned fourth elevator table row");
                    int speed=era==2?0x200:0x100; a.w(0x10,speed); a.w(0x12,-speed); a.w(0x3E,era==2?0x50:0xA0);
                }
            }
            case 4 -> {
                p.solid(a,SOLID_OBJECT);
                if(a.w(0x3E)!=0) { a.al(8,a.sw(0x10)<<8); a.al(12,a.sw(0x12)<<8); a.aw(0x3E,-1);
                    if(a.w(0x3E)==0) { a.w(0x10,0); a.w(0x12,0); } }
            }
            default -> throw new IllegalStateException("Unknown Elevator routine");
        }
        p.draw(a);
        // Source loc_20CA88 clears saved state using unmasked time_zone; Root's despawn port owns that reached read.
        if(CdR4SwitchDoor.outside(a.w(8),p.cameraX())) p.elevatorDespawn(a);
    }
    public static void switchElevator(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole);
        if(a.b(0x24)==0) {
            init(a,p,0x446A,Mapping.SWITCH_ELEVATOR);
            if(a.b(0x3C)==0) {
                a.w(0x36,a.w(8)); a.w(0x32,a.w(12)); a.aw(8,16); a.b(0x19,16); a.b(0x16,64); a.b(0x1A,1);
                var child=c.allocate(a,true);
                if(child==null) { p.delete(a); switchElevatorTail(c,a,p); return; }
                child.b(0,a.b(0)); child.w(0x36,a.w(0x36)); child.w(0x32,a.w(0x32)); child.b(0x19,16); child.b(0x16,32);
                child.b(0x3C,1); child.w(12,a.w(12)+32); child.w(8,a.w(8)-32); child.w(0x34,a.rawAddress()); a.w(0x34,child.rawAddress()); child.b(0x1A,2);
                var button=c.allocate(a,true);
                if(button==null) { p.delete(a); switchElevatorTail(c,a,p); return; }
                button.b(0,0x31); button.w(0x34,a.rawAddress()); a.w(0x30,button.rawAddress()); button.b(0x39,0xBC);
                button.w(0x36,a.w(0x36)); button.w(0x32,a.w(0x32));
            }
        } else if(a.b(0x24)!=2) throw new IllegalStateException("Unknown SwitchElevator routine");
        switchElevatorMotion(c,a,p);
        switchElevatorTail(c,a,p);
    }
    private static void switchElevatorMotion(CdR4MechanismCut c,CdR4MechanismImage a,Ports p) {
        if(a.b(0x3C)!=0) return;
        int saved=a.l(12);
        if(a.sw(0x12)<0) a.aw(12,-64);
        int distance=p.floorDistance(a,a.sw(0x12)<0);
        if((short)distance<0) a.w(0x12,0);
        a.l(12,saved); a.al(12,a.sw(0x12)<<8);
        if(a.w(0x34)!=0) { var child=c.linkWord(a.w(0x34)); if(child.b(0)==0x30) child.w(0x12,a.w(0x12)); }
        if(a.w(0x30)==0) return;
        var button=c.linkWord(a.w(0x30)); if(button.b(0)!=0x31) return;
        if((button.b(0x28)&3)==0) { a.aw(0x12,8); if(a.sw(0x12)>=0 && a.w(0x12)>=256) a.w(0x12,256); }
        else { a.aw(0x12,-8); if(a.sw(0x12)<0 && a.w(0x12)<0xFF00) a.w(0x12,-256); }
    }
    private static void switchElevatorTail(CdR4MechanismCut c,CdR4MechanismImage a,Ports p) {
        p.solid(a,SOLID_OBJECT);
        if(a.b(0x3C)!=0 && a.w(0x34)!=0) {
            var parent=c.linkWord(a.w(0x34)); if(parent.b(0)!=0x30) { p.delete(a); return; }
            a.w(8,parent.w(8)-32); a.w(12,parent.w(12)+32);
        }
        p.draw(a); if(CdR4SwitchDoor.outside(a.w(0x36),p.cameraX())) p.delete(a);
    }
    public static void pressureElevator(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole); var player=c.actor(0); int routine=a.b(0x24);
        if(routine==0) { a.ab(0x24,2); a.b(1,a.b(1)|4); a.b(0x18,3); a.b(0x17,32); a.b(0x19,32);
            a.w(2,0x4310); a.l(4,p.mapping(Mapping.PRESSURE_ELEVATOR)); a.w(0x2A,a.w(12)); a.b(0x16,33); return; }
        if(routine==2) { int y=(player.w(12)-224)&65535; if((short)y>(short)a.w(0x2A)) { a.ab(0x24,2); a.w(12,y); } p.checkDespawn(a,a.w(8)); return; }
        if(routine==4 || routine==6) {
            if(routine==4) { a.ab(0x24,2); a.b(0x16,33); a.b(0x1A,a.b(0x1A)&254); a.l(0x2C,0); a.w(0x12,0); }
            a.al(12,a.l(0x2C)); a.al(0x2C,0x400); if(a.l(0x2C)>0x10000) a.l(0x2C,0x10000);
            int distance=(short)(a.w(12)-player.w(12)); if(distance<0) distance=(short)-distance;
            if(a.sb(1)>=0 && distance>224) a.b(0x24,2);
            else if(p.solid(a,TOP_SOLID_OBJECT)) { a.b(0x24,8); p.getOff(a); a.b(0x16,31); a.aw(12,2); player.aw(12,4); p.sound(0xBF); }
            p.draw(a); p.checkDespawn(a,a.w(8)); return;
        }
        if(routine==8 || routine==10) {
            if(routine==8) { a.ab(0x24,2); a.b(0x1A,a.b(0x1A)|1); a.b(0x16,31); a.w(0x12,-512); }
            if(a.sw(12)<=a.sw(0x2A)) a.b(0x24,2);
            else if(!p.solid(a,TOP_SOLID_OBJECT)) { a.b(0x24,4); a.aw(12,-2); player.aw(12,-4); }
            p.draw(a); a.al(12,-0x20000); p.checkDespawn(a,a.w(8)); return;
        }
        throw new IllegalStateException("Unknown PressureElevator routine");
    }
    public static void platformChain(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole);
        if(a.b(0x24)==0) {
            a.ab(0x24,2); a.b(1,a.b(1)|4); a.w(2,0x4340); if(p.act()==2) a.w(2,0x4400);
            a.l(4,p.mapping(Mapping.PLATFORM_CHAIN)); a.b(0x19,32); a.b(0x16,8);
            if(a.b(0x3C)==0) {
                a.w(0x36,a.w(8)); a.w(0x32,a.w(12));
                for(int n=1;n<=8;n++) { var child=c.allocate(a,false); if(child==null) continue;
                    child.b(0,0x3E); child.b(0x28,a.b(0x28)); child.b(0x3C,n); child.w(0x36,a.w(0x36)); child.w(0x32,a.w(0x32)); }
            }
            int[] angles={32,0,-32,24,16,8,-8,-16,-24}; int part=a.b(0x3C);
            a.b(0x3E,angles[part]); a.b(0x1A,part<3?0:1); a.b(0x18,1);
            if(a.b(0x1A)!=0) { a.b(0x18,3); a.aw(2,-0x4000); }
            a.w(0x30,(a.b(0x28)&16)==0?256:-256);
        } else if(a.b(0x24)!=2) throw new IllegalStateException("Unknown PlatformChain routine");
        if((a.b(0x28)&128)==0) {
            if((c.switches().read(a.b(0x28)&15)&128)!=0) { if(a.b(0x3D)==0) { a.w(0x30,-a.w(0x30)); a.b(0x3D,1); } }
            else a.b(0x3D,0);
        }
        a.aw(0x3E,a.w(0x30)); var trig=p.sineCosine(a.b(0x3E));
        // MULS.L result then ASR.W retains the product's upper word before SWAP, not sign extension.
        int y=swappedScaled(trig.sine())+(a.w(0x32)<<16), x=swappedScaled(trig.cosine())+(a.w(0x36)<<16);
        int dy=y-a.l(12), dx=x-a.l(8); a.l(12,y); a.l(8,x); a.w(0x12,(dy<<8)>>>16); a.w(0x10,(dx<<8)>>>16);
        if(a.b(0x1A)==0 && p.solid(a,TOP_SOLID_OBJECT)) CdR4SwitchDoor.align(a,c.actor(0),2);
        p.draw(a); if(CdR4SwitchDoor.outside(a.w(0x36),p.cameraX())) p.delete(a);
    }
    private static int swappedScaled(int value) { int product=(short)value*6; int scaled=(product&0xFFFF0000)|(((short)product>>4)&65535); return Integer.rotateLeft(scaled,16); }
}

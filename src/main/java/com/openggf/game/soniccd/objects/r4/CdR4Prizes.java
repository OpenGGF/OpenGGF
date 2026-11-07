package com.openggf.game.soniccd.objects.r4;

import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;

/** PrizeSwitch/RingPrize/PointsPrize semantic leaves of src/objects/r4/prize.asm,
 * pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Tables are algorithm operands,
 * mappings/animation bytes are supplied only by actual media through Root ports. */
public final class CdR4Prizes {
    private CdR4Prizes() {}
    public enum Mapping { PRIZE_SWITCH, RING, POINTS_PRIZE }
    public interface Ports extends CdR4MechanismServices {
        int mapping(Mapping mapping);
        int timeSecondsByte();
        int bottomBound();
        void sound(int nativeWord);
        /** AddPoints receives d0=100 native units (1000 displayed), not a fitted display amount. */
        void addPoints(int nativeUnits);
        void collectRing();
        void animateRingPrize(CdR4MechanismImage actor);
    }
    public static void prizeSwitch(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole);
        switch(a.b(0x24)) {
            case 0 -> initialize(c,a,p);
            case 2 -> {
                contact(c,a,p);
                var parent=c.linkWord(a.w(0x3A));
                if(parent.w(0x3C)!=0) {
                    if(a.b(0x2E)==0) { p.sound(0xBF); a.b(0x2E,255); }
                    int previous=a.b(0x2F); a.ab(0x2F,16);
                    if(previous>=240) { a.ab(0x24,2); p.getOff(a); a.w(0x38,0); a.b(0x1A,2); }
                }
            }
            case 4 -> award(c,a,p);
            default -> throw new IllegalStateException("Unknown PrizeSwitch routine");
        }
        p.draw(a); p.checkDespawn(a,a.w(0x36));
    }
    private static void initialize(CdR4MechanismCut c,CdR4MechanismImage a,Ports p) {
        a.ab(0x24,2); a.b(1,a.b(1)|4); a.w(2,0x50A); a.b(0x18,3); a.l(4,p.mapping(Mapping.PRIZE_SWITCH)); a.b(0x19,14); a.b(0x16,8);
        if(a.b(0x34)!=0) return;
        a.w(0x36,a.w(8)); a.w(0x32,a.w(12)); a.w(0x3A,a.rawAddress());
        int[] x={0,-64,-128,64,96,128}, y={0,64,128,-64,-128,-128};
        for(int n=1;n<=5;n++) {
            var child=c.allocate(a,false); if(child==null) continue;
            child.b(0,0x3F); child.w(0x36,a.w(0x36)); child.w(0x3A,a.rawAddress()); child.b(0x34,n);
            child.w(8,a.w(8)+x[n]); child.w(12,a.w(12)+y[n]); child.w(0x32,child.w(12));
        }
    }
    private static void contact(CdR4MechanismCut c,CdR4MechanismImage a,Ports p) {
        a.b(0x30,a.b(0x31)); var player=c.actor(0); boolean pressed=p.solid(a,BTM_SOLID_OBJECT); a.b(0x31,pressed?255:0);
        if(pressed && player.sw(0x12)>=0 && a.b(0x2E)==0) {
            var parent=c.linkWord(a.w(0x3A)); parent.w(0x3C,a.rawAddress());
            int index=a.b(0x34)*6+((p.timeSecondsByte()&255)/10&7);
            int[] table={2,1,0,3,3,2,1,0,3,3,2,2,0,3,3,2,2,1,2,2,1,0,3,3,3,2,2,1,0,3,3,3,2,2,1,0,0,0,0,0,0,0};
            a.b(0x35,table[index]); parent.b(0x35,a.b(0x35));
        }
        if(a.w(0x30)==255) { player.aw(12,8); a.aw(12,4); a.b(0x1A,1); a.ab(0x16,-4); }
        if(a.w(0x30)==0xFF00) { player.aw(12,-8); a.aw(12,-4); a.b(0x1A,0); a.ab(0x16,4); }
    }
    private static boolean selected(CdR4MechanismCut c,CdR4MechanismImage a) {
        var parent=c.linkWord(a.w(0x3A)); return parent.w(0x3C)==(a.rawAddress()&65535);
    }
    private static void award(CdR4MechanismCut c,CdR4MechanismImage a,Ports p) {
        var parent=c.linkWord(a.w(0x3A));
        switch(parent.b(0x35)) {
            case 0 -> {
                if(a.b(0x38)!=0) { p.delete(a); return; }
                if(!selected(c,a) && a.b(0x34)!=4) {
                    var child=c.allocate(a,false); if(child!=null) { child.b(0,0x22); child.w(8,a.w(8)); child.w(0x36,a.w(0x36)); child.w(12,a.w(0x32)); }
                }
                a.ab(0x38,1);
            }
            case 1 -> {
                if(a.b(0x38)!=0) { p.delete(a); return; }
                if(!selected(c,a)) {
                    p.addPoints(100); var child=c.allocate(a,false);
                    if(child!=null) { child.b(0,0x41); child.w(8,a.w(8)); child.w(12,a.w(12)); }
                }
                a.ab(0x38,1);
            }
            case 2 -> {
                if(selected(c,a)) { int prev=a.b(0x38); a.ab(0x38,1); if(prev==255) p.delete(a); }
                else {
                    int previous=a.b(0x39); a.ab(0x39,4);
                    if(previous>=252) {
                        var child=c.allocate(a,false); if(child!=null) { child.b(0,0x20); child.w(8,a.w(8)); child.w(12,a.w(0x32)); child.b(0x28,2); }
                        a.ab(0x38,1); if(a.b(0x38)>=2) p.delete(a);
                    }
                }
            }
            case 3 -> {
                if(parent!=a) { p.delete(a); return; }
                int previous=a.b(0x38); a.ab(0x38,16);
                if(previous>=240) {
                    if(a.b(0x39)>=21) { p.delete(a); return; }
                    var child=c.allocate(a,false);
                    if(child!=null) {
                        child.b(0,0x40); child.w(12,a.w(12)-0x180);
                        int[] offsets={-32,0,32,64,96,127,96,64,32,0,-32,-32,0,32,64,96,127,96,64,32,0,-32};
                        child.w(8,a.w(8)+offsets[a.b(0x39)]);
                    }
                    a.ab(0x39,1); // increments on allocation failure too.
                }
            }
            default -> throw new IllegalStateException("Unknown prize award dispatch");
        }
    }
    public static void ringPrize(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole); int routine=a.b(0x24);
        if(routine==0) {
            a.ab(0x24,2); a.b(0x16,8); a.b(0x17,8); a.l(4,p.mapping(Mapping.RING)); a.w(2,0xA7AE); a.b(1,a.b(1)|4); a.b(0x18,3);
            a.b(0x20,0x47); a.b(0x19,8); a.w(0x38,0x800); a.w(0x12,0x100); a.b(0x3A,255); a.b(0x3B,0x30); routine=2;
        }
        if(routine==2) {
            p.move(a); a.aw(0x12,0x18);
            boolean floor=a.b(0x3B)==0;
            if(!floor) { a.ab(0x3B,-1); floor=a.b(0x3B)==0; }
            if(floor) { int distance=p.floorDistance(a,false); if((short)distance<0) { a.aw(12,distance); int speed=a.sw(0x12); a.w(0x12,-(speed-(speed>>2))); } }
            a.aw(0x36,a.w(0x38)); a.b(0x1A,(a.w(0x36)>>>12)&3); // low two bits of ROL.W #4
            a.aw(0x38,-8); a.ab(0x3A,-1);
            if(a.b(0x3A)==0 || (((p.bottomBound()+224)&65535)<a.w(12))) { p.delete(a); return; }
            p.draw(a); return;
        }
        if(routine==4) { a.ab(0x24,2); a.b(0x20,0); a.b(0x18,1); p.collectRing(); routine=6; }
        if(routine==6) { p.animateRingPrize(a); p.draw(a); return; }
        if(routine==8) { p.delete(a); return; }
        throw new IllegalStateException("Unknown RingPrize routine");
    }
    public static void pointsPrize(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole);
        if(a.b(0x24)==0) { a.ab(0x24,2); a.l(4,p.mapping(Mapping.POINTS_PRIZE)); a.w(2,0x36E); a.b(1,4); a.b(0x18,1); a.b(0x19,8); a.w(0x12,-0x300); }
        else if(a.b(0x24)!=2) throw new IllegalStateException("Unknown PointsPrize routine");
        if(a.sw(0x12)<0) { p.move(a); a.aw(0x12,0x18); } else p.delete(a);
        p.draw(a);
    }
}

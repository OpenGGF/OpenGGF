package com.openggf.game.soniccd.objects.r4;

import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;

/** BreakPoleObject/GeyserObject, src/objects/r4/{break_pole,geyser}.asm, native
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Capture uses raw source actor links. */
public final class CdR4BreakGeyser {
    private CdR4BreakGeyser() {}
    public enum Mapping { BREAK_POLE, GEYSER }
    public interface Ports extends CdR4MechanismServices {
        int mapping(Mapping mapping);
        int debugModeByte();
        /** Native MOVE.W p1_joy_hold: high held, low tap; these are not a normalized held BYTE. */
        int controllerWord();
        int waterY();
        void paletteWord(int byteOffset,int nativeWord);
        void animateGeyser(CdR4MechanismImage actor);
    }
    public static void breakPole(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole); int routine=a.b(0x24);
        if(routine==0) { a.b(1,a.b(1)|4); a.w(2,0x42F8); a.l(4,p.mapping(Mapping.BREAK_POLE)); a.ab(0x24,2); routine=2; }
        if(routine==2) {
            if(p.debugModeByte()==0) {
                var player=c.actor(0); int dx=(a.w(8)-player.w(8)+4)&65535;
                if((short)dx>=0 && dx<8) {
                    int dy=(a.w(12)-player.w(12)+24)&65535;
                    if((short)dy>=0 && dy<48) {
                        player.b(0x2C,player.b(0x2C)|1); player.w(8,a.w(8)); boolean left=player.sw(0x10)<0;
                        player.b(0x22,(player.b(0x22)&254)|(left?1:0)); a.b(1,a.b(1)|(left?1:0)); player.aw(8,left?-24:24);
                        player.b(0x1C,0x11); a.w(0x2A,180); if(a.b(0x28)!=0) a.w(0x2A,60); a.l(0x2C,player.rawAddress()); a.ab(0x24,2);
                    }
                }
            }
        } else if(routine==4) {
            int rawPlayer=a.l(0x2C); a.aw(0x2A,-1);
            if(a.w(0x2A)==0) release(a,c.linkLong(rawPlayer));
            else {
                int ctrl=p.controllerWord();
                if((ctrl&0x70)!=0) release(a,c.linkLong(rawPlayer));
                else {
                    int sourceY=a.w(12);
                    if((ctrl&0x100)!=0) { var player=c.linkLong(rawPlayer); player.aw(12,-1); int limit=(sourceY-24)&65535; if(limit>=player.w(12)) player.w(12,limit); }
                    else if((ctrl&0x200)!=0) { var player=c.linkLong(rawPlayer); player.aw(12,1); int limit=(sourceY+24)&65535; if(limit<player.w(12)) player.w(12,limit); }
                }
            }
        } else if(routine!=6) throw new IllegalStateException("Unknown BreakPole routine");
        p.draw(a); p.checkDespawn(a,a.w(8));
    }
    private static void release(CdR4MechanismImage a,CdR4MechanismImage player) { player.b(0x2C,player.b(0x2C)&254); a.b(0x1A,1); a.ab(0x24,2); }
    public static void geyser(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole);
        if(a.b(0x28)==255) {
            if(a.b(0x24)==0) { a.ab(0x24,2); a.b(1,a.b(1)|4); a.w(2,0x6300); a.b(0x18,3); a.l(4,p.mapping(Mapping.GEYSER)); a.b(0x19,16); a.b(0x16,16); a.b(0x1C,1); }
            droplet(a,p); return;
        }
        int routine=a.b(0x24);
        if(routine==0) {
            p.paletteWord(0x6E,0x646); p.paletteWord(0x70,0xA8A); p.paletteWord(0x72,0xEEE);
            a.ab(0x24,2); a.b(1,a.b(1)|4); a.w(2,0x6300); a.b(0x18,1); a.l(4,p.mapping(Mapping.GEYSER)); a.b(0x19,32); a.b(0x16,60); routine=2;
        }
        if(routine==2) {
            int previous=a.b(0x3B); a.ab(0x3B,16);
            if(previous>=240) {
                var child=c.allocate(a,false);
                if(child!=null) {
                    child.b(0,a.b(0)); child.b(0x28,255); child.b(0x1A,5); int row=a.b(0x3A)&3; child.b(0x3A,row);
                    int[] xs={-40,8,40,-8}, vx={-32,32,64,-64};
                    child.w(8,a.w(8)+xs[row]); child.w(12,a.w(12)-72); child.w(0x10,vx[row]); child.w(0x12,-128); a.ab(0x3A,1);
                }
            }
            p.animateGeyser(a); a.w(12,p.waterY()-48); p.solid(a,TOP_SOLID_OBJECT);
        } else if(routine==4) dropletBody(a,p); else throw new IllegalStateException("Unknown Geyser routine");
        // Non-FF routine4 returns to the outer Draw/Check too, even after DeleteObject.
        p.draw(a); p.checkDespawn(a,a.w(8));
    }
    private static void droplet(CdR4MechanismImage a,Ports p) { dropletBody(a,p); }
    private static void dropletBody(CdR4MechanismImage a,Ports p) {
        int prev=a.b(0x3B); a.ab(0x3B,2); if(prev>=254) { p.delete(a); return; }
        a.al(8,a.sw(0x10)<<8); a.al(12,a.sw(0x12)<<8); a.aw(0x12,8); p.animateGeyser(a); p.draw(a); p.checkDespawn(a,a.w(8));
    }
}

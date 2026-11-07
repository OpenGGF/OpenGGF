package com.openggf.game.soniccd.objects.r4;

import static com.openggf.game.soniccd.objects.CdSolidObject.Entry.*;

/** switch.asm, door.asm, big_door.asm at native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Finite semantic leaves; original ordinary scheduling/admission/publication is Root-owned. */
public final class CdR4SwitchDoor {
    private CdR4SwitchDoor() {}
    public enum Mapping { SWITCH, SWITCH_2, DOOR, BIG_DOOR }
    public interface Ports extends CdR4MechanismServices {
        int mapping(Mapping mapping);
        int switchBaseWord();
        int cameraX();
        void sound(int nativeWord);
    }
    private static void init(CdR4MechanismImage a,Ports p,int tile,Mapping mapping) {
        a.ab(0x24,2); a.b(1,a.b(1)|4); a.w(2,tile); a.b(0x18,3); a.l(4,p.mapping(mapping));
    }
    private static int bankIndex(int raw,Ports p) { return (raw-p.switchBaseWord())&65535; }
    public static void switchObject(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole); var player=c.actor(0);
        if(a.b(0x24)==0) {
            a.ab(0x24,2); a.b(1,a.b(1)|4); a.b(0x18,3); a.b(0x17,0x10); a.b(0x19,0x10);
            a.w(2,0x50A); a.l(4,p.mapping(Mapping.SWITCH)); a.b(0x16,10);
            a.w(0x3C,p.switchBaseWord()+a.b(0x28));
        }
        if(a.w(0x34)!=0) {
            var parent=c.linkWord(a.w(0x34)); int x=a.w(8);
            a.w(8,parent.w(8)+a.sb(0x38)); a.w(0x10,(a.w(8)-x)<<8); a.w(12,parent.w(12)+a.sb(0x39));
        }
        a.b(0x3E,a.b(0x3F));
        boolean pressed=p.solid(a,SOLID_OBJECT);
        if(pressed) align(a,player,1);
        // The native SNE uses SolidObject/alignment's retained condition; alignment ends MOVE.W.
        // The source's alignment result may be zero, so preserve the second condition explicitly.
        pressed=pressed && player.w(12)!=0;
        a.b(0x3F,pressed?255:0);
        int index=bankIndex(a.w(0x3C),p), value=c.switches().read(index);
        if(!pressed) c.switchByte(index,value&0x7F);
        else { align(a,player,1); c.switchByte(index,value|0x80); c.switchByte(index,c.switches().read(index)|0x40); }
        if(a.w(0x3E)==255) {
            if(a.sb(1)<0) p.sound(0xBF);
            c.switchByte(index,c.switches().read(index)^0x20); player.aw(12,8);
            if(a.w(0x34)!=0) a.ab(0x39,4); else a.aw(12,4);
            a.b(0x1A,1); a.ab(0x16,-4);
        }
        if(a.w(0x3E)==0xFF00) {
            player.aw(12,-8); if(a.w(0x34)!=0) a.ab(0x39,-4); else a.aw(12,-4);
            a.b(0x1A,0); a.ab(0x16,4);
        }
        p.draw(a);
        if(a.w(0x34)==0) p.checkDespawn(a,a.w(8));
        else if(outside(c.linkWord(a.w(0x34)).w(0x36),p.cameraX())) p.despawn(a);
    }
    static void align(CdR4MechanismImage a,CdR4MechanismImage player,int bias) {
        player.w(12,a.w(12)-a.sb(0x16)-player.sb(0x16)+bias);
    }
    static boolean outside(int anchor,int camera) { return (((anchor&0xFF80)-((camera-0x80)&0xFF80))&65535)>0x280; }
    public static void switch2(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole); var player=c.actor(0);
        if(a.b(0x24)==0) {
            init(a,p,0x50A,Mapping.SWITCH_2); if(a.w(0x36)==0) a.w(0x36,a.w(8)); a.b(0x19,14); a.b(0x16,5);
        } else if(a.b(0x24)!=2) throw new IllegalStateException("Unknown Switch2 routine");
        int width=(short)(a.sb(0x19)+8), dx=(a.w(8)==player.w(8)?0:(short)(player.w(8)-a.w(8)));
        dx=(dx+width)&65535;
        int dy=(a.w(12)-16-(player.w(12)+player.sb(0x16)))&65535;
        boolean hit=(short)dx>=0 && dx<((width*2)&65535) && dy>=0xFFF0;
        a.b(0x28, hit?a.b(0x28)|1:a.b(0x28)&0xFE); a.b(0x1A,a.b(0x28)!=0?1:0);
        p.solid(a,SOLID_OBJECT);
        if(a.w(0x34)!=0) { var parent=c.linkWord(a.w(0x34)); a.w(8,parent.w(8)+a.sb(0x38)); a.w(12,parent.w(12)+a.sb(0x39)); }
        p.draw(a); p.checkDespawn(a,a.w(0x36));
    }
    public static void door(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole); var player=c.actor(0);
        switch(a.b(0x24)) {
            case 0 -> {
                a.ab(0x24,2); a.b(1,a.b(1)|4); a.w(2,0x4440); a.b(0x18,2); a.l(4,p.mapping(Mapping.DOOR));
                a.w(0x36,a.w(8)); a.w(0x32,a.w(12)); a.b(0x30,a.b(0x28)&15); a.b(0x31,a.b(0x28)>>>4);
                int[][] shape={{64,8,128,2},{64,8,128,2},{8,32,64,1},{8,64,128,0},{8,64,128,0}};
                int[] row=shape[a.b(0x31)]; a.b(0x19,row[0]); a.b(0x16,row[1]); a.b(0x3B,row[2]); a.b(0x1A,row[3]);
            }
            case 2 -> {
                a.b(0x3C,(c.switches().read(a.b(0x30))&128)==0?255:0);
                if(a.b(0x31)!=4 && a.b(0x3A)!=0) a.b(0x3C,0);
                a.w(0x38,player.w(8)); a.w(0x3E,player.w(12)); doorMove(a); p.solid(a,SOLID_OBJECT);
                if(a.b(0x31)!=4 && a.b(0x3A)==a.b(0x3B)) a.ab(0x24,2);
            }
            case 4 -> {
                int type=a.b(0x31); if(type>4) throw new IllegalStateException("Unknown door type");
                int pos=type<2?12:8, saved=type<2?0x3E:0x38, radius=type<2?0x16:0x17;
                int d;
                if(a.w(pos)<a.w(saved)) {
                    d=(player.sb(radius)+player.w(pos)-a.w(pos))&65535;
                    if((player.sb(radius)+player.w(pos)&65535)<a.w(pos) && ((-d)&(type<2?65535:255))>=(type<2?16:8)) a.ab(0x24,2);
                } else {
                    d=((byte)-player.b(radius)+player.w(pos)-a.w(pos))&65535;
                    if((((byte)-player.b(radius)+player.w(pos))&65535)>=a.w(pos) && (d&255)>=8) a.ab(0x24,2);
                }
                p.solid(a,SOLID_OBJECT);
            }
            case 6 -> { a.b(0x3C,255); doorMove(a); if(a.b(0x3A)==0) a.b(0x24,2); p.solid(a,SOLID_OBJECT); }
            default -> throw new IllegalStateException("Unknown Door routine");
        }
        p.draw(a); p.checkDespawn(a,a.w(8));
    }
    private static void doorMove(CdR4MechanismImage a) {
        int type=a.b(0x31); if(type>4) throw new IllegalStateException("Unknown door motion");
        int amount=a.b(0x3A);
        if(a.b(0x3C)!=0) { a.ab(0x3A,-8); if(amount<8) a.b(0x3A,0); }
        else { a.ab(0x3A,8); if(a.b(0x3A)>=a.b(0x3B)) a.b(0x3A,a.b(0x3B)); }
        if(type<2) a.w(8,a.w(0x36)+(type==0?-a.b(0x3A):a.b(0x3A)));
        else a.w(12,a.w(0x32)+(type==3?a.b(0x3A):-a.b(0x3A)));
    }
    public static void bigDoor(CdR4MechanismCut c,int whole,Ports p) {
        var a=c.actor(whole); int routine=a.b(0x24);
        if(routine==0) {
            int index=a.b(0x28); if(c.switches().read(index)!=0) { p.delete(a); return; }
            a.l(0x30,(short)(p.switchBaseWord()+index));
            a.ab(0x24,2); a.b(1,a.b(1)|4); a.w(2,0x446A); a.l(4,p.mapping(Mapping.BIG_DOOR)); a.l(0x16,0x40400340); routine=2;
        }
        if(routine==2) {
            if(c.switches().read(bankIndex(a.l(0x30),p))==0) { p.solid(a,SOLID_OBJECT); p.draw(a); p.checkDespawn(a,a.w(8)); return; }
            a.ab(0x24,2); a.b(0x17,16); a.b(0x19,16); a.w(0x2A,128); a.l(0x2C,-0x10000); a.ab(0x1A,1); a.aw(8,-48);
            int x=a.w(8);
            for(int n=2;n>=0;n--) {
                var child=c.allocate(a,true); if(child==null) { p.delete(a); return; }
                child.copyFrom(a); x=(x+32)&65535; child.w(8,x);
                if((n&1)==0) { child.l(0x2C,-child.l(0x2C)); child.ab(0x1A,1); }
            }
        } else if(routine==4) {
            a.aw(0x2A,-1); if(a.sw(0x2A)<0) { p.delete(a); return; } a.al(12,a.l(0x2C));
        } else throw new IllegalStateException("Unknown BigDoor routine");
        p.solid(a,SOLID_OBJECT); p.draw(a);
    }
}

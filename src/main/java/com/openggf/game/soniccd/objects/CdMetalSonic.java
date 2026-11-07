package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR31IntroAssets;
import java.util.Objects;
import static com.openggf.game.soniccd.objects.CdIntroSpikes.Read.*;

/** A-only $31 and $34 full graphs. Not PPZ SonicHole31 or SpinTunnel34.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/objects/r3/intro.asm:13-237,827-878 (actual historical A include).
 * Shipped algorithm; no FixBugs alternative. Raw links/dirty timers/baseY LONG stay literal.
 * Uses P5's synchronous Ports contract; returned images are observations, not work receipts.
 */
public final class CdMetalSonic {
    private final CdR31IntroAssets assets;
    public CdMetalSonic(CdR31IntroAssets assets){this.assets=Objects.requireNonNull(assets);}
    public CdObjectState run(CdObjectPool pool,int whole,CdIntroSpikes.Ports ports) {
        var c=new P5Call(assets,pool,whole,ports,0x31);
        int routine=c.s().routine();P5Call.routine(routine,0xA);
        if(routine==0) {
            c.b(0x24,2);c.b(1,c.s().spriteFlags()|4);c.w(2,0x3D0);c.b(0x18,2);
            c.l(4,assets.metalMappings());c.b(0x1A,0xE);c.b(0x16,0x20);c.b(0x3A,0x3C);c.b(0x20,0);
            routine=2;
        }
        switch(routine) {
            case 2 -> {
                if(c.b(0x3A)!=0)c.b(0x3A,c.b(0x3A)-1);
                if(c.b(0x3A)==0) {
                    c.flip(true);c.w(0x3E,-0x10);c.b(0x24,c.s().routine()+2);
                    if((c.s().spriteFlags()&0x80)!=0)c.fm(0xCA);
                }
            }
            case 4 -> {
                c.moveX();int v=(short)(c.w(0x3E)+c.w(0x10));if(v<=-0x300)v=-0x300;c.w(0x10,v);
                int target=0x3E0,link=c.w(0x34);
                if(link!=0)target=(c.linkedX(link)+0x20)&0xFFFF;
                if(target>=c.s().centreX()) {
                    c.w(0x10,0);c.w(0x3E,0);c.b(0x3D,0xFF);c.b(0x1C,2);
                    var free=pool.findObjSlot();
                    if(free.isPresent()){c.childB(free.getAsInt(),0,0x34);c.childW(free.getAsInt(),0x34,c.s().identity().nativeAddress24());}
                    c.b(0x20,0);c.w(0x32,c.s().centreY());c.b(0x24,c.s().routine()+2);
                }
                c.animateIntro(assets.metalAnimations());
            }
            case 6 -> {
                c.facePlayer();bob(c);
                int timer=c.b(0x3A)+4;c.b(0x3A,timer);
                if(timer>255) {c.b(0x24,c.s().routine()+2);c.w(0x12,-0x2C0);c.w(0x3E,0xB);c.b(0x3B,0x40);c.b(0x30,0x50);}
                c.animateIntro(assets.metalAnimations());
            }
            case 8 -> {
                // A1 still names primary after SetFacing/Bob; reads remain at their actual cuts.
                c.facePlayer();
                boolean check=true;
                if(c.b(0x3B)!=0) {
                    c.moveX();c.moveY();c.w(0x12,c.w(0x12)+c.w(0x3E));c.b(0x3B,c.b(0x3B)-1);
                    if(c.b(0x3B)!=0)check=false;
                    else{c.w(0x3E,0);c.w(0x12,0);c.w(0x32,c.s().centreY());}
                }
                if(check) {
                    bob(c);c.b(0x3A,c.b(0x3A)+4);
                    int x=c.s().centreX(),playerX=c.read(PLAYER_X),d=(x-playerX)&0xFFFF;
                    // BCS WORD subtraction, then CMPI.B: a low-byte predicate, NOT distance160.
                    if(x<playerX || (d&255)<0xA0) {
                        c.b(0x30,c.b(0x30)-1);
                        if(c.b(0x30)==0){c.flip(false);c.w(0x10,0);c.w(0x3E,0x60);c.b(0x24,c.s().routine()+2);}
                    }
                }
                c.animateIntro(assets.metalAnimations());
            }
            case 0xA -> {
                c.moveX();int v=(c.w(0x3E)+c.w(0x10))&0xFFFF;if(v>=0x400)v=0x400;c.w(0x10,v);
                if(c.s().centreX()>=0x528)capture(c);else c.animateIntro(assets.metalAnimations());
            }
            default -> throw new AssertionError();
        }
        if(c.read(PLAYER_X)>=0x4C0)capture(c);else c.draw();
        return c.s();
    }
    private static void bob(P5Call c) {
        int sine4=(short)(c.assets.sine(c.b(0x3A))*4);
        // MOVE.W baseY was partial. ADD.L consumes BOTH baseY and raw Amy-link WORD.
        c.l(0xC,c.l(0x32)+(sine4<<8));
    }
    static void capture(P5Call c) {
        c.stable(()->{c.ports.setAmyCaptured(c.cut());return null;});
        c.stable(()->{c.ports.palette(c.assets.capturePalette(),c.cut());return null;});
        c.delete();
    }
    /** Exhaust has no parent-ID normalization or retained Java-parent identity. */
    public CdObjectState runExhaust(CdObjectPool pool,int whole,CdIntroSpikes.Ports ports) {
        var c=new P5Call(assets,pool,whole,ports,0x34);P5Call.routine(c.s().routine(),2);
        if(c.s().routine()==0) {
            c.b(0x24,2);c.b(1,c.s().spriteFlags()|4);c.w(2,0x3D0);c.l(4,assets.metalMappings());c.b(0x18,3);
        }
        int raw=c.w(0x34);
        if(c.linkedId(raw)!=0x31)c.delete();
        else {
                    int x=c.linkedX(raw)-0x10;
            if((c.linkedFlags(raw)&1)!=0)x+=0x20;
            c.w(8,x);c.w(0xC,c.linkedY(raw));
            c.animate(assets.exhaustAnimations());
        }
        c.draw();return c.s();
    }
}

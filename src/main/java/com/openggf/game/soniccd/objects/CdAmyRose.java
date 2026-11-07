package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR31IntroAssets;
import java.util.Objects;
import static com.openggf.game.soniccd.objects.CdIntroSpikes.Read.*;

/** Complete A-only Amy$32/Heart$33 graphs, source-linked to actual Metal$31.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/objects/r3/intro.asm:248-526,758-818 (actual historical A include).
 * No FixBugs alternative; no movement capture replay or new programme lifecycle.
 * Synchronous Ports retain reached prefixes; Root owns original invocation and full-pair adoption.
 */
public final class CdAmyRose {
    private final CdR31IntroAssets assets;
    public CdAmyRose(CdR31IntroAssets assets){this.assets=Objects.requireNonNull(assets);}
    public CdObjectState run(CdObjectPool pool,int whole,CdIntroSpikes.Ports ports) {
        var c=new P5Call(assets,pool,whole,ports,0x32);
        if(c.read(TIME_ATTACK)!=0 || c.read(AMY_CAPTURED)!=0){CdMetalSonic.capture(c);return c.s();}
        int routine=c.s().routine();P5Call.routine(routine,4);
        switch(routine) {
            case 0 -> {
                if((byte)c.b(0x3E)>=0){c.stable(()->{ports.loadPlc(0xB,c.cut());return null;});c.b(0x3E,0xFF);}
                c.b(1,c.s().spriteFlags()|4);c.w(2,0x235E);c.b(0x18,1);c.l(4,assets.amyMappings());
                c.stable(()->{ports.palette(assets.amyPalette(),c.cut());return null;});
                c.face(c.read(PLAYER_X));
                if(c.read(PLAYER_X)>=c.s().centreX())c.b(0x24,c.s().routine()+2);
                c.animateIntro(assets.amyAnimations());
            }
            case 2 -> {
                int link=c.w(0x34);
                // Native only reads byte3D here: no ID check or stale-link repair before it.
                if(link!=0 && c.linkedCaptureFlag(link)!=0) {
                    c.b(0x24,4);c.stable(()->{ports.subCpu(0x7D,c.cut());return null;});
                } else {
                    c.face(c.read(PLAYER_X));
                    if((c.s().flags()&1)!=0 && c.s().centreX()<0x80){c.b(0x1C,0);c.w(0x10,0);}
                    else {
                        if(c.s().centreX()<0x3C0) {
                            int v=(short)(c.w(0x10)+((c.s().flags()&1)!=0?-0x20:0x20));
                            int magnitude=v<0?(short)-v:v;
                            if((magnitude&0xFFFF)>=0x200)v=v<0?-0x200:0x200;
                            c.w(0x10,v);c.moveX();c.b(0x1C,1);
                        }
                        if(c.s().centreX()>=0x3C0) {
                            c.b(0x1C,0);
                            if(c.w(0x34)==0) {
                                var free=pool.findObjSlot();
                                if(free.isPresent()) {
                                    int child=free.getAsInt();c.childB(child,0,0x31);c.childW(child,8,0x500);c.childW(child,0xC,0x3E8);
                                    c.childW(child,0x34,c.s().identity().nativeAddress24());c.w(0x34,pool.state(child).identity().nativeAddress24());
                                }
                            }
                        }
                    }
                }
                c.animateIntro(assets.amyAnimations());makeHeart(c);
            }
            case 4 -> {
                int raw=c.w(0x34);
                if(c.linkedId(raw)!=0x31)c.delete();
                else {
                    c.flip(true);int offset=8;
                    if((c.linkedFlags(raw)&1)!=0){offset=-8;c.flip(false);}
                    c.w(8,c.linkedX(raw)+offset);c.w(0xC,c.linkedY(raw)+4);c.b(0x1C,2);c.animateIntro(assets.amyAnimations());
                }
            }
            default -> throw new AssertionError();
        }
        if(c.read(PLAYER_X)>=0x4C0)CdMetalSonic.capture(c);else c.draw();
        return c.s();
    }
    private static void makeHeart(P5Call c) {
        int timer=c.b(0x3B)+8;c.b(0x3B,timer);if(timer<=255)return;
        var free=c.pool.findObjSlot();if(free.isEmpty())return;
        int child=free.getAsInt();c.childB(child,0,0x33);
        c.childW(child,8,c.s().centreX()+((c.s().flags()&1)==0?0xC:-0xE));c.childW(child,0xC,c.s().centreY()-0xC);
    }
    public CdObjectState runHeart(CdObjectPool pool,int whole,CdIntroSpikes.Ports ports) {
        var c=new P5Call(assets,pool,whole,ports,0x33);P5Call.routine(c.s().routine(),2);
        if(c.s().routine()==0) {
            c.b(0x24,2);c.b(1,c.s().spriteFlags()|4);c.w(2,0x35E);c.l(4,assets.amyMappings());
            c.b(0x18,3);c.w(0x12,-0x60);c.b(0x1A,0xA);
        }
        if(c.b(0x3C)==0)c.w(0x10,assets.sine((c.b(0x3A)*3)&255)>>2);
        c.moveX();c.moveY();c.b(0x3A,c.b(0x3A)+1);
        int timer=c.b(0x3A);
        if(timer==0x14)c.b(0x1A,c.b(0x1A)+1);
        if(timer==0x6E){c.b(0x1A,c.b(0x1A)+1);c.w(0x12,0);c.w(0x10,0);c.b(0x3C,0xFF);}
        if(timer==0x78)c.delete();
        c.draw();ports.despawn(c.cut());return c.s();
    }
}

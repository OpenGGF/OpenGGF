package com.openggf.game.soniccd.objects;

/** N1ecc5f3: src/objects/r4/boss.asm:1200-1283, attached45 visits then ballistic.
 * Native upper/lower inclusive bounds, raw parent reread and deletion skip animation/draw. */
public final class CdR4BossMissile {
    private CdR4BossMissile() { }
    static void run(CdR4Boss.Cursor c) {
        if(c.b(0x24)==0)c.init(CdR4Boss.Part.MISSILE,2,6,4,4,0x31E);
        switch(c.b(0x24)) {
            case 2 -> {
                int parent=c.link(0x2E);
                if((c.pool.state(parent).copyBytes()[0x2C]&128)!=0){c.delete();return;}
                c.w(8,CdR4Boss.word(c.pool.state(parent),8));c.w(0xC,CdR4Boss.word(c.pool.state(parent),0xC));
                c.aw(8,c.sw(0x38));c.aw(0xC,c.sw(0x3A));c.ab(0x2A,1);
                if(c.b(0x2A)==0x2D){c.b(0x20,0xFE);c.b(0x21,2);c.ab(0x24,2);}
            }
            case 4 -> {
                int parent=c.link(0x2E);
                if((c.pool.state(parent).copyBytes()[0x2C]&128)!=0){c.delete();return;}
                c.al(8,c.l(0x3C));c.al(0xC,c.l(0x10));
                if(c.sw(8)<0x9A0||c.sw(8)>0xB00||c.sw(0xC)>0x5D0){c.delete();return;}
            }
            default -> c.badRoutine();
        }
        c.animate(CdR4Boss.Part.MISSILE);c.draw();
    }
}

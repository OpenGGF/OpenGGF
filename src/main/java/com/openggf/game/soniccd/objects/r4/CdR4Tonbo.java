package com.openggf.game.soniccd.objects.r4;
import com.openggf.game.soniccd.objects.CdObjectState;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Data.*;
/** TonboObject through loc_20D5C4 (src/objects/r4/tonbo.asm:3-73), native pin in ports. */
public final class CdR4Tonbo {
    private CdR4Tonbo() { }
    public static CdObjectState run(CdR4ObjectScope c,int n){return c.run(n,0x2b,()->{
        if(c.future(n))return;c.routine(n,2);
        if(c.b(n,0x24)==0){
            c.ab(n,0x24,2);c.b(n,1,4);c.b(n,0x20,0x2c);c.b(n,0x18,1);
            c.b(n,0x19,0x1c);c.b(n,0x17,0x1c);c.b(n,0x16,0x10);c.w(n,2,0x2416);
            c.w(n,0x3e,c.w(n,8));c.l(n,0x2a,c.l(n,12));
            boolean second=c.b(n,0x28)!=0;c.l(n,4,c.pointer(second?TONBO_MAP2:TONBO_MAP1));
            c.l(n,0x30,second?-0x8000:-0x10000);c.w(n,0x34,second?1:4);
            c.w(n,0x38,second?0x200:0x100);c.w(n,0x36,second?0x100:0x80);
        }
        c.al(n,8,c.l(n,0x30));int sine=c.sine(c.w(n,0x2e)).sine();
        c.l(n,12,((sine<<16)>>4)+c.l(n,0x2a));c.aw(n,0x2e,c.w(n,0x34));
        c.aw(n,0x36,-1);if(c.w(n,0x36)==0){c.w(n,0x36,c.w(n,0x38));c.l(n,0x30,-c.l(n,0x30));c.flip(n);}
        c.animate(n,TONBO_ANIM);c.draw(n);c.despawn(n,c.w(n,0x3e));
    });}
}

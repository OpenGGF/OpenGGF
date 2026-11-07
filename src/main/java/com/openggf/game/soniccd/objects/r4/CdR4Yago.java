package com.openggf.game.soniccd.objects.r4;
import com.openggf.game.soniccd.objects.CdObjectState;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Data.*;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Read.*;
/** Both complete Yago routine tables (objects/r4/yago.asm:3-461); differences stay explicit. */
public final class CdR4Yago {
    private CdR4Yago() { }
    public static CdObjectState run(CdR4ObjectScope c,int n){return c.run(n,0x2e,()->{
        boolean second=c.b(n,0x28)!=0;if(c.future(n))return;c.routine(n,0x10);
        int routine=c.b(n,0x24);
        if(routine==0){
            c.w(n,0x3e,c.w(n,8));c.ab(n,0x24,2);c.b(n,1,4);c.b(n,0x20,0x2f);
            c.b(n,0x18,1);c.b(n,0x17,0x18);c.b(n,0x16,12);c.w(n,2,0x23f0);
            c.l(n,4,c.pointer(second?YAGO_MAP2:YAGO_MAP1));routine=2;
        }
        switch(routine){
            case 2->{c.al(n,12,0x10000);int distance=c.floor(n);if(near(distance)){
                c.ab(n,0x24,2);if(second)c.aw(n,12,distance-5);
                c.l(n,0x2a,0x500000);c.l(n,0x2e,-0x4000);
            }}
            case 4->{
                c.al(n,0x2a,-0x4000);if(c.l(n,0x2a)<0){turnWait(c,n);break;}
                c.al(n,8,c.l(n,0x2e));int distance=c.floor(n);c.aw(n,12,distance-5);
                if(near(c.wall(n))){turnWait(c,n);break;}
                int delta=(c.w(n,8)-c.read(PLAYER_X))&65535;if((c.b(n,1)&1)!=0)delta=(-delta)&65535;
                if(c.range(delta,0x10,0x40)){c.ab(n,0x24,6);c.w(n,0x32,7);}
            }
            case 6->{c.aw(n,0x32,-1);if(c.w(n,0x32)==0){c.ab(n,0x24,2);c.w(n,0x32,0x1f);c.l(n,0x2a,0x500000);c.l(n,0x2e,-c.l(n,0x2e));c.flip(n);}}
            case 8->{c.aw(n,0x32,-1);if(c.w(n,0x32)==0)c.ab(n,0x24,-4);}
            case 0xa->{c.aw(n,0x32,-1);if(c.w(n,0x32)==0)attackWait(c,n);}
            case 0xc->{c.al(n,12,second?0x2800:0x2000);c.aw(n,0x32,-1);if(c.w(n,0x32)==0){
                c.ab(n,0x24,2);int speed=second?-0x10000:-0x48000;
                // loc_20E036/loc_20E2A6 publish the negative speed before testing facing and NEG.L.
                c.l(n,0x38,speed);if((c.b(n,1)&1)!=0)c.l(n,0x38,-c.l(n,0x38));
                c.l(n,0x34,-0x18000);
            }}
            case 0xe->{
                c.al(n,8,c.l(n,0x38));int wall=c.wall(n);
                if(second){
                    if(wall<0){c.ab(n,0x24,2);break;}
                    c.al(n,12,c.l(n,0x34));c.al(n,0x34,0x1000);
                    if(c.l(n,0x34)>=0&&near(c.floor(n)))c.b(n,0x24,4);
                }else{
                    if(near(wall)){attackWait(c,n);break;}
                    c.al(n,12,c.l(n,0x34));if(c.l(n,0x34)<0)c.al(n,0x34,0x2000);
                    // Source's inner current-X despawn precedes animation, Draw, outer anchor despawn.
                    c.despawn(n,null);
                }
            }
            case 0x10->{c.al(n,12,0x10000);int distance=c.floor(n);if(near(distance)){
                c.b(n,0x24,6);c.aw(n,12,distance-5);c.w(n,0x32,0x1f);
            }}
            default->throw new AssertionError();
        }
        c.animate(n,second?YAGO_ANIM2:YAGO_ANIM1);c.draw(n);c.despawn(n,c.w(n,0x3e));
    });}
    // CMP.W #5 / BMI tests N of the wrapped WORD subtraction, including overflow.
    private static boolean near(int distance){return (short)(distance-5)<0;}
    private static void turnWait(CdR4ObjectScope c,int n){c.ab(n,0x24,2);c.w(n,0x32,0x1f);}
    private static void attackWait(CdR4ObjectScope c,int n){c.ab(n,0x24,2);c.w(n,0x32,0x1f);}
}

package com.openggf.game.soniccd.objects.r4;
import com.openggf.game.soniccd.objects.CdObjectState;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Data.*;
/** CircleSpikeBallObject (objects/r4/circle_spike_ball.asm:3-138). Init returns without Draw. */
public final class CdR4CircleSpikeBall {
    private CdR4CircleSpikeBall() { }
    public static CdObjectState run(CdR4ObjectScope c,int n){return c.run(n,0x38,()->{
        if(c.w(n,0x3e)!=0&&c.b(c.link(c.w(n,0x3e)),0)!=0x38){c.delete(n);return;}
        c.routine(n,2);
        if(c.b(n,0x24)==0){init(c,n);return;}
        c.aw(n,0x2e,c.w(n,0x30));var sc=c.sine((short)c.w(n,0x2e)>>8);
        int count=c.w(n,0x32)&63;
        c.w(n,8,shift(sc.cosine(),count)+c.w(n,0x2a));
        c.w(n,12,shift(sc.sine(),count)+c.w(n,0x2c));c.draw(n);
        if(c.w(n,0x3e)==0)c.despawn(n,null);
    });}
    private static int shift(int value,int count){return count>=16?(value<0?-1:0):(short)value>>count;}
    private static void init(CdR4ObjectScope c,int n){
        c.w(n,0x34,c.w(n,8));int[] children=new int[3];
        for(int k=0;k<3;k++){
            int child=c.spawn(n,false);if(child<0){c.delete(n);return;}
            children[k]=child;c.b(child,0,c.b(n,0));c.b(child,0x24,2);c.w(child,0x3e,c.raw(n));
        }
        int a3=children[0],a2=children[1],a1=children[2];int phase=(byte)c.b(n,0x28)<0?0x800:-0x800;
        c.w(a1,0x2e,phase);c.w(a2,0x2e,phase*2);c.w(a3,0x2e,phase*3);
        c.w(n,0x30,0x100);if((byte)c.b(n,0x28)<0)c.w(n,0x30,-c.w(n,0x30));
        c.w(n,0x32,Math.abs((byte)c.b(n,0x28))&127);
        c.b(n,0x18,1);c.b(a1,0x18,2);c.b(a2,0x18,3);c.b(a3,0x18,4);
        for(int child:new int[]{n,a1,a2,a3}){
            c.b(child,1,c.b(child,1)|4);c.b(child,0x20,0xa9);c.b(child,0x19,16);c.b(child,0x17,16);c.b(child,0x16,16);
            c.w(child,2,0x370);c.l(child,4,c.pointer(CIRCLE_SPIKE_BALL_MAP));c.b(child,0x24,2);
            c.w(child,0x2a,c.w(n,8));c.w(child,0x2c,c.w(n,12));c.w(child,0x30,c.w(n,0x30));c.w(child,0x32,c.w(n,0x32));
        }
    }
}

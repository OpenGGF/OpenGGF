package com.openggf.game.soniccd.objects.r4;
import com.openggf.game.soniccd.objects.CdObjectState;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Data.*;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Read.*;
/** FallSpikesObject (objects/r4/fall_spikes.asm:3-190); after-current child, lazy visible-body dispatch. */
public final class CdR4FallSpikes {
    private CdR4FallSpikes() { }
    public static CdObjectState run(CdR4ObjectScope c,int n){return c.run(n,0x32,()->{
        if((byte)c.b(n,0x28)<0){if(c.b(c.link(c.w(n,0x3e)),0)!=0x32)c.delete(n);else c.draw(n);return;}
        int routine=c.b(n,0x24);
        if(routine==0||(byte)c.b(n,1)<0){c.routine(n,12);
            switch(routine){
                case 0->init(c,n);
                case 2->{
                    int y=(c.read(PLAYER_Y)-c.w(n,12))&65535;
                    if(c.range(y,0x40,0x80)&&c.range(c.read(PLAYER_X)-c.w(n,8),-0x10,0x20)){
                        c.ab(n,0x24,2);c.w(n,0x12,0x100);c.w(n,0x32,0x14);
                    }
                }
                case 4->{movePair(c,n);c.al(n,0x2a,0x2000);c.aw(n,0x32,-1);if((short)c.w(n,0x32)<0){c.ab(n,0x24,2);c.l(n,0x2a,0);c.w(n,0x32,0x1e);}}
                case 6->{c.aw(n,0x32,-1);if((short)c.w(n,0x32)<0)c.ab(n,0x24,2);}
                case 8->{movePair(c,n);c.al(n,0x2a,c.l(n,0x34));int floor=c.floor(n);if(floor<0){
                    c.ab(n,0x24,2);c.aw(n,12,floor);c.w(n,0x12,0);c.l(n,0x2e,0x200000);c.delete(c.link(c.w(n,0x3c)));
                }}
                case 10->{int delta=c.l(n,0x2a);c.al(n,12,delta);c.al(n,0x2e,-delta);if(c.l(n,0x2e)<0)c.ab(n,0x24,2);}
                case 12->{ }
                default->throw new AssertionError();
            }c.solid(n);
        }
        c.draw(n);c.despawn(n,null);
    });}
    private static void movePair(CdR4ObjectScope c,int n){int delta=c.l(n,0x2a);c.al(n,12,delta);c.al(c.link(c.w(n,0x3c)),12,delta);}
    private static void init(CdR4ObjectScope c,int n){
        c.ab(n,0x24,2);c.b(n,1,c.b(n,1)|4);c.b(n,0x18,3);c.b(n,0x19,16);c.b(n,0x17,16);c.b(n,0x16,0x40);
        c.w(n,2,0x3a0);c.l(n,4,c.pointer(FALL_SPIKES_MAP));
        c.l(n,0x34,(short)c.w(n,12)<=(short)c.read(STATIC_WATER_Y)?0x3000:0x1000);
        int child=c.spawn(n,true);if(child<0)return;
        c.b(child,0,c.b(n,0));c.b(child,0x28,255);c.w(child,8,c.w(n,8));c.w(child,12,c.w(n,12));c.aw(child,12,0x30);
        c.w(child,0x3e,c.raw(n));c.w(n,0x3c,c.raw(child));c.b(child,0x20,0xb0);c.b(child,1,c.b(n,1));c.b(child,0x18,c.b(n,0x18));
        c.b(child,0x19,16);c.b(child,0x17,16);c.b(child,0x16,16);c.w(child,2,0x3a0);c.l(child,4,c.pointer(FALL_SPIKES_MAP));c.b(child,0x1a,1);
    }
}

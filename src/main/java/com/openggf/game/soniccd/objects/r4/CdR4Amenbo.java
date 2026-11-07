package com.openggf.game.soniccd.objects.r4;
import com.openggf.game.soniccd.objects.CdObjectState;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Data.*;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Read.*;
/** AmenboObject/sub_20D8D8/loc_20D9B6 (objects/r4/amenbo.asm:3-235), both body and missile. */
public final class CdR4Amenbo {
    private CdR4Amenbo() { }
    public static CdObjectState run(CdR4ObjectScope c,int n){return c.run(n,0x2c,()->{
        if((byte)c.b(n,0x28)<0){c.al(n,8,c.l(n,0x2c));animate(c,n);c.draw(n);c.despawn(n,null);return;}
        if(c.future(n))return;c.routine(n,8);
        int routine=c.b(n,0x24);
        if(routine==0){
            c.ab(n,0x24,2);c.b(n,1,c.b(n,1)|4);c.b(n,0x18,3);c.w(n,2,0x23b0);
            c.b(n,0x19,0x14);c.b(n,0x17,0x14);c.b(n,0x16,12);c.b(n,0x20,0x31);
            c.w(n,0x3c,0x78);c.w(n,0x3e,c.w(n,8));boolean second=c.b(n,0x28)!=0;
            // Both native LEAs precede the stores of a1/a2 and motion longs.
            int map=c.pointer(second?AMENBO_MAP2:AMENBO_MAP1),anim=c.pointer(second?AMENBO_ANIM2:AMENBO_ANIM1);
            c.l(n,4,map);c.l(n,0x38,anim);c.l(n,0x30,second?-0x200:-0x600);c.l(n,0x34,second?0xc0:0x100);routine=2;
        }
        switch(routine){
            case 2->{int speed=c.l(n,0x2c)+c.l(n,0x30),min=c.b(n,0x28)==0?-0x18000:-0xc000;
                if(speed<=min){speed=min;c.ab(n,0x24,2);c.l(n,0x30,-c.l(n,0x30));}
                else if(speed>=-min){speed=-min;c.ab(n,0x24,2);c.l(n,0x30,-c.l(n,0x30));}
                c.l(n,0x2c,speed);if(c.b(n,0x28)==0)shoot(c,n);animate(c,n);}
            case 4->{int old=c.l(n,0x2c),speed=old+c.l(n,0x34);
                if((old<0)!=(speed<0)){c.ab(n,0x24,2);c.l(n,0x34,-c.l(n,0x34));speed=0;c.w(n,0x2a,0x3c);}
                c.l(n,0x2c,speed);if(c.b(n,0x28)==0)shoot(c,n);}
            case 6->{c.aw(n,0x2a,-1);if((short)c.w(n,0x2a)<0){c.ab(n,0x24,2);c.flip(n);c.w(n,0x2a,0x3c);}if(c.b(n,0x28)==0)shoot(c,n);}
            case 8->{c.aw(n,0x2a,-1);if((short)c.w(n,0x2a)<0)c.b(n,0x24,2);}
            default->throw new AssertionError();
        }
        c.al(n,8,c.l(n,0x2c));c.w(n,12,c.read(WATER_Y));c.aw(n,12,-8);c.draw(n);c.despawn(n,null);
    });}
    private static void animate(CdR4ObjectScope c,int n){
        c.animate(n,AMENBO_RETAINED_ANIM,c.l(n,0x38));
    }
    private static void shoot(CdR4ObjectScope c,int n){
        c.aw(n,0x3c,-1);if((short)c.w(n,0x3c)>=0)return;c.w(n,0x3c,0x78);
        int dy=(c.read(PLAYER_Y)-c.w(n,12))&65535;
        if(!c.range(dy,-0x60,0xc0))return;
        int dx=(c.read(PLAYER_X)-c.w(n,8))&65535;
        if(!c.range(dx,-0x60,0xc0))return;
        if(((short)dx<0)!=((c.b(n,1)&1)==0))return;
        int child=c.spawn(n,false);if(child<0)return;
        c.b(child,0,c.b(n,0));c.b(child,0x28,0xff);c.w(child,0x2a,c.raw(n));
        c.w(child,8,c.w(n,8));c.w(child,12,c.w(n,12));c.b(child,1,c.b(n,1));
        c.b(child,0x18,c.b(n,0x18));c.ab(child,0x18,1);c.w(child,2,c.w(n,2));
        c.b(child,0x19,4);c.b(child,0x17,4);c.b(child,0x16,4);c.b(child,0x20,0xb2);
        c.l(child,4,c.l(n,4));c.l(child,0x38,c.l(n,0x38));c.b(child,0x1c,1);
        c.l(child,0x2c,(c.b(child,1)&1)==0?-0x20000:0x20000);
        if((byte)c.b(n,1)<0)c.fm(0xa0);
    }
}

package com.openggf.game.soniccd.objects.r4;
import com.openggf.game.soniccd.objects.CdObjectState;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Data.*;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Read.*;
/** TagaTagaObject/TagaMissileObject (objects/r4/taga_taga.asm:3-329). Retains raw links/dirty children. */
public final class CdR4TagaTaga {
    private CdR4TagaTaga() { }
    public static CdObjectState run(CdR4ObjectScope c,int n){return c.run(n,0x2d,()->{
        if((byte)c.b(n,0x28)<0){missile(c,n);return;}if(c.future(n))return;c.routine(n,8);
        switch(c.b(n,0x24)){
            case 0->init(c,n);
            case 2->{
                if(c.b(n,0x28)==0&&inRange(c,n)){c.ab(n,0x24,2);c.w(n,0x2a,0x3c);break;}
                int dx=c.l(n,0x2c);c.al(n,8,dx);
                if(c.b(n,0x28)==0)for(int o:new int[]{0x30,0x32,0x34})c.al(c.link(c.w(n,o)),8,dx);
                c.aw(n,0x2a,-1);if((short)c.w(n,0x2a)<0){
                    c.w(n,0x2a,c.w(n,0x36));c.l(n,0x2c,-c.l(n,0x2c));c.flip(n);
                    if(c.b(n,0x28)==0){
                        int a=c.link(c.w(n,0x30)),b=c.link(c.w(n,0x32)),d=c.link(c.w(n,0x34));
                        c.w(a,8,c.w(n,8));c.w(b,8,c.w(n,8));c.w(d,8,c.w(n,8));
                        int[] offsets={-2,3,7};int[] children={a,b,d};
                        for(int k=0;k<3;k++)c.aw(children[k],8,(c.b(n,1)&1)==0?offsets[k]:-offsets[k]-3);
                    }
                }c.animate(n,TAGA_ANIM);
            }
            case 4->{c.aw(n,0x2a,-1);if((short)c.w(n,0x2a)>=0)break;
                if((byte)c.b(n,1)<0)c.fm(0xb3);
                c.w(n,0x2a,0x3c);c.ab(n,0x24,2);
                if(c.b(n,0x28)==0){int k=0;for(int o:new int[]{0x30,0x32,0x34}){
                    int child=c.link(c.w(n,o));c.l(child,0x2a,new int[]{-0x20000,0x10000,0x20000}[k++]);
                    c.l(child,0x2e,-0x30000);c.l(child,0x32,0);c.l(child,0x36,0x2000);c.ab(child,0x24,2);
                }}
            }
            case 6->{c.aw(n,0x2a,-1);if((short)c.w(n,0x2a)<0){c.ab(n,0x24,2);
                // Routine6 stores default speed before its subtype read; each Guard cut retains that prefix.
                c.l(n,0x2c,0x40000);if(c.b(n,0x28)!=0)c.l(n,0x2c,0x18000);
                if((c.b(n,1)&1)==0)c.l(n,0x2c,-c.l(n,0x2c));}}
            case 8->c.al(n,8,c.l(n,0x2c));
            default->throw new AssertionError();
        }
        c.draw(n);c.despawn(n,null);
    });}
    private static boolean inRange(CdR4ObjectScope c,int n){
        int y=(c.read(PLAYER_Y)-c.w(n,12))&65535;if(!c.range(y,-0x38,0x38))return false;
        int x=(c.read(PLAYER_X)-c.w(n,8))&65535;if((c.b(n,1)&1)==0)x=(-x)&65535;
        return c.range(x,0x28,0x78);
    }
    private static void init(CdR4ObjectScope c,int n){
        c.w(n,0x38,c.w(n,8));c.b(n,1,c.b(n,1)|4);c.b(n,0x18,3);c.w(n,2,0x23d0);
        c.b(n,0x19,0x14);c.b(n,0x17,0x14);c.b(n,0x16,8);c.b(n,0x20,0x2d);c.ab(n,0x24,2);
        c.l(n,4,c.pointer(TAGA_MAP1));c.l(n,0x2c,-0x10000);c.w(n,0x36,0xc8);
        if(c.b(n,0x28)!=0){c.l(n,4,c.pointer(TAGA_MAP2));c.l(n,0x2c,-0x8000);c.w(n,0x36,0x190);return;}
        for(int o:new int[]{0x30,0x32,0x34}){
            int child=c.spawn(n,false);if(child<0){c.delete(n);return;}
            c.b(child,0,c.b(n,0));c.b(child,0x28,0xff);c.b(child,0x3d,c.b(n,0x23));
            c.w(child,0x3e,c.raw(n));c.w(n,o,c.raw(child));
        }
        int k=0;for(int o:new int[]{0x30,0x32,0x34}){
            int child=c.link(c.w(n,o));c.w(child,8,c.w(n,8));c.w(child,12,c.w(n,12));c.b(child,1,c.b(child,1)|4);
            c.w(child,2,0x23d0);c.b(child,0x18,3);c.b(child,0x19,2);c.b(child,0x17,2);c.b(child,0x16,2);
            c.aw(child,8,new int[]{2,-3,-7}[k]);c.aw(child,12,new int[]{-10,-10,-9}[k++]);
        }
    }
    private static void missile(CdR4ObjectScope c,int n){
        int parent=c.link(c.w(n,0x3e));if(c.b(parent,0)!=0x2d||c.b(parent,0x23)!=c.b(n,0x3d)){c.delete(n);return;}
        c.routine(n,4);if(c.b(n,0x24)==0){c.ab(n,0x24,2);c.l(n,4,c.pointer(TAGA_MISSILE_MAP));}
        if(c.b(n,0x24)==4){c.al(n,8,c.l(n,0x2a));c.al(n,12,c.l(n,0x2e));c.al(n,0x2a,c.l(n,0x32));c.al(n,0x2e,c.l(n,0x36));
            if((short)(c.w(n,12)-c.read(PLAYER_Y))>=0xe0){c.delete(n);return;}}
        c.draw(n);
    }
}

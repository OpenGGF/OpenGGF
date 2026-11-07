package com.openggf.game.soniccd.objects.r4;
import com.openggf.game.soniccd.objects.CdObjectState;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Data.*;
/** MaceObject/children (objects/r4/mace.asm:3-230), six dirty lowest-free children in native order. */
public final class CdR4Mace {
    private CdR4Mace() { }
    public static CdObjectState run(CdR4ObjectScope c,int n){return c.run(n,0x36,()->{
        c.routine(n,2);
        if((byte)c.b(n,0x28)<0){
            if(c.b(n,0x24)==0){
                c.ab(n,0x24,2);c.b(n,1,c.b(n,1)|4);
                if(c.b(n,0x28)==255){c.b(n,0x18,3);c.l(n,4,c.pointer(MACE_CHAIN2_MAP));c.w(n,2,0x368);}
                else{c.b(n,0x18,2);c.l(n,4,c.pointer(MACE_CHAIN1_MAP));c.w(n,2,0x370);c.b(n,0x20,0xa9);}
            }
            c.w(n,8,c.w(n,0x2a));c.w(n,12,c.w(n,0x2e));
            if(c.b(c.link(c.w(n,0x3e)),0)!=0x36)c.delete(n);else c.draw(n);return;
        }
        if(c.b(n,0x24)==0){
            c.ab(n,0x24,2);c.b(n,1,c.b(n,1)|4);c.b(n,0x18,3);c.b(n,0x17,8);c.b(n,0x16,8);
            c.l(n,4,c.pointer(MACE_MAP));c.w(n,2,0x368);c.w(n,0x2c,0x100);
            if(c.b(n,0x28)!=0)c.w(n,0x2c,-c.w(n,0x2c));
            for(int o=0x2e;o<=0x38;o+=2){
                int child=c.spawn(n,false);if(child<0){c.delete(n);c.draw(n);c.despawn(n,null);return;}
                c.w(n,o,c.raw(child));c.b(child,0,c.b(n,0));c.b(child,0x28,o==0x38?0xfe:0xff);
                c.b(child,0x17,8);c.b(child,0x16,8);c.w(child,0x3e,c.raw(n));
            }
        }
        c.aw(n,0x2a,c.w(n,0x2c));var sc=c.sine(c.w(n,0x2a)>>>8);
        int sy=(sc.sine()<<16)>>2,sx=(sc.cosine()<<16)>>2;
        int halfY=sy>>1,halfX=sx>>1,quarterY=halfY>>1,quarterX=halfX>>1;
        // Read each raw parent field at its reached cut; an earlier raw self-alias can change a later link.
        for(int k=0;k<6;k++){
            int child=c.link(c.w(n,0x2e+2*k));c.l(child,0x2e,c.l(n,12));c.l(child,0x2a,c.l(n,8));
            switch(k){
                case 0->{c.al(child,0x2e,quarterY);c.al(child,0x2a,quarterX);}
                case 1->{c.al(child,0x2e,halfY);c.al(child,0x2a,halfX);}
                case 2->{c.al(child,0x2e,quarterY);c.al(child,0x2a,quarterX);c.al(child,0x2e,halfY);c.al(child,0x2a,halfX);}
                case 3->{c.al(child,0x2e,sy);c.al(child,0x2a,sx);}
                case 4->{c.al(child,0x2e,quarterY);c.al(child,0x2a,quarterX);c.al(child,0x2e,sy);c.al(child,0x2a,sx);}
                case 5->{c.al(child,0x2e,halfY);c.al(child,0x2a,halfX);c.al(child,0x2e,sy);c.al(child,0x2a,sx);}
                default->throw new AssertionError();
            }
        }
        c.draw(n);c.despawn(n,null);
    });}
}

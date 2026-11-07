package com.openggf.game.soniccd.objects.r4;
import com.openggf.game.soniccd.objects.CdObjectState;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Data.*;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Read.*;
/** SpikesObject (objects/r4/spikes.asm:3-147). Native Solid/Hurt remain Root-owned semantic joins. */
public final class CdR4Spikes {
    private CdR4Spikes() { }
    public static CdObjectState run(CdR4ObjectScope c,int n){return c.run(n,0x22,()->{
        c.routine(n,2);if(c.b(n,0x24)==0){
            c.ab(n,0x24,2);c.b(n,1,c.b(n,1)|4);c.w(n,2,0x3a0);c.b(n,0x18,4);c.l(n,4,c.pointer(SPIKES_MAP));
            if(c.w(n,0x36)==0)c.w(n,0x36,c.w(n,8));c.w(n,0x32,c.w(n,12));
            c.b(n,0x17,0x12);c.b(n,0x16,12);if(c.b(n,0x28)!=0){c.b(n,0x1a,1);c.b(n,0x20,0x83);}
        }
        if(c.b(n,0x28)!=1){move(c,n);c.w(n,12,c.b(n,0x3b)+c.w(n,0x32));}
        if(c.b(n,0x1a)!=0)c.solid(n);
        else if(c.solid(n)&&(c.b(n,0x22)&8)!=0&&c.read(WARPING)==0&&c.read(INVINCIBLE)==0
            &&c.read(PLAYER_ROUTINE)<4&&c.read(PLAYER_HURT_TIMER)==0)c.hurt(n);
        c.draw(n);
        int low=c.w(n,0x34);if(low!=0){int parent=c.link(low);if(c.b(parent,0)==0){c.delete(n);return;}
            c.w(n,8,c.w(parent,8));c.w(n,12,c.w(parent,12));
            c.aw(n,8,(byte)c.b(n,0x38));c.aw(n,12,(byte)c.b(n,0x39));
        }
        c.despawn(n,c.w(n,0x36));
    });}
    private static void move(CdR4ObjectScope c,int n){
        if(c.b(n,0x3a)!=0){c.ab(n,0x3a,-1);if(c.b(n,0x3a)!=0)return;if((c.b(n,1)&128)!=0)c.fm(0xb7);}
        if(c.b(n,0x3c)!=0){int old=c.b(n,0x3b);c.ab(n,0x3b,-8);if(old>=8)return;
            c.b(n,0x3b,0);c.b(n,0x3c,0);c.b(n,0x3a,0x3c);
        }else{c.ab(n,0x3b,8);if(c.b(n,0x3b)<0x20)return;
            c.b(n,0x3b,0x20);c.b(n,0x3c,1);c.b(n,0x3a,0x3c);
        }
    }
}

package com.openggf.game.soniccd.objects.r4;
import com.openggf.game.soniccd.objects.CdObjectState;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Data.*;
/** SpikeBallObject (objects/r4/spike_ball.asm:3-20) and active TestObject fallthrough. */
public final class CdR4SimpleHazards {
    private CdR4SimpleHazards() { }
    public static CdObjectState spikeBall(CdR4ObjectScope c,int n){return c.run(n,0x37,()->{
        if(c.b(n,0x24)==0){c.ab(n,0x24,2);c.b(n,1,c.b(n,1)|4);c.b(n,0x18,3);c.b(n,0x20,0xa9);
            c.b(n,0x19,16);c.b(n,0x17,16);c.b(n,0x16,16);c.w(n,2,0x370);c.l(n,4,c.pointer(SPIKE_BALL_MAP));}
        c.draw(n);c.despawn(n,null);
    });}
    public static CdObjectState testObject(CdR4ObjectScope c,int n){return c.run(n,6,()->c.test(n));}
}

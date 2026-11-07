package com.openggf.game.soniccd.objects;

/** Complete parent/child graph, N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Collision Chaos/Objects/Pocket/Main.asm:14-628, CC_LEGACY_POCKET_ABI=1 for A/B/C/D.
 * The child keeps dirty subtype/routine bytes. Release dereferences the raw physical link even
 * after reuse; child follow only compares parent ID2F. No generation links or RNG are introduced.
 */
public final class CdPocket {
    private CdPocket() { }
    public static CdPinballContext.Result run(CdPinballContext c,int wholeIndex){return c.run(wholeIndex,0x2f,()->{
        if((c.image().b(0x28)&128)==0)child(c);else parent(c);});}
    private static void parent(CdPinballContext c){var i=c.image();CdPinballContext.routine(i,0,2,4,6,8,10,12,14,16,18,20);
        int routine=i.b(0x24);
        if(routine==0){if(c.period()==2&&c.goodFuture()!=0){c.delete();tail(c);return;}
            i.b(0x24,2);i.b(1,i.b(1)|4);i.b(0x18,0);i.w(2,0x3af);i.l(4,c.assets.pocketParent().mappingAddress());i.w(0x30,7);c.put(i);
            routine=2;
        }
        if(routine==2){waitForPlayer(c);tail(c);return;}
        i=c.image();i.w(0x2a,i.w(0x2a)-1);c.put(i);
        if((short)i.w(0x2a)>=0){tail(c);return;}
        switch(routine){
            case 4 -> {advance(c,6,6,2);if(!c.pocketChild())c.delete();else c.control(64,0);}
            case 6 -> advance(c,8,2,3);
            case 8 -> advance(c,10,30,0);
            case 10,12 -> {advance(c,routine+2,30,-1);i=c.image();if(i.w(0x30)!=0){i.w(0x30,i.w(0x30)-1);c.put(i);c.points();}}
            case 14 -> advance(c,16,4,3);
            case 16 -> advance(c,18,6,2);
            case 18 -> {
                // Source FM call precedes ALL release stores, including routine/timer/child/control.
                c.sound(CdPinballContext.SoundKind.FM,0x9f);advance(c,20,120,0);
                i=c.image();c.releaseChild(i.w(0x2c));i=c.image();
                if(i.b(0x3e)!=0){i.b(0x3e,0);c.put(i);c.control(0,65);}
            }
            case 20 -> {i.b(0x24,2);i.b(0x3e,0);c.put(i);}
            default -> throw new AssertionError("Prevalidated Pocket routine");
        }
        tail(c);
    }
    private static void waitForPlayer(CdPinballContext c){if(c.debug()!=0)return;var i=c.image();var p=c.motion();
        if(p.yVelocity()>=0&&p.routine()!=4&&p.routine()!=6
                &&CdPinballContext.within(c.playerY()-i.w(12),24,16)
                &&CdPinballContext.within(c.playerX()-i.w(8),24,48)){
            i.b(0x3e,255);c.put(i);c.capturePlayer(i.w(8),(i.w(12)-16)&65535);
        }
        // The source checks retained caught even when CheckPlayer returned without a new capture.
        if(c.image().b(0x3e)!=0){advance(c,4,8,1);c.sound(CdPinballContext.SoundKind.FM,0xd7);}
    }
    private static void advance(CdPinballContext c,int routine,int timer,int frame){var i=c.image();i.b(0x24,routine);i.w(0x2a,timer);
        if(frame>=0)i.b(0x1a,frame);c.put(i);}
    private static void tail(CdPinballContext c){c.draw();c.despawn(c.image().w(8),false);}
    private static void child(CdPinballContext c){var i=c.image();CdPinballContext.routine(i,0,2,4);
        if(i.b(0x24)==4){c.delete();return;}
        if(i.b(0x24)==0){i.b(0x24,2);i.b(1,i.b(1)|4);i.b(0x18,0);i.b(0x19,24);i.b(0x16,8);i.w(2,0x43af);
            i.l(4,c.assets.pocketChild().mappingAddress());c.put(i);}
        if(c.parentId(i.w(0x2e))!=0x2f){c.delete();return;}
        c.animate(c.assets.pocketChild());c.draw();
    }
}

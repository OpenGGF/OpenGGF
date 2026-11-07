package com.openggf.game.soniccd.objects;

/** ObjFlipper_Init/Main/CheckPlayer, N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * CC Bumper and Flipper/Main.asm:215-345; B/C/D objects/r3/bumper_flipper.asm:FlipperCheckPlayer.
 * Jump remains exclusively CdPlayerMovement.checkJump: FM_SPRING precedes its single animation patch.
 */
public final class CdFlipper {
    private CdFlipper() { }
    public static CdPinballContext.Result run(CdPinballContext c,int wholeIndex){return c.run(wholeIndex,0x1e,()->body(c));}
    private static void body(CdPinballContext c){var i=c.image();CdPinballContext.routine(i,0,2);
        if(i.b(0x24)==0){i.b(0x24,2);i.l(4,c.assets.flipper().mappingAddress());i.w(2,0x49d);i.b(1,4);
            i.b(0x18,1);i.b(0x19,32);i.b(0x16,16);c.put(i);}
        if(i.b(0x1c)==0)check(c);
        c.animate(c.assets.flipper());c.draw();c.despawn(c.image().w(8),false);
    }
    private static void check(CdPinballContext c){var i=c.image();var p=c.motion();
        int dx=(c.playerX()-i.w(8)+i.b(0x19))&65535;
        if(p.animation()==0x2b||(short)dx<0||dx>=2*i.b(0x19)){off(c);return;}
        int column=(c.playerX()-((i.w(8)-i.b(0x19))&65535))&65535;
        if((i.b(0x22)&1)!=0)column=(64-column)&65535;
        boolean bottom=p.yVelocity()<0;
        int slopeY=(i.w(12)+c.assets.flipperSlope(bottom,column))&65535;
        int radius=bottom?-p.yRadius():p.yRadius();
        int penetration=(c.playerY()+radius-slopeY+8)&65535;
        if((short)penetration<0||penetration>=16){off(c);return;}
        c.playerY((slopeY-radius)&65535);
        if(bottom)c.velocities(p.xVelocity(),0,p.xVelocity());
        else c.attachment(CdPinballContext.AttachmentKind.STAND_ON_OBJECT);
    }
    private static void off(CdPinballContext c){c.attachment(CdPinballContext.AttachmentKind.GET_OFF_OBJECT);}
}

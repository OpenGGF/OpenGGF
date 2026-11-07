package com.openggf.game.soniccd.objects;

/** ObjBumperPlatform_Init/Main N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * CC Objects/Bumper Platform/Main.asm:30-167 and objects/r3/bumper_platform.asm:loc_20B1DA..loc_20B278.
 * Collision always selects native primary player; full subtype WORD selects X/Y movement.
 */
public final class CdBumperPlatform {
    private CdBumperPlatform() { }
    public static CdPinballContext.Result run(CdPinballContext c,int wholeIndex){return c.run(wholeIndex,0x2a,()->body(c));}
    private static void body(CdPinballContext c){var i=c.image();CdPinballContext.routine(i,0,2);
        if(i.b(0x24)==0){i.w(0x3e,i.w(8));i.b(0x24,2);i.l(4,c.assets.platform().mappingAddress());i.w(2,0x435e);
            i.b(1,4);i.b(0x19,32);i.b(0x16,16);i.b(0x18,1);i.b(0x20,0xe7);
            boolean wide=(i.b(0x28)&128)!=0;i.w(0x30,wide?192:160);i.w(0x2e,wide?96:80);
            i.l(0x2a,(i.b(0x28)&64)!=0?0x10000:-0x10000);c.put(i);}
        if(i.b(0x21)!=0){if((i.b(1)&128)!=0)c.sound(CdPinballContext.SoundKind.FM,0xb5);
            i=c.image();i.b(0x21,0);c.put(i);
            int dx=(short)(c.playerX()-i.w(8)),dy=(short)(c.playerY()-i.w(12));var p=c.motion();
            if(CdPinballContext.within(dy,8,16))c.velocities(0,p.yVelocity(),p.groundVelocity());
            else if(CdPinballContext.within(dx,24,48))c.launch(p.xVelocity(),dy<0?-0x700:0x700,false);
            else c.launch(dx<0?-0x4f3:0x4f3,dy<0?-0x4f3:0x4f3,false);
        }
        i=c.image();CdPinballContext.move(i,false);c.put(i);c.draw();c.despawn(c.image().w(0x3e),true);
    }
}

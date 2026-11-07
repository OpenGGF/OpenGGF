package com.openggf.game.soniccd.objects;

/** ObjBumper_Init/Main, N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Collision Chaos/Objects/Bumper and Flipper/Main.asm:39-180;
 * B/C/D src/objects/r3/bumper_flipper.asm:BumperObject_0_Routine0/2.
 * Shipped legacy ABI1 uses shared SpawnPoints, not the alternate ABI0 helper.
 */
public final class CdBumper {
    private CdBumper() { }
    public static CdPinballContext.Result run(CdPinballContext c,int wholeIndex){return c.run(wholeIndex,0x1d,()->body(c));}
    private static void body(CdPinballContext c){var i=c.image();CdPinballContext.routine(i,0,2);
        if(i.b(0x24)==0){i.b(0x24,2);i.l(4,c.assets.bumper().mappingAddress());i.w(2,0x4ce);i.b(1,4);
            i.b(0x17,16);i.b(0x19,16);i.b(0x16,16);i.b(0x18,1);i.b(0x20,0xd7);i.w(0x32,i.w(8));i.w(0x34,5);
            int subtype=i.b(0x28),range=((subtype&2)!=0?64:0)+((subtype&1)!=0?32:0);
            i.w(0x30,range);i.w(0x2e,range>>>1);
            // Zero range does NOT clear a dirty oVar2A long.
            if(range!=0)i.l(0x2a,(subtype&64)!=0?0x10000:-0x10000);
            c.put(i);
        }
        i=c.image();CdPinballContext.move(i,true);c.put(i);
        if(i.b(0x21)!=0){
            if(i.w(0x34)!=0){i.w(0x34,i.w(0x34)-1);c.put(i);c.points();}
            i=c.image();if((i.b(1)&128)!=0)c.sound(CdPinballContext.SoundKind.SUB_CPU,0x7f);
            i=c.image();i.b(0x21,0);c.put(i);
            int angle=c.math.calcAngle((short)(i.w(8)-c.playerX()),(short)(i.w(12)-c.playerY()));
            var trig=c.math.calcSine(angle);
            c.launch((short)(((short)trig.cosine()*-0x700)>>8),(short)(((short)trig.sine()*-0x700)>>8),true);
            i=c.image();i.b(0x1c,1);c.put(i);c.incrementBumperSave();
        }
        c.animate(c.assets.bumper());c.draw();c.despawn(c.image().w(0x32),true);
    }
}

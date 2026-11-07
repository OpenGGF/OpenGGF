package com.openggf.game.soniccd.objects;

/** ObjGlassShatter N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * CC Objects/Glass Shatter/Main.asm:24-71; B/C/D objects/r3/glass_shatter.asm:GlassShatterObject_0_Routine0/2/4.
 * This body runs only after the existing PlayerCheckBlock has performed its actual terrain mutation
 * and allocated ID29. It neither performs nor certifies that earlier shared owner invocation.
 */
public final class CdGlassShatter {
    private CdGlassShatter() { }
    public static CdPinballContext.Result run(CdPinballContext c,int wholeIndex){return c.run(wholeIndex,0x29,()->body(c));}
    private static void body(CdPinballContext c){var i=c.image();CdPinballContext.routine(i,0,2,4);
        if(i.b(0x24)==0){i.b(0x24,2);i.b(1,i.b(1)|4);i.b(0x18,3);i.b(0x17,16);i.b(0x19,16);i.b(0x16,16);
            i.w(2,c.act()==2?0x642a:0x638b);i.l(4,c.assets.glass().mappingAddress());c.put(i);c.points();
            c.animate(c.assets.glass());
        }else if(i.b(0x24)==2)c.animate(c.assets.glass());else c.delete();
        // JSR routine then JMP Draw, including routine4's now-zero image.
        c.draw();
    }
}

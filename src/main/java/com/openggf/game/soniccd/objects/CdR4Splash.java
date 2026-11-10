package com.openggf.game.soniccd.objects;

import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.*;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.Value.*;

/** SplashObject, src/objects/r4/splash.asm:3-44, N=1ecc5f3. All call failures keep reached stores. */
public final class CdR4Splash {
    private CdR4Splash() { }
    public static void run(Slot o, Calls s, int mappingsBits) {
        int routine = read(o, B.ROUTINE);
        if (routine == 4) { s.delete(o); return; }
        if (routine == 0) {
            write(o, B.ROUTINE, read(o, B.ROUTINE) + 2);
            write(o, B.SPRITE_FLAGS, 4); write(o, B.LAYER, 1);
            o.write(L.MAPPINGS, mappingsBits); write(o, W.TILE, 0x298);
            write(o, B.ANIM, read(o, B.SUBTYPE));
            s.sound(read(o, B.SUBTYPE) < 2 ? 0xA2 : 0xA1);
        } else if (routine != 2) throw new UnsupportedOperationException("Splash routine " + routine);
        write(o, W.Y, read(s, WATER_Y)); write(o, W.Y, read(o, W.Y) + 4);
        s.animate(o, Animation.SPLASH); s.draw(o);
    }
}

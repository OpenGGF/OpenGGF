package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.*;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.Value.*;

/** WaterSurfaceObject, src/objects/r4/water_surface.asm:3-49, N=1ecc5f3.
 * Borrow the actual two-byte offset table and mapping LONG from the media owner. No art loading.
 */
public final class CdR4WaterSurface {
    private CdR4WaterSurface() { }
    public static void run(Slot o, Calls s, int mappingsBits, CdAssetBytes offsets) {
        int routine = read(o, B.ROUTINE);
        if (routine == 0) {
            write(o, B.SPRITE_FLAGS, read(o, B.SPRITE_FLAGS) | 4);
            write(o, W.TILE, 0x8280); o.write(L.MAPPINGS, mappingsBits);
            write(o, B.ROUTINE, read(o, B.ROUTINE) + 2);
        } else if (routine != 2) throw new UnsupportedOperationException("WaterSurface routine " + routine);
        int x = (read(s, CAMERA_X) + 0xA0) & 0xFFFF;
        int phase = (read(o, B.PHASE) + 1) & 0xFF;
        if (phase >= 2) phase = 0;
        write(o, B.PHASE, phase);
        write(o, W.X, x + offsets.signedByte(phase));
        write(o, W.Y, read(s, WATER_Y)); write(o, W.Y, read(o, W.Y) + 2);
        s.animate(o, Animation.WATER_SURFACE); s.draw(o);
    }
}

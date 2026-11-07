package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR31SolidHazardAssets;
import java.util.Objects;

/**
 * N1ecc5f3 CC Drum Platform/Main.asm:18-294. Component-only: zero layout rows in R31A/B/C/D.
 * Does not imply a natural route or current-art installation for this dispatchable body.
 */
public final class CdDrumPlatform {
    private final CdR31SolidHazardAssets assets;
    public CdDrumPlatform(CdR31SolidHazardAssets assets) { this.assets = Objects.requireNonNull(assets); }
    public void run(CdObjectPool staging, int wholeIndex, CdCcObjectServices services) {
        var c = new CdCcBodyCursor(staging, wholeIndex, 0x28, services); c.routine(6);
        if (c.b(0x24) == 0) {
            c.b(1, c.b(1) | 4); c.w(2, 0x36A); c.b(0x18, 3); c.map(assets.drumMap()); c.b(0x19, 0x18);
            c.w(0x32, c.w(8)); c.w(0x30, c.w(12)); c.b(0x24, 2); c.b(0x3B, c.b(0x28));
            if (c.b(0x28) >= 2) c.b(0x24, 4); if (c.b(0x28) >= 6) c.b(0x24, 6);
        } else cycle(c);
        c.draw(); c.tail(0x32);
    }
    private void top(CdCcBodyCursor c) {
        if ((c.b(1) & 0x80) == 0) return;
        if (c.services.timeZone() == 0) {
            int frame = c.b(0x1A);
            if (frame == 2) c.b(0x16, 8); else if (frame >= 3 && frame <= 6) c.b(0x16, 5);
        }
        c.contact(CdSolidObject.Entry.TOP_SOLID_OBJECT);
    }
    private void cycle(CdCcBodyCursor c) {
        int routine = c.b(0x24);
        if (routine == 4) top(c);
        if (c.b(0x3A) == 0) {
            if (routine != 4) c.contact(CdSolidObject.Entry.GET_OFF_OBJECT);
            load(c); return;
        }
        if (c.services.timeZone() == 0 || c.services.animationArtTimer() != 0) return;
        c.w(0x12, c.w(0x36)); c.moveY(c.w(0x36)); c.b(0x3A, c.b(0x3A) - 1);
        if (c.b(0x3A) != 0) return;
        c.b(0x3B, c.b(0x3B) + 1);
        int end = routine == 2 ? 2 : routine == 4 ? 6 : 18;
        if (c.b(0x3B) < end) { cycle(c); return; }
        if (routine == 6) { c.w(0x3A, 0); c.b(0x24, 2); } else c.b(0x24, routine + 2);
    }
    private void load(CdCcBodyCursor c) {
        int at = (c.b(0x3B) * 3) & 255;
        // MOVE.B to the HIGH velocity byte retains the dirty fractional low byte.
        c.b(0x36, assets.drumMotion().unsignedByte(at)); c.b(0x16, assets.drumMotion().unsignedByte(at + 1));
        c.b(0x1A, assets.drumMotion().unsignedByte(at + 2)); c.b(0x3A, 4);
        c.b(0x18, assets.drumPriorities().unsignedByte(c.b(0x1A)));
    }
}

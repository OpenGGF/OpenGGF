package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR31SolidHazardAssets;
import java.util.Objects;

/** N1ecc5f3 CC Door/Main.asm:39-183, shipped delay bytes are stored but never counted here. */
public final class CdDoor {
    private final CdR31SolidHazardAssets assets;
    public CdDoor(CdR31SolidHazardAssets assets) { this.assets = Objects.requireNonNull(assets); }
    public void run(CdObjectPool staging, int wholeIndex, CdCcObjectServices services) {
        var c = new CdCcBodyCursor(staging, wholeIndex, 0x26, services); c.routine(2);
        if (c.b(0x24) == 0) {
            c.b(1, c.b(1) | 4); c.w(2, 0x371); c.b(0x18, 3); c.map(assets.doorMap());
            c.b(0x19, 0x1C); c.b(0x16, 8); c.w(0x36, c.w(8)); c.w(0x32, c.w(12));
            c.b(0x3A, assets.doorDelays().unsignedByte(c.b(0x28) & 15)); c.b(0x24, 2);
        }
        boolean positive = (byte) c.b(0x28) < 0;
        if ((services.primaryY() & 0xFFFF) >= c.w(12)) {
            c.b(0x3B, c.b(0x3B) + 0x10);
            if (c.b(0x3B) >= 0x40) { c.b(0x3B, 0x40); c.b(0x3A, 8); }
        } else {
            int prior = c.b(0x3B); c.b(0x3B, prior - 0x10);
            if (prior < 0x10) {
                c.b(0x3B, 0);
                if ((c.b(0x28) & 15) == 0 && c.b(0x3C) == 0) { c.b(0x3A, 0x3C); c.b(0x3C, 0xFF); }
            }
        }
        c.w(8, c.w(0x36) + (positive ? c.b(0x3B) : -c.b(0x3B)));
        c.contact(CdSolidObject.Entry.TOP_SOLID_OBJECT); c.draw(); c.tail(0x36);
    }
}

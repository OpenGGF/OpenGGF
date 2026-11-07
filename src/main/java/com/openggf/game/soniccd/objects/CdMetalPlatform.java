package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR31SolidHazardAssets;
import java.util.Objects;

/** N1ecc5f3 ObjMetalPlatform (scddisasm/src/Level/Collision Chaos/Objects/Metal Platform/Main.asm:39-652), shipped legacy setup returns and contact order. */
public final class CdMetalPlatform {
    private final CdR31SolidHazardAssets assets;
    public CdMetalPlatform(CdR31SolidHazardAssets assets) { this.assets = Objects.requireNonNull(assets); }
    public void run(CdObjectPool staging, int wholeIndex, CdCcObjectServices services) {
        var c = new CdCcBodyCursor(staging, wholeIndex, 0x23, services); c.routine(2);
        if (c.b(0x24) == 0) {
            c.b(1, c.b(1) | 4); c.w(2, 0x34E); c.b(0x18, 3); c.map(assets.metalMap());
            c.b(0x19, 0x20); c.b(0x16, 0x10); c.w(0x32, c.w(8)); c.w(0x30, c.w(12)); c.b(0x24, 2);
        }
        // ADD.B subtype,subtype in the historical dispatch wraps before the table lookup.
        int type = ((c.b(0x28) * 2) & 255) / 2;
        switch (type) {
            case 0 -> { c.b(0x3F, 1); top(c); }
            case 1, 2 -> horizontal(c);
            case 3, 4 -> vertical(c);
            case 5, 6, 7 -> drop(c);
            default -> throw new UnsupportedOperationException("CC metal behavior " + type);
        }
        if (c.b(0x3F) != 0) {
            if ((c.b(0x22) & 8) != 0) {
                if (c.b(0x3E) != 0x40) { c.b(0x3E, c.b(0x3E) + 8); bob(c); }
            } else if (c.b(0x3E) != 0) { c.b(0x3E, c.b(0x3E) - 8); bob(c); }
        }
        c.tail(0x32); c.draw(); // Native metal despawns inside Update, before wrapper Draw.
    }
    private static int top(CdCcBodyCursor c) { return c.contact(CdSolidObject.Entry.TOP_SOLID_OBJECT); }
    private static void bob(CdCcBodyCursor c) {
        int product = (short) c.services.sine(c.b(0x3E)) * 0x400;
        c.w(12, c.w(0x30) + (product >>> 16));
    }
    private void load(CdCcBodyCursor c, CdAssetBytes table, int base) {
        int at = base + ((c.b(0x39) * 4) & 255);
        c.b(0x38, table.unsignedByte(at)); c.w(0x36, table.signedByte(at + 1)); c.w(0x34, table.unsignedWord(at + 2));
    }
    private static void advanceLoop(CdCcBodyCursor c) {
        c.b(0x39, c.b(0x39) + 1); if (c.b(0x39) == 8) c.b(0x39, 2);
    }
    private void horizontal(CdCcBodyCursor c) {
        if (c.b(0x38) == 0) {
            c.b(0x3F, 1); load(c, c.b(0x28) == 1 ? assets.metalRight() : assets.metalLeft(), 0); top(c); return;
        }
        c.w(0x10, c.w(0x34)); c.moveX(c.w(0x34)); c.w(0x34, c.w(0x34) + c.w(0x36));
        c.b(0x38, c.b(0x38) - 1);
        if (c.b(0x38) == 0) { advanceLoop(c); horizontal(c); } else top(c);
    }
    private void vertical(CdCcBodyCursor c) {
        top(c);
        if (c.b(0x38) == 0) {
            c.b(0x3F, 0); load(c, c.b(0x28) == 3 ? assets.metalUp() : assets.metalDown(), 0); return;
        }
        c.moveY(c.w(0x34)); c.w(0x34, c.w(0x34) + c.w(0x36)); c.w(0x12, c.w(0x34) & 0xFF00);
        c.b(0x38, c.b(0x38) - 1);
        if (c.b(0x38) == 0) { advanceLoop(c); vertical(c); }
    }
    private void drop(CdCcBodyCursor c) {
        top(c);
        switch (c.b(0x3A)) {
            case 0 -> {
                if (c.b(0x38) == 0) {
                    c.b(0x3F, 1); if (top(c) != 0) c.b(0x38, 0x1E);
                } else {
                    c.b(0x38, c.b(0x38) - 1);
                    if (c.b(0x38) == 0) { c.b(0x3F, 0); c.b(0x3A, c.b(0x3A) + 2); }
                }
            }
            case 2 -> dropMove(c);
            case 4 -> { }
            default -> throw new UnsupportedOperationException("CC metal drop routine " + c.b(0x3A));
        }
    }
    private void dropMove(CdCcBodyCursor c) {
        if (c.b(0x38) == 0) {
            var table = c.b(0x28) == 5 ? assets.metalDrop5() : c.b(0x28) == 6 ? assets.metalDrop6() : assets.metalDrop7();
            load(c, table, 0); return;
        }
        c.w(0x34, c.w(0x34) + c.w(0x36)); c.w(0x12, c.w(0x34) & 0xFF00); c.moveY(c.w(0x34));
        c.b(0x38, c.b(0x38) - 1);
        if (c.b(0x38) != 0) return;
        c.b(0x39, c.b(0x39) + 1);
        if (c.b(0x39) != 3) { dropMove(c); return; }
        c.w(0x12, 0); c.b(0x3F, 1); c.w(0x30, c.w(12)); c.b(0x3A, c.b(0x3A) + 2);
    }
}

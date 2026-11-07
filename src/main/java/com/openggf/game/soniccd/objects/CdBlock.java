package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR31SolidHazardAssets;
import java.util.Objects;

/** N1ecc5f3 CC Block/Main.asm:30-214, shipped CC_LEGACY_BLOCK_ABI=1 in A/B/C/D. */
public final class CdBlock {
    private final CdR31SolidHazardAssets assets;
    public CdBlock(CdR31SolidHazardAssets assets) { this.assets = Objects.requireNonNull(assets); }
    public void run(CdObjectPool staging, int wholeIndex, CdCcObjectServices services) {
        var c = new CdCcBodyCursor(staging, wholeIndex, 0x21, services); c.routine(2);
        boolean deleted = false;
        if (c.b(0x24) == 0) {
            c.b(0x24, 2); c.b(1, c.b(1) | 4); c.w(2, 0x433E); c.b(0x18, 3); c.map(assets.blockMap());
            c.b(0x19, 0x10); c.b(0x16, 0x10); c.w(0x32, c.w(8)); c.w(0x30, c.w(12));
            if (c.b(0x28) >= 2) {
                var child = staging.findNextObjSlotAfterWholeIndex(wholeIndex);
                if (child.isEmpty()) { staging.deleteObject(wholeIndex); deleted = true; }
                else {
                    int at = child.getAsInt();
                    c.childByte(at, 0, 0x0A); c.childWord(at, 8, c.w(8)); c.childWord(at, 12, c.w(12));
                    c.childByte(at, 0x39, 0xEA); c.childWord(at, 0x34, c.lowAddress());
                    c.childByte(at, 0x28, (c.b(0x28) & 1) * 2);
                }
            }
        }
        if (!deleted) {
            c.contact(CdSolidObject.Entry.SOLID_OBJECT);
            if (services.timeZone() != 0 && c.b(0x28) != 1) {
                if (c.b(0x3A) != 0) {
                    c.w(0x36, c.w(0x36) + c.w(0x38)); c.w(0x10, c.w(0x36)); c.moveX(c.w(0x36));
                    c.b(0x3A, c.b(0x3A) - 1);
                    if (c.b(0x3A) == 0) { c.b(0x3B, (c.b(0x3B) + 1) & 3); load(c); }
                } else load(c);
            }
        }
        c.draw(); c.tail(0x32);
    }
    private void load(CdCcBodyCursor c) {
        int at = (c.b(0x3B) * 4) & 255;
        c.b(0x3A, assets.blockMotion().unsignedByte(at));
        c.w(0x38, assets.blockMotion().signedByte(at + 1));
        c.w(0x36, assets.blockMotion().unsignedWord(at + 2));
    }
}

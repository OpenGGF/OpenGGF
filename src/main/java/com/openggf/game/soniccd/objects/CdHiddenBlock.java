package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR31SolidHazardAssets;
import java.util.Objects;

/** N1ecc5f3 CC Block/Main.asm:218-428, including dirty attached Spring/CC Spikes children. */
public final class CdHiddenBlock {
    private final CdR31SolidHazardAssets assets;
    public CdHiddenBlock(CdR31SolidHazardAssets assets) { this.assets = Objects.requireNonNull(assets); }
    public void run(CdObjectPool staging, int wholeIndex, CdCcObjectServices services) {
        var c = new CdCcBodyCursor(staging, wholeIndex, 0x22, services); c.routine(2);
        if (c.b(0x24) == 0) {
            c.b(1, c.b(1) | 4); c.w(2, 0x39F); c.b(0x18, 3); c.map(assets.hiddenMap());
            c.b(0x1A, c.b(0x28) >>> 2); c.b(0x19, 0x10); c.b(0x16, 0xE);
            c.w(0x36, c.w(8)); c.w(0x32, c.w(12)); c.b(0x24, 2);
            int kind = c.b(0x28) & 3;
            if (kind != 0) {
                var child = staging.findNextObjSlotAfterWholeIndex(wholeIndex);
                if (child.isPresent()) {
                    int at = child.getAsInt(), id = kind == 1 ? 0x20 : 0xA;
                    c.childByte(at, 0, id); c.childWord(at, 8, c.w(8)); c.childWord(at, 12, c.w(12));
                    c.childWord(at, 0x34, c.lowAddress()); c.childByte(at, 0x38, 0x20); c.childByte(at, 0x28, 2);
                    if (id != 0x20) { c.childByte(at, 0x38, 0x18); c.childByte(at, 0x28, 4 | ((c.b(0x28) & 1) * 2)); }
                }
            }
        }
        int left = (c.w(8) - (byte) c.b(0x19)) & 0xFFFF, player = services.primaryX() & 0xFFFF;
        if (player < left) {
            c.b(0x3B, c.b(0x3B) + 8); if (c.b(0x3B) >= 0x20) c.b(0x3B, 0x20);
        } else if (((player - left) & 0xFFFF) >= 0x50) {
            int prior = c.b(0x3B); c.b(0x3B, prior - 8); if (prior < 8) c.b(0x3B, 0);
        }
        c.w(12, c.w(0x32) + ((c.b(0x28) & 4) == 0 ? c.b(0x3B) : -c.b(0x3B)));
        c.contact(CdSolidObject.Entry.SOLID_OBJECT); c.draw(); c.tail(0x36);
    }
}

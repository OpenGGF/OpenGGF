package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR31SolidHazardAssets;
import java.util.Objects;

/** CC ID$20 only. N1ecc5f3 CC Spikes/Main.asm:18-244; PPZ ID$26 is untouched. */
public final class CdCcSpikes {
    private final CdR31SolidHazardAssets assets;
    public CdCcSpikes(CdR31SolidHazardAssets assets) { this.assets = Objects.requireNonNull(assets); }
    public void run(CdObjectPool staging, int wholeIndex, CdCcObjectServices services) {
        var c = new CdCcBodyCursor(staging, wholeIndex, 0x20, services); c.routine(2);
        if (c.b(0x24) == 0) {
            c.b(0x24, c.b(0x24) + 2); c.b(1, c.b(1) | 4);
            c.w(2, services.act() == 2 ? 0x3FC : 0x31E); c.b(0x18, 3); c.map(assets.spikesMap());
            c.w(0x36, c.w(8)); c.w(0x32, c.w(12));
            int frame = c.b(0x28) & 3; c.b(0x1A, frame);
            c.b(0x19, assets.spikesProperties().unsignedByte(frame * 3));
            c.b(0x16, assets.spikesProperties().unsignedByte(frame * 3 + 1));
            c.b(0x20, assets.spikesProperties().unsignedByte(frame * 3 + 2));
        }
        if ((c.b(0x28) & 4) != 0) {
            offset(c);
            int direction = c.b(0x28) & 3, delta = c.b(0x3B);
            c.w(direction < 2 ? 12 : 8, c.w(direction < 2 ? 0x32 : 0x36)
                    + (direction == 1 || direction == 2 ? -delta : delta));
        }
        boolean top = c.b(0x1A) == 0;
        int contact = c.contact(CdSolidObject.Entry.SOLID_OBJECT);
        if (top && contact != 0 && (c.b(0x22) & 8) != 0
                && services.timeWarp() == 0 && services.invincible() == 0
                && services.primaryRoutine() < 4 && services.primaryHurt() == 0) {
            services.rewindPrimaryYBeforeHurt(); services.hurtPrimary(staging, wholeIndex);
        }
        c.draw();
        int parent = c.w(0x34);
        if (parent != 0) {
            c.w(8, services.parentX(parent, staging)); c.w(12, services.parentY(parent, staging));
            c.w(8, c.w(8) + (byte) c.b(0x38)); c.w(12, c.w(12) + (byte) c.b(0x39));
        }
        c.tail(0x36);
    }
    private static void offset(CdCcBodyCursor c) {
        if (c.b(0x3A) != 0) {
            c.b(0x3A, c.b(0x3A) - 1);
            if (c.b(0x3A) != 0) return;
            if ((c.b(1) & 0x80) != 0) c.services.playFm(0xB7);
        }
        if (c.b(0x3C) != 0) {
            int prior = c.b(0x3B); c.b(0x3B, prior - 8);
            if (prior >= 8) return;
            c.b(0x3B, 0); c.b(0x3C, 0); c.b(0x3A, 0x3C);
        } else {
            c.b(0x3B, c.b(0x3B) + 8);
            if (c.b(0x3B) < 0x20) return;
            c.b(0x3B, 0x20); c.b(0x3C, 1); c.b(0x3A, 0x3C);
        }
    }
}

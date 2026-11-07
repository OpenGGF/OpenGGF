package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR31SolidHazardAssets;
import java.util.Objects;

/** N1ecc5f3 CC Fire Shooter/Main.asm:48-564; all three roles, shipped legacy operand widths. */
public final class CdFireShooter {
    private final CdR31SolidHazardAssets assets;
    public CdFireShooter(CdR31SolidHazardAssets assets) { this.assets = Objects.requireNonNull(assets); }
    public void run(CdObjectPool staging, int wholeIndex, CdCcObjectServices services) {
        var c = new CdCcBodyCursor(staging, wholeIndex, 0x27, services);
        int role = c.b(0x28);
        switch (role) {
            case 1 -> flame(c);
            case 2 -> projectile(c);
            default -> emitter(c);
        }
        c.draw();
        if (role == 2) services.checkProjectileDespawn(staging, wholeIndex); else c.tail(0x36);
    }
    private void base(CdCcBodyCursor c, int priority) {
        c.b(1, c.b(1) | 4); c.w(2, 0x379); c.b(0x18, priority); c.map(assets.fireMap());
    }
    private void emitter(CdCcBodyCursor c) {
        c.routine(2); if (c.b(0x24) != 0) return;
        base(c, 2); c.w(0x36, c.w(8)); c.w(0x32, c.w(12)); c.b(0x19, 0x10);
        var free = c.pool.findObjSlot(); if (free.isEmpty()) return;
        int at = free.getAsInt(); c.childByte(at, 0, 0x27); c.childWord(at, 8, c.w(8));
        c.childWord(at, 12, c.w(12) + 8); c.childByte(at, 0x28, 1); c.b(0x24, c.b(0x24) + 2);
    }
    private int solid(CdCcBodyCursor c) { return c.contact(CdSolidObject.Entry.SOLID_OBJECT); }
    private void flame(CdCcBodyCursor c) {
        c.routine(8);
        switch (c.b(0x24)) {
            case 0 -> {
                base(c, 3); c.b(0x19, 0x10); c.b(0x16, 0x14); c.w(0x36, c.w(8)); c.w(0x32, c.w(12));
                c.b(0x1A, 1); c.b(0x24, 2); // Init returns; no detection/Solid in this frame.
            }
            case 2 -> detect(c);
            case 4 -> raise(c);
            case 6 -> lower(c);
            case 8 -> {
                if (c.b(0x3A) == 0) c.b(0x24, 2);
                else { c.b(0x3A, c.b(0x3A) - 1); solid(c); }
            }
            default -> throw new AssertionError();
        }
    }
    private void detect(CdCcBodyCursor c) {
        if (c.b(0x3A) == 0) {
            int x = c.w(8), player = c.services.primaryX() & 0xFFFF;
            int distance = (x - player) & 0xFFFF; if (x < player) distance = (-distance) & 0xFFFF;
            if (distance >= 0x78) { solid(c); return; }
            c.b(0x3A, 0x3C);
        }
        c.b(0x3A, c.b(0x3A) - 1);
        if (c.b(0x3A) == 0) { c.w(0x3A, 0); c.b(0x1A, 1); c.b(0x24, c.b(0x24) + 2); return; }
        if (activePeriod(c)) c.animate(assets.fireAnimation());
        solid(c);
    }
    private static boolean activePeriod(CdCcBodyCursor c) {
        if (c.services.timeZone() == 0) return false;
        if (c.services.timeZone() == 1) return true;
        return c.services.goodFuture() == 0;
    }
    private void raise(CdCcBodyCursor c) {
        c.b(0x3B, c.b(0x3B) + 8); if (c.b(0x3B) >= 0x20) c.b(0x3B, 0x20);
        c.w(12, c.w(0x32) - (byte) c.b(0x3B));
        if (c.b(0x3B) != 0x20) { solid(c); return; }
        if (activePeriod(c)) { spawnProjectile(c, false); spawnProjectile(c, true); }
        if (solid(c) == 0) { c.b(0x3A, 8); c.b(0x24, c.b(0x24) + 2); }
        else { c.services.launchPrimaryFromFlame(); c.b(0x22, c.b(0x22) & ~8); }
    }
    private static void spawnProjectile(CdCcBodyCursor c, boolean right) {
        var free = c.pool.findObjSlot(); if (free.isEmpty()) return;
        int at = free.getAsInt(); c.childByte(at, 0, 0x27); c.childByte(at, 0x28, 2);
        // ADDQ.B #8,d0 preserves the high Y byte, even across $xxFF.
        c.childWord(at, 12, (c.w(12) & 0xFF00) | ((c.w(12) + 8) & 255));
        c.childWord(at, 8, c.w(8) + (right ? 0x18 : -0x18));
        if (right) c.childByte(at, 0x3A, 1); // Left direction and both burnout bytes remain dirty.
    }
    private void lower(CdCcBodyCursor c) {
        if (c.b(0x3A) != 0) { c.b(0x3A, c.b(0x3A) - 1); solid(c); return; }
        int prior = c.b(0x3B); c.b(0x3B, prior - 4); if (prior < 4) c.b(0x3B, 0);
        c.w(12, c.w(0x32) - (byte) c.b(0x3B));
        if (c.b(0x3B) == 0) { c.b(0x3A, 0x3C); c.b(0x24, c.b(0x24) + 2); }
        solid(c);
    }
    private void projectile(CdCcBodyCursor c) {
        c.routine(2);
        if (c.b(0x24) == 0) {
            c.b(1, c.b(1) | 4); c.w(2, 0x379); c.b(0x18, 4); c.b(0x20, 0x98); c.map(assets.fireMap()); c.w(0x36, c.w(8));
            boolean right = c.w(0x3A) != 0;
            c.w(0x10, right ? 0x100 : -0x100); c.w(0x3E, right ? 0x10 : -0x10); c.b(0x1C, right ? 2 : 1);
            c.b(0x24, 2); c.animate(assets.fireAnimation());
        }
        c.w(0x10, c.w(0x10) + c.w(0x3E)); c.moveX(c.w(0x10)); c.animate(assets.fireAnimation());
        int x = c.w(8), base = c.w(0x36), distance = (x - base) & 0xFFFF;
        if (x < base) distance = (-distance) & 0xFFFF;
        if (distance < 0x40) return;
        c.b(0x20, 0);
        if (c.b(0x3B) == 0) { c.b(0x1C, c.b(0x1C) + 2); c.b(0x3B, 0x1E); return; }
        c.b(0x3B, c.b(0x3B) - 1); if (c.b(0x3B) == 0) c.pool.deleteObject(c.index);
    }
}

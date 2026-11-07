package com.openggf.game.soniccd.objects;

import static com.openggf.game.soniccd.objects.CdCczEnemyPorts.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** N1ecc5f3 Spike Chain/Main.asm:34-239, ABI1 STAGE_TIME+2 children and unguarded stores. */
public final class CdSpikeChain extends CdCczEnemySupport {
    public CdSpikeChain(CdObjectPlacementRuntime placement, Assets assets, CdCczEnemyPorts ports) {
        super(placement, assets, null, ports);
    }
    @Override int id() { return 0x2C; }
    @Override void body() {
        if (s.subtype() >= 128) { child(); return; }
        switch (s.routine()) {
            case 0 -> { if (init()) rotate(); }
            case 2 -> rotate();
            default -> unsupported();
        }
        draw(); despawn(s.centreX()); // includes full-delete/cleared-ID failure tail
    }
    private int count() {
        int n = read(assets::chainLinkCount);
        if (n < 2 || n > 4) throw new IllegalArgumentException("Actual programme chain length must be 2..4");
        return n;
    }
    private boolean init() {
        next(); put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() | 4).moveByte(PRIORITY, 1)
                .moveByte(WIDTH, 8).moveByte(Y_RADIUS, 8)); mapping(Mapping.CHAIN_PARENT);
        put(s.moveWord(TILE, 0x3BD)); w(0x2C, s.subtype() == 0 ? 0x100 : -0x100);
        int n = count();
        for (int i = 0; i < n; i++) {
            // SpawnObjectAfter always starts from parent a0; previous allocated child's ID
            // prevents reuse. No cursor starting after the last allocation is manufactured.
            var link = allocate(true);
            if (link == null) { delete(); return false; }
            link = wordAt(link, 0x3E, s.identity().nativeAddress24()); pool.write(link);
            w(0x2E + i * 2, link.identity().nativeAddress24());
            link = link.moveByte(ID, s.id()).moveByte(SUBTYPE, 255).moveByte(WIDTH, 8).moveByte(Y_RADIUS, 8);
            pool.write(link);
        }
        return true;
    }
    private void rotate() {
        w(0x2A, w(0x2A) + w(0x2C));
        var sin = read(() -> ports.sine(w(0x2A) >>> 8));
        int dy4 = (sin.sine() << 16) >> 2, dx4 = (sin.cosine() << 16) >> 2;
        int dy2 = dy4 >> 1, dx2 = dx4 >> 1, dy1 = dy2 >> 1, dx1 = dx2 >> 1;
        int n = count();
        for (int i = 0; i < n; i++) {
            // Native code does NOT check child ID/parent relation before these stores.
            // Preserve same-ID reused dynamic slots, reject only unowned/misaligned addresses.
            var link = linked(w(0x2E + i * 2));
            int dy = switch (i) { case 0 -> dy1; case 1 -> dy2; case 2 -> dy1 + dy2; default -> dy4; };
            int dx = switch (i) { case 0 -> dx1; case 1 -> dx2; case 2 -> dx1 + dx2; default -> dx4; };
            link = longAt(link, 0x2E, s.longBits(CdObjectState.LongField.Y_POSITION));
            link = longAt(link, 0x2A, s.longBits(CdObjectState.LongField.X_POSITION));
            link = longAt(link, 0x2E, rawLong(link, 0x2E) + dy);
            link = longAt(link, 0x2A, rawLong(link, 0x2A) + dx); pool.write(link);
            s = pool.state(s.identity().wholeIndex()); // handles valid native self aliases
        }
    }
    private static int rawLong(CdObjectState image, int at) {
        byte[] v = image.copyBytes(); return (Byte.toUnsignedInt(v[at]) << 24) | (Byte.toUnsignedInt(v[at + 1]) << 16)
                | (Byte.toUnsignedInt(v[at + 2]) << 8) | Byte.toUnsignedInt(v[at + 3]);
    }
    private void child() {
        switch (s.routine()) {
            case 0 -> {
                next(); put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() | 4).moveByte(PRIORITY, 1));
                mapping(Mapping.CHAIN_LINK); put(s.moveWord(TILE, 0x3BD).moveByte(COLLISION_TYPE, 0x8B)); copyPosition();
            }
            case 2 -> copyPosition();
            default -> unsupported();
        }
        if (linked(w(0x3E)).id() != 0x2C) { delete(); return; }
        draw();
    }
    private void copyPosition() { put(s.moveWord(X, w(0x2A)).moveWord(Y, w(0x2E))); }
}

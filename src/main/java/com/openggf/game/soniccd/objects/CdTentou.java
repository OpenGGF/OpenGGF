package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.player.CdPlayerTerrain;
import static com.openggf.game.soniccd.objects.CdCczEnemyPorts.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** N1ecc5f3 Tentou/Main.asm:39-432, actual historical ABI1 graph including bomb. */
public final class CdTentou extends CdCczEnemySupport {
    public CdTentou(CdObjectPlacementRuntime placement, Assets assets, CdPlayerTerrain terrain, CdCczEnemyPorts ports) {
        super(placement, assets, terrain, ports);
    }
    @Override int id() { return 0x2E; }
    @Override void body() {
        if ((byte) (s.subtype() - 1) >= 0) { bomb(); draw(); return; }
        if (goodFuture()) return;
        switch (s.routine()) {
            case 0 -> { init(); findFloor(); }
            case 2 -> findFloor();
            case 4 -> waitPlayer();
            case 6 -> travel();
            case 8 -> put(s.addLong(Y_POSITION, -0x8000));
            case 10 -> crossGap();
            default -> unsupported();
        }
        animate(Animation.TENTOU); draw(); despawn(s.centreX());
    }
    private void init() {
        next(); put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() | 0x84).moveByte(PRIORITY, 1)
                .moveByte(COLLISION_TYPE, 6).moveByte(WIDTH, 14).moveByte(Y_RADIUS, 14).moveWord(TILE, 0xA46A));
        if (s.subtype() < 128) b(0x3F, 255);
        mapping(s.subtype() < 128 ? Mapping.TENTOU_NORMAL : Mapping.TENTOU_DAMAGED);
    }
    private void findFloor() {
        put(s.addLong(Y_POSITION, 0x10000)); int distance = floor();
        int adjusted = (short) (distance - 8);
        // SUBQ.W/BGT uses N==V, not the wrapped result's sign alone.
        if (distance > 8) return;
        put(s.addWord(Y, -adjusted)); l(0x32, s.longBits(Y_POSITION)); l(0x2E, 0x3000); w(0x36, 8); next();
        if (l(0x2A) != 0) next(); // full LONG, includes dirty timer/motion bytes
    }
    private void waitPlayer() {
        bob();
        int dy = (s.centreY() - p(Player.Y) + 0x50) & 65535;
        if (dy >= 0xA0) return;
        int dx = (s.centreX() - p(Player.X)) & 65535;
        if (((dx + 0x50) & 65535) >= 0xA0) return;
        next(); l(0x2A, -0xC000);
        if ((short) dx >= 0) {
            put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() ^ 1).moveByte(FLAGS, s.flags() ^ 1)); l(0x2A, -l(0x2A));
        }
    }
    private void travel() {
        put(s.addLong(X_POSITION, l(0x2A)).moveLong(Y_POSITION, l(0x32)));
        if (wall(l(0x2A) < 0, false) < 0) { next(); return; }
        int d = floor();
        if (d >= 0x10) { put(s.addByte(ROUTINE, 4)); bob(); l(0x32, s.longBits(Y_POSITION)); return; }
        put(s.addWord(Y, (short) (d - 8))); l(0x32, s.longBits(Y_POSITION)); bob();
        if (b(0x3F) == 0) return;
        w(0x36, w(0x36) & 0x7F); // actual stored AND, including failed allocation
        if (w(0x36) != 0) return;
        var child = allocate(false); if (child == null) return;
        pool.write(child.moveByte(ID, s.id()).moveLong(X_POSITION, s.longBits(X_POSITION))
                .moveLong(Y_POSITION, s.longBits(Y_POSITION)).addWord(Y, 16).moveByte(SUBTYPE, 1));
    }
    private void bob() {
        w(0x36, w(0x36) + 1); put(s.addLong(Y_POSITION, l(0x2E)));
        if ((w(0x36) & 15) == 0) l(0x2E, -l(0x2E));
    }
    private void crossGap() {
        put(s.addLong(X_POSITION, l(0x2A)));
        int x = s.centreX() - s.unsignedByte(WIDTH);
        if (l(0x2A) < 0) x += 2 * s.unsignedByte(WIDTH);
        if (floorAt(x) >= 0x10) put(s.addByte(ROUTINE, -8));
    }
    private void bomb() {
        switch (s.routine()) {
            case 0 -> { bombInit(); bombFall(); }
            case 2 -> bombFall();
            case 4 -> bombArm();
            case 6 -> bombFuse();
            case 8 -> explode();
            default -> unsupported();
        }
    }
    private void bombInit() {
        next(); put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() | 4).moveByte(PRIORITY, 2)
                .moveByte(COLLISION_TYPE, 0x8B).moveByte(X_RADIUS, 6).moveByte(WIDTH, 6)
                .moveByte(Y_RADIUS, 6).moveWord(TILE, 0x495)); mapping(Mapping.BOMB);
    }
    private void bombFall() {
        if (s.unsignedByte(COLLISION_STATUS) != 0) { explode(); return; }
        put(s.addLong(Y_POSITION, 0x10000)); int d = floor();
        if (d < 0) { put(s.addWord(Y, d)); w(0x2A, 0x78); next(); }
    }
    private void bombArm() {
        if (s.unsignedByte(COLLISION_STATUS) != 0) { explode(); return; }
        if (tick(0x2A)) { w(0x2A, 0x78); next(); }
    }
    private void bombFuse() {
        if (s.unsignedByte(COLLISION_STATUS) != 0) { explode(); return; }
        if (tick(0x2A)) next();
        animate(Animation.BOMB); // zero timer advances routine; conversion occurs on NEXT call
    }
    private void explode() {
        put(s.moveByte(ID, 0x18).moveByte(ROUTINE, 0).moveByte(ROUTINE_2, 1));
        if ((s.spriteFlags() & 128) != 0) sound(Sound.EXPLOSION);
        // No recursive Explosion/Points/Flower dispatch. Existing real owner must schedule it.
    }
}

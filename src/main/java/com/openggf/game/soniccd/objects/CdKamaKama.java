package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.player.CdPlayerTerrain;
import static com.openggf.game.soniccd.objects.CdCczEnemyPorts.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** N1ecc5f3 KamaKama/Main.asm:44-446, actual CC_LEGACY_KAMA_KAMA_ABI=1 graph. */
public final class CdKamaKama extends CdCczEnemySupport {
    public CdKamaKama(CdObjectPlacementRuntime placement, Assets assets, CdPlayerTerrain terrain, CdCczEnemyPorts ports) {
        super(placement, assets, terrain, ports);
    }
    @Override int id() { return 0x24; }
    @Override void body() {
        if (goodFuture()) return;
        switch (s.routine()) {
            case 0 -> { init(); fall(); }
            case 2 -> fall();
            case 4 -> watch();
            case 6 -> attackWait();
            case 8 -> recoveryWait();
            case 10 -> walk();
            case 12 -> turnWait();
            default -> unsupported();
        }
        despawn(s.centreX());
    }
    private void init() {
        b(0x2E, s.subtype()); // signed stored subtype survives Future secondaryFF untouched
        put(s.moveByte(MAP_FRAME, s.subtype() < 128 ? 1 : 2));
        mapping(s.subtype() < 128 ? Mapping.KAMA_NORMAL : Mapping.KAMA_DAMAGED);
        put(s.moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 3).moveByte(WIDTH, 16)
                .moveByte(Y_RADIUS, 20).moveWord(TILE, 0x23F2).moveWord(Y_VELOCITY, 0x100)
                .moveByte(COLLISION_TYPE, 6)); next();
    }
    private void fall() { move(); if (floor() < 0) next(); animatedDraw(); }
    private void watch() {
        if ((s.spriteFlags() & 128) == 0) { noTarget(); return; }
        put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() & ~1).moveByte(FLAGS, s.flags() & ~1));
        int x = s.centreX(), px = p(Player.X), dx = (x - px) & 65535;
        if (x < px) { dx = (-dx) & 65535; put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() | 1).moveByte(FLAGS, s.flags() | 1)); }
        if ((s.spriteFlags() & 128) == 0) { noTarget(); return; }
        int y = s.centreY(), py = p(Player.Y), dy = (y - py) & 65535;
        if (y < py) dy = (-dy) & 65535;
        if (dy >= 0x20 || dx >= 0x60) { noTarget(); return; }
        put(s.moveByte(ANIMATION, 2)); spawnSickles(); w(0x2C, 0x78); next(); attackWait();
    }
    private void noTarget() { put(s.moveByte(ANIMATION, 1)); animatedDraw(); }
    private void attackWait() {
        if (!tick(0x2C)) { animatedDraw(); return; }
        put(s.moveByte(ANIMATION, 3)); w(0x2C, 0x3C); next(); recoveryWait();
    }
    private void recoveryWait() { if (!tick(0x2C)) { animatedDraw(); return; } next(); walk(); }
    private void walk() {
        int speed = s.subtype() < 128 ? 0x100 : 0x80;
        if ((s.flags() & 1) == 0) speed = -speed;
        put(s.moveByte(ANIMATION, 0).moveWord(X_VELOCITY, speed)); move();
        boolean hit = wall(true, true) < 0;
        if (!hit) hit = wall(false, true) < 0;
        if (!hit) {
            int distance = floor();
            if (distance == 0) { animatedDraw(); return; }
            hit = distance >= 7 || distance < -7;
            if (!hit) put(s.addWord(Y, distance));
        }
        if (hit) {
            w(0x2C, 0x48); put(s.moveWord(ANIMATION_WORD, 3)); next(); turnWait(); return;
        }
        animatedDraw();
    }
    private void turnWait() {
        if (tick(0x2C)) put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() ^ 1)
                .moveByte(FLAGS, s.flags() ^ 1).addByte(ROUTINE, -2));
        animatedDraw();
    }
    private void animatedDraw() { animate(Animation.KAMA); draw(); }
    private void spawnSickles() {
        if (s.subtype() >= 128) return;
        spawnSickle(0x40, 17, -4); // second attempt occurs even if first failed
        spawnSickle(0x14, 9, -6);
    }
    private void spawnSickle(int delay, int offset, int dy) {
        var child = allocate(false); if (child == null) return;
        int dx = (s.spriteFlags() & 1) != 0 ? offset : -offset;
        child = child.moveByte(ID, 0x25);
        child = wordAt(child, 0x30, s.identity().nativeAddress24());
        child = wordAt(child, 0x2C, delay).moveByte(SUBTYPE, s.subtype());
        child = byteAt(child, 0x2E, s.spriteFlags()).moveWord(Y, s.centreY() + dy).moveWord(X, s.centreX() + dx);
        pool.write(child); // no implicit clear, reservation or child dispatch
    }
}

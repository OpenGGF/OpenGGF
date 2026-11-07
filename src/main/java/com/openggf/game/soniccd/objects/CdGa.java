package com.openggf.game.soniccd.objects;

import static com.openggf.game.soniccd.objects.CdCczEnemyPorts.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** N1ecc5f3 Ga/Main.asm; B/C/D objects/r3/ga.asm, equivalent historical ABI1 graph. */
public final class CdGa extends CdCczEnemySupport {
    public CdGa(CdObjectPlacementRuntime placement, Assets assets, CdCczEnemyPorts ports) { super(placement, assets, null, ports); }
    @Override int id() { return 0x2D; }
    @Override void body() {
        if ((byte) (s.subtype() - 1) >= 0) { dust(); draw(); return; }
        if (goodFuture()) return;
        switch (s.routine()) {
            case 0 -> { init(); waitFirst(); }
            case 2 -> waitFirst();
            case 4 -> emerge();
            case 6 -> prepareFlight();
            case 8 -> flight();
            default -> unsupported();
        }
        draw(); despawn(s.centreX()); // active routines have already drawn once
    }
    private void init() {
        next(); put(s.moveByte(SPRITE_FLAGS, 4).moveByte(MAP_FRAME, 0).moveByte(PRIORITY, 1)
                .moveByte(WIDTH, 16).moveByte(Y_RADIUS, 8).moveWord(TILE, 0xA421)); w(0x2A, 0x3D);
        if (s.subtype() < 128) b(0x3F, 255);
        mapping(s.subtype() < 128 ? Mapping.GA_NORMAL : Mapping.GA_DAMAGED);
    }
    private void waitFirst() { if (tick(0x2A)) { next(); w(0x2A, 0x3D); } }
    private void emerge() {
        if (tick(0x2A)) { put(s.moveByte(COLLISION_TYPE, 0x25)); next(); put(s.addByte(ANIMATION, 1)); w(0x2A, 0x33); }
        animatedDraw();
    }
    private void prepareFlight() {
        if (tick(0x2A)) {
            put(s.moveByte(COLLISION_TYPE, 0x26)); next(); put(s.addByte(ANIMATION, 1));
            l(0x30, 0x8000); w(0x2A, s.subtype() < 128 ? 0x20 : 0x30);
            l(0x2C, s.subtype() < 128 ? -0x8000 : -0x4000);
            if ((short) (s.centreX() - p(Player.X)) < 0) {
                l(0x2C, -l(0x2C)); put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() | 1).moveByte(FLAGS, s.flags() | 1));
            }
        }
        animatedDraw();
    }
    private void flight() {
        put(s.addLong(X_POSITION, l(0x2C)).addLong(Y_POSITION, l(0x30)));
        if (tick(0x2A)) {
            w(0x2A, s.subtype() < 128 ? 0x41 : 0x61); l(0x30, -l(0x30));
            if (b(0x3F) != 0) {
                var child = allocate(false);
                if (child != null) pool.write(child.moveByte(ID, s.id()).moveLong(X_POSITION, s.longBits(X_POSITION))
                        .moveLong(Y_POSITION, s.longBits(Y_POSITION)).moveByte(SUBTYPE, 1));
            }
        }
        animatedDraw();
    }
    private void animatedDraw() { animate(Animation.GA); draw(); }
    private void dust() {
        switch (s.routine()) {
            case 0 -> {
                next(); put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() | 4).moveByte(PRIORITY, 1)
                        .moveByte(X_RADIUS, 6).moveByte(WIDTH, 6).moveByte(Y_RADIUS, 6).moveWord(TILE, 0xA7AE));
                mapping(Mapping.RING); w(0x2A, 0xB4); dustMain();
            }
            case 2 -> dustMain();
            case 4 -> delete();
            default -> unsupported();
        }
    }
    private void dustMain() {
        if (tick(0x2A)) { delete(); return; }
        put(s.addLong(Y_POSITION, 0x10000)); animate(Animation.DUST);
    }
}

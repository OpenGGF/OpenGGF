package com.openggf.game.soniccd.objects;

import static com.openggf.game.soniccd.objects.CdCczEnemyPorts.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** N1ecc5f3 KamaKama/Main.asm:457-605; parry changes sickle, never writes player. */
public final class CdKamaSickle extends CdCczEnemySupport {
    public CdKamaSickle(CdObjectPlacementRuntime placement, Assets assets, CdCczEnemyPorts ports) {
        super(placement, assets, null, ports);
    }
    @Override int id() { return 0x25; }
    @Override void body() {
        switch (s.routine()) {
            case 0 -> init(); // native Init returns, without countdown/parent test/animation
            case 2 -> waitLaunch();
            case 4 -> fly();
            default -> unsupported();
        }
        draw(); despawn(w(0x32)); // reload after draw, including cleared-ID tail
    }
    private void init() {
        put(s.moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 1).moveByte(X_RADIUS, 4)
                .moveByte(Y_RADIUS, 4).moveWord(TILE, 0x23F2)); mapping(Mapping.SICKLE);
        w(0x32, s.centreX()); int speed = 0x300;
        if ((b(0x2E) & 1) == 0) {
            put(s.moveByte(SPRITE_FLAGS, s.spriteFlags() | 1).moveByte(FLAGS, s.flags() | 1)); speed = -speed;
        }
        put(s.moveWord(X_VELOCITY, speed)); next();
    }
    private void waitLaunch() {
        if (tick(0x2C)) { next(); put(s.moveByte(COLLISION_TYPE, 0x87)); b(0x34, 0x3C); }
        if (linked(w(0x30)).id() != 0x24) { delete(); return; }
        animate(Animation.SICKLE);
    }
    private void fly() {
        move(); put(s.addWord(Y_VELOCITY, w(0x2A)));
        if (s.unsignedByte(MAP_FRAME) == 0 && (s.spriteFlags() & 128) != 0) sound(Sound.SICKLE);
        if (parried()) {
            put(s.moveByte(COLLISION_TYPE, 0).moveByte(ANIMATION, 1));
            int vx = p(Player.X_VELOCITY); put(s.addWord(X_VELOCITY, vx).moveWord(Y_VELOCITY, 0xF800));
            w(0x2A, 0x40);
        }
        b(0x34, b(0x34) - 1);
        if (b(0x34) == 0) { b(0x34, 1); put(s.moveByte(COLLISION_TYPE, 0)); }
        animate(Animation.SICKLE);
    }
    private boolean parried() {
        if (g(Global.INVINCIBLE) == 0 && (p(Player.FLAGS) & 4) == 0) return false;
        int x = (p(Player.X) - s.centreX() + 0x1C) & 65535;
        if ((short) x < 0 || x >= 0x38) return false;
        int y = (p(Player.Y) - s.centreY() + 0x1C) & 65535;
        return (short) y >= 0 && y < 0x38;
    }
}

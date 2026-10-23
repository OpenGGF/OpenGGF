package com.openggf.sprites.playable;

import com.openggf.game.internal.NativePlayerSpeedPolicy;

/** Per-actor effective WORDs. UNKNOWN is independent of every possible unsigned word value. */
final class NativePlayerSpeedWords {
    // Structural owner supplied by participant assembly; never part of the value snapshot.
    private com.openggf.game.session.WorldSession ownerWorld;
    private short maximum;
    private short acceleration;
    private short deceleration;
    private boolean known;

    record Snapshot(short maximum, short acceleration, short deceleration, boolean known) { }

    void publish(int maximum, int acceleration, int deceleration) {
        if ((maximum | acceleration | deceleration) < 0
                || maximum > 0xFFFF || acceleration > 0xFFFF || deceleration > 0xFFFF) {
            throw new IllegalArgumentException("Native speed words must be unsigned 16-bit values");
        }
        this.maximum = (short) maximum;
        this.acceleration = (short) acceleration;
        this.deceleration = (short) deceleration;
        known = true;
    }

    void bindOwner(com.openggf.game.session.WorldSession world) {
        if (ownerWorld != world) { forget(); ownerWorld = world; }
    }

    void forget() { known = false; }
    int maximum() { return known ? Short.toUnsignedInt(maximum) : -1; }
    int acceleration() { return known ? Short.toUnsignedInt(acceleration) : -1; }
    int deceleration() { return known ? Short.toUnsignedInt(deceleration) : -1; }

    Snapshot capture() {
        return known ? new Snapshot(maximum, acceleration, deceleration, true)
                : new Snapshot((short) 0, (short) 0, (short) 0, false);
    }

    void restore(Snapshot snapshot) {
        if (ownerWorld == null || snapshot == null || !snapshot.known()) { forget(); return; }
        maximum = snapshot.maximum();
        acceleration = snapshot.acceleration();
        deceleration = snapshot.deceleration();
        known = true;
    }

    static NativePlayerSpeedPolicy policyFor(AbstractPlayableSprite player) {
        try {
            var world = player.nativeSpeedWords.ownerWorld;
            var module = world != null ? world.getGameModule() : null;
            var provider = module != null ? module.getPhysicsProvider() : null;
            return provider instanceof NativePlayerSpeedPolicy policy && policy.supports(player, world) ? policy : null;
        } catch (IllegalStateException unavailable) {
            return null;
        }
    }
}

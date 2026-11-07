package com.openggf.game.soniccd.objects.r4;

import com.openggf.game.soniccd.objects.CdSolidObject;

/** Root-owned shared semantic helper calls, acting ONLY on the borrowed staging cut.
 * Implementations must reuse source-equivalent movement/Solid/animation/draw/retirement kernels,
 * retaining their reached failed-call prefixes. No method may publish into a live World.
 * Calls are synchronous; a completed value is never a command for later replay. */
public interface CdR4MechanismServices {
    boolean solid(CdR4MechanismImage actor, CdSolidObject.Entry entry);
    void getOff(CdR4MechanismImage actor);
    void draw(CdR4MechanismImage actor);
    void checkDespawn(CdR4MechanismImage actor, int anchorX);
    void despawn(CdR4MechanismImage actor);
    void delete(CdR4MechanismImage actor);
    void move(CdR4MechanismImage actor);
    int floorDistance(CdR4MechanismImage actor, boolean ceiling);
}

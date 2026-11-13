package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRoutineLifetime;
import java.util.function.IntUnaryOperator;

/** ROM-tail profile shared by S2/KiS2; no gameplay or comparison state is stored here. */
public final class Sonic2RoutineLifetimes implements ObjectRoutineLifetime {
    private final IntUnaryOperator coarse;
    public Sonic2RoutineLifetimes(IntUnaryOperator coarse) { this.coarse = coarse; }
    @Override public int coarseCameraX(int slot) { return coarse.applyAsInt(slot); }
    @Override public Tail tail(ObjectInstance object) {
        // Obj4B_Projectile/Flame/Main (s2.asm:60980-61067).
        if (object instanceof BuzzerBadnikInstance buzzer) return buzzer.routineTail();
        if (object instanceof BuzzerBadnikInstance.BuzzerFlameChild) return Tail.MARK_OBJ_GONE_P1;
        if (object instanceof BadnikProjectileInstance projectile && projectile.isBuzzerStinger())
            return Tail.MARK_OBJ_GONE_P1;
        // ObjA3 routines 2..C; loc_3884A alone uses plain MarkObjGone
        // (s2.asm:76239-76374). Init returns without an off-screen tail.
        if (object instanceof FlasherBadnikInstance flasher) return flasher.routineTail();
        return Tail.MARK_OBJ_GONE;
    }
}

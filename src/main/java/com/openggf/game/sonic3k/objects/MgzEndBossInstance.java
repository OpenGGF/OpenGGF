package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;

/**
 * S3K Obj $A1 — Marble Garden Act 2 end boss.
 *
 * <p>ROM: {@code Obj_MGZEndBoss} (sonic3k.asm:142715+) uses the same
 * drill vehicle, Robotnik ship, head, debris, palette and art setup as the
 * MGZ2 drilling-Robotnik mini-events, then drives the full boss arena sequence
 * from the dynamic-resize gate. This class currently shares the composite
 * vehicle implementation while the arena event owns the ROM spawn/lock path.
 */
public final class MgzEndBossInstance extends MgzDrillingRobotnikInstance {

    public MgzEndBossInstance(ObjectSpawn spawn) {
        this(spawn, false);
    }

    /** Internal finite encounter preset; native motion and endings remain owned here. */
    public MgzEndBossInstance(ObjectSpawn spawn, boolean counterCadence) {
        super(spawn, false, counterCadence);
    }

}

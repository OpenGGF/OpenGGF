package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RomWorldPositionedObject;

/**
 * The independent CreateBossExp00 SST allocated at MGZ's killing hit.
 * TunnelbotMiniboss_CreateExplosion (sonic3k.asm:185391-185394) creates it
 * before the boss later becomes EndSignControl; it must outlive that handoff.
 */
final class MgzMinibossExplosionController extends AbstractS3kBossExplosionObjectInstance
        implements RewindRecreatable, RomWorldPositionedObject {
    MgzMinibossExplosionController(int x, int y) {
        this(new ObjectSpawn(x, y, 0, 0, 0, false, y));
    }

    private MgzMinibossExplosionController(ObjectSpawn spawn) {
        super(spawn, "MGZMinibossExplosionControl");
    }

    @Override public MgzMinibossExplosionController recreateForRewind(RewindRecreateContext context) {
        return new MgzMinibossExplosionController(context.spawn());
    }
}

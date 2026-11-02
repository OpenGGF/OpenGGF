package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Common fixed-capsule ROM state; native child graphs and results continuations remain adapters. */
public abstract class AbstractS3kEggCapsuleInstance extends AbstractObjectInstance {
    protected boolean opened;
    protected int mappingFrame;
    protected int postOpenTimer;

    protected AbstractS3kEggCapsuleInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    /** sub_865DE (sonic3k.asm:181652-181667): common opening writes before child allocation. */
    protected final void initializeCapsuleOpening() {
        opened = true;
        mappingFrame = 1;
        postOpenTimer = 0x40;
        if (locksNativeP2CpuOnOpen()) {
            PlayableEntity candidate = services().playerQuery().nativeP2OrNull();
            if (candidate instanceof AbstractPlayableSprite p2 && p2.getCpuController() != null) {
                onNativeP2ControlAcquired(p2);
                p2.getCpuController().setController2SignedLocked(true);
            }
        }
    }

    protected boolean locksNativeP2CpuOnOpen() { return true; }
    protected void onNativeP2ControlAcquired(AbstractPlayableSprite player) { }

    protected final void drawCapsuleBody() {
        var renderer = getRenderer(Sonic3kObjectArtKeys.EGG_CAPSULE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
        }
    }
}

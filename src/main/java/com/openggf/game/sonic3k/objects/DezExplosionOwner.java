package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectInstance;

/** Native parent3 position and $38 bit 5 consumed by Obj_WaitForParent. */
interface DezExplosionOwner extends ObjectInstance {
    int explosionControl();
}

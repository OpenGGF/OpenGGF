package com.openggf.sprites.playable;

import com.openggf.level.objects.PerObjectRewindSnapshot;

/** Values owned by the player until its persistent insta-shield is registered. */
record PendingInstaShieldRewindExtra(PerObjectRewindSnapshot shieldState)
        implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {
}

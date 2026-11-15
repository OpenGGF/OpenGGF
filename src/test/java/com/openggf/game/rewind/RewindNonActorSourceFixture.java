package com.openggf.game.rewind;

import com.openggf.level.objects.PerObjectRewindSnapshot;

/** Guard fixture: a restore-shaped helper is not an object actor. */
final class RewindNonActorSourceFixture {
    public void restoreRewindState(PerObjectRewindSnapshot snapshot) { }
}

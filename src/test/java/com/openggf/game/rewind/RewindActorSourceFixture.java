package com.openggf.game.rewind;

import com.openggf.level.objects.AbstractObjectInstance;

/** Guard fixture: the source's primary type is not the actor it also declares. */
final class RewindActorSourceFixture {
}

abstract class SecondaryRewindActor extends AbstractObjectInstance {
    SecondaryRewindActor() {
        super(null, "secondary-rewind-guard-actor");
    }
}

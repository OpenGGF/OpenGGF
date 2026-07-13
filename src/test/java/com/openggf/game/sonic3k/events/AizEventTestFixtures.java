package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;

import java.util.concurrent.atomic.AtomicInteger;

/** Test-only construction helpers for AIZ event timing fixtures. */
public final class AizEventTestFixtures {
    private static final Sonic3kLoadBootstrap FIRE_TRANSITION_BOOTSTRAP =
            new Sonic3kLoadBootstrap(Sonic3kLoadBootstrap.Mode.SKIP_INTRO, null);

    private AizEventTestFixtures() {
    }

    public static Sonic3kAIZEvents newFireTransitionEvents() {
        AtomicInteger vblankCounter = new AtomicInteger();
        return new Sonic3kAIZEvents(FIRE_TRANSITION_BOOTSTRAP, vblankCounter::getAndIncrement);
    }
}

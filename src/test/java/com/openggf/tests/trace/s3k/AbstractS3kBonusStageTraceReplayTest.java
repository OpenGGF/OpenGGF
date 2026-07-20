package com.openggf.tests.trace.s3k;

import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.trace.TraceData;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.tests.trace.AbstractTraceReplayTest;

/**
 * Shared base for S3K bonus-stage trace replay (gumball/pachinko/slots).
 * Bonus zones run on the LEVEL pipeline, so the entire level replay stack
 * applies; the only addition is the bonus-entry bootstrap after load.
 */
public abstract class AbstractS3kBonusStageTraceReplayTest extends AbstractTraceReplayTest {

    @Override
    protected void afterFixtureBuild(TraceData trace) {
        TraceReplaySessionBootstrap.applyBonusStageEntry(trace);
    }

    /**
     * The recorder captured $B000 — the object the ROM camera tracks — so the
     * comparator must follow the same object. Gumball and pachinko never
     * change which sprite the camera focuses on, but the slot runtime swaps
     * the tracked player onto the dedicated slot-machine playable
     * ({@code S3kSlotBonusStageRuntime.bootstrap()} calls
     * {@code getCamera().setFocusedSprite(slotPlayer)}). Reading the
     * camera-focused sprite here is engine-state-keyed, not bonus-type-keyed:
     * it stays a no-op for gumball/pachinko (focus never leaves the original
     * player) and picks up the swap automatically for slots.
     */
    @Override
    protected AbstractPlayableSprite comparedSprite(HeadlessTestFixture fixture) {
        AbstractPlayableSprite focused = GameServices.camera().getFocusedSprite();
        return selectComparedSprite(focused, fixture.sprite());
    }

    /**
     * Pure selection rule extracted for unit testing without an engine:
     * prefer the camera-focused sprite when present, else fall back to the
     * fixture's primary sprite.
     */
    static AbstractPlayableSprite selectComparedSprite(
            AbstractPlayableSprite focused, AbstractPlayableSprite fixtureSprite) {
        return focused != null ? focused : fixtureSprite;
    }
}

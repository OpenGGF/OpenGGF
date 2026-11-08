package com.openggf.game.sonic3k.competition;

import com.openggf.game.arena.RaceSwapAdmission;
import com.openggf.game.arena.RaceSwapApplier;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.objects.ObjectServices;
import com.openggf.game.arena.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Streamed partner reconstruction for loc_3652A; the native box still owns its animation. */
final class S3kArenaSwapApplier extends com.openggf.game.arena.AbstractRaceSwapApplier implements RewindSnapshottable<S3kArenaSwapApplier.State> {
    record State(boolean requestPending) { }
    @RewindTransient(reason="World binding, recreated with the hosted arena; session has its own native snapshot")
    private final S3kCompetitionNativeHost host;
    @RewindTransient(reason="Read-only geometry supplier bound to the hosted arena")
    private final java.util.function.Supplier<TrackGeometry> geometry;
    private boolean requestPending;

    S3kArenaSwapApplier(S3kCompetitionNativeHost host,java.util.function.Supplier<TrackGeometry> geometry) {
        this.host = host; this.geometry = geometry;
    }
    boolean requestNative() {
        if (!host.world().context().getRunPolicy().swapEnabled()) return false;
        // A native solo break still follows loc_3652A immediately when swapping is disabled.
        if (host.session().player(0).routine() >= 6) return true;
        requestPending = true;
        return true;
    }
    @Override public boolean request(ObjectServices services,AbstractPlayableSprite requester) { return requestNative(); }
    @Override public RaceSignal consumeSignal() {
        boolean requested = requestPending; requestPending = false;
        return requested ? RaceSignal.SWAP_REQUEST : RaceSignal.NONE;
    }
    @Override public RaceEventResult apply(RaceEvent event,GameplayModeContext context) {
        java.util.Objects.requireNonNull(event);
        var player = host.session().player(0);
        boolean live = player.routine() < 6 && !host.session().finished(0);
        if (event instanceof RaceEvent.SwapApply swap && live
                && RaceSwapAdmission.accepts(context,host.player(0),swap.target(),5,0xB,geometry.get())) {
            var target = swap.target();
            // loc_3652A (sonic3k.asm:73480-73525): partner shoes, timer and Character_Speeds.
            // The stream carries only the shoe bit, so other local secondary bits stay local.
            player.setStatusSecondary((player.statusSecondary() & ~4) | (target.shoeKind()==0 ? 0 : 4));
            player.setSpeedShoesTimer(target.shoeTimer());
            player.resetSpeeds();
            if (target.shoeKind()==1) player.setSpeeds(0xC00,0x18,0x80);
            else if (target.shoeKind()==2) player.setSpeeds(0x300,8,0x20);
            return new RaceEventResult(event,RaceEventResult.Outcome.APPLIED);
        }
        if (live) host.session().soloSwapShoes();
        long id = event instanceof RaceEvent.SwapApply swap ? swap.swapId() : ((RaceEvent.SwapFallback)event).swapId();
        return new RaceEventResult(new RaceEvent.SwapFallback(id),RaceEventResult.Outcome.FALLBACK);
    }
    @Override public void reset() { requestPending=false; clearEvents(); }
    @Override public String key() { return "race-arena-swap"; }
    @Override public State capture() { return new State(requestPending); }
    @Override public void restore(State state) { requestPending=state.requestPending(); clearEvents(); }
    @Override public void resetForMissingSnapshot() { reset(); }
}

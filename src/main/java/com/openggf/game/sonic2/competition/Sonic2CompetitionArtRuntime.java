package com.openggf.game.sonic2.competition;

import com.openggf.data.Rom;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.internal.PlayerDplcDmaSink;
import com.openggf.game.resources.QueueDiagnosticSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceVramService;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.Objects;

/**
 * One world-owned art port selecting the actual ROM/PLC/VRAM owner for each act.
 * The rewind binding precedes camera and playable reconstruction; the policy
 * subsequently rebinds the selected owner to the restored canonical humans.
 */
final class Sonic2CompetitionArtRuntime implements RuntimeArtCoordinator, PlayerDplcDmaSink {
    private final RuntimeArtCoordinator delegate;
    private Sonic2InterlaceVramService owner;
    private RewindRegistry registered;
    private final RewindSnapshottable<Binding> bindingAdapter = new RewindSnapshottable<>() {
        @Override public String key() { return Sonic2InterlaceVramService.REWIND_KEY; }
        @Override public Binding capture() {
            return owner == null ? new Binding(null, null) : new Binding(owner, owner.capture());
        }
        @Override public void restore(Binding binding) {
            Objects.requireNonNull(binding, "art binding");
            owner = binding.owner();
            if (owner != null) owner.restore(binding.snapshot());
        }
        @Override public void resetForMissingSnapshot() {
            Sonic2CompetitionArtRuntime.this.resetForMissingSnapshot();
        }
    };

    Sonic2CompetitionArtRuntime(RuntimeArtCoordinator delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    void install(Rom rom, Sonic2PlcService plc, NativeCompetitionRoster roster) {
        var next = new Sonic2InterlaceVramService(rom, plc, delegate);
        bindPlayers(next, roster);
        next.resetForNativeEntry();
        owner = next;
    }

    Sonic2InterlaceVramService ownerOrNull() { return owner; }

    Sonic2InterlaceVramService requireOwner() {
        if (owner == null) throw new IllegalStateException("Native S2 art has not been installed");
        return owner;
    }

    void bindPlayers(NativeCompetitionRoster roster) {
        // The policy also binds the initial roster before createGame installs art.
        // Installation itself binds that roster; later calls only replace identities.
        if (owner != null) bindPlayers(owner, roster);
    }

    private static void bindPlayers(Sonic2InterlaceVramService target, NativeCompetitionRoster roster) {
        Objects.requireNonNull(roster, "native roster");
        var sonic = roster.player(0);
        var tails = roster.player(1);
        if (sonic == null || tails == null || sonic == tails
                || sonic.isCpuControlled() || tails.isCpuControlled())
            throw new IllegalArgumentException("Native art requires two distinct human playables");
        target.bindPlayers(sonic, tails);
    }

    private RuntimeArtCoordinator selected() { return owner == null ? delegate : owner; }

    @Override public void playerLoadPlc(AbstractPlayableSprite player, int mappingFrame, SpriteDplcFrame dplc) {
        requireOwner().playerLoadPlc(player, mappingFrame, dplc);
    }

    @Override public void tailsTailLoadPlc(int mappingFrame, SpriteDplcFrame dplc) {
        requireOwner().tailsTailLoadPlc(mappingFrame, dplc);
    }

    @Override public void playerLoadPlcOwnedByObject(AbstractPlayableSprite player) {
        requireOwner().playerLoadPlcOwnedByObject(player);
    }

    @Override public void beforeTimingService(HardwareServiceBoundary boundary) {
        selected().beforeTimingService(boundary);
    }

    @Override public void afterTimingService(HardwareServiceBoundary boundary) {
        selected().afterTimingService(boundary);
    }

    @Override public void deferProductionSubmissionForHeldLoopTail() {
        selected().deferProductionSubmissionForHeldLoopTail();
    }

    @Override public void deferProductionFirstChildForLateProducer() {
        selected().deferProductionFirstChildForLateProducer();
    }

    @Override public void deferProductionSubmissionForHeldLoopTailClosure() {
        selected().deferProductionSubmissionForHeldLoopTailClosure();
    }

    @Override public void finishHeldLoopTailClosure() { selected().finishHeldLoopTailClosure(); }

    @Override public boolean ownsHeldLevelCounterHardwareTail() {
        return selected().ownsHeldLevelCounterHardwareTail();
    }

    @Override public void runHeldIterationLoopTail() { selected().runHeldIterationLoopTail(); }

    @Override public List<QueueDiagnosticSnapshot> captureQueueDiagnostics() {
        return selected().captureQueueDiagnostics();
    }

    @Override public void resetForMissingSnapshot() { selected().resetForMissingSnapshot(); }

    @Override public void registerRewindAdapters(RewindRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        if (registered == registry) return;
        if (registered != null) throw new IllegalStateException("Native art already belongs to another registry");
        delegate.registerRewindAdapters(registry);
        try {
            // Never register owner: that would also register the delegate again,
            // and a retained snapshot must keep selecting its original act owner.
            registry.register(bindingAdapter);
        } catch (RuntimeException | Error failure) {
            delegate.deregisterRewindAdapters(registry);
            throw failure;
        }
        registered = registry;
    }

    @Override public void deregisterRewindAdapters(RewindRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        if (registered == null) return;
        if (registered != registry) throw new IllegalStateException("Foreign native art rewind registry");
        registry.deregister(Sonic2InterlaceVramService.REWIND_KEY);
        delegate.deregisterRewindAdapters(registry);
        registered = null;
    }

    private record Binding(Sonic2InterlaceVramService owner, Sonic2InterlaceVramService.Snapshot snapshot) {
        private Binding {
            if ((owner == null) != (snapshot == null))
                throw new IllegalArgumentException("Native art owner and snapshot must be present together");
        }
    }
}

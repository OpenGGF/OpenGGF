package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;

import java.nio.ByteBuffer;

/**
 * Combined level animation manager for Sonic 3 &amp; Knuckles.
 * Delegates to the pattern animator and palette cycler, and aggregates
 * snapshot state from both halves so a rewind restores visual state fully.
 */
public final class Sonic3kLevelAnimationManager implements AnimatedPatternManager, AnimatedPaletteManager,
        RewindSnapshottable<PatternAnimatorSnapshot> {

    /** See {@code Sonic2LevelAnimationManager.COMBINED_EXTRA_MAGIC} for rationale. */
    private static final byte COMBINED_EXTRA_MAGIC = (byte) 0xC3;

    private final Sonic3kPatternAnimator patternAnimator;
    private final Sonic3kPaletteCycler paletteCycler;

    public Sonic3kLevelAnimationManager(RomByteReader reader, Level level,
                                        int zoneIndex, int actIndex, boolean isSkipIntro) {
        this.patternAnimator = new Sonic3kPatternAnimator(reader, level,
                zoneIndex, actIndex, isSkipIntro);
        // Resolve the palette ownership registry from the active gameplay mode
        // context. The fallback inside Sonic3kPaletteCycler covers headless tests
        // and the bootstrap window where the mode context is not yet wired; the
        // production path always finds a non-null registry here.
        this.paletteCycler = new Sonic3kPaletteCycler(reader, level, zoneIndex, actIndex,
                resolvePaletteRegistry(), null);
    }

    private static com.openggf.game.palette.PaletteOwnershipRegistry resolvePaletteRegistry() {
        try {
            return GameServices.paletteOwnershipRegistryOrNull();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void update() {
        patternAnimator.update();
        paletteCycler.update();
    }

    /**
     * Replay bootstrap for native {@code Animate_Tiles} calls that happened
     * before the first compared row. This deliberately excludes
     * {@code Animate_Palette}; the ROM setup pass calls only the tile animator.
     */
    public void updatePatternsOnlyForReplayBootstrap() {
        patternAnimator.update();
    }

    @Override
    public String key() {
        return patternAnimator.key();
    }

    @Override
    public PatternAnimatorSnapshot capture() {
        PatternAnimatorSnapshot inner = patternAnimator.capture();
        byte[] innerExtra = inner.extra() != null ? inner.extra() : new byte[0];
        byte[] cyclerState = paletteCycler.captureCyclerState();
        ByteBuffer wrapped = ByteBuffer.allocate(1 + 4 + innerExtra.length + cyclerState.length);
        wrapped.put(COMBINED_EXTRA_MAGIC);
        wrapped.putInt(innerExtra.length);
        wrapped.put(innerExtra);
        wrapped.put(cyclerState);
        return new PatternAnimatorSnapshot(inner.scriptCounters(), inner.handlerCounters(),
                wrapped.array());
    }

    @Override
    public void restore(PatternAnimatorSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        byte[] extra = snapshot.extra();
        if (extra == null || extra.length == 0 || extra[0] != COMBINED_EXTRA_MAGIC) {
            patternAnimator.restore(snapshot);
            return;
        }
        ByteBuffer buf = ByteBuffer.wrap(extra, 1, extra.length - 1);
        int innerSize = buf.getInt();
        if (innerSize < 0 || innerSize > buf.remaining()) {
            patternAnimator.restore(snapshot);
            return;
        }
        byte[] innerExtra = new byte[innerSize];
        buf.get(innerExtra);
        byte[] cyclerState = new byte[buf.remaining()];
        buf.get(cyclerState);
        PatternAnimatorSnapshot inner = new PatternAnimatorSnapshot(snapshot.scriptCounters(),
                snapshot.handlerCounters(), innerExtra);
        patternAnimator.restore(inner);
        paletteCycler.restoreCyclerState(cyclerState);
    }
}

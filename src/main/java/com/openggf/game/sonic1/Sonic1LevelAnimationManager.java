package com.openggf.game.sonic1;

import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;

import java.nio.ByteBuffer;

/**
 * One S1 level-owned visual clock, matching the combined S2/S3K registration.
 * Forward dispatch retains pattern then palette order; the existing
 * pattern-animator registry entry now also retains the palette owner's counters.
 */
final class Sonic1LevelAnimationManager implements AnimatedPatternManager, AnimatedPaletteManager,
        RewindSnapshottable<PatternAnimatorSnapshot> {
    private final Sonic1PatternAnimator patternAnimator;
    private final Sonic1PaletteCycler paletteCycler;

    Sonic1LevelAnimationManager(RomByteReader reader, Level level, int zoneIndex) {
        patternAnimator = new Sonic1PatternAnimator(reader, level, zoneIndex);
        paletteCycler = new Sonic1PaletteCycler(level, zoneIndex);
    }

    @Override public void update() {
        patternAnimator.update();
        paletteCycler.update();
    }

    @Override public String key() { return patternAnimator.key(); }

    @Override public PatternAnimatorSnapshot capture() {
        var patterns = patternAnimator.capture();
        byte[] publication = patterns.extra();
        byte[] palette = paletteCycler.captureCyclerState();
        byte[] extra = ByteBuffer.allocate(Integer.BYTES + publication.length + palette.length)
                .putInt(publication.length).put(publication).put(palette).array();
        return new PatternAnimatorSnapshot(patterns.scriptCounters(), patterns.handlerCounters(), extra);
    }

    @Override public void restore(PatternAnimatorSnapshot snapshot) {
        byte[] extra = snapshot.extra();
        if (extra == null) {
            patternAnimator.restore(snapshot);
            return;
        }
        if (extra.length < Integer.BYTES)
            throw new IllegalArgumentException("Invalid S1 combined animation snapshot");
        ByteBuffer in = ByteBuffer.wrap(extra);
        int publicationLength = in.getInt();
        if (publicationLength < Integer.BYTES || publicationLength > in.remaining())
            throw new IllegalArgumentException("Invalid S1 animation publication length");
        byte[] publication = new byte[publicationLength];
        in.get(publication);
        byte[] palette = new byte[in.remaining()];
        in.get(palette);
        patternAnimator.restore(new PatternAnimatorSnapshot(snapshot.scriptCounters(),
                snapshot.handlerCounters(), publication));
        paletteCycler.restoreCyclerState(palette);
    }
}

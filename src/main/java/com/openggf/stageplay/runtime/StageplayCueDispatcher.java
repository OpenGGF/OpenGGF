package com.openggf.stageplay.runtime;

import com.openggf.stageplay.StageplayDocument;
import java.util.Objects;
import java.util.function.Consumer;

/** Explicit forward event admission, separate from visual sampling and seek. */
public final class StageplayCueDispatcher {
    private final StageplayDocument document;
    private int frame = -1;
    public StageplayCueDispatcher(StageplayDocument document) { this.document = Objects.requireNonNull(document); }
    public int frame() { return frame; }
    public void seek(int frame) {
        if (frame < -1 || frame >= document.durationFrames()) throw new IllegalArgumentException("Cue cursor outside scene");
        this.frame = frame;
    }
    public void advance(int destination, Consumer<StageplayDocument.AudioCue> cueConsumer) {
        if (destination < frame || destination >= document.durationFrames())
            throw new IllegalArgumentException("Use seek for backwards or out-of-range transport");
        Objects.requireNonNull(cueConsumer);
        // Stable ordering: authored order is retained for cues at the same frame.
        var due = document.audio().stream().filter(cue -> cue.frame() > frame && cue.frame() <= destination)
                .sorted(java.util.Comparator.comparingInt(StageplayDocument.AudioCue::frame)).toList();
        frame = destination; // A failed callback cannot cause an already-dispatched cue to fire again.
        for (var cue : due) cueConsumer.accept(cue);
    }
}

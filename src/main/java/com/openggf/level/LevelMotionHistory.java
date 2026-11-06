package com.openggf.level;

import com.openggf.graphics.SpriteMotionProjection;
import com.openggf.graphics.SpritePresentation;

/** Host presentation history only. Native rewind and publication never capture these slots. */
final class LevelMotionHistory {
    record Previous(long serviceStamp, LevelMotionPair.Sample sample) { }
    record Current(long serviceStamp, LevelMotionPackets.Picture picture) { }
    record Offer(SpritePresentation.Frame frame, LevelScrollPresentation scroll,
                 SpriteMotionProjection sprites, SpriteMotionProjection.Offset camera) { }
    private long serviceStamp;
    private Previous previous;
    private Current current;
    private LevelMotionPair pair;
    private boolean presentedLatest;

    void observe(LevelMotionPackets.Picture picture) {
        serviceStamp++;
        previous = current == null ? null : new Previous(current.serviceStamp(), anchorsOnly(current.picture().sample()));
        current = new Current(serviceStamp, picture);
        pair = previous == null ? null : LevelMotionPair.between(previous.sample(), picture.sample());
        presentedLatest = false;
    }
    private static LevelMotionPair.Sample anchorsOnly(LevelMotionPair.Sample sample) {
        return new LevelMotionPair.Sample(sample.motion().anchorsOnly(), sample.scroll(), sample.metadata(), sample.cuts());
    }
    void invalidate() { previous = null; current = null; pair = null; presentedLatest = false; }
    void presentedLatest() { presentedLatest = true; }
    Offer offer(PictureSelection selection) {
        if (current == null) return null;
        var picture = current.picture();
        if (selection instanceof PictureSelection.Latest || presentedLatest || pair == null)
            return new Offer(picture.frame(), picture.sample().scroll(), null, SpriteMotionProjection.Offset.ZERO);
        var projection = pair.at((PictureSelection.Between) selection);
        return new Offer(picture.frame(), projection.scroll(), projection.sprites(), projection.camera());
    }
}

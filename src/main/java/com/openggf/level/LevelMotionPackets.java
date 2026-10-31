package com.openggf.level;

import com.openggf.graphics.SpritePresentation;
import java.util.IdentityHashMap;

/** Non-rewind sidecars follow their exact frame references across native SAT publication. */
final class LevelMotionPackets {
    record Picture(SpritePresentation.Frame frame, LevelMotionPair.Sample sample) { }
    private final IdentityHashMap<SpritePresentation.Frame, Picture> packets = new IdentityHashMap<>();
    boolean active;

    void prepared(Picture picture, SpritePresentation.Frame published) {
        packets.keySet().removeIf(frame -> frame != published);
        packets.put(picture.frame(), picture);
    }
    Picture forFrame(SpritePresentation.Frame frame) { return packets.get(frame); }
    void clear() { packets.clear(); }
}

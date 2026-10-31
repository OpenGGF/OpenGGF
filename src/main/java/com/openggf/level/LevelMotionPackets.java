package com.openggf.level;

import com.openggf.graphics.MotionPacket;
import com.openggf.graphics.SpriteMotionInternalAccess;
import com.openggf.graphics.SpritePresentation;
import java.util.IdentityHashMap;

/** Non-rewind sidecars follow their exact frame references across native SAT publication. */
final class LevelMotionPackets {
    private final IdentityHashMap<SpritePresentation.Frame, MotionPacket> packets = new IdentityHashMap<>();
    boolean active;

    void prepared(SpriteMotionInternalAccess.Capture capture, SpritePresentation.Frame published) {
        packets.keySet().removeIf(frame -> frame != published);
        packets.put(capture.frame(), capture.motion());
    }
    MotionPacket forFrame(SpritePresentation.Frame frame) { return packets.get(frame); }
    void clear() { packets.clear(); }
}

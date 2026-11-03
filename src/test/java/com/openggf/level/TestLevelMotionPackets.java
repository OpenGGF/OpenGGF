package com.openggf.level;

import com.openggf.graphics.MotionPacket;
import com.openggf.graphics.SpritePresentation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestLevelMotionPackets {
    private static LevelMotionPackets.Picture picture(SpritePresentation.Frame frame, MotionPacket motion) {
        return new LevelMotionPackets.Picture(frame, new LevelMotionPair.Sample(motion, mock(LevelScrollPresentation.class),
                new LevelMotionPair.Metadata(0, 0, 320, 224, 16, false), java.util.Map.of()));
    }
    @Test void aPublishedPacketIsSelectedByFrameIdentityAndSurvivesPreparationsDuringLag() {
        var packets = new LevelMotionPackets();
        var published = SpritePresentation.Frame.empty();
        var motion = mock(MotionPacket.class);
        packets.prepared(picture(published, motion), SpritePresentation.Frame.empty());
        for (int service = 0; service < 3; service++) {
            var prepared = new SpritePresentation.Frame(java.util.List.of());
            assertEquals(published, prepared);
            packets.prepared(picture(prepared, mock(MotionPacket.class)), published);
            assertSame(motion, packets.forFrame(published).sample().motion());
            assertNull(packets.forFrame(new SpritePresentation.Frame(java.util.List.of())), "Equal native frames are distinct publications");
        }
        packets.clear();
        assertNull(packets.forFrame(published), "Restore cannot reuse metadata from the discarded timeline");
    }
    @Test void replacedPreparationsAreRetiredWithoutDroppingThePublishedPacket() {
        var packets = new LevelMotionPackets();
        var published = SpritePresentation.Frame.empty();
        var first = new SpritePresentation.Frame(java.util.List.of());
        var second = new SpritePresentation.Frame(java.util.List.of());
        packets.prepared(picture(first, mock(MotionPacket.class)), published);
        packets.prepared(picture(second, mock(MotionPacket.class)), published);
        assertNull(packets.forFrame(first));
        assertNotNull(packets.forFrame(second));
    }
}

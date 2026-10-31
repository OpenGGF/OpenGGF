package com.openggf.level;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.graphics.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestLevelMotionHistory {
    private static final Object KEY = new LevelMotionOwners.ObjectKey(ObjectRefId.dynamic(3, 0, 8));
    private static LevelMotionPackets.Picture picture(int camera, Map<MotionCut, Long> cuts) {
        var owned = MotionPacketFixture.owned(KEY, 100 + camera, 100, camera, 0);
        var registers = new LevelScrollPresentation.Registers(camera, 0, camera, (short) 0, (short) 0,
                0, 512, false, false, false, false, (short) 0, false, (short) 0, null);
        var scroll = new LevelScrollPresentation(registers, new int[]{-camera << 16}, null, null, null, null);
        return new LevelMotionPackets.Picture(owned.frame(), new LevelMotionPair.Sample(owned.packet(), scroll,
                new LevelMotionPair.Metadata(camera, 0, 320, 224, 16, false), cuts));
    }
    @Test void firstPictureIsExactAndAnExactBarrierPreventsAnyLaterBackwardStep() {
        var history = new LevelMotionHistory();
        var first = picture(0, Map.of()); var second = picture(8, Map.of());
        assertNull(history.offer(PictureSelection.between(.5)));
        history.observe(first);
        assertNull(history.offer(PictureSelection.between(.5)).sprites());
        history.observe(second);
        assertEquals(new SpriteMotionProjection.Offset(-4, 0), history.offer(PictureSelection.between(.5)).camera());
        history.presentedLatest();
        assertNull(history.offer(PictureSelection.between(0)).sprites());
        assertSame(second.frame(), history.offer(PictureSelection.between(0)).frame());
        history.observe(picture(16, Map.of()));
        assertEquals(new SpriteMotionProjection.Offset(-4, 0), history.offer(PictureSelection.between(.5)).camera());
    }
    @Test void lagObservationsHoldThePublishedFrameAndInvalidationNeedsAFreshPair() {
        var history = new LevelMotionHistory();
        var first = picture(0, Map.of()); var second = picture(8, Map.of());
        history.observe(first); history.observe(second);
        history.observe(second);
        var held = history.offer(PictureSelection.between(0));
        assertSame(second.frame(), held.frame());
        assertEquals(SpriteMotionProjection.Offset.ZERO, held.camera());
        assertEquals(SpriteMotionProjection.Offset.ZERO, held.sprites().offset(second.frame().tiles().getFirst()));
        history.invalidate();
        assertNull(history.offer(PictureSelection.between(.5)));
        history.observe(second);
        assertNull(history.offer(PictureSelection.between(.5)).sprites());
    }
    @Test void priorSlotsRetainAnchorsButRetireNativeTileAndArtReferences() throws Exception {
        var history = new LevelMotionHistory();
        var first = picture(0, Map.of()); history.observe(first); history.observe(picture(8, Map.of()));
        var field = LevelMotionHistory.class.getDeclaredField("previous"); field.setAccessible(true);
        var previous = (LevelMotionHistory.Previous) field.get(history);
        assertEquals(first.sample().motion().owners(), previous.sample().motion().owners());
        assertNull(previous.sample().motion().owner(first.frame().tiles().getFirst()));
        assertNotSame(first.sample().motion(), previous.sample().motion());
    }
    @Test void cutRevisionsSurviveReplacedPreparationsAndOnlyCutTheirPublishedGeneration() {
        var history = new LevelMotionHistory(); var packets = new LevelMotionPackets();
        var first = picture(0, Map.of()); var second = picture(4, Map.of());
        history.observe(first); history.observe(second);
        var cut = MotionCut.object(((LevelMotionOwners.ObjectKey) KEY).id());
        var abandoned = picture(8, Map.of(cut, 1L)); var replacement = picture(10, Map.of(cut, 1L));
        packets.prepared(second, first.frame());
        packets.prepared(abandoned, second.frame());
        assertEquals(new SpriteMotionProjection.Offset(-2, 0), history.offer(PictureSelection.between(.5)).camera());
        packets.prepared(replacement, second.frame());
        assertNull(packets.forFrame(abandoned.frame()));
        history.observe(packets.forFrame(replacement.frame()));
        var published = history.offer(PictureSelection.between(.5));
        assertEquals(SpriteMotionProjection.Offset.ZERO, published.sprites().offset(replacement.frame().tiles().getFirst()));
        var next = picture(14, Map.of(cut, 1L)); history.observe(next);
        assertEquals(new SpriteMotionProjection.Offset(-2, 0),
                history.offer(PictureSelection.between(.5)).sprites().offset(next.frame().tiles().getFirst()));
        var cutAgain = picture(16, Map.of(cut, 2L)); history.observe(cutAgain);
        assertEquals(SpriteMotionProjection.Offset.ZERO,
                history.offer(PictureSelection.between(.5)).sprites().offset(cutAgain.frame().tiles().getFirst()));
    }
}

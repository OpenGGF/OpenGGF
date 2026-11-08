package com.openggf.level.render;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpriteMaskReplayRole;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.ObjectSpriteSheet;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestSpriteFrameCapture {
    private final RecordingGraphics graphics = new RecordingGraphics();
    private final ObjectSpriteSheet sheet = new ObjectSpriteSheet(new Pattern[]{new Pattern()},
            List.of(new SpriteMappingFrame(List.of(new SpriteMappingPiece(-4,-8,1,1,0,false,false,0)))), 0,1);
    private final PatternSpriteRenderer renderer = new PatternSpriteRenderer(sheet, graphics);

    @Test void fullFramesRecordEveryOverloadAndSuppressEvenBeforeAtlasBinding() {
        var captured = SpriteFrameCaptureInternalAccess.capture(() -> {
            renderer.drawFrameIndex(0,100,80);
            renderer.drawFrameIndex(0,101,81,true,false);
            renderer.drawFrameIndex(0,102,82,false,true,2);
            renderer.drawFrameIndexWithSatReplayRole(0,103,83,true,true,3,SpriteMaskReplayRole.NORMAL);
        });
        assertEquals(List.of(
                new SpriteFrameCaptureInternalAccess.FrameCall(renderer,sheet,0,100,80,false,false,-1),
                new SpriteFrameCaptureInternalAccess.FrameCall(renderer,sheet,0,101,81,true,false,-1),
                new SpriteFrameCaptureInternalAccess.FrameCall(renderer,sheet,0,102,82,false,true,2),
                new SpriteFrameCaptureInternalAccess.FrameCall(renderer,sheet,0,103,83,true,true,3)), captured.calls());
        assertTrue(graphics.tiles.isEmpty());
    }

    @Test void partialOrAlteredDrawsAreExplicitlyRejected() {
        var calls = List.<Runnable>of(
                () -> renderer.drawPieces(sheet.getFrame(0).pieces(),0,0,false,false),
                () -> renderer.drawFramePieceByIndex(0,0,0,0,false,false),
                () -> renderer.drawPatternIndex(0,0,0,0),
                () -> renderer.drawFrameIndexFilteredByPriority(0,0,0,false,false,-1,true),
                () -> renderer.drawFrameIndexFilteredByPriority(0,0,0,false,false,-1,true,SpriteMaskReplayRole.NORMAL),
                () -> renderer.drawFrameIndexForcedPriority(0,0,0,false,false,-1,true),
                () -> renderer.drawFrameIndexWithPaletteBase(0,0,0,false,false,1));
        for (var draw : calls) {
            var capture = SpriteFrameCaptureInternalAccess.capture(draw);
            assertEquals(1,capture.calls().size());
            assertInstanceOf(SpriteFrameCaptureInternalAccess.Untranscribable.class,capture.calls().getFirst());
        }
        assertTrue(graphics.tiles.isEmpty());
    }

    @Test void scopeRestoresAfterExceptionsAndInactivePieceOutputIsIdentical() {
        renderer.ensurePatternsCached(graphics,512);
        renderer.drawFrameIndex(0,100,80,true,false,2);
        var before = List.copyOf(graphics.tiles);
        assertFalse(before.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SpriteFrameCaptureInternalAccess.capture(() -> {
            renderer.drawFrameIndex(0,100,80);
            throw new IllegalArgumentException("injected failure");
        }));
        assertThrows(IllegalStateException.class, () -> SpriteFrameCaptureInternalAccess.capture(() ->
                SpriteFrameCaptureInternalAccess.capture(() -> {})));
        assertEquals(before,graphics.tiles);
        graphics.tiles.clear();
        renderer.drawFrameIndex(0,100,80,true,false,2);
        assertEquals(before,graphics.tiles);
    }

    private static final class RecordingGraphics extends GraphicsManager {
        final List<String> tiles = new ArrayList<>();
        @Override public void cachePatternTexture(Pattern pattern,int index) { }
        @Override public void renderPatternWithId(int index,PatternDesc desc,int x,int y) {
            tiles.add(index+":"+desc.get()+":"+x+":"+y);
        }
    }
}

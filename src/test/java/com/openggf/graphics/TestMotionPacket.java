package com.openggf.graphics;

import com.openggf.game.rewind.identity.*;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.level.render.SpritePresentationRenderer;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestMotionPacket {
    private GraphicsManager graphics;
    @BeforeEach void setup() {
        GraphicsManager.destroyForReinit();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
    }
    @AfterEach void cleanup() { SessionManager.clear(); GraphicsManager.destroyForReinit(); }
    private MotionPacket.Owner owner(int ordinal) {
        return new MotionPacket.Owner(new MotionPacket.ObjectKey(ObjectRefId.dynamic(3, 0, ordinal)),
                125, 240, MotionPacket.Anchoring.FOLLOW);
    }
    @Test void frozenSidecarRetainsExactCollectionOriginAndUsesTileIdentity() {
        var owner = owner(10);
        var capture = SpriteMotionInternalAccess.collect(graphics, 100, 200,
                () -> SpritePresentationRenderer.prepare(graphics, 100, 200, () -> {
                    SpriteMotionInternalAccess.withOwner(graphics, owner,
                            () -> graphics.renderPatternWithId(3, new PatternDesc(), 125, 240));
                    graphics.renderPatternWithId(3, new PatternDesc(), 125, 240);
                }));
        assertEquals(100, capture.motion().cameraX());
        assertEquals(200, capture.motion().cameraY());
        assertEquals(capture.frame().tiles().getFirst(), capture.frame().tiles().getLast());
        assertSame(owner, capture.motion().owner(capture.frame().tiles().getFirst()));
        assertNull(capture.motion().owner(capture.frame().tiles().getLast()));
        assertThrows(UnsupportedOperationException.class, () -> capture.motion().owners().clear());
        assertNull(graphics.spriteMotionCollection);
        assertNull(graphics.spriteMotionOwner);
    }
    @Test void satEnvelopeCopiesKeepOwnershipThroughClippingAndFragments() {
        var owner = owner(1);
        var tile = SpriteSatEntry.of(20, 20, 2, 2, 1, 0, false, false, false, false).withMotionOwner(owner);
        for (var copy : List.of(tile.clipRect(0, 1, 0, 1), tile.clipRows(1, 1),
                tile.withVisibleScanlines(20, 24), tile.withMaskReplayRole(SpriteMaskReplayRole.NORMAL),
                tile.withGhost(new SpriteSatEntry.Ghost(.5f, false)), tile.withPresentationSubject(SpritePresentation.Subject.WORLD),
                tile.withTileOcclusionPaletteMask(2), tile.withPixelOperation(SpriteSatEntry.PixelOperation.SHADOW_15),
                tile.withReplayFragments(List.of(tile)))) assertSame(owner, copy.motionOwner());
    }
    @Test void satReplayRetainsTheOwnerAfterTheProducerScopeCloses() {
        var owner = owner(1);
        var capture = SpriteMotionInternalAccess.collect(graphics, 10, 20,
                () -> SpritePresentationRenderer.prepare(graphics, 10, 20, () -> {
                    graphics.beginSpriteSatCollection();
                    SpriteMotionInternalAccess.withOwner(graphics, owner, () -> graphics.submitSpriteSatPiece(
                            new SpritePieceRenderer.PreparedPiece(20, 30, 1, 1, 3, 3, 0, false, false, false, false,
                                    SpriteMaskReplayRole.NORMAL, 0, 1, 0, 1, "player")));
                    graphics.endSpriteSatCollectionAndReplay();
                }));
        assertEquals(1, capture.frame().tiles().size());
        assertSame(owner, capture.motion().owner(capture.frame().tiles().getFirst()));
    }
    @Test void anchorFreeScreenTagsSurviveSatReplay() {
        var capture = SpriteMotionInternalAccess.collect(graphics, 10, 20,
                () -> SpritePresentationRenderer.prepare(graphics, 10, 20, () -> {
                    graphics.beginSpriteSatCollection();
                    SpriteMotionInternalAccess.withOwner(graphics, MotionPacket.Screen.INSTANCE,
                            () -> graphics.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                                    20, 30, 1, 1, 3, 3, 0, false, false, false, false,
                                    SpriteMaskReplayRole.NORMAL, 0, 1, 0, 1, "screen")));
                    graphics.endSpriteSatCollectionAndReplay();
                }));
        var tile = capture.frame().tiles().getFirst();
        assertTrue(capture.motion().screen(tile));
        assertNull(capture.motion().owner(tile));
        assertTrue(capture.motion().owners().isEmpty(), "Screen positioning invents no world anchor");
        var camera = new SpriteMotionProjection.Offset(-4, 2);
        var projection = new SpriteMotionProjection(capture.motion(), java.util.Map.of(), .5, camera);
        assertEquals(camera, projection.offset(tile));
        assertThrows(AssertionError.class, () -> assertEquals(SpriteMotionProjection.Offset.ZERO,
                projection.offset(tile)), "A dropped SCREEN tag must fail the projection comparison");
    }
    @Test void failedCollectionAndNestedOwnerScopesCannotLeakMetadata() {
        var outer = owner(1); var inner = owner(2);
        assertThrows(IllegalArgumentException.class, () -> SpriteMotionInternalAccess.collect(graphics, 0, 0,
                () -> {
                    SpriteMotionInternalAccess.withOwner(graphics, outer, () -> {
                        SpriteMotionInternalAccess.withOwner(graphics, inner, () -> assertSame(inner, graphics.spriteMotionOwner));
                        assertSame(outer, graphics.spriteMotionOwner);
                        throw new IllegalArgumentException("failed producer");
                    });
                    return SpritePresentation.Frame.empty();
                }));
        assertNull(graphics.spriteMotionCollection); assertNull(graphics.spriteMotionOwner);
        assertNotEquals(new MotionPacket.PlayerKey(PlayerRefId.mainPlayer(), new Object()),
                new MotionPacket.PlayerKey(PlayerRefId.mainPlayer(), new Object()));
        assertNotEquals(owner(1).key(), owner(2).key(), "Slot reuse has a new creation ordinal");
    }
    @Test void centralHeadCompositionCarriesTheOwnerToEveryReplacementFragment() {
        var pattern = new com.openggf.level.Pattern();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pattern.setPixel(x, y, (byte) 5);
        var mask = new SpritePresentation.HeadMask(0, 8, java.util.Set.of(0), List.of(
                new SpritePresentation.MaskPoint(0, 0), new SpritePresentation.MaskPoint(8, 0),
                new SpritePresentation.MaskPoint(8, 8), new SpritePresentation.MaskPoint(0, 8)));
        var head = new SpritePresentation.HeadTransform(mask, 0, 20, 30, false, false, 200,
                PatternAtlasRange.PLAYER_PRESENTATION.base(), java.util.Map.of(3, SpritePresentationRenderer.version(pattern)),
                SpritePresentationRenderer.headSatReplay());
        var owner = owner(5);
        var capture = SpriteMotionInternalAccess.collect(graphics, 10, 20,
                () -> SpritePresentationRenderer.prepare(graphics, 10, 20, () ->
                        SpriteMotionInternalAccess.withOwner(graphics, owner, () ->
                                SpritePresentation.withSubject(graphics, new SpritePresentation.Subject("player",
                                        SpritePresentation.Part.BODY, false, 200, head), () ->
                                        graphics.renderPatternWithId(3, new PatternDesc(), 20, 30)))));
        assertTrue(capture.frame().tiles().size() > 1, "The central composer must replace and enlarge the source");
        for (var tile : capture.frame().tiles()) assertSame(owner, capture.motion().owner(tile));
    }
    @Test void inactivePreparationLeavesNativeFramesUnchangedAndAllocatesNoSidecar() {
        var frame = SpritePresentationRenderer.prepare(graphics, 10, 20,
                () -> graphics.renderPatternWithId(3, new PatternDesc(), 20, 30));
        assertNull(graphics.spriteMotionCollection);
        assertNull(graphics.spriteMotionOwner);
        assertEquals(10, frame.tiles().getFirst().x());
        assertEquals(10, frame.tiles().getFirst().y());
    }
}

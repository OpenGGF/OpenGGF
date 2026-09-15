package com.openggf.graphics;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.PatternDesc;
import org.junit.jupiter.api.*;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class TestSpritePresentation {
    private GraphicsManager graphics;
    @BeforeEach void setup() {
        GraphicsManager.destroyForReinit();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
    }
    @AfterEach void cleanup() { SessionManager.clear(); GraphicsManager.destroyForReinit(); }

    @Test void headlessPreparationCopiesMutableDescriptorsAndCameraCoordinatesWithoutDrawing() {
        PatternDesc desc = new PatternDesc();
        desc.setPaletteIndex(3); desc.setHFlip(true); desc.setPriority(true);
        var frame = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 100, 200, () -> {
            SpritePresentation.layer(graphics, SpritePresentation.Layer.PLAYER);
            graphics.setUseSpritePriorityShader(true);
            graphics.setCurrentSpriteHighPriority(true);
            graphics.renderPatternWithId(9000, desc, 125, 240);
            desc.setPaletteIndex(1); desc.setHFlip(false);
            SpritePresentation.layer(graphics, SpritePresentation.Layer.HUD);
            graphics.setUseSpritePriorityShader(false);
            graphics.renderPatternWithId(42, desc, 108, 208);
        });
        assertEquals(2, frame.tiles().size());
        var player = frame.tiles().getFirst();
        assertEquals(9000, player.patternId()); assertEquals(3, player.palette());
        assertTrue(player.hFlip()); assertTrue(player.priority());
        assertEquals(25, player.x()); assertEquals(40, player.y());
        assertEquals(SpritePresentation.Layer.HUD, frame.tiles().getLast().layer());
        assertEquals(8, frame.tiles().getLast().x());
        assertTrue(graphics.commands.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> frame.tiles().clear());
    }

    @Test void replayFiltersPublishedLayersAndKeepsScreenPositionsWithAnotherCamera() {
        PatternDesc desc = new PatternDesc();
        var original = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 100, 200, () -> {
            SpritePresentation.layer(graphics, SpritePresentation.Layer.OBJECT);
            graphics.renderPatternWithId(3, desc, 132, 216);
            SpritePresentation.layer(graphics, SpritePresentation.Layer.HUD);
            graphics.renderPatternWithId(4, desc, 108, 208);
        });
        var replay = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 900, 700,
                () -> com.openggf.level.render.SpritePresentationRenderer.draw(graphics, original, 900, 700,
                        layer -> layer == SpritePresentation.Layer.OBJECT));
        assertEquals(1, replay.tiles().size());
        assertEquals(32, replay.tiles().getFirst().x());
        assertEquals(16, replay.tiles().getFirst().y());
        assertEquals(3, replay.tiles().getFirst().patternId());
    }

    @Test void failedProducerCannotLeaveCaptureModeOrAlterTheLastImmutableFrame() {
        var input = new ArrayList<SpritePresentation.Tile>();
        var immutable = new SpritePresentation.Frame(input);
        input.add(new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, 1, 0,
                false, false, false, 0, 0, 8, 8, false, 15, false, 1));
        assertThrows(IllegalArgumentException.class, () -> com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            throw new IllegalArgumentException("producer failed");
        }));
        assertNull(graphics.spritePresentationBuilder);
        assertTrue(immutable.tiles().isEmpty());
    }

    @Test void satCollectionRetainsPriorityAndLayerBeforeItsTransientScratchIsCleared() {
        var frame = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            graphics.beginSpriteSatCollection();
            graphics.setCurrentSpriteSatBucket(2);
            SpritePresentation.layer(graphics, SpritePresentation.Layer.PLAYER);
            graphics.submitSpriteSatPiece(new com.openggf.level.render.SpritePieceRenderer.PreparedPiece(
                    10, 20, 1, 1, 3, 3, 2, false, false, true, false,
                    SpriteMaskReplayRole.NORMAL, 0, 1, 0, 1, "player"));
            graphics.endSpriteSatCollectionAndReplay();
        });
        assertEquals(1, frame.tiles().size());
        assertTrue(frame.tiles().getFirst().priority());
        assertEquals(SpritePresentation.Layer.PLAYER, frame.tiles().getFirst().layer());
        assertFalse(graphics.isSpriteSatCollectionActive());
        assertTrue(graphics.commands.isEmpty());
    }

    @Test void mutableDplcBanksCannotChangeAnAlreadyPreparedPresentation() {
        var pattern = new com.openggf.level.Pattern();
        pattern.setPixel(2, 3, (byte) 7);
        var frame = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            com.openggf.level.render.SpritePresentationRenderer.bindPatternBank(graphics, 9000, new com.openggf.level.Pattern[]{pattern});
            graphics.renderPatternWithId(9000, new PatternDesc(), 0, 0);
        });
        pattern.setPixel(2, 3, (byte) 12);
        assertEquals(7, com.openggf.level.render.SpritePresentationRenderer.pattern(frame.patternVersions().get(9000)).getPixel(2, 3));
        var second = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            com.openggf.level.render.SpritePresentationRenderer.bindPatternBank(graphics, 9000, new com.openggf.level.Pattern[]{pattern});
            graphics.renderPatternWithId(9000, new PatternDesc(), 0, 0);
        });
        assertEquals(12, com.openggf.level.render.SpritePresentationRenderer.pattern(second.patternVersions().get(9000)).getPixel(2, 3));
        assertNotEquals(frame.patternVersions(), second.patternVersions());
    }

    @Test void unusedDplcTailDoesNotBecomeDisplayedOrRewindState() {
        var used = new com.openggf.level.Pattern();
        var unused = new com.openggf.level.Pattern();
        Runnable produce = () -> {
            com.openggf.level.render.SpritePresentationRenderer.bindPatternBank(graphics, 9000, new com.openggf.level.Pattern[]{used, unused});
            graphics.renderPatternWithId(9000, new PatternDesc(), 0, 0);
        };
        var before = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0, produce);
        unused.setPixel(0, 0, (byte) 12);
        var after = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0, produce);
        assertEquals(java.util.Set.of(9000), before.patternVersions().keySet());
        assertEquals(before, after, "unreferenced animation history cannot change the displayed frame");
    }

    @Test void primitiveGeometryIsRetainedWithoutKeepingAnExecutableCommand() {
        var frame = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 100, 200, () -> {
            graphics.registerCommand(new GLCommand(GLCommand.CommandType.RECTI,
                    org.lwjgl.opengl.GL11.GL_QUADS, 1, 0, 0, 105, 210, 115, 220));
        });
        assertEquals(1, frame.primitives().size());
        var primitive = assertInstanceOf(GLCommand.PresentationPrimitive.class, frame.primitives().getFirst().primitive());
        assertEquals(5, primitive.x1()); assertEquals(10, primitive.y1());
        assertEquals(15, primitive.x2()); assertEquals(20, primitive.y2());
        assertTrue(graphics.commands.isEmpty());
    }

    @Test void groupedObjectVerticesRetainTheirDrawModeAndIndependentGeometry() {
        var commands = new ArrayList<GLCommand>();
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, 1, 0, 0, 105, 210, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, 1, 0, 0, 115, 220, 0, 0));
        var frame = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 100, 200, () -> {
            graphics.enqueueDebugLineState();
            graphics.registerCommand(new GLCommandGroup(org.lwjgl.opengl.GL11.GL_LINES, commands));
            graphics.enqueueDefaultShaderState();
        });
        commands.clear();
        var group = assertInstanceOf(GLCommandGroup.PresentationGroup.class, frame.primitives().getFirst().primitive());
        assertEquals(org.lwjgl.opengl.GL11.GL_LINES, group.method());
        assertEquals(2, group.vertices().size());
        assertEquals(5, group.vertices().getFirst().x1());
        assertEquals(20, group.vertices().getLast().y1());
        assertTrue(graphics.commands.isEmpty());
    }

    @Test void verticalWrapIsResolvedAtPreparationRatherThanAgainstTheLaterCamera() {
        graphics.enableVerticalWrapAdjust(2048, 2000);
        var frame = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 2000,
                () -> graphics.renderPatternWithId(1, new PatternDesc(), 0, 16));
        graphics.disableVerticalWrapAdjust();
        assertEquals(64, frame.tiles().getFirst().y());
    }
}

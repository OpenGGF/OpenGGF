package com.openggf.editor;

import com.openggf.tests.TestEnvironment;
import com.openggf.debug.DebugColor;
import com.openggf.game.session.EditorCursorState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.editor.render.EditorTextRenderer;
import com.openggf.editor.render.EditorWorldOverlayRenderer;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.MutableLevel;
import com.openggf.level.PatternDesc;
import com.openggf.level.Pattern;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEditorRenderingSmoke {
    private static final GraphicsManager TEST_GRAPHICS = GraphicsManager.getInstance();

    @BeforeEach
    void setUp() {
        TestEnvironment.resetEngineOnly();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        TEST_GRAPHICS.resetState();
    }

    @Test
    void textRenderer_buildsCommandsForRenderableLines() {
        InspectableTextRenderer renderer = new InspectableTextRenderer();

        List<EditorTextRenderer.TextCommand> commands = renderer.buildCommands(List.of("One", "", "Two"),
                8, 12);

        assertEquals(2, commands.size());
        assertEquals("One", commands.get(0).text());
        assertEquals("Two", commands.get(1).text());
        assertEquals(10, commands.get(0).lineHeight());
        assertEquals(12, commands.get(0).y());
        assertEquals(32, commands.get(1).y());
        assertEquals(commands.get(0).y() + commands.get(0).lineHeight() * 2, commands.get(1).y());
    }

    @Test
    void textRenderer_queuesTextBatchInsteadOfRenderingImmediately() throws Exception {
        GraphicsManager.getInstance().resetState();
        InspectableTextRenderer renderer = new InspectableTextRenderer();

        assertEquals(0, graphicsCommandQueueSize());

        renderer.renderLines(List.of("Queued"), 8, 12);

        assertEquals(1, graphicsCommandQueueSize());
    }

    @Test
    void textRenderer_executesQueuedBatchThroughPixelFontBackend() throws Exception {
        GraphicsManager.getInstance().resetState();
        float[] projectionMatrix = {
                1f, 2f, 3f, 4f,
                5f, 6f, 7f, 8f,
                9f, 10f, 11f, 12f,
                13f, 14f, 15f, 16f
        };
        TEST_GRAPHICS.setProjectionMatrixBuffer(projectionMatrix);
        RecordingPixelFontTextRenderer pixelFontTextRenderer = new RecordingPixelFontTextRenderer();
        InspectableTextRenderer renderer = new InspectableTextRenderer(pixelFontTextRenderer);

        renderer.renderLines(List.of("Queued"), 8, 12);

        assertEquals(1, graphicsCommandQueueSize());
        queuedGraphicsCommand().execute(0, 0, 320, 224);

        assertEquals(1, pixelFontTextRenderer.projectionCalls);
        assertSame(projectionMatrix, pixelFontTextRenderer.lastProjection);
        assertEquals(1, pixelFontTextRenderer.calls.size());
        DrawCall call = pixelFontTextRenderer.calls.get(0);
        assertEquals("Queued", call.text());
        assertEquals(8, call.x());
        assertEquals(12, call.y());
        assertEquals(DebugColor.WHITE, call.color());
    }

    @Test
    void worldOverlayRenderer_usesCurrentSessionCursorWhenRendering() {
        SessionManager.openGameplaySession(new Sonic2GameModule());
        SessionManager.enterEditorMode(new EditorCursorState(320, 448));
        CapturingWorldOverlayRenderer renderer = new CapturingWorldOverlayRenderer();

        renderer.render();

        assertEquals(SessionManager.getCurrentEditorMode().getCursor(), renderer.capturedCursor);
    }

    @Test
    void worldOverlayRenderer_buildsDifferentCursorCommandsForDifferentPositions() {
        InspectableWorldOverlayRenderer renderer = new InspectableWorldOverlayRenderer();

        List<GLCommand> first = renderer.buildCursorCommands(new EditorCursorState(320, 448));
        List<GLCommand> second = renderer.buildCursorCommands(new EditorCursorState(384, 480));

        assertFalse(first.isEmpty());
        assertFalse(second.isEmpty());
        assertNotEquals(commandSignature(first), commandSignature(second));
    }

    @Test
    void worldOverlayRenderer_buildsGridCommandsAlongsideCursor() {
        InspectableWorldOverlayRenderer renderer = new InspectableWorldOverlayRenderer();

        List<GLCommand> cursorOnly = renderer.buildCursorCommands(new EditorCursorState(64, 64));
        List<GLCommand> worldCommands = renderer.buildWorldCommands(new EditorCursorState(64, 64));

        assertFalse(cursorOnly.isEmpty());
        assertTrue(worldCommands.size() > cursorOnly.size());
        assertTrue(worldCommands.size() - cursorOnly.size() >= 16);
    }

    @Test
    void worldOverlayRenderer_buildsVisibleSpawnMarkersAndObjectLabelsInSpawnModes() {
        PreviewLevel source = new PreviewLevel();
        source.setSpawns(List.of(
                new ObjectSpawn(120, 80, 0x2A, 0x0F, 0, true, 0, 7),
                new ObjectSpawn(500, 500, 0x01, 0x02, 0, true, 0, 8)),
                List.of(new RingSpawn(160, 96, 9), new RingSpawn(600, 600, 10)));
        MutableLevel level = MutableLevel.snapshot(source);
        InspectableWorldOverlayRenderer renderer = new InspectableWorldOverlayRenderer();

        EditorWorldOverlayRenderer.SpawnOverlay overlay = renderer.buildSpawnOverlay(
                level, EditorSpawnEditMode.OBJECTS, 100, 64, 320, 224);

        assertFalse(overlay.markerCommands().isEmpty());
        assertTrue(commandSignature(overlay.markerCommands()).contains("114,144"));
        assertTrue(commandSignature(overlay.markerCommands()).contains("160,132"));
        assertFalse(commandSignature(overlay.markerCommands()).contains("500,500"));
        assertEquals(List.of(new EditorWorldOverlayRenderer.SpawnLabel("2A:0F", 120, 80)),
                overlay.labels());

        EditorWorldOverlayRenderer.SpawnOverlay ringModeOverlay = renderer.buildSpawnOverlay(
                level, EditorSpawnEditMode.RINGS, 100, 64, 320, 224);
        assertEquals(commandSignature(overlay.markerCommands()),
                commandSignature(ringModeOverlay.markerCommands()));
        assertEquals(overlay.labels(), ringModeOverlay.labels());
    }

    @Test
    void worldOverlayRenderer_hidesSpawnMarkersOutsideSpawnEditModes() {
        PreviewLevel source = new PreviewLevel();
        source.setSpawns(List.of(new ObjectSpawn(120, 80, 0x2A, 0x0F, 0, true, 0, 7)),
                List.of(new RingSpawn(160, 96, 9)));
        MutableLevel level = MutableLevel.snapshot(source);
        InspectableWorldOverlayRenderer renderer = new InspectableWorldOverlayRenderer();

        EditorWorldOverlayRenderer.SpawnOverlay overlay = renderer.buildSpawnOverlay(
                level, EditorSpawnEditMode.TILES, 0, 0, 320, 224);

        assertTrue(overlay.markerCommands().isEmpty());
        assertTrue(overlay.labels().isEmpty());
    }

    @Test
    void worldOverlayRenderer_usesWrapped16BitDeltasPastSignedCameraBoundary() {
        PreviewLevel source = new PreviewLevel();
        source.setSpawns(List.of(new ObjectSpawn(0x8010, 0x8010, 0x7E, 0xA5, 0, true, 0, 7)),
                List.of(new RingSpawn(0x8180, 0x8140, 9)));
        MutableLevel level = MutableLevel.snapshot(source);
        InspectableWorldOverlayRenderer renderer = new InspectableWorldOverlayRenderer();

        EditorWorldOverlayRenderer.SpawnOverlay overlay = renderer.buildSpawnOverlay(
                level, EditorSpawnEditMode.OBJECTS, (short) 0x7FF0, (short) 0x7FF0, 512, 224);

        assertFalse(overlay.markerCommands().isEmpty());
        assertEquals(List.of(new EditorWorldOverlayRenderer.SpawnLabel("7E:A5", 0x8010, 0x8010)),
                overlay.labels());
        assertTrue(commandSignature(overlay.markerCommands()).contains("32778,-32560"),
                commandSignature(overlay.markerCommands()));
    }

    @Test
    void worldOverlayRenderer_leavesPlacementSpritesAndLabelsToWindowChrome() throws Exception {
        PreviewLevel source = new PreviewLevel();
        source.setSpawns(List.of(
                        new ObjectSpawn(120, 80, 0x2A, 0x0F, 0, true, 0, 7),
                        new ObjectSpawn(160, 96, 0x04, 0xB0, 0, true, 0, 8)),
                List.of(new RingSpawn(192, 112, 9)));
        MutableLevel level = MutableLevel.snapshot(source);
        SessionManager.openGameplaySession(new Sonic2GameModule());
        SessionManager.getCurrentWorldSession().setCurrentLevel(level);
        SessionManager.enterEditorMode(new EditorCursorState(120, 80));
        SessionManager.getCurrentEditorMode().getCamera().setX((short) 32);
        SessionManager.getCurrentEditorMode().getCamera().setY((short) 16);
        SessionManager.getCurrentEditorMode().getCamera().captureRenderCopy();

        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        RecordingPixelFontTextRenderer pixelFont = new RecordingPixelFontTextRenderer();
        EditorTextRenderer textRenderer = new EditorTextRenderer(TEST_GRAPHICS, pixelFont);
        EditorWorldOverlayRenderer renderer = new EditorWorldOverlayRenderer(controller, TEST_GRAPHICS, textRenderer);
        TEST_GRAPHICS.resetState();

        renderer.render();

        assertEquals(0, graphicsCommandQueueSize(), "No ROM-font labels or debug crosses in the world pass");
        assertTrue(pixelFont.calls.isEmpty());
    }

    private static final class InspectableTextRenderer extends EditorTextRenderer {
        private InspectableTextRenderer() {
            super(TEST_GRAPHICS);
        }

        private InspectableTextRenderer(PixelFontTextRenderer textRenderer) {
            super(TEST_GRAPHICS, textRenderer);
        }

        private List<TextCommand> buildCommands(List<String> lines, int x, int y) {
            return buildTextCommands(lines, x, y);
        }
    }

    private static final class RecordingPixelFontTextRenderer extends PixelFontTextRenderer {
        private int projectionCalls;
        private float[] lastProjection;
        private final List<DrawCall> calls = new ArrayList<>();

        @Override
        public void setProjectionMatrix(float[] projectionMatrix) {
            projectionCalls++;
            lastProjection = projectionMatrix;
        }

        @Override
        public void drawShadowedText(String text, int x, int y, DebugColor color) {
            calls.add(new DrawCall(text, x, y, color));
        }
    }

    private record DrawCall(String text, int x, int y, DebugColor color) {}

    private static final class InspectableWorldOverlayRenderer extends EditorWorldOverlayRenderer {
        private List<GLCommand> buildCursorCommands(EditorCursorState cursor) {
            List<GLCommand> commands = new ArrayList<>();
            appendCursorCommands(commands, cursor);
            return commands;
        }

        private List<GLCommand> buildWorldCommands(EditorCursorState cursor) {
            List<GLCommand> commands = new ArrayList<>();
            appendWorldCommands(commands, cursor);
            return commands;
        }

        private SpawnOverlay buildSpawnOverlay(MutableLevel level,
                                               EditorSpawnEditMode mode,
                                               int left,
                                               int top,
                                               int right,
                                               int bottom) {
            return super.buildSpawnOverlay(level, mode, left, top, right, bottom);
        }
    }

    private static final class CapturingWorldOverlayRenderer extends EditorWorldOverlayRenderer {
        private EditorCursorState capturedCursor;

        @Override
        protected void appendCursorCommands(List<GLCommand> commands, EditorCursorState cursor) {
            capturedCursor = cursor;
        }
    }

    private static String commandSignature(List<GLCommand> commands) {
        StringBuilder signature = new StringBuilder();
        for (GLCommand command : commands) {
            signature.append(command.getCommandType())
                    .append(':')
                    .append((int) command.getX1())
                    .append(',')
                    .append((int) command.getY1())
                    .append(';');
        }
        return signature.toString();
    }

    @SuppressWarnings("unchecked")
    private static int graphicsCommandQueueSize() throws Exception {
        Field commands = GraphicsManager.class.getDeclaredField("commands");
        commands.setAccessible(true);
        return ((List<GLCommandable>) commands.get(GraphicsManager.getInstance())).size();
    }

    @SuppressWarnings("unchecked")
    private static GLCommandable queuedGraphicsCommand() throws Exception {
        return queuedGraphicsCommand(0);
    }

    @SuppressWarnings("unchecked")
    private static GLCommandable queuedGraphicsCommand(int index) throws Exception {
        Field commands = GraphicsManager.class.getDeclaredField("commands");
        commands.setAccessible(true);
        return ((List<GLCommandable>) commands.get(GraphicsManager.getInstance())).get(index);
    }

    private static LevelEditorController createPreviewController() {
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(createPreviewLevel());
        return controller;
    }

    private static MutableLevel createPreviewLevel() {
        return MutableLevel.snapshot(new PreviewLevel());
    }

    private static final class PreviewLevel extends AbstractLevel {
        PreviewLevel() {
            super(0);
            patternCount = 1;
            patterns = new Pattern[] { new Pattern() };

            chunkCount = 9;
            chunks = new Chunk[chunkCount];
            for (int i = 0; i < chunkCount; i++) {
                chunks[i] = new Chunk();
                chunks[i].setPatternDesc(0, 0, new PatternDesc(i * 4 + 1));
                chunks[i].setPatternDesc(1, 0, new PatternDesc(i * 4 + 2));
                chunks[i].setPatternDesc(0, 1, new PatternDesc(i * 4 + 3));
                chunks[i].setPatternDesc(1, 1, new PatternDesc(i * 4 + 4));
            }

            blockCount = 3;
            blocks = new Block[blockCount];
            for (int i = 0; i < blockCount; i++) {
                blocks[i] = new Block(2);
                blocks[i].setChunkDesc(0, 0, new ChunkDesc(i));
                blocks[i].setChunkDesc(1, 0, new ChunkDesc((i + 1) % chunkCount));
                blocks[i].setChunkDesc(0, 1, new ChunkDesc((i + 2) % chunkCount));
                blocks[i].setChunkDesc(1, 1, new ChunkDesc((i + 3) % chunkCount));
            }

            solidTileCount = 0;
            solidTiles = new com.openggf.level.SolidTile[0];
            map = new com.openggf.level.Map(2, 2, 2);
            map.setValue(0, 0, 0, (byte) 0);
            map.setValue(0, 1, 0, (byte) 1);
            map.setValue(0, 0, 1, (byte) 2);
            map.setValue(0, 1, 1, (byte) 0);
            map.setValue(1, 0, 0, (byte) 0);
            map.setValue(1, 1, 0, (byte) 1);
            map.setValue(1, 0, 1, (byte) 2);
            map.setValue(1, 1, 1, (byte) 0);
            palettes = new com.openggf.level.Palette[] {
                    new com.openggf.level.Palette(),
                    new com.openggf.level.Palette(),
                    new com.openggf.level.Palette(),
                    new com.openggf.level.Palette()
            };
            objects = List.of();
            rings = List.of();
            minX = 0;
            maxX = 255;
            minY = 0;
            maxY = 191;
        }

        void setSpawns(List<ObjectSpawn> objects, List<RingSpawn> rings) {
            this.objects = List.copyOf(objects);
            this.rings = List.copyOf(rings);
        }

        @Override
        public int getChunksPerBlockSide() {
            return 2;
        }

        @Override
        public int getBlockPixelSize() {
            return 128;
        }

        @Override
        public int getPaletteCount() {
            return palettes.length;
        }

        @Override
        public com.openggf.level.Palette getPalette(int index) {
            return palettes[index];
        }

        @Override
        public int getPatternCount() {
            return patternCount;
        }

        @Override
        public Pattern getPattern(int index) {
            return patterns[index];
        }

        @Override
        public int getChunkCount() {
            return chunkCount;
        }

        @Override
        public Chunk getChunk(int index) {
            return chunks[index];
        }

        @Override
        public int getBlockCount() {
            return blockCount;
        }

        @Override
        public Block getBlock(int index) {
            return blocks[index];
        }

        @Override
        public com.openggf.level.SolidTile getSolidTile(int index) {
            return solidTiles[index];
        }

        @Override
        public com.openggf.level.Map getMap() {
            return map;
        }

        @Override
        public List<ObjectSpawn> getObjects() {
            return objects;
        }

        @Override
        public List<RingSpawn> getRings() {
            return rings;
        }

        @Override
        public RingSpriteSheet getRingSpriteSheet() {
            return null;
        }

        @Override
        public int getMinX() {
            return minX;
        }

        @Override
        public int getMaxX() {
            return maxX;
        }

        @Override
        public int getMinY() {
            return minY;
        }

        @Override
        public int getMaxY() {
            return maxY;
        }

        @Override
        public int getZoneIndex() {
            return 0;
        }
    }
}

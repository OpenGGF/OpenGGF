package com.openggf.tests.graphics;

import com.openggf.graphics.pipeline.RenderCommand;
import com.openggf.graphics.pipeline.RenderOrderRecorder;
import com.openggf.graphics.pipeline.RenderPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for render order compliance.
 * These tests ensure the rendering pipeline maintains correct ordering:
 * SCENE â†’ OVERLAY â†’ FADE_PASS
 */
public class RenderOrderTest {
    
    private RenderOrderRecorder recorder;
    
    @BeforeEach
    public void setUp() {
        recorder = RenderOrderRecorder.getInstance();
        recorder.clear();
        recorder.setEnabled(true);
    }
    
    @Test
    public void testRecorderDisabledByDefault() {
        RenderOrderRecorder fresh = new RenderOrderRecorder() {
            // Create new instance to test default state
        };
        assertFalse(fresh.isEnabled(), "New RenderOrderRecorder should be disabled by default");
        assertNotNull(RenderOrderRecorder.getInstance());
    }
    
    @Test
    public void testRecordingWhenEnabled() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        List<RenderCommand> commands = recorder.getCommands();
        assertEquals(3, commands.size());
        assertEquals(RenderPhase.SCENE, commands.get(0).phase());
        assertEquals(RenderPhase.OVERLAY, commands.get(1).phase());
        assertEquals(RenderPhase.FADE_PASS, commands.get(2).phase());
    }
    
    @Test
    public void testNoRecordingWhenDisabled() {
        recorder.setEnabled(false);
        recorder.record(RenderPhase.SCENE, "Level");
        
        assertTrue(recorder.getCommands().isEmpty());
    }
    
    @Test
    public void testClearRemovesAllCommands() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        assertFalse(recorder.getCommands().isEmpty());
        
        recorder.clear();
        assertTrue(recorder.getCommands().isEmpty());
    }
    
    @Test
    public void testOrderIndexIncrementsCorrectly() {
        recorder.clear();
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        List<RenderCommand> commands = recorder.getCommands();
        assertEquals(0, commands.get(0).orderIndex());
        assertEquals(1, commands.get(1).orderIndex());
        assertEquals(2, commands.get(2).orderIndex());
    }
    
    @Test
    public void testVerifyOrder_correctOrder() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        List<String> violations = recorder.verifyOrder();
        assertTrue(violations.isEmpty(), "Correct order should have no violations");
    }
    
    @Test
    public void testVerifyOrder_fadeBeforeOverlay() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.FADE_PASS, "Fade"); // Wrong order!
        recorder.record(RenderPhase.OVERLAY, "HUD");
        
        List<String> violations = recorder.verifyOrder();
        assertFalse(violations.isEmpty(), "Should detect order violation");
        assertTrue(violations.get(0).contains("HUD"), "Should mention HUD");
    }
    
    @Test
    public void testVerifyOrder_overlayBeforeScene() {
        recorder.record(RenderPhase.OVERLAY, "HUD"); // Wrong order!
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        List<String> violations = recorder.verifyOrder();
        assertFalse(violations.isEmpty(), "Should detect order violation");
    }
    
    @Test
    public void testFadeRenderedLast_correct() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        assertTrue(recorder.fadeRenderedLast());
    }
    
    @Test
    public void testFadeRenderedLast_incorrect() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        recorder.record(RenderPhase.OVERLAY, "HUD"); // HUD after fade is wrong
        
        assertFalse(recorder.fadeRenderedLast());
    }
    
    @Test
    public void testFadeRenderedLast_emptyIsTrue() {
        assertTrue(recorder.fadeRenderedLast(), "Empty recorder should return true");
    }
    
    @Test
    public void testFadeRenderedLast_noFade() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        
        // No fade recorded - last is OVERLAY
        assertFalse(recorder.fadeRenderedLast());
    }
    
    @Test
    public void testRenderCommandRecord() {
        RenderCommand cmd = RenderCommand.of(RenderPhase.SCENE, "Level", 0);
        
        assertEquals(RenderPhase.SCENE, cmd.phase());
        assertEquals("Level", cmd.component());
        assertEquals(0, cmd.orderIndex());
    }
    
    @Test
    public void testRenderPhaseOrdering() {
        // Verify enum ordinals are correct for comparison
        assertTrue(RenderPhase.SCENE.ordinal() < RenderPhase.OVERLAY.ordinal());
        assertTrue(RenderPhase.OVERLAY.ordinal() < RenderPhase.FADE_PASS.ordinal());
        assertTrue(RenderPhase.FADE_PASS.ordinal() < RenderPhase.POST_FADE_DIAGNOSTIC.ordinal());
    }
    
    @Test
    public void testMultipleSceneComponentsAllowed() {
        recorder.record(RenderPhase.SCENE, "Background");
        recorder.record(RenderPhase.SCENE, "Sprites");
        recorder.record(RenderPhase.SCENE, "Foreground");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        List<String> violations = recorder.verifyOrder();
        assertTrue(violations.isEmpty(), "Multiple SCENE components should be allowed");
    }
    
    @Test
    public void testMultipleOverlayComponentsAllowed() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.OVERLAY, "Debug");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        
        List<String> violations = recorder.verifyOrder();
        assertTrue(violations.isEmpty(), "Multiple OVERLAY components should be allowed");
    }

    @Test
    public void testExplicitPostFadeDiagnosticsAreAllowedAfterFade() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.OVERLAY, "HUD");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        recorder.recordPostFadeDiagnostic("TraceHud");

        assertTrue(recorder.verifyOrder().isEmpty(), "Post-fade diagnostic phase follows fade");
        assertTrue(recorder.verifyPostFadeDiagnosticsAllowed(List.of("TraceHud")).isEmpty(),
                "Registered post-fade diagnostic should be allowed");
    }

    @Test
    public void testUnregisteredPostFadeDiagnosticsAreReported() {
        recorder.record(RenderPhase.SCENE, "Level");
        recorder.record(RenderPhase.FADE_PASS, "Fade");
        recorder.recordPostFadeDiagnostic("NewOverlay");

        List<String> violations = recorder.verifyPostFadeDiagnosticsAllowed(List.of("TraceHud"));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("NewOverlay"), "Violation should name the unregistered overlay");
    }

    @Test
    public void testEnginePostFadeOverlaysAreRecordedAsExplicitExceptions() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));

        assertPostFadeExceptionRecordedBefore(source,
                "recordPostFadeDiagnostic(\"DisplayColorProfileNotification\")",
                "renderDisplayColorProfileNotification();");
        assertPostFadeExceptionRecordedBefore(source,
                "recordPostFadeDiagnostic(\"TraceHud\")",
                "traceSession.render(traceHudTextRenderer);");
        assertPostFadeExceptionRecordedBefore(source,
                "recordPostFadeDiagnostic(\"LiveRewindHud\")",
                "gameLoop.renderLiveRewindHud(traceHudTextRenderer, (int) projectionWidth);");
        assertPostFadeExceptionRecordedBefore(source,
                "recordPostFadeDiagnostic(\"CreditsDemoSprites\")",
                "levelManager.renderSpriteObjectPass(spriteManager, true);");
        assertPostFadeExceptionRecordedBefore(source,
                "recordPostFadeDiagnostic(\"SpecialStageDiagnosticOverlay\")",
                "ssProvider.renderAlignmentOverlay(\n"
                        + "\t\t\t\t\t\tspecialStageViewport.logicalWidth(), "
                        + "specialStageViewport.logicalHeight());");
        assertPostFadeExceptionRecordedBefore(source,
                "recordPostFadeDiagnostic(\"SpecialStageDiagnosticOverlay\")",
                "ssProvider.renderLagCompensationOverlay(\n"
                        + "\t\t\t\t\t\tspecialStageViewport.logicalWidth(), "
                        + "specialStageViewport.logicalHeight());");
        assertPostFadeExceptionRecordedBefore(source,
                "recordPostFadeDiagnostic(\"DebugOverlay\")",
                "getDebugRenderer().renderDebugInfo();");
    }

    @Test
    public void testStageRingsRenderAtRomPriorityBucketInsideSpriteObjectPasses() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/openggf/level/LevelRenderer.java"));

        String preparation = sourceBetween(source, "private void prepareOrDrawLiveSprites(",
                "private void renderGhostsForLayer(");
        String regular = sourceBetween(source, "public void renderSpriteObjectPass(",
                "private void prepareOrDrawLiveSprites(");
        String filtered = sourceBetween(source, "private void renderSpriteObjectPassFiltered(",
                "private void drawStageRingsForBucket(");
        String capture = sourceBetween(source, "SpritePresentation.Frame captureSceneSpriteTable(",
                "private boolean cinematicDraw;");
        String publication = sourceBetween(source, "void prepareSpritePresentation(",
                "void publishHudCounters(");
        String rings = sourceBetween(source, "private void drawStageRingsForBucket(",
                "public void renderEndingBackground(");

        assertTrue(regular.contains("drawPublishedSprites("));
        assertTrue(regular.contains("prepareOrDrawLiveSprites(spriteManager);"));
        assertTrue(regular.contains("captureSceneSpriteTable(spriteManager)"));
        assertTrue(filtered.contains("drawPublishedSprites("));
        assertTrue(filtered.contains("captureSceneSpriteTable(spriteManager)"));
        assertTrue(capture.contains("prepareOrDrawLiveSprites(sprites)"));
        assertTrue(publication.contains("renderSpriteObjectPass(sprites, false);"));
        assertEquals(0, countOccurrences(regular + filtered, "drawStageRingsForBucket(ringManager,"),
                "draw consumers replay the shared table instead of submitting duplicate ring entries");

        int buckets = preparation.indexOf(
                "for (int bucket = RenderPriority.MIN; bucket <= RenderPriority.MAX; bucket++)");
        int ringCall = preparation.indexOf("drawStageRingsForBucket(ringManager,");
        int zoneTail = preparation.indexOf("bucketSpriteSource.appendSpritesAfterPriorityBucket(bucket);");
        int replay = preparation.indexOf("graphicsManager.endSpriteSatCollectionAndReplay();");
        assertTrue(buckets >= 0 && buckets < ringCall && ringCall < zoneTail && zoneTail < replay,
                "legacy stage rings join the ascending native table bucket before its zone tail and replay");
        assertEquals(1, countOccurrences(preparation, "drawStageRingsForBucket(ringManager,"),
                "every sprite/object consumer shares one native table producer");
        assertTrue(rings.contains("bucket != RenderPriority.PLAYER_DEFAULT"),
                "Obj25 rings use ROM priority bucket 2, preserving the native overlap order");
        assertTrue(rings.contains("ringManager.draw(lm.frameCounter);"),
                "stage-ring drawing still goes through RingManager");
        assertTrue(rings.indexOf("ringManager.draw(lm.frameCounter);")
                < rings.indexOf("ringManager.drawLostRings(lm.frameCounter);"),
                "placed and lost-ring producers retain their order");
    }

    @Test
    public void nativeRingEmissionProfilesUseTheirOwnProducerWithoutLegacyDuplication() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/openggf/level/LevelRenderer.java"));
        String preparation = sourceBetween(source, "private void prepareOrDrawLiveSprites(",
                "private void renderGhostsForLayer(");
        int placed = preparation.indexOf("RingDrawInternalAccess.drawPlaced(ringManager,");
        int buckets = preparation.indexOf(
                "for (int bucket = RenderPriority.MIN; bucket <= RenderPriority.MAX; bucket++)");
        assertTrue(preparation.contains("profile.placedRingEmission() == "
                + "com.openggf.game.internal.SpriteTableBuildProfile.PlacedRings.BEFORE_OBJECT_BUCKETS"));
        assertTrue(placed >= 0 && placed < buckets,
                "the native ring-list profile emits its rows before object buckets");
        assertTrue(preparation.contains("RingDrawInternalAccess.slottedAttracted(ringManager, bucket)"),
                "physical ring slots join the matching object bucket instead of a late global draw");
        int legacyGuard = preparation.indexOf(
                "if (!(lm.gameModule instanceof com.openggf.game.internal.SpriteTableBuildProfile))");
        int legacyDraw = preparation.indexOf("drawStageRingsForBucket(ringManager,");
        assertTrue(buckets < legacyGuard && legacyGuard < legacyDraw,
                "native emission profiles never receive an additional legacy ring pass");
    }

    private static String sourceBetween(String source, String start, String end) {
        int first = source.indexOf(start);
        int last = source.indexOf(end, Math.max(0, first));
        assertTrue(first >= 0 && last > first, "Expected source owner between " + start + " and " + end);
        return source.substring(first, last);
    }

    private static void assertPostFadeExceptionRecordedBefore(String source,
                                                              String recorderCall,
                                                              String renderCall) {
        int renderIndex = source.indexOf(renderCall);
        assertTrue(renderIndex >= 0, "Expected Engine display render call: " + renderCall);

        int recorderIndex = source.lastIndexOf(recorderCall, renderIndex);
        assertTrue(recorderIndex >= 0,
                "Expected " + recorderCall + " before " + renderCall);
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}

package com.openggf.mods.code;

import com.openggf.game.GameServiceBundle;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.mode.GameplayFrameDisposition;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.objects.authored.AuthoredStageplayService;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestAuthoredStageplayServices {
    @TempDir Path directory;

    @Test void multipleScenesShareOneControllerAndBusyGateWithIndependentRewindState() throws Exception {
        scenes();
        var prepared = AuthoredStageplayServices.prepareForEditor(directory, "s2", List.of("one.stageplay", "two.stageplay"));
        try {
            assertEquals(2, prepared.adapters().size());
            assertEquals(2, prepared.adapters().stream().map(RewindSnapshottable::key).distinct().count());
            assertTrue(prepared.controller().requiresExclusiveControl());
            var idle = capture(prepared);
            assertTrue(prepared.service().play("one.stageplay", true));
            var firstRequested = capture(prepared);
            assertFalse(prepared.service().play("one.stageplay", true));
            assertFalse(prepared.service().play("two.stageplay", true), "A different scene cannot replace the world's active request");
            assertFalse(prepared.controller().drawScene(), "An unprepared requested scene must not draw");
            assertThrows(IllegalArgumentException.class, () -> prepared.service().play("two.stageplay", false));
            assertThrows(IllegalArgumentException.class, () -> prepared.service().play("missing.stageplay", true));
            restore(prepared, idle);
            assertTrue(prepared.service().play("two.stageplay", true));
            restore(prepared, firstRequested);
            assertFalse(prepared.service().play("two.stageplay", true), "Restoring player states restores routing ownership");
            prepared.adapters().forEach(RewindSnapshottable::resetForMissingSnapshot);
            assertTrue(prepared.service().play("two.stageplay", true));
        } finally { prepared.controller().close(); }
    }

    @Test void closedCachedEditorGraphRebuildsFreshPlayersAndKeepsStableRewindDelegates() throws Exception {
        scenes();
        var prepared = AuthoredStageplayServices.prepareForEditor(directory, "s2", List.of("one.stageplay", "two.stageplay"));
        var stableAdapters = prepared.adapters();
        var initialIdle = capture(prepared);
        assertTrue(prepared.service().play("one.stageplay", true));
        var firstRequested = capture(prepared);
        prepared.controller().close();
        prepared.controller().close();
        assertFalse(prepared.controller().drawScene());

        try {
            assertTrue(prepared.service().play("two.stageplay", true), "A retired session must not retain its old request");
            assertSame(stableAdapters, prepared.adapters(), "Published rewind adapters remain stable across sessions");
            var secondRequested = capture(prepared);
            assertNotEquals(firstRequested, secondRequested, "Stable adapters must capture the new players");
            restore(prepared, initialIdle);
            assertTrue(prepared.service().play("one.stageplay", true));
            restore(prepared, secondRequested);
            assertFalse(prepared.service().play("one.stageplay", true), "Restore must target the current player graph");
            assertEquals(secondRequested, capture(prepared));
            prepared.adapters().forEach(RewindSnapshottable::resetForMissingSnapshot);
            assertEquals(initialIdle, capture(prepared));

            prepared.controller().close();
            var course = mock(CourseControl.class, RETURNS_DEEP_STUBS);
            assertEquals(GameplayFrameDisposition.ADVANCE_NATIVE_INPUT,
                    prepared.controller().beforeFrame(course, null), "The next session can start with an ordinary native row");
            prepared.controller().afterTick(course, true);
            assertTrue(prepared.service().play("two.stageplay", true));
            assertEquals(GameplayFrameDisposition.ADVANCE_NATIVE_INPUT,
                    prepared.controller().beforeFrame(course, null), "An unready presentation must not prepare a rendering host");
            prepared.controller().afterTick(course, true);

            prepared.controller().close();
            assertEquals(initialIdle, capture(prepared), "Rewind capture can initialize a fresh idle generation");
            prepared.controller().close();
            restore(prepared, firstRequested);
            assertFalse(prepared.service().play("two.stageplay", true), "Rewind restore can initialize the current generation");
        } finally { prepared.controller().close(); }
    }

    @Test void publicRegistrationLeavesExactlyOneBundleAndCreatesFreshGraphsPerApplication() throws Exception {
        scenes();
        try (var assets = ModAssetRoot.directory(directory.getParent(), directory,
                ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
            var context = new ModContext("authored-scenes-test", "s2", assets);
            AuthoredStageplayServices.register(context, List.of("one.stageplay", "two.stageplay", "one.stageplay"));
            var plan = context.freeze();
            assertEquals(1, plan.serviceBundles().size(), "Temporary public player bundles must not publish competing controllers");
            var factory = plan.serviceBundles().values().iterator().next();
            GameServiceBundle first = factory.get(), second = factory.get();
            var a = (AuthoredStageplayService) first.services().get(AuthoredStageplayService.class);
            var b = (AuthoredStageplayService) second.services().get(AuthoredStageplayService.class);
            var firstController = (GameplayFrameController) first.services().get(GameplayFrameController.class);
            var secondController = (GameplayFrameController) second.services().get(GameplayFrameController.class);
            try {
                assertNotSame(firstController, secondController);
                assertEquals(2, first.rewindAdapters().size());
                assertTrue(a.play("one.stageplay", true));
                assertFalse(a.play("two.stageplay", true));
                assertTrue(b.play("two.stageplay", true));
            } finally { firstController.close(); secondController.close(); }
        }
    }

    @Test void assetValidationRejectsMissingMalformedScenesAndMissingTypedDependenciesWithoutApplyingGraphs() throws Exception {
        scenes();
        Files.writeString(directory.resolve("malformed.stageplay"), "{not-json");
        var document = StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224);
        StageplayStore.write(directory.resolve("voice.stageplay"), document.withAudio(List.of(
                new StageplayDocument.AudioCue("voice", 0, StageplayDocument.AudioKind.VOICE,
                        "s2", -1, "missing.wav", 1))));
        StageplayStore.write(directory.resolve("set.stageplay"), document.withSet(
                new StageplayDocument.SetSource("s2", 0, 0, "", "missing.ogfproject")));
        try (var assets = ModAssetRoot.directory(directory.getParent(), directory,
                ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
            assertDoesNotThrow(() -> AuthoredStageplayServices.validateAssets(assets, "s2",
                    List.of("one.stageplay", "two.stageplay")));
            for (String resource : List.of("missing.stageplay", "malformed.stageplay", "voice.stageplay", "set.stageplay"))
                assertThrows(IOException.class, () -> AuthoredStageplayServices.validateAssets(assets, "s2", List.of(resource)), resource);
        }
    }

    private void scenes() throws Exception {
        var document = StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224);
        StageplayStore.write(directory.resolve("one.stageplay"), document.withName("One"));
        StageplayStore.write(directory.resolve("two.stageplay"), document.withName("Two"));
    }

    private static Map<String, Object> capture(AuthoredStageplayServices.Prepared prepared) {
        var states = new LinkedHashMap<String, Object>();
        prepared.adapters().forEach(adapter -> states.put(adapter.key(), adapter.capture()));
        return states;
    }

    private static void restore(AuthoredStageplayServices.Prepared prepared, Map<String, Object> states) {
        prepared.adapters().forEach(adapter -> restoreUnchecked(adapter, states.get(adapter.key())));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreUnchecked(RewindSnapshottable adapter, Object state) { adapter.restore(state); }
}

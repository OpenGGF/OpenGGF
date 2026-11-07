package com.openggf.game.sonic2.competition.view;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.control.*;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.competition.*;
import com.openggf.game.sonic2.titlecard.TitleCardManager;
import com.openggf.graphics.*;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionViewsRestore {
    @Test
    void destructiveWorldRestoreAndThirtyFrameForwardReplayPreservePixels() throws Exception {
        GameServices.audio().setBackend(new com.openggf.audio.NullAudioBackend());
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var services = EngineServices.current();
        var root = SessionManager.getCurrentWorldSession().rootGameModule();
        try (var gl = PresentationOffscreenTestContext.openConfigured(
                     320, 224, PresentationOffscreenTestContext.LegacyProfile.CORE_41);
                AutoCloseable cleanup = () -> {
                    try {
                        SessionManager.closeGameplaySession();
                    } finally {
                        services.graphics().cleanup();
                    }
                };
                var views = new Sonic2CompetitionViews(services.graphics())) {
            var graphics = services.graphics();
            graphics.setHeadlessMode(false);
            graphics.init(com.openggf.Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            graphics.setViewport(0, 0, 320, 224);
            glViewport(0, 0, 320, 224);
            var session = new Sonic2CompetitionSession();
            var host = new Sonic2NativeCompetitionHost(root, services,
                    ignored -> {},
                    views,
                    new Sonic2NativeCompetitionHost.NativeState() {
                        public boolean swapActorWorldState(
                                GameplayModeContext context, Sonic2CompetitionSession state) {
                            return false;
                        }
                        public void freezeObjects(GameplayModeContext context, int ticks) {}
                    });
            var input = new InputHandler();
            input.setLogicalOverride(LogicalInputSnapshot.neutral());
            host.bindNativeWaitInput(input::logical, () -> false);
            session.updateMenu(LogicalInputSnapshot.ofPlayers(
                                       PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()),
                    true);
            host.loadAct(session, 0, 0);
            graphics.bindRuntimeManagedReferences(host.context().getCamera(), host.context().getFadeManager());
            var title = (TitleCardManager) root.getTitleCardProvider();
            int count = 0;
            while (!title.isComplete() && count++ < 150) step(host, input);
            assertTrue(title.isComplete());
            input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                    PlayerInputState.of(8, 8, 8, 8, false, false), PlayerInputState.of(8, 8, 8, 8, false, false)));
            for (int i = 0; i < 35; i++) step(host, input);
            var context = host.context();
            var video = context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionVideoFrame.class);
            var registry = context.getRewindRegistry();
            var saved = registry.capture();
            var original = draw(views, context, session);
            int frame = context.getLevelManager().getFrameCounter(), vint = context.getObjectManager().getVblaCounter();
            var state = video.registers().capture();
            var art = video.art().capture();
            var scroll = video.planeScroll().capture();
            var planes = video.planes().capture();
            assertArrayEquals(original, draw(views, context, session), "drawing twice is stable");
            assertEquals(frame, context.getLevelManager().getFrameCounter());
            assertEquals(vint, context.getObjectManager().getVblaCounter());
            assertEquals(state, video.registers().capture());
            assertEquals(art, video.art().capture());
            assertEquals(scroll, video.planeScroll().capture());
            for (int plane = 0; plane < 3; plane++)
                assertArrayEquals(planes.cells()[plane], video.planes().capture().cells()[plane]);
            assertArrayEquals(planes.revisions(), video.planes().capture().revisions());
            for (int i = 0; i < 30; i++) step(host, input);
            var forward = draw(views, context, session);
            assertFalse(java.util.Arrays.equals(original, forward), "moving-camera forward run must change pixels");
            for (var plane : Sonic2CompetitionPlanes.PlaneId.values())
                for (int y = 0; y < 32; y++)
                    for (int x = 0; x < 64; x++) video.planes().write(plane, x, y, 0xFFFF);
            video.art().resetForMissingSnapshot();
            video.planeScroll().menuClear();
            video.spritePages().restore(new Sonic2CompetitionSpritePages.Pair(
                    Sonic2CompetitionSatBuilder.Page.empty(0), Sonic2CompetitionSatBuilder.Page.empty(1)));
            registry.restore(saved);
            assertArrayEquals(original, draw(views, context, session), "draw immediately after destructive restore");
            for (int i = 0; i < 30; i++) step(host, input);
            assertArrayEquals(forward, draw(views, context, session), "thirty-frame replay");
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            TestEnvironment.resetAll();
        }
    }
    private static int[] draw(
            Sonic2CompetitionViews views, GameplayModeContext context, Sonic2CompetitionSession session) {
        PaletteUploadPresentation.withoutDisplayedFade(GameServices.graphics(), () -> views.draw(context, session));
        return ScreenshotCapture.captureFramebuffer(320, 224).pixels();
    }
    private static void step(Sonic2NativeCompetitionHost host, InputHandler input) {
        var context = host.context();
        LevelFrameStep.updateTimers(LevelFrameContext.from(context));
        context.plcFrameLifecycle().runLogicalIteration(context::updateFade, frame -> {
            var result = new LevelFrameResult[1];
            NativeCompetitionIterationAccess.runAdmitted(frame, () -> result[0] = host.step(input, frame));
            return result[0];
        });
    }
}

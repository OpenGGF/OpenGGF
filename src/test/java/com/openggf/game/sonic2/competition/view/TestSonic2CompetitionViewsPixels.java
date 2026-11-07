package com.openggf.game.sonic2.competition.view;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.audio.NullAudioBackend;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.competition.*;
import com.openggf.game.sonic2.titlecard.TitleCardManager;
import com.openggf.graphics.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** C1 artifacts from the real native host. References are comparison-only, never runtime inputs. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionViewsPixels {
    private static final Path OUTPUT = Path.of("target/s2comp-c1");
    @ParameterizedTest @ValueSource(ints={0,11,12})
    void captureNativeSplitWorldWithoutAdvancingItWhileDrawing(int zone) throws Exception {
        GameServices.audio().setBackend(new NullAudioBackend());
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var services = EngineServices.current();
        var root = SessionManager.getCurrentWorldSession().rootGameModule();
        try (var gl = PresentationOffscreenTestContext.openConfigured(320, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
             AutoCloseable cleanup = () -> {
                 try { SessionManager.closeGameplaySession(); }
                 finally { services.graphics().cleanup(); }
             };
             var views = new Sonic2CompetitionViews(services.graphics())) {
            var graphics = services.graphics();
            graphics.setHeadlessMode(false);
            graphics.init(com.openggf.Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            graphics.setViewport(0, 0, 320, 224); glViewport(0, 0, 320, 224);
            Files.createDirectories(OUTPUT);
            var session = new Sonic2CompetitionSession();
            var titleCaptured = new boolean[1];
            var probe = new Sonic2NativeCompetitionHost.Views() {
                public void prepare(GameplayModeContext context, Sonic2CompetitionSession state,
                                    Sonic2CompetitionLevelProfile profile, Sonic2CompetitionNativeArt art) {
                    views.prepare(context, state, profile, art);
                    var video = context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionVideoFrame.class);
                    if (zone == 0 && !titleCaptured[0] && !context.getCamera().isLevelStarted()
                            && video.registers().capture().published()
                            && video.registers().capture().upperPlane() == Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P2) {
                        titleCaptured[0] = true;
                        try { capture(views, context, state, "ehz-title-planes", "locked native title leave; planes only at C1"); }
                        catch (Exception failure) { throw new RuntimeException(failure); }
                    }
                }
                public void draw(GameplayModeContext context, Sonic2CompetitionSession state) { views.draw(context, state); }
            };
            var host = new Sonic2NativeCompetitionHost(root, services, ignored -> { }, probe,
                    new Sonic2NativeCompetitionHost.NativeState() {
                        public boolean swapActorWorldState(GameplayModeContext context, Sonic2CompetitionSession state) { return false; }
                        public void freezeObjects(GameplayModeContext context, int ticks) { }
                    });
            var input = new InputHandler(); input.setLogicalOverride(LogicalInputSnapshot.neutral());
            host.bindNativeWaitInput(input::logical, () -> false);
            int menuMask = zone == 11 ? 8 : zone == 12 ? 2 : 0;
            session.updateMenu(LogicalInputSnapshot.ofPlayers(
                    PlayerInputState.of(menuMask, menuMask, menuMask, menuMask, true, true), PlayerInputState.neutral()), true);
            host.loadAct(session, zone, 0);
            graphics.bindRuntimeManagedReferences(host.context().getCamera(), host.context().getFadeManager());
            var title = (TitleCardManager) root.getTitleCardProvider();
            int ticks = 0;
            while (!title.isComplete() && ticks++ < 150) step(host, input);
            assertTrue(title.isComplete(), "capture follows actual title owner completion");
            step(host, input); // publish the first completed post-card sprite page
            String name = zone == 0 ? "ehz-first" : zone == 11 ? "mcz-start" : "cnz-start";
            capture(views, host.context(), session, name, "first native display after title owner completion");
            if (zone == 0) {
                assertTrue(titleCaptured[0]);
                // Independent seeded split fixture, no reference CSV/state hydration. The
                // two humans/cameras still belong to the one production native world.
                var second = session.player(1);
                NativePositionOps.writeXPosResetSubpixel(second, 2167);
                NativePositionOps.writeYPosResetSubpixel(second, 784);
                second.setXSpeed((short)0); second.setYSpeed((short)0); second.setGSpeed((short)0);
                session.camera(1).updatePosition(true); session.camera(1).captureRenderCopy();
                var video = host.context().getWorldSession().getGameModule().getGameService(Sonic2CompetitionVideoFrame.class);
                var draw = new Sonic2CompetitionPlaneDraw(host.nativeArt().workRamBytes());
                video.planes().apply(draw.initialForeground(camera(session,0), camera(session,1)));
                video.planeScroll().initialize(session.camera(0).getX(), session.camera(0).getY(),
                        session.camera(1).getX(), session.camera(1).getY());
                step(host, input); step(host, input);
                assertTrue(second.getCentreX() - session.player(0).getCentreX() >= 128);
                assertTrue(second.getCentreY() - session.player(0).getCentreY() >= 64);
                assertTrue(second.isRenderFlagOnScreen(), "P2 own-view visibility must reach the actual human");
                capture(views, host.context(), session, "ehz-separated", "seeded P2 position; explicit initial FG load, then two native waits");
            }
            assertEquals(GL_NO_ERROR, glGetError());
            assertInstanceOf(NullAudioBackend.class, services.audio().getBackend());
        } finally { TestEnvironment.resetAll(); }
    }
    /**
     * Obj01_Hurt_Normal (s2.asm:38174-38193) returns from Sonic_HurtStop then
     * directly jumps to DisplaySprite. Sonic_HurtStop (s2.asm:38217-38227)
     * resets invulnerability to $78, whose control flash would skip this frame
     * in Sonic_Display (s2.asm:36280-36288). Obj02_Hurt has the same direct path
     * (s2.asm:41063-41079), Tails_HurtStop (s2.asm:41105-41116).
     */
    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.Disabled("Lead C1 deferral: SpriteManager must cache admission before clearing HurtStop latch; expose via SpriteManagerInternalAccess")
    void hurtLandingInCompetitionKeepsItsDirectLandingFrameAdmission() throws Exception {
        GameServices.audio().setBackend(new NullAudioBackend());
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var services = EngineServices.current();
        var root = SessionManager.getCurrentWorldSession().rootGameModule();
        try (var views = new Sonic2CompetitionViews(services.graphics())) {
            var session = new Sonic2CompetitionSession();
            var host = new Sonic2NativeCompetitionHost(root, services, ignored -> { }, views,
                    new Sonic2NativeCompetitionHost.NativeState() {
                        public boolean swapActorWorldState(GameplayModeContext context, Sonic2CompetitionSession state) { return false; }
                        public void freezeObjects(GameplayModeContext context, int ticks) { }
                    });
            var input = new InputHandler(); input.setLogicalOverride(LogicalInputSnapshot.neutral());
            host.bindNativeWaitInput(input::logical, () -> false);
            session.updateMenu(LogicalInputSnapshot.ofPlayers(
                    PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()), true);
            host.loadAct(session, 0, 0);
            var human = session.player(0);
            human.setAir(true); human.setHurt(true); human.setAir(false);
            assertTrue(human.shouldRefreshRenderFlagThisFrame(), "HurtStop owns direct display on landing");
            // Current generic refresh reads the true decision, then clears the
            // latch before NativeHost invokes Views.prepare. The bridge must
            // preserve that decision instead of re-reading the cleared latch.
            host.context().getSpriteManager().refreshPlayableRenderFlags(session.camera(0));
            views.prepare(host.context(), session, host.profile(), host.nativeArt());
            var video = host.context().getWorldSession().getGameModule().getGameService(Sonic2CompetitionVideoFrame.class);
            assertTrue(video.spritePages().visibility().stream().anyMatch(v -> v.slot() == 0),
                    "landing-frame body must be admitted by native BuildSprites");
        } finally {
            SessionManager.closeGameplaySession();
            TestEnvironment.resetAll();
        }
    }
    private static Sonic2CompetitionPlaneDraw.CameraCopy camera(Sonic2CompetitionSession session, int slot) {
        return new Sonic2CompetitionPlaneDraw.CameraCopy(session.camera(slot).getX(), session.camera(slot).getY());
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
    private static void capture(Sonic2CompetitionViews views, GameplayModeContext context,
            Sonic2CompetitionSession session, String name, String description) throws Exception {
        int levelFrame = context.getLevelManager().getFrameCounter();
        int objectFrame = context.getObjectManager().getFrameCounter();
        int vint = context.getObjectManager().getVblaCounter();
        var first = session.camera(0).capture(); var second = session.camera(1).capture();
        var video = context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionVideoFrame.class);
        var registers = video.registers().capture();
        PaletteUploadPresentation.withoutDisplayedFade(GameServices.graphics(), () -> views.draw(context, session));
        var image = ScreenshotCapture.captureFramebuffer(320, 224);
        assertTrue(java.util.Arrays.stream(image.pixels()).distinct().count() > 2, "capture must contain rendered content");
        ScreenshotCapture.savePNG(image, OUTPUT.resolve(name + ".png"));
        Files.writeString(OUTPUT.resolve(name + ".txt"), description + "\nscroll=A1 Sonic2CompetitionScroll\n"
                + "initial-deform seed pending=" + name.startsWith("ehz-") + "\n"
                + "postLoadScrollClear=" + video.planeScroll().postLoadScrollClear() + "\n"
                + "levelFrame=" + levelFrame + " vint=" + vint + " upperPlane=" + registers.upperPlane() + "\n"
                + "P1=" + session.player(0).getCentreX() + "," + session.player(0).getCentreY()
                + " P2=" + session.player(1).getCentreX() + "," + session.player(1).getCentreY() + "\n"
                + "camera1=" + first.x() + "," + first.y() + " camera2=" + second.x() + "," + second.y() + "\n");
        assertEquals(first, session.camera(0).capture()); assertEquals(second, session.camera(1).capture());
        assertEquals(levelFrame, context.getLevelManager().getFrameCounter());
        assertEquals(objectFrame, context.getObjectManager().getFrameCounter());
        assertEquals(vint, context.getObjectManager().getVblaCounter());
        assertEquals(registers, video.registers().capture());
    }
}

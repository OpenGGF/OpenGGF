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
        try (var gl = PresentationOffscreenTestContext.openConfigured(320, 448,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
             AutoCloseable cleanup = () -> {
                 try { SessionManager.closeGameplaySession(); }
                 finally { services.graphics().cleanup(); }
             };
             var views = new Sonic2CompetitionViews(services.graphics())) {
            var graphics = services.graphics();
            graphics.setHeadlessMode(false);
            graphics.init(com.openggf.Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            graphics.setViewport(0, 0, 320, 448); glViewport(0, 0, 320, 448);
            Files.createDirectories(OUTPUT);
            var session = new Sonic2CompetitionSession();
            var titleCaptured = new boolean[2];
            var probe = new Sonic2NativeCompetitionHost.Views() {
                public void prepare(GameplayModeContext context, Sonic2CompetitionSession state,
                                    Sonic2CompetitionLevelProfile profile, Sonic2CompetitionNativeArt art) {
                    views.prepare(context, state, profile, art);
                    var video = context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionVideoFrame.class);
                    if (zone == 0 && !titleCaptured[0] && !context.getCamera().isLevelStarted()
                            && ((TitleCardManager) root.getTitleCardProvider()).capture().leavePass() == 22
                            && video.registers().capture().published()
                            && video.registers().capture().upperPlane() == Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P2) {
                        titleCaptured[0] = true;
                        try { capture(views, context, state, "ehz-title-locked-planes", "locked background departure, leave pass 22; native f1325; planes only"); }
                        catch (Exception failure) { throw new RuntimeException(failure); }
                    }
                    if (zone == 0 && !titleCaptured[1] && context.getLevelManager().getFrameCounter() == 50) {
                        titleCaptured[1] = true;
                        try { capture(views, context, state, "ehz-title-planes", "text departure, LFC50; native f1380 retained field0 from f1379; title art pending S5"); }
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
            var resident=host.context().getWorldSession().getGameModule().getGameService(Sonic2CompetitionArtResidency.class);
            for (int tile=0;tile<22;tile++) assertArrayEquals(TestEnvironment.currentRom().readBytes(0x4134C+tile*32,32),
                    resident.patternBytes(0x6E2+tile), "Hud_Base field cell source slot "+Integer.toHexString(0x6E2+tile));
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
                // Lead-authorized observation fixture: native moving camera history,
                // not gameplay hydration or a new runtime delay. Vint_Level (s2.asm:756)
                // publishes the previous completed DeformBgLayer output. BizHawk's
                // f2300 even rows retain f2299 (H=-2001); its latest f2300 H is -2004.
                var second = session.player(1);
                NativePositionOps.writeXPosResetSubpixel(second, 2161);
                NativePositionOps.writeYPosResetSubpixel(second, 784);
                second.setXSpeed((short)0); second.setYSpeed((short)0); second.setGSpeed((short)0);
                session.camera(1).updatePosition(true); session.camera(1).captureRenderCopy();
                var video = host.context().getWorldSession().getGameModule().getGameService(Sonic2CompetitionVideoFrame.class);
                var draw = new Sonic2CompetitionPlaneDraw(host.nativeArt().workRamBytes());
                video.planes().apply(draw.initialForeground(camera(session,0), camera(session,1)));
                video.planeScroll().initialize(session.camera(0).getX(), session.camera(0).getY(),
                        session.camera(1).getX(), session.camera(1).getY());
                step(host, input); // completed camera 2001 / native f2298
                NativePositionOps.writeXPosResetSubpixel(second, 2164);
                step(host, input); // native f2299: publish H=-2001, complete camera 2004
                assertEquals(2004, session.camera(1).getX());
                assertEquals(-2001, (short)(video.registers().capture().horizontal().get(180) >>> 16));
                capture(views, host.context(), session, "ehz-separated", "native camera history 2001->2004; f2300 field0 retained from f2299; published H=-2001");
                NativePositionOps.writeXPosResetSubpixel(second, 2167);
                step(host, input); // native f2300: publish H=-2004, complete camera 2007
                assertEquals(2007, session.camera(1).getX());
                assertEquals(-2004, (short)(video.registers().capture().horizontal().get(180) >>> 16));
                capture(views, host.context(), session, "ehz-separated-latest", "native f2300 latest publication H=-2004, fixed-field projection; compare with retained field0 separately");
                assertTrue(second.getCentreX() - session.player(0).getCentreX() >= 128);
                assertTrue(second.getCentreY() - session.player(0).getCentreY() >= 64);
                assertTrue(second.isRenderFlagOnScreen(), "P2 own-view visibility must reach the actual human");

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
        var image = ScreenshotCapture.captureFramebuffer(320, 448);
        TestSonic2CompetitionFieldPatternOwnership.assertAtlas(video.art(), GameServices.graphics());
        assertTrue(java.util.Arrays.stream(image.pixels()).distinct().count() > 2, "capture must contain rendered content");
        ScreenshotCapture.savePNG(image, OUTPUT.resolve(name + ".png"));
        Files.writeString(OUTPUT.resolve(name + ".txt"), description + "\nscroll=A1 Sonic2CompetitionScroll\n"
                + "initial-deform seed pending=" + name.startsWith("ehz-") + "\n"
                + "ring-phase port pending=" + (video.displayState().ringsAnimationFrame() < 0) + "\n"
                + "publishedCamera2=" + registers.secondCamera().x() + "," + registers.secondCamera().y() + "\n"
                + "postLoadScrollClear=" + video.planeScroll().postLoadScrollClear() + "\n"
                + "levelFrame=" + levelFrame + " vint=" + vint + " upperPlane=" + registers.upperPlane() + "\n"
                + "P1=" + session.player(0).getCentreX() + "," + session.player(0).getCentreY()
                + " P2=" + session.player(1).getCentreX() + "," + session.player(1).getCentreY() + "\n"
                + "camera1=" + first.x() + "," + first.y() + " camera2=" + second.x() + "," + second.y() + "\n");
        var provenance = new StringBuilder();
        for (var page : java.util.List.of(registers.pages().first(), registers.pages().second())) {
            provenance.append("page ").append(page.player()).append('\n');
            for (var entry : page.entries()) provenance.append(entry).append(" projected=")
                    .append(entry.x()).append(',').append(entry.y()).append('\n');
        }
        for (int slot=0;slot<2;slot++) {
            var ringWindow=com.openggf.level.rings.RingNativeDisplayInternalAccess.window(
                    context.getLevelManager().getRingManager(),context.getLevelManager().getCurrentLevel().getRings(),slot,levelFrame);
            provenance.append("ring window ").append(slot).append(' ').append(ringWindow).append('\n');
        }
        for (int[] point : new int[][]{{65,8},{65,118},{70,128},{76,130},{110,147},{115,148}}) {
            int y=point[1]*2,x=point[0];
            int h=(short)(registers.horizontal().get(y/2)>>>16);
            int v=(y<216?registers.foreground1():registers.foreground2());
            var plane=y<216?registers.upperPlane():Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P2;
            int word=video.planes().cell(plane,(x-h)>>3,(y+v)>>4);
            provenance.append("FG ").append(x).append(',').append(y).append(" h=").append(h).append(" v=").append(v)
                    .append(" word=").append(Integer.toHexString(word)).append('\n');
        }
        provenance.append("EHZ pulse slots39C/39D blank=").append(
                java.util.Arrays.equals(new byte[32],video.art().patternBytes(0x39C))
                && java.util.Arrays.equals(new byte[32],video.art().patternBytes(0x39D))).append('\n');
        var empty = new Sonic2CompetitionSpritePages.Pair(Sonic2CompetitionSatBuilder.Page.empty(0),
                Sonic2CompetitionSatBuilder.Page.empty(1));
        video.registers().restore(new Sonic2CompetitionViewRegisters.State(registers.published(),
                registers.foreground1(), registers.background1(), registers.foreground2(), registers.background2(),
                registers.horizontal(), registers.upperPlane(), registers.nextUpperPlane(),
                registers.firstCamera(), registers.secondCamera(), empty, registers.paletteWords()));
        try {
            PaletteUploadPresentation.withoutDisplayedFade(GameServices.graphics(), () -> views.draw(context, session));
            ScreenshotCapture.savePNG(ScreenshotCapture.captureFramebuffer(320,448), OUTPUT.resolve(name+"-planes-only.png"));
        } finally { video.registers().restore(registers); }
        Files.writeString(OUTPUT.resolve(name+"-provenance.txt"), provenance);
        assertEquals(first, session.camera(0).capture()); assertEquals(second, session.camera(1).capture());
        assertEquals(levelFrame, context.getLevelManager().getFrameCounter());
        assertEquals(objectFrame, context.getObjectManager().getFrameCounter());
        assertEquals(vint, context.getObjectManager().getVblaCounter());
        assertEquals(registers, video.registers().capture());
    }
}

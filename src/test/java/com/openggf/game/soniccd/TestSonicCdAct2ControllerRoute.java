package com.openggf.game.soniccd;

import com.openggf.audio.NullAudioBackend;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.RetainedPlaneSceneRenderer;
import com.openggf.graphics.SpritePresentation;
import org.lwjgl.BufferUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.objects.CdTitleCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Original-USA controller-only programme handoff; no actor/global/pool/camera/art seeds.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level/Main.asm:85-407;
 * Title Card/Main.asm:39-193. Call bounds select fixture inputs only.
 * Real EGL pixels, selected-primary omission and owned atlas retirement are checked.
 * This is OpenGGF output, not a native-image or regional-parity claim.
 */
@Isolated
class TestSonicCdAct2ControllerRoute {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualResultsProgrammeReturnLoadsAct2AndRunsItsNativeFirstWalkWithBothReplayScopes() throws Exception {
        byte[] imageForExport;
        try (var egl = TestSonicCdPastResultsRoute.Egl.open()) {
        assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
        var old = ready(); var h = old.harness(); var module = h.module();
        for (int call = old.returned() + 1; call <= 10419; call++) step(h, controller(call));
        assertEquals(SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED, module.pastLoadState().phase());
        assertFalse(old.frame().closed());
        var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var oldSource = old.frame().stageSource();
        int lives = module.pastLoadState().ring().lives();
        var heldRight = controller(10419);
        int firstMain = -1;
        for (int offset = 1; offset <= 256; offset++) {
            int call = 10419 + offset;
            boolean canReplay = module.act2LoadingSelected();
            var registry = h.mode().getRewindRegistry();
            var full = canReplay ? registry.capture() : null;
            var course = canReplay ? registry.captureCourse() : null;
            var before = canReplay ? captureNative(h) : null;
            step(h, heldRight);
            assertTrue(module.act2LoadingSelected(), "The actual programme return must select its next FULL load");
            assertSame(world, h.mode().getWorldSession());
            assertEquals(generation + 1, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            var frame = module.requirePlayerFrame();
            assertNotSame(old.frame(), frame); assertNotSame(oldSource, frame.stageSource());
            assertTrue(old.frame().closed()); assertFalse(frame.closed());
            assertEquals(CdStageKey.parse("R12A"), frame.stageSource().stage());
            assertEquals(1, frame.environment().movement().gates().zoneAct());
            assertEquals(1, frame.environment().movement().gates().timeZone());
            assertEquals(lives, module.act2LoadState().ring().lives());
            assertEquals("6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7",
                    module.act2LoadState().assets().identity().mmdSha256());
            if (canReplay) {
                var after = captureNative(h);
                registry.restore(full); assertNativeCapture(before, h);
                step(h, heldRight); assertNativeCapture(after, h);
                registry.restoreCourse(course); assertNativeCapture(before, h);
                step(h, heldRight); assertNativeCapture(after, h);
                assertSame(frame, module.requirePlayerFrame()); assertSame(world, h.mode().getWorldSession());
                assertEquals(generation + 1, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            }
            if (module.act2LoadState().calls().stream().anyMatch(c -> c.name().equals("ORDINARY_MAIN_RETURN"))) {
                firstMain = call;
                break;
            }
        }
        assertTrue(firstMain > 10419, "A real complete ordinary walk must return within the finite fixture bound");
        var state = module.act2LoadState();
        assertEquals("BEGIN_LEVEL", state.phase().name());
        assertEquals(1, module.requirePlayerFrame().environment().globals().levelFrames());
        assertEquals(1, module.requirePlayerFrame().state().header().slotId());
        assertEquals(CdTitleCard.OBJECT_ID, state.reservedObjects().unsignedByte(3 * 64));
        assertEquals(2, state.reservedObjects().unsignedByte(3 * 64 + 0x24));
        assertEquals(8, state.placement().pool().slots().stream().filter(s -> s.id() == CdTitleCard.OBJECT_ID).count());
        for (int whole = 38; whole <= 45; whole++) {
            var child = state.placement().pool().slots().get(whole - 32);
            assertEquals(CdTitleCard.OBJECT_ID, child.id(), "Title parent4 allocates before the six actual placed bodies");
            assertEquals(4, child.routine(), "Later physical children perform their first slide in the same walk");
        }
        var frame = module.requirePlayerFrame(); var source = frame.stageSource();
        for (int next = 1; next <= 3; next++) {
            var registry = h.mode().getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
            var before = captureNative(h); step(h, heldRight); var after = captureNative(h);
            registry.restore(full); assertNativeCapture(before, h); step(h, heldRight); assertNativeCapture(after, h);
            registry.restoreCourse(course); assertNativeCapture(before, h); step(h, heldRight); assertNativeCapture(after, h);
            assertSame(frame, module.requirePlayerFrame()); assertSame(source, frame.stageSource()); assertFalse(frame.closed());
            assertEquals(1 + next, frame.environment().globals().levelFrames());
        }
        var selected = module.act2SelectedDisplay();
        var cpu = selected.inputs().selectedCpu().orElseThrow();
        assertTrue(cpu.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 0),
                "The real following V-int selects the primary image from a completed ordinary walk");
        var scene = assertInstanceOf(SonicCdPastDisplay.Ready.class, selected.projection(),
                "Selected actual Act2 art at firstMain=" + firstMain + ": " + selected.projection()).scene();
        var unchanged = captureNative(h); var renderRegistry = h.mode().getRewindRegistry();
        var renderCapture = renderRegistry.capture(); var handler = module.getScrollHandlerProvider().getHandler(0);
        var renderScroll = handler.captureRewindState(); var callbacks = new java.util.concurrent.atomic.AtomicInteger();
        GameServices.graphics().registerCommand((x, y, width, height) -> {
            callbacks.incrementAndGet();
            assertThrows(IllegalStateException.class, renderRegistry::capture);
            assertThrows(IllegalStateException.class, () -> renderRegistry.restore(renderCapture));
            assertThrows(IllegalStateException.class, handler::captureRewindState);
            assertThrows(IllegalStateException.class, () -> handler.restoreRewindState(renderScroll));
            assertThrows(IllegalStateException.class, () -> module.serviceAct2Loading(8));
            assertThrows(IllegalStateException.class, () -> module.gameplayFrameController().drawOverlay());
        });
        var actual = draw(h); imageForExport = actual.clone();
        assertEquals(1, callbacks.get()); assertFalse(frame.closed());
        assertArrayEquals(actual, draw(h)); assertNativeCapture(unchanged, h);
        var presentation = assertInstanceOf(SonicCdPlayerPresentation.class, actualField(module, "playerPresentation"));
        var binding = actualField(presentation, "act2RenderBinding");
        var renderer = assertInstanceOf(RetainedPlaneSceneRenderer.class, actualField(binding, "renderer"));
        var camera = h.mode().getCamera(); clearPixels();
        renderer.draw(TestSonicCdPastResultsRoute.omitWhole(scene, cpu, 0), camera.getX(), camera.getY(), module::validateAct2LoadingCut);
        assertFalse(Arrays.equals(actual, pixels()), "Actual selected primary must contribute visible Act2 pixels");
        assertNativeCapture(unchanged, h); assertArrayEquals(actual, draw(h)); assertNativeCapture(unchanged, h);
        var atlas = GameServices.graphics().getPatternAtlas(); var installed = scene.nativePatterns().keySet();
        assertFalse(installed.isEmpty());
        for (int id : installed) assertNotNull(atlas.getEntry(PatternAtlasRange.OBJECTS.base() + id));
        // GPU-only other-producer contrast, never gameplay art or a substitute source.
        atlas.registerRange(PatternAtlasRange.HUD); int other = PatternAtlasRange.HUD.base() + 19;
        if (atlas.getEntry(other) == null) GameServices.graphics().cachePatternTexture(new com.openggf.level.Pattern(), other);
        var unrelated = atlas.getEntry(other); var sample = SpritePresentation.patternSample(GameServices.graphics(), other);
        atlas.beginBatch();
        try {
            assertThrows(IllegalStateException.class, presentation::unbind);
            assertSame(binding, actualField(presentation, "act2RenderBinding"),
                    "Rejected close retains its actual renderer ledger for cleanup retry");
        } finally { atlas.endBatch(); }
        SessionManager.closeGameplaySession();
        for (int id : installed) {
            assertNull(atlas.getEntry(PatternAtlasRange.OBJECTS.base() + id));
            assertNull(SpritePresentation.patternSample(GameServices.graphics(), PatternAtlasRange.OBJECTS.base() + id));
        }
        assertSame(unrelated, atlas.getEntry(other)); assertEquals(sample, SpritePresentation.patternSample(GameServices.graphics(), other));
        System.out.printf("Controller-only Act2: first native Main returned%d; three further returns/full-course replay; actual primary omission pixels and %d selected uploads retired.%n", firstMain, installed.size());
        } finally { fixture.cleanup(); }
        writeOptionalCapture(imageForExport);
    }

    @Test void actualTitleSlideOutServicesItsQueuedArtThenReleasesTheSamePlayerToOrdinaryRightMovement() throws Exception {
        var old = ready(); var h = old.harness(); var module = h.module();
        for (int call = old.returned() + 1; call <= 10419; call++) step(h, controller(call));
        var right = controller(10419);
        for (int load = 0; load < 256; load++) {
            step(h, right);
            if (module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN)) break;
        }
        var frame = module.requirePlayerFrame(); var source = frame.stageSource();
        assertEquals(1, frame.environment().globals().levelFrames());
        assertEquals(1, frame.environment().controls().controlLocked());
        int lockedX = frame.state().motion().centreX16_16();
        boolean sawSlideOut = false, sawQueuedPlc = false, replayedPlc = false, released = false;
        int callsAfterMain = 0;
        for (; callsAfterMain < 512; callsAfterMain++) {
            var before = module.act2LoadState();
            int oldRoutine = before.reservedObjects().unsignedByte(3 * 64 + 0x24);
            long oldHead = before.section().queue().headSource();
            try { step(h, right); }
            catch (Exception | Error failure) {
                System.err.printf("Act2 Title stopped after %d completed ordinary calls; last closed parent routine=%d timer=%d Y=%d PLChead=%08X remaining=%d lock=%d actor=%s; retained actor=%s env=%s%n",
                        callsAfterMain + 1, oldRoutine, before.reservedObjects().unsignedByte(3 * 64 + 0x1E),
                        before.reservedObjects().unsignedWord(3 * 64 + 0xA), oldHead, before.section().remainingTilesWord(),
                        before.shared().environment().controls().controlLocked(), before.actor(), frame.state(), frame.environment());
                throw failure;
            }
            var now = module.act2LoadState();
            int routine = now.reservedObjects().unsignedByte(3 * 64 + 0x24);
            assertSame(frame, module.requirePlayerFrame()); assertSame(source, frame.stageSource()); assertFalse(frame.closed());
            assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN, now.calls().getLast());
            if (routine == 6 && !sawSlideOut) { sawSlideOut = true; replayNextRight(h, right); }
            if (routine == 10) {
                sawQueuedPlc |= now.section().queue().headSource() != 0;
                if (!replayedPlc && oldHead != 0) { replayedPlc = true; replayNextRight(h, right); }
            }
            if (frame.environment().controls().controlLocked() == 0) {
                assertEquals(0, now.reservedObjects().unsignedByte(3 * 64));
                assertEquals(0, now.section().queue().headSource());
                replayNextRight(h, right); released = true; break;
            }
            assertEquals(lockedX, frame.state().motion().centreX16_16(), "Held Right cannot move Sonic before the native Title lock clear");
        }
        assertTrue(sawSlideOut); assertTrue(sawQueuedPlc); assertTrue(replayedPlc);
        assertTrue(released, "The finite Title/PLC2 source must release controls within 512 ordinary calls");
        int releaseX = frame.state().motion().centreX16_16();
        for (int walk = 0; walk < 64; walk++) step(h, right);
        assertTrue(frame.state().motion().centreX16_16() > releaseX, "Actual unlocked Right must move the native player");
        assertEquals(0, frame.environment().controls().controlLocked());
        replayNextRight(h, right);
        System.out.printf("Controller-only Act2 Title: queued PLC observed, same Frame controls released after %d ordinary calls; 64 Right calls and full/course replay returned; X=%d.%n",
                callsAfterMain + 1, frame.state().motion().centreX16_16() >>> 16);
    }

    @Test void actualUnlockedAct2RightRouteReturnsThroughTheLastMeasuredClosedCutWithFullAndCourseReplay() throws Exception {
        var old = ready(); var h = old.harness(); var module = h.module();
        var right = controller(10419);
        for (int call = old.returned() + 1; call <= 10865; call++) {
            step(h, call <= 10419 ? controller(call) : right);
            if (call == 10763 || call == 10778) replayNextRight(h,right); // Genuine Boulder/Anton first calls, no net frame.
            if (call == 10718) replayNextRight(h, right); // Actual fresh TimePost8/9 Init at10719, no net extra frame.
            if (call == 10719) {
                assertEquals(2, module.act2LoadState().calls().stream().filter(c -> c == SonicCdGameModule.Act2LoadCall.TIME_POST_BODY).count());
                for (int whole = 38; whole <= 39; whole++) {
                    var post = module.act2LoadState().placement().pool().slots().get(whole - 32);
                    assertEquals(0x19, post.id()); assertEquals(2, post.routine());
                    assertEquals(8 + whole - 38, post.subtype());
                }
            }
        }
        var frame = module.requirePlayerFrame(); var source = frame.stageSource();
        assertTrue(old.frame().closed()); assertFalse(frame.closed());
        assertEquals(CdStageKey.parse("R12A"), source.stage());
        assertEquals(0, frame.environment().controls().controlLocked());
        assertEquals(0, module.act2LoadState().reservedObjects().unsignedByte(3 * 64));
        assertEquals(0, module.act2LoadState().section().queue().headSource());
        assertTrue(frame.state().motion().centreX16_16() > (80 << 16), "Ordinary native Right moved beyond its native entry");
        replayNextRight(h, right);
        step(h, right); // Retain the earlier positive10866 before crossing the newly admitted families.
        var pool=module.act2LoadState().placement().pool();
        assertEquals(0x1B,pool.slots().get(43-32).id());assertEquals(2,pool.slots().get(43-32).routine());
        assertEquals(0x3F,pool.slots().get(34-32).id());
        for(int whole:new int[]{46,48}) {assertEquals(0x3E,pool.slots().get(whole-32).id());assertEquals(2,pool.slots().get(whole-32).routine());}
        var platforms=pool.slots().stream().filter(image->image.id()==0x21).toList();
        assertFalse(platforms.isEmpty(),"Natural route must have admitted live Platform bodies at the completed cut");
        for(var platform:platforms) {
            assertEquals(2,platform.routine());assertEquals(0x44BE,platform.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE));
            assertEquals(0x20C9F2,platform.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));
        }
        assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.ANTON_BODY));
        assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.PATA_BATA_BODY));
        assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.PLATFORM_BODY));
        var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        replayNextRight(h, right); // Actual10866->10867 Tamabboh/Float entry, no net extra frame.
        for (int call = 10867; call <= 11032; call++) {
            step(h, right);
            assertLaterRouteOwners(h, frame, source, world, generation);
            if (call == 10867) {
                assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TAMABBOH_BODY));
                assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.FLOAT_BLOCK_BODY));
            }
        }
        replayNextRight(h, right); // The last measured successful11032->11033 crossing, both replay scopes.
        step(h, right);
        assertLaterRouteOwners(h, frame, source, world, generation);
        replayNextRight(h, right); // Actual11033->11034 Checkpoint entry, no net extra frame.
        for (int call = 11034; call <= 11145; call++) {
            step(h, right);
            assertLaterRouteOwners(h, frame, source, world, generation);
            if (call == 11034)
                assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.CHECKPOINT_BODY));
        }
        replayNextRight(h, right); // Retain the positive11145->11146 crossing.
        step(h, right);
        assertLaterRouteOwners(h, frame, source, world, generation);
        replayNextRight(h, right); // Actual11146->11147 natural Collapse Init, both scopes.
        for (int call = 11147; call <= 11212; call++) {
            step(h, right);
            assertLaterRouteOwners(h, frame, source, world, generation);
            if (call == 11147 || call == 11148) {
                var calls = module.act2LoadState().calls();
                assertTrue(calls.contains(SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_BODY));
                assertTrue(calls.contains(SonicCdGameModule.Act2LoadCall.COLLAPSE_PLATFORM_SOLID));
                assertTrue(module.act2LoadState().placement().pool().slots().stream().anyMatch(image -> image.id() == 0x20
                        && image.routine() == 2 && image.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE) == 0x44BE
                        && image.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS) == 0x20C414));
                if (call == 11147) replayNextRight(h, right); // Real next Main11147->11148, no net frame.
            }
        }
        replayNextRight(h, right); // Retain positive11212->11213, before the controller jump.
        step(h, right);
        assertLaterRouteOwners(h, frame, source, world, generation);
        int preJumpX=frame.state().motion().centreX16_16()>>>16;
        var jump=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,2,0,false,false),PlayerInputState.neutral());
        replayNextRight(h,jump); // Actual11213->11214 held B edge, both scopes.
        for(int call=11214;call<=11586;call++) {
            if(call==11342 || call==11470)replayNextRight(h,jump); // Next two fixed jump edges.
            if(call==11245 || call==11373 || call==11501)replayNextRight(h,jump); // Last held-jump row.
            if(call==11261 || call==11292 || call==11544 || call==11554)replayNextRight(h,right); // Actual Init/floor/FM entry.
            step(h,(call-11214)%128<32?jump:right);
            assertLaterRouteOwners(h,frame,source,world,generation);
            if(call==11245 || call==11373 || call==11501)replayNextRight(h,right); // Actual jump release.
            if(call==11261 || call==11262)assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.LOG_SHADOW_BODY));
            if(call==11292 || call==11293)assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.MOSQUI_BODY));
            if(call==11261 || call==11292)replayNextRight(h,right); // Real next Main, no net forward row.
            if(call==11544)assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.MOSQUI_PRIMARY_ANGLE));
            if(call==11554)assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.MOSQUI_FM));
        }
        replayNextRight(h,right); // Retain the earlier positive11586->11587 before the admitted Door.
        step(h,right);assertLaterRouteOwners(h,frame,source,world,generation);
        for(int call=11588;call<=12670;call++) {
            var input=(call-11214)%128<32?jump:right;
            if(call==11588 || call==11589 || call==12649 || call==12650 || call==12670)replayNextRight(h,input);
            step(h,input);assertLaterRouteOwners(h,frame,source,world,generation);
            if(call==11588 || call==11589) {
                assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_BODY));
                assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SECONDARY_TOP));
            }
            if(call==12649) {
                assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY));
                assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.POINTS_BODY));
            }
        }
        var livePlacement=(com.openggf.game.soniccd.objects.CdObjectPlacementRuntime)actualField(actualField(module,"act2Loading"),"placement");
        assertTrue(livePlacement.pool().capture().slots().stream().anyMatch(row->row.id()==0x1F && row.routine()==0),"Actual Explosion converted to Flower before the next unbound visit");
        for(int call=12671;call<=12699;call++) {
            if(call==12671 || call==12672 || call==12699)replayNextRight(h,right);
            step(h,right);assertLaterRouteOwners(h,frame,source,world,generation);
            assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.FLOWER_BODY));
            assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.FLOWER_PRIMARY_ANGLE));
        }
        for(int call=12700;call<=12884;call++) {
            var input=(call-11214)%128<32?jump:right;
            if(call==12700 || call==12701 || call==12878 || call==12884)replayNextRight(h,input);
            step(h,input);assertLaterRouteOwners(h,frame,source,world,generation);
            if(call==12700 || call==12701) {
                assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_BODY));
                assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_PLAYER_READ));
                assertFalse(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_PLAYER));
            }
            if(call==12700)assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_SOURCE_READ));
        }
        assertEquals(0,frame.state().motion().playerControl(),"This measured segment admits Path Init/Main, not natural acquisition");
        assertEquals(0,frame.environment().globals().timeWarpTimer());
        assertEquals(255,frame.environment().movement().gates().timeWarpDirection());
        assertTrue(Math.abs((short)frame.state().motion().groundVelocity())>=0x600,"The next reached fast-warp reserved read remains a separate owner");
        assertTrue((frame.state().motion().centreX16_16()>>>16)>preJumpX,"Controller-only jump advances beyond the observed obstruction");
        assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN, module.act2LoadState().calls().getLast());
        assertSame(frame, module.requirePlayerFrame()); assertSame(source, frame.stageSource()); assertFalse(frame.closed());
        assertInstanceOf(SonicCdPastDisplay.Ready.class, module.act2SelectedDisplay().projection());
        var before = captureNative(h); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse();
        registry.restore(full); assertNativeCapture(before, h);
        registry.restoreCourse(course); assertNativeCapture(before, h);
        System.out.printf("Controller-only Act2 Right+B returned12884; full/course10718->10719,10763->10764,10778->10779,10865->10866,10866->10867,11032->11033,11033->11034,11145->11146,11146->11147,11147->11148,11212->11213,11213->11214,11244->11245,11245->11246,11260->11261,11261->11262,11291->11292,11292->11293 11543->11544,11553->11554 and11586->11587,11587->11588,11588->11589,12648->12649,12649->12650,12669->12670,12670->12671,12671->12672,12698->12699,12699->12700,12700->12701,12877->12878,12883->12884, native PathInit/Main and natural Flower/Door/Explosion/Points/TimePosts/Boulder/Anton/Pata/Platform/Tamabboh/Float/Checkpoint/Collapse/Log/Mosqui, controls clear and selected picture Ready; X=%d.%n",
                frame.state().motion().centreX16_16() >>> 16);
    }

    // BEGIN later-route EGL case: additive to the three pinned controller cases.
    @Test void actualLaterAct2ControllerCutsRenderSelectedSonicAndReplayTheirCompleteValuesAndPixels() throws Exception {
        byte[] imageForExport;
        try (var egl = TestSonicCdPastResultsRoute.Egl.open()) {
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            var old = ready(); var h = old.harness(); var module = h.module();
            var world = h.mode().getWorldSession();
            long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
            var right = controller(10419);
            for (int call = old.returned() + 1; call <= 10777; call++)
                step(h, call <= 10419 ? controller(call) : right);
            var frame = module.requirePlayerFrame(); var source = frame.stageSource();
            assertTrue(old.frame().closed()); assertFalse(frame.closed());
            assertEquals(CdStageKey.parse("R12A"), source.stage());
            assertEquals(0, frame.environment().controls().controlLocked(), "The actual Title path has released held Right");
            assertEquals(0, module.act2LoadState().reservedObjects().unsignedByte(3 * 64));
            assertLaterRouteOwners(h, frame, source, world, generation + 1);
            renderReplayNextRight(h, right, 10778);
            assertLaterRouteOwners(h, frame, source, world, generation + 1);
            for (int call = 10779; call <= 10865; call++) {
                step(h, right);
                assertLaterRouteOwners(h, frame, source, world, generation + 1);
            }
            renderReplayNextRight(h, right, 10866);
            assertLaterRouteOwners(h, frame, source, world, generation + 1);
            for (int call = 10867; call <= 11032; call++) {
                step(h, right);
                assertLaterRouteOwners(h, frame, source, world, generation + 1);
                if (call == 10867) {
                    assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TAMABBOH_BODY));
                    assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.FLOAT_BLOCK_BODY));
                }
            }
            renderReplayNextRight(h, right, 11033);
            assertLaterRouteOwners(h, frame, source, world, generation + 1);
            for(int call=11034;call<=11212;call++) {step(h,right);assertLaterRouteOwners(h,frame,source,world,generation+1);}
            renderReplayNextRight(h,right,11213);
            var jump=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,2,0,false,false),PlayerInputState.neutral());
            for(int call=11214;call<=11340;call++) {
                step(h,call<=11245?jump:right);assertLaterRouteOwners(h,frame,source,world,generation+1);
            }
            renderReplayNextRight(h,right,11341);
            for(int call=11342;call<=11586;call++) {
                step(h,(call-11214)%128<32?jump:right);assertLaterRouteOwners(h,frame,source,world,generation+1);
            }
            renderReplayNextRight(h,right,11587);
            for(int call=11588;call<=12669;call++) {
                step(h,(call-11214)%128<32?jump:right);assertLaterRouteOwners(h,frame,source,world,generation+1);
            }
            renderReplayNextRight(h,right,12670);
            for(int call=12671;call<=12698;call++) {step(h,right);assertLaterRouteOwners(h,frame,source,world,generation+1);}
            renderReplayNextRight(h,right,12699);
            for(int call=12700;call<=12883;call++) {step(h,(call-11214)%128<32?jump:right);assertLaterRouteOwners(h,frame,source,world,generation+1);}
            imageForExport=renderReplayNextRight(h,jump,12884).clone();
            assertEquals(0, frame.environment().controls().controlLocked());
            System.out.println("ENGINE controller-only Act2 EGL: selected display pixels/full-course replay at10778,10866,11033,11213,11341 11587,12670,12699 and12884; selected Sonic omission changes all nine endpoint pictures; no forward call12885.");
        } finally { fixture.cleanup(); }
        writeOptionalLaterCapture(imageForExport);
    }

    /** Captures precede both pixel work and the one real forward call; each replay leaves the same endpoint. */
    private static byte[] renderReplayNextRight(Harness h, LogicalInputSnapshot right, int completedCall) throws Exception {
        var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h);
        var frame = h.module().requirePlayerFrame(); var source = frame.stageSource(); var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var beforePixels = drawLaterSelectedCut(h, false, completedCall - 1); assertNativeCapture(before, h);
        step(h, right); assertLaterRouteOwners(h, frame, source, world, generation);
        var after = captureNative(h);
        var afterPixels = drawLaterSelectedCut(h, true, completedCall); assertNativeCapture(after, h);
        registry.restore(full); assertNativeCapture(before, h);
        assertArrayEquals(beforePixels, drawLaterSelectedCut(h, false, completedCall - 1)); assertNativeCapture(before, h);
        step(h, right); assertNativeCapture(after, h); assertLaterRouteOwners(h, frame, source, world, generation);
        assertArrayEquals(afterPixels, drawLaterSelectedCut(h, true, completedCall)); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h);
        assertArrayEquals(beforePixels, drawLaterSelectedCut(h, false, completedCall - 1)); assertNativeCapture(before, h);
        step(h, right); assertNativeCapture(after, h); assertLaterRouteOwners(h, frame, source, world, generation);
        assertArrayEquals(afterPixels, drawLaterSelectedCut(h, true, completedCall)); assertNativeCapture(after, h);
        return afterPixels;
    }

    private static void assertLaterRouteOwners(Harness h, Object frame, Object source, Object world, long generation) {
        var current = h.module().requirePlayerFrame();
        assertSame(frame, current); assertSame(source, current.stageSource()); assertFalse(current.closed());
        assertSame(world, h.mode().getWorldSession());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN, h.module().act2LoadState().calls().getLast());
    }

    /** Draw the actual selected prior CPU/current display; omission changes renderer input only. */
    private static byte[] drawLaterSelectedCut(Harness h, boolean provePrimary, int completedCall) throws Exception {
        var module = h.module(); var before = captureNative(h); var selected = module.act2SelectedDisplay();
        var cpu = selected.inputs().selectedCpu().orElseThrow();
        var scene = assertInstanceOf(SonicCdPastDisplay.Ready.class, selected.projection(),
                "Actual selected later Act2 display at call" + completedCall + ": " + selected.projection()).scene();
        var foreground = selected.inputs().foreground(); var background = selected.inputs().background();
        var current = selected.inputs().current(); var player = selected.inputs().player();
        var frame = module.requirePlayerFrame(); var source = frame.stageSource(); var world = h.mode().getWorldSession();
        var atlas = GameServices.graphics().getPatternAtlas();
        var actual = draw(h); assertNativeCapture(before, h);
        var presentation = assertInstanceOf(SonicCdPlayerPresentation.class, actualField(module, "playerPresentation"));
        var binding = actualField(presentation, "act2RenderBinding");
        var renderer = assertInstanceOf(RetainedPlaneSceneRenderer.class, actualField(binding, "renderer"));
        var entries = new java.util.LinkedHashMap<Integer, Object>();
        var samples = new java.util.LinkedHashMap<Integer, Object>();
        assertFalse(scene.nativePatterns().isEmpty());
        for (int id : scene.nativePatterns().keySet()) {
            int logical = PatternAtlasRange.OBJECTS.base() + id;
            var entry = atlas.getEntry(logical); assertNotNull(entry);
            entries.put(logical, entry); samples.put(logical, SpritePresentation.patternSample(GameServices.graphics(), logical));
        }
        assertArrayEquals(actual, draw(h)); assertNativeCapture(before, h);
        if (provePrimary) {
            assertTrue(cpu.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 0),
                    "The actual selected prior CPU at call" + completedCall + " contains Sonic");
            var omitted = TestSonicCdPastResultsRoute.omitWhole(scene, cpu, 0);
            assertSame(scene.foreground(), omitted.foreground()); assertSame(scene.background(), omitted.background());
            assertArrayEquals(scene.packedHscroll256(), omitted.packedHscroll256());
            assertEquals(scene.vscrollLongBits(), omitted.vscrollLongBits());
            assertArrayEquals(scene.selectedPalette128(), omitted.selectedPalette128());
            assertEquals(scene.nativePatterns(), omitted.nativePatterns());
            var camera = h.mode().getCamera(); clearPixels();
            renderer.draw(omitted, camera.getX(), camera.getY(), module::validateAct2LoadingCut);
            assertFalse(Arrays.equals(actual, pixels()), "Selected Sonic contributes engine pixels at actual controller call" + completedCall);
            assertNativeCapture(before, h); assertArrayEquals(actual, draw(h)); assertNativeCapture(before, h);
        }
        assertSame(selected, module.act2SelectedDisplay());
        assertSame(foreground, module.act2SelectedDisplay().inputs().foreground());
        assertSame(background, module.act2SelectedDisplay().inputs().background());
        assertSame(current, module.act2SelectedDisplay().inputs().current());
        assertSame(player, module.act2SelectedDisplay().inputs().player());
        assertSame(frame, module.requirePlayerFrame()); assertSame(source, frame.stageSource());
        assertSame(world, h.mode().getWorldSession()); assertSame(atlas, GameServices.graphics().getPatternAtlas());
        assertSame(binding, actualField(presentation, "act2RenderBinding")); assertSame(renderer, actualField(binding, "renderer"));
        for (int logical : entries.keySet()) {
            assertSame(entries.get(logical), atlas.getEntry(logical));
            assertEquals(samples.get(logical), SpritePresentation.patternSample(GameServices.graphics(), logical));
        }
        assertNativeCapture(before, h); return actual;
    }

    /** Executed only after module/session/graphics/context/boot fixture cleanup; never a test input. */
    private static void writeOptionalLaterCapture(byte[] rgba) throws Exception {
        String output = System.getenv("SONICCD_ACT2_ROUTE_OUTPUT"); if (output == null || output.isBlank()) return;
        var directory = Path.of(output).toAbsolutePath().normalize();
        var owned = Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "captures", "engine").toAbsolutePath().normalize();
        assertTrue(directory.startsWith(owned) && !directory.equals(owned), "Actual ENGINE route export needs its own directory under the engine capture root");
        assertEquals(320 * 224 * 4, rgba.length); Files.createDirectories(directory);
        var image = new java.awt.image.BufferedImage(320, 224, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
            int at = ((223 - y) * 320 + x) * 4;
            int argb = (rgba[at + 3] & 255) << 24 | (rgba[at] & 255) << 16 | (rgba[at + 1] & 255) << 8 | rgba[at + 2] & 255;
            image.setRGB(x, y, argb);
        }
        if (!javax.imageio.ImageIO.write(image, "png", directory.resolve("ENGINE-act2-controller-call-12884.png").toFile()))
            throw new java.io.IOException("PNG writer unavailable");
        Files.writeString(directory.resolve("ENGINE-act2-controller-call-12884.txt"),
                "ENGINE: OpenGGF original-USA controller-only route, actual completed call12884.\n"
                + "320x224 RGBA from the actual selected prior CPU/current Act2 display on configured surfaceless EGL.\n"
                + "Full/course complete-value and pixel replay; selected Sonic renderer-only omission contrast.\n"
                + "Written after module/session/graphics/context/boot fixture cleanup. No native-image or parity claim.\n");
    }
    // END later-route EGL case.

    private static void replayNextRight(Harness h, LogicalInputSnapshot right) throws Exception {
        var registry = h.mode().getRewindRegistry(); var before = captureNative(h);
        var full = registry.capture(); var course = registry.captureCourse();
        step(h, right); var after = captureNative(h);
        registry.restore(full); assertNativeCapture(before, h); step(h, right); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h); step(h, right); assertNativeCapture(after, h);
        registry.restore(full); assertNativeCapture(before, h);
    }

    private static byte[] draw(Harness h) { clearPixels(); h.module().gameplayFrameController().drawOverlay(); return pixels(); }
    private static void clearPixels() {
        glViewport(0, 0, 320, 224); glClearColor(1, 0, 1, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }
    private static byte[] pixels() {
        glFinish(); var bytes = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, bytes); assertEquals(GL_NO_ERROR, glGetError());
        byte[] result = new byte[bytes.capacity()]; bytes.get(result); return result;
    }
    /** Called after session, module, graphics, context and fixture cleanup; output is never a runtime input. */
    private static void writeOptionalCapture(byte[] rgba) throws Exception {
        String output = System.getenv("SONICCD_ACT2_ENTRY_OUTPUT"); if (output == null || output.isBlank()) return;
        var directory = Path.of(output).toAbsolutePath().normalize();
        var owned = Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "captures", "engine").toAbsolutePath().normalize();
        assertTrue(directory.startsWith(owned) && !directory.equals(owned)); Files.createDirectories(directory);
        var image = new java.awt.image.BufferedImage(320, 224, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
            int at = ((223 - y) * 320 + x) * 4;
            int argb = (rgba[at + 3] & 255) << 24 | (rgba[at] & 255) << 16 | (rgba[at + 1] & 255) << 8 | rgba[at + 2] & 255;
            image.setRGB(x, y, argb);
        }
        if (!javax.imageio.ImageIO.write(image, "png", directory.resolve("actual-act2-entry.png").toFile())) throw new java.io.IOException("PNG writer unavailable");
    }

    private static LogicalInputSnapshot controller(int call) {
        if (call < 1 || call > 10419) throw new IllegalArgumentException("Fixed original recipe ends at programme return10419");
        if (call <= 8276) return route(call);
        int directions = 8, actions = call <= 8308 ? 2 : 0;
        if (call >= 9001 && call <= 9203) directions = 4;
        else if (call >= 9206 && call <= 9217) directions = 0;
        else if (call >= 9218 && call <= 9263) { directions = 2; actions = call == 9219 ? 2 : 0; }
        else if (call >= 9336 && call <= 9367) actions = 2;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions, 0, actions, 0, false, false),
                PlayerInputState.neutral());
    }
}

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
        for(int call=12885;call<=12886;call++) {
            replayNextRight(h,jump);
            step(h,jump);assertLaterRouteOwners(h,frame,source,world,generation);
            var stars=module.act2LoadState();
            assertTrue(stars.calls().contains(SonicCdGameModule.Act2LoadCall.TIME_STARS_BODY));
            assertTrue(stars.calls().contains(call==12885?SonicCdGameModule.Act2LoadCall.TIME_STARS_INIT:SonicCdGameModule.Act2LoadCall.TIME_STARS_ACTIVE));
            for(int whole=12;whole<16;whole++) {
                var image=stars.reservedObjects().slice((whole-1)*64,64);
                assertEquals(3,image.unsignedByte(0));assertEquals(6,image.unsignedByte(0x24));
                assertEquals(whole-7,image.unsignedByte(0x1C));assertEquals(0x21D528,image.unsignedLong(4));
            }
            assertEquals(call==12885?0:0x86,stars.level().powerup());
            assertEquals(call-12884,frame.environment().globals().timeWarpTimer());
        }
        for(int call=12887;call<=13062;call++) {
            var input=(call-11214)%128<32?jump:right;
            if(call==12887 || call==12888 || call==12891 || call==13062)replayNextRight(h,input);
            step(h,input);assertLaterRouteOwners(h,frame,source,world,generation);
            if(call==12887) {
                assertNaturalPowerupCurrent(h);
                assertEquals(3,frame.environment().globals().timeWarpTimer());
                assertTrue(module.act2SelectedDisplay().inputs().selectedCpu().orElseThrow().provenance().stream()
                        .anyMatch(p->p.slotRef().wholeIndex()>=12 && p.slotRef().wholeIndex()<16),
                        "Actual prior star Draw is selected only after the real current upload");
            }
            if(call==12891) {
                assertEquals(0,frame.environment().globals().timeWarpTimer());
                var raw=module.act2LoadState().reservedObjects();
                for(int whole=12;whole<16;whole++)assertEquals(0,raw.unsignedByte((whole-1)*64));
            }
        }
        assertNaturalPowerupCurrent(h);
        for(int call=13063;call<=13126;call++) {
            var input=call<=13098?((call-11214)%128<32?jump:right):controller(call);
            if(call==13063 || call==13064 || call==13095 || call==13096 || call==13097 || call==13098
                    || call==13099 || call==13125 || call==13126)replayNextRight(h,input);
            step(h,input);assertLaterRouteOwners(h,frame,source,world,generation);
            assertNaturalItemShieldCut(h,call);
        }
        // Keep the old13126 closed cut; it now uses the ordinary Left backtrack after13098.
        for(int call=13127;call<=13264;call++) {
            var input=controller(call);
            if(call==13195 || call==13199 || call==13264)replayNextRight(h,input);
            step(h,input);assertLaterRouteOwners(h,frame,source,world,generation);
        }
        // Retain comparison-only closed13264 checkpoint before the newly measured Ring reward at13265.
        assertEquals(5674,frame.state().motion().centreX16_16()>>>16);
        assertEquals(584,frame.state().motion().centreY16_16()>>>16);
        assertEquals(0,frame.state().motion().playerControl());
        assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_BODY));
        assertFalse(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_PLAYER));
        assertFalse(module.act2LoadState().sounds().stream().anyMatch(SonicCdGameModule.PastTunnelPathFm.class::isInstance));
        assertNaturalShieldCurrent(h);
        assertEquals(0,frame.environment().globals().timeWarpTimer());
        assertTrue(module.act2SelectedDisplay().inputs().selectedCpu().orElseThrow().provenance().stream()
                .noneMatch(p->p.slotRef().wholeIndex()>=12 && p.slotRef().wholeIndex()<16));
        assertTrue((frame.state().motion().centreX16_16()>>>16)>preJumpX,"Controller-only jump advances beyond the observed obstruction");
        assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN, module.act2LoadState().calls().getLast());
        assertSame(frame, module.requirePlayerFrame()); assertSame(source, frame.stageSource()); assertFalse(frame.closed());
        assertInstanceOf(SonicCdPastDisplay.Ready.class, module.act2SelectedDisplay().projection());
        var before = captureNative(h); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse();
        registry.restore(full); assertNativeCapture(before, h);
        registry.restoreCourse(course); assertNativeCapture(before, h);
        System.out.printf("Controller-only Act2 returned13264 after fixed Left96,L+B4,Left66; full/course10718->10719,10763->10764,10778->10779,10865->10866,10866->10867,11032->11033,11033->11034,11145->11146,11146->11147,11147->11148,11212->11213,11213->11214,11244->11245,11245->11246,11260->11261,11261->11262,11291->11292,11292->11293 11543->11544,11553->11554 and11586->11587,11587->11588,11588->11589,12648->12649,12649->12650,12669->12670,12670->12671,12671->12672,12698->12699,12699->12700,12700->12701,12877->12878,12883->12884,12884->12885,12885->12886,12886->12887,12887->12888,12890->12891,13061->13062,13062->13063,13063->13064,13094->13095,13095->13096,13096->13097,13097->13098,13098->13099,13124->13125,13125->13126,13194->13195,13198->13199,13263->13264, actual native Item/Shield award/Init/active/upload and Item retirement, CPU/current TimeStars upload and reset/retirement, native TimeStarsInit/Active and PathInit/Main plus natural Flower/Door/Explosion/Points/TimePosts/Boulder/Anton/Pata/Platform/Tamabboh/Float/Checkpoint/Collapse/Log/Mosqui, controls clear and selected picture Ready; X=%d.%n",
                frame.state().motion().centreX16_16() >>> 16);
        // Keep the complete13264 checkpoint above; the same controller recipe now reaches closed13385.
        assertEquals(23,module.act2LoadState().ring().rings());
        for(int call=13265;call<=13385;call++) {
            var input=controller(call);
            if(call==13265 || call==13266 || call==13277 || call==13278 || call==13279
                    || call==13363 || call==13364 || call==13365 || call==13366 || call==13367
                    || call==13368 || call==13384 || call==13385)replayNextRight(h,input);
            step(h,input);assertLaterRouteOwners(h,frame,source,world,generation);
            assertNaturalDoorSplashCut(h,call);
        }
        var closed13385=captureNative(h);var endFull=registry.capture();var endCourse=registry.captureCourse();
        registry.restore(endFull);assertNativeCapture(closed13385,h);
        registry.restoreCourse(endCourse);assertNativeCapture(closed13385,h);
        System.out.println("Controller-only Act2 closed13385: Ring13265; Path79 acquire13277/FM91 and Got13278/FM91; Door67 child41 emission13364, Init/FMA2 at13365, selected Splash13366, control release13367 and FC routine4 still Draw13385. Full/course before-forward replay surrounds each edge. This complete13385 checkpoint is retained before the additional Robot segment below.");
        // Root facts a73f8f901dc67dcc261256a9c6bc68a46eaa06628b39c12ff2a3cfc158e17bba: comparisons only.
        // N1ecc5f3 Robot Generator/Main.asm:25-94; raw timeZone1 returns through Draw/despawn without Past activity.
        assertArrayEquals(new byte[64],module.act2LoadState().placement().pool().slots().get(47-32).copyBytes(),
                "Last closed13385 has no Robot47; ordinary SpawnObjects admits its source on13386");
        byte[] expectedRobot47=java.util.HexFormat.of().parseHex("2d0404000020e4f619f000000160000000000000000020220422020000000000000000b902000000000000040020e73401600001000000000000000000000000");
        for(int call=13386;call<=13422;call++) {
            var input=controller(call);
            if(call==13386 || call==13387 || call==13422)replayNextRight(h,input);
            step(h,input);assertLaterRouteOwners(h,frame,source,world,generation);
            var robotState=module.act2LoadState();var robotCalls=robotState.calls();
            assertArrayEquals(expectedRobot47,robotState.placement().pool().slots().get(47-32).copyBytes(),
                    "Complete naturally initialized Robot47 image at closed"+call);
            assertEquals(1,robotCalls.stream().filter(c->c==SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY).count());
            assertTrue(robotCalls.indexOf(SonicCdGameModule.Act2LoadCall.PRIMARY_MAIN)
                    <robotCalls.indexOf(SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY));
            assertTrue(robotCalls.indexOf(SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY)
                    <robotCalls.indexOf(SonicCdGameModule.Act2LoadCall.DYNAMIC_OBJECTS));
            assertFalse(robotState.sounds().stream().anyMatch(SonicCdGameModule.PastRobotGeneratorFm.class::isInstance));
            assertEquals(call==13386,robotCalls.contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY));
            if(call==13386) {
                assertArrayEquals(new byte[64],robotState.placement().pool().slots().get(41-32).copyBytes(),
                        "The same completed walk returns from Splash41 routine4 with all64 bytes deleted before Robot47");
                assertTrue(robotCalls.indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY)
                        <robotCalls.indexOf(SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY));
            }
            assertEquals(1,frame.environment().movement().gates().timeZone(),"Robot reads actual raw timeZone1");
            assertEquals(1,frame.environment().movement().gates().zoneAct(),"Robot reads actual act1");
            assertEquals(255,frame.environment().movement().gates().timeWarpDirection());
            assertEquals(0,frame.state().motion().playerControl());assertEquals(0,frame.environment().controls().controlLocked());
        }
        assertEquals(0x18612800,frame.state().motion().centreX16_16());
        assertEquals(0x004B1700,frame.state().motion().centreY16_16());
        assertEquals(6241,frame.state().motion().centreX16_16()>>>16);assertEquals(75,frame.state().motion().centreY16_16()>>>16);
        assertEquals(35,module.act2LoadState().ring().rings());
        assertInstanceOf(SonicCdPastDisplay.Ready.class,module.act2SelectedDisplay().projection());
        var closed13422=captureNative(h);var robotEndFull=registry.capture();var robotEndCourse=registry.captureCourse();
        registry.restore(robotEndFull);assertNativeCapture(closed13422,h);
        registry.restoreCourse(robotEndCourse);assertNativeCapture(closed13422,h);
        System.out.println("Controller-only Act2 closed13422: natural Robot47 Init13386 and Main13387..13422 preserve the exact full64 source image, one returned body receipt per walk and no Robot FM. Completed13386 deletes all64 Splash41 bytes. Full/course before-forward replay covers13385->13386,13386->13387 and13421->13422; endpoint X6241/Y75, rings35, raw timeZone1/act1, controls clear. This complete13422 checkpoint is retained before the new MovingSpring segment below.");
        // FINAL Root facts c55ba8b828de75be238e16830ee44273aa9b25ff133c7300db4590a65e4a2fc9; comparisons only.
        // Rows13423..13450: complete parent41/actual child71 images and completed Main CPU hash; never ingress.
        String[][] expectedMovingSpringCuts={
            {"0f04033800209a961a1a00000376000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036600000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","fee8ddf5172ef8b797169bfa4afb77e74e6170d16cd7c868178a8f2115f5d6c0"}, // 13423
            {"0f04033800209a961a1a00000377000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","60d9a2146d0c9497da22893c82d83abab2a7731cf19bd82f40339bc7a9de4542"}, // 13424
            {"0f04033800209a961a1a00000378000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036800000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","209bf359564f7ec913759575f4f5978b01a5c421ba5b624a6a39058fea4db115"}, // 13425
            {"0f04033800209a961a1a00000379000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036900000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","87d08f45ce37fefccc2f958b270feaa1b4f3a7a56c2180148bbe6ad6046af031"}, // 13426
            {"0f04033800209a961a1a0000037a000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036a00000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","f473020fac5fd48fa655ba841952ef4671103497f39e6d04fd3c84a52d6b6272"}, // 13427
            {"0f04033800209a961a1a0000037b000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036b00000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","935b91d5ac77db2aced4237fd5e5a56f13ffc01cb5c5be8d9880c62851947bc0"}, // 13428
            {"0f04033800209a961a1a0000037c000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036c00000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","fd0233eed5d0f680c6059e5833660fe2e42eb794b932a8e890ad622851dcfcbc"}, // 13429
            {"0f04033800209a961a1a0000037d000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036d00000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","9dea91588c0b13582b5ab682c80b4b25899fc747046453c380eed76422ce2b79"}, // 13430
            {"0f04033800209a961a1a0000037e000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036e00000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","bc6f9d419fb73c282e373ea174f802f1efcbb769eec6251fac4699dbdfdcf68e"}, // 13431
            {"0f04033800209a961a1a0000037f000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036f00000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","9ecab959afe59e95fe4e30e79bf6dfb125000909a1ae9e019dab7e6330d83810"}, // 13432
            {"0f04033800209a961a1a00000380000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037000000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","bfa16dac9057d035474593969e8049eea5406761863b856b108e2c77b0d6745d"}, // 13433
            {"0f04033800209a961a1a00000381000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037100000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","e1dade45216cce41c946d3efa1b2b5db150c6c036193628f482097c569015017"}, // 13434
            {"0f04033800209a961a1a00000382000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037200000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","47e3cc996e751cf47b8d0412b1ee0a0fba46cb10eae0f2485cf8520e2fddc1b1"}, // 13435
            {"0f04033800209a961a1a00000383000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037300000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","68d1ef1bc26b92f14a8c194f63059e5d150d25b1700382a1249c9b0cd108c061"}, // 13436
            {"0f04033800209a961a1a00000384000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037400000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","cf6ed8423e436d18985d6575e284895adb193c19e4886c70bf09d1e0cf2131a7"}, // 13437
            {"0f04033800209a961a1a00000385000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037500000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","ad4cd2dcb1fcb472436537f7a608115131199792c968b9b515ed0ca7dfb72ffa"}, // 13438
            {"0f04033800209a961a1a00000386000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037600000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","e1a941e6c3ee11a76741977135d6e7e9c41e5385e72a867b7f2b27fd3ee5e082"}, // 13439
            {"0f04033800209a961a1a00000387000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","9ece44a5f07e1862f978297f3e0d95b119d9e4d6b0c37b8ff1e2b511462a4f8a"}, // 13440
            {"0f04033800209a961a1a00000388000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037800000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","41345faaf7b5d446774088284d5a9dc7400def1a3066a953ba456b5ed987982a"}, // 13441
            {"0f04033800209a961a1a00000387000001800000000007000408000000000000000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a1a0000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","fa7d12214e7af80f27781f39dcbed44429574ba55c0409f34d23417c86181a58"}, // 13442
            {"0f04033800209a961a1b80000387000001800000000007000408000100000800000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a1b0000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","0c26b115f14ced1b5f9b772b895dc99cdef8356d3d14e65cb592e6ef7ed50f0d"}, // 13443
            {"0f04033800209a961a1d00000387000001800000000007000408000100000700000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a1d0000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","8cdd8f5462479aecf7a23480c094bc66a4816a6f0f6bfc395ff0cccce7a05079"}, // 13444
            {"0f04033800209a961a1e80000387000001800000000007000408000100000600000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a1e0000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","8cc6a4091175b5a6c62bd5f516738718c0858f48c56574d5cf93a6c2b50cf13e"}, // 13445
            {"0f04033800209a961a2000000387000001800000000007000408000100000500000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a200000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","4eefff6cf0ff61fb98265e3f4efd89573c25b4a2d25fb87bd8ff45d780ef5118"}, // 13446
            {"0f04033800209a961a2180000387000001800000000007000408000100000400000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a210000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","ec85adac06e09db945fd89fe4398024fde4e89f713979268e7902fbb3c70da0c"}, // 13447
            {"0f04033800209a961a2300000387000001800000000007000408000100000300000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a230000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","799e8833275535457440bf6d4a7e3e170ed4712f3b0413b75760f6cb3869c67a"}, // 13448
            {"0f04033800209a961a2480000387000001800000000007000408000100000200000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a240000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","c598b9734dcb2224780fd2e3216742e5183cadbd787cfcf943516ae910810646"}, // 13449
            {"0f04033800209a961a2600000387000001800000000007000408000100000100000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a260000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","e93f638f76fa87505e09bcc608e67131c3cd4d717ceeb653a02e5615f0d33e92"}, // 13450
        };
        // Original ledger indices: wheel, Robot, later child(last Spring), Dynamic, Main return, Spring count, CPU pieces, primary scratch.
        int[][] expectedMovingSpringReceipts={
            {31,37,67,72,80,6,27,4}, // 13423
            {31,36,58,61,69,5,26,4}, // 13424
            {31,36,58,61,69,5,37,4}, // 13425
            {31,36,58,61,69,5,34,4}, // 13426
            {31,36,58,61,69,5,25,4}, // 13427
            {31,36,58,61,69,5,34,4}, // 13428
            {31,36,58,61,69,5,29,4}, // 13429
            {31,36,58,61,69,5,26,4}, // 13430
            {31,36,58,61,69,5,37,4}, // 13431
            {31,36,58,61,69,5,34,4}, // 13432
            {31,36,58,61,69,5,23,4}, // 13433
            {31,36,58,61,69,5,38,4}, // 13434
            {29,34,56,59,67,5,29,4}, // 13435
            {29,34,56,59,67,5,26,4}, // 13436
            {29,34,56,59,67,5,37,4}, // 13437
            {29,34,56,59,67,5,30,4}, // 13438
            {29,34,56,59,67,5,25,4}, // 13439
            {29,34,56,59,67,5,38,4}, // 13440
            {29,34,56,59,67,5,29,4}, // 13441
            {29,34,56,59,67,5,26,4}, // 13442
            {29,34,56,59,67,5,23,4}, // 13443
            {29,34,56,59,67,5,38,4}, // 13444
            {29,34,56,59,67,5,33,4}, // 13445
            {29,34,56,59,67,5,26,4}, // 13446
            {29,34,56,59,67,5,37,252}, // 13447
            {29,34,56,59,67,5,26,252}, // 13448
            {31,36,58,61,69,5,25,252}, // 13449
            {31,36,58,61,69,5,38,252}, // 13450
        };
        assertArrayEquals(new byte[64],module.act2LoadState().placement().pool().slots().get(41-32).copyBytes());
        assertArrayEquals(new byte[64],module.act2LoadState().placement().pool().slots().get(71-32).copyBytes(),
                "The actual later child71 was a complete zero image at closed13422, before real allocation");
        for(int call=13423;call<=13450;call++) {
            var input=controller(call);
            if(call==13423 || call==13424 || call==13442 || call==13443 || call==13450)replayNextRight(h,input);
            step(h,input);assertLaterRouteOwners(h,frame,source,world,generation);
            int row=call-13423;var movingState=module.act2LoadState();var movingCalls=movingState.calls();
            var movingParent=movingState.placement().pool().slots().get(41-32);var movingChild=movingState.placement().pool().slots().get(71-32);
            assertArrayEquals(java.util.HexFormat.of().parseHex(expectedMovingSpringCuts[row][0]),movingParent.copyBytes(),"Complete actual parent41 at closed"+call);
            assertArrayEquals(java.util.HexFormat.of().parseHex(expectedMovingSpringCuts[row][1]),movingChild.copyBytes(),"Complete actual child71 at closed"+call);
            assertArrayEquals(expectedRobot47,movingState.placement().pool().slots().get(47-32).copyBytes(),"Existing Robot47 native nonwrites");
            assertEquals(0x0F,movingParent.id());assertEquals(call<13442?2:4,movingParent.routine());
            assertEquals(call<13442?886+row:903,movingParent.centreY());
            assertEquals(call<=13442?6682<<16:(6682<<16)+(call-13442)*0x18000,movingParent.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.X_POSITION));
            assertEquals(0x180,movingParent.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.X_VELOCITY));
            assertEquals(0x0A,movingChild.id());assertEquals(2,movingChild.routine(),"Actual later child already ran ordinary Spring Init in the allocation pass");
            assertEquals(movingParent.centreX(),movingChild.centreX());assertEquals(movingParent.centreY()-0x10,movingChild.centreY());
            assertEquals(0,movingChild.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.X_FRACTION));
            assertEquals(0,movingChild.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.Y_FRACTION));
            assertEquals(1,movingCalls.stream().filter(c->c==SonicCdGameModule.Act2LoadCall.MOVING_SPRING_BODY).count());
            assertEquals(expectedMovingSpringReceipts[row][0],movingCalls.indexOf(SonicCdGameModule.Act2LoadCall.MOVING_SPRING_BODY));
            assertEquals(expectedMovingSpringReceipts[row][1],movingCalls.indexOf(SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY));
            assertEquals(expectedMovingSpringReceipts[row][2],movingCalls.lastIndexOf(SonicCdGameModule.Act2LoadCall.SPRING_BODY));
            assertEquals(expectedMovingSpringReceipts[row][3],movingCalls.indexOf(SonicCdGameModule.Act2LoadCall.DYNAMIC_OBJECTS));
            assertEquals(expectedMovingSpringReceipts[row][4],movingCalls.indexOf(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN));
            assertEquals(expectedMovingSpringReceipts[row][5],movingCalls.stream().filter(c->c==SonicCdGameModule.Act2LoadCall.SPRING_BODY).count());
            assertTrue(expectedMovingSpringReceipts[row][0]<expectedMovingSpringReceipts[row][1]
                    && expectedMovingSpringReceipts[row][1]<expectedMovingSpringReceipts[row][2]
                    && expectedMovingSpringReceipts[row][2]<expectedMovingSpringReceipts[row][3]);
            assertEquals(new com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.Status(2,6144,338,303,189,173,0,0),movingState.placement().status());
            assertEquals(expectedMovingSpringReceipts[row][7],frame.environment().movement().globals().primaryAngle());
            assertEquals(0,movingState.ring().timeStopTimer());assertEquals(35,movingState.ring().rings());
            assertEquals(1,frame.environment().movement().gates().timeZone());assertEquals(1,frame.environment().movement().gates().zoneAct());
            assertEquals(0,frame.state().motion().playerControl());assertEquals(0,frame.environment().controls().controlLocked());
            var movingLoop=actualField(actualField(actualField(module,"act2Loading"),"values"),"loop");
            var movingCpu=assertInstanceOf(com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat.class,
                    ((java.util.Optional<?>)actualField(movingLoop,"completedDraw")).orElseThrow());
            assertEquals(640,movingCpu.bytes().length);assertEquals(expectedMovingSpringReceipts[row][6],movingCpu.spriteCount());
            assertEquals(expectedMovingSpringCuts[row][2],java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(movingCpu.bytes())));
            assertTrue(movingCpu.provenance().stream().noneMatch(p->p.slotRef().wholeIndex()==41 || p.slotRef().wholeIndex()==47 || p.slotRef().wholeIndex()==71),
                    "Watched natural parent/Robot/child have no pieces in the completed Main CPU; no selected-current or pixel claim");
            assertInstanceOf(SonicCdPastDisplay.Ready.class,module.act2SelectedDisplay().projection());
        }
        assertEquals(0x18E1B800,frame.state().motion().centreX16_16());assertEquals(0x0035A700,frame.state().motion().centreY16_16());
        assertEquals(6369,frame.state().motion().centreX16_16()>>>16);assertEquals(53,frame.state().motion().centreY16_16()>>>16);
        assertEquals(252,frame.environment().movement().globals().primaryAngle());
        var closed13450=captureNative(h);var wheelEndFull=registry.capture();var wheelEndCourse=registry.captureCourse();
        registry.restore(wheelEndFull);assertNativeCapture(closed13450,h);registry.restoreCourse(wheelEndCourse);assertNativeCapture(closed13450,h);
        System.out.println("Controller-only Act2 closed13450: genuine MovingSpring41 Init13423 allocates later ordinary Spring71, which runs in the same pass; Align through13442 then Main13443..13450 and WORD parent following retain exact full64 comparisons, original body receipts, completed Main CPU values and full/course replay. Endpoint Sonic6369/53, rings35, controls0, primary scratch252; wheel6694/903 and child6694/887 remain offscreen. Positive execution stops before separate failed13451 SwingingPlatform ID29/whole42. No wheel/child selected-current or pixel claim.");
    }

    // BEGIN later-route EGL case: additive to the three pinned controller cases.
    @Test void actualLaterAct2ControllerCutsRenderSelectedSonicAndReplayTheirCompleteValuesAndPixels() throws Exception {
        byte[] imageForExport;
        byte[] splashImageForExport=null;
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
            renderReplayNextRight(h,jump,12884);
            step(h,jump);assertLaterRouteOwners(h,frame,source,world,generation+1);
            assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TIME_STARS_INIT));
            renderReplayNextRight(h,jump,12886);
            imageForExport=renderReplayNextRight(h,jump,12887).clone();
            assertNaturalPowerupCurrent(h);
            for(int call=12888;call<=13061;call++) {
                step(h,(call-11214)%128<32?jump:right);assertLaterRouteOwners(h,frame,source,world,generation+1);
            }
            renderReplayNextRight(h,(13062-11214)%128<32?jump:right,13062);
            assertNaturalPowerupCurrent(h);
            for(int call=13063;call<=13097;call++) {
                var input=(call-11214)%128<32?jump:right;
                if(call==13063 || call==13095 || call==13096 || call==13097)replayNextRight(h,input);
                step(h,input);assertLaterRouteOwners(h,frame,source,world,generation+1);assertNaturalItemShieldCut(h,call);
            }
            imageForExport=renderReplayNextRight(h,(13098-11214)%128<32?jump:right,13098).clone();
            assertNaturalItemShieldCut(h,13098);assertNaturalShieldCurrent(h);
            for(int call=13099;call<=13125;call++) {
                var input=controller(call);
                if(call==13125)replayNextRight(h,input);
                step(h,input);assertLaterRouteOwners(h,frame,source,world,generation+1);assertNaturalItemShieldCut(h,call);
            }
            renderReplayNextRight(h,controller(13126),13126);
            assertNaturalItemShieldCut(h,13126);assertNaturalShieldCurrent(h);
            // The old13126 endpoint is retained with the new actual Left input, not its old pose.
            for(int call=13127;call<=13263;call++) {
                var input=controller(call);
                if(call==13195 || call==13199)replayNextRight(h,input);
                step(h,input);assertLaterRouteOwners(h,frame,source,world,generation+1);
            }
            // Fifteenth Sonic endpoint; retain imageForExport from the actual Shield cut13098.
            renderReplayNextRight(h,controller(13264),13264);
            assertEquals(5674,frame.state().motion().centreX16_16()>>>16);
            assertEquals(584,frame.state().motion().centreY16_16()>>>16);
            assertEquals(0,frame.state().motion().playerControl());
            assertTrue(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_BODY));
            assertFalse(module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_PLAYER));
            assertFalse(module.act2LoadState().sounds().stream().anyMatch(SonicCdGameModule.PastTunnelPathFm.class::isInstance));
            assertEquals(0, frame.environment().controls().controlLocked());
            // Two additive Sonic endpoints; imageForExport remains the old Shield13098 picture.
            assertEquals(23,module.act2LoadState().ring().rings());
            for(int call=13265;call<=13385;call++) {
                var input=controller(call);
                if(call==13366 || call==13385) {
                    var image=renderReplayNextRight(h,input,call);
                    if(call==13366)splashImageForExport=image.clone();
                } else {
                    if(call==13265 || call==13266 || call==13277 || call==13278 || call==13279
                            || call==13363 || call==13364 || call==13365 || call==13367
                            || call==13368 || call==13384)replayNextRight(h,input);
                    step(h,input);assertLaterRouteOwners(h,frame,source,world,generation+1);
                }
                assertLaterRouteOwners(h,frame,source,world,generation+1);
                assertNaturalDoorSplashCut(h,call);
            }
            assertNotNull(splashImageForExport);
            System.out.println("ENGINE controller-only Act2 EGL: all fifteen old Sonic endpoints retained at10778,10866,11033,11213,11341,11587,12670,12699,12884,12886,12887,13062,13098,13126,13264; selected Sonic/full-course value and pixel replay also at13366 and13385, seventeen retained endpoints. Existing Stars12887/Shield13098 omission contrasts remain. Selected natural Splash41 omission at13366 changes renderer-only pixels with planes/scroll/palette/current versions unchanged. Ring13265, Path acquisition/Got, emission/Init/control release and FC edges have before-forward full/course checkpoints. The complete13385 checkpoint remains before the additional Robot segment below.");
            // Only existing selected-Sonic Ready/omission rendering is extended; Robot visibility is not asserted.
            assertArrayEquals(new byte[64],module.act2LoadState().placement().pool().slots().get(47-32).copyBytes());
            byte[] expectedRobot47=java.util.HexFormat.of().parseHex("2d0404000020e4f619f000000160000000000000000020220422020000000000000000b902000000000000040020e73401600001000000000000000000000000");
            for(int call=13386;call<=13422;call++) {
                var input=controller(call);
                if(call==13386 || call==13422)renderReplayNextRight(h,input,call);
                else {
                    if(call==13387)replayNextRight(h,input);
                    step(h,input);
                }
                assertLaterRouteOwners(h,frame,source,world,generation+1);
                var robotState=module.act2LoadState();var robotCalls=robotState.calls();
                assertArrayEquals(expectedRobot47,robotState.placement().pool().slots().get(47-32).copyBytes(),
                        "Complete naturally initialized Robot47 image at closed"+call);
                assertEquals(1,robotCalls.stream().filter(c->c==SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY).count());
                assertTrue(robotCalls.indexOf(SonicCdGameModule.Act2LoadCall.PRIMARY_MAIN)
                        <robotCalls.indexOf(SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY));
                assertTrue(robotCalls.indexOf(SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY)
                        <robotCalls.indexOf(SonicCdGameModule.Act2LoadCall.DYNAMIC_OBJECTS));
                assertFalse(robotState.sounds().stream().anyMatch(SonicCdGameModule.PastRobotGeneratorFm.class::isInstance));
                assertEquals(call==13386,robotCalls.contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY));
                if(call==13386) {
                    assertArrayEquals(new byte[64],robotState.placement().pool().slots().get(41-32).copyBytes(),
                            "The same completed walk returns from Splash41 routine4 with all64 bytes deleted before Robot47");
                    assertTrue(robotCalls.indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY)
                            <robotCalls.indexOf(SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY));
                }
                assertEquals(1,frame.environment().movement().gates().timeZone(),"Robot reads actual raw timeZone1");
                assertEquals(1,frame.environment().movement().gates().zoneAct(),"Robot reads actual act1");
                assertEquals(255,frame.environment().movement().gates().timeWarpDirection());
                assertEquals(0,frame.state().motion().playerControl());assertEquals(0,frame.environment().controls().controlLocked());
            }
            assertEquals(0x18612800,frame.state().motion().centreX16_16());
            assertEquals(0x004B1700,frame.state().motion().centreY16_16());
            assertEquals(6241,frame.state().motion().centreX16_16()>>>16);assertEquals(75,frame.state().motion().centreY16_16()>>>16);
            assertEquals(35,module.act2LoadState().ring().rings());
            assertInstanceOf(SonicCdPastDisplay.Ready.class,module.act2SelectedDisplay().projection());
            System.out.println("ENGINE controller-only Act2 EGL: all seventeen old selected-Sonic endpoints remain, plus13386 and13422, nineteen retained endpoints with complete-value/full-course and pixel replay through the existing Ready/selected-Sonic omission path. Robot47 is checked by its exact native64 and one returned body receipt, with no Robot selected-current, pixel visibility or omission claim. Old Shield13098 and Splash13366 exports remain unchanged. The complete13422 checkpoint remains before the MovingSpring segment below.");
            // FINAL Root facts c55ba8b828de75be238e16830ee44273aa9b25ff133c7300db4590a65e4a2fc9; comparisons only.
            // Rows13423..13450: complete parent41/actual child71 images and completed Main CPU hash; never ingress.
            String[][] expectedMovingSpringCuts={
                {"0f04033800209a961a1a00000376000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036600000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","fee8ddf5172ef8b797169bfa4afb77e74e6170d16cd7c868178a8f2115f5d6c0"}, // 13423
                {"0f04033800209a961a1a00000377000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","60d9a2146d0c9497da22893c82d83abab2a7731cf19bd82f40339bc7a9de4542"}, // 13424
                {"0f04033800209a961a1a00000378000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036800000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","209bf359564f7ec913759575f4f5978b01a5c421ba5b624a6a39058fea4db115"}, // 13425
                {"0f04033800209a961a1a00000379000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036900000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","87d08f45ce37fefccc2f958b270feaa1b4f3a7a56c2180148bbe6ad6046af031"}, // 13426
                {"0f04033800209a961a1a0000037a000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036a00000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","f473020fac5fd48fa655ba841952ef4671103497f39e6d04fd3c84a52d6b6272"}, // 13427
                {"0f04033800209a961a1a0000037b000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036b00000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","935b91d5ac77db2aced4237fd5e5a56f13ffc01cb5c5be8d9880c62851947bc0"}, // 13428
                {"0f04033800209a961a1a0000037c000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036c00000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","fd0233eed5d0f680c6059e5833660fe2e42eb794b932a8e890ad622851dcfcbc"}, // 13429
                {"0f04033800209a961a1a0000037d000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036d00000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","9dea91588c0b13582b5ab682c80b4b25899fc747046453c380eed76422ce2b79"}, // 13430
                {"0f04033800209a961a1a0000037e000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036e00000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","bc6f9d419fb73c282e373ea174f802f1efcbb769eec6251fac4699dbdfdcf68e"}, // 13431
                {"0f04033800209a961a1a0000037f000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000036f00000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","9ecab959afe59e95fe4e30e79bf6dfb125000909a1ae9e019dab7e6330d83810"}, // 13432
                {"0f04033800209a961a1a00000380000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037000000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","bfa16dac9057d035474593969e8049eea5406761863b856b108e2c77b0d6745d"}, // 13433
                {"0f04033800209a961a1a00000381000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037100000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","e1dade45216cce41c946d3efa1b2b5db150c6c036193628f482097c569015017"}, // 13434
                {"0f04033800209a961a1a00000382000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037200000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","47e3cc996e751cf47b8d0412b1ee0a0fba46cb10eae0f2485cf8520e2fddc1b1"}, // 13435
                {"0f04033800209a961a1a00000383000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037300000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","68d1ef1bc26b92f14a8c194f63059e5d150d25b1700382a1249c9b0cd108c061"}, // 13436
                {"0f04033800209a961a1a00000384000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037400000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","cf6ed8423e436d18985d6575e284895adb193c19e4886c70bf09d1e0cf2131a7"}, // 13437
                {"0f04033800209a961a1a00000385000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037500000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","ad4cd2dcb1fcb472436537f7a608115131199792c968b9b515ed0ca7dfb72ffa"}, // 13438
                {"0f04033800209a961a1a00000386000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037600000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","e1a941e6c3ee11a76741977135d6e7e9c41e5385e72a867b7f2b27fd3ee5e082"}, // 13439
                {"0f04033800209a961a1a00000387000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","9ece44a5f07e1862f978297f3e0d95b119d9e4d6b0c37b8ff1e2b511462a4f8a"}, // 13440
                {"0f04033800209a961a1a00000388000001800000000007000408000000000000000000000200000000000000000000000000000000001a1a0000000000000000","0a040520002099c81a1a0000037800000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","41345faaf7b5d446774088284d5a9dc7400def1a3066a953ba456b5ed987982a"}, // 13441
                {"0f04033800209a961a1a00000387000001800000000007000408000000000000000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a1a0000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","fa7d12214e7af80f27781f39dcbed44429574ba55c0409f34d23417c86181a58"}, // 13442
                {"0f04033800209a961a1b80000387000001800000000007000408000100000800000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a1b0000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","0c26b115f14ced1b5f9b772b895dc99cdef8356d3d14e65cb592e6ef7ed50f0d"}, // 13443
                {"0f04033800209a961a1d00000387000001800000000007000408000100000700000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a1d0000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","8cdd8f5462479aecf7a23480c094bc66a4816a6f0f6bfc395ff0cccce7a05079"}, // 13444
                {"0f04033800209a961a1e80000387000001800000000007000408000100000600000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a1e0000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","8cc6a4091175b5a6c62bd5f516738718c0858f48c56574d5cf93a6c2b50cf13e"}, // 13445
                {"0f04033800209a961a2000000387000001800000000007000408000100000500000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a200000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","4eefff6cf0ff61fb98265e3f4efd89573c25b4a2d25fb87bd8ff45d780ef5118"}, // 13446
                {"0f04033800209a961a2180000387000001800000000007000408000100000400000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a210000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","ec85adac06e09db945fd89fe4398024fde4e89f713979268e7902fbb3c70da0c"}, // 13447
                {"0f04033800209a961a2300000387000001800000000007000408000100000300000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a230000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","799e8833275535457440bf6d4a7e3e170ed4712f3b0413b75760f6cb3869c67a"}, // 13448
                {"0f04033800209a961a2480000387000001800000000007000408000100000200000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a240000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","c598b9734dcb2224780fd2e3216742e5183cadbd787cfcf943516ae910810646"}, // 13449
                {"0f04033800209a961a2600000387000001800000000007000408000100000100000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a260000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","e93f638f76fa87505e09bcc608e67131c3cd4d717ceeb653a02e5615f0d33e92"}, // 13450
            };
            // Original ledger indices: wheel, Robot, later child(last Spring), Dynamic, Main return, Spring count, CPU pieces, primary scratch.
            int[][] expectedMovingSpringReceipts={
                {31,37,67,72,80,6,27,4}, // 13423
                {31,36,58,61,69,5,26,4}, // 13424
                {31,36,58,61,69,5,37,4}, // 13425
                {31,36,58,61,69,5,34,4}, // 13426
                {31,36,58,61,69,5,25,4}, // 13427
                {31,36,58,61,69,5,34,4}, // 13428
                {31,36,58,61,69,5,29,4}, // 13429
                {31,36,58,61,69,5,26,4}, // 13430
                {31,36,58,61,69,5,37,4}, // 13431
                {31,36,58,61,69,5,34,4}, // 13432
                {31,36,58,61,69,5,23,4}, // 13433
                {31,36,58,61,69,5,38,4}, // 13434
                {29,34,56,59,67,5,29,4}, // 13435
                {29,34,56,59,67,5,26,4}, // 13436
                {29,34,56,59,67,5,37,4}, // 13437
                {29,34,56,59,67,5,30,4}, // 13438
                {29,34,56,59,67,5,25,4}, // 13439
                {29,34,56,59,67,5,38,4}, // 13440
                {29,34,56,59,67,5,29,4}, // 13441
                {29,34,56,59,67,5,26,4}, // 13442
                {29,34,56,59,67,5,23,4}, // 13443
                {29,34,56,59,67,5,38,4}, // 13444
                {29,34,56,59,67,5,33,4}, // 13445
                {29,34,56,59,67,5,26,4}, // 13446
                {29,34,56,59,67,5,37,252}, // 13447
                {29,34,56,59,67,5,26,252}, // 13448
                {31,36,58,61,69,5,25,252}, // 13449
                {31,36,58,61,69,5,38,252}, // 13450
            };
            assertArrayEquals(new byte[64],module.act2LoadState().placement().pool().slots().get(41-32).copyBytes());
            assertArrayEquals(new byte[64],module.act2LoadState().placement().pool().slots().get(71-32).copyBytes(),
                    "The actual later child71 was a complete zero image at closed13422, before real allocation");
            for(int call=13423;call<=13450;call++) {
                var input=controller(call);
                if(call==13423 || call==13450)renderReplayNextRight(h,input,call);
                else {
                    if(call==13424 || call==13442 || call==13443)replayNextRight(h,input);
                    step(h,input);
                }
                assertLaterRouteOwners(h,frame,source,world,generation+1);
                int row=call-13423;var movingState=module.act2LoadState();var movingCalls=movingState.calls();
                var movingParent=movingState.placement().pool().slots().get(41-32);var movingChild=movingState.placement().pool().slots().get(71-32);
                assertArrayEquals(java.util.HexFormat.of().parseHex(expectedMovingSpringCuts[row][0]),movingParent.copyBytes(),"Complete actual parent41 at closed"+call);
                assertArrayEquals(java.util.HexFormat.of().parseHex(expectedMovingSpringCuts[row][1]),movingChild.copyBytes(),"Complete actual child71 at closed"+call);
                assertArrayEquals(expectedRobot47,movingState.placement().pool().slots().get(47-32).copyBytes(),"Existing Robot47 native nonwrites");
                assertEquals(0x0F,movingParent.id());assertEquals(call<13442?2:4,movingParent.routine());
                assertEquals(call<13442?886+row:903,movingParent.centreY());
                assertEquals(call<=13442?6682<<16:(6682<<16)+(call-13442)*0x18000,movingParent.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.X_POSITION));
                assertEquals(0x180,movingParent.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.X_VELOCITY));
                assertEquals(0x0A,movingChild.id());assertEquals(2,movingChild.routine(),"Actual later child already ran ordinary Spring Init in the allocation pass");
                assertEquals(movingParent.centreX(),movingChild.centreX());assertEquals(movingParent.centreY()-0x10,movingChild.centreY());
                assertEquals(0,movingChild.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.X_FRACTION));
                assertEquals(0,movingChild.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.Y_FRACTION));
                assertEquals(1,movingCalls.stream().filter(c->c==SonicCdGameModule.Act2LoadCall.MOVING_SPRING_BODY).count());
                assertEquals(expectedMovingSpringReceipts[row][0],movingCalls.indexOf(SonicCdGameModule.Act2LoadCall.MOVING_SPRING_BODY));
                assertEquals(expectedMovingSpringReceipts[row][1],movingCalls.indexOf(SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY));
                assertEquals(expectedMovingSpringReceipts[row][2],movingCalls.lastIndexOf(SonicCdGameModule.Act2LoadCall.SPRING_BODY));
                assertEquals(expectedMovingSpringReceipts[row][3],movingCalls.indexOf(SonicCdGameModule.Act2LoadCall.DYNAMIC_OBJECTS));
                assertEquals(expectedMovingSpringReceipts[row][4],movingCalls.indexOf(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN));
                assertEquals(expectedMovingSpringReceipts[row][5],movingCalls.stream().filter(c->c==SonicCdGameModule.Act2LoadCall.SPRING_BODY).count());
                assertTrue(expectedMovingSpringReceipts[row][0]<expectedMovingSpringReceipts[row][1]
                        && expectedMovingSpringReceipts[row][1]<expectedMovingSpringReceipts[row][2]
                        && expectedMovingSpringReceipts[row][2]<expectedMovingSpringReceipts[row][3]);
                assertEquals(new com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.Status(2,6144,338,303,189,173,0,0),movingState.placement().status());
                assertEquals(expectedMovingSpringReceipts[row][7],frame.environment().movement().globals().primaryAngle());
                assertEquals(0,movingState.ring().timeStopTimer());assertEquals(35,movingState.ring().rings());
                assertEquals(1,frame.environment().movement().gates().timeZone());assertEquals(1,frame.environment().movement().gates().zoneAct());
                assertEquals(0,frame.state().motion().playerControl());assertEquals(0,frame.environment().controls().controlLocked());
                var movingLoop=actualField(actualField(actualField(module,"act2Loading"),"values"),"loop");
                var movingCpu=assertInstanceOf(com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat.class,
                        ((java.util.Optional<?>)actualField(movingLoop,"completedDraw")).orElseThrow());
                assertEquals(640,movingCpu.bytes().length);assertEquals(expectedMovingSpringReceipts[row][6],movingCpu.spriteCount());
                assertEquals(expectedMovingSpringCuts[row][2],java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(movingCpu.bytes())));
                assertTrue(movingCpu.provenance().stream().noneMatch(p->p.slotRef().wholeIndex()==41 || p.slotRef().wholeIndex()==47 || p.slotRef().wholeIndex()==71),
                        "Watched natural parent/Robot/child have no pieces in the completed Main CPU; no selected-current or pixel claim");
                assertInstanceOf(SonicCdPastDisplay.Ready.class,module.act2SelectedDisplay().projection());
            }
            assertEquals(0x18E1B800,frame.state().motion().centreX16_16());assertEquals(0x0035A700,frame.state().motion().centreY16_16());
            assertEquals(6369,frame.state().motion().centreX16_16()>>>16);assertEquals(53,frame.state().motion().centreY16_16()>>>16);
            assertEquals(252,frame.environment().movement().globals().primaryAngle());
            System.out.println("ENGINE controller-only Act2 EGL: all nineteen old selected-Sonic endpoints remain, plus13423 and13450, twenty-one total through the unchanged genuine Ready/selected-Sonic omission and full/course before/after value/pixel replay helper. Natural MovingSpring41/child71 full64, body receipts, field sequence and completed Main CPU hashes are comparison-only; both remain offscreen and no wheel/child selected-current or pixel claim is made. Old Shield/Splash contrasts and exports are unchanged. Positive execution stops at closed13450 before the separate failed13451 SwingingPlatform ID29/whole42 prefix. No native-image, regional or whole-act claim.");
        } finally { fixture.cleanup(); }
        writeOptionalLaterCapture(imageForExport);
        writeOptionalSplashCapture(splashImageForExport);
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
        if(provePrimary && completedCall==12887) {
            var omitted=omitSelectedTimeStars(scene,cpu);
            assertSame(scene.foreground(),omitted.foreground());assertSame(scene.background(),omitted.background());
            assertArrayEquals(scene.packedHscroll256(),omitted.packedHscroll256());
            assertEquals(scene.vscrollLongBits(),omitted.vscrollLongBits());
            assertArrayEquals(scene.selectedPalette128(),omitted.selectedPalette128());
            assertEquals(scene.nativePatterns(),omitted.nativePatterns());
            var camera=h.mode().getCamera();clearPixels();
            renderer.draw(omitted,camera.getX(),camera.getY(),module::validateAct2LoadingCut);
            assertFalse(Arrays.equals(actual,pixels()),"Actually selected natural TimeStars contribute visible engine pixels");
            assertNativeCapture(before,h);assertArrayEquals(actual,draw(h));assertNativeCapture(before,h);
        }
        if(provePrimary && completedCall==13098) {
            assertTrue(cpu.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex()==6));
            var omitted=TestSonicCdPastResultsRoute.omitWhole(scene,cpu,6);
            assertSame(scene.foreground(),omitted.foreground());assertSame(scene.background(),omitted.background());
            assertArrayEquals(scene.packedHscroll256(),omitted.packedHscroll256());
            assertEquals(scene.vscrollLongBits(),omitted.vscrollLongBits());
            assertArrayEquals(scene.selectedPalette128(),omitted.selectedPalette128());
            assertEquals(scene.nativePatterns(),omitted.nativePatterns());
            var camera=h.mode().getCamera();clearPixels();
            renderer.draw(omitted,camera.getX(),camera.getY(),module::validateAct2LoadingCut);
            assertFalse(Arrays.equals(actual,pixels()),"Actually selected natural Shield contributes engine pixels");
            assertNativeCapture(before,h);assertArrayEquals(actual,draw(h));assertNativeCapture(before,h);
        }
        if(provePrimary && completedCall==13366) {
            assertEquals(java.util.List.of(0,1),cpu.provenance().stream()
                    .filter(p->p.slotRef().wholeIndex()==41).map(p->p.mappingPieceOrdinal()).toList(),
                    "The selected prior CPU contains the two actual Splash41 mapping pieces only after Init");
            var omitted=TestSonicCdPastResultsRoute.omitWhole(scene,cpu,41);
            assertSame(scene.foreground(),omitted.foreground());assertSame(scene.background(),omitted.background());
            assertArrayEquals(scene.packedHscroll256(),omitted.packedHscroll256());
            assertEquals(scene.vscrollLongBits(),omitted.vscrollLongBits());
            assertArrayEquals(scene.selectedPalette128(),omitted.selectedPalette128());
            assertEquals(scene.displayEnabled(),omitted.displayEnabled());
            assertEquals(scene.nativePatterns(),omitted.nativePatterns());
            assertEquals(scene.sprites().patternVersions(),omitted.sprites().patternVersions());
            var camera=h.mode().getCamera();clearPixels();
            renderer.draw(omitted,camera.getX(),camera.getY(),module::validateAct2LoadingCut);
            assertFalse(Arrays.equals(actual,pixels()),"Actually selected natural Splash41 contributes engine pixels at13366");
            assertNativeCapture(before,h);assertArrayEquals(actual,draw(h));assertNativeCapture(before,h);
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

    /** Actual captured CPU/current values; these expectations are comparison-only, never ingress. */
    private static void assertNaturalPowerupCurrent(Harness h) throws Exception {
        var observed=h.module().act2LoadState();
        var values=actualField(actualField(h.module(),"act2Loading"),"values");
        var optional=(java.util.Optional<?>)actualField(actualField(values,"loop"),"powerupCpu");
        var cpu=assertInstanceOf(com.openggf.game.soniccd.level.CdAssetBytes.class,optional.orElseThrow());
        assertEquals(1152,cpu.length());assertEquals(6,observed.level().powerup());
        assertArrayEquals(observed.assets().objects().powerupArtSources().copy(2).copy(),cpu.slice(0,1024).copy());
        assertArrayEquals(new byte[128],cpu.slice(1024,128).copy(),"Real Main clear's unwritten native CPU tail");
        for(int at=0;at<1152;at+=32)assertArrayEquals(cpu.slice(at,32).copy(),
                com.openggf.game.soniccd.resources.CdR12aCameraArtResidency.readTile(observed.section().residency(),(0xA880+at)>>>5).copy());
    }

    /** Measured controller cuts; only comparisons, no diagnostic value supplies gameplay state. */
    private static void assertNaturalItemShieldCut(Harness h,int completedCall) throws Exception {
        var state=h.module().act2LoadState();var item=state.placement().pool().slots().get(0);
        if(completedCall==13063) {
            assertTrue(state.calls().contains(SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_BODY));
            assertEquals(0x1A,item.id());assertEquals(2,item.routine());assertEquals(0x20A8CB,item.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));
            assertEquals(-0x2E8,item.signedWord(com.openggf.game.soniccd.objects.CdObjectState.WordField.Y_VELOCITY));
        }
        if(completedCall==13095) {
            assertTrue(state.calls().contains(SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_SHIELD_AWARD));
            assertEquals(4,item.routine());assertEquals(29,item.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.MONITOR_ITEM_DELETE));
            byte[] expected=new byte[64];expected[0]=3;assertArrayEquals(expected,state.reservedObjects().slice(5*64,64).copy());
            assertFalse(state.calls().contains(SonicCdGameModule.Act2LoadCall.SHIELD_BODY));
            assertEquals(1,state.sounds().stream().filter(SonicCdGameModule.PastMonitorItemShieldFm.class::isInstance).count());
            assertNaturalPowerupCurrent(h);
        }
        if(completedCall==13096) {
            assertTrue(state.calls().contains(SonicCdGameModule.Act2LoadCall.SHIELD_INIT));
            assertFalse(state.calls().contains(SonicCdGameModule.Act2LoadCall.SHIELD_ACTIVE));
            assertEquals(2,state.reservedObjects().unsignedByte(5*64+0x24));
            assertEquals(0x21D528,state.reservedObjects().unsignedLong(5*64+4));
            assertTrue(h.module().act2SelectedDisplay().inputs().selectedCpu().orElseThrow().provenance().stream().noneMatch(p->p.slotRef().wholeIndex()==6));
        }
        if(completedCall==13097) {
            assertTrue(state.calls().contains(SonicCdGameModule.Act2LoadCall.SHIELD_ACTIVE));assertEquals(0x82,state.level().powerup());
            assertTrue(h.module().act2SelectedDisplay().inputs().selectedCpu().orElseThrow().provenance().stream().noneMatch(p->p.slotRef().wholeIndex()==6));
        }
        if(completedCall==13098) {
            assertNaturalShieldCurrent(h);
            assertEquals(4,h.module().act2SelectedDisplay().inputs().selectedCpu().orElseThrow().provenance().stream().filter(p->p.slotRef().wholeIndex()==6).count());
        }
        if(completedCall==13125) {
            assertTrue(state.calls().contains(SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_BODY));
            assertArrayEquals(new byte[64],item.copyBytes(),"Actual signed deletion countdown and ID0 wrapper Draw");
        }
        if(completedCall==13126)assertFalse(state.calls().contains(SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_BODY));
    }

    /**
     * Comparison-only closed observations supplied by Root (facts SHA-256
     * f33cf98df54588011030c7992a95b199603de9ed267fb1c88af67dd925e24d4e).
     * N1ecc5f3: Tunnel Path/Main.asm:200-253,257-299; Main (Door).asm:23-38,149-177;
     * Animations (Door Splash).asm:1-9. No expected byte is an input or restore source.
     */
    private static void assertNaturalDoorSplashCut(Harness h,int completedCall) {
        assertTrue(completedCall>=13265 && completedCall<=13385,"Only measured completed cuts are positive endpoints");
        var module=h.module();var state=module.act2LoadState();var frame=module.requirePlayerFrame();
        var calls=state.calls();var pool=state.placement().pool();
        assertEquals(33,state.ring().rings());assertEquals(3,state.ring().lives());assertEquals(0,state.ring().livesFlags());
        assertEquals(0,frame.environment().controls().controlLocked());
        assertEquals(completedCall>=13277 && completedCall<=13366?0x81:0,frame.state().motion().playerControl());
        assertEquals(completedCall==13265,calls.contains(SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_RING_AWARD));
        assertEquals(completedCall>=13277 && completedCall<=13367,calls.contains(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_PLAYER));
        assertEquals(completedCall==13277 || completedCall==13278,calls.contains(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_FM));
        assertEquals(completedCall>=13365,calls.contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY));
        assertEquals(completedCall==13365,calls.contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_FM));
        assertEquals(completedCall==13265?java.util.List.of(com.openggf.game.soniccd.objects.CdMonitorItem.RingAwardSound.FM_RING):java.util.List.of(),
                state.sounds().stream().filter(SonicCdGameModule.PastMonitorItemRing.class::isInstance)
                        .map(SonicCdGameModule.PastMonitorItemRing.class::cast).map(SonicCdGameModule.PastMonitorItemRing::sound).toList());
        assertEquals(completedCall==13277 || completedCall==13278?java.util.List.of(com.openggf.game.soniccd.objects.CdTunnelPath.FmSound.FM_91):java.util.List.of(),
                state.sounds().stream().filter(SonicCdGameModule.PastTunnelPathFm.class::isInstance)
                        .map(SonicCdGameModule.PastTunnelPathFm.class::cast).map(SonicCdGameModule.PastTunnelPathFm::sound).toList());
        assertEquals(completedCall==13365?java.util.List.of(com.openggf.game.soniccd.objects.CdTunnelDoorSplash.FmSound.FM_A2):java.util.List.of(),
                state.sounds().stream().filter(SonicCdGameModule.PastTunnelDoorSplashFm.class::isInstance)
                        .map(SonicCdGameModule.PastTunnelDoorSplashFm.class::cast).map(SonicCdGameModule.PastTunnelDoorSplashFm::sound).toList());
        if(completedCall==13265) {
            assertEquals(1,state.ring().updateHudRings(),"Actual ten-ring reward changes23 to33");
            assertTrue(calls.indexOf(SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_RING_AWARD)
                    <calls.indexOf(SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_BODY));
        }
        if(completedCall==13277 || completedCall==13278)assertTrue(calls.indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_FM)
                <calls.indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_PATH_BODY));
        if(completedCall==13365)assertTrue(calls.indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_FM)
                <calls.indexOf(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY));
        String pathImage=switch(completedCall) {
            case 13265 -> "070405410021d5281630000002900000000000000000000001100000000000000000000002000000020000000000000000000000000016300290004400208c98";
            case 13276 -> "070405410021d5281630000002900000000000000000000001100000000000000000000002000000020000000000000000000000000016300290004400208c98";
            case 13277 -> "070405410021d5281630000002900000000000000000000001100000000000000000000004000000020000000000000000000000000016300290004400208c98";
            case 13278 -> "070405410021d5281630000002900000000000000000000001100000000000000000000006000000020000000000000000000000000016300290004400208c98";
            case 13279 -> "070405410021d5281630000002900000000000000000000001100000000000000000000006000000020000000000088000000000000016300318044400208c98";
            case 13363 -> "070405410021d5281630000002900000000000000000000001100000000000000000000006000000020000000000038000000000000017d00268404400208c98";
            case 13364 -> "070405410021d5281630000002900000000000000000000001100000000000000000000006000000020000000000028000000000000017d00268404400208c98";
            case 13365 -> "070405410021d5281630000002900000000000000000000001100000000000000000000006000000020000000000018000000000000017d00268404400208c98";
            case 13366 -> "070405410021d5281630000002900000000000000000000001100000000000000000000006000000020000000000008000000000000017d00268404400208c98";
            case 13367 -> "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
            default -> null;
        };
        if(pathImage!=null)assertArrayEquals(java.util.HexFormat.of().parseHex(pathImage),pool.slots().get(79-32).copyBytes(),
                "Complete natural Path79 image at closed"+completedCall);
        String splashImage=switch(completedCall) {
            case 13363 -> "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
            case 13364 -> "0b0000000000000017d0000002a30000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
            case 13365 -> "0b8403a70021d65217d0000002a30000000000000000000001000001000003000000000002000000000000000000000000000000000000000000000000000000";
            case 13366 -> "0b8403a70021d65217d0000002a30000000000000000000001000001000002000000000002000000000000000000000000000000000000000000000000000000";
            case 13367 -> "0b8403a70021d65217d0000002a30000000000000000000001000001000001000000000002000000000000000000000000000000000000000000000000000000";
            case 13368 -> "0b8403a70021d65217d0000002a30000000000000000000001000001000000000000000002000000000000000000000000000000000000000000000000000000";
            case 13369 -> "0b8403a70021d65217d0000002a30000000000000000000001000402000003000000000002000000000000000000000000000000000000000000000000000000";
            case 13370 -> "0b8403a70021d65217d0000002a30000000000000000000001000402000002000000000002000000000000000000000000000000000000000000000000000000";
            case 13371 -> "0b8403a70021d65217d0000002a30000000000000000000001000402000001000000000002000000000000000000000000000000000000000000000000000000";
            case 13372 -> "0b8403a70021d65217d0000002a30000000000000000000001000402000000000000000002000000000000000000000000000000000000000000000000000000";
            case 13373 -> "0b8403a70021d65217d0000002a30000000000000000000001000303000003000000000002000000000000000000000000000000000000000000000000000000";
            case 13374 -> "0b8403a70021d65217d0000002a30000000000000000000001000303000002000000000002000000000000000000000000000000000000000000000000000000";
            case 13375 -> "0b0403a70021d65217d0000002a30000000000000000000001000303000001000000000002000000000000000000000000000000000000000000000000000000";
            case 13376 -> "0b0403a70021d65217d0000002a30000000000000000000001000303000000000000000002000000000000000000000000000000000000000000000000000000";
            case 13377 -> "0b0403a70021d65217d0000002a30000000000000000000001000104000003000000000002000000000000000000000000000000000000000000000000000000";
            case 13378 -> "0b0403a70021d65217d0000002a30000000000000000000001000104000002000000000002000000000000000000000000000000000000000000000000000000";
            case 13379 -> "0b0403a70021d65217d0000002a30000000000000000000001000104000001000000000002000000000000000000000000000000000000000000000000000000";
            case 13380 -> "0b0403a70021d65217d0000002a30000000000000000000001000104000000000000000002000000000000000000000000000000000000000000000000000000";
            case 13381 -> "0b0403a70021d65217d0000002a30000000000000000000001000205000003000000000002000000000000000000000000000000000000000000000000000000";
            case 13382 -> "0b0403a70021d65217d0000002a30000000000000000000001000205000002000000000002000000000000000000000000000000000000000000000000000000";
            case 13383 -> "0b0403a70021d65217d0000002a30000000000000000000001000205000001000000000002000000000000000000000000000000000000000000000000000000";
            case 13384 -> "0b0403a70021d65217d0000002a30000000000000000000001000205000000000000000002000000000000000000000000000000000000000000000000000000";
            case 13385 -> "0b0403a70021d65217d0000002a30000000000000000000001000205000003000000000004000000000000000000000000000000000000000000000000000000";
            default -> null;
        };
        var splash=pool.slots().get(41-32);
        if(splashImage!=null)assertArrayEquals(java.util.HexFormat.of().parseHex(splashImage),splash.copyBytes(),
                "Complete natural Splash41 image at closed"+completedCall);
        if(completedCall==13364) {
            var door=pool.slots().get(67-32);
            assertEquals(0x0D,door.id());assertEquals(4,door.routine());
            assertEquals(0,splash.routine(),"Door67 emits into the already visited earlier41; Init waits for the next physical pass");
            assertFalse(calls.contains(SonicCdGameModule.Act2LoadCall.TUNNEL_DOOR_SPLASH_BODY));
        }
        if(completedCall>=13365) {
            int[] expectedFlags={0x84,0x84,0x84,0x84,0x84,0x84,0x84,0x84,0x84,0x84,0x04,0x04,0x04,0x04,0x04,0x04,0x04,0x04,0x04,0x04,0x04};
            assertEquals(expectedFlags[completedCall-13365],splash.spriteFlags(),"Actual Draw/BSET script at closed"+completedCall);
            assertEquals(0x0B,splash.id());assertEquals(0x03A7,splash.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE));
            assertEquals(0x21D652,splash.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));
            var selected=module.act2SelectedDisplay().inputs().selectedCpu().orElseThrow();
            java.util.List<Integer> expectedPieces=completedCall==13365 || completedCall>=13376?java.util.List.of()
                    :completedCall<13370?java.util.List.of(0,1):java.util.List.of(0,1,2,3);
            assertEquals(expectedPieces,selected.provenance().stream().filter(p->p.slotRef().wholeIndex()==41)
                    .map(p->p.mappingPieceOrdinal()).toList(),"Actual prior CPU selection at closed"+completedCall);
        }
        if(completedCall==13385) {
            assertEquals(4,splash.routine(),"FC advances the routine but still returns through Draw; deletion is a later dispatch");
            assertEquals(0x17E0F900,frame.state().motion().centreX16_16());
            assertEquals(0x016E7700,frame.state().motion().centreY16_16());
            assertEquals(6112,frame.state().motion().centreX16_16()>>>16);assertEquals(366,frame.state().motion().centreY16_16()>>>16);
        }
    }

    /** Native selector2 copies source0 only at the subsequent actual Vint, preserving the real128-byte tail. */
    private static void assertNaturalShieldCurrent(Harness h) throws Exception {
        var state=h.module().act2LoadState();var values=actualField(actualField(h.module(),"act2Loading"),"values");
        var cpu=assertInstanceOf(com.openggf.game.soniccd.level.CdAssetBytes.class,((java.util.Optional<?>)actualField(actualField(values,"loop"),"powerupCpu")).orElseThrow());
        var contact=assertInstanceOf(com.openggf.game.soniccd.player.CdPlayerBody.ContactGlobals.class,actualField(actualField(values,"globals"),"contact"));
        assertEquals(1,contact.shield());assertEquals(2,state.level().powerup());assertEquals(3,state.reservedObjects().unsignedByte(5*64));
        assertEquals(1152,cpu.length());assertArrayEquals(state.assets().objects().powerupArtSources().copy(0).copy(),cpu.slice(0,1024).copy());
        assertArrayEquals(new byte[128],cpu.slice(1024,128).copy());
        for(int at=0;at<1152;at+=32)assertArrayEquals(cpu.slice(at,32).copy(),com.openggf.game.soniccd.resources.CdR12aCameraArtResidency.readTile(state.section().residency(),(0xA880+at)>>>5).copy());
    }

    /** Omit only actually selected reserved12..15 tiles; preserve all native ordering and other layers. */
    private static RetainedPlaneSceneRenderer.Scene omitSelectedTimeStars(RetainedPlaneSceneRenderer.Scene scene,
            com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat cpu) {
        byte[] raw=cpu.bytes();var tiles=new java.util.ArrayList<SpritePresentation.Tile>();int at=0;
        for(int index=cpu.spriteCount()-1;index>=0;index--) {
            int size=raw[index*8+2]&15,count=(((size>>>2)&3)+1)*((size&3)+1);
            int whole=cpu.provenance().get(index).slotRef().wholeIndex();
            if(whole<12 || whole>=16)tiles.addAll(scene.sprites().tiles().subList(at,at+count));
            at+=count;
        }
        assertEquals(scene.sprites().tiles().size(),at);assertTrue(tiles.size()<at,"Selected native SAT contains TimeStars");
        var picture=new SpritePresentation.Frame(tiles,java.util.List.of(),scene.sprites().patternVersions());
        return new RetainedPlaneSceneRenderer.Scene(scene.foreground(),scene.background(),scene.packedHscroll256(),
                scene.vscrollLongBits(),scene.selectedPalette128(),scene.displayEnabled(),picture,scene.nativePatterns());
    }

    /** Executed only after module/session/graphics/context/boot fixture cleanup; never a test input. */
    private static void writeOptionalLaterCapture(byte[] rgba) throws Exception {
        String output = System.getenv("SONICCD_ACT2_ROUTE_OUTPUT"); if (output == null || output.isBlank()) return;
        var directory = Path.of(output).toAbsolutePath().normalize();
        var owned = Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "captures", "engine").toAbsolutePath().normalize();
        assertTrue(directory.startsWith(owned) && !directory.equals(owned), "Actual ENGINE route export needs its own directory under the engine capture root");
        assertEquals(320 * 224 * 4, rgba.length); Files.createDirectories(directory);
        writeEngineCapturePng(rgba,directory.resolve("ENGINE-act2-controller-call-13098.png"));
        Files.writeString(directory.resolve("ENGINE-act2-controller-call-13098.txt"),
                "ENGINE: OpenGGF original-USA controller-only route, actual completed call13098.\n"
                + "320x224 RGBA from the actual selected prior CPU/current Act2 display on configured surfaceless EGL.\n"
                + "Full/course complete-value and pixel replay; selected Sonic and natural Shield renderer-only omission contrasts; earlier12887 Stars contrast retained.\n"
                + "Written after module/session/graphics/context/boot fixture cleanup. No native-image or parity claim.\n");
    }
    /** Both optional engine exports use the old bottom-up RGBA conversion after every live owner closes. */
    private static void writeEngineCapturePng(byte[] rgba,Path output) throws Exception {
        assertEquals(320*224*4,rgba.length);
        var image = new java.awt.image.BufferedImage(320, 224, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
            int at = ((223 - y) * 320 + x) * 4;
            int argb = (rgba[at + 3] & 255) << 24 | (rgba[at] & 255) << 16 | (rgba[at + 1] & 255) << 8 | rgba[at + 2] & 255;
            image.setRGB(x, y, argb);
        }
        if (!javax.imageio.ImageIO.write(image,"png",output.toFile()))throw new java.io.IOException("PNG writer unavailable");
    }

    /** A separate opt-in, executed after session/module/graphics/EGL/boot fixture cleanup; output only. */
    private static void writeOptionalSplashCapture(byte[] rgba) throws Exception {
        String output=System.getenv("SONICCD_ACT2_SPLASH_OUTPUT");if(output==null || output.isBlank())return;
        var directory=Path.of(output).toAbsolutePath().normalize();
        var owned=Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD","captures","engine").toAbsolutePath().normalize();
        assertTrue(directory.startsWith(owned) && !directory.equals(owned),"Actual ENGINE Splash export needs its own directory under the engine capture root");
        String shieldOutput=System.getenv("SONICCD_ACT2_ROUTE_OUTPUT");
        if(shieldOutput!=null && !shieldOutput.isBlank())assertNotEquals(Path.of(shieldOutput).toAbsolutePath().normalize(),directory,
                "Splash and the existing Shield export use separate directories");
        assertEquals(320*224*4,rgba.length);Files.createDirectories(directory);
        writeEngineCapturePng(rgba,directory.resolve("ENGINE-act2-controller-call-13366.png"));
        Files.writeString(directory.resolve("ENGINE-act2-controller-call-13366.txt"),
                "ENGINE: OpenGGF original-USA controller-only route, actual completed call13366.\n"
                + "320x224 RGBA from actual selected prior CPU/current Act2 display on configured surfaceless EGL.\n"
                + "Selected Sonic and natural Splash41 renderer-only omission; full/course complete-value and pixel replay.\n"
                + "Written after module/session/graphics/EGL/boot fixture cleanup. No native-image, parity or atlas-retirement claim.\n"
                + "Positive route ends at closed13385;13386 is outside this test and has no completed deletion claim.\n");
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
        if (call < 1 || call > 13550) throw new IllegalArgumentException("Fixed ordinary controller recipe is1..13550; positive qualification stops13385");
        if (call > 10419) {
            // Exact old prefix through13098; then Left96, Left+B4, Left96, Right256.
            // Positive tests stop at completed13385; calls13386..13550 remain recipe only.
            int directions = call >= 13099 && call <= 13294 ? 4 : 8;
            int actions = call <= 13098 ? (call >= 11214 && (call - 11214) % 128 < 32 ? 2 : 0)
                    : (call >= 13195 && call <= 13198 ? 2 : 0);
            return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions, 0, actions, 0, false, false),
                    PlayerInputState.neutral());
        }
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

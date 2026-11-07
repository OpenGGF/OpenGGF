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

    @Test void actualEglNaturalR13dLoadingSelectsVisibleSonicAndReplays() throws Exception {
        String futureMediaRoot=System.getProperty("soniccd.media.root");
        org.junit.jupiter.api.Assumptions.assumeTrue(futureMediaRoot!=null&&!futureMediaRoot.isBlank(),"Optional configured USA CD media absent");
        assertTrue(Files.isDirectory(Path.of(futureMediaRoot).resolve("USA")),"Configured USA CD media root must exist; no cached-source fallback");
        byte[] futureImageForExport=null;int futureVisibleCallForExport=-1;
        byte[] bossImageForExport=null;String bossSelectionForExport=null;
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()) {
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
        System.out.println("Controller-only Act2 closed13450: genuine MovingSpring41 Init13423 allocates later ordinary Spring71, which runs in the same pass; Align through13442 then Main13443..13450 and WORD parent following retain exact full64 comparisons, original body receipts, completed Main CPU values and full/course replay. Endpoint Sonic6369/53, rings35, controls0, primary scratch252; wheel6694/903 and child6694/887 remain offscreen. This complete13450 checkpoint precedes the measured Swing segment below. No wheel/child selected-current or pixel claim.");
        // BEGIN measured Swing promotion: source-only comparisons; Root facts4799c82e6634ff00dca32fbeceeab83b9fe59c5c8168bf93d5ece82b57d4f408.
        // N1ecc5f3 Swing/Main.asm:83-135 allocates dirty ID29 children, which the increasing walk later dispatches.
        // Main.asm:137-197 owns long-difference velocity, HIGH phase byte and native sine-X/cosine-Y WORD stores.
        int[] swingCutCalls={13451,13452,13486};
        int[] swingWatched={42,51,53,57,58,41,71,47};
        var swingVisit=actualField(module,"act2Loading");
        var swingPrior13450=module.act2LoadState();
        byte[] expectedSwingPriorZero=java.util.HexFormat.of().parseHex("00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
        for(int whole:new int[]{42,51,53,57,58})assertArrayEquals(expectedSwingPriorZero,
                swingPrior13450.placement().pool().slots().get(whole-32).copyBytes(),
                "Actual closed13450 full64 preimage; comparison only, never allocation-state ingress");
        // Columns: Swing42/51/53/57/58, Wheel41, ordinary Spring71, Robot47, completed Main SHA, selected VInt CPU SHA.
        String[][] expectedSwingImages={
            {"290404180020ed601aa000000188000000000000000008000318000000000000000000000200000004000000000000040008018802001aa0fe00c00800000008","290404180020ed601a6000000188000060008800000008000318010000000000000000000200000004000000000000040008018802001aa0fe00c00804000008","290404180020ed601a7000000188000000000000000008000318020000000000000000000200000004000000000000040008018802001aa0fe00c00803000008","290404180020ed601a8000000188000000000000000008000318020000000000000000000200000004000000000000040008018802001aa0fe00c00802000008","290404180020ed601a9000000188000000000000000008000318020000000000000000000200000004000000000000040008018802001aa0fe00c00801000008","0f04033800209a961a2780000387000001800000000007000408000100000000000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a270000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","2d0404000020e4f619f000000160000000000000000020220422020000000000000000b902000000000000040020e73401600001000000000000000000000000","d1f027d2e3fa30bb38238ad575fa10e8afae478a9388217d004a93628d3921ac","e93f638f76fa87505e09bcc608e67131c3cd4d717ceeb653a02e5615f0d33e92"}, // 13451
            {"290404180020ed601aa000000188000000000000000008000318000000000000000000000200000004000000000000040010018802001aa0fe00c01800000008","290404180020ed601a6000000188000000000000000008000318010000000000000000000200000004000000000000040010018802001aa0fe00c01804000008","290404180020ed601a7000000188000000000000000008000318020000000000000000000200000004000000000000040010018802001aa0fe00c01803000008","290404180020ed601a8000000188000000000000000008000318020000000000000000000200000004000000000000040010018802001aa0fe00c01802000008","290404180020ed601a9000000188000000000000000008000318020000000000000000000200000004000000000000040010018802001aa0fe00c01801000008","0f04033800209a961a2900000387000001800000000007000408010200000800000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a290000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","2d0404000020e4f619f000000160000000000000000020220422020000000000000000b902000000000000040020e73401600001000000000000000000000000","2006ff102a7975faaefe288110a94b53870ca46a3f3fe24c2692c2a49e9640f6","d1f027d2e3fa30bb38238ad575fa10e8afae478a9388217d004a93628d3921ac"}, // 13452
            {"290404180020ed601aa000000188000000000000000008000318000000000000000000000200000004000000000000040120018802001aa0fe00d4d000000008","290404180020ed601a67000001a6000000000200000008000318010000000000000000000200000004000000000000040120018802001aa0fe00d4d004000008","290404180020ed601a750000019e000000000000000008000318020000000000000000000200000004000000000000040120018802001aa0fe00d4d003000008","290404180020ed601a8300000197000000000000000008000318020000000000000000000200000004000000000000040120018802001aa0fe00d4d002000008","290404180020ed601a910000018f000000000000000008000318020000000000000000000200000004000000000000040120018802001aa0fe00d4d001000008","0f04033800209a961a41000003810000fe800000000007000408000100000100000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a410000037100000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","2d8404000020e4f619f000000160000000000000000020220422020000000000000000b902000000000000040020e73401600001000000000000000000000000","b4c384a6b81965db0b60d9e9e57934ac8feabb6ba55cbbd8273c61c1779c4f81","2dbcf7c5d84da6a4f108d297e1b986f696c8a63a680a505e0f40bad2b4eb2acf"}, // 13486
        };
        // Columns: five Swing receipt indices, Wheel, Robot, Dynamic, Return, Main count, selected count, phase, angle, actorX/Y, scratch, VInt, levelFrames, cameraX/Y.
        int[][] expectedSwingNumbers={
            {32,41,44,48,49,31,37,66,74,33,38,49160,8,417791744,3673856,252,3030,3007,6278,0}, // 13451
            {32,40,43,47,48,31,37,52,60,26,33,49176,16,418143744,3845888,252,3031,3008,6284,0}, // 13452
            {32,41,44,48,49,31,37,55,63,48,39,54480,288,431394816,18224896,244,3065,3042,6486,145}, // 13486
        };
        String[] expectedSwingStatus={"Status[routine=2, previousChunkWord=6272, rightRecordIndex=340, leftRecordIndex=312, rightSavedId=189, leftSavedId=176, nullRightByteOffset=0, nullLeftByteOffset=0]","Status[routine=2, previousChunkWord=6272, rightRecordIndex=340, leftRecordIndex=312, rightSavedId=189, leftSavedId=176, nullRightByteOffset=0, nullLeftByteOffset=0]","Status[routine=2, previousChunkWord=6400, rightRecordIndex=343, leftRecordIndex=312, rightSavedId=190, leftSavedId=176, nullRightByteOffset=0, nullLeftByteOffset=0]"};
        for(int call=13451;call<=13486;call++) {
            var input=controller(call);
            if(call==13451 || call==13452 || call==13486)replayNextRight(h,input);
            step(h,input);assertLaterRouteOwners(h,frame,source,world,generation);
            int row=java.util.Arrays.binarySearch(swingCutCalls,call);
            if(row<0)continue;
            var swingState=module.act2LoadState();var swingCalls=swingState.calls();var swingPool=swingState.placement().pool().slots();
            for(int index=0;index<swingWatched.length;index++)assertArrayEquals(java.util.HexFormat.of().parseHex(expectedSwingImages[row][index]),
                    swingPool.get(swingWatched[index]-32).copyBytes(),"Measured complete source whole"+swingWatched[index]+" at closed"+call);
            var swingFamily=swingPool.stream().filter(image->image.id()==0x29).toList();
            assertEquals(java.util.List.of(42,51,53,57,58),swingFamily.stream().map(image->image.identity().wholeIndex()).toList());
            assertEquals(java.util.List.of(0,4,3,2,1),swingFamily.stream().map(image->image.copyBytes()[0x3C]&0xFF).toList());
            for(int index=0;index<swingFamily.size();index++) {
                var image=swingFamily.get(index);var raw=java.nio.ByteBuffer.wrap(image.copyBytes());
                assertEquals(2,image.routine(),"Every later physical child already completed its own Init in the allocation walk");
                assertEquals(4,image.subtype());assertEquals(0,image.subtype2());assertEquals(4,image.copyBytes()[0x2F]&0xFF);
                assertEquals(6816,raw.getShort(0x36)&0xFFFF);assertEquals(392,raw.getShort(0x32)&0xFFFF);
                assertEquals(expectedSwingNumbers[row][11],raw.getShort(0x3A)&0xFFFF);assertEquals(expectedSwingNumbers[row][12],raw.getShort(0x30)&0xFFFF);
                assertEquals(8,raw.getShort(0x3E)&0xFFFF);assertEquals(0x200,raw.getShort(0x34)&0xFFFF);assertEquals(0xFE00,raw.getShort(0x38)&0xFFFF);
                assertEquals(0,image.copyBytes()[0x3D]&0xFF);assertEquals(0,image.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.X_FRACTION));
                assertEquals(0,image.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.Y_FRACTION));
                assertEquals(0x418,image.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE));
                assertEquals(0x20ED60,image.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));
                assertEquals(index==0?0:index==1?1:2,image.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.MAP_FRAME));
                assertEquals(8,image.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.Y_RADIUS));
                assertEquals(0x18,image.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.WIDTH));
            }
            var actualSwingReceiptIndices=java.util.stream.IntStream.range(0,swingCalls.size()).filter(index->swingCalls.get(index).name().equals("SWING_BODY")).boxed().toList();
            assertEquals(java.util.Arrays.stream(expectedSwingNumbers[row],0,5).boxed().toList(),actualSwingReceiptIndices);
            assertEquals(SonicCdGameModule.Act2LoadCall.PRIMARY_MAIN,swingCalls.get(8));
            assertEquals(expectedSwingNumbers[row][5],swingCalls.indexOf(SonicCdGameModule.Act2LoadCall.MOVING_SPRING_BODY));
            assertEquals(expectedSwingNumbers[row][6],swingCalls.indexOf(SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY));
            assertEquals(expectedSwingNumbers[row][7],swingCalls.indexOf(SonicCdGameModule.Act2LoadCall.DYNAMIC_OBJECTS));
            assertEquals(expectedSwingNumbers[row][8],swingCalls.indexOf(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN));
            assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,swingCalls.getLast());
            assertEquals(expectedSwingStatus[row],swingState.placement().status().toString());
            assertEquals(expectedSwingNumbers[row][13],frame.state().motion().centreX16_16());assertEquals(expectedSwingNumbers[row][14],frame.state().motion().centreY16_16());
            assertEquals(expectedSwingNumbers[row][15],frame.environment().movement().globals().primaryAngle());
            assertEquals((long)expectedSwingNumbers[row][16],frame.vIntClock().absoluteValue().orElseThrow());
            assertEquals(expectedSwingNumbers[row][17],frame.environment().globals().levelFrames());
            var swingCamera=((SonicCdPlayerPresentation)actualField(swingVisit,"presentation")).act2ForegroundImage();
            assertEquals(expectedSwingNumbers[row][18],swingCamera.cameraX());assertEquals(expectedSwingNumbers[row][19],swingCamera.cameraYWord());
            assertEquals(35,swingState.ring().rings());assertEquals(0,swingState.ring().timeStopTimer());
            assertEquals(1,frame.environment().movement().gates().timeZone());assertEquals(1,frame.environment().movement().gates().zoneAct());
            assertEquals(255,frame.environment().movement().gates().timeWarpDirection());
            assertEquals(0,frame.state().motion().playerControl());assertEquals(0,frame.environment().controls().controlLocked());
            assertEquals(8,frame.environment().controls().rawHeld());assertEquals(0,frame.environment().controls().rawTap());
            assertEquals(8,frame.environment().controls().logicalHeld());assertEquals(0,frame.environment().controls().logicalTap());
            var swingLoop=actualField(actualField(swingVisit,"values"),"loop");
            var swingCpu=assertInstanceOf(com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat.class,((java.util.Optional<?>)actualField(swingLoop,"completedDraw")).orElseThrow());
            assertEquals(640,swingCpu.bytes().length);assertEquals(expectedSwingNumbers[row][9],swingCpu.spriteCount());
            assertEquals(expectedSwingImages[row][8],java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(swingCpu.bytes())));
            var swingSelected=module.act2SelectedDisplay();assertInstanceOf(SonicCdPastDisplay.Ready.class,swingSelected.projection());
            var selectedSwingCpu=swingSelected.inputs().selectedCpu().orElseThrow();assertEquals(expectedSwingNumbers[row][10],selectedSwingCpu.spriteCount());
            assertEquals(expectedSwingImages[row][9],java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(selectedSwingCpu.bytes())));
            assertNotEquals(expectedSwingImages[row][8],expectedSwingImages[row][9],"Completed Main CPU is distinct from the preceding CPU selected at this VInt");
            assertEquals(java.util.List.of(0),selectedSwingCpu.provenance().stream().filter(piece->piece.slotRef().wholeIndex()==0).map(piece->piece.mappingPieceOrdinal()).toList());
            assertTrue(swingCpu.provenance().stream().noneMatch(piece->java.util.Arrays.stream(swingWatched,0,5).anyMatch(whole->whole==piece.slotRef().wholeIndex())));
            assertTrue(selectedSwingCpu.provenance().stream().noneMatch(piece->java.util.Arrays.stream(swingWatched,0,5).anyMatch(whole->whole==piece.slotRef().wholeIndex())),
                    "Actual Swing group has no Main or selected VInt pieces at these cuts; no family current/pixel claim");
        }
        assertEquals(209,frame.environment().globals().timeWarpTimer(),"Last closed13486 timer before the next primary save boundary");
        var swingClosed13486=captureNative(h);var swingRegistry=h.mode().getRewindRegistry();var swingEndFull=swingRegistry.capture();var swingEndCourse=swingRegistry.captureCourse();
        swingRegistry.restore(swingEndFull);assertNativeCapture(swingClosed13486,h);swingRegistry.restoreCourse(swingEndCourse);assertNativeCapture(swingClosed13486,h);
        // BEGIN measured natural TimeWarp save continuation; expected values are comparison only.
        var warpPresentation=(SonicCdPlayerPresentation)actualField(swingVisit,"presentation");
        var warpBeforeState=module.act2LoadState();var warpBeforeActor=frame.state();var warpBeforeEnvironment=frame.environment();
        var warpBeforeForeground=warpPresentation.act2ForegroundImage();var warpBeforeBackground=warpPresentation.act2BackgroundImage();
        var warpBeforeCustody=actualField(actualField(actualField(swingVisit,"values"),"globals"),"timeWarpCustody");
        var warpBeforeSaved=actualField(warpBeforeCustody,"saved");assertTrue(((java.util.Optional<?>)actualField(warpBeforeCustody,"completed")).isEmpty());
        var warpPreviousCpu=assertInstanceOf(com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat.class,
                ((java.util.Optional<?>)actualField(actualField(actualField(swingVisit,"values"),"loop"),"completedDraw")).orElseThrow());
        com.openggf.game.soniccd.objects.CdTimeWarpSaveData.State warpSaved210=null;Object warpReceipt210=null;
        var warpLockedForeground=warpBeforeForeground;
        byte[] warpExpectedSaved=java.util.HexFormat.of().parseHex("0023000019b60116060310195600910cab001b04c0001b032a001b00000000100006000a480000300c00");
        byte[] warpExpectedKnown=java.util.HexFormat.of().parseHex("01".repeat(42));
        int[][] warpActorNumbers={
            {0x19BC9000,0x01205F00,1536,2688,4096,2,0,6,132,7,14,2,2,0}, // 13487
            {0x19C29000,0x012ADF00,1536,2744,4096,2,0,6,132,7,14,2,2,0}, // 13488
            {0x19C89000,0x01359700,1536,2800,4096,2,0,6,132,7,14,2,2,0}, // 13489
            {0x19CE9000,0x01408700,1536,2856,4096,2,0,6,132,7,14,2,2,0}, // 13490
            {0x19D49000,0x014BAF00,1536,2912,4096,2,0,6,132,7,14,2,2,0}, // 13491
            {0x19DA9000,0x01570F00,1536,2968,4096,2,0,6,132,7,14,2,2,0}, // 13492
            {0x19E09000,0x0162A700,1536,3024,4096,2,0,6,132,7,14,2,2,0}, // 13493
            {0x19E69000,0x016E7700,1536,3080,4096,2,0,6,132,7,14,2,2,0}, // 13494
            {0x19EC9000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0}, // 13495
            {0x19F29000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0}, // 13496
            {0x19F89000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0}, // 13497
            {0x19FE9000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0}, // 13498
            {0x1A049000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0}, // 13499
            {0x1A0A9000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0}, // 13500
            {0x1A109000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0}, // 13501
            {0x1A169000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0}, // 13502
            {0x1A1C9000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0}, // 13503
            {0x1A229000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0}, // 13504
            {0x1A289000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0}, // 13505
            {0x1A2E9000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0}, // 13506
        };
        int[] warpCpuCounts={32,21,36,29,24,37,30,25,36,29,24,21,36,33,24,37,26,25,36,33};
        String[] warpCpuHashes={
            "5922f5b1292ad98b2be1d4a13ce7f474a51bbe38a10b45ff8be66272ec73aa2c", // 13487
            "956dfb12ab7f9695919935aee84be4f68fbbda425c19279138169cfc35b9ccfa", // 13488
            "296b545a95bc046c90772a42bffdfc744d754a0fd454741645a796c85418a7c5", // 13489
            "fe90a1ac76342606317ce74daab68f634ff85e3eeeb5522391e8d26632be4ae1", // 13490
            "217ba6c9b5b223a3320f27ad90fc86efe9869b945d64f3431548592fcc47690d", // 13491
            "068fba8977ea17e0afef3bd544155b597324ad649d053589eee6b94ca6d23b3e", // 13492
            "437c3c7b131d003d0f13eebc1582938191f5d0844d89dc2285d5ae33d37b7be2", // 13493
            "a77a83e366dd487c57f8de5bdd1d8db982494e543e863b944d7d08da90288633", // 13494
            "108a6cf977f1eab39374d68ed885903cc6d64fc3f5eb312c939bf77a5ba84154", // 13495
            "b5df6eb9b88d481d878631a19fc759b35c1eeb5fc1ce28ec7668b73299e592a6", // 13496
            "1d31ac09d636df8af6b57e5b597c5ee894974fc978a40058073aa3e41a6a59ba", // 13497
            "3c542cd7493b63bc6d38a205804f83f483e6fc5400c67063a47c43a7f7aedf8e", // 13498
            "a1e4fac638c9dc0e2fa9085e30f31381ac2e68d4156c217503e4a42af5e0febc", // 13499
            "704003ccd39b451098ea93c1f8ca48f5bc8c2cf3b83a8ab22e24ebfac1928328", // 13500
            "12953d2ad58e35d847bc531bae155ce5087063d18d79a09fad10bd755af8790e", // 13501
            "046fc402996e9baae73cef64445617f218c10bfc9495f0e42d194e081fdf9019", // 13502
            "5e848f7a1e74efb94b28474ee8716b24107f3338ea4d958cd1259d0a4bdd75fd", // 13503
            "8416d5ce97973e5cfe014efde41b1a061597b3d281ac90e4810619dbdf75a723", // 13504
            "dc365bbde640328eaef470174897c8f400b6c4b1a93a07fbae3d4d503406a3e5", // 13505
            "1a8f631b1bf342ccdb6aa588ff812c8d024f57ea3a10c29ab48cabc87f6faa1f", // 13506
        };
        for(int call=13487;call<=13506;call++) {
            var input=controller(call);
            if(call==13487 || call==13488 || call==13506)replayNextRight(h,input); // Before-forward full/course; no net extra call.
            step(h,input);assertLaterRouteOwners(h,frame,source,world,generation);
            assertSame(swingVisit,actualField(module,"act2Loading"));assertTrue(module.act2LoadingSelected());
            assertEquals(CdStageKey.parse("R12A"),source.stage()); // The marked cut still owns the old programme, not a destination load.
            int row=call-13487;var warpState=module.act2LoadState();var warpMotion=frame.state().motion();
            assertArrayEquals(warpActorNumbers[row],new int[]{warpMotion.centreX16_16(),warpMotion.centreY16_16(),warpMotion.xVelocity(),warpMotion.yVelocity(),warpMotion.groundVelocity(),warpMotion.routine(),warpMotion.angle(),warpMotion.flags(),warpMotion.spriteFlags(),warpMotion.xRadius(),warpMotion.yRadius(),warpMotion.animation(),warpMotion.previousAnimation(),warpMotion.playerControl()});
            assertEquals(210+row,frame.environment().globals().timeWarpTimer());assertEquals(3043+row,frame.environment().globals().levelFrames());
            assertEquals(3066L+row,frame.vIntClock().absoluteValue().orElseThrow());
            assertEquals(0x80,frame.environment().movement().gates().timeZone());assertEquals(1,frame.environment().movement().gates().zoneAct());
            assertEquals(255,frame.environment().movement().gates().timeWarpDirection());
            assertEquals(8,frame.environment().controls().rawHeld());assertEquals(0,frame.environment().controls().rawTap());
            assertEquals(8,frame.environment().controls().logicalHeld());assertEquals(0,frame.environment().controls().logicalTap());assertEquals(0,frame.environment().controls().controlLocked());
            assertEquals("BEGIN_LEVEL",warpState.phase().name());assertEquals(2,warpState.level().spawnMode());assertEquals(0,warpState.level().levelRestart());
            assertEquals(35,warpState.ring().rings());assertEquals(3,warpState.ring().lives());
            var warpValues=actualField(swingVisit,"values");var warpGlobals=actualField(warpValues,"globals");var warpCustody=actualField(warpGlobals,"timeWarpCustody");
            var warpSaved=assertInstanceOf(com.openggf.game.soniccd.objects.CdTimeWarpSaveData.State.class,actualField(warpCustody,"saved"));
            var warpReceipt=((java.util.Optional<?>)actualField(warpCustody,"completed")).orElseThrow();
            assertArrayEquals(warpExpectedSaved,(byte[])actualField(warpSaved,"bytes"));
            assertArrayEquals(warpExpectedKnown,(byte[])actualField(warpSaved,"knownMask"));assertArrayEquals(warpExpectedKnown,warpSaved.knownMask().copy());
            var warpCanonical=java.nio.ByteBuffer.allocate(42);
            for(int[] range:new int[][]{{0xFF1528,2},{0xFF1545,2},{0xFF1548,5},{0xFF154E,28},{0xFF1570,4},{0xFF1593,1}})
                for(int address=range[0];address<range[0]+range[1];address++)warpCanonical.put((byte)warpSaved.knownByte(address).orElseThrow());
            assertArrayEquals(warpExpectedSaved,warpCanonical.array(),"All42 addressed canonical saved bytes, without padding or inferred unknowns");
            if(call==13487) {
                assertNotSame(warpBeforeSaved,warpSaved);warpSaved210=warpSaved;warpReceipt210=warpReceipt;warpLockedForeground=warpPresentation.act2ForegroundImage();
                var m=warpBeforeActor.motion();var e=warpBeforeEnvironment.movement();var fg=warpBeforeForeground;var bg=warpBeforeBackground;
                var live=assertInstanceOf(com.openggf.game.soniccd.objects.CdCheckpointLiveAux.State.class,actualField(warpGlobals,"live"));
                // Native local8 read before the original MODE tail; other sources come from this real save's owners.
                int[] warpSourceValues={warpBeforeState.level().spawnMode(),m.centreX16_16()>>>16,m.centreY16_16()>>>16,m.groundVelocity()&65535,m.xVelocity()&65535,m.yVelocity()&65535,m.flags()&~0x48,
                        com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(live,com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.WATER_ROUTINE),e.bounds().bottom(),fg.cameraX(),fg.cameraYWord(),bg.cameraBgXWord(),bg.cameraBgYWord(),bg.cameraBg2XWord(),bg.cameraBg2YWord(),bg.cameraBg3XWord(),bg.cameraBg3YWord(),
                        com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(live,com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.WATER_HEIGHT2),com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(live,com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.WATER_FULLSCREEN),warpState.ring().rings(),warpState.ring().livesFlags(),
                        com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(live,com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.TIME_LONG),e.gates().miniSonic()};
                var warpFields=com.openggf.game.soniccd.objects.CdTimeWarpSaveData.SavedField.values();assertEquals(23,warpFields.length);assertEquals(warpFields.length,warpSourceValues.length);
                for(int index=0;index<warpFields.length;index++)assertEquals(warpSourceValues[index],warpSaved.read(warpFields[index]),warpFields[index].name());
                assertEquals(0,warpBeforeState.level().spawnMode());assertEquals(0,warpBeforeForeground.scrollLock());assertNotSame(warpBeforeForeground,warpLockedForeground);
                assertEquals(6582,warpSaved.read(com.openggf.game.soniccd.objects.CdTimeWarpSaveData.SavedField.PRIMARY_X));
                assertEquals(278,warpSaved.read(com.openggf.game.soniccd.objects.CdTimeWarpSaveData.SavedField.PRIMARY_Y));
                assertNotEquals(warpMotion.centreX16_16()>>>16,warpSaved.read(com.openggf.game.soniccd.objects.CdTimeWarpSaveData.SavedField.PRIMARY_X),"Saved local position precedes the genuine MODE continuation");
            }
            assertSame(warpSaved210,warpSaved);assertSame(warpReceipt210,warpReceipt); // Spawn2 returns do not create a second save or receipt.
            assertSame(swingVisit,actualField(warpReceipt,"owner"));assertSame(frame,actualField(warpReceipt,"frame"));assertSame(source,actualField(warpReceipt,"stageSource"));
            assertSame(warpState.assets(),actualField(warpReceipt,"assets"));assertSame(world,actualField(warpReceipt,"world"));assertEquals(generation,((Number)actualField(warpReceipt,"generation")).longValue());
            assertEquals(0x80,(int)actualField(warpReceipt,"rawPeriod"));assertSame(warpSaved,actualField(warpReceipt,"saved"));
            assertSame(warpLockedForeground,warpPresentation.act2ForegroundImage());assertSame(warpBeforeBackground,warpPresentation.act2BackgroundImage());
            assertEquals(0x0100,warpPresentation.act2ForegroundImage().scrollLock());assertEquals(6486,warpPresentation.act2ForegroundImage().cameraX());assertEquals(145,warpPresentation.act2ForegroundImage().cameraYWord());
            var warpCpu=assertInstanceOf(com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat.class,((java.util.Optional<?>)actualField(actualField(warpValues,"loop"),"completedDraw")).orElseThrow());
            String warpCpuHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(warpCpu.bytes()));
            assertEquals(warpCpuCounts[row],warpCpu.spriteCount());assertEquals(warpCpuHashes[row],warpCpuHash);
            assertTrue(warpCpu.provenance().stream().noneMatch(piece->piece.slotRef().wholeIndex()==0),"This call's Main DrawObject is suppressed at timer>=210");
            var warpSelected=module.act2SelectedDisplay();assertInstanceOf(SonicCdPastDisplay.Ready.class,warpSelected.projection());var warpSelectedCpu=warpSelected.inputs().selectedCpu().orElseThrow();
            assertArrayEquals(warpPreviousCpu.bytes(),warpSelectedCpu.bytes());assertEquals(warpPreviousCpu.spriteCount(),warpSelectedCpu.spriteCount());assertEquals(warpPreviousCpu.provenance(),warpSelectedCpu.provenance());
            assertNotEquals(warpCpuHash,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(warpSelectedCpu.bytes())),"Already selected VInt CPU precedes this call's completed Main CPU; no new pixel/visibility claim");
            warpPreviousCpu=warpCpu;
        }
        var warpClosed13506=captureNative(h);var warpEndFull=registry.capture();var warpEndCourse=registry.captureCourse();
        registry.restore(warpEndFull);assertNativeCapture(warpClosed13506,h);registry.restoreCourse(warpEndCourse);assertNativeCapture(warpClosed13506,h);
        assertLaterRouteOwners(h,frame,source,world,generation);
        var warpLastClosed=module.act2LoadState();var warpLastPoolBytes=java.nio.ByteBuffer.allocate(96*64);
        for(var image:warpLastClosed.placement().pool().slots())warpLastPoolBytes.put(image.copyBytes());
        assertEquals("7988061948ffd8ed3d38a52bbd7f5d9a73b90fa513a340f47c0b85e58d1558bf",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(warpLastPoolBytes.array())));
        // BEGIN measured natural TimeWarp230 old exit; facts are expected values only.
        assertEquals(SonicCdGameModule.LevelProgramPhase.ACT2_LOADING,module.levelProgramState().phase());
        assertEquals(1,module.levelProgramState().palFadeFlags());assertEquals(0,module.levelProgramState().whiteFadeSteps());
        var exitInitialValues=actualField(swingVisit,"values");var exitInitialLoop=actualField(exitInitialValues,"loop");
        var exitPaletteTimers=actualField(exitInitialLoop,"paletteTimers7");var exitPaletteSteps=actualField(exitInitialLoop,"paletteSteps7");
        var exitCodeTable=warpLastClosed.section().codeTable();com.openggf.game.soniccd.resources.CdR12aCameraArtResidency.State exitResidency=null;
        var exitCpuImage=(com.openggf.game.soniccd.level.CdAssetBytes)actualField(exitInitialValues,"cpuSat");
        var exitFrozenActor=frame.captureActor();var exitFrozenArt=frame.artState();var exitFrozenRing=warpLastClosed.ring();
        var exitFrozenReserved=(com.openggf.game.soniccd.level.CdAssetBytes)actualField(exitInitialValues,"hudObjects");
        var exitFrozenLive=actualField(actualField(exitInitialValues,"globals"),"live");
        var exitFrozenSection=warpLastClosed.section();
        String[] exitSelectedPaletteHashes={
            "7efc9dacd5ab5b0c8e86bbd59026d8b53425cc53ac9bc0ea21c874cced38eb7b", // 13507
            "7efc9dacd5ab5b0c8e86bbd59026d8b53425cc53ac9bc0ea21c874cced38eb7b", // 13508
            "e5931efcd54dbad2dabe014e5f4b854ac3f21d929c3d9be50b3875c1dd9e2293", // 13509
            "9a428feca1e241c235dfba431210cc2712132eaac6a952b41b52636d583b67c3", // 13510
            "240590ab74b760423ad84074a5178810b03bb99fc451dc28f02f094ba666ce8f", // 13511
            "278867b9b9c0b232fadec6e363b790da13e70309a33a765e2ae69e90fe41c7fa", // 13512
            "c107f358a93ea5cc2fbe4664eceb81a10429a6bb925c8875cb72956971d49b97", // 13513
            "63cdccb1f6c441416ce7dbe54e2422c8211d0bb6770cc1f4587c78d348567e63", // 13514
            "45314c673dc749ccac7cfc9382f98557d5c5ab59e6122092d384c62888123052", // 13515
            "54c4381402ff580d07a17fb5ee0929c05742d6e6a9c238e9d51f31a70ea89c61", // 13516
            "4d027f01bb59cfa15dfacff71410c21f45ebe85ee1d893dcc6eaa80f3911448c", // 13517
            "4b223ba4aad519eaf3aa1ce3a5e0e64901c51174bc04b60944481716c4f77b85", // 13518
            "8507c999db9808c6bf3dd3d201bd2bd821139c874e02692f18b0bb25fa6d5527", // 13519
            "e7fbe99c6cb938e7c6cefbce2c346a639a4ce259607dac13305f54599b3aaf07", // 13520
            "a3193ed861ea932898a034215b3d754941a7be2d494337d4da8d54f6f6943ed4", // 13521
            "142b1da1768449bbbcf55f1867236b13ff37808f3c19b607dfd1e07806329323", // 13522
            "97ed8b8411de8cc1ff442df1d0696310c49e7a69fdc2bfe843ddcc08c6f62ec7", // 13523
            "90a714f1efb218b906fd4168998ce80867c134b20e554a5dc0496d8991717058", // 13524
            "05a4eecb53df4116df9f4a3d2fca0a3fd4e1cacc21a29c7ea5cce98a45584cf9", // 13525
            "66dc89b66aecf902bd44125c1991b960754831a930cb2ca387aa4c5ebd55304a", // 13526
            "376c9a4d0d7da033f5b30002c4789b80ccfd971b577b9721bee328e412c3cba1", // 13527
            "dc59c4174d0ec06d2a1091106e821e3f6883a3ccc7096ebdb32c0ee279786ffc", // 13528
            "f2dbbbbda7805eae87ded25e3e3b334bbde9f0ff29f6093188d7ec9bc5b529d0", // 13529
        };
        java.util.List<String> exitExpectedCalls=java.util.List.of(
                "ORDINARY_PUBLICATION","UPDATE_ANIMATED_TILES","ORDINARY_DRAW_LEVEL","ORDINARY_SLOW_PLC","UPDATE_HUD","ORDINARY_SERVICE","LEVEL_FRAME_INCREMENT",
                "SPAWN_OBJECTS","PRIMARY_MAIN","SHIELD_ACTIVE","SHIELD_BODY","TIME_STARS_ACTIVE","TIME_STARS_BODY","TIME_STARS_ACTIVE",
                "TIME_STARS_BODY","TIME_STARS_ACTIVE","TIME_STARS_BODY","TIME_STARS_ACTIVE","TIME_STARS_BODY","RESERVED_OBJECTS","SPRING_BODY",
                "RING_BODY","RING_BODY","SPRING_BODY","RING_BODY","RING_BODY","MOVING_SPRING_BODY","RING_BODY",
                "SPRING_BODY","RING_BODY","SPRING_BODY","FLOWER_BODY","SPRING_BODY","RING_BODY","RING_BODY",
                "SPRING_BODY","RING_BODY","RING_BODY","SPRING_BODY","DYNAMIC_OBJECTS","OLD_EXIT_PRELUDE","OLD_EXIT_CLEAR_PLCS");
        for(int call=13507;call<=13529;call++) {
            var input=controller(call);int waits=call-13507;
            var exitPriorValues=actualField(swingVisit,"values");
            var exitPriorPalette=(com.openggf.game.soniccd.level.CdAssetBytes)actualField(exitPriorValues,"cpuPalette");
            var exitPriorWater=(com.openggf.game.soniccd.level.CdAssetBytes)actualField(exitPriorValues,"waterPalette");
            var exitExpectedColours=waits==0?null:com.openggf.game.soniccd.resources.CdLevelPaletteFade.stepToWhite(
                    new com.openggf.game.soniccd.resources.CdLevelPaletteFade.Palettes(exitPriorPalette,exitPriorWater));
            if(call==13507 || call==13508 || call==13518 || call==13529)replayNextRight(h,input); // Existing before-forward full/course helper restores this cut.
            step(h,input);
            // Old exit has no ORDINARY_MAIN_RETURN; check the actual owners independently.
            assertSame(frame,module.requirePlayerFrame());assertFalse(frame.closed());assertSame(source,frame.stageSource());
            assertSame(world,h.mode().getWorldSession());assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            assertSame(swingVisit,actualField(module,"act2Loading"));assertTrue(module.act2LoadingSelected());assertEquals(CdStageKey.parse("R12A"),source.stage());assertTrue(module.warpSceneState().isEmpty());
            var exitState=module.act2LoadState();var exitValues=actualField(swingVisit,"values");var exitLoop=actualField(exitValues,"loop");
            var exitProgramme=module.levelProgramState();boolean returned=waits==22;
            assertEquals(returned?SonicCdGameModule.Act2LoadPhase.OLD_RETURNED:SonicCdGameModule.Act2LoadPhase.OLD_WHITE_FADE,exitState.phase());
            assertEquals(returned?SonicCdGameModule.LevelProgramPhase.OLD_RETURNED:SonicCdGameModule.LevelProgramPhase.WHITE_FADE,exitProgramme.phase());
            assertEquals(waits,exitProgramme.whiteFadeSteps());assertEquals(returned?0:1,exitProgramme.palFadeFlags());
            assertEquals(0x80,exitProgramme.gameMode());assertEquals(0,exitProgramme.levelStarted());assertEquals(0,exitProgramme.demoMode());
            assertEquals(0,exitProgramme.timeStones());assertEquals(0,exitProgramme.specialStage());assertEquals(0,exitProgramme.usePlayer2());assertEquals(java.util.OptionalInt.empty(),exitProgramme.goodFutureFlags());
            assertEquals(new com.openggf.game.soniccd.objects.CdLevelGlobals(returned?0:0x0100,1,0,2,3,0,2,6,0),exitState.level());
            assertEquals(0x80,frame.environment().movement().gates().timeZone());assertEquals(1,frame.environment().movement().gates().zoneAct());assertEquals(returned?0:255,frame.environment().movement().gates().timeWarpDirection());
            assertEquals(230+waits,frame.environment().globals().timeWarpTimer());assertEquals(3063,frame.environment().globals().levelFrames());assertEquals(3086L+waits,frame.vIntClock().absoluteValue().orElseThrow());
            assertEquals(8,frame.environment().controls().rawHeld());assertEquals(0,frame.environment().controls().rawTap());
            assertEquals(8,frame.environment().controls().logicalHeld());assertEquals(0,frame.environment().controls().logicalTap());assertEquals(0,frame.environment().controls().controlLocked());assertFalse(frame.paused());
            var exitCustody=actualField(actualField(exitValues,"globals"),"timeWarpCustody");assertSame(warpSaved210,actualField(exitCustody,"saved"));
            assertSame(warpReceipt210,((java.util.Optional<?>)actualField(exitCustody,"completed")).orElseThrow());
            assertArrayEquals(warpExpectedSaved,(byte[])actualField(warpSaved210,"bytes"));assertArrayEquals(warpExpectedKnown,warpSaved210.knownMask().copy());
            assertSame(warpLockedForeground,warpPresentation.act2ForegroundImage());assertSame(warpBeforeBackground,warpPresentation.act2BackgroundImage());
            assertEquals(0x0100,warpPresentation.act2ForegroundImage().scrollLock());assertEquals(6486,warpPresentation.act2ForegroundImage().cameraX());assertEquals(145,warpPresentation.act2ForegroundImage().cameraYWord());
            var exitPoolBytes=java.nio.ByteBuffer.allocate(96*64);for(var image:exitState.placement().pool().slots())exitPoolBytes.put(image.copyBytes());
            assertEquals("ac10a9caa06b3d89aa1ff49d699e204efffe037ff4b1f0965952c283272ffba6",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(exitPoolBytes.array())));
            assertEquals(warpLastClosed.placement().status(),exitState.placement().status());assertArrayEquals(warpLastClosed.placement().savedFlags().copy(),exitState.placement().savedFlags().copy());
            var exitExpectedNow=new java.util.ArrayList<>(exitExpectedCalls);
            for(int wait=0;wait<waits;wait++)exitExpectedNow.addAll(java.util.List.of("OLD_EXIT_FADE_SERVICE","OLD_EXIT_WHITE_COLOURS","PROCESS_PLCS"));
            if(returned)exitExpectedNow.add("OLD_EXIT_RETURN");
            assertEquals(exitExpectedNow,exitState.calls().stream().map(Object::toString).toList(),"No repeated ordinary body/HUD/level-frame/camera/SAT/tail work during selector12 waits");
            var exitSection=exitState.section();assertSame(exitCodeTable,exitSection.codeTable());
            // Ordinary V-int/HUD work precedes the13507prelude. Anchor its completed current
            // value here, then prove all selector12 waits retain that exact image.
            if(waits==0)exitResidency=exitSection.residency();
            assertSame(exitResidency,exitSection.residency());
            assertEquals(0x0600,exitSection.sectionWord());assertArrayEquals(new byte[96],exitSection.queue().bytes().copy());
            assertEquals(0,exitSection.queue().headSource());assertEquals(0,exitSection.queue().headDestination());assertEquals(0,exitSection.remainingTilesWord());assertEquals(0,exitSection.processBudgetWord());
            assertEquals(0,exitSection.hintWord());assertEquals(0,exitSection.padWord());assertFalse(exitSection.stopped());assertTrue(exitSection.decoder().isEmpty());
            assertEquals("SavedDecoder[writer=CLEARED, repeatWord=0, pixelNibble=0, previousRow=0, readWord=0, shiftWord=0]",exitSection.savedDecoder().toString());
            assertSame(exitPaletteTimers,actualField(exitLoop,"paletteTimers7"));assertSame(exitPaletteSteps,actualField(exitLoop,"paletteSteps7"));
            assertEquals(0x003F,(int)actualField(((java.util.Optional<?>)actualField(actualField(exitValues,"globals"),"tail")).orElseThrow(),"paletteFadeInfoWord"));
            var exitPalette=(com.openggf.game.soniccd.level.CdAssetBytes)actualField(exitValues,"cpuPalette");var exitWater=(com.openggf.game.soniccd.level.CdAssetBytes)actualField(exitValues,"waterPalette");
            var exitDisplayedPalette=(com.openggf.game.soniccd.level.CdAssetBytes)actualField(exitValues,"displayedPalette");
            assertArrayEquals(exitPriorPalette.copy(),exitDisplayedPalette.copy(),"The genuine selector12 publishes its prior current palette before colour work");
            assertEquals(exitSelectedPaletteHashes[waits],java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(exitDisplayedPalette.copy())));
            if(waits==0) {
                assertSame(exitPriorPalette,exitPalette);assertSame(exitPriorWater,exitWater);
                assertFalse(java.util.Arrays.equals(warpLastPoolBytes.array(),exitPoolBytes.array()),"The returned13507 prelude retains the genuine final physical walk");
                var m=frame.state().motion();assertArrayEquals(new int[]{0x1A349000,0x016B7F00,1536,0,1536,2,0,0,132,9,19,0,0,0},
                        new int[]{m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerControl()});
                exitFrozenActor=frame.captureActor();exitFrozenArt=frame.artState();exitFrozenRing=exitState.ring();exitFrozenReserved=exitState.reservedObjects();
                exitFrozenLive=actualField(actualField(exitValues,"globals"),"live");exitFrozenSection=exitSection;
            } else {
                // Existing component oracle checks the caller's normal/water flow; selected hashes above are independently recorded ENGINE comparisons.
                assertArrayEquals(exitExpectedColours.palette().copy(),exitPalette.copy());assertArrayEquals(exitExpectedColours.waterPalette().copy(),exitWater.copy());
                assertEquals(exitFrozenActor,frame.captureActor());assertSame(exitFrozenArt,frame.artState());assertEquals(exitFrozenRing,exitState.ring());
                assertSame(exitFrozenReserved,exitState.reservedObjects());assertSame(exitFrozenLive,actualField(actualField(exitValues,"globals"),"live"));assertEquals(exitFrozenSection,exitSection);
            }
            assertEquals("927a3b61c33cd4169db796a4e20d56eab47fd06edfad43a1415315e0c8afc2f5",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(exitState.reservedObjects().copy())));
            assertSame(warpPreviousCpu,((java.util.Optional<?>)actualField(exitLoop,"completedDraw")).orElseThrow());assertSame(exitCpuImage,actualField(exitValues,"cpuSat"));
            var exitSelected=module.act2SelectedDisplay();assertInstanceOf(SonicCdPastDisplay.Ready.class,exitSelected.projection());var exitSelectedCpu=exitSelected.inputs().selectedCpu().orElseThrow();
            assertEquals(33,exitSelectedCpu.spriteCount());assertArrayEquals(warpPreviousCpu.bytes(),exitSelectedCpu.bytes());assertEquals(warpPreviousCpu.provenance(),exitSelectedCpu.provenance());
            assertEquals("1a8f631b1bf342ccdb6aa588ff812c8d024f57ea3a10c29ab48cabc87f6faa1f",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(exitSelectedCpu.bytes())));
            assertArrayEquals(exitCpuImage.copy(),((com.openggf.game.soniccd.level.CdAssetBytes)actualField(exitValues,"displayedSat")).copy());
            assertSame(exitResidency,exitSelected.inputs().current());assertSame(frame.artState(),exitSelected.inputs().player());
            assertSame(actualField(exitValues,"foregroundPlane"),exitSelected.inputs().foreground());assertSame(warpPresentation.act2PlaneImage(),exitSelected.inputs().background());
            assertEquals("d06d7ac7ebf96364d6e05f67e8898137f71145e68c0bc98cdc3a5c81fc556820",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(((com.openggf.game.soniccd.level.CdAssetBytes)actualField(exitValues,"displayedHScroll")).copy())));
            assertEquals(0x0091001B,(int)actualField(exitValues,"displayedVScrollLongBits"));
            // Retained Main CPU and already selected VInt CPU are separate partitions with equal bytes here; no new SAT build, pixels or visibility claim.
        }
        var exitClosed13529=captureNative(h);var exitEndFull=registry.capture();var exitEndCourse=registry.captureCourse();
        registry.restore(exitEndFull);assertNativeCapture(exitClosed13529,h);registry.restoreCourse(exitEndCourse);assertNativeCapture(exitClosed13529,h);
        var exitBeforeStopValues=actualField(swingVisit,"values");var exitBeforeStopCalls=actualField(exitBeforeStopValues,"calls");
        var exitBeforeStopProgramme=module.levelProgramState();var exitBeforeStopClock=frame.vIntClock();
        assertEquals(3108L,exitBeforeStopClock.absoluteValue().orElseThrow());assertEquals(SonicCdGameModule.LevelProgramPhase.OLD_RETURNED,exitBeforeStopProgramme.phase());
        step(h,controller(13530));assertEquals(SonicCdGameModule.LevelProgramPhase.WARP,module.levelProgramState().phase());
        assertEquals(com.openggf.game.soniccd.scene.CdWarpScene.Phase.DELAY,module.warpSceneState().orElseThrow().phase());
        assertEquals(90,module.warpSceneState().orElseThrow().timerWord());
        assertEquals(java.util.List.of(SonicCdGameModule.ProgramSound.STOP_FM),module.programSoundRequests());
        for(int row=1;row<=354;row++){
            int call=13530+row;boolean replay=row==1||row==91||row==115||row==330||row==354;
            var ipxWarpFull=replay?registry.capture():null;var ipxWarpCourse=replay?registry.captureCourse():null;var ipxWarpBefore=replay?captureNative(h):null;
            step(h,controller(call));
            if(replay){var ipxWarpAfter=captureNative(h);registry.restore(ipxWarpFull);assertNativeCapture(ipxWarpBefore,h);step(h,controller(call));assertNativeCapture(ipxWarpAfter,h);
                registry.restoreCourse(ipxWarpCourse);assertNativeCapture(ipxWarpBefore,h);step(h,controller(call));assertNativeCapture(ipxWarpAfter,h);}
            assertSame(exitBeforeStopValues,actualField(swingVisit,"values"));assertSame(exitBeforeStopClock,frame.vIntClock());
            assertEquals(row,module.warpSceneState().orElseThrow().vIntCounterWord());assertFalse(frame.closed());
            assertSame(warpSaved210,actualField(actualField(actualField(actualField(swingVisit,"values"),"globals"),"timeWarpCustody"),"saved"));
            assertEquals(22,module.levelProgramState().whiteFadeSteps());assertEquals(0,module.levelProgramState().palFadeFlags());
        }
        assertEquals(com.openggf.game.soniccd.scene.CdWarpScene.Phase.RETURNED,module.warpSceneState().orElseThrow().phase());
        var ipxWarpEndFull=registry.capture();var ipxWarpEndCourse=registry.captureCourse();var warpBeforeBlank=captureNative(h);
        step(h,controller(13885));var warpAfterBlank=captureNative(h);
        registry.restore(ipxWarpEndFull);assertNativeCapture(warpBeforeBlank,h);step(h,controller(13885));assertNativeCapture(warpAfterBlank,h);
        registry.restoreCourse(ipxWarpEndCourse);assertNativeCapture(warpBeforeBlank,h);step(h,controller(13885));assertNativeCapture(warpAfterBlank,h);
        assertEquals(SonicCdGameModule.LevelProgramPhase.PAST_PENDING,module.levelProgramState().phase());
        assertEquals(java.util.List.of(SonicCdGameModule.ProgramSound.STOP_FM,SonicCdGameModule.ProgramSound.WARP_DIRECT_FM_D8,SonicCdGameModule.ProgramSound.STOP_FM),module.programSoundRequests());
        // Actual R12SubCmds Past selection performs one FULL target load; no extra source V-int.
        step(h,controller(13886));
        assertTrue(frame.closed());assertFalse(module.act2LoadingSelected());assertTrue(module.act2PastLoadingSelected());
        assertSame(world,h.mode().getWorldSession());assertEquals(generation+1,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        var targetFrame=module.requirePlayerFrame();var target=module.act2PastLoadingVisit();var targetHost=module.act2PastWorldHost();
        assertNotSame(frame,targetFrame);assertFalse(targetFrame.closed());assertEquals(CdStageKey.parse("R12B"),targetFrame.stageSource().stage());
        assertEquals(1,targetFrame.environment().movement().gates().zoneAct());assertEquals(0x80,targetFrame.environment().movement().gates().timeZone());
        assertEquals(3108L,targetFrame.vIntClock().absoluteValue().orElseThrow());assertEquals(3063,targetFrame.environment().globals().levelFrames());
        assertEquals(SonicCdAct2PastLoadingVisit.Phase.FIRST_PLC,target.state().phase());assertEquals(22,target.state().oldWhiteSteps());
        assertEquals(0,target.state().initialWhiteSteps());assertFalse(target.state().displayEnabled());assertSame(warpSaved210,target.saved());
        assertEquals("14de4fdb4979125a8c0c197c926133353d20a2ca5b2d57db2e363c4e94460a64",((com.openggf.game.soniccd.level.SonicCdAct2PastLevel)h.mode().getLevelManager().getCurrentLevel()).assets().identity().mmdSha256());
        assertSame(exitBeforeStopValues,actualField(swingVisit,"values"));assertSame(exitBeforeStopCalls,actualField(exitBeforeStopValues,"calls"));
        assertSame(exitBeforeStopClock,actualField(frame,"vIntClock"));
        int targetReturnedCall=13886;
        for(int call=13887;call<=14142&&target.state().phase()!=SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL;call++) {
            var beforeTarget=captureNative(h);var fullTarget=registry.capture();var courseTarget=registry.captureCourse();
            step(h,controller(call));var afterTarget=captureNative(h);
            registry.restore(fullTarget);assertNativeCapture(beforeTarget,h);step(h,controller(call));assertNativeCapture(afterTarget,h);
            registry.restoreCourse(courseTarget);assertNativeCapture(beforeTarget,h);step(h,controller(call));assertNativeCapture(afterTarget,h);
            assertSame(targetFrame,module.requirePlayerFrame());assertSame(world,h.mode().getWorldSession());assertFalse(targetFrame.closed());
            assertEquals(generation+1,h.mode().getLevelManager().getCompletedProductionLoadGeneration());targetReturnedCall=call;
        }
        assertEquals(SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL,target.state().phase(),"Finite target loading must complete before any ordinary walk");
        assertEquals(20,target.completedSavedStores().size());assertEquals(0,target.state().initialWhiteSteps());assertEquals(22,target.state().fromWhiteSteps());
        assertEquals(0,targetFrame.environment().globals().levelFrames());assertEquals(0,targetFrame.environment().movement().gates().timeZone());
        assertEquals(1,targetFrame.state().header().slotId());assertEquals(0,targetFrame.state().motion().routine());
        assertEquals(0,target.state().section().queue().headSource());assertEquals(0,target.state().section().remainingTilesWord());
        assertTrue(targetHost.geometryReady());assertTrue(target.state().displayEnabled());
        var targetSelected=assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,targetHost.selected()).selection();
        assertInstanceOf(SonicCdPastDisplay.Ready.class,targetSelected.projection(),"Actual completed target loading selects genuine current planes");
        assertTrue(targetSelected.inputs().selectedCpu().isEmpty(),"No ordinary CPU SAT or visible Sonic was fabricated during load");
        boolean targetSonicSelected=false;
        var afterProjectorRight=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,0,0,false,false),PlayerInputState.neutral());
        var afterProjectorJump=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,2,0,false,false),PlayerInputState.neutral());
        for(int ordinary=1;ordinary<=1782;ordinary++){
            int call=targetReturnedCall+ordinary;
            var actualInput=call<=14516?controller(call):call>=15601&&call<=15632?afterProjectorJump:afterProjectorRight;
            var beforeMotion=targetFrame.state().motion();
            var beforeOrdinary=captureNative(h);var fullOrdinary=registry.capture();var courseOrdinary=registry.captureCourse();
            try{step(h,actualInput);}
            catch(RuntimeException|Error original){throw new AssertionError("R12B continuation: last returned="+(call-1)+", first failed="+call+", primary prefix="+targetFrame.state()+", failure="+original,original);}
            var afterOrdinary=captureNative(h);
            assertFalse(targetFrame.closed(),"Target ordinary call"+call);assertEquals(ordinary,targetFrame.environment().globals().levelFrames());
            if(ordinary<=12)assertEquals(2,targetFrame.state().motion().routine());assertTrue(targetHost.video().completedCpu().isPresent());
            assertSame(world,h.mode().getWorldSession());assertEquals(generation+1,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            var selected=assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,targetHost.selected()).selection();
            assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection(),"Actual target selected display at call"+call);
            targetSonicSelected|=selected.inputs().selectedCpu().map(cpu->cpu.provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==0)).orElse(false);
            if(ordinary==16){
                var actualBoulder=targetHost.placement().pool().state(65);assertEquals(0x1B,actualBoulder.id());assertEquals(2,actualBoulder.routine());
                assertEquals(0x20940A,actualBoulder.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));
                assertEquals(0x035E,actualBoulder.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE));
                var actualWall=targetHost.placement().pool().state(69);assertEquals(0x36,actualWall.id());assertEquals(2,actualWall.routine());
                assertEquals(0x20EE64,actualWall.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));
                assertEquals(0x44BE,actualWall.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE));
            }
            if(ordinary==32){
                var actualBlock=targetHost.placement().pool().state(33);assertEquals(0x12,actualBlock.id());assertEquals(2,actualBlock.routine());
                assertEquals(0x20A1B0,actualBlock.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));
                assertEquals(0x434C,actualBlock.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE));
            }
            if(call==14516){
                var parent=targetHost.placement().pool().state(32);
                assertEquals(0x2E,parent.id());assertEquals(2,parent.routine());
                assertEquals(0x20E9F0,parent.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));
                assertEquals(0x3AF,parent.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE));
                var actualChildren=targetHost.placement().pool().capture().slots().stream()
                        .filter(v->(v.id()==0x2E||v.id()==0x24)&&v.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.EXPLOSION_POINTS_FRAME)==0xD800&&v.identity().wholeIndex()!=32).toList();
                assertEquals(java.util.List.of(0xFF,1,0x80,0x81),actualChildren.stream().map(com.openggf.game.soniccd.objects.CdObjectState::subtype).toList());
                assertEquals(java.util.List.of(2,2,4,4),actualChildren.stream().map(com.openggf.game.soniccd.objects.CdObjectState::routine).toList());
                assertTrue(actualChildren.stream().allMatch(v->v.identity().wholeIndex()>32));
            }
            if(call==14534){
                assertEquals(4,targetFrame.state().motion().routine(),"Actual damage enters native Hurt after the earlier Main prefix");
                assertEquals(0,targetHost.globals().ring().rings());
                assertTrue(targetHost.placement().pool().capture().slots().stream().anyMatch(v->v.id()==0x11),"The real damage prefix created LostRing children");
            }
            if(call==14535){
                var afterHurt=targetFrame.state().motion();assertEquals(4,beforeMotion.routine());assertEquals(4,afterHurt.routine());
                assertEquals(beforeMotion.centreX16_16()+(beforeMotion.xVelocity()<<8),afterHurt.centreX16_16());
                assertEquals(beforeMotion.centreY16_16()+(beforeMotion.yVelocity()<<8),afterHurt.centreY16_16());
                assertEquals(beforeMotion.xVelocity(),afterHurt.xVelocity());assertEquals(beforeMotion.yVelocity()+0x30,afterHurt.yVelocity());
            }
            if(call==14580){assertEquals(2,targetFrame.state().motion().routine());assertEquals(0,targetFrame.state().motion().flags()&2,"Native Hurt landing returned to Main");}
            if(ordinary==1||ordinary==4||ordinary==12||ordinary==15||ordinary==16||ordinary==17||ordinary==31||ordinary==32||ordinary==33||ordinary==64||ordinary==128||ordinary==208||ordinary==209||ordinary==216||ordinary==217||ordinary==240||ordinary==272||ordinary==312||ordinary==313||ordinary==314||ordinary==384||ordinary==472||ordinary==473||ordinary==488||ordinary==489||ordinary==579||ordinary==580||ordinary==581||ordinary==599||ordinary==600||ordinary==601||ordinary==645||ordinary==709||ordinary==773||ordinary==1665||ordinary==1666||ordinary==1697||ordinary==1733||ordinary==1782){
                registry.restore(fullOrdinary);assertNativeCapture(beforeOrdinary,h);step(h,actualInput);assertNativeCapture(afterOrdinary,h);
                registry.restoreCourse(courseOrdinary);assertNativeCapture(beforeOrdinary,h);step(h,actualInput);assertNativeCapture(afterOrdinary,h);
            }
        }
        assertTrue(targetSonicSelected,"Fixed1782 real target services retain native Blink/Draw/DPLC/next8 provenance without actor or art injection");
        assertEquals(15717,targetReturnedCall+1782);assertFalse(targetFrame.closed());
        var closedBeforeSpin=targetFrame.state().motion();assertEquals(2,closedBeforeSpin.routine());
        assertEquals(561324800,closedBeforeSpin.centreX16_16());assertEquals(35794432,closedBeforeSpin.centreY16_16());
        assertEquals(1523,closedBeforeSpin.xVelocity());assertEquals(626,closedBeforeSpin.yVelocity());
        assertEquals(1653,closedBeforeSpin.groundVelocity());assertEquals(16,closedBeforeSpin.angle());
        assertEquals(0,target.state().section().queue().headSource());assertEquals(0,target.state().section().remainingTilesWord());
        System.out.println("Controller-only Act2: WARP13885, actual R12B FULL13886, loading returned"+targetReturnedCall+
                ",1782 real target services returned through15717; native Projector/four later children, damage/LostRing prefix, Hurt/landing and fixedRight+B15601..15632. Full/course replay includes the actual damage, Hurt, landing, jump and later cuts. No new pixels, native/regional/audio or full-act parity claim.");
        // BEGIN measured native Spring/Boulder/Goal continuation through closed16187.
        for (int call = 15718; call <= 16187; call++) {
            var input = (call >= 16042 && call <= 16073 || call >= 16120 && call <= 16151)
                    ? afterProjectorJump : afterProjectorRight;
            boolean replay = call == 15718 || call == 16041 || call == 16042 || call == 16064
                    || call == 16102 || call == 16109 || call == 16119 || call == 16120 || call == 16160 || call == 16187;
            var beforeExtension = replay ? captureNative(h) : null;
            var fullExtension = replay ? registry.capture() : null;
            var courseExtension = replay ? registry.captureCourse() : null;
            try { step(h, input); }
            catch (RuntimeException | Error original) {
                throw new AssertionError("R12B promoted continuation last returned=" + (call - 1)
                        + ", first failed=" + call + ", actual player prefix=" + targetFrame.state(), original);
            }
            assertFalse(targetFrame.closed(), "Actual closed continuation call" + call);
            assertSame(targetFrame, module.requirePlayerFrame()); assertSame(world, h.mode().getWorldSession());
            assertEquals(generation + 1, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            assertEquals(call - targetReturnedCall, targetFrame.environment().globals().levelFrames());
            if (call == 16041) {
                assertEquals(0, targetFrame.state().motion().flags() & 2);
                assertEquals(0, targetFrame.state().motion().yVelocity());
            }
            if (call == 16042 || call == 16120) {
                assertEquals(1, targetFrame.state().motion().playerJump());
                assertNotEquals(0, targetFrame.state().motion().flags() & 2);
            }
            if (call == 16064) {
                var diagonal = targetHost.placement().pool().state(38);
                assertEquals(0x0A, diagonal.id()); assertEquals(0x14, diagonal.routine());
                assertTrue(targetFrame.state().motion().xVelocity() > 0, "Native jump cleared the diagonal Spring");
                assertTrue(targetHost.sounds().stream().noneMatch(SonicCdGameModule.PastSpringFm.class::isInstance));
            }
            if (call == 16102) {
                var actualGoal = targetHost.placement().pool().state(33);
                assertEquals(0x16, actualGoal.id()); assertEquals(4, actualGoal.routine());
                assertEquals(0, targetHost.placement().pool().state(32).id(),
                        "Goal completion submits PLC12; the later placed Signpost has not reached its spawn cut");
            }
            if (call == 16109) {
                var actualSignpost = targetHost.placement().pool().state(32);
                assertEquals(0x17, actualSignpost.id()); assertEquals(2, actualSignpost.routine());
                assertEquals(252, actualSignpost.savedFlagsId());
                assertEquals(10552, actualSignpost.centreX());
            }
            if (call == 16119) {
                assertEquals(0, targetFrame.state().motion().flags() & 2);
                assertEquals(0, targetFrame.state().motion().yVelocity());
                var boulder = targetHost.placement().pool().state(34);
                assertEquals(0x1B, boulder.id()); assertEquals(2, boulder.routine());
                assertEquals(10144, boulder.centreX());
            }
            if (replay) {
                var afterExtension = captureNative(h);
                registry.restore(fullExtension); assertNativeCapture(beforeExtension, h);
                step(h, input); assertNativeCapture(afterExtension, h);
                registry.restoreCourse(courseExtension); assertNativeCapture(beforeExtension, h);
                step(h, input); assertNativeCapture(afterExtension, h);
            }
        }
        assertTrue((targetFrame.state().motion().centreX16_16() >>> 16) > 10144,
                "Actual Boulder jump has passed its unchanged native solid centre");
        assertEquals(0x16, targetHost.placement().pool().state(33).id());
        assertEquals(4, targetHost.placement().pool().state(33).routine());
        assertEquals(0x17, targetHost.placement().pool().state(32).id());
        System.out.println("Controller-only R12B: returned16187 through real Spin, later Spring jump, Goal trigger"
                + ", native placed Signpost and fresh Boulder jump; ten further FULL/course replay cuts."
                + " This protected16187 prefix precedes the measured old-programme return below; no parity claim.");
        // END measured native Spring/Boulder/Goal continuation through closed16187.

        // BEGIN measured native BigRing/Results/old-programme return through closed16979.
        // Fixed held Right continues the existing measured recipe; no gameplay-value ingress.
        var endRegistry=h.mode().getRewindRegistry();var endSource=targetFrame.stageSource();
        for(int call=16188;call<=16979;call++) {
            boolean replay=call==16188||call==16220||call==16500||call==16954||call==16955
                    ||call==16956||call==16976||call==16977||call==16978||call==16979;
            var fullEnd=replay?endRegistry.capture():null;
            var courseEnd=replay?endRegistry.captureCourse():null;
            var nativeBeforeEnd=replay?captureNative(h):null;
            var actorBeforeEnd=targetFrame.captureActor();
            var clockBeforeEnd=targetFrame.vIntClock();
            int framesBeforeEnd=targetFrame.environment().globals().levelFrames();
            step(h,right);
            assertSame(targetFrame,module.requirePlayerFrame());assertSame(endSource,targetFrame.stageSource());
            assertSame(world,h.mode().getWorldSession());
            assertEquals(generation+1,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            assertFalse(targetFrame.closed());assertFalse(target.failed());
            if(call==16188) {
                var actualBigRing=targetHost.placement().pool().state(35);
                assertTrue(targetHost.globals().ring().rings()<50,"Actual native Ring-count gate, no constructed collection");
                assertEquals(0x14,actualBigRing.id());assertEquals(0,actualBigRing.routine());
                assertEquals(0,actualBigRing.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));
                assertTrue(targetHost.video().completedCpu().orElseThrow().provenance().stream().noneMatch(v->v.slotRef().wholeIndex()==35));
            }
            var phase=target.state().phase();
            var expectedPhase=call<16955?SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL
                    :call<16977?SonicCdAct2PastLoadingVisit.Phase.OLD_BLACK_FADE
                    :call==16977?SonicCdAct2PastLoadingVisit.Phase.OLD_CLEAR_WAIT
                    :call==16978?SonicCdAct2PastLoadingVisit.Phase.OLD_RETURNED
                    :SonicCdAct2PastLoadingVisit.Phase.OLD_IPX_RETURNED;
            assertEquals(expectedPhase,phase,"Actual native Results/old-exit phase at"+call);
            if(call>=16956) {
                assertEquals(actorBeforeEnd,targetFrame.captureActor(),"Fade/clear/IPX do not run Sonic Body");
                assertEquals(framesBeforeEnd,targetFrame.environment().globals().levelFrames());
            }
            if(call==16979)assertEquals(clockBeforeEnd,targetFrame.vIntClock(),"Semantic IPX blank does not pulse the old Frame");
            if(replay) {
                var fullAfterEnd=endRegistry.capture();var nativeAfterEnd=captureNative(h);
                endRegistry.restore(fullEnd);assertNativeCapture(nativeBeforeEnd,h);
                assertNaturalR12bSnapshotValue(fullEnd,endRegistry.capture(),"exit before FULL"+call);
                step(h,right);assertNativeCapture(nativeAfterEnd,h);
                assertNaturalR12bSnapshotValue(fullAfterEnd,endRegistry.capture(),"exit after FULL"+call);
                endRegistry.restoreCourse(courseEnd);assertNativeCapture(nativeBeforeEnd,h);
                assertNaturalR12bSnapshotValue(fullEnd,endRegistry.capture(),"exit before course"+call);
                step(h,right);assertNativeCapture(nativeAfterEnd,h);
                assertNaturalR12bSnapshotValue(fullAfterEnd,endRegistry.capture(),"exit after course"+call);
            }
        }
        assertEquals(2,targetFrame.environment().movement().gates().zoneAct());
        assertEquals(2,targetFrame.environment().movement().gates().timeZone());
        assertEquals(0,target.state().level().levelRestart());
        assertEquals(1,target.state().level().plcLoadFlags()&3);
        assertEquals(0,target.state().section().queue().headSource());
        assertEquals(0,target.state().section().remainingTilesWord());
        assertEquals(0xFF,module.act2PastOldProgrammeReturnCode());
        assertFalse(((SonicCdAct2PastWorldHost.Published)targetHost.selected()).selection().inputs().displayEnabled());
        assertEquals(new SonicCdGameModule.PastProgrammeSound(SonicCdGameModule.ProgramSound.STOP_FM),targetHost.sounds().getLast());
        System.out.println("R12B_NATURAL_END16979 {program:"+target.state().program()+",level:"+target.state().level()
                +",module:"+module.levelProgramState()+",clock:"+targetFrame.vIntClock()+",ring:"+targetHost.globals().ring()+"}");
        // BEGIN measured natural R13D loading/Boss route promotion through18294; author Java/EGL UNRUN.
        // Actual incoming goodFuture selects D. Fixed inputs never depend on observed state.
        assertEquals(0,target.state().program().goodFutureByte(),"Source prerequisite: actual incoming bad Future selection");
        assertEquals(0,target.state().program().specialStageByte(),"RunBossLevel must follow the genuine non-SpecialStage branch");
        var actualData=assertInstanceOf(CdGameDataSource.class,world.getDataSource());
        var futureAssets=actualData.act3FutureAssets().orElseThrow(()->new AssertionError("R13_NATIVE_PREREQUISITE: retained original C/D programmes absent"))
                .select(target.state().program().goodFutureByte());
        assertEquals(CdNativeFile.R13D,futureAssets.identity().file());
        assertEquals(actualData.assets().identity().observedWholeDiscSha256(),futureAssets.identity().observedWholeDiscSha256());
        long previousGeneration=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var servicesBeforeFuture=module.programServicesState();
        R13NaturalOwners futureOwners=null;
        var futurePhases=java.util.EnumSet.noneOf(SonicCdAct3FutureLoadingVisit.Phase.class);
        int firstReady=-1,firstSelectedSonic=-1,firstVisibleSonic=-1,firstTitlePlc=-1,firstServicedTitle=-1,firstBossFight=-1,firstBossPlc=-1;
        boolean title4Created=false,originalSonicInit=false,originalSonicMain=false,loadingReplay=false,deferredTitleServiced=false;
        for(int actualCall=16980;actualCall<=18294;actualCall++) {
            // Exact Root-measured recipe: B16/Right16 on17143..17782, then Right only.
            var actualInput=actualCall>17142&&actualCall<=17782&&(actualCall-17143)%32<16?jump:right;
            var r13Registry=h.mode().getRewindRegistry();boolean canReplay=module.act3FutureLoadingSelected();
            var fullBefore=canReplay?r13Registry.capture():null;var courseBefore=canReplay?r13Registry.captureCourse():null;
            var beforePhase=canReplay?module.act3FutureLoadingVisit().state().phase():null;
            int beforeRoutine=canReplay?module.requirePlayerFrame().state().motion().routine():-1;
            int beforeLevelFrame=canReplay?module.requirePlayerFrame().environment().globals().levelFrames():-1;
            var oldCpu=canReplay?module.act3FutureWorldHost().video().completedCpu():java.util.Optional.<com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat>empty();
            String beforeBoss=canReplay?naturalR13BossKey(futureOwners.host().placement().pool().capture()):"";
            int beforeFight=canReplay?futureOwners.frame().environment().movement().bounds().bossFight():-1;
            boolean beforeWork=canReplay&&(futureOwners.visit().state().section().queue().headSource()!=0
                    ||futureOwners.visit().state().section().remainingTilesWord()!=0);
            boolean fixedCut=actualCall==17762||actualCall==17782||actualCall==18028||actualCall==18294;
            boolean sampleBefore=firstServicedTitle<0||fixedCut||(actualCall-firstServicedTitle)%128==0;
            String beforeDiagnostic="closed"+(actualCall-1)+", phase="+beforePhase+", routine="+beforeRoutine
                    +", fight="+beforeFight+", Boss="+beforeBoss;
            // Actual prior selection; native Hurt/occlusion need not leave Sonic in later pictures.
            byte[] pixelsBefore=canReplay&&sampleBefore&&beforePhase==SonicCdAct3FutureLoadingVisit.Phase.READY
                    &&module.act3FutureWorldHost().selected() instanceof SonicCdAct3FutureWorldHost.Published p
                    &&p.selection().projection() instanceof SonicCdPastDisplay.Ready
                    ?(firstServicedTitle<0?drawNaturalR13Image(h,futureOwners):drawNaturalR13SelectedImage(h,futureOwners,actualCall-1)):null;
            if(canReplay)assertNaturalR13Snapshots(h,fullBefore,courseBefore,"before actual forward"+actualCall);
            try {step(h,actualInput);} // Same fixed input for the actual forward and both replay scopes.
            catch(RuntimeException|Error original) {
                System.out.println("R13_BOSS_ROUTE_FIRST_FAILURE actualCall="+actualCall+", actualInput="+actualInput+", lastClosed="+beforeDiagnostic+", original="+original);
                throw original;
            }
            assertTrue(module.act3FutureLoadingSelected(),"R13_NATIVE_PREREQUISITE: genuine old return did not select Future FULL");
            if(futureOwners==null) {
                var futureFrame=module.requirePlayerFrame();var futureVisit=module.act3FutureLoadingVisit();var futureHost=module.act3FutureWorldHost();
                futureOwners=new R13NaturalOwners(futureFrame,futureFrame.stageSource(),futureHost,futureVisit,
                        assertInstanceOf(CdWorld.class,actualField(module,"act3FutureWorld")),futureAssets,
                        assertInstanceOf(com.openggf.game.soniccd.level.SonicCdAct3FutureLevel.class,h.mode().getLevelManager().getCurrentLevel()),
                        world,Math.incrementExact(previousGeneration));
                assertTrue(targetFrame.closed());assertNotSame(targetFrame,futureFrame);assertNotSame(endSource,futureFrame.stageSource());
                var completed=module.programServicesState().completedCalls();var earlier=servicesBeforeFuture.completedCalls();
                assertEquals(earlier,completed.subList(0,earlier.size()),"Prior real programme calls remain an exact prefix");
                assertEquals(java.util.List.of(SonicCdGameModule.ProgramStartCall.LOAD_LEVEL_PCM,SonicCdGameModule.ProgramStartCall.INIT_VDP,
                        SonicCdGameModule.ProgramStartCall.INIT_CONTROLLERS),completed.subList(earlier.size(),completed.size()),
                        "Actual RunMMD/Start assembly, not a preloaded Frame fixture");
            }
            assertNaturalR13Owners(h,futureOwners);
            var core=futureOwners.visit().state();var phase=core.phase();futurePhases.add(phase);
            if(core.hudSlots().unsignedByte(3*64)==CdTitleCard.OBJECT_ID)title4Created=true;
            if(beforePhase==SonicCdAct3FutureLoadingVisit.Phase.READY) {
                assertFalse(futureOwners.frame().paused());
                assertEquals((beforeLevelFrame+1)&0xFFFF,futureOwners.frame().environment().globals().levelFrames(),"One original unpaused Main");
                if(beforeRoutine==0){assertEquals(2,futureOwners.frame().state().motion().routine());originalSonicInit=true;}
                else if(beforeRoutine==2) {
                    if(!deferredTitleServiced)assertEquals(2,futureOwners.frame().state().motion().routine(),"Original pre-release Title policy");
                    assertTrue(futureOwners.frame().display().isPresent());originalSonicMain=true;
                }
                var selected=assertInstanceOf(SonicCdAct3FutureWorldHost.Published.class,futureOwners.host().selected()).selection();
                assertNaturalR13SnapshotValue(oldCpu,selected.inputs().selectedCpu(),"Selector8 keeps actual prior CPU, never NOW");
                if(oldCpu.isPresent())assertSame(oldCpu.orElseThrow(),selected.inputs().selectedCpu().orElseThrow());
                assertEquals("DONE",actualField(actualField(actualField(futureOwners.ordinary(),"binding"),"objects"),"phase").toString(),"Original physical walk/drain completed");
            }
            var fullAfter=r13Registry.capture();var courseAfter=r13Registry.captureCourse();
            assertNaturalR13Snapshots(h,fullAfter,courseAfter,"after actual forward before pixels"+actualCall);
            boolean newlySelected=false,selectedSonic=false;R13NaturalPixels selectedPixels=null;
            if(phase==SonicCdAct3FutureLoadingVisit.Phase.READY) {
                if(firstReady<0)firstReady=actualCall;
                assertEquals(22,core.fromFadeSteps(),"Native22-source-step palette fade completed");
                var selected=assertInstanceOf(SonicCdAct3FutureWorldHost.Published.class,futureOwners.host().selected()).selection();
                if(firstServicedTitle>=0)assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection(),"Actual selected-current scene at"+actualCall+": "+selected.projection());
                if(selected.inputs().selectedCpu().isPresent()
                        &&selected.inputs().selectedCpu().orElseThrow().provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==0)) {
                    selectedSonic=true;
                    if(firstSelectedSonic<0){firstSelectedSonic=actualCall;newlySelected=true;}
                    if(firstVisibleSonic<0) {
                        selectedPixels=drawNaturalR13SelectedSonic(h,futureOwners,actualCall);
                        assertNaturalR13Snapshots(h,fullAfter,courseAfter,"after first selected-Sonic pixels"+actualCall);
                        if(!Arrays.equals(selectedPixels.rgba(),selectedPixels.withoutSonic())) {
                            firstVisibleSonic=actualCall;futureVisibleCallForExport=actualCall;futureImageForExport=selectedPixels.rgba();
                        }
                    }
                }
            }
            long titlePlcs=core.calls().stream().filter(c->c==SonicCdAct3FutureLoadingVisit.Call.TITLE_PLC_APPEND).count();
            assertTrue(titlePlcs<=1,"Original whole4 Title must not replay deferred PLC2");boolean newTitle=titlePlcs==1&&firstTitlePlc<0;
            if(newTitle){firstTitlePlc=actualCall;assertTrue(core.section().queue().headSource()!=0||core.section().remainingTilesWord()!=0,"Original deferred2 creates pending source work");}
            if(firstTitlePlc>=0&&actualCall>firstTitlePlc&&core.section().queue().headSource()==0&&core.section().remainingTilesWord()==0)deferredTitleServiced=true;
            boolean firstRelease=firstServicedTitle<0&&firstVisibleSonic>=0&&deferredTitleServiced&&futureOwners.frame().environment().controls().controlLocked()==0;
            if(firstRelease)firstServicedTitle=actualCall;
            int fight=futureOwners.frame().environment().movement().bounds().bossFight();
            if(firstBossFight<0&&fight!=0)firstBossFight=actualCall;
            long bossPlcs=core.calls().stream().filter(c->c==SonicCdAct3FutureLoadingVisit.Call.BOSS_PLC_APPEND).count();
            assertTrue(bossPlcs<=1,"Original parent Init may append PLC4 only once");boolean newBossPlc=bossPlcs==1&&firstBossPlc<0;
            if(newBossPlc){firstBossPlc=actualCall;assertTrue(core.section().queue().headSource()!=0||core.section().remainingTilesWord()!=0);}
            boolean work=core.section().queue().headSource()!=0||core.section().remainingTilesWord()!=0;
            String boss=naturalR13BossKey(futureOwners.host().placement().pool().capture());
            boolean changedBoss=!beforeBoss.equals(boss),changedFight=beforeFight!=fight;
            if(actualCall==17762||actualCall==17782) {
                var motion=futureOwners.frame().state().motion();var layout=futureOwners.source().terrain().layout();
                assertEquals(0x91,layout.cell(com.openggf.game.soniccd.level.CdStageTerrain.Plane.FOREGROUND,
                        (motion.centreX16_16()>>>16)>>>8,(motion.centreY16_16()>>>16)>>>8).rawByte(),"Original first loop placement");
            }
            R13BossPixels bossPixels=actualCall==18294?drawNaturalR13SelectedBoss(h,futureOwners):null;
            if(bossPixels!=null){bossImageForExport=bossPixels.rgba();bossSelectionForExport=bossPixels.selectedWholes().toString();}
            boolean replay=canReplay&&(phase!=beforePhase||newlySelected||newTitle||firstRelease||changedFight||changedBoss
                    ||newBossPlc||beforeWork&&!work||fixedCut);
            if(replay) {
                if(selectedPixels==null&&selectedSonic&&(firstServicedTitle<0||firstRelease))selectedPixels=drawNaturalR13SelectedSonic(h,futureOwners,actualCall);
                byte[] actualPixels=selectedPixels!=null?selectedPixels.rgba():phase==SonicCdAct3FutureLoadingVisit.Phase.READY&&firstServicedTitle>=0
                        ?drawNaturalR13SelectedImage(h,futureOwners,actualCall):null;
                assertNaturalR13Snapshots(h,fullAfter,courseAfter,"before FULL/course replay"+actualCall);
                for(boolean courseScope:new boolean[]{false,true}) {
                    if(courseScope)r13Registry.restoreCourse(courseBefore);else r13Registry.restore(fullBefore);
                    assertNaturalR13Owners(h,futureOwners);assertNaturalR13Snapshots(h,fullBefore,courseBefore,"restored before"+courseScope+"/"+actualCall);
                    if(pixelsBefore!=null)assertArrayEquals(pixelsBefore,firstServicedTitle>=0?drawNaturalR13SelectedImage(h,futureOwners,actualCall-1)
                            :drawNaturalR13Image(h,futureOwners),"Actual before-forward pixels restored");
                    assertNaturalR13Snapshots(h,fullBefore,courseBefore,"before replay forward"+courseScope+"/"+actualCall);
                    try {step(h,actualInput);}catch(RuntimeException|Error original) {
                        System.out.println("R13_BOSS_ROUTE_REPLAY_FAILURE actualCall="+actualCall+", course="+courseScope+", lastClosed="+beforeDiagnostic+", original="+original);throw original;
                    }
                    assertNaturalR13Owners(h,futureOwners);assertNaturalR13Snapshots(h,fullAfter,courseAfter,"after replay forward"+courseScope+"/"+actualCall);
                    if(selectedPixels!=null) {
                        var replayPixels=drawNaturalR13SelectedSonic(h,futureOwners,actualCall);
                        assertArrayEquals(selectedPixels.rgba(),replayPixels.rgba(),"Actual after-forward pixels restored");
                        assertArrayEquals(selectedPixels.withoutSonic(),replayPixels.withoutSonic(),"Same opening Sonic omission contrast");
                    } else if(actualPixels!=null)assertArrayEquals(actualPixels,drawNaturalR13SelectedImage(h,futureOwners,actualCall),"Actual selected scene repeats without requiring Sonic visibility");
                    if(bossPixels!=null) {
                        var replayBoss=drawNaturalR13SelectedBoss(h,futureOwners);assertEquals(bossPixels.selectedWholes(),replayBoss.selectedWholes());
                        assertArrayEquals(bossPixels.rgba(),replayBoss.rgba(),"Actual selected Boss image replays");
                        assertArrayEquals(bossPixels.withoutBoss(),replayBoss.withoutBoss(),"Same renderer-only Boss group omission replays");
                    }
                    assertNaturalR13Snapshots(h,fullAfter,courseAfter,"after replay pixels"+courseScope+"/"+actualCall);
                }
                if(phase!=SonicCdAct3FutureLoadingVisit.Phase.READY)loadingReplay=true;
            }
        }
        assertNotNull(futureOwners,"R13_NATIVE_PREREQUISITE: no retained Future FULL by finite18294");
        assertTrue(firstReady>=16980&&firstSelectedSonic>=firstReady&&firstVisibleSonic>=firstSelectedSonic,"Original Ready/selected/visible Sonic prerequisite");
        assertTrue(firstServicedTitle>=firstVisibleSonic&&firstTitlePlc>=firstReady,"Original Title2 and its actual queue must complete");
        assertTrue(title4Created&&originalSonicInit&&originalSonicMain&&loadingReplay,
                "Genuine whole4 Title creation, native Init/Main and loading checkpoint replay are required");
        assertTrue(futurePhases.contains(SonicCdAct3FutureLoadingVisit.Phase.FIRST_PLC));
        assertTrue(futurePhases.contains(SonicCdAct3FutureLoadingVisit.Phase.LEVEL_PLC));
        assertTrue(futurePhases.contains(SonicCdAct3FutureLoadingVisit.Phase.FROM_BLACK)
                ||futurePhases.contains(SonicCdAct3FutureLoadingVisit.Phase.FROM_WHITE));
        var finalCore=futureOwners.visit().state();
        for(var required:java.util.List.of(SonicCdAct3FutureLoadingVisit.Call.CLEAR_PLCS,SonicCdAct3FutureLoadingVisit.Call.FIRST_WAIT,
                SonicCdAct3FutureLoadingVisit.Call.LEVEL_WAIT,SonicCdAct3FutureLoadingVisit.Call.IMMEDIATE_LEVEL,
                SonicCdAct3FutureLoadingVisit.Call.IMMEDIATE_STANDARD,SonicCdAct3FutureLoadingVisit.Call.TITLE_CARD_CREATE,
                SonicCdAct3FutureLoadingVisit.Call.BEGIN_MAIN,SonicCdAct3FutureLoadingVisit.Call.LOAD_PLAYER,
                SonicCdAct3FutureLoadingVisit.Call.FROM_FADE_SERVICE,SonicCdAct3FutureLoadingVisit.Call.BEGIN_LEVEL,
                SonicCdAct3FutureLoadingVisit.Call.TITLE_PLC_APPEND))assertTrue(finalCore.calls().contains(required),"Original loading call required: "+required);
        assertEquals(22,finalCore.calls().stream().filter(c->c==SonicCdAct3FutureLoadingVisit.Call.FROM_FADE_SERVICE).count());
        assertNaturalR13Owners(h,futureOwners);
        var finalFull=h.mode().getRewindRegistry().capture();var finalCourse=h.mode().getRewindRegistry().captureCourse();
        assertEquals(17142,firstServicedTitle,"Qualified original Title/queue/unlock cut");
        assertEquals(18028,firstBossFight,"Original Boss Init/fight cut");assertEquals(firstBossFight,firstBossPlc);
        assertEquals(0,finalCore.section().queue().headSource());assertEquals(0,finalCore.section().remainingTilesWord());
        assertNotNull(bossImageForExport);assertNotNull(bossSelectionForExport);
        assertArrayEquals(bossImageForExport,drawNaturalR13SelectedBoss(h,futureOwners).rgba());
        assertNaturalR13Snapshots(h,finalFull,finalCourse,"final18294 state before retirement");
        retireNaturalR13Renderer(h,futureOwners);
        System.out.println("ENGINE original-USA natural same-World R13D: firstReady="+firstReady+", firstSelectedSonic="+firstSelectedSonic
                +", firstVisibleSonic="+firstVisibleSonic+", originalTitle2="+firstTitlePlc+", titleServiced="+firstServicedTitle
                +", actualBossFight="+firstBossFight+", originalBossPLC4="+firstBossPlc+", endpoint18294 selectedBoss="+bossSelectionForExport
                +"; exact fixed ordinary input; original source/current/priorCPU, complete FULL/course and selected pixel replay. No defeat/Results/full-act/native-image/audio/regional/parity claim.");
        // END measured natural R13D loading/Boss route promotion through18294.
        // END measured native BigRing/Results/old-programme return through closed16979.
        // END measured natural TimeWarp230 old exit.
        // END measured natural TimeWarp save continuation.
        // END measured Swing promotion.
        } finally {fixture.cleanup();}
        // Export is optional and occurs after all module/session/graphics/EGL/fixture cleanup.
        writeOptionalNaturalR13Capture(futureImageForExport,futureVisibleCallForExport);
        writeOptionalNaturalR13BossCapture(bossImageForExport,bossSelectionForExport);
    }

    // BEGIN natural R13D selected Boss comparison helpers 20261107; no gameplay/publication authority.
    private record R13BossPixels(byte[] rgba,byte[] withoutBoss,java.util.List<Integer> selectedWholes) {
        private R13BossPixels {rgba=rgba.clone();withoutBoss=withoutBoss.clone();selectedWholes=java.util.List.copyOf(selectedWholes);}
        @Override public byte[] rgba(){return rgba.clone();}
        @Override public byte[] withoutBoss(){return withoutBoss.clone();}
    }
    /** Same bounded native ID/routine key as the frozen observer; comparison only, never input selection. */
    private static String naturalR13BossKey(com.openggf.game.soniccd.objects.CdObjectPool.Snapshot pool) {
        var key=new java.util.StringJoiner(",");for(var slot:pool.slots())if(slot.id()>=0x2A&&slot.id()<=0x32)
            key.add(slot.identity().wholeIndex()+":"+Integer.toHexString(slot.id())+"/"+slot.routine()+"/"
                    +slot.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.ROUTINE_2));return key.toString();
    }
    /** Actual selected-current source proof from the frozen observer, without requiring Sonic provenance. */
    private static byte[] drawNaturalR13SelectedImage(Harness h,R13NaturalOwners o,int call) throws Exception {
        assertNaturalR13Owners(h,o);var selected=assertInstanceOf(SonicCdAct3FutureWorldHost.Published.class,o.host().selected()).selection();var inputs=selected.inputs();
        var scene=assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection(),"Actual selected scene at"+call+": "+selected.projection()).scene();
        var cpu=inputs.selectedCpu().orElseThrow(()->new AssertionError("Actual selected prior CPU absent at"+call));assertArrayEquals(cpu.bytes(),inputs.selectedSat().copy());
        assertEquals(inputs.displayEnabled(),scene.displayEnabled());assertSame(o.host().video().current(),inputs.current());
        assertTrue(o.host().geometryReady());assertEquals(o.assets().range(com.openggf.game.soniccd.level.CdR13FutureAssets.Asset.LEVEL_COLLISION).nativeAddress(),o.host().collisionPointer().orElseThrow());
        assertArrayEquals(o.assets().terrain().blocks().copy(),o.visit().state().blocksRam().orElseThrow().copy());assertArrayEquals(o.assets().terrain().layout().ram().copy(),o.visit().state().layoutRam().orElseThrow().copy());
        assertArrayEquals(o.frame().artState().cpuBuffer().copy(),inputs.player().cpuBuffer().copy());assertArrayEquals(o.frame().artState().residentBank().copy(),inputs.player().residentBank().copy());
        assertEquals(o.frame().artState().lastFrame(),inputs.player().lastFrame());assertEquals(o.frame().artState().dirty(),inputs.player().dirty());
        assertArrayEquals(inputs.palette().copy(),scene.selectedPalette128());assertEquals(inputs.vscrollLongBits(),scene.vscrollLongBits());assertEquals(0x20,scene.selectedBackdropPaletteIndex());
        var fg=scene.foreground().words64x32();var bg=scene.background().words64x32();for(int y=0;y<32;y++)for(int x=0;x<64;x++){assertEquals(inputs.foreground().word(x,y),fg[y*64+x]);assertEquals(inputs.background().word(x,y),bg[y*64+x]);}
        var hs=scene.packedHscroll256();for(int i=0;i<256;i++)assertEquals((int)inputs.hscroll().unsignedLong(i*4),hs[i]);
        for(var entry:scene.nativePatterns().entrySet()){int id=entry.getKey();var raw=id>=0x780&&id<0x798?inputs.player().residentBank().slice((id-0x780)*32,32):com.openggf.game.soniccd.resources.CdR13ArtResidency.readTile(inputs.current(),id);assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.patternVersion(raw),entry.getValue());}
        assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.convert(cpu,id->new com.openggf.game.soniccd.resources.CdObjectPresentation.TilePattern(PatternAtlasRange.OBJECTS.base()+id,java.util.Objects.requireNonNull(scene.nativePatterns().get(id)))),scene.sprites());
        byte[] image=drawNaturalR13Image(h,o);assertArrayEquals(image,drawNaturalR13Image(h,o),"Repeated actual renderer selected pixels");return image;
    }
    /** Native Eggman Init/child declarations and live original routine/mapping/tile joins at the measured cut.
     * N1ecc: EggmanObject_0_Routine0 (src/objects/r1/boss.asm:307-330), sub_20BD36 (:346-470),
     * EggmanObject_0_RoutineC/4 (:502-571). Physical slots/routines are Root's measured output assertions. */
    private static java.util.List<Integer> assertNaturalR13BossEndpoint(R13NaturalOwners o,
            com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat cpu) throws Exception {
        var metadata=com.openggf.game.soniccd.level.CdR13BossAssets.bind(o.assets());assertTrue(metadata.ownsAssets(o.assets()));
        assertEquals(o.assets().identity(),metadata.identity());
        var live=o.host().placement().pool().capture().slots().stream().filter(slot->slot.id()>=0x2A&&slot.id()<=0x32).toList();
        assertEquals(java.util.List.of(32,37,38,39,40,41,42,43,44,45,46,47,48,49,50),live.stream().map(slot->slot.identity().wholeIndex()).toList());
        assertEquals(0x2A,live.getFirst().id());assertEquals(4,live.getFirst().routine());assertEquals(342,live.getFirst().centreY());
        assertEquals(14,metadata.children().size());
        assertEquals(metadata.children().stream().map(child->child.objectId()).toList(),live.subList(1,live.size()).stream().map(slot->slot.id()).toList(),
                "All original native child declarations, in their actual allocation/physical order");
        for(int n=0;n<metadata.children().size();n++)assertEquals(n,metadata.children().get(n).ordinal());
        assertEquals(java.util.List.of(4,2,12,2,4,16,4,10,2,8,2,2,4,2,2),live.stream().map(slot->slot.routine()).toList(),"Measured original parent/child routines");
        var parts=com.openggf.game.soniccd.level.CdR13BossAssets.Part.values();var visuals=com.openggf.game.soniccd.level.CdR13BossAssets.Visual.values();
        for(var slot:live) {
            var part=parts[slot.id()-0x2A];assertEquals(slot.id(),part.nativeId());var body=metadata.body(part);
            assertEquals(0,slot.routine()&1);assertTrue(slot.routine()/2<body.routineTargets().size(),"Actual native routine-directory bound");
            int visualIndex=slot.id()==0x32?9:slot.id()==0x31?(slot.identity().wholeIndex()==49?8:7):slot.id()-0x2A;
            var visual=visuals[visualIndex];assertEquals(body.tileWord(),visual.baseTileWord());
            assertEquals(visual.baseTileWord(),slot.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE));
            assertEquals(metadata.range(visual.mappingTable()).nativeAddress(),slot.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS),
                    "Original selected programme's mapping root, including the two distinct native forearms");
        }
        assertEquals(1,o.frame().environment().movement().bounds().bossFight());assertEquals(1,o.host().globals().bossFlagsByte().orElseThrow());
        assertEquals(1,o.visit().state().calls().stream().filter(call->call==SonicCdAct3FutureLoadingVisit.Call.BOSS_PLC_APPEND).count());
        assertEquals(0,o.visit().state().section().queue().headSource());assertEquals(0,o.visit().state().section().remainingTilesWord());
        var liveWholes=live.stream().map(slot->slot.identity().wholeIndex()).collect(java.util.stream.Collectors.toSet());
        var selected=cpu.provenance().stream().map(piece->piece.slotRef().wholeIndex()).filter(liveWholes::contains).distinct().toList();
        assertEquals(java.util.List.of(40,39,44,47,46,43,45,32,37,38,42,50,41,49),selected,"Original selected prior-CPU Boss order, not physical order or NOW");
        assertFalse(selected.contains(48),"The live second arm is genuinely absent from this selected prior CPU");
        assertEquals(java.util.Set.of(0x2A,0x2B,0x2C,0x2D,0x2E,0x2F,0x30,0x31,0x32),live.stream().filter(slot->selected.contains(slot.identity().wholeIndex())).map(slot->slot.id()).collect(java.util.stream.Collectors.toSet()));
        return selected;
    }
    /** One stable filter over the ORIGINAL CPU/Scene. Never repeatedly omit with mismatched tile offsets. */
    private static RetainedPlaneSceneRenderer.Scene omitNaturalR13BossGroup(RetainedPlaneSceneRenderer.Scene scene,
            com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat cpu,java.util.Set<Integer> wholes) {
        assertFalse(wholes.isEmpty());byte[] raw=cpu.bytes();var tiles=new java.util.ArrayList<SpritePresentation.Tile>();int at=0;
        for(int index=cpu.spriteCount()-1;index>=0;index--) {
            int size=raw[index*8+2]&15,count=(((size>>>2)&3)+1)*((size&3)+1);
            if(!wholes.contains(cpu.provenance().get(index).slotRef().wholeIndex()))tiles.addAll(scene.sprites().tiles().subList(at,at+count));
            at+=count;
        }
        assertEquals(scene.sprites().tiles().size(),at);assertTrue(tiles.size()<at,"Selected Boss group must own original sprite entries");
        var picture=new SpritePresentation.Frame(tiles,scene.sprites().primitives(),scene.sprites().patternVersions());
        var omitted=new RetainedPlaneSceneRenderer.Scene(scene.foreground(),scene.background(),scene.packedHscroll256(),scene.vscrollLongBits(),
                scene.selectedPalette128(),scene.displayEnabled(),picture,scene.nativePatterns(),scene.selectedBackdropPaletteIndex());
        assertNaturalR12bOmissionPartitions(scene,omitted);assertEquals(scene.sprites().primitives(),omitted.sprites().primitives());return omitted;
    }
    /** Real current planes/SAT/palette/backdrop and renderer-only Boss omission; no gameplay image is published. */
    private static R13BossPixels drawNaturalR13SelectedBoss(Harness h,R13NaturalOwners o) throws Exception {
        assertNaturalR13Owners(h,o);assertEquals(SonicCdAct3FutureLoadingVisit.Phase.READY,o.visit().state().phase());
        var registry=h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
        var placement=o.host().placement().capture();var clock=o.frame().vIntClock();var art=o.frame().artState();var publication=o.host().selected();
        var selected=assertInstanceOf(SonicCdAct3FutureWorldHost.Published.class,publication).selection();var inputs=selected.inputs();
        var scene=assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection()).scene();
        var cpu=inputs.selectedCpu().orElseThrow();var wholes=assertNaturalR13BossEndpoint(o,cpu);
        byte[] actual=drawNaturalR13SelectedImage(h,o,18294);assertTrue(scene.displayEnabled());assertFalse(scene.nativePatterns().isEmpty());
        var binding=actualField(o.host(),"render");var renderer=assertInstanceOf(RetainedPlaneSceneRenderer.class,actualField(binding,"renderer"));
        assertSame(o.world(),actualField(binding,"world"));assertSame(o.level(),actualField(binding,"level"));assertEquals(o.generation(),actualField(binding,"generation"));
        assertSame(GameServices.graphics(),actualField(binding,"graphics"));assertSame(GameServices.graphics().getPatternAtlas(),actualField(binding,"atlas"));
        assertSame(h.mode().getCamera(),actualField(binding,"camera"));assertSame(Thread.currentThread(),actualField(binding,"thread"));
        assertEquals("surfaceless-egl",actualField(binding,"backend"));assertSame(org.lwjgl.opengl.GL.getCapabilities(),actualField(binding,"capabilities"));
        var omitted=omitNaturalR13BossGroup(scene,cpu,java.util.Set.copyOf(wholes));var camera=h.mode().getCamera();long epoch=o.level().currentEpoch();
        clearPixels();renderer.draw(omitted,camera.getX(),camera.getY(),h.module()::validateAct3FutureLoadingCut);byte[] without=pixels();
        assertFalse(Arrays.equals(actual,without),"Fixed18294 selected Boss group must contribute visible pixels; no alternative cut/search");
        clearPixels();renderer.draw(omitted,camera.getX(),camera.getY(),h.module()::validateAct3FutureLoadingCut);assertArrayEquals(without,pixels());
        assertEquals(epoch,o.level().currentEpoch());assertSame(publication,o.host().selected());assertSame(inputs.current(),o.host().video().current());
        assertSame(art,o.frame().artState());assertEquals(clock,o.frame().vIntClock());
        assertNaturalR13SnapshotValue(placement,o.host().placement().capture(),"Boss omission complete placement/pool/status/saved nonwrites");
        assertNaturalR13Snapshots(h,full,course,"Boss omission complete FULL/course nonwrites");
        assertArrayEquals(actual,drawNaturalR13SelectedImage(h,o,18294),"Original selected scene redraw");
        assertNaturalR13Snapshots(h,full,course,"Boss original image restored without gameplay publication");
        return new R13BossPixels(actual,without,wholes);
    }
    /** Optional ENGINE output only after every live fixture/session/graphics/EGL owner has closed. */
    private static void writeOptionalNaturalR13BossCapture(byte[] rgba,String selectedWholes) throws Exception {
        String output=System.getenv("SONICCD_R13_BOSS_OUTPUT");if(output==null||output.isBlank())return;
        assertNotNull(rgba);assertEquals(320*224*4,rgba.length);assertNotNull(selectedWholes);
        var directory=Path.of(output);assertTrue(directory.isAbsolute());assertEquals(directory,directory.normalize());assertEquals(output,directory.toString());
        var owned=Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD","captures","engine").toAbsolutePath().normalize();
        assertTrue(directory.startsWith(owned)&&!directory.equals(owned),"Optional Boss export requires its own canonical child of captures/engine");
        // Check every existing component and both destinations BEFORE creating even a directory.
        for(var ancestor=directory;ancestor!=null;ancestor=ancestor.getParent()) {
            assertFalse(Files.isSymbolicLink(ancestor),"No symlink traversal before capture writes");
            if(Files.exists(ancestor,java.nio.file.LinkOption.NOFOLLOW_LINKS))assertTrue(Files.isDirectory(ancestor,java.nio.file.LinkOption.NOFOLLOW_LINKS));
        }
        String stem="ENGINE-R13D-natural-Boss-call-18294";var png=directory.resolve(stem+".png");var text=directory.resolve(stem+".txt");
        assertFalse(Files.exists(png,java.nio.file.LinkOption.NOFOLLOW_LINKS));assertFalse(Files.exists(text,java.nio.file.LinkOption.NOFOLLOW_LINKS));
        Files.createDirectories(directory);assertEquals(directory,directory.toRealPath());assertEquals(owned,owned.toRealPath());
        writeEngineCapturePng(rgba,png);
        Files.writeString(text,"ENGINE OpenGGF original-USA actual same-World R13D at returned call18294.\n"
                +"Original route through17142; fixed Right+B16/Right16 through17782, then Right only through18294.\n"
                +"Actual parent32 ID2A/routine4/Y342 and original native children; selected prior-CPU Boss wholes="+selectedWholes+".\n"
                +"Actual selected current planes/SAT/palette/backdrop; stable renderer-only Boss group omission changes visible pixels; FULL/course/pixel replay and atlas retirement.\n"
                +"Written after module/session/graphics/EGL/fixture cleanup. No defeat/Results/native screenshot/full-act/audio/regional/parity claim.\n");
    }
    // END natural R13D selected Boss comparison helpers 20261107.

    // BEGIN finite natural R13D comparison/render helpers 20261106.
    /** Comparison-only retained actual owners, never input to a production factory or publication. */
    private record R13NaturalOwners(com.openggf.game.soniccd.player.CdPlayerFrameRuntime frame,
            com.openggf.game.soniccd.level.CdStagePlayerSource source,SonicCdAct3FutureWorldHost host,
            SonicCdAct3FutureLoadingVisit visit,CdWorld ordinary,
            com.openggf.game.soniccd.level.CdR13FutureAssets assets,com.openggf.game.soniccd.level.SonicCdAct3FutureLevel level,
            Object world,long generation) { }
    private record R13NaturalPixels(byte[] rgba,byte[] withoutSonic) {
        private R13NaturalPixels {rgba=rgba.clone();withoutSonic=withoutSonic.clone();}
        @Override public byte[] rgba(){return rgba.clone();}
        @Override public byte[] withoutSonic(){return withoutSonic.clone();}
    }
    private static void assertNaturalR13Owners(Harness h,R13NaturalOwners o) throws Exception {
        assertNotNull(o);var m=h.module();m.validateAct3FutureLoadingCut();o.visit().requireServiceIdle();
        assertTrue(m.act3FutureLoadingSelected());assertFalse(m.act2PastLoadingSelected());assertFalse(m.act2LoadingSelected());
        assertSame(o.frame(),m.requirePlayerFrame());assertSame(o.source(),o.frame().stageSource());assertFalse(o.frame().closed());
        assertSame(o.host(),m.act3FutureWorldHost());assertSame(o.visit(),m.act3FutureLoadingVisit());assertSame(o.assets(),o.visit().assets());
        assertSame(o.ordinary(),actualField(m,"act3FutureWorld"));assertFalse(o.ordinary().isOrdinaryRunning());assertFalse(o.visit().failed());
        assertSame(o.world(),h.mode().getWorldSession());assertSame(m,h.mode().getWorldSession().rootGameModule());
        assertSame(m,h.mode().getWorldSession().resolvedGameModule());assertSame(m.actualAct3RegistrySource(),h.mode().getWorldSession().getDataSource());
        assertSame(o.assets(),m.actualAct3RegistryAssets());assertSame(o.level(),h.mode().getLevelManager().getCurrentLevel());assertSame(o.assets(),o.level().assets());
        assertEquals(o.generation(),h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertEquals(0,h.mode().getLevelManager().getCurrentZone());assertEquals(0,h.mode().getWorldSession().getCurrentZone());
        assertEquals(2,h.mode().getLevelManager().getCurrentAct());assertEquals(2,h.mode().getWorldSession().getCurrentAct());
        assertEquals(2,h.mode().getLevelManager().getApparentAct());assertEquals(2,h.mode().getWorldSession().getApparentAct());
        assertTrue(o.source().ownsAct3Future(o.assets()));assertTrue(o.host().placement().ownsAct3FutureAssets(o.assets()));
        assertSame(o.assets().placements(),o.host().placement().placements());
        assertEquals(CdStageKey.parse("R13D"),o.source().stage());assertEquals(CdReleaseVariant.USA,o.assets().identity().variant());
        assertEquals(CdNativeFile.R13D,o.assets().identity().file());assertEquals(0,o.host().selectedGoodFutureByte());
        assertEquals(2,o.frame().environment().movement().gates().zoneAct());assertEquals(2,o.frame().environment().movement().gates().timeZone());
        assertEquals(o.visit().state().program().specialStageByte(),m.levelProgramState().specialStage());
        assertEquals(o.visit().state().program().timeStonesByte(),m.levelProgramState().timeStones());
        assertSame(o.visit(),actualField(o.host(),"owner"));assertSame(o.ordinary(),actualField(o.host(),"world"));
        assertSame(o.frame(),actualField(o.host(),"frame"));assertSame(o.assets(),actualField(o.host(),"assets"));
        assertSame(actualField(m,"act3FutureArt"),actualField(o.host(),"art"));
        assertTrue(o.host().video().current().ownsAssets(o.assets()));assertTrue(o.host().video().current().hasNativeHudProfile());
        assertSame(o.visit().state().section().residency(),o.host().video().current());
        o.frame().captureActor();o.frame().captureShared(); // Existing actual closed-Frame guard, no fabricated captures.
    }
    private static void assertNaturalR13Snapshots(Harness h,Object full,Object course,String label) throws Exception {
        var registry=h.mode().getRewindRegistry();
        assertNaturalR13SnapshotValue(full,registry.capture(),label+" FULL");
        assertNaturalR13SnapshotValue(course,registry.captureCourse(),label+" course");
    }
    /** Extend comparison only for the new opaque snapshot. Existing checkpoint/COW rules remain the sole Level rule. */
    private static void assertNaturalR13SnapshotValue(Object expected,Object actual,String label) throws Exception {
        if(expected instanceof com.openggf.game.rewind.CompositeSnapshot e) {
            var a=assertInstanceOf(com.openggf.game.rewind.CompositeSnapshot.class,actual);
            assertNaturalR13SnapshotValue(e.entries(),a.entries(),label);return;
        }
        if(expected instanceof java.util.Map<?,?> e) {
            var a=assertInstanceOf(java.util.Map.class,actual);assertEquals(e.keySet(),a.keySet(),label+" complete owner keys");
            for(var key:e.keySet())assertNaturalR13SnapshotValue(e.get(key),a.get(key),label+"["+key+"]");return;
        }
        if(expected instanceof SonicCdAct3FutureLoadingVisit.Snapshot) {
            assertEquals(expected.getClass(),actual.getClass());var names=new java.util.TreeSet<String>();
            for(var f:expected.getClass().getDeclaredFields()) {
                if(java.lang.reflect.Modifier.isStatic(f.getModifiers()))continue;names.add(f.getName());f.setAccessible(true);
                assertNaturalR13SnapshotValue(f.get(expected),f.get(actual),label+"."+f.getName());
            }
            assertEquals(java.util.Set.of("owner","state","actor","shared","host"),names,"No opaque R13 snapshot field may be omitted");return;
        }
        if(expected!=null&&expected.getClass().getName().equals("com.openggf.game.soniccd.SonicCdGameModule$Act3FutureShared")) {
            assertEquals(expected.getClass(),actual.getClass());var names=new java.util.TreeSet<String>();
            for(var c:expected.getClass().getRecordComponents()) {
                names.add(c.getName());var getter=c.getAccessor();getter.setAccessible(true);
                assertNaturalR13SnapshotValue(getter.invoke(expected),getter.invoke(actual),label+"."+c.getName());
            }
            assertEquals(java.util.Set.of("loading","services","goodFutureFlags","ipxProgress"),names,"Complete actual module shared capture");return;
        }
        assertNaturalR12bSnapshotValue(expected,actual,label);
    }
    /** Ordinary production overlay only; neither pixel collection nor comparison changes the retained selected image. */
    private static byte[] drawNaturalR13Image(Harness h,R13NaturalOwners o) throws Exception {
        assertNaturalR13Owners(h,o);long epoch=o.level().currentEpoch();var selected=o.host().selected();
        var full=h.mode().getRewindRegistry().capture();var course=h.mode().getRewindRegistry().captureCourse();
        long drawEpoch=o.level().currentEpoch();var actual=draw(h);
        assertEquals(drawEpoch,o.level().currentEpoch(),"R13 draw may not advance the live COW epoch");
        assertTrue(drawEpoch>=epoch);assertSame(selected,o.host().selected());assertNaturalR13Owners(h,o);
        assertNaturalR13Snapshots(h,full,course,"production R13 draw nonwrites");return actual;
    }
    /** Pixel oracle is renderer-only Sonic omission against the exact immutable selected Scene, not a golden/native screenshot. */
    private static R13NaturalPixels drawNaturalR13SelectedSonic(Harness h,R13NaturalOwners o,int call) throws Exception {
        assertNaturalR13Owners(h,o);assertEquals(SonicCdAct3FutureLoadingVisit.Phase.READY,o.visit().state().phase());
        var selected=assertInstanceOf(SonicCdAct3FutureWorldHost.Published.class,o.host().selected()).selection();var inputs=selected.inputs();
        var scene=assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection(),"R13_NATIVE_PREREQUISITE: selected-native Ready image at actual"+call+": "+selected.projection()).scene();
        var cpu=inputs.selectedCpu().orElseThrow(()->new AssertionError("R13_NATIVE_PREREQUISITE: no original prior CPU at"+call));
        assertTrue(cpu.provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==0),"Actual selected prior CPU must contain Sonic0");
        assertArrayEquals(cpu.bytes(),inputs.selectedSat().copy());assertTrue(inputs.displayEnabled());assertTrue(scene.displayEnabled());
        assertTrue(o.host().geometryReady());assertEquals(o.assets().range(com.openggf.game.soniccd.level.CdR13FutureAssets.Asset.LEVEL_COLLISION).nativeAddress(),o.host().collisionPointer().orElseThrow());
        assertArrayEquals(o.assets().terrain().blocks().copy(),o.visit().state().blocksRam().orElseThrow().copy());
        assertArrayEquals(o.assets().terrain().layout().ram().copy(),o.visit().state().layoutRam().orElseThrow().copy());
        assertSame(o.host().video().current(),inputs.current());
        assertArrayEquals(o.frame().artState().cpuBuffer().copy(),inputs.player().cpuBuffer().copy());
        assertArrayEquals(o.frame().artState().residentBank().copy(),inputs.player().residentBank().copy());
        assertEquals(o.frame().artState().lastFrame(),inputs.player().lastFrame());assertEquals(o.frame().artState().dirty(),inputs.player().dirty());
        var fg=scene.foreground().words64x32();var bg=scene.background().words64x32();
        for(int y=0;y<32;y++)for(int x=0;x<64;x++) {
            assertEquals(inputs.foreground().word(x,y),fg[y*64+x],"Selected physical FG descriptor");
            assertEquals(inputs.background().word(x,y),bg[y*64+x],"Selected physical BG descriptor");
        }
        var packed=scene.packedHscroll256();for(int i=0;i<256;i++)assertEquals((int)inputs.hscroll().unsignedLong(i*4),packed[i],"Full selected HScroll including retained tail");
        assertEquals(inputs.vscrollLongBits(),scene.vscrollLongBits());assertArrayEquals(inputs.palette().copy(),scene.selectedPalette128());
        assertEquals(0x20,scene.selectedBackdropPaletteIndex(),"Source-declared LevelStart line2/color0 selection, no guessed pixel color");
        assertFalse(scene.nativePatterns().isEmpty());
        for(var entry:scene.nativePatterns().entrySet()) {
            int id=entry.getKey();var raw=id>=0x780&&id<0x798?inputs.player().residentBank().slice((id-0x780)*32,32)
                    :com.openggf.game.soniccd.resources.CdR13ArtResidency.readTile(inputs.current(),id);
            assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.patternVersion(raw),entry.getValue(),"Actual current-native bytes for selected pattern"+id);
        }
        assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.convert(cpu,id->
                new com.openggf.game.soniccd.resources.CdObjectPresentation.TilePattern(PatternAtlasRange.OBJECTS.base()+id,
                        java.util.Objects.requireNonNull(scene.nativePatterns().get(id)))),scene.sprites(),"Exact original selected SAT geometry and current versions");
        var full=h.mode().getRewindRegistry().capture();var course=h.mode().getRewindRegistry().captureCourse();
        var actual=drawNaturalR13Image(h,o);assertArrayEquals(actual,drawNaturalR13Image(h,o));
        var binding=actualField(o.host(),"render");var renderer=assertInstanceOf(RetainedPlaneSceneRenderer.class,actualField(binding,"renderer"));
        assertSame(o.world(),actualField(binding,"world"));assertSame(o.level(),actualField(binding,"level"));assertEquals(o.generation(),actualField(binding,"generation"));
        assertSame(GameServices.graphics(),actualField(binding,"graphics"));assertSame(GameServices.graphics().getPatternAtlas(),actualField(binding,"atlas"));
        assertSame(h.mode().getCamera(),actualField(binding,"camera"));assertSame(Thread.currentThread(),actualField(binding,"thread"));
        assertEquals("surfaceless-egl",actualField(binding,"backend"));assertSame(org.lwjgl.opengl.GL.getCapabilities(),actualField(binding,"capabilities"));
        var omitted=TestSonicCdPastResultsRoute.omitWhole(scene,cpu,0);assertNaturalR12bOmissionPartitions(scene,omitted);
        var camera=h.mode().getCamera();long epoch=o.level().currentEpoch();clearPixels();
        renderer.draw(omitted,camera.getX(),camera.getY(),h.module()::validateAct3FutureLoadingCut);var without=pixels();
        assertEquals(epoch,o.level().currentEpoch());assertNaturalR13Snapshots(h,full,course,"Sonic omission nonwrites");
        assertSame(selected,assertInstanceOf(SonicCdAct3FutureWorldHost.Published.class,o.host().selected()).selection());
        assertArrayEquals(actual,drawNaturalR13Image(h,o));assertNaturalR13Snapshots(h,full,course,"selected image reinstated without gameplay publication");
        return new R13NaturalPixels(actual,without);
    }
    /** Existing renderer retirement, plus a GPU-only unrelated producer retained until its graphics owner closes. */
    private static void retireNaturalR13Renderer(Harness h,R13NaturalOwners o) throws Exception {
        var graphics=GameServices.graphics();var atlas=graphics.getPatternAtlas();var binding=actualField(o.host(),"render");
        var renderer=assertInstanceOf(RetainedPlaneSceneRenderer.class,actualField(binding,"renderer"));
        var touched=assertInstanceOf(java.util.Map.class,actualField(renderer,"touched"));var installed=new java.util.ArrayList<Integer>();
        for(var id:touched.keySet()){installed.add(assertInstanceOf(Integer.class,id));assertNotNull(atlas.getEntry((Integer)id));}
        assertFalse(installed.isEmpty());
        atlas.registerRange(PatternAtlasRange.HUD);int other=PatternAtlasRange.HUD.base()+19;
        if(atlas.getEntry(other)==null)graphics.cachePatternTexture(new com.openggf.level.Pattern(),other); // GPU-only contrast; no gameplay/source art.
        var unrelated=atlas.getEntry(other);var sample=SpritePresentation.patternSample(graphics,other);
        var mask=graphics.getTilePriorityFBO();assertNotNull(mask);int texture=mask.getTextureId();
        SessionManager.closeGameplaySession();
        assertTrue(o.frame().closed());assertTrue((boolean)actualField(o.host(),"closed"));assertNull(actualField(o.host(),"render"));
        assertTrue((boolean)actualField(renderer,"closed"));assertTrue(touched.isEmpty());
        for(int id:installed){assertNull(atlas.getEntry(id));assertNull(SpritePresentation.patternSample(graphics,id));}
        assertSame(unrelated,atlas.getEntry(other));assertEquals(sample,SpritePresentation.patternSample(graphics,other));
        assertSame(mask,graphics.getTilePriorityFBO());assertTrue(glIsTexture(texture));
    }
    /** ENGINE output only after session/module/graphics/context/fixture close. No output is read as an oracle. */
    private static void writeOptionalNaturalR13Capture(byte[] rgba,int call) throws Exception {
        String output=System.getenv("SONICCD_R13_FIRST_SONIC_OUTPUT");if(output==null||output.isBlank())return;
        assertNotNull(rgba);assertTrue(call>=16980&&call<=20000);
        var directory=Path.of(output).toAbsolutePath().normalize();
        var owned=Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD","captures","engine").toAbsolutePath().normalize();
        assertTrue(directory.startsWith(owned)&&!directory.equals(owned),"Optional ENGINE export requires its own directory under captures/engine");
        Files.createDirectories(directory);assertEquals(directory,directory.toRealPath(),"No symbolic-link capture traversal");
        String stem="ENGINE-R13D-natural-first-Sonic-call-"+call;writeEngineCapturePng(rgba,directory.resolve(stem+".png"));
        Files.writeString(directory.resolve(stem+".txt"),"ENGINE OpenGGF original-USA actual same-World R13D, first renderer-visible selected Sonic at returned"+call+".\n"
                +"Fixed held Right after16979; actual original loading and native Body; full/course value and pixel replay; renderer-only Sonic omission contrast.\n"
                +"Written after module/session/graphics/EGL/fixture cleanup. No native screenshot, boss/fight/full-act/audio/regional/parity claim.\n");
    }
    // END finite natural R13D comparison/render helpers 20261106.

    // BEGIN later-route EGL case: additive to the three pinned controller cases.
    @Test void actualLaterAct2ControllerCutsRenderSelectedSonicAndReplayTheirCompleteValuesAndPixels() throws Exception {
        byte[] imageForExport;
        byte[] splashImageForExport=null;
        byte[] swingImageForExport=null;
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
            System.out.println("ENGINE controller-only Act2 EGL: all nineteen old selected-Sonic endpoints remain, plus13423 and13450, twenty-one total through the unchanged genuine Ready/selected-Sonic omission and full/course before/after value/pixel replay helper. Natural MovingSpring41/child71 full64, body receipts, field sequence and completed Main CPU hashes are comparison-only; both remain offscreen and no wheel/child selected-current or pixel claim is made. Old Shield/Splash contrasts and exports are unchanged. This complete13450 checkpoint precedes the measured Swing segment below. No native-image, regional or whole-act claim.");
            // BEGIN measured Swing promotion: source-only comparisons; Root facts4799c82e6634ff00dca32fbeceeab83b9fe59c5c8168bf93d5ece82b57d4f408.
            // N1ecc5f3 Swing/Main.asm:83-135 allocates dirty ID29 children, which the increasing walk later dispatches.
            // Main.asm:137-197 owns long-difference velocity, HIGH phase byte and native sine-X/cosine-Y WORD stores.
            int[] swingCutCalls={13451,13452,13486};
            int[] swingWatched={42,51,53,57,58,41,71,47};
            var swingVisit=actualField(module,"act2Loading");
            var swingPrior13450=module.act2LoadState();
            byte[] expectedSwingPriorZero=java.util.HexFormat.of().parseHex("00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
            for(int whole:new int[]{42,51,53,57,58})assertArrayEquals(expectedSwingPriorZero,
                    swingPrior13450.placement().pool().slots().get(whole-32).copyBytes(),
                    "Actual closed13450 full64 preimage; comparison only, never allocation-state ingress");
            // Columns: Swing42/51/53/57/58, Wheel41, ordinary Spring71, Robot47, completed Main SHA, selected VInt CPU SHA.
            String[][] expectedSwingImages={
                {"290404180020ed601aa000000188000000000000000008000318000000000000000000000200000004000000000000040008018802001aa0fe00c00800000008","290404180020ed601a6000000188000060008800000008000318010000000000000000000200000004000000000000040008018802001aa0fe00c00804000008","290404180020ed601a7000000188000000000000000008000318020000000000000000000200000004000000000000040008018802001aa0fe00c00803000008","290404180020ed601a8000000188000000000000000008000318020000000000000000000200000004000000000000040008018802001aa0fe00c00802000008","290404180020ed601a9000000188000000000000000008000318020000000000000000000200000004000000000000040008018802001aa0fe00c00801000008","0f04033800209a961a2780000387000001800000000007000408000100000000000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a270000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","2d0404000020e4f619f000000160000000000000000020220422020000000000000000b902000000000000040020e73401600001000000000000000000000000","d1f027d2e3fa30bb38238ad575fa10e8afae478a9388217d004a93628d3921ac","e93f638f76fa87505e09bcc608e67131c3cd4d717ceeb653a02e5615f0d33e92"}, // 13451
                {"290404180020ed601aa000000188000000000000000008000318000000000000000000000200000004000000000000040010018802001aa0fe00c01800000008","290404180020ed601a6000000188000000000000000008000318010000000000000000000200000004000000000000040010018802001aa0fe00c01804000008","290404180020ed601a7000000188000000000000000008000318020000000000000000000200000004000000000000040010018802001aa0fe00c01803000008","290404180020ed601a8000000188000000000000000008000318020000000000000000000200000004000000000000040010018802001aa0fe00c01802000008","290404180020ed601a9000000188000000000000000008000318020000000000000000000200000004000000000000040010018802001aa0fe00c01801000008","0f04033800209a961a2900000387000001800000000007000408010200000800000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a290000037700000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","2d0404000020e4f619f000000160000000000000000020220422020000000000000000b902000000000000040020e73401600001000000000000000000000000","2006ff102a7975faaefe288110a94b53870ca46a3f3fe24c2692c2a49e9640f6","d1f027d2e3fa30bb38238ad575fa10e8afae478a9388217d004a93628d3921ac"}, // 13452
                {"290404180020ed601aa000000188000000000000000008000318000000000000000000000200000004000000000000040120018802001aa0fe00d4d000000008","290404180020ed601a67000001a6000000000200000008000318010000000000000000000200000004000000000000040120018802001aa0fe00d4d004000008","290404180020ed601a750000019e000000000000000008000318020000000000000000000200000004000000000000040120018802001aa0fe00d4d003000008","290404180020ed601a8300000197000000000000000008000318020000000000000000000200000004000000000000040120018802001aa0fe00d4d002000008","290404180020ed601a910000018f000000000000000008000318020000000000000000000200000004000000000000040120018802001aa0fe00d4d001000008","0f04033800209a961a41000003810000fe800000000007000408000100000100000000000400000000000000000000000000038700001a1a0000000000000000","0a040520002099c81a410000037100000000000000000800041000000000000000000000020000000000000000000000f0000000da401a1a00f0000000000000","2d8404000020e4f619f000000160000000000000000020220422020000000000000000b902000000000000040020e73401600001000000000000000000000000","b4c384a6b81965db0b60d9e9e57934ac8feabb6ba55cbbd8273c61c1779c4f81","2dbcf7c5d84da6a4f108d297e1b986f696c8a63a680a505e0f40bad2b4eb2acf"}, // 13486
            };
            // Columns: five Swing receipt indices, Wheel, Robot, Dynamic, Return, Main count, selected count, phase, angle, actorX/Y, scratch, VInt, levelFrames, cameraX/Y.
            int[][] expectedSwingNumbers={
                {32,41,44,48,49,31,37,66,74,33,38,49160,8,417791744,3673856,252,3030,3007,6278,0}, // 13451
                {32,40,43,47,48,31,37,52,60,26,33,49176,16,418143744,3845888,252,3031,3008,6284,0}, // 13452
                {32,41,44,48,49,31,37,55,63,48,39,54480,288,431394816,18224896,244,3065,3042,6486,145}, // 13486
            };
            String[] expectedSwingStatus={"Status[routine=2, previousChunkWord=6272, rightRecordIndex=340, leftRecordIndex=312, rightSavedId=189, leftSavedId=176, nullRightByteOffset=0, nullLeftByteOffset=0]","Status[routine=2, previousChunkWord=6272, rightRecordIndex=340, leftRecordIndex=312, rightSavedId=189, leftSavedId=176, nullRightByteOffset=0, nullLeftByteOffset=0]","Status[routine=2, previousChunkWord=6400, rightRecordIndex=343, leftRecordIndex=312, rightSavedId=190, leftSavedId=176, nullRightByteOffset=0, nullLeftByteOffset=0]"};
            for(int call=13451;call<=13486;call++) {
                var input=controller(call);
                if(call==13451 || call==13486) {
                    var swingPixels=renderReplayNextRight(h,input,call);
                    if(call==13486)swingImageForExport=swingPixels.clone();
                }
                else {if(call==13452)replayNextRight(h,input);step(h,input);}
                assertLaterRouteOwners(h,frame,source,world,generation+1);
                int row=java.util.Arrays.binarySearch(swingCutCalls,call);
                if(row<0)continue;
                var swingState=module.act2LoadState();var swingCalls=swingState.calls();var swingPool=swingState.placement().pool().slots();
                for(int index=0;index<swingWatched.length;index++)assertArrayEquals(java.util.HexFormat.of().parseHex(expectedSwingImages[row][index]),
                        swingPool.get(swingWatched[index]-32).copyBytes(),"Measured complete source whole"+swingWatched[index]+" at closed"+call);
                var swingFamily=swingPool.stream().filter(image->image.id()==0x29).toList();
                assertEquals(java.util.List.of(42,51,53,57,58),swingFamily.stream().map(image->image.identity().wholeIndex()).toList());
                assertEquals(java.util.List.of(0,4,3,2,1),swingFamily.stream().map(image->image.copyBytes()[0x3C]&0xFF).toList());
                for(int index=0;index<swingFamily.size();index++) {
                    var image=swingFamily.get(index);var raw=java.nio.ByteBuffer.wrap(image.copyBytes());
                    assertEquals(2,image.routine(),"Every later physical child already completed its own Init in the allocation walk");
                    assertEquals(4,image.subtype());assertEquals(0,image.subtype2());assertEquals(4,image.copyBytes()[0x2F]&0xFF);
                    assertEquals(6816,raw.getShort(0x36)&0xFFFF);assertEquals(392,raw.getShort(0x32)&0xFFFF);
                    assertEquals(expectedSwingNumbers[row][11],raw.getShort(0x3A)&0xFFFF);assertEquals(expectedSwingNumbers[row][12],raw.getShort(0x30)&0xFFFF);
                    assertEquals(8,raw.getShort(0x3E)&0xFFFF);assertEquals(0x200,raw.getShort(0x34)&0xFFFF);assertEquals(0xFE00,raw.getShort(0x38)&0xFFFF);
                    assertEquals(0,image.copyBytes()[0x3D]&0xFF);assertEquals(0,image.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.X_FRACTION));
                    assertEquals(0,image.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.Y_FRACTION));
                    assertEquals(0x418,image.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE));
                    assertEquals(0x20ED60,image.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));
                    assertEquals(index==0?0:index==1?1:2,image.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.MAP_FRAME));
                    assertEquals(8,image.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.Y_RADIUS));
                    assertEquals(0x18,image.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.WIDTH));
                }
                var actualSwingReceiptIndices=java.util.stream.IntStream.range(0,swingCalls.size()).filter(index->swingCalls.get(index).name().equals("SWING_BODY")).boxed().toList();
                assertEquals(java.util.Arrays.stream(expectedSwingNumbers[row],0,5).boxed().toList(),actualSwingReceiptIndices);
                assertEquals(SonicCdGameModule.Act2LoadCall.PRIMARY_MAIN,swingCalls.get(8));
                assertEquals(expectedSwingNumbers[row][5],swingCalls.indexOf(SonicCdGameModule.Act2LoadCall.MOVING_SPRING_BODY));
                assertEquals(expectedSwingNumbers[row][6],swingCalls.indexOf(SonicCdGameModule.Act2LoadCall.ROBOT_GENERATOR_BODY));
                assertEquals(expectedSwingNumbers[row][7],swingCalls.indexOf(SonicCdGameModule.Act2LoadCall.DYNAMIC_OBJECTS));
                assertEquals(expectedSwingNumbers[row][8],swingCalls.indexOf(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN));
                assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,swingCalls.getLast());
                assertEquals(expectedSwingStatus[row],swingState.placement().status().toString());
                assertEquals(expectedSwingNumbers[row][13],frame.state().motion().centreX16_16());assertEquals(expectedSwingNumbers[row][14],frame.state().motion().centreY16_16());
                assertEquals(expectedSwingNumbers[row][15],frame.environment().movement().globals().primaryAngle());
                assertEquals((long)expectedSwingNumbers[row][16],frame.vIntClock().absoluteValue().orElseThrow());
                assertEquals(expectedSwingNumbers[row][17],frame.environment().globals().levelFrames());
                var swingCamera=((SonicCdPlayerPresentation)actualField(swingVisit,"presentation")).act2ForegroundImage();
                assertEquals(expectedSwingNumbers[row][18],swingCamera.cameraX());assertEquals(expectedSwingNumbers[row][19],swingCamera.cameraYWord());
                assertEquals(35,swingState.ring().rings());assertEquals(0,swingState.ring().timeStopTimer());
                assertEquals(1,frame.environment().movement().gates().timeZone());assertEquals(1,frame.environment().movement().gates().zoneAct());
                assertEquals(255,frame.environment().movement().gates().timeWarpDirection());
                assertEquals(0,frame.state().motion().playerControl());assertEquals(0,frame.environment().controls().controlLocked());
                assertEquals(8,frame.environment().controls().rawHeld());assertEquals(0,frame.environment().controls().rawTap());
                assertEquals(8,frame.environment().controls().logicalHeld());assertEquals(0,frame.environment().controls().logicalTap());
                var swingLoop=actualField(actualField(swingVisit,"values"),"loop");
                var swingCpu=assertInstanceOf(com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat.class,((java.util.Optional<?>)actualField(swingLoop,"completedDraw")).orElseThrow());
                assertEquals(640,swingCpu.bytes().length);assertEquals(expectedSwingNumbers[row][9],swingCpu.spriteCount());
                assertEquals(expectedSwingImages[row][8],java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(swingCpu.bytes())));
                var swingSelected=module.act2SelectedDisplay();assertInstanceOf(SonicCdPastDisplay.Ready.class,swingSelected.projection());
                var selectedSwingCpu=swingSelected.inputs().selectedCpu().orElseThrow();assertEquals(expectedSwingNumbers[row][10],selectedSwingCpu.spriteCount());
                assertEquals(expectedSwingImages[row][9],java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(selectedSwingCpu.bytes())));
                assertNotEquals(expectedSwingImages[row][8],expectedSwingImages[row][9],"Completed Main CPU is distinct from the preceding CPU selected at this VInt");
                assertEquals(java.util.List.of(0),selectedSwingCpu.provenance().stream().filter(piece->piece.slotRef().wholeIndex()==0).map(piece->piece.mappingPieceOrdinal()).toList());
                assertTrue(swingCpu.provenance().stream().noneMatch(piece->java.util.Arrays.stream(swingWatched,0,5).anyMatch(whole->whole==piece.slotRef().wholeIndex())));
                assertTrue(selectedSwingCpu.provenance().stream().noneMatch(piece->java.util.Arrays.stream(swingWatched,0,5).anyMatch(whole->whole==piece.slotRef().wholeIndex())),
                        "Actual Swing group has no Main or selected VInt pieces at these cuts; no family current/pixel claim");
            }
            assertEquals(209,frame.environment().globals().timeWarpTimer(),"Last closed13486 timer before the next primary save boundary");
            var swingClosed13486=captureNative(h);var swingRegistry=h.mode().getRewindRegistry();var swingEndFull=swingRegistry.capture();var swingEndCourse=swingRegistry.captureCourse();
            swingRegistry.restore(swingEndFull);assertNativeCapture(swingClosed13486,h);swingRegistry.restoreCourse(swingEndCourse);assertNativeCapture(swingClosed13486,h);
            System.out.println("ENGINE Act2 EGL: all21 previous Sonic endpoints and Shield/Splash contrasts/exports remain; new selected-Sonic13451 and13486 use the unchanged Ready/omission/full-course complete-value/pixel replay helper,23 endpoints total. Actual Swing42/51/53/57/58 full64 and CPU-vs-selected VInt comparisons remain offscreen family observations. Positive execution ends at closed13486; no native image/parity claim.");
            // END measured Swing promotion.
        } finally { fixture.cleanup(); }
        writeOptionalLaterCapture(imageForExport);
        writeOptionalSplashCapture(splashImageForExport);
        // Optional actual ENGINE selected13486 export occurs after module/session/graphics/boot cleanup.
        String swingOutput=System.getenv("SONICCD_ACT2_SWING_OUTPUT");
        if(swingOutput!=null && !swingOutput.isBlank()) {
            assertNotNull(swingImageForExport);
            Path directory=Path.of(swingOutput).toAbsolutePath().normalize();
            Path owned=Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD","captures","engine").toRealPath();
            assertTrue(directory.startsWith(owned) && !directory.equals(owned),"ENGINE Swing-route capture needs its own directory under engine captures");
            Files.createDirectories(directory);
            assertEquals(directory,directory.toRealPath(),"Capture directory cannot traverse a symbolic link");
            writeEngineCapturePng(swingImageForExport,directory.resolve("ENGINE-act2-controller-call-13486.png"));
        }
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
            assertEquals(scene.displayEnabled(), omitted.displayEnabled());
            assertEquals(scene.selectedBackdropPaletteIndex(), omitted.selectedBackdropPaletteIndex());
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
            assertEquals(scene.displayEnabled(),omitted.displayEnabled());
            assertEquals(scene.selectedBackdropPaletteIndex(),omitted.selectedBackdropPaletteIndex());
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
            assertEquals(scene.displayEnabled(),omitted.displayEnabled());
            assertEquals(scene.selectedBackdropPaletteIndex(),omitted.selectedBackdropPaletteIndex());
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
            assertEquals(scene.selectedBackdropPaletteIndex(),omitted.selectedBackdropPaletteIndex());
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
                scene.vscrollLongBits(),scene.selectedPalette128(),scene.displayEnabled(),picture,scene.nativePatterns(),scene.selectedBackdropPaletteIndex());
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
        if (call < 1 || call > 14516) throw new IllegalArgumentException("Fixed ordinary controller recipe is1..14516; later input exploration has its own finite bound");
        if (call > 10419) {
            // Exact old prefix through13098; then Left96, Left+B4, Left96, Right256.
            // The established held-Right suffix continues unchanged through the WARP return;
            // controller bounds are fixture input selection, never gameplay behavior.
            int directions = call >= 13099 && call <= 13294 ? 4 : 8;
            int actions = call <= 13098 ? (call >= 11214 && (call - 11214) % 128 < 32 ? 2 : 0)
                    : (call >= 13195 && call <= 13198 ? 2 : 0);
            if(call>=14144&&call<=14151)actions=2; // Fixed native jump input, never a state/camera write.
            if(call>=14408&&call<=14423){directions=2;actions=2;} // Fixed Down+B16 followed by Right; no state-dependent input choice.
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

    // BEGIN natural R12B Wall EGL proof: independent cold route, no component ingress.
    @Test void actualNaturalR12bControllerSonicCutsReplayCompleteValuesAndEglPixels() throws Exception {
        byte[] exportPixels=null;int exportCall=-1;
        var sonicSelectedCalls=new java.util.ArrayList<Integer>();var sonicVisibleCalls=new java.util.ArrayList<Integer>();
        var wallSelectedCalls=new java.util.ArrayList<Integer>();var wallVisibleCalls=new java.util.ArrayList<Integer>();
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()) {
            assertInstanceOf(NullAudioBackend.class,GameServices.audio().getBackend());
            var prepared=ready();var h=prepared.harness();var module=h.module();var world=h.mode().getWorldSession();
            R12bNaturalOwners owners=null;
            long beforeTargetGeneration=-1;
            // Exact inherited controller input, including the real old programme/WARP/FULL path.
            for(int call=prepared.returned()+1;call<=13951;call++) {
                if(call==13886) {
                    assertTrue(module.act2LoadingSelected());assertFalse(module.act2PastLoadingSelected());
                    beforeTargetGeneration=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
                }
                try {step(h,controller(call));}
                catch(RuntimeException|Error failure) {throw new AssertionError("Fixed natural R12B EGL route: last returned="+(call-1)+", first failed="+call,failure);}
                assertSame(world,h.mode().getWorldSession());
                if(call==13886) {
                    assertTrue(module.act2PastLoadingSelected());assertFalse(module.act2LoadingSelected());
                    var target=module.requirePlayerFrame();assertNotSame(prepared.frame(),target);assertTrue(prepared.frame().closed());
                    assertEquals(beforeTargetGeneration+1,h.mode().getLevelManager().getCompletedProductionLoadGeneration(),"Exactly the actual target FULL generation");
                    owners=new R12bNaturalOwners(target,target.stageSource(),module.act2PastWorldHost(),module.act2PastLoadingVisit(),world,beforeTargetGeneration+1);
                }
                if(owners!=null) {
                    assertSame(owners.frame(),module.requirePlayerFrame());assertSame(owners.source(),module.requirePlayerFrame().stageSource());
                    assertSame(owners.host(),module.act2PastWorldHost());assertSame(owners.visit(),module.act2PastLoadingVisit());
                    assertEquals(owners.generation(),h.mode().getLevelManager().getCompletedProductionLoadGeneration());
                    assertFalse(module.requirePlayerFrame().closed());
                }
            }
            assertNotNull(owners,"The fixed route must have performed the genuine R12B FULL");
            // Fifteen forward edges end at13966; no service of unsupported13967 is requested.
            for(int completedCall=13951;completedCall<=13966;completedCall++) {
                var evidence=replayNaturalR12bNextController(h,owners,completedCall);
                if(evidence.sonicSelected())sonicSelectedCalls.add(completedCall);
                if(evidence.sonicVisible())sonicVisibleCalls.add(completedCall);
                if(evidence.wallSelected())wallSelectedCalls.add(completedCall);
                if(evidence.wallVisible())wallVisibleCalls.add(completedCall);
                if(exportCall<0&&evidence.sonicVisible()) {
                    exportCall=completedCall;exportPixels=evidence.rgba().clone();
                }
            }
            assertFalse(sonicSelectedCalls.isEmpty(),"Finite13951..13966 gap: no genuine selected Sonic0; no pose/art/input substitution");
            assertFalse(sonicVisibleCalls.isEmpty(),"Finite13951..13966 gap: Sonic0 omission never changed pixels; selected="+sonicSelectedCalls);
            // The actual fixed window selects no Wall69. Retain this measured coverage gap;
            // its live Init header is not proof of selection or visible Wall pixels.
            assertTrue(wallSelectedCalls.isEmpty(),"Measured13951..13966 has no selected Wall69; reassess coverage if the actual source changes");
            assertTrue(wallVisibleCalls.isEmpty());
            assertTrue(exportCall>=13951&&exportCall<=13966,"Finite13951..13966 gap: no visible selected Sonic0; Sonic="+sonicVisibleCalls);
            assertNotNull(exportPixels);assertNaturalR12bRenderOwners(h,owners);
            System.out.println("ENGINE original-USA natural R12B: fixed controller returned13966;16 selected Ready cuts13951..13966, complete native/full/course state and pixel replay; first visible Sonic0 call"+exportCall+
                    ". Sonic selected="+sonicSelectedCalls+", visible="+sonicVisibleCalls+", Wall selected="+wallSelectedCalls+", visible="+wallVisibleCalls+". Wall69 absent from selected CPU in this window. No13967, visible Wall, emulator/native-image/audio/regional/full-act parity claim.");
        } finally {fixture.cleanup();}
        // All module/session/graphics/EGL/fixture owners are retired before any optional output.
        writeOptionalNaturalR12bCapture(exportPixels,exportCall);
    }

    /** Comparison-only identities borrowed from the one real target FULL, never runtime ingress. */
    private record R12bNaturalOwners(com.openggf.game.soniccd.player.CdPlayerFrameRuntime frame,
            com.openggf.game.soniccd.level.CdStagePlayerSource source,SonicCdAct2PastWorldHost host,
            SonicCdAct2PastLoadingVisit visit,Object world,long generation) { }
    private record R12bNaturalPixels(byte[] rgba,boolean sonicSelected,boolean sonicVisible,boolean wallSelected,boolean wallVisible) {
        private R12bNaturalPixels {rgba=rgba.clone();}
    }

    private static void assertNaturalR12bRenderOwners(Harness h,R12bNaturalOwners owners) throws Exception {
        var module=h.module();module.validateAct2PastLoadingCut();
        assertTrue(module.act2PastLoadingSelected());assertFalse(module.act2LoadingSelected());
        assertSame(owners.frame(),module.requirePlayerFrame());assertSame(owners.source(),owners.frame().stageSource());assertFalse(owners.frame().closed());
        assertSame(owners.host(),module.act2PastWorldHost());assertSame(owners.visit(),module.act2PastLoadingVisit());
        assertSame(owners.world(),h.mode().getWorldSession());assertSame(module,h.mode().getWorldSession().getGameModule());
        assertEquals(owners.generation(),h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        var level=assertInstanceOf(com.openggf.game.soniccd.level.SonicCdAct2PastLevel.class,h.mode().getLevelManager().getCurrentLevel());
        var terrain=assertInstanceOf(com.openggf.game.soniccd.level.CdR12bTerrainAssets.class,actualField(owners.host(),"terrain"));
        var player=assertInstanceOf(com.openggf.game.soniccd.level.CdR12bPlayerAssets.class,actualField(owners.host(),"player"));
        assertSame(level.assets(),terrain);assertTrue(owners.source().ownsAct2Past(terrain,player));
        assertEquals(CdStageKey.parse("R12B"),owners.source().stage());assertEquals(CdReleaseVariant.USA,terrain.identity().variant());
        assertEquals(CdNativeFile.R12B,terrain.identity().file());
        assertEquals("14de4fdb4979125a8c0c197c926133353d20a2ca5b2d57db2e363c4e94460a64",terrain.identity().mmdSha256());
        assertEquals(SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL,owners.visit().state().phase());
        assertTrue(owners.host().geometryReady());assertTrue(owners.visit().state().displayEnabled());
        assertEquals(0x230F60,owners.frame().state().header().mappingAddress());
    }

    /** Actual selected prior CPU; the two omissions change renderer input only. */
    private static R12bNaturalPixels drawNaturalR12bSelectedCut(Harness h,R12bNaturalOwners owners,int completedCall) throws Exception {
        assertNaturalR12bRenderOwners(h,owners);var module=h.module();var host=owners.host();
        var before=captureNative(h);var publication=assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,host.selected());
        var selected=publication.selection();var inputs=selected.inputs();
        var scene=assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection(),"Actual R12B selected display at returned"+completedCall+": "+selected.projection()).scene();
        assertTrue(inputs.displayEnabled());assertTrue(scene.displayEnabled());
        var cpu=inputs.selectedCpu().orElseThrow(()->new AssertionError("Returned"+completedCall+" has no genuine selected prior CPU"));
        assertArrayEquals(cpu.bytes(),inputs.selectedSat().copy());assertFalse(scene.nativePatterns().isEmpty());
        var actual=drawNaturalR12bImage(h);assertNativeCapture(before,h);assertArrayEquals(actual,drawNaturalR12bImage(h));assertNativeCapture(before,h);
        var binding=actualField(host,"render");assertNotNull(binding);
        var renderer=assertInstanceOf(RetainedPlaneSceneRenderer.class,actualField(binding,"renderer"));
        assertSame(h.mode().getWorldSession(),actualField(binding,"world"));assertEquals(owners.generation(),actualField(binding,"generation"));
        var camera=h.mode().getCamera();boolean sonicSelected=false,sonicVisible=false,wallSelected=false,wallVisible=false;
        for(int whole:new int[]{0,69}) {
            boolean present=cpu.provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==whole);
            if(!present)continue; // Absence is not a successful omission or a visibility claim.
            if(whole==69) {
                var wall=host.placement().pool().state(69);assertEquals(0x36,wall.id(),"Selected whole69 retains its genuine Wall owner");
                assertEquals(com.openggf.game.soniccd.level.CdR12bObjectAssets.View.MAP_SPR_BREAKABLE_WALL.range().nativeAddress(),
                        wall.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));
            }
            var omitted=TestSonicCdPastResultsRoute.omitWhole(scene,cpu,whole);
            assertNaturalR12bOmissionPartitions(scene,omitted);
            var level=assertInstanceOf(com.openggf.level.AbstractLevel.class,h.mode().getLevelManager().getCurrentLevel());
            long epoch=level.currentEpoch();
            clearPixels();renderer.draw(omitted,camera.getX(),camera.getY(),module::validateAct2PastLoadingCut);
            assertSame(level,h.mode().getLevelManager().getCurrentLevel());
            assertEquals(epoch,level.currentEpoch(),"Renderer-only omission must not advance the level COW epoch");
            boolean visible=!Arrays.equals(actual,pixels());assertNativeCapture(before,h);
            assertArrayEquals(actual,drawNaturalR12bImage(h));assertNativeCapture(before,h);
            if(whole==0){sonicSelected=true;sonicVisible=visible;}else{wallSelected=true;wallVisible=visible;}
        }
        assertSame(publication,host.selected());assertSame(selected,assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,host.selected()).selection());
        assertSame(inputs.foreground(),selected.inputs().foreground());assertSame(inputs.background(),selected.inputs().background());
        assertSame(inputs.current(),selected.inputs().current());assertSame(inputs.player(),selected.inputs().player());
        assertSame(binding,actualField(host,"render"));assertSame(renderer,actualField(binding,"renderer"));
        assertNaturalR12bRenderOwners(h,owners);assertNativeCapture(before,h);
        return new R12bNaturalPixels(actual,sonicSelected,sonicVisible,wallSelected,wallVisible);
    }

    /** Both complete rewind scopes are captured BEFORE pixels and before the one forward edge. */
    private static R12bNaturalPixels replayNaturalR12bNextController(Harness h,R12bNaturalOwners owners,int completedCall) throws Exception {
        assertTrue(completedCall>=13951&&completedCall<=13966);assertNaturalR12bRenderOwners(h,owners);
        var registry=h.mode().getRewindRegistry();var before=captureNative(h);
        var full=registry.capture();var course=registry.captureCourse();
        var selected=assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,owners.host().selected()).selection();
        var evidence=drawNaturalR12bSelectedCut(h,owners,completedCall);assertNativeCapture(before,h);
        assertNaturalR12bSnapshotValue(full,registry.capture(),"full before pixels");assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"course before pixels");
        if(completedCall==13966) {
            registry.restore(full);assertNativeCapture(before,h);assertEquals(selected,assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,owners.host().selected()).selection());
            assertNaturalR12bSnapshotValue(full,registry.capture(),"final full restore");assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"final full->course");
            assertArrayEquals(evidence.rgba(),drawNaturalR12bImage(h));assertNativeCapture(before,h);
            registry.restoreCourse(course);assertNativeCapture(before,h);assertEquals(selected,assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,owners.host().selected()).selection());
            assertNaturalR12bSnapshotValue(full,registry.capture(),"final course->full");assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"final course restore");
            assertArrayEquals(evidence.rgba(),drawNaturalR12bImage(h));assertNativeCapture(before,h);assertNaturalR12bRenderOwners(h,owners);return evidence;
        }
        int next=completedCall+1;
        try {step(h,controller(next));}
        catch(RuntimeException|Error failure){throw new AssertionError("Fixed R12B render edge: last returned="+completedCall+", first failed="+next,failure);}
        assertNaturalR12bRenderOwners(h,owners);var after=captureNative(h);var fullAfter=registry.capture();var courseAfter=registry.captureCourse();
        var afterSelected=assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,owners.host().selected()).selection();
        assertInstanceOf(SonicCdPastDisplay.Ready.class,afterSelected.projection(),"Actual forward Ready at"+next);
        var afterPixels=drawNaturalR12bImage(h);assertNativeCapture(after,h);
        assertNaturalR12bSnapshotValue(fullAfter,registry.capture(),"full after pixels");assertNaturalR12bSnapshotValue(courseAfter,registry.captureCourse(),"course after pixels");
        registry.restore(full);assertNativeCapture(before,h);assertEquals(selected,assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,owners.host().selected()).selection());
        assertNaturalR12bSnapshotValue(full,registry.capture(),"full before replay");assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"course at full-before");
        assertArrayEquals(evidence.rgba(),drawNaturalR12bImage(h));assertNativeCapture(before,h);
        step(h,controller(next));assertNaturalR12bRenderOwners(h,owners);assertNativeCapture(after,h);
        assertEquals(afterSelected,assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,owners.host().selected()).selection());
        assertNaturalR12bSnapshotValue(fullAfter,registry.capture(),"full forward replay");assertNaturalR12bSnapshotValue(courseAfter,registry.captureCourse(),"course at full-forward");
        assertArrayEquals(afterPixels,drawNaturalR12bImage(h));assertNativeCapture(after,h);
        registry.restoreCourse(course);assertNativeCapture(before,h);assertEquals(selected,assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,owners.host().selected()).selection());
        assertNaturalR12bSnapshotValue(full,registry.capture(),"full at course-before");assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"course before replay");
        assertArrayEquals(evidence.rgba(),drawNaturalR12bImage(h));assertNativeCapture(before,h);
        step(h,controller(next));assertNaturalR12bRenderOwners(h,owners);assertNativeCapture(after,h);
        assertEquals(afterSelected,assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,owners.host().selected()).selection());
        assertNaturalR12bSnapshotValue(fullAfter,registry.capture(),"full at course-forward");assertNaturalR12bSnapshotValue(courseAfter,registry.captureCourse(),"course forward replay");
        assertArrayEquals(afterPixels,drawNaturalR12bImage(h));assertNativeCapture(after,h);
        return evidence; // Both real replays end at the same next closed cut; outer loop never double-steps.
    }

    private static void assertNaturalR12bOmissionPartitions(RetainedPlaneSceneRenderer.Scene actual,RetainedPlaneSceneRenderer.Scene omitted) {
        assertSame(actual.foreground(),omitted.foreground());assertSame(actual.background(),omitted.background());
        assertArrayEquals(actual.packedHscroll256(),omitted.packedHscroll256());assertEquals(actual.vscrollLongBits(),omitted.vscrollLongBits());
        assertArrayEquals(actual.selectedPalette128(),omitted.selectedPalette128());assertEquals(actual.displayEnabled(),omitted.displayEnabled());
        assertEquals(actual.selectedBackdropPaletteIndex(),omitted.selectedBackdropPaletteIndex());
        assertEquals(actual.nativePatterns(),omitted.nativePatterns());assertEquals(actual.sprites().patternVersions(),omitted.sprites().patternVersions());
    }

    /** Drawing must preserve the live COW epoch; rewind capture/restore advance it by contract. */
    private static byte[] drawNaturalR12bImage(Harness h) throws Exception {
        var level=assertInstanceOf(com.openggf.level.AbstractLevel.class,h.mode().getLevelManager().getCurrentLevel());
        long epoch=level.currentEpoch();var rgba=draw(h);
        assertSame(level,h.mode().getLevelManager().getCurrentLevel());
        assertEquals(epoch,level.currentEpoch(),"Production drawing must not advance the level COW epoch");
        return rgba;
    }

    /** Compare full values, accounting only for the level adapter's monotone COW capture epoch. */
    private static void assertNaturalR12bSnapshotValue(Object expected,Object actual,String field) throws Exception {
        if(expected instanceof com.openggf.game.rewind.CompositeSnapshot composite){
            expected=composite.entries();
            actual=assertInstanceOf(com.openggf.game.rewind.CompositeSnapshot.class,actual).entries();
        }
        if(expected instanceof java.util.Map<?,?> entries) {
            var actualEntries=assertInstanceOf(java.util.Map.class,actual);
            assertEquals(entries.keySet(),actualEntries.keySet(),"R12B natural "+field+" owner keys");
            for(var key:entries.keySet())assertNaturalR12bSnapshotValue(entries.get(key),actualEntries.get(key),field+"["+key+"]");
            return;
        }
        if(expected instanceof com.openggf.game.rewind.snapshot.LevelSnapshot level) {
            var other=assertInstanceOf(com.openggf.game.rewind.snapshot.LevelSnapshot.class,actual);
            assertTrue(other.epochAtCapture()>=level.epochAtCapture(),"R12B natural "+field+" COW epoch must remain monotone");
            // LevelRewindSnapshotAdapter.capture/restore bump the epoch; every other component is restored data.
            for(var component:com.openggf.game.rewind.snapshot.LevelSnapshot.class.getRecordComponents()) {
                if(component.getName().equals("epochAtCapture"))continue;
                assertNaturalR12bSnapshotValue(component.getAccessor().invoke(level),component.getAccessor().invoke(other),field+"."+component.getName());
            }
            return;
        }
        var compare=TestSonicCdPastLoadingWorld.class.getDeclaredMethod("assertNativeValue",Object.class,Object.class,String.class);
        assertFalse(compare.isAnnotationPresent(Test.class));compare.setAccessible(true);
        try {compare.invoke(null,expected,actual,"R12B natural "+field);}
        catch(java.lang.reflect.InvocationTargetException failure) {
            var cause=failure.getCause();if(cause instanceof Error error)throw error;
            if(cause instanceof Exception error)throw error;throw new AssertionError("Existing native comparator failed",cause);
        }
    }

    /** Optional output only, after the try-with-resources and fixture finally have both completed. */
    private static void writeOptionalNaturalR12bCapture(byte[] rgba,int completedCall) throws Exception {
        String output=System.getenv("SONICCD_R12B_NATURAL_OUTPUT");if(output==null||output.isBlank())return;
        assertNotNull(rgba);assertEquals(320*224*4,rgba.length);assertTrue(completedCall>=13951&&completedCall<=13966);
        var directory=Path.of(output);assertTrue(directory.isAbsolute());assertEquals(directory,directory.normalize());assertEquals(output,directory.toString());
        String root=System.getenv("SONICCD_ENGINE_CAPTURE_ROOT");
        assertNotNull(root,"Optional export requires an explicit task-owned ENGINE capture root");
        var owned=Path.of(root).toRealPath();
        assertTrue(directory.startsWith(owned)&&!directory.equals(owned),"Natural ENGINE R12B output requires its own canonical directory under E/captures/engine");
        for(var ancestor=directory;ancestor!=null;ancestor=ancestor.getParent()) {
            assertFalse(Files.isSymbolicLink(ancestor),"Capture output cannot traverse symlinks");
            if(Files.exists(ancestor,java.nio.file.LinkOption.NOFOLLOW_LINKS))assertTrue(Files.isDirectory(ancestor,java.nio.file.LinkOption.NOFOLLOW_LINKS));
        }
        Files.createDirectories(directory);assertEquals(directory,directory.toRealPath());
        String stem="ENGINE-R12B-controller-call-"+completedCall;
        var png=directory.resolve(stem+".png");var text=directory.resolve(stem+".txt");
        assertFalse(Files.exists(png,java.nio.file.LinkOption.NOFOLLOW_LINKS));assertFalse(Files.exists(text,java.nio.file.LinkOption.NOFOLLOW_LINKS));
        writeEngineCapturePng(rgba,png);
        Files.writeString(text,"ENGINE: OpenGGF original-USA natural controller-only R12B, actual returned call"+completedCall+".\n"
                +"First selected visible Sonic0 cut within fixed13951..13966; no selected Wall69 in this window; natural route returned13966.\n"
                +"320x224 bottom-up RGBA, production retained scene on surfaceless EGL; repeated actual pixels and renderer-only selected whole0 omission; Wall69 never selected in the measured window.\n"
                +"Complete native/full/course values and pixels replayed; final13966 has closed-cut restore only, no13967 service.\n"
                +"Exported after module/session/graphics/EGL/fixture cleanup. Image bytes never feed gameplay.\n"
                +"ENGINE/native-source evidence only; no emulator/native-image/audio/regional or full-act parity claim.\n");
    }
    // END natural R12B Wall EGL proof.

    // BEGIN natural R12B Projector EGL proof; independent cold route, comparison-only observations.
    @Test void actualEglNaturalR12bProjectorUsesServicedSelectedPixelsAndReplays() throws Exception {
        byte[] exportPixels=null,exportOmitted=null;int exportCall=-1;String exportDetails=null;
        var selectedCalls=new java.util.ArrayList<Integer>();var visibleCalls=new java.util.ArrayList<Integer>();
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()) {
            assertInstanceOf(NullAudioBackend.class,GameServices.audio().getBackend());
            var prepared=ready();var h=prepared.harness();var module=h.module();var world=h.mode().getWorldSession();
            var right=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,0,0,false,false),PlayerInputState.neutral());
            R12bNaturalOwners owners=null;long beforeTargetGeneration=-1;
            for(int call=prepared.returned()+1;call<=14707;call++) {
                if(call==13886)beforeTargetGeneration=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
                try {step(h,call<=14516?controller(call):right);}
                catch(RuntimeException|Error failure){throw new AssertionError("Fixed Projector EGL prefix: last returned="+(call-1)+", first failed="+call,failure);}
                assertSame(world,h.mode().getWorldSession());
                if(call==13886) {
                    assertTrue(module.act2PastLoadingSelected());assertFalse(module.act2LoadingSelected());
                    var frame=module.requirePlayerFrame();assertNotSame(prepared.frame(),frame);assertTrue(prepared.frame().closed());
                    assertEquals(beforeTargetGeneration+1,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
                    owners=new R12bNaturalOwners(frame,frame.stageSource(),module.act2PastWorldHost(),module.act2PastLoadingVisit(),world,beforeTargetGeneration+1);
                }
                if(owners!=null) {
                    assertSame(owners.frame(),module.requirePlayerFrame());assertSame(owners.source(),module.requirePlayerFrame().stageSource());
                    assertSame(owners.host(),module.act2PastWorldHost());assertSame(owners.visit(),module.act2PastLoadingVisit());
                    assertEquals(owners.generation(),h.mode().getLevelManager().getCompletedProductionLoadGeneration());assertFalse(owners.frame().closed());
                }
            }
            assertNotNull(owners,"The unchanged controller prefix must perform the genuine R12B FULL");assertNaturalR12bRenderOwners(h,owners);
            var registry=h.mode().getRewindRegistry();
            // Ascending fixed cuts only: the first selected parent whose omission changes actual pixels wins.
            for(int call=14708;call<=14716&&exportCall<0;call++) {
                assertNaturalR12bRenderOwners(h,owners);var host=owners.host();
                var before=captureNative(h);var full=registry.capture();var course=registry.captureCourse();
                var beforeSelected=assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,host.selected()).selection();
                var previousCpu=host.video().completedCpu().orElseThrow();var previousParent=host.placement().pool().state(32);
                var beforePixels=drawNaturalR12bImage(h);assertNativeCapture(before,h);
                assertNaturalR12bSnapshotValue(full,registry.capture(),"Projector full before pixels/forward");
                assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"Projector course before pixels/forward");
                try {step(h,right);}
                catch(RuntimeException|Error failure){throw new AssertionError("Fixed Projector EGL window: last returned="+(call-1)+", first failed="+call,failure);}
                assertNaturalR12bRenderOwners(h,owners);
                var after=captureNative(h);var fullAfter=registry.capture();var courseAfter=registry.captureCourse();
                var publication=assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,host.selected());var selected=publication.selection();var inputs=selected.inputs();
                var scene=assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection(),"Actual Projector-window Ready at"+call).scene();
                assertTrue(inputs.displayEnabled());assertTrue(scene.displayEnabled());
                assertEquals(com.openggf.game.soniccd.level.SonicCdLevel.LEVEL_BACKDROP_PALETTE_INDEX,scene.selectedBackdropPaletteIndex());
                var cpu=inputs.selectedCpu().orElseThrow();assertArrayEquals(cpu.bytes(),inputs.selectedSat().copy());
                assertArrayEquals(previousCpu.bytes(),cpu.bytes());assertEquals(previousCpu.spriteCount(),cpu.spriteCount());assertEquals(previousCpu.provenance(),cpu.provenance());
                var actual=drawNaturalR12bImage(h);assertNativeCapture(after,h);assertArrayEquals(actual,drawNaturalR12bImage(h));assertNativeCapture(after,h);
                assertNaturalR12bSnapshotValue(fullAfter,registry.capture(),"Projector full after pixels");
                assertNaturalR12bSnapshotValue(courseAfter,registry.captureCourse(),"Projector course after pixels");
                if(cpu.provenance().stream().noneMatch(piece->piece.slotRef().wholeIndex()==32))continue;
                selectedCalls.add(call);
                assertEquals(0x2E,previousParent.id());assertEquals(2,previousParent.routine());
                assertEquals(0x20E9F0,previousParent.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS),"The genuinely selected prior CPU came from the native Projector32 source");
                assertEquals(0x20E9F0,com.openggf.game.soniccd.level.CdR12bObjectAssets.View.MAP_SPR_PROJECTOR.range().nativeAddress());
                // Read final selected pattern IDs only; never preload, service, clear or supply known art.
                var parentPatterns=new java.util.TreeSet<Integer>();var raw=cpu.bytes();
                for(int index=0;index<cpu.spriteCount();index++)if(cpu.provenance().get(index).slotRef().wholeIndex()==32) {
                    int size=raw[index*8+2]&15,count=(((size>>>2)&3)+1)*((size&3)+1);
                    int first=(((raw[index*8+4]&255)<<8)|(raw[index*8+5]&255))&0x7FF;
                    for(int tile=0;tile<count;tile++)parentPatterns.add((first+tile)&0x7FF);
                }
                assertFalse(parentPatterns.isEmpty());
                for(int id:parentPatterns) {
                    var serviced=com.openggf.game.soniccd.resources.CdR12bCameraArtResidency.readTile(inputs.current(),id);
                    var version=com.openggf.game.soniccd.resources.CdObjectPresentation.patternVersion(serviced);
                    assertEquals(version,scene.nativePatterns().get(id),"Selected native current pattern"+id);
                    assertEquals(version,scene.sprites().patternVersions().get(PatternAtlasRange.OBJECTS.base()+id));
                }
                var binding=actualField(host,"render");assertNotNull(binding);
                var renderer=assertInstanceOf(RetainedPlaneSceneRenderer.class,actualField(binding,"renderer"));
                assertSame(world,actualField(binding,"world"));assertEquals(owners.generation(),actualField(binding,"generation"));
                var cut=TestSonicCdPastResultsRoute.omitWhole(scene,cpu,32);
                // The older generic helper defaults backdrop0; carry the actual source-backed backdrop explicitly.
                var omitted=new RetainedPlaneSceneRenderer.Scene(cut.foreground(),cut.background(),cut.packedHscroll256(),cut.vscrollLongBits(),
                        cut.selectedPalette128(),cut.displayEnabled(),cut.sprites(),cut.nativePatterns(),scene.selectedBackdropPaletteIndex());
                assertNaturalR12bOmissionPartitions(scene,omitted);assertEquals(scene.selectedBackdropPaletteIndex(),omitted.selectedBackdropPaletteIndex());
                assertEquals(scene.sprites().primitives(),omitted.sprites().primitives());assertSame(cut.sprites(),omitted.sprites());
                assertTrue(omitted.sprites().tiles().size()<scene.sprites().tiles().size());
                var camera=h.mode().getCamera();var level=assertInstanceOf(com.openggf.level.AbstractLevel.class,h.mode().getLevelManager().getCurrentLevel());
                long epoch=level.currentEpoch();clearPixels();renderer.draw(omitted,camera.getX(),camera.getY(),module::validateAct2PastLoadingCut);var withoutParent=pixels();
                assertSame(level,h.mode().getLevelManager().getCurrentLevel());assertEquals(epoch,level.currentEpoch());assertNativeCapture(after,h);
                assertSame(publication,host.selected());assertSame(inputs.current(),selected.inputs().current());assertSame(inputs.player(),selected.inputs().player());
                assertSame(inputs.foreground(),selected.inputs().foreground());assertSame(inputs.background(),selected.inputs().background());
                assertSame(binding,actualField(host,"render"));assertSame(renderer,actualField(binding,"renderer"));
                assertNaturalR12bSnapshotValue(fullAfter,registry.capture(),"Projector full after omission");
                assertNaturalR12bSnapshotValue(courseAfter,registry.captureCourse(),"Projector course after omission");
                assertArrayEquals(actual,drawNaturalR12bImage(h));assertNativeCapture(after,h);
                if(Arrays.equals(actual,withoutParent))continue;
                visibleCalls.add(call);
                // Replay this already observed ordinary edge into the chosen cut; no service beyond14716.
                for(int scope=0;scope<2;scope++) {
                    if(scope==0)registry.restore(full);else registry.restoreCourse(course);
                    assertNaturalR12bRenderOwners(h,owners);assertNativeCapture(before,h);
                    assertEquals(beforeSelected,assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,host.selected()).selection());
                    assertNaturalR12bSnapshotValue(full,registry.capture(),"Projector full at restored before"+scope);
                    assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"Projector course at restored before"+scope);
                    assertArrayEquals(beforePixels,drawNaturalR12bImage(h));assertNativeCapture(before,h);
                    assertNaturalR12bSnapshotValue(full,registry.capture(),"Projector full before replay forward"+scope);
                    assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"Projector course before replay forward"+scope);
                    step(h,right);assertNaturalR12bRenderOwners(h,owners);assertNativeCapture(after,h);
                    assertEquals(selected,assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,host.selected()).selection());
                    assertNaturalR12bSnapshotValue(fullAfter,registry.capture(),"Projector full replay after"+scope);
                    assertNaturalR12bSnapshotValue(courseAfter,registry.captureCourse(),"Projector course replay after"+scope);
                    assertArrayEquals(actual,drawNaturalR12bImage(h));assertNativeCapture(after,h);
                    assertNaturalR12bSnapshotValue(fullAfter,registry.capture(),"Projector full replay pixels"+scope);
                    assertNaturalR12bSnapshotValue(courseAfter,registry.captureCourse(),"Projector course replay pixels"+scope);
                }
                exportCall=call;exportPixels=actual.clone();exportOmitted=withoutParent.clone();
                var selectedChildren=cpu.provenance().stream().map(piece->piece.slotRef().wholeIndex()).filter(whole->whole==64||whole==65).distinct().toList();
                exportDetails="selected parent32 source20E9F0; current patterns="+parentPatterns+"; selected whole slots64/65="+selectedChildren+" (selection only; Hologram identity/visibility not claimed)";
            }
            assertTrue(exportCall>=14708&&exportCall<=14716,"Bounded14708..14716 gap: no genuinely selected/render-visible Projector32; selected="+selectedCalls);
            assertNotNull(exportPixels);assertNotNull(exportOmitted);assertFalse(Arrays.equals(exportPixels,exportOmitted));assertNaturalR12bRenderOwners(h,owners);
            System.out.println("ENGINE natural R12B Projector: first selected/render-visible parent32 at returned"+exportCall+", selected="+selectedCalls+", visible="+visibleCalls+
                    "; full/course complete checkpoint-aware native values and actual pixels replayed before/after its fixed Right edge; "+exportDetails+". No native-image/audio/regional/full-act parity claim.");
        } finally {fixture.cleanup();}
        writeOptionalNaturalR12bProjectorCapture(exportPixels,exportOmitted,exportCall,exportDetails);
    }

    /** Optional artifacts only after module/session/graphics/EGL/fixture cleanup; never gameplay ingress. */
    private static void writeOptionalNaturalR12bProjectorCapture(byte[] actual,byte[] withoutParent,int call,String details) throws Exception {
        String output=System.getenv("SONICCD_R12B_PROJECTOR_OUTPUT");if(output==null||output.isBlank())return;
        assertNotNull(actual);assertNotNull(withoutParent);assertEquals(320*224*4,actual.length);assertEquals(actual.length,withoutParent.length);assertTrue(call>=14708&&call<=14716);
        var directory=Path.of(output);assertTrue(directory.isAbsolute());assertEquals(directory,directory.normalize());assertEquals(output,directory.toString());
        String root=System.getenv("SONICCD_ENGINE_CAPTURE_ROOT");assertNotNull(root,"Explicit canonical ENGINE capture root required");
        var owned=Path.of(root).toRealPath();assertEquals(Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD","captures","engine").toRealPath(),owned);
        assertTrue(directory.startsWith(owned)&&!directory.equals(owned),"Projector output needs its own canonical directory under E/captures/engine");
        for(var ancestor=directory;ancestor!=null;ancestor=ancestor.getParent()) {
            assertFalse(Files.isSymbolicLink(ancestor),"Capture output cannot traverse symlinks");
            if(Files.exists(ancestor,java.nio.file.LinkOption.NOFOLLOW_LINKS))assertTrue(Files.isDirectory(ancestor,java.nio.file.LinkOption.NOFOLLOW_LINKS));
        }
        Files.createDirectories(directory);assertEquals(directory,directory.toRealPath());
        String stem="ENGINE-R12B-natural-projector-call-"+call;var png=directory.resolve(stem+".png");var omitted=directory.resolve(stem+"-without-parent32.png");var text=directory.resolve(stem+".txt");
        for(var path:java.util.List.of(png,omitted,text))assertFalse(Files.exists(path,java.nio.file.LinkOption.NOFOLLOW_LINKS));
        writeEngineCapturePng(actual,png);writeEngineCapturePng(withoutParent,omitted);
        Files.writeString(text,"ENGINE OpenGGF original-USA natural R12B Projector; actual returned call"+call+".\n"
                +"Fixed original controller1..14516 then Right; first selected/render-visible parent32 in14708..14716.\n"
                +"320x224 bottom-up RGBA, production retained native planes/sprites/current pixels and source-backed backdrop on configured EGL.\n"
                +"Comparison omits only selected parent32 sprites; all other scene partitions and native slot values retained.\n"
                +"Full/course complete checkpoint-aware native values and actual before/after pixels replayed. "+details+".\n"
                +"Exported after module/session/graphics/EGL/fixture cleanup; pixels never feed gameplay.\n"
                +"ENGINE/native-source evidence only; no emulator/native-image/audio/regional/full-act parity claim.\n");
    }
    // END natural R12B Projector EGL proof.


    // BEGIN natural R12B Goal16102 EGL proof; fixed observed cut, comparison-only, Java/EGL UNRUN by author.
    /** Goal33 ID16/routine4 is the observed16102 cut; source-placed Signpost32 appears later at16109.
     * Native N1ecc5f3a Level End/Main568-658,664-681; Level/Main821-847.
     * One fixed cut plus its16103 edge only; no visibility search, component effect-list replay or state ingress. */
    @Test void actualEglNaturalR12bGoalAt16102UsesCurrentSelectedPixelsAndReplays() throws Exception {
        byte[] exportPixels=null,exportOmitted=null;String exportDetails=null;
        String mediaRoot=System.getProperty("soniccd.media.root");
        org.junit.jupiter.api.Assumptions.assumeTrue(mediaRoot!=null&&!mediaRoot.isBlank(),"Optional actual original CD media");
        assertTrue(Files.isDirectory(Path.of(mediaRoot)),"Configured bad media root must fail");
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()) {
            assertInstanceOf(NullAudioBackend.class,GameServices.audio().getBackend());
            var prepared=ready();var h=prepared.harness();var module=h.module();var world=h.mode().getWorldSession();
            R12bNaturalOwners owners=null;long beforeTargetGeneration=-1;
            com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat previousCpu=null;
            com.openggf.game.soniccd.objects.CdObjectState previousGoal=null;
            for(int call=prepared.returned()+1;call<=16102;call++) {
                if(call==13886)beforeTargetGeneration=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
                if(call==16102){assertNotNull(owners);previousCpu=owners.host().video().completedCpu().orElseThrow();previousGoal=owners.host().placement().pool().state(33);}
                try {step(h,naturalR12bGoalController(call));}
                catch(RuntimeException|Error failure){throw new AssertionError("Fixed Goal EGL prefix: last returned="+(call-1)+", first failed="+call,failure);}
                assertSame(world,h.mode().getWorldSession());
                if(call==13886){
                    assertTrue(module.act2PastLoadingSelected());assertFalse(module.act2LoadingSelected());
                    var frame=module.requirePlayerFrame();assertNotSame(prepared.frame(),frame);assertTrue(prepared.frame().closed());
                    assertEquals(beforeTargetGeneration+1,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
                    owners=new R12bNaturalOwners(frame,frame.stageSource(),module.act2PastWorldHost(),module.act2PastLoadingVisit(),world,beforeTargetGeneration+1);
                }
                if(owners!=null){
                    assertSame(owners.frame(),module.requirePlayerFrame());assertSame(owners.source(),module.requirePlayerFrame().stageSource());
                    assertSame(owners.host(),module.act2PastWorldHost());assertSame(owners.visit(),module.act2PastLoadingVisit());
                    assertEquals(owners.generation(),h.mode().getLevelManager().getCompletedProductionLoadGeneration());assertFalse(owners.frame().closed());
                }
            }
            assertNotNull(owners);assertNaturalR12bRenderOwners(h,owners);assertNotNull(previousCpu);assertNotNull(previousGoal);
            var host=owners.host();var pool=host.placement().pool();var goal=pool.state(33);
            assertEquals(0x16,goal.id(),"Fixed16102 gap: Goal must be actual whole33/ID16, not a mapping-address alias");assertEquals(4,goal.routine());
            assertEquals(0x16,previousGoal.id());
            long goalMap=com.openggf.game.soniccd.level.CdR12bObjectAssets.View.MAP_SPR_GOAL_SIGNPOST.range().nativeAddress();
            assertEquals(0x21D9B4,goalMap);assertEquals(goalMap,previousGoal.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS));
            assertEquals(5,previousGoal.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.MAP_FRAME));
            assertEquals(0x8381,previousGoal.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE));
            var registry=h.mode().getRewindRegistry();var before=captureNative(h);var full=registry.capture();var course=registry.captureCourse();
            var publication=assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,host.selected());var selected=publication.selection();var inputs=selected.inputs();
            var scene=assertInstanceOf(SonicCdPastDisplay.Ready.class,selected.projection(),"Fixed16102 gap: genuinely selected current scene is not Ready; no alternate cut permitted").scene();
            assertTrue(inputs.displayEnabled());assertTrue(scene.displayEnabled());assertEquals(com.openggf.game.soniccd.level.SonicCdLevel.LEVEL_BACKDROP_PALETTE_INDEX,scene.selectedBackdropPaletteIndex());
            var cpu=inputs.selectedCpu().orElseThrow(()->new AssertionError("Fixed16102 gap: no actual selected prior CPU"));
            assertArrayEquals(cpu.bytes(),inputs.selectedSat().copy());assertArrayEquals(previousCpu.bytes(),cpu.bytes());
            assertEquals(previousCpu.spriteCount(),cpu.spriteCount());assertEquals(previousCpu.provenance(),cpu.provenance());
            assertTrue(cpu.provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==33),"Fixed16102 gap: no selected Goal33; no alternative/fitted cut");
            assertTrue(cpu.provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==0),"Fixed16102 gap: no selected Sonic0");
            assertEquals(0x230F60,owners.frame().state().header().mappingAddress());assertSame(owners.visit().state().section().residency(),inputs.current());
            var goalPatterns=new java.util.TreeSet<Integer>();var sonicPatterns=new java.util.TreeSet<Integer>();var raw=cpu.bytes();
            for(int index=0;index<cpu.spriteCount();index++){
                int whole=cpu.provenance().get(index).slotRef().wholeIndex();if(whole!=0&&whole!=33)continue;
                int size=raw[index*8+2]&15,count=(((size>>>2)&3)+1)*((size&3)+1);
                int first=(((raw[index*8+4]&255)<<8)|(raw[index*8+5]&255))&0x7FF;
                for(int tile=0;tile<count;tile++)(whole==33?goalPatterns:sonicPatterns).add((first+tile)&0x7FF);
            }
            assertFalse(goalPatterns.isEmpty());assertFalse(sonicPatterns.isEmpty());var goalBytes=new java.util.TreeMap<Integer,byte[]>();
            for(int id:goalPatterns){
                assertTrue(id>=0x381&&id<0x381+24,"Native R12B Past Goal base381 plus actual24-pattern source bound");
                var bytes=com.openggf.game.soniccd.resources.CdR12bCameraArtResidency.readTile(inputs.current(),id);goalBytes.put(id,bytes.copy());
                var version=com.openggf.game.soniccd.resources.CdObjectPresentation.patternVersion(bytes);
                assertEquals(version,scene.nativePatterns().get(id),"Actual current Goal pattern32 bytes "+id);
                assertEquals(version,scene.sprites().patternVersions().get(PatternAtlasRange.OBJECTS.base()+id));
            }
            for(int id:sonicPatterns){
                int first=com.openggf.game.soniccd.resources.CdPlayerArtRuntime.VRAM_BYTE_ADDRESS/32;
                assertTrue(id>=first&&id<first+inputs.player().residentBank().length()/32,"Selected Sonic must use the actual existing resident window");
                var bytes=inputs.player().residentBank().slice((id-first)*32,32);var version=com.openggf.game.soniccd.resources.CdObjectPresentation.patternVersion(bytes);
                assertEquals(version,scene.nativePatterns().get(id),"Actual resident Sonic pattern32 bytes "+id);
                assertEquals(version,scene.sprites().patternVersions().get(PatternAtlasRange.OBJECTS.base()+id));
            }
            assertNativeCapture(before,h);assertNaturalR12bSnapshotValue(full,registry.capture(),"Goal16102 full before pixels/forward");assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"Goal16102 course before pixels/forward");
            var actual=drawNaturalR12bImage(h);assertNativeCapture(before,h);assertArrayEquals(actual,drawNaturalR12bImage(h));assertNativeCapture(before,h);
            var binding=actualField(host,"render");assertNotNull(binding);var renderer=assertInstanceOf(RetainedPlaneSceneRenderer.class,actualField(binding,"renderer"));
            assertSame(world,actualField(binding,"world"));assertEquals(owners.generation(),actualField(binding,"generation"));
            var atlas=GameServices.graphics().getPatternAtlas();var ownedPatterns=new java.util.TreeSet<Integer>(scene.nativePatterns().keySet());
            for(int id:goalPatterns)assertEquals(scene.nativePatterns().get(id),SpritePresentation.patternSample(GameServices.graphics(),PatternAtlasRange.OBJECTS.base()+id));
            for(int id:sonicPatterns)assertEquals(scene.nativePatterns().get(id),SpritePresentation.patternSample(GameServices.graphics(),PatternAtlasRange.OBJECTS.base()+id));
            // GPU-only other-producer identity control, never native art or a source substitute.
            atlas.registerRange(PatternAtlasRange.HUD);int other=PatternAtlasRange.HUD.base()+19;
            if(atlas.getEntry(other)==null)GameServices.graphics().cachePatternTexture(new com.openggf.level.Pattern(),other);
            var unrelated=atlas.getEntry(other);var unrelatedSample=SpritePresentation.patternSample(GameServices.graphics(),other);assertNotNull(unrelated);
            var cut=TestSonicCdPastResultsRoute.omitWhole(scene,cpu,33);
            var omitted=new RetainedPlaneSceneRenderer.Scene(cut.foreground(),cut.background(),cut.packedHscroll256(),cut.vscrollLongBits(),cut.selectedPalette128(),
                    scene.displayEnabled(),cut.sprites(),cut.nativePatterns(),scene.selectedBackdropPaletteIndex());
            assertNaturalR12bOmissionPartitions(scene,omitted);assertEquals(scene.sprites().primitives(),omitted.sprites().primitives());assertSame(cut.sprites(),omitted.sprites());assertTrue(omitted.sprites().tiles().size()<scene.sprites().tiles().size());
            var level=assertInstanceOf(com.openggf.level.AbstractLevel.class,h.mode().getLevelManager().getCurrentLevel());var camera=h.mode().getCamera();long epoch=level.currentEpoch();
            clearPixels();renderer.draw(omitted,camera.getX(),camera.getY(),module::validateAct2PastLoadingCut);var withoutGoal=pixels();
            assertFalse(Arrays.equals(actual,withoutGoal),"Fixed16102 gap: renderer-only Goal33 omission changes no visible pixels; no alternate cut permitted");
            clearPixels();renderer.draw(omitted,camera.getX(),camera.getY(),module::validateAct2PastLoadingCut);assertArrayEquals(withoutGoal,pixels());
            assertSame(level,h.mode().getLevelManager().getCurrentLevel());assertEquals(epoch,level.currentEpoch());assertNativeCapture(before,h);
            assertSame(publication,host.selected());assertSame(inputs.current(),selected.inputs().current());assertSame(inputs.player(),selected.inputs().player());assertSame(inputs.foreground(),selected.inputs().foreground());assertSame(inputs.background(),selected.inputs().background());
            assertSame(binding,actualField(host,"render"));assertSame(renderer,actualField(binding,"renderer"));assertSame(unrelated,atlas.getEntry(other));assertEquals(unrelatedSample,SpritePresentation.patternSample(GameServices.graphics(),other));
            for(var tile:goalBytes.entrySet())assertArrayEquals(tile.getValue(),com.openggf.game.soniccd.resources.CdR12bCameraArtResidency.readTile(inputs.current(),tile.getKey()).copy());
            assertNaturalR12bSnapshotValue(full,registry.capture(),"Goal16102 full after omission");assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"Goal16102 course after omission");
            assertArrayEquals(actual,drawNaturalR12bImage(h));assertNativeCapture(before,h);
            // Exactly one ordinary forward edge; complete values are checked before each pixel/replay step.
            assertNaturalR12bSnapshotValue(full,registry.capture(),"Goal full immediately before forward");assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"Goal course immediately before forward");
            step(h,naturalR12bGoalController(16103));assertNaturalR12bRenderOwners(h,owners);var after=captureNative(h);var fullAfter=registry.capture();var courseAfter=registry.captureCourse();
            var afterSelected=assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,host.selected()).selection();var afterScene=assertInstanceOf(SonicCdPastDisplay.Ready.class,afterSelected.projection(),"Fixed16103 forward Ready gap").scene();ownedPatterns.addAll(afterScene.nativePatterns().keySet());
            assertNaturalR12bSnapshotValue(fullAfter,registry.capture(),"Goal16103 full before pixels");assertNaturalR12bSnapshotValue(courseAfter,registry.captureCourse(),"Goal16103 course before pixels");
            var afterPixels=drawNaturalR12bImage(h);assertNativeCapture(after,h);assertArrayEquals(afterPixels,drawNaturalR12bImage(h));assertNativeCapture(after,h);
            for(int scope=0;scope<2;scope++){
                if(scope==0)registry.restore(full);else registry.restoreCourse(course);
                assertNaturalR12bRenderOwners(h,owners);assertNativeCapture(before,h);assertEquals(selected,assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,host.selected()).selection());
                assertNaturalR12bSnapshotValue(full,registry.capture(),"Goal full before replay pixels "+scope);assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"Goal course before replay pixels "+scope);
                assertArrayEquals(actual,drawNaturalR12bImage(h));assertNativeCapture(before,h);
                assertNaturalR12bSnapshotValue(full,registry.capture(),"Goal full before forward "+scope);assertNaturalR12bSnapshotValue(course,registry.captureCourse(),"Goal course before forward "+scope);
                step(h,naturalR12bGoalController(16103));assertNaturalR12bRenderOwners(h,owners);assertNativeCapture(after,h);assertEquals(afterSelected,assertInstanceOf(SonicCdAct2PastWorldHost.Published.class,host.selected()).selection());
                assertNaturalR12bSnapshotValue(fullAfter,registry.capture(),"Goal full after replay before pixels "+scope);assertNaturalR12bSnapshotValue(courseAfter,registry.captureCourse(),"Goal course after replay before pixels "+scope);
                assertArrayEquals(afterPixels,drawNaturalR12bImage(h));assertNativeCapture(after,h);
                assertNaturalR12bSnapshotValue(fullAfter,registry.capture(),"Goal full after replay pixels "+scope);assertNaturalR12bSnapshotValue(courseAfter,registry.captureCourse(),"Goal course after replay pixels "+scope);
                assertSame(unrelated,atlas.getEntry(other));assertEquals(unrelatedSample,SpritePresentation.patternSample(GameServices.graphics(),other));
            }
            exportPixels=actual.clone();exportOmitted=withoutGoal.clone();exportDetails="Goal33 ID16/frame5/map21D9B4/tile8381 current patterns="+goalPatterns+"; selected Sonic0 resident patterns="+sonicPatterns;
            SessionManager.closeGameplaySession();
            for(int id:ownedPatterns){assertNull(atlas.getEntry(PatternAtlasRange.OBJECTS.base()+id));assertNull(SpritePresentation.patternSample(GameServices.graphics(),PatternAtlasRange.OBJECTS.base()+id));}
            assertSame(unrelated,atlas.getEntry(other));assertEquals(unrelatedSample,SpritePresentation.patternSample(GameServices.graphics(),other));
        } finally {fixture.cleanup();}
        // Optional artifacts occur only after module/session/graphics/EGL/fixture cleanup has succeeded.
        System.out.println("ENGINE natural R12B Goal fixed16102: selected Ready Goal33/Sonic0, native current/resident pattern bytes, repeated actual pixels and whole33-only renderer omission; complete FULL/course values and16103 pixels replayed. "+exportDetails+". Cleanup completed; no emulator/audio/regional/full-act claim.");
        writeOptionalNaturalR12bGoalCapture(exportPixels,exportOmitted,exportDetails);
    }

    private static LogicalInputSnapshot naturalR12bGoalController(int call) {
        if(call<1||call>16187)throw new IllegalArgumentException("Fixed measured Goal recipe ends before BigRing16188");
        if(call<=14516)return controller(call);
        int actions=call>=15601&&call<=15632||call>=16042&&call<=16073||call>=16120&&call<=16151?2:0;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,actions,0,false,false),PlayerInputState.neutral());
    }

    /** Existing allowlisted Projector output/root mechanism; unique Goal names, after successful cleanup only. */
    private static void writeOptionalNaturalR12bGoalCapture(byte[] actual,byte[] withoutGoal,String details) throws Exception {
        String output=System.getenv("SONICCD_R12B_PROJECTOR_OUTPUT");if(output==null||output.isBlank())return;
        assertNotNull(actual);assertNotNull(withoutGoal);assertNotNull(details);assertEquals(320*224*4,actual.length);assertEquals(actual.length,withoutGoal.length);assertFalse(Arrays.equals(actual,withoutGoal));
        var directory=Path.of(output);assertTrue(directory.isAbsolute());assertEquals(directory,directory.normalize());assertEquals(output,directory.toString());
        String root=System.getenv("SONICCD_ENGINE_CAPTURE_ROOT");assertNotNull(root,"Explicit canonical ENGINE capture root required");
        var owned=Path.of(root).toRealPath();assertEquals(Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD","captures","engine").toRealPath(),owned);
        assertTrue(directory.startsWith(owned)&&!directory.equals(owned),"Goal output needs its own canonical directory under E/captures/engine");
        for(var ancestor=directory;ancestor!=null;ancestor=ancestor.getParent()){
            assertFalse(Files.isSymbolicLink(ancestor),"Capture output cannot traverse symlinks");
            if(Files.exists(ancestor,java.nio.file.LinkOption.NOFOLLOW_LINKS))assertTrue(Files.isDirectory(ancestor,java.nio.file.LinkOption.NOFOLLOW_LINKS));
        }
        Files.createDirectories(directory);assertEquals(directory,directory.toRealPath());
        String stem="ENGINE-R12B-natural-goal-call-16102";var png=directory.resolve(stem+".png");var omitted=directory.resolve(stem+"-without-parent33.png");var text=directory.resolve(stem+".txt");
        for(var path:java.util.List.of(png,omitted,text))assertFalse(Files.exists(path,java.nio.file.LinkOption.NOFOLLOW_LINKS));
        writeEngineCapturePng(actual,png);writeEngineCapturePng(withoutGoal,omitted);
        Files.writeString(text,"ENGINE OpenGGF original-USA natural R12B Goal, fixed returned call16102.\n"
                +"Original controller1..14516; Right except B15601..15632,16042..16073; fixed pixel cut precedes the existing Boulder window16120..16151.\n"
                +"320x224 bottom-up RGBA, existing configured EGL/native retained renderer; Goal33-only omission preserves CPU/planes/palette/current/display/backdrop.\n"
                +"Complete checkpoint-aware FULL/course native values and repeated actual16102/16103 pixels; "+details+".\n"
                +"Exported after successful module/session/graphics/EGL/fixture cleanup; pixels never feed gameplay. ENGINE/source comparison only; no emulator/audio/regional/full-act claim.\n");
    }
    // END natural R12B Goal16102 EGL proof.
    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
}

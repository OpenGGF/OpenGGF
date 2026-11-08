package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** Actual original-media pass/Frame with labelled constructed entries. No World birth or route claim. */
class TestSonicCdR31aSpringReviewControls {
    @Test void downMainUsesOriginalBottomSolidThenLaunchSoundAndSameVisitAnimation() throws Exception {
        try (var h = present(0, 0)) {
            var source = hull(h, 14, 980).moveByte(SPRITE_FLAGS, 0x86);
            h.placement.pool().write(source);
            var body = (CdSpring) field(h.pass, "spring");
            var observer = (CdSpring.NativeObserver) field(body, "observer");
            int[] animationOccurrences = {0};
            set(body, "observer", new CdSpring.NativeObserver() {
                public void before(CdSpring.OriginalCall call) {
                    if (call.site() == CdSpring.Site.ANIMATION) animationOccurrences[0]++;
                    observer.before(call);
                }
                public void after(CdSpring.OriginalCall call) { observer.after(call); }
            });
            assertEquals(1, h.frame.state().header().slotId());
            assertEquals(16, source.unsignedByte(WIDTH));
            assertEquals(8, source.unsignedByte(Y_RADIUS));
            h.walk();
            assertEquals(16, h.placement.pool().state(32).routine());
            assertEquals(0x1000, h.frame.state().motion().yVelocity());
            assertEquals(1001, h.frame.state().motion().centreY16_16() >>> 16);
            assertEquals(1, h.cut.sounds().size());
            assertInstanceOf(SonicCdGameModule.PastSpringFm.class, h.cut.sounds().getFirst());
            assertTrue(animationOccurrences[0] > 0,
                    "Down must fall through the returning sound into original animation operations");
            // Native Spring/Data/Animations.asm .Compress begins dc.b0: delay zero is intentional.
            assertEquals(0, h.placement.pool().state(32).unsignedByte(ANIMATION_TIME));
            assertEquals(0, h.placement.pool().state(32).unsignedByte(MAP_FRAME));
            h.finish();
        }
    }

    @Test void nativeStoppedSolidKeepsItsExactCauseAcrossAllLaterPublicationFaults() throws Exception {
        for (int stage = 0; stage < 3; stage++) {
            try (var h = present(8, 2)) {
                hull(h, 2, 1031);
                var body = (CdSpring) field(h.pass, "spring");
                var observer = (CdSpring.NativeObserver) field(body, "observer");
                var reached = new AtomicReference<CdSolidObject.NativeStop>();
                var originalFrame = new AtomicReference<CdPlayerBody.State>();
                var fault = new AssertionError("after reached native stop, stage " + stage);
                int selected = stage;
                int[] attempts = {0};
                set(body, "observer", new CdSpring.NativeObserver() {
                    public void before(CdSpring.OriginalCall call) {
                        if (call.site() == CdSpring.Site.SOLID) {
                            var actual = (CdSpring.PrefixSolidCall) call.payload();
                            var stop = assertInstanceOf(CdSolidObject.NativeStopped.class, actual.outcome());
                            assertEquals(CdSolidObject.StopSite.STAND_RELEASE, stop.stop().site());
                            reached.set(stop.stop());
                            originalFrame.set(h.frame.state());
                            if (selected == 0) throw fault;
                        }
                        observer.before(call);
                    }
                    public void after(CdSpring.OriginalCall call) { observer.after(call); }
                });
                h.beforeAdopt = () -> {
                    if (reached.get() != null) {
                        attempts[0]++;
                        if (selected == 1) throw fault;
                    }
                };
                h.afterAdopt = () -> { if (selected == 2 && reached.get() != null) throw fault; };
                var actual = assertThrows(Throwable.class, h::walk);
                assertNotNull(reached.get(), "Must reach the real shared native stop first");
                assertSame(reached.get().originalException(), actual);
                assertTrue(Arrays.stream(actual.getSuppressed()).anyMatch(suppressed -> suppressed == fault));
                assertEquals(selected == 0 ? 0 : 1, attempts[0]);
                assertEquals(selected == 2 ? 8 : 0, h.placement.pool().state(32).flags() & 8);
                if (selected != 2) assertSame(originalFrame.get(), h.frame.state());
                assertNull(field(h.pass, "springInvocation"));
                assertNull(field(body, "pending"));
            }
        }
    }

    @Test void reachedSolidRejectsDivergentCurrentTerrainBeforeItsPlayerPrefix() throws Exception {
        try (var h = present(0, 0)) {
            hull(h, 2, 1031);
            h.placement.pool().write(h.placement.pool().state(32).moveWord(X_VELOCITY, 0x100));
            h.terrainDiverged = true;
            var body = (CdSpring) field(h.pass, "spring");
            var observer = (CdSpring.NativeObserver) field(body, "observer");
            var atEntry = new AtomicReference<CdPlayerBody.State>();
            set(body, "observer", new CdSpring.NativeObserver() {
                public void before(CdSpring.OriginalCall call) {
                    if (call.site() == CdSpring.Site.STORE) atEntry.set(h.frame.state());
                    observer.before(call);
                }
                public void after(CdSpring.OriginalCall call) { observer.after(call); }
            });
            var fault = assertThrows(SonicCdR31aObjectPass.Unowned.class, h::walk);
            assertEquals(SonicCdR31aObjectPass.Boundary.CURRENT_TERRAIN_OWNER, fault.boundary());
            assertNotNull(atEntry.get());
            assertSame(atEntry.get(), h.frame.state());
            assertEquals(0, h.placement.pool().state(32).flags() & 8);
            assertNull(field(h.pass, "springInvocation"));
        }
    }

    @Test void terrainAdmissionDoesNotRejectInitDisplayOnlyOrThePrimaryMirror() throws Exception {
        for (int mode : new int[]{0, 1, 2}) {
            try (var h = new TestSonicCdR31aObjectPass.Harness()) {
                var source = TestSonicCdR31aSpringPass.seed(h, 32, mode == 1 ? 2 : 0, 0);
                if (mode == 2) h.placement.pool().write(source.moveByte(ROUTINE_2, 5));
                h.terrainDiverged = true;
                h.walk();
                h.finish();
            }
        }
    }

    @Test void allNineNativeSpringFramesUseTheirLiteralRegisteredRootExactlyOnce() throws Exception {
        try (var h = new TestSonicCdR31aObjectPass.Harness()) {
            var source = h.objects.springAssets();
            var visuals = List.of(source.vertical(), source.horizontal(), source.diagonal());
            int[] roots = {0x20968A, 0x209690, 0x2096DA};
            var mapper = CdObjectMappings.fromR31aAssets(h.assets, h.objects);
            for (int orientation = 0; orientation < 3; orientation++) {
                int address = roots[orientation];
                for (int frame = 0; frame < 3; frame++) {
                    var nativeFrame = visuals.get(orientation).mappingFrames().get(frame);
                    var pieces = nativeFrame.pieces().stream()
                            .map(p -> new CdObjectDrawQueue.RawPiece(p.y(), p.sizeByte(), p.rawTileWord(), p.x()))
                            .toList();
                    assertEquals(new CdObjectDrawQueue.FrameMapping(nativeFrame.pieces().size(), pieces),
                            mapper.resolve(address, false, frame));
                }
                assertThrows(UnsupportedOperationException.class, () -> mapper.resolve(address, false, 3));
                assertThrows(UnsupportedOperationException.class, () -> mapper.resolve(address, true, 0));
                assertThrows(UnsupportedOperationException.class, () -> mapper.resolve(address | 0xFF000000, false, 0));
                assertThrows(UnsupportedOperationException.class, () -> mapper.resolve(address + 1, false, 0));
            }
        }
    }

    /** Explicit present-primary entry, then the real existing Init dispatch; no actor setter or fake secondary. */
    static TestSonicCdR31aObjectPass.Harness present(int flags, int stand) throws Exception {
        var h = new TestSonicCdR31aObjectPass.Harness();
        var s = h.frame.state(); var m = s.motion();
        var environment = h.frame.environment(); var art = h.frame.artState(); var clock = h.frame.vIntClock();
        var motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), 0, 0, 0, 0,
                m.angle(), flags, m.spriteFlags(), m.xRadius(), m.yRadius(), 0, 0, m.playerJump(),
                m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var state = new CdPlayerBody.State(motion, s.animation(), new CdPlayerBody.Header(1, 0, 0, 0, 0),
                s.timers(), s.history(), s.rotationDistance(), new CdPlayerBody.ObjectLinks(0, stand), s.rotationAngle());
        h.frame.close();
        h.frame = CdPlayerFrameRuntime.fromR31aEntry(h.assets, state, environment, art, clock);
        h.pass = new SonicCdR31aObjectPass(h.assets, h.frame, h.placement, h.objects, h);
        h.service();
        return h;
    }

    /** Literal native up/down Init hull; the constructed current routine is intentional. */
    static CdObjectState hull(TestSonicCdR31aObjectPass.Harness h, int routine, int y) {
        var source = TestSonicCdR31aSpringPass.seed(h, 32, routine, 0)
                .moveByte(SPRITE_FLAGS, 0x84).moveWord(X, 96).moveWord(Y, y)
                .moveByte(WIDTH, 16).moveByte(Y_RADIUS, 8).moveWord(SPRING_SPEED, 0xF000);
        h.placement.pool().write(source);
        return source;
    }
    private static Object field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
}

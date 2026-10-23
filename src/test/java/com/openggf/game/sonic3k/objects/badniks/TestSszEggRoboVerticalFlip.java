package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FixBugs=0: SetUp_ObjAttributes, CreateChild1_Normal, sub_91930,
 * Refresh_ChildPositionAdjusted and CreateChild10_NormalAdjusted.
 * Public flags 0/1/2/3, the pairing word and manual own-routine entries are explicit
 * component inlets. Stock SSZ1 has only flags 0/1; this is no natural V-route proof.
 * No private state, snapshot seeds, reflection, fake clock or policy override.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszEggRoboVerticalFlip {
    private static final int PICTURE_CAMERA_X = 0x100, PICTURE_CAMERA_Y = 0xB80;
    private record Family(EggRoboBadnikInstance body, EggRoboGunArmChildInstance gun,
                          EggRoboJetFlameChildInstance jet, EggRoboShotInstance shot) { }
    private record Ids(ObjectRefId body, ObjectRefId gun, ObjectRefId jet, ObjectRefId shot) { }
    private record Forward(List<SpritePresentation.Frame> frames, List<CompositeSnapshot> worlds) { }
    private record Tile(int pattern, int palette, boolean h, boolean v, boolean high,
                        int mask, float x, float y, float width, float height) { }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void nativeBirthInitMainAndDelayedVersusLiveYHaveIndependentHandV(int flags) throws Exception {
        var fixture = scene();
        Family f = fighter(flags);
        assertEquals(0xC00, f.body().getY(), "INIT fallthrough Swing: $F8/256, fraction retained");
        assertRaw(f);
        assertMapping(f.body(), 1, 0x200, 0xC00, (flags & 1) != 0, (flags & 2) != 0);
        init(f);
        assertRaw(f);
        main(f);
        assertAnchors(f, flags, 0xC00, 0xC00);
        assertPictures(f, flags, 1, 4);
        // Native sequence: $F8+$F0=$1E8 -> live C01; then +$E8=$2D0 -> live C02.
        // $32 is still C00. These literals are operands, not values from the new carrier.
        f.body().update(4, null);
        main(f);
        assertEquals(0xC01, f.body().getY());
        assertAnchors(f, flags, 0xC00, 0xC01);
        f.body().update(6, null);
        main(f);
        assertEquals(0xC02, f.body().getY());
        assertAnchors(f, flags, 0xC00, 0xC02);
        assertPictures(f, flags, 1, 4);
        assertNotNull(fixture.gameplayMode().getRewindRegistry());
    }

    /**
     * Native loc_9159A entries, including INIT's first hover: Swing_UpAndDown
     * (177856), MoveSprite2 (36053), sub_918E2 (198769), history (198484/198805).
     * Literal WORD velocity / 16.16 Y / delayed Gun anchor / Jet frame, not a Java oracle.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void literalFullSwingIncludesBothRepeatedEndpointsAndHistoryClosure(int flags) throws Exception {
        scene(); Family f = fighter(flags); init(f); main(f);
        int[][] rows = {
                {1, 0x00F8, 0x0C00, 0xF800, 0x0C00, 4},
                {2, 0x00F0, 0x0C01, 0xE800, 0x0C00, 4},
                {28, 0x0020, 0x0C0F, 0x5000, 0x0C0F, 4},
                {29, 0x0018, 0x0C0F, 0x6800, 0x0C0F, 5},
                {31, 0x0008, 0x0C0F, 0x8000, 0x0C0F, 5},
                {32, 0x0000, 0x0C0F, 0x8000, 0x0C0F, 5},
                {33, 0xFFF8, 0x0C0F, 0x7800, 0x0C0F, 6},
                {62, 0xFF10, 0x0C00, 0xF800, 0x0C02, 6},
                {63, 0xFF08, 0x0C00, 0x0000, 0x0C01, 6},
                {64, 0xFF08, 0x0BFF, 0x0800, 0x0C00, 6},
                {65, 0xFF10, 0x0BFE, 0x1800, 0x0C00, 6},
                {94, 0xFFF8, 0x0BF0, 0x8000, 0x0BF0, 6},
                {95, 0x0000, 0x0BF0, 0x8000, 0x0BF0, 5},
                {96, 0x0008, 0x0BF0, 0x8800, 0x0BF0, 5},
                {98, 0x0018, 0x0BF0, 0xB000, 0x0BF0, 5},
                {99, 0x0020, 0x0BF0, 0xD000, 0x0BF0, 4},
                {125, 0x00F0, 0x0BFF, 0x0800, 0x0BFD, 4},
                {126, 0x00F8, 0x0C00, 0x0000, 0x0BFE, 4},
                {127, 0x00F8, 0x0C00, 0xF800, 0x0BFF, 4},
                {128, 0x00F0, 0x0C01, 0xE800, 0x0C00, 4}
        };
        int entry = 1;
        for (int[] row : rows) {
            while (entry < row[0]) ordered(f, 2 * ++entry, null);
            assertMotion(f, row[1], row[2], row[3]);
            assertEquals("HOVER", f.body().stateForTest());
            assertAnchors(f, flags, row[4], row[2]);
            assertPictures(f, flags, 1, row[5]);
        }
        // Motion repeats after 126 entries, with extrema +/-15.5 held at31/32 and94/95.
        // n127 repeats n1 motion, but $32=BFF rather than zero; n128 closes both history words.
        // The repeated velocities at64/127 and their following values infer private direction.
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void endpointPausePreservesHistoryThroughUnderflowAndTwoDispatchResume(int flags) throws Exception {
        var fixture = scene(); Family f = endpoint(fixture, fighter(flags), flags, true);
        assertPictures(f, flags, 1, 6);
        assertEquals((flags & 1) == 0 ? 0x1EF : 0x211, f.shot().getX());
        assertEquals((flags & 2) == 0 ? 0xBF9 : 0xC01, f.shot().getY(), "Shot always adds -4");
        assertMapping(f.shot(), 7, f.shot().getX(), f.shot().getY(), (flags & 1) != 0, false);
        ordered(f, 400, fixture.sprite()); // Body ran armed before Gun's 96th cooldown underflow.
        assertEquals(-1, f.gun().cooldownForTest());
        assertEquals("HOVER_SHOOTING", f.body().stateForTest());
        assertMotion(f, 0xFF08, 0xC00, 0);
        assertAnchors(f, flags, 0xC01, 0xC00);
        ordered(f, 402, fixture.sprite()); // Select HOVER and return, without history writes.
        assertEquals("HOVER", f.body().stateForTest());
        assertMotion(f, 0xFF08, 0xC00, 0);
        assertAnchors(f, flags, 0xC01, 0xC00);
        ordered(f, 404, null); // Actual hover64: repeated negative endpoint and frozen $30 -> $32.
        assertMotion(f, 0xFF08, 0xBFF, 0x0800);
        assertAnchors(f, flags, 0xC00, 0xBFF);
        assertPictures(f, flags, 1, 6);
        ordered(f, 406, null);
        assertMotion(f, 0xFF10, 0xBFE, 0x1800);
        assertAnchors(f, flags, 0xC00, 0xBFE);
        assertPictures(f, flags, 1, 6);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void naturalArmingLatchesCooldownAndShotCopiesOnlyH(int flags) throws Exception {
        var fixture = scene();
        Family f = fighter(flags); init(f); main(f);
        f = fire(fixture, f, flags);
        int gunX = f.gun().getX(), gunY = f.gun().getY();
        assertEquals((flags & 2) == 0 ? 0xBFC : 0xC04, gunY);
        assertEquals((gunX + ((flags & 1) == 0 ? 11 : -11)) & 0xFFFF, f.shot().getX());
        assertEquals((flags & 2) == 0 ? 0xBF8 : 0xC00, f.shot().getY(), "Shot always adds -4");
        assertMapping(f.shot(), 7, f.shot().getX(), f.shot().getY(), (flags & 1) != 0, false);
        // Move Player 1 across the Body while it is frozen; cooldown neither follows nor reaims.
        pose(fixture, (flags & 1) == 0 ? 0x300 : 0x100, 0xB00);
        for (int entry = 0; entry < 95; entry++) {
            f.body().update(10 + entry, fixture.sprite());
            f.gun().update(10 + entry, null);
        }
        assertEquals(0, f.gun().cooldownForTest());
        assertEquals(gunX, f.gun().getX()); assertEquals(gunY, f.gun().getY());
        assertMapping(f.gun(), 2, gunX, gunY, (flags & 1) != 0, (flags & 2) != 0);
        f.gun().update(200, null);
        assertEquals(-1, f.gun().cooldownForTest(), "96th cooldown entry underflows");
        assertMapping(f.gun(), 2, gunX, gunY, (flags & 1) != 0, (flags & 2) != 0);
        for (int entry = 0; entry < 32; entry++) f.shot().update(2 + entry, null);
        assertTrue(f.shot().armedForTest());
        assertMapping(f.shot(), 7, f.shot().getX(), f.shot().getY(), (flags & 1) != 0, false);
        int shotY = f.shot().getY(); f.shot().update(40, null);
        assertEquals(shotY, f.shot().getY());
        assertMapping(f.shot(), 7, f.shot().getX(), shotY, (flags & 1) != 0, false);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void retiredParentStillSuppliesMainOperandsBeforeUndrawnLinkClear(int flags) {
        scene(); Family f = fighter(flags); init(f);
        f.body().update(4, null); f.body().update(6, null);
        ObjectLifetimeOps.deleteNoRespawn(f.body());
        main(f);
        assertAnchors(f, flags, 0xC00, 0xC02);
        assertTrue(picture(f.gun(), false).tiles().isEmpty());
        assertTrue(picture(f.jet(), true).tiles().isEmpty());
        assertFalse(f.gun().isDestroyed()); assertFalse(f.jet().isDestroyed());
        main(f);
        assertTrue(f.gun().isDestroyed()); assertTrue(f.jet().isDestroyed());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void realReleaserLaunchUsesLiveFallbackBeforeMirroringGunDy(int flags) {
        scene();
        var body = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 4, flags, false, 0));
        body.setServices(TestEnvironment.objectServices()); manager().addDynamicObject(body);
        body.update(1, null); body.refreshPostCameraRenderState(); body.update(2, null); body.update(3, null);
        var guns = List.copyOf(manager().activeObjectsOfType(EggRoboGunArmChildInstance.class));
        var jets = List.copyOf(manager().activeObjectsOfType(EggRoboJetFlameChildInstance.class));
        for (int release = 0; release < 5; release++) {
            body.refreshPostCameraRenderState(); body.update(0x1200 + 16 * release, null);
        }
        assertEquals("RISING", body.stateForTest());
        var gun = manager().activeObjectsOfType(EggRoboGunArmChildInstance.class).stream()
                .filter(child -> !guns.contains(child)).findFirst().orElseThrow();
        var jet = manager().activeObjectsOfType(EggRoboJetFlameChildInstance.class).stream()
                .filter(child -> !jets.contains(child)).findFirst().orElseThrow();
        var f = new Family(body, gun, jet, null); assertRaw(f); init(f); assertRaw(f);
        body.update(4, null); // Cleared y_vel + -$10 => BFF.F000; no hover history exists.
        main(f);
        assertEquals(0xBFF, body.getY());
        assertEquals((flags & 2) == 0 ? 0xBFB : 0xC03, gun.getY());
        assertEquals((flags & 2) == 0 ? 0xC1B : 0xBE3, jet.getY());
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,0", "2,0", "3,0", "2,1", "3,1", "2,2", "3,2",
            "2,3", "3,3", "2,4", "3,4", "2,5", "3,5", "2,6", "3,6",
            "0,7", "1,7", "0,8", "1,8"})
    void twoFreshCompleteWorldCyclesPreserveExactFamiliesAndImmutableFrames(int flags, int boundary) {
        var fixture = scene();
        Family a = fighter(flags), b = fighter(flags ^ 2); // Same XY and H, different native V.
        a = checkpoint(fixture, a, flags, boundary);
        b = checkpoint(fixture, b, flags ^ 2, boundary);
        Ids aid = ids(a), bid = ids(b);
        assertNotEquals(aid.body(), bid.body()); assertNotEquals(aid.gun(), bid.gun());
        assertNotEquals(aid.jet(), bid.jet());
        Set<ObjectRefId> namespace = namespace();
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        List<SpritePresentation.Frame> atBoundary = pictures(a, b);
        Forward forward = forward(fixture, a, b, boundary);
        CompositeSnapshot after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            Family oldA = a, oldB = b;
            remove(a); remove(b);
            registry.restore(before);
            a = resolve(aid); b = resolve(bid);
            fresh(oldA, a); fresh(oldB, b);
            assertEquals(aid, ids(a)); assertEquals(bid, ids(b));
            assertEquals(namespace, namespace(), "complete remaining identity namespace");
            assertWorld(before, registry.capture());
            assertEquals(atBoundary, pictures(a, b), "latched V/draw across fresh restore");
            Forward replay = forward(fixture, a, b, boundary);
            assertEquals(forward.frames(), replay.frames(), "four ordered passes at endpoint spots");
            assertEquals(forward.worlds().size(), replay.worlds().size());
            for (int entry = 0; entry < forward.worlds().size(); entry++) {
                assertWorld(forward.worlds().get(entry), replay.worlds().get(entry));
            }
            assertWorld(after, registry.capture());
        }
    }

    private static Family checkpoint(HeadlessTestFixture fixture, Family f, int flags, int boundary) {
        if (boundary >= 7) return endpoint(fixture, f, flags, boundary == 8);
        if (boundary == 0) return f; // Raw cleared children.
        init(f);
        if (boundary == 1) return f; // INIT returned; no MAIN draw.
        main(f);
        if (boundary == 2) return f;
        f = fire(fixture, f, flags);
        if (boundary == 3) return f; // Charging Shot, cooldown95.
        if (boundary == 4) {
            for (int i = 0; i < 95; i++) f.gun().update(i, null);
            assertEquals(0, f.gun().cooldownForTest());
        } else if (boundary == 5) {
            ObjectLifetimeOps.deleteNoRespawn(f.body());
            main(f); // Final cooldown work and Jet refresh; both select pending delete.
            assertEquals(94, f.gun().cooldownForTest());
            assertTrue(picture(f.gun(), false).tiles().isEmpty());
        } else {
            for (int i = 0; i < 32; i++) f.shot().update(i, null);
            assertTrue(f.shot().armedForTest());
        }
        return f;
    }

    private static Forward forward(HeadlessTestFixture fixture, Family a, Family b, int boundary) {
        var frames = new ArrayList<SpritePresentation.Frame>();
        var worlds = new ArrayList<CompositeSnapshot>();
        for (int entry = 0; entry < (boundary >= 7 ? 4 : 3); entry++) {
            for (Family f : List.of(a, b)) {
                ordered(f, 400 + entry, null);
                if (f.shot() != null) f.shot().update(400 + entry, null);
                if (boundary == 8 && entry < 2) {
                    assertMotion(f, 0xFF08, 0xC00, 0);
                    assertEquals(entry == 0 ? "HOVER_SHOOTING" : "HOVER", f.body().stateForTest());
                    assertEquals(-1, f.gun().cooldownForTest());
                } else if (boundary == 7 && entry < 2 || boundary == 8 && entry >= 2) {
                    int resumed = boundary == 7 ? entry : entry - 2;
                    assertMotion(f, resumed == 0 ? 0xFF08 : 0xFF10,
                            resumed == 0 ? 0xBFF : 0xBFE, resumed == 0 ? 0x0800 : 0x1800);
                }
            }
            frames.addAll(pictures(a, b));
            worlds.add(fixture.gameplayMode().getRewindRegistry().capture());
        }
        return new Forward(List.copyOf(frames), List.copyOf(worlds));
    }

    /** Only Body/Gun/Jet dispatch here; Shot's own charging entries remain explicit in forward(). */
    private static Family endpoint(HeadlessTestFixture fixture, Family f, int flags, boolean armed) {
        init(f); main(f);
        for (int n = 2; n <= 61; n++) ordered(f, 2 * n, null);
        pose(fixture, (flags & 1) == 0 ? 0x100 : 0x300, 0xC09);
        int shots = manager().activeObjectsOfType(EggRoboShotInstance.class).size();
        ordered(f, 124, fixture.sprite()); // n62 post-movement distance9 rejects.
        assertMotion(f, 0xFF10, 0xC00, 0xF800);
        assertEquals("HOVER", f.body().stateForTest());
        assertEquals(shots, manager().activeObjectsOfType(EggRoboShotInstance.class).size());
        assertAnchors(f, flags, 0xC02, 0xC00);
        if (!armed) {
            ordered(f, 126, null);
            assertMotion(f, 0xFF08, 0xC00, 0);
            assertEquals("HOVER", f.body().stateForTest());
            assertAnchors(f, flags, 0xC01, 0xC00);
            return f;
        }
        f = fire(fixture, f, flags, 126, 0xC08); // n63 distance8 arms after movement.
        assertMotion(f, 0xFF08, 0xC00, 0);
        assertAnchors(f, flags, 0xC01, 0xC00);
        assertEquals((flags & 2) == 0 ? 0xBFD : 0xC05, f.gun().getY());
        pose(fixture, (flags & 1) == 0 ? 0x300 : 0x100, 0xB00);
        for (int entry = 0; entry < 95; entry++) {
            ordered(f, 200 + 2 * entry, fixture.sprite());
            assertEquals(94 - entry, f.gun().cooldownForTest());
            assertEquals("HOVER_SHOOTING", f.body().stateForTest());
            assertMotion(f, 0xFF08, 0xC00, 0);
            assertAnchors(f, flags, 0xC01, 0xC00);
        }
        return f;
    }

    private static void assertMotion(Family f, int velocityWord, int yWord, int fraction) {
        assertEquals(0x200, f.body().getX());
        assertEquals(velocityWord, f.body().currentYVelocity() & 0xFFFF, "native signed velocity WORD");
        assertEquals(yWord, f.body().getY(), "native integer Y");
        assertEquals(fraction, f.body().ySubpixel & 0xFFFF, "native 16-bit fraction");
    }

    private static void ordered(Family f, int vIntRunCount, PlayableEntity player) {
        f.body().update(vIntRunCount, player);
        main(f, vIntRunCount); // Body -> Gun -> Jet: actual parent-clear visibility on next pass.
    }

    private static HeadlessTestFixture scene() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2);
        manager().setRewindInPlaceRestoreEnabledForTest(false);
        GameServices.camera().setX((short) PICTURE_CAMERA_X); GameServices.camera().setY((short) PICTURE_CAMERA_Y);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        ((SszZoneRuntimeState) TestEnvironment.objectServices().zoneRuntimeState()).markEggRoboFlyByPassed(0);
        return fixture;
    }

    private static Family fighter(int flags) {
        var body = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 2, flags, false, 0));
        body.setServices(TestEnvironment.objectServices()); manager().addDynamicObject(body);
        body.update(1, null); body.refreshPostCameraRenderState(); body.update(2, null);
        var guns = List.copyOf(manager().activeObjectsOfType(EggRoboGunArmChildInstance.class));
        var jets = List.copyOf(manager().activeObjectsOfType(EggRoboJetFlameChildInstance.class));
        body.update(0, null); // Actual accepted attributes, allocation and first hover.
        var bornGuns = manager().activeObjectsOfType(EggRoboGunArmChildInstance.class).stream()
                .filter(child -> !guns.contains(child)).toList();
        var bornJets = manager().activeObjectsOfType(EggRoboJetFlameChildInstance.class).stream()
                .filter(child -> !jets.contains(child)).toList();
        assertEquals(1, bornGuns.size()); assertEquals(1, bornJets.size());
        return new Family(body, bornGuns.getFirst(), bornJets.getFirst(), null);
    }

    private static void assertRaw(Family f) {
        assertEquals(0x1E4, f.gun().getX()); assertEquals(0xBFC, f.gun().getY());
        assertEquals(0x1F4, f.jet().getX()); assertEquals(0xC1C, f.jet().getY());
        assertTrue(picture(f.gun(), false).tiles().isEmpty());
        assertTrue(picture(f.jet(), true).tiles().isEmpty());
    }
    private static void init(Family f) { f.jet().update(0, null); f.gun().update(0, null); }
    private static void main(Family f) { main(f, 2); }
    private static void main(Family f, int vIntRunCount) {
        f.gun().update(vIntRunCount, null); f.jet().update(vIntRunCount, null);
    }
    private static void assertAnchors(Family f, int flags, int delayed, int live) {
        assertEquals((flags & 1) == 0 ? 0x1E4 : 0x21C, f.gun().getX());
        assertEquals((flags & 1) == 0 ? 0x1F4 : 0x20C, f.jet().getX());
        assertEquals(delayed + ((flags & 2) == 0 ? -4 : 4), f.gun().getY());
        assertEquals(live + ((flags & 2) == 0 ? 28 : -28), f.jet().getY());
    }
    private static void assertPictures(Family f, int flags, int bodyFrame, int jetFrame) throws Exception {
        boolean h = (flags & 1) != 0, v = (flags & 2) != 0;
        assertMapping(f.body(), bodyFrame, f.body().getX(), f.body().getY(), h, v);
        assertMapping(f.gun(), 2, f.gun().getX(), f.gun().getY(), h, v);
        assertMapping(f.jet(), jetFrame, f.jet().getX(), f.jet().getY(), h, v);
    }
    private static Family fire(HeadlessTestFixture fixture, Family f, int flags) {
        return fire(fixture, f, flags, 8, 0xC00);
    }
    private static Family fire(HeadlessTestFixture fixture, Family f, int flags, int vIntRunCount, int playerY) {
        pose(fixture, (flags & 1) == 0 ? 0x100 : 0x300, playerY);
        var shots = List.copyOf(manager().activeObjectsOfType(EggRoboShotInstance.class));
        ordered(f, vIntRunCount, fixture.sprite());
        assertEquals("HOVER_SHOOTING", f.body().stateForTest());
        assertEquals(95, f.gun().cooldownForTest());
        var born = manager().activeObjectsOfType(EggRoboShotInstance.class).stream()
                .filter(child -> !shots.contains(child)).toList();
        assertEquals(1, born.size());
        return new Family(f.body(), f.gun(), f.jet(), born.getFirst());
    }
    private static void pose(HeadlessTestFixture fixture, int x, int y) {
        NativePositionOps.writeXPosPreserveSubpixel(fixture.sprite(), x);
        NativePositionOps.writeYPosPreserveSubpixel(fixture.sprite(), y);
        assertEquals(x, fixture.sprite().getCentreX() & 0xFFFF);
        assertEquals(y, fixture.sprite().getCentreY() & 0xFFFF);
    }

    /** Actual SAT admission/replay and direct CPU output; no access to private graphics state. */
    private static SpritePresentation.Frame picture(AbstractObjectInstance object, boolean sat) {
        GraphicsManager g = TestEnvironment.objectServices().graphicsManager();
        boolean shader = g.isUseSpritePriorityShader(); int mask = g.getCurrentSpriteTileOcclusionPaletteMask();
        try {
            g.setUseSpritePriorityShader(true); g.setCurrentSpriteTileOcclusionPaletteMask(object.getTileOcclusionPaletteMask());
            return SpritePresentationRenderer.prepare(g, PICTURE_CAMERA_X, PICTURE_CAMERA_Y, () -> {
                SpritePresentation.layer(g, SpritePresentation.Layer.OBJECT);
                if (sat) { g.beginSpriteSatCollection(); g.setCurrentSpriteSatBucket(object.getPriorityBucket()); }
                try { object.appendRenderCommands(new ArrayList<>()); }
                finally { if (sat) g.endSpriteSatCollectionAndReplay(); }
            });
        } finally {
            g.setUseSpritePriorityShader(shader); g.setCurrentSpriteTileOcclusionPaletteMask(mask);
        }
    }

    private static Tile tile(SpritePresentation.Tile t) {
        assertTrue(t.priorityShader()); assertFalse(t.ghost()); assertFalse(t.rowClipped());
        return new Tile(t.patternId(), t.palette(), t.hFlip(), t.vFlip(), t.priority(),
                t.occlusionMask(), t.x(), t.y(), t.width(), t.height());
    }

    /** Independent locked-on ROM mapping expansion, never calls the production flip/position helper. */
    private static void assertMapping(AbstractObjectInstance object, int frame, int x, int y,
                                      boolean h, boolean v) throws Exception {
        var direct = picture(object, false); var sat = picture(object, true);
        assertFalse(direct.tiles().isEmpty()); assertEquals(5, object.getPriorityBucket());
        assertTrue(object.isHighPriority());
        List<Tile> actual = direct.tiles().stream().map(TestSszEggRoboVerticalFlip::tile).toList();
        assertEquals(actual, sat.tiles().stream().map(TestSszEggRoboVerticalFlip::tile).toList(), "actual SAT replay flags/masks/geometry");
        var rom = TestEnvironment.objectServices().rom();
        int at = 0x184F34 + (rom.read16BitAddr(0x184F34 + 2 * frame) & 0xFFFF);
        int pieces = rom.read16BitAddr(at) & 0xFFFF;
        List<Tile> expected = new ArrayList<>();
        // Cache base is an allocation address, not a ROM constant. Infer only that translation;
        // every mapping word, geometry, flip, palette, mask and tile order remains independently checked.
        int base = actual.getFirst().pattern() - (rom.read16BitAddr(at + 4 + 6 * (pieces - 1)) & 0x7FF);
        for (int p = pieces - 1; p >= 0; p--) {
            byte[] row = rom.readBytes(at + 2 + 6 * p, 6);
            int word = ((row[2] & 255) << 8) | (row[3] & 255);
            int w = ((row[1] & 12) >> 2) + 1, height = (row[1] & 3) + 1;
            int dx = (short) (((row[4] & 255) << 8) | (row[5] & 255)), dy = row[0];
            if (h) dx = -dx - 8 * w;
            if (v) dy = -dy - 8 * height;
            boolean ph = h ^ ((word & 0x800) != 0), pv = v ^ ((word & 0x1000) != 0);
            for (int r = 0; r < height; r++) for (int col = 0; col < w; col++) {
                int pattern = base + (word & 0x7FF) + col * height + r;
                int tx = x - PICTURE_CAMERA_X + dx + 8 * (ph ? w - 1 - col : col);
                int ty = y - PICTURE_CAMERA_Y + dy + 8 * (pv ? height - 1 - r : r);
                expected.add(new Tile(pattern, (word >> 13) & 3, ph, pv, true, 0, tx, ty, 8, 8));
            }
        }
        assertEquals(expected, actual, "ROM piece mirror, tile flags, palette, priority and mask");
    }

    private static ObjectManager manager() { return TestEnvironment.objectServices().objectManager(); }
    private static ObjectRefId id(ObjectInstance object) {
        ObjectRefId id = manager().captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id); return id;
    }
    private static Ids ids(Family f) { return new Ids(id(f.body()), id(f.gun()), id(f.jet()), f.shot() == null ? null : id(f.shot())); }
    private static Set<ObjectRefId> namespace() {
        var ids = new HashSet<ObjectRefId>();
        for (ObjectInstance object : manager().getActiveObjects()) assertTrue(ids.add(id(object)));
        return Set.copyOf(ids);
    }
    private static ObjectInstance resolve(ObjectRefId id) {
        return manager().captureIdentityContext().requireIdentityTable().resolveObject(id, true);
    }
    private static Family resolve(Ids ids) {
        return new Family((EggRoboBadnikInstance) resolve(ids.body()), (EggRoboGunArmChildInstance) resolve(ids.gun()),
                (EggRoboJetFlameChildInstance) resolve(ids.jet()), ids.shot() == null ? null : (EggRoboShotInstance) resolve(ids.shot()));
    }
    private static void remove(Family f) {
        if (f.shot() != null) manager().removeDynamicObject(f.shot());
        manager().removeDynamicObject(f.gun()); manager().removeDynamicObject(f.jet()); manager().removeDynamicObject(f.body());
    }
    private static void fresh(Family old, Family restored) {
        assertNotSame(old.body(), restored.body()); assertNotSame(old.gun(), restored.gun()); assertNotSame(old.jet(), restored.jet());
        if (old.shot() != null) assertNotSame(old.shot(), restored.shot());
    }
    private static List<SpritePresentation.Frame> pictures(Family a, Family b) {
        List<SpritePresentation.Frame> frames = new ArrayList<>();
        for (Family f : List.of(a, b)) {
            for (AbstractObjectInstance object : List.of(f.body(), f.gun(), f.jet())) frames.add(picture(object, true));
            if (f.shot() != null) frames.add(picture(f.shot(), true));
        }
        return List.copyOf(frames);
    }
    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), "whole registered key set");
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> key + ": " + diff);
        }
    }
}

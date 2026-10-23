package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * ROM ObjDat3_9199A/919A6/919B2, loc_915F6/loc_9164E and allocation-time art_tile copies.
 * Pairing/request words and manual routine entries are declared component inlets, not route proof.
 * The controlled render manager is real but has no placements; full-world replay uses the actual
 * fixture manager and registry. No palette/flip/scaler/lifetime or V-int publication change is fitted.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszEggRoboArtPriority {
    private record Scene(HeadlessTestFixture fixture, ObjectServices services, ObjectManager objects) { }
    private record Family(EggRoboBadnikInstance body, EggRoboGunArmChildInstance gun,
                          EggRoboJetFlameChildInstance jet, EggRoboShotInstance shot) { }
    private record FamilyIds(ObjectRefId body, ObjectRefId gun, ObjectRefId jet, ObjectRefId shot) { }
    private record ManagedPicture(SpritePresentation.Frame frame, List<SpriteSatEntry> sat) { }
    private record Forward(Family high, Family low, List<SpritePresentation.Frame> pictures) { }

    @Test
    void originalLockedOnRowsAndEveryEggRoboMappingPieceSeparateArtPriorityFromBucket() throws Exception {
        Scene scene = scene(false);
        var rom = scene.services().rom();
        assertArrayEquals(new byte[] {0, 2, 0x4B, (byte) 0xE8, (byte) 0xA5, 0, 2, (byte) 0x80,
                0x20, 0x20, 0, 0}, rom.readBytes(0x9199A, 12));
        assertArrayEquals(new byte[] {0, 0x18, 0x4F, 0x34, (byte) 0x85, 0, 2, (byte) 0x80,
                0x14, 0x18, 1, 6}, rom.readBytes(0x919A6, 12));
        assertArrayEquals(new byte[] {0, 0x18, 0x4F, 0x34, (byte) 0x85, 0, 2, (byte) 0x80,
                4, 4, 0, 0}, rom.readBytes(0x919B2, 12));
        int total = 0;
        for (int frame = 0; frame < 8; frame++) {
            int at = 0x184F34 + (rom.read16BitAddr(0x184F34 + 2 * frame) & 0xFFFF);
            int count = rom.read16BitAddr(at) & 0xFFFF;
            for (int piece = 0; piece < count; piece++) {
                assertEquals(0, rom.read16BitAddr(at + 4 + 6 * piece) & 0x8000);
            }
            total += count;
        }
        assertEquals(17, total);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void rawAndRejectedFightersStayLowUntilTheActualAcceptedAttributesWrite(int flags) {
        Scene scene = scene(false);
        ssz(scene).setEggRoboFlyByBits(0); // Explicit native pairing-word component inlet.
        EggRoboBadnikInstance rejected = raw(scene, 2, flags);
        assertFalse(rejected.isHighPriority());
        wake(rejected);
        assertFalse(rejected.isHighPriority(), "continuation return is not the attributes write");
        rejected.update(3, null);
        assertTrue(rejected.isDestroyed());
        assertFalse(rejected.isHighPriority(), "initialized=true does not certify an art write");
        assertTrue(direct(scene, rejected).tiles().isEmpty());

        ssz(scene).markEggRoboFlyByPassed(0);
        EggRoboBadnikInstance accepted = raw(scene, 2, flags);
        assertFalse(accepted.isHighPriority());
        wake(accepted);
        accepted.update(3, null);
        assertTrue(accepted.isHighPriority());
        assertPriority(scene, accepted, true, true);
        assertEquals(5, accepted.getPriorityBucket());
        Family family = familyOf(scene, accepted);
        assertTrue(family.gun().isHighPriority(), "copy exists before Gun INIT");
        assertTrue(family.jet().isHighPriority(), "copy exists before Jet INIT");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    void theOtherAttributeRowsWriteHighOnlyOnTheirOwnInitialization(int subtype) {
        Scene scene = scene(false);
        EggRoboBadnikInstance body = raw(scene, subtype, 0);
        assertFalse(body.isHighPriority());
        wake(body);
        assertFalse(body.isHighPriority());
        body.update(3, null);
        assertTrue(body.isHighPriority());
        // Both currently use empty frame0 here; the flyby's separate scaled-art gap is not repaired.
        assertTrue(direct(scene, body).tiles().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void releaserCopiesRemainLowThroughDeferredInitAndShotArmingAfterBodyRaises(int flags) {
        Scene scene = scene(false);
        Family family = converted(scene, flags);
        assertFalse(family.body().isHighPriority());
        assertFalse(family.gun().isHighPriority());
        assertFalse(family.jet().isHighPriority());
        assertTrue(direct(scene, family.gun()).tiles().isEmpty());
        assertTrue(direct(scene, family.jet()).tiles().isEmpty());
        raiseBody(family.body());
        assertTrue(family.body().isHighPriority());
        assertFalse(family.gun().isHighPriority(), "deferred INIT cannot borrow the raised body bit");
        assertFalse(family.jet().isHighPriority());
        family = readyChildren(family);
        assertPriority(scene, family.body(), true, true);
        assertPriority(scene, family.gun(), false, true);
        assertPriority(scene, family.jet(), false, true);
        family = fire(scene, family);
        assertSame(family.gun(), field(family.shot(), "chargingGun"));
        assertFalse(family.shot().isHighPriority(), "Shot copies low Gun although its Body is now high");
        arm(family.shot());
        assertNull(field(family.shot(), "chargingGun"));
        assertFalse(family.shot().isHighPriority(), "priority survives the charging link's last-use release");
        assertPriority(scene, family.shot(), false, true);
        assertEquals(5, family.shot().getPriorityBucket());
    }

    @Test
    void realManagedPictureAndSatKeepHighLowHighMasksAndNativeSlotOrder() {
        Scene scene = scene(true);
        Family high = readyChildren(fighter(scene, 0));
        Family low = readyChildren(converted(scene, 0));
        high = fire(scene, high); // The later high Shot sorts in front of the intervening low family.
        arm(high.shot());
        Set<ObjectInstance> expected = Set.of(high.body(), high.gun(), high.jet(), high.shot(),
                low.body(), low.gun(), low.jet());
        assertEquals(expected, Set.copyOf(bucketActors(scene)));
        for (ObjectInstance object : List.of(high.body(), high.gun(), high.jet(), high.shot())) {
            assertPriority(scene, (AbstractObjectInstance) object, true, true);
        }
        for (ObjectInstance object : List.of(low.body(), low.gun(), low.jet())) {
            assertPriority(scene, (AbstractObjectInstance) object, false, true);
        }
        ManagedPicture picture = managed(scene, false);
        assertEquals(List.of(0, 15, 0), maskRuns(picture.frame()));
        assertEquals(referencePicture(scene), picture.frame(),
                "complete real-manager picture agrees with the same raw actors and native SST order");
        List<ObjectRefId> beforeOrder = bucketActors(scene).stream().map(object -> id(scene, object)).toList();
        ManagedPicture sat = managed(scene, true);
        assertEquals(referenceSat(scene), sat.sat(),
                "complete SAT preserves per-piece/global flags, mapping, flips, palette and slot order");
        assertTrue(sat.sat().stream().anyMatch(entry -> entry.piece().piecePriority() && entry.piece().globalHighPriority()));
        assertTrue(sat.sat().stream().anyMatch(entry -> !entry.piece().piecePriority() && !entry.piece().globalHighPriority()));

        raiseBody(low.body());
        assertPriority(scene, low.body(), true, true);
        assertPriority(scene, low.gun(), false, true);
        assertPriority(scene, low.jet(), false, true);
        ManagedPicture raised = managed(scene, false); // refreshRenderBucketsIfChanged must see Body's write.
        assertEquals(referencePicture(scene), raised.frame());
        assertEquals(beforeOrder, bucketActors(scene).stream().map(object -> id(scene, object)).toList(),
                "changing plane priority does not move a sprite to a different SST/bucket position");
    }

    @Test
    void publicAndExistingThreeArgumentInletsStayLowWithoutQueryingTheHighParent() {
        Scene scene = scene(false);
        Family high = readyChildren(fighter(scene, 0));
        var gun = new EggRoboGunArmChildInstance(high.gun().getSpawn(), high.body());
        var jet = new EggRoboJetFlameChildInstance(high.jet().getSpawn(), high.body());
        var direct = new EggRoboShotInstance(new ObjectSpawn(0x200, 0xC00, 0, 0, 0, false, 0), true);
        var player = EggRoboShotInstance.playerShot(direct.getSpawn(), true);
        var oldBound = EggRoboShotInstance.gunShot(direct.getSpawn(), true, high.gun());
        for (AbstractObjectInstance object : List.of(gun, jet, direct, player, oldBound)) {
            assertFalse(object.isHighPriority());
            object.setServices(scene.services());
        }
        gun.update(0, null); jet.update(0, null);
        gun.update(1, null); jet.update(1, null);
        assertPriority(scene, gun, false, true);
        assertPriority(scene, jet, false, true);
        for (EggRoboShotInstance shot : List.of(direct, player, oldBound)) {
            arm(shot);
            assertPriority(scene, shot, false, true);
        }
        // Same actual native Gun geometry/palette/flips, only the two priority outputs differ.
        SpritePresentation.Frame lowFrame = direct(scene, gun), highFrame = direct(scene, high.gun());
        assertEquals(lowFrame.primitives(), highFrame.primitives());
        assertEquals(lowFrame.patternVersions(), highFrame.patternVersions());
        assertEquals(lowFrame.tiles().stream().map(TestSszEggRoboArtPriority::asHigh).toList(), highFrame.tiles());
    }

    @Test
    void aNewPreparedPriorityGenerationDoesNotRewriteThePublishedFrameAndRestoresWholeWorld() {
        Scene scene = scene(false);
        Family low = readyChildren(converted(scene, 0));
        var level = scene.services().levelManager();
        var registry = scene.fixture().gameplayMode().getRewindRegistry();
        LevelSpritePresentation.prepare(level, GameServices.sprites());
        SpritePresentation.Frame oldPrepared = prepared(registry.capture());
        assertFalse(oldPrepared.tiles().isEmpty());
        LevelSpritePresentation.publish(level, PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(oldPrepared, LevelSpritePresentation.published(level));

        raiseBody(low.body());
        LevelSpritePresentation.prepare(level, GameServices.sprites());
        CompositeSnapshot checkpoint = registry.capture();
        SpritePresentation.Frame nextPrepared = prepared(checkpoint);
        assertNotEquals(oldPrepared, nextPrepared);
        assertSame(oldPrepared, LevelSpritePresentation.published(level),
                "owner writes and a new CPU preparation cannot mutate an already published generation");
        LevelSpritePresentation.publish(level, PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(nextPrepared, LevelSpritePresentation.published(level));
        registry.restore(checkpoint);
        assertWorld(checkpoint, registry.capture(), "prepared-new/published-old complete world");
        assertEquals(oldPrepared, LevelSpritePresentation.published(level));
        LevelSpritePresentation.publish(level, PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(nextPrepared, LevelSpritePresentation.published(level));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void exactInitiallyEqualFamiliesReplayBirthDeferredInitChargeAndArmedCopiesTwice(int boundary) {
        Scene scene = scene(false);
        Family high = fighter(scene, 0), low = converted(scene, 0);
        assertEquals(high.gun().getX(), low.gun().getX());
        assertEquals(high.gun().getY(), low.gun().getY());
        assertEquals(high.jet().getX(), low.jet().getX());
        assertEquals(high.jet().getY(), low.jet().getY());
        assertNotEquals(id(scene, high.body()), id(scene, low.body()));
        assertNotEquals(id(scene, high.gun()), id(scene, low.gun()));
        if (boundary >= 1) raiseBody(low.body());
        if (boundary >= 2) {
            high = fire(scene, readyChildren(high));
            low = fire(scene, readyChildren(low));
            high.shot().update(1, null); low.shot().update(1, null);
        }
        if (boundary == 3) {
            finishCharge(high.shot()); finishCharge(low.shot());
            assertNull(field(high.shot(), "chargingGun"));
            assertNull(field(low.shot(), "chargingGun"));
        }
        FamilyIds highIds = ids(scene, high), lowIds = ids(scene, low);
        var registry = scene.fixture().gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        Forward expected = forward(scene, high, low);
        CompositeSnapshot after = registry.capture();
        high = expected.high(); low = expected.low();
        for (int cycle = 0; cycle < 2; cycle++) {
            removeFamily(scene, high); removeFamily(scene, low);
            registry.restore(before);
            Family restoredHigh = resolve(scene, highIds), restoredLow = resolve(scene, lowIds);
            assertFresh(high, restoredHigh); assertFresh(low, restoredLow);
            assertFamilyCopies(scene, restoredHigh, true); assertFamilyCopies(scene, restoredLow, false);
            assertWorld(before, registry.capture(), "fresh priority boundary " + boundary + " cycle " + cycle);
            Forward actual = forward(scene, restoredHigh, restoredLow);
            assertEquals(expected.pictures(), actual.pictures());
            assertWorld(after, registry.capture(), "forward priority boundary " + boundary + " cycle " + cycle);
            high = actual.high(); low = actual.low();
        }
    }

    private static Forward forward(Scene scene, Family high, Family low) {
        if (low.body().stateForTest().equals("RISING")) raiseBody(low.body());
        high = readyChildren(high); low = readyChildren(low);
        if (high.shot() == null) high = fire(scene, high);
        if (low.shot() == null) low = fire(scene, low);
        finishCharge(high.shot()); finishCharge(low.shot());
        high.shot().update(0x4001, null); low.shot().update(0x4001, null);
        assertFamilyCopies(scene, high, true); assertFamilyCopies(scene, low, false);
        List<SpritePresentation.Frame> pictures = new ArrayList<>();
        for (AbstractObjectInstance object : List.of(high.body(), high.gun(), high.jet(), high.shot(),
                low.body(), low.gun(), low.jet(), low.shot())) pictures.add(direct(scene, object));
        pictures.add(managed(scene, false).frame());
        return new Forward(high, low, List.copyOf(pictures));
    }

    private static void assertFamilyCopies(Scene scene, Family family, boolean copy) {
        assertEquals(copy, family.gun().isHighPriority());
        assertEquals(copy, family.jet().isHighPriority());
        assertSame(family.body(), field(family.gun(), "parent"));
        assertSame(family.body(), field(family.jet(), "parent"));
        if (family.shot() != null) {
            assertEquals(copy, family.shot().isHighPriority());
            if (bool(family.shot(), "chargingGunBound")) {
                assertSame(family.gun(), field(family.shot(), "chargingGun"));
                assertEquals(id(scene, family.gun()), id(scene, (ObjectInstance) field(family.shot(), "chargingGun")));
            } else assertNull(field(family.shot(), "chargingGun"));
        }
    }

    private static Scene scene(boolean controlledRender) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2);
        ObjectServices actual = TestEnvironment.objectServices();
        GameServices.camera().setX((short) 0x100); GameServices.camera().setY((short) 0xB80);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        if (!controlledRender) {
            actual.objectManager().setRewindInPlaceRestoreEnabledForTest(false);
            return new Scene(fixture, actual, actual.objectManager());
        }
        // Keep the actual S3K registry/slot layout, with no placements, for the complete render inventory.
        ObjectServices services = mock(ObjectServices.class, delegatesTo(actual));
        var registry = (ObjectRegistry) field(actual.objectManager(), "registry");
        ObjectManager objects = new ObjectManager(List.of(), registry, 0, null, null,
                actual.graphicsManager(), actual.camera(), services);
        doReturn(objects).when(services).objectManager();
        doReturn(objects.objectQuery()).when(services).objectQuery();
        return new Scene(fixture, services, objects);
    }

    private static SszZoneRuntimeState ssz(Scene scene) {
        return (SszZoneRuntimeState) scene.services().zoneRuntimeState();
    }
    private static EggRoboBadnikInstance raw(Scene scene, int subtype, int flags) {
        var body = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, subtype, flags, false, 0));
        body.setServices(scene.services()); scene.objects().addDynamicObject(body);
        return body;
    }
    private static void wake(EggRoboBadnikInstance body) {
        body.update(1, null); body.refreshPostCameraRenderState(); body.update(2, null);
    }
    private static Family fighter(Scene scene, int flags) {
        ssz(scene).markEggRoboFlyByPassed(0);
        var body = raw(scene, 2, flags); wake(body); body.update(3, null);
        return familyOf(scene, body);
    }
    private static Family converted(Scene scene, int flags) {
        var body = raw(scene, 4, flags); wake(body); body.update(3, null);
        assertTrue(body.isHighPriority(), "ObjDat3_919B2 marker");
        for (int release = 0; release < 5; release++) {
            body.refreshPostCameraRenderState();
            body.update(0x1200 + 16 * release, null);
        }
        assertEquals("RISING", body.stateForTest());
        return familyOf(scene, body);
    }
    private static Family familyOf(Scene scene, EggRoboBadnikInstance body) {
        var gun = scene.objects().activeObjectsOfType(EggRoboGunArmChildInstance.class).stream()
                .filter(object -> field(object, "parent") == body).findFirst().orElseThrow();
        var jet = scene.objects().activeObjectsOfType(EggRoboJetFlameChildInstance.class).stream()
                .filter(object -> field(object, "parent") == body).findFirst().orElseThrow();
        return new Family(body, gun, jet, null);
    }
    private static Family readyChildren(Family family) {
        if (field(family.gun(), "phase").toString().equals("INIT")) family.gun().update(0, null);
        if (!bool(family.jet(), "initialized")) family.jet().update(0, null);
        family.jet().update(1, null);
        if (field(family.gun(), "phase").toString().equals("MAIN") && family.shot() == null) {
            family.gun().update(1, null);
        }
        return family;
    }
    private static Family fire(Scene scene, Family family) {
        set(family.body(), "gunArmed", true); // Declared native request-word component inlet.
        family.gun().update(3, null);
        var shot = scene.objects().activeObjectsOfType(EggRoboShotInstance.class).stream()
                .filter(object -> field(object, "chargingGun") == family.gun()).findFirst().orElseThrow();
        return new Family(family.body(), family.gun(), family.jet(), shot);
    }
    private static void raiseBody(EggRoboBadnikInstance body) {
        for (int entry = 0; entry < 64 && body.stateForTest().equals("RISING"); entry++) {
            body.update(0x1301 + entry, null);
        }
        assertEquals("FALLING", body.stateForTest(), "original velocity threshold, not a fitted frame");
        assertTrue(body.isHighPriority());
    }
    private static void arm(EggRoboShotInstance shot) {
        assertEquals(31, shot.chargeForTest()); finishCharge(shot);
    }
    private static void finishCharge(EggRoboShotInstance shot) {
        for (int entry = 0; entry < 32 && !shot.armedForTest(); entry++) shot.update(0x2201 + entry, null);
        assertTrue(shot.armedForTest());
    }

    private static SpritePresentation.Frame direct(Scene scene, AbstractObjectInstance object) {
        GraphicsManager graphics = scene.services().graphicsManager();
        int mask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        boolean shader = graphics.isUseSpritePriorityShader();
        try {
            graphics.setUseSpritePriorityShader(true);
            graphics.setCurrentSpriteTileOcclusionPaletteMask(object.getTileOcclusionPaletteMask());
            return SpritePresentationRenderer.prepare(graphics, 0, 0,
                    () -> object.appendRenderCommands(new ArrayList<>()));
        } finally {
            graphics.setCurrentSpriteTileOcclusionPaletteMask(mask);
            graphics.setUseSpritePriorityShader(shader);
        }
    }
    private static void assertPriority(Scene scene, AbstractObjectInstance object, boolean high, boolean nonempty) {
        assertEquals(high, object.isHighPriority());
        SpritePresentation.Frame frame = direct(scene, object);
        if (nonempty) assertFalse(frame.tiles().isEmpty());
        assertTrue(frame.tiles().stream().allMatch(tile -> tile.priority() == high
                && tile.occlusionMask() == (high ? 0 : 15)));
    }
    private static SpritePresentation.Tile asHigh(SpritePresentation.Tile tile) {
        return new SpritePresentation.Tile(tile.layer(), tile.patternId(), tile.palette(), tile.hFlip(), tile.vFlip(),
                true, tile.x(), tile.y(), tile.width(), tile.height(), tile.priorityShader(), 0,
                tile.ghost(), tile.ghostAlpha(), tile.rowStart(), tile.rowEnd(), tile.subject());
    }

    private static List<ObjectInstance> bucketActors(Scene scene) {
        return scene.objects().getActiveObjects().stream().filter(object -> object.getPriorityBucket() == 5)
                .sorted(Comparator.comparingInt(object -> ((AbstractObjectInstance) object).getSlotIndex()))
                .toList();
    }
    private static ManagedPicture managed(Scene scene, boolean sat) {
        GraphicsManager graphics = scene.services().graphicsManager();
        int mask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        boolean shader = graphics.isUseSpritePriorityShader();
        List<SpriteSatEntry> entries = new ArrayList<>();
        try {
            graphics.setUseSpritePriorityShader(true);
            scene.objects().refreshRenderBucketsIfChanged();
            SpritePresentation.Frame frame = SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
                SpritePresentation.layer(graphics, SpritePresentation.Layer.OBJECT);
                if (sat) { graphics.beginSpriteSatCollection(); graphics.setCurrentSpriteSatBucket(5); }
                try {
                    scene.objects().drawUnifiedBucketWithPriority(5, graphics);
                    if (sat) entries.addAll(satEntries(graphics));
                } finally {
                    if (sat) graphics.endSpriteSatCollectionAndReplay();
                }
            });
            return new ManagedPicture(frame, List.copyOf(entries));
        } finally {
            graphics.setCurrentSpriteTileOcclusionPaletteMask(mask);
            graphics.setUseSpritePriorityShader(shader);
        }
    }
    private static SpritePresentation.Frame referencePicture(Scene scene) {
        GraphicsManager graphics = scene.services().graphicsManager();
        int mask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        boolean shader = graphics.isUseSpritePriorityShader();
        try {
            graphics.setUseSpritePriorityShader(true);
            return SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
                SpritePresentation.layer(graphics, SpritePresentation.Layer.OBJECT);
                scene.objects().drawUnifiedBucket(5, (object, high) ->
                        graphics.setCurrentSpriteTileOcclusionPaletteMask(object.getTileOcclusionPaletteMask()));
            });
        } finally {
            graphics.setCurrentSpriteTileOcclusionPaletteMask(mask);
            graphics.setUseSpritePriorityShader(shader);
        }
    }
    private static List<SpriteSatEntry> referenceSat(Scene scene) {
        GraphicsManager graphics = scene.services().graphicsManager();
        int mask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        boolean shader = graphics.isUseSpritePriorityShader();
        List<SpriteSatEntry> entries = new ArrayList<>();
        try {
            graphics.setUseSpritePriorityShader(true);
            SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
                SpritePresentation.layer(graphics, SpritePresentation.Layer.OBJECT);
                graphics.beginSpriteSatCollection(); graphics.setCurrentSpriteSatBucket(5);
                try {
                    for (ObjectInstance object : bucketActors(scene)) {
                        graphics.setCurrentSpriteTileOcclusionPaletteMask(object.getTileOcclusionPaletteMask());
                        object.appendRenderCommands(new ArrayList<>());
                    }
                    entries.addAll(satEntries(graphics));
                } finally { graphics.endSpriteSatCollectionAndReplay(); }
            });
            return List.copyOf(entries);
        } finally {
            graphics.setCurrentSpriteTileOcclusionPaletteMask(mask);
            graphics.setUseSpritePriorityShader(shader);
        }
    }
    @SuppressWarnings("unchecked")
    private static List<SpriteSatEntry> satEntries(GraphicsManager graphics) {
        return List.copyOf((List<SpriteSatEntry>) field(graphics, "spriteSatEntries"));
    }
    private static List<Integer> maskRuns(SpritePresentation.Frame frame) {
        List<Integer> masks = new ArrayList<>();
        for (SpritePresentation.Tile tile : frame.tiles()) {
            if (masks.isEmpty() || masks.getLast() != tile.occlusionMask()) masks.add(tile.occlusionMask());
        }
        return List.copyOf(masks);
    }
    private static SpritePresentation.Frame prepared(CompositeSnapshot snapshot) {
        return (SpritePresentation.Frame) invoke(snapshot.get("level-sprite-presentation"), "prepared");
    }
    private static FamilyIds ids(Scene scene, Family family) {
        return new FamilyIds(id(scene, family.body()), id(scene, family.gun()), id(scene, family.jet()),
                family.shot() == null ? null : id(scene, family.shot()));
    }
    private static Family resolve(Scene scene, FamilyIds ids) {
        return new Family((EggRoboBadnikInstance) resolve(scene, ids.body()),
                (EggRoboGunArmChildInstance) resolve(scene, ids.gun()),
                (EggRoboJetFlameChildInstance) resolve(scene, ids.jet()),
                ids.shot() == null ? null : (EggRoboShotInstance) resolve(scene, ids.shot()));
    }
    private static void removeFamily(Scene scene, Family family) {
        if (family.shot() != null) scene.objects().removeDynamicObject(family.shot());
        scene.objects().removeDynamicObject(family.gun()); scene.objects().removeDynamicObject(family.jet());
        scene.objects().removeDynamicObject(family.body());
    }
    private static void assertFresh(Family old, Family restored) {
        assertNotSame(old.body(), restored.body()); assertNotSame(old.gun(), restored.gun());
        assertNotSame(old.jet(), restored.jet());
        if (restored.shot() != null) assertNotSame(old.shot(), restored.shot());
    }
    private static ObjectRefId id(Scene scene, ObjectInstance object) {
        ObjectRefId id = scene.objects().captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id); return id;
    }
    private static ObjectInstance resolve(Scene scene, ObjectRefId id) {
        return scene.objects().captureIdentityContext().requireIdentityTable().resolveObject(id, true);
    }
    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)).isEmpty(),
                    () -> label + " " + key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }
    private static boolean bool(Object object, String name) { return (boolean) field(object, name); }
    private static Field declared(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new AssertionError(name);
    }
    private static Object field(Object object, String name) {
        try { return declared(object, name).get(object); }
        catch (IllegalAccessException error) { throw new AssertionError(error); }
    }
    private static void set(Object object, String name, Object value) {
        try { declared(object, name).set(object, value); }
        catch (IllegalAccessException error) { throw new AssertionError(error); }
    }
    private static Object invoke(Object object, String name) {
        try { Method method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}

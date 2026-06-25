package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.badniks.CluckoidBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.DragonflyBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.MantisBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.MushmeanieBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.RibotBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.SnaleBlasterBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.TurboSpikerBadnikInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kBadnikChildGraphRewind {

    private static final String DRAGONFLY_LINKED_BODY_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.DragonflyBadnikInstance$LinkedBodyChild";
    private static final String DRAGONFLY_WING_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.DragonflyBadnikInstance$WingChild";
    private static final String SPIKER_SIDE_LAUNCHER_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance$SpikerSideLauncherChild";
    private static final String SPIKER_TOP_SPIKE_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance$SpikerTopSpikeChild";
    private static final String TURBO_SPIKER_SHELL_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.TurboSpikerBadnikInstance$TurboSpikerShellChild";
    private static final String TURBO_SPIKER_TRAIL_EMITTER =
            "com.openggf.game.sonic3k.objects.badniks.TurboSpikerBadnikInstance$TurboSpikerTrailEmitter";
    private static final String TURBO_SPIKER_WATERFALL_OVERLAY_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.TurboSpikerBadnikInstance$TurboSpikerWaterfallOverlayChild";
    private static final String CLUCKOID_ARROW_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.CluckoidBadnikInstance$ArrowChild";
    private static final String MANTIS_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.MantisBadnikInstance$MantisChild";
    private static final String MUSHMEANIE_SHELL_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.MushmeanieBadnikInstance$ShellChild";
    private static final String RIBOT_ACTIVE_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.RibotBadnikInstance$RibotActiveChild";
    private static final String RIBOT_VISUAL_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.RibotBadnikInstance$RibotVisualChild";
    private static final String SNALE_BLASTER_COVER_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.SnaleBlasterBadnikInstance$SnaleBlasterCoverChild";
    private static final String SNALE_BLASTER_SHOOTER_CHILD =
            "com.openggf.game.sonic3k.objects.badniks.SnaleBlasterBadnikInstance$SnaleBlasterShooterChild";

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x400, 0x300, 0);
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void dragonflyLinkedBodyGraphRestoresExactParentAndPreviousSegmentByIdentity() {
        Harness harness = Harness.create(new MhzTestRegistry(), List.of(
                new ObjectSpawn(0x120, 0x100, Sonic3kObjectIds.DRAGONFLY, 0, 0, false, 10),
                new ObjectSpawn(0x260, 0x140, Sonic3kObjectIds.DRAGONFLY, 0, 0, false, 11)));
        ObjectManager objectManager = harness.objectManager();
        TestablePlayableSprite player = player();
        List<DragonflyBadnikInstance> sourceParents = liveByType(objectManager, DragonflyBadnikInstance.class);
        assertEquals(2, sourceParents.size(), "precondition: two Dragonfly parents must be captured");
        DragonflyBadnikInstance sourceParentA = sourceParents.get(0);
        DragonflyBadnikInstance sourceParentB = sourceParents.get(1);
        sourceParentA.update(0, player);
        sourceParentA.update(1, player);
        sourceParentB.update(0, player);
        sourceParentB.update(1, player);
        List<ObjectInstance> sourceSegments = liveByClassName(objectManager, DRAGONFLY_LINKED_BODY_CHILD);
        assertEquals(14, sourceSegments.size(), "precondition: Dragonfly setup must create seven body links per parent");
        List<ObjectInstance> sourceWings = liveByClassName(objectManager, DRAGONFLY_WING_CHILD);
        assertEquals(2, sourceWings.size(), "precondition: each Dragonfly must create one wing child");

        ObjectInstance sourceSegmentA0 = segmentByParentAndIndex(sourceSegments, sourceParentA, 0);
        ObjectInstance sourceSegmentA1 = segmentByParentAndIndex(sourceSegments, sourceParentA, 1);
        ObjectInstance sourceSegmentB0 = segmentByParentAndIndex(sourceSegments, sourceParentB, 0);
        ObjectInstance sourceSegmentB1 = segmentByParentAndIndex(sourceSegments, sourceParentB, 1);
        ObjectInstance sourceWingA = childWithParent(sourceWings, sourceParentA);
        ObjectInstance sourceWingB = childWithParent(sourceWings, sourceParentB);
        setIntField(sourceSegmentA0, "childX", 0x1A0);
        setIntField(sourceSegmentA0, "childY", 0x120);
        setIntField(sourceSegmentA0, "countdown", 7);
        setIntField(sourceSegmentA1, "childX", 0x1B0);
        setIntField(sourceSegmentA1, "childY", 0x118);
        setIntField(sourceSegmentA1, "countdown", 5);
        setIntField(sourceSegmentB0, "childX", 0x268);
        setIntField(sourceSegmentB0, "childY", 0x150);
        setIntField(sourceSegmentB0, "countdown", 3);
        setIntField(sourceSegmentB1, "childX", 0x278);
        setIntField(sourceSegmentB1, "childY", 0x158);
        setIntField(sourceSegmentB1, "countdown", 2);

        ObjectRefId parentAId = objectId(objectManager, sourceParentA);
        ObjectRefId parentBId = objectId(objectManager, sourceParentB);
        ObjectRefId segmentA0Id = objectId(objectManager, sourceSegmentA0);
        ObjectRefId segmentA1Id = objectId(objectManager, sourceSegmentA1);
        ObjectRefId segmentB0Id = objectId(objectManager, sourceSegmentB0);
        ObjectRefId segmentB1Id = objectId(objectManager, sourceSegmentB1);
        ObjectRefId wingAId = objectId(objectManager, sourceWingA);
        ObjectRefId wingBId = objectId(objectManager, sourceWingB);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.createDynamicObject(() -> new DragonflyBadnikInstance(new ObjectSpawn(
                0x2C0, 0x160, Sonic3kObjectIds.DRAGONFLY, 0, 0, false, 11)));

        rewindRegistry.restore(snapshot);

        DragonflyBadnikInstance restoredParentA = objectById(objectManager, DragonflyBadnikInstance.class, parentAId);
        DragonflyBadnikInstance restoredParentB = objectById(objectManager, DragonflyBadnikInstance.class, parentBId);
        List<ObjectInstance> restoredSegments = liveByClassName(objectManager, DRAGONFLY_LINKED_BODY_CHILD);
        assertEquals(14, restoredSegments.size(), "restore must keep exactly the captured fourteen body links");
        List<ObjectInstance> restoredWings = liveByClassName(objectManager, DRAGONFLY_WING_CHILD);
        assertEquals(2, restoredWings.size(), "restore must keep exactly the captured two wing children");
        ObjectInstance restoredSegmentA0 = objectById(objectManager, ObjectInstance.class, segmentA0Id);
        ObjectInstance restoredSegmentA1 = objectById(objectManager, ObjectInstance.class, segmentA1Id);
        ObjectInstance restoredSegmentB0 = objectById(objectManager, ObjectInstance.class, segmentB0Id);
        ObjectInstance restoredSegmentB1 = objectById(objectManager, ObjectInstance.class, segmentB1Id);
        ObjectInstance restoredWingA = objectById(objectManager, ObjectInstance.class, wingAId);
        ObjectInstance restoredWingB = objectById(objectManager, ObjectInstance.class, wingBId);

        assertNotSame(sourceSegmentA0, restoredSegmentA0, "restore must recreate removed graph A body segment 0");
        assertNotSame(sourceSegmentA1, restoredSegmentA1, "restore must recreate removed graph A body segment 1");
        assertNotSame(sourceSegmentB0, restoredSegmentB0, "restore must recreate removed graph B body segment 0");
        assertNotSame(sourceSegmentB1, restoredSegmentB1, "restore must recreate removed graph B body segment 1");
        assertNotSame(sourceWingA, restoredWingA, "restore must recreate removed graph A wing child");
        assertNotSame(sourceWingB, restoredWingB, "restore must recreate removed graph B wing child");
        assertSame(restoredParentA, readObjectField(restoredWingA, "parent"),
                "graph A wing parent must resolve to restored Dragonfly A");
        assertSame(restoredParentB, readObjectField(restoredWingB, "parent"),
                "graph B wing parent must resolve to restored Dragonfly B");
        assertSame(restoredParentA, readObjectField(restoredSegmentA0, "parent"),
                "graph A segment 0 parent must resolve to restored Dragonfly A");
        assertSame(restoredParentA, readObjectField(restoredSegmentA0, "followAnchor"),
                "graph A segment 0 followAnchor must resolve to restored Dragonfly A");
        assertSame(restoredParentA, readObjectField(restoredSegmentA1, "parent"),
                "graph A segment 1 parent must resolve to restored Dragonfly A");
        assertSame(restoredSegmentA0, readObjectField(restoredSegmentA1, "followAnchor"),
                "graph A segment 1 followAnchor must resolve to restored graph A segment 0");
        assertSame(restoredParentB, readObjectField(restoredSegmentB0, "parent"),
                "graph B segment 0 parent must resolve to restored Dragonfly B");
        assertSame(restoredParentB, readObjectField(restoredSegmentB0, "followAnchor"),
                "graph B segment 0 followAnchor must resolve to restored Dragonfly B");
        assertSame(restoredParentB, readObjectField(restoredSegmentB1, "parent"),
                "graph B segment 1 parent must resolve to restored Dragonfly B");
        assertSame(restoredSegmentB0, readObjectField(restoredSegmentB1, "followAnchor"),
                "graph B segment 1 followAnchor must resolve to restored graph B segment 0");
        assertEquals(0x1A0, readIntField(restoredSegmentA0, "childX"));
        assertEquals(0x120, readIntField(restoredSegmentA0, "childY"));
        assertEquals(7, readIntField(restoredSegmentA0, "countdown"));
        assertEquals(0x1B0, readIntField(restoredSegmentA1, "childX"));
        assertEquals(0x118, readIntField(restoredSegmentA1, "childY"));
        assertEquals(5, readIntField(restoredSegmentA1, "countdown"));
        assertEquals(0x268, readIntField(restoredSegmentB0, "childX"));
        assertEquals(0x150, readIntField(restoredSegmentB0, "childY"));
        assertEquals(3, readIntField(restoredSegmentB0, "countdown"));
        assertEquals(0x278, readIntField(restoredSegmentB1, "childX"));
        assertEquals(0x158, readIntField(restoredSegmentB1, "childY"));
        assertEquals(2, readIntField(restoredSegmentB1, "countdown"));
    }

    @Test
    void spikerTopSpikeRestoresExactParentAndCooldownState() {
        Harness harness = Harness.create(new S3klTestRegistry(), List.of(
                new ObjectSpawn(0x160, 0x120, Sonic3kObjectIds.SPIKER, 0, 0, false, 20),
                new ObjectSpawn(0x260, 0x120, Sonic3kObjectIds.SPIKER, 0, 0, false, 21)));
        ObjectManager objectManager = harness.objectManager();
        List<SpikerBadnikInstance> sourceParents = liveByType(objectManager, SpikerBadnikInstance.class);
        assertEquals(2, sourceParents.size(), "precondition: two Spiker parents must be captured");
        SpikerBadnikInstance sourceParentA = sourceParents.get(0);
        SpikerBadnikInstance sourceParentB = sourceParents.get(1);
        sourceParentA.update(0, player());
        sourceParentA.update(1, player());
        sourceParentB.update(0, player());
        sourceParentB.update(1, player());
        List<ObjectInstance> sourceTopSpikes = liveByClassName(objectManager, SPIKER_TOP_SPIKE_CHILD);
        assertEquals(2, sourceTopSpikes.size(), "precondition: each Spiker must create one top spike");
        List<ObjectInstance> sourceSideLaunchers = liveByClassName(objectManager, SPIKER_SIDE_LAUNCHER_CHILD);
        assertEquals(4, sourceSideLaunchers.size(), "precondition: each Spiker must create two side launchers");
        ObjectInstance sourceTopSpikeA = childWithParent(sourceTopSpikes, sourceParentA);
        ObjectInstance sourceTopSpikeB = childWithParent(sourceTopSpikes, sourceParentB);
        ObjectInstance sourceLeftLauncherA = childWithParentAndSide(sourceSideLaunchers, sourceParentA, true);
        ObjectInstance sourceRightLauncherA = childWithParentAndSide(sourceSideLaunchers, sourceParentA, false);
        ObjectInstance sourceLeftLauncherB = childWithParentAndSide(sourceSideLaunchers, sourceParentB, true);
        ObjectInstance sourceRightLauncherB = childWithParentAndSide(sourceSideLaunchers, sourceParentB, false);
        setIntField(sourceTopSpikeA, "cooldown", 9);
        setIntField(sourceTopSpikeB, "cooldown", 4);

        ObjectRefId parentAId = objectId(objectManager, sourceParentA);
        ObjectRefId parentBId = objectId(objectManager, sourceParentB);
        ObjectRefId topSpikeAId = objectId(objectManager, sourceTopSpikeA);
        ObjectRefId topSpikeBId = objectId(objectManager, sourceTopSpikeB);
        ObjectRefId leftLauncherAId = objectId(objectManager, sourceLeftLauncherA);
        ObjectRefId rightLauncherAId = objectId(objectManager, sourceRightLauncherA);
        ObjectRefId leftLauncherBId = objectId(objectManager, sourceLeftLauncherB);
        ObjectRefId rightLauncherBId = objectId(objectManager, sourceRightLauncherB);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.createDynamicObject(() -> new SpikerBadnikInstance(new ObjectSpawn(
                0x300, 0x180, Sonic3kObjectIds.SPIKER, 0, 0, false, 21)));

        rewindRegistry.restore(snapshot);

        SpikerBadnikInstance restoredParentA = objectById(objectManager, SpikerBadnikInstance.class, parentAId);
        SpikerBadnikInstance restoredParentB = objectById(objectManager, SpikerBadnikInstance.class, parentBId);
        ObjectInstance restoredTopSpikeA = objectById(objectManager, ObjectInstance.class, topSpikeAId);
        ObjectInstance restoredTopSpikeB = objectById(objectManager, ObjectInstance.class, topSpikeBId);
        ObjectInstance restoredLeftLauncherA = objectById(objectManager, ObjectInstance.class, leftLauncherAId);
        ObjectInstance restoredRightLauncherA = objectById(objectManager, ObjectInstance.class, rightLauncherAId);
        ObjectInstance restoredLeftLauncherB = objectById(objectManager, ObjectInstance.class, leftLauncherBId);
        ObjectInstance restoredRightLauncherB = objectById(objectManager, ObjectInstance.class, rightLauncherBId);
        assertNotSame(sourceTopSpikeA, restoredTopSpikeA, "restore must recreate removed top spike A");
        assertNotSame(sourceTopSpikeB, restoredTopSpikeB, "restore must recreate removed top spike B");
        assertNotSame(sourceLeftLauncherA, restoredLeftLauncherA, "restore must recreate left launcher A");
        assertNotSame(sourceRightLauncherA, restoredRightLauncherA, "restore must recreate right launcher A");
        assertNotSame(sourceLeftLauncherB, restoredLeftLauncherB, "restore must recreate left launcher B");
        assertNotSame(sourceRightLauncherB, restoredRightLauncherB, "restore must recreate right launcher B");
        assertSame(restoredParentA, readObjectField(restoredTopSpikeA, "parent"),
                "top spike A parent must resolve to restored Spiker A");
        assertSame(restoredParentB, readObjectField(restoredTopSpikeB, "parent"),
                "top spike B parent must resolve to restored Spiker B");
        assertSame(restoredParentA, readObjectField(restoredLeftLauncherA, "parent"),
                "left launcher A parent must resolve to restored Spiker A");
        assertSame(restoredParentA, readObjectField(restoredRightLauncherA, "parent"),
                "right launcher A parent must resolve to restored Spiker A");
        assertSame(restoredParentB, readObjectField(restoredLeftLauncherB, "parent"),
                "left launcher B parent must resolve to restored Spiker B");
        assertSame(restoredParentB, readObjectField(restoredRightLauncherB, "parent"),
                "right launcher B parent must resolve to restored Spiker B");
        assertSame(restoredLeftLauncherA, readObjectField(restoredParentA, "leftLauncher"),
                "Spiker A leftLauncher slot must resolve to restored left launcher");
        assertSame(restoredRightLauncherA, readObjectField(restoredParentA, "rightLauncher"),
                "Spiker A rightLauncher slot must resolve to restored right launcher");
        assertSame(restoredTopSpikeA, readObjectField(restoredParentA, "topSpike"),
                "Spiker A topSpike slot must resolve to restored top spike");
        assertSame(restoredLeftLauncherB, readObjectField(restoredParentB, "leftLauncher"),
                "Spiker B leftLauncher slot must resolve to restored left launcher");
        assertSame(restoredRightLauncherB, readObjectField(restoredParentB, "rightLauncher"),
                "Spiker B rightLauncher slot must resolve to restored right launcher");
        assertSame(restoredTopSpikeB, readObjectField(restoredParentB, "topSpike"),
                "Spiker B topSpike slot must resolve to restored top spike");
        assertTrue((Boolean) readObjectField(restoredLeftLauncherA, "leftSide"));
        assertTrue(!(Boolean) readObjectField(restoredRightLauncherA, "leftSide"));
        assertTrue((Boolean) readObjectField(restoredLeftLauncherB, "leftSide"));
        assertTrue(!(Boolean) readObjectField(restoredRightLauncherB, "leftSide"));
        assertEquals(9, readIntField(restoredTopSpikeA, "cooldown"),
                "top spike A cooldown must be restored from compact state");
        assertEquals(4, readIntField(restoredTopSpikeB, "cooldown"),
                "top spike B cooldown must be restored from compact state");
    }

    @Test
    void turboSpikerShellRestoresExactParentAndAttachedShellStateWithoutTrailEmitter() {
        Harness harness = Harness.create(new S3klTestRegistry(), List.of(
                new ObjectSpawn(0x1C0, 0x140, Sonic3kObjectIds.TURBO_SPIKER, 4, 0, false, 30),
                new ObjectSpawn(0x2A0, 0x140, Sonic3kObjectIds.TURBO_SPIKER, 4, 0, false, 31)));
        ObjectManager objectManager = harness.objectManager();
        TestablePlayableSprite player = player();
        List<TurboSpikerBadnikInstance> sourceParents = liveByType(objectManager, TurboSpikerBadnikInstance.class);
        assertEquals(2, sourceParents.size(), "precondition: two Turbo Spiker parents must be captured");
        TurboSpikerBadnikInstance sourceParentA = sourceParents.get(0);
        TurboSpikerBadnikInstance sourceParentB = sourceParents.get(1);
        sourceParentA.update(0, player);
        sourceParentB.update(0, player);
        List<ObjectInstance> sourceShells = liveByClassName(objectManager, TURBO_SPIKER_SHELL_CHILD);
        assertEquals(2, sourceShells.size(), "precondition: each Turbo Spiker must create one shell");
        ObjectInstance sourceShellA = childWithParent(sourceShells, sourceParentA);
        ObjectInstance sourceShellB = childWithParent(sourceShells, sourceParentB);
        setIntField(sourceShellA, "currentX", 0x1D8);
        setIntField(sourceShellA, "currentY", 0x148);
        setIntField(sourceShellA, "xVelocity", 0x33);
        setIntField(sourceShellA, "yVelocity", -0x44);
        setIntField(sourceShellB, "currentX", 0x2B8);
        setIntField(sourceShellB, "currentY", 0x158);
        setIntField(sourceShellB, "xVelocity", 0x55);
        setIntField(sourceShellB, "yVelocity", -0x66);

        ObjectRefId parentAId = objectId(objectManager, sourceParentA);
        ObjectRefId parentBId = objectId(objectManager, sourceParentB);
        ObjectRefId shellAId = objectId(objectManager, sourceShellA);
        ObjectRefId shellBId = objectId(objectManager, sourceShellB);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.createDynamicObject(() -> new TurboSpikerBadnikInstance(new ObjectSpawn(
                0x340, 0x180, Sonic3kObjectIds.TURBO_SPIKER, 4, 0, false, 31)));

        rewindRegistry.restore(snapshot);

        TurboSpikerBadnikInstance restoredParentA =
                objectById(objectManager, TurboSpikerBadnikInstance.class, parentAId);
        TurboSpikerBadnikInstance restoredParentB =
                objectById(objectManager, TurboSpikerBadnikInstance.class, parentBId);
        ObjectInstance restoredShellA = objectById(objectManager, ObjectInstance.class, shellAId);
        ObjectInstance restoredShellB = objectById(objectManager, ObjectInstance.class, shellBId);
        assertNotSame(sourceShellA, restoredShellA, "restore must recreate removed shell child A");
        assertNotSame(sourceShellB, restoredShellB, "restore must recreate removed shell child B");
        assertSame(restoredParentA, readObjectField(restoredShellA, "parent"),
                "shell A parent must resolve to restored Turbo Spiker A");
        assertSame(restoredParentB, readObjectField(restoredShellB, "parent"),
                "shell B parent must resolve to restored Turbo Spiker B");
        assertTrue((Boolean) readObjectField(restoredShellA, "attached"),
                "attached shell A state must restore as attached");
        assertTrue((Boolean) readObjectField(restoredShellB, "attached"),
                "attached shell B state must restore as attached");
        assertNull(readObjectField(restoredShellA, "trailEmitter"),
                "attached shell A restore must not synthesize a launched-shell trail emitter");
        assertNull(readObjectField(restoredShellB, "trailEmitter"),
                "attached shell B restore must not synthesize a launched-shell trail emitter");
        assertEquals(0x1D8, readIntField(restoredShellA, "currentX"));
        assertEquals(0x148, readIntField(restoredShellA, "currentY"));
        assertEquals(0x33, readIntField(restoredShellA, "xVelocity"));
        assertEquals(-0x44, readIntField(restoredShellA, "yVelocity"));
        assertEquals(0x2B8, readIntField(restoredShellB, "currentX"));
        assertEquals(0x158, readIntField(restoredShellB, "currentY"));
        assertEquals(0x55, readIntField(restoredShellB, "xVelocity"));
        assertEquals(-0x66, readIntField(restoredShellB, "yVelocity"));
    }

    @Test
    void turboSpikerLaunchedShellTrailAndWaterfallOverlayRelinkToRestoredOwners() {
        Harness harness = Harness.create(new S3klTestRegistry(), List.of(
                new ObjectSpawn(0x1C0, 0x140, Sonic3kObjectIds.TURBO_SPIKER, 4, 0, false, 32),
                new ObjectSpawn(0x2A0, 0x140, Sonic3kObjectIds.TURBO_SPIKER, 4, 2, false, 33)));
        ObjectManager objectManager = harness.objectManager();
        TestablePlayableSprite player = player();
        List<TurboSpikerBadnikInstance> sourceParents = liveByType(objectManager, TurboSpikerBadnikInstance.class);
        assertEquals(2, sourceParents.size(), "precondition: two Turbo Spiker parents must be captured");
        TurboSpikerBadnikInstance sourceParentA = sourceParents.get(0);
        TurboSpikerBadnikInstance sourceParentB = sourceParents.get(1);
        sourceParentA.update(0, player);
        sourceParentB.update(0, player);
        List<ObjectInstance> sourceShells = liveByClassName(objectManager, TURBO_SPIKER_SHELL_CHILD);
        assertEquals(2, sourceShells.size(), "precondition: each Turbo Spiker must create one shell");
        List<ObjectInstance> sourceOverlays = liveByClassName(objectManager, TURBO_SPIKER_WATERFALL_OVERLAY_CHILD);
        assertEquals(1, sourceOverlays.size(), "precondition: hidden Turbo Spiker must create one overlay");
        ObjectInstance sourceShellA = childWithParent(sourceShells, sourceParentA);
        ObjectInstance sourceShellB = childWithParent(sourceShells, sourceParentB);
        ObjectInstance sourceOverlayB = childWithParent(sourceOverlays, sourceParentB);
        setObjectField(sourceParentA, "shellChild", sourceShellA);
        setObjectField(sourceParentB, "shellChild", sourceShellB);
        setObjectField(sourceShellA, "attached", false);
        setIntField(sourceShellA, "currentX", 0x1E8);
        setIntField(sourceShellA, "currentY", 0x150);
        ObjectInstance sourceTrailA = objectManager.createDynamicObject(
                () -> instantiateTurboSpikerTrailEmitter(sourceShellA));
        setObjectField(sourceShellA, "trailEmitter", sourceTrailA);
        setObjectField(sourceTrailA, "shell", sourceShellA);
        setIntField(sourceTrailA, "mappingFrame", 7);

        ObjectRefId parentAId = objectId(objectManager, sourceParentA);
        ObjectRefId parentBId = objectId(objectManager, sourceParentB);
        ObjectRefId shellAId = objectId(objectManager, sourceShellA);
        ObjectRefId shellBId = objectId(objectManager, sourceShellB);
        ObjectRefId trailAId = objectId(objectManager, sourceTrailA);
        ObjectRefId overlayBId = objectId(objectManager, sourceOverlayB);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.createDynamicObject(() -> new TurboSpikerBadnikInstance(new ObjectSpawn(
                0x340, 0x180, Sonic3kObjectIds.TURBO_SPIKER, 4, 0, false, 34)));

        rewindRegistry.restore(snapshot);

        TurboSpikerBadnikInstance restoredParentA =
                objectById(objectManager, TurboSpikerBadnikInstance.class, parentAId);
        TurboSpikerBadnikInstance restoredParentB =
                objectById(objectManager, TurboSpikerBadnikInstance.class, parentBId);
        ObjectInstance restoredShellA = objectById(objectManager, ObjectInstance.class, shellAId);
        ObjectInstance restoredShellB = objectById(objectManager, ObjectInstance.class, shellBId);
        ObjectInstance restoredTrailA = objectById(objectManager, ObjectInstance.class, trailAId);
        ObjectInstance restoredOverlayB = objectById(objectManager, ObjectInstance.class, overlayBId);
        assertNotSame(sourceTrailA, restoredTrailA, "restore must recreate removed trail emitter");
        assertNotSame(sourceOverlayB, restoredOverlayB, "restore must recreate removed waterfall overlay");
        assertSame(restoredShellA, readObjectField(restoredTrailA, "shell"),
                "trail emitter must resolve to restored launched shell");
        assertSame(restoredTrailA, readObjectField(restoredShellA, "trailEmitter"),
                "shell A trail slot must not retain stale pre-restore emitter");
        assertSame(restoredParentA, readObjectField(restoredShellA, "parent"),
                "launched shell A parent must resolve to restored Turbo Spiker A");
        assertSame(restoredParentB, readObjectField(restoredShellB, "parent"),
                "attached shell B parent must resolve to restored Turbo Spiker B");
        assertSame(restoredShellA, readObjectField(restoredParentA, "shellChild"),
                "parent A shell slot must resolve to restored shell A");
        assertSame(restoredShellB, readObjectField(restoredParentB, "shellChild"),
                "parent B shell slot must resolve to restored shell B");
        assertSame(restoredParentB, readObjectField(restoredOverlayB, "parent"),
                "waterfall overlay must resolve to restored hidden Turbo Spiker");
        assertTrue(!(Boolean) readObjectField(restoredShellA, "attached"),
                "launched shell state must restore as detached");
        assertEquals(7, readIntField(restoredTrailA, "mappingFrame"),
                "trail emitter animation state must restore from compact state");
    }

    @Test
    void cluckoidArrowRelinksToRestoredParentByLayoutSlot() {
        Harness harness = Harness.create(new MhzTestRegistry(), List.of(
                new ObjectSpawn(0x140, 0x120, Sonic3kObjectIds.CLUCKOID, 0, 0, false, 0, 60),
                new ObjectSpawn(0x260, 0x120, Sonic3kObjectIds.CLUCKOID, 0, 0, false, 0, 61)));
        ObjectManager objectManager = harness.objectManager();
        TestablePlayableSprite player = player();
        List<CluckoidBadnikInstance> sourceParents = liveByType(objectManager, CluckoidBadnikInstance.class);
        assertEquals(2, sourceParents.size(), "precondition: two Cluckoid parents must be captured");
        CluckoidBadnikInstance sourceParentA = sourceParents.get(0);
        CluckoidBadnikInstance sourceParentB = sourceParents.get(1);
        sourceParentA.update(0, player);
        sourceParentA.update(1, player);
        sourceParentB.update(0, player);
        sourceParentB.update(1, player);
        List<ObjectInstance> sourceArrows = liveByClassName(objectManager, CLUCKOID_ARROW_CHILD);
        assertEquals(2, sourceArrows.size(), "precondition: each Cluckoid must create one arrow child");
        ObjectInstance sourceArrowA = childWithParent(sourceArrows, sourceParentA);
        ObjectInstance sourceArrowB = childWithParent(sourceArrows, sourceParentB);

        ObjectRefId parentAId = objectId(objectManager, sourceParentA);
        ObjectRefId parentBId = objectId(objectManager, sourceParentB);
        ObjectRefId arrowAId = objectId(objectManager, sourceArrowA);
        ObjectRefId arrowBId = objectId(objectManager, sourceArrowB);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.createDynamicObject(() -> new CluckoidBadnikInstance(new ObjectSpawn(
                0x340, 0x180, Sonic3kObjectIds.CLUCKOID, 0, 0, false, 62)));

        rewindRegistry.restore(snapshot);

        CluckoidBadnikInstance restoredParentA =
                objectById(objectManager, CluckoidBadnikInstance.class, parentAId);
        CluckoidBadnikInstance restoredParentB =
                objectById(objectManager, CluckoidBadnikInstance.class, parentBId);
        ObjectInstance restoredArrowA = objectById(objectManager, ObjectInstance.class, arrowAId);
        ObjectInstance restoredArrowB = objectById(objectManager, ObjectInstance.class, arrowBId);
        assertNotSame(sourceArrowA, restoredArrowA, "restore must recreate Cluckoid arrow A");
        assertNotSame(sourceArrowB, restoredArrowB, "restore must recreate Cluckoid arrow B");
        assertSame(restoredParentA, readObjectField(restoredArrowA, "parent"),
                "arrow A parent must resolve to restored layout-slot A Cluckoid");
        assertSame(restoredParentB, readObjectField(restoredArrowB, "parent"),
                "arrow B parent must resolve to restored layout-slot B Cluckoid");
        assertEquals(2, liveByClassName(objectManager, CLUCKOID_ARROW_CHILD).size(),
                "restore must keep exactly the captured Cluckoid arrows");
    }

    @Test
    void mushmeanieShellRelinksToRestoredParentAndParentSlot() {
        Harness harness = Harness.create(new MhzTestRegistry(), List.of(
                new ObjectSpawn(0x180, 0x130, Sonic3kObjectIds.MUSHMEANIE, 0, 0, false, 0, 70),
                new ObjectSpawn(0x2A0, 0x130, Sonic3kObjectIds.MUSHMEANIE, 0, 0, false, 0, 71)));
        ObjectManager objectManager = harness.objectManager();
        TestablePlayableSprite player = player();
        List<MushmeanieBadnikInstance> sourceParents = liveByType(objectManager, MushmeanieBadnikInstance.class);
        assertEquals(2, sourceParents.size(), "precondition: two Mushmeanie parents must be captured");
        MushmeanieBadnikInstance sourceParentA = sourceParents.get(0);
        MushmeanieBadnikInstance sourceParentB = sourceParents.get(1);
        sourceParentA.update(0, player);
        sourceParentA.update(1, player);
        sourceParentB.update(0, player);
        sourceParentB.update(1, player);
        List<ObjectInstance> sourceShells = liveByClassName(objectManager, MUSHMEANIE_SHELL_CHILD);
        assertEquals(2, sourceShells.size(), "precondition: each Mushmeanie must create one shell child");
        ObjectInstance sourceShellA = childWithParent(sourceShells, sourceParentA);
        ObjectInstance sourceShellB = childWithParent(sourceShells, sourceParentB);
        setBooleanField(sourceShellA, "launched", true);
        setIntField(sourceShellA, "x", 0x1C0);
        setIntField(sourceShellA, "y", 0x118);
        setIntField(sourceShellA, "xVelocity", 0x33);
        setIntField(sourceShellA, "yVelocity", -0x44);
        setBooleanField(sourceShellB, "launched", true);
        setIntField(sourceShellB, "x", 0x2D0);
        setIntField(sourceShellB, "y", 0x150);
        setIntField(sourceShellB, "xVelocity", -0x55);
        setIntField(sourceShellB, "yVelocity", -0x66);

        ObjectRefId parentAId = objectId(objectManager, sourceParentA);
        ObjectRefId parentBId = objectId(objectManager, sourceParentB);
        ObjectRefId shellAId = objectId(objectManager, sourceShellA);
        ObjectRefId shellBId = objectId(objectManager, sourceShellB);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.createDynamicObject(() -> new MushmeanieBadnikInstance(new ObjectSpawn(
                0x220, 0x130, Sonic3kObjectIds.MUSHMEANIE, 0, 0, false, 72)));

        rewindRegistry.restore(snapshot);

        MushmeanieBadnikInstance restoredParentA =
                objectById(objectManager, MushmeanieBadnikInstance.class, parentAId);
        MushmeanieBadnikInstance restoredParentB =
                objectById(objectManager, MushmeanieBadnikInstance.class, parentBId);
        ObjectInstance restoredShellA = objectById(objectManager, ObjectInstance.class, shellAId);
        ObjectInstance restoredShellB = objectById(objectManager, ObjectInstance.class, shellBId);
        assertNotSame(sourceShellA, restoredShellA, "restore must recreate Mushmeanie shell A");
        assertNotSame(sourceShellB, restoredShellB, "restore must recreate Mushmeanie shell B");
        assertSame(restoredParentA, readObjectField(restoredShellA, "parent"),
                "shell A parent must resolve to restored Mushmeanie A");
        assertSame(restoredParentB, readObjectField(restoredShellB, "parent"),
                "shell B parent must resolve to restored Mushmeanie B");
        assertSame(restoredShellA, readObjectField(restoredParentA, "shellChild"),
                "Mushmeanie A shell slot must resolve to restored shell A");
        assertSame(restoredShellB, readObjectField(restoredParentB, "shellChild"),
                "Mushmeanie B shell slot must resolve to restored shell B");
        assertTrue((Boolean) readObjectField(restoredShellA, "launched"));
        assertTrue((Boolean) readObjectField(restoredShellB, "launched"));
        assertEquals(0x1C0, readIntField(restoredShellA, "x"));
        assertEquals(0x118, readIntField(restoredShellA, "y"));
        assertEquals(0x33, readIntField(restoredShellA, "xVelocity"));
        assertEquals(-0x44, readIntField(restoredShellA, "yVelocity"));
        assertEquals(0x2D0, readIntField(restoredShellB, "x"));
        assertEquals(0x150, readIntField(restoredShellB, "y"));
        assertEquals(-0x55, readIntField(restoredShellB, "xVelocity"));
        assertEquals(-0x66, readIntField(restoredShellB, "yVelocity"));
        assertEquals(2, liveByClassName(objectManager, MUSHMEANIE_SHELL_CHILD).size(),
                "restore must keep exactly the captured Mushmeanie shells");
    }

    @Test
    void mushmeanieShellParentFailsForObjectWithoutRegisteredRewindIdentity() {
        Harness harness = Harness.create(new MhzTestRegistry(), List.of());
        ObjectManager objectManager = harness.objectManager();
        MushmeanieBadnikInstance externalParent = new MushmeanieBadnikInstance(new ObjectSpawn(
                0x180, 0x130, Sonic3kObjectIds.MUSHMEANIE, 0, 0, false, 73));
        ObjectInstance shell = objectManager.createDynamicObject(
                () -> instantiateMushmeanieShellChild(externalParent));
        assertSame(externalParent, readObjectField(shell, "parent"),
                "precondition: shell holds a non-null parent outside ObjectManager identity registration");

        RewindRegistry rewindRegistry = registryFor(objectManager);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, rewindRegistry::capture);
        assertTrue(thrown.getMessage().contains("no registered id for object reference"),
                "missing required Mushmeanie parent targets must fail loudly");
    }

    @Test
    void mantisChildRelinksToRestoredParentAndParentSlot() {
        Harness harness = Harness.create(new S3klTestRegistry(), List.of(
                new ObjectSpawn(0x180, 0x130, Sonic3kObjectIds.MANTIS, 0, 0, false, 0, 80),
                new ObjectSpawn(0x2A0, 0x130, Sonic3kObjectIds.MANTIS, 0, 0, false, 0, 81)));
        ObjectManager objectManager = harness.objectManager();
        TestablePlayableSprite player = player();
        List<MantisBadnikInstance> sourceParents = liveByType(objectManager, MantisBadnikInstance.class);
        assertEquals(2, sourceParents.size(), "precondition: two Mantis parents must be captured");
        MantisBadnikInstance sourceParentA = sourceParents.get(0);
        MantisBadnikInstance sourceParentB = sourceParents.get(1);
        sourceParentA.update(0, player);
        sourceParentB.update(0, player);
        List<ObjectInstance> sourceChildren = liveByClassName(objectManager, MANTIS_CHILD);
        assertEquals(2, sourceChildren.size(), "precondition: each Mantis must create one visual child");
        ObjectInstance sourceChildA = childWithParent(sourceChildren, sourceParentA);
        ObjectInstance sourceChildB = childWithParent(sourceChildren, sourceParentB);
        setIntField(sourceChildA, "currentX", 0x19A);
        setIntField(sourceChildA, "currentY", 0x120);
        setIntField(sourceChildA, "mappingFrame", 5);
        setIntField(sourceChildB, "currentX", 0x2B2);
        setIntField(sourceChildB, "currentY", 0x126);
        setIntField(sourceChildB, "mappingFrame", 4);

        ObjectRefId parentAId = objectId(objectManager, sourceParentA);
        ObjectRefId parentBId = objectId(objectManager, sourceParentB);
        ObjectRefId childAId = objectId(objectManager, sourceChildA);
        ObjectRefId childBId = objectId(objectManager, sourceChildB);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.createDynamicObject(() -> new MantisBadnikInstance(new ObjectSpawn(
                0x220, 0x130, Sonic3kObjectIds.MANTIS, 0, 0, false, 82)));

        rewindRegistry.restore(snapshot);

        MantisBadnikInstance restoredParentA =
                objectById(objectManager, MantisBadnikInstance.class, parentAId);
        MantisBadnikInstance restoredParentB =
                objectById(objectManager, MantisBadnikInstance.class, parentBId);
        ObjectInstance restoredChildA = objectById(objectManager, ObjectInstance.class, childAId);
        ObjectInstance restoredChildB = objectById(objectManager, ObjectInstance.class, childBId);
        assertNotSame(sourceChildA, restoredChildA, "restore must recreate Mantis child A");
        assertNotSame(sourceChildB, restoredChildB, "restore must recreate Mantis child B");
        assertSame(restoredParentA, readObjectField(restoredChildA, "parent"),
                "child A parent must resolve to restored Mantis A");
        assertSame(restoredParentB, readObjectField(restoredChildB, "parent"),
                "child B parent must resolve to restored Mantis B");
        assertSame(restoredChildA, readObjectField(restoredParentA, "child"),
                "Mantis A child slot must resolve to restored child A");
        assertSame(restoredChildB, readObjectField(restoredParentB, "child"),
                "Mantis B child slot must resolve to restored child B");
        assertEquals(0x19A, readIntField(restoredChildA, "currentX"));
        assertEquals(0x120, readIntField(restoredChildA, "currentY"));
        assertEquals(5, readIntField(restoredChildA, "mappingFrame"));
        assertEquals(0x2B2, readIntField(restoredChildB, "currentX"));
        assertEquals(0x126, readIntField(restoredChildB, "currentY"));
        assertEquals(4, readIntField(restoredChildB, "mappingFrame"));
        assertEquals(2, liveByClassName(objectManager, MANTIS_CHILD).size(),
                "restore must keep exactly the captured Mantis children");
    }

    @Test
    void mantisChildParentFailsForObjectWithoutRegisteredRewindIdentity() {
        Harness harness = Harness.create(new MhzTestRegistry(), List.of());
        ObjectManager objectManager = harness.objectManager();
        MantisBadnikInstance externalParent = new MantisBadnikInstance(new ObjectSpawn(
                0x180, 0x130, Sonic3kObjectIds.MANTIS, 0, 0, false, 83));
        ObjectInstance child = objectManager.createDynamicObject(
                () -> instantiateMantisChild(externalParent));
        assertSame(externalParent, readObjectField(child, "parent"),
                "precondition: child holds a non-null parent outside ObjectManager identity registration");

        RewindRegistry rewindRegistry = registryFor(objectManager);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, rewindRegistry::capture);
        assertTrue(thrown.getMessage().contains("no registered id for object reference"),
                "missing required Mantis parent targets must fail loudly");
    }

    @Test
    void ribotVisualChildrenRelinkToRestoredActiveChildren() {
        Harness harness = Harness.create(new S3klTestRegistry(), List.of(
                new ObjectSpawn(0x180, 0x130, Sonic3kObjectIds.RIBOT, 4, 0, false, 90),
                new ObjectSpawn(0x2A0, 0x130, Sonic3kObjectIds.RIBOT, 4, 0, false, 91)));
        ObjectManager objectManager = harness.objectManager();
        TestablePlayableSprite player = player();
        List<RibotBadnikInstance> sourceParents = liveByType(objectManager, RibotBadnikInstance.class);
        assertEquals(2, sourceParents.size(), "precondition: two Ribot parents must be captured");
        RibotBadnikInstance sourceParentA = sourceParents.get(0);
        RibotBadnikInstance sourceParentB = sourceParents.get(1);
        sourceParentA.update(0, player);
        sourceParentB.update(0, player);
        List<ObjectInstance> sourceActiveChildren = liveByClassName(objectManager, RIBOT_ACTIVE_CHILD);
        assertEquals(2, sourceActiveChildren.size(), "precondition: each Ribot must create one active child");
        ObjectInstance sourceActiveA = childWithParent(sourceActiveChildren, sourceParentA);
        ObjectInstance sourceActiveB = childWithParent(sourceActiveChildren, sourceParentB);
        sourceActiveA.update(1, player);
        sourceActiveB.update(1, player);
        List<ObjectInstance> sourceVisuals = liveByClassName(objectManager, RIBOT_VISUAL_CHILD);
        assertEquals(6, sourceVisuals.size(), "precondition: each active child must create three visuals");
        ObjectInstance sourceVisualA0 = visualChildWithParentAndIndex(sourceVisuals, sourceActiveA, 0);
        ObjectInstance sourceVisualA2 = visualChildWithParentAndIndex(sourceVisuals, sourceActiveA, 2);
        ObjectInstance sourceVisualB1 = visualChildWithParentAndIndex(sourceVisuals, sourceActiveB, 1);
        setIntField(sourceVisualA0, "currentX", 0x190);
        setIntField(sourceVisualA0, "currentY", 0x118);
        setIntField(sourceVisualA0, "originX", 0x18C);
        setIntField(sourceVisualA2, "currentX", 0x1A4);
        setIntField(sourceVisualA2, "currentY", 0x120);
        setIntField(sourceVisualB1, "currentX", 0x2AC);
        setIntField(sourceVisualB1, "currentY", 0x12C);

        ObjectRefId parentAId = objectId(objectManager, sourceParentA);
        ObjectRefId parentBId = objectId(objectManager, sourceParentB);
        ObjectRefId activeAId = objectId(objectManager, sourceActiveA);
        ObjectRefId activeBId = objectId(objectManager, sourceActiveB);
        ObjectRefId visualA0Id = objectId(objectManager, sourceVisualA0);
        ObjectRefId visualA2Id = objectId(objectManager, sourceVisualA2);
        ObjectRefId visualB1Id = objectId(objectManager, sourceVisualB1);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.createDynamicObject(() -> new RibotBadnikInstance(new ObjectSpawn(
                0x220, 0x130, Sonic3kObjectIds.RIBOT, 4, 0, false, 92)));

        rewindRegistry.restore(snapshot);

        RibotBadnikInstance restoredParentA =
                objectById(objectManager, RibotBadnikInstance.class, parentAId);
        RibotBadnikInstance restoredParentB =
                objectById(objectManager, RibotBadnikInstance.class, parentBId);
        ObjectInstance restoredActiveA = objectById(objectManager, ObjectInstance.class, activeAId);
        ObjectInstance restoredActiveB = objectById(objectManager, ObjectInstance.class, activeBId);
        ObjectInstance restoredVisualA0 = objectById(objectManager, ObjectInstance.class, visualA0Id);
        ObjectInstance restoredVisualA2 = objectById(objectManager, ObjectInstance.class, visualA2Id);
        ObjectInstance restoredVisualB1 = objectById(objectManager, ObjectInstance.class, visualB1Id);

        assertNotSame(sourceVisualA0, restoredVisualA0, "restore must recreate Ribot visual A0");
        assertNotSame(sourceVisualA2, restoredVisualA2, "restore must recreate Ribot visual A2");
        assertNotSame(sourceVisualB1, restoredVisualB1, "restore must recreate Ribot visual B1");
        assertSame(restoredParentA, readObjectField(restoredActiveA, "parent"),
                "active child A parent must resolve to restored Ribot A");
        assertSame(restoredParentB, readObjectField(restoredActiveB, "parent"),
                "active child B parent must resolve to restored Ribot B");
        assertSame(restoredActiveA, readObjectField(restoredVisualA0, "parent"),
                "visual A0 parent must resolve to restored active child A");
        assertSame(restoredActiveA, readObjectField(restoredVisualA2, "parent"),
                "visual A2 parent must resolve to restored active child A");
        assertSame(restoredActiveB, readObjectField(restoredVisualB1, "parent"),
                "visual B1 parent must resolve to restored active child B");
        assertEquals(0x190, readIntField(restoredVisualA0, "currentX"));
        assertEquals(0x118, readIntField(restoredVisualA0, "currentY"));
        assertEquals(0x18C, readIntField(restoredVisualA0, "originX"));
        assertEquals(0x1A4, readIntField(restoredVisualA2, "currentX"));
        assertEquals(0x120, readIntField(restoredVisualA2, "currentY"));
        assertEquals(0x2AC, readIntField(restoredVisualB1, "currentX"));
        assertEquals(0x12C, readIntField(restoredVisualB1, "currentY"));
        assertEquals(6, liveByClassName(objectManager, RIBOT_VISUAL_CHILD).size(),
                "restore must keep exactly the captured Ribot visuals");
    }

    @Test
    void snaleBlasterChildrenRelinkToRestoredParentAndParentSlots() {
        Harness harness = Harness.create(new S3klTestRegistry(), List.of(
                new ObjectSpawn(0x180, 0x130, Sonic3kObjectIds.SNALE_BLASTER, 0, 0, false, 100),
                new ObjectSpawn(0x2A0, 0x130, Sonic3kObjectIds.SNALE_BLASTER, 1, 0, false, 101)));
        ObjectManager objectManager = harness.objectManager();
        TestablePlayableSprite player = player();
        List<SnaleBlasterBadnikInstance> sourceParents =
                liveByType(objectManager, SnaleBlasterBadnikInstance.class);
        assertEquals(2, sourceParents.size(), "precondition: two SnaleBlaster parents must be captured");
        SnaleBlasterBadnikInstance sourceParentA = sourceParents.get(0);
        SnaleBlasterBadnikInstance sourceParentB = sourceParents.get(1);
        sourceParentA.update(0, player);
        sourceParentB.update(0, player);
        List<ObjectInstance> sourceShooters = liveByClassName(objectManager, SNALE_BLASTER_SHOOTER_CHILD);
        assertEquals(4, sourceShooters.size(), "precondition: each SnaleBlaster must create two shooters");
        List<ObjectInstance> sourceCovers = liveByClassName(objectManager, SNALE_BLASTER_COVER_CHILD);
        assertEquals(2, sourceCovers.size(), "precondition: each SnaleBlaster must create one cover");
        ObjectInstance sourceShooterA0 = childWithParentAndYOffset(sourceShooters, sourceParentA, 0);
        ObjectInstance sourceShooterB7 = childWithParentAndYOffset(sourceShooters, sourceParentB, 7);
        ObjectInstance sourceCoverA = childWithParent(sourceCovers, sourceParentA);
        ObjectInstance sourceCoverB = childWithParent(sourceCovers, sourceParentB);
        setIntField(sourceShooterA0, "currentX", 0x191);
        setIntField(sourceShooterA0, "currentY", 0x121);
        setIntField(sourceShooterA0, "mappingFrame", 8);
        setIntField(sourceShooterA0, "animIndex", 2);
        setIntField(sourceShooterA0, "animTimer", 3);
        setBooleanField(sourceShooterA0, "shotFired", true);
        setIntField(sourceShooterB7, "currentX", 0x2A8);
        setIntField(sourceShooterB7, "currentY", 0x13A);
        setIntField(sourceShooterB7, "mappingFrame", 7);
        setIntField(sourceCoverA, "currentX", 0x188);
        setIntField(sourceCoverA, "currentY", 0x134);
        setIntField(sourceCoverA, "mappingFrame", 10);
        setIntField(sourceCoverA, "animIndex", 2);
        setIntField(sourceCoverA, "animTimer", 11);

        ObjectRefId parentAId = objectId(objectManager, sourceParentA);
        ObjectRefId parentBId = objectId(objectManager, sourceParentB);
        ObjectRefId shooterA0Id = objectId(objectManager, sourceShooterA0);
        ObjectRefId shooterB7Id = objectId(objectManager, sourceShooterB7);
        ObjectRefId coverAId = objectId(objectManager, sourceCoverA);
        ObjectRefId coverBId = objectId(objectManager, sourceCoverB);
        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.createDynamicObject(() -> new SnaleBlasterBadnikInstance(new ObjectSpawn(
                0x220, 0x130, Sonic3kObjectIds.SNALE_BLASTER, 0, 0, false, 102)));

        rewindRegistry.restore(snapshot);

        SnaleBlasterBadnikInstance restoredParentA =
                objectById(objectManager, SnaleBlasterBadnikInstance.class, parentAId);
        SnaleBlasterBadnikInstance restoredParentB =
                objectById(objectManager, SnaleBlasterBadnikInstance.class, parentBId);
        ObjectInstance restoredShooterA0 = objectById(objectManager, ObjectInstance.class, shooterA0Id);
        ObjectInstance restoredShooterB7 = objectById(objectManager, ObjectInstance.class, shooterB7Id);
        ObjectInstance restoredCoverA = objectById(objectManager, ObjectInstance.class, coverAId);
        ObjectInstance restoredCoverB = objectById(objectManager, ObjectInstance.class, coverBId);

        assertNotSame(sourceShooterA0, restoredShooterA0, "restore must recreate Snale shooter A0");
        assertNotSame(sourceShooterB7, restoredShooterB7, "restore must recreate Snale shooter B7");
        assertNotSame(sourceCoverA, restoredCoverA, "restore must recreate Snale cover A");
        assertNotSame(sourceCoverB, restoredCoverB, "restore must recreate Snale cover B");
        assertSame(restoredParentA, readObjectField(restoredShooterA0, "parent"),
                "shooter A0 parent must resolve to restored SnaleBlaster A");
        assertSame(restoredParentB, readObjectField(restoredShooterB7, "parent"),
                "shooter B7 parent must resolve to restored SnaleBlaster B");
        assertSame(restoredParentA, readObjectField(restoredCoverA, "parent"),
                "cover A parent must resolve to restored SnaleBlaster A");
        assertSame(restoredParentB, readObjectField(restoredCoverB, "parent"),
                "cover B parent must resolve to restored SnaleBlaster B");
        assertSame(restoredCoverA, readObjectField(restoredParentA, "cover"),
                "SnaleBlaster A cover slot must resolve to restored cover A");
        assertSame(restoredCoverB, readObjectField(restoredParentB, "cover"),
                "SnaleBlaster B cover slot must resolve to restored cover B");
        assertTrue(((List<?>) readObjectField(restoredParentA, "shooters")).contains(restoredShooterA0),
                "SnaleBlaster A shooter list must contain restored shooter A0");
        assertTrue(((List<?>) readObjectField(restoredParentB, "shooters")).contains(restoredShooterB7),
                "SnaleBlaster B shooter list must contain restored shooter B7");
        assertEquals(0x191, readIntField(restoredShooterA0, "currentX"));
        assertEquals(0x121, readIntField(restoredShooterA0, "currentY"));
        assertEquals(8, readIntField(restoredShooterA0, "mappingFrame"));
        assertEquals(2, readIntField(restoredShooterA0, "animIndex"));
        assertEquals(3, readIntField(restoredShooterA0, "animTimer"));
        assertTrue((Boolean) readObjectField(restoredShooterA0, "shotFired"));
        assertEquals(0x2A8, readIntField(restoredShooterB7, "currentX"));
        assertEquals(0x13A, readIntField(restoredShooterB7, "currentY"));
        assertEquals(7, readIntField(restoredShooterB7, "mappingFrame"));
        assertEquals(0x188, readIntField(restoredCoverA, "currentX"));
        assertEquals(0x134, readIntField(restoredCoverA, "currentY"));
        assertEquals(10, readIntField(restoredCoverA, "mappingFrame"));
        assertEquals(2, readIntField(restoredCoverA, "animIndex"));
        assertEquals(11, readIntField(restoredCoverA, "animTimer"));
        assertEquals(4, liveByClassName(objectManager, SNALE_BLASTER_SHOOTER_CHILD).size(),
                "restore must keep exactly the captured SnaleBlaster shooters");
        assertEquals(2, liveByClassName(objectManager, SNALE_BLASTER_COVER_CHILD).size(),
                "restore must keep exactly the captured SnaleBlaster covers");
    }

    @Test
    void captureFailsForNonNullObjectReferenceWithoutRegisteredRewindIdentity() {
        Harness harness = Harness.create(new S3klTestRegistry(), List.of());
        ObjectManager objectManager = harness.objectManager();
        SpikerBadnikInstance externalParent = new SpikerBadnikInstance(new ObjectSpawn(
                0x120, 0x100, Sonic3kObjectIds.SPIKER, 0, 0, false, 40));
        ObjectInstance child = objectManager.createDynamicObject(
                () -> instantiateSpikerTopSpikeChild(externalParent));
        assertSame(externalParent, readObjectField(child, "parent"),
                "precondition: child holds a non-null parent outside ObjectManager identity registration");

        RewindRegistry rewindRegistry = registryFor(objectManager);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, rewindRegistry::capture);
        assertTrue(thrown.getMessage().contains("no registered id for object reference"),
                "missing required object targets must fail loudly");
    }

    @Test
    void dragonflyFollowAnchorFailsForObjectWithoutRegisteredRewindIdentity() {
        Harness harness = Harness.create(new MhzTestRegistry(), List.of(
                new ObjectSpawn(0x120, 0x100, Sonic3kObjectIds.DRAGONFLY, 0, 0, false, 50)));
        ObjectManager objectManager = harness.objectManager();
        DragonflyBadnikInstance sourceParent = liveByType(objectManager, DragonflyBadnikInstance.class).getFirst();
        sourceParent.update(0, player());
        sourceParent.update(1, player());
        ObjectInstance sourceSegment = segmentByParentAndIndex(
                liveByClassName(objectManager, DRAGONFLY_LINKED_BODY_CHILD),
                sourceParent,
                1);
        DragonflyBadnikInstance externalAnchor = new DragonflyBadnikInstance(new ObjectSpawn(
                0x180, 0x110, Sonic3kObjectIds.DRAGONFLY, 0, 0, false, 51));
        setObjectField(sourceSegment, "followAnchor", externalAnchor);
        assertSame(externalAnchor, readObjectField(sourceSegment, "followAnchor"),
                "precondition: linked body must hold a follow anchor outside ObjectManager identity registration");

        RewindRegistry rewindRegistry = registryFor(objectManager);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, rewindRegistry::capture);
        assertTrue(thrown.getMessage().contains("no registered id for object reference"),
                "missing required Dragonfly follow-anchor targets must fail loudly");
    }

    private static ObjectInstance instantiateSpikerTopSpikeChild(SpikerBadnikInstance parent) {
        try {
            Class<?> cls = Class.forName(SPIKER_TOP_SPIKE_CHILD);
            Constructor<?> ctor = cls.getDeclaredConstructor(SpikerBadnikInstance.class);
            ctor.setAccessible(true);
            return (ObjectInstance) ctor.newInstance(parent);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to construct Spiker top spike child", e);
        }
    }

    private static ObjectInstance instantiateTurboSpikerTrailEmitter(ObjectInstance shell) {
        try {
            Class<?> cls = Class.forName(TURBO_SPIKER_TRAIL_EMITTER);
            Constructor<?> ctor = cls.getDeclaredConstructor(Class.forName(TURBO_SPIKER_SHELL_CHILD));
            ctor.setAccessible(true);
            return (ObjectInstance) ctor.newInstance(shell);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to construct Turbo Spiker trail emitter", e);
        }
    }

    private static ObjectInstance instantiateMushmeanieShellChild(MushmeanieBadnikInstance parent) {
        try {
            Class<?> cls = Class.forName(MUSHMEANIE_SHELL_CHILD);
            Constructor<?> ctor = cls.getDeclaredConstructor(MushmeanieBadnikInstance.class);
            ctor.setAccessible(true);
            return (ObjectInstance) ctor.newInstance(parent);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to construct Mushmeanie shell child", e);
        }
    }

    private static ObjectInstance instantiateMantisChild(MantisBadnikInstance parent) {
        try {
            Class<?> cls = Class.forName(MANTIS_CHILD);
            Constructor<?> ctor = cls.getDeclaredConstructor(MantisBadnikInstance.class);
            ctor.setAccessible(true);
            return (ObjectInstance) ctor.newInstance(parent);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to construct Mantis child", e);
        }
    }

    private static ObjectInstance segmentByParentAndIndex(List<ObjectInstance> segments,
                                                          DragonflyBadnikInstance parent,
                                                          int segmentIndex) {
        return segments.stream()
                .filter(segment -> readObjectField(segment, "parent") == parent)
                .filter(segment -> readIntField(segment, "segmentIndex") == segmentIndex)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing Dragonfly segment " + segmentIndex));
    }

    private static ObjectInstance childWithParent(List<ObjectInstance> children, ObjectInstance parent) {
        return children.stream()
                .filter(child -> readObjectField(child, "parent") == parent)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing child for " + parent.getClass()));
    }

    private static ObjectInstance childWithParentAndSide(
            List<ObjectInstance> children, ObjectInstance parent, boolean leftSide) {
        return children.stream()
                .filter(child -> readObjectField(child, "parent") == parent)
                .filter(child -> ((Boolean) readObjectField(child, "leftSide")) == leftSide)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing "
                        + (leftSide ? "left" : "right") + " child for " + parent.getClass()));
    }

    private static ObjectInstance childWithParentAndYOffset(
            List<ObjectInstance> children, ObjectInstance parent, int yOffset) {
        return children.stream()
                .filter(child -> readObjectField(child, "parent") == parent)
                .filter(child -> readIntField(child, "yOffset") == yOffset)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing yOffset "
                        + yOffset + " child for " + parent.getClass()));
    }

    private static ObjectInstance visualChildWithParentAndIndex(
            List<ObjectInstance> children, ObjectInstance parent, int visualIndex) {
        return children.stream()
                .filter(child -> readObjectField(child, "parent") == parent)
                .filter(child -> readIntField(child, "visualIndex") == visualIndex)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing visual "
                        + visualIndex + " child for " + parent.getClass()));
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        return rewindRegistry;
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        RewindIdentityTable table = objectManager.captureIdentityContext().requireIdentityTable();
        ObjectRefId id = table.idFor(object);
        assertNotNull(id, "ObjectManager capture identity table must register " + object.getClass());
        return id;
    }

    private static <T extends ObjectInstance> T objectById(ObjectManager objectManager, Class<T> type, ObjectRefId id) {
        return liveByType(objectManager, type).stream()
                .filter(object -> objectId(objectManager, object).equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing restored object " + id));
    }

    private static <T extends ObjectInstance> List<T> liveByType(ObjectManager objectManager, Class<T> type) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> type.isAssignableFrom(object.getClass()) && !object.isDestroyed())
                .map(type::cast)
                .sorted(Comparator.comparingInt(TestS3kBadnikChildGraphRewind::slotIndex))
                .toList();
    }

    private static List<ObjectInstance> liveByClassName(ObjectManager objectManager, String className) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass().getName().equals(className) && !object.isDestroyed())
                .sorted(Comparator.comparingInt(TestS3kBadnikChildGraphRewind::slotIndex))
                .toList();
    }

    private static int slotIndex(ObjectInstance object) {
        return object instanceof AbstractObjectInstance aoi ? aoi.getSlotIndex() : -1;
    }

    private static int readIntField(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static Object readObjectField(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static void setIntField(Object target, String fieldName, int value) {
        try {
            findField(target.getClass(), fieldName).setInt(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static void setBooleanField(Object target, String fieldName, boolean value) {
        try {
            findField(target.getClass(), fieldName).setBoolean(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static void setObjectField(Object target, String fieldName, Object value) {
        try {
            findField(target.getClass(), fieldName).set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static TestablePlayableSprite player() {
        return new TestablePlayableSprite("sonic", (short) 0x180, (short) 0x120);
    }

    private record Harness(ObjectManager objectManager) {
        static Harness create(ObjectRegistry registry, List<ObjectSpawn> spawns) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
            TestablePlayableSprite player = player();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public ObjectPlayerQuery playerQuery() {
                    return new ObjectPlayerQuery(() -> player, List::of);
                }
            };
            ObjectManager objectManager = new ObjectManager(
                    spawns,
                    registry,
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            holder[0] = objectManager;
            objectManager.reset(0);
            return new Harness(objectManager);
        }
    }

    private static final class S3klTestRegistry extends Sonic3kObjectRegistry {
        @Override
        protected int currentRomZoneId() {
            return Sonic3kZoneIds.ZONE_HCZ;
        }
    }

    private static final class MhzTestRegistry extends Sonic3kObjectRegistry {
        @Override
        protected int currentRomZoneId() {
            return Sonic3kZoneIds.ZONE_MHZ;
        }
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 0x400; }
            @Override public short getHeight() { return 0x300; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}

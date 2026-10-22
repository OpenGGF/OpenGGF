package com.openggf.game.sonic1.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.NativeSpriteSubmissionAccess.Admission;
import com.openggf.level.objects.NativeSpriteSubmissionSource;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.SubpixelMotion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Producer-side DisplaySprite contract, using the real ObjectManager walk.
 * Bom_Main (_incObj/5F Badnik - Walking Bomb.asm:22-33) makes zero calls for
 * fresh fuse/shrapnel SSTs. Bom_Fuse (:153-158) makes one in-range call, while
 * FixBugs=0 expiry (:171-220) makes two for its reused SST, or one before the
 * RememberState deletion. A cleared obFrame is not permission to draw it.
 * DisplaySprite (_incObj/sub DisplaySprite.asm:6-19) admits 63 FIFO slot words
 * per native bucket; it neither deduplicates nor removes an orphan address.
 * The in-range body at slot32 submits first, followed by explicit Fuse/Shrapnel
 * calls and the tail capacity observer in the same native SST walk.
 */
class TestSonic1BombNativeSpriteSubmission {
    private static final int BUCKET = 3;
    private static final int FUSE_SLOT = 40;
    private static final int TAIL_SLOT = 127;

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void resetGraphics() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void freshFuseMainMakesNoCallBeforeItsFirstBurnPass() {
        ObjectManager manager = newManager(0);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 160);
        player.setCentreY((short) 96);

        manager.update(0, player, List.of(), 1);

        Sonic1BombFuseInstance fuse = manager.activeObjectsOfType(Sonic1BombFuseInstance.class).getFirst();
        assertEquals(33, fuse.getSlotIndex());
        assertTrue(fuse instanceof NativeSpriteSubmissionSource);
        assertEquals(List.of(), callsForSlot(manager, fuse.getSlotIndex()),
                "fresh Bom_Main returns without DisplaySprite or RememberState");
        assertEquals(62, tail(manager).accepted,
                "the body submits once; the fresh fuse makes no call");
        assertEquals(expectedQueue(62, 32), NativeSpriteSubmissionAccess.admissions(manager));

        manager.update(0, player, List.of(), 2);

        assertEquals(List.of(new Admission(33, BUCKET)), callsForSlot(manager, 33));
        assertEquals(61, tail(manager).accepted, "one body and one ordinary fuse submission");
        assertEquals(expectedQueue(61, 32, 33), NativeSpriteSubmissionAccess.admissions(manager));
    }

    @Test
    void expiryQueuesTheReusedSlotTwiceAndNoFreshShrapnelSlots() {
        ObjectManager manager = withFuse(0, 160, false, 0);

        manager.update(0, null, List.of(), 1);

        assertEquals(List.of(new Admission(FUSE_SLOT, BUCKET), new Admission(FUSE_SLOT, BUCKET)),
                callsForSlot(manager, FUSE_SLOT));
        List<Sonic1BombShrapnelInstance> fragments = fragments(manager);
        assertEquals(List.of(40, 41, 42, 43), fragments.stream()
                .map(AbstractObjectInstance::getSlotIndex).toList());
        for (Sonic1BombShrapnelInstance child : fragments.subList(1, fragments.size())) {
            assertTrue(child instanceof NativeSpriteSubmissionSource);
            assertEquals(List.of(), callsForSlot(manager, child.getSlotIndex()),
                    "fresh Bom_Main does not submit even though its stored frame is zero");
        }
        assertEquals(60, tail(manager).accepted);
        assertEquals(expectedQueue(60, 32, FUSE_SLOT, FUSE_SLOT),
                NativeSpriteSubmissionAccess.admissions(manager));

        manager.update(0, null, List.of(), 2);

        for (Sonic1BombShrapnelInstance fragment : fragments(manager)) {
            assertEquals(List.of(new Admission(fragment.getSlotIndex(), BUCKET)),
                    callsForSlot(manager, fragment.getSlotIndex()),
                    "a regular Bom_Shrapnel pass submits exactly once");
        }
        assertEquals(58, tail(manager).accepted, "one body plus four regular shrapnel calls");
        assertEquals(expectedQueue(58, 32, 40, 41, 42, 43),
                NativeSpriteSubmissionAccess.admissions(manager));
    }

    @ParameterizedTest
    @ValueSource(ints = {160, 0x400})
    void ordinaryFuseRememberStateSubmitsOnlyOnItsInRangeBranch(int fuseX) {
        ObjectManager manager = withFuse(1, fuseX, false, 0);

        manager.update(0, null, List.of(), 1);

        boolean inRange = fuseX == 160;
        assertEquals(inRange ? List.of(new Admission(FUSE_SLOT, BUCKET)) : List.of(),
                callsForSlot(manager, FUSE_SLOT));
        assertEquals(inRange ? 1 : 0,
                manager.activeObjectsOfType(Sonic1BombFuseInstance.class).size());
        assertEquals(inRange ? 61 : 62, tail(manager).accepted);
        assertEquals(inRange ? expectedQueue(61, 32, FUSE_SLOT) : expectedQueue(62, 32),
                NativeSpriteSubmissionAccess.admissions(manager));
    }

    @Test
    void expiryOutsideRememberStateRangeKeepsItsFirstOrphanAdmission() {
        ObjectManager manager = withFuse(0, 0x400, false, 0);

        // The actual object pass reaches the live fuse before RememberState
        // deletes its in-place replacement; no direct object update is used.
        manager.update(0, null, List.of(), 1);

        assertEquals(List.of(new Admission(FUSE_SLOT, BUCKET)), callsForSlot(manager, FUSE_SLOT),
                "Bom_Shrapnel queues first; the later deletion cannot undo that word");
        assertTrue(fragments(manager).stream().noneMatch(piece -> piece.getSlotIndex() == FUSE_SLOT));
        assertEquals(61, tail(manager).accepted,
                "body plus orphan consume two explicit queue words despite deletion");
        assertEquals(expectedQueue(61, 32, FUSE_SLOT), NativeSpriteSubmissionAccess.admissions(manager));
    }

    @ParameterizedTest
    @ValueSource(ints = {59, 63})
    void finiteBucketAdmitsCallsAtTheirExecutionTimeWithoutChangingGameplay(int prefixCalls) {
        ObjectManager manager = withFuse(0, 160, false, prefixCalls);

        manager.update(0, null, List.of(), 1);

        // Body (1) + prefix (59) + conversion (2) leaves one of 63 words for tail.
        // Body (1) + the first 62 of 63 prefix attempts fills it before the fuse.
        int acceptedPrefix = prefixCalls == 59 ? 59 : 62;
        List<Admission> expected = Stream.concat(Stream.of(new Admission(32, BUCKET)),
                IntStream.range(0, acceptedPrefix)
                        .mapToObj(ignored -> new Admission(34, BUCKET))).toList();
        expected = Stream.concat(expected.stream(), prefixCalls == 59
                ? Stream.of(new Admission(FUSE_SLOT, BUCKET), new Admission(FUSE_SLOT, BUCKET),
                        new Admission(TAIL_SLOT, BUCKET))
                : Stream.empty()).toList();
        assertEquals(expected, NativeSpriteSubmissionAccess.admissions(manager),
                "63 admitted words retain execution order, including repeated slot references");
        assertEquals(prefixCalls == 59 ? 1 : 0, tail(manager).accepted);
        assertEquals(acceptedPrefix, manager.activeObjectsOfType(QueueFillProbe.class).stream()
                .filter(probe -> probe.getSlotIndex() == 34).findFirst().orElseThrow().accepted,
                "the body consumes the first word before prefix admission");
        assertEquals(4, fragments(manager).size(), "display admission does not gate shrapnel creation");
        assertEquals(158, fragments(manager).getFirst().getX());
        assertEquals(93, fragments(manager).getFirst().getY());
    }

    @Test
    void destructiveQueueAndBombRestoreRetainsDuplicatesAndReplaysForward() throws Exception {
        ObjectManager manager = withFuse(0, 160, true, 0);
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        manager.update(0, null, List.of(), 1);
        Sonic1BombShrapnelInstance first = fragments(manager).getFirst();
        List<Admission> convertedQueue = NativeSpriteSubmissionAccess.admissions(manager);
        assertEquals(expectedQueue(60, 32, FUSE_SLOT, FUSE_SLOT), convertedQueue);
        List<Flight> convertedFlight = flight(manager);
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        NativeSpriteSubmissionAccess.registerRewind(manager, registry);
        var converted = registry.capture();

        manager.update(0, null, List.of(), 2);
        List<Admission> forwardQueue = NativeSpriteSubmissionAccess.admissions(manager);
        assertEquals(expectedQueue(58, 32, 40, 41, 42, 43), forwardQueue);
        List<Flight> forwardFlight = flight(manager);
        assertNotEquals(convertedQueue, forwardQueue, "the later pass destroys the duplicate-call queue");
        assertNotEquals(convertedFlight, forwardFlight, "the later pass advances actual Bomb motion");

        registry.restore(converted);

        assertNotSame(first, fragments(manager).getFirst(), "force recreation instead of retaining live owners");
        assertEquals(convertedFlight, flight(manager));
        assertEquals(convertedQueue, NativeSpriteSubmissionAccess.admissions(manager),
                "queue restore runs after graph recreation and retains raw slot references");
        assertFalse(NativeSpriteSubmissionAccess.submit(manager, tail(manager), BUCKET),
                "restoring the full queue also restores its bucket admission limit");
        manager.update(0, null, List.of(), 2);
        assertEquals(forwardFlight, flight(manager));
        assertEquals(forwardQueue, NativeSpriteSubmissionAccess.admissions(manager));
    }

    private static ObjectManager withFuse(int timer, int x, boolean ceiling, int prefixCalls) {
        ObjectManager manager = newManager(prefixCalls);
        Sonic1BombBadnikInstance body = manager.activeObjectsOfType(Sonic1BombBadnikInstance.class).getFirst();
        manager.createDynamicObjectAtSlot(() -> new Sonic1BombFuseInstance(
                x, 96, false, ceiling, timer, ceiling ? -0x10 : 0x10, body), FUSE_SLOT);
        return manager;
    }

    private static ObjectManager newManager(int prefixCalls) {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic1ObjectRegistry(),
                0, null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.enableCounterBasedRespawn();
        manager.reset(0);
        manager.createDynamicObjectAtSlot(() -> new Sonic1BombBadnikInstance(
                new ObjectSpawn(160, 96, 0x5F, 0, 0, false, 0)), 32);
        if (prefixCalls != 0) {
            manager.createDynamicObjectAtSlot(() -> new QueueFillProbe(prefixCalls), 34);
        }
        manager.createDynamicObjectAtSlot(() -> new QueueFillProbe(63), TAIL_SLOT);
        return manager;
    }

    /** Exact independent expected FIFO; every number comes from native calls/capacity. */
    private static List<Admission> expectedQueue(int tailCalls, int... precedingSlots) {
        return Stream.concat(IntStream.of(precedingSlots).mapToObj(slot -> new Admission(slot, BUCKET)),
                IntStream.range(0, tailCalls).mapToObj(ignored -> new Admission(TAIL_SLOT, BUCKET))).toList();
    }

    private static List<Admission> callsForSlot(ObjectManager manager, int slot) {
        return NativeSpriteSubmissionAccess.admissions(manager).stream()
                .filter(call -> call.slot() == slot).toList();
    }

    private static QueueFillProbe tail(ObjectManager manager) {
        return manager.activeObjectsOfType(QueueFillProbe.class).stream()
                .filter(probe -> probe.getSlotIndex() == TAIL_SLOT).findFirst().orElseThrow();
    }

    private static List<Sonic1BombShrapnelInstance> fragments(ObjectManager manager) {
        return manager.activeObjectsOfType(Sonic1BombShrapnelInstance.class).stream()
                .filter(piece -> !piece.isDestroyed())
                .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
    }

    private static List<Flight> flight(ObjectManager manager) throws Exception {
        return fragments(manager).stream().map(piece -> {
            try {
                var motion = (SubpixelMotion.State) field(piece, "motionState").get(piece);
                return new Flight(piece.getSlotIndex(), piece.getX(), piece.getY(), motion.xSub, motion.ySub,
                        field(piece, "yVelocity").getInt(piece), field(piece, "animTickCounter").getInt(piece),
                        field(piece, "renderXFlip").getBoolean(piece), field(piece, "renderYFlip").getBoolean(piece));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }).toList();
    }

    private static Field field(Object object, String name) throws NoSuchFieldException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private record Flight(int slot, int x, int y, int xFraction, int yFraction,
                          int yVelocity, int animationTick, boolean xFlip, boolean yFlip) { }

    /** Capacity observer runs inside the same native SST walk, never at draw time. */
    private static final class QueueFillProbe extends AbstractObjectInstance
            implements NativeSpriteSubmissionSource, RewindRecreatable {
        private int requested;
        private int accepted;

        // The generic rewind codec constructs a harmless zero-argument probe
        // before invoking recreateForRewind; scalar state is restored later.
        QueueFillProbe() {
            this(0);
        }

        QueueFillProbe(int requested) {
            super(new ObjectSpawn(160, 96, 0x7F, 0, 0, false, 0), "QueueFillProbe");
            this.requested = requested;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            accepted = 0;
            for (int call = 0; call < requested; call++) {
                if (NativeSpriteSubmissionAccess.submit(services().objectManager(), this, BUCKET)) {
                    accepted++;
                }
            }
        }

        @Override
        public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) {
            // This producer measures call admission without any render-time work.
        }

        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new QueueFillProbe(0);
        }
    }
}

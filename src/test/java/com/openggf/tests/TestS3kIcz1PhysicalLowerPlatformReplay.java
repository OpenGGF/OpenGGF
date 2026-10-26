package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.IczSwingingPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ICZ1 native320 solo Sonic: cold physical post1/lower-piece contact and a real
 * attached swing, with two fresh full-registry replay cycles at each boundary.
 *
 * <p>Origin: S3K parity continuation, 2026-10-20. The input stream was observed
 * through GameLoop; it is input data, never gameplay state hydration. Parent
 * placement $3CE0/$0784 and its lower piece at Y+8 are distinct. Native
 * sub_8B0B0 arms the swing; loc_8AD20 precedes the circular update. Public
 * contact/motion assertions do not reconstruct the callback's velocity operand.
 * This is not a natural death/reload, Knuckles, whole-act or native-pixel test.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kIcz1PhysicalLowerPlatformReplay {
    private static final int L = AbstractPlayableSprite.INPUT_LEFT;
    private static final int R = AbstractPlayableSprite.INPUT_RIGHT;
    private static final int D = AbstractPlayableSprite.INPUT_DOWN;
    private static final int A = AbstractPlayableSprite.INPUT_JUMP;
    private static final int POST_X = 0x3BD0, POST_Y = 0x076F;
    private static final int PLATFORM_X = 0x3CE0, PLATFORM_Y = 0x0784;
    private static final CanonicalEntryMatrixSupport.Row ROW =
            new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "off", "");

    private record Hold(int frames, int mask) {}
    // The observed1988-input contact stream, split before its final Right input.
    private static final List<Hold> BEFORE_CONTACT = List.of(
            new Hold(1185, 0), new Hold(1, A), new Hold(109, 0), new Hold(93, R), new Hold(4, 0),
            new Hold(1, D), new Hold(1, D | A), new Hold(1, D), new Hold(1, D | A),
            new Hold(1, D), new Hold(1, D | A), new Hold(2, D),
            new Hold(4, 0), new Hold(62, L), new Hold(10, 0), new Hold(75, R), new Hold(150, 0),
            new Hold(1, D), new Hold(1, D | A), new Hold(1, D), new Hold(1, D | A),
            new Hold(1, D), new Hold(1, D | A), new Hold(2, D),
            new Hold(52, 0), new Hold(14, L | A), new Hold(5, L), new Hold(66, 0),
            new Hold(1, D), new Hold(1, D | A), new Hold(1, D), new Hold(1, D | A),
            new Hold(1, D), new Hold(1, D | A), new Hold(2, D), new Hold(33, L), new Hold(100, R));
    private static final List<Hold> EXCURSION = List.of(new Hold(8, L), new Hold(32, 0));

    private final CanonicalEntryMatrixSupport entry = new CanonicalEntryMatrixSupport() {
        @Override TestS3kCanonicalEntryLifecycle.Act binding() {
            return new TestS3kCanonicalEntryLifecycle.Act("ICZ1", 5, 0);
        }
    };
    private InputHandler input;
    private GameLoop loop;
    private RewindRegistry registry;
    private ObjectManager objects;
    private Object runtime;
    private IczSwingingPlatformObjectInstance platform;
    private ObjectRefId platformId;
    private PlayerRefId playerId;
    private Bk2FrameInput previous = pad(0, 0);
    private int inputCount, lives;
    private boolean naturalRingLoss;

    private record Spot(CompositeSnapshot world, Bk2FrameInput previousInput, int inputCount) {}
    private record RidePoint(int lowerX, int lowerY, int playerX, int playerY, int vx, int groundSpeed) {}

    @BeforeEach
    void saveConfiguration() {
        entry.saveConfiguration();
    }

    @AfterEach
    void restoreConfiguration() {
        try {
            if (loop != null) loop.closePresence();
        } finally {
            entry.restoreConfiguration();
        }
    }

    @Test
    void nativeSonicContactsAndRidesTheLowerPieceWithTwoFreshWorldReplays() {
        var fixture = entry.boot(ROW);
        entry.assertOwners(ROW);
        assertInstanceOf(Sonic.class, player());
        assertEquals("sonic", player().characterKey().persisted());
        objects = GameServices.level().getObjectManager();
        runtime = GameServices.zoneRuntimeRegistry().current();
        lives = GameServices.gameState().getLives();
        registry = fixture.gameplayMode().getRewindRegistry();
        input = new InputHandler();
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(previous, previous));
        loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        assertTrue(loop.ownsGameplayFadeLifecycle());
        assertFalse(checkpoint().isActive());
        assertEquals(-1, checkpoint().getLastCheckpointIndex());

        advance(BEFORE_CONTACT, false);
        assertEquals(1987, inputCount, "authentic stream before its last Right");
        assertTrue(naturalRingLoss, "ordinary prefix actually loses its collected rings through hurt");
        assertSavedPost();
        assertPlacedPost();
        platform = placedPlatform();
        assertEquals(PLATFORM_X, platform.getX());
        assertEquals(PLATFORM_Y, platform.getY(), "parent body, not lower-piece center");
        assertEquals(PLATFORM_Y + 8, platform.getPieceY(0));
        assertNull(objects.getRidingObject(player()), "last pre-contact state has no current support");
        var identities = objects.captureIdentityContext().requireIdentityTable();
        platformId = identities.idFor(platform);
        playerId = identities.encodePlayer(player());
        assertNotNull(platformId);
        assertNotNull(playerId);
        assertFalse(playerId.isNull());
        // Exercise production reconstruction, not the optional in-place restore optimization.
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        Spot beforeContact = capture();

        step(R, true);
        assertEquals(1988, inputCount);
        assertEquals(PLATFORM_Y + 8, platform.getPieceY(0), "contact lower-piece center");
        Spot contact = capture();

        var expectedRide = new ArrayList<RidePoint>();
        expectedRide.add(point());
        appendRide(expectedRide, List.of(new Hold(8, L), new Hold(31, 0)));
        Spot beforeReturn = capture();
        RidePoint lastUpwardSide = point();
        step(0, true);
        expectedRide.add(point());
        assertTrue(point().lowerY() > lastUpwardSide.lowerY(), "actual final neutral crosses the sampled turn");
        assertFirstExcursion(expectedRide);
        Spot returned = capture();
        assertEquals(2028, inputCount);

        for (int cycle = 0; cycle < 2; cycle++) {
            restore(beforeContact, false, "before lower contact " + cycle);
            step(R, true);
            same(contact, "lower contact replay " + cycle, 1);
        }
        for (int cycle = 0; cycle < 2; cycle++) {
            restore(contact, true, "lower ride start " + cycle);
            var actualRide = new ArrayList<RidePoint>();
            actualRide.add(point());
            appendRide(actualRide, EXCURSION);
            assertEquals(expectedRide, actualRide, "all ordinary ride samples replay " + cycle);
            assertFirstExcursion(actualRide);
            same(returned, "full excursion replay " + cycle, 40);
        }
        // Restore a non-default moving state too, including circular fractions/chain arrays.
        for (int cycle = 0; cycle < 2; cycle++) {
            restore(beforeReturn, true, "before sampled return " + cycle);
            int beforeY = platform.getPieceY(0);
            step(0, true);
            assertTrue(platform.getPieceY(0) > beforeY);
            same(returned, "sampled return replay " + cycle, 1);
        }
    }

    private void advance(List<Hold> holds, boolean riding) {
        for (Hold hold : holds) {
            for (int i = 0; i < hold.frames(); i++) step(hold.mask(), riding);
        }
    }

    private void appendRide(List<RidePoint> points, List<Hold> holds) {
        for (Hold hold : holds) {
            for (int i = 0; i < hold.frames(); i++) {
                step(hold.mask(), true);
                points.add(point());
            }
        }
    }

    private void step(int mask, boolean riding) {
        int ringsBefore = player().getRingCount();
        var current = pad(inputCount, mask);
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
        previous = current;
        inputCount++;
        loop.step();
        GameServices.graphics().runPendingRenderThreadTasks();
        naturalRingLoss |= ringsBefore > 0 && player().getRingCount() == 0 && player().isHurt();
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        assertSame(objects, GameServices.level().getObjectManager());
        assertSame(runtime, GameServices.zoneRuntimeRegistry().current());
        assertFalse(player().getDead(), "no hidden death or reload");
        assertEquals(lives, GameServices.gameState().getLives());
        if (riding) assertRide();
    }

    private Spot capture() {
        assertStableWorld();
        objects.validateRewindReferenceClosure();
        return new Spot(registry.capture(), previous, inputCount);
    }

    private void restore(Spot spot, boolean riding, String label) {
        var discarded = platform;
        registry.restore(spot.world());
        // Test-driver input history is external to the world snapshot; restore its exact held baseline.
        previous = spot.previousInput();
        inputCount = spot.inputCount();
        var identities = objects.captureIdentityContext().requireIdentityTable();
        platform = assertInstanceOf(IczSwingingPlatformObjectInstance.class,
                identities.resolveObject(platformId, true));
        assertNotSame(discarded, platform, "fresh restored actor");
        assertSame(discarded.getSpawn(), platform.getSpawn(), "original ROM placement identity");
        assertFalse(objects.getActiveObjects().stream().anyMatch(object -> object == discarded));
        assertEquals(platformId, identities.idFor(platform));
        assertEquals(playerId, identities.encodePlayer(player()), "current P1 identity");
        assertSame(player(), identities.resolvePlayer(playerId, true));
        assertEquals(1L, objects.activeObjectsOfType(IczSwingingPlatformObjectInstance.class).stream()
                .filter(object -> platformId.equals(identities.idFor(object))).count());
        assertStableWorld();
        if (riding) assertRide();
        else {
            assertNull(objects.getRidingObject(player()));
            assertFalse(objects.hasStandingContact(player()));
        }
        objects.validateRewindReferenceClosure();
        same(spot, label, 0);
    }

    private void same(Spot expected, String label, int frames) {
        var actual = registry.capture();
        assertEquals(expected.world().entries().keySet(), actual.entries().keySet(), label + " complete layout");
        TestS3kCanonicalEntryLifecycle.assertSameWorld(expected.world(), actual, label, frames);
    }

    private void assertRide() {
        var p1 = player();
        assertSame(platform, objects.getRidingObject(p1));
        assertEquals(0, objects.getRidingPieceIndex(p1));
        assertTrue(objects.hasStandingContact(p1));
        // Native child SSTs have separate standing bits; the folded parent has no aggregate bit.
        var standingBits = objects.rewindSnapshottable().capture().solidContactState().standingBits();
        assertTrue(standingBits.stream()
                .filter(binding -> playerId.equals(binding.playerId()))
                .flatMap(binding -> binding.keys().stream())
                .anyMatch(key -> key.spawn() == platform.getSpawn() && !key.instanceKey() && key.pieceIndex() == 0),
                "the actual lower-child standing bit is captured for current P1");
        assertTrue(p1.isOnObject());
        assertFalse(p1.getAir());
        assertFalse(p1.isHurt());
        assertSame(platform, p1.getLatchedSolidObjectInstance(), "current restored latch owner");
        assertTrue(p1.hasLatchedSolidObjectBinding());
        assertFalse(p1.isLatchedSolidObjectReleased());
        assertEquals(Sonic3kObjectIds.ICZ_SWINGING_PLATFORM, p1.getLatchedSolidObjectId());
        assertEquals(platform.getSlotIndex(), p1.getInteractSlotIndex());
        assertEquals(1, p1.getRingCount(), "natural recollected ring remains; no ring seed");
        assertEquals(0, p1.getInvulnerableFrames());
        assertSavedPost();
    }

    private void assertStableWorld() {
        entry.assertOwners(ROW);
        assertEquals("sonic", player().characterKey().persisted());
        assertInstanceOf(Sonic.class, player());
        assertSame(objects, GameServices.level().getObjectManager());
        assertSame(runtime, GameServices.zoneRuntimeRegistry().current());
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        assertFalse(player().getDead());
        assertEquals(lives, GameServices.gameState().getLives());
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
        assertFalse(GameServices.fade().isActive());
        assertFalse(GameServices.fade().hasPendingCompletion());
        assertSavedPost();
    }

    private void assertSavedPost() {
        assertTrue(checkpoint().isActive());
        assertEquals(1, checkpoint().getLastCheckpointIndex());
        assertEquals(POST_X, checkpoint().getSavedX());
        assertEquals(POST_Y, checkpoint().getSavedY());
    }

    private void assertPlacedPost() {
        var posts = objects.activeObjectsOfType(Sonic3kStarPostObjectInstance.class).stream()
                .filter(post -> post.getSpawn().x() == POST_X && post.getSpawn().y() == POST_Y).toList();
        assertEquals(1, posts.size(), "one actual ROM-placed post");
        assertEquals(1, posts.getFirst().getCheckpointIndex());
    }

    private IczSwingingPlatformObjectInstance placedPlatform() {
        var candidates = objects.activeObjectsOfType(IczSwingingPlatformObjectInstance.class).stream()
                .filter(object -> object.getSpawn().x() == PLATFORM_X && object.getSpawn().y() == PLATFORM_Y
                        && object.getSpawn().subtype() == 0).toList();
        assertEquals(1, candidates.size(), "exact actual placed platform, never nearest-owner reconstruction");
        return candidates.getFirst();
    }

    private RidePoint point() {
        return new RidePoint(platform.getPieceX(0), platform.getPieceY(0),
                player().getCentreX(), player().getCentreY(), player().getXSpeed(), player().getGSpeed());
    }

    private static void assertFirstExcursion(List<RidePoint> points) {
        boolean rose = false, moved = false;
        int firstReturn = -1;
        for (int i = 1; i < points.size(); i++) {
            int dy = points.get(i).lowerY() - points.get(i - 1).lowerY();
            moved |= points.get(i).lowerX() != points.get(i - 1).lowerX() || dy != 0;
            if (dy < 0) rose = true;
            if (rose && dy > 0 && firstReturn < 0) firstReturn = i;
        }
        assertTrue(moved, "ordinary updates move the attached platform");
        assertTrue(rose, "actual upward excursion");
        assertEquals(points.size() - 1, firstReturn, "zeros are not a turn; tail ends on first sampled return");
    }

    private static AbstractPlayableSprite player() { return GameServices.sprites().getMainPlayable(); }
    private static CheckpointState checkpoint() {
        return assertInstanceOf(CheckpointState.class, GameServices.level().getCheckpointState());
    }
    private static Bk2FrameInput pad(int frame, int mask) {
        return new Bk2FrameInput(frame, mask, (mask & A) != 0 ? InputActionMasks.ACTION_A : 0, false, "");
    }
}

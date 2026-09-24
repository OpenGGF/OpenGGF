package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.S3kBossDefeatSignpostFlow;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.boss.BossChildComponent;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZMiniboss} (sonic3k.asm:160001-160900), the Lava Reef act 1 miniboss.
 *
 * <p>The load-bearing reading here is the child census. {@code loc_78562} makes two rings of
 * twelve through {@code CreateChild8_TreeListRepeated}, whose subtype counter steps with
 * {@code addq.w #2,d2} (sonic3k.asm:177181-177203) -- by <b>two</b>, not one. {@code loc_7880A}
 * then sorts each child by subtype: {@code 0} is an arm segment, {@code $16} the firing hand, and
 * everything between an arm link. Stepping by two makes {@code $16} the twelfth child, so each
 * ring is one segment, ten links and one hand.
 *
 * <p>Read the counter as stepping by one and the same code produces two arm segments, twenty-two
 * links and <b>no hands at all</b> -- and since the hands are the only children that shoot and the
 * only ones with their own {@code collision_property}, the whole fight would be inert. That is
 * what these assertions are for.
 */
class TestLrzMinibossInstance {

    private static final int SPAWN_X = 0x2C00;
    private static final int SPAWN_Y = 0x0700;

    private LrzMinibossInstance boss;
    private Camera camera;
    private TestObjectServices services;

    @Test
    void wideViewportKeepsBothArmAnchorsAtTheirNativeWorldPositions() {
        var wideCamera = org.mockito.Mockito.mock(Camera.class);
        org.mockito.Mockito.when(wideCamera.getWidth()).thenReturn((short)800);
        org.mockito.Mockito.when(wideCamera.getX()).thenReturn((short)0x2B10);
        org.mockito.Mockito.when(wideCamera.getY()).thenReturn((short)0x710);
        var wideServices = new TestObjectServices().withIsolatedObjectManager().withCamera(wideCamera);
        boss.setServices(wideServices);
        boss.update(1000,null);
        for (boolean mirrored : new boolean[]{false,true}) {
            var arm = new LrzMinibossArmSegmentChild(boss,0,mirrored);
            arm.setServices(wideServices);
            arm.update(1000,null);
            assertEquals(mirrored ? 0x2D20 : 0x2C20,arm.getX());
        }
    }

    @Test
    void detachedDebrisUsesNativePriorityBucket() {
        var piece = new LrzMinibossDebrisChild(boss, 0);
        assertEquals(1, piece.getPriorityBucket(), "word_78D7E priority $80 selects bucket one");
    }

    @Test
    void detachedDebrisCullingDeletesOnTheFollowingNativeDispatch() {
        var piece = new LrzMinibossDebrisChild(boss, 0);
        piece.setServices(services);
        piece.update(1, null);
        assertTrue(piece.wasDrawnThisFrame());
        piece.offsetNativePositionWordsPreserveSubpixel(0x4000, 0);
        piece.update(2, null);
        assertFalse(piece.wasDrawnThisFrame());
        assertFalse(piece.isDestroyed(), "Go_Delete_Sprite_3 only installs the delete routine");
        piece.update(3, null);
        assertTrue(piece.isDestroyed());
    }

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        SessionManager.clear();
        camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) SPAWN_X);
        camera.setY((short) SPAWN_Y);
        boss = new LrzMinibossInstance(new ObjectSpawn(SPAWN_X, SPAWN_Y, 0x9D, 0, 0, false, 0));
        services = new TestObjectServices().withIsolatedObjectManager().withCamera(camera)
                .withGameState(new com.openggf.game.GameStateManager());
        boss.setServices(services);
        // Every test below this line is about the fight itself, which loc_78528 does not install
        // until Check_CameraInRange, sub_85D6A and loc_85CA4 have finished. Drive that gate here
        // so each test's first boss.update() is the routine table's slot 0, loc_78562, exactly as
        // it was before the gate existed. The gate's own behaviour is asserted separately, on a
        // boss built by ungatedBossWithCameraAt().
        camera.setY((short) 0x0710);
        for (int frame = 0; frame < 600 && !boss.isArenaGateComplete(); frame++) {
            boss.update(frame, null);
        }
        assertTrue(boss.isArenaGateComplete(), "arena gate did not complete in setUp");
        camera.setY((short) SPAWN_Y);
    }

    /**
     * A drill at an arbitrary spawn height whose arena gate has already been driven to
     * completion, so its first {@code update} is {@code loc_78562}.
     */
    private LrzMinibossInstance startedBossAt(int spawnY) {
        camera.setY((short) 0x0710);
        LrzMinibossInstance started =
                new LrzMinibossInstance(new ObjectSpawn(SPAWN_X, spawnY, 0x9D, 0, 0, false, 0));
        started.setServices(
                new TestObjectServices().withIsolatedObjectManager().withCamera(camera));
        for (int frame = 0; frame < 600 && !started.isArenaGateComplete(); frame++) {
            started.update(frame, null);
        }
        assertTrue(started.isArenaGateComplete(), "arena gate did not complete for the fixture");
        camera.setY((short) SPAWN_Y);
        return started;
    }

    /**
     * A second drill with its own services, used only by the gate tests: {@link #setUp} has
     * already driven {@link #boss} past its gate.
     */
    private LrzMinibossInstance ungatedBossWithCameraAt(int cameraX, int cameraY) {
        camera.setX((short) cameraX);
        camera.setY((short) cameraY);
        LrzMinibossInstance fresh =
                new LrzMinibossInstance(new ObjectSpawn(SPAWN_X, SPAWN_Y, 0x9D, 0, 0, false, 0));
        fresh.setServices(new TestObjectServices().withIsolatedObjectManager().withCamera(camera));
        return fresh;
    }

    // ---------------------------------------------------------------------
    // The arena gate: Check_CameraInRange, sub_85D6A and loc_85CA4.
    // ---------------------------------------------------------------------

    /**
     * {@code Obj_LRZMiniboss} (sonic3k.asm:160001-160010) does not enter {@code off_7854C} at all
     * on its first dispatch. It runs {@code Check_CameraInRange} against {@code word_784E0}
     * ({@code $610,$810,$2B00,$2D00}), installs {@code loc_78522} -- which is nothing but
     * {@code jmp loc_85CA4} -- and only when that gate has finished does {@code $34(a0)}
     * ({@code loc_78528}) install {@code loc_78538}, the routine table whose slot 0 is
     * {@code loc_78562} and its two child rings.
     *
     * <p>So a drill whose camera has not reached the arena has <b>no children</b>: the arms are
     * not there to unroll, and the fight has not started.
     */
    @Test
    void theDrillCreatesNothingUntilTheCameraGateCompletes() {
        LrzMinibossInstance gated = ungatedBossWithCameraAt(0x2B00, 0x0600);
        for (int frame = 0; frame < 300; frame++) {
            gated.update(frame, null);
        }
        assertFalse(gated.isArenaGateComplete(),
                "loc_85CA4 needs Camera_Y >= _unkFAB0 ($710); at $600 it only walks Camera_min_Y");
        assertTrue(gated.getChildComponents().isEmpty(),
                "loc_78562 runs from loc_78538, which loc_78528 installs only once the gate ends");
    }

    /**
     * {@code sub_85D6A} takes {@code word_784E8} = {@code $710,$710,$2C00,$2C00} into
     * {@code _unkFAB0/2/4/6}, and {@code loc_85CF2}/{@code loc_85D36} write exactly those into
     * {@code Camera_min_Y_pos}, {@code Camera_target_max_Y_pos}, {@code Camera_min_X_pos} and
     * {@code Camera_max_X_pos}. The recorded Sonic + Tails run shows the same numbers: from row
     * 22990 to the end of the fight its camera is pinned at {@code ($2C00,$710)} and never moves.
     */
    @Test
    void theGateLocksTheArenaToWord784E8() {
        LrzMinibossInstance gated = ungatedBossWithCameraAt(0x2C00, 0x0710);
        for (int frame = 0; frame < 300 && !gated.isArenaGateComplete(); frame++) {
            gated.update(frame, null);
        }
        assertTrue(gated.isArenaGateComplete(), "loc_85D48: all three $27 bits set");
        assertEquals(0x2C00, camera.getMinX() & 0xFFFF, "Camera_min_X_pos = _unkFAB4");
        assertEquals(0x2C00, camera.getMaxX() & 0xFFFF, "Camera_max_X_pos = _unkFAB6");
        assertEquals(0x0710, camera.getMinY() & 0xFFFF, "Camera_min_Y_pos = _unkFAB0");
        assertEquals(0x0710, camera.getMaxYTarget() & 0xFFFF, "Camera_target_max_Y_pos = _unkFAB2");
    }

    /**
     * {@code loc_85D70}'s {@code move.w #2*60,$2E(a0)} is counted down by {@code loc_85CA4}
     * before {@code boss_saved_mus} ({@code mus_Miniboss}) is played, so the fight music does not
     * start on the frame the player arrives. The camera bits can latch earlier; the gate is not
     * complete until the music bit is set too ({@code loc_85D48} tests all three).
     */
    @Test
    void theGateWaitsTwoSecondsBeforeTheMinibossMusic() {
        LrzMinibossInstance gated = ungatedBossWithCameraAt(0x2C00, 0x0710);
        int completedAt = -1;
        for (int frame = 0; frame < 300; frame++) {
            gated.update(frame, null);
            if (gated.isArenaGateComplete()) {
                completedAt = frame;
                break;
            }
        }
        assertTrue(completedAt >= 2 * 60,
                "loc_85CA4 plays boss_saved_mus only after $2E counts past zero, "
                        + "and loc_85D48 needs bit 0 for the gate to end; completed at "
                        + completedAt);
    }

    @Test
    void initCreatesTwoRingsOfOneSegmentTenLinksAndOneHand() {
        boss.update(0, null);

        List<BossChildComponent> children = boss.getChildComponents();
        assertEquals(24, children.size(),
                "two CreateChild8_TreeListRepeated rings of $C-1+1 = 12 (ChildObjDat_78D84 and _78D8A)");
        assertEquals(2L, countOf(children, LrzMinibossArmSegmentChild.class),
                "subtype 0 of each ring is loc_78838, the camera-anchored arm segment");
        assertEquals(2L, countOf(children, LrzMinibossHandChild.class),
                "subtype $16 is loc_78922, the firing hand -- reachable only because "
                        + "CreateChild8_TreeListRepeated steps the subtype by two");
        assertEquals(20L, countOf(children, LrzMinibossOrbiterChild.class),
                "subtypes 2..$14 are loc_788DE arm links");
    }

    @Test
    void theTwoRingsAreMirrorImages() {
        boss.update(0, null);
        List<LrzMinibossArmSegmentChild> segments = boss.getChildComponents().stream()
                .filter(LrzMinibossArmSegmentChild.class::isInstance)
                .map(LrzMinibossArmSegmentChild.class::cast)
                .toList();
        assertEquals(2, segments.size());
        assertNotEquals(segments.get(0).isMirrored(), segments.get(1).isMirrored(),
                "loc_787FE sets render_flags bit 0 on the second ring only");
    }

    /**
     * {@code loc_78592} -> {@code loc_785C2}: the boss waits {@code $2F} frames for its art, then
     * extends the arms and hovers for {@code $15F}.
     */
    @Test
    void theArtDelayRunsForFortySevenFramesBeforeTheArmsExtend() {
        boss.update(0, null);      // loc_78562, then routine steps to loc_78592
        boss.update(1, null);      // loc_78592 queues the art and loads $2F

        for (int frame = 0; frame < 0x2F; frame++) {
            boss.update(2 + frame, null);
            assertEquals(0, boss.getFlags38() & 0x08,
                    "bset #3,$38 happens only when $2E passes zero, at frame $2F");
        }
        boss.update(2 + 0x2F, null);
        assertTrue((boss.getFlags38() & 0x08) != 0,
                "loc_785C2: bset #3,$38(a0) once the $2F countdown goes negative");
    }

    /**
     * {@code loc_78D2C} -> {@code loc_787D8}: with both hands dead the hover collapses from
     * {@code $15F} to {@code $1F}, which is what ends the fight quickly.
     */
    @Test
    void killingBothHandsShortensTheHover() {
        boss.update(0, null);
        List<LrzMinibossHandChild> hands = boss.getChildComponents().stream()
                .filter(LrzMinibossHandChild.class::isInstance)
                .map(LrzMinibossHandChild.class::cast)
                .toList();
        assertEquals(2, hands.size());

        // The production path only: the touch pass zeroes collision_flags and decrements
        // collision_property (sonic3k.asm:20916-20923), and sub_78CF4 does the rest from the
        // hand's own update. Nothing here calls a test-only hit entry point.
        int frame = hitEveryHandUntilDead(hands, 0x600);

        for (LrzMinibossHandChild hand : hands) {
            assertEquals(0, hand.getHitsRemaining(),
                    "collision_property 4 at loc_78922, after " + frame + " frames");
            assertTrue(hand.isStatusBit7Set(),
                    "the blow that empties collision_property sets status bit 7");
        }
        assertEquals(0xC0, boss.getFlags38() & 0xC0,
                "sub_78CF4's loc_78D2C sets $38 bits 6 and 7, one per facing");
    }

    /**
     * The two frames on which the hand's routine changes, which break the obvious rule in opposite
     * directions.
     *
     * <p>{@code loc_78946} (sonic3k.asm:160388-160405) ends {@code bra.w sub_78B46} with no
     * {@code Draw_Sprite} after it, so the frame the hand <b>arms</b> is not drawn -- and it has
     * done no positional work either, so drawing it would put the sprite wherever the create loop
     * left it. {@code loc_7897A} (sonic3k.asm:160407-160432) tests the parent's bit 2 and rewrites
     * {@code (a0)} for the next frame, but this frame still falls through {@code loc_7898C} to
     * {@code loc_789C4}, so the frame it <b>stops</b> is drawn.
     *
     * <p>Deriving the draw from the routine byte gets both of these backwards at once, and a still
     * frame cannot tell you so.
     */
    @Test
    void theHandIsNotDrawnOnTheFrameItArmsButIsOnTheFrameItStops() {
        LrzMinibossHandChild hand = runToFiringHand(false, 0x200);
        // The arming frame is the last frame of ROUTINE_IDLE, i.e. the frame isFiring() first
        // reads true -- and on that frame loc_78946 ran, not loc_7897A.
        assertTrue(hand.isFiring(), "precondition: the hand has just armed");
        assertFalse(hand.wasDrawnThisFrame(),
                "loc_78946 ends bra.w sub_78B46: there is no Draw_Sprite on the arming frame");

        boss.update(nextFrame(), null);
        assertTrue(hand.wasDrawnThisFrame(), "loc_7897A draws once it is live");

        // Bit 2 is the arm segment's "finished retracting" cue; loc_7897A reads it and hands back
        // to loc_78946 for the NEXT frame, drawing on this one.
        boss.setHandReloadFlag();
        boss.update(nextFrame(), null);
        assertFalse(hand.isFiring(), "the bit-2 test reinstalls loc_78946");
        assertTrue(hand.wasDrawnThisFrame(),
                "loc_7897A falls through loc_7898C to loc_789C4 on the frame it hands back");

        boss.update(nextFrame(), null);
        assertFalse(hand.wasDrawnThisFrame(), "and the idle frame after it is not drawn");
    }

    /**
     * {@code sub_78CF4} (sonic3k.asm:160756-160776): the hand's own hit path. It is gated on
     * {@code collision_flags(a0)} being <b>zero</b> -- the touch pass zeroes it and stows the old
     * value in {@code $25} -- opens a {@code $20}-frame window in {@code $20(a0)}, plays
     * {@code sfx_BossHit}, creates {@code ChildObjDat_78D98}'s ring, and restores
     * {@code collision_flags} from {@code $25} only when the window counts out.
     *
     * <p>Watched across the whole window, because the defect an endpoint assertion misses is a
     * window that restores the byte immediately: the hand would then take all four hits in four
     * frames.
     */
    @Test
    void aHitOnTheHandOpensATwentyFrameWindowBeforeItCanBeHitAgain() {
        LrzMinibossHandChild hand = runToFiringHand(false, 0x200);
        boss.update(nextFrame(), null);
        assertEquals(6, hand.getCollisionFlags(), "word_78D6C's collision byte is 6");

        hand.onPlayerAttack(null, null);
        assertEquals(0, hand.getCollisionFlags(), "the touch pass zeroes collision_flags");
        assertEquals(3, hand.getHitsRemaining());

        List<Integer> windowByFrame = new ArrayList<>();
        for (int i = 0; i < 0x20; i++) {
            boss.update(nextFrame(), null);
            windowByFrame.add(hand.getHitWindowTimer());
        }
        // $20 is seeded to $20 on the first frame sub_78CF4 sees the zero, then decremented on
        // that same frame, so the first observed value is $1F and the last is 0.
        assertEquals(0x1F, windowByFrame.get(0).intValue(), "observed: " + windowByFrame);
        assertEquals(0, windowByFrame.get(windowByFrame.size() - 1).intValue(),
                "observed: " + windowByFrame);
        assertEquals(0x20, windowByFrame.stream().distinct().count(),
                "the window must step by one every frame, not jump: " + windowByFrame);
        assertEquals(6, hand.getCollisionFlags(),
                "move.b $25(a0),collision_flags(a0) only when the window reaches zero");
    }

    /**
     * {@code move.w a0,d0 / move.b d0,$1C(a1)} (sonic3k.asm:20917-20918) records which player
     * landed the blow: {@code $00} for {@code Player_1} at {@code $FFFFB000}, {@code $4A} for
     * {@code Player_2} at {@code $FFFFB04A}.
     */
    @Test
    void theTouchPassRecordsWhichPlayerHitTheDrill() {
        advanceTo(ROUTINE_SLAM, 0x400);
        assertNotEquals(0, boss.getCollisionFlags(), "the slam frame is the hittable one");
        boss.onPlayerAttack(null, null);
        assertEquals(0x00, boss.getAttackerMarker(),
                "the main character's object address is $FFFFB000");
        assertFalse(boss.isStatusBit7Set(), "five hits left, so no kill marker yet");
    }

    /**
     * {@code MoveSprite_AtAngleLookup} reads {@code parent3}, a stored object-slot pointer. When
     * the link it points at is gone the ROM has no anchor to read, and substituting the boss body
     * would teleport the hand from the end of the arm to the drill on one frame.
     */
    @Test
    void theHandStaysPutWhenItsAnchorLinkIsGone() {
        LrzMinibossHandChild hand = runToFiringHand(false, 0x200);
        boss.update(nextFrame(), null);
        boss.update(nextFrame(), null);
        int[] before = {hand.getX(), hand.getY()};
        assertNotEquals(boss.getX(), hand.getX(),
                "precondition: the hand is not already sitting on the drill");

        boss.getChildComponents().removeIf(child -> child instanceof LrzMinibossRingChild ring
                && !ring.ringMirrored() && ring.ringSubtype() == 0x14);

        for (int i = 0; i < 8; i++) {
            boss.update(nextFrame(), null);
            assertEquals(before[0], hand.getX(),
                    "the hand moved after its anchor was retired (frame " + i + ")");
            assertEquals(before[1], hand.getY(),
                    "the hand moved after its anchor was retired (frame " + i + ")");
        }
    }

    /**
     * {@code sub_78B46} (sonic3k.asm:160568-160590) plus {@code loc_78B86}
     * (sonic3k.asm:160592-160605). Killing a hand sets the parent's bit for that ring, and every
     * child of that ring then parks on {@code Wait_Draw} for {@code $2C - subtype * 2} frames
     * before exploding and, {@code $F} frames later, deleting.
     *
     * <p>The subtype rises towards the hand, so the wait <b>shortens</b> towards the hand: the arm
     * peels away from its far end first. Only the other ring is untouched. Watched as an ordering
     * across frames, because a retirement that fires all at once and a retirement in the wrong
     * order both end with the same empty ring.
     */
    @Test
    void killingAHandPeelsItsOwnArmAwayFromTheHandEndFirst() {
        boss.update(frameCursor, null);          // loc_78562 creates the two rings
        LrzMinibossHandChild doomed = hands().stream().filter(h -> !h.ringMirrored())
                .findFirst().orElseThrow();
        hitEveryHandUntilDead(List.of(doomed), 0x600);
        assertEquals(0x40, boss.getFlags38() & 0xC0,
                "only the unmirrored ring's bit 6 is set");

        Map<Integer, Integer> deletedOnFrame = new LinkedHashMap<>();
        int survivorsOfTheOtherRing = -1;
        for (int i = 0; i < 0x60; i++) {
            int frame = nextFrame();
            boss.update(frame, null);
            if (i == 0) {
                for (var child : boss.getChildComponents()) {
                    if (child instanceof LrzMinibossRingChild ring) {
                        assertEquals(ring.ringMirrored() ? 0 : 1, child.getPriorityBucket(),
                                "sub_78B46 changes only the retiring ring from $00 to $80");
                    }
                }
            }
            for (int subtype = 2; subtype <= 0x14; subtype += 2) {
                final int s = subtype;
                boolean present = boss.getChildComponents().stream()
                        .anyMatch(c -> c instanceof LrzMinibossRingChild ring
                                && !ring.ringMirrored() && ring.ringSubtype() == s);
                if (!present) {
                    deletedOnFrame.putIfAbsent(subtype, frame);
                }
            }
            survivorsOfTheOtherRing = (int) boss.getChildComponents().stream()
                    .filter(c -> c instanceof LrzMinibossRingChild ring && ring.ringMirrored())
                    .count();
        }

        assertEquals(10, deletedOnFrame.size(),
                "every link of the dead hand's ring retires: " + deletedOnFrame);
        assertEquals(12, survivorsOfTheOtherRing,
                "the other ring's bit was never set, so none of its twelve children retire");
        for (int subtype = 4; subtype <= 0x14; subtype += 2) {
            assertTrue(deletedOnFrame.get(subtype) <= deletedOnFrame.get(subtype - 2),
                    "$2E = $2C - subtype*2 shortens towards the hand, so a higher subtype must "
                            + "not outlive a lower one; observed " + deletedOnFrame);
        }
        assertTrue(deletedOnFrame.get(0x14) < deletedOnFrame.get(2),
                "the link nearest the hand must go before the link nearest the shoulder: "
                        + deletedOnFrame);
        long liveRingParts = boss.getChildComponents().stream()
                .filter(LrzMinibossRingChild.class::isInstance).count();
        boss.afterRewindRestoreSettled();
        assertEquals(liveRingParts, boss.getChildComponents().stream()
                        .filter(LrzMinibossRingChild.class::isInstance).count(),
                "settling a restored graph must not resurrect the defeated hand's arm");
    }

    /**
     * The drill's travel bounds, and the direction it moves between them. {@code loc_78562} sets
     * {@code _unkFAB0 = $7A8} and {@code _unkFAB2 = y_pos}; {@code loc_78606} starts the leg with
     * {@code y_vel = -$400}; and {@code loc_78628}'s {@code cmp.w y_pos(a0),d0 / blo.w} returns
     * while {@code $7A8} is numerically below {@code y_pos}. So a boss spawned <b>below</b>
     * {@code $7A8} climbs to it and stops there; it does not snap on the first frame, and it does
     * not sail past.
     *
     * <p>This is the assertion that catches an inverted comparison. With the branch the wrong way
     * round the boss either arrives instantly or never arrives, and only a test that watches the
     * position over the whole leg can tell those apart from the correct behaviour.
     */
    @Test
    void theDrillClimbsToTheTravelTopOneStepAtATime() {
        int spawnY = 0x0900;                 // below $7A8 on screen, numerically greater
        boss = startedBossAt(spawnY);

        int frame = 0;
        while (boss.getY() == spawnY && frame < 2000) {
            boss.update(frame++, null);
        }
        assertTrue(frame < 2000, "the boss never left its spawn height; the rise leg was not reached");

        // loc_78606's y_vel = -$400 is four pixels a frame, so $900 - $7A8 = $158 takes 86
        // frames. An inverted compare in loc_78628 snaps straight to $7A8 in one, which every
        // endpoint assertion alone would happily accept - so count the steps, not the endpoint.
        int stepsAtIntermediateHeights = 0;
        int previous = boss.getY();
        assertTrue(previous > 0x07A8 && previous < spawnY,
                "the first moved frame must land strictly between the spawn height and $7A8, "
                        + "not on $7A8 itself; it did land on " + Integer.toHexString(previous));
        while (boss.getY() != 0x07A8 && frame < 2000) {
            boss.update(frame++, null);
            assertTrue(boss.getY() <= previous,
                    "loc_78606 sets y_vel = -$400: this leg only ever moves up");
            if (boss.getY() > 0x07A8) {
                stepsAtIntermediateHeights++;
            }
            previous = boss.getY();
        }
        assertEquals(0x07A8, boss.getY(),
                "loc_78628 snaps y_pos to _unkFAB0 = $7A8 and swings there");
        assertTrue(stepsAtIntermediateHeights > 50,
                "at four pixels a frame the $158 climb takes about 86 frames; only "
                        + stepsAtIntermediateHeights + " intermediate frames were observed, "
                        + "which is what an inverted loc_78628 compare looks like");
    }

    /**
     * {@code parent3(a1)} is a stored pointer: retiring a sibling does not re-aim anyone
     * (sonic3k.asm:177181-177203). The engine prunes destroyed children from the parent's list,
     * so resolving the predecessor by list position survives an unrelated removal only by luck --
     * a link and its immediate neighbour shift together. It does not survive the removal of the
     * <b>anchor itself</b>: ring two's first link would then take whatever slid into that slot,
     * which is ring one's hand, wiring the two arms together. Identity by (ring, subtype) instead
     * resolves to nothing and falls back to the boss, which is the safe reading.
     *
     * <p>Break it by resolving with {@code indexOf(this) - 1} and this fails with ring one's hand.
     */
    @Test
    void retiringALinksAnchorDoesNotAimItAtTheOtherRing() {
        boss.update(0, null);
        List<BossChildComponent> children = boss.getChildComponents();

        LrzMinibossOrbiterChild firstLinkOfSecondRing = children.stream()
                .filter(LrzMinibossOrbiterChild.class::isInstance)
                .map(LrzMinibossOrbiterChild.class::cast)
                .filter(link -> link.ringMirrored() && link.ringSubtype() == 2)
                .findFirst().orElseThrow();
        BossChildComponent anchor = firstLinkOfSecondRing.previousLinkForTest();
        assertTrue(anchor instanceof LrzMinibossArmSegmentChild
                        && ((LrzMinibossRingChild) anchor).ringMirrored(),
                "subtype 2's predecessor is its own ring's arm segment, but it was "
                        + (anchor == null ? "null" : anchor.getClass().getSimpleName()));

        // sub_78B46 retires children one at a time; the parent prunes them from its list.
        children.remove(anchor);

        BossChildComponent after = firstLinkOfSecondRing.previousLinkForTest();
        assertFalse(after instanceof LrzMinibossRingChild ring && !ring.ringMirrored(),
                "retiring the anchor aimed ring two's first link at a ring one child ("
                        + (after == null ? "null" : after.getClass().getSimpleName())
                        + "), wiring the two arms together");
    }

    // ======================================================================
    // Motion over frames. Every assertion below watches a value change frame
    // by frame, because the defects these hold are all shape defects: a
    // stagger that collapses, an animation that starts one pair late, an
    // angle that never reaches its endpoint, a hand that follows the wrong
    // object. An endpoint assertion agrees with every one of them.
    // ======================================================================

    /**
     * {@code sub_78BD6} (sonic3k.asm:160642-160648) parks each link and each hand on
     * {@code Wait_Draw} for the {@code $2E} {@code loc_7880A} gave it -- {@code (mirrored ? $10 : 0)
     * + subtype * 2} (sonic3k.asm:160258-160261, 160276-160279) -- so the two arms unroll link by
     * link. Without it every child runs {@code loc_788F4} on the same frame and both arms snap out
     * fully formed.
     *
     * <p>Delete the {@code staggerElapsed} gate and this fails with every link's first moved frame
     * equal to 2.
     */
    @Test
    void eachArmLinkStartsMovingOnItsOwnStaggerFrame() {
        boss.update(0, null);
        Map<LrzMinibossOrbiterChild, int[]> origin = new LinkedHashMap<>();
        for (LrzMinibossOrbiterChild link : links()) {
            origin.put(link, new int[] {link.getX(), link.getY()});
        }
        assertEquals(20, origin.size());

        Map<LrzMinibossOrbiterChild, Integer> firstMovedFrame = new HashMap<>();
        for (int frame = 1; frame <= 0x50; frame++) {
            boss.update(frame, null);
            for (Map.Entry<LrzMinibossOrbiterChild, int[]> entry : origin.entrySet()) {
                LrzMinibossOrbiterChild link = entry.getKey();
                if (firstMovedFrame.containsKey(link)) {
                    continue;
                }
                if (link.getX() != entry.getValue()[0] || link.getY() != entry.getValue()[1]) {
                    firstMovedFrame.put(link, frame);
                }
            }
        }

        assertEquals(20, firstMovedFrame.size(), "every link must come alive within $50 frames");
        for (Map.Entry<LrzMinibossOrbiterChild, Integer> entry : firstMovedFrame.entrySet()) {
            LrzMinibossOrbiterChild link = entry.getKey();
            // $2E counts down from the creation frame; Obj_Wait runs $34 on the frame the counter
            // goes negative, and $34 only installs loc_788F4, so the first move is one frame later.
            int stagger = (link.ringMirrored() ? 0x10 : 0) + link.ringSubtype() * 2;
            assertEquals(stagger + 2, entry.getValue().intValue(),
                    "link subtype " + Integer.toHexString(link.ringSubtype())
                            + (link.ringMirrored() ? " (mirrored)" : "") + " has $2E = "
                            + Integer.toHexString(stagger));
        }

        // The unmirrored ring starts on frames 6, 10 ... 42 and the mirrored one, $10 later, on
        // 22, 26 ... 58. Six frames are shared, so twenty links come alive on fourteen distinct
        // frames -- not twenty, and emphatically not one.
        List<Integer> distinct = firstMovedFrame.values().stream().distinct().sorted().toList();
        assertEquals(14, distinct.size(), "observed start frames: " + distinct);
        assertEquals(6, distinct.get(0).intValue());
        assertEquals(58, distinct.get(distinct.size() - 1).intValue());
    }

    /**
     * {@code Animate_RawMultiDelay} (sonic3k.asm:177563-177579) does {@code addq.w #2,d0}
     * <b>before</b> the read, on an {@code anim_frame} {@code Set_Raw_Animation} just cleared, so
     * the first pair a script plays is index 2. Index 0/1 is only the frame {@code loc_845F2}
     * emits when {@code $FC} restarts the script.
     *
     * <p>{@code byte_78DF1} is {@code 3,0 3,0 4,0 $F4}: three pairs, each with delay 0, then the
     * {@code $F4} callback. {@code loc_786DA} moves the drill {@code addq.w #4,y_pos} per frame,
     * so the drop is exactly three frames and twelve pixels. Start the walk at index 0 and it is
     * four frames and sixteen -- which no endpoint assertion on "the boss reached the slam" can
     * see.
     */
    @Test
    void theDropIsThreeFramesAndTwelvePixels() {
        int frame = advanceTo(ROUTINE_DROP, 0x400);
        int yAtDropStart = boss.getY();
        List<Integer> droppedTo = new ArrayList<>();
        while (boss.getState().routine == ROUTINE_DROP && frame < 0x500) {
            boss.update(++frame, null);
            droppedTo.add(boss.getY());
        }
        assertEquals(ROUTINE_SLAM, boss.getState().routine,
                "byte_78DF1's $F4 hands loc_786EA the slam");
        assertEquals(3, droppedTo.size(),
                "loc_786DA ran " + droppedTo.size() + " times; byte_78DF1 has three pairs");
        assertEquals(List.of(yAtDropStart + 4, yAtDropStart + 8, yAtDropStart + 12), droppedTo,
                "four pixels a frame, from " + Integer.toHexString(yAtDropStart));
    }

    /**
     * {@code loc_788F4} (sonic3k.asm:160361-160377) writes the stepped angle at {@code loc_7890C}
     * <b>unconditionally</b>; the out-of-window branch at {@code loc_78908} only negates
     * {@code $40} and then falls through to the same store. So the sway reaches {@code $6F} and
     * {@code $91}, one step outside the {@code [$70,$90]} test window, before turning round.
     * Recomputing the angle after flipping the step keeps it inside and loses two units of travel
     * at each extreme.
     */
    @Test
    void theLinkAngleOvershootsItsTestWindowByOneStepAtEachEnd() {
        boss.update(0, null);
        LrzMinibossOrbiterChild link = links().get(0);
        int minimum = 0xFF;
        int maximum = 0x00;
        for (int frame = 1; frame <= 0x120; frame++) {
            boss.update(frame, null);
            if (!link.isStaggerElapsed()) {
                continue;
            }
            minimum = Math.min(minimum, link.getAngle());
            maximum = Math.max(maximum, link.getAngle());
        }
        assertEquals(0x6F, minimum, "loc_78908 negates $40 but loc_7890C still stores $6F");
        assertEquals(0x91, maximum, "and still stores $91 at the other end");
    }

    /**
     * {@code MoveSprite_CircularSimple} (sonic3k.asm:178424-178441) with {@code d2 = 4}: the 8.8
     * sine and cosine are lifted into the whole half of a 16.16 longword and shifted right four,
     * so the offset from {@code parent3} is {@code sine << 12} on X and {@code cosine << 12} on Y,
     * added to and stored back as a longword. The values are the ROM's
     * {@code Levels/Misc/sine.bin}, not {@code Math.sin}: at angle {@code $80} the table is
     * {@code (0, -256)}, at {@code $90} it is {@code (-97, -236)} and at {@code $6F} it is
     * {@code (103, -234)}.
     */
    @Test
    void aLinkHangsOffItsPredecessorAtTheRomSineTableOffset() {
        boss.update(0, null);
        LrzMinibossOrbiterChild link = links().stream()
                .filter(candidate -> !candidate.ringMirrored() && candidate.ringSubtype() == 2)
                .findFirst().orElseThrow();
        LrzMinibossArmSegmentChild anchor = segments().stream()
                .filter(segment -> !segment.isMirrored()).findFirst().orElseThrow();

        Map<Integer, int[]> romSineCosine = Map.of(
                0x80, new int[] {0, -256},
                0x90, new int[] {-97, -236},
                0x6F, new int[] {103, -234});
        Map<Integer, Boolean> seen = new HashMap<>();

        boolean liveLastFrame = false;
        for (int frame = 1; frame <= 0x120; frame++) {
            boss.update(frame, null);
            boolean live = liveLastFrame;
            liveLastFrame = link.isStaggerElapsed();
            if (!live) {
                // Obj_Wait's $34 only installs loc_788F4; the first positional frame is the next
                // one, and until then the link still sits where the create loop copied it.
                continue;
            }
            int[] expected = romSineCosine.get(link.getAngle());
            if (expected == null) {
                continue;
            }
            seen.put(link.getAngle(), true);
            assertEquals(expected[0] << 12, link.ringXFixed() - anchor.ringXFixed(),
                    "angle " + Integer.toHexString(link.getAngle()) + ": sine.bin gives "
                            + expected[0] + ", so the 16.16 X offset is sine << 12");
            assertEquals(expected[1] << 12, link.ringYFixed() - anchor.ringYFixed(),
                    "angle " + Integer.toHexString(link.getAngle()) + ": cosine " + expected[1]);
        }
        assertEquals(3, seen.size(), "all three sampled angles must occur within $120 frames");
    }

    /**
     * {@code loc_7897A} (sonic3k.asm:160422-160446) positions the hand with
     * {@code MoveSprite_AtAngleLookup} over {@code AngleLookup_2}, anchored on {@code parent3} --
     * the tenth arm link, subtype {@code $14} -- not on the boss body. {@code sub_78BD6} leaves
     * {@code $3C = $80} and nothing ever steps it, so the offset is
     * {@code AtAngle_80_BF} at {@code lo = 0}: {@code (-AngleLookup_2[0], -AngleLookup_2[$3F])}
     * {@code = (0, -$18)}.
     *
     * <p>{@code loc_78946}, the idle routine, does no positional work at all and has no
     * {@code Draw_Sprite} after it. Inheriting the shared sync-to-parent instead is what makes
     * shots appear to leave the drill rather than the end of the arm.
     */
    @Test
    void theFiringHandRidesTheLastArmLinkNotTheBossBody() {
        int frame = 0;
        boss.update(frame, null);
        LrzMinibossHandChild hand = hands().stream()
                .filter(candidate -> !candidate.ringMirrored()).findFirst().orElseThrow();
        LrzMinibossOrbiterChild lastLink = links().stream()
                .filter(candidate -> !candidate.ringMirrored() && candidate.ringSubtype() == 0x14)
                .findFirst().orElseThrow();

        int firingFrames = 0;
        boolean firingLastFrame = false;
        while (frame < 0x400 && firingFrames < 40) {
            boss.update(++frame, null);
            boolean positioned = firingLastFrame;
            firingLastFrame = hand.isFiring();
            if (!positioned) {
                // loc_78946 installs loc_7897A and returns; the first positional frame is the
                // next one. Until then the hand is still where CreateChild8 copied it -- on the
                // boss -- which is exactly the state this test exists to see it leave.
                continue;
            }
            firingFrames++;
            assertEquals(lastLink.getX(), hand.getX(),
                    "AtAngle_80_BF's d5 is -AngleLookup_2[0] = 0, so the hand shares its anchor's X");
            assertEquals(lastLink.getY() - 0x18, hand.getY(),
                    "and d6 is -AngleLookup_2[$3F] = -$18");
            assertNotEquals(boss.getY(), hand.getY(),
                    "the hand must not be following the drill body");
        }
        assertTrue(firingFrames >= 40,
                "the hand never entered loc_7897A within $400 frames; observed " + firingFrames);
    }

    /**
     * {@code loc_786BC} sets {@code collision_flags = $B5} for the drop -- a hurt box the player
     * cannot damage -- and {@code loc_786EA} replaces it with {@code 6} for the slam, which is
     * the only phase with a hit box. {@code loc_78784} clears it again once
     * {@code anim_frame >= 6} on the way back down.
     */
    @Test
    void onlyTheSlamCarriesAHitBox() {
        int frame = advanceTo(ROUTINE_DROP, 0x400);
        assertEquals(0xB5, boss.getCollisionFlags(),
                "loc_786BC: move.b #$B5,collision_flags(a0)");
        frame = advanceTo(ROUTINE_SLAM, 0x500);
        assertEquals(6, boss.getCollisionFlags(), "loc_786EA: move.b #6,collision_flags(a0)");
    }

    /**
     * {@code sub_78C14} (sonic3k.asm:160641-160670) with the {@code FixBugs = 0} branch. A hit
     * zeroes {@code collision_flags} (the touch code's work, stowed in {@code $25}) and
     * decrements {@code collision_property}; {@code sub_78C14} then runs {@code $20} frames of
     * {@code sub_78C98}, alternating the {@code word_78CB2} window on bit 0 of {@code $20}, and
     * restores {@code collision_flags} when the counter reaches zero.
     *
     * <p>The shipped {@code addi.w #2*2,d0} makes the flash half read from word <b>2</b> --
     * {@code 2, $644, $422, 0, $888, $AAA}, a window straddling the boss's own colours and the
     * white flash -- where the {@code FixBugs} branch would read the six white words at word 6.
     * Change {@code FLASH_WORD_OFFSET_SHIPPED} to {@code 6} and this fails, which is the point:
     * the wrong-looking flash is the shipped behaviour.
     */
    @Test
    void aHitRunsTheShippedWrongHalfOfWord78CB2ForThirtyTwoFrames() {
        int frame = advanceTo(ROUTINE_SLAM, 0x500);
        assertEquals(6, boss.getState().hitCount, "collision_property 6 at loc_78562");

        boss.onPlayerAttack(null, null);
        assertEquals(5, boss.getState().hitCount, "Touch_Response decrements collision_property");
        assertEquals(0, boss.getCollisionFlags(), "and zeroes collision_flags, stowing $25");

        List<Integer> windows = new ArrayList<>();
        for (int i = 0; i < 0x20; i++) {
            boss.update(++frame, null);
            windows.add(boss.getLastFlashWindow());
        }
        assertEquals(2, windows.get(0).intValue(),
                "$20 starts at $20, so bit 0 is clear and the FIRST frame takes the bugged window");
        assertEquals(0, windows.get(1).intValue(), "$1F: bit 0 set, so the boss's own colours");
        assertFalse(windows.contains(6),
                "word 6 is the white flash the FixBugs branch would use; the shipped ROM never "
                        + "reaches it");
        assertEquals(List.of(0, 2), windows.stream().distinct().sorted().toList(),
                "sub_78C98 only ever copies from word 0 or word 2 with FixBugs = 0");
        assertEquals(0, boss.getHitInvulnTimer(), "$20 counted out");
        assertEquals(6, boss.getCollisionFlags(),
                "move.b $25(a0),collision_flags(a0) restores the slam hit box");
        // word_78CA6's six offsets are into Normal_palette_line_2, and the ROM's palette line
        // names are ONE-based: sonic3k.constants.asm:767-770 has Normal_palette ds.b $80 with
        // Normal_palette_line_2 = Normal_palette+$20. Reading the digit as a zero-based engine
        // index puts the whole flash on the wrong line -- and because the shipped window is the
        // boss's own colours rather than white, the symptom is unrelated sprites tinting for $20
        // frames, not an invisible flash.
        assertEquals(1, LrzMinibossInstance.flashPaletteLine(),
                "Normal_palette_line_2 is Normal_palette+$20, i.e. engine palette index 1");
    }

    /**
     * {@code Sprite_CheckDeleteTouchXY} (sonic3k.asm:179032-179043) is coarse and asymmetric: the
     * X test aligns {@code x_pos} to {@code $FF80} and compares against
     * {@code Camera_X_pos_coarse_back = (Camera_X_pos - $80) & $FF80} with a {@code $280} window,
     * and the Y test is {@code y_pos - Camera_Y_pos + $80} against {@code $200}. Both are
     * {@code bhi}, so a value exactly on the bound survives. An eyeballed
     * {@code cameraX + $180} / {@code cameraY + $140} box retires shots that the ROM keeps.
     */
    @Test
    void aShotIsCulledOnTheRomWindowNotAnEyeballedBox() {
        camera.setX((short) 0x1000);
        camera.setY((short) 0x0700);
        // Camera_X_pos_coarse_back = ($1000 - $80) & $FF80 = $F80. The arguments below are the
        // hand's position: ChildObjDat_78D90 offsets the shot by +8 in X, and loc_78A1C runs
        // MoveSprite2 before Sprite_CheckDeleteTouchXY, so shot 1's word_78BCA pair ($200,$300)
        // has already added 2 in X and 3 in Y by the time the cull reads x_pos/y_pos.
        assertTrue(shotSurvivesAt(0x1200, 0x0700),
                "x_pos $120A coarsens to $1200; $1200 - $F80 = $280, which bhi does not delete -- "
                        + "and which a cameraX + $180 box would");
        assertFalse(shotSurvivesAt(0x1280, 0x0700), "x_pos $128A coarsens to $1280; $300 > $280");
        assertTrue(shotSurvivesAt(0x1000, 0x087D),
                "y_pos $880: $880 - $700 + $80 = $200 exactly, which bhi keeps -- a "
                        + "cameraY + $140 box killed it $40 earlier");
        assertFalse(shotSurvivesAt(0x1000, 0x087E), "y_pos $881 gives $201 > $200");
    }

    /**
     * The defeat chain, end to end: {@code loc_78C60} -> {@code Wait_FadeToLevelMusic}
     * (sonic3k.asm:179656-179669) -> {@code loc_787E0} (sonic3k.asm:160247-160255).
     *
     * <p>Three readings are load-bearing and none of them can be seen from the end state.
     * {@code loc_78C60} does <b>not</b> reseed {@code $2E}: the fade wait consumes whatever the
     * interrupted phase left in it, so the pause before the drill breaks up is not a fixed
     * length. {@code loc_85674} clears {@code render_flags} bit 7 on the frame the wait ends, so
     * the drill stops being drawn <i>before</i> the pieces appear rather than with them. And
     * {@code loc_787E0}'s {@code CreateChild1_Normal} runs once, producing exactly eleven pieces
     * at the {@code ChildObjDat_78D9E} offsets -- not a scatter, and not a piece per frame.
     */
    @Test
    void theDefeatChainFadesTheDrillOutBeforeItBreaksIntoElevenPieces() {
        int hits = hitDrillUntilDefeated(0x1200);
        assertEquals(6, hits, "collision_property 6 at loc_78562");
        assertTrue(boss.getState().defeated, "loc_78C60");
        assertEquals(0xC0, boss.getFlags38() & 0xC0, "bset #6 and #7 of $38(a0)");
        assertEquals(1, boss.getDefeatPhase(), "(a0) = Wait_FadeToLevelMusic");
        assertFalse(boss.isDrawSuppressed(),
                "Wait_FadeToLevelMusic draws every frame until $2E goes negative");
        assertEquals(0, debris().size(), "loc_787E0 has not run yet");

        int fadeFrames = 0;
        while (!boss.isDrawSuppressed() && fadeFrames < 0x400) {
            assertEquals(0, debris().size(),
                    "the pieces must not appear before the fade ends (frame " + fadeFrames + ")");
            boss.update(nextFrame(), null);
            fadeFrames++;
        }
        assertTrue(fadeFrames > 0 && fadeFrames < 0x400,
                "the fade wait ended after " + fadeFrames + " frames");
        assertEquals(2, boss.getDefeatPhase(), "loc_787E0 has run");
        assertTrue(boss.isDestroyed(), "the drill becomes EndSignControl instead of retaining a second slot");
        assertTrue(boss.isDrawSuppressed(),
                "loc_85674: bclr #7,render_flags(a0), and nothing sets it again");

        List<LrzMinibossDebrisChild> pieces = debris();
        assertEquals(11, pieces.size(), "ChildObjDat_78D9E is dc.w $B-1, so eleven pieces");
        assertEquals(1, countActive(S3kBossDefeatSignpostFlow.class),
                "loc_787E0 ends jmp (Obj_EndSignControl).l");

        // The eleven are distinguishable: CreateChild1_Normal's sequential subtype picks both the
        // frame (RawAni_78A9C) and the velocity (Obj_VelocityIndex entries 23..33). Collapse the
        // subtype to a constant and all eleven leave on the same arc with the same frame.
        List<Integer> frames = pieces.stream().map(LrzMinibossDebrisChild::getMappingFrame).toList();
        assertEquals(List.of(0x0C, 0x0C, 0x0C, 0x11, 0x11, 0x12, 0x13, 0x0D, 0x0E, 0x0F, 0x10),
                frames, "RawAni_78A9C read with lsr.w #1");

        // And they move, on their own velocities. The creation offsets are already distinct, so
        // "distinct X" on the creation frame proves nothing at all; what this asserts is the
        // per-piece displacement after four frames, which is the velocity and nothing else.
        // The table below is the production one, so it is an oracle for the mechanism (the 16.16
        // step and where gravity lands) and not for the values. These three rows are transcribed
        // here straight from Obj_VelocityIndex entries 23, 24 and 33 (sonic3k.asm:179203-179213)
        // so at least the ends and the start of the window are checked against the ROM itself.
        assertEquals(List.of(0, -0x100),
                List.of(LrzMinibossDebrisChild.DEBRIS_VELOCITIES[0][0],
                        LrzMinibossDebrisChild.DEBRIS_VELOCITIES[0][1]),
                "Obj_VelocityIndex entry 23, which d0 = $5C selects for subtype 0");
        assertEquals(List.of(-0x100, -0x100),
                List.of(LrzMinibossDebrisChild.DEBRIS_VELOCITIES[1][0],
                        LrzMinibossDebrisChild.DEBRIS_VELOCITIES[1][1]),
                "entry 24, one four-byte step on from subtype 2's subtype * 2");
        assertEquals(List.of(0x300, -0x300),
                List.of(LrzMinibossDebrisChild.DEBRIS_VELOCITIES[10][0],
                        LrzMinibossDebrisChild.DEBRIS_VELOCITIES[10][1]),
                "entry 33, the last of the eleven");

        // loc_78A70 draws the initialized pieces without a movement step.
        stepFrameWithDebris();
        Map<Integer, int[]> before = new LinkedHashMap<>();
        for (LrzMinibossDebrisChild piece : pieces) {
            before.put(piece.getIndex(), new int[] {piece.getX(), piece.getY()});
        }
        for (int i = 0; i < 4; i++) {
            stepFrameWithDebris();
        }
        for (LrzMinibossDebrisChild piece : pieces) {
            int[] origin = before.get(piece.getIndex());
            // MoveSprite in 16.16: four frames of x_vel/$100 pixels, and four of y_vel with
            // gravity $38 added before each step after the first.
            int expectedDx = 0;
            int expectedDy = 0;
            int yVelocity = LrzMinibossDebrisChild.DEBRIS_VELOCITIES[piece.getIndex()][1];
            for (int frame = 0; frame < 4; frame++) {
                expectedDx += LrzMinibossDebrisChild.DEBRIS_VELOCITIES[piece.getIndex()][0];
                expectedDy += yVelocity;
                yVelocity += 0x38;
            }
            assertEquals(origin[0] + (expectedDx >> 8), piece.getX(),
                    "piece " + piece.getIndex() + " X after four MoveSprite frames");
            assertEquals(origin[1] + (expectedDy >> 8), piece.getY(),
                    "piece " + piece.getIndex() + " Y: gravity is added to y_vel AFTER the step");
        }
    }

    /**
     * {@code Obj_FlickerMove}'s tail is {@code bchg #6,$38(a0) / beq -> rts}, and {@code bchg}
     * sets the condition code from the bit's value <b>before</b> the change. Starting from a
     * cleared byte the first frame therefore does not draw and the second does, alternating from
     * there. Reading {@code bchg} as setting the flag from the new value inverts the whole
     * pattern, which is invisible in a still and obvious in motion.
     */
    @Test
    void theDebrisFlickersOnAlternateFramesStartingWithASkippedOne() {
        hitDrillUntilDefeated(0x1200);
        for (int i = 0; i < 0x400 && debris().isEmpty(); i++) {
            boss.update(nextFrame(), null);
        }
        assertFalse(debris().isEmpty(), "loc_787E0 never ran within $400 frames of the defeat");
        LrzMinibossDebrisChild piece = debris().get(0);
        int originX = piece.getX();
        int originY = piece.getY();
        stepFrameWithDebris();
        assertTrue(piece.wasDrawnThisFrame(), "loc_78A70 ends in Draw_Sprite");
        assertEquals(originX, piece.getX());
        assertEquals(originY, piece.getY());
        List<Boolean> drawn = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            stepFrameWithDebris();
            drawn.add(piece.wasDrawnThisFrame());
        }
        assertEquals(List.of(false, true, false, true, false, true, false, true), drawn,
                "bchg reads the old bit: observed " + drawn);
    }

    // ===== helpers =====

    private static final int ROUTINE_DROP = 0x10;
    private static final int ROUTINE_SLAM = 0x12;

    /**
     * The shared {@code V_int_run_count} cursor. {@code shouldUpdate} drops a child update whose
     * count matches the parent's last, so every frame in a test must be a new number.
     */
    private int frameCursor;

    private int nextFrame() {
        return ++frameCursor;
    }

    /**
     * Runs frames until the named ring's hand is in {@code loc_7897A}. The hand arms on the frame
     * the boss sets {@code $38} bit 3 and its own {@code $2E} stagger has elapsed, so this returns
     * with the hand on its <b>arming</b> frame -- the one {@code loc_78946} does not draw.
     */
    private LrzMinibossHandChild runToFiringHand(boolean mirrored, int limit) {
        boss.update(frameCursor, null);
        LrzMinibossHandChild hand = hands().stream()
                .filter(h -> h.ringMirrored() == mirrored).findFirst().orElseThrow();
        for (int i = 0; i < limit && !hand.isFiring(); i++) {
            boss.update(nextFrame(), null);
        }
        assertTrue(hand.isFiring(), "the hand never reached loc_7897A in " + limit + " frames");
        return hand;
    }

    /**
     * Drives the production hit path until every named hand has run out of
     * {@code collision_property}: attack whenever the touch pass would be allowed to (the hand is
     * live and its {@code collision_flags} is non-zero), and otherwise just step a frame so
     * {@code sub_78CF4} can count its {@code $20} window out.
     */
    private int hitEveryHandUntilDead(List<LrzMinibossHandChild> targets, int limit) {
        boss.update(frameCursor, null);
        for (int i = 0; i < limit; i++) {
            // loc_78D2C only runs from sub_78CF4, i.e. from a firing frame after the last hit,
            // so the ring's bit -- not the hit count -- is what says the kill has landed.
            boolean flagged = targets.stream().allMatch(h ->
                    h.getHitsRemaining() == 0
                            && (boss.getFlags38() & (h.ringMirrored() ? 0x80 : 0x40)) != 0);
            if (flagged) {
                return frameCursor;
            }
            for (LrzMinibossHandChild hand : targets) {
                if (hand.isFiring() && hand.getCollisionFlags() != 0 && hand.getHitsRemaining() > 0) {
                    hand.onPlayerAttack(null, null);
                }
            }
            boss.update(nextFrame(), null);
        }
        throw new AssertionError("hands not emptied in " + limit + " frames: "
                + targets.stream().map(h -> Integer.toString(h.getHitsRemaining())).toList());
    }

    /**
     * Drives the drill's own hit path to zero through the production touch entry point: attack
     * whenever {@code collision_flags} is non-zero (the slam and the first half of the recovery),
     * and otherwise step a frame so {@code sub_78C14} can count its {@code $20} window out.
     *
     * @return the number of hits landed
     */
    private int hitDrillUntilDefeated(int limit) {
        boss.update(frameCursor, null);
        int hits = 0;
        for (int i = 0; i < limit && !boss.getState().defeated; i++) {
            // Only collision_flags 6 is the attackable-enemy category. loc_786BC's $B5 is the
            // drop's hurt box: andi.b #$C0 on it gives $80, so Touch_Response takes the hurt
            // branch and never calls the boss's attack entry point. Attacking on any non-zero
            // byte would let the test kill the drill in phases the ROM cannot.
            if (boss.getCollisionFlags() == 6) {
                boss.onPlayerAttack(null, null);
                hits++;
            }
            boss.update(nextFrame(), null);
        }
        assertTrue(boss.getState().defeated, "the drill never reached loc_78C60 in " + limit
                + " frames (" + hits + " hits landed)");
        return hits;
    }

    /**
     * One frame for the drill and for the pieces. {@code CreateChild1_Normal} allocates its
     * children into their own object slots ({@code AllocateObjectAfterCurrent}), so the main
     * object loop runs them, not the parent's child pass -- and in this fixture nothing else
     * drives the object manager.
     */
    private void stepFrameWithDebris() {
        int frame = nextFrame();
        boss.update(frame, null);
        for (LrzMinibossDebrisChild piece : debris()) {
            piece.update(frame, null);
        }
    }

    private List<LrzMinibossDebrisChild> debris() {
        return services.objectManager().getActiveObjects().stream()
                .filter(LrzMinibossDebrisChild.class::isInstance)
                .map(LrzMinibossDebrisChild.class::cast)
                .toList();
    }

    private long countActive(Class<?> type) {
        return services.objectManager().getActiveObjects().stream().filter(type::isInstance).count();
    }

    /** Runs the boss until {@code routine(a0)} reads {@code target}, and returns the frame. */
    private int advanceTo(int targetRoutine, int limit) {
        int frame = frameCursor;
        boss.update(frame, null);
        while (boss.getState().routine != targetRoutine && frame < limit) {
            boss.update(frame = nextFrame(), null);
        }
        assertEquals(targetRoutine, boss.getState().routine,
                "routine $" + Integer.toHexString(targetRoutine) + " not reached in " + limit
                        + " frames (stuck at $" + Integer.toHexString(boss.getState().routine) + ")");
        return frame;
    }

    private boolean shotSurvivesAt(int x, int y) {
        LrzMinibossProjectileChild shot = new LrzMinibossProjectileChild(x, y, 1, false);
        shot.setServices(new TestObjectServices().withIsolatedObjectManager().withCamera(camera));
        // loc_78A1C runs MoveSprite2 first, so read the verdict before the shot has travelled far
        // enough to matter: word_78BCA's first pair is ($200,$300), i.e. two and three pixels.
        shot.update(1, null);
        return !shot.isDestroyed();
    }

    private List<LrzMinibossOrbiterChild> links() {
        return boss.getChildComponents().stream()
                .filter(LrzMinibossOrbiterChild.class::isInstance)
                .map(LrzMinibossOrbiterChild.class::cast).toList();
    }

    private List<LrzMinibossHandChild> hands() {
        return boss.getChildComponents().stream()
                .filter(LrzMinibossHandChild.class::isInstance)
                .map(LrzMinibossHandChild.class::cast).toList();
    }

    private List<LrzMinibossArmSegmentChild> segments() {
        return boss.getChildComponents().stream()
                .filter(LrzMinibossArmSegmentChild.class::isInstance)
                .map(LrzMinibossArmSegmentChild.class::cast).toList();
    }

    private static long countOf(List<BossChildComponent> children, Class<?> type) {
        return children.stream().filter(type::isInstance).count();
    }
}

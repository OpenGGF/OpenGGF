package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.session.SessionManager;
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

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        SessionManager.clear();
        camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) SPAWN_X);
        camera.setY((short) SPAWN_Y);
        boss = new LrzMinibossInstance(new ObjectSpawn(SPAWN_X, SPAWN_Y, 0x9D, 0, 0, false, 0));
        boss.setServices(new TestObjectServices().withIsolatedObjectManager().withCamera(camera));
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

        for (LrzMinibossHandChild hand : hands) {
            for (int hit = 0; hit < 4; hit++) {
                hand.takeHit();
            }
            assertEquals(0, hand.getHitsRemaining(),
                    "collision_property 4 at loc_78922");
        }
        assertEquals(0xC0, boss.getFlags38() & 0xC0,
                "sub_78CF4's loc_78D2C sets $38 bits 6 and 7, one per facing");
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
        boss = new LrzMinibossInstance(new ObjectSpawn(SPAWN_X, spawnY, 0x9D, 0, 0, false, 0));
        boss.setServices(new TestObjectServices().withIsolatedObjectManager().withCamera(camera));

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
     * {@code loc_78562} creates the 24 children once, in {@code ROUTINE_INIT}. A rewind restore
     * that lands on any later routine never replays that loop, so if the capture came back short
     * the boss would fight the rest of the round with a truncated arm and nothing would say so.
     * The census is rebuilt from the same deterministic (ring, subtype) loop.
     */
    @Test
    void aRestoreAfterInitStillHasTwentyFourChildren() {
        boss.update(0, null);
        boss.update(1, null);
        assertEquals(24, boss.getChildComponents().size());

        // Whatever the capture lost: a whole ring's hand, a mid-arm link, a segment.
        boss.getChildComponents().removeIf(child -> child instanceof LrzMinibossHandChild hand
                && hand.ringMirrored());
        boss.getChildComponents().removeIf(child -> child instanceof LrzMinibossOrbiterChild link
                && !link.ringMirrored() && link.ringSubtype() == 0x0A);
        assertEquals(22, boss.getChildComponents().size());

        boss.afterRewindRestoreSettled();

        assertEquals(24, boss.getChildComponents().size(),
                "the create loop is deterministic in ring and subtype; rebuild what is missing");
        assertEquals(2L, countOf(boss.getChildComponents(), LrzMinibossHandChild.class));
        assertEquals(20L, countOf(boss.getChildComponents(), LrzMinibossOrbiterChild.class));
        assertEquals(2L, countOf(boss.getChildComponents(), LrzMinibossArmSegmentChild.class));
    }

    // ===== helpers =====

    private static final int ROUTINE_DROP = 0x10;
    private static final int ROUTINE_SLAM = 0x12;

    /** Runs the boss until {@code routine(a0)} reads {@code target}, and returns the frame. */
    private int advanceTo(int targetRoutine, int limit) {
        int frame = 0;
        boss.update(frame, null);
        while (boss.getState().routine != targetRoutine && frame < limit) {
            boss.update(++frame, null);
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

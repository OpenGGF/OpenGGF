package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.CheckpointState;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.SszBouncyCloudObjectInstance;
import com.openggf.game.sonic3k.objects.SszBouncyCloudPuffObjectInstance;
import com.openggf.game.sonic3k.objects.SszCarriedPlayerPose;
import com.openggf.game.sonic3k.objects.SszElevatorBarObjectInstance;
import com.openggf.game.sonic3k.objects.SszRetractingSpringObjectInstance;
import com.openggf.game.sonic3k.objects.SszRotatingPlatformCarrierObjectInstance;
import com.openggf.game.sonic3k.objects.SszRotatingPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.SszSwingingCarrierArcObjectInstance;
import com.openggf.game.sonic3k.objects.SszSwingingCarrierBarObjectInstance;
import com.openggf.game.sonic3k.objects.SszSwingingCarrierObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remaining Sky Sanctuary act-1 traversal families: {@code Obj_SSZBouncyCloud} ({@code $7D}),
 * {@code Obj_SSZElevatorBar} ({@code $7A}), {@code Obj_SSZRotatingPlatform} ({@code $76}),
 * {@code Obj_SSZSwingingCarrier} ({@code $75}) and {@code Obj_SSZRetractingSpring} ({@code $74}).
 *
 * <p>Every expectation is decoded from the ROM here — the placement list at {@code SSZ1_Sprites}
 * and the tables {@code byte_46698}, {@code byte_466A0}, {@code word_466C8}, {@code byte_468C4}
 * and {@code byte_468DC} — or taken from a cited routine, never from the engine's own copies.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszCarriersAndSprings {

    /** {@code SSZ1_Sprites}. */
    private static final int SSZ1_SPRITES = 0x1F90EE;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    private record Placement(int x, int y, int subtype) {}

    private static List<Placement> placementsOf(int objectId) throws IOException {
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        List<Placement> found = new ArrayList<>();
        int address = SSZ1_SPRITES;
        while (rom.readU16BE(address) != 0xFFFF) {
            if (rom.readU8(address + 4) == objectId) {
                found.add(new Placement(rom.readU16BE(address),
                        rom.readU16BE(address + 2) & 0xFFF, rom.readU8(address + 5)));
            }
            address += 6;
        }
        return found;
    }

    /** The five inventory rows this class takes off the act-1 placeholder census. */
    @Test
    void theRomPlacesTheCountsAndSubtypesTheInventoryClaims() throws IOException {
        assertEquals(27, placementsOf(0x7D).size(), "$7D records in SSZ1_Sprites");
        assertEquals(5, placementsOf(0x7A).size(), "$7A records");
        assertEquals(7, placementsOf(0x76).size(), "$76 records");
        assertEquals(8, placementsOf(0x75).size(), "$75 records");
        assertEquals(5, placementsOf(0x74).size(), "$74 records");

        for (Placement cloud : placementsOf(0x7D)) {
            assertEquals(0, cloud.subtype(), "$7D never reads its subtype and none is set");
        }
        for (Placement bar : placementsOf(0x7A)) {
            assertEquals(0, bar.subtype(), "$7A never reads its subtype and none is set");
        }
        for (Placement spring : placementsOf(0x74)) {
            assertEquals(0, spring.subtype(), "$74 never reads its subtype and none is set");
        }
        // $76 reads only bit 0 (loc_45F10's btst #0,subtype), and only 0 and 1 are placed.
        assertEquals(3, placementsOf(0x76).stream().filter(p -> p.subtype() == 0x00).count(),
                "$76:$00, the narrow carrier");
        assertEquals(4, placementsOf(0x76).stream().filter(p -> p.subtype() == 0x01).count(),
                "$76:$01, the wide carrier");
        // $75 reads bit 7 (mode) and bits 0-1 (segment count); $00, $80 and $82 are what is placed.
        assertEquals(5, placementsOf(0x75).stream().filter(p -> p.subtype() == 0x00).count(),
                "$75:$00 pendulums");
        assertEquals(1, placementsOf(0x75).stream().filter(p -> p.subtype() == 0x80).count(),
                "$75:$80 rotator with six segments");
        assertEquals(2, placementsOf(0x75).stream().filter(p -> p.subtype() == 0x82).count(),
                "$75:$82 rotator with eight segments");
    }

    /**
     * The two {@code $7D} placements the inventory flags: their raw Y words carry the {@code $1000}
     * wrap bit, so they load at {@code $03C} and {@code $04C} rather than {@code $103C}/{@code $104C}.
     */
    @Test
    void theTwoWrappedBouncyCloudsLoadBelowTheSeam() throws IOException {
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        int wrapped = 0;
        int address = SSZ1_SPRITES;
        while (rom.readU16BE(address) != 0xFFFF) {
            if (rom.readU8(address + 4) == 0x7D && (rom.readU16BE(address + 2) & 0x1000) != 0) {
                wrapped++;
                int loaded = rom.readU16BE(address + 2) & 0xFFF;
                assertTrue(loaded == 0x03C || loaded == 0x04C,
                        "wrapped $7D loads at $03C or $04C, not $" + Integer.toHexString(loaded));
            }
            address += 6;
        }
        assertEquals(2, wrapped, "exactly two $7D placements set the wrap bit");
    }

    /**
     * {@code byte_46698} with {@code byte_4669F} as its eighth byte — the sag ramp
     * {@code sub_45170} walks down as the bounce counter runs out — and {@code byte_466A0}, the
     * forty-byte recoil the launched player feeds back.
     */
    @Test
    void theCloudReadsItsSagAndRecoilRampsFromTheRom() throws IOException {
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        int[] sag = new int[8];
        for (int index = 0; index < 8; index++) {
            sag[index] = rom.readU8(SszBouncyCloudObjectInstance.SAG_TABLE_ADDR + index);
        }
        assertArrayEqualsAsBytes(new int[]{0x00, 0x0A, 0x12, 0x16, 0x17, 0x16, 0x12, 0x0A}, sag,
                "byte_46698 + byte_4669F");
        // The landing writes byte_4669F, i.e. index 7, and the countdown then reads 6 down to 1.
        assertEquals(0x0A, sag[SszBouncyCloudObjectInstance.BOUNCE_FRAMES],
                "the landing sag is byte_4669F");
        assertEquals(0x17, sag[4], "the deepest sag is byte_46698[4]");

        // The recoil index starts at $26 and the table has $28 entries; index $27 is never read.
        assertEquals(0x26, SszBouncyCloudObjectInstance.RECOIL_START_INDEX, "move.b #$26,1(a2)");
        assertEquals(-7, (byte) rom.readU8(
                        SszBouncyCloudObjectInstance.RECOIL_TABLE_ADDR + 0x26),
                "byte_466A0[$26], the first recoil the cloud shows");
        assertEquals(0, (byte) rom.readU8(
                        SszBouncyCloudObjectInstance.RECOIL_TABLE_ADDR),
                "byte_466A0[0], where the index sticks");
    }

    /**
     * {@code word_466C8}: four rows of X offset, Y offset, X velocity and Y velocity, and
     * {@code loc_452DA}'s window check, which is what makes a cloud shed fewer than four puffs
     * when the player bounces off its end.
     */
    @Test
    void thePuffRowsAndTheirWindowCheckComeFromTheRom() throws IOException {
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        int base = SszBouncyCloudObjectInstance.PUFF_TABLE_ADDR;
        assertEquals(4, SszBouncyCloudObjectInstance.PUFF_COUNT, "moveq #4-1,d2");
        int[][] expected = {
                {-0x0C, -0x10, -0x2C0, -0x400},
                {0x0C, -0x10, 0x2C0, -0x400},
                {-0x10, -0x0C, -0x500, -0x200},
                {0x10, -0x0C, 0x500, -0x200},
        };
        for (int row = 0; row < 4; row++) {
            for (int word = 0; word < 4; word++) {
                assertEquals(expected[row][word],
                        (short) rom.readU16BE(base + row * 8 + word * 2),
                        "word_466C8 row " + row + " word " + word);
            }
        }
        // A leftward puff survives while the cloud's X minus $18 is at or below it.
        int cloudX = 0x0740;
        assertTrue(SszBouncyCloudPuffObjectInstance.survivesWindow(cloudX, cloudX - 0x0C, -0x2C0),
                "a puff just left of centre survives");
        assertFalse(SszBouncyCloudPuffObjectInstance.survivesWindow(cloudX, cloudX - 0x30, -0x2C0),
                "a leftward puff further than $18 out is deleted on its first pass");
        assertTrue(SszBouncyCloudPuffObjectInstance.survivesWindow(cloudX, cloudX + 0x0C, 0x2C0),
                "a puff just right of centre survives");
        assertFalse(SszBouncyCloudPuffObjectInstance.survivesWindow(cloudX, cloudX + 0x30, 0x2C0),
                "a rightward puff further than $18 out is deleted on its first pass");
    }

    /**
     * {@code loc_460A6}: {@code (((angle + $A) & $FF) * 3) >> 5} masked even. Multiplying by three
     * and shifting five is not an even twelfth, so this pins the uneven row boundaries rather than
     * a divide the ROM never does.
     */
    @Test
    void theCarriedPlayerPoseIndexIsTheRomsUnevenTwelfth() throws IOException {
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        int rows = 0;
        int previous = -1;
        for (int angle = 0; angle < 256; angle++) {
            int offset = SszCarriedPlayerPose.rowOffset(angle);
            assertTrue(offset >= 0 && offset < SszCarriedPlayerPose.ROW_COUNT * 2,
                    "angle " + angle + " selects row byte offset " + offset);
            assertEquals(0, offset & 1, "the row offset is always even");
            if (offset != previous) {
                rows++;
                previous = offset;
            }
        }
        // The cycle starts partway into row 0 and wraps back into it, so the walk sees 13 changes.
        assertEquals(13, rows, "twelve rows, entered once each plus the wrap back into row 0");
        // ((($F5 + $A) & $FF) * 3) >> 5 = 23, masked even = 22, the last row's byte offset.
        assertEquals(0x16, SszCarriedPlayerPose.rowOffset(0xF5), "angle $F5 selects the last row");
        // The bias wraps $F6 back to zero, so the cycle re-enters row 0 before the angle does.
        assertEquals(0x00, SszCarriedPlayerPose.rowOffset(0xF6), "angle $F6 wraps to row 0");
        // byte_468C4's first row is the one the ROM's own listing shows.
        assertEquals(0x01, rom.readU8(SszCarriedPlayerPose.TABLE_ADDR), "byte_468C4[0] render flags");
        assertEquals(0x55, rom.readU8(SszCarriedPlayerPose.TABLE_ADDR + 1), "byte_468C4[1] frame");
    }

    /**
     * {@code byte_468DC}: the retracting spring's {@code SolidObjSloped2} profile, one signed byte
     * per two pixels. The first seventeen samples are flat at {@code $11} and the tail falls to
     * {@code -$C}, which is what makes the spring's top a ledge rather than a ramp.
     */
    @Test
    void theRetractingSpringSolidProfileComesFromTheRom() throws IOException {
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        int base = SszRetractingSpringObjectInstance.HEIGHT_TABLE_ADDR;
        assertEquals(36, SszRetractingSpringObjectInstance.HEIGHT_TABLE_BYTES,
                "36 samples covers the $23 half-width at two pixels each");
        for (int index = 0; index < 17; index++) {
            assertEquals(0x11, (byte) rom.readU8(base + index),
                    "byte_468DC[" + index + "] is flat");
        }
        assertEquals(0x10, (byte) rom.readU8(base + 17), "the profile starts to fall at 17");
        assertEquals(-0x0C, (byte) rom.readU8(base + 35), "byte_468DC[35]");
        assertEquals(0x0C00, SszRetractingSpringObjectInstance.LAUNCH_VEL, "move.w #$C00,d1");
    }

    /**
     * {@code loc_450EC}: the cloud sags on the ramp and then throws the player. The bounce counter
     * is {@code routine(a0)}, shared between players, and {@code loc_451DC} sets {@code y_vel} to
     * {@code -$700} — asserted through the player's rise, not just the field, because a launch that
     * writes the field and is immediately overwritten looks identical otherwise.
     */
    @Test
    void standingOnABouncyCloudSagsAndThenThrowsThePlayerUpward() {
        // $7D:$00 at ($DC0,$B78), reached from the ledge above it.
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x0DC0, 0x0B55);
        SszBouncyCloudObjectInstance cloud = null;
        for (int frame = 0; frame < 240 && cloud == null; frame++) {
            fixture.stepIdleFrames(1);
            cloud = active(SszBouncyCloudObjectInstance.class);
        }
        assertNotNull(cloud, "a $7D cloud loads near ($DC0,$B78)");

        // Several clouds are in the window; take whichever one the player actually lands on.
        int landedAt = -1;
        for (int frame = 0; frame < 900 && landedAt < 0; frame++) {
            fixture.stepIdleFrames(1);
            for (SszBouncyCloudObjectInstance candidate
                    : allActive(SszBouncyCloudObjectInstance.class)) {
                if (candidate.stateForTest(0) != 0) {
                    cloud = candidate;
                    landedAt = frame;
                    break;
                }
            }
        }
        assertTrue(landedAt >= 0, "the player lands on a cloud");

        // sub_45170 writes routine(a0) and the player's countdown from the same value, so while
        // one player rides they stay equal, and every frame's sag is byte_46698 at that index.
        List<Integer> sagRamp = new ArrayList<>();
        boolean launched = false;
        for (int frame = 0; frame < 60 && !launched; frame++) {
            int state = cloud.stateForTest(0);
            if (state > 0) {
                assertEquals(state, cloud.bounceCounterForTest(),
                        "routine(a0) is the same countdown the single rider holds");
                assertEquals(cloud.sagByte(state), cloud.sagForTest(0) & 0xFF,
                        "6(a2) is byte_46698 at the countdown index");
                sagRamp.add(cloud.sagForTest(0) & 0xFF);
            }
            fixture.stepIdleFrames(1);
            launched = cloud.stateForTest(0) < 0;
        }
        assertTrue(launched, "the countdown reaches zero and loc_451DC fires");
        assertEquals(0x17, sagRamp.stream().mapToInt(Integer::intValue).max().orElse(0),
                "the sag ramp peaks at byte_46698[4]");
        assertEquals(0x0A, sagRamp.get(sagRamp.size() - 1),
                "the last frame before the throw is back to byte_46698[1]");
        assertEquals(SszBouncyCloudObjectInstance.RECOIL_START_INDEX, cloud.recoilIndexForTest(0),
                "move.b #$26,1(a2)");
        assertEquals(0, cloud.sagForTest(0), "clr.b 6(a2) at the throw");

        // The throw is real motion, not just a field write: y_vel -$700 lifts the player clear of
        // the cloud within a handful of frames and the recoil index counts down as it does.
        int startY = playerCentreY();
        int highestRise = 0;
        for (int frame = 0; frame < 30; frame++) {
            fixture.stepIdleFrames(1);
            highestRise = Math.max(highestRise, startY - playerCentreY());
        }
        assertTrue(highestRise >= 0x20,
                "y_vel -$700 lifts the player at least $20 px, rose " + highestRise);
        assertTrue(cloud.recoilIndexForTest(0) < SszBouncyCloudObjectInstance.RECOIL_START_INDEX,
                "loc_4527A walks byte_466A0 down while the player is airborne");
    }

    /**
     * {@code loc_45400}: the bar catches a player from above and holds them at
     * {@code y_pos(a0) + $14}. Player 2's offset and the Tails-alone case are {@code $11}.
     */
    @Test
    void theElevatorBarCatchesThePlayerAndHangsThemAtTheRomOffset() {
        // $7A:$00 at ($6C0,$568).
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x6C0, 0x520);
        SszElevatorBarObjectInstance bar = null;
        for (int frame = 0; frame < 240 && bar == null; frame++) {
            fixture.stepIdleFrames(1);
            bar = active(SszElevatorBarObjectInstance.class);
        }
        assertNotNull(bar, "$7A:$00 at ($6C0,$568)");
        assertEquals(0x180, bar.priorityWordForTest(), "move.w #$180,priority(a0)");

        int caughtAt = -1;
        for (int frame = 0; frame < 600 && caughtAt < 0; frame++) {
            fixture.stepIdleFrames(1);
            if (bar.holdingForTest(0)) {
                caughtAt = frame;
            }
        }
        assertTrue(caughtAt >= 0, "the bar catches the falling player");
        assertEquals(0x14, SszElevatorBarObjectInstance.HANG_OFFSET_LEADER, "moveq #$14,d1");
        assertEquals(0x11, SszElevatorBarObjectInstance.HANG_OFFSET_OTHER, "moveq #$11,d1");
        for (int frame = 0; frame < 20; frame++) {
            fixture.stepIdleFrames(1);
            assertEquals((bar.getY() + SszElevatorBarObjectInstance.HANG_OFFSET_LEADER) & 0xFFFF,
                    playerCentreY(),
                    "loc_4548A rewrites y_pos(a1) from the bar every frame");
        }
    }

    /**
     * {@code Obj_SSZRotatingPlatform} allocates the invisible carrier at {@code loc_45F10} in its
     * init, {@code $30} pixels below itself and {@code $60} or {@code $A0} wide by subtype bit 0.
     */
    @Test
    void theRotatingPlatformAllocatesItsCarrierWithTheSubtypeWidth() {
        // $76:$00 at ($C00,$800).
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x0C00, 0x780);
        SszRotatingPlatformObjectInstance post = null;
        for (int frame = 0; frame < 240 && post == null; frame++) {
            fixture.stepIdleFrames(1);
            post = active(SszRotatingPlatformObjectInstance.class);
        }
        assertNotNull(post, "a $76 post loads near ($C00,$800)");
        SszRotatingPlatformCarrierObjectInstance carrier = post.carrierForTest();
        assertNotNull(carrier, "jsr (AllocateObjectAfterCurrent) in the init");
        assertEquals((post.getY() + SszRotatingPlatformCarrierObjectInstance.Y_OFFSET) & 0xFFFF,
                carrier.getY() & 0xFFFF, "addi.w #$30,y_pos(a0)");
        assertEquals(post.getX() & 0xFFFF, carrier.getX() & 0xFFFF,
                "the carrier inherits the post's X");
        int expectedWidth = (post.getSpawn().subtype() & 1) != 0
                ? SszRotatingPlatformCarrierObjectInstance.WIDE_WIDTH
                : SszRotatingPlatformCarrierObjectInstance.NARROW_WIDTH;
        assertEquals(expectedWidth, carrier.widthPixelsForTest(),
                "btst #0,subtype(a0) picks $60 or $A0");
        assertEquals(0x14, SszRotatingPlatformCarrierObjectInstance.MAX_RADIUS,
                "cmpi.w #$14,4(a3)");
    }

    /**
     * {@code Obj_SSZSwingingCarrier} builds a three-object chain, and the arc's segment count is
     * {@code (subtype & 3) + 6}. The rotator's angle advances one step per frame; the pendulum's
     * is a {@code Gradual_SwingOffset} biased by {@code $41}.
     */
    @Test
    void theSwingingCarrierBuildsItsArcAndRiderBarFromTheSubtype() {
        // $75:$00 at ($D40,$200).
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x0D40, 0x1C0);
        SszSwingingCarrierObjectInstance hub = null;
        for (int frame = 0; frame < 240 && hub == null; frame++) {
            fixture.stepIdleFrames(1);
            hub = active(SszSwingingCarrierObjectInstance.class);
        }
        assertNotNull(hub, "a $75 hub loads near ($D40,$200)");
        SszSwingingCarrierArcObjectInstance arc = hub.arcForTest();
        assertNotNull(arc, "the hub allocates the arc at loc_461A8");
        assertEquals(SszSwingingCarrierArcObjectInstance.BASE_SEGMENTS
                        + (hub.getSpawn().subtype() & 3), arc.segmentCount(),
                "addq.w #6,d0 over subtype & 3");
        assertNotNull(active(SszSwingingCarrierBarObjectInstance.class),
                "the arc allocates the rider bar at loc_46284");

        // The arm moves: the tip is not where it was ten frames ago, and every segment stays
        // within the $68 half-width the init declares.
        int tipX = arc.tipX();
        int tipY = arc.tipY();
        fixture.stepIdleFrames(10);
        assertTrue(arc.tipX() != tipX || arc.tipY() != tipY, "the arm swings");
        for (int index = 0; index < arc.segmentCount(); index++) {
            int dx = Math.abs(((arc.segmentXForTest(index) - arc.getX()) << 16) >> 16);
            int dy = Math.abs(((arc.segmentYForTest(index) - arc.getY()) << 16) >> 16);
            assertTrue(dx <= 0x68 && dy <= 0x68,
                    "segment " + index + " stays inside the arm's declared $68 box");
        }
    }

    /**
     * {@code loc_46452}: the spring only exists while a player is inside its approach box, the
     * extension is gated on {@code btst #0,(Level_frame_counter+1).w}, and below mapping frame 2
     * it parks at {@code x_pos $7FFF} so it is not solid.
     */
    @Test
    void theRetractingSpringIsIntangibleUntilAPlayerApproaches() {
        // $74:$00 at ($A60,$A30).
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x0A60, 0x0A30);
        SszRetractingSpringObjectInstance spring = null;
        for (int frame = 0; frame < 240 && spring == null; frame++) {
            fixture.stepIdleFrames(1);
            spring = active(SszRetractingSpringObjectInstance.class);
        }
        assertNotNull(spring, "$74:$00 at ($A60,$A30)");

        int steps = 0;
        int previousFrame = spring.mappingFrameForTest();
        for (int frame = 0; frame < 600 && spring.mappingFrameForTest() < 3; frame++) {
            if (spring.mappingFrameForTest() < 2) {
                assertEquals(0x7FFF, spring.solidXForTest(),
                        "loc_464CE hands the solid routine $7FFF while the spring is retracted");
                assertEquals(0x0A60, spring.getX() & 0xFFFF,
                        "and puts the real X back, so the placement window still sees it");
            }
            int counterBefore = GameServices.level().getFrameCounter();
            fixture.stepIdleFrames(1);
            int now = spring.mappingFrameForTest();
            if (now != previousFrame) {
                steps++;
                // btst #0,(Level_frame_counter+1).w: the extension only moves on an odd counter.
                assertEquals(1, GameServices.level().getFrameCounter() & 1,
                        "the extension stepped on an even Level_frame_counter (was "
                                + counterBefore + ")");
                previousFrame = now;
            }
        }
        assertEquals(3, spring.mappingFrameForTest(),
                "the spring reaches frame 3 with the player in its box");
        // The 0 -> 1 step lands on the frame the search loop consumed finding the object, so two
        // of the three are observable here; the parity assertion above is what pins the gate.
        assertTrue(steps >= 2, "the extension steps once per odd counter, saw " + steps);
        assertEquals(0x0A60, spring.getX() & 0xFFFF, "an extended spring reports its real X");
    }

    // ---------------------------------------------------------------------------------------
    // Rewind spots. One per family, taken while the object is mid-action rather than at rest:
    // capture, step, capture, restore, compare, replay forward, compare again.
    // ---------------------------------------------------------------------------------------

    /** {@code $7D} mid sag ramp, with the shared bounce counter part-way down. */
    @Test
    void theBouncyCloudSurvivesACaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x0DC0, 0x0B55);
        rewindSpot(fixture, () -> {
            for (SszBouncyCloudObjectInstance cloud
                    : allActive(SszBouncyCloudObjectInstance.class)) {
                if (cloud.stateForTest(0) > 1) {
                    return true;
                }
            }
            return false;
        }, "a cloud mid sag ramp");
    }

    /** {@code $7A} while it is holding the player on its swing. */
    @Test
    void theElevatorBarSurvivesACaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x6C0, 0x520);
        rewindSpot(fixture, () -> {
            SszElevatorBarObjectInstance bar = active(SszElevatorBarObjectInstance.class);
            return bar != null && bar.holdingForTest(0);
        }, "a bar holding the player");
    }

    /** {@code $76} with its invisible carrier allocated — two objects and a reference between them. */
    @Test
    void theRotatingPlatformSurvivesACaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x0C00, 0x780);
        rewindSpot(fixture, () -> {
            SszRotatingPlatformObjectInstance post = active(SszRotatingPlatformObjectInstance.class);
            return post != null && post.carrierForTest() != null;
        }, "a post with its carrier");
    }

    /** {@code $75} with the whole hub -> arc -> rider bar chain up and the arm swinging. */
    @Test
    void theSwingingCarrierChainSurvivesACaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x0D40, 0x1C0);
        rewindSpot(fixture, () -> {
            SszSwingingCarrierObjectInstance hub = active(SszSwingingCarrierObjectInstance.class);
            return hub != null && hub.arcForTest() != null
                    && active(SszSwingingCarrierBarObjectInstance.class) != null;
        }, "the full carrier chain");
    }

    /** {@code $74} part-way through its extension, where the frame-counter gate is live. */
    @Test
    void theRetractingSpringSurvivesACaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x0A60, 0x0A30);
        rewindSpot(fixture, () -> {
            SszRetractingSpringObjectInstance spring =
                    active(SszRetractingSpringObjectInstance.class);
            return spring != null && spring.mappingFrameForTest() >= 1;
        }, "a spring mid extension");
    }

    /**
     * Steps until {@code ready} holds, then captures, steps, captures, restores, compares, replays
     * one frame and compares again. A restore that drops captured state shows up in the first
     * comparison; a restore that leaves the object unable to continue shows up in the second.
     *
     * <p><b>What this cannot see.</b> At these spots the object instances survive the restore in
     * place, so a subclass's {@code restoreRewindState} for an {@code ObjectRefId} sidecar is never
     * exercised: disabling {@code SszRotatingPlatformObjectInstance}'s carrier restore outright
     * leaves both this spot and {@code TestEveryObjectRewindRoundTrip} green. Object-reference
     * restore needs a spot that forces recreation — a cull and reload across the capture — and is
     * recorded as owed in the act-1 matrix rather than claimed here.
     */
    private static void rewindSpot(HeadlessTestFixture fixture,
            java.util.function.BooleanSupplier ready, String what) {
        boolean reached = false;
        for (int frame = 0; frame < 900 && !reached; frame++) {
            fixture.stepIdleFrames(1);
            reached = ready.getAsBoolean();
        }
        assertTrue(reached, "the spot was never reached: " + what);

        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot after = registry.capture();

        registry.restore(before);
        sameSnapshot(before, registry.capture(), "restore at " + what);
        fixture.runner().primeInputState(
                new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        sameSnapshot(after, registry.capture(), "forward replay at " + what);
    }

    private static void sameSnapshot(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label);
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }

    private static void assertArrayEqualsAsBytes(int[] expected, int[] actual, String message) {
        assertEquals(expected.length, actual.length, message + " length");
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], actual[index], message + "[" + index + "]");
        }
    }

    private static int playerCentreY() {
        var player = GameServices.sprites().getMainPlayable();
        return player == null ? 0 : player.getCentreY() & 0xFFFF;
    }

    private static <T> List<T> allActive(Class<T> type) {
        List<T> found = new ArrayList<>();
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return found;
        }
        for (ObjectInstance instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                found.add(type.cast(instance));
            }
        }
        return found;
    }

    private static <T> T active(Class<T> type) {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return null;
        }
        for (ObjectInstance instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                return type.cast(instance);
            }
        }
        return null;
    }

    private static HeadlessTestFixture bootAtCheckpoint(int width, int x, int y) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle()
                .startPosition((short) x, (short) y)
                .startPositionIsCentre()
                .build();
        if (GameServices.level().getCheckpointState() instanceof CheckpointState checkpoint) {
            checkpoint.saveCheckpoint(1, x, y, false);
        }
        return fixture;
    }
}

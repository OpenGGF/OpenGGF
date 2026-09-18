package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.SszBridgeDebrisObjectInstance;
import com.openggf.game.sonic3k.objects.SszCollapsingBridgeObjectInstance;
import com.openggf.game.sonic3k.objects.SszCollapsingColumnDebrisObjectInstance;
import com.openggf.game.sonic3k.objects.SszCollapsingColumnObjectInstance;
import com.openggf.game.sonic3k.objects.SszFloatingPlatformObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_SSZFloatingPlatform} ({@code $7F}, sonic3k.asm:89968-90005) and
 * {@code Obj_SSZCollapsingColumn} ({@code $7E}, 90007-90118) with its {@code loc_44BCC} debris.
 *
 * <p>Expectations come from the routines and from {@code word_46618} ({@code $46618}) decoded from
 * the ROM here, not from the engine's copies.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszTraversalPlatforms {

    /** {@code SSZ1_Sprites}. */
    private static final int SSZ1_SPRITES = 0x1F90EE;
    /** {@code word_46618}: eight rows of X offset, Y offset, hang delay, mapping frame. */
    private static final int DEBRIS_TABLE = 0x046618;
    /** {@code cmpi.w #4,d0}. */
    private static final int MAX_DIP = 4;

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

    /** The inventory rows these two classes take off the placeholder census. */
    @Test
    void theRomPlacesTheCountsTheInventoryClaims() throws IOException {
        assertEquals(8, placementsOf(0x7F).size(), "$7F records in SSZ1_Sprites");
        assertEquals(25, placementsOf(0x7E).size(), "$7E records in SSZ1_Sprites");
        for (Placement placement : placementsOf(0x7F)) {
            assertEquals(0, placement.subtype(), "$7F is subtype 0 everywhere; it is never read");
        }
        for (Placement placement : placementsOf(0x7E)) {
            assertEquals(0, placement.subtype(), "$7E is subtype 0 everywhere");
        }
    }

    /**
     * {@code loc_44AA0}: {@code $2E(a0)} counts up to 4 while a player stands on the platform and
     * back down to 0 afterwards, one pixel per frame, and {@code y_pos = y_vel + $2E}.
     */
    @Test
    void theFloatingPlatformDipsFourPixelsUnderAStandingPlayerAndRises() {
        // $7F:$00 at ($600,$C20), on the stretch the bridge opens.
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x600, 0xBC0);
        SszFloatingPlatformObjectInstance platform = null;
        for (int frame = 0; frame < 240 && platform == null; frame++) {
            fixture.stepIdleFrames(1);
            platform = active(SszFloatingPlatformObjectInstance.class);
        }
        assertNotNull(platform, "$7F:$00 at ($600,$C20)");
        int baseY = platform.getY() & 0xFFFF;
        assertEquals(0xC20, baseY, "y_vel(a0) = y_pos(a0) at init");

        int maxDip = 0;
        for (int frame = 0; frame < 240; frame++) {
            fixture.stepIdleFrames(1);
            maxDip = Math.max(maxDip, (platform.getY() & 0xFFFF) - 0xC20);
        }
        assertEquals(MAX_DIP, maxDip,
                "the dip saturates at 4 px (cmpi.w #4,d0 / bhs.s loc_44ABE)");
        assertTrue((platform.getY() & 0xFFFF) - 0xC20 <= MAX_DIP,
                "y_pos never leaves y_vel..y_vel+4");
    }

    /**
     * {@code loc_44B30}: standing on a column allocates eight {@code word_46618} pieces, and
     * {@code loc_44B90} parks the column at {@code x_pos $7FFF} once every piece has reported.
     */
    @Test
    void theCollapsingColumnBreaksIntoTheEightRomDebrisPiecesAndThenParks() throws IOException {
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        int[] expectedFrames = new int[8];
        for (int piece = 0; piece < 8; piece++) {
            expectedFrames[piece] = rom.readU16BE(DEBRIS_TABLE + piece * 8 + 6);
        }

        // $7E:$00 at ($600,$960).
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x600, 0x900);
        SszCollapsingColumnObjectInstance column = null;
        for (int frame = 0; frame < 240 && column == null; frame++) {
            fixture.stepIdleFrames(1);
            column = active(SszCollapsingColumnObjectInstance.class);
        }
        assertNotNull(column, "$7E:$00 at ($600,$960)");

        int debrisSeen = 0;
        for (int frame = 0; frame < 600 && !column.collapsedForTest(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(column.collapsedForTest(),
                "standing on the column runs loc_44B30");
        debrisSeen = countActive(SszCollapsingColumnDebrisObjectInstance.class);
        assertEquals(SszCollapsingColumnObjectInstance.DEBRIS_COUNT, debrisSeen,
                "moveq #8-1,d2: one piece per word_46618 row");
        // word_46618 row 6 has a hang delay of 1, and the pieces are allocated after the current
        // slot, so that one runs in the same object pass as the collapse: its delay reaches zero
        // immediately and it reports back with subq.b #1,routine(a1) on the collapse frame. Seven
        // is the ROM's count at the end of that frame, not eight.
        int delayOnePieces = 0;
        for (int piece = 0; piece < 8; piece++) {
            if (rom.readU16BE(DEBRIS_TABLE + piece * 8 + 4) == 1) {
                delayOnePieces++;
            }
        }
        assertEquals(1, delayOnePieces, "exactly one word_46618 row hangs for a single frame");
        assertEquals(SszCollapsingColumnObjectInstance.DEBRIS_COUNT - delayOnePieces,
                column.pendingDebrisForTest(),
                "routine(a0) counts the pieces still to report");
        assertEquals(8, expectedFrames.length, "word_46618 rows decoded from the ROM");

        for (int frame = 0; frame < 600 && column.pendingDebrisForTest() > 0; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(column.pendingDebrisForTest() <= 0,
                "every piece reports back with subq.b #1,routine(a1)");
        // The pieces report during their own update pass, which runs after the column's, so
        // loc_44B90's tst.b routine(a0) sees the final zero on the following frame.
        fixture.stepIdleFrames(1);
        assertEquals(0x7FFF, column.getX() & 0xFFFF,
                "loc_44B98 move.w #$7FFF,x_pos(a0)");
    }

    /**
     * {@code Obj_SSZCollapsingBridge} ({@code $7C}): the ROM places it seven times as {@code $00}
     * and once as {@code $80}, and bit 7 is the only subtype bit the routine reads
     * ({@code tst.b subtype(a0)} / {@code bmi.s loc_44C96}) — set means the section never
     * collapses. Bits 0-6 are unread, so the two rows are the whole behaviour space.
     */
    @Test
    void theCollapsingBridgeReadsOnlyBitSevenOfItsSubtype() throws IOException {
        List<Placement> bridges = placementsOf(0x7C);
        assertEquals(8, bridges.size(), "$7C records in SSZ1_Sprites");
        assertEquals(7, bridges.stream().filter(p -> p.subtype() == 0x00).count(),
                "$7C:$00 collapsing sections");
        assertEquals(1, bridges.stream().filter(p -> p.subtype() == 0x80).count(),
                "$7C:$80, the one permanent section");
        for (Placement bridge : bridges) {
            assertTrue(bridge.subtype() == 0x00 || bridge.subtype() == 0x80,
                    "no $7C placement sets a bit the routine never reads: "
                            + Integer.toHexString(bridge.subtype()));
        }
    }

    /**
     * {@code loc_44C9C}-{@code loc_44D22}: standing on a collapsing section lays four
     * {@code loc_45052} pieces and then shrinks the solid half-width from {@code $20} by 8 every
     * sixth frame until the section parks at {@code x_pos $7FFF}.
     */
    @Test
    void theCollapsingBridgeShedsFourPiecesAndShrinksToNothing() {
        // $7C:$00 at ($820,$648).
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x820, 0x5E0);
        SszCollapsingBridgeObjectInstance bridge = null;
        for (int frame = 0; frame < 300 && bridge == null; frame++) {
            fixture.stepIdleFrames(1);
            bridge = active(SszCollapsingBridgeObjectInstance.class);
        }
        assertNotNull(bridge, "$7C:$00 at ($820,$648)");
        assertEquals(0x20, bridge.halfWidthForTest(), "moveq #$20,d1 before the collapse");

        for (int frame = 0; frame < 600 && !bridge.collapsedForTest(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(bridge.collapsedForTest(), "standing on the section runs loc_44C9C");
        assertEquals(SszCollapsingBridgeObjectInstance.DEBRIS_COUNT,
                countActive(SszBridgeDebrisObjectInstance.class),
                "moveq #4-1,d5: four loc_45052 pieces");

        // subq.w #8,$32(a0) every sixth frame: $20 -> 0 is four steps, 24 frames.
        int parkedAt = -1;
        for (int frame = 0; frame < 120; frame++) {
            fixture.stepIdleFrames(1);
            if (bridge.halfWidthForTest() <= 0) {
                parkedAt = frame;
                break;
            }
        }
        assertTrue(parkedAt >= 0, "the section shrinks away");
        assertTrue(parkedAt >= 20 && parkedAt <= 28,
                "four 8-pixel steps at one per six frames is about 24 frames, not " + parkedAt);
        // move.w #$7FFF,x_pos(a0) and then loc_44D3A's add.w d0,x_pos(a0) with d0 = +/-8, so the
        // parked X is $7FF7 or $8007 depending on which side the player stood.
        int parkedX = bridge.getX() & 0xFFFF;
        assertTrue(parkedX >= 0x7FF7 && parkedX <= 0x8007,
                "parked off-level at $7FFF +/- 8, was $" + Integer.toHexString(parkedX));
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

    private static int countActive(Class<?> type) {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return 0;
        }
        int count = 0;
        for (ObjectInstance instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                count++;
            }
        }
        return count;
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

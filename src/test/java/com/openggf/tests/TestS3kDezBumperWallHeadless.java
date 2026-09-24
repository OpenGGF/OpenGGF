package com.openggf.tests;

import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezBumperWallObjectInstance;
import com.openggf.game.sonic3k.objects.S3kDezGravityPuzzleObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SKL {@code $60}, {@code Obj_DEZBumperWall} (sonic3k.asm:95958-96082).
 *
 * <p>Every expected number is a literal from the ROM listing or from the decoded
 * {@code DEZ1_Sprites} layout.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezBumperWallHeadless {

    /** {@code DEZ1_Sprites} record 311: the full-height wall at {@code $2600,$07C0}. */
    private static final int WALL_X = 0x2600;
    private static final int WALL_Y = 0x07C0;
    /** Record 331: the exit gate at {@code $280C,$0820}, subtype {@code $80}. */
    private static final int GATE_X = 0x280C;
    private static final int GATE_Y = 0x0820;
    /** Record 319: the one {@code $61} placement, whose panels the gate reads. */
    private static final int PUZZLE_X = 0x2690;
    private static final int PUZZLE_Y = 0x0840;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /**
     * {@code loc_497C2} :95983-95985 for subtype 0, and {@code loc_49804} :96013-96017 for a
     * positive one, where {@code height_pixels} is the subtype itself (:95968) and
     * {@code d3 = d2 + 1}.
     */
    @Test
    void theSubtypeSignChoosesBetweenAWallAndAPost() {
        HeadlessTestFixture fixture = fixture();
        try {
            var wall = place(WALL_X, WALL_Y, 0x00);
            assertEquals(0x17, wall.getSolidParams().halfWidth(), "d1 = $17");
            assertEquals(0x20, wall.getSolidParams().airHalfHeight(), "d2 = $20");
            assertEquals(0x21, wall.getSolidParams().groundHalfHeight(), "d3 = $21");
            assertFalse(wall.isPostForTest(), "subtype 0 is the beq path");

            var shortPost = place(0x25B8, 0x0718, 0x18);
            assertEquals(0x13, shortPost.getSolidParams().halfWidth(), "d1 = $13");
            assertEquals(0x18, shortPost.getSolidParams().airHalfHeight(), "d2 = height_pixels");
            assertEquals(0x19, shortPost.getSolidParams().groundHalfHeight(), "d3 = d2 + 1");
            assertTrue(shortPost.isPostForTest(), "a positive subtype is the bmi fall-through");

            var tallPost = place(0x2738, 0x0778, 0x38);
            assertEquals(0x38, tallPost.getSolidParams().airHalfHeight(), "records 327/328");
            assertEquals(0x39, tallPost.getSolidParams().groundHalfHeight());
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_497AE} / {@code loc_497B4} :95974-95981. A negative subtype is the full-height
     * wall plus one test: {@code cmpi.b #$3F,(MHZ_pollen_counter).w}, and only all six panel
     * bits move it to {@code $7F00}.
     */
    @Test
    void theGateOnlyOpensOnAllSixPanelBits() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            var gate = place(GATE_X, GATE_Y, 0x80);
            assertTrue(gate.isGateForTest(), "subtype $80 is bmi");
            assertEquals(0x17, gate.getSolidParams().halfWidth(), "it is still the $17 wall");

            gate.update(0, sprite);
            assertFalse(gate.isOpenForTest(), "no panels pressed");
            assertEquals(GATE_X, gate.getX(), "and it stays where it was placed");

            // Five of the six is not $3F, and the ROM compares the whole byte.
            pressPanels(0x1F, sprite);
            gate.update(1, sprite);
            assertFalse(gate.isOpenForTest(), "$1F is bne, so the gate holds");
            assertEquals(GATE_X, gate.getX());

            pressPanels(0x3F, sprite);
            gate.update(2, sprite);
            assertTrue(gate.isOpenForTest(), "$3F is the only value that passes");
            assertEquals(0x7F00, gate.getX(), "move.w #$7F00,x_pos(a0) (:95980)");
            assertFalse(gate.isSolidFor(sprite), "and it is out of every player's reach");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code sub_49848} :96046-96047 plays {@code sfx_Bumper} ({@code $AA}) and falls into
     * {@code loc_49850}, the same launch {@code Obj_DEZGravityPuzzle} uses: {@code x_vel}
     * {@code ±$C00} away from the wall, airborne, {@code ground_vel} 1 negated for a
     * left-facing player, and the endless tumble.
     */
    @Test
    void aPushLaunchesThePlayerAwayFromTheWallAndSoundsTheBumper() {
        HeadlessTestFixture fixture = fixture();
        AudioManager audioManager = AudioManager.getInstance();
        List<Integer> requested = new ArrayList<>();
        try {
            audioManager.setRequestObserver((requestClass, rawSoundId) -> requested.add(rawSoundId));
            AbstractPlayableSprite sprite = fixture.sprite();
            var wall = place(WALL_X, WALL_Y, 0x00);

            moveTo(sprite, WALL_X - 0x18, WALL_Y);
            push(wall, sprite);
            assertEquals(-0xC00, sprite.getXSpeed(), "a player on the left goes further left");
            assertEquals(-1, sprite.getGSpeed(), "neg.w ground_vel(a1) (:96079)");
            assertTrue(sprite.getAir(), "bset #Status_InAir (:96071)");
            assertEquals(-1, (byte) sprite.getFlipsRemaining(), "move.b #-1,flips_remaining");
            assertEquals(4, sprite.getFlipSpeed(), "move.b #4,flip_speed");
            assertEquals(List.of(Sonic3kSfx.BUMPER.id), requested, "sfx_Bumper ($AA) once");

            requested.clear();
            moveTo(sprite, WALL_X + 0x18, WALL_Y);
            sprite.setFlipAngle(0);
            push(wall, sprite);
            assertEquals(0xC00, sprite.getXSpeed(), "and a player on the right goes right");
            assertEquals(1, sprite.getGSpeed());
            assertEquals(List.of(Sonic3kSfx.BUMPER.id), requested);
        } finally {
            audioManager.setRequestObserver(null);
            SessionManager.clear();
        }
    }

    /** {@code swap d6 / andi.w #1|2,d6 / beq} (:95989-95991): a touch that is not a push does nothing. */
    @Test
    void aContactWithoutAPushDoesNothing() {
        HeadlessTestFixture fixture = fixture();
        AudioManager audioManager = AudioManager.getInstance();
        List<Integer> requested = new ArrayList<>();
        try {
            audioManager.setRequestObserver((requestClass, rawSoundId) -> requested.add(rawSoundId));
            AbstractPlayableSprite sprite = fixture.sprite();
            var wall = place(WALL_X, WALL_Y, 0x00);
            moveTo(sprite, WALL_X - 0x18, WALL_Y);
            sprite.setXSpeed((short) 0);
            wall.onSolidContact(sprite, new SolidContact(true, false, false, true, false), 0);
            assertEquals(0, sprite.getXSpeed(), "standing on it is not a push");
            assertEquals(List.of(), requested, "and it is silent");
        } finally {
            audioManager.setRequestObserver(null);
            SessionManager.clear();
        }
    }

    // --- helpers ---

    /** Drives the one act 1 {@code $61} placement until the requested panel bits are set. */
    private void pressPanels(int bits, AbstractPlayableSprite sprite) {
        ObjectSpawn spawn = new ObjectSpawn(PUZZLE_X, PUZZLE_Y, 0x61, 0, 0, false, PUZZLE_Y, -1);
        S3kDezGravityPuzzleObjectInstance puzzle = new S3kDezGravityPuzzleObjectInstance(spawn);
        GameServices.level().getObjectManager().addDynamicObject(puzzle);
        int[][] offsets = { {-1, -0x40}, {-1, 0x00}, {-1, 0x10}, {0, -0x40}, {0, 0x00}, {0, 0x10} };
        for (int bit = 0; bit < offsets.length; bit++) {
            if ((bits & (1 << bit)) == 0) {
                continue;
            }
            moveTo(sprite, PUZZLE_X + offsets[bit][0], PUZZLE_Y + offsets[bit][1]);
            puzzle.onSolidContact(sprite, new SolidContact(false, true, false, false, true), 0);
        }
        assertEquals(bits, puzzle.panelBitsForTest(), "precondition: the panel bitfield");
    }

    private void push(S3kDezBumperWallObjectInstance wall, AbstractPlayableSprite sprite) {
        wall.onSolidContact(sprite, new SolidContact(false, true, false, false, true), 0);
    }

    private S3kDezBumperWallObjectInstance place(int x, int y, int subtype) {
        ObjectSpawn spawn = new ObjectSpawn(x, y, 0x60, subtype, 0, false, y, -1);
        S3kDezBumperWallObjectInstance wall = new S3kDezBumperWallObjectInstance(spawn);
        GameServices.level().getObjectManager().addDynamicObject(wall);
        return wall;
    }

    private void moveTo(AbstractPlayableSprite sprite, int centreX, int centreY) {
        NativePositionOps.writeXPosResetSubpixel(sprite, centreX);
        NativePositionOps.writeYPosResetSubpixel(sprite, centreY);
    }

    private HeadlessTestFixture fixture() {
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .build();
    }
}

package com.openggf.game.sonic1.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.constants.Sonic1AnimationIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/** BossFinal_Eggman_Escape writes the logical pad after Sonic's object slot. */
@RequiresRom(SonicGame.SONIC_1)
class TestS1FzEscapeNativeInput {
    private RomByteReader rom;

    @BeforeEach
    void setup() throws Exception {
        rom = RomByteReader.fromRom(TestEnvironment.currentRom());
    }

    @AfterEach
    void cleanup() {
        SessionManager.clear();
    }

    @Test
    void shippedEscapeWritesTheWholePadWordAndBranchesPastTheLedgeOnNonnegativeVelocity() {
        // Complete .watchEggman -> .ledge -> .ending sequence from REV01.
        // BPL's target is $1A9FE, after the MOVE.W into player obX, rather
        // than merely after the forced-Up write. F602 is held/pressed together.
        byte[] expected = {
                0x0C, 0x78, 0x27, (byte) 0x90, (byte) 0xD0, 0x08, 0x6D, 0x1C,
                0x11, (byte) 0xFC, 0x00, 0x01, (byte) 0xF7, (byte) 0xCC,
                0x31, (byte) 0xFC, 0x00, 0x00, (byte) 0xF6, 0x02,
                0x42, 0x78, (byte) 0xD0, 0x14,
                0x4A, 0x68, 0x00, 0x12, 0x6A, 0x14,
                0x31, (byte) 0xFC, 0x01, 0x00, (byte) 0xF6, 0x02,
                0x0C, 0x78, 0x27, (byte) 0xE0, (byte) 0xD0, 0x08, 0x6D, 0x06,
                0x31, (byte) 0xFC, 0x27, (byte) 0xE0, (byte) 0xD0, 0x08,
                0x0C, 0x68, 0x29, 0x00, 0x00, 0x08, 0x65, 0x10,
                0x4A, 0x28, 0x00, 0x01, 0x6B, 0x0A,
                0x11, (byte) 0xFC, 0x00, 0x18, (byte) 0xF6, 0x00,
                0x60, 0x00, (byte) 0xFA, (byte) 0xEE
        };
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i] & 0xFF, rom.readU8(0x1A9CC + i),
                    "native escape instruction byte at +" + i);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-0x18, 0, 0x60})
    void playerBeforeTheNativeThresholdRetainsItsOwnControllerWord(int bossYVelocity) throws Exception {
        // The native BLT comparisons treat $8000 and $FFFF as negative X,
        // rather than allowing an unsigned view to lock and clamp the player.
        for (int playerX : new int[] {0x278F, 0x8000, 0xFFFF}) {
            TestablePlayableSprite player = player(playerX);
            Sonic1FZBossInstance boss = boss(bossYVelocity);

            finalFlight(boss, player);

            assertFalse(player.isControlLocked());
            assertEquals(AbstractPlayableSprite.INPUT_RIGHT, player.getForcedInputMask());
            assertEquals(AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP,
                    player.getLogicalInputState());
            assertTrue(player.isLogicalJumpPressActive());
            assertTrue(player.getJumpPressHistory(0));
            assertEquals(0x400, player.getGSpeed());
            assertEquals(playerX, player.getCentreX() & 0xFFFF);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-0x18, 0xFFFF, 0, 0x60})
    void lockPublishesUpOnlyForASignedNegativeShipVelocity(int bossYVelocity) throws Exception {
        TestablePlayableSprite player = player(0x2790);

        finalFlight(boss(bossYVelocity), player);

        int expected = (short) bossYVelocity < 0 ? AbstractPlayableSprite.INPUT_UP : 0;
        assertTrue(player.isControlLocked());
        assertEquals(expected, player.getForcedInputMask());
        assertEquals(expected, player.getLogicalInputState());
        assertEquals(expected, player.getInputHistory(0));
        assertFalse(player.isLogicalJumpPressActive(), "MOVE.W clears the pressed byte even when Up is held");
        assertFalse(player.getJumpPressHistory(0));
        assertEquals(0, player.getGSpeed());
        assertEquals(0x255, player.getXSpeed(), "CLR.W inertia must not clear airborne X velocity");
        assertTrue(player.isDownPressed(), "the late logical word store leaves the raw controller untouched");
        assertTrue(player.isRawControllerJumpJustPressed());
        assertEquals(0x2790, player.getCentreX() & 0xFFFF);
    }

    @ParameterizedTest
    @ValueSource(ints = {-0x18, 0, 0x60})
    void ledgeClampRunsOnlyOnTheRisingBranchAndPreservesTheFractionalWord(int bossYVelocity)
            throws Exception {
        for (int playerX : new int[] {0x27DF, 0x27E0, 0x27E1}) {
            TestablePlayableSprite player = player(playerX);
            player.setSubpixelRaw(0xA53C, 0x789A);

            finalFlight(boss(bossYVelocity), player);

            int expectedX = bossYVelocity < 0 && playerX >= 0x27E0 ? 0x27E0 : playerX;
            assertEquals(expectedX, player.getCentreX() & 0xFFFF);
            assertEquals(0xA53C, player.getXSubpixelRaw(), "native MOVE.W never overwrites the X fraction");
            assertEquals(0x789A, player.getYSubpixelRaw());
        }
    }

    @Test
    void lateUpSurvivesTheNextProductionPlayerDispatchAndRestoreThenForward() throws Exception {
        // Use an ordinary loaded, grounded Sonic to independently consume the
        // boss's logical word. Arena geometry is irrelevant to this controller
        // regression; return to that real floor after evaluating the FZ X gate.
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        AbstractPlayableSprite sonic = fixture.sprite();
        short floorX = sonic.getCentreX();
        sonic.setCentreX((short) 0x2790);
        Sonic1FZBossInstance boss = boss(-0x18);
        finalFlight(boss, sonic);
        sonic.setCentreX(floorX);
        sonic.setAnimationId(Sonic1AnimationIds.WAIT);
        assertFalse(sonic.getAir(), "the production player starts on a real ROM-backed floor");
        var checkpoint = sonic.captureRewindState();

        InputHandler input = new InputHandler();
        PlayerInputState raw = PlayerInputState.of(AbstractPlayableSprite.INPUT_LEFT,
                AbstractPlayableSprite.INPUT_LEFT, 0x04, 0x04, false, false);
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(raw, PlayerInputState.neutral()));
        GameServices.sprites().update(input);
        assertLookUp(sonic);
        assertEquals(raw, input.logical().player1(), "forced logical input must leave the raw pad intact");
        assertTrue(sonic.isLeftPressed());
        assertTrue(sonic.isRawControllerJumpJustPressed());
        int mapping = sonic.getMappingFrame();
        // Ani_Sonic index $07 is SonAni_LookUp: $3F,$05,$FF in REV01.
        int lookUpScript = 0x1421C + rom.readU16BE(0x1421C + Sonic1AnimationIds.LOOK_UP.id() * 2);
        assertEquals(0x3F, rom.readU8(lookUpScript));
        assertEquals(5, rom.readU8(lookUpScript + 1));
        assertEquals(0xFF, rom.readU8(lookUpScript + 2));
        assertEquals(rom.readU8(lookUpScript + 1), mapping,
                "the next production player slot selects the native Look Up mapping");

        sonic.setCentreX((short) 0x2790);
        boss.getState().yVel = 0;
        finalFlight(boss, sonic);
        assertEquals(0, sonic.getForcedInputMask(), "the falling/neutral branch replaces a prior forced Up");
        assertEquals(0, sonic.getLogicalInputState());
        assertFalse(sonic.isLogicalJumpPressActive());

        sonic.restoreRewindState(checkpoint);
        GameServices.sprites().update(input);
        assertLookUp(sonic);
        assertEquals(mapping, sonic.getMappingFrame(), "restored controller and animation clocks replay identically");
    }

    private static void assertLookUp(AbstractPlayableSprite player) {
        assertTrue(player.isControlLocked());
        assertEquals(AbstractPlayableSprite.INPUT_UP, player.getForcedInputMask());
        assertEquals(AbstractPlayableSprite.INPUT_UP, player.getLogicalInputState());
        assertEquals(AbstractPlayableSprite.INPUT_UP, player.getInputHistory(0));
        assertFalse(player.isLogicalJumpPressActive());
        assertFalse(player.getJumpPressHistory(0));
        assertEquals(Sonic1AnimationIds.LOOK_UP.id(), player.getAnimationId());
        assertFalse(player.getAir(), "the raw jump edge is not a logical jump under the lock");
    }

    private static TestablePlayableSprite player(int x) {
        var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) x);
        player.setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT);
        player.writeLogicalInputAndCurrentFollowerHistory(
                AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP, true);
        player.setDirectionalInputPressed(false, true, false, false);
        player.setJumpInputPressed(true, true);
        player.setGSpeed((short) 0x400);
        player.setXSpeed((short) 0x255);
        return player;
    }

    private Sonic1FZBossInstance boss(int yVelocity) {
        var boss = new Sonic1FZBossInstance(new ObjectSpawn(0, 0, 0x85, 0, 0, false, 0));
        boss.setServices(new TestObjectServices() {
            @Override public RomByteReader romReader() { return rom; }
        }.withCamera(new Camera()));
        boss.getState().routineSecondary = 14;
        boss.getState().yVel = yVelocity;
        return boss;
    }

    private static void finalFlight(Sonic1FZBossInstance boss, AbstractPlayableSprite player)
            throws Exception {
        Method method = Sonic1FZBossInstance.class.getDeclaredMethod(
                "updateFinalFlight", AbstractPlayableSprite.class, int.class);
        method.setAccessible(true);
        method.invoke(boss, player, 1);
    }
}

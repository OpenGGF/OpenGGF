package com.openggf.game.sonic3k.specialstage;

import com.openggf.audio.AudioManager;
import com.openggf.tests.reel.S3kTestReelPresentation;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;

import static com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TASVideos 3521M / 5639S: turning on red, bumper abuse and emerald positioning.
 * Exercises the production manager after an explicitly seeded local entry boundary;
 * no emulator input import, rendering, ROM loading, or movie synchronization claim.
 * Native oracle is pinned skdisasm 8b2cf3c4, FixBugs=0:
 * sub_9580 (sonic3k.asm:11950-12115), loc_97AA (sonic3k.asm:12167-12171),
 * loc_84C2 (sonic3k.asm:10773-10789), sub_9B62 (sonic3k.asm:12564-12625),
 * including loc_9BA6. See the S3K TAS obligation matrix for missing routes.
 */
@ExtendWith(SingletonResetExtension.class)
class TestTasS3kBlueSphereMotion {
    @BeforeEach
    void configureNoDeviceServices() {
        TestEnvironment.resetAll();
        AudioManager.getInstance().setBackend(new AudioTestFixtures.RecordingAudioBackend());
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void latchedTurnSuppressesRedCollisionUntilCompletionMovesOffAlignment() {
        try (var reel = S3kTestReelPresentation.open()) {
        var manager = managerAt(0x1000, 0x1000, 0x1000);
        var player = manager.getPlayer();
        // Entry boundary: a previous input has latched left at a cell center.
        // This isolates the collision tail; it does not pretend to reproduce
        // the publication's jump/subpixel sequence that reaches this boundary.
        set(player, "turning", 4);
        manager.getGrid().setCell(16, 16, CELL_RED);

        for (int update = 1; update <= 15; update++) {
            manager.update(); reel.afterBlueSphere(manager);
            assertEquals(4 * update, player.getAngle());
            assertEquals(0x1000, player.getXPos());
            assertEquals(0x1000, player.getYPos());
            assertFalse(player.reachedCellCheck(), "sub_9580 returns during rotation");
            assertFalse(player.isFailed(), "manager must honor that return over a red sphere");
        }
        manager.update(); reel.afterBlueSphere(manager);
        assertEquals(0x40, player.getAngle());
        assertEquals(0x0FF0, player.getXPos(), "completed turn moves on this same update");
        assertEquals(0x1000, player.getYPos());
        assertTrue(player.reachedCellCheck());
        assertFalse(player.isFailed(), "$E0 alignment mask rejects the moved position");
        assertEquals(CELL_RED, manager.getGrid().getCell(16, 16), "red was avoided, not erased");
            }
    }

    @Test
    void alignedRedWithoutTurnFailsThroughTheManager() {
        try (var reel = S3kTestReelPresentation.open()) {
        var manager = managerAt(0x1000, 0x1000, 0);
        manager.getGrid().setCell(16, 16, CELL_RED);

        manager.update(); reel.afterBlueSphere(manager);

        assertTrue(manager.getPlayer().reachedCellCheck());
        assertTrue(manager.getPlayer().isFailed(), "positive collision control for the turn test");
        assertEquals(1, manager.getPlayer().getFadeTimer());
            }
    }

    @Test
    void bumperCollisionReversesDisplacementButRetainsStoredVelocity() {
        try (var reel = S3kTestReelPresentation.open()) {
        var manager = managerAt(0x1000, 0x1010, 0x1000);
        var player = manager.getPlayer();
        manager.getGrid().setCell(16, 16, CELL_BUMPER);
        manager.update(); reel.afterBlueSphere(manager);
        assertEquals(0x1000, player.getYPos());
        assertTrue(player.isBumperLocked(), "real bumper collision must arm the lock");
        manager.update(); reel.afterBlueSphere(manager);
        assertEquals(0x1010, player.getYPos(), "loc_96F8 negates the position operand");
        assertEquals(0x1000, player.getVelocity(), "stored velocity is unchanged on same-cell bounce");
        assertTrue(player.isBumperLocked());
            }
    }

    @Test
    void bumperUnlockSkipsMovementAndRedCollisionForExactlyOneUpdate() {
        try (var reel = S3kTestReelPresentation.open()) {
        var manager = managerAt(0x1000, 0x1100, 0x1000);
        var player = manager.getPlayer();
        // Local entry to loc_96CE: retained bumper index belongs to the
        // previous cell; current position has reached the next aligned cell.
        player.activateBumperLock(16 * 32 + 16);
        manager.getGrid().setCell(16, 17, CELL_RED);
        manager.update(); reel.afterBlueSphere(manager);
        assertFalse(player.isBumperLocked());
        assertEquals(-0x1000, player.getVelocity());
        assertEquals(0x1100, player.getYPos(), "loc_96CE writes velocity then returns");
        assertFalse(player.reachedCellCheck());
        assertFalse(player.isFailed(), "unlock must not collide with the aligned red cell");

        manager.update(); reel.afterBlueSphere(manager);
        assertEquals(0x1110, player.getYPos());
        assertTrue(player.reachedCellCheck());
        assertTrue(player.isFailed(), "next update resumes collision; $1110 is still aligned");
            }
    }

    @ParameterizedTest
    @CsvSource({"0x10DF, 0x0070, 0x087F", "0x10E0, 0x0090, 0x0780"})
    void lastSphereStarts85UpdateDelayAndEmeraldRoundingBoundary(
            int startY, int expectedEmeraldIndex, int expectedDistance) {
        try (var reel = S3kTestReelPresentation.open()) {
        var manager = managerAt(0x1000, startY, 0x1000);
        // Both starting fractions round into the same final sphere after the
        // first 16-subpixel step. The one-subpixel difference later crosses
        // emerald placement's add-$80 / shift-8 rounding boundary.
        manager.getGrid().setCell(16, 17, CELL_BLUE);
        set(manager, "spheresLeft", 1);
        manager.update(); reel.afterBlueSphere(manager);
        assertEquals(0, manager.getSpheresLeft(), "last sphere must be collected by the live queue");
        assertEquals(1, manager.getClearRoutine());
        assertEquals(2, manager.getClearTimer());
        assertFalse(manager.hasEmeraldCollected());

        for (int afterTouch = 1; afterTouch <= 84; afterTouch++) {
            manager.update(); reel.afterBlueSphere(manager);
            assertEquals(1, manager.getClearRoutine(), "emerald must not appear early");
            assertEquals(0x1000, manager.getPlayer().getVelocity());
        }
        assertEquals(0x101, manager.getClearTimer(), "2/3/4-step native timer reaches 257");
        manager.update(); reel.afterBlueSphere(manager);
        assertEquals(2, manager.getClearRoutine(), "85th update after touch places the emerald");
        assertEquals(0x800, manager.getPlayer().getVelocity());
        int emeraldY = (expectedEmeraldIndex / 32) * 0x100;
        assertEquals(expectedDistance, (manager.getPlayer().getYPos() - emeraldY) & 0x1FFF);
        assertEquals(CELL_CHAOS_EMERALD, manager.getGrid().getCellByIndex(expectedEmeraldIndex));
        int nonEmpty = 0;
        for (int i = 0; i < 1024; i++) {
            if (manager.getGrid().getCellByIndex(i) != CELL_EMPTY) nonEmpty++;
        }
        assertEquals(1, nonEmpty, "placement clears the old grid, leaving exactly the emerald");
            }
    }

    private static Sonic3kSpecialStageManager managerAt(int x, int y, int velocity) {
        var manager = new Sonic3kSpecialStageManager();
        // Supply only the local entry prerequisites. Production update owns
        // every transition and observation below; presentation/entry fades and
        // art queues are deliberately outside this component fixture.
        set(manager, "initialized", true);
        set(manager, "stagePalettesUploaded", true);
        set(manager, "playerCharacter", PlayerCharacter.KNUCKLES);
        manager.getBanner().initialize();
        manager.getPlayer().initialize(0, x, y, false);
        manager.getPlayer().setVelocity(velocity);
        return manager;
    }

    private static void set(Object owner, String name, Object value) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}

package com.openggf.game.soniccd;

import com.openggf.game.LevelLoadCause;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** Pure LevelStart carry policy, source-derived from Level/Main.asm at pin1ecc5f3a. */
class TestSonicCdRestartCarry {
    @Test void normalRestartKeepsLivesScoreSavedBankHistoryArtAndTheLastReadHeldByte() {
        var f = fixture(); var next = carry(f);
        assertEquals(new CdRingGlobals(0, 2, 0, 0, 0, 0, 0, 0), next.ring());
        assertEquals(new CdOpeningObjectRuntime.ScoreGlobals(0xFFFFFFE1, 0x80000000, 0), next.score());
        assertEquals(new CdPlayerBody.ContactGlobals(0, 0, 0xD5), next.contact());
        // LevelStart :37/:271 set only the two PLC bits; clears/fade write the other values.
        assertEquals(new CdLevelGlobals(0, 0, 0, 0, 0xA3, 0, 2, 0, 0), next.level());
        assertArrayEquals(f.saved().copy(), next.savedObjectFlags().copy());
        assertArrayEquals(f.actor().history().bytes().copy(), next.player().history().copy());
        assertArrayEquals(f.art().cpuBuffer().copy(), next.player().artCpu().copy());
        assertArrayEquals(f.art().residentBank().copy(), next.player().artResident().copy());
        assertEquals(0x81, next.player().debugAngle());
        assertEquals(0x82, next.player().debugAngleShift());
        assertEquals(0x83, next.player().debugQuadrant());
        assertEquals(0x84, next.player().debugFloorDistance());
        assertEquals(0x8012, next.player().debugCheat());
        assertEquals(0x80, next.player().rawHeld());
    }

    @Test void clearFlowersZerosPowerupCpuButClearScreenDoesNotClearItsResidentVram() {
        var f = fixture(); var next = carry(f);
        assertArrayEquals(new byte[0x480], next.powerupArt().cpuBuffer().copy());
        assertArrayEquals(f.powerup().residentBank().copy(), next.powerupArt().residentBank().copy());
        assertEquals(0, next.level().powerup());
    }

    @Test void zeroLivesStopsAtTheNativeProgramExitRatherThanInstallingFreshLives() {
        var f = fixture();
        var ring = new CdRingGlobals(0, 0, 0, 0, 1, 0, 0, 0);
        var failure = assertThrows(SonicCdGameModule.UnsupportedRestart.class, () ->
                SonicCdGameModule.restartCarry(ring, f.level(), f.contact(), f.score(), f.saved(),
                        f.environment(), f.actor(), f.art(), f.powerup(), f.background()));
        assertEquals(SonicCdGameModule.RestartBoundary.GAME_OVER_EXIT, failure.boundary());
    }

    @ParameterizedTest @CsvSource({"restart,2", "checkpoint,1", "spawn,1", "plc,2", "timeOver,1",
            "powerup,2", "powerup,10", "powerup,128", "powerup,138", "zone,256",
            "period,0", "period,2", "period,129", "timeAttack,1"})
    void unownedLevelStartBranchesRejectBeforeAnyOwnerIsRetired(String field, int value) {
        var f = fixture(); var l = f.level(); var e = f.environment(); var m = e.movement();
        var g = m.gates(); var b = e.globals();
        var level = new CdLevelGlobals(field.equals("restart") ? value : l.levelRestart(), l.updateHudTime(),
                field.equals("checkpoint") ? value : l.checkpoint(), field.equals("spawn") ? value : l.spawnMode(),
                field.equals("plc") ? value : l.plcLoadFlags(), l.flowerCount(), l.fadeEnableDisplay(),
                field.equals("powerup") ? value : l.powerup(), field.equals("timeOver") ? value : l.timeOver());
        var gates = new CdPlayerMovement.Gates(field.equals("zone") ? value : g.zoneAct(),
                field.equals("period") ? value : g.timeZone(), g.miniSonic(), g.waterSlide(),
                g.windTunnel(), g.speedShoes(), g.timeWarpDirection());
        var globals = new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(),
                field.equals("timeAttack") ? value : b.timeAttackMode(), b.debugMode(), b.debugCheat(),
                b.timeWarpTimer(), b.invincible(), b.bossMusic());
        var environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(),
                gates, m.globals()), e.controls(), globals, e.dynamicSlots());
        var failure = assertThrows(SonicCdGameModule.UnsupportedRestart.class, () ->
                SonicCdGameModule.restartCarry(f.ring(), level, f.contact(), f.score(), f.saved(),
                        environment, f.actor(), f.art(), f.powerup(), f.background()));
        assertEquals(SonicCdGameModule.RestartBoundary.RESTART_PATH, failure.boundary());
    }

    @Test void savedBankMustBeTheCompleteNativeTwoPlus2fcBytes() {
        var f = fixture();
        for (int size : new int[] {0, CdObjectPlacementRuntime.SAVED_BANK_BYTES - 1,
                CdObjectPlacementRuntime.SAVED_BANK_BYTES + 1}) {
            assertThrows(IllegalArgumentException.class, () -> SonicCdGameModule.restartCarry(f.ring(), f.level(),
                    f.contact(), f.score(), CdAssetBytes.copyOf(new byte[size]), f.environment(),
                    f.actor(), f.art(), f.powerup(), f.background()));
        }
    }

    @Test void merelyRequestingTheDeathLoadCauseCannotForgeAStagedCarry() {
        var context = SonicCdPlayerInitProfile.openingObjectsRestartContext();
        assertEquals(LevelLoadCause.FULL_DEATH_RELOAD, context.getLoadCause());
        var module = SonicCdGameModule.openingObjectsDevelopmentPlayer();
        assertThrows(UnsupportedOperationException.class, () -> module.getLevelInitProfile().levelLoadSteps(context));
    }

    // Native ClearScreen (Functions (General).asm:220-226) clears all HScroll; deformation has no LevelStart clear.
    @Test void restartCarriesDeformationButClearsEveryHscrollByteWithoutASecondStateOwner() {
        var f = fixture(); var next = carry(f);
        assertArrayEquals(f.background().deformationRam().copy(), next.background().deformationRam().copy());
        assertArrayEquals(new byte[1024], next.background().hscrollRam().copy());
        assertEquals(0xA7, next.background().deformationRam().unsignedByte(0));
        assertEquals(0, next.background().hscrollRam().unsignedByte(1023));
        assertEquals(0xCC, f.background().hscrollRam().unsignedByte(1023), "old visit remains unchanged");
        byte[] callerCopy = next.background().hscrollRam().copy(); callerCopy[1023] = (byte) 0xFF;
        assertEquals(0, next.background().hscrollRam().unsignedByte(1023));
    }

    @Test void restartRequiresExplicitCompleteBackgroundBuffersAndNeverDefaultsMissingIngress() {
        var f = fixture();
        for (int length : new int[] {0, 511, 513}) {
            assertThrows(IllegalArgumentException.class, () -> new SonicCdGameModule.BackgroundBuffers(
                    bytes(length, 1), f.background().hscrollRam()));
        }
        for (int length : new int[] {0, 1023, 1025}) {
            assertThrows(IllegalArgumentException.class, () -> new SonicCdGameModule.BackgroundBuffers(
                    f.background().deformationRam(), bytes(length, 1)));
        }
        assertThrows(NullPointerException.class, () -> SonicCdGameModule.restartCarry(f.ring(), f.level(),
                f.contact(), f.score(), f.saved(), f.environment(), f.actor(), f.art(), f.powerup(), null));
        assertThrows(NullPointerException.class, () -> new SonicCdGameModule.BackgroundBuffers(
                null, f.background().hscrollRam()));
        assertThrows(NullPointerException.class, () -> new SonicCdGameModule.BackgroundBuffers(
                f.background().deformationRam(), null));
    }

    private record Fixture(CdRingGlobals ring, CdLevelGlobals level, CdPlayerBody.ContactGlobals contact,
                           CdOpeningObjectRuntime.ScoreGlobals score, CdAssetBytes saved,
                           CdPlayerBody.Environment environment, CdPlayerBody.State actor,
                           CdPlayerArtRuntime.State art, CdPowerupArtRuntime.State powerup,
                           SonicCdGameModule.BackgroundBuffers background) { }
    private static SonicCdGameModule.RestartCarry carry(Fixture f) {
        return SonicCdGameModule.restartCarry(f.ring(), f.level(), f.contact(), f.score(), f.saved(),
                f.environment(), f.actor(), f.art(), f.powerup(), f.background());
    }
    private static Fixture fixture() {
        var motion = new CdPlayerMotionState(0x12345678, 0x76543210, 11, 22, 33, 8, 4, 2, 4,
                9, 19, 0x18, 0x18, 0, 0, 0, 0, 0, 0x11, 0x22);
        var actor = new CdPlayerBody.State(motion, new CdPlayerBody.AnimationCursor(3, 4, 5),
                new CdPlayerBody.Header(1, 0x21F200, 0x8780, 0, 24), new CdPlayerBody.Timers(5, 6, 7, 0),
                new CdPlayerBody.History(bytes(0x100, 7), 0xFC), 0x99, new CdPlayerBody.ObjectLinks(0xA1, 0xFE));
        var movement = new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600, 0xC, 0x80),
                new CdPlayerMovement.Bounds(0, 0x1000, 0x70B, 0),
                new CdPlayerMovement.Gates(0, 1, 0, 0, 0, 0, 0),
                new CdPlayerMovement.Globals(2, 96, 0x12, 0x34, 90, 8, 0x81, 0x82, 0x83, 0x84));
        var environment = new CdPlayerBody.Environment(movement, new CdPlayerBody.Controls(0x80, 0x70, 8, 4, 0),
                new CdPlayerBody.Globals(1112, 160, 0, 0, 0x8012, 0, 0, 0), CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        return new Fixture(new CdRingGlobals(450, 2, 5, 0x80, 0x55, 320, 8, 2),
                new CdLevelGlobals(1, 7, 0, 0, 0xA1, 0x123456, 0xFF, 8, 0),
                new CdPlayerBody.ContactGlobals(0x80, 3, 0xD5),
                new CdOpeningObjectRuntime.ScoreGlobals(0xFFFFFFE1, 0x80000000, 0x80),
                bytes(CdObjectPlacementRuntime.SAVED_BANK_BYTES, 11), environment, actor,
                new CdPlayerArtRuntime.State(bytes(0x300, 0x5A), bytes(0x300, 0x33), 57, true),
                new CdPowerupArtRuntime.State(bytes(0x480, 0x77), bytes(0x480, 0x22)),
                new SonicCdGameModule.BackgroundBuffers(bytes(512, 0xA7), bytes(1024, 0xD3)));
    }
    private static CdAssetBytes bytes(int size, int seed) {
        byte[] data = new byte[size]; for (int i = 0; i < size; i++) data[i] = (byte) (seed + i * 7);
        return CdAssetBytes.copyOf(data);
    }
}

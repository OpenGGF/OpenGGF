package com.openggf.game.soniccd;

import com.openggf.game.LevelLoadCause;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdR11aCameraArtResidency;
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
                        f.environment(), f.actor(), f.art(), f.powerup(), f.background(), cameraArt(), checkpointOwner()));
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
                        environment, f.actor(), f.art(), f.powerup(), f.background(), cameraArt(), checkpointOwner()));
        assertEquals(SonicCdGameModule.RestartBoundary.RESTART_PATH, failure.boundary());
    }

    @Test void savedBankMustBeTheCompleteNativeTwoPlus2fcBytes() {
        var f = fixture();
        for (int size : new int[] {0, CdObjectPlacementRuntime.SAVED_BANK_BYTES - 1,
                CdObjectPlacementRuntime.SAVED_BANK_BYTES + 1}) {
            assertThrows(IllegalArgumentException.class, () -> SonicCdGameModule.restartCarry(f.ring(), f.level(),
                    f.contact(), f.score(), CdAssetBytes.copyOf(new byte[size]), f.environment(),
                    f.actor(), f.art(), f.powerup(), f.background(), cameraArt(), checkpointOwner()));
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
                f.contact(), f.score(), f.saved(), f.environment(), f.actor(), f.art(), f.powerup(), null, cameraArt(), checkpointOwner()));
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

    @Test void checkpointRestartCarriesIncomingBytesUntilTheActualSavedLoadAndKeepsSectionHigh() {
        var f=fixture();var l=f.level();var owner=checkpointOwner();var art=cameraArt();
        var checkpoint=new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),2,1,l.plcLoadFlags() | 2,l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver());
        var next=SonicCdGameModule.restartCarry(f.ring(),checkpoint,f.contact(),f.score(),f.saved(),f.environment(),f.actor(),f.art(),f.powerup(),f.background(),art,owner);
        assertSame(owner,next.checkpointState());assertSame(owner.saved(),next.checkpointState().saved());
        assertEquals(0xDEADBEEF,CdCheckpointLiveAux.read(next.checkpointState().live(),CdCheckpointLiveAux.Field.TIME_LONG));
        assertEquals(0x56,CdCheckpointLiveAux.read(next.checkpointState().live(),CdCheckpointLiveAux.Field.WATER_ROUTINE));
        assertEquals(2,next.level().checkpoint());assertEquals(1,next.level().spawnMode());
        assertEquals(l.updateHudTime(),next.level().updateHudTime(),"load-time LevelScroll sees the carried byte before Main304");
        assertEquals(f.ring(),next.ring(),"LoadCheckpointData, rather than the carry builder, clears rings/livesFlags");
        assertEquals(0xAB12,next.cameraArt().sectionWord(),"checkpoint retains title-loaded path; actual LoadSectionArt owns the next HIGH store");
        assertSame(art.residency(),next.cameraArt().residency());
    }

    @Test void checkpointCarryDoesNotPrereadTheSavedPayloadOrInventItWhenUnknown() {
        var f=fixture();var l=f.level();var checkpoint=new CdLevelGlobals(l.levelRestart(),0,2,1,l.plcLoadFlags() | 2,0,2,0,0);
        var unknown=new CdOpeningObjectRuntime.CheckpointOwnerState(CdCheckpointLiveAux.unknown(),CdCheckpointSaveData.State.unknown());
        var next=SonicCdGameModule.restartCarry(f.ring(),checkpoint,f.contact(),f.score(),f.saved(),f.environment(),f.actor(),f.art(),f.powerup(),f.background(),cameraArt(),unknown);
        assertSame(unknown,next.checkpointState());
        assertThrows(CdCheckpointSaveData.UnknownSavedByte.class,()->next.checkpointState().saved().read(CdCheckpointSaveData.SavedField.SPAWN_MODE));
    }

    @Test void checkpointZeroRestartAlsoCarriesTheSavedAndLiveOwnerUntilTheNewVisitWriters() {
        var f=fixture();var owner=checkpointOwner();
        var next=SonicCdGameModule.restartCarry(f.ring(),f.level(),f.contact(),f.score(),f.saved(),f.environment(),f.actor(),f.art(),f.powerup(),f.background(),cameraArt(),owner);
        assertSame(owner,next.checkpointState());assertEquals(0,next.level().checkpoint());assertEquals(0,next.level().spawnMode());
        assertEquals(0x12,next.cameraArt().sectionWord(),"existing checkpoint0 title-omitted DEV HIGH clear remains explicit");
    }

    @Test void checkpointRestartRejectsWarpBeforeReadingAnyUnknownSavedByteOrRetiringOwners() {
        var f=fixture();var l=f.level();
        var warp=new CdLevelGlobals(l.levelRestart(),0,2,2,l.plcLoadFlags(),0,2,0,0);
        var unknown=new CdOpeningObjectRuntime.CheckpointOwnerState(CdCheckpointLiveAux.unknown(),CdCheckpointSaveData.State.unknown());
        var failure=assertThrows(SonicCdGameModule.UnsupportedRestart.class,()->SonicCdGameModule.restartCarry(f.ring(),warp,f.contact(),f.score(),f.saved(),f.environment(),f.actor(),f.art(),f.powerup(),f.background(),cameraArt(),unknown));
        assertEquals(SonicCdGameModule.RestartBoundary.RESTART_PATH,failure.boundary());
        assertEquals(0,unknown.live().knownMask());
    }

    @Test void checkpointCarryRequiresItsOneExplicitSavedAndLivePartition() {
        var f=fixture();
        assertThrows(NullPointerException.class,()->SonicCdGameModule.restartCarry(f.ring(),f.level(),f.contact(),f.score(),f.saved(),f.environment(),f.actor(),f.art(),f.powerup(),f.background(),cameraArt(),null));
    }

    @Test void checkpointWithoutTitleLoadedBitStopsBeforeReadingUnknownSavedPayload() {
        var f = fixture(); var l = f.level();
        var unknown = new CdOpeningObjectRuntime.CheckpointOwnerState(
                CdCheckpointLiveAux.unknown(), CdCheckpointSaveData.State.unknown());
        for (int flags : new int[] {0, 1, 0xA1, 2}) {
            var checkpoint = new CdLevelGlobals(1, l.updateHudTime(), 2, 1,
                    flags, l.flowerCount(), l.fadeEnableDisplay(), l.powerup(), 0);
            var failure = assertThrows(SonicCdGameModule.UnsupportedRestart.class, () ->
                    SonicCdGameModule.restartCarry(f.ring(), checkpoint, f.contact(), f.score(), f.saved(),
                            f.environment(), f.actor(), f.art(), f.powerup(), f.background(), cameraArt(), unknown));
            assertEquals(SonicCdGameModule.RestartBoundary.RESTART_PATH, failure.boundary());
        }
        assertEquals(0, unknown.live().knownMask());
        assertEquals(0, unknown.saved().knownMask().unsignedByte(0));
    }

    @Test void bothCarryPathsPreservePartialKnowledgeAndDeferTheNewVisitClears() {
        var f = fixture(); var l = f.level();
        byte[] raw = new byte[31]; byte[] mask = new byte[31];
        raw[0] = (byte) 0x81; mask[0] = 1;
        raw[1] = 0x12; mask[1] = 1;
        var saved = CdCheckpointSaveData.State.supplied(CdAssetBytes.copyOf(raw), CdAssetBytes.copyOf(mask));
        var live = CdCheckpointLiveAux.declaredDevIngress(
                CdAssetBytes.copyOf(new byte[] {0, 5, 0, 0, 0, 0, 0x56, 0}), 0x42);
        var owner = new CdOpeningObjectRuntime.CheckpointOwnerState(live, saved);
        for (boolean checkpoint : new boolean[] {false, true}) {
            var level = checkpoint ? new CdLevelGlobals(1, l.updateHudTime(), 2, 1,
                    l.plcLoadFlags() | 2, l.flowerCount(), l.fadeEnableDisplay(), l.powerup(), 0) : l;
            var next = SonicCdGameModule.restartCarry(f.ring(), level, f.contact(), f.score(), f.saved(),
                    f.environment(), f.actor(), f.art(), f.powerup(), f.background(), cameraArt(), owner);
            assertSame(owner, next.checkpointState());
            assertSame(live, next.checkpointState().live());
            assertSame(saved, next.checkpointState().saved());
            assertEquals(0x42, next.checkpointState().live().knownMask());
            assertEquals(0x81, next.checkpointState().saved().read(CdCheckpointSaveData.SavedField.SPAWN_MODE));
            assertThrows(CdCheckpointSaveData.UnknownSavedByte.class,
                    () -> next.checkpointState().saved().read(CdCheckpointSaveData.SavedField.PRIMARY_X));
            assertThrows(CdCheckpointLiveAux.UnknownLiveByte.class,
                    () -> CdCheckpointLiveAux.read(next.checkpointState().live(), CdCheckpointLiveAux.Field.TIME_LONG));
            assertEquals(checkpoint ? f.ring().rings() : 0, next.ring().rings());
            assertEquals(checkpoint ? l.updateHudTime() : 0, next.level().updateHudTime());
        }
    }


    @Test void clearFlowersNewVisitResetsOnlyRingTimersAndTheFirstTailUsesTheClearedPhase() {
        var before=new CdRingGlobals(0xFFFF,0xFF,0xA5,0x80,0x55,0xFFFF,8,2);
        var cleared=SonicCdGameModule.clearFlowersRingGlobals(before);
        assertEquals(new CdRingGlobals(0xFFFF,0xFF,0xA5,0x80,0x55,0,0,0),cleared);
        var first=cleared.advanceGlobalRingAnimation();
        assertEquals(7,first.ringAnimationTimer());assertEquals(1,first.ringAnimationFrame());assertEquals(0,first.timeStopTimer());
        assertEquals(0xFFFF,before.timeStopTimer());assertEquals(8,before.ringAnimationTimer());assertEquals(2,before.ringAnimationFrame());
        assertThrows(NullPointerException.class,()->SonicCdGameModule.clearFlowersRingGlobals(null));
    }

    private static CdOpeningObjectRuntime.CheckpointOwnerState checkpointOwner() {
        return new CdOpeningObjectRuntime.CheckpointOwnerState(CdCheckpointLiveAux.declaredDevIngress(
                CdAssetBytes.copyOf(new byte[]{(byte)0xDE,(byte)0xAD,(byte)0xBE,(byte)0xEF,0x12,0x34,0x56,(byte)0x9A}),0xFF),
                CdCheckpointSaveData.State.unknown());
    }

    private static SonicCdGameModule.RestartCarry carry(Fixture f) {
        return SonicCdGameModule.restartCarry(f.ring(), f.level(), f.contact(), f.score(), f.saved(),
                f.environment(), f.actor(), f.art(), f.powerup(), f.background(), cameraArt(), checkpointOwner());
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
    @Test void restartCarriesCurrentCameraBytesAndNemesisTableButClearsOnlySectionHighByte() {
        var f = fixture(); var input = cameraArt();
        var next = SonicCdGameModule.restartCarry(f.ring(),f.level(),f.contact(),f.score(),f.saved(),
                f.environment(),f.actor(),f.art(),f.powerup(),f.background(),input, checkpointOwner());
        assertSame(input.residency(),next.cameraArt().residency());
        assertArrayEquals(input.codeTable512().copy(),next.cameraArt().codeTable512().copy());
        assertEquals(0x12,next.cameraArt().sectionWord());
        assertEquals(0xAB12,input.sectionWord(),"old ingress unchanged; Main275 addresses only HIGH byte");
        for(int id:new int[]{0x3A0,0x3E5,0x409,0x41E,0x424,0x440,0x47F})
            assertArrayEquals(CdR11aCameraArtResidency.readTile(input.residency(),id).copy(),
                    CdR11aCameraArtResidency.readTile(next.cameraArt().residency(),id).copy());
        var copy=next.cameraArt().codeTable512().copy();copy[511]^=1;
        assertArrayEquals(input.codeTable512().copy(),next.cameraArt().codeTable512().copy());
    }
    @Test void currentCameraRestartRequiresExplicitCompleteIngressWithoutColdFallback() {
        var f=fixture();var input=cameraArt();
        assertThrows(NullPointerException.class,()->SonicCdGameModule.restartCarry(f.ring(),f.level(),f.contact(),f.score(),f.saved(),
                f.environment(),f.actor(),f.art(),f.powerup(),f.background(),null, checkpointOwner()));
        for(int length:new int[]{0,511,513}) assertThrows(IllegalArgumentException.class,()->
                new CdOpeningObjectRuntime.CameraArtRestartIngress(input.residency(),bytes(length,0),0));
        for(int word:new int[]{-1,0x10000}) assertThrows(IllegalArgumentException.class,()->
                new CdOpeningObjectRuntime.CameraArtRestartIngress(input.residency(),input.codeTable512(),word));
        assertThrows(NullPointerException.class,()->new CdOpeningObjectRuntime.CameraArtRestartIngress(null,input.codeTable512(),0));
        assertThrows(NullPointerException.class,()->new CdOpeningObjectRuntime.CameraArtRestartIngress(input.residency(),null,0));
    }
    private static CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt() {
        return new CdOpeningObjectRuntime.CameraArtRestartIngress(CdR11aCameraArtResidency.knownSeed(
                bytes(0x8C0,0x73),bytes(0x2C0,0x81),bytes(0xB80,0x84)),bytes(512,0xC7),0xAB12);
    }

}

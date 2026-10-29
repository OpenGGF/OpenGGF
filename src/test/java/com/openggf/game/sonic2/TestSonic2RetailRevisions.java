package com.openggf.game.sonic2;

import com.openggf.data.RetailRomRevision;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.attract.Sonic2AttractService;
import com.openggf.game.sonic2.competition.Sonic2CompetitionAssets;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.sonic2.competition.specialstage.CompetitiveHalfpipeRomData;
import com.openggf.game.sonic2.competition.specialstage.CompetitiveHalfpipeTrack;
import com.openggf.game.sonic2.credits.Sonic2CreditsMappings;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageDataLoader;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRomMappings;
import com.openggf.game.sonic2.titlescreen.Sonic2TitlePreviewProvider;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Paired original-image witnesses; REV00 is explicitly supplied, never inferred or copied. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2RetailRevisions {
    private Rom rev00() throws Exception {
        Path baseline = com.openggf.tests.RomTestUtils.ensureSonic2RomAvailable().toPath().toAbsolutePath();
        String explicit = System.getProperty("sonic2.rev00.rom.path");
        Path path = explicit == null ? baseline.resolveSibling("s2-r0.gen") : Path.of(explicit);
        if (explicit == null) assumeTrue(Files.isRegularFile(path), "Original REV00 image is optional");
        assertTrue(path.isAbsolute(), "The original REV00 path must be absolute");
        Rom rom = new Rom();
        assertTrue(rom.open(path.toString()));
        assertEquals(RetailRomRevision.S2_REV00, RetailRomRevision.detect(rom));
        assertEquals(RetailRomRevision.S2_REV01, RetailRomRevision.detect(TestEnvironment.currentRom()));
        return rom;
    }

    @Test void modulesBindOneSnapshotAndKeepConcurrentOppositeRevisions() throws Exception {
        try (Rom early = rev00()) {
            var zero = new Sonic2GameModule();
            var one = new Sonic2GameModule();
            zero.bindRetailRom(early);
            one.bindRetailRom(TestEnvironment.currentRom());
            zero.createGame(early); // Detection binding and direct creation are idempotent.
            assertEquals(RetailRomRevision.S2_REV00, zero.retailRevision());
            assertEquals(RetailRomRevision.S2_REV01, one.retailRevision());
            assertEquals(Sonic2PhysicsProvider.class, zero.getPhysicsProvider().getClass());
            assertEquals(0x42584, zero.getRomOffsetProvider().getOffset("level", "LEVEL_DATA_DIR"));
            assertEquals(0x42594, one.getRomOffsetProvider().getOffset("level", "LEVEL_DATA_DIR"));
            assertEquals(one.getRomOffsetProvider().getOffset("art", "ART_UNC_SONIC_SIZE"),
                    zero.getRomOffsetProvider().getOffset("art", "ART_UNC_SONIC_SIZE"));
            assertTrue(zero.getGameService(Sonic2RevisionProfile.class).displaysNativeDebugGeometry());
            assertFalse(one.getGameService(Sonic2RevisionProfile.class).displaysNativeDebugGeometry());
            assertThrows(IllegalStateException.class, () -> zero.bindRetailRom(TestEnvironment.currentRom()));
            assertEquals(RetailRomRevision.S2_REV00, zero.retailRevision());
            byte[] altered = early.readBytes(0, 0x100000);
            altered[0x200] ^= 1;
            try (Rom unknown = Rom.fromReader(RomByteReader.fromBytes(altered), "unverified S2 image")) {
                var compatible = new Sonic2GameModule();
                compatible.bindRetailRom(unknown);
                assertEquals(RetailRomRevision.UNKNOWN, compatible.retailRevision());
                assertTrue(compatible.getGameService(Sonic2RevisionProfile.class).rejectsHurtBumperAndLauncher());
            }
        }
    }

    @Test void actualRevisionLayoutsPreserveTheShippedDataDifferences() throws Exception {
        try (Rom early = rev00()) {
            var zero = new Sonic2ObjectPlacement(RomByteReader.fromRom(early));
            var one = new Sonic2ObjectPlacement(RomByteReader.fromRom(TestEnvironment.currentRom()));
            var earlySwitch = zero.load(new ZoneAct(0, 1)).stream()
                    .filter(o -> o.objectId() == 3 && o.subtype() == 0x0A && o.x() > 0x1900 && o.x() < 0x1B00)
                    .findFirst().orElseThrow();
            var lateSwitch = one.load(new ZoneAct(0, 1)).stream()
                    .filter(o -> o.objectId() == 3 && o.subtype() == 0x0A && o.x() > 0x1900 && o.x() < 0x1B00)
                    .findFirst().orElseThrow();
            assertEquals(0x1A08, earlySwitch.x()); assertEquals(0x380, earlySwitch.y());
            assertEquals(0x19F8, lateSwitch.x()); assertEquals(0x328, lateSwitch.y());
            var earlyPosts = zero.load(new ZoneAct(6, 0)).stream().filter(o -> o.objectId() == 0x79).toList();
            var latePosts = one.load(new ZoneAct(6, 0)).stream().filter(o -> o.objectId() == 0x79).toList();
            assertEquals(3, earlyPosts.size());
            assertTrue(earlyPosts.stream().noneMatch(o -> o.respawnTracked()));
            assertTrue(latePosts.stream().allMatch(o -> o.respawnTracked()));
            for (int act = 0; act < 2; act++) {
                var earlySign = zero.load(new ZoneAct(12, act)).stream().filter(o -> o.objectId() == 0x0D).findFirst().orElseThrow();
                var lateSign = one.load(new ZoneAct(12, act)).stream().filter(o -> o.objectId() == 0x0D).findFirst().orElseThrow();
                assertEquals(earlySign.y() - 4, lateSign.y());
                var earlyCompetition = Sonic2CompetitionLevelProfile.load(early, 12, act);
                var lateCompetition = Sonic2CompetitionLevelProfile.load(TestEnvironment.currentRom(), 12, act);
                assertEquals(lateCompetition.objectAddress() + 12, earlyCompetition.objectAddress());
                assertEquals(lateCompetition.objects().size(), earlyCompetition.objects().size());
                assertNotEquals(lateCompetition.objects(), earlyCompetition.objects());
                assertEquals(lateCompetition.decodeBaseTerrain(TestEnvironment.currentRom()).nativeBlockWords(),
                        earlyCompetition.decodeBaseTerrain(early).nativeBlockWords());
            }
        }
    }

    @Test void standardPlayableLevelsDecodeFromBothImages() throws Exception {
        try (Rom early = rev00()) {
            var zero = new Sonic2(early);
            var one = new Sonic2(TestEnvironment.currentRom());
            // Resource-plan HTZ/WFZ loads have their existing separate graphics path.
            for (int index : new int[]{0, 1, 2, 4, 6, 10, 12, 14, 15, 16, 17, 19}) {
                var a = zero.buildDetachedLevel(index);
                var b = one.buildDetachedLevel(index);
                assertEquals(b.getZoneIndex(), a.getZoneIndex(), "zone " + index);
                assertEquals(b.getPatternCount(), a.getPatternCount(), "patterns " + index);
                assertEquals(b.getChunkCount(), a.getChunkCount(), "chunks " + index);
                assertEquals(b.getBlockCount(), a.getBlockCount(), "blocks " + index);
                assertEquals(b.getMap().getWidth(), a.getMap().getWidth(), "layout " + index);
                assertEquals(b.getMinX(), a.getMinX()); assertEquals(b.getMaxX(), a.getMaxX());
                assertEquals(b.getRings(), a.getRings());
                assertArrayEquals(one.getBackgroundScroll(index, 0x1000, 0x500),
                        zero.getBackgroundScroll(index, 0x1000, 0x500), "background " + index);
            }
        }
    }

    @Test void artAnimationsFrontendAndHalfpipeUseTheirRevisionOwnedAddresses() throws Exception {
        try (Rom early = rev00()) {
            Rom late = TestEnvironment.currentRom();
            var zeroReader = RomByteReader.fromRom(early);
            var oneReader = RomByteReader.fromRom(late);
            var zeroArt = new Sonic2PlayerArt(zeroReader);
            var oneArt = new Sonic2PlayerArt(oneReader);
            assertEquals(oneArt.loadSonic().mappingFrames(), zeroArt.loadSonic().mappingFrames());
            assertEquals(oneArt.loadTails().mappingFrames(), zeroArt.loadTails().mappingFrames());
            assertEquals(oneArt.loadTails().animationSet().getScriptCount(), zeroArt.loadTails().animationSet().getScriptCount());
            var a = new Sonic2ObjectArt(early, zeroReader);
            var b = new Sonic2ObjectArt(late, oneReader);
            List<ObjectSpriteSheet> earlySheets = List.of(a.loadBridgeSheet(), a.loadFlipperSheet(),
                    a.loadMTZSpikeSheet(), a.loadEHZBossSheet(), a.loadWFZBossSheet());
            List<ObjectSpriteSheet> lateSheets = List.of(b.loadBridgeSheet(), b.loadFlipperSheet(),
                    b.loadMTZSpikeSheet(), b.loadEHZBossSheet(), b.loadWFZBossSheet());
            for (int i = 0; i < earlySheets.size(); i++) {
                assertEquals(lateSheets.get(i).getFrameCount(), earlySheets.get(i).getFrameCount());
                assertTrue(earlySheets.get(i).getFrameCount() > 0);
                assertEquals(lateSheets.get(i).getFrame(0), earlySheets.get(i).getFrame(0));
            }
            assertTrue(new Sonic2TitlePreviewProvider().load(early).isPresent());
            assertNotNull(Sonic2CompetitionAssets.load(early));
            assertEquals(new Sonic2AttractService(late).titleDuration(), new Sonic2AttractService(early).titleDuration());
            var earlyStage = new Sonic2SpecialStageDataLoader(early);
            var lateStage = new Sonic2SpecialStageDataLoader(late);
            assertArrayEquals(lateStage.getRingRequirementsTeam(), earlyStage.getRingRequirementsTeam());
            assertArrayEquals(lateStage.getRingRequirementsSolo(), earlyStage.getRingRequirementsSolo());
            assertEquals(lateStage.getPlayerDplcPlans(), earlyStage.getPlayerDplcPlans());
            for (var family : Sonic2SpecialStageRomMappings.Family.values()) {
                var earlyFrames = Sonic2SpecialStageRomMappings.load(early, family);
                var lateFrames = Sonic2SpecialStageRomMappings.load(late, family);
                assertEquals(lateFrames.size(), earlyFrames.size());
                for (int i = 0; i < earlyFrames.size(); i++) assertEquals(lateFrames.get(i).pieces(), earlyFrames.get(i).pieces());
            }
            assertDoesNotThrow(() -> new CompetitiveHalfpipeTrack(new CompetitiveHalfpipeRomData(early)));
            // Music, SFX and DAC bank placement/content are unchanged in REV00/01.
            assertArrayEquals(oneReader.slice(0xF0000, 0x10000), zeroReader.slice(0xF0000, 0x10000));
            var earlyCredits = Sonic2CreditsMappings.createFrames(early);
            var lateCredits = Sonic2CreditsMappings.createFrames(late);
            assertEquals(21, earlyCredits.size());
            assertEquals(Sonic2CreditsMappings.createFrames().get(17), lateCredits.get(17));
            assertNotEquals(earlyCredits.get(17), lateCredits.get(17));
            for (int screen = 0; screen < 21; screen++) {
                if (screen != 17) assertEquals(lateCredits.get(screen), earlyCredits.get(screen));
            }
        }
    }
}

package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.internal.HostedGameMedia.MediaKind;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.SyntheticProgramDisc;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scanning and adding games: Sonic CD is first-class, nothing that already works is replaced silently. */
class TestSetupGuideLibrary {
    @TempDir Path dir;

    private static final List<SetupGuideLibrary.Game> GAMES = SetupGuideLibrary.games();

    private static SetupGuideLibrary.Game game(String id) {
        return GAMES.stream().filter(g -> g.id().equals(id)).findFirst().orElseThrow();
    }

    private static SetupGuideLibrary.Current library(Map<String, Path> available) {
        return new SetupGuideLibrary.Current() {
            @Override public boolean available(SetupGuideLibrary.Game g) { return available.containsKey(g.id()); }
            @Override public Optional<Path> file(SetupGuideLibrary.Game g) { return Optional.ofNullable(available.get(g.id())); }
        };
    }

    @Test void theShelfListsTheThreeCartridgesAndSonicCdWithItsDiscKey() {
        assertEquals(List.of("s1", "s2", "s3k", "scd"), GAMES.stream().map(SetupGuideLibrary.Game::id).toList());
        assertTrue(game("scd").hosted());
        assertEquals(SonicConfiguration.SONIC_CD_DISC, game("scd").key());
    }

    @Test void aScanFindsSonicCdByHeaderAndAddsItWithoutTouchingWorkingGames() throws Exception {
        Path usa = SyntheticProgramDisc.write(dir.resolve("Games/Sonic CD"), CdReleaseVariant.USA);
        var findings = SetupGuideLibrary.scan(dir.resolve("Games"), GAMES);
        var cd = findings.best("scd").orElseThrow();
        assertEquals(MediaKind.READY, cd.kind());
        var plan = SetupGuideLibrary.plan(findings, GAMES, library(Map.of("s2", dir.resolve("s2.gen"))));
        assertEquals(Map.of(SonicConfiguration.SONIC_CD_DISC, usa.toAbsolutePath().normalize().toString()), plan.writes(),
                "only the new disc; the ROM folder stays because a game already works");
        assertEquals(List.of(game("scd")), plan.added());
    }

    @Test void aSplitPairInSeparateDirectoriesIsSavedAsTwoComponentPaths() throws Exception {
        Path three = writeCartridge("three", "SONIC THE HEDGEHOG 3");
        Path knuckles = writeCartridge("knuckles", "SONIC & KNUCKLES");
        var findings = SetupGuideLibrary.scan(dir, GAMES);
        var plan = SetupGuideLibrary.plan(findings, GAMES, library(Map.of()));
        assertEquals(List.of(knuckles, three), findings.best("s3k").orElseThrow().paths());
        assertEquals("", plan.writes().get(SonicConfiguration.SONIC_3K_ROM));
        assertEquals(three.toString(), plan.writes().get(SonicConfiguration.SONIC_3_ROM));
        assertEquals(knuckles.toString(), plan.writes().get(SonicConfiguration.SONIC_KNUCKLES_ROM));
    }

    private Path writeCartridge(String folder, String title) throws Exception {
        Path path = Files.createDirectories(dir.resolve(folder)).resolve("image.bin");
        byte[] bytes = new byte[0x200000];
        byte[] titleBytes = title.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(titleBytes, 0, bytes, 0x120, titleBytes.length);
        return Files.write(path, bytes).toAbsolutePath().normalize();
    }

    @Test void problemDiscsAreReportedByKindAndWriteNothing() throws Exception {
        SyntheticProgramDisc.write(dir.resolve("eu"), CdReleaseVariant.EUROPE);
        var findings = SetupGuideLibrary.scan(dir, GAMES);
        assertEquals(MediaKind.OTHER_RELEASE, findings.best("scd").orElseThrow().kind());
        var current = library(Map.of());
        assertEquals(SetupGuideLibrary.Status.WRONG_RELEASE, SetupGuideLibrary.status(game("scd"), current, findings, false));
        assertTrue(SetupGuideLibrary.plan(findings, GAMES, current).writes().isEmpty());
        assertEquals(SetupGuideLibrary.Status.MISSING, SetupGuideLibrary.status(game("s1"), current, findings, false),
                "Sonic CD's problem does not block or mark the cartridges");
    }

    @Test void aUsableDiscWinsOverABrokenOneInTheSameScan() throws Exception {
        Path broken = SyntheticProgramDisc.write(dir.resolve("a-broken"), CdReleaseVariant.USA);
        Files.delete(broken.resolveSibling("audio20.bin"));
        Path good = SyntheticProgramDisc.write(dir.resolve("b-good"), CdReleaseVariant.USA);
        var best = SetupGuideLibrary.scan(dir, GAMES).best("scd").orElseThrow();
        assertEquals(good.toAbsolutePath().normalize(), best.file());
    }

    @Test void aDifferentFileForAWorkingGameIsAConflictNotAWrite() throws Exception {
        Path current = SyntheticProgramDisc.write(dir.resolve("current"), CdReleaseVariant.USA);
        Path other = SyntheticProgramDisc.write(dir.resolve("other"), CdReleaseVariant.USA);
        var findings = SetupGuideLibrary.locate(other, GAMES);
        var plan = SetupGuideLibrary.plan(findings, GAMES, library(Map.of("scd", current)));
        assertTrue(plan.writes().isEmpty());
        assertEquals(1, plan.conflicts().size());
        assertEquals(other.toAbsolutePath().normalize(), plan.conflicts().getFirst().found());
        var same = SetupGuideLibrary.plan(SetupGuideLibrary.locate(current, GAMES), GAMES, library(Map.of("scd", current)));
        assertTrue(same.conflicts().isEmpty() && same.writes().isEmpty(), "the same file is neither a write nor a conflict");
    }

    @Test void choosingTheNewFileInTheConflictWritesOnlyThatGame() throws Exception {
        var config = SonicConfigurationService.createStandalone(dir);
        Path other = SyntheticProgramDisc.write(dir.resolve("other"), CdReleaseVariant.USA);
        SetupGuideLibrary.replace(config, new SetupGuideLibrary.Conflict(game("scd"), dir.resolve("old.cue"), other));
        var saved = SonicConfigurationService.createStandalone(dir);
        assertEquals(other.toString(), saved.getString(SonicConfiguration.SONIC_CD_DISC));
        assertEquals(".", saved.getString(SonicConfiguration.ROMS_DIRECTORY));
    }

    @Test void anUnrelatedFileIsNotAGame() throws Exception {
        Path text = Files.writeString(dir.resolve("notes.cue"), "REM not a disc\n");
        assertTrue(SetupGuideLibrary.locate(text, GAMES).findings().stream().allMatch(f -> f.kind() == MediaKind.UNREADABLE),
                "a malformed sheet is reported as unreadable, never as a game");
        Path other = Files.write(dir.resolve("other.bin"), new byte[4096]);
        assertTrue(SetupGuideLibrary.locate(other, GAMES).findings().isEmpty());
    }

    @Test void aRealCartridgeIsIdentifiedByContentAndSetsTheRomFolderOnlyForAnEmptyLibrary() throws Exception {
        String rom = System.getProperty("sonic2.rom.path");
        Assumptions.assumeTrue(rom != null && Files.isRegularFile(Path.of(rom)), "sonic2.rom.path not set");
        var findings = SetupGuideLibrary.locate(Path.of(rom), GAMES);
        assertEquals(Set.of("s2"), Set.copyOf(findings.findings().stream().map(SetupGuideLibrary.Finding::gameId).toList()));
        var empty = SetupGuideLibrary.plan(findings, GAMES, library(Map.of()));
        assertEquals(Path.of(rom).toAbsolutePath().normalize().getParent().toString(),
                empty.writes().get(SonicConfiguration.ROMS_DIRECTORY));
        var withCd = SetupGuideLibrary.plan(findings, GAMES, library(Map.of("scd", dir.resolve("cd.cue"))));
        assertFalse(withCd.writes().containsKey(SonicConfiguration.ROMS_DIRECTORY));
        assertTrue(withCd.writes().containsKey(SonicConfiguration.SONIC_2_ROM));
    }
}

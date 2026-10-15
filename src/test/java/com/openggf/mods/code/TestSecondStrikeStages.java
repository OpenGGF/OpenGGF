package com.openggf.mods.code;

import com.openggf.game.GameServices;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.objects.GumballMachineObjectInstance;
import com.openggf.game.sonic3k.difficulty.BlueSphereLayoutPolicy;
import com.openggf.game.sonic3k.difficulty.GumballSpringPolicy;
import com.openggf.game.sonic3k.difficulty.S3kDifficultyRules;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageGrid;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageManager;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageDataLoader;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageRingConverter;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageRomOffsets;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.mods.validation.ModValidator;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native board consumers and unchanged Gumball recovery after campaign publication. */
class TestSecondStrikeStages {
    @TempDir
    static Path content;
    private static URLClassLoader loader;
    private static BlueSphereLayoutPolicy boards;
    private static GumballSpringPolicy springs;

    @BeforeAll
    static void compileContent() throws Exception {
        loader = compilePolicies(content);
        boards = (BlueSphereLayoutPolicy) loader.loadClass("secondstrike.campaign.CampaignBlueSphereBoards")
                .getConstructor().newInstance();
        springs = (GumballSpringPolicy) loader.loadClass("secondstrike.stages.SecondStrikeStages")
                .getConstructor().newInstance();
    }

    @AfterAll
    static void closeLoader() throws Exception {
        if (loader != null) loader.close();
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void firstRomBoardChangesOnlyTwelveEmptyApproachCells() throws Exception {
        byte[] source = romBoard(0);
        byte[] before = source.clone();
        byte[] variant = boards.transformLayout(0, source);
        assertArrayEquals(variant, S3kDifficultyRules.blueSphereLayout(source, variant));
        assertNotSame(source, variant);
        assertArrayEquals(before, source, "creator policy cannot overwrite the decoded ROM input");
        assertArrayEquals(Arrays.copyOfRange(source, GRID_CELL_COUNT, source.length),
                Arrays.copyOfRange(variant, GRID_CELL_COUNT, variant.length));
        assertEquals(102, count(variant, CELL_BLUE));
        assertEquals(count(source, CELL_RING), count(variant, CELL_RING));
        assertEquals(count(source, CELL_RED) + 12, count(variant, CELL_RED));
        int changed = 0;
        for (int cell = 0; cell < GRID_CELL_COUNT; cell++) {
            if (source[cell] != variant[cell]) {
                changed++;
                assertEquals(CELL_EMPTY, source[cell]);
                assertEquals(CELL_RED, variant[cell]);
            }
        }
        assertEquals(12, changed);
        // Four native opening cells give the player a readable runway. The added
        // gate is on the old straight line and the neighbouring lane remains open.
        for (int y = 2; y <= 6; y++) assertEquals(source[y * 32 + 2], variant[y * 32 + 2]);
        assertEquals(CELL_RED, variant[8 * 32 + 2]);
        assertEquals(CELL_EMPTY, variant[8 * 32 + 3]);
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void nativeBlueLoopStillProducesTheSameSixteenRings() throws Exception {
        byte[] source = romBoard(0);
        assertEquals(16, convertTopLeftLoop(source));
        assertEquals(16, convertTopLeftLoop(boards.transformLayout(0, source)),
                "extra approach hazards must not change the native ring-conversion reward");
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void nativeBoardUsesTheActiveWorldPolicyEvenWhenBootstrapIsStock() throws Exception {
        GameModule bootstrap = GameModuleRegistry.getBootstrapDefault();
        AtomicInteger calls = new AtomicInteger();
        GameModule active = boardModule((stage, source) -> {
            calls.incrementAndGet();
            return boards.transformLayout(stage, source);
        });
        try {
            SessionManager.openGameplaySession(new Sonic3kGameModule(), active, null);
            GameModuleRegistry.setCurrent(new Sonic3kGameModule());
            var manager = new Sonic3kSpecialStageManager();
            manager.initialize(0);
            assertEquals(1, calls.get());
            assertEquals(CELL_RED, manager.getGrid().getCell(2, 8));
            assertEquals(102, manager.getSpheresLeft());
        } finally {
            SessionManager.clear();
            GameModuleRegistry.setCurrent(bootstrap);
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void preSessionBoardIgnoresAStaleCreatorPolicyInBootstrap() throws Exception {
        GameModule bootstrap = GameModuleRegistry.getBootstrapDefault();
        AtomicInteger calls = new AtomicInteger();
        try {
            SessionManager.clear();
            GameModuleRegistry.setCurrent(boardModule((stage, source) -> {
                calls.incrementAndGet();
                return boards.transformLayout(stage, source);
            }));
            var manager = new Sonic3kSpecialStageManager();
            manager.initialize(0);
            assertEquals(0, calls.get(), "pre-session loading must not invoke creator callbacks");
            assertEquals(CELL_EMPTY, manager.getGrid().getCell(2, 8));
            assertEquals(102, manager.getSpheresLeft());
        } finally {
            SessionManager.clear();
            GameModuleRegistry.setCurrent(bootstrap);
        }
    }

    private static GameModule boardModule(BlueSphereLayoutPolicy policy) {
        return new DelegatingGameModule(new Sonic3kGameModule(), "test:second-strike-board") {
            @Override public <T> T getGameService(Class<T> contract) {
                return contract == BlueSphereLayoutPolicy.class ? contract.cast(policy)
                        : super.getGameService(contract);
            }
        };
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void unusedEighthSlotsRemainNativeAndPassHostAdmission() throws Exception {
        // The fourteen authored boards have their own campaign fixture. These
        // two unused native slots retain the original pass-through contract.
        for (int stage : new int[] {7, 15}) {
            byte[] source = romBoard(stage);
            byte[] result = boards.transformLayout(stage, source);
            assertNotSame(source, result);
            assertArrayEquals(source, result, "unused native slot " + stage + " remains stock");
            assertArrayEquals(source, S3kDifficultyRules.blueSphereLayout(source, result));
        }
    }

    @Test
    void unexpectedLayoutAndObstructedGateAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> boards.transformLayout(0, new byte[1024]));
        byte[] source = new byte[0x408];
        source[8 * 32 + 2] = CELL_BLUE;
        assertThrows(IllegalArgumentException.class, () -> boards.transformLayout(0, source),
                "a different ROM board must never silently lose a native blue sphere");
    }

    @Test
    void gateCannotCutOffAnOriginallyReachableBlueSphere() {
        byte[] source = new byte[0x408];
        Arrays.fill(source, 0, GRID_CELL_COUNT, (byte) CELL_RED);
        // An open approach ending in one blue. The first authored gate would seal
        // it; all other gate cells are harmless isolated empties for this check.
        int[][] gates = {{2, 8}, {3, 11}, {2, 20}, {3, 23}, {28, 8}, {29, 11},
                {28, 20}, {29, 23}, {10, 3}, {21, 2}, {10, 28}, {21, 29}};
        for (int[] gate : gates) source[gate[1] * 32 + gate[0]] = CELL_EMPTY;
        for (int y = 2; y <= 8; y++) source[y * 32 + 2] = CELL_EMPTY;
        source[9 * 32 + 2] = CELL_BLUE;
        source[1024] = (byte) 0x80;
        source[1026] = 2;
        source[1028] = 2;
        assertThrows(IllegalArgumentException.class, () -> S3kDifficultyRules.blueSphereLayout(source, boards.transformLayout(0, source)),
                "the canonical native owner, rather than copied creator safety logic, rejects lost reachability");
    }

    @Test
    void gumballKeepsThreeSeparatedSymmetricRecoverySpringsWithoutSharedMutation() {
        int[] offsets = springs.springOffsets();
        assertArrayEquals(offsets, S3kDifficultyRules.gumballSpringOffsets(offsets));
        assertArrayEquals(new int[] {-32, 0, 32}, offsets);
        assertTrue(offsets[1] - offsets[0] >= 32);
        assertTrue(offsets[2] - offsets[1] >= 32);
        offsets[0] = 1000;
        assertArrayEquals(new int[] {-32, 0, 32}, springs.springOffsets());
    }

    @Test
    void nativeGumballUsesAuthoredSpringsAndRepRestoresThatSameRecoveryLayout() {
        BonusHarness harder = gumball(springs);
        GumballMachineObjectInstance machine = harder.machine();
        machine.update(0, null);
        assertEquals(List.of(0xE0, 0x100, 0x120), liveSpringXs(harder));
        machine.respawnSprings();
        assertEquals(List.of(0xE0, 0x100, 0x120), liveSpringXs(harder),
                "REP restores authored consumable springs through its existing native owner");

        BonusHarness stock = gumball(null);
        stock.machine().update(0, null);
        assertEquals(List.of(0xD0, 0xF0, 0x110, 0x130), liveSpringXs(stock),
                "disabled Second Strike retains all four native springs");
    }

    @Test
    void nativeGumballPrizeSequenceRemainsIdenticalForMatchedRng() {
        BonusHarness harder = gumball(springs);
        BonusHarness stock = gumball(null);
        for (int roll = 0; roll < 128; roll++) {
            assertEquals(stock.machine().chooseBallSubtype(), harder.machine().chooseBallSubtype(),
                    "the harder challenge must not consume extra RNG or reduce native prizes");
        }
    }

    @Test
    void engineAdmissionRejectsUnsafeOwnedChangesAndCopiesAcceptedValues() {
        assertEquals(-1, S3kDifficultyRules.followupDelayTicks(-1));
        assertEquals(20, S3kDifficultyRules.followupDelayTicks(20));
        for (int delay : new int[] {-2, 0, 15, 61}) {
            assertThrows(IllegalArgumentException.class, () -> S3kDifficultyRules.followupDelayTicks(delay));
        }
        for (int[] offsets : new int[][] {{0}, {-48, -24, 0, 24, 48}, {0, 0},
                {-32, 0, 16}, {-49, 49}, {-7, 7}}) {
            assertThrows(IllegalArgumentException.class, () -> S3kDifficultyRules.gumballSpringOffsets(offsets));
        }
        int[] offsets = {-32, 0, 32};
        int[] admittedOffsets = S3kDifficultyRules.gumballSpringOffsets(offsets);
        offsets[0] = -100;
        assertArrayEquals(new int[] {-32, 0, 32}, admittedOffsets);

        byte[] source = new byte[0x408];
        source[10 * 32 + 10] = CELL_BLUE;
        source[12 * 32 + 12] = CELL_RING;
        byte[] candidate = source.clone();
        byte[] admitted = S3kDifficultyRules.blueSphereLayout(source, candidate);
        assertNotSame(candidate, admitted);
        assertArrayEquals(source, admitted);
        for (int cell : new int[] {0, 10 * 32 + 10, 12 * 32 + 12, 1024}) {
            byte[] unsafe = source.clone();
            unsafe[cell] = (byte) (unsafe[cell] == CELL_RED ? CELL_EMPTY : CELL_RED);
            assertThrows(IllegalArgumentException.class, () -> S3kDifficultyRules.blueSphereLayout(source, unsafe));
        }
        byte[] tooMany = source.clone();
        Arrays.fill(tooMany, 4 * 32, 5 * 32 + 1, (byte) CELL_RED);
        assertThrows(IllegalArgumentException.class, () -> S3kDifficultyRules.blueSphereLayout(source, tooMany));
        byte[] cutoff = source.clone();
        for (int cell : new int[] {9 * 32 + 10, 11 * 32 + 10, 10 * 32 + 9, 10 * 32 + 11}) {
            cutoff[cell] = CELL_RED;
        }
        assertThrows(IllegalArgumentException.class, () -> S3kDifficultyRules.blueSphereLayout(source, cutoff));
    }

    @Test
    void compiledPoliciesRemainEligibleForCreatorRuntimePublication() throws Exception {
        Path entrypoint = content.resolve("PolicyProbeMod.java");
        Files.writeString(entrypoint, """
                package secondstrike.rules;
                public final class PolicyProbeMod implements com.openggf.mods.code.GgfMod {
                    public void register(com.openggf.mods.code.ModContext context) {
                        context.registerService("boss", com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy.class,
                                new secondstrike.boss.SecondStrikeBosses());
                        context.registerService("board", com.openggf.game.sonic3k.difficulty.BlueSphereLayoutPolicy.class,
                                new secondstrike.campaign.CampaignBlueSphereBoards());
                        context.registerService("springs", com.openggf.game.sonic3k.difficulty.GumballSpringPolicy.class,
                                new secondstrike.stages.SecondStrikeStages());
                    }
                }
                """);
        Path output = content.resolve("policies");
        String classpath = output + java.io.File.pathSeparator + System.getProperty("java.class.path");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "25", "-cp", classpath, "-d", output.toString(), entrypoint.toString()));
        var report = new ModValidator().validateDirectory(output, "secondstrike.rules.PolicyProbeMod");
        assertTrue(report.eligible(), report.findings().toString());
    }

    private record BonusHarness(GumballMachineObjectInstance machine, ObjectManager objects) { }

    private static BonusHarness gumball(GumballSpringPolicy springPolicy) {
        GameModule module = new DelegatingGameModule(new Sonic3kGameModule(), "test:second-strike") {
            @Override public <T> T getGameService(Class<T> contract) {
                return contract == GumballSpringPolicy.class ? contract.cast(springPolicy) : null;
            }
        };
        StubObjectServices services = new StubObjectServices() {
            @Override public GameModule gameModule() { return module; }
        }.withIsolatedObjectManager();
        ObjectManager objects = services.objectManager();
        GumballMachineObjectInstance machine = objects.createDynamicObject(() -> new GumballMachineObjectInstance(
                new ObjectSpawn(0x100, 0x310, 0x86, 0, 0, false, 0)));
        return new BonusHarness(machine, objects);
    }

    private static List<Integer> liveSpringXs(BonusHarness harness) {
        return harness.objects().getActiveObjects().stream()
                .filter(object -> object.getName().equals("GumballSpring") && !object.isDestroyed())
                .map(ObjectInstance::getX).sorted().toList();
    }

    private static byte[] romBoard(int stage) throws Exception {
        if (stage >= 8) {
            byte[] decoded = Sonic3kSpecialStageDataLoader.create().getCompressedLayoutSet(0);
            int offset = (stage - 8) * Sonic3kSpecialStageRomOffsets.LAYOUT_STAGE_SIZE;
            return Arrays.copyOfRange(decoded, offset,
                    offset + Sonic3kSpecialStageRomOffsets.LAYOUT_STAGE_SIZE);
        }
        return GameServices.rom().getRom().readBytes(Sonic3kSpecialStageRomOffsets.LAYOUT_S3_STAGE_1
                + (long) stage * Sonic3kSpecialStageRomOffsets.LAYOUT_STAGE_SIZE,
                Sonic3kSpecialStageRomOffsets.LAYOUT_STAGE_SIZE);
    }

    private static int count(byte[] layout, int type) {
        int result = 0;
        for (int i = 0; i < GRID_CELL_COUNT; i++) if ((layout[i] & 0xFF) == type) result++;
        return result;
    }

    private static int convertTopLeftLoop(byte[] layout) {
        Sonic3kSpecialStageGrid grid = new Sonic3kSpecialStageGrid();
        grid.loadFromLayoutData(layout);
        for (int y = 4; y <= 7; y++) {
            for (int x = 4; x <= 7; x++) {
                if (x == 4 || x == 7 || y == 4 || y == 7) grid.setCell(x, y, CELL_RED);
            }
        }
        grid.setCell(4, 4, CELL_TOUCHED);
        assertTrue(new Sonic3kSpecialStageRingConverter().convert(grid, 4 * 32 + 4).converted);
        int rings = 0;
        for (int cell = 0; cell < GRID_CELL_COUNT; cell++) {
            if (grid.getCellByIndex(cell) == CELL_RING) rings++;
        }
        return rings;
    }

    static URLClassLoader compilePolicies(Path directory) throws Exception {
        return SecondStrikeSourceFixture.compile(directory.resolve("policies"),
                SecondStrikeSourceFixture.MAIN.resolve("secondstrike/boss/SecondStrikeBosses.java"),
                SecondStrikeSourceFixture.MAIN.resolve("secondstrike/campaign/CampaignBlueSphereBoards.java"),
                SecondStrikeSourceFixture.MAIN.resolve("secondstrike/stages/SecondStrikeStages.java"));
    }
}

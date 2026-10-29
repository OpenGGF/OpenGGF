package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectTileIds;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual original USA media cases; no assumptions/skips or A facade. New source-only vectors were frozen
 * before production. N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Spring/Main.asm:186-244,492-496;
 * Spring/Data/Mappings.asm:1-82; Object Tile IDs (Act 1 Past).asm:7-44; Object Tile ID Set.asm:7-17.
 * Init-only callbacks observe the synchronous draw cut; real Solid/math are supplied but must not run.
 * These are bounded component cases, not Past admission, a returned World frame, pixels or audio parity.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdPastSpring {
    private static final int WHOLE = 40;
    private static final int MAP = 0x2099F8;
    private static final int[] TARGETS = {36,38,40,42,44,46,56,50,54,52,48,58,60,62,64,66,68,70};
    private static final int[] WORDS = {0x2396,0x243F,0x23ED,0x2000,0x2403,0x4334,0x0372,0x0381,
            0x4000,0x4000,0x0320,0x0346,0x8328,0,0,0x0490,0x039E,0};

    @Test void namedFactoryUsesTheActualOriginalPastAggregateAndFourUnpaddedViews() throws Exception {
        var a = retail(); var spring = CdSpring.Assets.fromPastAssets(a);
        assertEquals(CdReleaseVariant.USA, a.identity().variant());
        assertEquals(CdStagePayload.R11B.stage(), a.identity().stage());
        assertEquals(CdNativeFile.R11B, a.identity().file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, a.identity().domain());
        assertEquals("b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265", a.identity().mmdSha256());
        assertEquals(MAP, spring.mappingsAddress());
        assertArrayEquals(a.view(SPRING_SPEEDS).bytes().copy(), spring.speedWords().copy());
        assertArrayEquals(a.view(OBJECT_TILE_IDS).bytes().copy(), spring.objectTileIds().copy());
        assertEquals(4, spring.speedWords().length()); assertEquals(72, spring.objectTileIds().length());
        assertEquals("5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621", sha(spring.speedWords().copy()));
        assertEquals("7f6759c3ab336c2eee70a2fea5128e9920c86d418590ae557e0310948c563da1", sha(spring.objectTileIds().copy()));
        assertEquals("7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5", sha(a.view(ANI_SPRING).bytes().copy()));
        assertEquals("09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c", sha(a.view(MAP_SPRING).bytes().copy()));
        assertThrows(NullPointerException.class, () -> CdSpring.Assets.fromPastAssets(null));
        assertThrows(IllegalArgumentException.class,
                () -> CdR11aObjectAssets.decodeTileWord(spring.objectTileIds().copy(), 15, 0));
    }

    @Test void threeIndependentNativeRootsKeepTheFullIncludeAndRealScript() throws Exception {
        var a = retail(); var s = CdSpring.Assets.fromPastAssets(a);
        var families = List.of(s.vertical(), s.horizontal(), s.diagonal());
        int[] roots = {0,6,80};
        int[][] relative = {{12,24,30},{40,52,58},{6,42,78}};
        int[][] pieces = {{2,1,3},{2,1,3},{7,7,8}};
        for (int i = 0; i < families.size(); i++) {
            var family = families.get(i);
            assertEquals(roots[i], family.mappingTableOffset());
            assertArrayEquals(a.view(MAP_SPRING).bytes().copy(), family.mappings().copy());
            assertEquals(200, family.mappings().length());
            assertArrayEquals(HexFormat.of().parseHex("00020000010102020202020201fc"), family.animationTable().copy());
            assertEquals(1, family.animations().size());
            assertEquals(List.of(0,1,1,2,2,2,2,2,2,1), family.animations().getFirst().frameBytes());
            assertEquals(3, family.mappingFrames().size());
            for (int frame = 0; frame < 3; frame++) {
                assertEquals(relative[i][frame], family.mappingFrames().get(frame).relativeOffset());
                assertEquals(pieces[i][frame], family.mappingFrames().get(frame).pieces().size());
            }
        }
        assertEquals(0x2C, s.diagonal().mappingFrames().get(2).pieces().get(7).tileOffset());
    }

    @Test void actualPastDirectoryHasAllEighteenSignedTargetsAndEndsAt72BeforeAnimal() throws Exception {
        var bytes = retail().view(OBJECT_TILE_IDS).bytes();
        for (int selector = 0; selector < 18; selector++) {
            assertEquals(TARGETS[selector], bytes.unsignedWord(selector * 2), "selector " + selector);
            assertEquals(WORDS[selector], bytes.unsignedWord(TARGETS[selector]));
            assertEquals(TARGETS[selector] + 2, CdObjectTileIds.endOfBody(bytes, selector));
        }
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedWord(72));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.slice(72, 2));
    }

    @Test void normalSideDownAndDiagonalInitUseLiteralPastRootsDimensionsTilesAndSpeed() throws Exception {
        var a = retail(); var assets = CdSpring.Assets.fromPastAssets(a);
        // subtype,spriteFlags,routine,width,Yradius,mapOffset,tile,signedSpeed,objectFlags
        int[][] vectors = {{0,0,2,16,8,0,0x0520,-4096,0}, {2,0,2,16,8,0,0x2520,-2560,0},
                {4,2,8,8,16,6,0x0520,-4096,0}, {6,0,8,8,16,6,0x2520,-2560,0},
                {0,2,14,16,8,0,0x0520,-4096,2}, {2,2,14,16,8,0,0x2520,-2560,2},
                {8,0,20,24,12,80,0x0490,-4096,0}, {10,2,20,24,12,80,0x2490,-2560,0},
                {12,2,8,8,16,6,0x0520,-4096,0}, {14,2,8,8,16,6,0x2520,-2560,0}};
        for (int[] v : vectors) {
            var h = new InitHarness(a, assets);
            h.seed(v[0], v[1], 0); var before = h.slot();
            var expected = prefix(before, v[2], v[3], v[4], v[5], v[1] | 4)
                    .moveWord(TILE, v[6]).moveWord(SPRING_SPEED, v[7]).moveByte(FLAGS, v[8]);
            var result = h.run();
            assertEquals(expected, result.state()); assertEquals(expected, h.slot());
            assertEquals(List.of(expected), h.draws);
            assertEquals(List.of(new CdSpring.RoutineEntered(CdSpring.NativeRoutine.INIT),
                    new CdSpring.CallReturned(CdSpring.NativeCall.DRAW_OBJECT)), result.effects());
            assertEquals(h.player, result.finalPlayer()); assertEquals(h.globals, result.finalGlobals());
        }
    }

    @Test void everySubtypeByteSelectsOnlyTheNativeSpeedWordAndOtherWidthsAreRejected() throws Exception {
        var assets = CdSpring.Assets.fromPastAssets(retail());
        for (int subtype = 0; subtype < 256; subtype++) {
            assertEquals((subtype & 2) == 0 ? -4096 : -2560, assets.speedWord(subtype), "subtype " + subtype);
        }
        assertThrows(IllegalArgumentException.class, () -> assets.speedWord(-1));
        assertThrows(IllegalArgumentException.class, () -> assets.speedWord(256));
    }

    @Test void everyUnreviewedPastSubtypeTwoStopsAfterTheExactPublishedInitPrefix() throws Exception {
        var a = retail(); var assets = CdSpring.Assets.fromPastAssets(a);
        for (int subtype2 = 1; subtype2 < 256; subtype2++) {
            var h = new InitHarness(a, assets); h.seed(10, 0x12, subtype2);
            var before = h.slot();
            var error = assertThrows(CdSpring.UnsupportedSpring.class, h::run);
            assertEquals(CdSpring.UnsupportedBoundary.DIAGONAL_TILE_SELECTOR, error.boundary());
            assertEquals(WHOLE, error.wholeIndex()); assertEquals(15, error.value());
            assertTrue(error.readAddress24().isEmpty());
            assertEquals(prefix(before, 20,24,12,80,0x16), h.slot());
            assertTrue(h.draws.isEmpty());
        }
    }

    @Test void malformedPastDirectoryAtEverySelectorFailsAtReachedLookupAfterTheSamePrefix() throws Exception {
        var a = retail(); var original = CdSpring.Assets.fromPastAssets(a);
        for (int selector = 0; selector < 18; selector++) {
            for (int bad : new int[]{34,35,71,72,0x8000,0xFFFF}) {
                byte[] changed = original.objectTileIds().copy(); word(changed, selector * 2, bad);
                var h = new InitHarness(a, tileFormat(original, CdAssetBytes.copyOf(changed)));
                h.seed(10, 0x12, 0); var before = h.slot();
                var error = assertThrows(IllegalArgumentException.class, h::run);
                assertEquals("Object tile signed relative word points outside declared data bodies", error.getMessage());
                assertEquals(prefix(before, 20,24,12,80,0x16), h.slot());
                assertTrue(h.draws.isEmpty());
            }
        }
    }

    @Test void sourceValueAtSelectorFIsReadAndConstructorKeepsEveryOtherExactShape() throws Exception {
        var a = retail(); var original = CdSpring.Assets.fromPastAssets(a);
        // Constructed mutation of a retained copy tests format consumption, not an authenticated source claim.
        byte[] changed = original.objectTileIds().copy(); word(changed, 66, 0x1274);
        var h = new InitHarness(a, tileFormat(original, CdAssetBytes.copyOf(changed)));
        h.seed(8,0,0); h.run(); assertEquals(0x1274, h.slot().word(TILE));
        for (int extent : new int[]{0,36,38,70,71,73,75,76}) {
            assertThrows(IllegalArgumentException.class,
                    () -> tileFormat(original, CdAssetBytes.copyOf(new byte[extent])));
        }
        assertThrows(IllegalArgumentException.class, () -> new CdSpring.Assets(original.horizontal(), original.vertical(),
                original.diagonal(), MAP, original.speedWords(), original.objectTileIds()));
        assertThrows(IllegalArgumentException.class, () -> new CdSpring.Assets(original.vertical(), original.horizontal(),
                original.diagonal(), MAP, CdAssetBytes.copyOf(new byte[2]), original.objectTileIds()));
        assertThrows(IllegalArgumentException.class, () -> new CdSpring.Assets(original.vertical(), original.horizontal(),
                original.diagonal(), 0xFFFFFF-198, original.speedWords(), original.objectTileIds()));
        assertEquals(6, CdSpring.Assets.class.getRecordComponents().length);
        assertEquals(1, CdSpring.class.getConstructors().length);
        assertEquals(8, CdSpring.class.getConstructors()[0].getParameterCount());
        assertEquals(4, CdSpring.Result.class.getRecordComponents().length);
    }

    private static CdSpring.Assets tileFormat(CdSpring.Assets a, CdAssetBytes bytes) {
        return new CdSpring.Assets(a.vertical(), a.horizontal(), a.diagonal(), a.mappingsAddress(), a.speedWords(), bytes);
    }
    private static CdObjectState prefix(CdObjectState before, int routine, int width, int radius, int mapOffset, int spr) {
        return before.moveByte(ROUTINE, routine).moveLong(MAPPINGS, MAP + mapOffset).moveWord(TILE, 0x0520)
                .moveByte(SPRITE_FLAGS, spr).moveByte(WIDTH, width).moveByte(Y_RADIUS, radius)
                .moveWord(SPRING_ANCHOR_X, 0x0100).moveByte(PRIORITY, 4);
    }

    private static final class InitHarness {
        final CdObjectPlacementRuntime runtime;
        final CdSpring spring;
        final List<CdObjectState> draws = new ArrayList<>();
        final CdSolidObject.Player player = new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(
                new CdPlayerMotionState(0x01008000,0x00671234,0x123,0x400,0,2,0x10,2,4,9,19,
                        0,0,1,0,0,0,0,0x56,0x78), 0x780,2,0,0),1,0,0);
        final CdSolidObject.Globals globals = new CdSolidObject.Globals(0,4,0x12,0x34);
        InitHarness(CdR11bAssets actual, CdSpring.Assets assets) {
            runtime = new CdObjectPlacementRuntime(actual.placements());
            var movement = CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromPast(actual));
            spring = new CdSpring(runtime, assets, new CdPlayerMath(actual.player()), new CdSolidObject(movement),
                    call -> fail("Init must not reach SolidCommit"), call -> fail("Init must not reach LaunchCommit"),
                    (pool, identity) -> draws.add(pool.state(identity.wholeIndex())),
                    sound -> fail("Init must not request sound"));
        }
        CdObjectState slot() { return runtime.pool().state(WHOLE); }
        void seed(int subtype, int sprite, int subtype2) {
            runtime.pool().write(slot().moveByte(ID,0x0A).moveWord(X,0x0100).moveWord(Y,0x0080)
                    .moveWord(X_FRACTION,0x4321).moveWord(Y_FRACTION,0x8765).moveByte(SUBTYPE,subtype)
                    .moveByte(SUBTYPE2,subtype2).moveByte(SPRITE_FLAGS,sprite).moveWord(SPRING_SPEED,0x1234)
                    .moveByte(ANIMATION_TIME,7).moveByte(MAP_FRAME,2));
        }
        CdSpring.Result run() {
            return spring.run(WHOLE, new CdSpring.Globals(128), new CdSpring.SolidInputs(player,
                    new CdSolidObject.Gates(0,0), globals));
        }
    }

    private String retainedRoot;
    private CdR11bAssets retained;
    private CdR11bAssets retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Original USA media cases require soniccd.media.root");
        assertFalse(root.isBlank(), "Original USA media root must not be blank");
        if (retained != null && root.equals(retainedRoot)) return retained;
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA source must contain exactly one existing CUE");
            cue = cues.getFirst();
        }
        try (var programs = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(programs));
        }
        retainedRoot = root;
        return retained; // Immutable detached aggregate only; never a file handle or validation receipt.
    }
    private static void word(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value;
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}

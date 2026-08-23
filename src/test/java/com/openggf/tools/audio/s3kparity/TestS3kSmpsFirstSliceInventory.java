package com.openggf.tools.audio.s3kparity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSmpsFirstSliceInventory {

    private static final Set<String> REQUIRED_SERVICE_KEYS = Set.of(
            "s3k.service.order",
            "s3k.tempo.carry-service",
            "s3k.speed.extra-service",
            "s3k.sfx.admission-restore",
            "s3k.sfx.continuous",
            "s3k.note-fill",
            "s3k.collapse.modulation-psg-noise",
            "s3k.pause-fade-jingle-stop-all",
            "s3k.dac-fm6",
            "s3k.sega-pcm",
            "s3k.pal.full-driver-repeat",
            "s3k.ring-speaker.alternation");

    @Test
    void lockedOnDialectPinsEveryShippedBuildCondition() {
        assertEquals(Map.of(
                        "SonicDriverVer", "4",
                        "fix_sndbugs", "0",
                        "FixMusicAndSFXDataBugs", "0",
                        "FixBugs", "0"),
                S3kDriverServiceInventory.LOCKED_ON_SOURCE_CONDITIONS);
    }

    @Test
    void firstSliceDeclaresEveryRequiredGlobalServiceFamily() {
        List<S3kSmpsReachabilityInventory.Behavior> rows =
                S3kDriverServiceInventory.firstSliceRows();

        S3kDriverServiceInventory.validateCompleteFirstSlice(
                REQUIRED_SERVICE_KEYS, rows);
        assertEquals(REQUIRED_SERVICE_KEYS,
                rows.stream().map(S3kSmpsReachabilityInventory.Behavior::key)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        assertTrue(rows.stream().allMatch(row -> !row.sourceCitation().isBlank()));
        assertTrue(rows.stream().allMatch(row -> !row.runtimeOwner().isBlank()));
    }

    @Test
    void firstSliceValidationRejectsDuplicateMissingAndCompetingStatusDimensions() {
        List<S3kSmpsReachabilityInventory.Behavior> rows =
                S3kDriverServiceInventory.firstSliceRows();
        assertThrows(IllegalArgumentException.class,
                () -> S3kDriverServiceInventory.validateCompleteFirstSlice(
                        REQUIRED_SERVICE_KEYS,
                        rows.subList(1, rows.size())));
        assertThrows(IllegalArgumentException.class,
                () -> S3kDriverServiceInventory.validateCompleteFirstSlice(
                        REQUIRED_SERVICE_KEYS,
                        java.util.stream.Stream.concat(rows.stream(), rows.stream().limit(1))
                                .toList()));
    }

    @Test
    void inventoryLimitsArePositiveAndBounded() {
        assertEquals(new S3kSmpsReachabilityInventory.InventoryLimits(
                        131_072, 524_288, 16, 256),
                S3kSmpsReachabilityInventory.FIRST_SLICE_LIMITS);
        assertThrows(IllegalArgumentException.class,
                () -> new S3kSmpsReachabilityInventory.InventoryLimits(0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new S3kSmpsReachabilityInventory.InventoryLimits(1, 1, 17, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new S3kSmpsReachabilityInventory.InventoryLimits(1, 1, 1, 257));
        assertThrows(IllegalArgumentException.class,
                () -> new S3kSmpsReachabilityInventory.InventoryLimits(
                        131_073, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new S3kSmpsReachabilityInventory.InventoryLimits(
                        1, 524_289, 1, 1));
    }

    @Test
    void lockedOnDialectOwnsOnlyItsExactCoordinationCommandSpace() {
        assertTrue(S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4
                .ownsCommand(0xE0, -1));
        assertTrue(S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4
                .ownsCommand(0xFF, 0x07));
        assertTrue(!S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4
                .ownsCommand(0xFF, 0x08));
        assertTrue(!S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4
                .ownsCommand(0xDF, -1));
    }

    @Test
    void authenticatedFirstSliceStreamsCloseWithExpectedBehaviorFamilies() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(TestEnvironment.currentRom());

        S3kSmpsReachabilityInventory.InventoryResult collapse =
                S3kSmpsReachabilityInventory.inventoryAll(
                        S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4,
                        "sfx.59.collapse", loader.loadSfx(Sonic3kSfx.COLLAPSE.id),
                        S3kSmpsReachabilityInventory.ExternalEvent.SFX_QUEUE,
                        S3kSmpsReachabilityInventory.FIRST_SLICE_LIMITS);
        assertTrue(collapse.frontiers().isEmpty(), collapse.frontiers()::toString);
        assertContainsBehaviors(collapse,
                "coord.e0.pan", "coord.ec.psg-volume", "coord.ef.voice",
                "coord.f0.modulation", "coord.f3.psg-noise", "coord.f2.stop",
                "coord.f6.jump", "coord.f7.loop", "stream.note");

        S3kSmpsReachabilityInventory.InventoryResult dash =
                S3kSmpsReachabilityInventory.inventoryAll(
                        S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4,
                        "sfx.b6.spindash-release", loader.loadSfx(Sonic3kSfx.DASH.id),
                        S3kSmpsReachabilityInventory.ExternalEvent.SFX_QUEUE,
                        S3kSmpsReachabilityInventory.FIRST_SLICE_LIMITS);
        assertTrue(dash.frontiers().isEmpty(), dash.frontiers()::toString);
        assertContainsBehaviors(dash,
                "coord.ef.voice", "coord.f0.modulation", "coord.f3.psg-noise",
                "coord.f2.stop", "coord.f5.psg-voice", "stream.note");

        S3kSmpsReachabilityInventory.InventoryResult invincibility =
                S3kSmpsReachabilityInventory.inventoryAll(
                        S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4,
                        "music.2c.invincibility",
                        loader.loadMusic(Sonic3kMusic.INVINCIBILITY.id),
                        S3kSmpsReachabilityInventory.ExternalEvent.MUSIC_QUEUE,
                        S3kSmpsReachabilityInventory.FIRST_SLICE_LIMITS);
        assertTrue(invincibility.frontiers().isEmpty(), invincibility.frontiers()::toString);
        assertContainsBehaviors(invincibility,
                "coord.e8.note-fill", "coord.ef.voice", "coord.f0.modulation",
                "coord.f5.psg-voice", "coord.f6.jump", "stream.note");
    }

    @Test
    void unknownCommandsAndCapacityRemainExplicitFrontiers() {
        S3kSmpsReachabilityInventory.InventoryResult unknown = syntheticInventory(
                new byte[] {0, (byte) 0xFF, 0x08});
        assertTrue(unknown.frontiers().stream()
                .anyMatch(frontier -> frontier.reason().contains("unknown S3K FF")));

        S3kSmpsReachabilityInventory.InventoryResult capped =
                S3kSmpsReachabilityInventory.inventoryAll(
                        S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4,
                        "synthetic.cap", new SyntheticData(new byte[] {0, 1, 1, 1, (byte) 0xF2}),
                        S3kSmpsReachabilityInventory.ExternalEvent.SFX_QUEUE,
                        new S3kSmpsReachabilityInventory.InventoryLimits(2, 8, 2, 8));
        assertTrue(capped.frontiers().stream()
                .anyMatch(frontier -> frontier.reason().contains("state cap")));

        S3kSmpsReachabilityInventory.InventoryResult edgeCapped =
                S3kSmpsReachabilityInventory.inventoryAll(
                        S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4,
                        "synthetic.edge-cap",
                        new SyntheticData(new byte[] {0, 1, 1, (byte) 0xF2}),
                        S3kSmpsReachabilityInventory.ExternalEvent.SFX_QUEUE,
                        new S3kSmpsReachabilityInventory.InventoryLimits(8, 1, 2, 8));
        assertEquals(2, edgeCapped.states().size(),
                "an edge rejected at the cap must not publish its target state");
        assertTrue(edgeCapped.frontiers().stream()
                .anyMatch(frontier -> frontier.reason().contains("edge cap")));
    }

    @Test
    void callDepthCapKeepsRecursiveControlFlowExplicit() {
        byte[] stream = new byte[12];
        stream[1] = (byte) 0xF8;
        stream[2] = 6;
        stream[3] = 0;
        stream[4] = (byte) 0xF2;
        stream[6] = (byte) 0xF8;
        stream[7] = 11;
        stream[8] = 0;
        stream[9] = (byte) 0xF9;
        stream[11] = (byte) 0xF2;
        S3kSmpsReachabilityInventory.InventoryResult result =
                S3kSmpsReachabilityInventory.inventoryAll(
                        S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4,
                        "synthetic.call-depth", new SyntheticData(stream),
                        S3kSmpsReachabilityInventory.ExternalEvent.SFX_QUEUE,
                        new S3kSmpsReachabilityInventory.InventoryLimits(32, 64, 1, 8));
        assertTrue(result.frontiers().stream()
                .anyMatch(frontier -> frontier.reason().contains("call depth")));
    }

    @Test
    void shippedReturnUnderflowIsClassifiedInsteadOfDiscarded() {
        S3kSmpsReachabilityInventory.InventoryResult result = syntheticInventory(
                new byte[] {0, (byte) 0xF9});
        assertTrue(result.frontiers().isEmpty(), result.frontiers()::toString);
        assertContainsBehaviors(result, "source-bug.return-underflow");
        S3kSmpsReachabilityInventory.Behavior behavior = result.behaviors().stream()
                .filter(row -> row.key().equals("source-bug.return-underflow"))
                .findFirst().orElseThrow();
        assertEquals(S3kSmpsReachabilityInventory.SourceBehavior.SHIPPED_BUG,
                behavior.sourceBehavior());
    }

    @Test
    void copyMemProducesAProvenBoundedOverlay() {
        byte[] stream = new byte[12];
        stream[1] = (byte) 0xFF;
        stream[2] = 0x03;
        stream[3] = 0x0A;
        stream[4] = 0;
        stream[5] = 2;
        stream[8] = (byte) 0xF2;
        stream[10] = (byte) 0xE8;
        stream[11] = 0x05;

        S3kSmpsReachabilityInventory.InventoryResult result = syntheticInventory(stream);
        assertTrue(result.frontiers().isEmpty(), result.frontiers()::toString);
        assertTrue(result.states().stream().anyMatch(state -> state.pc() == 8
                        && state.overlay().equals(Map.of(6, 0xE8, 7, 0x05))
                        && state.sharedProjection().get("copy_mem_seen") == 1),
                () -> "Missing copied overlay from " + result.states());

        S3kSmpsReachabilityInventory.InventoryResult overflow =
                S3kSmpsReachabilityInventory.inventoryAll(
                        S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4,
                        "synthetic.copy-overflow", new SyntheticData(stream),
                        S3kSmpsReachabilityInventory.ExternalEvent.SFX_QUEUE,
                        new S3kSmpsReachabilityInventory.InventoryLimits(32, 64, 2, 1));
        assertTrue(overflow.frontiers().stream()
                .anyMatch(frontier -> frontier.reason().contains("overlay cap")));
    }

    @Test
    void laterControlFlowReadsTheCopiedStreamOverlay() {
        byte[] stream = new byte[14];
        stream[1] = (byte) 0xFF;
        stream[2] = 0x03;
        stream[3] = 0x0D;
        stream[4] = 0;
        stream[5] = 1;
        stream[6] = 1;
        stream[7] = (byte) 0xF6;
        stream[8] = 6;
        stream[9] = 0;
        stream[13] = (byte) 0xF2;

        S3kSmpsReachabilityInventory.InventoryResult result = syntheticInventory(stream);
        assertTrue(result.frontiers().isEmpty(), result.frontiers()::toString);
        Set<String> keys = result.behaviors().stream()
                .map(S3kSmpsReachabilityInventory.Behavior::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertTrue(keys.contains("coord.f2.stop"), keys::toString);
        assertTrue(!keys.contains("stream.duration"),
                () -> "decoder ignored COPY_MEM overlay: " + keys);
    }

    @Test
    void inventoryRecordsDefensivelyCopyMutableState() {
        java.util.ArrayList<Integer> stack = new java.util.ArrayList<>(List.of(4));
        java.util.HashMap<Integer, Integer> loops = new java.util.HashMap<>(Map.of(0, 2));
        S3kSmpsReachabilityInventory.State state = new S3kSmpsReachabilityInventory.State(
                S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4,
                "synthetic", 0, 1, 1, stack, loops, Map.of(), Map.of(),
                S3kSmpsReachabilityInventory.ExternalEvent.SFX_QUEUE);
        stack.add(8);
        loops.put(1, 3);
        assertEquals(List.of(4), state.callStack());
        assertEquals(Map.of(0, 2), state.loopCounters());
        assertThrows(UnsupportedOperationException.class, () -> state.callStack().add(9));
    }

    @Test
    void canonicalFirstSliceArtifactIsDeterministicAndMatchesTheAuthenticatedRom()
            throws Exception {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(TestEnvironment.currentRom());
        Map<String, S3kSmpsReachabilityInventory.InventoryResult> streams =
                new LinkedHashMap<>();
        streams.put("sfx.59.collapse", inventory(loader.loadSfx(Sonic3kSfx.COLLAPSE.id),
                "sfx.59.collapse", S3kSmpsReachabilityInventory.ExternalEvent.SFX_QUEUE));
        streams.put("sfx.b6.spindash-release", inventory(loader.loadSfx(Sonic3kSfx.DASH.id),
                "sfx.b6.spindash-release", S3kSmpsReachabilityInventory.ExternalEvent.SFX_QUEUE));
        streams.put("music.2c.invincibility",
                inventory(loader.loadMusic(Sonic3kMusic.INVINCIBILITY.id),
                        "music.2c.invincibility",
                        S3kSmpsReachabilityInventory.ExternalEvent.MUSIC_QUEUE));

        S3kDriverServiceInventory.FirstSliceInventory inventory =
                new S3kDriverServiceInventory.FirstSliceInventory(
                        "cfbf98c36c776677290a872547ac47c53d2761d6",
                        S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4,
                        S3kDriverServiceInventory.LOCKED_ON_SOURCE_CONDITIONS,
                        S3kSmpsReachabilityInventory.FIRST_SLICE_LIMITS,
                        streams,
                        S3kDriverServiceInventory.firstSliceRows());
        assertThrows(IllegalArgumentException.class,
                () -> new S3kDriverServiceInventory.FirstSliceInventory(
                        "cfbf98c36c776677290a872547ac47c53d2761d6",
                        S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4,
                        Map.of("SonicDriverVer", "4"),
                        S3kSmpsReachabilityInventory.FIRST_SLICE_LIMITS,
                        streams,
                        S3kDriverServiceInventory.firstSliceRows()));

        String first = S3kDriverServiceInventory.writeCanonicalJson(inventory);
        String second = S3kDriverServiceInventory.writeCanonicalJson(inventory);
        assertEquals(first, second);

        JsonNode document = new ObjectMapper().readTree(first);
        assertEquals("openggf.s3k-smps-first-slice-inventory.v1",
                document.path("schema").asText());
        assertEquals(0, document.path("body").path("frontier_count").asInt());
        assertEquals(3, document.path("body").path("streams").size());
        String canonicalBody = new ObjectMapper().writeValueAsString(document.path("body"));
        assertEquals(sha256(canonicalBody), document.path("body_sha256").asText());

        Path artifact = Path.of("docs/architecture/research/audio/"
                + "s3k-smps-first-slice-inventory-v1.json");
        assertEquals(Files.readString(artifact, StandardCharsets.UTF_8), first);
        String output = System.getProperty("s3k.inventory.output");
        if (output != null) {
            Files.writeString(Path.of(output), first, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        }
    }

    private static S3kSmpsReachabilityInventory.InventoryResult inventory(
            AbstractSmpsData data,
            String key,
            S3kSmpsReachabilityInventory.ExternalEvent event) {
        return S3kSmpsReachabilityInventory.inventoryAll(
                S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4,
                key,
                data,
                event,
                S3kSmpsReachabilityInventory.FIRST_SLICE_LIMITS);
    }

    private static String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static S3kSmpsReachabilityInventory.InventoryResult syntheticInventory(byte[] bytes) {
        return S3kSmpsReachabilityInventory.inventoryAll(
                S3kSmpsReachabilityInventory.Dialect.LOCKED_ON_S3K_V4,
                "synthetic", new SyntheticData(bytes),
                S3kSmpsReachabilityInventory.ExternalEvent.SFX_QUEUE,
                new S3kSmpsReachabilityInventory.InventoryLimits(64, 128, 4, 16));
    }

    private static void assertContainsBehaviors(
            S3kSmpsReachabilityInventory.InventoryResult inventory,
            String... keys) {
        Set<String> actual = inventory.behaviors().stream()
                .map(S3kSmpsReachabilityInventory.Behavior::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (String key : keys) {
            assertTrue(actual.contains(key), () -> "Missing " + key + " from " + actual);
        }
    }

    private static final class SyntheticData extends AbstractSmpsData {
        private SyntheticData(byte[] data) {
            super(data, 0);
        }

        @Override
        protected void parseHeader() {
            fmPointers = data.length > 1 ? new int[] {1} : new int[0];
            psgPointers = new int[0];
        }

        @Override
        public byte[] getVoice(int voiceId) {
            return new byte[0];
        }

        @Override
        public byte[] getPsgEnvelope(int id) {
            return new byte[0];
        }

        @Override
        public int read16(int offset) {
            return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
        }

        @Override
        public int getBaseNoteOffset() {
            return 0;
        }
    }
}

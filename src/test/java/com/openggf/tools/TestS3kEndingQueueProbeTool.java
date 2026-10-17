package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.sonic3k.resources.S3kKosModuleDescriptor;
import com.openggf.game.sonic3k.resources.S3kKosModuleSnapshot;
import com.openggf.game.timing.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kEndingQueueProbeTool {
    private String[] args(String product, String window) {
        return new String[]{"--rom", "original.gen", "--out-dir", "new-output", "--product", product,
                "--region", "ntsc", "--progress", "+2", "--level-window", window};
    }

    @Test void acceptsOnlyDeclaredTwoProductsAndAnExplicitBoundedWindow() {
        for (String product : new String[]{"sonic-solo", "tails-solo"}) {
            var args = S3kEndingQueueProbeTool.Arguments.parse(args(product, "4400:4446"));
            assertEquals(4400, args.first()); assertEquals(4446, args.last());
            assertEquals("3333333", args.sampler().emeraldStates());
            assertEquals("", args.sampler().settings().sidekickCharacter());
            assertNull(args.sampler().spots());
        }
        for (String product : new String[]{"sonic-tails", "knuckles", "sonic"}) {
            assertThrows(IllegalArgumentException.class, () -> S3kEndingQueueProbeTool.Arguments.parse(args(product, "4400:4446")));
        }
        for (String window : new String[]{"", "auto", "0:1", "4446:4400", "1:129", "59999:60000", " 4400:4446"}) {
            assertThrows(IllegalArgumentException.class, () -> S3kEndingQueueProbeTool.Arguments.parse(args("sonic-solo", window)));
        }
        for (int index : new int[]{7, 9}) {
            var invalid = args("sonic-solo", "4400:4446"); invalid[index] = "unsupported";
            assertThrows(IllegalArgumentException.class, () -> S3kEndingQueueProbeTool.Arguments.parse(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> S3kEndingQueueProbeTool.Arguments.parse(new String[]{"--rom", "one", "--rom", "two"}));
    }

    @Test void projectionRetainsRealHandleIdentityAndParentChildMetadataWithoutPreparedBytes() throws Exception {
        byte[] secret = "never-export-this-payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var handle = new HardwareWorkHandle(HardwareWorkKind.KOS_MODULE_QUEUE, 7, "sha256:parent");
        var child = new HardwareWorkHandle(HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, 12, "sha256:actual-submission");
        var preparation = new S3kKosModuleSnapshot(new S3kKosModuleDescriptor(0x1CFB44, 88, 0, 0x8000, 4), secret,
                2, 0x400, child, 20, secret, false);
        var job = new HardwareTimingJob.Snapshot(handle.kind(), 0x1CFB44, 88, 0, 0x8000, "KosM", 4, false,
                HardwareWorkFeatures.EMPTY, handle, preparation, secret, false, false, true, false,
                4, 2, Set.of(HardwareServiceBoundary.PRE_MAIN_LOOP), LoadTimeDecisionSource.MEASURED, "profile-test");
        var projection = S3kEndingQueueProbeTool.projectJob(job);
        assertEquals(7, projection.handle().ordinal());
        assertEquals("sha256:parent", projection.handle().submissionFingerprint());
        assertEquals(12, projection.module().activeChild().ordinal());
        assertEquals("sha256:actual-submission", projection.module().activeChild().submissionFingerprint());
        assertEquals(2, projection.remainingServiceFrames());
        assertEquals(List.of("PRE_MAIN_LOOP"), projection.eligibleBoundaries());
        String json = new ObjectMapper().writeValueAsString(projection);
        for (String forbidden : new String[]{"preparedPayload", "preparationSnapshot", "decoder", "archive", "output",
                Base64.getEncoder().encodeToString(secret), "never-export-this-payload"}) assertFalse(json.contains(forbidden), forbidden);
        assertArrayEquals(secret, job.preparedPayload());
        assertArrayEquals(secret, preparation.archive());
        assertFalse(job.ready()); assertEquals(2, job.remainingServiceFrames());
    }

    @Test void recordedAdmissionIsRejectedWithoutChangingAnyPolicy() {
        EnumMap<HardwareWorkKind, HardwareReadinessAdmissionPolicy> policies = new EnumMap<>(HardwareWorkKind.class);
        for (var kind : HardwareWorkKind.values()) policies.put(kind, HardwareReadinessAdmissionPolicy.LIVE);
        var live = new HardwareTimingSnapshot(Map.of(), List.of(), policies, false, false, HardwareServiceBoundary.PRE_MAIN_LOOP);
        assertDoesNotThrow(() -> S3kEndingQueueProbeTool.requireLive(live));
        policies.put(HardwareWorkKind.KOS_MODULE_QUEUE, HardwareReadinessAdmissionPolicy.RECORDED);
        var recorded = new HardwareTimingSnapshot(Map.of(), List.of(), policies, true, false, HardwareServiceBoundary.POST_OBJECTS);
        assertThrows(IllegalStateException.class, () -> S3kEndingQueueProbeTool.requireLive(recorded));
        assertEquals(HardwareReadinessAdmissionPolicy.RECORDED, recorded.admissionPolicies().get(HardwareWorkKind.KOS_MODULE_QUEUE));
        assertEquals(HardwareReadinessAdmissionPolicy.LIVE, live.admissionPolicies().get(HardwareWorkKind.KOS_MODULE_QUEUE));
    }
}

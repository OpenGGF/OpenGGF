package com.openggf.tools.audio.completerun;

import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Metadata;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ProducerKind;

/** Metadata uses frozen profile declarations; this is not executable artifact attestation. */
public final class CompleteRunAudioProducerMetadata {
    private CompleteRunAudioProducerMetadata() { }

    public static Metadata reference(CompleteRunAudioProducer.Request request, String fixedProfileId)
            throws Exception {
        java.util.Objects.requireNonNull(request, "reference producer request");
        if (request.producerKind() != ProducerKind.REFERENCE || !fixedProfileId.equals(request.profileId())) {
            throw new IllegalArgumentException("reference request differs from its fixed binding");
        }
        if (!CompleteRunAudioProducerRegistry.tryLoadProfile(fixedProfileId)) {
            throw new IllegalArgumentException("unknown fixed reference profile");
        }
        var profile = CompleteRunAudioProducerRegistry.requirePinned(fixedProfileId, ProducerKind.REFERENCE);
        var metadata = pinned(profile, ProducerKind.REFERENCE);
        CompleteRunAudioTool.verifyReferenceHome(request.referenceHome(), profile);
        return metadata;
    }

    public static Metadata pinned(CompleteRunAudioProfile profile, ProducerKind kind) {
        var binding = profile.producerBindings().get(kind);
        if (binding instanceof CompleteRunAudioTrace.UnavailableProducerBinding unavailable) {
            throw new IllegalStateException("producer metadata is unavailable: " + unavailable.reason());
        }
        if (!(binding instanceof CompleteRunAudioTrace.PinnedProducerBinding)) {
            throw new IllegalStateException("producer metadata has no pinned binding");
        }
        var metadata = new Metadata(CompleteRunAudioTrace.SCHEMA, profile.id(), profile.fixture(), kind,
                profile.producerRuntimeIdentities().get(kind), profile.observerRuntimeIdentities().get(kind),
                profile.observerProofs().get(kind),
                new CompleteRunAudioTrace.ChunkPolicy(CompleteRunAudioTrace.CHUNK_FRAME_ROWS, "gzip", 0),
                profile.hardwareRoles(), profile.stateInventory(), profile.comparisonLayerInventory(),
                profile.producerObservationInventories().get(kind));
        metadata.validateProfile(profile);
        return metadata;
    }
}

package com.openggf.audio.presentation;

public interface AudioPresentationDependencyResolver {
    DecodedPcm resolvePcm(String assetId);

    SmpsCompositeVoice recreateSmps(PresentationVoiceSnapshot.Smps snapshot);

    /**
     * Rebuilds a creator-supplied streamed override. Only a live installed
     * streamed-music port can vouch for an owner-scoped track key, so resolvers
     * without one reject the restore rather than substituting silence: a mod
     * override that quietly became silence after a rewind would read as an
     * engine audio bug.
     */
    default PresentationVoice recreateStreamed(
            PresentationVoiceSnapshot.Streamed snapshot) {
        throw new IllegalStateException(
                "no streamed-music port for " + snapshot.sourceDescriptor());
    }

    default PresentationVoice recreateVoice(
            AudioPresentationCommand.VoiceDescriptor descriptor) {
        if (descriptor instanceof AudioPresentationCommand.SampleVoiceDescriptor sample) {
            PresentationVoiceSnapshot.Sample snapshot = sample.snapshot();
            return SampleBackedVoice.restore(
                    snapshot, resolvePcm(snapshot.assetId()));
        }
        if (descriptor instanceof AudioPresentationCommand.StreamedVoiceDescriptor streamed) {
            return recreateStreamed(streamed.snapshot());
        }
        return recreateSmps(
                (AudioPresentationCommand.SmpsVoiceDescriptor) descriptor);
    }

    default SmpsCompositeVoice recreateSmps(
            AudioPresentationCommand.SmpsVoiceDescriptor descriptor) {
        throw new IllegalStateException(
                "no cached SMPS music for " + descriptor.sourceDescriptor());
    }
}

package com.openggf.audio.presentation;

public interface AudioPresentationDependencyResolver {
    DecodedPcm resolvePcm(String assetId);

    SmpsCompositeVoice recreateSmps(PresentationVoiceSnapshot.Smps snapshot);

    default PresentationVoice recreateVoice(
            AudioPresentationCommand.VoiceDescriptor descriptor) {
        if (descriptor instanceof AudioPresentationCommand.SampleVoiceDescriptor sample) {
            PresentationVoiceSnapshot.Sample snapshot = sample.snapshot();
            return SampleBackedVoice.restore(
                    snapshot, resolvePcm(snapshot.assetId()));
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

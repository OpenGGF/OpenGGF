package com.openggf.audio.presentation;

public interface AudioPresentationDependencyResolver {
    interface DiagnosticTransaction {
        DiagnosticTransaction NONE = new DiagnosticTransaction() {
            @Override
            public void endPreparation() {
            }

            @Override
            public void commit() {
            }

            @Override
            public void discard() {
            }
        };

        /** Stops associating newly constructed voices with this transaction. */
        void endPreparation();

        /** Publishes every deferred callback in its original cross-observer order. */
        void commit();

        /** Drops deferred callbacks and suppresses cleanup callbacks from its voices. */
        void discard();
    }

    default DiagnosticTransaction beginDiagnosticTransaction() {
        return DiagnosticTransaction.NONE;
    }

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

package com.openggf.audio.presentation;

public interface AudioPresentationDependencyResolver {
    DecodedPcm resolvePcm(String assetId);

    SmpsCompositeVoice recreateSmps(PresentationVoiceSnapshot.Smps snapshot);
}

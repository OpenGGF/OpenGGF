package com.openggf.audio.presentation;

import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.YmServiceTimingProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class TestSmpsSequencerConfigCatalogCopy {
    @Test
    void everyCatalogConfigCopyPreservesYmTimingProfileIdentity() {
        YmServiceTimingProfile profile = YmServiceTimingProfile.of(1,
                new YmServiceTimingProfile.Segment(
                        YmServiceTimingProfile.SegmentKind.KEY_OFF,
                        new YmServiceTimingProfile.Variant(1, 4, true, false,
                                0, YmServiceTimingProfile.PathKind.ORDINARY_NOTE),
                        new long[] { 0 }));
        SmpsSequencerConfig source = new SmpsSequencerConfig.Builder()
                .ymServiceTimingProfile(profile)
                .build();
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());

        assertSame(profile, source.getYmServiceTimingProfile());
        assertSame(profile, SmpsAssetCatalog.copyConfigWithoutHandler(source)
                .getYmServiceTimingProfile());
        assertSame(profile, SmpsAssetCatalog.bindLegacyConfig(
                "s3k", source, false, handlers).getYmServiceTimingProfile());
    }
}

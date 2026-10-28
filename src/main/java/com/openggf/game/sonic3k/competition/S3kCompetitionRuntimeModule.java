package com.openggf.game.sonic3k.competition;

import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;

import java.util.List;
import java.util.Objects;

/** Arena-scoped S3K composition, using the base module's ROM decode and terrain resources. */
public final class S3kCompetitionRuntimeModule extends DelegatingGameModule {
    private final S3kCompetitionLevelInitProfile profile;
    private final ObjectArtProvider art;
    private final ScrollHandlerProvider scroll;
    private final ZoneFeatureProvider features;
    private final com.openggf.audio.GameAudioProfile audio;

    public S3kCompetitionRuntimeModule(GameModule base, S3kCompetitionLevelInitProfile profile,
            ObjectArtProvider art, ScrollHandlerProvider scroll) {
        this(base,profile,art,scroll,new S3kCompetitionZoneFeatures(()->null));
    }

    public S3kCompetitionRuntimeModule(GameModule base, S3kCompetitionLevelInitProfile profile,
            ObjectArtProvider art, ScrollHandlerProvider scroll,ZoneFeatureProvider features) {
        super(base, "engine:s3k-native-competition");
        requireS3kModule(base, "S3K ROM module required");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.art = Objects.requireNonNull(art, "art");
        this.scroll = Objects.requireNonNull(scroll, "scroll");
        this.features = Objects.requireNonNull(features, "features");
        audio = profile.resources() == S3kCompetitionResources.ORIGINAL_S3
                ? new com.openggf.game.sonic3k.audio.Sonic3kAudioProfile(
                        com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile.SONIC_3)
                : base.getAudioProfile();
    }

    /** Assembly precondition shared with the host; never a gameplay policy selector. */
    static void requireS3kModule(GameModule module, String message) {
        if (module.getGameId() != GameId.S3K) throw new IllegalArgumentException(message);
    }

    @Override public com.openggf.game.Game createGame(com.openggf.data.Rom rom) {
        if (profile.resources() != S3kCompetitionResources.ORIGINAL_S3) return super.createGame(rom);
        try { return new S3CompetitionGame(rom); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    @Override public com.openggf.game.Game createGame(com.openggf.game.GameDataSource source) {
        if (profile.resources() != S3kCompetitionResources.ORIGINAL_S3) return super.createGame(source);
        return createGame(source.rom().orElseThrow(() -> new IllegalArgumentException("Native Competition requires ROM bytes")));
    }

    @Override public LevelInitProfile getLevelInitProfile() { return profile; }
    @Override public com.openggf.audio.GameAudioProfile getAudioProfile() { return audio; }
    @Override public ObjectArtProvider getObjectArtProvider() { return art; }
    @Override public ScrollHandlerProvider getScrollHandlerProvider() { return scroll; }
    @Override public ZoneFeatureProvider getZoneFeatureProvider() { return features; }
    @Override public boolean supportsSidekick() { return false; }
    @Override public TitleCardProvider getTitleCardProvider() { return null; }

    @Override public <T> T getGameService(Class<T> type) {
        // Obj_TitleCardInit exits for E..12 before any campaign title/Nemesis admission.
        // The native profile owns publication; unrelated base services still delegate.
        if (type == PlcLifecycleService.class || type == Sonic3kLevelTitlePlcService.class
                || type == Sonic3kTitleCardManager.class) return null;
        return super.getGameService(type);
    }

    @Override public List<RewindSnapshottable<?>> rewindAdapters() {
        return super.rewindAdapters().stream().filter(adapter -> !(adapter instanceof Sonic3kLevelTitlePlcService)).toList();
    }
}

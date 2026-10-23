package com.openggf.game.sonic3k.competition;

import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.TitleCardProvider;
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

    public S3kCompetitionRuntimeModule(GameModule base, S3kCompetitionLevelInitProfile profile,
            ObjectArtProvider art, ScrollHandlerProvider scroll) {
        super(base, "engine:s3k-native-competition");
        if (base.getGameId() != GameId.S3K) throw new IllegalArgumentException("S3K ROM module required");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.art = Objects.requireNonNull(art, "art");
        this.scroll = Objects.requireNonNull(scroll, "scroll");
    }

    @Override public LevelInitProfile getLevelInitProfile() { return profile; }
    @Override public ObjectArtProvider getObjectArtProvider() { return art; }
    @Override public ScrollHandlerProvider getScrollHandlerProvider() { return scroll; }
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

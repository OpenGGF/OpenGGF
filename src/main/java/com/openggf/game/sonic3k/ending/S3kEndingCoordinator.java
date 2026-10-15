package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.game.sonic3k.objects.SongFadeTransitionInstance;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/** Shared sub_5B18E entry, used from the owning S3K level ScreenEvent. */
public final class S3kEndingCoordinator {
    private S3kEndingCoordinator() { }
    public static Optional<S3kEndingState> current(ZoneRuntimeRegistry registry) {
        if (registry == null) return Optional.empty();
        if (registry.current() instanceof EndingZoneRuntimeState state) return Optional.of(state.ending());
        if (registry.current() instanceof SszZoneRuntimeState state) return Optional.of(state.ending());
        return Optional.empty();
    }
    public static S3kEndingState state(ObjectServices services) {
        return current(services.zoneRuntimeRegistry()).orElseThrow(
                () -> new IllegalStateException("The active level has no native ending owner"));
    }
    public static void initializeProgress(ObjectServices services) {
        var state = state(services);
        state.nativePlayer().ensure(services);
        if (!state.initialized()) state.initializeProgress(services.gameState().getEmeraldCount(),
                services.gameState().hasAllSuperEmeralds() ? 7 : 0);
    }
    public static void beginCredits(ObjectServices services) {
        var state = state(services);
        if (state.credits().active()) return;
        initializeProgress(services);
        try {
            state.credits().initialize(services.romReader(),
                    ((S3kZoneRuntimeState) services.zoneRuntimeState()).playerCharacter(), state.progress(),
                    "PAL".equalsIgnoreCase(services.configuration().getString(SonicConfiguration.REGION)));
            services.camera().setY((short) 0); services.camera().setYCopy((short) 0);
            state.art().loadRaw(services, S3kEndingResources.CREDIT_FONT, S3kEndingResources.CREDIT_TILE);
            var provider = (Sonic3kObjectArtProvider) services.levelManager().getGameModule().getObjectArtProvider();
            provider.refreshSheetPatterns(S3kEndingArt.FONT, S3kEndingArt.font(services.rom()));
            provider.ensurePatternsCached(services.graphicsManager(), PatternAtlasRange.OBJECTS.base());
            // loc_5B1D2 writes only these two palette cells; the island palette survives.
            paletteWord(services, 0, 1, 0xEEE);
            paletteWord(services, 1, 1, 0x0EE);
            if(services.zoneRuntimeState() instanceof SszZoneRuntimeState)
                services.objectManager().createDynamicObject(()->SongFadeTransitionInstance.transitionTo(Sonic3kMusic.CREDITS_SK.id));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    public static boolean updateCredits(ObjectServices services) {
        var state = state(services);
        if (!state.credits().active()) { beginCredits(services); return false; }
        state.art().service(services);
        try {
            int previousRoutine=state.credits().routine();
            boolean completed = state.credits().step(services.romReader());
            if(previousRoutine==2&&state.credits().routine()==4)
                state.art().queueModule(services,S3kEndingResources.AND_KNUCKLES,0x3EF); // sub_5B42A
            services.camera().setY((short) (state.credits().scroll() >> 16));
            if(completed)services.levelGamestate().setTimerFrames(0);
            if(completed&&services.zoneRuntimeState() instanceof EndingZoneRuntimeState ending)
                ending.screen().foregroundSignal=true; // loc_5B29E, shared Events_fg_4
            if (completed && services.zoneRuntimeState() instanceof SszZoneRuntimeState ssz) {
                ssz.setEventsFg4(0xFFFF); // loc_5B29E
                if (state.progress() >= 0) beginPostCredits(services); // loc_5B2B8
                else {
                    // loc_5B2CE clears only _unkFAB9. Existing clouds retain
                    // both velocity words and naturally leave the native window.
                    var clouds=state.clouds();
                    clouds.configure(0,clouds.xVelocity(),clouds.yVelocity());
                }
            }
            return completed;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    public static void beginPostCredits(ObjectServices services) {
        var state = state(services);
        if (state.postCreditsStarted()) return;
        var object = services.objectManager().createDynamicObject(S3kPostCreditsObjectInstance::new);
        if (object != null) state.markPostCreditsStarted();
    }
    public static void paletteWord(ObjectServices services, int line, int color, int word) {
        S3kPaletteWriteSupport.applyContiguousPatch(services.paletteOwnershipRegistryOrNull(), services.currentLevel(),
                services.graphicsManager(), "s3k-ending", S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                line, color, new byte[] {(byte) (word >>> 8), (byte) word});
    }
}

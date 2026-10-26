package com.openggf.tools.audio.completerun;

import com.openggf.Engine;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tools.audio.completerun.CompleteRunAudioProducer.Request;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ProducerKind;

import java.nio.file.Files;
import java.util.Objects;

/** Fixed production startup and output-only chip projection for the S1/S2 producers. */
public final class CompleteRunAudioOpenGgfProducerSupport {
    private CompleteRunAudioOpenGgfProducerSupport() { }

    public static void capture(Request request, String fixedProfileId) throws Exception {
        var profile = CompleteRunAudioOpenGgfProducerPreflight.requirePinned(request, fixedProfileId);
        capture(request, profile, CompleteRunAudioProducerRegistry.startup(fixedProfileId));
    }

    private static void capture(Request request, CompleteRunAudioProfile profile,
            CompleteRunAudioProducerRegistry.Startup startup) throws Exception {
        // Dormant until independent artifact attestation is installed. Matching profile
        // declarations alone is not executable attestation and must never enable a binding.
        var metadata = CompleteRunAudioProducerMetadata.pinned(profile, ProducerKind.OPENGGF);
        var projection = new CompleteRunAudioFrameChipProjection(metadata.producerObservationInventory());
        validateInputs(request, profile);
        var movie = new Bk2MovieLoader().load(request.bk2());
        if (movie.getFrameCount() != profile.fixture().bk2RowCount()) {
            throw new IllegalArgumentException("BK2 row count differs from fixed fixture");
        }
        if (Engine.currentGameLoop() != null || SessionManager.getCurrentWorldSession() != null) {
            throw new IllegalStateException("complete-run capture requires an unowned production startup");
        }
        if (!com.openggf.ModSubsystem.current().processCatalog().effective().orderedEnabled().isEmpty()
                || com.openggf.ModSubsystem.current().developmentBaseGame().isPresent()) {
            throw new IllegalStateException("fixed production capture cannot inherit external content");
        }
        EngineContext previous = EngineServices.current();
        var preferences = OwnedTemporaryDirectory.create(request.output().getParent(), ".audio-preferences-");
        try {
            var config = configuration(preferences.path(), request, startup);
            var graphics = new GraphicsManager();
            graphics.initHeadless();
            var context = new EngineContext(config, graphics, previous.audio(), previous.roms(),
                    previous.profiler(), previous.debugOverlay(), previous.playbackDebug(),
                    previous.romDetection(), previous.crossGameFeatures());
            previous.audio().resetState();
            previous.audio().setRom(null);
            previous.roms().close();
            EngineServices.configure(context);
            try {
                var writer = new CompleteRunAudioCaptureStore().writeNew(request.output(), metadata);
                CompleteRunAudioOpenGgfCapture.run(context, movie, profile.fixture(), writer, projection);
            } finally {
                try {
                    context.audio().setRom(null);
                    context.roms().close();
                } finally {
                    try { graphics.cleanup(); }
                    finally { EngineServices.configure(previous); }
                }
            }
        } finally { preferences.delete(); }
    }

    static SonicConfigurationService configuration(java.nio.file.Path preferences, Request request,
            CompleteRunAudioProducerRegistry.Startup startup) {
        var config = SonicConfigurationService.createStandalone(preferences);
        config.resetToDefaults();
        config.setConfigValue(SonicConfiguration.DEFAULT_ROM, startup.gameId());
        config.setConfigValue(startup.romKey(), request.rom().toString());
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, true);
        config.setConfigValue(SonicConfiguration.AUDIO_FM_CORE, "accurate");
        config.setConfigValue(SonicConfiguration.FPS, 60);
        config.setConfigValue(SonicConfiguration.SHOW_LEGAL_DISCLAIMER_ON_STARTUP, false);
        config.setConfigValue(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP, false);
        config.setConfigValue(SonicConfiguration.TITLE_SCREEN_ON_STARTUP, true);
        config.setConfigValue(SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, startup.sidekick());
        return config;
    }

    private static void validateInputs(Request request, CompleteRunAudioProfile profile) throws Exception {
        Objects.requireNonNull(request, "producer request");
        var fixture = profile.fixture();
        CompleteRunAudioFiles.requireDigest(CompleteRunAudioFiles.file(request.rom(), "ROM"),
                "SHA-1", fixture.romSha1(), "ROM");
        CompleteRunAudioFiles.requireDigest(CompleteRunAudioFiles.file(request.bk2(), "BK2"),
                "SHA-256", fixture.bk2Sha256(), "BK2");
        CompleteRunAudioFiles.requireDigest(CompleteRunAudioFiles.file(request.runManifest(), "run manifest"),
                "SHA-256", fixture.runManifestSha256(), "run manifest");
        var output = CompleteRunAudioFiles.absolute(request.output(), "capture output");
        if (Files.exists(output, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new java.nio.file.FileAlreadyExistsException(output.toString());
        }
        CompleteRunAudioFiles.directory(output.getParent(), "capture output parent");
    }
}

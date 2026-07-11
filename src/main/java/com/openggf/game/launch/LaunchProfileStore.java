package com.openggf.game.launch;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.patch.ResolutionContext;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

@com.openggf.game.ModApi
public class LaunchProfileStore {
    private static final Logger LOGGER = Logger.getLogger(LaunchProfileStore.class.getName());

    private final SonicConfigurationService configService;
    private final Map<MasterTitleScreen.GameEntry, List<String>> patchMainCharacters;

    public LaunchProfileStore(SonicConfigurationService configService) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.patchMainCharacters = emptyAvailability();
    }

    public LaunchProfileStore(SonicConfigurationService configService,
            ModuleResolutionService resolutionService, ResolutionContext resolutionContext) {
        this.configService = Objects.requireNonNull(configService, "configService");
        Objects.requireNonNull(resolutionService, "resolutionService");
        Objects.requireNonNull(resolutionContext, "resolutionContext");
        this.patchMainCharacters = snapshotAvailability(resolutionService, resolutionContext);
    }

    private static Map<MasterTitleScreen.GameEntry, List<String>> snapshotAvailability(
            ModuleResolutionService resolutionService, ResolutionContext resolutionContext) {
        Map<MasterTitleScreen.GameEntry, List<String>> snapshot;
        int failuresBefore;
        do {
            failuresBefore = resolutionContext.failedOwners().size();
            snapshot = queryAvailability(resolutionService, resolutionContext);
        } while (resolutionContext.failedOwners().size() != failuresBefore);
        return snapshot;
    }

    private static Map<MasterTitleScreen.GameEntry, List<String>> queryAvailability(
            ModuleResolutionService resolutionService, ResolutionContext resolutionContext) {
        EnumMap<MasterTitleScreen.GameEntry, List<String>> availability =
                new EnumMap<>(MasterTitleScreen.GameEntry.class);
        for (MasterTitleScreen.GameEntry entry : MasterTitleScreen.GameEntry.values()) {
            availability.put(entry, List.copyOf(resolutionService.availableMainCharacters(
                    resolutionContext, LaunchProfile.gameId(entry))));
        }
        return Map.copyOf(availability);
    }

    public LaunchProfile load(MasterTitleScreen.GameEntry entry) {
        Keys keys = keysFor(entry);
        String crossGameSource = configService.getString(keys.crossGameSource());
        if (LaunchProfile.gameId(entry).equals(crossGameSource)) {
            LOGGER.warning("Invalid launch profile donor for " + entry.gameId + ": " + crossGameSource
                    + "; replacing with off.");
            crossGameSource = "off";
        }
        return new LaunchProfile(
                configService.getBoolean(keys.rewind()),
                crossGameSource,
                configService.getBoolean(keys.debugTools()),
                configService.getString(keys.aspect()),
                configService.getString(keys.mainCharacter()),
                configService.getString(keys.sidekick()))
                .sanitizedFor(entry, patchMainCharacters(entry));
    }

    public void save(MasterTitleScreen.GameEntry entry, LaunchProfile profile) {
        Objects.requireNonNull(profile, "profile");
        Keys keys = keysFor(entry);
        LaunchProfile sanitized = sanitize(profile, entry);
        configService.setConfigValue(keys.rewind(), sanitized.rewind());
        configService.setConfigValue(keys.crossGameSource(), sanitized.crossGameSource());
        configService.setConfigValue(keys.debugTools(), sanitized.debugTools());
        configService.setConfigValue(keys.aspect(), sanitized.aspect());
        configService.setConfigValue(keys.mainCharacter(), sanitized.mainCharacter());
        configService.setConfigValue(keys.sidekick(), sanitized.sidekick());
        configService.saveConfig();
    }

    public LaunchProfile sanitize(LaunchProfile profile, MasterTitleScreen.GameEntry entry) {
        return Objects.requireNonNull(profile, "profile")
                .sanitizedFor(entry, patchMainCharacters(entry));
    }

    public LaunchProfile withNext(LaunchProfile profile, LaunchProfile.Row row,
            MasterTitleScreen.GameEntry entry) {
        return Objects.requireNonNull(profile, "profile")
                .withNext(row, entry, patchMainCharacters(entry));
    }

    public LaunchProfile withPrevious(LaunchProfile profile, LaunchProfile.Row row,
            MasterTitleScreen.GameEntry entry) {
        return Objects.requireNonNull(profile, "profile")
                .withPrevious(row, entry, patchMainCharacters(entry));
    }

    public boolean isNonStandard(LaunchProfile profile, LaunchProfile.Row row,
            MasterTitleScreen.GameEntry entry) {
        return Objects.requireNonNull(profile, "profile")
                .isNonStandard(row, entry, patchMainCharacters(entry));
    }

    public boolean isCharacterPairStandard(LaunchProfile profile,
            MasterTitleScreen.GameEntry entry) {
        return Objects.requireNonNull(profile, "profile")
                .isCharacterPairStandard(entry, patchMainCharacters(entry));
    }

    private List<String> patchMainCharacters(MasterTitleScreen.GameEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return patchMainCharacters.getOrDefault(entry, List.of());
    }

    private static Map<MasterTitleScreen.GameEntry, List<String>> emptyAvailability() {
        EnumMap<MasterTitleScreen.GameEntry, List<String>> availability =
                new EnumMap<>(MasterTitleScreen.GameEntry.class);
        for (MasterTitleScreen.GameEntry entry : MasterTitleScreen.GameEntry.values()) {
            availability.put(entry, List.of());
        }
        return Map.copyOf(availability);
    }

    private static Keys keysFor(MasterTitleScreen.GameEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return switch (entry) {
            case SONIC_1 -> new Keys(
                    SonicConfiguration.LAUNCH_S1_REWIND,
                    SonicConfiguration.LAUNCH_S1_CROSS_GAME_SOURCE,
                    SonicConfiguration.LAUNCH_S1_DEBUG_TOOLS,
                    SonicConfiguration.LAUNCH_S1_ASPECT,
                    SonicConfiguration.LAUNCH_S1_MAIN_CHARACTER,
                    SonicConfiguration.LAUNCH_S1_SIDEKICK);
            case SONIC_2 -> new Keys(
                    SonicConfiguration.LAUNCH_S2_REWIND,
                    SonicConfiguration.LAUNCH_S2_CROSS_GAME_SOURCE,
                    SonicConfiguration.LAUNCH_S2_DEBUG_TOOLS,
                    SonicConfiguration.LAUNCH_S2_ASPECT,
                    SonicConfiguration.LAUNCH_S2_MAIN_CHARACTER,
                    SonicConfiguration.LAUNCH_S2_SIDEKICK);
            case SONIC_3K -> new Keys(
                    SonicConfiguration.LAUNCH_S3K_REWIND,
                    SonicConfiguration.LAUNCH_S3K_CROSS_GAME_SOURCE,
                    SonicConfiguration.LAUNCH_S3K_DEBUG_TOOLS,
                    SonicConfiguration.LAUNCH_S3K_ASPECT,
                    SonicConfiguration.LAUNCH_S3K_MAIN_CHARACTER,
                    SonicConfiguration.LAUNCH_S3K_SIDEKICK);
        };
    }

    private record Keys(
            SonicConfiguration rewind,
            SonicConfiguration crossGameSource,
            SonicConfiguration debugTools,
            SonicConfiguration aspect,
            SonicConfiguration mainCharacter,
            SonicConfiguration sidekick) {
    }
}

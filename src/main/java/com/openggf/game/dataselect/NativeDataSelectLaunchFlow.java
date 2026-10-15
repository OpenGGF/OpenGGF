package com.openggf.game.dataselect;

import com.openggf.GameLoop;
import com.openggf.game.CharacterKey;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.ZoneKey;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.GameplayTeamAvailability;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.save.*;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Native save-menu launch owner shared by the window and headless presentation hosts. */
public final class NativeDataSelectLaunchFlow {
    private NativeDataSelectLaunchFlow() { }

    /** Platform-specific error routing and manager attachment; gameplay/save decisions stay here. */
    public interface Host {
        ModuleResolutionService.PreparedLaunch prepareLaunch();
        GameModule resolve(ModuleResolutionService.PreparedLaunch launch, GameModule root,
                           GameplayLaunchRequest request);
        void initialize(GameplayModeContext gameplay);
        RuntimeException unavailableCharacter(String owner, String message, RuntimeException cause);
    }

    public static void launch(DataSelectAction action, EngineContext services, GameLoop loop, Host host) {
        SessionSaveRequests.flushPendingSaves();
        GameModule module = SessionManager.requireCurrentGameModule();
        SaveManager saves = new SaveManager(SavePaths.root());
        Map<String, Object> payload = loadPayload(module, action, saves);
        SaveSessionContext save = saveContext(module, action, payload);
        String game = SessionManager.getCurrentWorldSession().rootGameModule().getGameId().code();
        var prepared = host.prepareLaunch();
        List<String> available = new ArrayList<>(stockCharacters(game));
        available.addAll(services.moduleResolutionService().availableMainCharacters(prepared, game));
        if (CrossGameFeatureProvider.isActive()) {
            available.addAll(stockCharacters(services.crossGameFeatures().getDonorGameId()));
        }
        var frontendPatterns = com.openggf.game.internal.FrontendPatternHandoffAccess.capture(
                SessionManager.getCurrentGameplayMode());
        GameplayModeContext gameplay = openSession(save, available, prepared, host);
        if (gameplay == null) return;
        host.initialize(gameplay);
        com.openggf.game.internal.FrontendPatternHandoffAccess.transfer(frontendPatterns, gameplay);
        try {
            gameplay.getLevelManager().loadZoneAndActForFreshRuntime(action.zone(), action.act());
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to load zone " + action.zone() + " act " + action.act()
                    + " from data select", failure);
        }
        com.openggf.game.internal.FrontendPatternHandoffAccess.installed(gameplay);
        restoreProgress(gameplay, payload);
        loop.setGameMode(GameMode.LEVEL);
        saveReason(action.type()).ifPresent(loop::requestSaveForCurrentSession);
    }

    public static GameplayModeContext openSession(SaveSessionContext save, List<String> available,
            ModuleResolutionService.PreparedLaunch prepared, Host host) {
        var previous = SessionManager.getCurrentWorldSession();
        GameModule root = previous.rootGameModule();
        var dataSource = previous.getDataSource();
        String game = root.getGameId().code();
        SaveSessionContext sanitized = GameplayTeamAvailability.sanitizeForLaunch(save, game, available);
        SelectedTeam team = sanitized.selectedTeam();
        GameModule resolved = host.resolve(prepared, root,
                new GameplayLaunchRequest(game, team.mainCharacter(), team.sidekicks()));
        if (resolved == null) return null;
        ZoneKey destination = resolved.getZoneRegistry().zoneKey(sanitized.startZone());
        GameplayLaunchTeam launchTeam = resolved.getGameplayPolicyProvider().launchTeam(destination)
                .orElseGet(() -> new GameplayLaunchTeam(character(team.mainCharacter()),
                        team.sidekicks().stream().map(NativeDataSelectLaunchFlow::character).toList()));
        SaveSessionContext launchContext;
        try {
            launchContext = GameplayTeamAvailability.requireForLaunch(sanitized, launchTeam,
                    resolved.getPlayableCharacterRegistry());
        } catch (GameplayTeamBootstrap.UnavailableRequiredCharacter unavailable) {
            String owner = destination instanceof ZoneKey.Mod mod ? mod.ownerModId()
                    : unavailable.key().ownerModId().orElse("engine");
            throw host.unavailableCharacter(owner, unavailable.getMessage(), unavailable);
        }
        return SessionManager.openGameplaySession(root, resolved, dataSource, launchContext);
    }

    public static List<String> stockCharacters(String game) {
        return switch (game) {
            case "s1" -> List.of("sonic");
            case "s2" -> List.of("sonic", "tails");
            case "s3k" -> List.of("sonic", "tails", "knuckles");
            default -> List.of("sonic");
        };
    }

    private static CharacterKey character(String persisted) {
        String canonical = persisted.equalsIgnoreCase("sonic") || persisted.equalsIgnoreCase("tails")
                || persisted.equalsIgnoreCase("knuckles") ? persisted.toLowerCase(java.util.Locale.ROOT) : persisted;
        return CharacterKey.parsePersisted(canonical);
    }

    public static Optional<SaveReason> saveReason(DataSelectActionType type) {
        return switch (type) {
            case NEW_SLOT_START -> Optional.of(SaveReason.NEW_SLOT_START);
            case LOAD_SLOT -> Optional.of(SaveReason.EXISTING_SLOT_LOAD);
            case CLEAR_RESTART -> Optional.of(SaveReason.CLEAR_RESTART_COMMIT);
            case NONE, NO_SAVE_START, DELETE_SLOT -> Optional.empty();
        };
    }

    public static SaveSessionContext saveContext(GameModule module, DataSelectAction action,
                                                 Map<String, Object> payload) {
        SelectedTeam team = payload == null ? action.team() : teamFromPayload(payload, action.team());
        SaveSessionContext context = action.slot() > 0
                ? SaveSessionContext.forSlot(module.getGameCode(), action.slot(), team, action.zone(), action.act())
                : SaveSessionContext.noSave(module.getGameCode(), team, action.zone(), action.act());
        if (payload != null && Boolean.TRUE.equals(payload.get("clear"))) context.markClear();
        return context;
    }

    public static Map<String, Object> loadPayload(GameModule module, DataSelectAction action, SaveManager saves) {
        if (action.slot() <= 0) return null;
        return switch (action.type()) {
            case LOAD_SLOT, CLEAR_RESTART -> {
                try {
                    SaveSlotSummary summary = saves.readSlotSummary(module.getGameCode(), action.slot());
                    yield summary.isLoadable() ? summary.payload() : null;
                } catch (IOException failure) {
                    throw new IllegalStateException("Failed to read save slot " + action.slot()
                            + " for data select launch", failure);
                }
            }
            case NONE, NO_SAVE_START, NEW_SLOT_START, DELETE_SLOT -> null;
        };
    }

    public static void restoreProgress(GameplayModeContext gameplay, Map<String, Object> payload) {
        if (gameplay == null || payload == null) return;
        var state = gameplay.getGameStateManager();
        int lives = integer(payload, "lives", state.getLives());
        int continues = integer(payload, "continues", state.getContinues());
        var provider = gameplay.getWorldSession() == null ? null
                : gameplay.getWorldSession().getGameModule().getSaveSnapshotProvider();
        if (provider == null || !provider.restoreProgress(state, lives, continues, payload)) {
            state.restoreSaveProgress(lives, continues, integers(payload.get("chaosEmeralds")),
                    integers(payload.get("superEmeralds")),
                    payload.get("emeraldsConverted") instanceof Boolean converted ? converted : null);
        }
    }

    private static SelectedTeam teamFromPayload(Map<String, Object> payload, SelectedTeam fallback) {
        if (!(payload.get("mainCharacter") instanceof String main)) return fallback;
        Object raw = payload.get("sidekicks");
        return new SelectedTeam(main, raw instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of());
    }

    private static int integer(Map<String, Object> payload, String key, int fallback) {
        return payload.get(key) instanceof Number value ? value.intValue() : fallback;
    }

    private static List<Integer> integers(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream().filter(Number.class::isInstance).map(Number.class::cast).map(Number::intValue).toList();
    }
}

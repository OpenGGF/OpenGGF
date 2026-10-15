package com.openggf.game.launch;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.MasterTitleEntry;
import com.openggf.game.MasterTitleScreen.GameEntry;
import com.openggf.game.ZoneKey;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Tracks only interactive stock launches, independently of suspended and replay sessions. */
public final class HubGameplayProgress {
    private final HubProgressStore store;
    private GameEntry game;
    private int zone = -1, act = -1;
    private String character;

    public HubGameplayProgress(SonicConfigurationService config) { this(new HubProgressStore(config)); }
    HubGameplayProgress(HubProgressStore store) { this.store = store; }

    public void prepare(MasterTitleEntry.Launch launch, boolean programmatic,
            MasterTitleLaunchCoordinator coordinator, Supplier<GameModule> module,
            BiConsumer<Integer, Integer> startLevel) {
        game = !programmatic && launch != null && launch.entry() instanceof MasterTitleEntry.Stock stock
                ? stock.game() : null;
        zone = act = -1;
        // Programmatic launches already own the pending callback (trace/recording bootstrap).
        if (game == null || launch.action() != MasterTitleEntry.Action.CONTINUE) return;
        GameEntry selected = game;
        store.load(selected).ifPresent(progress -> coordinator.setPendingLaunchCallback(() -> {
            if (progress.character().equals(store.currentCharacter())
                    && validRuntime(module.get(), selected, progress.zone(), progress.act()))
                startLevel.accept(progress.zone(), progress.act());
        }));
    }

    public void observe(GameModule module, int zone, int act, boolean ordinaryGameplay) {
        observe(module, zone, act, store.currentCharacter(), ordinaryGameplay);
    }

    public void observe(GameModule module, int zone, int act, String character, boolean ordinaryGameplay) {
        if (!ordinaryGameplay || game == null || this.zone == zone && this.act == act
                && java.util.Objects.equals(this.character, character)
                || !validRuntime(module, game, zone, act)) return;
        this.zone = zone;
        this.act = act;
        this.character = character;
        store.reached(game, zone, act, character);
    }

    public void end() { game = null; }

    private static boolean validRuntime(GameModule module, GameEntry game, int zone, int act) {
        if (module == null || !game.gameId.equals(module.getGameCode()) || !HubProgressStore.valid(game, zone, act))
            return false;
        var registry = module.getZoneRegistry();
        return registry != null && zone < registry.getZoneCount() && act < registry.getActCount(zone)
                && registry.zoneKey(zone) instanceof ZoneKey.Stock;
    }
}

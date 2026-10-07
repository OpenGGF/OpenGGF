package survivors;

import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.level.Level;
import com.openggf.mods.code.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Sonic Survivors: a bounce-driven survivors roguelike over Sonic 2. Every route zone's acts
 * become walled arenas (see {@link Stages}); the {@link Stage} controller runs each one. The
 * module keeps the run ({@link RunState}) across level loads and the {@link Profile} on disk.
 */
public final class SurvivorsMod implements GgfMod {
    static final String ID = "sonic-survivors";
    /** Set to pin every run's random stream (decimal or 0x hex), for tests and reproducing a run. */
    static final String SEED_PROPERTY = "sonic-survivors.seed";

    @Override public void register(ModContext context) {
        context.registerObject("stage", (spawn, registry) -> new Stage(spawn));
        context.registerObject("enemy", (spawn, registry) -> new Enemy(spawn));
        context.registerObject("boss", (spawn, registry) -> new Boss(spawn));
        context.registerObject("shot", (spawn, registry) -> new Shot(spawn));
        context.registerObject("pickup", (spawn, registry) -> new Pickup(spawn));
        context.registerGamePatch(new Patch());
    }

    static long seed() {
        String pinned = System.getProperty(SEED_PROPERTY);
        return pinned != null ? Long.decode(pinned.trim()) : java.util.concurrent.ThreadLocalRandom.current().nextLong();
    }

    public static final class Patch implements GamePatch {
        @Override public String id() { return ID + ":arenas"; }
        @Override public String displayName() { return "Sonic Survivors"; }
        @Override public String baseGameId() { return "s2"; }
        /** Both stock playable characters can lead a solo arena run. */
        @Override public boolean activatesFor(GameplayLaunchRequest request) {
            return request.gameId().equals("s2") && (request.mainCharacter().equals("sonic") || request.mainCharacter().equals("tails"));
        }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.S2); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) { return new Module(base); }
    }

    /** No water anywhere in the arenas (Chemical Plant 2 and Aquatic Ruin are fought dry). */
    private record DryWater(WaterDataProvider stock) implements WaterDataProvider {
        @Override public boolean hasWater(int zoneId, int actId, PlayerCharacter character) { return false; }
        @Override public int getStartingWaterLevel(int zoneId, int actId) { return stock.getStartingWaterLevel(zoneId, actId); }
        @Override public com.openggf.level.Palette[] getUnderwaterPalette(Rom rom, int zoneId, int actId, PlayerCharacter character) {
            return stock.getUnderwaterPalette(rom, zoneId, actId, character);
        }
        @Override public DynamicWaterHandler getDynamicHandler(int zoneId, int actId, PlayerCharacter character) { return null; }
    }

    /**
     * The stock zone events' level setup (which installs the zone runtime state that scroll
     * handlers such as Hill Top's read) without any of their per-frame scripting: no camera
     * locks, boss arenas, earthquakes or act-end sequences run inside an arena.
     */
    private record InitOnlyEvents(LevelEventProvider stock) implements LevelEventProvider {
        @Override public void initLevel(int zone, int act) { stock.initLevel(zone, act); }
        @Override public void update() { }
    }

    public static final class Module extends DelegatingGameModule {
        private Game game;
        private boolean active;
        private Arena arena;
        private final RunState run = new RunState();
        private final MenuArt menuArt = new MenuArt();
        private Profile profile;
        private ArenaZones zones;
        private SurvivorsTitle title;
        private InitOnlyEvents events;
        private com.openggf.control.InputHandler liveInput;
        private final LevelInputOverlay overlayInput = input -> {
            liveInput = input;
            return active && (!run.active || run.paused || run.pendingLevels > 0);
        };

        @Override public boolean supportsSidekick() { return false; }
        @Override public boolean isSidekickSuppressedForZone(int zoneId) { return true; }
        // The arena's own menus and game over replace the stock card pair; the death routine
        // still holds the corpse, as it does with a game-over provider installed.
        private final GameOverFlowProvider arenaGameOver = (services, timeOver) -> services.fadeOutMusic();

        public Module(GameModule base) { super(base, ID + ":arenas"); }

        // Super forms are deferred until they have a Survivors-specific design.
        // Omit the controller so native jump, monitor and debug activation stay disabled.
        @Override public SuperStateController createSuperStateController(AbstractPlayableSprite player) {
            return null;
        }

        private Profile profile() {
            if (profile == null) {
                profile = new Profile(com.openggf.game.save.SavePaths.root().resolve(ID).resolve("profile.txt")).load();
            }
            return profile;
        }

        @Override public Game createGame(Rom rom) { return game = super.createGame(rom); }
        @Override public Game createGame(com.openggf.game.GameDataSource source) { return game = super.createGame(source); }

        /** The route stage and act for a level index, or null for Sky Chase and anything off the route. */
        private Arena arenaFor(int index) {
            var registry = base().getZoneRegistry();
            for (int zone = 0; zone < registry.getZoneCount(); zone++) {
                int stage = Stages.stageOfZone(zone);
                if (stage < 0) continue;
                var acts = registry.getLevelDataForZone(zone);
                for (int act = 0; act < acts.size() && act < Stages.acts(stage); act++) {
                    if (acts.get(act).levelIndex() == index) return Arena.of(stage, act);
                }
            }
            return null;
        }

        @Override public Level loadLevelOverride(int index) throws IOException {
            arena = arenaFor(index);
            active = arena != null;
            if (!active) return super.loadLevelOverride(index);
            // A run that reaches this level from somewhere off its route (a debug jump) restarts in camp.
            if (run.active && (run.stage != arena.stage() || run.act != arena.act())) run.end();
            Level original = game.loadLevel(index);
            return com.openggf.level.MutableLevel.snapshot(new ArenaLevel(original, arena));
        }

        @Override public ZoneRegistry getZoneRegistry() {
            ZoneRegistry stock = super.getZoneRegistry();
            if (zones == null || zones.base() != stock) zones = new ArenaZones(stock);
            return zones;
        }

        @Override public LevelEventProvider getLevelEventProvider() {
            LevelEventProvider stock = super.getLevelEventProvider();
            if (!active || stock == null) return stock;
            if (events == null || events.stock() != stock) events = new InitOnlyEvents(stock);
            return events;
        }

        @Override public WaterDataProvider getWaterDataProvider() {
            WaterDataProvider stock = super.getWaterDataProvider();
            return active && stock != null ? new DryWater(stock) : stock;
        }

        @Override public GameOverFlowProvider getGameOverFlowProvider() {
            return active ? arenaGameOver : super.getGameOverFlowProvider();
        }

        @Override public TitleScreenProvider getTitleScreenProvider() {
            TitleScreenProvider stock = super.getTitleScreenProvider();
            if (stock == null) return null;
            if (title == null || title.base() != stock) title = new SurvivorsTitle(stock, this::profile);
            return title;
        }

        /** The arenas are laid out for the 400-pixel 16:9 view. */
        @Override public String requiredDisplayAspect() { return "WIDE_16_9"; }
        /** The title's zone picker is the only way in; the stock level select would skip the route. */
        @Override public boolean suppressesLevelSelect() { return true; }

        @Override public List<com.openggf.game.rewind.RewindSnapshottable<?>> rewindAdapters() {
            var adapters = new java.util.ArrayList<com.openggf.game.rewind.RewindSnapshottable<?>>(super.rewindAdapters());
            adapters.add(run);
            return List.copyOf(adapters);
        }

        @Override public <T> T getGameService(Class<T> type) {
            if (type == LevelInputOverlay.class) return type.cast(overlayInput);
            if (type == MenuArt.class) return type.cast(menuArt);
            if (type == RunState.class) return type.cast(run);
            if (type == Profile.class) return type.cast(profile());
            if (type == Arena.class) return active ? type.cast(arena) : null;
            // Prefer the host-dispatched level input; the title reference covers early handoff.
            if (type == com.openggf.control.InputHandler.class) return type.cast(liveInput != null ? liveInput : title == null ? null : title.input());
            return super.getGameService(type);
        }
    }
}

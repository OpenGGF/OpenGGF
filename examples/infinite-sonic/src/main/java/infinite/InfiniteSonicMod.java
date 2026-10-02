package infinite;

import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.level.Level;
import com.openggf.mods.code.*;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public final class InfiniteSonicMod implements GgfMod {
    @Override public void register(ModContext context) {
        context.registerObject("controller", (spawn, registry) -> new CourseController(spawn));
        context.registerObject("badnik", (spawn, registry) -> new CourseBadnik(spawn, spawn.x()));
        context.registerObject("ring", (spawn, registry) -> new CourseRing(spawn));
        context.registerObject("burst", (spawn, registry) -> new CourseBurst(spawn));
        context.registerGamePatch(new Patch());
    }

    public static final class Patch implements GamePatch {
        @Override public String id() { return "infinite-sonic:terrain"; }
        @Override public String displayName() { return "Infinite Sonic"; }
        @Override public String baseGameId() { return "s1"; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) {
            return request.gameId().equals("s1") && request.mainCharacter().equals("sonic")
                    && request.sidekicks().isEmpty();
        }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.S1); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) { return new Module(base); }
    }

    public static final class Module extends DelegatingGameModule {
        private Game game;
        private TerrainLibrary library;
        private boolean active;
        private final ChallengeClock clock = new ChallengeClock();
        private final LevelEventProvider endlessEvents = new LevelEventProvider() {
            @Override public void initLevel(int zone, int act) { }
            @Override public void update() { }
        };
        public Module(GameModule base) { super(base, "infinite-sonic:terrain"); }
        @Override public Game createGame(Rom rom) { return game = super.createGame(rom); }
        @Override public Game createGame(com.openggf.game.GameDataSource source) {
            return game = super.createGame(source);
        }
        @Override public Level loadLevelOverride(int index) throws IOException {
            active = index == base().getZoneRegistry().getLevelDataForZone(0).getFirst().levelIndex();
            clock.reset();
            if (!active) return super.loadLevelOverride(index);
            Level original = game.loadLevel(index);
            library = new TerrainLibrary(original);
            return com.openggf.level.MutableLevel.snapshot(new InfiniteLevel(original, library));
        }
        // The course draws its own GAME OVER and restart prompt, so replace the
        // stock card pair. Returning a provider (rather than null) still lets the
        // death routine hold the corpse with no restart countdown.
        private final GameOverFlowProvider courseGameOver = (services, timeOver) -> services.fadeOutMusic();
        @Override public GameOverFlowProvider getGameOverFlowProvider() {
            return active ? courseGameOver : super.getGameOverFlowProvider();
        }
        @Override public LevelEventProvider getLevelEventProvider() {
            return active ? endlessEvents : super.getLevelEventProvider();
        }
        @Override public double gameplayAudioPlaybackRate() {
            return active ? clock.multiplier() : super.gameplayAudioPlaybackRate();
        }
        @Override public int gameplayStepsPerFrame() {
            return active ? clock.nextFrameSteps() : super.gameplayStepsPerFrame();
        }
        @Override public List<com.openggf.game.rewind.RewindSnapshottable<?>> rewindAdapters() {
            var adapters = new java.util.ArrayList<com.openggf.game.rewind.RewindSnapshottable<?>>(super.rewindAdapters());
            adapters.add(clock);
            return List.copyOf(adapters);
        }
        @Override public <T> T getGameService(Class<T> type) {
            if (type == ChallengeClock.class) return type.cast(clock);
            return type == TerrainLibrary.class ? type.cast(library) : super.getGameService(type);
        }
    }
}

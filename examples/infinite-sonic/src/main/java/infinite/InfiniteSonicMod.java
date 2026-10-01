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
            if (!active) return super.loadLevelOverride(index);
            Level original = game.loadLevel(index);
            library = new TerrainLibrary(original);
            return com.openggf.level.MutableLevel.snapshot(new InfiniteLevel(original, library));
        }
        @Override public LevelEventProvider getLevelEventProvider() {
            return active ? endlessEvents : super.getLevelEventProvider();
        }
        @Override public <T> T getGameService(Class<T> type) {
            return type == TerrainLibrary.class ? type.cast(library) : super.getGameService(type);
        }
    }
}

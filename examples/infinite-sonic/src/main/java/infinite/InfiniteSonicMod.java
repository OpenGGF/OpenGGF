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
        context.registerObject("monitor", (spawn, registry) -> new CourseMonitor(spawn));
        context.registerObject("hazard", (spawn, registry) -> new CourseHazard(spawn));
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

    /** Set to pin every course to one seed (decimal or 0x hex), for tests and reproducing a course. */
    static final String SEED_PROPERTY = "infinite-sonic.seed";

    /** The pinned seed if {@link #SEED_PROPERTY} is set, otherwise a fresh random one. */
    static long courseSeed() {
        String pinned = System.getProperty(SEED_PROPERTY);
        return pinned != null ? Long.decode(pinned.trim()) : java.util.concurrent.ThreadLocalRandom.current().nextLong();
    }

    /** Registry zones 0-5: GHZ, MZ, SYZ, LZ, SLZ and SBZ. Final Zone (6) and the ending stay stock. */
    static final int COURSE_ZONES = 6;

    /** The course has no water; the stock provider still supplies palettes for other levels. */
    private record DryWater(WaterDataProvider stock) implements WaterDataProvider {
        @Override public boolean hasWater(int zoneId, int actId, PlayerCharacter character) { return false; }
        @Override public int getStartingWaterLevel(int zoneId, int actId) { return stock.getStartingWaterLevel(zoneId, actId); }
        @Override public com.openggf.level.Palette[] getUnderwaterPalette(Rom rom, int zoneId, int actId, PlayerCharacter character) {
            return stock.getUnderwaterPalette(rom, zoneId, actId, character);
        }
        @Override public DynamicWaterHandler getDynamicHandler(int zoneId, int actId, PlayerCharacter character) { return null; }
    }

    /** The stock profiles with the course's lower top running speed; everything else unchanged. */
    private record CoursePhysics(PhysicsProvider stock) implements PhysicsProvider {
        @Override public PhysicsProfile getProfile(String character) { return slower(stock.getProfile(character)); }
        @Override public PhysicsProfile getInitProfile(String character) {
            return slower(stock.getInitProfile(character));
        }
        @Override public PhysicsModifiers getModifiers() { return stock.getModifiers(); }
        @Override public com.openggf.game.rules.GameRules getRules() { return stock.getRules(); }
        private static PhysicsProfile slower(PhysicsProfile p) {
            if (p == null) return null;
            return new PhysicsProfile(p.runAccel(), p.runDecel(), p.friction(),
                    (short) Math.min(p.max(), CourseController.COURSE_MAX_SPEED), p.jump(), p.slopeRunning(),
                    p.slopeRollingUp(), p.slopeRollingDown(), p.rollDecel(), p.minStartRollSpeed(),
                    p.minRollSpeed(), p.maxRoll(), p.rollHeight(), p.runHeight(), p.standXRadius(),
                    p.standYRadius(), p.rollXRadius(), p.rollYRadius(), p.singleFacingBalance(),
                    p.onObjectBalanceShift());
        }
    }

    public static final class Module extends DelegatingGameModule {
        private Game game;
        private TerrainLibrary library;
        private boolean active;
        private int activeIndex = -1;
        private CourseZones zones;
        private final CourseFeatures dryFeatures = new CourseFeatures();
        private final ChallengeClock clock = new ChallengeClock();
        private final CourseSession session = new CourseSession();
        private Leaderboard leaderboard;
        // Identifies the current level load's run on the leaderboard.
        private CourseRun run = new CourseRun(0);
        /** The saved per-zone top scores, read on first use. */
        private Leaderboard leaderboard() {
            if (leaderboard == null) {
                leaderboard = new Leaderboard(com.openggf.game.save.SavePaths.root()
                        .resolve("infinite-sonic").resolve("leaderboard.txt"));
            }
            return leaderboard;
        }
        private final LevelEventProvider endlessEvents = new LevelEventProvider() {
            @Override public void initLevel(int zone, int act) { }
            @Override public void update() { }
        };
        private com.openggf.level.objects.ObjectRegistry stockObjects;
        public Module(GameModule base) { super(base, "infinite-sonic:terrain"); }
        /** The session's object registry, which the course uses to spawn stock platforms. */
        private com.openggf.level.objects.ObjectRegistry stockObjects() {
            if (stockObjects == null) stockObjects = createObjectRegistry();
            return stockObjects;
        }
        @Override public Game createGame(Rom rom) { return game = super.createGame(rom); }
        @Override public Game createGame(com.openggf.game.GameDataSource source) {
            return game = super.createGame(source);
        }
        /** True for every act of GHZ, MZ, SYZ, LZ, SLZ and SBZ (including SBZ3's LZ layout). */
        private boolean courseLevel(int index) {
            var registry = base().getZoneRegistry();
            for (int zone = 0; zone < Math.min(COURSE_ZONES, registry.getZoneCount()); zone++) {
                for (var act : registry.getLevelDataForZone(zone)) {
                    if (act.levelIndex() == index) return true;
                }
            }
            return false;
        }
        @Override public Level loadLevelOverride(int index) throws IOException {
            active = courseLevel(index);
            activeIndex = active ? index : -1;
            library = null;
            // CONTINUE revives in place, so every load starts a fresh run at 1x.
            clock.reset();
            session.reset();
            run = new CourseRun(System.nanoTime());
            if (!active) return super.loadLevelOverride(index);
            Level original = game.loadLevel(index);
            // Every load (a fresh run, RESTART) lays a new course; CONTINUE never reloads, so it keeps its own.
            library = new TerrainLibrary(original, courseSeed());
            return com.openggf.level.MutableLevel.snapshot(new InfiniteLevel(original, library));
        }
        // Sonic's 19px standing radius puts his centre above the seam floor.
        private int courseStartY(int index) {
            return index == activeIndex && library != null ? library.seam() - 19 : -1;
        }
        @Override public ZoneRegistry getZoneRegistry() {
            ZoneRegistry stock = super.getZoneRegistry();
            if (zones == null || zones.base() != stock) zones = new CourseZones(stock, COURSE_ZONES, this::courseStartY);
            return zones;
        }
        private CourseScroll scroll;
        @Override public ScrollHandlerProvider getScrollHandlerProvider() {
            ScrollHandlerProvider stock = super.getScrollHandlerProvider();
            if (stock == null) return null;
            if (scroll == null || scroll.base() != stock) scroll = new CourseScroll(stock, this::courseOrigin);
            return scroll;
        }
        /** The live course window's origin in pixels, which the background scrolls from; 0 off the course. */
        private long courseOrigin() {
            var level = active ? GameServices.levelOrNull() : null;
            var objects = level == null ? null : level.getObjectManager();
            if (objects == null) return 0;
            for (var object : objects.getActiveObjects()) {
                if (object instanceof CourseController course) return course.originPixels();
            }
            return 0;
        }
        @Override public ZoneFeatureProvider getZoneFeatureProvider() {
            return active ? dryFeatures : super.getZoneFeatureProvider();
        }
        /** On the course Sonic's top running speed is {@link CourseController#COURSE_MAX_SPEED}. */
        @Override public PhysicsProvider getPhysicsProvider() {
            PhysicsProvider stock = super.getPhysicsProvider();
            return active && stock != null ? new CoursePhysics(stock) : stock;
        }
        @Override public WaterDataProvider getWaterDataProvider() {
            WaterDataProvider stock = super.getWaterDataProvider();
            return active && stock != null ? new DryWater(stock) : stock;
        }
        // The course draws its own GAME OVER and restart prompt, so replace the
        // stock card pair. Returning a provider (rather than null) still lets the
        // death routine hold the corpse with no restart countdown.
        private final GameOverFlowProvider courseGameOver = (services, timeOver) -> services.fadeOutMusic();
        @Override public GameOverFlowProvider getGameOverFlowProvider() {
            return active ? courseGameOver : super.getGameOverFlowProvider();
        }
        private TitleWordmark title;
        @Override public TitleScreenProvider getTitleScreenProvider() {
            TitleScreenProvider stock = super.getTitleScreenProvider();
            if (stock == null) return null;
            if (title == null || title.base() != stock) {
                title = new TitleWordmark(stock, courseZoneNames(), new LeaderboardScreen(courseZoneNames(), this::leaderboard),
                        this::titleLevel, this::titleCameraY);
            }
            return title;
        }
        // Act 1 of each course zone, read once for the title background; empty if it could not be read.
        private final java.util.Map<Integer, java.util.Optional<Level>> titleLevels = new java.util.HashMap<>();
        private Game titleGame;
        /**
         * A course zone's stock act 1, for its title background. Uses its own {@code Sonic1} reader
         * rather than createGame, which would replace the session's PLC service.
         */
        private Level titleLevel(int zone) {
            if (zone < 0 || zone >= COURSE_ZONES) return null;
            return titleLevels.computeIfAbsent(zone, z -> {
                try {
                    if (titleGame == null) titleGame = new com.openggf.game.sonic1.Sonic1(GameServices.rom().getRom());
                    return java.util.Optional.of(titleGame.loadLevel(base().getZoneRegistry().getLevelDataForZone(z).get(0).levelIndex()));
                } catch (IOException | RuntimeException e) {
                    java.util.logging.Logger.getLogger(InfiniteSonicMod.class.getName())
                            .log(java.util.logging.Level.WARNING, "No title background for zone " + z, e);
                    return java.util.Optional.empty();
                }
            }).orElse(null);
        }
        /** The camera Y the title shows a zone's background for: framing its stock act 1 start. */
        private int titleCameraY(int zone) {
            var act = base().getZoneRegistry().getLevelDataForZone(zone).get(0);
            return Math.max(0, act.startY() - 112);
        }
        private List<String> courseZoneNames() {
            var registry = base().getZoneRegistry();
            var names = new java.util.ArrayList<String>();
            for (int zone = 0; zone < Math.min(COURSE_ZONES, registry.getZoneCount()); zone++) {
                names.add(registry.getZoneName(zone));
            }
            return names;
        }
        // Each course zone is one endless run, so its title card drops "ACT n".
        @Override public boolean showsTitleCardActNumber(int zoneIndex, int actIndex) {
            return zoneIndex >= COURSE_ZONES && super.showsTitleCardActNumber(zoneIndex, actIndex);
        }
        @Override public LevelEventProvider getLevelEventProvider() {
            return active ? endlessEvents : super.getLevelEventProvider();
        }
        /** The course is designed for the 400px 16:9 view: more lookahead at the scroll speed. */
        @Override public String requiredDisplayAspect() { return "WIDE_16_9"; }
        /** The title zone picker is the course's only zone choice; acts are not separate courses. */
        @Override public boolean suppressesLevelSelect() { return true; }
        @Override public double gameplayAudioPlaybackRate() {
            return active ? clock.multiplier() : super.gameplayAudioPlaybackRate();
        }
        @Override public int gameplayStepsPerFrame() {
            return active ? clock.nextFrameSteps() : super.gameplayStepsPerFrame();
        }
        @Override public List<com.openggf.game.rewind.RewindSnapshottable<?>> rewindAdapters() {
            var adapters = new java.util.ArrayList<com.openggf.game.rewind.RewindSnapshottable<?>>(super.rewindAdapters());
            adapters.add(clock);
            adapters.add(session);
            return List.copyOf(adapters);
        }
        @Override public <T> T getGameService(Class<T> type) {
            if (type == ChallengeClock.class) return type.cast(clock);
            if (type == CourseSession.class) return type.cast(session);
            if (type == Leaderboard.class) return type.cast(leaderboard());
            if (type == CourseRun.class) return type.cast(run);
            // The live input the title last saw: the course reads Escape from it to leave.
            if (type == com.openggf.control.InputHandler.class) return title == null ? null : type.cast(title.input());
            if (type == com.openggf.level.objects.ObjectRegistry.class) return type.cast(stockObjects());
            return type == TerrainLibrary.class ? type.cast(library) : super.getGameService(type);
        }
    }
}

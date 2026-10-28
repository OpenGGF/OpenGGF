package echoes;

import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.level.*;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.mods.code.*;
import java.io.IOException;
import java.util.*;

/** Stock Sonic 2 title decorated with the Echo Engine; patched stock acts supply room terrain. */
public final class EchoesMod implements GgfMod {
    public static final String ID = "sonic-echoes";
    @Override public void register(ModContext context) {
        for (String kind : List.of("plate", "gate", "rift", "shard"))
            context.registerObject(kind, (spawn, registry) -> new EchoObject(spawn));
        context.registerObject("display", (spawn, registry) -> new EchoDisplay(spawn));
        context.registerRomObjectArt("sparkle", new RomArtRequest(
                com.openggf.game.sonic2.constants.Sonic2Constants.ART_NEM_INVINCIBILITY_STARS_ADDR, RomArtCompression.NEMESIS, 0,
                com.openggf.game.sonic2.constants.Sonic2Constants.MAP_UNC_INVINCIBILITY_STARS_ADDR, 0, 0, 64));
        context.registerRomObjectArt("button", new RomArtRequest(
                com.openggf.game.sonic2.constants.Sonic2Constants.ART_NEM_BUTTON_ADDR, RomArtCompression.NEMESIS, 0,
                com.openggf.game.sonic2.constants.Sonic2Constants.MAP_UNC_BUTTON_ADDR, 0, 1, 32));
        context.registerRomObjectArt("barrier", new RomArtRequest(
                com.openggf.game.sonic2.constants.Sonic2Constants.ART_NEM_HTZ_VALVE_BARRIER_ADDR, RomArtCompression.NEMESIS, 0,
                com.openggf.game.sonic2.constants.Sonic2Constants.MAP_UNC_BARRIER_ADDR, 0, 1, 128));
        var storage = context.storage();
        var rooms = RoomDefinition.catalogue(context.modAssets());
        context.registerServiceBundle("room", () -> {
            var session = new RoomSession(new EchoProgress(storage).load(), rooms);
            return GameServiceBundle.builder().service(RoomSession.class, session)
                    .service(RoomLauncher.class, new RoomLauncher(session)).service(com.openggf.game.mode.GameplayFrameController.class, session).build();
        });
        context.registerGamePatch(new Patch());
    }
    public static final class Patch implements GamePatch {
        @Override public String id() { return ID + ":rooms"; }
        @Override public String displayName() { return "Sonic Echoes"; }
        @Override public String baseGameId() { return "s2"; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) { return request.gameId().equals("s2") && request.mainCharacter().equals("sonic"); }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.S2); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) { return new Module(base); }
    }
    public static final class Module extends DelegatingGameModule {
        private Game game;
        private boolean active;
        private final RoomSession session;
        private final RoomActions actions;
        private EchoHub title;
        // The engine asks for these every frame. Each wrapper is built once per stock object it
        // decorates, so a long room session does not mint a new owner callback identity per frame
        // (the fault boundary caps live callback identities; ~3400 frames exhausted it).
        private ZoneRegistry stockZones, zones;
        private LevelEventProvider stockEvents, roomEvents;
        private final GameOverFlowProvider roomGameOver = (services, timeOver) -> { };
        public Module(GameModule base) {
            super(base, ID + ":rooms");
            session = Objects.requireNonNull(base.getGameService(RoomSession.class));
            actions = new RoomActions(session);
        }
        @Override public Game createGame(Rom rom) { return game = super.createGame(rom); }
        @Override public Game createGame(GameDataSource source) { return game = super.createGame(source); }
        @Override public com.openggf.game.mode.GameplayFrameController gameplayFrameController() {
            return active ? base().getGameService(com.openggf.game.mode.GameplayFrameController.class) : null;
        }
        @Override public TitleScreenProvider getTitleScreenProvider() {
            var stock=super.getTitleScreenProvider();
            if(stock==null) return null;
            if(title==null||title.base()!=stock) title=new EchoHub(stock,session,base().getGameService(RoomLauncher.class));
            return title;
        }
        @Override public boolean suppressesLevelSelect() { return true; }
        @Override public boolean supportsSidekick() { return false; }
        @Override public String requiredDisplayAspect() { return "WIDE_16_9"; }
        @Override public Level loadLevelOverride(int index) throws IOException {
            var room = session.room();
            int expected = super.getZoneRegistry().getLevelDataForZone(room.zone()).get(room.act()).levelIndex();
            active = index == expected;
            if (!active) return super.loadLevelOverride(index);
            var source = game.loadLevel(index);
            return MutableLevel.snapshot(new DelegatingLevel(source) {
                @Override public List<ObjectSpawn> getObjects() {
                    var result = new ArrayList<ObjectSpawn>();
                    result.add(new ObjectSpawn(room.spawnX(), room.spawnY(), 0, 0, 0, false, room.spawnY(), 255, ID, ID + ":display"));
                    for (int i = 0; i < room.objects().size(); i++) {
                        var o = room.objects().get(i);
                        if (o.kind().equals("native")) {
                            // Native: channels is the decimal/hex object ID, latch is its subtype.
                            result.add(new ObjectSpawn(o.x(), o.y(), Integer.decode(o.channels()), o.latch(), 0, false, o.y()));
                        } else result.add(new ObjectSpawn(o.x(), o.y(), 0, i, 0, false, o.y(), i, ID, ID + ":" + o.kind()));
                    }
                    return result;
                }
                @Override public List<RingSpawn> getRings() { return List.of(); }
            });
        }
        @Override public ZoneRegistry getZoneRegistry() {
            var stock = super.getZoneRegistry();
            if (zones == null || stockZones != stock) { stockZones = stock; zones = roomZones(stock); }
            return zones;
        }
        private ZoneRegistry roomZones(ZoneRegistry stockRegistry) {
            return new DelegatingZoneRegistry(stockRegistry) {
                @Override public int[] getStartPosition(int zone, int act) {
                    var r = session.room();
                    return zone == r.zone() && act == r.act() ? new int[]{r.spawnX(), r.spawnY()} : super.getStartPosition(zone, act);
                }
                @Override public List<LevelDescriptor> getLevelDataForZone(int zone) {
                    var stock = super.getLevelDataForZone(zone);
                    if (zone != session.room().zone()) return stock;
                    var result = new ArrayList<>(stock);
                    var r = session.room(); var descriptor = stock.get(r.act());
                    result.set(r.act(), new LevelDescriptor() {
                        @Override public int levelIndex() { return descriptor.levelIndex(); }
                        @Override public int startX() { return r.spawnX(); }
                        @Override public int startY() { return r.spawnY(); }
                    });
                    return List.copyOf(result);
                }
            };
        }
        @Override public LevelEventProvider getLevelEventProvider() {
            var stock = super.getLevelEventProvider();
            if (!active || stock == null) return stock;
            if (roomEvents == null || stockEvents != stock) {
                stockEvents = stock;
                roomEvents = new LevelEventProvider() {
                    @Override public void initLevel(int zone, int act) { stock.initLevel(zone, act); }
                    @Override public void update() { }
                };
            }
            return roomEvents;
        }
        @Override public GameOverFlowProvider getGameOverFlowProvider() {
            return active ? roomGameOver : super.getGameOverFlowProvider();
        }
        @Override public <T> T getGameService(Class<T> type) {
            if (type == LevelInputOverlay.class) return active ? type.cast(actions) : null;
            return super.getGameService(type);
        }
    }
}

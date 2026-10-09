package starpost.realtown;

import com.openggf.game.GameServiceBundle;
import com.openggf.game.LevelInputOverlay;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.code.ModContext;
import java.util.List;

/** Registration is inert in stock acts. Terrain integration places director admission anchors across the route. */
public final class TownContent {
    public static final String OWNER = "starpost-valley";
    public static final String CONTROLLER = "starpost-valley:town-controller";
    public static final String VILLAGER = "starpost-valley:town-villager";
    public static final String DOOR = "starpost-valley:town-door";
    public static final String PICKUP = "starpost-valley:town-pickup";
    public static final String DECORATION = "starpost-valley:town-decoration";
    private TownContent() {}

    public static TownSession register(ModContext context) {
        TownSession session = new TownSession();
        context.registerServiceBundle("town", () -> GameServiceBundle.builder()
            .capturedService("state", TownSession.class, session)
            .service(LevelInputOverlay.class, new TownInput(session)).build());
        context.registerObject("town-controller", (spawn, registry) -> new TownController(spawn));
        context.registerObject("town-villager", (spawn, registry) -> new TownVillager(spawn));
        context.registerObject("town-door", (spawn, registry) -> new TownDoor(spawn));
        context.registerObject("town-pickup", (spawn, registry) -> new TownPickup(spawn));
        context.registerObject("town-decoration", (spawn, registry) -> new TownDecoration(spawn));
        return session;
    }

    /** The terrain/E1 integration registers this for its tagged valley destination. */
    public static void registerInput(ModContext context, com.openggf.game.ZoneKey.Mod destination, TownSession session) {
        context.registerInputFilter(new com.openggf.mods.code.ModInputFilterContribution(destination,new TownInput(session)));
    }

    public static ObjectSpawn spawn(String key, int index, int x, int y) {
        return new ObjectSpawn(x,y,0,index,0,false,y,-1,OWNER,key);
    }
    public static List<ObjectSpawn> placements() {
        // Native object admission is camera-local; each possible return spawn needs a nearby anchor.
        return java.util.stream.IntStream.range(0, 13)
            .mapToObj(i -> spawn(CONTROLLER,i,150 + 256*i,192)).toList();
    }
}

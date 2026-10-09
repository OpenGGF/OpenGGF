package starpost.realtown;

import com.openggf.game.GameServiceBundle;
import com.openggf.game.LevelInputOverlay;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.code.ModContext;
import java.util.List;

/** Registration is inert in stock acts. Terrain integration places only the controller. */
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
        context.registerObject(CONTROLLER, (spawn, registry) -> new TownController(spawn));
        context.registerObject(VILLAGER, (spawn, registry) -> new TownVillager(spawn));
        context.registerObject(DOOR, (spawn, registry) -> new TownDoor(spawn));
        context.registerObject(PICKUP, (spawn, registry) -> new TownPickup(spawn));
        context.registerObject(DECORATION, (spawn, registry) -> new TownDecoration(spawn));
        return session;
    }

    public static ObjectSpawn spawn(String key, int index, int x, int y) {
        return new ObjectSpawn(x,y,0,index,0,false,y,-1,OWNER,key);
    }
    public static List<ObjectSpawn> placements() {
        return List.of(spawn(CONTROLLER,0,150,192));
    }
}

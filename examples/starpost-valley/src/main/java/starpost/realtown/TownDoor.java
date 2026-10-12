package starpost.realtown;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;
import starpost.valley.Valley;

/** Door requests an act exit; it never runs a scene menu while gameplay still owns the frame. */
public final class TownDoor extends AbstractObjectInstance implements ModRewindRecreatable {
    public TownDoor(ObjectSpawn spawn) { super(spawn,"Town doorway"); }
    public boolean isPersistent() { return true; }
    public int getPriorityBucket() { return 6; }
    private TownSession town() { return services().gameService(TownSession.class); }
    public Valley.Place place() { return town().layout().doors.get(spawn.subtype()); }
    public boolean enter(int x,int y) {
        TownSession town=town(); Valley.Place door=place();
        int floor=town.layout().ground.floorBelow(door.x(),0);
        if (town.modal() || Math.abs(x-door.x())>door.halfWidth() || Math.abs(y-floor)>48) return false;
        town.request(door.id(),null,x,y); return true;
    }
    public void update(int vIntRunCount, PlayableEntity player) {}
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new TownDoor(context.spawn()); }
    public void appendRenderCommands(List<GLCommand> commands) {
        TownSession town=town();
        if (town!=null && town.active() && town.presentation()!=null) town.presentation().door(services(),town,place());
    }
}

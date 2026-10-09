package starpost.realtown;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;
import starpost.valley.Pickups;

/** Daily rings and forage use the scene's exact availability and reward rules. */
public final class TownPickup extends AbstractObjectInstance implements ModRewindRecreatable {
    public TownPickup(ObjectSpawn spawn) { super(spawn,"Town pickup"); }
    public boolean isPersistent() { return true; }
    public int getPriorityBucket() { return 3; }
    private TownSession town() { return services().gameService(TownSession.class); }
    public Pickups.Pickup pickup() {
        TownSession t=town();
        return t.pickups().today(t.game(),t.layout().ground,t.layout().springX,t.layout().loopX).get(spawn.subtype());
    }
    public void update(int vIntRunCount, PlayableEntity player) {
        TownSession town=town();
        if (town==null || !town.active() || town.modal() || player==null) return;
        Pickups.Pickup pickup=pickup();
        if (Math.abs(player.getCentreX()-pickup.x())<12 && Math.abs(player.getCentreY()-pickup.y())<22
            && town.pickups().collect(pickup.index(),pickup.item(),town.game())) {
            town.collected(pickup.item());
            if (pickup.item()==null) services().levelGamestate().addRings(1);
            services().playSfx(pickup.item()==null?starpost.scene.Sfx.RING:starpost.scene.Sfx.GRAB);
        }
    }
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new TownPickup(context.spawn()); }
    public void appendRenderCommands(List<GLCommand> commands) {
        TownSession town=town();
        if (town!=null && town.active() && town.presentation()!=null && !town.pickups().taken(spawn.subtype()))
            town.presentation().pickup(services(),town,pickup());
    }
}

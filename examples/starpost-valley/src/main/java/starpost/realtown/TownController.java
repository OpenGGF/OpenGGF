package starpost.realtown;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** Persistent town director; the engine continues to own S3K movement and collision. */
public final class TownController extends AbstractObjectInstance implements ModRewindRecreatable {
    private boolean placed;
    private boolean ownsHold;
    public TownController(ObjectSpawn spawn) { super(spawn, "Starpost town"); }
    public boolean isPersistent() { return true; }
    public boolean isHighPriority() { return true; }
    public int getPriorityBucket() { return 0; }
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new TownController(context.spawn()); }
    private TownSession town() { return services().gameService(TownSession.class); }

    public void update(int vIntRunCount, PlayableEntity entity) {
        TownSession town = town();
        if (town == null || !town.active() || entity == null) return;
        if (!placed) {
            placed = true;
            int width = town.layout().ground.right();
            town.attachGround(new ActGround(services(), width));
            services().objectManager().addDynamicObject(new TownDecoration(TownContent.spawn(TownContent.DECORATION,0,150,192)));
            services().objectManager().addDynamicObject(new TownDecoration(TownContent.spawn(TownContent.DECORATION,1,150,192)));
            int i=0;
            for (var def : town.people().cast.all()) {
                var spawn = TownContent.spawn(TownContent.VILLAGER,i++,town.layout().anchor(def.home()),192);
                services().objectManager().addDynamicObject(new TownVillager(spawn));
            }
            for (i=0; i<town.layout().doors.size(); i++) {
                var door = town.layout().doors.get(i);
                services().objectManager().addDynamicObject(new TownDoor(TownContent.spawn(TownContent.DOOR,i,door.x(),192)));
            }
            for (var pickup : town.pickups().today(town.game(),town.layout().ground,town.layout().springX,town.layout().loopX))
                services().objectManager().addDynamicObject(new TownPickup(TownContent.spawn(TownContent.PICKUP,
                    pickup.index(),Math.round(pickup.x()),Math.round(pickup.y()))));
        }
        int x=entity.getCentreX(), y=entity.getCentreY();
        boolean held=town.modal();
        town.tick(x,y,!entity.getAir());
        if (!held && !town.modal() && !entity.getAir() && !entity.isObjectControlled()) {
            if (town.doorAction()) {
                for (var object : services().objectManager().getActiveObjects()) {
                    if (object instanceof TownDoor door && door.enter(x,y)) break;
                }
            } else if (town.action()) {
                TownVillager nearest=null; double distance=Double.MAX_VALUE;
                for (var object : services().objectManager().getActiveObjects()) {
                    if (object instanceof TownVillager v && v.visible()) {
                        double d=Math.abs(v.x()-x);
                        if (d<=22 && Math.abs(v.feet()-(y+entity.getYRadius()))<=32 && d<distance) {
                            nearest=v; distance=d;
                        }
                    }
                }
                if (nearest!=null) town.talk(nearest.id());
            }
        }
        if (entity instanceof AbstractPlayableSprite player) {
            if (town.modal() && !ownsHold && !player.isObjectControlled()) {
                ownsHold=true;
                player.setObjectControlled(true);
                player.setGSpeed((short)0); player.setXSpeed((short)0); player.setYSpeed((short)0);
            } else if (!town.modal() && ownsHold) {
                player.setObjectControlled(false); ownsHold=false;
            }
        }
        town.clearInput();
        services().levelGamestate().pauseTimer();
    }
    public void appendRenderCommands(List<GLCommand> commands) {
        TownSession town=town();
        if (town!=null && town.active() && town.presentation()!=null) town.presentation().draw(services(),town);
    }
}

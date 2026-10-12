package starpost.realtown;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;
import starpost.people.Bodies;
import starpost.people.Spot;
import starpost.people.VillagerDef;

/** Existing schedules with real act floor probes. Scalar state uses the owner-bounded rewind codec. */
public final class TownVillager extends AbstractObjectInstance implements ModRewindRecreatable {
    private float x;
    private int feet;
    private boolean initialized, visible, walking, facingLeft;
    private String insideAt;
    private long animation;
    public TownVillager(ObjectSpawn spawn) { super(spawn,"Town neighbour"); x=spawn.x(); feet=spawn.y(); }
    public boolean isPersistent() { return true; }
    public int getPriorityBucket() { return 4; }
    public int getX() { return Math.round(x); }
    public int getY() { return feet; }
    public float x() { return x; }
    public int feet() { return feet; }
    public boolean visible() { return visible; }
    public boolean walking() { return walking; }
    public boolean facingLeft() { return facingLeft; }
    public long animation() { return animation; }
    private TownSession town() { return services().gameService(TownSession.class); }
    public VillagerDef definition() { return town().people().cast.all().get(spawn.subtype()); }
    public String id() { return definition().id; }
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new TownVillager(context.spawn()); }
    public void update(int vIntRunCount, PlayableEntity player) {
        TownSession town=town();
        if (town==null || !town.active() || town.modal()) return;
        animation++; walking=false;
        VillagerDef def=definition();
        if (!town.people().present(def,town.game())) { visible=false; insideAt=null; return; }
        Spot goal=town.people().spotFor(def,town.game());
        if (goal==null) { visible=false; return; }
        int target=goal.farm()?town.layout().anchor("farm_gate"):town.layout().anchor(goal.anchor())+goal.dx();
        if (!initialized) {
            initialized=true; x=target; feet=town.walkFloor(target);
            visible=!goal.inside()&&!goal.farm(); insideAt=goal.inside()?goal.anchor():goal.farm()?"farm_gate":null;
            return;
        }
        if (!visible) {
            if (insideAt!=null && (goal.inside()&&insideAt.equals(goal.anchor()) || goal.farm()&&insideAt.equals("farm_gate"))) return;
            if (insideAt==null) x=target;
            else x=town.layout().anchor(insideAt);
            visible=true; insideAt=null;
        }
        float dx=target-x;
        if (Math.abs(dx)>0.5f) {
            float next=x+Math.signum(dx)*Math.min(Math.abs(dx),Bodies.speed(def.body()));
            int floor=town.walkFloor(Math.round(next));
            // Neighbours and native solid decks share one continuous, bounded-step footpath.
            if (floor<4096 && Math.abs(floor-feet)<=32) { x=next; feet=floor; walking=true; facingLeft=dx<0; }
        } else {
            x=target;
            if (goal.inside() || goal.farm()) { visible=false; insideAt=goal.farm()?"farm_gate":goal.anchor(); }
        }
    }
    public void appendRenderCommands(List<GLCommand> commands) {
        TownSession town=town();
        if (town!=null && town.active() && town.presentation()!=null && visible)
            town.presentation().villager(services(),town,this);
    }
}

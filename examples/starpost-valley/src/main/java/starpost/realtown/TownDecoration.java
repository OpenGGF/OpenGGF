package starpost.realtown;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Festival board/shelf behind people; bunting in front. The original festival renderer owns art. */
public final class TownDecoration extends AbstractObjectInstance implements ModRewindRecreatable {
    public TownDecoration(ObjectSpawn spawn) { super(spawn,"Town dressing"); }
    public boolean isPersistent() { return true; }
    public boolean isHighPriority() { return spawn.subtype()==1; }
    public int getPriorityBucket() { return spawn.subtype()==0?7:1; }
    public void update(int vIntRunCount,PlayableEntity player) {}
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new TownDecoration(context.spawn()); }
    public void appendRenderCommands(List<GLCommand> commands) {
        TownSession town=services().gameService(TownSession.class);
        if (town!=null&&town.active()&&town.presentation()!=null) town.presentation().decoration(services(),town,spawn.subtype()==1);
    }
}

package starpost.realruins;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Dropped ore/Record/scrap with captured motion and partial bag collection. */
public final class RuinsFind extends AbstractObjectInstance implements ModRewindRecreatable {
    private transient RuinsPresentation presentation;
    public RuinsFind(ObjectSpawn spawn) { super(spawn,"Ruins find"); }
    public int getPriorityBucket() { return 3; }
    public boolean isPersistent() { return true; }
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new RuinsFind(context.spawn()); }
    public void update(int vIntRunCount,PlayableEntity player) {
        var session=services().gameService(RuinsSession.class);
        if(session==null || !session.active() || player==null) return;
        int i=spawn.subtype(); var find=session.finds().get(i);
        if(find.count()<=0 || session.owned(find.id())) { setDestroyed(true); return; }
        float x=find.x()+find.vx(),y=find.y()+find.vy(),vy=find.vy()+0x18/256f;
        int floor=session.chamber().floorBelow(Math.round(x),Math.round(y)-8);
        if(vy>0 && y+8>=floor) { y=floor-8;vy=0; }
        int count=find.count();
        if(Math.abs(x-player.getCentreX())<18 && Math.abs(y+RuinsLevel.ORIGIN-player.getCentreY())<30) {
            int before=session.game().inventory.total(find.id());
            session.find(find.id(),count);
            count-=session.game().inventory.total(find.id())-before;
        }
        session.finds().set(i,new RuinsSession.Find(find.id(),count,x,y,find.vx(),vy,find.age()+1));
        if(count<=0) setDestroyed(true);
    }
    public void appendRenderCommands(List<GLCommand> commands) {
        var session=services().gameService(RuinsSession.class);
        if(session==null || !session.active()) return;
        if(presentation==null) presentation=new RuinsPresentation(session);
        presentation.find(services(),session,spawn.subtype());
    }
}

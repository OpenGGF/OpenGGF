package starpost.realfest;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** Native level pickup with captured ownership; the champion shares the Ring Hunt's taken bits. */
public final class ActivityRing extends AbstractObjectInstance implements ModRewindRecreatable {
    private boolean collected;
    private transient ActivityPresentation presentation;
    public ActivityRing(ObjectSpawn spawn) { super(spawn,"Festival ring"); }
    public boolean isPersistent() { return true; }
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new ActivityRing(context.spawn()); }
    public void update(int vIntRunCount,PlayableEntity entity) {
        var s=services().gameService(ActivitySession.class);
        if(s==null || !s.active() || !(entity instanceof AbstractPlayableSprite p) || s.heldPlayer()) return;
        if(!collected && Math.abs(p.getCentreX()-spawn.x())<p.getXRadius()+8 && Math.abs(p.getCentreY()-spawn.y())<p.getYRadius()+8) {
            if(s.kind().equals("hunt")) s.take(spawn.subtype(),p); else p.addRings(1);
            collected=true;
        }
    }
    public void appendRenderCommands(List<GLCommand> commands) {
        var s=services().gameService(ActivitySession.class);
        if(s==null || !s.active() || collected || s.kind().equals("hunt") && s.taken(spawn.subtype())) return;
        if(presentation==null) presentation=new ActivityPresentation(s);
        presentation.ring(services(),s,spawn.x(),spawn.y());
    }
}

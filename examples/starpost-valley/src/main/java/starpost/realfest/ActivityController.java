package starpost.realfest;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;
import starpost.realtown.ActGround;

/** One admitted persistent director; native collision and movement run in the ordinary game loop. */
public final class ActivityController extends AbstractObjectInstance implements ModRewindRecreatable {
    private boolean initialized,ownsHold,rocksPlaced;
    private transient ActivityPresentation presentation;
    public ActivityController(ObjectSpawn spawn) { super(spawn,"Valley activity"); }
    public boolean isPersistent() { return true; }
    public boolean isHighPriority() { return true; }
    public int getPriorityBucket() { return 0; }
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new ActivityController(context.spawn()); }
    public void update(int vIntRunCount,PlayableEntity entity) {
        var session=services().gameService(ActivitySession.class);
        if(session==null || !session.active() || !(entity instanceof AbstractPlayableSprite player) || !session.claim(spawn.subtype())) return;
        var seasons=services().gameService(starpost.realvalley.ActSeasons.class);
        if(seasons!=null) seasons.bind(services().paletteOwnershipRegistryOrNull(),session.game());
        int width=CourseLevel.blocks(session.kind(),session.game().calendar.year()).length*256;
        var ground=new ActGround(services(),width,128);
        services().camera().setMaxX((short)Math.max(0,width-320));
        services().camera().setViewportMaxX((short)Math.max(0,width-services().camera().getWidth()));
        if(!initialized) {
            initialized=true; int x=CourseLevel.start(session.kind());
            NativePositionOps.writeYPosResetSubpixel(player,ground.floorBelow(x,128)-player.getYRadius());
            player.setAir(false); session.initHunt(x0->ground.floorBelow(x0,128));
            if(session.kind().equals("hunt")) for(int i=0;i<session.rings().size();i++) {
                var r=session.rings().get(i);
                services().objectManager().addDynamicObject(new ActivityRing(ActivityContent.spawn("activity-ring",i,Math.round(r.x()),Math.round(r.y()))));
            }
            if(session.kind().equals("lake") && session.game().flags.contains("lake_bridge"))
                for(int deckX=520;deckX<752;deckX+=16) services().objectManager().addDynamicObject(new ActivityDeck(
                    ActivityContent.spawn("activity-deck",deckX,deckX,ground.floorBelow(504,128)+4)));
            if(session.kind().equals("snowboard")) for(int col=2;col<CourseLevel.blocks("snowboard",session.game().calendar.year()).length-2;col+=3)
                for(int j=0;j<5;j++) {
                    int rx=col*256+60+j*18,ry=ground.floorBelow(rx,128)-40;
                    services().objectManager().addDynamicObject(new ActivityRing(ActivityContent.spawn("activity-ring",col*5+j,rx,ry)));
                }
        }
        if(initialized && session.kind().equals("snowboard") && !rocksPlaced) {
            rocksPlaced=true;
            for(int col=4;col<CourseLevel.blocks("snowboard",session.game().calendar.year()).length-2;col+=3)
                for(int lx=60;lx<200;lx+=10) {
                    int rockX=col*256+lx;
                    if(Math.abs(ground.floorBelow(rockX+12,128)-ground.floorBelow(rockX-12,128))<3) {
                        services().objectManager().addDynamicObject(new ActivityRock(ActivityContent.spawn("activity-rock",col,rockX,ground.floorBelow(rockX,128)))); break;
                    }
                }
        }
        if(session.heldPlayer() && !player.getAir() && !ownsHold && !player.isObjectControlled()) {
            player.setObjectControlled(true); ownsHold=true;
            player.setGSpeed((short)0); player.setXSpeed((short)0); player.setYSpeed((short)0);
        } else if(!session.heldPlayer() && ownsHold) { player.setObjectControlled(false); ownsHold=false; }
        // A board's minimum forward pace uses the native speed fields; sensors, jumps and gravity stay native.
        if(session.kind().equals("snowboard") && !session.heldPlayer() && !player.getAir() && !player.isHurt()) {
            int pace=session.crash()>0?2:8; if(player.getGSpeed()<pace*256) player.setGSpeed((short)(pace*256));
        }
        session.tick(player,x->ground.floorBelow(x,128));
        if(session.exit()!=null) services().requestActExit(session.exit(),session.result(player));
        session.clearInput(); services().levelGamestate().pauseTimer();
        session.shell().music.want(session.kind().equals("snowboard")?"s3k":"s1",
            session.kind().equals("snowboard")?0x0B:session.kind().equals("hunt")?0x89:0x81);
        session.shell().music.update();
    }
    public void appendRenderCommands(List<GLCommand> commands) {
        var session=services().gameService(ActivitySession.class);
        if(session==null || !session.active() || !session.owns(spawn.subtype())) return;
        if(presentation==null) presentation=new ActivityPresentation(session);
        presentation.draw(services(),session);
    }
}

package starpost.realfest;

import com.openggf.level.objects.ObjectServices;
import com.openggf.mods.scene.*;
import com.openggf.mods.ui.LevelOverlayCanvas;
import java.util.*;
import starpost.art.Anim;
import starpost.festivals.*;
import starpost.people.*;
import starpost.realtown.LevelPictureCanvas;
import starpost.ui.Text;

/** ROM-backed actors/board and activity HUD over the real level; drawing never advances rules. */
public final class ActivityPresentation {
    private final Map<SceneImage,List<LevelPictureCanvas.Span>> images=LevelPictureCanvas.cache();
    private final FestivalArt festivalArt;
    private final PeopleArt peopleArt;
    private final starpost.fishing.LakeOverlay lake;
    public ActivityPresentation(ActivitySession s) {
        festivalArt=new FestivalArt(s.shell().art,s.shell().ctx.art().rom("s2"));
        peopleArt=new PeopleArt(s.shell().art); lake=new starpost.fishing.LakeOverlay(s.shell().art);
    }
    private SceneCanvas canvas(ObjectServices services) {
        return new LevelPictureCanvas(new LevelOverlayCanvas(services.graphicsManager(),services.camera().getWidth(),services.camera().getHeight()),images);
    }
    public void ring(ObjectServices services,ActivitySession s,int x,int y) {
        canvas(services).draw(s.shell().art.ring.frame(s.ticks()/8%4),x-services.camera().getX(),y-services.camera().getY(),SceneDraw.plain());
    }
    public void draw(ObjectServices services,ActivitySession s) {
        var canvas=canvas(services); int cx=services.camera().getX(),cy=services.camera().getY();
        var player=services.camera().getFocusedSprite(); if(player==null) return;
        if(s.kind().equals("lake")) {
            starpost.scene.PlayScreen.drawHud(s.shell(),canvas);
            lake.draw(s.shell(),canvas,s.lake(),player.getCentreX()-cx,player.getCentreY()+player.getYRadius()-cy,cx,cy,s.ticks());
            return;
        }
        if(s.kind().equals("hunt") && s.town().presentation()!=null) {
            s.town().presentation().decoration(services,s.town(),false);
            for(var place:s.town().layout().doors) s.town().presentation().door(services,s.town(),place);
            s.town().presentation().decoration(services,s.town(),true);
        }
        canvas.fill(0,0,canvas.width(),28,0xFF000000);
        String label=s.kind().equals("race")?"LAP "+Math.min(2,s.laps()+1)+" / 2":s.kind().equals("hunt")?"RING HUNT":"SNOWBOARD RUN";
        canvas.text(label,8,6,Text.YELLOW);
        int run=Math.max(0,s.ticks()-Race.COUNTDOWN);
        String time=s.kind().equals("hunt")?Integer.toString(Math.max(0,(RingHunt.TICKS-run+59)/60)):String.format("%d:%02d",run/3600,run/60%60);
        Text.right(canvas,time,canvas.width()-8,6,Text.WHITE);
        canvas.text("RINGS "+player.getRingCount(),8,17,Text.WHITE);
        if(s.ticks()<Race.COUNTDOWN) {
            s.shell().art.hud.number(canvas,Integer.toString((Race.COUNTDOWN-s.ticks()+59)/60),canvas.width()/2f-12,70,3);
        }
        if(s.kind().equals("race")) {
            for(var r:s.rivals()) {
                int x=CourseLevel.START+Math.floorMod(Math.round(r.x()-r.startX),CourseLevel.lapLength())-cx;
                int y=Math.round(r.eggY)-cy;
                if(r.flies()) {
                    var ship=festivalArt.eggMobile();
                    if(ship!=null) { canvas.draw(ship.frame(5),x,y,SceneDraw.plain()); canvas.draw(ship.frame(2),x,y,SceneDraw.plain()); }
                } else Bodies.draw(peopleArt,canvas,"hero:"+r.who,Bodies.WALK,s.ticks(),x,y,false,0,SceneDraw.plain());
            }
        } else if(s.kind().equals("hunt") && s.champion()!=null) {
            var c=s.champion();
            Bodies.draw(peopleArt,canvas,"hero:"+RingHunt.champion(s.game()),Bodies.WALK,s.ticks(),c.x-cx,c.y+16-cy,c.facingLeft,0,SceneDraw.plain());
            Text.right(canvas,"CHAMP "+c.score,canvas.width()-8,17,Text.YELLOW);
        } else if(s.kind().equals("snowboard")) {
            var board=festivalArt.snowboard();
            if(board!=null) canvas.draw(board.frame(1),player.getCentreX()-cx,player.getCentreY()+player.getYRadius()-cy-2,SceneDraw.plain());
            Text.right(canvas,"TRICKS "+s.tricks(),canvas.width()-8,17,Text.YELLOW);
            // Winter dressing leaves the native ROM terrain and collision intact.
            for(int i=0;i<32;i++) canvas.fill((i*131+s.ticks()/2)%canvas.width(),(i*71+s.ticks())%canvas.height(),1,2,0xD0FFFFFF);
        }
    }
}

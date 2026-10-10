package starpost.realruins;

import com.openggf.level.objects.ObjectServices;
import com.openggf.mods.scene.*;
import com.openggf.mods.ui.LevelOverlayCanvas;
import java.util.*;
import starpost.realtown.LevelPictureCanvas;
import starpost.ruins.*;
import starpost.ui.Text;

/** Draw-only ROM pictures and shafts; no gameplay mutation from rendering. */
public final class RuinsPresentation {
    private final Map<SceneImage,List<LevelPictureCanvas.Span>> images=LevelPictureCanvas.cache();
    private final Map<Integer,RomSheetPictures> badniks=new HashMap<>();
    public RuinsPresentation(RuinsSession session) {}
    private SceneCanvas canvas(ObjectServices services) {
        return new LevelPictureCanvas(new LevelOverlayCanvas(services.graphicsManager(),services.camera().getWidth(),services.camera().getHeight()),images);
    }
    public void thing(ObjectServices services,RuinsSession session,int index) {
        var canvas=canvas(services); var t=session.chamber().things.get(index);
        int cx=services.camera().getX(),cy=services.camera().getY()-RuinsLevel.ORIGIN;
        var art=session.art();
        if(t.type()==Chamber.BADNIK) {
            var b=session.badnik(index); if(b==null || !b.alive) return;
            var sheet=services.renderManager().getSheet("starpost-valley:ruins-badnik-"+b.kind);
            if(sheet==null) return;
            var pictures=badniks.computeIfAbsent(b.kind,k->new RomSheetPictures(sheet,art.palette(session.chamber().band)));
            var style=SceneDraw.plain().withFlipX(!b.facingLeft);
            if(b.kind==Badnik.CATERKILLER) for(int i=3;i>=1;i--) { var seg=b.segment(i); canvas.draw(pictures.frame(8),seg[0]-cx,seg[1]-cy,style); }
            canvas.draw(pictures.frame(BadnikFrames.frame(b,session.ticks())),b.x-cx,b.y-cy,style);
            if(b.kind==Badnik.ORBINAUT) for(int i=0;i<4;i++) { var ball=b.ball(i); canvas.draw(pictures.frame(3),ball[0]-cx,ball[1]-cy,style); }
            return;
        }
        int x=t.x()-cx,y=t.y()-cy;
        switch(t.type()) {
            case Chamber.RING -> canvas.draw(art.art.ring.frame((int)(session.ticks()/8%4)),x,y,SceneDraw.plain());
            case Chamber.ROCK -> stand(canvas,session.chamber().band==RuinsRules.MARBLE?art.greenBlock():art.art.purpleRock,0,x,y);
            case Chamber.MONITOR -> {
                stand(canvas,art.art.monitor,0,x,y);
                if(t.param()>=0 && t.param()<session.chamber().prizes.size()) art.art.icons.draw(canvas,
                    session.game().item(session.chamber().prizes.get(t.param())),x-8,y-29,SceneDraw.plain());
            }
            default -> { }
        }
    }
    private static void stand(SceneCanvas canvas,SceneSpriteSet set,int frame,float x,float feet) {
        if(set==null) return; var sprite=set.frame(frame);
        canvas.draw(sprite,x,feet-(sprite.height()-sprite.originY()),SceneDraw.plain());
    }
    public void find(ObjectServices services,RuinsSession session,int index) {
        var find=session.finds().get(index); if(find.count()<=0) return;
        var canvas=canvas(services); session.art().art.icons.draw(canvas,session.game().item(find.id()),
            find.x()-services.camera().getX()-8,find.y()+RuinsLevel.ORIGIN-services.camera().getY()-8,SceneDraw.plain());
    }
    public void draw(ObjectServices services,RuinsSession session) {
        var canvas=canvas(services); var chamber=session.chamber(); var art=session.art();
        int cx=services.camera().getX(),cy=services.camera().getY()-RuinsLevel.ORIGIN;
        // S1's lava animation art is ROM-backed; its collision tags remain generated unchanged.
        int frame=(int)(session.ticks()/20%3);
        for(var lava:chamber.lava) {
            var surface=art.lavaSurface(frame); var magma=art.magma(frame);
            canvas.clip(lava.x()-cx,lava.y()+10-cy,lava.w(),lava.h()-10);
            if(magma!=null) for(int y=lava.y()+10;y<lava.y()+lava.h();y+=32)
                for(int x=lava.x();x<lava.x()+lava.w();x+=32) canvas.draw(magma,x-cx,y-cy);
            if(surface!=null) for(int x=lava.x();x<lava.x()+lava.w();x+=32) canvas.draw(surface,x-cx,lava.y()+10-cy);
            canvas.unclip();
        }
        shaft(canvas,chamber.entryX-cx,chamber.entryY-cy,false);
        if(chamber.exitX>=0) shaft(canvas,chamber.exitX-cx,chamber.exitY-cy,true);
        if(chamber.waterY!=Chamber.NO_WATER) {
            canvas.fill(0,chamber.waterY-cy,canvas.width(),Math.max(0,canvas.height()-chamber.waterY+cy),0x301848C0);
            canvas.fill(0,chamber.waterY-cy,canvas.width(),2,0xB0B6DBFF);
        }
        for(var shot:session.shots()) {
            SceneSpriteSet set=shot.kind==Badnik.Shot.MISSILE?art.missile(chamber.band):art.badnik(shot.kind==Badnik.Shot.CANNONBALL?Badnik.BALL_HOG:shot.kind==Badnik.Shot.SPIKEBALL?Badnik.ORBINAUT:Badnik.BOMB,chamber.band);
            int f=shot.kind==Badnik.Shot.CANNONBALL?BadnikFrames.CANNONBALL:shot.kind==Badnik.Shot.SHRAPNEL?10+(int)(session.ticks()/4%2):shot.kind==Badnik.Shot.SPIKEBALL?BadnikFrames.ORBINAUT_BALL:(int)(session.ticks()/4%2);
            if(set!=null) canvas.draw(set.frame(f),shot.x-cx,shot.y-cy,SceneDraw.plain().withFlipX(shot.vx>0));
        }
        for(var puff:session.puffs()) canvas.draw(art.explosion.frame(Math.min(art.explosion.frameCount()-1,puff.age()/4)),puff.x()-cx,puff.y()-cy,SceneDraw.plain());
        for(var animal:session.animals()) stand(canvas,art.art.animal(animal.name()),!animal.hopping()?2:animal.vy()<0?1:0,animal.x()-cx,animal.feet()-cy);
        if(session.shell()!=null) starpost.scene.PlayScreen.drawHud(session.shell(),canvas);
        int rings=services.camera().getFocusedSprite()==null?0:services.camera().getFocusedSprite().getRingCount();
        Text.shadow(canvas,"HEALTH RINGS "+rings,16,40,Text.GREY);
        Text.right(canvas,"CHAMBER "+chamber.number,canvas.width()-8,40,Text.YELLOW);
        if(!session.notice().isEmpty()) Text.centred(canvas,session.notice(),56,Text.YELLOW);
        var player=services.camera().getFocusedSprite();
        if(player!=null && !player.getAir()) {
            int x=player.getCentreX(),feet=player.getCentreY()+player.getYRadius()-RuinsLevel.ORIGIN;
            String label=Math.abs(x-chamber.entryX)<32 && Math.abs(feet-chamber.entryY)<16?"UP: CLIMB OUT":
                chamber.exitX>=0 && Math.abs(x-chamber.exitX)<32 && Math.abs(feet-chamber.exitY)<16?"DOWN: CHAMBER "+(chamber.number+1):
                chamber.elevatorX>=0 && Math.abs(x-chamber.elevatorX)<32?"UP: ELEVATOR":"";
            if(!label.isEmpty()) Text.centred(canvas,label,canvas.height()-24,Text.YELLOW);
        }
    }
    private static void shaft(SceneCanvas canvas,int x,int feet,boolean down) {
        canvas.fill(x-10,feet-(down?4:64),20,down?8:64,down?0xFF101018:0x70FFFFDB);
        if(down) canvas.fill(x-12,feet-6,24,2,0xFFB6B6B6);
    }
}

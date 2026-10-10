package starpost.realtown;

import com.openggf.level.objects.ObjectServices;
import com.openggf.mods.scene.*;
import com.openggf.mods.ui.LevelOverlayCanvas;
import java.util.List;
import java.util.Map;
import starpost.art.Art;
import starpost.festivals.FestivalSystem;
import starpost.people.Bodies;
import starpost.people.PeopleArt;
import starpost.ui.Text;
import starpost.valley.Pickups;
import starpost.valley.Valley;

/** ROM presentation only; schedules, rewards and dialogue changes are never run during draw. */
public final class TownPresentation {
    private final Art art;
    private final PeopleArt peopleArt;
    private final FestivalSystem festivals;
    private final starpost.scene.PlayScreen play;
    private final starpost.scene.Shell shell;
    private final Map<SceneImage,List<LevelPictureCanvas.Span>> images=LevelPictureCanvas.cache();
    public TownPresentation(Art art,FestivalSystem festivals) {
        this(art,festivals,null);
    }
    public TownPresentation(Art art,FestivalSystem festivals,starpost.scene.PlayScreen play) {
        this(art,festivals,play,null);
    }
    public TownPresentation(Art art,FestivalSystem festivals,starpost.scene.PlayScreen play,starpost.scene.Shell shell) {
        this.art=art; peopleArt=new PeopleArt(art); this.festivals=festivals; this.play=play; this.shell=shell;
    }
    /** The retained context uses the same scoped donor route as the farm's soundtrack. */
    public void updateMusic(int x) {
        if (shell == null || play == null) return;
        play.chooseValleyMusic(shell,x);
        shell.music.update();
    }
    private SceneCanvas canvas(ObjectServices services) {
        return new LevelPictureCanvas(new LevelOverlayCanvas(services.graphicsManager(),
            services.camera().getWidth(),services.camera().getHeight()),images);
    }
    public void door(ObjectServices services,TownSession town,Valley.Place place) {
        SceneCanvas canvas=canvas(services);
        int x=place.x()-services.camera().getX();
        int floor=town.layout().ground.floorBelow(place.x(),0), y=floor-services.camera().getY();
        if (place.id().equals("museum") && play != null) {
            starpost.museum.MuseumSystem.drawTownAnnex(play,canvas,x,y);
            return;
        }
        var look=art.season(town.game().calendar.season());
        SceneImage building=switch (place.id()) {
            case "seed_stall"->look.seedStall; case "inn"->look.inn;
            case "workshop"->look.workshop; case "robomart"->look.robomart; default->null;
        };
        if (building!=null) canvas.draw(building,x-building.width()/2f,y-building.height()+2);
        else if (place.id().equals("capsule")) stand(canvas,art.capsule.frame(0),x,y);
        else if (place.id().equals("farm_gate")) stand(canvas,art.starpost.frame(0),x,y);
        int labelY=y-(building==null?56:building.height()+10);
        String label=place.label();
        if (x>=-150 && x<canvas.width()+150) {
            int width=canvas.textWidth(label)+8;
            canvas.fill(x-width/2,labelY,width,11,0xFF240000);
            canvas.text(label,x-width/2+4,labelY+2,Text.YELLOW);
        }
    }
    private static void stand(SceneCanvas canvas,SceneSprite sprite,int x,int feet) {
        canvas.draw(sprite,x,feet-(sprite.height()-sprite.originY()),SceneDraw.plain());
    }
    public void villager(ObjectServices services,TownSession town,TownVillager v) {
        SceneCanvas canvas=canvas(services);
        int x=v.getX()-services.camera().getX(), y=v.feet()-services.camera().getY();
        if (x<-60||x>canvas.width()+60) return;
        Bodies.draw(peopleArt,canvas,v.definition().body(),v.walking()?Bodies.WALK:Bodies.IDLE,
            v.animation(),x,y,v.facingLeft(),0,SceneDraw.plain());
        var player=services.camera().getFocusedSprite();
        if (player!=null&&Math.abs(player.getCentreX()-v.getX())<=40
                && Math.abs(player.getCentreY()+player.getYRadius()-v.feet())<=40)
            canvas.text(v.definition().name,x-canvas.textWidth(v.definition().name)/2,y+3,Text.YELLOW);
    }
    public void walkway(ObjectServices services,int x,int feet) {
        var canvas=canvas(services); stand(canvas,art.bridge.frame(0),x-services.camera().getX(),feet-services.camera().getY());
    }
    public void pickup(ObjectServices services,TownSession town,Pickups.Pickup pickup) {
        SceneCanvas canvas=canvas(services);
        float x=pickup.x()-services.camera().getX(), y=pickup.y()-services.camera().getY();
        if (x<-24||x>canvas.width()+24) return;
        if (pickup.item()==null) canvas.draw(art.ring.frame((int)(town.ticks()/8%4)),x,y,SceneDraw.plain());
        else art.icons.draw(canvas,town.game().item(pickup.item()),x-8,y-8,SceneDraw.plain());
    }
    public void decoration(ObjectServices services,TownSession town,boolean front) {
        if (festivals==null) return;
        SceneCanvas canvas=canvas(services);
        festivals.drawOnLevel(canvas,services.camera().getX(),services.camera().getY(),front,
            x->town.layout().ground.floorBelow(x,0),town.ticks());
    }
    public void draw(ObjectServices services,TownSession town) {
        SceneCanvas canvas=canvas(services);
        if(shell!=null) starpost.scene.PlayScreen.drawHud(shell,canvas,shell.ticks+town.ticks());
        if (!town.notice().isEmpty()) {
            canvas.fill(6,43,canvas.textWidth(town.notice())+8,13,0xE0101848);
            canvas.text(town.notice(),10,46,Text.WHITE);
        }
        if (town.speech()!=null) town.speech().draw(peopleArt,town.people(),town.game(),canvas,town.ticks());
        else if (town.askingGift()||town.invited()) {
            int y=canvas.height()-70;
            canvas.fill(8,y,canvas.width()-16,60,0xFF101848);
            String question=town.invited()?"JOIN THE "+town.game().section(starpost.festivals.Festivals.class).today(town.game()).name+"?"
                :"GIVE "+town.game().item(town.gift()).name()+" TO "+town.people().cast.get(town.speaker()).name+"?";
            canvas.text(com.openggf.mods.ui.CompactFont.fit(question,canvas.width()-36,1),18,y+12,Text.WHITE);
            canvas.text(town.yes()?"> YES     NO":"  YES   > NO",canvas.width()/2-60,y+37,Text.YELLOW);
        }
    }
}

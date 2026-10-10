package starpost.scene;

import com.openggf.mods.scene.*;
import java.util.List;
import starpost.art.Art;
import starpost.people.Bodies;
import starpost.people.PeopleArt;
import starpost.valley.Valley;

/** Fixed ROM town stage for menus and ceremonies. All platform play is in native acts. */
public final class TownBackdrop {
    public static final class Pose { public float x=700,y=192; public boolean facingLeft; }
    private final Shell shell;
    public final Valley valley;
    public final Pose pose=new Pose();
    public boolean labels;
    public SceneBackdrop sky;
    public java.util.function.IntFunction<List<Actor>> actors=view->List.of();
    private final PeopleArt peopleArt;
    private float camX,camY;
    public TownBackdrop(Shell shell) { this.shell=shell; valley=new Valley(shell.art); peopleArt=new PeopleArt(shell.art); }
    public float cameraX() { return camX; }
    public float cameraY() { return camY; }
    public void arriveFromFarm() { place(190); }
    public void place(float x) { pose.x=x; pose.y=valley.floorBelow(Math.round(x),0); snapCamera(); }
    public void snapCamera() {
        camX=Math.max(0,Math.min(valley.width()-shell.width(),pose.x-shell.width()/2f));
        camY=Math.max(-64,Math.min(Art.BLOCK-shell.height(),pose.y-150));
    }
    public void draw(SceneCanvas canvas,Art.Seasonal look,int light,SceneDraw tint) {
        int cx=Math.round(camX),cy=Math.round(camY);
        canvas.drawBackdrop(sky!=null?sky:look.backdrop(light),0,0,canvas.width(),canvas.height(),
            Math.max(0,Math.min(32,Math.round(8+camY*.1f))),camX,shell.ticks);
        for(int col=Math.max(0,cx/256);col<=Math.min(valley.blocks.length-1,(cx+canvas.width())/256);col++) {
            var block=look.block(valley.blocks[col]); if(block!=null) canvas.draw(block,col*256-cx,-cy,tint);
        }
        for(var place:valley.places) {
            var building=switch(place.id()) { case "seed_stall"->look.seedStall; case "inn"->look.inn;
                case "workshop"->look.workshop; case "robomart"->look.robomart; default->null; };
            if(building!=null) canvas.draw(building,place.x()-cx-building.width()/2f,valley.floorBelow(place.x(),0)-cy-building.height()+2,tint);
        }
        for(var actor:actors.apply(Actor.VALLEY)) if(Math.abs(actor.x()-camX-canvas.width()/2f)<canvas.width()) actor.draw(shell,canvas,cx,cy,tint);
        Bodies.draw(peopleArt,canvas,"hero:"+shell.game.farmer,Bodies.IDLE,shell.ticks,pose.x-cx,pose.y-cy,pose.facingLeft,0,tint);
        for(var actor:actors.apply(Actor.VALLEY)) if(Math.abs(actor.x()-camX-canvas.width()/2f)<canvas.width()) actor.drawOver(shell,canvas,cx,cy);
    }
}

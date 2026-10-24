package com.openggf.editor.stageplay;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.control.PhysicalGamepad;
import java.util.function.BiFunction;
import java.util.List;
import static org.lwjgl.glfw.GLFW.*;

/** Transient directing cursor. It never edits scene/camera state or advances the native world. */
public final class StageplayPositionPicker {
    public record View(double x,double y,double zoom) { }
    public record Floor(double x,double y,int surfaceY) { }
    public enum Result { NONE, CONFIRM, CANCEL }
    public interface Painter {
        void line(float x1,float y1,float x2,float y2,int rgb,float width);
        void rect(float x,float y,float width,float height,int rgb);
        void text(String text,float x,float y,int rgb);
        float textWidth(String text);
    }
    public static final String HINT="Arrows Move  Shift Fine  S Snap  Enter Pick  Esc Cancel";
    private final int width,height;
    private final BiFunction<Integer,Integer,List<Floor>> ground;
    private double x,y;
    private View camera;
    private Floor floor;
    private boolean snap;
    private int held;
    private int queriedX=Integer.MIN_VALUE,queriedY=Integer.MIN_VALUE;
    private boolean opening=true, previousSnapButton;
    private String detail="";

    StageplayPositionPicker(double x,double y,int width,int height,View camera,
                            boolean snap,BiFunction<Integer,Integer,List<Floor>> ground) {
        this.x=x;this.y=y;this.width=width;this.height=height;this.snap=snap;this.ground=ground;
        this.camera=new View(x-width/2.0/camera.zoom(),y-height/2.0/camera.zoom(),camera.zoom());
        query();
    }
    public double x(){return x;} public double y(){return y;}
    public double pickedY(){return snap&&floor!=null?floor.y():y;}
    public boolean snap(){return snap;} public Floor floor(){return floor;}
    public String detail(){return detail;} public View camera(){return camera;}
    public void resolvedCamera(View camera){this.camera=camera;}
    public void toggleSnap(){snap=!snap;}
    public void point(double x,double y) {if(this.x!=x||this.y!=y)floor=null;this.x=Math.clamp(x,0,32767);this.y=Math.clamp(y,-32768,32767);}
    public void pan(double dx,double dy) {
        camera=new View(Math.clamp(camera.x()+dx,-32768,32767),Math.clamp(camera.y()+dy,-32768,32767),camera.zoom());
    }
    public Result tick(InputHandler input) {
        boolean justOpened=opening;opening=false;
        if(MenuInput.textBack(input))return Result.CANCEL;
        if(!justOpened&&MenuInput.textAccept(input))return Result.CONFIRM;
        var pads=input.capturePhysicalInput().gamepads();
        int dx=(input.isPhysicalKeyDown(GLFW_KEY_RIGHT)?1:0)-(input.isPhysicalKeyDown(GLFW_KEY_LEFT)?1:0);
        int dy=(input.isPhysicalKeyDown(GLFW_KEY_DOWN)?1:0)-(input.isPhysicalKeyDown(GLFW_KEY_UP)?1:0);
        boolean fine=input.isPhysicalShiftDown(),snapButton=false;
        for(var pad:pads){
            dx+=(pad.buttonDown(PhysicalGamepad.BUTTON_DPAD_RIGHT)||pad.axis(PhysicalGamepad.AXIS_LEFT_X)>.55f?1:0)
                    -(pad.buttonDown(PhysicalGamepad.BUTTON_DPAD_LEFT)||pad.axis(PhysicalGamepad.AXIS_LEFT_X)<-.55f?1:0);
            dy+=(pad.buttonDown(PhysicalGamepad.BUTTON_DPAD_DOWN)||pad.axis(PhysicalGamepad.AXIS_LEFT_Y)>.55f?1:0)
                    -(pad.buttonDown(PhysicalGamepad.BUTTON_DPAD_UP)||pad.axis(PhysicalGamepad.AXIS_LEFT_Y)<-.55f?1:0);
            fine|=pad.buttonDown(PhysicalGamepad.BUTTON_RIGHT_BUMPER);snapButton|=pad.buttonDown(PhysicalGamepad.BUTTON_X);
        }
        if(input.isKeyPressed(GLFW_KEY_S)||snapButton&&!previousSnapButton)toggleSnap();previousSnapButton=snapButton;
        if(dx!=0||dy!=0){
            held++;int speed=fine?1:Math.min(24,1+held/8);
            point(x+Integer.signum(dx)*speed,y+Integer.signum(dy)*speed);
            double mx=width/camera.zoom()*.25,my=height/camera.zoom()*.25;
            if(x<camera.x()+mx)pan(x-camera.x()-mx,0);else if(x>camera.x()+width/camera.zoom()-mx)pan(x-(camera.x()+width/camera.zoom()-mx),0);
            if(y<camera.y()+my)pan(0,y-camera.y()-my);else if(y>camera.y()+height/camera.zoom()-my)pan(0,y-(camera.y()+height/camera.zoom()-my));
        }else held=0;
        if((int)Math.round(x)!=queriedX||(int)Math.round(y)!=queriedY)query();
        return Result.NONE;
    }
    public void refreshFloor(){if((int)Math.round(x)!=queriedX||(int)Math.round(y)!=queriedY)query();}
    private void query(){
        queriedX=(int)Math.round(x);queriedY=(int)Math.round(y);floor=null;
        if(ground==null)return;
        try {
            floor=ground.apply(queriedX,queriedY).stream().filter(p->p.surfaceY()>=y)
                    .min(java.util.Comparator.comparingInt(Floor::surfaceY)).orElse(null);
            detail=floor==null?"No usable floor under this point.":"Terrain floor; verify the placement in playback.";
        }catch(RuntimeException failure){detail="Floor preview unavailable: "+failure.getMessage();}
    }
    public void paint(Painter p,float left,float top,float w,float h,View view){
        float sx=left+(float)((x-view.x())*view.zoom()*w/width),sy=top+(float)((y-view.y())*view.zoom()*h/height);
        int gold=0xFFC845,ink=0x101820;
        if(floor!=null){
            float fy=top+(float)((floor.surfaceY()-view.y())*view.zoom()*h/height);
            p.line(sx-9,fy,sx+9,fy,0x61E5ED,2);p.line(sx,fy-5,sx,fy+3,0x61E5ED,2);
        }
        p.line(sx-10,sy,sx-3,sy,gold,2);p.line(sx+3,sy,sx+10,sy,gold,2);
        p.line(sx,sy-10,sx,sy-3,gold,2);p.line(sx,sy+3,sx,sy+10,gold,2);
        p.rect(sx-1,sy-1,2,2,gold);
        String tag=com.openggf.editor.project.EditorProjectMenu.coordinate(Double.toString(x))+", "+com.openggf.editor.project.EditorProjectMenu.coordinate(Double.toString(y));
        if(ground!=null&&(!snap||floor==null))tag+=snap?"  No floor":"  Snap off";
        if(snap&&floor!=null)tag+="  Snap "+com.openggf.editor.project.EditorProjectMenu.coordinate(Double.toString(floor.y()));
        float tw=p.textWidth(tag),tx=Math.clamp(sx+12,left+4,Math.max(left+4,left+w-tw-10)),ty=Math.clamp(sy-18,top+4,top+h-18);
        p.rect(tx-4,ty-3,tw+8,16,ink);p.text(tag,tx,ty,gold);
    }
}

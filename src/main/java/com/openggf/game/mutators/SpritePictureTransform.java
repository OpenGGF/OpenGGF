package com.openggf.game.mutators;

import com.openggf.graphics.SpritePresentation;
import com.openggf.mods.mutators.SpritePresentationPolicy;
import java.util.List;

/** Pure post-admission whole-picture geometry. Does not borrow the anatomy mask's pivot or shape. */
public record SpritePictureTransform(double xx,double xy,double yx,double yy,double tx,double ty,float hue) {
    public static final SpritePictureTransform IDENTITY=new SpritePictureTransform(1,0,0,1,0,0,0);
    public static SpritePictureTransform forTile(SpritePresentation.Tile tile,int cameraX,int cameraY,
                                                List<TransientMutatorPolicySource.Entry> entries) {
        var body=tile.subject().body();
        if(body==null||tile.subject().suppressed()) return IDENTITY;
        double sx=1,sy=1,angle=0; float hue=0;
        for(var entry:entries) if(entry.remainingPresentations()>0&&entry.policy() instanceof SpritePresentationPolicy policy
                && LivePresentationRuntime.targets(policy.target(),body.role())) {
            double phase=2*Math.PI*(entry.agePresentations()%policy.periodPresentations())/policy.periodPresentations();
            switch(policy.mode()) {
                case SCALE -> { sx*=policy.strengthPercent()/100.0;sy*=policy.strengthPercent()/100.0; }
                case ROTATE -> angle+=phase;
                case SQUASH -> { double stretch=1+(policy.strengthPercent()-100)/100.0*Math.sin(phase);sx*=stretch;sy/=stretch; }
                case RAINBOW -> hue+= (float)phase;
                case TRAIL -> { }
            }
        }
        double c=Math.cos(angle),s=Math.sin(angle),xx=c*sx,xy=-s*sy,yx=s*sx,yy=c*sy;
        double anchorX=body.anchorX()-cameraX,anchorY=body.anchorY()-cameraY;
        return new SpritePictureTransform(xx,xy,yx,yy,anchorX-xx*anchorX-xy*anchorY,
                anchorY-yx*anchorX-yy*anchorY,hue);
    }
    public float x(double x,double y) { return (float)(xx*x+xy*y+tx); }
    public float y(double x,double y) { return (float)(yx*x+yy*y+ty); }
    /** Visible source rows only, in top-left/top-right/bottom-right/bottom-left order. */
    public float[] quad(SpritePresentation.Tile tile,int cameraX,int cameraY) {
        double left=tile.x(),right=left+tile.width();
        double top=tile.y()+tile.height()*tile.rowStart()/8.0,bottom=tile.y()+tile.height()*tile.rowEnd()/8.0;
        return new float[]{x(left,top)+cameraX,y(left,top)+cameraY,x(right,top)+cameraX,y(right,top)+cameraY,
                x(right,bottom)+cameraX,y(right,bottom)+cameraY,x(left,bottom)+cameraX,y(left,bottom)+cameraY};
    }
    public boolean identity() { return xx==1&&xy==0&&yx==0&&yy==1&&tx==0&&ty==0&&hue==0; }
}

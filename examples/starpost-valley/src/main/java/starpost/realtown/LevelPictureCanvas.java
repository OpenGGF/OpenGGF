package starpost.realtown;

import com.openggf.mods.scene.*;
import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.PixelCanvas;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Thin draw adapter: existing ROM pictures become cached horizontal spans on the engine canvas. */
public final class LevelPictureCanvas implements SceneCanvas {
    private final PixelCanvas canvas;
    private final Map<SceneImage, List<Span>> images;
    private int clipX, clipY, clipW, clipH;
    public record Span(int x, int y, int width, int argb) {}
    public LevelPictureCanvas(PixelCanvas canvas, Map<SceneImage,List<Span>> images) {
        this.canvas=canvas; this.images=images; unclip();
    }
    public static Map<SceneImage,List<Span>> cache() { return new IdentityHashMap<>(); }
    public int width() { return canvas.width(); }
    public int height() { return canvas.height(); }
    public void clear(int rgb) { canvas.fill(0,0,width(),height(),0xFF000000|rgb); }
    public void fill(int x,int y,int w,int h,int argb) {
        int left=Math.max(x,clipX), top=Math.max(y,clipY);
        int right=Math.min(x+w,clipX+clipW), bottom=Math.min(y+h,clipY+clipH);
        canvas.fill(left,top,right-left,bottom-top,argb);
    }
    public void clip(int x,int y,int w,int h) { clipX=x; clipY=y; clipW=w; clipH=h; }
    public void unclip() { clipX=clipY=0; clipW=width(); clipH=height(); }
    public void text(String text,int x,int y,int argb) { CompactFont.draw(this,text,x,y,1,argb); }
    public int textWidth(String text) { return CompactFont.width(text,1); }
    public void draw(SceneImage image,float x,float y) { draw(image,x,y,SceneDraw.plain()); }
    public void draw(SceneImage image,float x,float y,SceneDraw style) {
        drawRegion(image,0,0,image.width(),image.height(),x,y,image.width()*style.scaleX(),image.height()*style.scaleY(),style);
    }
    public void draw(SceneSprite sprite,float x,float y,SceneDraw style) {
        float ox=style.flipX()?sprite.width()-sprite.originX():sprite.originX();
        float oy=style.flipY()?sprite.height()-sprite.originY():sprite.originY();
        draw(sprite.image(),x-ox*style.scaleX(),y-oy*style.scaleY(),style);
    }
    public void drawRegion(SceneImage image,int sx,int sy,int sw,int sh,float dx,float dy,float dw,float dh,SceneDraw style) {
        if (sw<=0||sh<=0||dw<=0||dh<=0||dx+dw<clipX||dx>=clipX+clipW||dy+dh<clipY||dy>=clipY+clipH) return;
        List<Span> spans=images.computeIfAbsent(image,LevelPictureCanvas::spans);
        for (Span span:spans) {
            if (span.y()<sy||span.y()>=sy+sh) continue;
            int left=Math.max(span.x(),sx), right=Math.min(span.x()+span.width(),sx+sw);
            if (right<=left) continue;
            int rx=style.flipX()?sx+sw-right:left-sx;
            int ry=style.flipY()?sy+sh-1-span.y():span.y()-sy;
            int x0=Math.round(dx+rx*dw/sw), x1=Math.round(dx+(rx+right-left)*dw/sw);
            int y0=Math.round(dy+ry*dh/sh), y1=Math.round(dy+(ry+1)*dh/sh);
            fill(x0,y0,x1-x0,y1-y0,tint(span.argb(),style));
        }
    }
    private static List<Span> spans(SceneImage image) {
        List<Span> out=new ArrayList<>();
        for (int y=0;y<image.height();y++) for (int x=0;x<image.width();) {
            int colour=image.pixel(x,y), begin=x++;
            while (x<image.width()&&image.pixel(x,y)==colour) x++;
            if ((colour>>>24)!=0) out.add(new Span(begin,y,x-begin,colour));
        }
        return List.copyOf(out);
    }
    private static int tint(int argb,SceneDraw style) {
        int tint=style.tint(), flash=style.flash(), alpha=flash>>>24;
        int out=(argb>>>24)*(tint>>>24)/255<<24;
        for (int shift=0;shift<=16;shift+=8) {
            int channel=(argb>>>shift&255)*(tint>>>shift&255)/255;
            channel=(channel*(255-alpha)+(flash>>>shift&255)*alpha)/255;
            out|=channel<<shift;
        }
        return out;
    }
}

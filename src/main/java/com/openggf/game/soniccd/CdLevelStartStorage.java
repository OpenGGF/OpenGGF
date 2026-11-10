package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.scroll.*;
import static com.openggf.game.soniccd.SonicCdR31aLoadingVisit.*;
/** One fixed Main167-223 clear sequence and existing shared scroll projections.
 * This class owns no lifetime/receipt/storage; original private loading calls own adoption.
 * Native unknown buffers remain opaque, never an addressable memory model. */
final class CdLevelStartStorage {
 enum Cut { POWERUP_FLOWERS,DRAW_QUEUE,FLOWERS,OBJECTS,UNKNOWN_BUFFER,MISC,CAMERA,DEMO_INDEX }
 interface Port {void clear(Cut cut);}
 static void clear(Port original){
  original.clear(Cut.POWERUP_FLOWERS);original.clear(Cut.DRAW_QUEUE);
  original.clear(Cut.FLOWERS);original.clear(Cut.OBJECTS);original.clear(Cut.UNKNOWN_BUFFER);
  original.clear(Cut.MISC);original.clear(Cut.CAMERA);original.clear(Cut.DEMO_INDEX);
 }
    static Scroll flowersCleared(Scroll s){var b=s.background();var c=new CdR11aBackgroundPlane.Camera(0,0);var copies=new Copies(0,0,new CdR11aBackgroundPlane.CopiedCameras(c,c,c),0,new CdR11aBackgroundPlane.FlagWords(0,0,0));return new Scroll(s.foreground(),bg(b,b.cameraBgX(),b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),zero(512),b.deformationRam(),b.hscrollRam(),b.vscroll()),copies,s.foregroundXFractionWord(),s.topBoundWord(),s.waterDeformWord());}
    static Scroll cameraCleared(Scroll s){var b=s.background();return new Scroll(new CdR11aForegroundScroll.State(0,0,0,0,0,0,0,0,0,0,0),bg(b,0,0,0,0,0,0,zero(8),0,0,0,b.cloudRam(),b.deformationRam(),b.hscrollRam(),b.vscroll()),s.copies(),0,0,0);}

    static CdR11aBackgroundScroll.State bg(CdR11aBackgroundScroll.State old,int x,int y,int x2,int y2,int x3,int y3,CdAssetBytes cross,int flags,int flags2,int flags3,CdAssetBytes cloud,CdAssetBytes deform,CdAssetBytes hscroll,int vscroll){return new CdR11aBackgroundScroll.State(x,y,x2,y2,x3,y3,cross,flags,flags2,flags3,cloud,deform,hscroll,vscroll);}
 private static CdAssetBytes zero(int n){return CdAssetBytes.copyOf(new byte[n]);}
 private CdLevelStartStorage(){}
}

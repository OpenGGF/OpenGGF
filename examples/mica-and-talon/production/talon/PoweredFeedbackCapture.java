// Purpose: controlled native-clock art comparisons in the real gameplay renderer.
// Inputs: game, character, powered, jar-or-dash, output; all pose/speed controls declared below.
// Origin: MICA/TALON owner-feedback round 2026-11-11. No comparison data enters simulation.
import com.openggf.game.*;
import com.openggf.tools.*;
import com.openggf.sprites.playable.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.configuration.*;
import java.nio.file.*;
import java.util.*;
public class PoweredFeedbackCapture {
 public static void main(String[] a) throws Exception {
  String game=a[0], character=a[1]; boolean powered=(Boolean.parseBoolean(a[2]) || a[2].equals("hyper")); Path out=Path.of(a[4]);
  Files.createDirectories(out); var settings=new GameplayCaptureSession.Settings(320,character,"","off",null,null,null);
  try(var session=new GameplayCaptureSession(settings)) {
   if(!a[3].equals("-")) session.applyMod(Path.of(a[3]));
   GameServices.configuration().setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT,true);
   session.boot(Path.of(game+".gen"),0,1,settings);
   for(int t=0;t<120;t++) session.step(null);
   var p=session.player();
   NativePositionOps.writeXPosResetSubpixel(p,p.getCentreX()+96);
   p.currentCamera().setX((short)(p.getCentreX()-160));
   // Declared stationary presentation fixture at the loaded start, native bit7 suppresses translation.
   var csv=new StringBuilder("phase,tick,animation,mapping,x,y,gspeed,cam_x,cam_y,palette\n");
   if(powered) {
    int groundX=p.getCentreX(),groundY=p.getCentreY();
    NativePositionOps.writeYPosResetSubpixel(p,groundY-96);
    p.setAir(true);p.setJumping(true);p.setYSpeed((short)0x28);
    p.currentCamera().setY((short)(p.getCentreY()-96));
    p.addRings(100-p.getRingCount());
    for(int emerald=0;emerald<p.currentGameState().getChaosEmeraldCount();emerald++)p.currentGameState().markEmeraldCollected(emerald);
    if(a[2].equals("hyper") || character.equals("tails"))for(int emerald=0;emerald<7;emerald++)p.currentGameState().markSuperEmeraldCollected(emerald);
    if(game.equals("s3k"))p.getSuperStateController().activateFromAirAbility();else p.getSuperStateController().checkTransformationBeforeMove();
    if(!p.getSuperStateController().isSuper())throw new IllegalStateException("Requested form did not activate: "+game+"/"+character);
    System.out.println("TRANSFORM_START forced="+p.getForcedAnimationId()+" scripts="+p.getAnimationSet().getAllScripts().keySet());
    capture(session,out,csv,"transform",100);
    NativePositionOps.writeXPosResetSubpixel(p,groundX);NativePositionOps.writeYPosResetSubpixel(p,groundY);p.currentCamera().setY((short)(groundY-96));
   }
   for(var pose:List.of(CanonicalAnimation.WAIT,CanonicalAnimation.RUN,CanonicalAnimation.ROLL,CanonicalAnimation.TAILS_FLY)) {
    if(pose.name().startsWith("TAILS") && !character.endsWith(":talon") && !character.equals("tails")) continue;
    if(pose==CanonicalAnimation.RUN) {p.setGSpeed((short)0x900);p.setMovementInputActive(true);} else {p.setGSpeed((short)0);p.setMovementInputActive(false);}
    p.setRolling(pose==CanonicalAnimation.ROLL);
    if(pose==CanonicalAnimation.ROLL)p.setGSpeed((short)0x900);
    p.setPushing(false);p.setAir(false);p.setJumping(false);p.setYSpeed((short)0);p.setXSpeed((short)0);p.setAngle((byte)0);
    ObjectControlState.nativeBit7FullControl().applyTo(p);
    if(pose==CanonicalAnimation.TAILS_FLY && p.getTailsFlightController()!=null){p.setAir(true);p.getTailsFlightController().activate();}
    int id=pose==CanonicalAnimation.TAILS_FLY ? p.currentGameModule().getDonorCapabilities().forPlayableCharacter(PlayerCharacter.TAILS_ALONE).resolveNativeId(pose)
        : p.resolveAnimationId(pose==CanonicalAnimation.RUN?CanonicalAnimation.WALK:pose);
    p.setForcedAnimationId(id);p.setAnimationId(id);p.setAnimationFrameIndex(0);p.setAnimationTick(0);
    capture(session,out,csv,pose.name().toLowerCase(Locale.ROOT),90);
   }
   Files.writeString(out.resolve("state.csv"),csv);
  }
 }
 static void capture(GameplayCaptureSession s,Path out,StringBuilder csv,String phase,int count)throws Exception {
  Path frames=out.resolve(phase);Files.createDirectories(frames);
  var p=s.player(); int x=p.getCentreX(),y=p.getCentreY();
  for(int tick=0;tick<count;tick++) {
   s.step(null);
   if(!phase.equals("transform")){NativePositionOps.writeXPosResetSubpixel(p,x);NativePositionOps.writeYPosResetSubpixel(p,y);}
   ScreenshotCapture.savePNG(s.render(),frames.resolve(String.format("%05d.png",tick)));
   var context=p.getSpriteRenderer().getRenderContext();
   var pal=context==null?null:context.getPalette(p.getSpriteRenderer().paletteLine());
   csv.append(phase).append(',').append(tick).append(',').append(p.getAnimationId()).append(':').append(p.getForcedAnimationId()).append(',').append(p.getMappingFrame()).append(',').append(p.getCentreX()).append(',').append(p.getCentreY()).append(',').append(p.getGSpeed()).append(',').append(p.currentCamera().getX()).append(',').append(p.currentCamera().getY()).append(',').append(pal==null?"native":pal.getColor(4).r+":"+pal.getColor(4).g+":"+pal.getColor(4).b).append('\n');
  }
 }
}

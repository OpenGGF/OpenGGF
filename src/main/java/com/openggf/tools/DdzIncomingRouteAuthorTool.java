package com.openggf.tools;

import com.openggf.debug.playback.*;
import com.openggf.game.*;
import com.openggf.game.sonic3k.objects.*;
import java.nio.file.*;
import java.util.*;
/**
 * Read-only controller author for an actual DEZ-to-DDZ handoff. Origin: the
 * September 2026 S&K completion campaign; reused after the final-floor repair.
 * Inputs are ROM, width, incoming movie, reference controller movie, output
 * prefix and optional cold-team. Default boot is the positioned DEZ2 encounter
 * ($34B0,$300), solo Sonic,200 rings,seven Super Emeralds. Cold-team starts DEZ1
 * with Sonic+Tails and only the emerald inventory declared. No post-boot gameplay
 * state is injected. The reference movie supplies controller buttons only;
 * phase observations choose those inputs and never hydrate physics from traces.
 * The reference is s3k-sonic-tails-complete-emeralds.bk2: its DDZ input starts
 * at frame514214. Other reference movies need independently identified offsets.
 * Produces a complete input script and observed state CSV, stopping on death or
 * the ending request. This is route authoring, not native parity verification.
 */
public final class DdzIncomingRouteAuthorTool {
 public static void main(String[] args) throws Exception {
  if(args.length<5 || args.length>6) throw new IllegalArgumentException(
    "Usage: DdzIncomingRouteAuthorTool ROM WIDTH INCOMING_BK2 REFERENCE_BK2 OUTPUT_PREFIX [cold-team]");
  int width=Integer.parseInt(args[1]);
  boolean cold=args.length==6;
  if(cold && !args[5].equals("cold-team")) throw new IllegalArgumentException("Unknown boot mode: "+args[5]);
  Path output=Path.of(args[4]);
  Path scriptPath=Path.of(output+".script"),statePath=Path.of(output+".csv");
  if(Files.exists(scriptPath)||Files.exists(statePath)) throw new IllegalArgumentException("Output already exists: "+output);
  if(output.toAbsolutePath().getParent()!=null) Files.createDirectories(output.toAbsolutePath().getParent());
  var settings=new GameplayCaptureSession.Settings(width,"sonic",cold?"tails":"","off",null,
    cold?null:0x34B0,cold?null:0x300,"3333333",false,false,null,null,false,cold?null:200,false);
  var movie=new Bk2MovieLoader().loadMovieOrInputLog(Path.of(args[3]));
  if(movie.getFrameCount()<=524271) throw new IllegalArgumentException("Expected the complete reference movie, including DDZ input at514214");
  var prefix=new Bk2MovieLoader().loadMovieOrInputLog(Path.of(args[2]));
  var script=new StringBuilder();
  var states=new StringBuilder(GameplayCaptureSession.stateHeader()+"\n");
  try(var session=new GameplayCaptureSession(settings)) {
   session.boot(Path.of(args[0]),11,cold?0:1,settings);
   int prefixFrames=0;
   while(GameServices.level().getCurrentZone()!=12 && prefixFrames<prefix.getFrameCount()) {
    var input=prefix.getFrame(prefixFrames);
    appendInput(script,input);
    session.step(input);session.render();
    states.append(session.stateLine(prefixFrames,input)).append('\n');
    prefixFrames++;
    if(session.player().getDead())throw new IllegalStateException("Incoming prefix death at "+(prefixFrames-1));
   }
   if(GameServices.level().getCurrentZone()!=12)throw new IllegalStateException("Incoming movie did not load DDZ");
   System.out.println("PREFIX "+prefixFrames);

   int stage=-1,stageStart=0,nativeStart=0,lastHealth=-2;boolean phase2=false;
   for(int f=0;f<16000;f++) {
    var mgr=GameServices.level().getObjectManager();var p=session.player();var camera=GameServices.camera();
    var boss=mgr.activeObjectsOfType(DdzEndBossObjectInstance.class).stream().findFirst().orElse(null);
    var body=mgr.activeObjectsOfType(DdzEndBossBodyObjectInstance.class).stream().findFirst().orElse(null);
    int routine=boss==null?-1:DdzDiagnostics.bossRoutine(boss);
    if(routine!=stage) {stage=routine;stageStart=f;
     nativeStart=switch(stage){case 2->phase2?9903:3662;case 4->phase2?9968:3806;case 6->phase2?10025:5188;case 8->5488;case 10->5822;case 12->5854;case 14->5991;case 0->9816;default->0;};
     if(stage==14)phase2=true;
     System.out.println("STAGE "+f+" routine="+routine+" rings="+p.getRingCount()+" xy="+p.getCentreX()+","+p.getCentreY());
    }
    int mask=0;
    if(boss==null&&!phase2&&f>50) {
     var rings=GameServices.level().getCurrentLevel().getRings();var ringManager=GameServices.level().getRingManager();
     var target=rings.stream().filter(r->!ringManager.isCollected(r)&&r.x()>p.getCentreX()-12&&r.x()<p.getCentreX()+600)
      .min(Comparator.comparingDouble(r->(r.x()-p.getCentreX())+Math.abs(r.y()-p.getCentreY())*0.8)).orElse(null);
     int ty=target==null?256:target.y(); int tx=camera.getX()+160;
     int dy=ty-p.getCentreY(),dx=tx-p.getCentreX();
     mask=(dy< -3?1:dy>3?2:0)|(dx< -5?4:dx>5?8:0);
    } else if(boss!=null) {
     int age=f-stageStart;
     int n=nativeStart+((stage==4&&!phase2)?age%1382:age);
     n=Math.min(n,10057);
     mask=movie.getFrame(514214+n).p1InputMask();
     if(stage==14) {
      int dy=boss.getY()+64-p.getCentreY();
      mask=8|(dy< -4?1:dy>4?2:0)|((f&1)==0?16:0);
     }
     if(stage==4&&!phase2&&body!=null) {
      int ty=body.getY()-120;
      int tx=body.getX()+36;
      int dy=ty-p.getCentreY(),dx=tx-p.getCentreX();
      mask=(dy< -3?1:dy>3?2:0)|(dx< -5?4:dx>5?8:0);
     }
    }
    var input=new Bk2FrameInput(prefixFrames+f,mask,(mask&16)!=0?1:0,false,"");
    appendInput(script,input);
    session.step(input);session.render();
    states.append(session.stateLine(prefixFrames+f,input)).append('\n');
    int health=body==null?-1:DdzDiagnostics.bodyHitPoints(body);
    if(health!=lastHealth||f%500==0||p.getDead())System.out.println("ROW "+f+" hp="+health+" rings="+p.getRingCount()+" xy="+p.getCentreX()+","+p.getCentreY()+" cam="+camera.getX()+","+camera.getY()+" body="+(body==null?"-":body.getX()+","+body.getY())+" dead="+p.getDead());
    lastHealth=health;
    if(p.getDead()||GameServices.level().getRequestedZone()==13) {System.out.println("END "+f+" request="+GameServices.level().getRequestedZone());break;}
   }
  } finally {
   Files.writeString(scriptPath,script,StandardOpenOption.CREATE_NEW);
   Files.writeString(statePath,states,StandardOpenOption.CREATE_NEW);
  }
 }
 private static void appendInput(StringBuilder script,Bk2FrameInput input) {
  if(input.p2InputMask()!=0 || input.p2ActionMask()!=0 || input.p2StartPressed())
   throw new IllegalArgumentException("The incoming movie must use P1-only input");
  var keys=new ArrayList<String>();
  for(int i=0;i<4;i++) if((input.p1InputMask()&(1<<i))!=0) keys.add(new String[]{"U","D","L","R"}[i]);
  for(int i=0;i<3;i++) if((input.p1ActionMask()&(1<<i))!=0) keys.add(new String[]{"A","B","C"}[i]);
  if(input.p1StartPressed())keys.add("S");
  script.append("1 ").append(keys.isEmpty()?"-":String.join("+",keys)).append('\n');
 }
}

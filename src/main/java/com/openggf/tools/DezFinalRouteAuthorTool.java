package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.TouchResponseProvider;
import java.util.List;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;

/**
 * Controller-only final DEZ route author, originating in the September 2026 S&K
 * completion campaign (37cba17bc). Inputs: ROM path, viewport width, output prefix.
 * Boots solo Sonic in zone23 with 200 rings and seven Super Emeralds, observes
 * live objects and emits ordinary controller input. Never writes gameplay state
 * after the declared boot; this is route exploration, not a native parity oracle.
 *
 * <p>Usage: {@code DezFinalRouteAuthorTool ROM WIDTH OUTPUT_PREFIX}. Produces a
 * script accepted by InputLogAuthorTool and a state CSV. Stops on death, outgoing
 * load or 22000 frames. Targets and braking distances are controller strategy,
 * not engine rules. Private encounter types are selected read-only by class name
 * so authoring does not expand the runtime's public API.
 */
public final class DezFinalRouteAuthorTool {
    public static void main(String[] args) throws Exception {
        if(args.length!=3) throw new IllegalArgumentException("Usage: DezFinalRouteAuthorTool ROM WIDTH OUTPUT_PREFIX");
        Path rom=Path.of(args[0]); int width=Integer.parseInt(args[1]); Path output=Path.of(args[2]);
        Path scriptPath=Path.of(output+".script"), statePath=Path.of(output+".csv");
        if(Files.exists(scriptPath)||Files.exists(statePath)) throw new IllegalArgumentException("Output already exists: "+output);
        if(output.toAbsolutePath().getParent()!=null) Files.createDirectories(output.toAbsolutePath().getParent());
        var settings=new GameplayCaptureSession.Settings(width,"sonic","","off",null,null,null,"3333333",false,false,null,null,false,200,false);
        var script=new StringBuilder(); var states=new StringBuilder(GameplayCaptureSession.stateHeader()+"\n");
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(rom,23,0,settings);
            int lastHealth=-1,lastCount=-1;
            boolean heldJump=false;
            int jumpTicks=0;
            boolean attackRun=false;
            int previousCoreHealth=8,previousShipHealth=-1;
            boolean brakeFinal=false;
            ObjectInstance lastBeam=null;
            int beamAge=0;
            for(int frame=0;frame<22000;frame++) {
                var p=session.player();
                var fingers=targets("DezFinalHand$Finger");
                var target=fingers.stream().filter(f->f.getCollisionProperty()>0)
                            .min(Comparator.comparingInt(f->Math.abs(f.getX()-p.getCentreX()))).orElse(null);
                var button=targets("DezFinalMouth$Button").stream().findFirst().orElse(null);
                var core=targets("DezFinalCore").stream().findFirst().orElse(null);
                var beam=objects("DezFinalBeam").stream().findFirst().orElse(null);
                if(beam!=lastBeam) {lastBeam=beam;beamAge=0;if(beam!=null)System.out.println("BEAM "+frame);}
                else if(beam!=null)beamAge++;
                int targetX=target!=null?target.getX():p.getCentreX()+80;
                boolean jump=target!=null&&!p.getAir()&&!heldJump&&Math.abs(targetX-p.getCentreX())<70;
                if(target==null&&button!=null) {
                    int safeX=com.openggf.game.sonic3k.runtime.DezFinalCamera.nativeX(GameServices.camera())+220;
                    int launchX=com.openggf.game.sonic3k.runtime.DezFinalCamera.nativeX(GameServices.camera())+190;
                    if(core!=null&&core.getCollisionProperty()<previousCoreHealth) {
                        System.out.println("CORE "+frame+" health "+core.getCollisionProperty());
                        attackRun=false; jumpTicks=0; previousCoreHealth=core.getCollisionProperty();
                    }
                    if(attackRun&&!p.getAir()&&jumpTicks< -10)attackRun=false;
                    if(!attackRun&&!p.getAir()&&Math.abs(p.getCentreX()-launchX)<12&&button.getCollisionFlags()!=0) {
                        attackRun=true;jumpTicks=24;
                    }
                    targetX=attackRun?(core!=null&&core.getCollisionFlags()!=0?core.getX():button.getX()):(button.getCollisionFlags()!=0?launchX:safeX);
                    // The visible charge script lasts 50 passes, followed by the ROM's
                    // 120-pass wait. Jump before its firing animation reaches the damaging row.
                    if(beam!=null&&beamAge>=145&&beamAge<155&&!p.getAir()) {
                        attackRun=false;targetX=safeX;jumpTicks=20;
                    }
                    jump=jumpTicks-->0;
                }
                var ship=targets("DezFinalEscapeShip").stream().findFirst().orElse(null);
                if(ship!=null&&ship.getCollisionProperty()!=previousShipHealth) {
                    System.out.println("SHIP "+frame+" health "+ship.getCollisionProperty());previousShipHealth=ship.getCollisionProperty();
                }
                if(ship!=null&&ship.getCollisionProperty()>0&&ship.getX()>=com.openggf.game.sonic3k.runtime.DezFinalCamera.nativeX(GameServices.camera())+180
                            &&ship.getY()<GameServices.camera().getY()+0x90) {
                    // Approach ahead of the last hit and brake: the native rebound
                    // reverses X velocity, so a leftward strike sends Sonic toward
                    // the surviving floor. A missed attempt returns to approach.
                    if(brakeFinal&&!p.getAir()&&jumpTicks< -10)brakeFinal=false;
                    if(ship.getCollisionProperty()==1&&!p.getAir()&&p.getCentreX()>ship.getX()+32)brakeFinal=true;
                    targetX=ship.getX()+(ship.getCollisionProperty()==1?(brakeFinal?-80:64):0);
                    if(!p.getAir()&&!heldJump&&(ship.getCollisionProperty()==1?brakeFinal&&p.getXSpeed()<=0x80:Math.abs(targetX-p.getCentreX())<72))jumpTicks=24;
                    jump=jumpTicks-->0;
                }
                if(ship!=null&&ship.getCollisionProperty()==0) {
                    targetX=com.openggf.game.sonic3k.runtime.DezFinalCamera.nativeX(GameServices.camera())+280;
                    if(!p.getAir()&&!heldJump)jumpTicks=24;
                    jump=jumpTicks-->0;
                }
                int dx=targetX-p.getCentreX();
                // Brake against observed momentum rather than writing any player or boss state.
                boolean left=dx< -8 || Math.abs(dx)<=8&&p.getXSpeed()>0x80;
                boolean right=dx>8 || Math.abs(dx)<=8&&p.getXSpeed()< -0x80;
                if(p.isObjectControlled()) {left=false;right=true;jump=false;}
                String buttons=(left?"L":right?"R":"")+(jump?(left||right?"+A":"A"):"");
                script.append("1 ").append(buttons.isEmpty()?"-":buttons).append('\n');
                int mask=(left?AbstractPlayableSprite.INPUT_LEFT:0)|(right?AbstractPlayableSprite.INPUT_RIGHT:0)|(jump?AbstractPlayableSprite.INPUT_JUMP:0);
                var input=new Bk2FrameInput(frame,mask,jump?1:0,false,"");
                session.step(input); session.render(); states.append(session.stateLine(frame,input)).append('\n'); heldJump=jump;
                int health=fingers.stream().mapToInt(Target::getCollisionProperty).sum();
                if(health!=lastHealth||fingers.size()!=lastCount||frame%600==0) {
                    System.out.println("STEP "+frame+" fingers "+fingers.size()+" health "+health+" p "+p.getCentreX()+","+p.getCentreY()+" camera "+com.openggf.game.sonic3k.runtime.DezFinalCamera.nativeX(GameServices.camera())+" dead "+p.getDead()+" core "+(core==null?-1:core.getCollisionProperty())+" button "+(button==null?-1:button.getCollisionFlags()));
                    lastHealth=health;lastCount=fingers.size();
                }
                if(GameServices.level().getCurrentZone()!=23||p.getDead())break;
            }
        } finally {
            // Keep the actual attempted input if a production update throws.
            Files.writeString(scriptPath,script,StandardOpenOption.CREATE_NEW);
            Files.writeString(statePath,states,StandardOpenOption.CREATE_NEW);
        }
    }

    private static List<ObjectInstance> objects(String type) {
        String qualified="com.openggf.game.sonic3k.objects."+type;
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object->object.getClass().getName().equals(qualified)).toList();
    }
    private static List<Target> targets(String type) {
        return objects(type).stream().map(Target::new).toList();
    }
    private record Target(ObjectInstance object) {
        int getX() { return object.getX(); }
        int getY() { return object.getY(); }
        int getCollisionProperty() { return ((TouchResponseProvider)object).getCollisionProperty(); }
        int getCollisionFlags() { return ((TouchResponseProvider)object).getCollisionFlags(); }
    }
}

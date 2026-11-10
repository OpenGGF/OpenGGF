package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.player.*;
import java.util.*;
/** Common completed pure contact response projection. Actual original owners prevalidate/adopt.
 * No Result/record grants a Frame, pool or World operation. */
public final class CdPrimaryTouchValues {
 public record Response(CdRingGlobals ring,CdOpeningObjectRuntime.ScoreGlobals score,CdPlayerBody.ContactGlobals contact,List<CdObjectState> changed){public Response{changed=List.copyOf(changed);}}
 public static Response response(CdPlayerBody.ObjectTouchResponse effect,CdRingGlobals ring){
  var result=effect.result();var world=result.world();return new Response(new CdRingGlobals(world.rings(),world.lives(),ring.livesFlags(),ring.updateHudRings(),world.updateHudLives(),ring.timeStopTimer(),ring.ringAnimationTimer(),ring.ringAnimationFrame()),new CdOpeningObjectRuntime.ScoreGlobals(world.score(),world.nextLifeScore(),world.updateHudScore()),effect.contact(),result.changedSlots());
 }
 private CdPrimaryTouchValues(){}
}

package com.openggf.game.soniccd;
import com.openggf.game.soniccd.player.*;
import java.util.*;
/** Shared Main music pause gate after the actual Finish/count. Values only, no clock or playback. */
final class CdMainMusic {
 record Result(int pauseMusicBit,List<SonicCdGameModule.PastSound> sounds){Result{sounds=List.copyOf(sounds);}}
 static Result step(int old,boolean paused,CdPlayerBody.Environment e){
  if((old&~128)!=0)throw new IllegalArgumentException("Actual pause music bit7");var sounds=new ArrayList<SonicCdGameModule.PastSound>();
  if(paused)sounds.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ChargeSound(CdPlayerCharge.Sound.FM_CHARGESTOP)));
  if(paused?((old&128)==0):((old&128)!=0)){boolean pcm=(e.movement().gates().timeZone()&127)==0&&e.globals().invincible()==0&&e.movement().gates().speedShoes()==0;var command=paused?(pcm?SonicCdGameModule.PastMusicCommand.SCMD_PAUSEPCM:SonicCdGameModule.PastMusicCommand.SCMD_PAUSECDA):(pcm?SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSEPCM:SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSECDA);sounds.add(new SonicCdGameModule.PastSubCpuSound(command));}
  return new Result(paused?old|128:old&127,sounds);
 }
 private CdMainMusic(){}
}

package com.openggf.game.soniccd.level;
/** Shared pure PlayLevelMusic WORD/BYTE arithmetic (native Main555-572), no table/default command. */
final class CdLevelMusicIndex {
 private CdLevelMusicIndex(){}
 static int select(int zoneAct,int rawPeriod,int timeAttack,int goodFuture){
  if((zoneAct&~65535)!=0)throw new IllegalArgumentException("Native zoneAct WORD required");
  for(int b:new int[]{rawPeriod,timeAttack,goodFuture})if((b&~255)!=0)throw new IllegalArgumentException("Native music selector BYTE required");
  int period=rawPeriod&127;if(timeAttack==0&&period==2)period=(period+goodFuture)&255;
  return ((zoneAct>>>8)*4+period)&65535;
 }
}

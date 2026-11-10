package com.openggf.game.soniccd;

/** Native Main.WaitPLC/WaitPLC2 and Palette Fade service cadence. N1ecc5f3a Main148-153,
 * 253-260; Palette Fade23-39,190-206. The real loading owner retains state and scope.
 * Each invocation consumes exactly one caller row; this kernel never drives another row. */
final class CdLoadingWaits {
 private CdLoadingWaits(){}
 interface Programme {
  void plc(int held);void palette(int held,boolean initial);
  void ordinary(int held);void returnWait(int held);
 }
 /** Single loading phase dispatch for both real callers. Named historical Phase is a
  * semantic programme cut, never a stage-name selector or a second retained scheduler. */
 static void service(SonicCdR31aLoadingVisit.Phase phase,int held,Programme original){
  switch(phase){
   case FIRST_PLC,LEVEL_PLC -> original.plc(held);
   case INITIAL_TO_WHITE,INITIAL_TO_BLACK -> original.palette(held,true);
   case FROM_WHITE,FROM_BLACK -> original.palette(held,false);
   case READY -> original.ordinary(held);
   case CLEAR_RETURN_WAIT -> original.returnWait(held);
   default -> throw new IllegalStateException("Unserviceable original loading phase");
  }
 }
 interface Plc {
  void selectorC(int held);
  boolean processAndTestQueue();
  void returned();
 }
 interface Palette {
  void selector12(int held);
  void colours();
  void processPlcs();
  boolean countAndTestLast();
  void returned();
 }
 static void plc(int held,Plc original){original.selectorC(held);if(original.processAndTestQueue())original.returned();}
 static void palette(int held,Palette original){original.selector12(held);original.colours();original.processPlcs();if(original.countAndTestLast())original.returned();}
}

package com.openggf.game.soniccd;

import java.util.Objects;
import com.openggf.game.soniccd.level.CdAssetBytes;

/** Shared semantic LevelStart entry, not a loader/Host/World or an invocation token.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff src/Level/Main.asm:19-57.
 * Actual source-bound callers retain each completed store before the next fallible read/call.
 * DEMO=0 original programmes only. Fade services remain in their source-bound caller. */
final class CdLevelStart {
 private CdLevelStart(){}
 enum Cut { CLEAR_DEMO,ENTRY_STORES,PLC_LOADED,FRESH_PREFIX,RESET_SAVED_FLAGS,FRESH_SUFFIX,
            MARK_INITIALIZING,CLEAR_PLCS,NORMAL_PALETTE }
 interface Port {
  void clearDemo();int timeStones();int timeAttack();void forceGoodFuture();
  void entryStores();boolean setPlcLoaded();
  void freshPrefix();void resetSavedFlags();void freshSuffix();void markInitializing();void clearPlcs();
 }
 static void enter(Port port){
  Objects.requireNonNull(port);
  port.clearDemo();
  // Native short-circuit: timeAttack is not read unless the complete stones BYTE is $7F.
  if(port.timeStones()==0x7F&&port.timeAttack()==0)port.forceGoodFuture();
  port.entryStores();
  if(!port.setPlcLoaded()){
   port.freshPrefix();port.resetSavedFlags();port.freshSuffix();
  }
  port.markInitializing();port.clearPlcs();
 }
 /** Main59-68: actual old-bit BSET branch. Callbacks are synchronous semantic stores,
  * not captured commands or invocation authority. A special-stage read short-circuits
  * both the period read and BSET exactly as the native direct branch does. */
 interface InitialFade {
  int specialStage();int rawPeriod();boolean setPaletteFadeFlag();
  void beginFade(boolean white);void skipped(boolean white);
 }
 static void initialFade(InitialFade port){
  Objects.requireNonNull(port);
  if(port.specialStage()!=0){port.beginFade(true);return;}
  boolean white=(port.rawPeriod()&128)!=0;
  if(port.setPaletteFadeFlag())port.beginFade(white);else port.skipped(white);
 }
 /** Main69-81. Only an actually completed white fade reaches the flag clear.
  * The SkipFade ingress does not. Reads/stores retain their landed prefix if a
  * later callback fails; this helper neither catches nor reconstructs effects. */
 interface WhiteReturn {
  void clearPaletteFadeFlag();void clearDirection();int restartWord();void clearRestartWord();
  int actByte();void clearPeriodMarker();void returned();
 }
 static boolean afterWhite(boolean completedFade,WhiteReturn port){
  Objects.requireNonNull(port);
  if(completedFade)port.clearPaletteFadeFlag();
  port.clearDirection();
  if(port.restartWord()==0)return false;
  port.clearRestartWord();
  if(port.actByte()==2)port.clearPeriodMarker();
  port.returned();return true;
 }
 /** Main.NormalLoad131-147: exactly64 colour WORDs, no displayed palette publication. */
 static CdAssetBytes normalLoadPalette(boolean white){
  byte[] bytes=new byte[128];if(white)for(int i=0;i<128;i+=2){bytes[i]=14;bytes[i+1]=(byte)238;}
  return CdAssetBytes.copyOf(bytes);
 }

}

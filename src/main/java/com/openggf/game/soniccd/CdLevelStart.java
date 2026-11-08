package com.openggf.game.soniccd;

import java.util.Objects;
import com.openggf.game.soniccd.level.CdAssetBytes;

/** Shared semantic LevelStart entry, not a loader/Host/World or an invocation token.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff src/Level/Main.asm:19-57.
 * Actual source-bound callers retain each completed store before the next fallible read/call.
 * DEMO=0 original programmes only. Later fades/waits are not executed by this prefix. */
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
 /** Main.NormalLoad131-147: exactly64 colour WORDs, no displayed palette publication. */
 static CdAssetBytes normalLoadPalette(boolean white){
  byte[] bytes=new byte[128];if(white)for(int i=0;i<128;i+=2){bytes[i]=14;bytes[i+1]=(byte)238;}
  return CdAssetBytes.copyOf(bytes);
 }

}

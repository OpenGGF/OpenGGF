package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import java.util.*;
/** Typed original R11 current-pattern adapter into the existing selected-display converter.
 * No static source pixels, service, renderer, mutable bank or invocation authority lives here. */
final class CdOriginalR11Display {
 record Inputs(Optional<CdObjectDrawQueue.CpuSat> selectedCpu,CdAssetBytes selectedSat,CdAssetBytes palette,
               CdAssetBytes hscroll,int vscrollLongBits,boolean displayEnabled,
               CdR11aBackgroundPlane.State foreground,CdR11aBackgroundPlane.State background,
               CdR11aCameraArtResidency.State current,CdPlayerArtRuntime.State player) implements SonicCdCurrentDisplay.Inputs {
  Inputs{Objects.requireNonNull(selectedCpu);if(selectedSat.length()!=640||palette.length()!=128||hscroll.length()!=1024)throw new IllegalArgumentException("Native SAT/palette/HScroll extents");if(displayEnabled){Objects.requireNonNull(foreground);Objects.requireNonNull(background);Objects.requireNonNull(current);Objects.requireNonNull(player);}}
 }
 record Selection(Inputs inputs,SonicCdPastDisplay.Projection projection){Selection{Objects.requireNonNull(inputs);Objects.requireNonNull(projection);}}
 static Selection project(Inputs inputs){return new Selection(inputs,SonicCdCurrentDisplay.project(inputs,id->{
  try{return CdR11aCameraArtResidency.readTile(inputs.current(),id);}
  catch(CdR11aCameraArtResidency.OwnershipBoundary fault){throw new SonicCdCurrentDisplay.ReadStopped(new SonicCdPastDisplay.DisplayFailure(
   SonicCdPastDisplay.Boundary.valueOf(fault.boundary().name()),-1,fault.nativePatternId(),fault.destinationWord()));}
 }));}
 private CdOriginalR11Display(){}
}

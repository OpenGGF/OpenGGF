package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import com.openggf.game.soniccd.scroll.CdR11bBackgroundScroll;

import java.io.IOException;
import java.util.Objects;

/** Actual original-R11 source strategy. No World, Frame, pool, lifecycle or scope is owned here.
 * The existing source-specific algorithms are selected, never copied or relabelled as TT.
 */
final class CdR11OriginalWorldBindings implements CdOriginalWorldBindings<CdR11aSectionArt.State> {
    private final CdStagePlayerSource source;
    private final CdR11aAssets assets;
    private final CdR11aSectionArt art;
    private final CdObjectPlacements placements;
    private final CdLevelLoadingSource past;

    CdR11OriginalWorldBindings(CdStagePlayerSource source,CdR11aAssets assets,
                               CdR11aSectionArt art,CdObjectPlacements placements) {
        this.source=Objects.requireNonNull(source);this.assets=Objects.requireNonNull(assets);this.past=null;
        this.art=Objects.requireNonNull(art);this.placements=Objects.requireNonNull(placements);
        if(!source.ownsPresent(assets)||!art.ownsLoadingAssets(assets)||placements!=assets.placements())
            throw new IllegalArgumentException("Exact original R11 source owners required");
    }
    /** Actual standard Past source pair. No A aggregate or TT profile is substituted. */
    CdR11OriginalWorldBindings(CdStagePlayerSource source,CdLevelLoadingSource past,
                               CdR11aSectionArt art,CdObjectPlacements placements){
        this.source=Objects.requireNonNull(source);this.past=Objects.requireNonNull(past);this.assets=null;
        this.art=Objects.requireNonNull(art);this.placements=Objects.requireNonNull(placements);
        if(past.file()!=com.openggf.game.soniccd.CdNativeFile.R11B
                ||!source.hasRetainedPpzProgrammeSource()||past.retainedPlayerSource()!=source
                ||past.standardBackgroundProgram()!=CdLevelLoadingSource.StandardBackgroundProgram.PARALLAX_CLOUD_BANDS
                ||!art.ownsLoadingSource(past,source)||placements!=past.placements())
            throw new IllegalArgumentException("Exact original Past source/current-art/placement owners required");
        past.standardPastObjectSource().requireRetained();
    }
    @Override public CdStagePlayerSource playerSource(){return source;}
    @Override public CdObjectPlacements placements(){return placements;}
    @Override public int nativeZoneActWord(){return source.ordinaryZoneActWord();}

    // These reads remain at the original constructor's source/call positions. No eager new preload.
    CdR11aObjectAssets objectAssets()throws IOException{return assets.openingObjects();}
    CdR11aStartupSource startupObjects()throws IOException{return assets.startupObjects();}
    CdObjectMappings mappings(CdR11aObjectAssets metadata,CdR11aStartupSource startup)throws IOException {
        return CdObjectMappings.fromOriginalR11Startup(assets,metadata,startup);
    }
    Hud<CdR11aSectionArt.State> hud(CdR11aStartupSource startup) {
        var nativeHud=new CdLevelHud.OriginalR11(assets,startup,art);
        return adaptHud(nativeHud);
    }
    Hud<CdR11aSectionArt.State> pastHud(){
        if(past==null)throw new IllegalStateException("No original Past HUD source");
        return adaptHud(new CdLevelHud.OriginalR11(past,past.standardPastObjectSource().font(),art));
    }
    private static Hud<CdR11aSectionArt.State> adaptHud(CdLevelHud.OriginalR11 nativeHud){
        return (before,inputs,access)->{
            var state=new CdLevelHud.OriginalR11State(before.section(),before.ring(),before.level(),before.score(),before.liveTime(),before.bonus());
            try{access.retain(hudState(nativeHud.run(state,inputs).state()));}
            catch(CdLevelHud.UnsupportedOriginalR11Hud original){
                try{access.retain(hudState(original.state()));}
                catch(RuntimeException|Error retention){if(retention!=original)original.addSuppressed(retention);}throw original;
            }
            catch(CdLevelHud.OriginalR11ArtFailure prefix){
                var original=prefix.original();
                try{access.retain(hudState(prefix.state()));}
                catch(RuntimeException|Error retention){if(retention!=original)original.addSuppressed(retention);}throw original;
            }
        };
    }
    private static HudState<CdR11aSectionArt.State> hudState(CdLevelHud.OriginalR11State state) {
        return new HudState<>(state.section(),state.ring(),state.level(),state.score(),state.liveTime(),state.bonus());
    }
    @Override public ScrollResult scroll(SonicCdR31aLoadingVisit.Scroll before,CdR11aForegroundScroll.Input input,
                                         int rawTimeZoneByte,int playerXVelocity,int playerX,int playerControl) {
        var out=CdR11aForegroundScroll.stepForPeriodBackground(before.foreground(),input,rawTimeZoneByte);
        if(past!=null){
            var back=CdR11bBackgroundScroll.step(before.background(),new CdR11bBackgroundScroll.Input(
                    out.state().cameraX(),out.state().cameraYWord(),(short)out.state().scrollXDiff(),
                    (short)out.state().scrollYDiff(),out.state().scrollLock()>>>8));
            return new ScrollResult(out,new SonicCdR31aLoadingVisit.Scroll(out.state(),back,before.copies(),
                    before.foregroundXFractionWord(),before.topBoundWord(),before.waterDeformWord()));
        }
        var back=CdR11aBackgroundScroll.bind(assets).step(before.background(),new CdR11aBackgroundScroll.Input(
                out.state().cameraX(),out.state().cameraYWord(),(short)out.state().scrollXDiff(),
                playerXVelocity,playerX,playerControl,out.state().scrollLock()>>>8));
        return new ScrollResult(out,new SonicCdR31aLoadingVisit.Scroll(out.state(),back.state(),before.copies(),
                before.foregroundXFractionWord(),before.topBoundWord(),before.waterDeformWord()));
    }
    @Override public PaletteResult paletteCycle(PaletteAccess access)throws IOException {
        var scripts=past==null?assets.paletteCycle().scripts():past.cycles().stream().map(c->{
            if(c.kind()!=CdLevelLoadingSource.CycleKind.STANDARD)throw new IllegalArgumentException("Foreign original Past palette format");
            return new CdLevelPaletteCycle.Script(c.script(),c.colors());
        }).toList();
        var before=access.readPalette();
        var result=CdLevelPaletteCycle.pass(scripts,
                new CdLevelPaletteCycle.State(before.palette(),before.timers7().slice(0,6),before.steps7().slice(0,6)));
        var next=result.state();var steps=before.steps7().copy();var timers=before.timers7().copy();
        System.arraycopy(next.steps().copy(),0,steps,0,6);System.arraycopy(next.timers().copy(),0,timers,0,6);
        return new PaletteResult(result,CdAssetBytes.copyOf(timers),CdAssetBytes.copyOf(steps));
    }
    @Override public UnsupportedOperationException paletteFailure(CdLevelPaletteCycle.Fault fault) {
        return new UnsupportedOperationException("Actual original R11 palette source read: "+fault);
    }
}

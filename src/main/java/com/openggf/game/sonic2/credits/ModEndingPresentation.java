package com.openggf.game.sonic2.credits;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.*;
import com.openggf.game.presentation.*;
import com.openggf.game.sonic2.Sonic2PlayerArt;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.S2SpriteDataLoader;
import com.openggf.level.Palette;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import java.io.IOException;
import java.util.*;

/** Mod art adapter for the native Sonic route; timing and companion art remain native. */
final class ModEndingPresentation implements Sonic2EndingCutsceneManager.Presentation {
    private final Rom rom;
    private final CharacterDefinition definition;
    private final Map<EndingSpriteSet.Surface, EndingSpriteSet> sets;
    private final Map<EndingSpriteSet.Surface, PlayerSpriteRenderer> renderers = new EnumMap<>(EndingSpriteSet.Surface.class);
    private final PlayerSpriteRenderer playable;
    private final SpriteArtSet nativePlayer;
    private final SpriteArtSet characterArt;
    private Palette[] nativePalettes;
    private final SuperFormProfile.Form powered;
    private int ticks;
    private int white = 7;
    private boolean toWhite;
    private static final int[] LEVELS = {0,36,73,109,146,182,219,255};

    static ModEndingPresentation create(Rom rom) throws IOException {
        var definition = StageCharacterPresentation.activeDefinition();
        var sets = new EnumMap<EndingSpriteSet.Surface, EndingSpriteSet>(EndingSpriteSet.Surface.class);
        if (definition != null && definition.presentationSupplier() != null) {
            var presentation = definition.presentationSupplier().load(definition.key().persisted());
            if (presentation != null) sets.putAll(presentation.endings());
        }
        // Photos depict the search party, not the missing main character.
        sets.remove(EndingSpriteSet.Surface.S2_PHOTOS);
        sets.remove(EndingSpriteSet.Surface.S2_TORNADO_PILOT);
        var world = com.openggf.game.session.SessionManager.getCurrentWorldSession();
        var sidekicks = com.openggf.game.session.ActiveGameplayTeamResolver.resolveSidekicks(GameServices.configuration());
        var photos = world == null ? null : sidekickPhotos(world.getPlayableCharacterRegistry(), sidekicks);
        if (photos != null) sets.put(EndingSpriteSet.Surface.S2_PHOTOS, photos);
        var pilot = EndingCharacterPresentation.sidekick(EndingSpriteSet.Surface.S2_TORNADO_PILOT);
        if (pilot != null) sets.put(EndingSpriteSet.Surface.S2_TORNADO_PILOT, pilot);
        if (!sets.containsKey(EndingSpriteSet.Surface.S2_TORNADO_PILOT) && !sets.containsKey(EndingSpriteSet.Surface.S2_ENDING) && !sets.containsKey(EndingSpriteSet.Surface.S2_PHOTOS)) return null;
        return new ModEndingPresentation(rom, definition, sets);
    }
    static EndingSpriteSet sidekickPhotos(PlayableCharacterRegistry registry, List<String> sidekicks) throws IOException {
        if (sidekicks.isEmpty()) return null;
        var sidekick = registry.find(CharacterKey.parsePersisted(sidekicks.getFirst())).orElse(null);
        if (sidekick == null || sidekick.presentationSupplier() == null) return null;
        var presentation = sidekick.presentationSupplier().load(sidekick.key().persisted());
        return presentation == null ? null : presentation.endings().get(EndingSpriteSet.Surface.S2_PHOTOS);
    }
    private ModEndingPresentation(Rom rom, CharacterDefinition definition,
            Map<EndingSpriteSet.Surface, EndingSpriteSet> sets) throws IOException {
        this.rom=rom; this.definition=definition; this.sets=sets;
        var nativeArt = new Sonic2PlayerArt(RomByteReader.fromRom(rom));
        nativePlayer = Sonic2EndingArt.determineEndingRoutine() == Sonic2EndingArt.EndingRoutine.TAILS
                ? nativeArt.loadTails() : nativeArt.loadSonic();
        characterArt=definition == null ? nativePlayer : definition.artSupplier().load(definition.key().persisted());
        playable = new PlayerSpriteRenderer(StageCharacterPresentation.playable(nativePlayer));
        StageCharacterPresentation.bindPalette(playable, definition);
        for (var entry:sets.entrySet()) if(java.util.EnumSet.of(EndingSpriteSet.Surface.S2_ENDING,
                EndingSpriteSet.Surface.S2_SUPER, EndingSpriteSet.Surface.S2_FLOAT,
                EndingSpriteSet.Surface.S2_PHOTOS, EndingSpriteSet.Surface.S2_TORNADO_PILOT).contains(entry.getKey()))
            renderers.put(entry.getKey(),EndingCharacterPresentation.renderer(entry.getValue()));
        var profile=definition==null || definition.superFormSupplier()==null?null:definition.superFormSupplier().load(definition.key().persisted());
        powered=profile==null?null:profile.games().getOrDefault(GameId.S2,Map.of()).get(SuperFormProfile.Tier.SUPER);
        updatePalettes();
    }
    @Override public boolean nativeSuperChoreography() { return true; }
    @Override public void applyArt(Sonic2EndingArt art) {
        nativePalettes=Arrays.stream(art.getEndingPalettes()).map(Palette::deepCopy).toArray(Palette[]::new);
    }
    @Override public SpriteArtSet playerArt() { return nativePlayer; }
    @Override public List<SpriteMappingFrame> objectFrames() throws IOException {
        return S2SpriteDataLoader.loadMappingFrames(RomByteReader.fromRom(rom),Sonic2Constants.MAP_UNC_OBJCF_ADDR);
    }
    @Override public int[] floatingFrames() { return new int[]{84,85,86,87,88}; }
    @Override public int floatingFrameDuration() { return 8; }
    @Override public int[] walkingFrames() { return definition == null ? new int[]{0,1,2,3,4,5} : characterArt.animationSet().getScript(CanonicalAnimation.WALK.ordinal()).frames().stream().mapToInt(Integer::intValue).toArray(); }
    @Override public int waitingFrame() { return definition == null ? 1 : characterArt.animationSet().getScript(CanonicalAnimation.WAIT.ordinal()).frames().getFirst(); }
    @Override public String playerCode() { return definition == null ? "sonic" : definition.key().persisted(); }
    @Override public Palette[] palettes() { return Arrays.stream(nativePalettes).map(Palette::deepCopy).toArray(Palette[]::new); }
    @Override public Sonic2EndingArt.EndingRoutine endingRoutine() { return Sonic2EndingArt.determineEndingRoutine(); }
    @Override public Sonic2LogoFlashManager.Presentation logo() { return null; }
    @Override public boolean drawPlayer(int frame,int x,int y) {
        if (draw(EndingSpriteSet.Surface.S2_FLOAT,frame,x,y)) return true;
        if (definition == null) return false;
        playable.drawFrame(frame,x,y,false,false); return true;
    }
    @Override public boolean hasObjectFrame(int frame) {
        return sets.containsKey(EndingSpriteSet.Surface.S2_ENDING) && sets.get(EndingSpriteSet.Surface.S2_ENDING).sprites().frames().containsKey(frame)
                || sets.containsKey(EndingSpriteSet.Surface.S2_SUPER)
                && sets.get(EndingSpriteSet.Surface.S2_SUPER).sprites().frames().containsKey(frame);
    }
    @Override public boolean drawObject(int frame,int x,int y) {
        return draw(EndingSpriteSet.Surface.S2_ENDING,frame,x,y)
                || draw(EndingSpriteSet.Surface.S2_SUPER,frame,x,y);
    }
    @Override public boolean drawPilot(int frame,int x,int y) {
        return draw(EndingSpriteSet.Surface.S2_TORNADO_PILOT,frame,x,y);
    }
    @Override public boolean drawPhoto(int index,int x,int y) {
        var set=sets.get(EndingSpriteSet.Surface.S2_PHOTOS);
        if(index == 0 || set==null || !set.sprites().frames().containsKey(index)) return false;
        var graphics=GameServices.graphics(); graphics.beginPatternBatch();
        draw(EndingSpriteSet.Surface.S2_PHOTOS,index,x,y); graphics.flushPatternBatch();
        return true;
    }
    private boolean draw(EndingSpriteSet.Surface surface,int frame,int x,int y) {
        var set=sets.get(surface);
        if(set==null || !set.sprites().frames().containsKey(frame)) return false;
        renderers.get(surface).drawFrame(frame,x,y,false,false); return true;
    }
    @Override public void paletteStep(boolean toWhite) {
        this.toWhite=toWhite;
        white=Math.clamp(white+(toWhite?1:-1),0,7); updatePalettes();
    }
    @Override public void update() { ticks++; if(powered!=null && ticks%powered.cycleTicks()==0) updatePalettes(); }
    private void updatePalettes() {
        for(var entry:renderers.entrySet()) {
            var palette=new Palette(); int[] rgb=sets.get(entry.getKey()).rgb();
            boolean gilded=endingRoutine()==Sonic2EndingArt.EndingRoutine.SUPER_SONIC
                    && entry.getKey()!=EndingSpriteSet.Surface.S2_PHOTOS
                    && entry.getKey()!=EndingSpriteSet.Surface.S2_TORNADO_PILOT;
            for(int i=0;i<16;i++) {
                var color=new Palette.Color((byte)(rgb[i]>>16),(byte)(rgb[i]>>8),(byte)rgb[i]);
                if(gilded && powered!=null) color.fromSegaFormat(powered.dryCycle().get((ticks/powered.cycleTicks())%powered.dryCycle().size()).get(i));
                color.r=fade(color.r); color.g=fade(color.g); color.b=fade(color.b);
                palette.setColor(i,color);
            }
            publish(entry.getValue(),palette);
            if(entry.getKey()==EndingSpriteSet.Surface.S2_FLOAT) publish(playable,palette);
        }
    }
    private byte fade(byte component) {
        int level=0;
        while(level<7 && LEVELS[level]<(component&255)) level++;
        return (byte)LEVELS[toWhite?Math.min(7,level+white):Math.max(level,white)];
    }
    private static void publish(PlayerSpriteRenderer renderer,Palette palette) {
        var context=renderer.getRenderContext(); context.setPalette(0,palette);
        GameServices.graphics().cachePaletteTexture(palette,context.getEffectivePaletteLine(0));
    }
}

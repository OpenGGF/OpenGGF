package com.openggf.tools;

import com.openggf.game.*;
import com.openggf.game.sonic3k.specialstage.*;
import com.openggf.debug.playback.*;
import com.openggf.graphics.*;
import com.openggf.level.Palette;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Purpose: read-only special-stage mapping, palette and fade observations alongside real pixels.
 * Inputs: built engine/example classpath, verified ROM resolver, native controller log, owned output.
 * Origin: MICA Blue Sphere draw-path correction, 2026-11-10.
 * Compile into target/character-probe; see playable_art/README.md. No comparison values drive gameplay.
 * S2 declares native stage admission at tick 240. Optional debug completion is declared at
 * tick 465 (S3K) / 780 (S2); no route-completion claim. Game act passed to boot is zero-based.
 */
public final class SpecialStageCharacterProbe {
  static Object field(Object o,String n) throws Exception {
    Class<?> c=o.getClass(); while(c!=null) {try {var f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){c=c.getSuperclass();}}return null;
  }
  static String rgb(Palette p) {
    if(p==null)return "";var out=new StringJoiner(";");for(int i=0;i<16;i++){var c=p.getColor(i);out.add(String.format("%02x%02x%02x",c.r&255,c.g&255,c.b&255));}return out.toString();
  }
  public static void main(String[] args) throws Exception {
    if (args.length != 8) throw new IllegalArgumentException(
        "game main sidekick jar-or-dash input output frames debug-complete");
    // game, main, sidekick, jar-or-dash, input, output, frames, complete
    String game=args[0],main=args[1],side=args[2];Path out=Path.of(args[5]);Files.createDirectories(out.resolve("frames"));
    var movie=new Bk2MovieLoader().loadMovieOrInputLog(Path.of(args[4]));
    boolean s3=game.equals("s3k");
    var settings=new GameplayCaptureSession.Settings(320,main,side,"off",null,
      s3?11616:3500,s3?1612:150,null,false,false,null,null,false, s3?42:70);
    Path rom=TraceToolRomLocations.resolve(game,GameServices.configuration(),Path.of(""));
    try(var session=new GameplayCaptureSession(settings);var csv=Files.newBufferedWriter(out.resolve("stage.csv"));var legacy=Files.newBufferedWriter(out.resolve("state.csv"))) {
      if(!args[3].equals("-"))session.applyMod(Path.of(args[3]));
      session.boot(rom,GameplayCaptureTool.ZoneIds.resolve(game,s3?"cnz":"ehz"),0,settings);session.declareTimer(s3?9600:6600);
      legacy.write(GameplayCaptureSession.stateHeader()+"\n");
      csv.write("frame,mode,mapping,velocity,angle,turning,jumping,jump_height,fade_timer,stage_counter,clear_routine,logical_palette,physical_palette,palette,display_fade,display_steps,display_mask,overlay_alpha,overlay_type,sidekick_mapping,sidekick_physical_palette,sidekick_palette\n");
      for(int i=0;i<Integer.parseInt(args[6]);i++) {
        var input=i<movie.getFrameCount()?movie.getFrame(i):null;session.step(input);
        if(!s3 && i==240)session.loop().enterSpecialStage();
        if(Boolean.parseBoolean(args[7]) && i==(s3?465:780))session.loop().debugCompleteSpecialStageWithEmerald();
        var image=session.render();
        ScreenshotCapture.savePNG(image,out.resolve(String.format("frames/%05d.png",i)));
        legacy.write(session.stateLine(i,input)+"\n");
        var provider=session.loop().getActiveSpecialStageProvider();
        String details=",,,,,,,,,,,"; String sk=",,";
        if(provider instanceof Sonic3kSpecialStageProvider p) {
          var manager=p.getManager();var player=manager.getPlayer();var snap=manager.captureComparisonState();
          var renderer=field(manager,"renderer");var cr=renderer==null?null:field(renderer,"characterStageRenderer");
          var context=cr==null?null:((com.openggf.sprites.render.PlayerSpriteRenderer)cr).getRenderContext();
          var palette=(Sonic3kSpecialStagePalette)field(manager,"palette");
          var tr=renderer==null?null:field(renderer,"tailsStageRenderer");
          var tc=tr==null?null:((com.openggf.sprites.render.PlayerSpriteRenderer)tr).getRenderContext();
          sk=manager.getTailsMappingFrame()+","+(tc==null?1:tc.getEffectivePaletteLine(0))+","+rgb(tc==null?(palette==null?null:palette.getPalette(1)):tc.getPalette(0));
          details=player.getMappingFrame()+","+player.getVelocity()+","+player.getAngle()+","+player.getTurning()+","+player.getJumping()+","+player.getJumpHeight()+","+player.getFadeTimer()+","+snap.frameCounter()+","+snap.clearRoutine()+",0,"+(context==null?0:context.getEffectivePaletteLine(0))+","+rgb(context==null?(palette==null?null:palette.getPalette(0)):context.getPalette(0));
        } else if(provider!=null && game.equals("s2")) {
          var manager=field(provider,"manager");var player=field(manager,"sonicPlayer");
          boolean tailsSolo=player==null; if(player==null)player=field(manager,"tailsPlayer");
          var renderer=field(manager,"renderer");
          var palettes=(Palette[])field(manager,"palettes");
          if(player!=null && renderer!=null && palettes!=null) {
            int line=(int)field(renderer,tailsSolo?"tailsStagePalette":"characterStagePalette");
            var context=RenderContext.getSidekickContexts().stream().filter(c->c.getEffectivePaletteLine(0)==line).findFirst().orElse(null);
            details=player.getClass().getMethod("getMappingFrame").invoke(player)+",,,,,,,,,"+(tailsSolo?2:1)+","+line+","+rgb(context==null?palettes[tailsSolo?2:1]:context.getPalette(0));
            var tails=field(manager,"tailsPlayer");int tl=(int)field(renderer,"tailsStagePalette");
            var tc=RenderContext.getSidekickContexts().stream().filter(c->c.getEffectivePaletteLine(0)==tl).findFirst().orElse(null);
            sk=(tails==null?"":tails.getClass().getMethod("getMappingFrame").invoke(tails))+","+tl+","+rgb(tc==null?palettes[2]:tc.getPalette(0));
          }
        }
        var f=PaletteUploadPresentation.captureDisplayedFade(GameServices.graphics());var overlay=GameServices.graphics().getFadeManager();
        csv.write(i+","+session.loop().getCurrentGameMode()+","+details+","+f.mode()+","+f.steps()+","+f.lineMask()+","+overlay.getFadeAlpha()+","+overlay.getFadeType()+","+sk+"\n");
      }
    }
  }
}

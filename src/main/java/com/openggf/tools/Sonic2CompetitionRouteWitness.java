package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic2.competition.view.*;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.color.DisplayColorConverter;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Read-only CNZ flip witness: resident ROM pose, complete displayed cell and actual pixels.
 * Inputs: production art/planes/palette plus a draw-only framebuffer supplier.
 * Origin: S2 competition lane P, 2026-11-08; no counters or comparison data select gameplay.
 */
final class Sonic2CompetitionRouteWitness {
    private final java.util.function.Supplier<com.openggf.data.RomByteReader> rom;
    private int[] destinations;
    private byte[][][] frames;
    Sonic2CompetitionRouteWitness(java.util.function.Supplier<com.openggf.data.RomByteReader> rom) { this.rom=rom; }

    static boolean midFlipFrame(int frame) { return frame>=0 && (frame&3)!=0; }
    static int residentFrame(byte[] resident, byte[][] frames) {
        for(int i=0;i<frames.length;i++) if(java.util.Arrays.equals(resident,frames[i])) return i;
        return -1;
    }
    private void loadTemplates() {
        if(frames!=null) return;
        var reader=rom.get();
        var scripts=com.openggf.level.animation.AniPlcParser.parseScripts(reader,
                com.openggf.game.sonic2.Sonic2RomAddresses.address(reader,0x40160));
        destinations=new int[scripts.size()];frames=new byte[scripts.size()][][];
        for(int i=0;i<scripts.size();i++) {
            var script=scripts.get(i);destinations[i]=script.destinationTileIndex();
            int[] source=script.frameTileIdsForTesting();frames[i]=new byte[source.length][];
            for(int j=0;j<source.length;j++) frames[i][j]=script.copyFramePayload(source[j]);
        }
    }
    private int scriptForCell(int tile) {
        for(int i=0;i<destinations.length;i++)
            if(tile>=destinations[i]/2 && tile<destinations[i]/2+frames[i][0].length/64) return i;
        return -1;
    }

    void observe(GameplayModeContext context, Map<String,Integer> facts, Supplier<RgbaImage> render) {
        facts.put("competition.flip.visible",0);
        if(!(context.getLevelManager().getAnimatedPatternManager() instanceof Sonic2CompetitionAnimatedArt animation)) return;
        var counters=animation.capture().counters();
        if(counters.size()!=2) return;
        var video=context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionVideoFrame.class);
        var state=video.registers().capture();
        if(!state.published() || video.displayState().teleportTimer()!=0) return;
        // Animated_CNZ_2P (s2.asm:86688-86733): flat poses recur every fourth entry.
        // Counters name submissions, not completed publication. Compare the actual resident
        // ROM-backed payload so a queued new frame cannot certify the previous flat picture.
        loadTemplates();
        int[] displayed={-1,-1};boolean[] intermediate=new boolean[2];
        for(int i=0;i<2;i++) {
            byte[] resident=new byte[frames[i][0].length];
            for(int j=0;j<resident.length/32;j++)
                System.arraycopy(video.art().patternBytes(destinations[i]+j),0,resident,j*32,32);
            displayed[i]=residentFrame(resident,frames[i]);intermediate[i]=midFlipFrame(displayed[i]);
            facts.put("competition.flip.displayedFrame"+i,displayed[i]);
        }
        if(!intermediate[0] && !intermediate[1]) return;
        int cached=0;
        for(var id:Sonic2CompetitionPlanes.PlaneId.values()) for(int yy=0;yy<32;yy++) for(int xx=0;xx<64;xx++) {
            int t=video.planes().cell(id,xx,yy)&0x3ff;
            if(scriptForCell(t)>=0) cached++;
        }
        facts.put("competition.flip.cachedCells",cached);
        var patterns=video.art().fields().live();
        RgbaImage image=null;
        for(int player=0;player<2;player++) {
            int top=player==0?0:108, bottom=player==0?108:224;
            int vscroll=(player==0?state.foreground1():state.foreground2())>>1;
            var plane=video.planes().fieldPlane(player==0?state.upperPlane():Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P2);
            for(int y=top;y<bottom;) {
                int sy=y+vscroll, nextY=Math.min(bottom,y+8-(sy&7));
                int horizontal=(short)(state.horizontal().get(y)>>>16);
                for(int x=0;x<320;) {
                    int sx=x-horizontal, nextX=Math.min(320,x+8-(sx&7));
                    int word=plane.descriptorAt(sx,sy), tile=word&0x3ff;
                    int script=scriptForCell(tile);
                    // A complete field-projected cell must fit inside this view: a clipped border pixel
                    // is not a useful authoring/capture witness.
                    if(script>=0 && intermediate[script] && nextX-x==8 && nextY-y==8) {
                        facts.put("competition.flip.planeVisible",1);
                        facts.put("competition.flip.candidateX",x);facts.put("competition.flip.candidateY",y);
                        var pattern=patterns[word&0x7ff];
                        for(int py=y;py<nextY;py++) for(int px=x;px<nextX;px++) {
                            int tx=(px-horizontal)&7, ty=(py+vscroll)&7;
                            if((word&0x800)!=0) tx=7-tx;
                            if((word&0x1000)!=0) ty=7-ty;
                            int color=pattern.getPixel(tx,ty)&15;if(color==0) continue;
                            int palette=state.paletteWords().get(((word>>>13)&3)*16+color);
                            var graphics=GameServices.graphics();
                            int rgb=DisplayColorConverter.toPackedRgb((((palette>>>1)&7)*255+3)/7,
                                    (((palette>>>5)&7)*255+3)/7,(((palette>>>9)&7)*255+3)/7,
                                    graphics.getDisplayColorProfile(),graphics.getDisplayColorExpansion());
                            if(rgb==0) continue;
                            if(image==null) image=render.get();
                            if((image.argb(px,py)&0xffffff)==rgb) {
                                facts.put("competition.flip.visible",1);
                                facts.put("competition.flip.player",player+1);
                                facts.put("competition.flip.screenX",px);facts.put("competition.flip.screenY",py);
                                facts.put("competition.flip.word",word);facts.put("competition.flip.rgb",rgb);
                                facts.put("competition.flip.frame",displayed[script]);
                                return;
                            }
                        }
                    }
                    x=nextX;
                }
                y=nextY;
            }
        }
    }
}

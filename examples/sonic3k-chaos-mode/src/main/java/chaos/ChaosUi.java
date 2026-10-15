package chaos;

import com.openggf.mods.ui.*;
import java.util.*;

/** Shared Chaos styling: navy/gold panels, severity colours and glyph-safe text. Draw-only helpers. */
public final class ChaosUi {
    private ChaosUi() { }
    public static final int NAVY=0xF00C1A33,NAVY_DEEP=0xF8060E1E,EDGE=0xFF4F7FC8,GOLD=0xFFFFC94A,WHITE=0xFFFFFFFF,
            TEXT=0xFFDCE8F4,DIM=0xFF8CA6BE,CYAN=0xFF75DFFE,SHADOW=0xFF000000,GOOD=0xFF6CE07A,WARN=0xFFFFB13B,BAD=0xFFFF5A4E;

    public static int severity(ChaosCatalog.Severity severity) {
        return switch(severity) {case ORDINARY->GOOD;case DISRUPTIVE->WARN;case MAJOR->BAD;};
    }
    public static int category(ChaosCatalog.Category category) {
        return switch(category) {case VISUAL->CYAN;case PHYSICS->0xFFC3A2FF;case DISPLACEMENT->0xFFFF8CC8;};
    }
    /** CompactFont has no typographic dashes, curly quotes or multiplication sign. */
    public static String safe(String text) {
        var result=new StringBuilder(text.length());
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            switch(c) {
                case '–','—' -> result.append('-');
                case '‘','’','"','“','”' -> result.append('\'');
                case '×' -> result.append('X');
                case '…' -> result.append("...");
                default -> result.append(c);
            }
        }
        return result.toString();
    }
    public static List<String> wrap(String text,int width,int maxLines) {
        TextLayout.Metrics metrics=line->CompactFont.width(line,1);
        var lines=new ArrayList<>(TextLayout.wrap(safe(text),Math.max(6,width),metrics));
        if(lines.size()>maxLines) {
            var kept=new ArrayList<>(lines.subList(0,maxLines));
            kept.set(maxLines-1,TextLayout.ellipsis(kept.get(maxLines-1)+" "+lines.get(maxLines),width,metrics));
            return kept;
        }
        return lines;
    }
    public static String fit(String text,int width,int scale) {return CompactFont.fit(safe(text),width,scale);}
    public static int width(String text,int scale) {return CompactFont.width(safe(text),scale);}
    public static void text(PixelCanvas canvas,String text,int x,int y,int scale,int argb) {
        CompactFont.shadowed(canvas,safe(text),x,y,scale,argb,alpha(SHADOW,argb>>>24));
    }
    public static void centred(PixelCanvas canvas,String text,int centre,int y,int scale,int argb) {
        text(canvas,text,centre-width(text,scale)/2,y,scale,argb);
    }
    public static void right(PixelCanvas canvas,String text,int right,int y,int scale,int argb) {
        text(canvas,text,right-width(text,scale),y,scale,argb);
    }
    /** Bevelled navy panel: light top/left edge, dark bottom/right edge, notched corners. */
    public static void panel(PixelCanvas canvas,int x,int y,int w,int h,int fill,int edge) {
        if(w<3 || h<3)return;
        canvas.fill(x,y,w,h,fill);
        canvas.fill(x+1,y,w-2,1,edge);canvas.fill(x,y+1,1,h-2,edge);
        int dark=mix(edge,0xFF000000,0.55);
        canvas.fill(x+1,y+h-1,w-2,1,dark);canvas.fill(x+w-1,y+1,1,h-2,dark);
    }
    public static void bar(PixelCanvas canvas,int x,int y,int w,int h,long value,long maximum,int background,int foreground) {
        if(w<=0 || h<=0)return;
        canvas.fill(x,y,w,h,background);
        int filled=maximum<=0?0:(int)(w*Math.clamp(value,0,maximum)/maximum);
        if(filled>0)canvas.fill(x,y,filled,h,foreground);
    }
    public static int alpha(int argb,int alpha) {return Math.clamp(alpha,0,255)<<24 | (argb&0xFFFFFF);}
    public static int fade(int argb,double amount) {return alpha(argb,(int)Math.round((argb>>>24)*Math.clamp(amount,0,1)));}
    public static int mix(int a,int b,double amount) {
        int result=0;
        for(int shift=0;shift<=24;shift+=8) {
            int x=(a>>>shift)&255,y=(b>>>shift)&255;
            result|=(int)Math.round(x+(y-x)*Math.clamp(amount,0,1))<<shift;
        }
        return result;
    }
    /** 0..1 triangle wave from a caller-owned logical clock; never a draw counter. */
    public static double pulse(long clock,int period) {
        int phase=(int)Math.floorMod(clock,(long)period);
        return 1-Math.abs(phase*2.0/period-1);
    }
    public static String seconds(long presentations) {return ((presentations+59)/60)+"S";}
}

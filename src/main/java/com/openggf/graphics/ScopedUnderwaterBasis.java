package com.openggf.graphics;

/** Last published native water inputs, detached from pending CRAM writers; private GPU residency may grow between V-ints. */
record ScopedUnderwaterBasis(PaletteView[] rows,PaletteView normalLine0,PaletteFadePresentation.Mode fadeMode,int fadeSteps,int fadeMask) {
    private static final PaletteView BLANK_LINE=new CapturedLine();
    static PaletteView blankLine() { return BLANK_LINE; }
    static ScopedUnderwaterBasis capture(PaletteView[] rows,PaletteView normalLine0,PaletteFadePresentation fade) {
        PaletteView[] capturedRows=rows==null?null:new PaletteView[rows.length];
        if(rows!=null)for(int line=0;line<rows.length;line++)capturedRows[line]=copy(rows[line]);
        return new ScopedUnderwaterBasis(capturedRows,copy(normalLine0),fade.mode(),fade.steps(),fade.lineMask());
    }
    boolean matches(PaletteView[] candidate,PaletteView normal,PaletteFadePresentation fade) {
        if(fadeMode!=fade.mode() || fadeSteps!=fade.steps() || fadeMask!=fade.lineMask())return false;
        if((rows==null)!=(candidate==null) || rows!=null && rows.length!=candidate.length)return false;
        if(rows!=null)for(int i=0;i<rows.length;i++)if(!same(rows[i],candidate[i]))return false;
        return same(normalLine0,normal);
    }
    private static PaletteView copy(PaletteView source) {
        return source==null?null:new CapturedLine(source);
    }
    private static boolean same(PaletteView a,PaletteView b) {
        if(a==null || b==null)return a==b;
        for(int color=0;color<16;color++)
            if(a.red(color)!=b.red(color) || a.green(color)!=b.green(color) || a.blue(color)!=b.blue(color))return false;
        return true;
    }
    /** Raw published RGB bytes; fade and display conversion remain composition-time operations. */
    private static final class CapturedLine implements PaletteView {
        private final byte[] rgb=new byte[16*3];
        private CapturedLine() { }
        private CapturedLine(PaletteView source) {
            for(int color=0;color<16;color++) {
                rgb[color*3]=source.red(color);
                rgb[color*3+1]=source.green(color);
                rgb[color*3+2]=source.blue(color);
            }
        }
        @Override public byte red(int colorIndex) { return rgb[colorIndex*3]; }
        @Override public byte green(int colorIndex) { return rgb[colorIndex*3+1]; }
        @Override public byte blue(int colorIndex) { return rgb[colorIndex*3+2]; }
    }
}

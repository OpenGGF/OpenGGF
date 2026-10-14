package com.openggf.graphics;

import com.openggf.level.Palette;

/** Last published native water inputs, detached from pending CRAM writers; private GPU residency may grow between V-ints. */
record ScopedUnderwaterBasis(Palette[] rows,Palette normalLine0,PaletteFadePresentation.Mode fadeMode,int fadeSteps,int fadeMask) {
    static ScopedUnderwaterBasis capture(Palette[] rows,Palette normalLine0,PaletteFadePresentation fade) {
        Palette[] copy=rows==null?null:new Palette[rows.length];
        if(rows!=null)for(int line=0;line<rows.length;line++)copy[line]=rows[line]==null?null:rows[line].deepCopy();
        return new ScopedUnderwaterBasis(copy,normalLine0==null?null:normalLine0.deepCopy(),fade.mode(),fade.steps(),fade.lineMask());
    }
    boolean matches(Palette[] candidate,Palette normal,PaletteFadePresentation fade) {
        if(fadeMode!=fade.mode() || fadeSteps!=fade.steps() || fadeMask!=fade.lineMask())return false;
        if((rows==null)!=(candidate==null) || rows!=null && rows.length!=candidate.length)return false;
        if(rows!=null)for(int i=0;i<rows.length;i++)if(!same(rows[i],candidate[i]))return false;
        return same(normalLine0,normal);
    }
    private static boolean same(Palette a,Palette b) { return a==null?b==null:a.dataEquals(b); }
}

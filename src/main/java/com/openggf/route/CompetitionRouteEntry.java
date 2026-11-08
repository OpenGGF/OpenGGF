package com.openggf.route;

/** Pure launch and menu input values shared by route authoring hosts. */
public final class CompetitionRouteEntry {
    private CompetitionRouteEntry() { }
    public static LaunchContract launch() {
        return new LaunchContract(RomIdentity.standard("s2"),new Inlet.Title(),new Roster("sonic","tails"),
                320,"off",new InitialState(0,Seeds.NONE));
    }

    /** Native cold menu witness, generated here rather than edited into traversal rows.
     * Entry is the retail 2x2 grid: bit0 Right, bit1 Down. Frames are zero-based BK2 rows.
     * This is an export prefix hypothesis until a cold BizHawk replay confirms it.
     */
    public static InputTape menuPrefix(int entry) {
        if(entry < 0 || entry > 3) throw new IllegalArgumentException("menu entry must be 0..3");
        var rows = new InputTape.Builder();
        for(int i=0;i<1200;i++) {
            int p1=0;
            if(i==900) p1=PhysicalButton.DOWN.bit();
            if(i==950 || i==1150) p1=PhysicalButton.START.bit();
            if(i==1050) p1=((entry&1)!=0 ? PhysicalButton.RIGHT.bit():0)
                    | ((entry&2)!=0 ? PhysicalButton.DOWN.bit():0);
            rows.add(new PadRow(p1,0));
        }
        return rows.build();
    }

}

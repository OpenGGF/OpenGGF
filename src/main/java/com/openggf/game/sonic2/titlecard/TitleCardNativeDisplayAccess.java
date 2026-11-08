package com.openggf.game.sonic2.titlecard;

import java.util.List;

/** Read-only Obj34 projection for native presentation; never advances title state. */
public final class TitleCardNativeDisplayAccess {
    private TitleCardNativeDisplayAccess() { }

    public enum Foreground { PLAYER_ONE, PLAYER_TWO }
    public record Destination(Foreground plane, int column, int row) { }
    public record FillRequest(Destination destination, int columns, int rows, int tileWord) { }
    public record RowReveal(Foreground plane, int screenY, int blockRows) { }

    public record Part(int slot, boolean alive, int routine, int mappingFrame, int width,
                       int x, int y, int source, int target, int duration,
                       int location, Destination destination, Destination destination2P, int splitPoint,
                       boolean displayed) { }
    public record Display(boolean leaving, boolean twoPlayer, List<Part> parts) {
        public Display { parts=List.copyOf(parts); }
    }
    public static Display read(TitleCardManager manager) {
        Obj34 state=manager.obj34;
        if(state==null) return new Display(false,false,List.of());
        var parts=new java.util.ArrayList<Part>(6);
        for(int i=0;i<6;i++) {
            Obj34.Part p=state.parts[i];
            // TitleCard_ZoneName starts at SST slot 2 ($B080).
            parts.add(new Part(2+i,p.alive,p.routine,p.frame,p.width,p.x,p.y,p.source,p.target,
                    p.duration,p.location,
                    p.dest==null?null:p.dest.destination(),p.dest2==null?null:p.dest2.destination(),
                    p.split,p.displayed));
        }
        return new Display(state.leave,state.twoPlayer,parts);
    }
    /** Read before VInt consumption; consumers receive plane cells, never hardware addresses. */
    public static List<FillRequest> pendingFills(TitleCardManager manager) {
        return manager.obj34==null?List.of():manager.obj34.pendingFills();
    }
    public static List<RowReveal> pendingRowReveals(TitleCardManager manager) {
        return manager.obj34==null?List.of():manager.obj34.pendingRowReveals();
    }
    public static int foregroundWord(TitleCardManager manager,int player,int column,int row) {
        if(player<0 || player>1) throw new IllegalArgumentException("player");
        return manager.obj34==null?0:manager.obj34.planeWord(player,column,row);
    }
}

package com.openggf.game.sonic2.titlecard;

import java.util.Arrays;
import java.util.List;

/** S2 Obj34's six slots, in RunObjects order; positions include the $80 sprite bias.
 * Obj34_Init / Obj34_TitleCardData (s2.asm:27328-27375).
 * No generic title-card animation state is retained alongside these fields.
 */
final class Obj34 {
    static final int NAME=0, ZONE=1, ACT=2, BACKGROUND=3, BOTTOM=4, LEFT=5;
    final Part[] parts = new Part[6];
    /** One native destination field, translated to either plane cells or reveal rows. */
    record Target(TitleCardNativeDisplayAccess.Destination destination, Integer revealY) {
        Target {
            if ((destination == null) == (revealY == null))
                throw new IllegalArgumentException("one title fill target kind is required");
        }
    }
    private Target target(int player, int column, int row) {
        return new Target(new TitleCardNativeDisplayAccess.Destination(
                player == 0 ? TitleCardNativeDisplayAccess.Foreground.PLAYER_ONE
                        : TitleCardNativeDisplayAccess.Foreground.PLAYER_TWO, column, row), null);
    }
    private final int[][] planes = new int[2][64*32];
    List<PartState> publishedParts = List.of();
    boolean leave;
    boolean twoPlayer;
    boolean singleAct;
    int zoneFrame, actFrame;

    record PartState(int routine, int frame, int width, int duration, int x, int y,
                     int source, int target, int location, Target dest, Target dest2,
                     int split, boolean alive, boolean displayed) {}
    record Snapshot(boolean leave, boolean twoPlayer, boolean singleAct, int zoneFrame, int actFrame, List<PartState> parts, List<PartState> publishedParts, List<Integer> foreground1, List<Integer> foreground2) {
        Snapshot { parts=List.copyOf(parts);publishedParts=List.copyOf(publishedParts);foreground1=List.copyOf(foreground1);foreground2=List.copyOf(foreground2); }
    }
    static final class Part {
        int routine, frame, width, duration, x, y, source, target, location, split;
        Target dest, dest2;
        boolean alive=true, displayed;
        Part(int routine,int frame,int width,int duration,int source,int target,int y) {
            this.routine=routine;this.frame=frame;this.width=width;this.duration=duration<<8;
            this.x=this.source=source;this.target=target;this.y=y;
        }
        Part(PartState s) {
            routine=s.routine;frame=s.frame;width=s.width;duration=s.duration;x=s.x;y=s.y;
            source=s.source;target=s.target;location=s.location;dest=s.dest;dest2=s.dest2;
            split=s.split;alive=s.alive;displayed=s.displayed;
        }
        void delete() {
            routine=frame=width=duration=x=y=source=target=location=split=0;
            dest=dest2=null;
            alive=displayed=false;
        }
        PartState capture() { return new PartState(routine,frame,width,duration,x,y,source,target,
                location,dest,dest2,split,alive,displayed); }
        boolean waitIn() {
            int delay=((duration>>8)-1)&255;
            duration=(duration&255)|(delay<<8);
            if(delay!=0) return true;
            duration=0x100;return false;
        }
        void move(int destination,int speed) {
            if(x!=destination) x=(x+(Integer.compare(destination,x)*speed))&65535;
            displayed=x<=0x200;
        }
    }
    void init(int zoneFrame,int actFrame,boolean twoPlayer) {
        publishedParts=List.of();
        for(int[] plane:planes) Arrays.fill(plane,0);
        this.zoneFrame=zoneFrame;this.actFrame=actFrame;
        this.twoPlayer=twoPlayer;singleAct=actFrame<0;leave=false;
        // Level_GetBgm allocates only the routine-zero parent. Obj34_Init
        // creates the six slots when RunObjects actually reaches it.
        for (int i=0;i<6;i++) {
            parts[i]=new Part(0,0,0,0,0,0,0);
            if(i!=NAME) parts[i].delete();
        }
    }
    private void initializeParts() {
        parts[0]=new Part(8,0,128,27,0x240,0x120,0xb8);
        parts[1]=new Part(10,0x11,64,28,0x28,0x148,0xd0);
        parts[2]=new Part(12,0x12,24,28,0x68,0x188,0xd0);
        parts[3]=new Part(2,0,0,0,0,0,0);
        parts[4]=new Part(4,0x15,72,8,0x2a8,0x168,0x120);
        parts[4].location=0x26;
        parts[5]=new Part(6,0x16,8,21,0x80,0xf0,0xf0);
    }
    void settle() {
        initializeParts();
        parts[NAME].frame=zoneFrame;parts[ACT].frame=actFrame;
        for(int i=0;i<6;i++) {
            Part p=parts[i];p.x=p.target;p.duration=i==BACKGROUND?0:0x100;p.displayed=p.alive && i!=BACKGROUND;
        }
        if(singleAct) parts[ACT].delete();
        parts[BACKGROUND].location=twoPlayer?32:16;
        parts[BOTTOM].location=0xff06;parts[BOTTOM].split=6;
        parts[LEFT].location=0xff0e;parts[LEFT].split=14;
    }
    /** The omitted load resumes at Level_StartGame, after the plane parts died. */
    void settleAfterLeave() {
        settle();
        leave=true;
        for(int i=BACKGROUND;i<=LEFT;i++) parts[i].delete();
        armTail();
    }
    void armLeave() {leave=true;parts[LEFT].routine=14;parts[LEFT].location=10;}
    void armTail() {
        for(int i=0;i<3;i++) {parts[i].routine=22;parts[i].duration=45;}
    }
    /** Returns the zone-name deletion (the only slot that queues exit PLCs).
     * Obj34_Wait through Obj34_WaitAndGoAway (s2.asm:27379-27637).
     */
    boolean tick() {
        boolean initPass=parts[NAME].alive && parts[NAME].routine==0;
        if(initPass) initializeParts();
        boolean deletedName=false;
        for(int i=0;i<6;i++) {
            Part p=parts[i];p.displayed=false;
            if(!p.alive) continue;
            // Init overwrites its own zone-name slot: that slot is not dispatched again.
            if(initPass && i==0) continue;
            switch(p.routine) {
                case 2 -> {
                    if(p.location!=(twoPlayer?32:16)) {
                        p.dest=target(0,0,p.location*(twoPlayer?1:2));
                        if(twoPlayer) p.dest2=target(1,0,p.location);
                        p.location++;
                    }
                }
                case 4,6,8,10,12 -> {
                    if(p.waitIn()) break;
                    if(p.routine==12 && singleAct) {p.delete();break;}
                    if(p.routine==8) p.frame=zoneFrame;
                    if(p.routine==12) p.frame=actFrame;
                    if(p.routine==4 && (short)p.location>=0) {
                        p.dest=target(0,p.location,twoPlayer?10:20);
                        if(twoPlayer) p.dest2=target(1,p.location,10);
                        p.location=(p.location-2)&65535;p.split=p.location;
                        // seq writes only the HIGH BYTE, retaining the low byte (fixBugs=0).
                        p.location=(p.location&255)|(p.location==6?0xff00:0);
                    }
                    if(p.routine==6 && (short)p.location>=0) {
                        p.dest=target(0,0,0);if(twoPlayer) p.dest2=target(1,0,0);
                        p.location=(p.location+2)&65535;p.split=p.location;
                        p.location=(p.location&255)|(p.location==14?0xff00:0);
                    }
                    p.move(p.target,16);
                }
                case 14 -> {
                    if((short)p.location<0) {
                        parts[BOTTOM].routine=16;parts[BOTTOM].location=0;p.delete();
                    } else {
                        p.dest=target(0,p.location,0);if(twoPlayer) p.dest2=target(1,p.location,0);
                        p.location=(p.location-4)&65535;
                        if(p.location==65534) p.location=0;
                        p.move(p.source,32);
                    }
                }
                case 16 -> {
                    if(p.location==40) {parts[BACKGROUND].routine=18;p.delete();}
                    else {
                        p.dest=target(0,p.location,twoPlayer?10:20);
                        if(twoPlayer) p.dest2=target(1,p.location,10);
                        p.location+=4;p.move(p.source,32);
                    }
                }
                case 18,20 -> {
                    if(p.routine==18) {p.routine=20;p.location=240;}
                    int next=(p.location-32)&65535;
                    if(next==0xffd0) p.delete();
                    else {p.location=next;p.dest=new Target(null,(int)(short)next);}
                }
                case 22 -> {
                    if(p.duration!=0) {p.duration=(p.duration-1)&65535;p.displayed=true;}
                    else if(p.x==p.source) p.delete();
                    else {p.move(p.source,32);if(!p.displayed) p.delete();}
                    if(i==NAME && !p.alive) {deletedName=true;leave=false;}
                }
                default -> throw new IllegalStateException("Obj34 routine "+p.routine);
            }
        }
        return deletedName;
    }
    int planeWord(int player,int x,int y) {return planes[player][((y&31)*64)+(x&63)];}
    private int tile(int offset,int palette) {
        return 0x8000|(palette<<13)|((0x580+offset)>>(twoPlayer?1:0));
    }
    private TitleCardNativeDisplayAccess.FillRequest fill(Target target,
            int columns, int rows, int word) {
        return new TitleCardNativeDisplayAccess.FillRequest(target.destination(),columns,rows,word);
    }
    /** DrawLevelTitleCard's semantic writes; there is no second fill state machine.
     * loc_155AE / loc_155F0 / loc_15640 (s2.asm:28977-29059),
     * loc_15670 / loc_156CE (s2.asm:29062-29124).
     */
    List<TitleCardNativeDisplayAccess.FillRequest> pendingFills() {
        var result=new java.util.ArrayList<TitleCardNativeDisplayAccess.FillRequest>();
        Part bg=parts[BACKGROUND],bottom=parts[BOTTOM],left=parts[LEFT];
        for(int player=0;player<(twoPlayer?2:1);player++) {
            Target b=player==0?bg.dest:bg.dest2;
            Target y=player==0?bottom.dest:bottom.dest2;
            Target r=player==0?left.dest:left.dest2;
            if(!leave) {
                if(b!=null) result.add(fill(b,64,twoPlayer?1:2,tile(0x5a,0)));
                if(y!=null) result.add(fill(y,0x26-bottom.split,twoPlayer?4:8,tile(0x5c,1)));
                if(r!=null) result.add(fill(r,left.split,twoPlayer?14:28,tile(0x58,0)));
            } else {
                if(r!=null) {
                    result.add(fill(r,4,twoPlayer?10:20,tile(0x5a,0)));
                    result.add(fill(target(player,r.destination().column(),
                            r.destination().row()+(twoPlayer?10:20)),4,twoPlayer?4:8,tile(0x5c,1)));
                }
                if(y!=null) result.add(fill(y,4,twoPlayer?4:8,tile(0x5a,0)));
            }
        }
        return List.copyOf(result);
    }
    /** loc_15714 / loc_15758 (s2.asm:29125-29179): P2 rows first, using its
     * retained camera copy; Vint_TitleCard refreshes only the P1 copy.
     * The consumer owns world-plane lookup; these requests carry no terrain data.
     */
    List<TitleCardNativeDisplayAccess.RowReveal> pendingRowReveals() {
        if(!leave || parts[BACKGROUND].dest==null) return List.of();
        int y=parts[BACKGROUND].dest.revealY();
        var p1=new TitleCardNativeDisplayAccess.RowReveal(
                TitleCardNativeDisplayAccess.Foreground.PLAYER_ONE,y,2);
        if(!twoPlayer) return List.of(p1);
        return List.of(new TitleCardNativeDisplayAccess.RowReveal(
                TitleCardNativeDisplayAccess.Foreground.PLAYER_TWO,y,2),p1);
    }
    /** VInt publication and DrawLevelTitleCard consume the preceding prepared pass. */
    void consumeFills() {
        // Vint_TitleCard (s2.asm:1005-1045) publishes the preceding BuildSprites page.
        // This is a retained projection, never a second animation clock.
        publishedParts=Arrays.stream(parts).map(Part::capture).toList();
        for(var fill:pendingFills()) {
            int player=fill.destination().plane()==TitleCardNativeDisplayAccess.Foreground.PLAYER_ONE?0:1;
            int start=fill.destination().row()*64+fill.destination().column();
            for(int y=0;y<fill.rows();y++) for(int x=0;x<fill.columns();x++)
                planes[player][(start+y*64+x)&2047]=fill.tileWord();
        }
        for(var reveal:pendingRowReveals()) {
            int player=reveal.plane()==TitleCardNativeDisplayAccess.Foreground.PLAYER_ONE?0:1;
            int row=reveal.screenY()>>(twoPlayer?4:3);
            // The overlay relinquishes coverage; the production world plane
            // supplies ROM terrain through its own camera-copy lookup.
            for(int y=0;y<(twoPlayer?2:4);y++)
                Arrays.fill(planes[player],((row+y)&31)*64,((row+y)&31)*64+64,0);
        }
        for(Part p:parts) {p.dest=null;p.dest2=null;}
    }
    Snapshot capture() { return new Snapshot(leave,twoPlayer,singleAct,zoneFrame,actFrame,
            Arrays.stream(parts).map(Part::capture).toList(),publishedParts,
            Arrays.stream(planes[0]).boxed().toList(),Arrays.stream(planes[1]).boxed().toList()); }
    void restore(Snapshot s) {
        leave=s.leave;twoPlayer=s.twoPlayer;singleAct=s.singleAct;zoneFrame=s.zoneFrame;actFrame=s.actFrame;
        publishedParts=s.publishedParts;
        for(int i=0;i<6;i++) parts[i]=new Part(s.parts.get(i));
        for(int i=0;i<2048;i++){planes[0][i]=s.foreground1.get(i);planes[1][i]=s.foreground2.get(i);}
    }
}

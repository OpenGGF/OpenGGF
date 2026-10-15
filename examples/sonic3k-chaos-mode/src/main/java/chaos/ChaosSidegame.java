package chaos;

import com.openggf.mods.state.SnapshotRandom;
import com.openggf.mods.ui.*;
import java.util.Arrays;

/** Five bounded, independent games. Only eligible held presentations call step; draw is pure. */
public final class ChaosSidegame {
    public enum Kind { PONG, SNAKE, STACK, RHYTHM, DEFENCE }
    public record Input(boolean left,boolean right,boolean up,boolean down,int buttons,boolean cancel) { }
    public record Snapshot(Kind kind,long random,int age,int score,boolean finished,boolean cancelled,
                           int[] state,int[] board) {
        public Snapshot { state=state.clone(); board=board.clone(); }
        @Override public int[] state() { return state.clone(); }
        @Override public int[] board() { return board.clone(); }
    }
    private final Kind kind;
    private final SnapshotRandom random;
    private int age,score;
    private boolean finished,cancelled;
    private int[] state=new int[32],board=new int[384];
    public ChaosSidegame(Kind kind,long seed) {
        this.kind=kind; random=new SnapshotRandom(seed==0?1:seed);
        switch(kind) {
            case PONG -> { state[0]=72;state[1]=72;state[2]=96*256;state[3]=64*256;state[4]=360;state[5]=256; }
            case SNAKE -> {state[0]=3;state[1]=1;board[0]=7*24+10;board[1]=7*24+9;board[2]=7*24+8;snakeFood();}
            case STACK -> stackSpawn();
            case RHYTHM -> {state[0]=random.nextInt(3);state[1]=90;}
            case DEFENCE -> state[0]=3;
        }
    }
    public Kind kind() { return kind; }
    public int age() { return age; }
    public int score() { return score; }
    public boolean finished() { return finished; }
    public boolean cancelled() { return cancelled; }
    public boolean won() {return !cancelled && kind==Kind.PONG && score>=3;}
    /** Wrap the scored result while the host lease still has its final presentation. */
    public void finishAtDeadline() {finished=true;}
    public int quietSeconds() { return cancelled?0:Math.min(30,score*2); }
    /** A native shield cue leads each approaching rhythm target by half a second. */
    public int cueLane() {return kind==Kind.RHYTHM && age==state[1]-30?state[0]:-1;}
    public void step(Input input) {
        if(finished) return;
        if(input.cancel()) {cancelled=finished=true;return;}
        if(++age>=1200) {finished=true;return;}
        switch(kind) {
            case PONG -> pong(input);
            case SNAKE -> snake(input);
            case STACK -> stack(input);
            case RHYTHM -> rhythm(input);
            case DEFENCE -> defence(input);
        }
    }
    private void pong(Input input) {
        state[0]=Math.clamp(state[0]+(input.down()?3:0)-(input.up()?3:0),0,104);
        state[1]=Math.clamp(state[1]+Integer.signum((state[3]>>8)-state[1]-12),0,104);
        state[2]+=state[4];state[3]+=state[5];
        if(state[3]<0 || state[3]>128*256) {state[3]=Math.clamp(state[3],0,128*256);state[5]=-state[5];}
        int y=state[3]>>8,x=state[2]>>8;
        if(x<=8 && state[4]<0 && y>=state[0]-3 && y<=state[0]+27) {state[4]=-state[4];if(++score>=3)finished=true;}
        if(x>=184 && state[4]>0 && y>=state[1]-3 && y<=state[1]+27) state[4]=-state[4];
        if(x<0 || x>192) finished=true;
    }
    private void snake(Input input) {
        int direction=input.up()?0:input.right()?1:input.down()?2:input.left()?3:state[1];
        if((direction+2)%4!=state[1]) state[1]=direction;
        if(age%8!=0) return;
        int head=board[0],x=head%24,y=head/24;
        x+=state[1]==1?1:state[1]==3?-1:0; y+=state[1]==2?1:state[1]==0?-1:0;
        if(x<0 || x>=24 || y<0 || y>=14) {finished=true;return;}
        int next=y*24+x,length=state[0];boolean grows=next==state[2];
        for(int n=0;n<length-(grows?0:1);n++) if(board[n]==next) {finished=true;return;}
        if(grows) {score++;length++;state[0]=length;}
        for(int n=length-1;n>0;n--) board[n]=board[n-1];board[0]=next;
        if(length>=336) {finished=true;return;}
        if(grows) snakeFood();
    }
    private void snakeFood() {
        int start=random.nextInt(336);
        for(int offset=0;offset<336;offset++) {
            int candidate=(start+offset)%336;boolean free=true;
            for(int n=0;n<state[0];n++) if(board[n]==candidate) {free=false;break;}
            if(free) {state[2]=candidate;return;}
        }
        finished=true;
    }
    private int[][] shape(int type,int rotation) {
        int[][] points=switch(type) {
            case 0 -> new int[][]{{-1,0},{0,0},{1,0},{2,0}};
            case 1 -> new int[][]{{0,0},{1,0},{0,1},{1,1}};
            case 2 -> new int[][]{{-1,0},{0,0},{1,0},{0,1}};
            case 3 -> new int[][]{{-1,0},{0,0},{0,1},{1,1}};
            case 4 -> new int[][]{{0,0},{1,0},{-1,1},{0,1}};
            case 5 -> new int[][]{{-1,0},{0,0},{1,0},{-1,1}};
            default -> new int[][]{{-1,0},{0,0},{1,0},{1,1}};
        };
        if(type!=1) for(int r=0;r<rotation;r++) for(var point:points) {int x=point[0];point[0]=-point[1];point[1]=x;}
        return points;
    }
    private boolean stackFits(int x,int y,int rotation) {
        for(var p:shape(state[2],rotation)) {
            int px=x+p[0],py=y+p[1];
            if(px<0 || px>=10 || py>=16 || (py>=0 && board[py*10+px]!=0)) return false;
        }
        return true;
    }
    private void stackSpawn() {
        state[0]=4;state[1]=0;state[2]=random.nextInt(7);state[3]=0;
        if(!stackFits(state[0],state[1],state[3])) finished=true;
    }
    private void stack(Input input) {
        int x=state[0]+(input.right()?1:0)-(input.left()?1:0);
        if(stackFits(x,state[1],state[3])) state[0]=x;
        if(input.up() && stackFits(state[0],state[1],(state[3]+1)%4)) state[3]=(state[3]+1)%4;
        if(input.buttons()!=0) {while(stackFits(state[0],state[1]+1,state[3])) state[1]++;stackLock();return;}
        if(input.down() || age%20==0) {
            if(stackFits(state[0],state[1]+1,state[3])) state[1]++; else stackLock();
        }
    }
    private void stackLock() {
        for(var p:shape(state[2],state[3])) {
            int y=state[1]+p[1]; if(y<0) {finished=true;return;}
            board[y*10+state[0]+p[0]]=state[2]+1;
        }
        for(int y=15;y>=0;y--) {
            boolean full=true;for(int x=0;x<10;x++) if(board[y*10+x]==0) full=false;
            if(full) {System.arraycopy(board,0,board,10,y*10);Arrays.fill(board,0,10,0);score++;y++;}
        }
        stackSpawn();
    }
    private void rhythm(Input input) {
        int difference=Math.abs(age-state[1]);
        if((input.buttons()&(1<<state[0]))!=0 && difference<=8 && state[2]==0) {score++;state[2]=1;}
        if(age>state[1]+8) {state[0]=random.nextInt(3);state[1]+=45;state[2]=0;}
    }
    private void defence(Input input) {
        if(age%120==0) state[0]=Math.min(5,state[0]+1);
        for(int lane=0;lane<3;lane++) if((input.buttons()&(1<<lane))!=0 && state[0]>0) {
            state[0]--;state[lane+1]=Math.min(300,state[lane+1]+120);
        }
        if(age%45==0) {
            int lane=random.nextInt(3);
            for(int slot=0;slot<8;slot++) if(board[lane*8+slot]==0) {board[lane*8+slot]=180;break;}
        }
        for(int lane=0;lane<3;lane++) {
            if(state[lane+1]>0) state[lane+1]--;
            if(age%20==0 && state[lane+1]>0) {
                int closest=-1;
                for(int slot=0;slot<8;slot++) if(board[lane*8+slot]>0 && (closest<0 || board[lane*8+slot]<board[closest])) closest=lane*8+slot;
                if(closest>=0) {board[closest]=0;score++;}
            }
            if(age%6==0) for(int slot=0;slot<8;slot++) if(board[lane*8+slot]>0 && --board[lane*8+slot]<=0) {finished=true;return;}
        }
    }
    public Snapshot capture() { return new Snapshot(kind,random.snapshot(),age,score,finished,cancelled,state,board); }
    public void restore(Snapshot snapshot) {
        if(snapshot.kind()!=kind) throw new IllegalArgumentException("Wrong sidegame");
        random.restore(snapshot.random());age=snapshot.age();score=snapshot.score();finished=snapshot.finished();cancelled=snapshot.cancelled();
        state=snapshot.state();board=snapshot.board();
    }
    private String title() {
        return switch(kind) {case PONG->"RING PONG";case SNAKE->"RING SNAKE";case STACK->"MONITOR STACK";case RHYTHM->"SHIELD BEAT";case DEFENCE->"BADNIK DEFENCE";};
    }
    private String unit(int value) {
        return switch(kind) {
            case PONG -> value==1?"return":"returns";
            case SNAKE -> value==1?"ring eaten":"rings eaten";
            case STACK -> value==1?"line cleared":"lines cleared";
            case RHYTHM -> value==1?"beat hit":"beats hit";
            case DEFENCE -> value==1?"badnik stopped":"badniks stopped";
        };
    }
    /** Receipt for a finished, non-winning or non-Pong game; a Pong win reports its own reward. */
    public String resultText() {
        if(cancelled) return "Failed: Cancelled. No reward this time.";
        if(score==0) return "Failed: No score this time. Back to the chaos!";
        String breather=quietSeconds()+" second Chaos breather.";
        if(kind==Kind.PONG) return "Failed: "+score+" "+unit(score)+", so no rings. "+breather;
        return "Complete: "+score+" "+unit(score)+". "+breather;
    }
    private String hint() {
        return switch(kind) {
            case PONG -> "UP/DOWN: MOVE PADDLE";
            case SNAKE -> "D-PAD: STEER, EAT RINGS";
            case STACK -> "L/R MOVE  UP TURN  A DROP";
            case RHYTHM -> "PRESS A B C ON THE BEAT";
            case DEFENCE -> "A B C: LAUNCH SPRINGS";
        };
    }
    private static int piece(int type) {
        return switch(type) {case 0->0xFF2D6CDF;case 1->0xFFE0C030;case 2->0xFFB040D0;case 3->0xFF40C060;case 4->0xFFE04848;case 5->0xFFE08830;default->0xFF40C8E0;};
    }
    /** Pure board drawing; framed playfield, readable title, score, timer and controls. */
    public void draw(PixelCanvas canvas,int left,int top,ChaosArt art) {
        ChaosUi.panel(canvas,left-6,top-16,208,188,0xF00A1730,ChaosUi.GOLD);
        UiPrimitives.gradient(canvas,left-5,top-15,206,12,0xF0183A78,0xF00A1730);
        ChaosUi.text(canvas,title(),left,top-13,1,ChaosUi.GOLD);
        String scoreText=switch(kind) {
            case PONG -> "RETURNS "+score+"/3";
            case SNAKE -> "RINGS "+score;
            case STACK -> "LINES "+score;
            case RHYTHM -> "BEATS "+score;
            case DEFENCE -> "STOPPED "+score+"  SPRINGS "+state[0];
        };
        ChaosUi.right(canvas,scoreText,left+196,top-13,1,ChaosUi.WHITE);
        int y=top+2;
        switch(kind) {
            case PONG -> {
                canvas.fill(left-2,y-2,200,140,0xC0050C1C);UiPrimitives.frame(canvas,left-3,y-3,202,142,0xFF35587E);
                for(int dash=0;dash<17;dash++)canvas.fill(left+97,y+dash*8,2,4,0x6075DFFE);
                art.draw(canvas,ChaosArt.Motif.SONIC,left,y+state[0],16,24);art.draw(canvas,ChaosArt.Motif.TAILS,left+180,y+state[1],16,24);
                art.draw(canvas,ChaosArt.Motif.RING,left+(state[2]>>8)-2,y+(state[3]>>8)-2,8,8);
            }
            case SNAKE -> {
                canvas.fill(left,y,192,112,0xC0050C1C);UiPrimitives.frame(canvas,left-2,y-2,196,116,0xFF35587E);
                for(int i=0;i<state[0];i++)art.draw(canvas,ChaosArt.Motif.ROLL,left+board[i]%24*8,y+board[i]/24*8,8,8);
                art.draw(canvas,ChaosArt.Motif.RING,left+state[2]%24*8,y+state[2]/24*8,8,8);
            }
            case STACK -> {
                int well=left+58;
                canvas.fill(well,y,80,128,0xC0050C1C);UiPrimitives.frame(canvas,well-2,y-2,84,132,0xFF35587E);
                for(int i=0;i<160;i++)if(board[i]>0) {
                    canvas.fill(well+i%10*8,y+i/10*8,8,8,ChaosUi.alpha(piece(board[i]-1),150));
                    art.draw(canvas,ChaosArt.Motif.MONITOR,well+i%10*8,y+i/10*8,8,8);
                }
                int landing=state[1];
                while(stackFits(state[0],landing+1,state[3]))landing++;
                for(var p:shape(state[2],state[3])) {
                    int py=landing+p[1];if(py>=0 && landing!=state[1])UiPrimitives.frame(canvas,well+(state[0]+p[0])*8,y+py*8,8,8,ChaosUi.alpha(piece(state[2]),170));
                }
                for(var p:shape(state[2],state[3])) {
                    int py=state[1]+p[1];
                    if(py>=0) {canvas.fill(well+(state[0]+p[0])*8,y+py*8,8,8,ChaosUi.alpha(piece(state[2]),190));art.draw(canvas,ChaosArt.Motif.MONITOR,well+(state[0]+p[0])*8,y+py*8,8,8);}
                }
            }
            case RHYTHM -> {
                for(int lane=0;lane<3;lane++) {
                    canvas.fill(left+lane*64+16,y,32,124,0xC012344F);
                    ChaosUi.centred(canvas,"ABC".substring(lane,lane+1),left+lane*64+32,y+126,1,ChaosUi.WHITE);
                }
                canvas.fill(left+12,y+100,172,2,ChaosUi.GOLD);canvas.fill(left+12,y+116,172,1,0x80FFC94A);
                int py=100-(state[1]-age)*2;
                art.draw(canvas,ChaosArt.Motif.values()[ChaosArt.Motif.FIRE.ordinal()+state[0]],left+state[0]*64+24,y+Math.clamp(py,0,94),16,16);
            }
            case DEFENCE -> {
                for(int lane=0;lane<3;lane++) {
                    int row=y+lane*44;
                    canvas.fill(left,row+4,196,36,0xC0050C1C);canvas.fill(left,row+4,2,36,ChaosUi.BAD);
                    ChaosUi.text(canvas,"ABC".substring(lane,lane+1),left+5,row+18,1,ChaosUi.DIM);
                    if(state[lane+1]>0)art.draw(canvas,ChaosArt.Motif.SPRING,left+12,row+14,16,16);
                    for(int i=0;i<8;i++)if(board[lane*8+i]>0)art.draw(canvas,ChaosArt.Motif.BADNIK,left+board[lane*8+i],row+14,16,16);
                }
            }
        }
        ChaosUi.bar(canvas,left,top+142,196,2,1200-age,1200,0x60FFFFFF,1200-age<=180?ChaosUi.WARN:ChaosUi.CYAN);
        ChaosUi.text(canvas,hint(),left,top+148,1,ChaosUi.TEXT);
        ChaosUi.right(canvas,ChaosUi.seconds(1200-age),left+196,top+148,1,ChaosUi.WHITE);
        ChaosUi.centred(canvas,"START OR ESC: QUIT (NO PENALTY)",left+98,top+159,1,ChaosUi.DIM);
    }
}

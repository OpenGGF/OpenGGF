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
    public void draw(PixelCanvas canvas,int left,int top,ChaosArt art) {
        canvas.fill(left-4,top-4,204,156,0xEF08172C);
        CompactFont.draw(canvas,kind.name()+"  "+score+(kind==Kind.PONG?"/3":""),left,top,1,0xFFFFFF80);
        CompactFont.draw(canvas,"TIME "+((1200-age+59)/60)+" START/ESC CANCEL",left,top+140,1,0xFFB4D8ED);
        int y=top+10;
        switch(kind) {
            case PONG -> {art.draw(canvas,ChaosArt.Motif.SONIC,left,y+state[0],16,24);art.draw(canvas,ChaosArt.Motif.TAILS,left+180,y+state[1],16,24);art.draw(canvas,ChaosArt.Motif.RING,left+(state[2]>>8)-2,y+(state[3]>>8)-2,8,8);}
            case SNAKE -> {for(int i=0;i<state[0];i++)art.draw(canvas,ChaosArt.Motif.ROLL,left+board[i]%24*8,y+board[i]/24*8,8,8);art.draw(canvas,ChaosArt.Motif.RING,left+state[2]%24*8,y+state[2]/24*8,8,8);}
            case STACK -> {for(int i=0;i<160;i++)if(board[i]>0)art.draw(canvas,ChaosArt.Motif.MONITOR,left+48+i%10*8,y+i/10*8,8,8);for(var p:shape(state[2],state[3])) {int py=state[1]+p[1];if(py>=0)art.draw(canvas,ChaosArt.Motif.MONITOR,left+48+(state[0]+p[0])*8,y+py*8,8,8);}}
            case RHYTHM -> {for(int lane=0;lane<3;lane++) {canvas.fill(left+lane*64+16,y,32,120,0xFF12344F);CompactFont.draw(canvas,"ABC".substring(lane,lane+1),left+lane*64+28,y+110,1,0xFFFFFFFF);}int py=100-(state[1]-age)*2;art.draw(canvas,ChaosArt.Motif.values()[ChaosArt.Motif.FIRE.ordinal()+state[0]],left+state[0]*64+24,y+Math.clamp(py,0,94),16,16);}
            case DEFENCE -> {for(int lane=0;lane<3;lane++) {canvas.fill(left,y+lane*40+4,192,1,0xFF305875);if(state[lane+1]>0)art.draw(canvas,ChaosArt.Motif.SPRING,left+2,y+lane*40+10,16,16);for(int i=0;i<8;i++)if(board[lane*8+i]>0)art.draw(canvas,ChaosArt.Motif.BADNIK,left+board[lane*8+i],y+lane*40+10,16,16);}}
        }
    }
}

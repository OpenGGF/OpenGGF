package echoes;

import com.openggf.mods.ui.PixelCanvas;

/** Derived room presentation: never changes the native world or recording clocks. */
final class EchoHud {
    private final EchoUi ui=new EchoUi();
    void draw(PixelCanvas c,RoomSession s) {
        var r=s.room(); int w=c.width(),h=c.height();
        ui.panel(c,8,7,w-16,39,0xFF407088);
        ui.text(c,r.id()+" "+r.name()+" - PAR "+r.par(),15,13,1,0xFFFFFFFF);
        String clock=EchoUi.time(s.remainingFrames());
        ui.text(c,clock,w-15-ui.font.width(clock,2),25,2,
                s.remainingFrames()<=180&&(s.remainingFrames()/8%2)==0?0xFFFF6058:0xFFFFFFFF);
        for(int i=0;i<r.echoCap();i++) {
            int x=15+i*17,y=29,colour=EchoUi.colour(i);
            c.fill(x,y,10,10,colour);
            if(i>=s.recordings().size()) c.fill(x+2,y+2,6,6,0xFF0A1535);
            else if(s.fractured(i)) {
                c.fill(x+4,y,2,4,0xFF0A1535);c.fill(x+2,y+4,4,2,0xFF0A1535);c.fill(x+4,y+6,2,4,0xFF0A1535);
            }
        }
        int barX=12,barW=w-24,barY=h-13;
        c.fill(barX,barY-7,barW,14,0xDA081028);
        for(int i=0;i<s.recordings().size();i++)
            c.fill(barX,barY-5+i,Math.max(1,barW*s.recordings().get(i).samples().size()/r.loopFrames()),1,EchoUi.colour(i));
        c.fill(barX,barY+2,barW,2,0xFF354765);
        int pos=barW*Math.min(r.loopFrames(),s.frame())/r.loopFrames();
        c.fill(barX,barY+2,pos,2,0xFF60E8FF);c.fill(barX+pos,barY-6,2,12,0xFFFFFFFF);
        if(s.loopCount()==0&&!s.cleared()) {
            ui.panel(c,8,h-43,w-16,23,0xFF344A70);
            ui.centre(c,"E ECHO  U UNDO  R RESET  H HINT  Q HUB",h-39,1,0xFFD0E0F0);
            ui.centre(c,"PAD Y      RB       LS      LB     BACK",h-29,1,0xFF88A8C8);
        }
        if(s.hintVisible()&&!s.cleared()) {
            ui.panel(c,20,63,w-40,52,0xFFFFD030);
            ui.text(c,"HINT / H TO CLOSE",28,71,1,0xFFFFD030);
            String hint=s.hint().isBlank()?"Record an echo. Released echoes hold still on their plates.":s.hint();
            int line=0,limit=(w-58)/6; StringBuilder text=new StringBuilder();
            for(String word:hint.split("\\s+")) {
                if(text.length()+word.length()+1>limit) { ui.text(c,text.toString(),28,86+line++*10,1,0xFFFFFFFF);text.setLength(0); }
                if(line>=2) break;
                if(!text.isEmpty()) text.append(' ');text.append(word);
            }
            if(line<2) ui.text(c,text.toString(),28,86+line*10,1,0xFFFFFFFF);
        }
        if(s.paradoxAge()>0&&!s.cleared()) {
            ui.panel(c,w/2-96,58,192,23,EchoUi.colour(s.paradoxSlot()));
            ui.centre(c,"ECHO "+(s.paradoxSlot()+1)+" PARADOX!",66,1,EchoUi.colour(s.paradoxSlot()));
        }
        if(s.rewindHold()>0) {
            int alpha=30+(RoomSession.REWIND_HOLD_FRAMES-s.rewindHold())*11;
            c.fill(0,0,w,h,alpha<<24|0xFFFFFF);
            ui.panel(c,w/2-80,91,160,35,0xFF60E8FF);
            ui.centre(c,"LOOP "+(s.loopCount()+1),101,2,0xFFFFFFFF);
        } else if(s.loopLabel()>0&&s.paradoxAge()==0&&!s.cleared()) ui.centre(c,"LOOP "+(s.loopCount()+1),63,2,0xFFFFFFFF);
        if(s.cleared()) clear(c,s);
    }
    private void clear(PixelCanvas c,RoomSession s) {
        c.fill(0,0,c.width(),c.height(),0x99081028);
        int x=(c.width()-276)/2;
        ui.panel(c,x,48,276,137,0xFF60E8FF);
        ui.centre(c,"ROOM CLEAR",61,3,0xFFFFFFFF);
        String[] labels={"CLEAR","PAR","SHARDS"};
        for(int i=0;i<3;i++) {
            int sx=x+40+i*80;
            if(s.clearAge()>=12+i*18) ui.star(c,sx,91,3,(s.clearStars()&1<<i)!=0);
            ui.text(c,labels[i],sx-5,117,1,0xFFA0B8D8);
        }
        ui.centre(c,"ECHOES "+s.lastClearEchoes()+" / PAR "+s.room().par()+"   "+EchoUi.time(s.lastClearFrames()),135,1,0xFFFFFFFF);
        if(s.newBest()) ui.centre(c,"NEW BEST",151,1,0xFFFFD030);
        if(s.clearAge()>=72) ui.centre(c,"A/C/START - RETURN TO HUB",170,1,0xFF60E8FF);
    }
}

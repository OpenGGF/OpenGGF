package echoes;

import com.openggf.game.TitleScreenProvider;
import com.openggf.game.GameServices;
import com.openggf.control.InputHandler;
import com.openggf.mods.ui.PixelCanvas;

/** Stock title decorator and bounded twelve-card catalogue; missing content stays visible. */
public final class EchoHub implements TitleScreenProvider {
    private final TitleScreenProvider base;
    private final RoomSession session;
    private final RoomLauncher launcher;
    private final EchoUi ui=new EchoUi();
    private final InputHandler neutral=new InputHandler();
    private int selected, activeFrames;
    private boolean open, exiting;
    public EchoHub(TitleScreenProvider base,RoomSession session,RoomLauncher launcher) {
        this.base=base;this.session=session;this.launcher=launcher;
    }
    public boolean hubOpen() { return open; }
    public TitleScreenProvider base() { return base; }
    public String selectedId() { return (selected/4+1)+"-"+(selected%4+1); }
    public void navigate(int dx,int dy) {
        int world=selected/4,card=selected%4;
        int col=card%2+dx,row=card/2+dy;
        if(col<0) { world=Math.floorMod(world-1,3);col=1; }
        if(col>1) { world=(world+1)%3;col=0; }
        row=Math.floorMod(row,2);
        selected=world*4+row*2+col;
    }
    public boolean launchSelected() {
        if(!session.progress().unlocked(selectedId())||!available(selectedId())) return false;
        launcher.launch(selectedId()); exiting=true; return true;
    }
    static boolean available(String id) { return EchoHub.class.getResource("/rooms/"+id+".properties")!=null; }
    @Override public void initialize() {
        base.initialize();activeFrames=0;exiting=false;
        open=session.consumeHubReturn();
        if(open) activeFrames=100;
        String id=session.hubCursor(); selected=(id.charAt(0)-'1')*4+id.charAt(2)-'1';
    }
    @Override public void reset() { base.reset();exiting=false;activeFrames=0; }
    @Override public State getState() { return exiting?State.EXITING:base.getState(); }
    @Override public boolean isExiting() { return exiting; }
    @Override public boolean isActive() { return base.isActive(); }
    @Override public boolean supportsLevelSelectOverlay() { return false; }
    @Override public boolean ownsEscapeInput() { return true; }
    @Override public TitleScreenAction consumeExitAction() { return TitleScreenAction.ONE_PLAYER; }
    @Override public int startZoneIndex() { return session.room().zone(); }
    @Override public int startActIndex() { return session.room().act(); }
    @Override public void setClearColor() { base.setClearColor(); }
    @Override public void update(InputHandler input) {
        session.progress().tick();
        if(base.getState()!=State.ACTIVE && !open) { base.update(input);return; }
        base.update(neutral);activeFrames++;
        if(input==null||activeFrames<100||exiting) return;
        var row=input.logical();
        if(!open) { if(accept(input)) { open=true;sound(0xA1); } return; }
        if((row.player1().actionPressedMask() & 2)!=0 || input.isKeyPressed(256)) { open=false;sound(0xCD);return; }
        int dx=(row.menuRight()?1:0)-(row.menuLeft()?1:0),dy=(row.menuDown()?1:0)-(row.menuUp()?1:0);
        if(dx!=0||dy!=0) { navigate(dx,dy);sound(0xCD); }
        if(accept(input)) sound(launchSelected()?0xA1:0xA6);
    }
    private static boolean accept(InputHandler input) {
        var row=input.logical();
        return (row.player1().actionPressedMask() & 5)!=0 || row.menuStart() || input.isKeyPressed(257);
    }
    private static void sound(int id) { GameServices.audio().playSfx(id); }
    @Override public void draw() { base.draw(); }
    @Override public void drawOverlay(PixelCanvas c) {
        if(open) { drawHub(c);return; }
        if(base.getState()!=State.ACTIVE||activeFrames<100) return;
        if(!open) {
            ui.panel(c,(c.width()-136)/2,145,136,31,0xFF60E8FF);
            ui.centre(c,"ECHOES",150,3,0xFF60E8FF);
            ui.centre(c,"START THE ECHO ENGINE",185,1,0xFFFFFFFF);return;
        }
    }
    public void drawHub(PixelCanvas c) {
        c.fill(0,0,c.width(),c.height(),0xF0081028);
        ui.text(c,"THE ECHO ENGINE",14,12,2,0xFF60E8FF);
        ui.star(c,c.width()-72,15,1,true);
        ui.text(c,session.progress().totalStars()+"/36",c.width()-60,15,1,0xFFFFD030);
        String[] zones={"EMERALD HILL","HILL TOP","CHEMICAL PLANT"};
        String[] themes={"FIRST ECHO","SEESAW SCIENCE","PARADOX PLANT"};
        int tabWidth=(c.width()-28)/3;
        for(int w=0;w<3;w++) {
            int x=14+w*tabWidth; boolean chosen=w==selected/4;
            c.fill(x,37,tabWidth-3,18,chosen?0xFF255675:0xFF172642);
            ui.text(c,(w+1)+" "+zones[w],x+4,43,1,chosen?0xFFFFFFFF:0xFF8094B8);
        }
        ui.text(c,themes[selected/4],15,63,1,0xFFA0B8D8);
        int cardW=(c.width()-40)/2;
        for(int i=0;i<4;i++) {
            String id=(selected/4+1)+"-"+(i+1); boolean exists=available(id),unlocked=session.progress().unlocked(id);
            int x=14+i%2*(cardW+12),y=79+i/2*57;
            int accent=selected%4==i?0xFF60E8FF:0xFF344A70;
            ui.panel(c,x,y,cardW,51,accent);
            var room=exists?RoomDefinition.load(id):null;
            ui.text(c,id+" "+(exists?room.name():"COMING SOON"),x+7,y+7,1,unlocked?0xFFFFFFFF:0xFF76839A);
            if(!unlocked) {
                int lx=x+cardW-13,ly=y+5;
                c.fill(lx+1,ly,5,5,0xFF8392AA);c.fill(lx+2,ly+1,3,4,0xFF0A1535);
                c.fill(lx,ly+4,7,6,0xFF8392AA);c.fill(lx+3,ly+6,1,2,0xFF0A1535);
            }
            var best=session.progress().best(id);
            for(int star=0;star<3;star++) ui.star(c,x+8+star*13,y+23,1,best!=null&&(best.stars()&1<<star)!=0);
            if(exists) ui.text(c,"BEST "+(best==null?"-":best.echoes())+" / PAR "+room.par(),x+53,y+23,1,unlocked?0xFFFFD030:0xFF807059);
            String detail=!exists?"COMING SOON":unlocked?"READY TO LOOP":"LOCK: "+session.progress().requirement(id);
            ui.text(c,detail,x+7,y+38,1,unlocked?0xFF91C0D8:0xFF9B8DAB);
        }
        ui.centre(c,"ARROWS MOVE  A/C/START PLAY  B BACK",203,1,0xFFA0C8E0);
        ui.centre(c,"LEFT/RIGHT AT GRID EDGE CHANGES WORLD",215,1,0xFF667C9C);
    }
}

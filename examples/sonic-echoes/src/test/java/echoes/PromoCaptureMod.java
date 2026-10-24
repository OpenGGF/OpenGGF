package echoes;

import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.game.mode.*;
import com.openggf.control.*;
import com.openggf.mods.code.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.*;
import java.nio.file.*;
import java.io.*;

/** Test-only promo instrumentation, 2026-10-24 Sonic Echoes promo task.
 * No gameplay writes: optional pre-boot room selection and post-render CSV observation.
 * Requires every=1, capture-from=0. Capture-only jar, never distribute.
 */
public final class PromoCaptureMod implements GgfMod {
    @Override public void register(ModContext context) {
        new EchoesMod().register(context);
        context.registerGamePatch(new GamePatch() {
            public String id(){return EchoesMod.ID+":promo-observation";}
            public String displayName(){return "Promo observation";}
            public String baseGameId(){return "s2";}
            public boolean activatesFor(GameplayLaunchRequest r){return r.gameId().equals("s2");}
            public Set<LogicalRom> romPrerequisites(){return Set.of(LogicalRom.S2);}
            public List<String> providedMainCharacters(){return List.of();}
            public GameModule apply(GameModule base,PatchContext patch){
                String room=System.getProperty("echoes.capture.room","");
                if(!room.isBlank()) base.getGameService(RoomLauncher.class).launch(room);
                return new ObservedModule(base);
            }
        });
    }
    static final class ObservedModule extends DelegatingGameModule {
        final RoomSession session;
        final Sidecar csv;
        TitleScreenProvider title;
        GameplayFrameController controller;
        ObservedModule(GameModule base){super(base,"sonic-echoes:promo-observation");session=base.getGameService(RoomSession.class);csv=new Sidecar(session);}
        public GameplayFrameController gameplayFrameController(){
            if(controller!=null)return controller;
            var delegate=super.gameplayFrameController();
            if(delegate==null)return null;
            return controller=new GameplayFrameController(){
                public boolean beforeTick(CourseControl c,LogicalInputSnapshot r){return delegate.beforeTick(c,r);}
                public GameplayFrameDisposition beforeFrame(CourseControl c,LogicalInputSnapshot r){csv.input=r.player1();csv.before(c);return delegate.beforeFrame(c,r);}
                public void afterTick(CourseControl c,boolean a){delegate.afterTick(c,a);csv.after(c,a);}
                public boolean allowsDebugRewind(){return delegate.allowsDebugRewind();}
                public boolean consumeTitleRequest(){return delegate.consumeTitleRequest();}
                public void drawOverlay(){delegate.drawOverlay();csv.write(null);}
                public void close(){delegate.close();}
            };
        }
        public TitleScreenProvider getTitleScreenProvider(){
            if(title!=null)return title;
            var d=super.getTitleScreenProvider();
            if(d==null)return null;
            final EchoHub observed=(EchoHub) unwrap(d);
            title=new TitleScreenProvider(){
                public void initialize(){d.initialize();}
                public void update(InputHandler i){
                    csv.input=i==null?PlayerInputState.neutral():i.logical().player1();
                    String previous=observed.selectedId();boolean exiting=d.isExiting();
                    d.update(i);
                    if(!previous.equals(observed.selectedId()))csv.events.add("hub_move");
                    if(!exiting&&d.isExiting())csv.events.add("hub_confirm");
                }
                public void draw(){d.draw();}
                public void drawOverlay(com.openggf.mods.ui.PixelCanvas c){d.drawOverlay(c);csv.hubSelected=observed.selectedId();csv.write(observed.hubOpen()?"HUB":"TITLE");}
                public void setClearColor(){d.setClearColor();}
                public void reset(){d.reset();}
                public State getState(){return d.getState();}
                public boolean isExiting(){return d.isExiting();}
                public boolean isActive(){return d.isActive();}
                public boolean ownsEscapeInput(){return d.ownsEscapeInput();}
                public boolean supportsLevelSelectOverlay(){return d.supportsLevelSelectOverlay();}
                public TitleScreenAction consumeExitAction(){return d.consumeExitAction();}
                public int startZoneIndex(){return d.startZoneIndex();}
                public int startActIndex(){return d.startActIndex();}
                public void setExitToLevelHandler(Runnable r){d.setExitToLevelHandler(r);}
            };return title;
        }
    }
    static Object unwrap(Object value){
        try { while(java.lang.reflect.Proxy.isProxyClass(value.getClass())) {var h=java.lang.reflect.Proxy.getInvocationHandler(value);var f=h.getClass().getDeclaredField("delegate");f.setAccessible(true);value=f.get(h);}return value; }
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    static final class Sidecar {
        String hubSelected="";
        final RoomSession s; PrintWriter out; int frame,previousHold,previousLoops,previousLoopFrame; boolean previousClear;
        Set<Integer> previousShards=Set.of();Set<String> previousChannels=Set.of(),previousGates=Set.of();
        final boolean[] previousFractured=new boolean[5];
        final List<String> events=new ArrayList<>();PlayerInputState input=PlayerInputState.neutral();
        final Map<AbstractPlayableSprite,Integer> oldYSpeed=new IdentityHashMap<>();
        final Map<AbstractPlayableSprite,Object> oldSupport=new IdentityHashMap<>();
        final List<com.openggf.level.objects.AbstractBadnikInstance> badniks=new ArrayList<>();
        Sidecar(RoomSession s){this.s=s;}
        void before(CourseControl c){
            var services=c.objectServices();oldYSpeed.clear();oldSupport.clear();badniks.clear();
            for(var p:services.spriteManager().getAllSprites())if(p instanceof AbstractPlayableSprite b){oldYSpeed.put(b,(int)b.getYSpeed());oldSupport.put(b,services.objectManager().getRidingObject(b));}
            for(var o:services.objectManager().getActiveObjects())if(o instanceof com.openggf.level.objects.AbstractBadnikInstance b&&!b.isDestroyed())badniks.add(b);
        }
        String who(AbstractPlayableSprite p){var list=GameServices.sprites().getSidekicks();int i=list.indexOf(p);return i<0?"live":"echo"+i;}
        void after(CourseControl c,boolean advanced){
            if(!advanced)return;
            var services=c.objectServices();
            for(var e:oldYSpeed.entrySet()){
                var p=e.getKey();if(!s.active(p,services))continue;
                if(p.getYSpeed() < -1500 && e.getValue()>=0){
                    Object support=oldSupport.get(p);
                    if(support!=null&&support.getClass().getSimpleName().contains("Seesaw"))events.add("seesaw_launch:"+who(p));
                    else for(var o:services.objectManager().getActiveObjects())if(o.getClass().getSimpleName().contains("Spring")&&Math.abs(o.getX()-p.getCentreX())<48&&Math.abs(o.getY()-p.getCentreY())<64){events.add("spring:"+who(p));break;}
                }
            }
            for(var b:badniks)if(b.isDestroyed()){
                AbstractPlayableSprite nearest=null;long distance=Long.MAX_VALUE;
                for(var p:oldYSpeed.keySet())if(s.active(p,services)){
                    long dx=p.getCentreX()-b.getX(),dy=p.getCentreY()-b.getY(),d=dx*dx+dy*dy;
                    if(d<distance){distance=d;nearest=p;}
                }
                if(nearest!=null&&distance<4096)events.add("badnik_kill:"+who(nearest));
            }
        }
        @SuppressWarnings("unchecked") Set<Integer> shards(){
            try{var f=RoomSession.class.getDeclaredField("shards");f.setAccessible(true);return Set.copyOf((Set<Integer>)f.get(s));}
            catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        void write(String override){
            String path=System.getProperty("echoes.capture.session","");if(path.isEmpty())return;
            if(out==null)try{Path p=Path.of(path);Files.createDirectories(p.getParent());out=new PrintWriter(Files.newBufferedWriter(p));
                out.print("frame,room,loop,loopFrame,phase,liveX,liveY,camX,camY");for(int i=0;i<5;i++)out.print(",e"+i+"x,e"+i+"y,e"+i+"state");out.println(",channels,shards,events,input,inputActions,inputStart,hubSelected");
            }catch(IOException e){throw new UncheckedIOException(e);}
            boolean level=override==null;
            var channels=new TreeSet<String>();var gates=new TreeSet<String>();
            if(level)for(var d:s.room().objects()){
                if(d.kind().equals("plate")&&s.channel(d.channels()))channels.add(d.channels());
                if(d.kind().equals("gate")&&s.allChannels(d.channels()))gates.add(d.channels());
            }
            var shards=level?shards():Set.<Integer>of();
            if(level){
                if(s.rewindHold()>previousHold){if(s.loopCount()>previousLoops){events.add("commit");if(s.frame()>=s.room().loopFrames())events.add("timeout");}events.add("rewind_start");}
                if(previousHold>0&&s.rewindHold()==0&&s.frame()==0)events.add("restore");
                for(int i=0;i<5;i++){if(s.fractured(i)&&!previousFractured[i])events.add("fracture:"+i);previousFractured[i]=s.fractured(i);}
                for(int i:shards)if(!previousShards.contains(i))events.add("shard:"+i);
                for(String ch:channels)if(!previousChannels.contains(ch))events.add("plate_on:"+ch);
                for(String ch:gates)if(!previousGates.contains(ch))events.add("gate_open:"+ch);
                if(s.cleared()&&!previousClear)events.add("clear");
                if(s.cleared()&&(s.clearAge()==12||s.clearAge()==30||s.clearAge()==48)){int n=(s.clearAge()-12)/18;if((s.clearStars()&1<<n)!=0)events.add("star:"+(n+1));}
            }
            var p=GameServices.camera().getFocusedSprite();var camera=GameServices.camera();
            boolean initialized;try{var f=RoomSession.class.getDeclaredField("initialized");f.setAccessible(true);initialized=f.getBoolean(s);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            String phase=override!=null?override:!initialized?"OTHER":s.cleared()?"CLEAR":s.rewindHold()>0?"REWIND_HOLD":s.frame()==0?"READY":"PLAYING";
            var row=new ArrayList<String>(List.of(""+frame++,s.room().id(),""+s.loopCount(),""+s.frame(),phase,p==null?"":""+p.getCentreX(),p==null?"":""+p.getCentreY(),""+camera.getX(),""+camera.getY()));
            var bodies=GameServices.sprites().getSidekicks();
            for(int i=0;i<5;i++){
                boolean active=level&&i<s.recordings().size();var b=i<bodies.size()?bodies.get(i):null;
                row.add(!active||b==null?"":""+b.getCentreX());row.add(!active||b==null?"":""+b.getCentreY());
                row.add(!active?"inactive":s.fractured(i)?"fractured":b!=null&&b.getGSpeed()==0&&!b.getAir()&&s.recordings().get(i).input(s.frame()).heldMask()==0?"holding":"active");
            }
            row.add(String.join(";",channels));row.add(shards.stream().sorted().map(Object::toString).collect(java.util.stream.Collectors.joining(";")));row.add(String.join(";",events));row.add(""+input.heldMask());row.add(""+input.actionHeldMask());row.add(""+input.startHeld());row.add(override==null?"":hubSelected);
            out.println(String.join(",",row));out.flush();if(out.checkError())throw new IllegalStateException("session.csv write failed");events.clear();
            previousHold=s.rewindHold();previousLoops=s.loopCount();previousLoopFrame=s.frame();previousClear=s.cleared();previousShards=shards;previousChannels=channels;previousGates=gates;
        }
    }
}

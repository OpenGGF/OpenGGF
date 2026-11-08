package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.rewind.CompositeSnapshot;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Ordinary input recipes only. Historical save1/platform80 is constructed by the existing
 * original Boot fixture; observations and captures never initialize gameplay values.
 * N1ecc5f3: Title Card/Main.asm WaitPLC; objects/player.asm PlayerMain/CheckJump;
 * actual historical objects/r3/intro.asm IntroSpikesSolid/Init. */
@Isolated class TestSonicCdR31aOrdinaryRoute {
    @TempDir Path directory;
    private final TestSonicCdR31aSelectedProfileLoading fixture=new TestSonicCdR31aSelectedProfileLoading();
    private final List<Picture> pictures=new ArrayList<>();
    private final Map<String,String> documents=new LinkedHashMap<>();
    private record Picture(String name,byte[] rgba,String state) { }
    @BeforeEach void setup(){fixture.directory=directory;fixture.setup();}
    @AfterEach void cleanup()throws Exception{
        fixture.cleanup();String raw=System.getProperty("soniccd.r31.route.capture.dir");if(raw==null)return;
        Path root=Path.of(Objects.requireNonNull(System.getenv("SONICCD_ENGINE_CAPTURE_ROOT"))).toRealPath(),dest=Path.of(raw).toRealPath();
        assertTrue(dest.startsWith(root)&&!dest.equals(root));
        for(var p:pictures){var image=new java.awt.image.BufferedImage(320,224,java.awt.image.BufferedImage.TYPE_INT_ARGB);byte[] b=p.rgba();
            for(int y=0;y<224;y++)for(int x=0;x<320;x++){int k=((223-y)*320+x)*4;image.setRGB(x,y,((b[k+3]&255)<<24)|((b[k]&255)<<16)|((b[k+1]&255)<<8)|(b[k+2]&255));}
            try(var output=Files.newOutputStream(dest.resolve(p.name()+".png"),StandardOpenOption.CREATE_NEW)){assertTrue(javax.imageio.ImageIO.write(image,"png",output));}
            Files.writeString(dest.resolve(p.name()+".state.txt"),p.state()+"\nExported after session/GL/fixture cleanup.\n",StandardOpenOption.CREATE_NEW);
        }
        for(var e:documents.entrySet())Files.writeString(dest.resolve(e.getKey()),e.getValue(),StandardOpenOption.CREATE_NEW);
    }
    private static void step(TestSonicCdR31aSelectedProfileLoading.Booted b,int held){
        // Public logical controls use A/B/C order1/2/4; native held uses A40/B10/C20.
        int action=((held&0x40)!=0?InputActionMasks.ACTION_A:0)|((held&0x10)!=0?InputActionMasks.ACTION_B:0)|((held&0x20)!=0?InputActionMasks.ACTION_C:0);
        var input=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(held&15,0,action,0,(held&0x80)!=0,false),PlayerInputState.neutral());
        assertEquals(held,SonicCdPlayerPresentation.nativeHeldByte(input,false));
        b.loop.getInputHandler().setLogicalOverride(input);b.loop.step();
    }
    private static int x(CdPlayerBody.State s){return s.motion().centreX16_16()>>>16;}
    private static int y(CdPlayerBody.State s){return s.motion().centreY16_16()>>>16;}
    private static boolean grounded(CdPlayerBody.State s){return s.motion().routine()==2&&(s.motion().flags()&2)==0;}
    private static String sha(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static String nearbyForeground(TestSonicCdR31aSelectedProfileLoading.Booted b){
        var t=b.module.r31WorldHost().currentTerrain();var s=b.module.requirePlayerFrame().state();var rows=new ArrayList<String>();
        for(int dy=-3;dy<=3;dy++)for(int dx=-2;dx<=2;dx++){int px=(x(s)&0xFFF0)+dx*16,py=(y(s)&0xFFF0)+dy*16;if(px<0||py<0||px>=0x8000||py>=0x800)continue;
            var cell=t.layout().nativeCell(com.openggf.game.soniccd.level.CdStageTerrain.Plane.FOREGROUND,px>>>8,py>>>8);rows.add(px+","+py+":chunk="+cell.chunkId()+",word="+(cell.blank()?"blank(no GetBlock value claim)":Integer.toHexString(t.chunkWord(cell.chunkId(),(px>>>4)&15,(py>>>4)&15).rawWord())));}
        return rows.toString();
    }
    private static int title(SonicCdR31aLoadingVisit core,int at){return core.state().reserved().unsignedByte(3*64+at);}
    private static String header(){return "recipeOrdinal,levelFrames,phase,x,y,xVelocity,yVelocity,groundVelocity,flags,routine,rawHeld,rawTap,logicalHeld,logicalTap,controlLocked,titleId,titleRoutine,titleY,titleTimer,plcHead,lives,cameraX,cameraY,bottom\n";}
    private static String row(TestSonicCdR31aSelectedProfileLoading.Booted b,int ordinal){
        var f=b.module.requirePlayerFrame();var m=f.state().motion();var c=f.environment().controls();var core=b.module.r31LoadingVisit();var h=b.module.r31WorldHost();var s=h.scroll().foreground();
        return ordinal+","+f.environment().globals().levelFrames()+","+core.state().phase()+","+x(f.state())+","+y(f.state())+","+m.xVelocity()+","+m.yVelocity()+","+m.groundVelocity()+","+m.flags()+","+m.routine()+","+c.rawHeld()+","+c.rawTap()+","+c.logicalHeld()+","+c.logicalTap()+","+c.controlLocked()+","+title(core,0)+","+title(core,0x24)+","+core.state().reserved().unsignedWord(192+0xA)+","+title(core,0x1E)+","+core.state().section().queue().headSource()+","+h.globals().ring().lives()+","+s.cameraX()+","+s.cameraYWord()+","+f.environment().movement().bounds().bottom()+"\n";
    }
    private static String cut(TestSonicCdR31aSelectedProfileLoading.Booted b,int ordinal){
        var f=b.module.requirePlayerFrame();var h=b.module.r31WorldHost();var p=h.placement().capture();
        var selected=assertInstanceOf(SonicCdR31aWorldHost.Published.class,h.selected()).selection().inputs();
        return "ACTUAL RETURNED engine cut; recipeOrdinal="+ordinal+"; generation="+b.mode.getLevelManager().getCompletedProductionLoadGeneration()+"\nprogramme="+b.module.r31LoadingVisit().assets().identity()+"\nCore="+b.module.r31LoadingVisit().state()+"\nState8="+f.state()+"\nHistory="+HexFormat.of().formatHex(f.state().history().bytes().copy())+"\nEnvironment="+f.environment()+"\nClock="+f.vIntClock()+"\nScroll="+h.scroll()+"\nGlobals="+h.globals()+"\nAmyCaptured="+h.amyCapturedByte()+"\nStatus="+p.status()+"\nSaved="+HexFormat.of().formatHex(p.savedFlags().copy())+"\nPool96="+p.pool().slots().stream().map(s->s.identity().wholeIndex()+":"+HexFormat.of().formatHex(s.copyBytes())).toList()+"\nCompletedCPU="+h.video().completedCpu().map(c->HexFormat.of().formatHex(c.bytes()))+"\nOLD_selectedCPU="+selected.selectedCpu().map(c->HexFormat.of().formatHex(c.bytes()))+"\nTerrainChunksSHA256="+sha(h.currentTerrain().chunks().copy())+"\nForegroundComparisonWords="+nearbyForeground(b)+"\nPlaneASHA256="+sha(h.foreground().nameTable().copy())+"\nPlaneBSHA256="+sha(h.background().nameTable().copy())+"\nPlayerCPU_SHA256="+sha(f.artState().cpuBuffer().copy())+"\nPlayerBankSHA256="+sha(f.artState().residentBank().copy())+"\nPixels use the actual OLD selected CPU/current-art publication; returned State8 may be newer.\n";
    }
    private void picture(TestSonicCdR31aSelectedProfileLoading.Booted b,int n,String name)throws Exception{
        byte[] rgba=TestSonicCdR31aSelectedProfileLoading.draw(b);pictures.add(new Picture(name,rgba,cut(b,n)+"rgbaSHA256="+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(rgba))));
    }
    private static void replayNext(TestSonicCdR31aSelectedProfileLoading.Booted b,int held)throws Exception{
        var r=b.mode.getRewindRegistry();var full=r.capture();var course=r.captureCourse();var before=TestSonicCdR31aSelectedProfileLoading.values(b);var pixels=TestSonicCdR31aSelectedProfileLoading.draw(b);
        step(b,held);var after=TestSonicCdR31aSelectedProfileLoading.values(b);var next=TestSonicCdR31aSelectedProfileLoading.draw(b);
        for(boolean c:new boolean[]{false,true}){if(c)r.restoreCourse(course);else r.restore(full);assertEquals(before,TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(pixels,TestSonicCdR31aSelectedProfileLoading.draw(b));step(b,held);assertEquals(after,TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(next,TestSonicCdR31aSelectedProfileLoading.draw(b));}
        r.restore(full);assertEquals(before,TestSonicCdR31aSelectedProfileLoading.values(b));
    }
    private record ClosedComparison(CompositeSnapshot full,CompositeSnapshot course,Object values,byte[] pixels) { }
    private static ClosedComparison comparison(TestSonicCdR31aSelectedProfileLoading.Booted b)throws Exception{
        var r=b.mode.getRewindRegistry();return new ClosedComparison(r.capture(),r.captureCourse(),TestSonicCdR31aSelectedProfileLoading.values(b),TestSonicCdR31aSelectedProfileLoading.draw(b));
    }
    /** Verify only after the ordinary next service actually returned. A first unowned edge is
     * never retried/restored; its last closed comparison stays diagnostic metadata only. */
    private static void verifyReturnedNext(TestSonicCdR31aSelectedProfileLoading.Booted b,int held,ClosedComparison before)throws Exception{
        var r=b.mode.getRewindRegistry();var closed=r.capture();var after=TestSonicCdR31aSelectedProfileLoading.values(b);var pixels=TestSonicCdR31aSelectedProfileLoading.draw(b);
        for(boolean c:new boolean[]{false,true}){if(c)r.restoreCourse(before.course());else r.restore(before.full());assertEquals(before.values(),TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(before.pixels(),TestSonicCdR31aSelectedProfileLoading.draw(b));step(b,held);assertEquals(after,TestSonicCdR31aSelectedProfileLoading.values(b));assertArrayEquals(pixels,TestSonicCdR31aSelectedProfileLoading.draw(b));}
        r.restore(closed);
    }
    @Test void rightOnlyHonoursOriginalTitleUnlockBeforeItsFirstClosedDeath()throws Exception{
        var csv=new StringBuilder(header());documents.put("right-only.csv","");
        try(var b=fixture.start(1,0x80)){
            TestSonicCdR31aSelectedProfileLoading.ready(b);var core=b.module.r31LoadingVisit();var f=b.module.requirePlayerFrame();assertEquals(1,f.environment().controls().controlLocked());assertEquals(0x3C,title(core,0));
            int unlocked=-1,death=-1;String lastAlive=cut(b,0);csv.append(row(b,0));
            for(int n=1;n<=500;n++){
                var prior=f.environment().controls();int routine=title(core,0x24);step(b,8);csv.append(row(b,n));var now=f.environment().controls();
                if(prior.controlLocked()!=0&&now.controlLocked()==0){
                    assertEquals(10,routine);assertEquals(0,core.state().section().queue().headSource());assertEquals(0,title(core,0));assertEquals(0,now.logicalHeld(),"Primary preceded the original Title unlock in this physical walk");assertEquals(8,now.rawHeld());unlocked=n;
                    picture(b,n,"initial-title-unlocked");replayNext(b,8);
                }
                if(unlocked<0){assertEquals(0,now.logicalHeld());assertEquals(0,now.logicalTap());}
                else if(n>unlocked){assertEquals(8,now.logicalHeld());}
                if(f.state().motion().routine()>=6){death=n;documents.put("right-only-first-death.txt","LAST_ALIVE\n"+lastAlive+"\nCLOSED_DEATH\n"+cut(b,n));break;}
                lastAlive=cut(b,n);
            }
            assertTrue(unlocked>0);assertTrue(death>unlocked);assertEquals(1,core.state().calls().stream().filter(c->c==SonicCdR31aLoadingVisit.Call.TITLE_PLC_APPEND).count());
            documents.put("right-only-summary.txt","Diagnostic recipe: Right8 only, stopped at first fully returned death, not extended into reload cycles. unlock="+unlocked+" death="+death+"\n");
        }finally{documents.put("right-only.csv",csv.toString());}
    }
    /** Controller observation only. Jump once before the first selected solid IntroSpikes wall.
     * Native $30 subtype!=1 is ordinary Solid; native widths/radii locate its leading edge.
     * A96-pixel approach leaves time for CheckJump's native$680 launch to clear its32-pixel halfheight.
     * No frame ordinal, actor store, terrain edit, physics change or fake player is used. */
    private static final class FirstWallJump {
        boolean jumped,landed,justRequested,rejected;int wallWhole=-1,wallRight,wallTop,jumps,lastLaunchX=-1,lastLaunchY=-1;
        int input(TestSonicCdR31aSelectedProfileLoading.Booted b){
            justRequested=false;var f=b.module.requirePlayerFrame();var s=f.state();var m=s.motion();
            if(jumped){
                if(!grounded(s))return m.yVelocity()<0?0x18:8;
                landed=true;
                // PlayerCheckJump clears native push bit5. A later grounded push is a new
                // actual wall, not an instruction to adjust the actor or its collision result.
                // Never retry a rejected launch at the same closed position.
                if((m.flags()&0x20)!=0&&(x(s)!=lastLaunchX||y(s)!=lastLaunchY)&&(f.environment().controls().rawHeld()&0x10)==0)
                    return request(s);
                return 8;
            }
            if(!grounded(s)||f.environment().controls().controlLocked()!=0)return 8;
            for(var wall:b.module.r31WorldHost().placement().capture().pool().slots()){
                if(wall.id()!=0x30||wall.routine()!=2||wall.subtype()==1||Math.abs(wall.centreY()-y(s))>m.yRadius()+wall.unsignedByte(CdObjectState.ByteField.Y_RADIUS))continue;
                int gap=wall.centreX()-wall.unsignedByte(CdObjectState.ByteField.WIDTH)-m.xRadius()-x(s);
                if(gap>=0&&gap<=96){wallWhole=wall.identity().wholeIndex();wallRight=wall.centreX()+wall.unsignedByte(CdObjectState.ByteField.WIDTH);wallTop=wall.centreY()-wall.unsignedByte(CdObjectState.ByteField.Y_RADIUS);return request(s);}
            }
            return 8;
        }
        private int request(CdPlayerBody.State s){jumped=true;justRequested=true;jumps++;lastLaunchX=x(s);lastLaunchY=y(s);return 0x18;}
        void returned(CdPlayerBody.State s){if(justRequested&&grounded(s))rejected=true;}
    }
    private static void assertCapturePalette(TestSonicCdR31aSelectedProfileLoading.Booted b)throws Exception{
        var expected=com.openggf.game.soniccd.level.CdR31IntroAssets.fromAssets(b.module.r31LoadingVisit().assets()).capturePalette();
        assertArrayEquals(expected.copy(),b.module.r31LoadingVisit().state().normalPalette().slice(32,32).copy());
    }
    private record PalettePublication(String producer,int whole,
            com.openggf.game.soniccd.level.CdAssetBytes before,
            com.openggf.game.soniccd.level.CdAssetBytes after) { }
    private static Object field(Object receiver,String name)throws ReflectiveOperationException{
        var f=receiver.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(receiver);
    }
    /** Comparison-only spy, installed/restored while idle. Every real operation is immediately
     * forwarded once to the unchanged World port with the original arguments and exception.
     * No private receipt is constructed, retained, replayed, acknowledged or used to drive input.
     * Observe inside original adopt, before ordinary palette cycling can hide a missing copy. */
    private static final class IntroPaletteObserver implements AutoCloseable {
        final TestSonicCdR31aSelectedProfileLoading.Booted boot;
        final Object pass,original;final java.lang.reflect.Field port;
        final List<PalettePublication> current=new ArrayList<>();boolean recording;
        IntroPaletteObserver(TestSonicCdR31aSelectedProfileLoading.Booted b)throws Exception{
            boot=b;var world=(CdWorld)field(b.module,"r31World");assertFalse(world.isOrdinaryRunning());
            pass=field(field(world,"binding"),"objects");assertNull(field(pass,"dispatch"));
            port=pass.getClass().getDeclaredField("ports");port.setAccessible(true);original=port.get(pass);
            var spy=java.lang.reflect.Proxy.newProxyInstance(SonicCdR31aObjectPass.Ports.class.getClassLoader(),
                    new Class<?>[]{SonicCdR31aObjectPass.Ports.class},(proxy,method,args)->{
                boolean capture=false;String producer=null;int whole=-1;
                com.openggf.game.soniccd.level.CdAssetBytes before=null,prepared=null;
                if(method.getName().equals("adopt")){
                    var call=(SonicCdR31aObjectPass.Adoption)args[0];
                    capture=call.introPalette()&&call.next().amyCapturedByte().orElse(-1)==0xFF;
                    if(capture){
                        var invocation=field(pass,"intro");producer=((Enum<?>)field(invocation,"kind")).name();
                        whole=(Integer)field(invocation,"whole");before=call.expected().core().normalPalette();
                        prepared=call.next().core().normalPalette();
                        assertSame(before,boot.module.r31LoadingVisit().state().normalPalette());
                    }
                }
                Object result;try{result=method.invoke(original,args);}
                catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
                if(capture){
                    var after=boot.module.r31LoadingVisit().state().normalPalette();
                    assertSame(prepared,after,"The original World port adopted this exact prepared palette");
                    assertNotSame(before,after,"Each original captured palette call copies even equal bytes");
                    assertCapturePalette(boot);
                    if(recording)current.add(new PalettePublication(producer,whole,before,after));
                }
                return result;
            });
            port.set(pass,spy);
        }
        void step(int held){current.clear();recording=true;try{TestSonicCdR31aOrdinaryRoute.step(boot,held);}finally{recording=false;}}
        @Override public void close()throws Exception{assertNull(field(pass,"dispatch"));port.set(pass,original);}
    }
    @Test void ordinaryJumpsPreserveLifeThroughNativeIntroClosureAtTheNextTerrainWall()throws Exception{
        var csv=new StringBuilder(header());var policy=new FirstWallJump();int returned=0,introPublished=-1,introClosed=-1;Throwable frontier=null;boolean airborne=false,airPicture=false,landingPicture=false,landedBeyond=false;int furthestGrounded=0;String last="";int amyWhole=-1,metalWhole=-1;com.openggf.game.soniccd.level.CdAssetBytes firstCapturePalette=null,secondCapturePalette=null,secondReturnedPalette=null;
        try(var b=fixture.start(1,0x80)){
            TestSonicCdR31aSelectedProfileLoading.ready(b);
            try(var paletteObserver=new IntroPaletteObserver(b)){
            var f=b.module.requirePlayerFrame();var source=f.stageSource();var core=b.module.r31LoadingVisit();var host=b.module.r31WorldHost();var placement=host.placement();int initialLives=host.globals().ring().lives();long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();last=cut(b,0);csv.append(row(b,0));ClosedComparison pending=null;
            for(int n=1;n<=5000;n++){
                int held=policy.input(b);boolean hadJumped=policy.jumped;if(hadJumped&&!airborne)pending=comparison(b);
                var priorPool=placement.capture().pool();
                try{paletteObserver.step(held);}catch(UnsupportedOperationException reached){frontier=reached;break;}returned=n;csv.append(row(b,n));
                if(pending!=null){verifyReturnedNext(b,held,pending);pending=null;}policy.returned(f.state());
                assertSame(f,b.module.requirePlayerFrame());assertSame(source,f.stageSource());assertSame(core,b.module.r31LoadingVisit());assertSame(host,b.module.r31WorldHost());assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());
                assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase(),"Playable recipe stops on death rather than continuing reload cycles");
                assertTrue(f.state().motion().routine()<6,"Actual ordinary recipe died; retain diagnostic, do not force or retry it");
                assertEquals(initialLives,host.globals().ring().lives());
                if(hadJumped&&!airborne&&(f.state().motion().flags()&2)!=0){assertEquals(0x10,f.environment().controls().rawTap()&0x10);assertEquals(-0x680,f.state().motion().yVelocity());airborne=true;picture(b,n,"first-native-jump");}
                else if(airborne&&!airPicture&&(f.state().motion().flags()&2)!=0){airPicture=true;picture(b,n,"following-returned-air-current-picture");}
                if(grounded(f.state()))furthestGrounded=Math.max(furthestGrounded,x(f.state()));
                if(airborne&&!landingPicture&&grounded(f.state())){landingPicture=true;picture(b,n,"first-source-ground-after-jump");pending=comparison(b);}
                // Native4C0 is an optional player-abort threshold, not completion. The normal
                // Metal escape reaches528 and publishes FF (intro.asm185-208), palette, delete.
                if(introPublished<0&&host.amyCapturedByte().orElseThrow()==0xFF){
                    var amys=priorPool.slots().stream().filter(s->s.id()==0x32).toList();
                    var metals=priorPool.slots().stream().filter(s->s.id()==0x31).toList();
                    assertEquals(1,amys.size());assertEquals(1,metals.size());
                    amyWhole=amys.getFirst().identity().wholeIndex();metalWhole=metals.getFirst().identity().wholeIndex();
                    assertTrue(amyWhole<metalWhole,"Amy already ran before Metal's capture publication");
                    assertEquals(4,amys.getFirst().routine());assertEquals(0x32,placement.pool().state(amyWhole).id());
                    assertEquals(4,placement.pool().state(amyWhole).routine());
                    assertArrayEquals(new byte[64],placement.pool().state(metalWhole).copyBytes());
                    assertEquals(1,paletteObserver.current.size(),"One original capture palette publication in Metal's pass");
                    var publication=paletteObserver.current.getFirst();assertEquals("METAL",publication.producer());assertEquals(metalWhole,publication.whole());
                    introPublished=n;firstCapturePalette=publication.after();assertCapturePalette(b);
                    documents.put("first-native-capture-publication.txt",cut(b,n));
                }else if(introPublished>=0&&secondCapturePalette==null){
                    assertEquals(introPublished+1,n,"Earlier Amy reads FF on the next actual physical pass");
                    assertEquals(0x32,priorPool.slots().get(amyWhole-32).id());assertEquals(4,priorPool.slots().get(amyWhole-32).routine());
                    assertArrayEquals(new byte[64],placement.pool().state(amyWhole).copyBytes());
                    assertEquals(1,paletteObserver.current.size(),"Earlier Amy makes its own original palette publication on its next pass");
                    var publication=paletteObserver.current.getFirst();assertEquals("AMY",publication.producer());assertEquals(amyWhole,publication.whole());
                    secondCapturePalette=publication.after();secondReturnedPalette=core.state().normalPalette();
                    assertNotSame(firstCapturePalette,secondCapturePalette,"Amy's actual publication is distinct from Metal's");assertCapturePalette(b);
                }
                // A parent earlier than Metal has already run at the first FF publication;
                // its native captured read/deletion must wait for the following physical pass.
                if(secondCapturePalette!=null&&airborne&&!landedBeyond&&grounded(f.state())&&x(f.state())>policy.wallRight&&y(f.state())+f.state().motion().yRadius()<policy.wallTop&&host.amyCapturedByte().orElseThrow()==0xFF&&placement.capture().pool().slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32)){
                    landedBeyond=true;introClosed=n;picture(b,n,"native-intro-closed-on-ground");pending=comparison(b);
                }
                last=cut(b,n);if(introClosed>=0&&n>introClosed){
                    assertEquals(introClosed+1,n);assertCapturePalette(b);
                    var selected=assertInstanceOf(SonicCdR31aWorldHost.Published.class,host.selected()).selection().inputs();
                    assertSame(secondReturnedPalette,selected.palette(),"Following ordinary VInt selects the actual completed palette after its ordinary cycle tail");
                    picture(b,n,"following-intro-palette-current-picture");break;
                }if(policy.rejected)break;
            }
            String partial=frontier==null?"none":frontier+"\n"+Arrays.toString(frontier.getStackTrace())+"\nState8="+f.state()+"\nEnvironment="+f.environment()+"\nPool="+placement.capture().pool().slots().stream().map(s->s.identity().wholeIndex()+":"+HexFormat.of().formatHex(s.copyBytes())).toList();
            documents.put("jump-route-result.txt","Recipe initial source wall then fresh grounded-push jumps; returned="+returned+" attempted="+(frontier==null?"none":returned+1)+" jumped="+policy.jumped+" wallWhole="+policy.wallWhole+" jumps="+policy.jumps+" rejected="+policy.rejected+" airborne="+airborne+" nativeCapturePublished="+introPublished+" nativeIntroClosed="+introClosed+" closedOnGround="+landedBeyond+" furthestGroundedX="+furthestGrounded+"\nLAST_CLOSED\n"+last+"\nFIRST_UNOWNED_PARTIAL(no picture/no closed snapshot)\n"+partial);
            assertNull(frontier,"An unsupported edge is diagnostic failure, never positive native intro closure");
            assertTrue(policy.jumped);assertTrue(airborne);assertTrue(furthestGrounded>0x60);assertTrue(landedBeyond,"Require actual native intro closure on safe ground");
            assertTrue(introPublished>0);assertEquals(introPublished+1,introClosed);assertNotNull(firstCapturePalette);assertNotNull(secondCapturePalette);
            assertEquals(introClosed+1,returned);assertTrue(grounded(f.state()));assertEquals(initialLives,host.globals().ring().lives());
            }
        }finally{documents.put("jump-route.csv",csv.toString());}
    }
}

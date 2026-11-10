package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original selected historical-save public input; native terrain observations never
 * feed engine state. N1ecc5f3: GetLevelBlock/FindLevelWall, R3 PlayerCheckBlock and intro. */
@Isolated class TestSonicCdR31aLowerRoute {
    @TempDir Path directory;
    final TestSonicCdR31aSelectedProfileLoading fixture=new TestSonicCdR31aSelectedProfileLoading();
    final Map<String,String> documents=new LinkedHashMap<>();
    private record Picture(String name,byte[] rgba,String state) { }
    final List<Picture> pictures=new ArrayList<>();
    @BeforeEach void setup(){fixture.directory=directory;fixture.setup();}
    @AfterEach void cleanup()throws Exception{
        fixture.cleanup();String path=System.getProperty("soniccd.r31.lower.evidence.dir");
        if(path!=null)for(var e:documents.entrySet())Files.writeString(Path.of(path).resolve(e.getKey()),e.getValue(),StandardOpenOption.CREATE_NEW);
        String raw=System.getProperty("soniccd.r31.lower.capture.dir");if(raw==null)return;
        Path root=Path.of(Objects.requireNonNull(System.getenv("SONICCD_ENGINE_CAPTURE_ROOT"))).toRealPath(),dest=Path.of(raw).toRealPath();assertTrue(dest.startsWith(root)&&!dest.equals(root));
        for(var p:pictures){var image=new java.awt.image.BufferedImage(320,224,java.awt.image.BufferedImage.TYPE_INT_ARGB);byte[] data=p.rgba();
            for(int y=0;y<224;y++)for(int x=0;x<320;x++){int k=((223-y)*320+x)*4;image.setRGB(x,y,((data[k+3]&255)<<24)|((data[k]&255)<<16)|((data[k+1]&255)<<8)|(data[k+2]&255));}
            try(var output=Files.newOutputStream(dest.resolve(p.name()+".png"),StandardOpenOption.CREATE_NEW)){assertTrue(javax.imageio.ImageIO.write(image,"png",output));}
            Files.writeString(dest.resolve(p.name()+".state.txt"),p.state()+"\nExported after session/GL/fixture cleanup.\n",StandardOpenOption.CREATE_NEW);
        }
    }
    private static int word(CdStageTerrain t,int x,int y,int flags){
        return assertInstanceOf(CdTerrainCollision.Available.class,CdTerrainCollision.fromStageTerrain(t).lookup(x,y,flags)).word().rawWord();
    }
    @Test void originalTerrainAndNativeProfilesExplainTheClosedIntroWall()throws Exception{
        try(var b=fixture.start(1,0x80)){
            TestSonicCdR31aSelectedProfileLoading.ready(b);var host=b.module.r31WorldHost();var f=b.module.requirePlayerFrame();
            var original=b.module.r31LoadingVisit().assets().terrain();var policy=new TestSonicCdR31aOrdinaryRoute.FirstWallJump();
            for(int n=1;n<=931;n++){
                TestSonicCdR31aOrdinaryRoute.step(b,policy.input(b));policy.returned(f.state());
                assertEquals(2,f.state().motion().routine());assertFalse(policy.rejected);
            }
            assertEquals(0xFF,host.amyCapturedByte().orElseThrow());assertEquals(1077,f.state().motion().centreX16_16()>>>16);
            assertEquals(908,f.state().motion().centreY16_16()>>>16);
            var current=host.currentTerrain();assertEquals(original.chunks(),current.chunks());assertEquals(original.layout().ram(),current.layout().ram());
            int flags=f.state().motion().spriteFlags();assertEquals(0x4002,word(current,1088,896,flags));
            var out=new StringBuilder("Original and current chunks/layout equal after actual returned931; no terrain mutation is inferred from equal bytes.\n");
            var actor=f.state();var environment=f.environment();
            var ids=new TreeSet<Integer>();var probes=CdPlayerTerrain.fromStageTerrain(current);
            for(int y=800;y<=1104;y+=16)for(int x=960;x<=1280;x+=16){
                var placement=CdTerrainCollision.fromStageTerrain(current).lookup(x,y,flags);
                out.append(x+","+y+" "+placement);
                if(placement instanceof CdTerrainCollision.Available a){int w=a.word().rawWord(),id=current.collision().idForBlock(w&0x7FF);ids.add(id);out.append(" id="+id+" angle="+current.collision().angle(id));}
                out.append('\n');
            }
            for(int id:ids){out.append("PROFILE "+id+" angle="+current.collision().angle(id)+" heights=");for(int i=0;i<16;i++)out.append(current.collision().height(id,i)+",");out.append(" widths=");for(int i=0;i<16;i++)out.append(current.collision().width(id,i)+",");out.append('\n');}
            for(int y=800;y<=1056;y++)out.append("WALL at1088,"+y+" "+probes.wall(1088,y,14,0,16,flags)+"\n");
            assertSame(actor,f.state());assertSame(environment,f.environment());assertSame(current,host.currentTerrain());
            out.append(TestSonicCdR31aOrdinaryRoute.cut(b,931));documents.put("terrain-audit.txt",out.toString());
        }
    }
    private void picture(TestSonicCdR31aSelectedProfileLoading.Booted b,int n,String name)throws Exception{
        byte[] rgba=TestSonicCdR31aSelectedProfileLoading.draw(b);String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(rgba));
        pictures.add(new Picture(name,rgba,TestSonicCdR31aOrdinaryRoute.cut(b,n)+"\nrgbaSHA256="+hash));
    }

    /** Source geometry: left lipX1024 has side solidity throughY975; lower floorY1024.
     * Return left off the upper ledge after Metal has retired the barriers, and turn
     * right only when the actual head is below that ceiling. No additional action tap.
     * Ordinals/captured records are diagnostics, never controller or engine values. */
    @Test void ordinaryBacktrackClosesTimePostInitBeforeTheNextGenuineOwner()throws Exception{
        var csv=new StringBuilder(TestSonicCdR31aOrdinaryRoute.header());int returned=0,closedIntro=-1,firstCapture=-1,retired=-1,amyWhole=-1,turned=-1,entered=-1,boosted=-1,postInitialised=-1,springInitialised=-1;Throwable frontier=null;boolean backtracking=false;String last="";CdAssetBytes firstCaptureNormal=null;
        try(var b=fixture.start(1,0x80)){
            TestSonicCdR31aSelectedProfileLoading.ready(b);var host=b.module.r31WorldHost();var core=b.module.r31LoadingVisit();var f=b.module.requirePlayerFrame();var placement=host.placement();var source=f.stageSource();
            long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();int lives=host.globals().ring().lives();var intro=new TestSonicCdR31aOrdinaryRoute.FirstWallJump();
            var capturePalette=core.assets().view(CdR31aAssets.Asset.PAL_LEVEL).slice(0,32);
            var terrain=host.currentTerrain();int flags=f.state().motion().spriteFlags();assertEquals(0x4003,word(terrain,1056,960,flags));assertEquals(0x2003,word(terrain,1056,1024,flags));
            // Complete original16-pixel side profile, not a guessed visual opening.
            assertEquals(81,terrain.collision().idForBlock(3));for(int i=0;i<16;i++)assertEquals(16,terrain.collision().width(81,i));
            last=TestSonicCdR31aOrdinaryRoute.cut(b,0);csv.append(TestSonicCdR31aOrdinaryRoute.row(b,0));TestSonicCdR31aOrdinaryRoute.ClosedComparison pending=null;
            for(int n=1;n<=5000;n++){
                var m=f.state().motion();int x=m.centreX16_16()>>>16,y=m.centreY16_16()>>>16;
                if(closedIntro<0&&host.amyCapturedByte().orElseThrow()==0xFF&&placement.capture().pool().slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32)){
                    closedIntro=returned;backtracking=true;assertEquals(2,m.routine());assertEquals(0,m.flags()&2);
                }
                if(backtracking&&y-m.yRadius()>=976){backtracking=false;turned=returned;documents.put("head-below-ceiling.txt",last);}
                int held=closedIntro<0?intro.input(b):returned==closedIntro?8:backtracking?4:8;
                var beforePool=placement.capture().pool();var beforeNormal=core.state().normalPalette();int beforeCapture=host.amyCapturedByte().orElseThrow();var beforeMotion=m;
                try{TestSonicCdR31aOrdinaryRoute.step(b,held);}catch(UnsupportedOperationException reached){frontier=reached;break;}
                returned=n;csv.append(TestSonicCdR31aOrdinaryRoute.row(b,n));
                // First returned death is terminal for this recipe. Preserve only its actual
                // diagnostic; do not capture/draw/restore/replay a pending milestone afterward.
                if(f.state().motion().routine()>=6){pending=null;last=TestSonicCdR31aOrdinaryRoute.cut(b,n);documents.put("first-returned-death.txt",last);break;}
                assertSame(f,b.module.requirePlayerFrame());assertSame(source,f.stageSource());assertSame(core,b.module.r31LoadingVisit());assertSame(host,b.module.r31WorldHost());assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());
                assertEquals(lives,host.globals().ring().lives());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());
                if(pending!=null){TestSonicCdR31aOrdinaryRoute.verifyReturnedNext(b,held,pending);pending=null;}
                var afterPool=placement.capture().pool();
                if(beforeCapture==0&&host.amyCapturedByte().orElseThrow()==0xFF){
                    var amy=beforePool.slots().stream().filter(s->s.id()==0x32).findFirst().orElseThrow();var metal=beforePool.slots().stream().filter(s->s.id()==0x31).findFirst().orElseThrow();
                    amyWhole=amy.identity().wholeIndex();assertTrue(amyWhole<metal.identity().wholeIndex(),"Amy already ran before the actual Metal completion in this physical pass");
                    assertEquals(0x32,afterPool.slots().stream().filter(s->s.identity().wholeIndex()==amy.identity().wholeIndex()).findFirst().orElseThrow().id());
                    assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31));firstCapture=n;firstCaptureNormal=core.state().normalPalette();assertNotSame(beforeNormal,firstCaptureNormal);assertArrayEquals(capturePalette.copy(),firstCaptureNormal.slice(32,32).copy());
                }else if(firstCapture>=0&&retired<0){
                    assertEquals(firstCapture+1,n,"Next original physical pass must retire the earlier Amy parent");assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32));retired=n;
                    assertEquals(0xFF,host.amyCapturedByte().orElseThrow());assertNotSame(firstCaptureNormal,core.state().normalPalette());assertArrayEquals(capturePalette.copy(),core.state().normalPalette().slice(32,32).copy());
                    documents.put("native-intro-transitions.txt","First FF="+firstCapture+"; earlier Amy whole="+amyWhole+" remained live. Next retirement="+retired+". Both complete normal128 references are distinct and source palette row32..64 equals StagePalette[0..32]. Main palette cycling also allocates buffers; these public closed observations alone cannot isolate callback allocation identity.\n");
                }
                intro.returned(f.state());assertFalse(intro.rejected);
                m=f.state().motion();x=m.centreX16_16()>>>16;y=m.centreY16_16()>>>16;
                if(entered<0&&turned>=0&&x>1088&&y-m.yRadius()>=976){entered=n;picture(b,n,"original-lower-passage-entered");pending=TestSonicCdR31aOrdinaryRoute.comparison(b);}
                if(boosted<0&&entered>=0&&(m.flags()&4)!=0&&m.xVelocity()>=0xA00){
                    assertEquals(0,beforeMotion.flags()&4);assertEquals(0xA00,m.xVelocity());assertEquals(0xA00,m.groundVelocity());assertEquals(7,m.xRadius());assertEquals(14,m.yRadius());
                    final int actualX=x,actualY=y;assertTrue(afterPool.slots().stream().anyMatch(s->s.id()==0x35&&s.subtype()==0&&((actualX-s.centreX()+0x28)&0xFFFF)<0x50&&((actualY-s.centreY()+0x28)&0xFFFF)<0x50));
                    boosted=n;picture(b,n,"original-roll-tunnel-speed");pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                if(postInitialised<0&&boosted>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x19&&s.subtype()==9&&s.routine()==2)){
                    postInitialised=n;documents.put("time-post-original-init-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                last=TestSonicCdR31aOrdinaryRoute.cut(b,n);
                // New SOURCE-only positive obligation after the genuine next0A read.
                // The controller expression/recipe above is byte-exact; no diagnostic is input.
                // Native StageObjects has unsaved0690/0497/0A/subtype0, independent of observed whole49.
                if(postInitialised>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520)){
                    springInitialised=n;break; // No new picture, retry, later endpoint or warp bypass.
                }
            }
            String partial=frontier==null?"none":frontier+"\n"+Arrays.toString(frontier.getStackTrace())+"\nState8="+f.state()+"\nEnvironment="+f.environment()+"\nPool="+placement.capture().pool().slots().stream().map(s->s.identity().wholeIndex()+":"+HexFormat.of().formatHex(s.copyBytes())).toList();
            documents.put("lower-route-result.txt","Existing unchanged recipe; SOURCE-ONLY Time Post continuation: qualified intro prefix; left off upper lip; right after actual head clears ceiling. returned="+returned+" attempted="+(frontier==null?"none":returned+1)+" introClosed="+closedIntro+" turned="+turned+" entered="+entered+" boosted="+boosted+" postInit="+postInitialised+" springInit="+springInitialised+"\nLAST_CLOSED\n"+last+"\nFIRST_UNOWNED_PARTIAL(no closed snapshot/picture/retry)\n"+partial);
            assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertTrue(turned>closedIntro);assertTrue(entered>turned,"Require genuine passage traversal before classifying a later frontier");assertTrue(boosted>entered);
            assertTrue(postInitialised>boosted,"Positive original Time Post Init closure is required before any later frontier; SOURCE-ONLY UNRUN, no endpoint claimed");
            assertTrue(springInitialised>postInitialised,"Actual controller must return the genuine next placed Spring Init; SOURCE-ONLY UNRUN, no completed1131 claim");
            if(frontier!=null){assertTrue(f.closed());documents.put("next-owner-source-only-observation.txt",partial);}
            else assertTrue(f.state().motion().routine()<6,"Returned death stops this recipe; never extend reload cycles as route progress");
        }finally{documents.put("lower-route.csv",csv.toString());}
    }
    // BEGIN appended after-Spring diagnostic observer, source-only 20261108.
    /** Same qualified controller/physical ingress; at most3000 original loading+ordinary+replay
     * calls. No new endpoint is known until Root executes this sole observer. */
    @Test void observeOriginalControllerAfterReturnedSpringToFirstStop()throws Exception{
        // Optional exclusive output leaf; existing cleanup alone writes documents/PNG after GL/session retirement.
        String output=System.getProperty("soniccd.r31.lower.evidence.dir"),capture=System.getProperty("soniccd.r31.lower.capture.dir");
        assertTrue(capture==null||output!=null,"Optional Spring PNG requires the same exclusive observer evidence directory");
        if(output!=null){
            Path e=Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD").toRealPath();
            Path dest=afterSpringOutputPath(output,e);
            if(capture!=null){assertEquals(dest,afterSpringOutputPath(capture,e));Path root=Path.of(Objects.requireNonNull(System.getenv("SONICCD_ENGINE_CAPTURE_ROOT"))).toRealPath();assertEquals(e.resolve("captures/engine").toRealPath(),root);assertTrue(dest.startsWith(root)&&!dest.equals(root));}
            Files.createDirectory(dest); // exclusive creation; an existing leaf rejects, never reused/overwritten
        }
        int loadingReturned=0,attempted=0,controllerAttempts=0,replayReturned=0;boolean returnedDeath=false;String springRow="not reached";
        var heldRows=new StringBuilder("recipeOrdinal,requestedHeldNative,controllerAttemptIncludingLoadingAndReplays\n");
        var csv=new StringBuilder(TestSonicCdR31aOrdinaryRoute.header());int returned=0,closedIntro=-1,firstCapture=-1,retired=-1,amyWhole=-1,turned=-1,entered=-1,boosted=-1,postInitialised=-1,springInitialised=-1;Throwable frontier=null;boolean backtracking=false;String last="";CdAssetBytes firstCaptureNormal=null;
        try(var b=fixture.start(1,0x80)){
            // Same original ready helper sequence (right8, native phase, bound256), counted explicitly.
            while(loadingReturned<256&&b.module.r31LoadingVisit().state().phase()!=SonicCdR31aLoadingVisit.Phase.READY){TestSonicCdR31aSelectedProfileLoading.step(b,8);loadingReturned++;controllerAttempts++;}
            assertEquals(SonicCdR31aLoadingVisit.Phase.READY,b.module.r31LoadingVisit().state().phase());var host=b.module.r31WorldHost();var core=b.module.r31LoadingVisit();var f=b.module.requirePlayerFrame();var placement=host.placement();var source=f.stageSource();
            long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();int lives=host.globals().ring().lives();var intro=new TestSonicCdR31aOrdinaryRoute.FirstWallJump();
            var capturePalette=core.assets().view(CdR31aAssets.Asset.PAL_LEVEL).slice(0,32);
            var terrain=host.currentTerrain();int flags=f.state().motion().spriteFlags();assertEquals(0x4003,word(terrain,1056,960,flags));assertEquals(0x2003,word(terrain,1056,1024,flags));
            // Complete original16-pixel side profile, not a guessed visual opening.
            assertEquals(81,terrain.collision().idForBlock(3));for(int i=0;i<16;i++)assertEquals(16,terrain.collision().width(81,i));
            last=TestSonicCdR31aOrdinaryRoute.cut(b,0);csv.append(TestSonicCdR31aOrdinaryRoute.row(b,0));TestSonicCdR31aOrdinaryRoute.ClosedComparison pending=null;
            for(int n=1;n<=3000;n++){
                // verifyReturnedNext has exactly two original replay calls; reserve them BEFORE a next step.
                if(controllerAttempts+1+(pending==null?0:2)>3000)break;
                var m=f.state().motion();int x=m.centreX16_16()>>>16,y=m.centreY16_16()>>>16;
                if(closedIntro<0&&host.amyCapturedByte().orElseThrow()==0xFF&&placement.capture().pool().slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32)){
                    closedIntro=returned;backtracking=true;assertEquals(2,m.routine());assertEquals(0,m.flags()&2);
                }
                if(backtracking&&y-m.yRadius()>=976){backtracking=false;turned=returned;documents.put("head-below-ceiling.txt",last);}
                int held=closedIntro<0?intro.input(b):returned==closedIntro?8:backtracking?4:8;
                var beforePool=placement.capture().pool();var beforeNormal=core.state().normalPalette();int beforeCapture=host.amyCapturedByte().orElseThrow();var beforeMotion=m;
                attempted=n;controllerAttempts++;heldRows.append(n+","+held+","+controllerAttempts+"\n");
                try{TestSonicCdR31aOrdinaryRoute.step(b,held);}catch(UnsupportedOperationException reached){frontier=reached;pending=null;break;}
                returned=n;csv.append(TestSonicCdR31aOrdinaryRoute.row(b,n));
                // First returned death is terminal for this recipe. Preserve only its actual
                // diagnostic; do not capture/draw/restore/replay a pending milestone afterward.
                if(f.state().motion().routine()>=6){returnedDeath=true;pending=null;last=TestSonicCdR31aOrdinaryRoute.cut(b,n);documents.put("first-returned-death.txt",last);break;}
                assertSame(f,b.module.requirePlayerFrame());assertSame(source,f.stageSource());assertSame(core,b.module.r31LoadingVisit());assertSame(host,b.module.r31WorldHost());assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());
                if(springInitialised<0)assertEquals(lives,host.globals().ring().lives());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());
                if(pending!=null){TestSonicCdR31aOrdinaryRoute.verifyReturnedNext(b,held,pending);controllerAttempts+=2;replayReturned+=2;pending=null;}
                var afterPool=placement.capture().pool();
                if(beforeCapture==0&&host.amyCapturedByte().orElseThrow()==0xFF){
                    var amy=beforePool.slots().stream().filter(s->s.id()==0x32).findFirst().orElseThrow();var metal=beforePool.slots().stream().filter(s->s.id()==0x31).findFirst().orElseThrow();
                    amyWhole=amy.identity().wholeIndex();assertTrue(amyWhole<metal.identity().wholeIndex(),"Amy already ran before the actual Metal completion in this physical pass");
                    assertEquals(0x32,afterPool.slots().stream().filter(s->s.identity().wholeIndex()==amy.identity().wholeIndex()).findFirst().orElseThrow().id());
                    assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31));firstCapture=n;firstCaptureNormal=core.state().normalPalette();assertNotSame(beforeNormal,firstCaptureNormal);assertArrayEquals(capturePalette.copy(),firstCaptureNormal.slice(32,32).copy());
                }else if(firstCapture>=0&&retired<0){
                    assertEquals(firstCapture+1,n,"Next original physical pass must retire the earlier Amy parent");assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32));retired=n;
                    assertEquals(0xFF,host.amyCapturedByte().orElseThrow());assertNotSame(firstCaptureNormal,core.state().normalPalette());assertArrayEquals(capturePalette.copy(),core.state().normalPalette().slice(32,32).copy());
                    documents.put("native-intro-transitions.txt","First FF="+firstCapture+"; earlier Amy whole="+amyWhole+" remained live. Next retirement="+retired+". Both complete normal128 references are distinct and source palette row32..64 equals StagePalette[0..32]. Main palette cycling also allocates buffers; these public closed observations alone cannot isolate callback allocation identity.\n");
                }
                intro.returned(f.state());assertFalse(intro.rejected);
                m=f.state().motion();x=m.centreX16_16()>>>16;y=m.centreY16_16()>>>16;
                if(entered<0&&turned>=0&&x>1088&&y-m.yRadius()>=976){entered=n;documents.put("after-spring-passage-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);}
                if(boosted<0&&entered>=0&&(m.flags()&4)!=0&&m.xVelocity()>=0xA00){
                    assertEquals(0,beforeMotion.flags()&4);assertEquals(0xA00,m.xVelocity());assertEquals(0xA00,m.groundVelocity());assertEquals(7,m.xRadius());assertEquals(14,m.yRadius());
                    final int actualX=x,actualY=y;assertTrue(afterPool.slots().stream().anyMatch(s->s.id()==0x35&&s.subtype()==0&&((actualX-s.centreX()+0x28)&0xFFFF)<0x50&&((actualY-s.centreY()+0x28)&0xFFFF)<0x50));
                    boosted=n;documents.put("after-spring-roll-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                if(postInitialised<0&&boosted>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x19&&s.subtype()==9&&s.routine()==2)){
                    postInitialised=n;documents.put("time-post-original-init-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                last=TestSonicCdR31aOrdinaryRoute.cut(b,n);
                // New SOURCE-only positive obligation after the genuine next0A read.
                // The controller expression/recipe above is byte-exact; no diagnostic is input.
                // Native StageObjects has unsaved0690/0497/0A/subtype0, independent of observed whole49.
                if(springInitialised<0&&postInitialised>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520)){
                    assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertTrue(turned>closedIntro);assertTrue(entered>turned);assertTrue(boosted>entered);assertTrue(postInitialised>boosted);assertEquals(2,intro.jumps,"Retain the original two-jump recipe");
                    var spring=afterPool.slots().stream().filter(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520).findFirst().orElseThrow();
                    var beforeSpring=beforePool.slots().stream().filter(s->s.identity().wholeIndex()==spring.identity().wholeIndex()).findFirst().orElseThrow();
                    assertTrue(beforeSpring.id()==0||(beforeSpring.id()==0x0A&&beforeSpring.routine()==0),"Prior closed slot is empty or the not-yet-initialised canonical Spring; do not invent an intermediate birth");if(beforeSpring.id()==0x0A){assertEquals(0x690,beforeSpring.centreX());assertEquals(0x497,beforeSpring.centreY());}assertEquals(0,spring.subtype());
                    springInitialised=n;assertTrue(springInitialised>postInitialised);
                    springRow="ACTUAL HEALTHY RETURNED Spring Init; recipeOrdinal="+n+" heldNative="+held+" whole="+spring.identity().wholeIndex()+" spriteFlags="+spring.spriteFlags()+" flags="+spring.flags()+" savedFlagsId(raw)="+spring.savedFlagsId()+" subtype="+spring.subtype()+"\nBefore64="+HexFormat.of().formatHex(beforeSpring.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(spring.copyBytes())+"\n"+last;
                    documents.put("after-spring-healthy-init.txt",springRow);
                    if(capture!=null){var pictureBefore=TestSonicCdR31aSelectedProfileLoading.values(b);picture(b,n,"healthy-returned-spring-init-old-selected-cpu");assertEquals(pictureBefore,TestSonicCdR31aSelectedProfileLoading.values(b),"Selected renderer is observation only");documents.put("after-spring-picture-boundary.txt","ENGINE picture selected at healthy returned Spring Init. OLD selected CPU/current-art/cameras may predate returned State8; no visible Spring, parity or current-primary-picture assertion.\n"+springRow);}
                    // Continue only this same ordinary returned stack/right8. No pending Spring receipt or warp bypass.
                }
            }
            String outcome=frontier!=null?"UNOWNED":returnedDeath?"RETURNED_DEATH":"BOUND_REACHED";
            String partial=frontier==null?"none":afterSpringException(frontier)+"\n"+Arrays.toString(frontier.getStackTrace())+"\nPARTIAL COMPONENT DIAGNOSTICS ONLY (not closed World/selected output)\nState8="+f.state()+"\nEnvironment="+f.environment()+"\nCorePhase="+core.state().phase()+"\nCoreCalls="+core.state().calls()+"\nPool="+placement.capture().pool().slots().stream().map(s->s.identity().wholeIndex()+":"+HexFormat.of().formatHex(s.copyBytes())).toList();
            int completedCalls=loadingReturned+returned+replayReturned;
            documents.put("after-spring-observation.txt","DIAGNOSTIC OBSERVER, NOT ROUTE SUCCESS. Constructed historical-save CCZ slot1/platform80; not natural PPZ-to-CCZ.\noutcome="+outcome+" loadingReturned="+loadingReturned+" recipeReturned="+returned+" replayReturned="+replayReturned+" completedControllerCalls="+completedCalls+" attemptedRecipeOrdinal="+attempted+" controllerAttemptsIncludingLoadingAndReplays="+controllerAttempts+" introClosed="+closedIntro+" turned="+turned+" entered="+entered+" boosted="+boosted+" postInit="+postInitialised+" springInit="+springInitialised+"\nA passed observer certifies these assertions only; an UNOWNED attempted service did not return.\nLAST_ACTUALLY_RETURNED_CLOSED_ROW (may be returned death)\n"+last+"\nHEALTHY_SPRING_MILESTONE\n"+springRow+"\nFIRST_UNOWNED_PARTIAL(no closed World capture/restore/picture/retry)\n"+partial);
            if(frontier!=null&&!(firstCapture>0&&retired==firstCapture+1&&closedIntro==retired&&turned>closedIntro&&entered>turned&&boosted>entered&&postInitialised>boosted&&springInitialised>postInitialised&&intro.jumps==2))throw (UnsupportedOperationException)frontier; // original object, never an early unsupported positive
            assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertTrue(turned>closedIntro);assertTrue(entered>turned,"Positive lower-passage closure before any later boundary");assertTrue(boosted>entered);assertTrue(postInitialised>boosted);assertTrue(springInitialised>postInitialised,"Early unsupported/death/bound is not a positive Spring prefix");assertEquals(2,intro.jumps);
            assertTrue(controllerAttempts<=3000);assertEquals(completedCalls+(frontier==null?0:1),controllerAttempts);
            if(frontier!=null){assertTrue(f.closed());documents.put("after-spring-first-unowned.txt",partial);}
            else if(returnedDeath)assertTrue(f.state().motion().routine()>=6,"Stop on the first returned death, before any pending replay");
            else{assertFalse(f.closed());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());assertTrue(f.state().motion().routine()<6);assertTrue(controllerAttempts+1>3000,"The only healthy no-boundary exit exhausts the total controller budget");}

        }finally{documents.put("after-spring-route.csv",csv.toString());documents.put("after-spring-held-inputs.csv",heldRows.toString());}
    }
    /** Both evidence and capture must already name the canonical, normalized leaf used by cleanup. */
    private static Path afterSpringOutputPath(String raw,Path boundary)throws java.io.IOException{
        Path requested=Path.of(raw);assertTrue(requested.isAbsolute());
        assertEquals(requested,requested.normalize(),"Output path must already be normalized; aliases cannot select another export leaf");
        Path parent=requested.getParent().toRealPath();assertTrue(parent.startsWith(boundary)&&!requested.equals(boundary));
        assertEquals(parent,requested.getParent(),"Canonical parent, no symlink/traversal output alias");
        return requested;
    }
    private static String afterSpringException(Throwable original){
        String boundary="not advertised",whole="not advertised",value="not advertised";
        if(original instanceof SonicCdR31aObjectPass.Unowned u){boundary=u.boundary().name();whole=Integer.toString(u.wholeIndex());value=u.value()+" (0x"+Integer.toHexString(u.value())+")";}
        else if(original instanceof SonicCdR31aLoadingVisit.UnsupportedLoading u)boundary=u.boundary().name();
        else if(original instanceof CdWorld.Unowned u)boundary=u.boundary().name();
        else if(original instanceof CdWorld.Stopped u)boundary=u.boundary().name();
        else if(original instanceof com.openggf.game.soniccd.player.CdPlayerFrameRuntime.UnsupportedFrame u)boundary=u.boundary().name();
        else if(original instanceof com.openggf.game.soniccd.player.CdPlayerBody.UnsupportedBody u)boundary=u.boundary().name();
        else if(original instanceof com.openggf.game.soniccd.player.CdPlayerMovement.UnsupportedMotion u)boundary=u.boundary().name();
        else if(original instanceof com.openggf.game.soniccd.player.CdPlayerBody.UnsupportedTouchHandler u){boundary="touch kind "+u.kind().name();whole=Integer.toString(u.selectedSlot().identity().wholeIndex());value="selectedSlot.id="+u.selectedSlot().id();}
        return "OriginalThrowableClass="+original.getClass().getName()+" identityHashCode="+System.identityHashCode(original)+" boundary="+boundary+" whole="+whole+" value="+value+"\nOriginal="+original+"\nCause="+original.getCause()+"\nSuppressed="+Arrays.toString(original.getSuppressed());
    }
    // END appended after-Spring diagnostic observer.

    // BEGIN appended positive Tentou continuation observer 20261108. SOURCE ONLY, all future execution UNRUN.
    // Same exact controller recipe; no new endpoint, placement/actor/state construction or input search.
    @Test void observeOriginalControllerAfterReturnedTentouToFirstStop()throws Exception{
        // Optional exclusive output leaf; existing cleanup alone writes documents/PNG after GL/session retirement.
        String output=System.getProperty("soniccd.r31.lower.evidence.dir"),capture=System.getProperty("soniccd.r31.lower.capture.dir");
        assertTrue(capture==null||output!=null,"Optional Spring PNG requires the same exclusive observer evidence directory");
        if(output!=null){
            Path e=Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD").toRealPath();
            Path dest=afterSpringOutputPath(output,e);
            if(capture!=null){assertEquals(dest,afterSpringOutputPath(capture,e));Path root=Path.of(Objects.requireNonNull(System.getenv("SONICCD_ENGINE_CAPTURE_ROOT"))).toRealPath();assertEquals(e.resolve("captures/engine").toRealPath(),root);assertTrue(dest.startsWith(root)&&!dest.equals(root));}
            Files.createDirectory(dest); // exclusive creation; an existing leaf rejects, never reused/overwritten
        }
        int loadingReturned=0,attempted=0,controllerAttempts=0,replayReturned=0;boolean returnedDeath=false;String springRow="not reached";
        var heldRows=new StringBuilder("recipeOrdinal,requestedHeldNative,controllerAttemptIncludingLoadingAndReplays\n");
        var csv=new StringBuilder(TestSonicCdR31aOrdinaryRoute.header());int returned=0,closedIntro=-1,firstCapture=-1,retired=-1,amyWhole=-1,turned=-1,entered=-1,boosted=-1,postInitialised=-1,springInitialised=-1;Throwable frontier=null;boolean backtracking=false,tentouReplayed=false;int tentouInitialised=-1,tentouWhole=-1;String tentouRow="not reached";String last="";CdAssetBytes firstCaptureNormal=null;
        try(var b=fixture.start(1,0x80)){
            // Same original ready helper sequence (right8, native phase, bound256), counted explicitly.
            while(loadingReturned<256&&b.module.r31LoadingVisit().state().phase()!=SonicCdR31aLoadingVisit.Phase.READY){TestSonicCdR31aSelectedProfileLoading.step(b,8);loadingReturned++;controllerAttempts++;}
            assertEquals(SonicCdR31aLoadingVisit.Phase.READY,b.module.r31LoadingVisit().state().phase());var host=b.module.r31WorldHost();var core=b.module.r31LoadingVisit();var f=b.module.requirePlayerFrame();var placement=host.placement();var source=f.stageSource();
            long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();int lives=host.globals().ring().lives();var intro=new TestSonicCdR31aOrdinaryRoute.FirstWallJump();
            var capturePalette=core.assets().view(CdR31aAssets.Asset.PAL_LEVEL).slice(0,32);
            var terrain=host.currentTerrain();int flags=f.state().motion().spriteFlags();assertEquals(0x4003,word(terrain,1056,960,flags));assertEquals(0x2003,word(terrain,1056,1024,flags));
            // Complete original16-pixel side profile, not a guessed visual opening.
            assertEquals(81,terrain.collision().idForBlock(3));for(int i=0;i<16;i++)assertEquals(16,terrain.collision().width(81,i));
            last=TestSonicCdR31aOrdinaryRoute.cut(b,0);csv.append(TestSonicCdR31aOrdinaryRoute.row(b,0));TestSonicCdR31aOrdinaryRoute.ClosedComparison pending=null;
            for(int n=1;n<=3000;n++){
                // verifyReturnedNext has exactly two original replay calls; reserve them BEFORE a next step.
                if(controllerAttempts+1+(pending==null?0:2)>3000)break;
                var m=f.state().motion();int x=m.centreX16_16()>>>16,y=m.centreY16_16()>>>16;
                if(closedIntro<0&&host.amyCapturedByte().orElseThrow()==0xFF&&placement.capture().pool().slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32)){
                    closedIntro=returned;backtracking=true;assertEquals(2,m.routine());assertEquals(0,m.flags()&2);
                }
                if(backtracking&&y-m.yRadius()>=976){backtracking=false;turned=returned;documents.put("head-below-ceiling.txt",last);}
                int held=closedIntro<0?intro.input(b):returned==closedIntro?8:backtracking?4:8;
                var beforePool=placement.capture().pool();var beforeNormal=core.state().normalPalette();int beforeCapture=host.amyCapturedByte().orElseThrow();var beforeMotion=m;
                attempted=n;controllerAttempts++;heldRows.append(n+","+held+","+controllerAttempts+"\n");
                try{TestSonicCdR31aOrdinaryRoute.step(b,held);}catch(UnsupportedOperationException reached){frontier=reached;pending=null;break;}
                returned=n;csv.append(TestSonicCdR31aOrdinaryRoute.row(b,n));
                // First returned death is terminal for this recipe. Preserve only its actual
                // diagnostic; do not capture/draw/restore/replay a pending milestone afterward.
                if(f.state().motion().routine()>=6){returnedDeath=true;pending=null;last=TestSonicCdR31aOrdinaryRoute.cut(b,n);documents.put("first-returned-death.txt",last);break;}
                assertSame(f,b.module.requirePlayerFrame());assertSame(source,f.stageSource());assertSame(core,b.module.r31LoadingVisit());assertSame(host,b.module.r31WorldHost());assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());
                if(springInitialised<0)assertEquals(lives,host.globals().ring().lives());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());
                if(pending!=null){TestSonicCdR31aOrdinaryRoute.verifyReturnedNext(b,held,pending);controllerAttempts+=2;replayReturned+=2;if(tentouInitialised>=0)tentouReplayed=true;pending=null;}
                var afterPool=placement.capture().pool();
                if(beforeCapture==0&&host.amyCapturedByte().orElseThrow()==0xFF){
                    var amy=beforePool.slots().stream().filter(s->s.id()==0x32).findFirst().orElseThrow();var metal=beforePool.slots().stream().filter(s->s.id()==0x31).findFirst().orElseThrow();
                    amyWhole=amy.identity().wholeIndex();assertTrue(amyWhole<metal.identity().wholeIndex(),"Amy already ran before the actual Metal completion in this physical pass");
                    assertEquals(0x32,afterPool.slots().stream().filter(s->s.identity().wholeIndex()==amy.identity().wholeIndex()).findFirst().orElseThrow().id());
                    assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31));firstCapture=n;firstCaptureNormal=core.state().normalPalette();assertNotSame(beforeNormal,firstCaptureNormal);assertArrayEquals(capturePalette.copy(),firstCaptureNormal.slice(32,32).copy());
                }else if(firstCapture>=0&&retired<0){
                    assertEquals(firstCapture+1,n,"Next original physical pass must retire the earlier Amy parent");assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32));retired=n;
                    assertEquals(0xFF,host.amyCapturedByte().orElseThrow());assertNotSame(firstCaptureNormal,core.state().normalPalette());assertArrayEquals(capturePalette.copy(),core.state().normalPalette().slice(32,32).copy());
                    documents.put("native-intro-transitions.txt","First FF="+firstCapture+"; earlier Amy whole="+amyWhole+" remained live. Next retirement="+retired+". Both complete normal128 references are distinct and source palette row32..64 equals StagePalette[0..32]. Main palette cycling also allocates buffers; these public closed observations alone cannot isolate callback allocation identity.\n");
                }
                intro.returned(f.state());assertFalse(intro.rejected);
                m=f.state().motion();x=m.centreX16_16()>>>16;y=m.centreY16_16()>>>16;
                if(entered<0&&turned>=0&&x>1088&&y-m.yRadius()>=976){entered=n;documents.put("after-tentou-passage-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);}
                if(boosted<0&&entered>=0&&(m.flags()&4)!=0&&m.xVelocity()>=0xA00){
                    assertEquals(0,beforeMotion.flags()&4);assertEquals(0xA00,m.xVelocity());assertEquals(0xA00,m.groundVelocity());assertEquals(7,m.xRadius());assertEquals(14,m.yRadius());
                    final int actualX=x,actualY=y;assertTrue(afterPool.slots().stream().anyMatch(s->s.id()==0x35&&s.subtype()==0&&((actualX-s.centreX()+0x28)&0xFFFF)<0x50&&((actualY-s.centreY()+0x28)&0xFFFF)<0x50));
                    boosted=n;documents.put("after-tentou-roll-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                if(postInitialised<0&&boosted>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x19&&s.subtype()==9&&s.routine()==2)){
                    postInitialised=n;documents.put("time-post-original-init-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                last=TestSonicCdR31aOrdinaryRoute.cut(b,n);
                // New SOURCE-only positive obligation after the genuine next0A read.
                // The controller expression/recipe above is byte-exact; no diagnostic is input.
                // Native StageObjects has unsaved0690/0497/0A/subtype0, independent of observed whole49.
                if(springInitialised<0&&postInitialised>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520)){
                    assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertTrue(turned>closedIntro);assertTrue(entered>turned);assertTrue(boosted>entered);assertTrue(postInitialised>boosted);assertEquals(2,intro.jumps,"Retain the original two-jump recipe");
                    var spring=afterPool.slots().stream().filter(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520).findFirst().orElseThrow();
                    var beforeSpring=beforePool.slots().stream().filter(s->s.identity().wholeIndex()==spring.identity().wholeIndex()).findFirst().orElseThrow();
                    assertTrue(beforeSpring.id()==0||(beforeSpring.id()==0x0A&&beforeSpring.routine()==0),"Prior closed slot is empty or the not-yet-initialised canonical Spring; do not invent an intermediate birth");if(beforeSpring.id()==0x0A){assertEquals(0x690,beforeSpring.centreX());assertEquals(0x497,beforeSpring.centreY());}assertEquals(0,spring.subtype());
                    springInitialised=n;assertTrue(springInitialised>postInitialised);
                    springRow="ACTUAL HEALTHY RETURNED Spring Init; recipeOrdinal="+n+" heldNative="+held+" whole="+spring.identity().wholeIndex()+" spriteFlags="+spring.spriteFlags()+" flags="+spring.flags()+" savedFlagsId(raw)="+spring.savedFlagsId()+" subtype="+spring.subtype()+"\nBefore64="+HexFormat.of().formatHex(beforeSpring.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(spring.copyBytes())+"\n"+last;
                    documents.put("after-tentou-healthy-init.txt",springRow);
                    if(capture!=null){var pictureBefore=TestSonicCdR31aSelectedProfileLoading.values(b);picture(b,n,"healthy-returned-spring-init-old-selected-cpu");assertEquals(pictureBefore,TestSonicCdR31aSelectedProfileLoading.values(b),"Selected renderer is observation only");documents.put("after-tentou-picture-boundary.txt","ENGINE picture selected at healthy returned Spring Init. OLD selected CPU/current-art/cameras may predate returned State8; no visible Spring, parity or current-primary-picture assertion.\n"+springRow);}
                    // Continue only this same ordinary returned stack/right8. No pending Spring receipt or warp bypass.
                }
                if(tentouInitialised<0&&springInitialised>=0){
                    var selected=afterPool.slots().stream().filter(s->s.id()==0x2E&&(byte)(s.subtype()-1)<0
                        &&(s.routine()==2||s.routine()==4||s.routine()==6)&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0xA46A
                        &&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==(s.subtype()<128?0x20BBC2:0x20BB50)
                        &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.PRIORITY)==1
                        &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.COLLISION_TYPE)==6
                        &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.WIDTH)==14&&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.Y_RADIUS)==14).findFirst();
                    if(selected.isPresent()){
                        var t=selected.orElseThrow();var prior=beforePool.slots().stream().filter(s->s.identity().wholeIndex()==t.identity().wholeIndex()).findFirst().orElseThrow();
                        assertTrue(prior.id()==0||prior.id()==0x2E&&prior.routine()==0,"Observe genuine first Init, not an advanced hydrated parent");
                        assertTrue(core.assets().placements().records().stream().anyMatch(r->(r.encodedId()&127)==0x2E&&r.centreX()==t.centreX()&&r.subtype()==t.subtype()),"Actual selected source placement, not whole/ordinal fitting");
                        tentouInitialised=n;tentouWhole=t.identity().wholeIndex();assertTrue(tentouInitialised>springInitialised);
                        tentouRow="ACTUAL HEALTHY RETURNED Tentou Init; recipeOrdinal="+n+" heldNative="+held+" whole="+tentouWhole+" flags="+t.flags()+" spriteFlags="+t.spriteFlags()+" savedFlagsId(raw)="+t.savedFlagsId()+" subtype="+t.subtype()+"\nBefore64="+HexFormat.of().formatHex(prior.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(t.copyBytes())+"\n"+last;
                        documents.put("tentou-original-init-closed.txt",tentouRow);pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                    }
                }
            }
            String outcome=frontier!=null?"UNOWNED":returnedDeath?"RETURNED_DEATH":"BOUND_REACHED";
            String partial=frontier==null?"none":afterSpringException(frontier)+"\n"+Arrays.toString(frontier.getStackTrace())+"\nPARTIAL COMPONENT DIAGNOSTICS ONLY (not closed World/selected output)\nState8="+f.state()+"\nEnvironment="+f.environment()+"\nCorePhase="+core.state().phase()+"\nCoreCalls="+core.state().calls()+"\nPool="+placement.capture().pool().slots().stream().map(s->s.identity().wholeIndex()+":"+HexFormat.of().formatHex(s.copyBytes())).toList();
            int completedCalls=loadingReturned+returned+replayReturned;
            documents.put("after-tentou-observation.txt","DIAGNOSTIC OBSERVER, NOT ROUTE SUCCESS. Constructed historical-save CCZ slot1/platform80; not natural PPZ-to-CCZ.\noutcome="+outcome+" loadingReturned="+loadingReturned+" recipeReturned="+returned+" replayReturned="+replayReturned+" completedControllerCalls="+completedCalls+" attemptedRecipeOrdinal="+attempted+" controllerAttemptsIncludingLoadingAndReplays="+controllerAttempts+" introClosed="+closedIntro+" turned="+turned+" entered="+entered+" boosted="+boosted+" postInit="+postInitialised+" springInit="+springInitialised+"\nA passed observer certifies these assertions only; an UNOWNED attempted service did not return.\nLAST_ACTUALLY_RETURNED_CLOSED_ROW (may be returned death)\n"+last+"\nHEALTHY_SPRING_MILESTONE\n"+springRow+"\nFIRST_UNOWNED_PARTIAL(no closed World capture/restore/picture/retry)\n"+partial);
            if(frontier!=null&&!(firstCapture>0&&retired==firstCapture+1&&closedIntro==retired&&turned>closedIntro&&entered>turned&&boosted>entered&&postInitialised>boosted&&springInitialised>postInitialised&&tentouInitialised>springInitialised&&tentouReplayed&&intro.jumps==2))throw (UnsupportedOperationException)frontier; // original object, never an early unsupported positive
            assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertTrue(turned>closedIntro);assertTrue(entered>turned,"Positive lower-passage closure before any later boundary");assertTrue(boosted>entered);assertTrue(postInitialised>boosted);assertTrue(springInitialised>postInitialised,"Early unsupported/death/bound is not a positive Spring prefix");assertEquals(2,intro.jumps);
            assertTrue(tentouInitialised>springInitialised,"Early unsupported/death/bound is NOT positive Tentou closure");assertTrue(tentouReplayed,"Same original lifetime FULL/course next-state replay must actually return before later classification");
            documents.put("tentou-closure.txt",tentouRow+"\nFULL/course next-state replay returned="+tentouReplayed);
            assertTrue(controllerAttempts<=3000);assertEquals(completedCalls+(frontier==null?0:1),controllerAttempts);
            if(frontier!=null){assertTrue(f.closed());documents.put("after-tentou-first-unowned.txt",partial);}
            else if(returnedDeath)assertTrue(f.state().motion().routine()>=6,"Stop on the first returned death, before any pending replay");
            else{assertFalse(f.closed());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());assertTrue(f.state().motion().routine()<6);assertTrue(controllerAttempts+1>3000,"The only healthy no-boundary exit exhausts the total controller budget");}

        }finally{documents.put("after-tentou-route.csv",csv.toString());documents.put("after-tentou-held-inputs.csv",heldRows.toString());}
    }
    // END appended positive Tentou continuation observer 20261108.
    // BEGIN appended CCAnimal positive presence observer 20261108. SOURCE ONLY / JAVA UNRUN.
    @Test void observeOriginalControllerAfterReturnedCcAnimalToFirstStop()throws Exception{
        // Optional exclusive output leaf; existing cleanup alone writes documents/PNG after GL/session retirement.
        String output=System.getProperty("soniccd.r31.lower.evidence.dir"),capture=System.getProperty("soniccd.r31.lower.capture.dir");
        assertTrue(capture==null||output!=null,"Optional Spring PNG requires the same exclusive observer evidence directory");
        if(output!=null){
            Path e=Path.of(System.getProperty("user.home"),"Downloads","Sonic-CD").toRealPath();
            Path dest=afterSpringOutputPath(output,e);
            if(capture!=null){assertEquals(dest,afterSpringOutputPath(capture,e));Path root=Path.of(Objects.requireNonNull(System.getenv("SONICCD_ENGINE_CAPTURE_ROOT"))).toRealPath();assertEquals(e.resolve("captures/engine").toRealPath(),root);assertTrue(dest.startsWith(root)&&!dest.equals(root));}
            Files.createDirectory(dest); // exclusive creation; an existing leaf rejects, never reused/overwritten
        }
        int loadingReturned=0,attempted=0,controllerAttempts=0,replayReturned=0;boolean returnedDeath=false;String springRow="not reached";
        var heldRows=new StringBuilder("recipeOrdinal,requestedHeldNative,controllerAttemptIncludingLoadingAndReplays\n");
        var csv=new StringBuilder(TestSonicCdR31aOrdinaryRoute.header());int returned=0,closedIntro=-1,firstCapture=-1,retired=-1,amyWhole=-1,turned=-1,entered=-1,boosted=-1,postInitialised=-1,springInitialised=-1;Throwable frontier=null;boolean backtracking=false,tentouReplayed=false;int tentouInitialised=-1,tentouWhole=-1,animalReturned=-1;boolean animalReplayed=false;String animalRow="not reached";String tentouRow="not reached";String last="";CdAssetBytes firstCaptureNormal=null;
        try(var b=fixture.start(1,0x80)){
            // Same original ready helper sequence (right8, native phase, bound256), counted explicitly.
            while(loadingReturned<256&&b.module.r31LoadingVisit().state().phase()!=SonicCdR31aLoadingVisit.Phase.READY){TestSonicCdR31aSelectedProfileLoading.step(b,8);loadingReturned++;controllerAttempts++;}
            assertEquals(SonicCdR31aLoadingVisit.Phase.READY,b.module.r31LoadingVisit().state().phase());var host=b.module.r31WorldHost();var core=b.module.r31LoadingVisit();var f=b.module.requirePlayerFrame();var placement=host.placement();var source=f.stageSource();
            long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();int lives=host.globals().ring().lives();var intro=new TestSonicCdR31aOrdinaryRoute.FirstWallJump();
            var capturePalette=core.assets().view(CdR31aAssets.Asset.PAL_LEVEL).slice(0,32);
            var terrain=host.currentTerrain();int flags=f.state().motion().spriteFlags();assertEquals(0x4003,word(terrain,1056,960,flags));assertEquals(0x2003,word(terrain,1056,1024,flags));
            // Complete original16-pixel side profile, not a guessed visual opening.
            assertEquals(81,terrain.collision().idForBlock(3));for(int i=0;i<16;i++)assertEquals(16,terrain.collision().width(81,i));
            last=TestSonicCdR31aOrdinaryRoute.cut(b,0);csv.append(TestSonicCdR31aOrdinaryRoute.row(b,0));TestSonicCdR31aOrdinaryRoute.ClosedComparison pending=null;
            for(int n=1;n<=3000;n++){
                // verifyReturnedNext has exactly two original replay calls; reserve them BEFORE a next step.
                if(controllerAttempts+1+(pending==null?0:2)>3000)break;
                var m=f.state().motion();int x=m.centreX16_16()>>>16,y=m.centreY16_16()>>>16;
                if(closedIntro<0&&host.amyCapturedByte().orElseThrow()==0xFF&&placement.capture().pool().slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32)){
                    closedIntro=returned;backtracking=true;assertEquals(2,m.routine());assertEquals(0,m.flags()&2);
                }
                if(backtracking&&y-m.yRadius()>=976){backtracking=false;turned=returned;documents.put("head-below-ceiling.txt",last);}
                int held=closedIntro<0?intro.input(b):returned==closedIntro?8:backtracking?4:8;
                var beforePool=placement.capture().pool();var beforeNormal=core.state().normalPalette();int beforeCapture=host.amyCapturedByte().orElseThrow();var beforeMotion=m;
                attempted=n;controllerAttempts++;heldRows.append(n+","+held+","+controllerAttempts+"\n");
                try{TestSonicCdR31aOrdinaryRoute.step(b,held);}catch(UnsupportedOperationException reached){frontier=reached;pending=null;break;}
                returned=n;csv.append(TestSonicCdR31aOrdinaryRoute.row(b,n));
                // First returned death is terminal for this recipe. Preserve only its actual
                // diagnostic; do not capture/draw/restore/replay a pending milestone afterward.
                if(f.state().motion().routine()>=6){returnedDeath=true;pending=null;last=TestSonicCdR31aOrdinaryRoute.cut(b,n);documents.put("first-returned-death.txt",last);break;}
                assertSame(f,b.module.requirePlayerFrame());assertSame(source,f.stageSource());assertSame(core,b.module.r31LoadingVisit());assertSame(host,b.module.r31WorldHost());assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());
                if(springInitialised<0)assertEquals(lives,host.globals().ring().lives());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());
                if(pending!=null){TestSonicCdR31aOrdinaryRoute.verifyReturnedNext(b,held,pending);controllerAttempts+=2;replayReturned+=2;if(tentouInitialised>=0)tentouReplayed=true;if(animalReturned>=0)animalReplayed=true;pending=null;}
                var afterPool=placement.capture().pool();
                if(beforeCapture==0&&host.amyCapturedByte().orElseThrow()==0xFF){
                    var amy=beforePool.slots().stream().filter(s->s.id()==0x32).findFirst().orElseThrow();var metal=beforePool.slots().stream().filter(s->s.id()==0x31).findFirst().orElseThrow();
                    amyWhole=amy.identity().wholeIndex();assertTrue(amyWhole<metal.identity().wholeIndex(),"Amy already ran before the actual Metal completion in this physical pass");
                    assertEquals(0x32,afterPool.slots().stream().filter(s->s.identity().wholeIndex()==amy.identity().wholeIndex()).findFirst().orElseThrow().id());
                    assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31));firstCapture=n;firstCaptureNormal=core.state().normalPalette();assertNotSame(beforeNormal,firstCaptureNormal);assertArrayEquals(capturePalette.copy(),firstCaptureNormal.slice(32,32).copy());
                }else if(firstCapture>=0&&retired<0){
                    assertEquals(firstCapture+1,n,"Next original physical pass must retire the earlier Amy parent");assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32));retired=n;
                    assertEquals(0xFF,host.amyCapturedByte().orElseThrow());assertNotSame(firstCaptureNormal,core.state().normalPalette());assertArrayEquals(capturePalette.copy(),core.state().normalPalette().slice(32,32).copy());
                    documents.put("native-intro-transitions.txt","First FF="+firstCapture+"; earlier Amy whole="+amyWhole+" remained live. Next retirement="+retired+". Both complete normal128 references are distinct and source palette row32..64 equals StagePalette[0..32]. Main palette cycling also allocates buffers; these public closed observations alone cannot isolate callback allocation identity.\n");
                }
                intro.returned(f.state());assertFalse(intro.rejected);
                m=f.state().motion();x=m.centreX16_16()>>>16;y=m.centreY16_16()>>>16;
                if(entered<0&&turned>=0&&x>1088&&y-m.yRadius()>=976){entered=n;documents.put("after-ccanimal-passage-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);}
                if(boosted<0&&entered>=0&&(m.flags()&4)!=0&&m.xVelocity()>=0xA00){
                    assertEquals(0,beforeMotion.flags()&4);assertEquals(0xA00,m.xVelocity());assertEquals(0xA00,m.groundVelocity());assertEquals(7,m.xRadius());assertEquals(14,m.yRadius());
                    final int actualX=x,actualY=y;assertTrue(afterPool.slots().stream().anyMatch(s->s.id()==0x35&&s.subtype()==0&&((actualX-s.centreX()+0x28)&0xFFFF)<0x50&&((actualY-s.centreY()+0x28)&0xFFFF)<0x50));
                    boosted=n;documents.put("after-ccanimal-roll-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                if(postInitialised<0&&boosted>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x19&&s.subtype()==9&&s.routine()==2)){
                    postInitialised=n;documents.put("time-post-original-init-closed.txt",TestSonicCdR31aOrdinaryRoute.cut(b,n));pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                last=TestSonicCdR31aOrdinaryRoute.cut(b,n);
                // New SOURCE-only positive obligation after the genuine next0A read.
                // The controller expression/recipe above is byte-exact; no diagnostic is input.
                // Native StageObjects has unsaved0690/0497/0A/subtype0, independent of observed whole49.
                if(springInitialised<0&&postInitialised>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520)){
                    assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertTrue(turned>closedIntro);assertTrue(entered>turned);assertTrue(boosted>entered);assertTrue(postInitialised>boosted);assertEquals(2,intro.jumps,"Retain the original two-jump recipe");
                    var spring=afterPool.slots().stream().filter(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520).findFirst().orElseThrow();
                    var beforeSpring=beforePool.slots().stream().filter(s->s.identity().wholeIndex()==spring.identity().wholeIndex()).findFirst().orElseThrow();
                    assertTrue(beforeSpring.id()==0||(beforeSpring.id()==0x0A&&beforeSpring.routine()==0),"Prior closed slot is empty or the not-yet-initialised canonical Spring; do not invent an intermediate birth");if(beforeSpring.id()==0x0A){assertEquals(0x690,beforeSpring.centreX());assertEquals(0x497,beforeSpring.centreY());}assertEquals(0,spring.subtype());
                    springInitialised=n;assertTrue(springInitialised>postInitialised);
                    springRow="ACTUAL HEALTHY RETURNED Spring Init; recipeOrdinal="+n+" heldNative="+held+" whole="+spring.identity().wholeIndex()+" spriteFlags="+spring.spriteFlags()+" flags="+spring.flags()+" savedFlagsId(raw)="+spring.savedFlagsId()+" subtype="+spring.subtype()+"\nBefore64="+HexFormat.of().formatHex(beforeSpring.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(spring.copyBytes())+"\n"+last;
                    documents.put("after-ccanimal-healthy-init.txt",springRow);
                    if(capture!=null){var pictureBefore=TestSonicCdR31aSelectedProfileLoading.values(b);picture(b,n,"healthy-returned-spring-init-old-selected-cpu");assertEquals(pictureBefore,TestSonicCdR31aSelectedProfileLoading.values(b),"Selected renderer is observation only");documents.put("after-ccanimal-picture-boundary.txt","ENGINE picture selected at healthy returned Spring Init. OLD selected CPU/current-art/cameras may predate returned State8; no visible Spring, parity or current-primary-picture assertion.\n"+springRow);}
                    // Continue only this same ordinary returned stack/right8. No pending Spring receipt or warp bypass.
                }
                if(tentouInitialised<0&&springInitialised>=0){
                    var selected=afterPool.slots().stream().filter(s->s.id()==0x2E&&(byte)(s.subtype()-1)<0
                        &&(s.routine()==2||s.routine()==4||s.routine()==6)&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0xA46A
                        &&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==(s.subtype()<128?0x20BBC2:0x20BB50)
                        &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.PRIORITY)==1
                        &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.COLLISION_TYPE)==6
                        &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.WIDTH)==14&&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.Y_RADIUS)==14).findFirst();
                    if(selected.isPresent()){
                        var t=selected.orElseThrow();var prior=beforePool.slots().stream().filter(s->s.identity().wholeIndex()==t.identity().wholeIndex()).findFirst().orElseThrow();
                        assertTrue(prior.id()==0||prior.id()==0x2E&&prior.routine()==0,"Observe genuine first Init, not an advanced hydrated parent");
                        assertTrue(core.assets().placements().records().stream().anyMatch(r->(r.encodedId()&127)==0x2E&&r.centreX()==t.centreX()&&r.subtype()==t.subtype()),"Actual selected source placement, not whole/ordinal fitting");
                        tentouInitialised=n;tentouWhole=t.identity().wholeIndex();assertTrue(tentouInitialised>springInitialised);
                        tentouRow="ACTUAL HEALTHY RETURNED Tentou Init; recipeOrdinal="+n+" heldNative="+held+" whole="+tentouWhole+" flags="+t.flags()+" spriteFlags="+t.spriteFlags()+" savedFlagsId(raw)="+t.savedFlagsId()+" subtype="+t.subtype()+"\nBefore64="+HexFormat.of().formatHex(prior.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(t.copyBytes())+"\n"+last;
                        documents.put("tentou-original-init-closed.txt",tentouRow);pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                    }
                }
                if(animalReturned<0&&springInitialised>=0){
                    for(var animal:afterPool.slots())if(animal.id()==0x39&&core.assets().placements().records().stream().anyMatch(r->(r.encodedId()&127)==0x39&&r.subtype()==animal.subtype()&&r.centreX()==animal.centreX()&&(r.packedY()&0xFFF)==animal.centreY())){
                        int rawPeriod=f.environment().movement().gates().timeZone();assertTrue((byte)rawPeriod<2);assertEquals(0,animal.subtype()&128);
                        assertEquals(0,host.globals().projectorByte().orElseThrow(),"Actual selected IPX.GameLoop clear retained through Host; absence is not zero");
                        var prior=beforePool.slots().stream().filter(v->v.identity().wholeIndex()==animal.identity().wholeIndex()).findFirst().orElseThrow();
                        if(prior.id()==0x39)assertArrayEquals(prior.copyBytes(),animal.copyBytes(),"Native projector-zero presence defers Init/Draw and retains dirty bytes");
                        else assertEquals(0,prior.id(),"Actual original source placement birth, no injected animal");
                        animalReturned=n;assertTrue(tentouInitialised>=0);assertTrue(animalReturned>=tentouInitialised);
                        animalRow="ACTUAL RETURNED SOURCE-PLACED CCAnimal presence/deferred-body; no Animal Init/Draw claim. recipeOrdinal="+n+" held="+held+" whole="+animal.identity().wholeIndex()+" rawPeriod="+rawPeriod+" projector="+host.globals().projectorByte()+"\nBefore64="+HexFormat.of().formatHex(prior.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(animal.copyBytes())+"\n"+last;
                        documents.put("ccanimal-presence-closed.txt",animalRow);if(pending==null)pending=TestSonicCdR31aOrdinaryRoute.comparison(b);break;
                    }
                }
            }
            String outcome=frontier!=null?"UNOWNED":returnedDeath?"RETURNED_DEATH":"BOUND_REACHED";
            String partial=frontier==null?"none":afterSpringException(frontier)+"\n"+Arrays.toString(frontier.getStackTrace())+"\nPARTIAL COMPONENT DIAGNOSTICS ONLY (not closed World/selected output)\nState8="+f.state()+"\nEnvironment="+f.environment()+"\nCorePhase="+core.state().phase()+"\nCoreCalls="+core.state().calls()+"\nPool="+placement.capture().pool().slots().stream().map(s->s.identity().wholeIndex()+":"+HexFormat.of().formatHex(s.copyBytes())).toList();
            int completedCalls=loadingReturned+returned+replayReturned;
            documents.put("after-ccanimal-observation.txt","DIAGNOSTIC OBSERVER, NOT ROUTE SUCCESS. Constructed historical-save CCZ slot1/platform80; not natural PPZ-to-CCZ.\noutcome="+outcome+" loadingReturned="+loadingReturned+" recipeReturned="+returned+" replayReturned="+replayReturned+" completedControllerCalls="+completedCalls+" attemptedRecipeOrdinal="+attempted+" controllerAttemptsIncludingLoadingAndReplays="+controllerAttempts+" introClosed="+closedIntro+" turned="+turned+" entered="+entered+" boosted="+boosted+" postInit="+postInitialised+" springInit="+springInitialised+"\nA passed observer certifies these assertions only; an UNOWNED attempted service did not return.\nLAST_ACTUALLY_RETURNED_CLOSED_ROW (may be returned death)\n"+last+"\nHEALTHY_SPRING_MILESTONE\n"+springRow+"\nFIRST_UNOWNED_PARTIAL(no closed World capture/restore/picture/retry)\n"+partial);
            if(frontier!=null&&!(firstCapture>0&&retired==firstCapture+1&&closedIntro==retired&&turned>closedIntro&&entered>turned&&boosted>entered&&postInitialised>boosted&&springInitialised>postInitialised&&tentouInitialised>springInitialised&&tentouReplayed&&animalReturned>=tentouInitialised&&animalReplayed&&intro.jumps==2))throw (UnsupportedOperationException)frontier; // original object, never an early unsupported positive
            assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertTrue(turned>closedIntro);assertTrue(entered>turned,"Positive lower-passage closure before any later boundary");assertTrue(boosted>entered);assertTrue(postInitialised>boosted);assertTrue(springInitialised>postInitialised,"Early unsupported/death/bound is not a positive Spring prefix");assertEquals(2,intro.jumps);
            assertTrue(tentouInitialised>springInitialised,"Early unsupported/death/bound is NOT positive Tentou closure");assertTrue(tentouReplayed,"Same original lifetime FULL/course next-state replay must actually return before later classification");
            documents.put("tentou-closure.txt",tentouRow+"\nFULL/course next-state replay returned="+tentouReplayed);
            assertTrue(animalReturned>=tentouInitialised&&animalReturned>0,"Early unsupported/death is not a returned Animal presence");assertTrue(animalReplayed,"Same-owner FULL/course next-state replay must actually return");documents.put("ccanimal-replay.txt",animalRow+"\nFULL/course replay returned="+animalReplayed);
            assertTrue(controllerAttempts<=3000);assertEquals(completedCalls+(frontier==null?0:1),controllerAttempts);
            if(frontier!=null){assertTrue(f.closed());documents.put("after-ccanimal-first-unowned.txt",partial);}
            else if(returnedDeath)assertTrue(f.state().motion().routine()>=6,"Stop on the first returned death, before any pending replay");
            else{assertFalse(f.closed());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());assertTrue(f.state().motion().routine()<6);assertTrue(controllerAttempts+1>3000,"The only healthy no-boundary exit exhausts the total controller budget");}

        }finally{documents.put("after-ccanimal-route.csv",csv.toString());documents.put("after-ccanimal-held-inputs.csv",heldRows.toString());}
    }
    // END appended CCAnimal positive presence observer 20261108.
    // BEGIN appended source-only after-Ga observer; all new execution UNRUN.
    @Test void observeOriginalControllerAfterReturnedGaToFirstStop()throws Exception{
        // This observer uses only its own strict text output; old cleanup exporters stay idle.
        assertNull(System.getProperty("soniccd.r31.lower.evidence.dir"));assertNull(System.getProperty("soniccd.r31.lower.capture.dir"));
        String raw=System.getenv("SONICCD_CCZ_AFTER_GA_OBSERVER_DIR");Path outputDirectory=null;
        if(raw!=null){Path requested=Path.of(raw);Path allowed=Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "research").toRealPath();
            if(!requested.isAbsolute()||!raw.equals(requested.toString())||!requested.equals(requested.normalize())||!Files.isDirectory(requested,LinkOption.NOFOLLOW_LINKS)||!requested.equals(requested.toRealPath())||requested.equals(allowed)||!requested.startsWith(allowed))throw new IllegalArgumentException("Observer output requires an existing exact canonical research subdirectory");
            outputDirectory=requested;}
        int loadingReturned=0,attempted=0,controllerAttempts=0,replayReturned=0;boolean returnedDeath=false;String springRow="not reached";
        int returned=0,closedIntro=-1,firstCapture=-1,retired=-1,amyWhole=-1,turned=-1,entered=-1,boosted=-1,postInitialised=-1,springInitialised=-1;Throwable frontier=null;boolean qualifiedPrefix=false;int gaReturned=-1,gaWhole=-1,gaCpuReturned=-1;long gaCpuPieces=0;String gaRow="not reached",cached="before first ordinary";boolean backtracking=false,tentouReplayed=false;int tentouInitialised=-1,tentouWhole=-1,animalReturned=-1;boolean animalReplayed=false;String animalRow="not reached";String tentouRow="not reached";String last="";CdAssetBytes firstCaptureNormal=null;
        try(var b=fixture.start(1,0x80)){
            // Same original ready helper sequence (right8, native phase, bound256), counted explicitly.
            while(loadingReturned<256&&b.module.r31LoadingVisit().state().phase()!=SonicCdR31aLoadingVisit.Phase.READY){TestSonicCdR31aSelectedProfileLoading.step(b,8);loadingReturned++;controllerAttempts++;}
            assertEquals(SonicCdR31aLoadingVisit.Phase.READY,b.module.r31LoadingVisit().state().phase());var host=b.module.r31WorldHost();var core=b.module.r31LoadingVisit();var f=b.module.requirePlayerFrame();var placement=host.placement();var source=f.stageSource();var rawPool=placement.pool();cached=afterGaHealthyFacts(core,host,0);
            long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();int lives=host.globals().ring().lives();var intro=new TestSonicCdR31aOrdinaryRoute.FirstWallJump();
            var capturePalette=core.assets().view(CdR31aAssets.Asset.PAL_LEVEL).slice(0,32);
            var terrain=host.currentTerrain();int flags=f.state().motion().spriteFlags();assertEquals(0x4003,word(terrain,1056,960,flags));assertEquals(0x2003,word(terrain,1056,1024,flags));
            // Complete original16-pixel side profile, not a guessed visual opening.
            assertEquals(81,terrain.collision().idForBlock(3));for(int i=0;i<16;i++)assertEquals(16,terrain.collision().width(81,i));
            last=afterGaActualFacts(f,placement.pool());TestSonicCdR31aOrdinaryRoute.ClosedComparison pending=null;
            for(int n=1;n<=3000;n++){
                // verifyReturnedNext has exactly two original replay calls; reserve them BEFORE a next step.
                if(controllerAttempts+1+(pending==null?0:2)>3000)break;
                var m=f.state().motion();int x=m.centreX16_16()>>>16,y=m.centreY16_16()>>>16;
                if(closedIntro<0&&host.amyCapturedByte().orElseThrow()==0xFF&&placement.capture().pool().slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32)){
                    closedIntro=returned;backtracking=true;assertEquals(2,m.routine());assertEquals(0,m.flags()&2);
                }
                if(backtracking&&y-m.yRadius()>=976){backtracking=false;turned=returned;}
                int held=closedIntro<0?intro.input(b):returned==closedIntro?8:backtracking?4:8;
                var beforePool=placement.capture().pool();var beforeNormal=core.state().normalPalette();int beforeCapture=host.amyCapturedByte().orElseThrow();var beforeMotion=m;
                attempted=n;controllerAttempts++;
                try{TestSonicCdR31aOrdinaryRoute.step(b,held);}catch(UnsupportedOperationException reached){frontier=reached;pending=null;break;}
                returned=n;
                // First returned death is terminal for this recipe. Preserve only its actual
                // diagnostic; do not capture/draw/restore/replay a pending milestone afterward.
                if(f.state().motion().routine()>=6){returnedDeath=true;pending=null;break;}
                assertSame(f,b.module.requirePlayerFrame());assertSame(source,f.stageSource());assertSame(core,b.module.r31LoadingVisit());assertSame(host,b.module.r31WorldHost());assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());
                if(springInitialised<0)assertEquals(lives,host.globals().ring().lives());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());
                if(pending!=null){TestSonicCdR31aOrdinaryRoute.verifyReturnedNext(b,held,pending);controllerAttempts+=2;replayReturned+=2;if(tentouInitialised>=0)tentouReplayed=true;if(animalReturned>=0)animalReplayed=true;pending=null;}
                var afterPool=placement.capture().pool();
                if(beforeCapture==0&&host.amyCapturedByte().orElseThrow()==0xFF){
                    var amy=beforePool.slots().stream().filter(s->s.id()==0x32).findFirst().orElseThrow();var metal=beforePool.slots().stream().filter(s->s.id()==0x31).findFirst().orElseThrow();
                    amyWhole=amy.identity().wholeIndex();assertTrue(amyWhole<metal.identity().wholeIndex(),"Amy already ran before the actual Metal completion in this physical pass");
                    assertEquals(0x32,afterPool.slots().stream().filter(s->s.identity().wholeIndex()==amy.identity().wholeIndex()).findFirst().orElseThrow().id());
                    assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31));firstCapture=n;firstCaptureNormal=core.state().normalPalette();assertNotSame(beforeNormal,firstCaptureNormal);assertArrayEquals(capturePalette.copy(),firstCaptureNormal.slice(32,32).copy());
                }else if(firstCapture>=0&&retired<0){
                    assertEquals(firstCapture+1,n,"Next original physical pass must retire the earlier Amy parent");assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32));retired=n;
                    assertEquals(0xFF,host.amyCapturedByte().orElseThrow());assertNotSame(firstCaptureNormal,core.state().normalPalette());assertArrayEquals(capturePalette.copy(),core.state().normalPalette().slice(32,32).copy());
                    
                }
                intro.returned(f.state());assertFalse(intro.rejected);
                m=f.state().motion();x=m.centreX16_16()>>>16;y=m.centreY16_16()>>>16;
                if(entered<0&&turned>=0&&x>1088&&y-m.yRadius()>=976){entered=n;pending=TestSonicCdR31aOrdinaryRoute.comparison(b);}
                if(boosted<0&&entered>=0&&(m.flags()&4)!=0&&m.xVelocity()>=0xA00){
                    assertEquals(0,beforeMotion.flags()&4);assertEquals(0xA00,m.xVelocity());assertEquals(0xA00,m.groundVelocity());assertEquals(7,m.xRadius());assertEquals(14,m.yRadius());
                    final int actualX=x,actualY=y;assertTrue(afterPool.slots().stream().anyMatch(s->s.id()==0x35&&s.subtype()==0&&((actualX-s.centreX()+0x28)&0xFFFF)<0x50&&((actualY-s.centreY()+0x28)&0xFFFF)<0x50));
                    boosted=n;pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                if(postInitialised<0&&boosted>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x19&&s.subtype()==9&&s.routine()==2)){
                    postInitialised=n;pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                cached=afterGaHealthyFacts(core,host,n);last=afterGaActualFacts(f,rawPool);
                // Existing qualified Spring/presence requirements after the genuine next0A read.
                // The controller expression/recipe above is byte-exact; no diagnostic is input.
                // Native StageObjects has unsaved0690/0497/0A/subtype0, independent of observed whole49.
                if(springInitialised<0&&postInitialised>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520)){
                    assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertTrue(turned>closedIntro);assertTrue(entered>turned);assertTrue(boosted>entered);assertTrue(postInitialised>boosted);assertEquals(2,intro.jumps,"Retain the original two-jump recipe");
                    var spring=afterPool.slots().stream().filter(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520).findFirst().orElseThrow();
                    var beforeSpring=beforePool.slots().stream().filter(s->s.identity().wholeIndex()==spring.identity().wholeIndex()).findFirst().orElseThrow();
                    assertTrue(beforeSpring.id()==0||(beforeSpring.id()==0x0A&&beforeSpring.routine()==0),"Prior closed slot is empty or the not-yet-initialised canonical Spring; do not invent an intermediate birth");if(beforeSpring.id()==0x0A){assertEquals(0x690,beforeSpring.centreX());assertEquals(0x497,beforeSpring.centreY());}assertEquals(0,spring.subtype());
                    springInitialised=n;assertTrue(springInitialised>postInitialised);
                    springRow="ACTUAL HEALTHY RETURNED Spring Init; recipeOrdinal="+n+" heldNative="+held+" whole="+spring.identity().wholeIndex()+" spriteFlags="+spring.spriteFlags()+" flags="+spring.flags()+" savedFlagsId(raw)="+spring.savedFlagsId()+" subtype="+spring.subtype()+"\nBefore64="+HexFormat.of().formatHex(beforeSpring.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(spring.copyBytes())+"\n"+last;
                    
                    
                    // Continue only this same ordinary returned stack/right8. No pending Spring receipt or warp bypass.
                }
                if(tentouInitialised<0&&springInitialised>=0){
                    var selected=afterPool.slots().stream().filter(s->s.id()==0x2E&&(byte)(s.subtype()-1)<0
                        &&(s.routine()==2||s.routine()==4||s.routine()==6)&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0xA46A
                        &&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==(s.subtype()<128?0x20BBC2:0x20BB50)
                        &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.PRIORITY)==1
                        &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.COLLISION_TYPE)==6
                        &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.WIDTH)==14&&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.Y_RADIUS)==14).findFirst();
                    if(selected.isPresent()){
                        var t=selected.orElseThrow();var prior=beforePool.slots().stream().filter(s->s.identity().wholeIndex()==t.identity().wholeIndex()).findFirst().orElseThrow();
                        assertTrue(prior.id()==0||prior.id()==0x2E&&prior.routine()==0,"Observe genuine first Init, not an advanced hydrated parent");
                        assertTrue(core.assets().placements().records().stream().anyMatch(r->(r.encodedId()&127)==0x2E&&r.centreX()==t.centreX()&&r.subtype()==t.subtype()),"Actual selected source placement, not whole/ordinal fitting");
                        tentouInitialised=n;tentouWhole=t.identity().wholeIndex();assertTrue(tentouInitialised>springInitialised);
                        tentouRow="ACTUAL HEALTHY RETURNED Tentou Init; recipeOrdinal="+n+" heldNative="+held+" whole="+tentouWhole+" flags="+t.flags()+" spriteFlags="+t.spriteFlags()+" savedFlagsId(raw)="+t.savedFlagsId()+" subtype="+t.subtype()+"\nBefore64="+HexFormat.of().formatHex(prior.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(t.copyBytes())+"\n"+last;
                        pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                    }
                }
                if(animalReturned<0&&springInitialised>=0){
                    for(var animal:afterPool.slots())if(animal.id()==0x39&&core.assets().placements().records().stream().anyMatch(r->(r.encodedId()&127)==0x39&&r.subtype()==animal.subtype()&&r.centreX()==animal.centreX()&&(r.packedY()&0xFFF)==animal.centreY())){
                        int rawPeriod=f.environment().movement().gates().timeZone();assertTrue((byte)rawPeriod<2);assertEquals(0,animal.subtype()&128);
                        assertEquals(0,host.globals().projectorByte().orElseThrow(),"Actual selected IPX.GameLoop clear retained through Host; absence is not zero");
                        var prior=beforePool.slots().stream().filter(v->v.identity().wholeIndex()==animal.identity().wholeIndex()).findFirst().orElseThrow();
                        if(prior.id()==0x39)assertArrayEquals(prior.copyBytes(),animal.copyBytes(),"Native projector-zero presence defers Init/Draw and retains dirty bytes");
                        else assertEquals(0,prior.id(),"Actual original source placement birth, no injected animal");
                        animalReturned=n;assertTrue(tentouInitialised>=0);assertTrue(animalReturned>=tentouInitialised);
                        animalRow="ACTUAL RETURNED SOURCE-PLACED CCAnimal presence/deferred-body; no Animal Init/Draw claim. recipeOrdinal="+n+" held="+held+" whole="+animal.identity().wholeIndex()+" rawPeriod="+rawPeriod+" projector="+host.globals().projectorByte()+"\nBefore64="+HexFormat.of().formatHex(prior.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(animal.copyBytes())+"\n"+last;
                        if(pending==null)pending=TestSonicCdR31aOrdinaryRoute.comparison(b);break;
                    }
                }
                // Regression assertion only: these ordinals never choose input or an object handler.
                if(n==1270){
                    assertEquals(24,loadingReturned);assertEquals(8,replayReturned);assertEquals(1302,controllerAttempts);
                    assertEquals(930,closedIntro);assertEquals(1003,turned);assertEquals(1102,entered);assertEquals(1116,boosted);assertEquals(1119,postInitialised);assertEquals(1131,springInitialised);
                    assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertEquals(2,intro.jumps);
                    assertTrue(tentouInitialised>springInitialised&&tentouReplayed);assertTrue(animalReturned>=tentouInitialised&&animalReplayed);assertNull(pending);
                    qualifiedPrefix=true;
                }
                if(gaReturned<0){
                    for(var g:afterPool.slots())if(g.id()==0x2D&&(byte)(g.subtype()-1)<0&&g.routine()==2
                        &&g.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0xA421
                        &&g.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==(g.subtype()<128?0x20B4F2:0x20B4B0)
                        &&(g.spriteFlags()&127)==4&&g.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.MAP_FRAME)==0
                        &&g.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.PRIORITY)==1
                        &&g.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.WIDTH)==16&&g.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.Y_RADIUS)==8
                        &&g.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.PATA_BATA_ANCHOR_X)==0x3C
                        &&core.assets().placements().records().stream().anyMatch(p->(p.encodedId()&127)==0x2D&&p.subtype()==g.subtype()&&p.centreX()==g.centreX()&&(p.packedY()&0xFFF)==g.centreY())){
                        assertTrue(qualifiedPrefix,"An earlier unsupported/premature Ga is not the R161 qualified prefix");assertEquals(1271,n);assertEquals(1303,controllerAttempts);assertEquals(44,g.identity().wholeIndex());assertEquals(0,core.state().program().goodFutureByte());
                        var prior=beforePool.slots().stream().filter(p->p.identity().wholeIndex()==g.identity().wholeIndex()).findFirst().orElseThrow();
                        assertTrue(prior.id()==0||prior.id()==0x2D&&prior.routine()==0,"Actual placement birth/uninitialized Ga, never injected advanced state");
                        if(g.subtype()<128)assertEquals(255,g.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.TAMABBOH_MISSILE_DIRECTION));
                        gaReturned=n;gaWhole=g.identity().wholeIndex();
                        gaRow="ACTUAL returned source-placed Ga Init; recipeOrdinal="+n+" overallReturned="+controllerAttempts+" whole="+gaWhole+" subtype="+g.subtype()+" spriteFlags="+g.spriteFlags()+"\nBefore64="+HexFormat.of().formatHex(prior.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(g.copyBytes())+"\nNative CdGa.body init/waitFirst then unconditional Draw/despawn returned on this stack. This is pinned source-path evidence, not an exposed Draw receipt or visible-piece claim.\n";
                        break;
                    }
                }
                if(gaReturned>=0){
                    final int observedWhole=gaWhole;gaCpuPieces=host.video().completedCpu().map(cpu->cpu.provenance().stream().filter(p->p.slotRef().wholeIndex()==observedWhole).count()).orElse(0L);
                    if(gaCpuReturned<0&&gaCpuPieces>0)gaCpuReturned=n;
                }
                cached=afterGaHealthyFacts(core,host,n);last=afterGaActualFacts(f,rawPool);

            }
            // No healthy Module/Host/Core getter below: frontier/death may have retired it.
            int completedCalls=loadingReturned+returned+replayReturned;
            var error=new StringBuilder();if(frontier!=null){String message=String.valueOf(frontier.getMessage());if(message.length()>2048)message=message.substring(0,2048)+"[truncated]";
                error.append(frontier.getClass().getName()).append(": ").append(message).append('\n');var stack=frontier.getStackTrace();for(int i=0;i<Math.min(12,stack.length);i++){String line=stack[i].toString();error.append(" at ").append(line,0,Math.min(512,line.length())).append('\n');}}
            String outcome=frontier!=null?"FIRST_UNSUPPORTED":returnedDeath?"FIRST_RETURNED_DEATH":"BOUND_REACHED";
            String report="DIAGNOSTIC OBSERVER ONLY; constructed historical-save CCZ slot1/platform80, not natural PPZ-to-CCZ, route success, parity, pixels or death continuation.\n"
                +"outcome="+outcome+" loadingReturned="+loadingReturned+" recipeReturned="+returned+" replayReturned="+replayReturned+" completedControllerCalls="+completedCalls+" lastAttemptedRecipeOrdinal="+attempted+" controllerAttempts="+controllerAttempts+" qualifiedR161Prefix="+qualifiedPrefix+" gaInitReturned="+gaReturned+" gaWhole="+gaWhole+" firstGaCpuProvenanceRecipe="+gaCpuReturned+" lastGaCpuPieces="+gaCpuPieces+"\n"
                +"FIRST_ERROR (bounded; none for returned-death/bound)\n"+error+"GA_MILESTONE\n"+gaRow
                +"No direct public Ga Draw receipt exists. CPU provenance records selected pieces when present; culling/empty mapping can emit none. Returned native Init plus its unconditional Draw call path is source-backed evidence only, never visible Ga/parity.\n"
                +"LAST_COMPLETE_HEALTHY_COMPONENT_CUT\n"+last+cached
                +"TERMINAL_DIRECT_FRAME_AND_RAW_POOL_ONLY (not a closed World/selected image; cached healthy facts above remain a different cut)\n"+afterGaActualFacts(f,rawPool);
            if(report.length()>32768)report=report.substring(0,32768)+"\n[report truncated at32768 characters]\n";
            if(frontier!=null&&!(qualifiedPrefix&&gaReturned==1271))throw(UnsupportedOperationException)frontier;
            assertTrue(qualifiedPrefix,"The exact returned R161 prefix must precede any later classification");assertEquals(1271,gaReturned,"Actual Ga Init/native Draw path must return, not only source presence");assertEquals(44,gaWhole);
            assertTrue(controllerAttempts<=3000);assertEquals(completedCalls+(frontier==null?0:1),controllerAttempts);
            if(frontier!=null)assertTrue(f.closed());else if(returnedDeath)assertTrue(f.state().motion().routine()>=6);else{assertFalse(f.closed());assertTrue(controllerAttempts+1>3000);}
            System.out.println(report);
            if(outputDirectory!=null){if(!Files.isDirectory(outputDirectory,LinkOption.NOFOLLOW_LINKS)||!outputDirectory.equals(outputDirectory.toRealPath()))throw new IllegalArgumentException("Validated observer output directory changed");Files.writeString(outputDirectory.resolve("ENGINE-CCZ-after-Ga-observer.txt"),report,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);}
        }
    }

    private static String afterGaHealthyFacts(SonicCdR31aLoadingVisit core,SonicCdR31aWorldHost host,int recipe){
        var s=core.state();var g=host.globals();var v=host.video();
        return "CACHED_HEALTHY_MODULE recipe="+recipe+" phase="+s.phase()+" program="+s.program()+" level="+s.level()+" ring="+g.ring()+" score="+g.score()+" amy="+host.amyCapturedByte()+" projector="+g.projectorByte()+" completedCpuSprites="+v.completedCpu().map(cpu->Integer.toString(cpu.spriteCount())).orElse("absent")+" selectedClass="+host.selected().getClass().getSimpleName()+"\n";
    }
    private static String afterGaActualFacts(com.openggf.game.soniccd.player.CdPlayerFrameRuntime frame,com.openggf.game.soniccd.objects.CdObjectPool pool){
        var m=frame.state().motion();var c=frame.environment().controls();var out=new StringBuilder("READ_ONLY_ACTUAL_FRAME closed=");
        out.append(frame.closed()).append(" clock=").append(frame.vIntClock()).append(" routine=").append(m.routine()).append(" xWord=").append(m.centreX16_16()>>>16).append(" yWord=").append(m.centreY16_16()>>>16).append(" xVelocity=").append(m.xVelocity()).append(" yVelocity=").append(m.yVelocity()).append(" flags=").append(m.flags()).append(" held=").append(c.rawHeld()).append(" tap=").append(c.rawTap()).append(" locked=").append(c.controlLocked()).append('\n');
        out.append("RAW_COMPONENT_POOL nonzero[whole:64bytes] (not a healthy World capture)=");for(var s:pool.capture().slots())if(s.id()!=0)out.append(s.identity().wholeIndex()).append(':').append(HexFormat.of().formatHex(s.copyBytes())).append(';');out.append('\n');
        if(out.length()>16000){out.setLength(16000);out.append("\n[raw component dump truncated at16000 characters]\n");}return out.toString();
    }
    // END appended source-only after-Ga observer.


    // BEGIN appended bounded after-Ga route reconnaissance; ALL NEW EXECUTION UNRUN.
    // No route geometry or jump is invented; only an existing controller policy is extended.
    @Test void observeOriginalControllerAfterReturnedGaBeyondRetainedBoundWithMotionSamples()throws Exception{runAfterGaRouteRecon(false);}
    @Test void observeOriginalControllerJumpAfterRetainedBounceToFirstStop()throws Exception{runAfterGaRouteRecon(true);}

    private void runAfterGaRouteRecon(boolean jumpExtension)throws Exception{
        // This observer uses only its own strict text output; old cleanup exporters stay idle.
        assertNull(System.getProperty("soniccd.r31.lower.evidence.dir"));assertNull(System.getProperty("soniccd.r31.lower.capture.dir"));
        String raw=System.getenv(jumpExtension?"SONICCD_CCZ_AFTER_BOUNCE_JUMP_DIR":"SONICCD_CCZ_ROUTE_RECON_DIR");Path outputDirectory=null;
        if(raw!=null){Path requested=Path.of(raw);Path allowed=Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "research").toRealPath();
            if(!requested.isAbsolute()||!raw.equals(requested.toString())||!requested.equals(requested.normalize())||!Files.isDirectory(requested,LinkOption.NOFOLLOW_LINKS)||!requested.equals(requested.toRealPath())||requested.equals(allowed)||!requested.startsWith(allowed))throw new IllegalArgumentException("Observer output requires an existing exact canonical research subdirectory");
            outputDirectory=requested;}
        final int maxControllerCalls=jumpExtension?3768:3256,retainedControllerBound=3000;
        boolean bounceCutReturned=false,jumpObserved=false,jumpNotObserved=false;int jumpRequests=0,jumpExtensionReturned=0;String jumpFacts="not requested";
        boolean retainedBoundReturned=false;int extensionReturned=0;
        var motionSamples=new StringBuilder(jumpExtension?"READ_ONLY_HEALTHY_JUMP_WINDOW; callerRight+B12 thenRight. Terrain probes are comparisons, never input.\n":"READ_ONLY_HEALTHY_LATE_WINDOW; requested inputs are unchanged Right8 after the existing intro policy. Terrain probes are comparisons, never input.\n");
        int loadingReturned=0,attempted=0,controllerAttempts=0,replayReturned=0;boolean returnedDeath=false;String springRow="not reached";
        int returned=0,closedIntro=-1,firstCapture=-1,retired=-1,amyWhole=-1,turned=-1,entered=-1,boosted=-1,postInitialised=-1,springInitialised=-1;Throwable frontier=null;boolean qualifiedPrefix=false;int gaReturned=-1,gaWhole=-1,gaCpuReturned=-1;long gaCpuPieces=0;String gaRow="not reached",cached="before first ordinary";boolean backtracking=false,tentouReplayed=false;int tentouInitialised=-1,tentouWhole=-1,animalReturned=-1;boolean animalReplayed=false;String animalRow="not reached";String tentouRow="not reached";String last="";CdAssetBytes firstCaptureNormal=null;
        try(var b=fixture.start(1,0x80)){
            // Same original ready helper sequence (right8, native phase, bound256), counted explicitly.
            while(loadingReturned<256&&b.module.r31LoadingVisit().state().phase()!=SonicCdR31aLoadingVisit.Phase.READY){TestSonicCdR31aSelectedProfileLoading.step(b,8);loadingReturned++;controllerAttempts++;}
            assertEquals(SonicCdR31aLoadingVisit.Phase.READY,b.module.r31LoadingVisit().state().phase());var host=b.module.r31WorldHost();var core=b.module.r31LoadingVisit();var f=b.module.requirePlayerFrame();var placement=host.placement();var source=f.stageSource();var rawPool=placement.pool();cached=afterGaHealthyFacts(core,host,0);
            long generation=b.mode.getLevelManager().getCompletedProductionLoadGeneration();int lives=host.globals().ring().lives();var intro=new TestSonicCdR31aOrdinaryRoute.FirstWallJump();
            var capturePalette=core.assets().view(CdR31aAssets.Asset.PAL_LEVEL).slice(0,32);
            var terrain=host.currentTerrain();int flags=f.state().motion().spriteFlags();assertEquals(0x4003,word(terrain,1056,960,flags));assertEquals(0x2003,word(terrain,1056,1024,flags));
            // Complete original16-pixel side profile, not a guessed visual opening.
            assertEquals(81,terrain.collision().idForBlock(3));for(int i=0;i<16;i++)assertEquals(16,terrain.collision().width(81,i));
            last=afterGaActualFacts(f,placement.pool());TestSonicCdR31aOrdinaryRoute.ClosedComparison pending=null;
            for(int n=1;n<=maxControllerCalls;n++){
                // verifyReturnedNext has exactly two original replay calls; reserve them BEFORE a next step.
                if(controllerAttempts+1+(pending==null?0:2)>maxControllerCalls)break;
                var m=f.state().motion();int x=m.centreX16_16()>>>16,y=m.centreY16_16()>>>16;
                if(closedIntro<0&&host.amyCapturedByte().orElseThrow()==0xFF&&placement.capture().pool().slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32)){
                    closedIntro=returned;backtracking=true;assertEquals(2,m.routine());assertEquals(0,m.flags()&2);
                }
                if(backtracking&&y-m.yRadius()>=976){backtracking=false;turned=returned;}
                int held=closedIntro<0?intro.input(b):returned==closedIntro?8:backtracking?4:8;
                if(jumpExtension&&controllerAttempts>=3256){
                    assertTrue(bounceCutReturned,"The actual grounded retained cut must precede new caller input");
                    assertNull(pending,"No replay/capture work is added after the retained cut");
                    if(controllerAttempts==3256){
                        assertFalse(f.closed());assertEquals(2,m.routine());assertEquals(0,m.flags()&2);
                        assertEquals(0,f.environment().controls().controlLocked());assertEquals(0,m.charge());
                        assertEquals(8,f.environment().controls().rawHeld());assertEquals(0,f.environment().controls().rawTap());
                    }
                    // Public caller policy only: native B10 + Right8 for12 calls, then Right8.
                    held=controllerAttempts<3268?0x18:8;if((held&0x10)!=0)jumpRequests++;
                }
                var beforePool=placement.capture().pool();var beforeNormal=core.state().normalPalette();int beforeCapture=host.amyCapturedByte().orElseThrow();var beforeMotion=m;
                attempted=n;controllerAttempts++;
                try{TestSonicCdR31aOrdinaryRoute.step(b,held);}catch(UnsupportedOperationException reached){frontier=reached;pending=null;break;}
                returned=n;if(jumpExtension&&controllerAttempts>3256)jumpExtensionReturned++;
                // First returned death is terminal for this recipe. Preserve only its actual
                // diagnostic; do not capture/draw/restore/replay a pending milestone afterward.
                if(f.state().motion().routine()>=6){returnedDeath=true;pending=null;break;}
                assertSame(f,b.module.requirePlayerFrame());assertSame(source,f.stageSource());assertSame(core,b.module.r31LoadingVisit());assertSame(host,b.module.r31WorldHost());assertEquals(generation,b.mode.getLevelManager().getCompletedProductionLoadGeneration());
                if(springInitialised<0)assertEquals(lives,host.globals().ring().lives());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());
                if(jumpExtension&&controllerAttempts==3257){
                    var actual=f.state().motion();var controls=f.environment().controls();
                    assertEquals(0x18,controls.rawHeld());assertEquals(0x10,controls.rawTap()&0x10);
                    jumpObserved=actual.playerJump()==1&&(actual.flags()&2)!=0&&actual.yVelocity()<0;
                    jumpFacts="FIRST_ACTUAL_JUMP_REQUEST_RETURN playerJump="+actual.playerJump()+" charge="+actual.charge()
                        +" airborne="+((actual.flags()&2)!=0)+" vx="+actual.xVelocity()+" vy="+actual.yVelocity()
                        +" held="+controls.rawHeld()+" tap="+controls.rawTap()+" nativeJumpStateObserved="+jumpObserved+"\n"
                        +afterGaRouteMotionSample(f,host,n,controllerAttempts,held);
                    if(!jumpObserved){jumpNotObserved=true;pending=null;cached=afterGaHealthyFacts(core,host,n);last=afterGaActualFacts(f,rawPool);break;}
                }
                if(pending!=null){TestSonicCdR31aOrdinaryRoute.verifyReturnedNext(b,held,pending);controllerAttempts+=2;replayReturned+=2;if(tentouInitialised>=0)tentouReplayed=true;if(animalReturned>=0)animalReplayed=true;pending=null;}
                var afterPool=placement.capture().pool();
                if(beforeCapture==0&&host.amyCapturedByte().orElseThrow()==0xFF){
                    var amy=beforePool.slots().stream().filter(s->s.id()==0x32).findFirst().orElseThrow();var metal=beforePool.slots().stream().filter(s->s.id()==0x31).findFirst().orElseThrow();
                    amyWhole=amy.identity().wholeIndex();assertTrue(amyWhole<metal.identity().wholeIndex(),"Amy already ran before the actual Metal completion in this physical pass");
                    assertEquals(0x32,afterPool.slots().stream().filter(s->s.identity().wholeIndex()==amy.identity().wholeIndex()).findFirst().orElseThrow().id());
                    assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31));firstCapture=n;firstCaptureNormal=core.state().normalPalette();assertNotSame(beforeNormal,firstCaptureNormal);assertArrayEquals(capturePalette.copy(),firstCaptureNormal.slice(32,32).copy());
                }else if(firstCapture>=0&&retired<0){
                    assertEquals(firstCapture+1,n,"Next original physical pass must retire the earlier Amy parent");assertTrue(afterPool.slots().stream().noneMatch(s->s.id()==0x31||s.id()==0x32));retired=n;
                    assertEquals(0xFF,host.amyCapturedByte().orElseThrow());assertNotSame(firstCaptureNormal,core.state().normalPalette());assertArrayEquals(capturePalette.copy(),core.state().normalPalette().slice(32,32).copy());
                    
                }
                intro.returned(f.state());assertFalse(intro.rejected);
                m=f.state().motion();x=m.centreX16_16()>>>16;y=m.centreY16_16()>>>16;
                if(entered<0&&turned>=0&&x>1088&&y-m.yRadius()>=976){entered=n;pending=TestSonicCdR31aOrdinaryRoute.comparison(b);}
                if(boosted<0&&entered>=0&&(m.flags()&4)!=0&&m.xVelocity()>=0xA00){
                    assertEquals(0,beforeMotion.flags()&4);assertEquals(0xA00,m.xVelocity());assertEquals(0xA00,m.groundVelocity());assertEquals(7,m.xRadius());assertEquals(14,m.yRadius());
                    final int actualX=x,actualY=y;assertTrue(afterPool.slots().stream().anyMatch(s->s.id()==0x35&&s.subtype()==0&&((actualX-s.centreX()+0x28)&0xFFFF)<0x50&&((actualY-s.centreY()+0x28)&0xFFFF)<0x50));
                    boosted=n;pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                if(postInitialised<0&&boosted>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x19&&s.subtype()==9&&s.routine()==2)){
                    postInitialised=n;pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                }
                cached=afterGaHealthyFacts(core,host,n);last=afterGaActualFacts(f,rawPool);
                // Existing qualified Spring/presence requirements after the genuine next0A read.
                // The controller expression/recipe above is byte-exact; no diagnostic is input.
                // Native StageObjects has unsaved0690/0497/0A/subtype0, independent of observed whole49.
                if(springInitialised<0&&postInitialised>=0&&afterPool.slots().stream().anyMatch(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520)){
                    assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertTrue(turned>closedIntro);assertTrue(entered>turned);assertTrue(boosted>entered);assertTrue(postInitialised>boosted);assertEquals(2,intro.jumps,"Retain the original two-jump recipe");
                    var spring=afterPool.slots().stream().filter(s->s.id()==0x0A&&s.centreX()==0x690&&s.centreY()==0x497&&s.routine()==2&&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==0x20968A&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0x520).findFirst().orElseThrow();
                    var beforeSpring=beforePool.slots().stream().filter(s->s.identity().wholeIndex()==spring.identity().wholeIndex()).findFirst().orElseThrow();
                    assertTrue(beforeSpring.id()==0||(beforeSpring.id()==0x0A&&beforeSpring.routine()==0),"Prior closed slot is empty or the not-yet-initialised canonical Spring; do not invent an intermediate birth");if(beforeSpring.id()==0x0A){assertEquals(0x690,beforeSpring.centreX());assertEquals(0x497,beforeSpring.centreY());}assertEquals(0,spring.subtype());
                    springInitialised=n;assertTrue(springInitialised>postInitialised);
                    springRow="ACTUAL HEALTHY RETURNED Spring Init; recipeOrdinal="+n+" heldNative="+held+" whole="+spring.identity().wholeIndex()+" spriteFlags="+spring.spriteFlags()+" flags="+spring.flags()+" savedFlagsId(raw)="+spring.savedFlagsId()+" subtype="+spring.subtype()+"\nBefore64="+HexFormat.of().formatHex(beforeSpring.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(spring.copyBytes())+"\n"+last;
                    
                    
                    // Continue only this same ordinary returned stack/right8. No pending Spring receipt or warp bypass.
                }
                if(tentouInitialised<0&&springInitialised>=0){
                    var selected=afterPool.slots().stream().filter(s->s.id()==0x2E&&(byte)(s.subtype()-1)<0
                        &&(s.routine()==2||s.routine()==4||s.routine()==6)&&s.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0xA46A
                        &&s.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==(s.subtype()<128?0x20BBC2:0x20BB50)
                        &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.PRIORITY)==1
                        &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.COLLISION_TYPE)==6
                        &&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.WIDTH)==14&&s.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.Y_RADIUS)==14).findFirst();
                    if(selected.isPresent()){
                        var t=selected.orElseThrow();var prior=beforePool.slots().stream().filter(s->s.identity().wholeIndex()==t.identity().wholeIndex()).findFirst().orElseThrow();
                        assertTrue(prior.id()==0||prior.id()==0x2E&&prior.routine()==0,"Observe genuine first Init, not an advanced hydrated parent");
                        assertTrue(core.assets().placements().records().stream().anyMatch(r->(r.encodedId()&127)==0x2E&&r.centreX()==t.centreX()&&r.subtype()==t.subtype()),"Actual selected source placement, not whole/ordinal fitting");
                        tentouInitialised=n;tentouWhole=t.identity().wholeIndex();assertTrue(tentouInitialised>springInitialised);
                        tentouRow="ACTUAL HEALTHY RETURNED Tentou Init; recipeOrdinal="+n+" heldNative="+held+" whole="+tentouWhole+" flags="+t.flags()+" spriteFlags="+t.spriteFlags()+" savedFlagsId(raw)="+t.savedFlagsId()+" subtype="+t.subtype()+"\nBefore64="+HexFormat.of().formatHex(prior.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(t.copyBytes())+"\n"+last;
                        pending=TestSonicCdR31aOrdinaryRoute.comparison(b);
                    }
                }
                if(animalReturned<0&&springInitialised>=0){
                    for(var animal:afterPool.slots())if(animal.id()==0x39&&core.assets().placements().records().stream().anyMatch(r->(r.encodedId()&127)==0x39&&r.subtype()==animal.subtype()&&r.centreX()==animal.centreX()&&(r.packedY()&0xFFF)==animal.centreY())){
                        int rawPeriod=f.environment().movement().gates().timeZone();assertTrue((byte)rawPeriod<2);assertEquals(0,animal.subtype()&128);
                        assertEquals(0,host.globals().projectorByte().orElseThrow(),"Actual selected IPX.GameLoop clear retained through Host; absence is not zero");
                        var prior=beforePool.slots().stream().filter(v->v.identity().wholeIndex()==animal.identity().wholeIndex()).findFirst().orElseThrow();
                        if(prior.id()==0x39)assertArrayEquals(prior.copyBytes(),animal.copyBytes(),"Native projector-zero presence defers Init/Draw and retains dirty bytes");
                        else assertEquals(0,prior.id(),"Actual original source placement birth, no injected animal");
                        animalReturned=n;assertTrue(tentouInitialised>=0);assertTrue(animalReturned>=tentouInitialised);
                        animalRow="ACTUAL RETURNED SOURCE-PLACED CCAnimal presence/deferred-body; no Animal Init/Draw claim. recipeOrdinal="+n+" held="+held+" whole="+animal.identity().wholeIndex()+" rawPeriod="+rawPeriod+" projector="+host.globals().projectorByte()+"\nBefore64="+HexFormat.of().formatHex(prior.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(animal.copyBytes())+"\n"+last;
                        if(pending==null)pending=TestSonicCdR31aOrdinaryRoute.comparison(b);break;
                    }
                }
                // Regression assertion only: these ordinals never choose input or an object handler.
                if(n==1270){
                    assertEquals(24,loadingReturned);assertEquals(8,replayReturned);assertEquals(1302,controllerAttempts);
                    assertEquals(930,closedIntro);assertEquals(1003,turned);assertEquals(1102,entered);assertEquals(1116,boosted);assertEquals(1119,postInitialised);assertEquals(1131,springInitialised);
                    assertTrue(firstCapture>0);assertEquals(firstCapture+1,retired);assertEquals(retired,closedIntro);assertEquals(2,intro.jumps);
                    assertTrue(tentouInitialised>springInitialised&&tentouReplayed);assertTrue(animalReturned>=tentouInitialised&&animalReplayed);assertNull(pending);
                    qualifiedPrefix=true;
                }
                if(gaReturned<0){
                    for(var g:afterPool.slots())if(g.id()==0x2D&&(byte)(g.subtype()-1)<0&&g.routine()==2
                        &&g.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE)==0xA421
                        &&g.longBits(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS)==(g.subtype()<128?0x20B4F2:0x20B4B0)
                        &&(g.spriteFlags()&127)==4&&g.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.MAP_FRAME)==0
                        &&g.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.PRIORITY)==1
                        &&g.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.WIDTH)==16&&g.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.Y_RADIUS)==8
                        &&g.word(com.openggf.game.soniccd.objects.CdObjectState.WordField.PATA_BATA_ANCHOR_X)==0x3C
                        &&core.assets().placements().records().stream().anyMatch(p->(p.encodedId()&127)==0x2D&&p.subtype()==g.subtype()&&p.centreX()==g.centreX()&&(p.packedY()&0xFFF)==g.centreY())){
                        assertTrue(qualifiedPrefix,"An earlier unsupported/premature Ga is not the R161 qualified prefix");assertEquals(1271,n);assertEquals(1303,controllerAttempts);assertEquals(44,g.identity().wholeIndex());assertEquals(0,core.state().program().goodFutureByte());
                        var prior=beforePool.slots().stream().filter(p->p.identity().wholeIndex()==g.identity().wholeIndex()).findFirst().orElseThrow();
                        assertTrue(prior.id()==0||prior.id()==0x2D&&prior.routine()==0,"Actual placement birth/uninitialized Ga, never injected advanced state");
                        if(g.subtype()<128)assertEquals(255,g.unsignedByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.TAMABBOH_MISSILE_DIRECTION));
                        gaReturned=n;gaWhole=g.identity().wholeIndex();
                        gaRow="ACTUAL returned source-placed Ga Init; recipeOrdinal="+n+" overallReturned="+controllerAttempts+" whole="+gaWhole+" subtype="+g.subtype()+" spriteFlags="+g.spriteFlags()+"\nBefore64="+HexFormat.of().formatHex(prior.copyBytes())+"\nAfter64="+HexFormat.of().formatHex(g.copyBytes())+"\nNative CdGa.body init/waitFirst then unconditional Draw/despawn returned on this stack. This is pinned source-path evidence, not an exposed Draw receipt or visible-piece claim.\n";
                        break;
                    }
                }
                if(gaReturned>=0){
                    final int observedWhole=gaWhole;gaCpuPieces=host.video().completedCpu().map(cpu->cpu.provenance().stream().filter(p->p.slotRef().wholeIndex()==observedWhole).count()).orElse(0L);
                    if(gaCpuReturned<0&&gaCpuPieces>0)gaCpuReturned=n;
                }
                cached=afterGaHealthyFacts(core,host,n);last=afterGaActualFacts(f,rawPool);
                // Regression-only cut. These observed values never select an input/handler.
                if(controllerAttempts==retainedControllerBound){
                    assertTrue(qualifiedPrefix);assertEquals(1271,gaReturned);assertEquals(44,gaWhole);assertNull(pending);
                    assertEquals(24,loadingReturned);assertEquals(2968,n);assertEquals(8,replayReturned);
                    var retained=f.state().motion();var controls=f.environment().controls();
                    assertEquals(2,retained.routine());assertEquals(1905,retained.centreX16_16()>>>16);assertEquals(832,retained.centreY16_16()>>>16);
                    assertEquals(-389,retained.xVelocity());assertEquals(133,retained.yVelocity());assertEquals(2,retained.flags());
                    assertEquals(8,held);assertEquals(8,controls.rawHeld());assertEquals(0,controls.controlLocked());
                    assertEquals(5,host.globals().ring().rings());assertEquals(3,host.globals().ring().lives());
                    retainedBoundReturned=true;
                }
                if(controllerAttempts>retainedControllerBound){
                    assertTrue(retainedBoundReturned,"No extension may bypass the retained actual controller cut");
                    if(!jumpExtension||controllerAttempts<=3256)assertEquals(8,held,"The retained extension stays unchanged Right8");
                    else assertEquals(controllerAttempts<=3268?0x18:8,held,"Only the new caller-only12-call jump burst differs");
                    assertNull(pending,"The extension issues no new capture/restore/replay work");
                    if(controllerAttempts<=3256)extensionReturned++;
                }
                if(jumpExtension&&controllerAttempts==3256){
                    assertTrue(retainedBoundReturned);assertEquals(3224,n);assertEquals(256,extensionReturned);
                    assertEquals(24,loadingReturned);assertEquals(8,replayReturned);assertNull(pending);
                    var cut=f.state().motion();var controls=f.environment().controls();
                    assertEquals(1974,cut.centreX16_16()>>>16);assertEquals(876,cut.centreY16_16()>>>16);
                    assertEquals(728,cut.xVelocity());assertEquals(0,cut.yVelocity());assertEquals(728,cut.groundVelocity());
                    assertEquals(0,cut.flags());assertEquals(2,cut.routine());assertEquals(0,controls.controlLocked());
                    assertEquals(8,controls.rawHeld());assertEquals(0,controls.rawTap());
                    assertEquals(5,host.globals().ring().rings());assertEquals(3,host.globals().ring().lives());
                    bounceCutReturned=true;
                }
                if((jumpExtension?controllerAttempts>=3256:controllerAttempts>=2880)
                        &&(controllerAttempts%16==0||controllerAttempts==retainedControllerBound||controllerAttempts==3256||controllerAttempts==maxControllerCalls))
                    motionSamples.append(afterGaRouteMotionSample(f,host,n,controllerAttempts,held));

            }
            // No healthy Module/Host/Core getter below: frontier/death may have retired it.
            int completedCalls=loadingReturned+returned+replayReturned;
            var error=new StringBuilder();if(frontier!=null){String message=String.valueOf(frontier.getMessage());if(message.length()>2048)message=message.substring(0,2048)+"[truncated]";
                error.append(frontier.getClass().getName()).append(": ").append(message).append('\n');var stack=frontier.getStackTrace();for(int i=0;i<Math.min(12,stack.length);i++){String line=stack[i].toString();error.append(" at ").append(line,0,Math.min(512,line.length())).append('\n');}}
            String outcome=frontier!=null?"FIRST_UNSUPPORTED":returnedDeath?"FIRST_RETURNED_DEATH":jumpNotObserved?"JUMP_STATE_NOT_OBSERVED":"BOUND_REACHED";
            String report=(jumpExtension?"DIAGNOSTIC JUMP CONTINUATION ONLY; exact retained3256 cut, then at most512 caller-only calls: Right+B12 then Right. No natural PPZ-to-CCZ, route success, parity, pixels or death continuation.\n":"DIAGNOSTIC ROUTE RECON ONLY; retained 3000-call cut plus at most256 unchanged Right8 calls. Constructed historical-save CCZ slot1/platform80, not natural PPZ-to-CCZ, route success, parity, pixels or death continuation.\n")
                +"outcome="+outcome+" loadingReturned="+loadingReturned+" recipeReturned="+returned+" replayReturned="+replayReturned+" completedControllerCalls="+completedCalls+" lastAttemptedRecipeOrdinal="+attempted+" controllerAttempts="+controllerAttempts+" retainedBoundReturned="+retainedBoundReturned+" extensionReturned="+extensionReturned+" qualifiedR161Prefix="+qualifiedPrefix+" gaInitReturned="+gaReturned+" gaWhole="+gaWhole+" firstGaCpuProvenanceRecipe="+gaCpuReturned+" lastGaCpuPieces="+gaCpuPieces+"\n"
                +"FIRST_ERROR (bounded; none for returned-death/bound)\n"+error+"GA_MILESTONE\n"+gaRow
                +"No direct public Ga Draw receipt exists. CPU provenance records selected pieces when present; culling/empty mapping can emit none. Returned native Init plus its unconditional Draw call path is source-backed evidence only, never visible Ga/parity.\n"
                +(jumpExtension?"NEW_JUMP_POLICY bounceCutReturned="+bounceCutReturned+" jumpRequests="+jumpRequests+" jumpExtensionReturned="+jumpExtensionReturned+" observedJumpState="+jumpObserved+"\n"+jumpFacts:"")
                +"LATE_WINDOW_MOTION_AND_TERRAIN_SAMPLES\n"+motionSamples
                +"LAST_COMPLETE_HEALTHY_COMPONENT_CUT\n"+last+cached
                +"TERMINAL_DIRECT_FRAME_AND_RAW_POOL_ONLY (not a closed World/selected image; cached healthy facts above remain a different cut)\n"+afterGaActualFacts(f,rawPool);
            int reportLimit=jumpExtension?65536:32768;
            if(report.length()>reportLimit)report=report.substring(0,reportLimit)+"\n[report truncated at"+reportLimit+" characters]\n";
            if(frontier!=null&&!(qualifiedPrefix&&gaReturned==1271&&retainedBoundReturned&&(!jumpExtension||bounceCutReturned)))throw(UnsupportedOperationException)frontier;
            assertTrue(retainedBoundReturned,"An earlier stop is not an after-retained-bound observation");
            assertTrue(extensionReturned<=256);
            if(jumpExtension){assertTrue(bounceCutReturned,"An earlier stop is not after the retained bounce cut");assertEquals(256,extensionReturned);
                assertTrue(jumpRequests<=12);assertTrue(jumpExtensionReturned<=512);
                if(frontier==null&&!returnedDeath)assertTrue(jumpObserved,"No native accepted jump was observed; "+jumpFacts+last);}
            assertTrue(qualifiedPrefix,"The exact returned R161 prefix must precede any later classification");assertEquals(1271,gaReturned,"Actual Ga Init/native Draw path must return, not only source presence");assertEquals(44,gaWhole);
            assertTrue(controllerAttempts<=maxControllerCalls);assertEquals(completedCalls+(frontier==null?0:1),controllerAttempts);
            if(frontier!=null)assertTrue(f.closed());else if(returnedDeath)assertTrue(f.state().motion().routine()>=6);else{assertFalse(f.closed());assertTrue(controllerAttempts+1>maxControllerCalls);assertEquals(256,extensionReturned);if(jumpExtension)assertEquals(512,jumpExtensionReturned);}
            System.out.println(report);
            if(outputDirectory!=null){if(!Files.isDirectory(outputDirectory,LinkOption.NOFOLLOW_LINKS)||!outputDirectory.equals(outputDirectory.toRealPath()))throw new IllegalArgumentException("Validated observer output directory changed");Files.writeString(outputDirectory.resolve(jumpExtension?"ENGINE-CCZ-after-retained-bounce-jump.txt":"ENGINE-CCZ-after-Ga-route-recon.txt"),report,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);}
        }
    }

    private static String afterGaRouteMotionSample(com.openggf.game.soniccd.player.CdPlayerFrameRuntime frame,
            SonicCdR31aWorldHost host,int recipe,int controllerCalls,int requested){
        // Called only after a healthy original return. No value feeds any input or native owner.
        var m=frame.state().motion();var c=frame.environment().controls();var ring=host.globals().ring();
        int x=m.centreX16_16()>>>16,y=m.centreY16_16()>>>16,flags=m.spriteFlags();
        var lookup=CdTerrainCollision.fromStageTerrain(host.currentTerrain());
        return "recipe="+recipe+" controllerCalls="+controllerCalls+" requested="+requested+" x="+x+" y="+y
            +" vx="+m.xVelocity()+" vy="+m.yVelocity()+" groundVelocity="+m.groundVelocity()+" flags="+m.flags()
            +" grounded="+((m.flags()&2)==0)+" held="+c.rawHeld()+" tap="+c.rawTap()+" locked="+c.controlLocked()
            +" routine="+m.routine()+" rings="+ring.rings()+" lives="+ring.lives()
            +" leftCell="+lookup.lookup((x-m.xRadius()-1)&65535,y,flags)
            +" rightCell="+lookup.lookup((x+m.xRadius()+1)&65535,y,flags)
            +" belowCell="+lookup.lookup(x,(y+m.yRadius()+1)&65535,flags)+"\n";
    }
    // END appended bounded after-Ga route reconnaissance.
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>

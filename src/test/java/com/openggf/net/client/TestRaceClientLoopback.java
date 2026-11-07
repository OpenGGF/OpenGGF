package com.openggf.net.client;

import openggf.racing.server.host.RaceHostImpl;

import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.host.ControlledRaceHost;
import com.openggf.net.host.RaceRoomHost;
import com.openggf.net.hub.HostRoundEngine;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import openggf.racing.protocol.ControlCodec;
import com.openggf.net.protocol.GhostPackets;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(60)
class TestRaceClientLoopback {
    private static final String FP = "0.6:cafe1234";
    private RaceRoomHost server;
    private ControlledRaceHost controlled;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private RaceRoomHost startServer(RaceHostImpl impl, Path dir, String policy)
            throws Exception {
        controlled = new ControlledRaceHost(impl);
        return controlled.start(0,
                new RoomHostConfig("LAN", "s3k", 0, 0, policy, null, 8, FP),
                PlayerIdentity.loadOrCreate(dir.resolve("host")),
                TrackValidationProfileSource.none());
    }

    private RaceClient connect(Path idDir, String name) throws Exception {
        return RaceClient.connect(URI.create("ws://127.0.0.1:" + server.port() + "/race"),
                PlayerIdentity.loadOrCreate(idDir), name, FP).get(10, TimeUnit.SECONDS);
    }

    private static RaceClient.InboundEvent await(
            RaceClient client, Predicate<RaceClient.InboundEvent> match) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            for (RaceClient.InboundEvent event : client.drainInbound()) {
                if (match.test(event)) {
                    return event;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("timed out");
    }

    @Test
    void competitionTeleportTravelsThroughTwoLoopbackClientsAndHostReceipts(@TempDir Path dir) throws Exception {
        controlled = new ControlledRaceHost();
        server = controlled.start(0,new RoomHostConfig("LAN","s2",0,0,"OPEN",null,8,FP),
                PlayerIdentity.loadOrCreate(dir.resolve("host")),
                (com.openggf.net.hub.KeyedTrackValidationProfileSource) key -> java.util.Optional.of(
                        new com.openggf.net.hub.TrackValidationProfile(4096,1024,32,60,0,0,0,13)));
        var configured = new java.util.concurrent.CompletableFuture<Void>();
        server.execute(()-> { server.room().configureTrackIdentity("ACT","COMPETITION"); configured.complete(null); });
        configured.get(5,TimeUnit.SECONDS);
        var a = connect(dir.resolve("a"),"A"); var b = connect(dir.resolve("b"),"B");
        try {
            controlled.startRound(new ControlMessage.RoundConfig("s2",0,0,600,"OPEN",null,
                    "ACT","COMPETITION","ITEMS",30));
            await(a,e -> e instanceof RaceClient.Control c && c.message() instanceof ControlMessage.RoundStart);
            await(b,e -> e instanceof RaceClient.Control c && c.message() instanceof ControlMessage.RoundStart);
            controlled.advance(10_000);
            a.sendControl(new ControlMessage.AttemptStart(1));
            b.sendControl(new ControlMessage.AttemptStart(1));
            for (var client:java.util.List.of(a,b)) {
                byte[] frames = new byte[3*GhostFrameCodec.BYTES];
                for(int i=0;i<3;i++) GhostFrameCodec.encode(new GhostFrame(1100+client.playerSlot()*200+i,100,0,
                        false,false,false,2,false,false,1,false,0),frames,i*GhostFrameCodec.BYTES);
                client.sendBinary(GhostPackets.encodeFrames(1,0,frames));
            }
            await(a,e -> e instanceof RaceClient.GhostData);
            await(b,e -> e instanceof RaceClient.GhostData);
            a.sendControl(new ControlMessage.TeleportRequest(1,2));
            var ca = (ControlMessage.TeleportSwap)((RaceClient.Control)await(a,
                    e -> e instanceof RaceClient.Control c && c.message() instanceof ControlMessage.TeleportSwap)).message();
            var cb = (ControlMessage.TeleportSwap)((RaceClient.Control)await(b,
                    e -> e instanceof RaceClient.Control c && c.message() instanceof ControlMessage.TeleportSwap)).message();
            assertEquals(ca,cb);
            a.sendControl(new ControlMessage.TeleportOutcome(ca.swapId(),3,"APPLIED"));
            b.sendControl(new ControlMessage.TeleportOutcome(cb.swapId(),3,"APPLIED"));
            // A room-thread barrier observes both outcome messages before inspecting the log.
            a.sendControl(new ControlMessage.Chat("barrier"));
            await(b,e -> e instanceof RaceClient.Control c && c.message() instanceof ControlMessage.ChatBroadcast);
            b.sendControl(new ControlMessage.Chat("barrier2"));
            await(a,e -> e instanceof RaceClient.Control c && c.message() instanceof ControlMessage.ChatBroadcast chat
                    && chat.text().equals("barrier2"));
            var receipts = new java.util.concurrent.CompletableFuture<java.util.List<com.openggf.net.hub.HostTeleportSwap.Decision>>();
            server.execute(()->receipts.complete(com.openggf.net.hub.HostTeleportSwap.logFor(server.room())));
            var log = receipts.get(5,TimeUnit.SECONDS);
            assertEquals(1,log.size()); assertEquals(2,log.getFirst().outcomes().size());
            for(var client:java.util.List.of(a,b)) {
                var t = ca.slotA()==client.playerSlot()?ca.targetB():ca.targetA();
                var event = new com.openggf.mods.run.RaceEvent.SwapApply(ca.swapId(),new com.openggf.mods.run.SwapTarget(
                        t.centreX(),t.centreY(),t.xVel(),t.yVel(),t.facingLeft(),t.secondaryPath(),t.priorityBucket(),
                        t.highPriority(),t.shoeKind(),t.shoeTimer(),t.progress()));
                var recording = new openggf.timeattack.AttemptInputRecording(new openggf.timeattack.AttemptStartDescriptor(
                        "s2",0,0,"sonic",FP,com.openggf.mods.run.TrackKind.ACT,
                        openggf.timeattack.CompetitionRoomSettings.Mode.COMPETITION,com.openggf.mods.run.MonitorRule.COMPETITION_ITEMS,
                        com.openggf.game.session.GameplayRunPolicy.DeathRestart.FAST_FROM_CHECKPOINT,true));
                recording.appendEvent(3,event);
                for(int i=0;i<4;i++)recording.appendFrame(8,false);
                var decoded=openggf.timeattack.AttemptInputRecording.decode(recording.encode());
                var receiptsForSlot=openggf.racing.server.master.VerificationJobQueue.swapReceipts(log,client.playerSlot());
                assertTrue(openggf.racing.server.verifier.VerifierSwapLog.matches(decoded,receiptsForSlot));
                verifySyntheticSwap(dir.resolve("verifier-"+client.playerSlot()),decoded,receiptsForSlot,client.playerSlot(),ca.slotA());
                byte[] applied=new byte[GhostFrameCodec.BYTES];
                GhostFrameCodec.encode(new GhostFrame(t.centreX(),t.centreY(),0,false,false,false,2,false,false,1,false,0),applied,0);
                client.sendBinary(GhostPackets.encodeFrames(1,3,applied));
            }
            await(a,e -> e instanceof RaceClient.GhostData g && g.aggregate().entries().stream()
                    .anyMatch(entry -> entry.playerSlot()==b.playerSlot() && entry.startFrameIndex()+entry.frameCount()>3));
            await(b,e -> e instanceof RaceClient.GhostData g && g.aggregate().entries().stream()
                    .anyMatch(entry -> entry.playerSlot()==a.playerSlot() && entry.startFrameIndex()+entry.frameCount()>3));
        } finally { a.close(); b.close(); }
    }

    private openggf.racing.server.verifier.AttemptReplayHarness.Result syntheticReplay(
            openggf.timeattack.AttemptInputRecording input,int slot,int requester) {
        try {
            var hash=java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes=new byte[GhostFrameCodec.BYTES];
            int x=1099+slot*200,y=100,priority=2,progress=1,shoes=0;boolean facing=false,path=false,high=false;
            for(int frame=0;frame<input.frameCount();frame++) {
                int step=frame;
                var event=input.events().stream().filter(e->e.step()==step).findFirst();
                if(event.isPresent()) {
                    var t=((com.openggf.mods.run.RaceEvent.SwapApply)event.get().event()).target();
                    x=t.centreX();y=t.centreY();priority=t.priorityBucket();progress=t.progress();
                    shoes=t.shoeKind();facing=t.facingLeft();path=t.secondaryPath();high=t.highPriority();
                } else {
                    int mask=input.heldMaskAt(frame);
                    if((mask&8)!=0)x++;if((mask&4)!=0)x--;
                }
                GhostFrameCodec.encode(new GhostFrame(x,y,0,facing,false,false,priority,high,false,progress,path,shoes),bytes,0);
                hash.update(bytes);
            }
            int finish=input.frameCount()-1;
            return new openggf.racing.server.verifier.AttemptReplayHarness.Result(true,0,finish,finish,
                    java.util.HexFormat.of().formatHex(hash.digest()),input.frameCount(),null,java.util.List.of(),java.util.List.of(),
                    com.openggf.mods.run.RaceClock.Failure.NONE,slot==requester?java.util.List.of(2):java.util.List.of());
        } catch(Exception e){throw new IllegalStateException(e);}
    }

    private void verifySyntheticSwap(Path identityDir,openggf.timeattack.AttemptInputRecording recording,
            java.util.List<openggf.racing.server.master.VerificationJobQueue.SwapReceipt> receipts,
            int slot,int requester) throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var expected=syntheticReplay(recording,slot,requester);
        for(int variant=0;variant<3;variant++) {
            var submitted=new openggf.timeattack.AttemptInputRecording(recording.start());
            var original=(com.openggf.mods.run.RaceEvent.SwapApply)recording.events().getFirst().event();
            var t=original.target();
            var event=variant==1?new com.openggf.mods.run.RaceEvent.SwapApply(original.swapId(),new com.openggf.mods.run.SwapTarget(
                    t.centreX()+1,t.centreY(),t.xVel(),t.yVel(),t.facingLeft(),t.secondaryPath(),t.priorityBucket(),
                    t.highPriority(),t.shoeKind(),t.shoeTimer(),t.progress())):original;
            submitted.appendEvent(3,event);
            for(int frame=0;frame<4;frame++)submitted.appendFrame(variant==2 && frame==1?4:recording.heldMaskAt(frame),false);
            var job=new openggf.racing.server.master.VerificationJobQueue.Job("synthetic-"+slot,"room",slot,"player","attempt",
                    FP,"s2:0:0","sonic",3,0,3,java.util.HexFormat.of().formatHex(submitted.sha256()),expected.ghostStreamHashHex(),false,0,
                    receipts,java.util.List.of(),java.util.List.of(),
                    new openggf.racing.server.master.VerificationJobQueue.LaunchPolicy("COMPETITION","ITEMS",true));
            var verdict=new java.util.concurrent.atomic.AtomicReference<String>();
            var api=new openggf.racing.server.verifier.VerifierWorker.MasterApi() {
                public String register(byte[] key,java.util.Set<String> fingerprints){return "test";}
                public java.util.Optional<String> pollJobJson(){try{return java.util.Optional.of(json.writeValueAsString(job));}catch(Exception e){throw new RuntimeException(e);}}
                public void heartbeat(){}
                public byte[] fetchRecording(String hash){return submitted.encode();}
                public boolean postVerdict(String id,String result,byte[] signature){verdict.set(result);return true;}
            };
            var worker=new openggf.racing.server.verifier.VerifierWorker(api,(input,fp)->syntheticReplay(input,slot,requester),
                    PlayerIdentity.loadOrCreate(identityDir));
            assertTrue(worker.pollOnce());
            assertEquals(switch(variant) {
                case 0 -> com.openggf.net.protocol.VerdictCodec.RESULT_PASS;
                case 1 -> com.openggf.net.protocol.VerdictCodec.RESULT_FAIL_DIVERGENT;
                default -> com.openggf.net.protocol.VerdictCodec.RESULT_FAIL_GHOST_HASH;
            },verdict.get());
        }
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void connectsChatsAndStreamsGhostFrames(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = startServer(impl, dir, "OPEN");
        RaceClient a = connect(dir.resolve("a"), "A");
        RaceClient b = connect(dir.resolve("b"), "B");
        assertEquals(0, a.playerSlot());
        assertEquals(1, b.playerSlot());
        assertTrue(a.isOpen());

        a.sendControl(new ControlMessage.Chat("hello lan"));
        RaceClient.InboundEvent chat = await(b, e -> e instanceof RaceClient.Control c
                && c.message() instanceof ControlMessage.ChatBroadcast);
        assertEquals("hello lan",
                ((ControlMessage.ChatBroadcast) ((RaceClient.Control) chat).message()).text());

        controlled.startRound(new ControlMessage.RoundConfig(
                "s3k", 0, 0, 10, "OPEN", null));
        await(a, e -> e instanceof RaceClient.Control c
                && c.message() instanceof ControlMessage.RoundStart);
        controlled.advance(HostRoundEngine.COUNTDOWN_MILLIS);

        byte[] frame = new byte[GhostFrameCodec.BYTES];
        GhostFrameCodec.encode(new GhostFrame(100, 200, 1,
                false, false, false, 2, false), frame, 0);
        a.sendControl(new ControlMessage.AttemptStart(1));
        a.sendBinary(GhostPackets.encodeFrames(1, 0, frame));
        RaceClient.InboundEvent aggregate = await(b, e -> e instanceof RaceClient.GhostData);
        GhostPackets.Aggregate decoded = ((RaceClient.GhostData) aggregate).aggregate();
        assertEquals(0, decoded.entries().get(0).playerSlot());

        a.close();
        await(b, e -> e instanceof RaceClient.Control c
                && c.message() instanceof ControlMessage.PlayerLeft left
                && left.slot() == a.joinAccepted().playerSlot());
        b.close();
    }

    @ParameterizedTest
    @EnumSource(RaceHostImpl.class)
    void fingerprintMismatchSurfacesJoinRejected(RaceHostImpl impl, @TempDir Path dir)
            throws Exception {
        server = startServer(impl, dir, "OPEN");
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> RaceClient.connect(
                                URI.create("ws://127.0.0.1:" + server.port() + "/race"),
                                PlayerIdentity.loadOrCreate(dir.resolve("c")),
                                "C", "0.6:deadbeef")
                        .get(10, TimeUnit.SECONDS));
        assertInstanceOf(RaceClient.JoinRejectedException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("fingerprint"));
    }

    @Test
    void connectToDeadPortFailsCleanlyWithinTimeout(@TempDir Path dir) throws Exception {
        long start = System.currentTimeMillis();
        assertThrows(ExecutionException.class,
                () -> RaceClient.connect(URI.create("ws://127.0.0.1:1/race"),
                                PlayerIdentity.loadOrCreate(dir.resolve("d")), "D", FP)
                        .get(15, TimeUnit.SECONDS));
        assertTrue(System.currentTimeMillis() - start < 15_000);
    }

    @Test
    void serverThatAcceptsButNeverAnswersFailsWithinJoinTimeout(@TempDir Path dir) throws Exception {
        try (java.net.ServerSocket silent = new java.net.ServerSocket(0)) {
            long start = System.currentTimeMillis();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> RaceClient.connect(
                                    URI.create("ws://127.0.0.1:" + silent.getLocalPort() + "/race"),
                                    PlayerIdentity.loadOrCreate(dir.resolve("e")), "E", FP)
                            .get(15, TimeUnit.SECONDS));
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed < 10_000, "failed in " + elapsed + "ms — join timeout did not fire");
            assertNotNull(failure.getCause());
        }
    }

    @Test
    void malformedFinalJoinClosesDirectConnection(@TempDir Path dir) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        AtomicReference<Channel> peer = new AtomicReference<>();
        Channel listening = null;
        try {
            String invalidJoin = ControlCodec.encode(null,
                    new ControlMessage.JoinAccepted("room-token", -1, null, null));
            listening = new ServerBootstrap().group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new HttpServerCodec());
                            channel.pipeline().addLast(new HttpObjectAggregator(8192));
                            channel.pipeline().addLast(
                                    new WebSocketServerProtocolHandler("/race"));
                            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override public void userEventTriggered(
                                        ChannelHandlerContext context, Object event)
                                        throws Exception {
                                    if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                                        peer.set(context.channel());
                                        context.writeAndFlush(new TextWebSocketFrame(invalidJoin));
                                    }
                                    super.userEventTriggered(context, event);
                                }
                            });
                        }
                    }).bind(0).sync().channel();
            int port = ((java.net.InetSocketAddress) listening.localAddress()).getPort();
            ExecutionException rejected = assertThrows(ExecutionException.class,
                    () -> RaceClient.connect(URI.create("ws://127.0.0.1:" + port + "/race"),
                                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "Guest", FP)
                            .get(5, TimeUnit.SECONDS));
            assertInstanceOf(com.openggf.net.protocol.ProtocolViolationException.class,
                    rejected.getCause());
            Channel connection = peer.get();
            assertNotNull(connection);
            assertTrue(connection.closeFuture().await(5, TimeUnit.SECONDS),
                    "malformed room admission must close the direct socket");
        } finally {
            if (listening != null) listening.close().sync();
            group.shutdownGracefully().sync();
        }
    }

    @Test
    void unacknowledgedDirectCloseIsAReachabilityFailure(@TempDir Path dir) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        Channel listening = null;
        try {
            listening = new ServerBootstrap().group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new HttpServerCodec());
                            channel.pipeline().addLast(new HttpObjectAggregator(8192));
                            channel.pipeline().addLast(new WebSocketServerProtocolHandler("/race"));
                            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override public void userEventTriggered(
                                        ChannelHandlerContext context, Object event)
                                        throws Exception {
                                    if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                                        context.channel().close();
                                    }
                                    super.userEventTriggered(context, event);
                                }
                            });
                        }
                    }).bind(0).sync().channel();
            int port = ((java.net.InetSocketAddress) listening.localAddress()).getPort();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> RaceClient.connect(URI.create("ws://127.0.0.1:" + port + "/race"),
                            PlayerIdentity.loadOrCreate(dir.resolve("guest")), "Guest", FP)
                            .get(5, TimeUnit.SECONDS));
            assertInstanceOf(RaceClient.DirectRouteLostException.class, failure.getCause());
            assertTrue(MasterClient.allowsRelayFallback(failure));
        } finally {
            if (listening != null) listening.close().sync();
            group.shutdownGracefully().sync();
        }
    }
}

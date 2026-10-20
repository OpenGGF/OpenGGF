package openggf.racing.server.master;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestMasterHttpAdmission {
    @Test
    void brokerDrainYieldsAfterOneBoundedSliceUnderContinuousArrival() {
        var broker = new ArrayDeque<Runnable>();
        var admission = new MasterHttpAdmission(broker::add);
        var executed = new AtomicInteger();
        class Arrival implements Runnable {
            @Override
            public void run() {
                int count = executed.incrementAndGet();
                if (count < 100) {
                    var next = admission.reserve(1);
                    assertNotNull(next);
                    admission.dispatch(next, () -> {
                        try { run(); } finally { next.finish(); }
                    }, () -> { });
                }
            }
        }
        var first = admission.reserve(1);
        assertNotNull(first);
        admission.dispatch(first, () -> {
            try { new Arrival().run(); } finally { first.finish(); }
        }, () -> { });
        broker.remove().run();
        assertEquals(32, executed.get(), "HTTP work must yield the broker to other queued tasks");
        assertEquals(1, broker.size());
        while (!broker.isEmpty()) broker.remove().run();
        assertEquals(100, executed.get());
    }

    @Test
    void rejectedDrainContinuationCancelsRemainingRequestsAndReleasesTheirBodies() {
        var broker = new ArrayDeque<Runnable>();
        var submissions = new AtomicInteger();
        var admission = new MasterHttpAdmission(task -> {
            if (submissions.getAndIncrement() > 0) throw new RejectedExecutionException("stopped");
            broker.add(task);
        });
        var executed = new AtomicInteger();
        var rejected = new AtomicInteger();
        class Arrival implements Runnable {
            @Override
            public void run() {
                executed.incrementAndGet();
                var next = admission.reserve(1);
                assertNotNull(next);
                admission.dispatch(next, () -> {
                    try { run(); } finally { next.finish(); }
                }, rejected::incrementAndGet);
            }
        }
        var first = admission.reserve(1);
        assertNotNull(first);
        admission.dispatch(first, () -> {
            try { new Arrival().run(); } finally { first.finish(); }
        }, rejected::incrementAndGet);
        broker.remove().run();
        assertEquals(32, executed.get());
        assertEquals(1, rejected.get());
        var available = admission.reserve(8 * 1024 * 1024);
        assertNotNull(available);
        available.finish();
        assertEquals(0, broker.size());
    }

    @Test
    void disconnectDuringHandlerKeepsItsBodyReservedUntilHandlerReturns() {
        var broker = new ArrayDeque<Runnable>();
        var admission = new MasterHttpAdmission(broker::add);
        var releasedDuringHandler = new AtomicBoolean();
        var channel = new EmbeddedChannel();
        channel.pipeline().addLast(new MasterHttpRoutes(MasterConfig.defaults(),
                (java.util.function.Function<String, Optional<String>>) token -> {
                    channel.close();
                    var premature = admission.reserve(8 * 1024 * 1024);
                    releasedDuringHandler.set(premature != null);
                    if (premature != null) premature.finish();
                    return Optional.empty();
                }, null, null, null, null, () -> 0, broker::add, (job, pass) -> { },
                new HashMap<>(), admission));
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT,
                "/recordings/" + "0".repeat(64), Unpooled.wrappedBuffer(new byte[4096]));
        request.headers().set("Authorization", "Bearer invalid");
        try {
            channel.writeInbound(request);
            broker.remove().run();
            channel.runPendingTasks();
            assertFalse(releasedDuringHandler.get());
            var available = admission.reserve(8 * 1024 * 1024);
            assertNotNull(available);
            available.finish();
            assertNull(channel.readOutbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void serverWideJobBoundReleasesOnDisconnectWithoutQueueingCanceledTasks() {
        var broker = new ArrayDeque<Runnable>();
        var admission = new MasterHttpAdmission(broker::add);
        var channels = new ArrayList<EmbeddedChannel>();
        try {
            for (int index = 0; index < 32; index++) {
                var channel = admittedChannel(admission, broker::add, MasterConfig.defaults());
                channels.add(channel);
                channel.writeInbound(request(new byte[4096]));
                assertNull(channel.readOutbound());
            }
            var rejected = admittedChannel(admission, broker::add, MasterConfig.defaults());
            channels.add(rejected);
            rejected.writeInbound(request(new byte[4096]));
            assertStatus(rejected, HttpResponseStatus.SERVICE_UNAVAILABLE);
            assertFalse(rejected.isOpen());

            channels.getFirst().close();
            // A closed connection must immediately free its body and global slot.
            // Repeated connect/close cycles must not grow the broker's task queue.
            for (int index = 0; index < 100; index++) {
                var replacement = admittedChannel(admission, broker::add, MasterConfig.defaults());
                replacement.writeInbound(request(new byte[4096]));
                assertNull(replacement.readOutbound());
                replacement.finishAndReleaseAll();
            }
            assertEquals(1, broker.size());
            broker.remove().run();
            for (var channel : channels) channel.runPendingTasks();
            assertStatus(channels.get(1), HttpResponseStatus.UNAUTHORIZED);
            assertTrue(channels.get(1).config().isAutoRead());
            channels.get(1).writeInbound(request(new byte[4096]));
            assertEquals(1, broker.size(), "ordinary completion must allow sequential requests");
            broker.remove().run();
            channels.get(1).runPendingTasks();
            assertStatus(channels.get(1), HttpResponseStatus.UNAUTHORIZED);
        } finally {
            for (var channel : channels) channel.finishAndReleaseAll();
        }
    }

    @Test
    void serverWideCopiedBodiesCannotExceedEightMiBEvenWithLargerConfiguredBodies() throws Exception {
        var config = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                "{\"maxRecordingBytes\":1048576}", MasterConfig.class);
        var broker = new ArrayDeque<Runnable>();
        var admission = new MasterHttpAdmission(broker::add);
        var channels = new ArrayList<EmbeddedChannel>();
        try {
            for (int index = 0; index < 8; index++) {
                var channel = admittedChannel(admission, broker::add, config);
                channels.add(channel);
                channel.writeInbound(request(new byte[1024 * 1024]));
                assertNull(channel.readOutbound());
            }
            var rejected = admittedChannel(admission, broker::add, config);
            channels.add(rejected);
            rejected.writeInbound(request(new byte[1]));
            assertStatus(rejected, HttpResponseStatus.SERVICE_UNAVAILABLE);
            channels.getFirst().close();
            var replacement = admittedChannel(admission, broker::add, config);
            channels.add(replacement);
            replacement.writeInbound(request(new byte[1024 * 1024]));
            assertNull(replacement.readOutbound());
            assertEquals(1, broker.size());
        } finally {
            for (var channel : channels) channel.finishAndReleaseAll();
        }
    }

    @Test
    void timeoutAndInboundFailureCancelQueuedWorkAndRestoreGlobalCapacity() {
        for (boolean timeout : new boolean[]{true, false}) {
            var broker = new ArrayDeque<Runnable>();
            var admission = new MasterHttpAdmission(broker::add);
            var channel = admittedChannel(admission, broker::add, MasterConfig.defaults());
            try {
                channel.writeInbound(request(new byte[4096]));
                if (timeout) {
                    channel.advanceTimeBy(31, TimeUnit.SECONDS);
                    channel.runScheduledPendingTasks();
                } else {
                    channel.pipeline().fireExceptionCaught(new IllegalStateException("decoder failure"));
                }
                assertFalse(channel.isOpen());
                var available = admission.reserve(8 * 1024 * 1024);
                assertNotNull(available, "cancellation must release body accounting");
                available.finish();
                broker.remove().run();
                assertNull(channel.readOutbound());
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    @Test
    void oversizedSecondHeaderClosesWithoutWritingAheadOfFirstResponse() {
        var broker = new ArrayDeque<Runnable>();
        var admission = new MasterHttpAdmission(broker::add);
        var routes = routes(admission, broker::add, MasterConfig.defaults());
        var channel = new EmbeddedChannel(new HttpServerCodec(), routes.pipelineGuard(),
                new HttpObjectAggregator(MasterHttpRoutes.maxHttpBodyBytes(MasterConfig.defaults())), routes);
        try {
            channel.writeInbound(Unpooled.copiedBuffer(
                    "POST /verifier/verdicts HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\n\r\n"
                            + "POST /verifier/verdicts HTTP/1.1\r\nHost: localhost\r\nContent-Length: 999999\r\n\r\n",
                    StandardCharsets.US_ASCII));
            assertFalse(channel.isOpen());
            assertNull(channel.readOutbound(), "aggregator must not emit a reordered 413");
            broker.remove().run();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void oversizedBodyIsReleasedBeforeDispatch() {
        var broker = new ArrayDeque<Runnable>();
        var admission = new MasterHttpAdmission(broker::add);
        var channel = admittedChannel(admission, broker::add, MasterConfig.defaults());
        var request = request(new byte[73_729]);
        try {
            channel.writeInbound(request);
            assertStatus(channel, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE);
            assertEquals(0, request.refCnt());
            assertEquals(0, broker.size());
            assertFalse(channel.isOpen());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void failedResponseWriteReleasesReservationAndClosesConnection() {
        var admission = new MasterHttpAdmission(Runnable::run);
        var channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                io.netty.util.ReferenceCountUtil.release(message);
                promise.setFailure(new IllegalStateException("broken socket"));
            }
        }, routes(admission, Runnable::run, MasterConfig.defaults()));
        try {
            channel.writeInbound(request(new byte[4096]));
            channel.runPendingTasks();
            assertFalse(channel.isOpen());
            var available = admission.reserve(8 * 1024 * 1024);
            assertNotNull(available);
            available.finish();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static MasterHttpRoutes routes(MasterHttpAdmission admission, Executor broker,
                                            MasterConfig config) {
        return new MasterHttpRoutes(config,
                (java.util.function.Function<String, Optional<String>>) token -> Optional.empty(),
                null, null, null, null, () -> 0, broker, (job, pass) -> { },
                new HashMap<>(), admission);
    }

    private static EmbeddedChannel admittedChannel(MasterHttpAdmission admission, Executor broker,
                                                   MasterConfig config) {
        return new EmbeddedChannel(routes(admission, broker, config));
    }

    private static DefaultFullHttpRequest request(byte[] body) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "/verifier/verdicts", Unpooled.wrappedBuffer(body));
    }

    private static void assertStatus(EmbeddedChannel channel, HttpResponseStatus status) {
        FullHttpResponse response = channel.readOutbound();
        assertNotNull(response);
        try {
            assertEquals(status, response.status());
        } finally {
            response.release();
        }
    }

    @Test
    void disconnectBeforeBrokerExecutionDiscardsBodyAndDoesNotInvokeHandler() {
        var pending = new ArrayDeque<Runnable>();
        var calls = new AtomicInteger();
        var routes = new MasterHttpRoutes(MasterConfig.defaults(),
                (java.util.function.Function<String, Optional<String>>) token -> {
                    calls.incrementAndGet();
                    return Optional.empty();
                }, null, null, null, null, () -> 0, pending::add, (job, pass) -> { });
        var channel = new EmbeddedChannel(routes);
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT,
                "/recordings/" + "0".repeat(64), Unpooled.wrappedBuffer(new byte[4096]));
        request.headers().set("Authorization", "Bearer invalid");
        try {
            channel.writeInbound(request);
            assertEquals(0, request.refCnt());
            channel.close();
            while (!pending.isEmpty()) pending.remove().run();
            channel.runPendingTasks();
            assertEquals(0, calls.get(), "closed requests must not reach application handlers");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void brokerRejectionProducesOneResponseAndClosesWithoutLeakingRequest() {
        var routes = new MasterHttpRoutes(MasterConfig.defaults(),
                (java.util.function.Function<String, Optional<String>>) token -> Optional.empty(),
                null, null, null, null, () -> 0, task -> {
                    throw new RejectedExecutionException("stopped");
                }, (job, pass) -> { });
        var channel = new EmbeddedChannel(routes);
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "/verifier/verdicts", Unpooled.wrappedBuffer(new byte[32]));
        try {
            channel.writeInbound(request);
            assertEquals(0, request.refCnt());
            FullHttpResponse response = channel.readOutbound();
            assertNotNull(response);
            assertEquals(HttpResponseStatus.SERVICE_UNAVAILABLE, response.status());
            response.release();
            assertFalse(channel.isOpen());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerFailureReleasesAdmissionForNextKeepAliveRequest() {
        var calls = new AtomicInteger();
        var routes = new MasterHttpRoutes(MasterConfig.defaults(),
                (java.util.function.Function<String, Optional<String>>) token -> {
                    if (calls.getAndIncrement() == 0) throw new IllegalStateException("broken store");
                    return Optional.empty();
                }, null, null, null, null, () -> 0, Runnable::run, (job, pass) -> { });
        var channel = new EmbeddedChannel(routes);
        try {
            for (var status : new HttpResponseStatus[]{HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseStatus.UNAUTHORIZED}) {
                var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT,
                        "/recordings/" + "0".repeat(64));
                request.headers().set("Authorization", "Bearer invalid");
                channel.writeInbound(request);
                channel.runPendingTasks();
                FullHttpResponse response = channel.readOutbound();
                assertNotNull(response);
                assertEquals(status, response.status());
                response.release();
                assertTrue(channel.isOpen());
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void oneInboundReadCannotDispatchAThousandUnauthenticatedBodies() {
        var pending = new ArrayDeque<Runnable>();
        var routes = new MasterHttpRoutes(MasterConfig.defaults(),
                (java.util.function.Function<String, Optional<String>>) token -> Optional.empty(),
                null, null, null, null, () -> 0, pending::add, (job, pass) -> { });
        var channel = new EmbeddedChannel(new HttpServerCodec(),
                new HttpObjectAggregator(73_728), routes);
        try {
            String request = "POST /verifier/verdicts HTTP/1.1\r\nHost: localhost\r\n"
                    + "Content-Length: 4096\r\n\r\n" + "x".repeat(4096);
            channel.writeInbound(Unpooled.copiedBuffer(request.repeat(1000),
                    StandardCharsets.US_ASCII));
            assertEquals(1, pending.size(), "pipeline must not queue copied request bodies");
            assertFalse(channel.isOpen(), "overlapping requests must close without reordered responses");
            pending.remove().run();
            channel.runPendingTasks();
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}

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
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class TestMasterHttpAdmission {
    @Test
    void unsupportedExpectationStormHasOneTerminalResponseAndBoundedCleanup() {
        assertExpectationRejectionBounded("unsupported", 0, HttpResponseStatus.EXPECTATION_FAILED);
    }

    @Test
    void oversizedContinueExpectationStormHasOneTerminalResponseAndBoundedCleanup() {
        assertExpectationRejectionBounded("100-continue", 73_729,
                HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE);
    }

    @Test
    void oversizedDeclaredBodyWithoutExpectationHasOneTerminalResponseAndBoundedCleanup() {
        assertPreAggregationRejectionBounded("POST /verifier/verdicts HTTP/1.1\r\n"
                        + "Host: localhost\r\nContent-Length: 73729\r\n\r\n", 1000,
                "oversized declared body", HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE);
    }

    @Test
    void oversizedChunkedBodyHasOneTerminalResponseAndBoundedCleanup() {
        assertPreAggregationRejectionBounded("POST /verifier/verdicts HTTP/1.1\r\n"
                        + "Host: localhost\r\nTransfer-Encoding: chunked\r\n\r\n12001\r\n"
                        + "x".repeat(73_729) + "\r\n0\r\n\r\n", 3,
                "oversized chunked body", HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE);
    }

    @Test
    void mixedExpectationFieldsCannotHideAnUnsupportedValue() {
        for (String expectations : new String[]{"100-continue\r\nExpect: unsupported",
                "unsupported\r\nExpect: 100-continue", "100-continue, unsupported"}) {
            assertExpectationRejectionBounded(expectations, 0, HttpResponseStatus.EXPECTATION_FAILED);
        }
    }

    private static void assertExpectationRejectionBounded(String expectation, int contentLength,
                                                           HttpResponseStatus status) {
        assertPreAggregationRejectionBounded("POST /verifier/verdicts HTTP/1.1\r\nHost: localhost\r\nExpect: "
                + expectation + "\r\nContent-Length: " + contentLength + "\r\n\r\n", 1000,
                expectation, status);
    }

    private static void assertPreAggregationRejectionBounded(String request, int repetitions,
                                                               String scenario, HttpResponseStatus status) {
        for (String completion : new String[]{"storm", "timeout", "success", "disconnect", "removal"}) {
            var broker = new ArrayDeque<Runnable>();
            var admission = new MasterHttpAdmission(broker::add);
            var held = new ArrayList<Object>();
            var promises = new ArrayList<ChannelPromise>();
            var reads = new AtomicInteger();
            var routes = routes(admission, broker::add, MasterConfig.defaults());
            var channel = new EmbeddedChannel(new HttpServerCodec(), new ChannelOutboundHandlerAdapter() {
                @Override
                public void read(ChannelHandlerContext context) {
                    reads.incrementAndGet();
                    context.read();
                }

                @Override
                public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                    held.add(message);
                    promises.add(promise);
                }
            }, routes.pipelineGuard(), new HttpObjectAggregator(73_728), routes);
            var wire = Unpooled.copiedBuffer(request.repeat(completion.equals("storm") ? repetitions : 1),
                    StandardCharsets.US_ASCII);
            try {
                int previousReads = reads.get();
                channel.writeInbound(wire);
                assertEquals(1, held.size(), scenario + ": rejected requests must not amplify writes");
                assertEquals(status, ((FullHttpResponse) held.getFirst()).status());
                assertEquals("close", ((FullHttpResponse) held.getFirst()).headers().get("Connection"));
                assertFalse(channel.config().isAutoRead());
                assertEquals(previousReads, reads.get(), "terminal rejection must not request more input");
                assertNull(channel.pipeline().get(HttpObjectAggregator.class), "release partial aggregation");
                // The default decoder can independently close a storm at its pipeline-depth cap.
                // A single request must remain open so its held write actually tests the deadline.
                if (!completion.equals("storm")) {
                    assertTrue(channel.isOpen(), "the test holds the response write pending");
                }
                assertEquals(0, wire.refCnt());
                assertTrue(broker.isEmpty());
                var available = admission.reserve(8 * 1024 * 1024);
                assertNotNull(available, "rejected headers must not retain a body reservation");
                available.finish();
                switch (completion) {
                    case "timeout" -> {
                        channel.advanceTimeBy(29, TimeUnit.SECONDS);
                        channel.runScheduledPendingTasks();
                        assertTrue(channel.isOpen());
                        channel.advanceTimeBy(2, TimeUnit.SECONDS);
                        channel.runScheduledPendingTasks();
                    }
                    case "storm", "success" -> promises.getFirst().trySuccess();
                    case "disconnect" -> channel.close();
                    case "removal" -> channel.pipeline().remove(routes);
                    default -> throw new AssertionError(completion);
                }
                assertFalse(channel.isOpen(), completion);
                promises.getFirst().trySuccess();
                assertFalse(channel.config().isAutoRead());
                assertEquals(-1L, channel.runScheduledPendingTasks(), "deadline must be cleaned up");
            } finally {
                held.forEach(io.netty.util.ReferenceCountUtil::release);
                promises.forEach(promise -> promise.trySuccess());
                channel.finishAndReleaseAll();
            }
        }
    }

    @Test
    void splitChunkedOverflowReleasesPartialAggregateWithoutAnotherRead() {
        var admission = new MasterHttpAdmission(Runnable::run);
        var held = new ArrayList<Object>();
        var promises = new ArrayList<ChannelPromise>();
        var reads = new AtomicInteger();
        var routes = routes(admission, Runnable::run, MasterConfig.defaults());
        var channel = new EmbeddedChannel(new HttpServerCodec(), new ChannelOutboundHandlerAdapter() {
            @Override
            public void read(ChannelHandlerContext context) {
                reads.incrementAndGet();
                context.read();
            }

            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                held.add(message);
                promises.add(promise);
            }
        }, routes.pipelineGuard(), new HttpObjectAggregator(73_728), routes);
        var prefix = Unpooled.copiedBuffer("POST /verifier/verdicts HTTP/1.1\r\nHost: localhost\r\n"
                + "Transfer-Encoding: chunked\r\n\r\n12000\r\n" + "x".repeat(73_728) + "\r\n",
                StandardCharsets.US_ASCII);
        var overflow = Unpooled.copiedBuffer("1\r\nx\r\n0\r\n\r\n", StandardCharsets.US_ASCII);
        try {
            channel.writeInbound(prefix);
            assertTrue(prefix.refCnt() > 0, "positive control: aggregator owns the incomplete body");
            assertTrue(held.isEmpty());
            int previousReads = reads.get();
            channel.writeInbound(overflow);
            assertEquals(1, held.size());
            assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, ((FullHttpResponse) held.getFirst()).status());
            assertEquals(previousReads, reads.get(), "partial aggregator must not request another read");
            assertNull(channel.pipeline().get(HttpObjectAggregator.class));
            assertEquals(0, prefix.refCnt(), "release the partial body before the response completes");
            assertEquals(0, overflow.refCnt());
            assertFalse(channel.config().isAutoRead());
            assertTrue(channel.isOpen());
            channel.advanceTimeBy(31, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();
            assertFalse(channel.isOpen());
        } finally {
            held.forEach(io.netty.util.ReferenceCountUtil::release);
            promises.forEach(promise -> promise.trySuccess());
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void removingGuardClosesActiveOrTerminalExchangeAndReleasesItsState() {
        for (String exchange : new String[]{"aggregating", "broker", "terminal"}) {
            var broker = new ArrayDeque<Runnable>();
            var admission = new MasterHttpAdmission(broker::add);
            var held = new ArrayList<Object>();
            var promises = new ArrayList<ChannelPromise>();
            var routes = routes(admission, broker::add, MasterConfig.defaults());
            var guard = routes.pipelineGuard();
            var channel = new EmbeddedChannel(new HttpServerCodec(), new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                    held.add(message);
                    promises.add(promise);
                }
            }, guard, new HttpObjectAggregator(73_728), routes);
            String request = exchange.equals("terminal")
                    ? "POST /verifier/verdicts HTTP/1.1\r\nExpect: unsupported\r\nContent-Length: 0\r\n\r\n"
                    : "POST /verifier/verdicts HTTP/1.1\r\nContent-Length: 16\r\n\r\n"
                    + "x".repeat(exchange.equals("aggregating") ? 4 : 16);
            var wire = Unpooled.copiedBuffer(request, StandardCharsets.US_ASCII);
            try {
                channel.writeInbound(wire);
                assertTrue(channel.isOpen());
                channel.pipeline().remove(guard);
                assertFalse(channel.isOpen(), exchange);
                assertEquals(0, wire.refCnt(), exchange);
                while (!broker.isEmpty()) broker.remove().run();
                var available = admission.reserve(8 * 1024 * 1024);
                assertNotNull(available, exchange);
                available.finish();
                assertEquals(exchange.equals("terminal") ? 1 : 0, held.size(), exchange);
                assertEquals(-1L, channel.runScheduledPendingTasks());
            } finally {
                held.forEach(io.netty.util.ReferenceCountUtil::release);
                promises.forEach(promise -> promise.trySuccess());
                channel.finishAndReleaseAll();
            }
        }
    }

    @Test
    void idleGuardPassesWebSocketFramesAndCanBeRemovedWithoutClosing() {
        var admission = new MasterHttpAdmission(Runnable::run);
        var routes = routes(admission, Runnable::run, MasterConfig.defaults());
        var guard = routes.pipelineGuard();
        var channel = new EmbeddedChannel(guard, routes);
        var frame = new TextWebSocketFrame("upgraded traffic");
        try {
            channel.writeInbound(frame);
            assertEquals(frame, channel.readInbound());
            channel.pipeline().remove(guard);
            assertTrue(channel.isOpen());
        } finally {
            frame.release();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void validContinueExpectationStillAggregatesAndAllowsSequentialKeepAlive() {
        var broker = new ArrayDeque<Runnable>();
        var admission = new MasterHttpAdmission(broker::add);
        var statuses = new ArrayList<HttpResponseStatus>();
        var routes = routes(admission, broker::add, MasterConfig.defaults());
        var channel = new EmbeddedChannel(new HttpServerCodec(), new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise)
                    throws Exception {
                if (message instanceof HttpResponse response) statuses.add(response.status());
                super.write(context, message, promise);
            }
        }, routes.pipelineGuard(), new HttpObjectAggregator(73_728), routes);
        try {
            channel.writeInbound(Unpooled.copiedBuffer("POST /verifier/verdicts HTTP/1.1\r\n"
                    + "Host: localhost\r\nExpect: 100-CONTINUE\r\nContent-Length: 16\r\n\r\n",
                    StandardCharsets.US_ASCII));
            assertEquals(java.util.List.of(HttpResponseStatus.CONTINUE), statuses);
            assertTrue(broker.isEmpty());
            channel.writeInbound(Unpooled.copiedBuffer("x".repeat(16), StandardCharsets.US_ASCII));
            assertEquals(1, broker.size());
            broker.remove().run();
            channel.runPendingTasks();
            channel.writeInbound(Unpooled.copiedBuffer("POST /verifier/verdicts HTTP/1.1\r\n"
                    + "Host: localhost\r\nContent-Length: 0\r\n\r\n", StandardCharsets.US_ASCII));
            assertEquals(1, broker.size());
            broker.remove().run();
            channel.runPendingTasks();
            String chunked = "POST /verifier/verdicts HTTP/1.1\r\nHost: localhost\r\n"
                    + "Transfer-Encoding: chunked\r\n\r\n12000\r\n" + "x".repeat(73_728) + "\r\n0\r\n\r\n";
            for (String next : new String[]{chunked, chunked,
                    "POST /verifier/verdicts HTTP/1.0\r\nHost: localhost\r\nConnection: keep-alive\r\n"
                            + "Expect: unsupported\r\nContent-Length: 0\r\n\r\n"}) {
                channel.writeInbound(Unpooled.copiedBuffer(next, StandardCharsets.US_ASCII));
                assertEquals(1, broker.size());
                broker.remove().run();
                channel.runPendingTasks();
            }
            assertEquals(java.util.List.of(HttpResponseStatus.CONTINUE,
                    HttpResponseStatus.UNAUTHORIZED, HttpResponseStatus.UNAUTHORIZED,
                    HttpResponseStatus.UNAUTHORIZED, HttpResponseStatus.UNAUTHORIZED,
                    HttpResponseStatus.UNAUTHORIZED), statuses);
            assertTrue(channel.isOpen());
            assertTrue(channel.config().isAutoRead());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void malformedKeepAliveResponsePausesReadsAndRejectsSameReadOverlap() {
        for (boolean guard : new boolean[]{false, true}) {
            for (String completion : new String[]{"success", "overlap", "timeout"}) {
                var admission = new MasterHttpAdmission(Runnable::run);
                var held = new ArrayList<Object>();
                var promises = new ArrayList<ChannelPromise>();
                var routes = routes(admission, Runnable::run, MasterConfig.defaults());
                var channel = new EmbeddedChannel(new HttpServerCodec(), new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                        held.add(message);
                        promises.add(promise);
                    }
                });
                if (guard) channel.pipeline().addLast(routes.pipelineGuard());
                channel.pipeline().addLast(new HttpObjectAggregator(73_728), routes);
                String request = "POST /verifier/%zz HTTP/1.1\r\nHost: localhost\r\n"
                        + "Content-Length: 16\r\n\r\n" + "x".repeat(16);
                var wire = Unpooled.copiedBuffer(request.repeat(completion.equals("overlap") ? 1000 : 1),
                        StandardCharsets.US_ASCII);
                try {
                    channel.writeInbound(wire);
                    assertEquals(1, held.size(), completion);
                    assertFalse(channel.config().isAutoRead(), completion);
                    assertEquals(0, wire.refCnt());
                    if (completion.equals("success")) {
                        assertTrue(channel.isOpen());
                        promises.getFirst().trySuccess();
                        channel.runPendingTasks();
                        assertTrue(channel.isOpen());
                        assertTrue(channel.config().isAutoRead());
                    } else {
                        if (completion.equals("timeout")) {
                            channel.advanceTimeBy(31, TimeUnit.SECONDS);
                            channel.runScheduledPendingTasks();
                        }
                        assertFalse(channel.isOpen(), completion);
                        promises.getFirst().trySuccess();
                        assertFalse(channel.config().isAutoRead(), completion);
                    }
                } finally {
                    held.forEach(io.netty.util.ReferenceCountUtil::release);
                    promises.forEach(promise -> promise.trySuccess());
                    channel.finishAndReleaseAll();
                }
            }
        }
    }

    @Test
    void terminalRejectionDeadlineClosesStalledWrite() {
        for (String failure : new String[]{"exhausted", "oversized", "dispatch", "malformed"}) {
            var admission = new MasterHttpAdmission(task -> {
                throw new RejectedExecutionException("stopped");
            });
            var occupied = failure.equals("exhausted")
                    ? admission.reserve(8 * 1024 * 1024) : null;
            var held = new ArrayList<Object>();
            var promises = new ArrayList<ChannelPromise>();
            var routes = routes(admission, Runnable::run, MasterConfig.defaults());
            var channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                    held.add(message);
                    promises.add(promise);
                }
            }, routes.pipelineGuard(), routes);
            var first = request(new byte[failure.equals("oversized") ? 73_729 : 16]);
            if (failure.equals("malformed")) {
                first.setUri("/verifier/%zz");
                first.headers().set("Connection", "close");
            }
            var second = request(new byte[16]);
            try {
                channel.writeInbound(first, second);
                assertEquals(1, held.size(), failure);
                assertFalse(channel.config().isAutoRead(), failure);
                assertEquals(0, first.refCnt());
                assertEquals(0, second.refCnt());
                channel.advanceTimeBy(29, TimeUnit.SECONDS);
                channel.runScheduledPendingTasks();
                assertTrue(channel.isOpen(), failure);
                channel.advanceTimeBy(2, TimeUnit.SECONDS);
                channel.runScheduledPendingTasks();
                assertFalse(channel.isOpen(), failure + ": stalled write must have a close deadline");
            } finally {
                held.forEach(io.netty.util.ReferenceCountUtil::release);
                promises.forEach(promise -> promise.trySuccess());
                channel.finishAndReleaseAll();
                if (occupied != null) occupied.finish();
            }
        }
    }

    @Test
    void throwingRejectionCallbackDoesNotAbortContinuationCleanup() {
        var broker = new ArrayDeque<Runnable>();
        var submissions = new AtomicInteger();
        var admission = new MasterHttpAdmission(task -> {
            if (submissions.getAndIncrement() == 1) throw new RejectedExecutionException("stopped");
            broker.add(task);
        });
        var executed = new AtomicInteger();
        var rejected = new AtomicInteger();
        class Arrival implements Runnable {
            @Override
            public void run() {
                if (executed.incrementAndGet() < 32) {
                    var next = admission.reserve(1);
                    assertNotNull(next);
                    admission.dispatch(next, () -> {
                        try { run(); } finally { next.finish(); }
                    }, () -> { });
                } else {
                    for (int index = 0; index < 2; index++) {
                        var next = admission.reserve(1);
                        assertNotNull(next);
                        admission.dispatch(next, () -> { }, () -> {
                            if (rejected.getAndIncrement() == 0) {
                                throw new IllegalStateException("broken rejection callback");
                            }
                        });
                    }
                }
            }
        }
        var first = admission.reserve(1);
        assertNotNull(first);
        admission.dispatch(first, () -> {
            try { new Arrival().run(); } finally { first.finish(); }
        }, () -> { });
        assertDoesNotThrow(() -> broker.remove().run());
        assertEquals(32, executed.get());
        assertEquals(2, rejected.get());
        var available = admission.reserve(8 * 1024 * 1024);
        assertNotNull(available);
        admission.dispatch(available, available::finish, () -> { });
        assertEquals(1, broker.size(), "later dispatch must restart the broker drain");
        broker.remove().run();
        var reused = admission.reserve(8 * 1024 * 1024);
        assertNotNull(reused);
        reused.finish();
    }

    @Test
    void exhaustedAdmissionKeepsOnlyOneRejectionWhileOutboundPromiseStalls() {
        var broker = new ArrayDeque<Runnable>();
        var admission = new MasterHttpAdmission(broker::add);
        var occupied = admission.reserve(8 * 1024 * 1024);
        assertNotNull(occupied);
        var held = new ArrayList<Object>();
        var promises = new ArrayList<ChannelPromise>();
        var routes = routes(admission, broker::add, MasterConfig.defaults());
        var channel = new EmbeddedChannel(new HttpServerCodec(), new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                held.add(message);
                promises.add(promise);
            }
        }, routes.pipelineGuard(), new HttpObjectAggregator(73_728), routes);
        var wire = Unpooled.copiedBuffer(("POST /verifier/verdicts HTTP/1.1\r\n"
                + "Host: localhost\r\nContent-Length: 16\r\n\r\n" + "x".repeat(16)).repeat(1000),
                StandardCharsets.US_ASCII);
        try {
            channel.writeInbound(wire);
            assertEquals(1, held.size(), "a held rejection must block further same-read responses");
            assertFalse(channel.config().isAutoRead());
            assertEquals(0, wire.refCnt(), "discarded same-read bodies must be released");
            assertEquals(0, broker.size());
        } finally {
            held.forEach(io.netty.util.ReferenceCountUtil::release);
            promises.forEach(promise -> promise.trySuccess());
            channel.finishAndReleaseAll();
            occupied.finish();
        }
    }

    @Test
    void allTerminalRejectionsDiscardFurtherFullRequestsWhileWriteStalls() {
        for (String failure : new String[]{"exhausted", "oversized", "dispatch"}) {
            for (boolean guard : new boolean[]{false, true}) {
                var admission = new MasterHttpAdmission(task -> {
                    throw new RejectedExecutionException("stopped");
                });
                var occupied = failure.equals("exhausted")
                        ? admission.reserve(8 * 1024 * 1024) : null;
                var held = new ArrayList<Object>();
                var promises = new ArrayList<ChannelPromise>();
                var routes = routes(admission, Runnable::run, MasterConfig.defaults());
                var channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                        held.add(message);
                        promises.add(promise);
                    }
                });
                if (guard) channel.pipeline().addLast(routes.pipelineGuard());
                channel.pipeline().addLast(routes);
                var first = request(new byte[failure.equals("oversized") ? 73_729 : 16]);
                var second = request(new byte[16]);
                try {
                    channel.writeInbound(first, second);
                    assertEquals(1, held.size(), failure + ": terminal response must be unique");
                    assertFalse(channel.config().isAutoRead(), failure);
                    assertEquals(0, first.refCnt());
                    assertEquals(0, second.refCnt());
                } finally {
                    held.forEach(io.netty.util.ReferenceCountUtil::release);
                    promises.forEach(promise -> promise.trySuccess());
                    channel.finishAndReleaseAll();
                    if (occupied != null) occupied.finish();
                }
            }
        }
    }

    @Test
    void completionAndTimerCancellationRejectionStillReleaseAndPermitLaterBrokerWork() throws Exception {
        var broker = new ArrayDeque<Runnable>();
        var admission = new MasterHttpAdmission(broker::add);
        var routes = routes(admission, broker::add, MasterConfig.defaults());
        var channel = new EmbeddedChannel();
        var context = mock(ChannelHandlerContext.class);
        var executor = mock(EventExecutor.class);
        var timeout = mock(ScheduledFuture.class);
        when(context.channel()).thenReturn(channel);
        when(context.executor()).thenReturn(executor);
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(timeout);
        doThrow(new RejectedExecutionException("completion executor stopped"))
                .when(executor).execute(any(Runnable.class));
        when(timeout.cancel(false)).thenThrow(new RejectedExecutionException("timer removal stopped"));
        when(context.close()).thenAnswer(call -> channel.close());
        try {
            routes.channelRead(context, request(new byte[4096]));
            assertDoesNotThrow(() -> broker.remove().run());
            var available = admission.reserve(8 * 1024 * 1024);
            assertNotNull(available, "timer cancellation failure must not leak the ticket");
            var completed = new AtomicBoolean();
            admission.dispatch(available, () -> {
                completed.set(true);
                available.finish();
            }, () -> { });
            assertEquals(1, broker.size(), "drain scheduling must remain live");
            broker.remove().run();
            assertTrue(completed.get());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void escapingBrokerActionDoesNotStrandOtherOrFutureRequests() {
        var broker = new ArrayDeque<Runnable>();
        var admission = new MasterHttpAdmission(broker::add);
        var failed = admission.reserve(4096);
        var healthy = admission.reserve(4096);
        assertNotNull(failed);
        assertNotNull(healthy);
        var completed = new AtomicInteger();
        admission.dispatch(failed, () -> {
            throw new IllegalStateException("unexpected cleanup failure");
        }, () -> { });
        admission.dispatch(healthy, () -> {
            completed.incrementAndGet();
            healthy.finish();
        }, () -> { });
        assertDoesNotThrow(() -> broker.remove().run());
        while (!broker.isEmpty()) broker.remove().run();
        assertEquals(1, completed.get());
        var available = admission.reserve(8 * 1024 * 1024);
        assertNotNull(available, "escaping actions must release their reservation");
        admission.dispatch(available, () -> {
            completed.incrementAndGet();
            available.finish();
        }, () -> { });
        assertEquals(1, broker.size());
        broker.remove().run();
        assertEquals(2, completed.get());
    }

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

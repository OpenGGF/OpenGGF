package com.openggf.net.master;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.VerdictCodec;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Bounded HTTP recording and verifier API mounted ahead of the master WebSocket. */
public final class MasterHttpRoutes extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final ObjectMapper JSON = new ObjectMapper();

    private record RegisterRequest(String pubKeyBase64, Set<String> fingerprints) { }
    private record RegisterResponse(String workerId, String workerToken) { }
    private record VerdictRequest(String jobId, String result, String signatureBase64) { }
    private record RequestData(HttpMethod method, String path, String authorization,
                               String workerId, byte[] body, boolean keepAlive) { }
    private record ResponseData(HttpResponseStatus status, String contentType, byte[] body) { }

    private final MasterConfig config;
    private final Predicate<String> sessionTokenValid;
    private final RecordingBlobStore blobs;
    private final VerifierRegistry verifiers;
    private final VerificationJobQueue jobs;
    private final VerdictConsequences consequences;
    private final LongSupplier clock;
    private final Executor brokerLoop;
    private final BiConsumer<VerificationJobQueue.Job, Boolean> verdictRouter;

    public MasterHttpRoutes(MasterConfig config, Predicate<String> sessionTokenValid,
                            RecordingBlobStore blobs, VerifierRegistry verifiers,
                            VerificationJobQueue jobs, VerdictConsequences consequences,
                            LongSupplier clock, Executor brokerLoop,
                            BiConsumer<VerificationJobQueue.Job, Boolean> verdictRouter) {
        this.config = config;
        this.sessionTokenValid = sessionTokenValid;
        this.blobs = blobs;
        this.verifiers = verifiers;
        this.jobs = jobs;
        this.consequences = consequences;
        this.clock = clock;
        this.brokerLoop = brokerLoop;
        this.verdictRouter = verdictRouter;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) {
        String path = new QueryStringDecoder(request.uri()).path();
        if (!path.startsWith("/recordings") && !path.startsWith("/verifier")) {
            context.fireChannelRead(request.retain());
            return;
        }
        RequestData data = new RequestData(request.method(), path,
                request.headers().get(HttpHeaderNames.AUTHORIZATION),
                request.headers().get("X-Worker-Id"),
                ByteBufUtil.getBytes(request.content()), HttpUtil.isKeepAlive(request));
        brokerLoop.execute(() -> {
            ResponseData response;
            try {
                response = handle(data);
            } catch (IllegalArgumentException badRequest) {
                response = response(HttpResponseStatus.BAD_REQUEST);
            } catch (RuntimeException serverFailure) {
                response = response(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
            write(context, response, data.keepAlive());
        });
    }

    private ResponseData handle(RequestData request) {
        if (request.method().equals(HttpMethod.PUT)
                && request.path().startsWith("/recordings/")) {
            return putRecording(request);
        }
        if (request.method().equals(HttpMethod.POST)
                && request.path().equals("/verifier/register")) {
            return register(request);
        }
        Optional<VerifierRegistry.Worker> worker = authenticateWorker(request);
        if (request.path().startsWith("/verifier")
                || request.method().equals(HttpMethod.GET)) {
            if (worker.isEmpty()) {
                return response(HttpResponseStatus.UNAUTHORIZED);
            }
        }
        if (request.method().equals(HttpMethod.GET)
                && request.path().equals("/verifier/jobs")) {
            return jobs.lease(worker.orElseThrow().workerId(),
                            worker.orElseThrow().fingerprints())
                    .map(job -> json(HttpResponseStatus.OK, job))
                    .orElseGet(() -> response(HttpResponseStatus.NO_CONTENT));
        }
        if (request.method().equals(HttpMethod.GET)
                && request.path().startsWith("/recordings/")) {
            String hash = recordingHash(request.path());
            return blobs.get(hash)
                    .map(bytes -> new ResponseData(HttpResponseStatus.OK,
                            "application/octet-stream", bytes))
                    .orElseGet(() -> response(HttpResponseStatus.NOT_FOUND));
        }
        if (request.method().equals(HttpMethod.POST)
                && request.path().equals("/verifier/verdicts")) {
            return postVerdict(request, worker.orElseThrow());
        }
        return response(HttpResponseStatus.NOT_FOUND);
    }

    private ResponseData putRecording(RequestData request) {
        String token = bearer(request.authorization());
        if (token == null || !sessionTokenValid.test(token)) {
            return response(HttpResponseStatus.UNAUTHORIZED);
        }
        if (request.body().length > config.maxRecordingBytes()) {
            return response(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE);
        }
        String hash = recordingHash(request.path());
        String actual = HexFormat.of().formatHex(sha256(request.body()));
        if (!actual.equals(hash)) {
            return response(HttpResponseStatus.BAD_REQUEST);
        }
        blobs.put(hash, request.body());
        jobs.onRecordingUploaded(hash);
        return response(HttpResponseStatus.NO_CONTENT);
    }

    private ResponseData register(RequestData request) {
        String registrationToken = config.verifierRegistrationToken();
        if (registrationToken == null || registrationToken.isBlank()) {
            return response(HttpResponseStatus.NOT_FOUND);
        }
        if (!registrationToken.equals(bearer(request.authorization()))) {
            return response(HttpResponseStatus.UNAUTHORIZED);
        }
        try {
            RegisterRequest registration = JSON.readValue(request.body(), RegisterRequest.class);
            byte[] publicKey = Base64.getDecoder().decode(registration.pubKeyBase64());
            VerifierRegistry.Worker worker = verifiers.register(publicKey,
                    registration.fingerprints() == null ? Set.of()
                            : registration.fingerprints());
            return json(HttpResponseStatus.OK,
                    new RegisterResponse(worker.workerId(), worker.workerToken()));
        } catch (Exception invalid) {
            return response(HttpResponseStatus.BAD_REQUEST);
        }
    }

    private ResponseData postVerdict(RequestData request,
                                     VerifierRegistry.Worker worker) {
        try {
            VerdictRequest verdict = JSON.readValue(request.body(), VerdictRequest.class);
            if (!VerdictCodec.isWorkerResult(verdict.result())) {
                return response(HttpResponseStatus.BAD_REQUEST);
            }
            VerificationJobQueue.Job job = jobs.find(verdict.jobId()).orElse(null);
            if (job == null) {
                return response(HttpResponseStatus.CONFLICT);
            }
            byte[] signature = Base64.getDecoder().decode(verdict.signatureBase64());
            if (!PlayerIdentity.verify(worker.publicKeyEncoded(),
                    VerdictCodec.canonicalBytes(job.jobId(), job.attemptRef(),
                            job.inputRecordingHashHex(), verdict.result()), signature)) {
                return response(HttpResponseStatus.BAD_REQUEST);
            }
            job = jobs.complete(job.jobId(), worker.workerId()).orElse(null);
            if (job == null) {
                return response(HttpResponseStatus.CONFLICT);
            }
            boolean pass = consequences.apply(new IdentityStore.VerdictRecord(
                    job.identityFingerprint(), job.attemptRef(),
                    job.inputRecordingHashHex(), verdict.result(),
                    verdict.signatureBase64(), clock.getAsLong()), worker.workerId());
            verdictRouter.accept(job, pass);
            return response(HttpResponseStatus.NO_CONTENT);
        } catch (IllegalArgumentException invalid) {
            return response(HttpResponseStatus.BAD_REQUEST);
        } catch (Exception invalid) {
            return response(HttpResponseStatus.BAD_REQUEST);
        }
    }

    private Optional<VerifierRegistry.Worker> authenticateWorker(RequestData request) {
        String token = bearer(request.authorization());
        if (request.workerId() == null || token == null) {
            return Optional.empty();
        }
        return verifiers.authenticate(request.workerId(), token);
    }

    private static String recordingHash(String path) {
        String prefix = "/recordings/";
        if (!path.startsWith(prefix) || path.length() != prefix.length() + 64) {
            throw new IllegalArgumentException("invalid recording path");
        }
        return path.substring(prefix.length());
    }

    private static String bearer(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()) : null;
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ResponseData json(HttpResponseStatus status, Object value) {
        try {
            return new ResponseData(status, "application/json",
                    JSON.writeValueAsBytes(value));
        } catch (Exception failure) {
            throw new IllegalStateException("unable to encode JSON", failure);
        }
    }

    private static ResponseData response(HttpResponseStatus status) {
        return new ResponseData(status, "text/plain; charset=utf-8", new byte[0]);
    }

    private static void write(ChannelHandlerContext context, ResponseData response,
                              boolean keepAlive) {
        var message = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                response.status(), Unpooled.wrappedBuffer(response.body()));
        message.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.body().length);
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, response.contentType());
        if (keepAlive) {
            message.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        context.writeAndFlush(message);
    }
}

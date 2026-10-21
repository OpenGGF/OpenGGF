package com.openggf.net.client;

import com.openggf.net.protocol.AuthenticationTranscript;
import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Observes the engine used by one dedicated HttpClient without changing its trust policy.
 * JDK WebSocket omits SSLSession; recording engine creation gives us that same validated
 * connection's session, rather than a separate TLS probe or an attacker-selected pin.
 */
final class ObservedTlsContext extends SSLContext {
    private final Observer observer;

    ObservedTlsContext(SSLContext delegate) { this(new Observer(delegate)); }
    private ObservedTlsContext(Observer observer) {
        super(observer, observer.delegate.getProvider(), observer.delegate.getProtocol());
        this.observer = observer;
    }

    String binding() {
        SSLEngine engine = observer.engine.get();
        if (engine == null || observer.ambiguous) throw new IllegalStateException("missing or ambiguous TLS connection");
        try {
            var peer = engine.getSession().getPeerCertificates();
            if (peer.length == 0) throw new IllegalStateException("TLS peer has no certificate");
            return AuthenticationTranscript.certificateBinding(peer[0]);
        } catch (SSLPeerUnverifiedException unverified) {
            throw new IllegalStateException("TLS peer was not validated", unverified);
        }
    }

    private static final class Observer extends SSLContextSpi {
        private final SSLContext delegate;
        private final AtomicReference<SSLEngine> engine = new AtomicReference<>();
        private volatile boolean ambiguous;
        private Observer(SSLContext delegate) { this.delegate = delegate; }
        private SSLEngine record(SSLEngine created) {
            if (!engine.compareAndSet(null, created)) ambiguous = true;
            return created;
        }
        @Override protected void engineInit(KeyManager[] keys, TrustManager[] trust, SecureRandom random)
                throws KeyManagementException { throw new KeyManagementException("observed context is already initialized"); }
        @Override protected SSLSocketFactory engineGetSocketFactory() { throw new UnsupportedOperationException("engine-only context"); }
        @Override protected SSLServerSocketFactory engineGetServerSocketFactory() { throw new UnsupportedOperationException("client-only context"); }
        @Override protected SSLEngine engineCreateSSLEngine() { return record(delegate.createSSLEngine()); }
        @Override protected SSLEngine engineCreateSSLEngine(String host, int port) { return record(delegate.createSSLEngine(host, port)); }
        @Override protected SSLSessionContext engineGetClientSessionContext() { return delegate.getClientSessionContext(); }
        @Override protected SSLSessionContext engineGetServerSessionContext() { return delegate.getServerSessionContext(); }
        @Override protected SSLParameters engineGetDefaultSSLParameters() { return delegate.getDefaultSSLParameters(); }
        @Override protected SSLParameters engineGetSupportedSSLParameters() { return delegate.getSupportedSSLParameters(); }
    }
}

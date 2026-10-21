package openggf.racing.server.host;

import com.openggf.net.protocol.AuthenticationTranscript;
import com.openggf.net.protocol.NetworkEndpointPolicy;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SslHandler;

/** Binds server verification to its own TLS engine, never a value from Hello. */
public final class NettyHandshakeBinding {
    private NettyHandshakeBinding() { }
    public static String localCertificate(Channel channel, String remoteHost) {
        var tls = channel.pipeline().get(SslHandler.class);
        if (tls != null) {
            var certificates = tls.engine().getSession().getLocalCertificates();
            if (certificates == null || certificates.length == 0) throw new IllegalStateException("TLS server has no certificate");
            return AuthenticationTranscript.certificateBinding(certificates[0]);
        }
        if (NetworkEndpointPolicy.literalLoopback(remoteHost)) return AuthenticationTranscript.LOCAL_PLAINTEXT;
        throw new IllegalArgumentException("plaintext peer must use literal loopback");
    }
}

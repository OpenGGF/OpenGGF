# Build with build-images.py, which supplies only the portable distribution.
# The checkout, ROMs and operator configuration are never the build context.
ARG JAVA_IMAGE=eclipse-temurin:25-jre-noble
FROM ${JAVA_IMAGE} AS runtime
ARG BUILD_IDENTITY_SHA256
LABEL org.opencontainers.image.source="https://github.com/OpenGGF/OpenGGF" \
      org.openggf.build-identity-sha256="${BUILD_IDENTITY_SHA256}"
RUN groupadd --gid 10001 openggf \
    && useradd --uid 10001 --gid 10001 --home-dir /data --no-create-home --shell /usr/sbin/nologin openggf \
    && mkdir /data /config \
    && chown 10001:10001 /data
COPY dist/ /opt/openggf/
ENV HOME=/data
WORKDIR /data
USER 10001:10001

FROM runtime AS master
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx1g"
EXPOSE 27900
# The admin listener is loopback-only. A small separate JVM makes an authenticated,
# non-mutating request; it does not inherit the server's heap reservation.
HEALTHCHECK --interval=15s --timeout=10s --start-period=30s --retries=3 \
    CMD JAVA_TOOL_OPTIONS=-Xmx32m /opt/openggf/bin/racing-server healthcheck --config /config/master.yaml
ENTRYPOINT ["/opt/openggf/bin/racing-server", "master"]
CMD ["--config", "/config/master.yaml", "--data", "/data"]

FROM runtime AS verifier
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx2g"
ENTRYPOINT ["/opt/openggf/bin/racing-server", "verifier"]

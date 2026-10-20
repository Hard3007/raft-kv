# syntax=docker/dockerfile:1
#
# Multi-stage, multi-arch image for one raft-kv node:
#   docker buildx build --platform linux/amd64,linux/arm64 -t raft-kv .
#
# Stage 1 always runs on the build machine's own architecture (--platform=$BUILDPLATFORM), so
# Gradle, javac and protoc run natively, once, even when the target is arm64. Java bytecode is
# architecture-independent, so its output is valid for every target.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk@sha256:23cc5734ff46e776f51ce66eb9ee08444fd47caabec0a95b7eb6e94be2d3696d AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :server:installDist --no-daemon --console=plain \
    && mkdir /data-skeleton

# Stage 2 is per target architecture, but it only copies files and sets metadata. There is no
# RUN step, so nothing ever executes under QEMU emulation.
FROM eclipse-temurin:21-jre@sha256:227118764cccd3c3294617061aaa93a08309c63a395eefa83e6b35032992ea99
COPY --from=build /src/server/build/install/raft-kv /opt/raft-kv
# An empty /data owned by the runtime user; a named volume mounted here inherits that ownership.
COPY --from=build --chown=1000:1000 /data-skeleton /data
USER 1000:1000

# Heap 256 MB. Netty's direct buffers, metaspace and the JIT code cache live outside the heap,
# so they are capped too, keeping the process well inside a 384 MB container limit.
ENV DATA_DIR=/data \
    GRPC_PORT=7000 \
    STATUS_PORT=8080 \
    JAVA_OPTS="-Xmx256m -XX:MaxMetaspaceSize=64m -XX:MaxDirectMemorySize=32m -XX:ReservedCodeCacheSize=32m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
EXPOSE 7000 8080
VOLUME /data
HEALTHCHECK --interval=5s --timeout=2s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8080/status || exit 1
ENTRYPOINT ["/opt/raft-kv/bin/raft-kv"]

# syntax=docker/dockerfile:1.7
#
# Speakeasy — single-image build.
#
# The Angular frontend is packaged into frontend.jar (see frontend/build.gradle)
# and served by Javalin from the classpath (RestApi.kt), so backend and frontend
# ship as one artifact. There is no separate web server.
#
# Build:  DOCKER_BUILDKIT=1 docker build -t speakeasy .
# Run:    see compose.yaml

##############################################################################
# Stage 1 — build
##############################################################################
FROM eclipse-temurin:17-jdk AS builder

# The Gradle daemon is pointless in a container: the JVM dies with the RUN step.
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

WORKDIR /src

# --- Build scripts first ---------------------------------------------------
# These change rarely, so dependency resolution below stays in the layer cache
# across most deploys, even when application sources change.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
COPY backend/build.gradle backend/settings.gradle backend/gradle.properties ./backend/
COPY frontend/build.gradle ./frontend/
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN chmod +x gradlew

# --- Step 1: generate the OpenAPI client -----------------------------------
# frontend/openapi is gitignored but imported by the Angular sources, so a fresh
# checkout CANNOT build the frontend until this has run. It must be a separate
# gradlew invocation: buildFrontend declares no dependency on openApiGenerate,
# so listing both tasks in one command does not guarantee the ordering.
COPY docs ./docs
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew openApiGenerate

# --- Step 2: build the Angular frontend ------------------------------------
# By far the slowest step (npm install + ng build), so it gets its own layer:
# backend-only changes reuse it entirely.
# The cache mounts keep server-side rebuilds fast — without them every deploy
# re-downloads Node 20.11, the npm tree and the Gradle distribution.
COPY frontend ./frontend
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/root/.npm \
    --mount=type=cache,target=/src/frontend/.gradle \
    ./gradlew :frontend:packageFrontend

# --- Step 3: build the backend and lay out the distribution ----------------
# installDist (not distTar) — no tarball to unpack afterwards.
COPY backend ./backend
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/root/.npm \
    --mount=type=cache,target=/src/frontend/.gradle \
    ./gradlew :backend:installDist

##############################################################################
# Stage 2 — runtime
##############################################################################
FROM eclipse-temurin:17-jre AS runtime

# Must match the owner of the mounted ./data and ./logs directories on the host,
# or the app cannot write its database. Override with --build-arg if the server
# account is not uid 1000.
ARG APP_UID=1000
ARG APP_GID=1000

# curl is only here for the HEALTHCHECK below.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# The Ubuntu 24.04 base already ships a "ubuntu" user and group at 1000, which
# collides with the common case of APP_UID=1000. Drop it, then create ours.
RUN if getent passwd ubuntu >/dev/null; then userdel --remove ubuntu; fi \
    && if ! getent group "${APP_GID}" >/dev/null; then groupadd --gid "${APP_GID}" speakeasy; fi \
    && useradd --uid "${APP_UID}" --gid "${APP_GID}" --no-create-home --home-dir /app --shell /bin/bash speakeasy

COPY --from=builder --chown=root:root /src/backend/build/install/backend /opt/speakeasy
COPY --chown=root:root docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh /opt/speakeasy/bin/backend

# /opt/speakeasy  the distribution, read-only at runtime
# /app            working directory — log4j2.xml writes to a RELATIVE "logs" dir,
#                 so logs land in /app/logs and must be mounted from there
# /data           --datapath: database.db, feedbackforms/, feedbackresults/,
#                 sessions.csv, smtp.properties
# /config         optional config.json, mounted read-only
RUN mkdir -p /app/logs /data /config \
    && chown -R "${APP_UID}:${APP_GID}" /app /data

WORKDIR /app
USER speakeasy
ENV HOME="/app"

# backend/build.gradle hardcodes applicationDefaultJvmArgs = -Xms4G -Xmx16G,
# which will fight (or blow through) a container memory limit. The generated
# start script appends JAVA_OPTS after those defaults and the last -Xmx wins.
# Note -XX:MaxRAMPercentage would NOT work here: an explicit -Xmx always takes
# precedence over it, regardless of ordering.
ENV JAVA_OPTS="-Xms512m -Xmx2g"

# So log timestamps and exported CSVs match the old server.
ENV TZ="Europe/Zurich"

ENV SPEAKEASY_DATA_PATH="/data" \
    SPEAKEASY_CONFIG="/config/config.json"

EXPOSE 8080 8443

# GET / serves the Angular index.html and needs no authentication.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/ >/dev/null || exit 1

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]

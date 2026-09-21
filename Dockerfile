# Speakeasy — single-image build.
#
# The Angular frontend is packaged into frontend.jar (see frontend/build.gradle)
# and served by Javalin from the classpath (RestApi.kt), so backend and frontend
# ship as one artifact. There is no separate web server.
#
# Deliberately buildable by BOTH the legacy builder and BuildKit: it uses no
# BuildKit-only syntax, so it does not require the `docker buildx` plugin, which
# is not installed on every server. Ordinary layer caching does the work that
# `RUN --mount=type=cache` would otherwise do — see the note above step 1.
#
# Build:  docker build -t speakeasy .
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
#
# Downloads (the Gradle distribution, dependency jars, Node 20.11, the npm tree)
# land in this stage's own layers rather than in a cache mount. They are
# therefore still reused by the later steps and by subsequent builds, for as
# long as the layers above stay valid. It costs some builder-image size, and a
# change to an early COPY forces a re-download — the trade for not depending on
# buildx. None of it reaches the runtime image, which is a separate stage.
COPY docs ./docs
RUN ./gradlew openApiGenerate

# --- Step 2a: install the npm dependencies ---------------------------------
# Keyed only on package.json, package-lock.json and build.gradle (copied above),
# so a source edit does not re-download the whole tree.
#
# The loop keeps npm's cache warm between attempts: npm cannot retry a tarball
# that dies mid-stream (ECONNRESET), and a failed RUN discards everything it
# fetched. maxsockets lowers npm's 15 parallel connections, which is what tends
# to trip firewalls and rate limits. Gradle's npm child process inherits these.
ENV npm_config_fetch_retries=5 \
    npm_config_fetch_retry_mintimeout=20000 \
    npm_config_fetch_retry_maxtimeout=120000 \
    npm_config_maxsockets=4
RUN for attempt in 1 2 3 4 5 6; do \
        ./gradlew :frontend:npmInstall && exit 0; \
        echo "npm install failed (attempt ${attempt}/6), retrying"; \
        sleep 5; \
    done; \
    exit 1

# --- Step 2b: build the Angular frontend -----------------------------------
# The slowest compile step, so it gets its own layer: backend-only changes
# reuse it entirely.
COPY frontend ./frontend
RUN ./gradlew :frontend:packageFrontend

# --- Step 3: build the backend and lay out the distribution ----------------
# installDist (not distTar) — no tarball to unpack afterwards.
COPY backend ./backend
RUN ./gradlew :backend:installDist

##############################################################################
# Stage 2 — runtime
##############################################################################
FROM eclipse-temurin:17-jre AS runtime

# The uid/gid the app runs as. /data and /app/logs below get chowned to this,
# and compose.yaml mounts named volumes there (populated from these paths on
# first use, ownership included) rather than host bind mounts, so there is no
# host directory to keep in sync with this anymore. Override with --build-arg
# if the server account is not uid 1000.
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
RUN chmod 755 /usr/local/bin/docker-entrypoint.sh /opt/speakeasy/bin/backend

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

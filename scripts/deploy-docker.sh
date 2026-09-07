#!/bin/bash

# Dockerized counterpart to deploy.sh.
#
# Same idea as before: run it from cron, it polls git and does nothing unless
# there is a new commit. Only the last two steps changed.
#
#   deploy.sh                        deploy-docker.sh
#   -----------------------------    --------------------------------
#   gradlew ... distTar              docker compose build
#   tar -xf ...                      (gone)
#   tmux kill-session / new-session  docker compose up -d
#
# The interactive CLI moves from `tmux attach -t speakeasy` to
# `docker attach speakeasy`. Detach with Ctrl-P Ctrl-Q, NOT Ctrl-C.
#
# Args:
#   --force-deploy : deploy even if there are no new commits
#   anything after -- is forwarded to the speakeasy binary
#
# Example crontab entry (every 5 minutes):
#   */5 * * * * $HOME/speakeasy/scripts/deploy-docker.sh

# Configuration. All overridable from the environment so the same script serves
# dev and production without being edited on the server — put them in the crontab
# line, e.g.
#   */5 * * * * SPEAKEASY_BRANCH=dockerize $HOME/speakeasy/scripts/deploy-docker.sh
REPO_DIR="${SPEAKEASY_REPO_DIR:-$HOME/speakeasy}"
LOG_FILE="${SPEAKEASY_DEPLOY_LOG:-$HOME/logs/deploy.log}"
BRANCH="${SPEAKEASY_BRANCH:-main}"
COMPOSE_PROJECT="${SPEAKEASY_COMPOSE_PROJECT:-speakeasy}"
CONTAINER_NAME="${SPEAKEASY_CONTAINER_NAME:-speakeasy}"

# The tmux session is no longer where the application runs — Docker owns that
# now. It hosts a persistent `docker attach` instead, so `tmux attach -t
# speakeasy` still lands you on the Speakeasy prompt exactly as before.
# Set SPEAKEASY_TMUX_CONSOLE=false to skip it.
TMUX_SESSION="${SPEAKEASY_TMUX_SESSION:-speakeasy}"

# Cache mounts in the Dockerfile need BuildKit. It is the default on modern
# Docker, but cron environments are minimal, so be explicit.
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# Ensure logs directory exists
mkdir -p "$HOME/logs"

# Nifty stolen from https://serverfault.com/questions/103501/how-can-i-fully-log-all-bash-scripts-actions
exec 3>&1 4>&2
trap 'exec 2>&4 1>&3' 0 1 2 3
exec 1>> "$LOG_FILE" 2>&1

# Flags
FORCE_DEPLOY=false
other_args=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --force-deploy)
      FORCE_DEPLOY=true
      shift
      ;;
    --)
      shift
      other_args=("$@")
      break
      ;;
    *)
      other_args+=("$1")
      shift
      ;;
  esac
done

# Log function
log() {
    echo "$(date +"%Y-%m-%d %H:%M:%S") - $1" >> "$LOG_FILE"
}

log "Starting deployment process on branch: $BRANCH..."
log "Forwarding these extra arguments: ${other_args[*]}"

cd "$REPO_DIR" || { log "Failed to navigate to repo directory"; exit 1; }

# Step 1: Check for updates on the specified branch
log "Checking for updates on branch: $BRANCH..."
git fetch origin $BRANCH
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/$BRANCH)

if [ "$LOCAL" != "$REMOTE" ] || [ "$FORCE_DEPLOY" = true ]; then
    log "New changes detected on branch $BRANCH. Pulling changes..."
    git checkout $BRANCH || { log "Failed to checkout branch $BRANCH"; exit 1; }
    git pull origin $BRANCH || { log "Failed to pull changes from branch $BRANCH"; exit 1; }
else
    log "No changes detected on branch $BRANCH. Exiting."
    exit 0
fi

# Make sure the mounted directories exist and belong to us before the container
# starts, otherwise Docker creates them as root and the app cannot write.
mkdir -p "$REPO_DIR/data" "$REPO_DIR/logs" "$REPO_DIR/config"

# The image builds a user with these ids so it can write to the mounts above.
# Auto-detected from the account cron runs as, which is the account that owns
# those directories. An explicitly exported value wins, if you need to override.
APP_UID="${APP_UID:-$(id -u)}"
APP_GID="${APP_GID:-$(id -g)}"
export APP_UID APP_GID
export SPEAKEASY_EXTRA_ARGS="${other_args[*]}"

# Step 2: Build the image
# The old container keeps serving traffic while this runs; only a successful
# build gets promoted in step 3.
log "Building Docker image..."
docker compose -p "$COMPOSE_PROJECT" build < /dev/null

EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
    log "Docker build failed with exit code $EXIT_CODE. Leaving the running container untouched."
    exit $EXIT_CODE
else
    log "Docker build completed successfully."
fi

# Step 3: Replace the running container
log "Restarting container..."
docker compose -p "$COMPOSE_PROJECT" up -d < /dev/null

EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
    log "docker compose up failed with exit code $EXIT_CODE."
    exit $EXIT_CODE
fi

# Step 4: Re-create the tmux console.
#
# `docker compose up -d` replaces the container, which drops any existing
# `docker attach`, so the session is rebuilt on every deploy. The retry loop
# reconnects on its own if the container is restarted later — by the restart
# policy, a healthcheck failure, or the next deployment.
#
# Note this preserves the old footgun as well as the old convenience: Ctrl-C in
# this session still reaches PID 1 and stops the application, exactly as it did
# when the JVM ran here directly. Detach with the usual tmux prefix + d.
if [ "${SPEAKEASY_TMUX_CONSOLE:-true}" = "true" ] && command -v tmux > /dev/null 2>&1; then
    log "Re-creating tmux console session: $TMUX_SESSION"
    tmux kill-session -t "$TMUX_SESSION" 2>/dev/null
    tmux new-session -d -s "$TMUX_SESSION" \
        "while true; do docker attach '$CONTAINER_NAME' || true; sleep 2; done"
elif [ "${SPEAKEASY_TMUX_CONSOLE:-true}" = "true" ]; then
    log "tmux not installed, skipping console session. Use 'docker attach $CONTAINER_NAME' instead."
fi

# Rebuilding on every deploy leaves untagged layers behind, which fills the disk
# within a few weeks. Only dangling images are removed; nothing tagged or in use.
log "Pruning dangling images..."
docker image prune -f < /dev/null

log "Application restarted successfully. Reach the CLI with: tmux attach -t $TMUX_SESSION"

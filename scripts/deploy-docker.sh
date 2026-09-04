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

# Configuration
REPO_DIR="$HOME/speakeasy"
LOG_FILE="$HOME/logs/deploy.log"
BRANCH="main"
COMPOSE_PROJECT="speakeasy"

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
APP_UID="$(id -u)"
APP_GID="$(id -g)"
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

# Rebuilding on every deploy leaves untagged layers behind, which fills the disk
# within a few weeks. Only dangling images are removed; nothing tagged or in use.
log "Pruning dangling images..."
docker image prune -f < /dev/null

log "Application restarted successfully. Attach to the CLI with: docker attach speakeasy"

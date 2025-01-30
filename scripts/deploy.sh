#!/bin/bash

# This script is used to deploy the application on a remote server.
# It should :
# 1. Pull the latest changes from the specified branch
#   - If there is no new commit, the script should exit
# 2. Run Gradle tasks to build the application
# 3. Restart the application in a tmux session

# Args :
# --force-deploy : Force the deployment even if there are no new changes
# Any other args/anything passed after -- will be transferred to speakeasy


# Configuration
REPO_DIR="$HOME/speakeasy"
LOG_FILE="$HOME/logs/deploy.log"
TMUX_SESSION="speakeasy"
BRANCH="main"

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

echo "Forwarding these extra arguments: ${other_args[@]}"
# Ensure logs directory exists
mkdir -p "$HOME/logs"

# Log function
log() {
    echo "$(date +"%Y-%m-%d %H:%M:%S") - $1" >> "$LOG_FILE"
}

log "Starting deployment process on branch: $BRANCH..."

cd "$REPO_DIR" || { log "Failed to navigate to repo directory"; exit 1; }

# Step 1: Check for updates on the specified branch
log "Checking for updates on branch: $BRANCH..."
git fetch origin $BRANCH
# rev-parse returns the commit hash
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

# Step 2: Run Gradle tasks
log "Running Gradle tasks..."
# https://stackoverflow.com/questions/50147013/why-does-running-gradlew-in-a-non-interactive-bash-session-closes-the-sessions
# gradlew uses exec to run java, thus swallowing the whole stdin
bash ./gradlew clean openApiGenerate packageFrontend distTar < /dev/null

EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
    log "Gradle tasks failed with exit code $EXIT_CODE. Exiting script."
    exit $EXIT_CODE
else
    log "Gradle tasks completed successfully."
fi

# Extract the generated tar file
log "Extracting tar file..."
tar -xf "$REPO_DIR/backend/build/distributions/backend-0.1.tar" -C "$REPO_DIR/backend/build/distributions/"

# Step 3: Restart the application in tmux
log "Restarting application in tmux session: $TMUX_SESSION..."
tmux kill-session -t "$TMUX_SESSION" 2>/dev/null
tmux new-session -d -s "$TMUX_SESSION"
# Using send-keys so the session is not killed upon error, so that any erro can be investigated in the session. 
tmux send-keys -t "$TMUX_SESSION" "$REPO_DIR/backend/build/distributions/backend-0.1/bin/backend ${other_args[@]}" Enter
log "Application restarted successfully."

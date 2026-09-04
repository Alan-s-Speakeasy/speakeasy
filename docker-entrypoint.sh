#!/bin/sh
#
# Speakeasy container entrypoint.
#
# Two jobs only:
#   1. Pass --config only when a config file is actually mounted. Main.kt
#      declares it with mustExist = true, so pointing it at a missing file is a
#      hard startup error rather than a fallback to defaults.
#   2. exec, so the JVM becomes PID 1 and receives the TTY and the signals
#      directly. Without exec the interactive CLI would be reading from a shell
#      that is no longer in the foreground.
set -eu

DATA_PATH="${SPEAKEASY_DATA_PATH:-/data}"
CONFIG_FILE="${SPEAKEASY_CONFIG:-/config/config.json}"

set -- --datapath "${DATA_PATH}" "$@"

if [ -f "${CONFIG_FILE}" ]; then
    echo "Using config file: ${CONFIG_FILE}"
    set -- --config "${CONFIG_FILE}" "$@"
else
    echo "No config file at ${CONFIG_FILE}, using built-in defaults."
fi

# Unquoted on purpose: this reproduces the argument passthrough that
# scripts/deploy.sh used to do with "${other_args[@]}".
# shellcheck disable=SC2086
exec /opt/speakeasy/bin/backend "$@" ${SPEAKEASY_EXTRA_ARGS:-}

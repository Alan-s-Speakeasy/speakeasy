<div align="center">

<img src="frontend/src/assets/logo.svg" width="200" alt="Logo">

# Alan's Speakeasy

</div>

Alan's Speakeasy is the backend infrastructure for the project accompanying the lecture *Advanced Topics in Artificial Intelligence* at the University of Zurich.
The repository is split into two components, **backend** and **frontend**. The backend is responsible for message routing, feedback collection and user management. It is written in *Kotlin*, uses *Javalin* as a web server and provides an *OpenAPI* compliant interface.
The frontend handles all direct interaction. It is built using *Angular*.


## Building and Running

*Gradle* is used as a build system for this project.

### Generating OpenAPI Specification and Client

To generate the OpenAPI Client binding for the frontend, use `scripts/fetch-and-generate-openapi.sh`.

This script will download the OpenAPI specification from swagger and then generate the TypeScript bindings.

### Running Speakeasy

In order to run the whole Speakeasy project, simply click and run `speakeasy/backend/src/main/kotlin/ch/ddis/speakeasy/Main.kt`.

Note that using `./gradlew run` to run this project for now would block the interactive Speakeasy CLI. 

### Data source

You can specify data folder by using the flag `--datapath` when running speakeasy. 

### For production/staging 

Please refer to `scripts/deploy.sh`. This script is meant to be run with cron and periodically checks this git repository for any new commit. Upon detecting any, speakeasy is updated to its newest version and restarted in a tmux shell. 

You can override the git checking with the flag `--force-deploy`.

## Docker

Speakeasy ships as a **single image**. The Angular frontend is packaged into
`frontend.jar` and served by Javalin from the classpath, so there is no separate
web server or frontend container.

### Quick start

```bash
mkdir -p config
cp config/config.json.example config/config.json       # optional, defaults are fine
cp .env.example .env                                    # per-server settings, edit as needed

APP_UID=$(id -u) APP_GID=$(id -g) docker compose --profile proxy up -d --build  # proxy profile to use the added Caddy service
```

The app has no published port of its own — Caddy is the only thing reachable
from outside the container, at the hostname set in `SPEAKEASY_SITE_ADDRESS`.

`app_data` (`/data`: `database.db`, feedback forms/results, `sessions.csv`,
`smtp.properties`) and `app_logs` (`/app/logs`) are named Docker volumes, not
host directories — see [Layout](#layout). To place `smtp.properties` (optional,
for evaluation mails), do it after the container exists rather than before:

```bash
cp data/smtp.properties.example /tmp/smtp.properties   # fill in real values
docker cp /tmp/smtp.properties speakeasy:/data/smtp.properties
```

### Reaching the admin CLI

`docker attach` replaces `tmux attach`:

```bash
docker attach speakeasy
```

> **Detach with `Ctrl-P` `Ctrl-Q`.** `Ctrl-C` sends SIGINT to PID 1 and stops the
> application — the same trap as pressing `Ctrl-C` inside tmux.

This works because `compose.yaml` sets `tty: true` and `stdin_open: true`. Without
them the container runs *headless*: HTTP keeps working, but JLine gets EOF at
startup, prints `Interactive terminal disabled` and sleeps, so `user add`,
`user import` and the `assignment` commands become unreachable.

`docker exec` is **not** an alternative for those commands. It starts a second
JVM that shares the filesystem but not the memory of the running server, so
`Cli.assignmentGenerator` is always null there, and a second writer on
`database.db` reintroduces `SQLITE_BUSY`.

### Layout

| Path in container | Mounted from | Contents |
| --- | --- | --- |
| `/data` | `app_data` (named volume) | `database.db`, `feedbackforms/`, `feedbackresults/`, `sessions.csv`, `smtp.properties` |
| `/app/logs` | `app_logs` (named volume) | log4j2 rolling logs — the path is **relative to the working directory**, hence `/app/logs` rather than something under `/data` |
| `/config` | `./config` (bind mount, read-only) | optional `config.json`; absent means built-in defaults |
| `/opt/speakeasy` | — | the application itself, read-only at runtime |

`/data` and `/app/logs` are named volumes rather than bind mounts (dedicated,
quota-backed storage; ownership is handled by Docker instead of a host
directory). `/config` stays a bind mount deliberately, since `config.json` is
meant to be opened and hand-edited.

### Things to know before deploying

- **File ownership.** The image creates its user from the `APP_UID`/`APP_GID`
  build args (default `1000`). `app_data`/`app_logs` are named volumes, so
  Docker populates their ownership from the image on first use — there's no
  host directory to keep in sync. `./config` is still a bind mount, but it's
  read-only and doesn't need write access to match.
- **SQLite constrains the topology.** Exactly one container, and the `app_data`
  volume must stay on **local disk** — locking is unreliable over NFS/CIFS.
  True by default (the `local` driver), but would break the same way a bind
  mount would if someone points the volume driver at network storage. Do not
  scale this service, and do not run two containers against one data volume.
- **Memory.** `backend/build.gradle` hardcodes `-Xms4G -Xmx16G`, which will fight
  a container limit. The image overrides this with `JAVA_OPTS=-Xms512m -Xmx2g`;
  the start script applies `JAVA_OPTS` after the defaults, so the last `-Xmx`
  wins. Raise `JAVA_OPTS` and `deploy.resources.limits.memory` together.
  (`-XX:MaxRAMPercentage` does *not* work here — an explicit `-Xmx` always beats
  it, whatever the order.)
- **Reverse proxy / TLS.** Production runs the bundled `caddy` service
  (`docker compose --profile proxy up -d`), which terminates TLS with a
  Let's Encrypt cert it obtains and renews automatically. Set the hostname per
  server via `SPEAKEASY_SITE_ADDRESS` in `.env`. Caddy reaches the
  app as `speakeasy:8080` over the compose network, so the app's own published
  port stays on loopback (or can be dropped entirely).
- **Secrets.** `smtp.properties` and any keystore stay on the volume, never in
  the image. The `SPEAKEASY_SMTP_*` environment variables work too.
- **Shutdown.** No shutdown hook is registered, so SIGTERM ends the JVM abruptly.
  SQLite's journal makes that safe; `stop_grace_period` lets in-flight requests
  finish first.

### Migrating an existing instance

`app_data` is a named volume, not a host directory, so getting existing data
into it takes one extra step compared to a plain `rsync`.

**From a pre-Docker (tmux) deployment:** rsync onto the host first, then load
that into the volume via a throwaway container:

```bash
mkdir -p /tmp/speakeasy-data
rsync -a olduser@oldserver:~/speakeasy/data/ /tmp/speakeasy-data/
docker volume create app_data
docker run --rm -v /tmp/speakeasy-data:/from -v app_data:/to alpine \
    sh -c "cp -a /from/. /to/"
```

**Upgrading an existing `./data` bind-mount deployment of this repo** to the
named volume: stop the container, then copy the bind-mounted directory across
the same way:

```bash
docker compose stop speakeasy
docker volume create app_data
docker run --rm -v "$(pwd)/data":/from -v app_data:/to alpine \
    sh -c "cp -a /from/. /to/"
docker compose up -d
```

Either way, back up the source directory first — this is a straight copy, not
a merge, so running it twice against a volume that already has data adds on
top rather than replacing it.

`data/users.db` (if present in the old directory) is a legacy leftover —
nothing in the current sources reads it.

### Automated deployment

`scripts/deploy-docker.sh` is the dockerized counterpart of `scripts/deploy.sh`:
same cron-driven git polling and the same `--force-deploy` and `-- <args>` flags,
but the build and restart steps become `docker compose build` and
`docker compose up -d`. The old container keeps serving until a build succeeds.

```cron
*/5 * * * * $HOME/speakeasy/scripts/deploy-docker.sh
```

Rebuilds stay reasonably quick because the Dockerfile uses BuildKit cache mounts
for the Gradle, npm and Node downloads, and because the frontend build sits in
its own layer — a backend-only commit reuses it untouched.


## User Management

User information is stored in a database, located in `data/users.db`, 
and managed by `SQLite` and `JetBrains/Exposed`. 
Each user has four properties in this database: *username*, *password*, *role*, and *id*, 
as well as some information about "user groups". The available roles are *ADMIN*, *HUMAN*, and *BOT*.

To add a new user, simply run Speakeasy and use its CLI, e.g:

`user add -r HUMAN -u new_username -p new_password`
```agsl
Options:
-r, --role [HUMAN|BOT|ADMIN]  Role of the user to add
-u, --username TEXT           Name of the user to add
-p, --password TEXT           Password of the user to add
-h, --help                    Show this message and exit
```



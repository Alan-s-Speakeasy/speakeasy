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
mkdir -p data logs config
cp data/smtp.properties.example data/smtp.properties   # optional, for evaluation mails
cp config/config.json.example config/config.json       # optional, defaults are fine

APP_UID=$(id -u) APP_GID=$(id -g) docker compose up -d --build
```

The app is then on `http://127.0.0.1:8080`.

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
| `/data` | `./data` | `database.db`, `feedbackforms/`, `feedbackresults/`, `sessions.csv`, `smtp.properties` |
| `/app/logs` | `./logs` | log4j2 rolling logs — the path is **relative to the working directory**, hence `/app/logs` rather than something under `/data` |
| `/config` | `./config` (read-only) | optional `config.json`; absent means built-in defaults |
| `/opt/speakeasy` | — | the application itself, read-only at runtime |

### Things to know before deploying

- **File ownership.** The image creates its user from the `APP_UID`/`APP_GID`
  build args (default `1000`). If they do not match the owner of `./data`, the
  app cannot create `database.db`. Rebuild with the right ids, don't `chmod 777`.
- **SQLite constrains the topology.** Exactly one container, and the `./data`
  volume must be on **local disk** — locking is unreliable over NFS/CIFS. Do not
  scale this service, and do not run two containers against one data directory.
- **Memory.** `backend/build.gradle` hardcodes `-Xms4G -Xmx16G`, which will fight
  a container limit. The image overrides this with `JAVA_OPTS=-Xms512m -Xmx2g`;
  the start script applies `JAVA_OPTS` after the defaults, so the last `-Xmx`
  wins. Raise `JAVA_OPTS` and `deploy.resources.limits.memory` together.
  (`-XX:MaxRAMPercentage` does *not* work here — an explicit `-Xmx` always beats
  it, whatever the order.)
- **Reverse proxy.** The port is published on loopback only, assuming TLS
  terminates on the host. `/sse/rooms` uses Server-Sent Events, so the proxy must
  set `proxy_buffering off` and a long read timeout or chat updates stall.
- **Secrets.** `smtp.properties` and any keystore stay on the volume, never in
  the image. The `SPEAKEASY_SMTP_*` environment variables work too.
- **Shutdown.** No shutdown hook is registered, so SIGTERM ends the JVM abruptly.
  SQLite's journal makes that safe; `stop_grace_period` lets in-flight requests
  finish first.

### Migrating an existing instance

Copy the live `data/` directory across *before* the first start, then fix
ownership to match the build args:

```bash
rsync -a olduser@oldserver:~/speakeasy/data/ ./data/
chown -R "$(id -u):$(id -g)" ./data
```

`data/users.db` is a legacy leftover — nothing in the current sources reads it.

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



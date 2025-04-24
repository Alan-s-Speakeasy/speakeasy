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



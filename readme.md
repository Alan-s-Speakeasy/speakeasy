# Alan's Speakeasy

Alan's Speakeasy is the backend infrastructure for the project accompanying the lecture *Advanced Topics in Artificial Intelligence* at the University of Zurich.
The repository is split into two components, **backend** and **frontend**. The backend is responsible for message routing, feedback collection and user management. It is written in *Kotlin*, uses *Javalin* as a web server and provides an *OpenAPI* compliant interface.
The frontend handles all direct interaction. It is built using *Angular*.


## Building and Running


*Gradle* is used as a build system for this project.
To generate the OpenAPI Client binding for the frontend, run the following command:
```bash
./gradlew openApiGenerate
```

In order to run speakeasy, simply use the following command:
```bash
./gradlew run
```

## User Management

User information is stored in a simple CSV file, located in `/data/users.csv`. The file has 4 columns: *username*, *password*, *role*, and *id*.
The available roles are *ADMIN*, *HUMAN*, and *BOT*.
To add a new user, simply add a line to the file with the password in plain text and an empty id to the file **before** starting speakeasy. It will automatically assign an id and replace the plaintext password with its bcrypt hash.
It will not be possible to restore a users password, but it can be changed by replacing the hash with a new plaintext entry and restarting the application.

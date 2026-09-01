
# disa-returns

This application is responsible for receiving monthly and annual returns from ISA Managers.

This application acts as a protected zone service to support the frontend UI journey where ISA Managers can upload their
returns using Upscan, as well as providing API platforms endpoints for ISA Managers who have chosen to integrate with
the API instead.

### Before running the app

This repository relies on having mongodb running locally. You can start it with:

```bash
# first check to see if mongo is already running
docker ps | grep mongodb

# if not, start it
docker run --restart unless-stopped --name mongodb -p 27017:27017 -d percona/percona-server-mongodb:7.0 --replSet rs0
```

Reference instructions for [setting up docker](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/developer-set-up/install-docker.html) and [running mongodb](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/developer-set-up/set-up-mongodb.html#install-mongodb-applesilicon-mac).

### Running the app

```bash
sbt run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes
```

```bash
# Run the app locally with service manager
sm2 --start DISA_RETURNS_ALL
```

```bash
curl http://localhost:1200/api/definition
```

### Running the test suite

To run the unit tests:

```bash
sbt test
```

To run the integration tests:

```bash
sbt it/test
```

#### Delete Monthly Return Summaries

```text
POST /test-only/monthly
Content-Type: application/json
```

```json
{
  "zReferences": ["Z1000", "Z1001"]
}
```

The endpoint deletes monthly-return summaries only for the supplied normalized Z-references. It returns `204 No
Content` on success and `400 Bad Request` for an empty or invalid body. It must not run during active traffic for those
Z-references.

### API Paths

Monthly-return API paths are periodless. The current routes are:

- `POST /monthly/:zReference`
- `POST /monthly/:zReference/declaration`
- `GET /monthly/:zReference/results/summary`
- `GET /monthly/:zReference/results?page=:page`
- `POST /callback/monthly/:zReference`

Tax year and month are derived by the service rather than supplied as path parameters.

### Before you commit

This service leverages scalaFmt to ensure that the code is formatted correctly.

Before you commit, please run the following commands to check that the code is formatted correctly:

```bash
# checks all source files are correctly formatted
sbt scalafmtCheckAll

# checks all sbt files are correctly formatted
sbt scalafmtSbtCheck

# if checks fail, you can format with the following commands

# formats all source files
sbt scalafmtAll

# formats all sbt files
sbt scalafmtSbt

# formats just the main source files (excludes test and configuration files)
sbt scalafmt
```

## Viewing the API specifications

*For internal HMRC developers.*

This repository contains API definitions for the DSA Returns API, deployed to the API platform.

To view and test this documentation locally, follow the instructions below.

```zsh
# Run the API platform devhub preview locally with service manager
sm2 -start DEVHUB_PREVIEW_OPENAPI

# Run disa returns locally with test-only routes enabled
sbt run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes

# Open the API platform devhub preview in your browser
open http://localhost:9680/api-documentation/docs/openapi/preview/
```

From this page, you can enter the fully qualified url of the documentation you wish to view, for example:

```
http://localhost:1200/api/conf/1.0/application.yaml
```

### Further documentation

You can view further information regarding this service via our [service guide](#).

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

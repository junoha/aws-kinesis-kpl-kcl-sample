# KPL and KCL sample application

- [KPL and KCL sample application](#kpl-and-kcl-sample-application)
  - [Requirement](#requirement)
  - [KPL](#kpl)
    - [Basic usage](#basic-usage)
    - [Parameters](#parameters)
  - [KCL](#kcl)
    - [Basic usage](#basic-usage-1)
    - [Parameters](#parameters-1)
  - [Run in Docker](#run-in-docker)
    - [Setup](#setup)
    - [How to use](#how-to-use)
  - [Run in Fargate](#run-in-fargate)

## Requirement

Java 8 or later and gradle 5 or later. Recommend to use [sdkman](https://sdkman.io/).

```
$ gradle -v

------------------------------------------------------------
Gradle 5.6.2
------------------------------------------------------------

Build time:   2019-09-05 16:13:54 UTC
Revision:     55a5e53d855db8fc7b0e494412fc624051a8e781

Kotlin:       1.3.41
Groovy:       2.5.4
Ant:          Apache Ant(TM) version 1.9.14 compiled on March 12 2019
JVM:          11.0.4 (Amazon.com Inc. 11.0.4+11-LTS)
OS:           Mac OS X 10.14.6 x86_64
```


## KPL

### Basic usage

Build application.
```
$ cd producer/app
$ ./gradlew clean shadowJar
```

Run Java with environment variables.
```
$ STREAM_NAME=sandbox REGION_NAME=ap-northeast-1 \
SECONDS_TO_RUN=300 \
LOG_LEVEL=INFO \
java -jar build/libs/kds-sample-producer-1.1.0-all.jar
```

### Parameters
|      Name      |          Description          |    Default     |
| :------------: | :---------------------------- | :------------: |
|  STREAM_NAME   | Name of stream to put record  |    sandbox     |
|  REGION_NAME   | Region name                   | ap-northeast-1 |
| SECONDS_TO_RUN | Period of running application |       30       |
|  LOG_LEVEL     | Log Level                     | None(required) |


## KCL

### Basic usage

Build application.
```
$ cd consumer/app
$ ./gradlew clean shadowJar
```

Run Java with environment variables.
```
# TRIM_HORIZON (default)
$ STREAM_NAME=sandbox REGION_NAME=ap-northeast-1 \
LOG_LEVEL=INFO \
java -jar build/libs/kds-sample-consumer-1.1.0-all.jar

# AT_TIMESTAMP
$ STREAM_NAME=sandbox REGION_NAME=ap-northeast-1 \
LOG_LEVEL=DEBUG \
INITIAL_POSITION=AT_TIMESTAMP \
TIMESTAMP=20201211045320 \
java -jar build/libs/kds-sample-consumer-1.1.0-all.jar
```

### Parameters
|      Name          |          Description                                             |    Default     |
| :----------------: | :--------------------------------------------------------------- | :------------: |
|  STREAM_NAME       | Name of stream to put record                                     |    sandbox     |
|  REGION_NAME       | Region name                                                      | ap-northeast-1 |
|  INITIAL_POSITION  | Initial position                                                 | TRIM_HORIZON   |
|  TIMESTAMP         | Timestamp when INITIAL_POSITION is AT_TIMESTAMP (yyyyMMddhhmmss) | 20210101000000 |
|  LOG_LEVEL         | Log Level                                                        | None(required) |


## Run in Docker

### Setup

Edit parameters in `~/.env`, which provides environment variables in `docker-compose.yml`

```
$ ls -a
.                  ..                 .env               README.md          consumer           docker-compose.yml producer

$ cat .env
AWS_ACCESS_KEY_ID=xxx
AWS_SECRET_ACCESS_KEY=yyy
STREAM_NAME=sandbox
REGION_NAME=ap-northeast-1
SECONDS_TO_RUN=60
```

### How to use

Create image.
```shell-session
$ docker-compose build
```

Run application in background.
```
$ docker-compose up -d
```

See and tail logs in containers.
```
$ docker-compse logs -f
```

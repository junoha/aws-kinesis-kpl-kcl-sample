# KPL and KCL sample application by Spring Boot with X-Ray

## Requirement

Java 15 or later and gradle 5 or later. Recommend to use [sdkman](https://sdkman.io/).

```
$ gradle -v

------------------------------------------------------------
Gradle 6.5.1
------------------------------------------------------------

Build time:   2020-06-30 06:32:47 UTC
Revision:     66bc713f7169626a7f0134bf452abde51550ea0a

Kotlin:       1.3.72
Groovy:       2.5.11
Ant:          Apache Ant(TM) version 1.10.7 compiled on September 1 2019
JVM:          14.0.2 (Oracle Corporation 14.0.2+12-46)
OS:           Mac OS X 10.15.6 x86_64
```

## (Option) X-Ray Daemon
If you run KPL/KCL locally, launch [xray daemon](https://docs.aws.amazon.com/xray/latest/devguide/xray-daemon-local.html) if you need.

```
$ ./xray_mac -o --log-level debug --region ap-northeast-1
```

## KPL

### Basic usage

Build application.
```
$ cd producer/app
$ ./gradlew clean build
```

### Parameters
|      Name      |          Description          |    Default     |
| :------------: | :---------------------------- | :------------: |
|  STREAM_NAME   | Name of stream to put record  |    sandbox     |
|  REGION_NAME   | Region name                   | ap-northeast-1 |
| SECONDS_TO_RUN | Period of running application |       30       |
| RECORDS_PER_SECOND | Number of records per sec | 2000           |
|  LOG_LEVEL     | Log Level                     |      INFO      |

Run Java with environment variables.
```
$ STREAM_NAME=sandbox REGION_NAME=ap-northeast-1 \
SECONDS_TO_RUN=300 \
java -jar build/libs/springbootdemo-producer-1.1.0-SNAPSHOT.jar

# DEBUG
$ STREAM_NAME=sandbox REGION_NAME=ap-northeast-1 \
SECONDS_TO_RUN=300 \
LOG_LEVEL=DEBUG \
java -jar build/libs/springbootdemo-producer-1.1.0-SNAPSHOT.jar
```

## KCL

### Basic usage

Build application.
```
$ cd consumer/app
$ ./gradlew clean build
```

### Parameters
|      Name                |          Description                                             |    Default     |
| :----------------------: | :--------------------------------------------------------------- | :------------: |
|  STREAM_NAME             | Name of stream to put record                                     |    sandbox     |
|  REGION_NAME             | Region name                                                      | ap-northeast-1 |
|  INITIAL_POSITION        | Initial position of RetrievalConfig                              |    LATEST      |
|  LEASE_INITIAL_POSITION  | Initial position of LeaseManagementConfig                        |  TRIM_HORIZON  |
|  TIMESTAMP               | Timestamp when INITIAL_POSITION is AT_TIMESTAMP (yyyyMMddhhmmss) | 20210101000000 |
|  FANOUT                  | Use enhanced fan-out or not                                      |    false       |
|  LOG_LEVEL               | Log Level                                                        |     INFO       |


Run Java with environment variables.
```
$ STREAM_NAME=sandbox REGION_NAME=ap-northeast-1 \
java -jar build/libs/springbootdemo-consumer-1.1.0-SNAPSHOT.jar

# AT_TIMESTAMP and DEBUG log
$ STREAM_NAME=sandbox REGION_NAME=ap-northeast-1 \
LOG_LEVEL=DEBUG \
INITIAL_POSITION=AT_TIMESTAMP \
TIMESTAMP=20201211045320 \
java -jar build/libs/springbootdemo-consumer-1.1.0-SNAPSHOT.jar

# Fan-out with TRIM_HORIZON
$ STREAM_NAME=sandbox REGION_NAME=ap-northeast-1 \
INITIAL_POSITION=TRIM_HORIZON \
fanout=true \
java -jar build/libs/springbootdemo-consumer-1.1.0-SNAPSHOT.jar
```

## KCL v1

### Basic usage

Build application.
```
$ cd consumer-v1/app
$ ./gradlew clean build
```

### Parameters
|      Name          |          Description                                             |    Default     |
| :----------------: | :--------------------------------------------------------------- | :------------: |
|  STREAM_NAME       | Name of stream to put record                                     |    sandbox     |
|  REGION_NAME       | Region name                                                      | ap-northeast-1 |
|  INITIAL_POSITION  | Initial position                                                 | TRIM_HORIZON   |
|  TIMESTAMP         | Timestamp when INITIAL_POSITION is AT_TIMESTAMP (yyyyMMddhhmmss) | 20210101000000 |
|  LOG_LEVEL         | Log Level                                                        |     INFO       |


Run Java with environment variables.
```
# TRIM_HORIZON (default)
$ STREAM_NAME=sandbox REGION_NAME=ap-northeast-1 \
java -jar build/libs/springbootdemo-consumer-v1-1.1.0-SNAPSHOT.jar

# AT_TIMESTAMP
$ STREAM_NAME=sandbox REGION_NAME=ap-northeast-1 \
LOG_LEVEL=DEBUG \
INITIAL_POSITION=AT_TIMESTAMP \
TIMESTAMP=20201211045320 \
java -jar build/libs/springbootdemo-consumer-v1-1.1.0-SNAPSHOT.jar
```

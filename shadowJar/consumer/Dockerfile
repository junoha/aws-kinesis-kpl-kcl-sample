FROM amazoncorretto:11.0.4 as builder

COPY ./app /app
WORKDIR /app
RUN ./gradlew clean shadowJar

FROM amazoncorretto:11.0.4

ENV APP_VERSION=1.0.0

RUN yum update -y && yum install -y shadow-utils && \
  groupadd -r kinesis && useradd -r -g kinesis kinesis

COPY --from=builder /app/build/libs/kds-sample-consumer-${APP_VERSION}-all.jar /kds-sample-consumer.jar
USER kinesis
WORKDIR /

ENTRYPOINT ["java", "-jar", "/kds-sample-consumer.jar"]

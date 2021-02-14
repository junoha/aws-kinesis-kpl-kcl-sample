package com.junoha.sample.kinesis.springbootdemo.consumer.service;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;
import com.google.common.collect.ImmutableSet;
import com.junoha.sample.kinesis.springbootdemo.consumer.service.recordprocessor.KclRecordProcessorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ConsumerService {
    // By default, Logback is used
    // https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-logging
    private static final Logger log = LoggerFactory.getLogger(ConsumerService.class);

    @Value(value = "${aws.kinesis.region_name}")
    private String regionName;

    @Value(value = "${aws.kinesis.stream_name}")
    private String streamName;

    @Value(value = "${aws.kinesis.initial_position}")
    private String initialPositionStr;

    @Value(value = "${aws.kinesis.timestamp}")
    private String timestamp;

    private InitialPositionInStream convertInitialPositionStr(String positionStr) {
        return switch (positionStr) {
            case "TRIM_HORIZON" -> InitialPositionInStream.TRIM_HORIZON;
            case "LATEST" -> InitialPositionInStream.LATEST;
            case "AT_TIMESTAMP" -> InitialPositionInStream.AT_TIMESTAMP;
            default -> throw new IllegalArgumentException("You should set INITIAL_POSITION to TRIM_HORIZON or LATEST or AT_TIMESTAMP: " + positionStr);
        };
    }

    /**
     * Entry point
     */
    public void execute() {
        log.info(String.format("regionName:%s, streamName:%s, initialPosition:%s, timestamp:%s",
                regionName, streamName, initialPositionStr, timestamp));

        final AWSCredentialsProvider credentialsProvider = new AWSCredentialsProviderChain(new DefaultAWSCredentialsProviderChain());
        final String workerId = "worker-v1-" + UUID.randomUUID();
        InitialPositionInStream position = convertInitialPositionStr(this.initialPositionStr);

        // By default, KCL v1 doesn't have WorkerIdentifier dimension
        final KinesisClientLibConfiguration kinesisClientLibConfiguration =
                new KinesisClientLibConfiguration(streamName, streamName, credentialsProvider, workerId)
                        .withRegionName(regionName)
                        .withMetricsEnabledDimensions(ImmutableSet.<String>builder()
                                .addAll(KinesisClientLibConfiguration.DEFAULT_METRICS_ENABLED_DIMENSIONS).add("WorkerIdentifier").build()
                        );

        if (position == InitialPositionInStream.AT_TIMESTAMP) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                kinesisClientLibConfiguration.withTimestampAtInitialPositionInStream(sdf.parse(this.timestamp));
            } catch (ParseException e) {
                throw new IllegalArgumentException("Cannot parse dateformat (yyyyMMddhhmmss) : " + this.timestamp);
            }
        } else {
            kinesisClientLibConfiguration.withInitialPositionInStream(position);
        }

        log.info("Running {} to process stream {} as worker {} ...", streamName, streamName, workerId);

        Worker worker = new Worker.Builder()
                .recordProcessorFactory(new KclRecordProcessorFactory())
                .config(kinesisClientLibConfiguration)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Waiting up to 20 seconds for shutdown to complete.");
            try {
                worker.startGracefulShutdown().get(20, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                log.info("Interrupted while waiting for graceful shutdown. Continuing.");
            } catch (ExecutionException e) {
                log.error("Exception while executing graceful shutdown.", e);
            } catch (TimeoutException e) {
                log.error("Timeout while waiting for shutdown.  Scheduler may not have exited.");
            } finally {
                log.info("Completed, shutting down now.");
            }
        }));

        // Start worker
        worker.run();
    }
}

package com.junoha.sample.kinesis.springbootdemo.consumer.service;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import com.junoha.sample.kinesis.springbootdemo.consumer.service.recordprocessor.KclRecordProcessorFactory;
import lombok.Synchronized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.InitialPositionInStream;
import software.amazon.kinesis.common.InitialPositionInStreamExtended;
import software.amazon.kinesis.common.KinesisClientUtil;
import software.amazon.kinesis.coordinator.Scheduler;
import software.amazon.kinesis.retrieval.polling.PollingConfig;

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

    @Value(value = "${aws.kinesis.lease_initial_position}")
    private String leaseInitialPositionStr;

    @Value(value = "${aws.kinesis.timestamp}")
    private String timestamp;

    @Value(value = "${aws.kinesis.fanout}")
    private boolean fanout;

    private KinesisAsyncClient getKinesisClient(Region region) {
        if (fanout) {
            // Add maxConcurrency tuning for fan-out
            // https://docs.aws.amazon.com/streams/latest/dev/building-enhanced-consumers-kcl-java.html
            // https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/http-configuration-netty.html
            return KinesisClientUtil.createKinesisAsyncClient(KinesisAsyncClient.builder()
                    .region(region)
                    .httpClientBuilder(NettyNioAsyncHttpClient.builder().maxConcurrency(100))
                    .overrideConfiguration(
                            ClientOverrideConfiguration.builder()
//                                .addExecutionInterceptor(new XRayTracingInterceptor("xray-kcl-listener-kinesis"))
                                    .build()
                    ));
        } else {
            return KinesisClientUtil.createKinesisAsyncClient(KinesisAsyncClient.builder()
                    .region(region)
                    .overrideConfiguration(
                            ClientOverrideConfiguration.builder()
//                                .addExecutionInterceptor(new XRayTracingInterceptor("xray-kcl-listener-kinesis"))
                                    .build()
                    ));
        }
    }

    private DynamoDbAsyncClient getDynamoDbClient(Region region) {
        return DynamoDbAsyncClient.builder()
                .region(region)
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
//                                .addExecutionInterceptor(new XRayTracingInterceptor("xray-kcl-listener-dynamodb"))
                                .build()
                )
                .build();
    }

    private CloudWatchAsyncClient getCloudWatchClient(Region region) {
        return CloudWatchAsyncClient.builder().
                region(region)
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
//                                .addExecutionInterceptor(new XRayTracingInterceptor("xray-kcl-listener-cloudwatch"))
                                .build()
                )
                .build();
    }

    private InitialPositionInStream convertInitialPositionStr(String positionStr) {
        return switch (positionStr) {
            case "TRIM_HORIZON" -> InitialPositionInStream.TRIM_HORIZON;
            case "LATEST" -> InitialPositionInStream.LATEST;
            case "AT_TIMESTAMP" -> InitialPositionInStream.AT_TIMESTAMP;
            default -> throw new IllegalArgumentException("Invalid InitialPosition: " + positionStr);
        };
    }

    private InitialPositionInStreamExtended getInitialPosition(String positionStr) {
        InitialPositionInStream position = convertInitialPositionStr(positionStr);

        if (position == InitialPositionInStream.AT_TIMESTAMP) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                return InitialPositionInStreamExtended.newInitialPositionAtTimestamp(sdf.parse(timestamp));
            } catch (java.text.ParseException e) {
                throw new IllegalArgumentException("Cannot parse dateformat (yyyyMMddhhmmss) : " + timestamp);
            }
        } else {
            return InitialPositionInStreamExtended.newInitialPosition(position);
        }
    }

    /**
     * Entry point
     */
    public void execute() {
        log.info(String.format("regionName:%s, streamName:%s", regionName, streamName));
        Region region = Region.of(this.regionName);
        KinesisAsyncClient kinesisClient = getKinesisClient(region);
        DynamoDbAsyncClient dynamoClient = getDynamoDbClient(region);
        CloudWatchAsyncClient cloudWatchClient = getCloudWatchClient(region);

        /*
          X-Ray setup
         */
        AWSXRayRecorder xrayRecorder = AWSXRay.getGlobalRecorder();
        Segment kinesisConsumerS = xrayRecorder.beginSegment("kinesisConsumerSegment");
        kinesisConsumerS.putAnnotation("parentID", xrayRecorder.getTraceEntity().getId());

        /*
          Sets up configuration for the KCL, including DynamoDB and CloudWatch dependencies. The final argument, a
          ShardRecordProcessorFactory, is where the logic for record processing lives, and is located in a private
          class below.
         */
        ConfigsBuilder configsBuilder = new ConfigsBuilder(
                streamName,
                streamName, // application name
                kinesisClient,
                dynamoClient,
                cloudWatchClient,
                "worker-v2-" + UUID.randomUUID().toString(),
                new KclRecordProcessorFactory(xrayRecorder.getTraceEntity()));

        /*
          The Scheduler (also called Worker in earlier versions of the KCL) is the entry point to the KCL. This
          instance is configured with defaults provided by the ConfigsBuilder.
         */
        InitialPositionInStreamExtended initialPositionStream = getInitialPosition(initialPositionStr);
        InitialPositionInStreamExtended leaseInitialPositionStream = getInitialPosition(leaseInitialPositionStr);

        Scheduler scheduler;
        if (fanout) {
            scheduler = new Scheduler(
                    configsBuilder.checkpointConfig(),
                    configsBuilder.coordinatorConfig(),
                    configsBuilder.leaseManagementConfig().initialPositionInStream(leaseInitialPositionStream),
                    configsBuilder.lifecycleConfig(),
                    configsBuilder.metricsConfig(),
                    configsBuilder.processorConfig(),
                    configsBuilder.retrievalConfig()
                            .initialPositionInStreamExtended(initialPositionStream));
        } else {
            scheduler = new Scheduler(
                    configsBuilder.checkpointConfig(),
                    configsBuilder.coordinatorConfig(),
                    configsBuilder.leaseManagementConfig().initialPositionInStream(leaseInitialPositionStream),
                    configsBuilder.lifecycleConfig(),
                    configsBuilder.metricsConfig(),
                    configsBuilder.processorConfig(),
                    configsBuilder.retrievalConfig()
                            .initialPositionInStreamExtended(initialPositionStream)
                            .retrievalSpecificConfig(new PollingConfig(streamName, kinesisClient)));
        }

        log.info("Scheduler leaseManagementConfig : {}", scheduler.leaseManagementConfig().toString());
        log.info("Scheduler retrievalConfig : {}", scheduler.retrievalConfig().toString());

        Thread schedulerThread = new Thread(scheduler);
        // schedulerThread.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopServer(scheduler)));
        schedulerThread.start();
    }

    @Synchronized
    private void stopServer(Scheduler scheduler) {
        log.info("Waiting up to 20 seconds for shutdown to complete.");
        try {
            scheduler.startGracefulShutdown().get(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.info("Interrupted while waiting for graceful shutdown. Continuing.");
        } catch (ExecutionException e) {
            log.error("Exception while executing graceful shutdown.", e);
        } catch (TimeoutException e) {
            log.error("Timeout while waiting for shutdown.  Scheduler may not have exited.");
        } finally {
            // close xray segment
            // AWSXRay.endSegment();
            log.info("Completed, shutting down now.");
        }
    }
}

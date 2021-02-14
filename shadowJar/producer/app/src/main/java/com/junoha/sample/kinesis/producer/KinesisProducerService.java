package com.junoha.sample.kinesis.producer;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.kinesis.producer.*;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class KinesisProducerService {

    private static final Logger log = LoggerFactory.getLogger(KinesisProducerService.class);
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1);
    private static final int RECORDS_PER_SECOND = 2000;

    private static KinesisProducer createProducer(String region) {
        KinesisProducerConfiguration config = new KinesisProducerConfiguration()
                .setRegion(region)
                .setCredentialsProvider(new DefaultAWSCredentialsProviderChain());
        return new KinesisProducer(config);
    }

    private static FutureCallback<UserRecordResult> createFutureCallback() {
        return new FutureCallback<>() {
            @Override
            public void onFailure(Throwable t) {
                if (t instanceof UserRecordFailedException) {
                    Attempt last = Iterables.getLast(
                            ((UserRecordFailedException) t).getResult().getAttempts());
                    log.error(String.format(
                            "Record failed to put - %s : %s",
                            last.getErrorCode(), last.getErrorMessage()));
                }
                log.error("Exception during put", t);
                System.exit(1);
            }

            @Override
            public void onSuccess(UserRecordResult result) {
                // Logging all record
//                log.info(String.format("ShardId:%s,getSequenceNumber:%s,isSuccessful:%s", result.getShardId(), result.getSequenceNumber(), result.isSuccessful()));
            }
        };
    }

    /**
     * Executes a function N times per second for M seconds with a
     * ScheduledExecutorService. The executor is shutdown at the end. This is
     * more precise than simply using scheduleAtFixedRate.
     *
     * @param exec            Executor
     * @param task            Task to perform
     * @param counter         Counter used to track how many times the task has been
     *                        executed
     * @param durationSeconds How many seconds to run for
     * @param ratePerSecond   How many times to execute task per second
     */
    private static void executeAtTargetRate(
            final ScheduledExecutorService exec,
            final Runnable task,
            final AtomicLong counter,
            final int durationSeconds,
            final int ratePerSecond) {

        exec.scheduleWithFixedDelay(new Runnable() {
            final long startTime = System.nanoTime();

            @Override
            public void run() {
                double secondsRun = (System.nanoTime() - startTime) / 1e9;
                double targetCount = Math.min(durationSeconds, secondsRun) * ratePerSecond;

                while (counter.get() < targetCount) {
                    counter.getAndIncrement();
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.error("Error running task", e);
                        System.exit(1);
                    }
                }

                if (secondsRun >= durationSeconds) {
                    exec.shutdown();
                }
            }
        }, 0, 1, TimeUnit.MILLISECONDS);
    }

    private static String getEnvVal(String key, String defaultValue) {
        return System.getenv(key) != null ? System.getenv(key) : defaultValue;
    }

    /**
     * Based on KPL sample code
     * https://github.com/awslabs/amazon-kinesis-producer/blob/master/java/amazon-kinesis-producer-sample/src/com/amazonaws/services/kinesis/producer/sample/SampleProducer.java
     *
     * @param args
     */
    public static void main(String[] args) throws InterruptedException {
        // Parameters
        final String streamName = getEnvVal("STREAM_NAME", "sandbox");
        final String region = getEnvVal("REGION_NAME", Region.AP_NORTHEAST_1.id());
        final String secondsToRunString = getEnvVal("SECONDS_TO_RUN", "30");

        final int secondsToRun = Integer.parseInt(secondsToRunString);
        if (secondsToRun <= 0) {
            log.error("Seconds to Run should be a positive integer");
            System.exit(1);
        }

        log.info(String.format("Stream name: %s Region: %s secondsToRun %d", streamName, region, secondsToRun));

        // The monotonically increasing sequence number we will put in the data of each record
        final AtomicLong sequenceNumber = new AtomicLong(0);
        // The number of records that have finished (either successfully put, or failed)
        final AtomicLong completed = new AtomicLong(0);

        final KinesisProducer producer = createProducer(region);
        final FutureCallback<UserRecordResult> callback = createFutureCallback();
        final ExecutorService callbackThreadPool = Executors.newCachedThreadPool();
        final Runnable putOneRecord = () -> {
            ListenableFuture<UserRecordResult> f = producer.addUserRecord(DataGenerator.generateUserRecord(streamName));
            Futures.addCallback(f, callback, callbackThreadPool);
        };

        // This gives us progress updates
        EXECUTOR.scheduleAtFixedRate(() -> {
            long put = sequenceNumber.get();
            long total = RECORDS_PER_SECOND * secondsToRun;
            double putPercent = 100.0 * put / total;
            long done = completed.get();
            double donePercent = 100.0 * done / total;
            log.info(String.format(
                    "Put %d of %d so far (%.2f %%), %d have completed (%.2f %%)",
                    put, total, putPercent, done, donePercent));
        }, 1, 1, TimeUnit.SECONDS);

        // Kick off the puts
        log.info(String.format(
                "Starting puts... will run for %d seconds at %d records per second",
                secondsToRun, RECORDS_PER_SECOND));
        executeAtTargetRate(EXECUTOR, putOneRecord, sequenceNumber, secondsToRun, RECORDS_PER_SECOND);

        // Wait for puts to finish. After this statement returns, we have
        // finished all calls to putRecord, but the records may still be
        // in-flight. We will additionally wait for all records to actually
        // finish later.
        EXECUTOR.awaitTermination(secondsToRun + 1, TimeUnit.SECONDS);

        log.info("Waiting for remaining puts to finish...");
        producer.flushSync();
        log.info("All records complete.");

        // This kills the child process and shuts down the threads managing it.
        producer.destroy();
        log.info("Finished.");

        System.exit(0);
    }
}

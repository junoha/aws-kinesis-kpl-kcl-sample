    package com.junoha.sample.kinesis.springbootdemo.consumer.service.recordprocessor;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Entity;
import com.amazonaws.xray.entities.Subsegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.kinesis.exceptions.InvalidStateException;
import software.amazon.kinesis.exceptions.ShutdownException;
import software.amazon.kinesis.exceptions.ThrottlingException;
import software.amazon.kinesis.lifecycle.events.*;
import software.amazon.kinesis.processor.RecordProcessorCheckpointer;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.retrieval.KinesisClientRecord;

import java.util.List;

public class KclRecordProcessor implements ShardRecordProcessor {

    private static final String SHARD_ID_MDC_KEY = "ShardId";

    private static final Logger log = LoggerFactory.getLogger(KclRecordProcessor.class);

    private String shardId;

    private static final long BACKOFF_TIME_IN_MILLIS = 3000L;
    private static final int NUM_RETRIES = 10;
    private static final long CHECKPOINT_INTERVAL_MILLIS = 60000L;
    private long nextCheckpointTimeInMillis;

    private final AWSXRayRecorder recorder = AWSXRay.getGlobalRecorder();
    private final Entity traceEntity;

    public KclRecordProcessor(Entity traceEntityPassed) {
        log.info(String.format("traceEntity(%s:%s)", traceEntityPassed.getId(), traceEntityPassed.getName()));
        this.traceEntity = traceEntityPassed;
    }

    /**
     * Invoked by the KCL before data records are delivered to the ShardRecordProcessor instance (via
     * processRecords). In this example we do nothing except some logging.
     *
     * @param initializationInput Provides information related to initialization.
     */
    public void initialize(InitializationInput initializationInput) {
        shardId = initializationInput.shardId();
        MDC.put(SHARD_ID_MDC_KEY, shardId);
        try {
            log.info("Initializing @ shard: {}, Sequence: {}", shardId, initializationInput.extendedSequenceNumber());
        } finally {
            MDC.remove(SHARD_ID_MDC_KEY);
        }
    }

    /**
     * Handles record processing logic. The Amazon Kinesis Client Library will invoke this method to deliver
     * data records to the application. In this example we simply log our records.
     *
     * @param processRecordsInput Provides the records to be processed as well as information and capabilities
     *                            related to them (e.g. checkpointing).
     */
    public void processRecords(ProcessRecordsInput processRecordsInput) {
        MDC.put(SHARD_ID_MDC_KEY, shardId);

        //sets the trace entity to the recorder to ensure subsegment that is running on a different thread is added to the trace
        recorder.setTraceEntity(traceEntity);
        //begin subsegment in the trace recorder to add the subsegment to the Kinesis Consumer segment
        Subsegment processRecordsSS = recorder.beginSubsegment("processRecords");
        processRecordsSS.putAnnotation("parentID", traceEntity.getId());

        try {
            log.info("Processing {} records from {}, MillisBehindLatest: {}, TimeSpentInCache: {}",
                    processRecordsInput.records().size(), shardId, processRecordsInput.millisBehindLatest(), processRecordsInput.timeSpentInCache().getSeconds());

            processRecordsWithRetries(processRecordsInput.records());

            // Checkpoint once every checkpoint interval.
            if (System.currentTimeMillis() > nextCheckpointTimeInMillis) {
                checkpoint(processRecordsInput.checkpointer());
                nextCheckpointTimeInMillis = System.currentTimeMillis() + CHECKPOINT_INTERVAL_MILLIS;
            }

        } catch (Throwable t) {
            log.error("Caught throwable while processing records. Aborting.");
            Runtime.getRuntime().halt(1);
            processRecordsSS.addException(t);
        } finally {
            processRecordsSS.putAnnotation("finish", true);
            recorder.endSubsegment();
            MDC.remove(SHARD_ID_MDC_KEY);
        }
    }

    private void processRecordsWithRetries(List<KinesisClientRecord> records) {
        for (KinesisClientRecord record : records) {
            boolean processedSuccessfully = false;
            for (int i = 0; i < NUM_RETRIES; i++) {
                try {
                    processSingleRecord(record);
                    processedSuccessfully = true;
                    break;
                } catch (Throwable t) {
                    log.warn("Caught throwable while processing record " + record, t);
                }

                // backoff if we encounter an exception.
                try {
                    Thread.sleep(BACKOFF_TIME_IN_MILLIS);
                } catch (InterruptedException e) {
                    log.debug("Interrupted sleep", e);
                }
            }

            if (!processedSuccessfully) {
                log.error("Couldn't process record " + record + ". Skipping the record.");
            }
        }
    }

    private void processSingleRecord(KinesisClientRecord record) {
        // log.info("record : PartitionKey {}, SequenceNumber {}, ApproximateArrivalTimestamp {}",
        //         record.partitionKey(), record.sequenceNumber(), record.approximateArrivalTimestamp());

        // !!!! Do your business logic here !!!!
    }

    /**
     * Called when the lease tied to this record processor has been lost. Once the lease has been lost,
     * the record processor can no longer checkpoint.
     *
     * @param leaseLostInput Provides access to functions and data related to the loss of the lease.
     */
    public void leaseLost(LeaseLostInput leaseLostInput) {
        MDC.put(SHARD_ID_MDC_KEY, shardId);
        try {
            log.info("Lost lease so terminating. : {}", shardId);
        } finally {
            MDC.remove(SHARD_ID_MDC_KEY);
        }
    }

    /**
     * Called when all data on this shard has been processed. Checkpointing must occur in the method for record
     * processing to be considered complete; an exception will be thrown otherwise.
     *
     * @param shardEndedInput Provides access to a checkpointer method for completing processing of the shard.
     */
    public void shardEnded(ShardEndedInput shardEndedInput) {
        MDC.put(SHARD_ID_MDC_KEY, shardId);
        try {
            log.info("Reached shard end checkpointing. : {}", shardId);
            shardEndedInput.checkpointer().checkpoint();
        } catch (ShutdownException | InvalidStateException e) {
            log.error("Exception while checkpointing at shard end. Giving up.", e);
        } finally {
            MDC.remove(SHARD_ID_MDC_KEY);
        }
    }

    /**
     * Invoked when Scheduler has been requested to shut down (i.e. we decide to stop running the app by pressing
     * Enter). Checkpoints and logs the data a final time.
     *
     * @param shutdownRequestedInput Provides access to a checkpointer, allowing a record processor to checkpoint
     *                               before the shutdown is completed.
     */
    public void shutdownRequested(ShutdownRequestedInput shutdownRequestedInput) {
        MDC.put(SHARD_ID_MDC_KEY, shardId);
        try {
            log.info("Scheduler is shutting down, checkpointing. {}", shardId);
            shutdownRequestedInput.checkpointer().checkpoint();
        } catch (ShutdownException | InvalidStateException e) {
            log.error("Exception while checkpointing at requested shutdown. Giving up.", e);
        } finally {
            MDC.remove(SHARD_ID_MDC_KEY);
        }
    }

    /**
     * Checkpoint with retries.
     *
     * @param checkpointer checkpointer
     */
    private void checkpoint(RecordProcessorCheckpointer checkpointer) {
        log.info("Checkpointing shard " + shardId);

        for (int i = 0; i < NUM_RETRIES; i++) {
            try {
                checkpointer.checkpoint();
                break;
            } catch (ShutdownException se) {
                // Ignore checkpoint if the processor instance has been shutdown (fail over).
                log.info("Caught shutdown exception, skipping checkpoint.", se);
                break;
            } catch (ThrottlingException e) {
                // Backoff and re-attempt checkpoint upon transient failures
                if (i >= (NUM_RETRIES - 1)) {
                    log.error("Checkpoint failed after " + (i + 1) + "attempts.", e);
                    break;
                } else {
                    log.info("Transient issue when checkpointing - attempt " + (i + 1) + " of "
                            + NUM_RETRIES, e);
                }
            } catch (InvalidStateException e) {
                // This indicates an issue with the DynamoDB table (check for table, provisioned IOPS).
                log.error("Cannot save checkpoint to the DynamoDB table used by the Amazon Kinesis Client Library.", e);
                break;
            }
            try {
                Thread.sleep(BACKOFF_TIME_IN_MILLIS);
            } catch (InterruptedException e) {
                log.debug("Interrupted sleep", e);
            }
        }
    }
}

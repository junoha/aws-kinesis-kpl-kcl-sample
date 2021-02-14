package com.junoha.sample.kinesis.springbootdemo.consumer.service.recordprocessor;

import com.amazonaws.services.kinesis.clientlibrary.exceptions.InvalidStateException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ShutdownException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ThrottlingException;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorCheckpointer;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IShutdownNotificationAware;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.ShutdownReason;
import com.amazonaws.services.kinesis.clientlibrary.types.InitializationInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ProcessRecordsInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ShutdownInput;
import com.amazonaws.services.kinesis.model.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class KclRecordProcessor implements IRecordProcessor, IShutdownNotificationAware {

    private static final Logger log = LoggerFactory.getLogger(KclRecordProcessor.class);
    private String shardId;

    private static final long BACKOFF_TIME_IN_MILLIS = 3000L;
    private static final int NUM_RETRIES = 10;
    private static final long CHECKPOINT_INTERVAL_MILLIS = 60000L;
    private long nextCheckpointTimeInMillis;

    @Override
    public void initialize(InitializationInput initializationInput) {
        log.info("Initializing record processor for ShardId: {}, ExtendedSequenceNumber: {}, PendingCheckpointSequenceNumber: {}",
                initializationInput.getShardId(), initializationInput.getExtendedSequenceNumber(), initializationInput.getPendingCheckpointSequenceNumber());
        this.shardId = initializationInput.getShardId();
    }

    @Override
    public void processRecords(ProcessRecordsInput processRecordsInput) {
        log.info("Processing {} records from {}, MillisBehindLatest: {}, TimeSpentInCache: {}",
                processRecordsInput.getRecords().size(), shardId, processRecordsInput.getMillisBehindLatest(), processRecordsInput.getTimeSpentInCache().getSeconds());

        processRecordsWithRetries(processRecordsInput.getRecords());

        // Checkpoint once every checkpoint interval.
        if (System.currentTimeMillis() > nextCheckpointTimeInMillis) {
            checkpoint(processRecordsInput.getCheckpointer());
            nextCheckpointTimeInMillis = System.currentTimeMillis() + CHECKPOINT_INTERVAL_MILLIS;
        }
    }

    private void processRecordsWithRetries(List<Record> records) {
        for (Record record : records) {
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

    private void processSingleRecord(Record record) {
        // log.info("record : PartitionKey {}, SequenceNumber {}, ApproximateArrivalTimestamp {}",
        //      record.getPartitionKey(), record.getSequenceNumber(), record.getApproximateArrivalTimestamp());

        // !!!! Do your business logic here !!!!
    }

    /**
     * Called when the worker has been requested to shutdown
     *
     * @param checkpointer
     */
    @Override
    public void shutdownRequested(IRecordProcessorCheckpointer checkpointer) {
        log.info("shutdownRequested : {}", shardId);
        try {
            checkpointer.checkpoint();
        } catch (InvalidStateException | ShutdownException e) {
            log.error("Exception while checkpointing at requested shutdown. Giving up.", e);
        }
    }

    @Override
    public void shutdown(ShutdownInput shutdownInput) {
        log.info("Shutting down record processor for shard: {} , ShutdownReason: {}", shardId, shutdownInput.getShutdownReason());

        if (shutdownInput.getShutdownReason() == ShutdownReason.TERMINATE) {
            checkpoint(shutdownInput.getCheckpointer());
        }
    }

    /**
     * Checkpoint with retries.
     *
     * @param checkpointer checkpointer
     */
    private void checkpoint(IRecordProcessorCheckpointer checkpointer) {
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

package com.junoha.sample.kinesis.springbootdemo.consumer.xray;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Entity;
import com.amazonaws.xray.entities.Subsegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import software.amazon.kinesis.checkpoint.CheckpointConfig;
import software.amazon.kinesis.coordinator.CoordinatorConfig;
import software.amazon.kinesis.coordinator.Scheduler;
import software.amazon.kinesis.leases.LeaseManagementConfig;
import software.amazon.kinesis.lifecycle.LifecycleConfig;
import software.amazon.kinesis.metrics.MetricsConfig;
import software.amazon.kinesis.processor.ProcessorConfig;
import software.amazon.kinesis.retrieval.RetrievalConfig;

public class XrayScheduler extends Scheduler {

    private static final Logger log = LoggerFactory.getLogger(XrayScheduler.class);
    private final AWSXRayRecorder recorder = AWSXRay.getGlobalRecorder();
    private final Entity traceEntity;

    public XrayScheduler(@NonNull final CheckpointConfig checkpointConfig,
                         @NonNull final CoordinatorConfig coordinatorConfig,
                         @NonNull final LeaseManagementConfig leaseManagementConfig,
                         @NonNull final LifecycleConfig lifecycleConfig,
                         @NonNull final MetricsConfig metricsConfig,
                         @NonNull final ProcessorConfig processorConfig,
                         @NonNull final RetrievalConfig retrievalConfig,
                         Entity traceEntityPassed) {
        super(checkpointConfig, coordinatorConfig, leaseManagementConfig, lifecycleConfig, metricsConfig, processorConfig, retrievalConfig);
        this.traceEntity = traceEntityPassed;
    }

    @Override
    public void run() {
        log.info("XrayScheduler.run() start");

        //sets the trace entity to the recorder to ensure subsegment that is running on a different thread is added to the trace
        this.recorder.setTraceEntity(this.traceEntity);
        //begin subsegment in the trace recorder to add the subsegment to the Kinesis Consumer segment
        Subsegment kinesisConsumerSS = this.recorder.beginSubsegment("kinesisConsumerSubSegment");

        try {
            kinesisConsumerSS.putAnnotation("parentID", this.traceEntity.getId());
            super.run();
            kinesisConsumerSS.putAnnotation("finish", true);
        } catch (Exception e) {
            kinesisConsumerSS.addException(e);
        } finally {
            this.recorder.endSubsegment();
        }
        log.info("XrayScheduler.run() end");
    }
}

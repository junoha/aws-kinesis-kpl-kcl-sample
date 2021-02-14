package com.junoha.sample.kinesis.springbootdemo.consumer.service.recordprocessor;

import com.amazonaws.xray.entities.Entity;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;

public class KclRecordProcessorFactory implements ShardRecordProcessorFactory {

    private final Entity traceEntity;

    public KclRecordProcessorFactory(Entity traceEntity) {
        this.traceEntity = traceEntity;
    }

    @Override
    public KclRecordProcessor shardRecordProcessor() {
        return new KclRecordProcessor(traceEntity);
    }
}

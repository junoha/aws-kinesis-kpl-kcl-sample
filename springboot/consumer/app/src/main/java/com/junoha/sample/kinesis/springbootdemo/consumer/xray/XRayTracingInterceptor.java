package com.junoha.sample.kinesis.springbootdemo.consumer.xray;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.interceptors.TracingInterceptor;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

public class XRayTracingInterceptor extends TracingInterceptor {
    private final String name;

    public XRayTracingInterceptor(String name) {
       this.name = name;
    }

    @Override
    public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
        AWSXRay.beginSegment(name);
        super.beforeExecution(context, executionAttributes);
    }

    @Override
    public void afterExecution(Context.AfterExecution context, ExecutionAttributes executionAttributes) {
        super.afterExecution(context, executionAttributes);
        AWSXRay.endSegment();
    }
}

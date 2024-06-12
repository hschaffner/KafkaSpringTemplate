/*
 * Copyright Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.confluent.heinz.interceptor.template;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class initConfiguration {

    static public OpenTelemetry initializeOpentelemtryGrpc() {
        Resource resource = Resource.getDefault()
                .merge(Resource.builder().put("service.name", "RetryServerApp").build());


        SpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                //.setEndpoint("http://localhost:4317") // this is the default for OTLP over gRPC
                //.setEndpoint("http://otel-collector:4317")
                .setEndpoint("http://my-jaeger-collector:4317")
                .build();


        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(resource)
//                .setSampler(Sampler.traceIdRatioBased(0.5)) // Set to sample a fraction of traces
                .build();

        // W3C format for context propagation
        TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();
        // or you could use B3 (multi header format)
        // TextMapPropagator propagator = B3Propagator.injectingMultiHeaders();


        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(propagator))
                .buildAndRegisterGlobal();

        return openTelemetry;
    }

    static public OpenTelemetry initializeOpentelemtryHttp() {
        Resource resource = Resource.getDefault()
                .merge(Resource.builder().put("service.name", "RetryServerApp").build());


        SpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint("http://localhost:4318/v1/traces") // this is the default for OTLP over HTTP
                .build();

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(resource)
                .build();


        // W3C format for context propagation
        TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();
        // or you could use B3 (multi header format)
        // TextMapPropagator propagator = B3Propagator.injectingMultiHeaders();

        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(propagator))
                .buildAndRegisterGlobal();


        return openTelemetry;
    }
}

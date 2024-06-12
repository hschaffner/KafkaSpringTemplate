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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.TracingConsumerInterceptor;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.TracingProducerInterceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;


@SpringBootApplication
public class KafkaSpringTemplateApplication {

    private static Tracer tracer;
    private static Span spanParent;
    private static OpenTelemetry openTelemetry;

    private static final Log logger = LogFactory.getLog(KafkaSpringTemplateApplication.class);

    private static ApplicationContext applicationContext;

    @Autowired
    Environment env;



    public static void main(String[] args) {
        logger.info("Starting OpenTelemetry Interceptor application ");
        //openTelemetry = initConfiguration.initializeOpentelemtryHttp();
        openTelemetry = initConfiguration.initializeOpentelemtryGrpc();
        logger.info("Opentelemetry is initialized");


        applicationContext = SpringApplication.run(KafkaSpringTemplateApplication.class, args);
        Environment envNoBean = applicationContext.getEnvironment();

    }


    @EnableKafka
    @Configuration
    public class KafkaConfig {

        @Bean
        public KafkaTemplate<String, avroMsg> kafkaTemplate() {
            return new KafkaTemplate<String, avroMsg>((org.springframework.kafka.core.ProducerFactory<String, avroMsg>) producerFactory());
        }

        @Bean
        public ProducerFactory producerFactory() {
            return (ProducerFactory) new DefaultKafkaProducerFactory<>(producerConfigs());
        }

        @Bean
        public Map<String, Object> producerConfigs() {
            Map<String, Object> configs = new HashMap<>();
            configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, env.getProperty("bootstrap.servers"));
            configs.put("schema.registry.url", env.getProperty("schema.registry.url"));
            configs.put("schema.registry.basic.auth.user.info", env.getProperty("schema.registry.basic.auth.user.info"));
            configs.put("basic.auth.credentials.source", env.getProperty("basic.auth.credentials.source"));
            configs.put("sasl.mechanism", env.getProperty("sasl.mechanism"));
            configs.put("sasl.jaas.config", env.getProperty("sasl.jaas.config"));
            configs.put("security.protocol", env.getProperty("security.protocol"));
            configs.put("client.dns.lookup", env.getProperty("client.dns.lookup"));
            configs.put("acks", "all");
            configs.put("auto.create.topics.enable", "true");
            configs.put("auto.register.schema", "false");
            configs.put("json.fail.invalid.schema", "true");
            configs.put("enable.idempotence", env.getProperty("enable.idempotence"));
            //configs.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, TracingProducerInterceptor.class.getName());
            configs.put("client.id", env.getProperty("producer.id"));
            configs.put("value.serializer", io.confluent.kafka.serializers.KafkaAvroSerializer.class.getName());
            configs.put("key.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getName());
            configs.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, TracingProducerInterceptor.class.getName());
            // See https://kafka.apache.org/documentation/#producerconfigs for more properties
            return configs;
        }


        @Bean
        public Map<String, Object> consumerProperties() {
            Map<String, Object> configscon = new HashMap<>();
            configscon.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, env.getProperty("bootstrap.servers"));
            configscon.put("schema.registry.url", env.getProperty("schema.registry.url"));
            configscon.put("schema.registry.basic.auth.user.info", env.getProperty("schema.registry.basic.auth.user.info"));
            configscon.put("basic.auth.credentials.source", env.getProperty("basic.auth.credentials.source"));
            configscon.put("sasl.mechanism", env.getProperty("sasl.mechanism"));
            configscon.put("sasl.jaas.config", env.getProperty("sasl.jaas.config"));
            configscon.put("security.protocol", env.getProperty("security.protocol"));
            configscon.put("client.dns.lookup", env.getProperty("client.dns.lookup"));
            configscon.put("acks", "all");
            configscon.put("auto.create.topics.enable", "true");
            configscon.put("auto.register.schema", "true");
            configscon.put("json.fail.invalid.schema", "true");
            //configscon.put("enable.idempotence", env.getProperty("enable.idempotence"));
            //configscon.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, TracingProducerInterceptor.class.getName());
            configscon.put("group.id", env.getProperty("consume.group.id"));
            configscon.put("value.deserializer", io.confluent.kafka.serializers.KafkaAvroDeserializer.class.getName());
            configscon.put("key.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class.getName());
            configscon.put("specific.avro.reader", "true");
            configscon.put("spring.kafka.consumer.properties.partition.assignment.strategy",
                    "org.apache.kafka.clients.consumer.RoundRobinAssignor");
            configscon.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, TracingConsumerInterceptor.class.getName());
            //       "org.apache.kafka.clients.consumer.RangeAssignor");
            // See https://kafka.apache.org/documentation/#producerconfigs for more properties
            return configscon;
        }

        @Bean
        public ConsumerFactory<String, avroMsg> consumerFactory() {
            return (ConsumerFactory) new DefaultKafkaConsumerFactory<String, avroMsg>(consumerProperties());
        }

        @Bean
        public ConcurrentKafkaListenerContainerFactory<String, avroMsg>
        kafkaListenerContainerFactory() {

            ConcurrentKafkaListenerContainerFactory<String, avroMsg> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory());
            factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
            factory.setCommonErrorHandler(errorHandler());
            factory.setConcurrency(6);
            return factory;
        }

        @Bean
        public KafkaAdmin kafkaAdmin() {
            return new KafkaAdmin(producerConfigs());
        }




        @Bean
        public DefaultErrorHandler errorHandler() {
            BackOff fixedBackOff = new FixedBackOff(100, 3);
            DefaultErrorHandler errorHandler = new DefaultErrorHandler((consumerRecord, e) -> {
                System.out.println(String.format("consumed record %s because this exception was thrown", consumerRecord.toString(), e.getClass().getName()));
            }, fixedBackOff);
            errorHandler.addRetryableExceptions(SocketTimeoutException.class, RuntimeException.class);
            errorHandler.addNotRetryableExceptions(NullPointerException.class);
            //errorHandler.addNotRetryableExceptions(NullPointerException.class);
            return errorHandler;
        }




        /*
        //This works but without DLT or topic writes for retries
        @Component
        @KafkaListener(id = "KafkaTemplateID", topics = "SpringTemplateTopic")
        public class RetryListener {

            //@KafkaListener(id = "KafkaTemplateID", topics = "SpringTemplateTopic")
            //public class ListenerHandler {



                @KafkaHandler
                //@KafkaListener(id = "KafkaTemplateID", topics = "SpringTemplateTopic")
                public void handleAvroMessage(avroMsg message) {
                    if (message.getFirstName()
                            .equalsIgnoreCase("test")) {
                        throw new MessagingException("test not allowed");
                    }
                    System.out.println("Avro Message received: " + message);
                }


                @DltHandler
                public void processMessage(avroMsg message) {
                    System.out.println("DLT received: " + message);
                }


            //}
        }


         */







        @Component
        @Configuration
        public class UpdateItemConsumer {

            @RetryableTopic(
                    attempts = "4",
                    autoCreateTopics = "true",
                    backoff = @Backoff(delay = 100, multiplier = 3.0),
                    concurrency = "1",
                    topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)

            @KafkaListener(id = "KafkaTemplateID", topics = "SpringTemplateTopic")
            //public void listen(@Payload final avroMsg payload) {
            public void listen(avroMsg record, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
                logger.info("Update Item Consumer: Received message with payload: " + record.toString());

                    if (record.getFirstName()
                            .equalsIgnoreCase("test")) {
                        throw new MessagingException("Username \"test\" not allowed");
                    }
                    System.out.println("Avro Message received: " + record.toString());
            }


            @DltHandler
            public void dlt(avroMsg record, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
                logger.error("Event from topic " + topic + " is dead lettered - event:" + record.toString());
            }
        }



    }


}

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.FileNotFoundException;

@org.springframework.web.bind.annotation.RestController
@RequestMapping(value = "/")
public class restController {
    @Autowired
    private Environment env;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private KafkaTemplate<String, avroMsg> kafkaTemplate;

    @Autowired
    private SendAvroMsg sendAvroMsg;

    //ConfluentSession confluentSession;

    Tracer tracer = GlobalOpenTelemetry.get().getTracer("ServerApp");
    Context parentContext = Context.current();
    //Context parentContext = Context.current();

    private final Log logger = LogFactory.getLog(restController.class);

    //Constructor
    @Autowired
    public restController(ApplicationContext applicationContext) {

    }



    @PostMapping("/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void postMessage(@RequestBody JsonMsg request,
                            HttpServletRequest httpRequest) {


        Span span = tracer.spanBuilder("restController")
                .setParent(parentContext)
                .setAttribute("Servlet Path", httpRequest.getServletPath())
                .setAttribute("Customer ID", request.getCustomerId())
                .setAttribute("Protoocl", httpRequest.getProtocol())
                .setAttribute("Content Length", httpRequest.getContentLength())
                .startSpan();



       try (Scope scope = span.makeCurrent()) {

            String JsonStr = "";
            ObjectMapper mapper = new ObjectMapper();
            //Output the POST message to confirm what was received

            try {
                JsonStr = mapper.writeValueAsString(request);
            } catch (JsonProcessingException je) {
                logger.info("++++++++++++++++++++JSON Error: \n:");
                je.printStackTrace();
            }
            logger.info(String.format("JSON REST POST Data -> %s ", JsonStr));

            try {
                sendAvroMsg.sendMessage(request);
            } catch (FileNotFoundException fe) {
                fe.printStackTrace();
            }


            /*
            try {
                this.confluentSession.sendAvroMessage(request);
            } catch (Exception e) {
                e.printStackTrace();
            }

             */
        } finally {
            span.end();
        }

    }

    @RequestMapping("/logtest")
    public String index() {
        final Log logger = LogFactory.getLog(getClass());

        logger.trace("A TRACE Message");
        logger.debug("A DEBUG Message");
        logger.info("An INFO Message");
        logger.warn("A WARN Message");
        logger.error("An ERROR Message");

        return "See the Logs to check the output for the levels of supported logging ...";
    }

}

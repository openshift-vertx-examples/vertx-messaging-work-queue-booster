/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.openshift.booster.messaging;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;

public class Worker {
    private static String id = "worker-vertx-" +
        (Math.round(Math.random() * (10000 - 1000)) + 1000);

    private static AtomicInteger requestsProcessed = new AtomicInteger(0);

    public static void main(String[] args) {
        try {
            String amqpHost = System.getenv("MESSAGING_SERVICE_HOST");
            String amqpPortString = System.getenv("MESSAGING_SERVICE_PORT");
            String amqpUser = System.getenv("MESSAGING_SERVICE_USER");
            String amqpPassword = System.getenv("MESSAGING_SERVICE_PASSWORD");

            String httpHost = System.getenv("HTTP_HOST");
            String httpPortString = System.getenv("HTTP_PORT");

            if (amqpHost == null) {
                amqpHost = "localhost";
            }

            if (amqpPortString == null) {
                amqpPortString = "5672";
            }

            if (amqpUser == null) {
                amqpUser = "work-queue";
            }

            if (amqpPassword == null) {
                amqpPassword = "work-queue";
            }

            if (httpHost == null) {
                httpHost = "localhost";
            }

            if (httpPortString == null) {
                httpPortString = "8080";
            }

            int amqpPort = Integer.parseInt(amqpPortString);
            int httpPort = Integer.parseInt(httpPortString);

            // AMQP

            Vertx vertx = Vertx.vertx();
            ProtonClient client = ProtonClient.create(vertx);

            client.connect(amqpHost, amqpPort, amqpUser, amqpPassword, (res) -> {
                    if (res.failed()) {
                        res.cause().printStackTrace();
                        return;
                    }

                    ProtonConnection conn = res.result();
                    conn.setContainer(id);
                    conn.open();

                    receiveRequests(vertx, conn);
                    sendStatusUpdates(vertx, conn);
                });

            // HTTP

            Router router = Router.router(vertx);

            router.get("/api/health/readiness").handler(Worker::handleGetReadiness);
            router.get("/api/health/liveness").handler(Worker::handleGetLiveness);

            vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(httpPort, httpHost, (result) -> {
                        if (result.failed()) {
                            result.cause().printStackTrace();
                            return;
                        }
                    });

            while (true) {
                Thread.sleep(60 * 1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void receiveRequests(Vertx vertx, ProtonConnection conn) {
        ProtonReceiver receiver = conn.createReceiver("work-queue/requests");

        // Ordinarily, a sender or receiver is tied to a named message
        // source or target. By contrast, a null sender transmits
        // messages using an "anonymous" link and routes them to their
        // destination using the "to" property of the message.
        ProtonSender sender = conn.createSender(null);

        receiver.handler((delivery, request) -> {
                String requestBody = (String) ((AmqpValue) request.getBody()).getValue();
                System.out.println("WORKER: Received request '" + requestBody + "'");

                String responseBody;

                try {
                    responseBody = processRequest(request);
                } catch (Exception e) {
                    System.err.println("WORKER: Failed processing request: " + e);
                    return;
                }

                System.out.println("WORKER: Sending response '" + responseBody + "'");

                Map<String, Object> props = new HashMap<String, Object>();
                props.put("worker_id", conn.getContainer());

                Message response = Message.Factory.create();
                response.setAddress(request.getReplyTo());
                response.setCorrelationId(request.getMessageId());
                response.setBody(new AmqpValue(responseBody));
                response.setApplicationProperties(new ApplicationProperties(props));

                sender.send(response);

                requestsProcessed.incrementAndGet();
            });

        sender.open();
        receiver.open();
    }

    private static String processRequest(Message request) throws Exception {
        String requestBody = (String) ((AmqpValue) request.getBody()).getValue();
        return requestBody.toUpperCase();
    }

    private static void sendStatusUpdates(Vertx vertx, ProtonConnection conn) {
        ProtonSender sender = conn.createSender("work-queue/worker-updates");

        vertx.setPeriodic(5 * 1000, (timer) -> {
                if (conn.isDisconnected()) {
                    vertx.cancelTimer(timer);
                    return;
                }

                if (sender.sendQueueFull()) {
                    return;
                }

                System.out.println("WORKER: Sending status update");

                Map<String, Object> props = new HashMap<String, Object>();
                props.put("workerId", conn.getContainer());
                props.put("timestamp", System.currentTimeMillis());
                props.put("requestsProcessed", requestsProcessed.get());

                Message status = Message.Factory.create();
                status.setApplicationProperties(new ApplicationProperties(props));

                sender.send(status);
            });

        sender.open();
    }

    private static void handleGetReadiness(RoutingContext rc) {
        rc.response().end("OK");
    }

    private static void handleGetLiveness(RoutingContext rc) {
        rc.response().end("OK");
    }
}

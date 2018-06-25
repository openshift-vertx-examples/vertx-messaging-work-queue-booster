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

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Data {
    private final Queue<Response> responses;
    private final Map<String, WorkerUpdate> workers;

    public Data() {
        this.responses = new ConcurrentLinkedQueue<>();
        this.workers = new ConcurrentHashMap<>();
    }

    public Queue<Response> getResponses() {
        return responses;
    }

    public Map<String, WorkerUpdate> getWorkers() {
        return workers;
    }

    @Override
    public String toString() {
        return String.format("Data{responses='%s', workers='%s'}", responses, workers);
    }
}

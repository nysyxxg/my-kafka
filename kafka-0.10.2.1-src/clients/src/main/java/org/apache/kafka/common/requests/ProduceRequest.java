/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.apache.kafka.common.requests;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.protocol.ProtoUtils;
import org.apache.kafka.common.protocol.types.Struct;
import org.apache.kafka.common.record.MemoryRecords;
import org.apache.kafka.common.utils.CollectionUtils;
import org.apache.kafka.common.utils.Utils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProduceRequest extends AbstractRequest {
    private static final String ACKS_KEY_NAME = "acks";
    private static final String TIMEOUT_KEY_NAME = "timeout";
    private static final String TOPIC_DATA_KEY_NAME = "topic_data";

    // topic level field names
    private static final String TOPIC_KEY_NAME = "topic";
    private static final String PARTITION_DATA_KEY_NAME = "data";

    // partition level field names
    private static final String PARTITION_KEY_NAME = "partition";
    private static final String RECORD_SET_KEY_NAME = "record_set";

    public static class Builder extends AbstractRequest.Builder<ProduceRequest> {
        private final short acks;
        private final int timeout;
        private final Map<TopicPartition, MemoryRecords> partitionRecords;

        public Builder(short acks, int timeout, Map<TopicPartition, MemoryRecords> partitionRecords) {
            super(ApiKeys.PRODUCE);
            this.acks = acks;
            this.timeout = timeout;
            this.partitionRecords = partitionRecords;
        }

        @Override
        public ProduceRequest build() {
            short version = version();
            if (version < 2) {
                throw new UnsupportedVersionException("ProduceRequest versions older than 2 are not supported.");
            }
            return new ProduceRequest(version, acks, timeout, partitionRecords);
        }

        @Override
        public String toString() {
            StringBuilder bld = new StringBuilder();
            bld.append("(type=ProduceRequest")
                    .append(", acks=").append(acks)
                    .append(", timeout=").append(timeout)
                    .append(", partitionRecords=(").append(Utils.mkString(partitionRecords))
                    .append("))");
            return bld.toString();
        }
    }

    private final short acks;
    private final int timeout;

    private final Map<TopicPartition, Integer> partitionSizes;

    // This is set to null by `clearPartitionRecords` to prevent unnecessary memory retention when a produce request is
    // put in the purgatory (due to client throttling, it can take a while before the response is sent).
    // Care should be taken in methods that use this field.
    private volatile Map<TopicPartition, MemoryRecords> partitionRecords;

    private ProduceRequest(short version, short acks, int timeout, Map<TopicPartition, MemoryRecords> partitionRecords) {
        super(new Struct(ProtoUtils.requestSchema(ApiKeys.PRODUCE.id, version)), version);
        Map<String, Map<Integer, MemoryRecords>> recordsByTopic = CollectionUtils.groupDataByTopic(partitionRecords);
        struct.set(ACKS_KEY_NAME, acks);
        struct.set(TIMEOUT_KEY_NAME, timeout);
        List<Struct> topicDatas = new ArrayList<>(recordsByTopic.size());
        for (Map.Entry<String, Map<Integer, MemoryRecords>> entry : recordsByTopic.entrySet()) {
            Struct topicData = struct.instance(TOPIC_DATA_KEY_NAME);
            topicData.set(TOPIC_KEY_NAME, entry.getKey());
            List<Struct> partitionArray = new ArrayList<>();
            for (Map.Entry<Integer, MemoryRecords> partitionEntry : entry.getValue().entrySet()) {
                MemoryRecords records = partitionEntry.getValue();
                Struct part = topicData.instance(PARTITION_DATA_KEY_NAME)
                                       .set(PARTITION_KEY_NAME, partitionEntry.getKey())
                                       .set(RECORD_SET_KEY_NAME, records);
                partitionArray.add(part);
            }
            topicData.set(PARTITION_DATA_KEY_NAME, partitionArray.toArray());
            topicDatas.add(topicData);
        }
        struct.set(TOPIC_DATA_KEY_NAME, topicDatas.toArray());
        this.acks = acks;
        this.timeout = timeout;
        this.partitionRecords = partitionRecords;
        this.partitionSizes = createPartitionSizes(partitionRecords);
    }

    private static Map<TopicPartition, Integer> createPartitionSizes(Map<TopicPartition, MemoryRecords> partitionRecords) {
        Map<TopicPartition, Integer> result = new HashMap<>(partitionRecords.size());
        for (Map.Entry<TopicPartition, MemoryRecords> entry : partitionRecords.entrySet())
            result.put(entry.getKey(), entry.getValue().sizeInBytes());
        return result;
    }

    public ProduceRequest(Struct struct, short version) {
        super(struct, version);
        partitionRecords = new HashMap<>();
        for (Object topicDataObj : struct.getArray(TOPIC_DATA_KEY_NAME)) {
            Struct topicData = (Struct) topicDataObj;
            String topic = topicData.getString(TOPIC_KEY_NAME);
            for (Object partitionResponseObj : topicData.getArray(PARTITION_DATA_KEY_NAME)) {
                Struct partitionResponse = (Struct) partitionResponseObj;
                int partition = partitionResponse.getInt(PARTITION_KEY_NAME);
                MemoryRecords records = (MemoryRecords) partitionResponse.getRecords(RECORD_SET_KEY_NAME);
                partitionRecords.put(new TopicPartition(topic, partition), records);
            }
        }
        partitionSizes = createPartitionSizes(partitionRecords);
        acks = struct.getShort(ACKS_KEY_NAME);
        timeout = struct.getInt(TIMEOUT_KEY_NAME);
    }

    @Override
    public String toString() {
        // Use the same format as `Struct.toString()`
        StringBuilder bld = new StringBuilder();
        bld.append("{acks=").append(acks)
                .append(",timeout=").append(timeout)
                .append(",partitionSizes=")
                .append(Utils.mkString(partitionSizes, "[", "]", "=", ","))
                .append("}");
        return bld.toString();
    }

    @Override
    public AbstractResponse getErrorResponse(Throwable e) {
        /* In case the producer doesn't actually want any response */
        if (acks == 0)
            return null;

        Map<TopicPartition, ProduceResponse.PartitionResponse> responseMap = new HashMap<>();
        ProduceResponse.PartitionResponse partitionResponse = new ProduceResponse.PartitionResponse(Errors.forException(e));

        for (TopicPartition tp : partitions())
            responseMap.put(tp, partitionResponse);

        short versionId = version();
        switch (versionId) {
            case 0:
                return new ProduceResponse(responseMap);
            case 1:
            case 2:
                return new ProduceResponse(responseMap, ProduceResponse.DEFAULT_THROTTLE_TIME, versionId);
            default:
                throw new IllegalArgumentException(String.format("Version %d is not valid. Valid versions for %s are 0 to %d",
                        versionId, this.getClass().getSimpleName(), ProtoUtils.latestVersion(ApiKeys.PRODUCE.id)));
        }
    }

    private Collection<TopicPartition> partitions() {
        return partitionSizes.keySet();
    }

    public short acks() {
        return acks;
    }

    public int timeout() {
        return timeout;
    }

    /**
     * Returns the partition records or throws IllegalStateException if clearPartitionRecords() has been invoked.
     */
    public Map<TopicPartition, MemoryRecords> partitionRecordsOrFail() {
        // Store it in a local variable to protect against concurrent updates
        Map<TopicPartition, MemoryRecords> partitionRecords = this.partitionRecords;
        if (partitionRecords == null)
            throw new IllegalStateException("The partition records are no longer available because " +
                    "clearPartitionRecords() has been invoked.");
        return partitionRecords;
    }

    public void clearPartitionRecords() {
        partitionRecords = null;
        // It would be better to make this null, but the change is too large for 0.10.2. In trunk, the struct field
        // was removed
        struct.clear();
    }

    public static ProduceRequest parse(ByteBuffer buffer, int versionId) {
        return new ProduceRequest(ProtoUtils.parseRequest(ApiKeys.PRODUCE.id, versionId, buffer), (short) versionId);
    }

    public static ProduceRequest parse(ByteBuffer buffer) {
        return parse(buffer, ProtoUtils.latestVersion(ApiKeys.PRODUCE.id));
    }
}

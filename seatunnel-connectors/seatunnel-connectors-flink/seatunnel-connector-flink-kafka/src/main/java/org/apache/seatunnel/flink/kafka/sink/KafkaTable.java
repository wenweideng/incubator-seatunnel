/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.flink.kafka.sink;

import org.apache.seatunnel.common.PropertiesUtil;
import org.apache.seatunnel.common.config.CheckConfigUtil;
import org.apache.seatunnel.common.config.CheckResult;
import org.apache.seatunnel.flink.FlinkEnvironment;
import org.apache.seatunnel.flink.stream.FlinkStreamSink;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.formats.json.JsonRowSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.Nullable;

import java.util.Properties;

public class KafkaTable implements FlinkStreamSink {

    private static final long serialVersionUID = 3980751499724935230L;
    private Config config;
    private Properties kafkaParams = new Properties();
    private String topic;

    @Override
    @Nullable
    public DataStreamSink<Row> outputStream(FlinkEnvironment env, DataStream<Row> dataStream) {
        StreamTableEnvironment tableEnvironment = env.getStreamTableEnvironment();
        Table table = tableEnvironment.fromDataStream(dataStream);
        TypeInformation<?>[] types = table.getSchema().getFieldTypes();
        String[] fieldNames = table.getSchema().getFieldNames();
        JsonRowSerializationSchema jsonRowSerializationSchema = JsonRowSerializationSchema
                .builder()
                .withTypeInfo(Types.ROW_NAMED(fieldNames, types))
                .build();

        dataStream.addSink(
                new FlinkKafkaProducer<Row>(
                    topic,
                    new KafkaSerializationSchema<Row>() {
                        @Override
                        public ProducerRecord<byte[], byte[]> serialize(Row row, @Nullable Long timestamp) {
                            return new ProducerRecord<>(topic, jsonRowSerializationSchema.serialize(row));
                        }
                    },
                    kafkaParams,
                    FlinkKafkaProducer.Semantic.EXACTLY_ONCE
                )
        );
        return null;
    }

    @Override
    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public CheckResult checkConfig() {
        return CheckConfigUtil.checkAllExists(config, "topics");
    }

    @Override
    public void prepare(FlinkEnvironment env) {
        topic = config.getString("topics");
        String producerPrefix = "producer.";
        PropertiesUtil.setProperties(config, kafkaParams, producerPrefix, false);
        kafkaParams.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        kafkaParams.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    }
}
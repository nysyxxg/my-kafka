/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.clients.producer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.ApiVersions;
import org.apache.kafka.clients.ClientDnsLookup;
import org.apache.kafka.clients.ClientUtils;
import org.apache.kafka.clients.KafkaClient;
import org.apache.kafka.clients.Metadata;
import org.apache.kafka.clients.NetworkClient;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.clients.producer.internals.BufferPool;
import org.apache.kafka.clients.producer.internals.ProducerInterceptors;
import org.apache.kafka.clients.producer.internals.ProducerMetrics;
import org.apache.kafka.clients.producer.internals.RecordAccumulator;
import org.apache.kafka.clients.producer.internals.Sender;
import org.apache.kafka.clients.producer.internals.TransactionManager;
import org.apache.kafka.clients.producer.internals.TransactionalRequestResult;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.ApiException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.internals.ClusterResourceListeners;
import org.apache.kafka.common.metrics.JmxReporter;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.apache.kafka.common.metrics.Sensor;
import org.apache.kafka.common.network.ChannelBuilder;
import org.apache.kafka.common.network.Selector;
import org.apache.kafka.common.record.AbstractRecords;
import org.apache.kafka.common.record.CompressionType;
import org.apache.kafka.common.record.RecordBatch;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.utils.AppInfoParser;
import org.apache.kafka.common.utils.KafkaThread;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.Time;
import org.slf4j.Logger;


/**
 * A Kafka client that publishes records to the Kafka cluster.
 * <P>
 * The producer is <i>thread safe</i> and sharing a single producer instance across threads will generally be faster than
 * having multiple instances.
 * <p>
 * Here is a simple example of using the producer to send records with strings containing sequential numbers as the key/value
 * pairs.
 * <pre>
 * {@code
 * Properties props = new Properties();
 * props.put("bootstrap.servers", "localhost:9092");
 * props.put("acks", "all");
 * props.put("delivery.timeout.ms", 30000);
 * props.put("batch.size", 16384);
 * props.put("linger.ms", 1);
 * props.put("buffer.memory", 33554432);
 * props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
 * props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
 *
 * Producer<String, String> producer = new KafkaProducer<>(props);
 * for (int i = 0; i < 100; i++)
 *     producer.send(new ProducerRecord<String, String>("my-topic", Integer.toString(i), Integer.toString(i)));
 *
 * producer.close();
 * }</pre>
 * <p>
 * The producer consists of a pool of buffer space that holds records that haven't yet been transmitted to the server
 * as well as a background I/O thread that is responsible for turning these records into requests and transmitting them
 * to the cluster. Failure to close the producer after use will leak these resources.
 * <p>
 * The {@link #send(ProducerRecord) send()} method is asynchronous. When called it adds the record to a buffer of pending record sends
 * and immediately returns. This allows the producer to batch together individual records for efficiency.
 * <p>
 * The <code>acks</code> config controls the criteria under which requests are considered complete. The "all" setting
 * we have specified will result in blocking on the full commit of the record, the slowest but most durable setting.
 * <p>
 * If the request fails, the producer can automatically retry, though since we have specified <code>retries</code>
 * as 0 it won't. Enabling retries also opens up the possibility of duplicates (see the documentation on
 * <a href="http://kafka.apache.org/documentation.html#semantics">message delivery semantics</a> for details).
 * <p>
 * The producer maintains buffers of unsent records for each partition. These buffers are of a size specified by
 * the <code>batch.size</code> config. Making this larger can result in more batching, but requires more memory (since we will
 * generally have one of these buffers for each active partition).
 * <p>
 * By default a buffer is available to send immediately even if there is additional unused space in the buffer. However if you
 * want to reduce the number of requests you can set <code>linger.ms</code> to something greater than 0. This will
 * instruct the producer to wait up to that number of milliseconds before sending a request in hope that more records will
 * arrive to fill up the same batch. This is analogous to Nagle's algorithm in TCP. For example, in the code snippet above,
 * likely all 100 records would be sent in a single request since we set our linger time to 1 millisecond. However this setting
 * would add 1 millisecond of latency to our request waiting for more records to arrive if we didn't fill up the buffer. Note that
 * records that arrive close together in time will generally batch together even with <code>linger.ms=0</code> so under heavy load
 * batching will occur regardless of the linger configuration; however setting this to something larger than 0 can lead to fewer, more
 * efficient requests when not under maximal load at the cost of a small amount of latency.
 * <p>
 * The <code>buffer.memory</code> controls the total amount of memory available to the producer for buffering. If records
 * are sent faster than they can be transmitted to the server then this buffer space will be exhausted. When the buffer space is
 * exhausted additional send calls will block. The threshold for time to block is determined by <code>max.block.ms</code> after which it throws
 * a TimeoutException.
 * <p>
 * The <code>key.serializer</code> and <code>value.serializer</code> instruct how to turn the key and value objects the user provides with
 * their <code>ProducerRecord</code> into bytes. You can use the included {@link org.apache.kafka.common.serialization.ByteArraySerializer} or
 * {@link org.apache.kafka.common.serialization.StringSerializer} for simple string or byte types.
 * <p>
 * From Kafka 0.11, the KafkaProducer supports two additional modes: the idempotent producer and the transactional producer.
 * The idempotent producer strengthens Kafka's delivery semantics from at least once to exactly once delivery. In particular
 * producer retries will no longer introduce duplicates. The transactional producer allows an application to send messages
 * to multiple partitions (and topics!) atomically.
 * </p>
 * <p>
 * To enable idempotence, the <code>enable.idempotence</code> configuration must be set to true. If set, the
 * <code>retries</code> config will default to <code>Integer.MAX_VALUE</code> and the <code>acks</code> config will
 * default to <code>all</code>. There are no API changes for the idempotent producer, so existing applications will
 * not need to be modified to take advantage of this feature.
 * </p>
 * <p>
 * To take advantage of the idempotent producer, it is imperative to avoid application level re-sends since these cannot
 * be de-duplicated. As such, if an application enables idempotence, it is recommended to leave the <code>retries</code>
 * config unset, as it will be defaulted to <code>Integer.MAX_VALUE</code>. Additionally, if a {@link #send(ProducerRecord)}
 * returns an error even with infinite retries (for instance if the message expires in the buffer before being sent),
 * then it is recommended to shut down the producer and check the contents of the last produced message to ensure that
 * it is not duplicated. Finally, the producer can only guarantee idempotence for messages sent within a single session.
 * </p>
 * <p>To use the transactional producer and the attendant APIs, you must set the <code>transactional.id</code>
 * configuration property. If the <code>transactional.id</code> is set, idempotence is automatically enabled along with
 * the producer configs which idempotence depends on. Further, topics which are included in transactions should be configured
 * for durability. In particular, the <code>replication.factor</code> should be at least <code>3</code>, and the
 * <code>min.insync.replicas</code> for these topics should be set to 2. Finally, in order for transactional guarantees
 * to be realized from end-to-end, the consumers must be configured to read only committed messages as well.
 * </p>
 * <p>
 * The purpose of the <code>transactional.id</code> is to enable transaction recovery across multiple sessions of a
 * single producer instance. It would typically be derived from the shard identifier in a partitioned, stateful, application.
 * As such, it should be unique to each producer instance running within a partitioned application.
 * </p>
 * <p>All the new transactional APIs are blocking and will throw exceptions on failure. The example
 * below illustrates how the new APIs are meant to be used. It is similar to the example above, except that all
 * 100 messages are part of a single transaction.
 * </p>
 * <p>
 * <pre>
 * {@code
 * Properties props = new Properties();
 * props.put("bootstrap.servers", "localhost:9092");
 * props.put("transactional.id", "my-transactional-id");
 * Producer<String, String> producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());
 *
 * producer.initTransactions();
 *
 * try {
 *     producer.beginTransaction();
 *     for (int i = 0; i < 100; i++)
 *         producer.send(new ProducerRecord<>("my-topic", Integer.toString(i), Integer.toString(i)));
 *     producer.commitTransaction();
 * } catch (ProducerFencedException | OutOfOrderSequenceException | AuthorizationException e) {
 *     // We can't recover from these exceptions, so our only option is to close the producer and exit.
 *     producer.close();
 * } catch (KafkaException e) {
 *     // For all other exceptions, just abort the transaction and try again.
 *     producer.abortTransaction();
 * }
 * producer.close();
 * } </pre>
 * </p>
 * <p>
 * As is hinted at in the example, there can be only one open transaction per producer. All messages sent between the
 * {@link #beginTransaction()} and {@link #commitTransaction()} calls will be part of a single transaction. When the
 * <code>transactional.id</code> is specified, all messages sent by the producer must be part of a transaction.
 * </p>
 * <p>
 * The transactional producer uses exceptions to communicate error states. In particular, it is not required
 * to specify callbacks for <code>producer.send()</code> or to call <code>.get()</code> on the returned Future: a
 * <code>KafkaException</code> would be thrown if any of the
 * <code>producer.send()</code> or transactional calls hit an irrecoverable error during a transaction. See the {@link #send(ProducerRecord)}
 * documentation for more details about detecting errors from a transactional send.
 * </p>
 * </p>By calling
 * <code>producer.abortTransaction()</code> upon receiving a <code>KafkaException</code> we can ensure that any
 * successful writes are marked as aborted, hence keeping the transactional guarantees.
 * </p>
 * <p>
 * This client can communicate with brokers that are version 0.10.0 or newer. Older or newer brokers may not support
 * certain client features.  For instance, the transactional APIs need broker versions 0.11.0 or later. You will receive an
 * <code>UnsupportedVersionException</code> when invoking an API that is not available in the running broker version.
 * </p>
 */
public class KafkaProducer<K, V> implements Producer<K, V> {

    private final Logger log;
    /** clientId 生成器，如果没有明确指定客户端 ID，则使用该字段顺序生成一个 */
    private static final AtomicInteger PRODUCER_CLIENT_ID_SEQUENCE = new AtomicInteger(1);
    private static final String JMX_PREFIX = "kafka.producer";
    public static final String NETWORK_THREAD_PREFIX = "kafka-producer-network-thread";
    public static final String PRODUCER_METRIC_GROUP_NAME = "producer-metrics";

    /** 生产者唯一标识（对应 client.id 属性配置 ） */
    private final String clientId;
    // Visible for testing
    final Metrics metrics;
    /** 分区选择器（对应 partitioner.class 属性配置），如果未明确指定分区，则基于一定的策略为消息选择合适的分区 */
    private final Partitioner partitioner;
    /** 消息的最大长度（对应 max.request.size 配置，包含消息头、序列化之后的 key 和 value） */
    private final int maxRequestSize;
    /** 发送单条消息的缓冲区大小（对应 buffer.memory 配置） */
    private final long totalMemorySize;
    /** kafka 集群元数据 */
    private final Metadata metadata;
    /** 消息收集器，用于收集并缓存消息，等待 Sender 线程的发送 */
    private final RecordAccumulator accumulator;
    /** 消息发送线程对象 */
    private final Sender sender;
    /** 消息发送线程 */
    private final Thread ioThread;
    /** 压缩算法（对应 compression.type 配置） */
    private final CompressionType compressionType;
    private final Sensor errors;
    /** 时间戳工具 */
    private final Time time;
    /** key 序列化器（对应 key.serializer 配置） */
    private final Serializer<K> keySerializer;
    /** value 序列化器（对应 value.serializer 配置） */
    private final Serializer<V> valueSerializer;
    /** 封装配置信息 */
    private final ProducerConfig producerConfig;
    /** 等待更新 kafka 集群元数据的最大时长 */
    private final long maxBlockTimeMs;
    /** 发送拦截器（对应 interceptor.classes 配置），用于待发送的消息进行拦截并修改，也可以对 ACK 响应进行拦截处理 */
    private final ProducerInterceptors<K, V> interceptors;
    /** 版本 */
    private final ApiVersions apiVersions;
    /** 事务 */
    private final TransactionManager transactionManager;
    private TransactionalRequestResult initTransactionsResult;

    /**
     * A producer is instantiated by providing a set of key-value pairs as configuration. Valid configuration strings
     * are documented <a href="http://kafka.apache.org/documentation.html#producerconfigs">here</a>. Values can be
     * either strings or Objects of the appropriate type (for example a numeric configuration would accept either the
     * string "42" or the integer 42).
     * <p>
     * Note: after creating a {@code KafkaProducer} you must always {@link #close()} it to avoid resource leaks.
     * @param configs   The producer configs
     *
     */
    public KafkaProducer(final Map<String, Object> configs) {
        this(new ProducerConfig(configs), null, null, null, null, null, Time.SYSTEM);
    }

    /**
     * A producer is instantiated by providing a set of key-value pairs as configuration, a key and a value {@link Serializer}.
     * Valid configuration strings are documented <a href="http://kafka.apache.org/documentation.html#producerconfigs">here</a>.
     * Values can be either strings or Objects of the appropriate type (for example a numeric configuration would accept
     * either the string "42" or the integer 42).
     * <p>
     * Note: after creating a {@code KafkaProducer} you must always {@link #close()} it to avoid resource leaks.
     * @param configs   The producer configs
     * @param keySerializer  The serializer for key that implements {@link Serializer}. The configure() method won't be
     *                       called in the producer when the serializer is passed in directly.
     * @param valueSerializer  The serializer for value that implements {@link Serializer}. The configure() method won't
     *                         be called in the producer when the serializer is passed in directly.
     */
    public KafkaProducer(Map<String, Object> configs, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this(new ProducerConfig(ProducerConfig.addSerializerToConfig(configs, keySerializer, valueSerializer)),
            keySerializer, valueSerializer, null, null, null, Time.SYSTEM);
    }

    /**
     * A producer is instantiated by providing a set of key-value pairs as configuration. Valid configuration strings
     * are documented <a href="http://kafka.apache.org/documentation.html#producerconfigs">here</a>.
     * <p>
     * Note: after creating a {@code KafkaProducer} you must always {@link #close()} it to avoid resource leaks.
     * @param properties   The producer configs
     */
    public KafkaProducer(Properties properties) {
        this(new ProducerConfig(properties), null, null, null, null, null, Time.SYSTEM);
    }

    /**
     * A producer is instantiated by providing a set of key-value pairs as configuration, a key and a value {@link Serializer}.
     * Valid configuration strings are documented <a href="http://kafka.apache.org/documentation.html#producerconfigs">here</a>.
     * <p>
     * Note: after creating a {@code KafkaProducer} you must always {@link #close()} it to avoid resource leaks.
     * @param properties   The producer configs
     * @param keySerializer  The serializer for key that implements {@link Serializer}. The configure() method won't be
     *                       called in the producer when the serializer is passed in directly.
     * @param valueSerializer  The serializer for value that implements {@link Serializer}. The configure() method won't
     *                         be called in the producer when the serializer is passed in directly.
     */
    public KafkaProducer(Properties properties, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this(new ProducerConfig(ProducerConfig.addSerializerToConfig(properties, keySerializer, valueSerializer)),
                keySerializer, valueSerializer, null, null, null, Time.SYSTEM);
    }

    // visible for testing
    @SuppressWarnings("unchecked")
    KafkaProducer(ProducerConfig config,
                  Serializer<K> keySerializer,
                  Serializer<V> valueSerializer,
                  Metadata metadata,
                  KafkaClient kafkaClient,
                  ProducerInterceptors interceptors,
                  Time time) {
        try {
            //todo: 配置一些用户自定义的参数
            Map<String, Object> userProvidedConfigs = config.originals();
            this.producerConfig = config;
            this.time = time;

            //todo: 配置client id编号，一般不配置
            String clientId = config.getString(ProducerConfig.CLIENT_ID_CONFIG);
            if (clientId.length() <= 0)
                clientId = "producer-" + PRODUCER_CLIENT_ID_SEQUENCE.getAndIncrement();
            this.clientId = clientId;

            //todo: kafka在0.11之后支持事务
            String transactionalId = userProvidedConfigs.containsKey(ProducerConfig.TRANSACTIONAL_ID_CONFIG) ?
                    (String) userProvidedConfigs.get(ProducerConfig.TRANSACTIONAL_ID_CONFIG) : null;
            LogContext logContext;
            if (transactionalId == null)
                logContext = new LogContext(String.format("[Producer clientId=%s] ", clientId));
            else
                logContext = new LogContext(String.format("[Producer clientId=%s, transactionalId=%s] ", clientId, transactionalId));
            log = logContext.logger(KafkaProducer.class);
            log.trace("Starting the Kafka producer");
            Map<String, String> metricTags = Collections.singletonMap("client-id", clientId);

            //todo: metric是一些监控信息，我们一般分析源码的时候 不需要去关心
            MetricConfig metricConfig = new MetricConfig().samples(config.getInt(ProducerConfig.METRICS_NUM_SAMPLES_CONFIG))
                    .timeWindow(config.getLong(ProducerConfig.METRICS_SAMPLE_WINDOW_MS_CONFIG), TimeUnit.MILLISECONDS)
                    .recordLevel(Sensor.RecordingLevel.forName(config.getString(ProducerConfig.METRICS_RECORDING_LEVEL_CONFIG)))
                    .tags(metricTags);
            List<MetricsReporter> reporters = config.getConfiguredInstances(ProducerConfig.METRIC_REPORTER_CLASSES_CONFIG,
                    MetricsReporter.class,
                    Collections.singletonMap(ProducerConfig.CLIENT_ID_CONFIG, clientId));
            reporters.add(new JmxReporter(JMX_PREFIX));
            this.metrics = new Metrics(metricConfig, reporters, time);

            //todo: 设置分区器，默认使用的DefaultPartitioner
            //partitioner作用：它是决定了一信息会最终发送到该topic的哪一个分区
            this.partitioner = config.getConfiguredInstance(ProducerConfig.PARTITIONER_CLASS_CONFIG, Partitioner.class);

            //todo: 重试时间间隔 retry.backoff.ms 默认100ms
            long retryBackoffMs = config.getLong(ProducerConfig.RETRY_BACKOFF_MS_CONFIG);

            //todo: 设置序列化器 也支持自定义序列化器
            if (keySerializer == null) {
                this.keySerializer = config.getConfiguredInstance(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                                                                                         Serializer.class);
                this.keySerializer.configure(config.originals(), true);
            } else {
                config.ignore(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG);
                this.keySerializer = keySerializer;
            }
            if (valueSerializer == null) {
                this.valueSerializer = config.getConfiguredInstance(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                                                                                           Serializer.class);
                this.valueSerializer.configure(config.originals(), false);
            } else {
                config.ignore(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);
                this.valueSerializer = valueSerializer;
            }

            // load interceptors and make sure they get clientId
            userProvidedConfigs.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
            ProducerConfig configWithClientId = new ProducerConfig(userProvidedConfigs, false);

            //todo: 设置拦截器   类似于一个过滤器
            List<ProducerInterceptor<K, V>> interceptorList = (List) configWithClientId.getConfiguredInstances(
                    ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, ProducerInterceptor.class);
            if (interceptors != null)
                this.interceptors = interceptors;
            else
                this.interceptors = new ProducerInterceptors<>(interceptorList);
            ClusterResourceListeners clusterResourceListeners = configureClusterResourceListeners(keySerializer,
                    valueSerializer, interceptorList, reporters);

            //todo: max.request.size 生产者往服务端发送消息的时候，规定一条消息最大多大？
            //如果你超过了这个规定消息的大小，你的消息就不能发送过去。
            //默认是1M，这个值偏小，在生产环境中，我们需要修改这个值。
            //经验值是10M。但是大家也可以根据自己公司的情况来。
            this.maxRequestSize = config.getInt(ProducerConfig.MAX_REQUEST_SIZE_CONFIG);

            //todo:  指的是缓存大小
            //默认值是32M，这个值一般是够用，如果有特殊情况的时候，我们可以去修改这个值。
            this.totalMemorySize = config.getLong(ProducerConfig.BUFFER_MEMORY_CONFIG);


            //todo: kafka是支持压缩数据的，这儿设置压缩格式。
            //提高你的系统的吞吐量，你可以设置压缩格式。
            //一次发送出去的消息就更多。生产者这儿会消耗更多的cpu.
            this.compressionType = CompressionType.forName(config.getString(ProducerConfig.COMPRESSION_TYPE_CONFIG));

            //todo:  当buffer满了或者metadata获取不到(比如leader挂了),
            // 或者序列化没完成分区函数没计算完等等情况下的最大阻塞时间,默认60000ms (60秒)
            this.maxBlockTimeMs = config.getLong(ProducerConfig.MAX_BLOCK_MS_CONFIG);
            this.transactionManager = configureTransactionState(config, logContext, log);
            //todo: 发送消息上报成功或失败的最大时间
            int deliveryTimeoutMs = configureDeliveryTimeout(config, log);

            //api versions
            this.apiVersions = new ApiVersions();

            //todo: 创建了一个核心的组件RecordAccumulator,缓存消息
            this.accumulator = new RecordAccumulator(logContext,
                    //每个批次的大小16KB
                    config.getInt(ProducerConfig.BATCH_SIZE_CONFIG),
                    //压缩
                    this.compressionType,
                    //
                    config.getInt(ProducerConfig.LINGER_MS_CONFIG),
                    retryBackoffMs,
                    deliveryTimeoutMs,
                    metrics,
                    PRODUCER_METRIC_GROUP_NAME,
                    time,
                    apiVersions,
                    transactionManager,
                    new BufferPool(this.totalMemorySize, config.getInt(ProducerConfig.BATCH_SIZE_CONFIG), metrics, time, PRODUCER_METRIC_GROUP_NAME));
            List<InetSocketAddress> addresses = ClientUtils.parseAndValidateAddresses(
                    config.getList(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG),
                    config.getString(ProducerConfig.CLIENT_DNS_LOOKUP_CONFIG));

            //todo: 判断kafka集群元数据信息是否存在
            if (metadata != null) {
                //MetaData包含了kafka集群元数据信息、主要有：kafka集群的节点有哪些、有哪些topic、每个topicY有哪些分区、该分区对应的ISR列表分布在哪些节点.
                //获取metadata其本质来说需要给kafka集群发送获取kafka集群的网络请求
                this.metadata = metadata;
            } else {
                //todo: 创建一个Metadata对象
                this.metadata = new Metadata(retryBackoffMs, config.getLong(ProducerConfig.METADATA_MAX_AGE_CONFIG),
                    true, true, clusterResourceListeners);
                //todo:  update去更新元数据。     node01:9092,node02:9092,node03:9092
                this.metadata.update(Cluster.bootstrap(addresses), Collections.emptySet(), time.milliseconds());
            }
            this.errors = this.metrics.sensor("errors");
            //todo: 初始化Sender线程
             //包含了业务代码
            this.sender = newSender(logContext, kafkaClient, this.metadata);
            String ioThreadName = NETWORK_THREAD_PREFIX + " | " + clientId;

            //todo: 创建了一个线程，然后里面传进去了一个sender对象。
            // 内部包含了线程代码
            //实现把业务代码与线程代码进行隔离，这样会显得清晰点
            this.ioThread = new KafkaThread(ioThreadName, this.sender, true);

            //todo: 启动线程
            this.ioThread.start();
            config.logUnused();
            AppInfoParser.registerAppInfo(JMX_PREFIX, clientId, metrics);
            log.debug("Kafka producer started");
        } catch (Throwable t) {
            // call close methods if internal objects are already constructed this is to prevent resource leak. see KAFKA-2121
            close(0, TimeUnit.MILLISECONDS, true);
            // now propagate the exception
            throw new KafkaException("Failed to construct kafka producer", t);
        }
    }

    // visible for testing
    Sender newSender(LogContext logContext, KafkaClient kafkaClient, Metadata metadata) {
        int maxInflightRequests = configureInflightRequests(producerConfig, transactionManager != null);
        int requestTimeoutMs = producerConfig.getInt(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG);
        ChannelBuilder channelBuilder = ClientUtils.createChannelBuilder(producerConfig);
        ProducerMetrics metricsRegistry = new ProducerMetrics(this.metrics);
        Sensor throttleTimeSensor = Sender.throttleTimeSensor(metricsRegistry.senderMetrics);
        //todo: 初始化了一个重要的网络管理的组件， KafkaClient 需要连接到server端
        /**
         * connections.max.idle.ms: 默认值是9分钟，一个网络连接最多空闲多久，超过这个空闲时间，就关闭这个网络连接。
         * 可以设置为-1 ，不回收连接，减少频繁的创建和销毁连接
         *
         *
         * todo: max.in.flight.requests.per.connection：默认是5
         *  发送数据的时候，其实是有多个网络连接。每个网络连接可以忍受 producer端发送给broker消息后，消息没有响应的个数。
         *  因为kafka有重试机制，所以有可能会造成数据乱序，如果想要保证有序，这个值要把设置为1.
         *
         * reconnect.backoff.ms：socket尝试重新连接指定主机的时间间隔
         * send.buffer.bytes：socket发送数据的缓冲区的大小，默认值是128K
         * receive.buffer.bytes：socket接受数据的缓冲区的大小，默认值是32K
         */
        KafkaClient client = kafkaClient != null ? kafkaClient : new NetworkClient(
                new Selector(producerConfig.getLong(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG),
                        this.metrics, time, "producer", channelBuilder, logContext),
                metadata,
                clientId,
                maxInflightRequests,
                producerConfig.getLong(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG),
                producerConfig.getLong(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG),
                producerConfig.getInt(ProducerConfig.SEND_BUFFER_CONFIG),
                producerConfig.getInt(ProducerConfig.RECEIVE_BUFFER_CONFIG),
                requestTimeoutMs,
                ClientDnsLookup.forConfig(producerConfig.getString(ProducerConfig.CLIENT_DNS_LOOKUP_CONFIG)),
                time,
                true,
                apiVersions,
                throttleTimeSensor,
                logContext);

        //获取重试的次数
        int retries = configureRetries(producerConfig, transactionManager != null, log);
        //获取acks参数
        short acks = configureAcks(producerConfig, transactionManager != null, log);

        /**
         *  retries: 重试的次数，默认为0，表示不重试
         *  acks:
         *     0:
         *      producer发送数据到broker后，就完了，没有返回值，不管写成功还是写失败都不管了。
         *       如果是这种请求数据丢失的概率是非常之高，一般不建议设置为0
         *
         *   1：
         *      producer发送数据到broker后，数据成功写入leader partition以后返回响应。
         *      数据 -》 broker（leader partition）
         *      也有丢失数据的风险
         *
         *  -1或者all：
         *       producer发送数据到broker后，数据要写入到leader partition里面，并且数据同步到所有的
         *       follower partition里面以后，才返回响应。
         */
        return new Sender(logContext,
                client,
                metadata,
                this.accumulator,
                maxInflightRequests == 1,
                producerConfig.getInt(ProducerConfig.MAX_REQUEST_SIZE_CONFIG),
                acks,
                retries,
                metricsRegistry.senderMetrics,
                time,
                requestTimeoutMs,
                producerConfig.getLong(ProducerConfig.RETRY_BACKOFF_MS_CONFIG),
                this.transactionManager,
                apiVersions);
    }

    private static int configureDeliveryTimeout(ProducerConfig config, Logger log) {

        int deliveryTimeoutMs = config.getInt(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG);
        int lingerMs = config.getInt(ProducerConfig.LINGER_MS_CONFIG);
        int requestTimeoutMs = config.getInt(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG);

        if (deliveryTimeoutMs < Integer.MAX_VALUE && deliveryTimeoutMs < lingerMs + requestTimeoutMs) {
            if (config.originals().containsKey(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG)) {
                // throw an exception if the user explicitly set an inconsistent value
                throw new ConfigException(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG
                    + " should be equal to or larger than " + ProducerConfig.LINGER_MS_CONFIG
                    + " + " + ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG);
            } else {
                // override deliveryTimeoutMs default value to lingerMs + requestTimeoutMs for backward compatibility
                deliveryTimeoutMs = lingerMs + requestTimeoutMs;
                log.warn("{} should be equal to or larger than {} + {}. Setting it to {}.",
                    ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, ProducerConfig.LINGER_MS_CONFIG,
                    ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
            }
        }
        return deliveryTimeoutMs;
    }

    private static TransactionManager configureTransactionState(ProducerConfig config, LogContext logContext, Logger log) {

        TransactionManager transactionManager = null;

        boolean userConfiguredIdempotence = false;
        if (config.originals().containsKey(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG))
            userConfiguredIdempotence = true;

        boolean userConfiguredTransactions = false;
        if (config.originals().containsKey(ProducerConfig.TRANSACTIONAL_ID_CONFIG))
            userConfiguredTransactions = true;

        boolean idempotenceEnabled = config.getBoolean(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG);

        if (!idempotenceEnabled && userConfiguredIdempotence && userConfiguredTransactions)
            throw new ConfigException("Cannot set a " + ProducerConfig.TRANSACTIONAL_ID_CONFIG + " without also enabling idempotence.");

        if (userConfiguredTransactions)
            idempotenceEnabled = true;

        if (idempotenceEnabled) {
            String transactionalId = config.getString(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
            int transactionTimeoutMs = config.getInt(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG);
            long retryBackoffMs = config.getLong(ProducerConfig.RETRY_BACKOFF_MS_CONFIG);
            transactionManager = new TransactionManager(logContext, transactionalId, transactionTimeoutMs, retryBackoffMs);
            if (transactionManager.isTransactional())
                log.info("Instantiated a transactional producer.");
            else
                log.info("Instantiated an idempotent producer.");
        }

        return transactionManager;
    }

    private static int configureRetries(ProducerConfig config, boolean idempotenceEnabled, Logger log) {
        boolean userConfiguredRetries = false;
        if (config.originals().containsKey(ProducerConfig.RETRIES_CONFIG)) {
            userConfiguredRetries = true;
        }
        if (idempotenceEnabled && !userConfiguredRetries) {
            // We recommend setting infinite retries when the idempotent producer is enabled, so it makes sense to make
            // this the default.
            log.info("Overriding the default retries config to the recommended value of {} since the idempotent " +
                    "producer is enabled.", Integer.MAX_VALUE);
            return Integer.MAX_VALUE;
        }
        if (idempotenceEnabled && config.getInt(ProducerConfig.RETRIES_CONFIG) == 0) {
            throw new ConfigException("Must set " + ProducerConfig.RETRIES_CONFIG + " to non-zero when using the idempotent producer.");
        }
        return config.getInt(ProducerConfig.RETRIES_CONFIG);
    }

    private static int configureInflightRequests(ProducerConfig config, boolean idempotenceEnabled) {
        if (idempotenceEnabled && 5 < config.getInt(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION)) {
            throw new ConfigException("Must set " + ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION + " to at most 5" +
                    " to use the idempotent producer.");
        }
        return config.getInt(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION);
    }

    private static short configureAcks(ProducerConfig config, boolean idempotenceEnabled, Logger log) {
        boolean userConfiguredAcks = false;
        short acks = (short) parseAcks(config.getString(ProducerConfig.ACKS_CONFIG));
        if (config.originals().containsKey(ProducerConfig.ACKS_CONFIG)) {
            userConfiguredAcks = true;
        }

        if (idempotenceEnabled && !userConfiguredAcks) {
            log.info("Overriding the default {} to all since idempotence is enabled.", ProducerConfig.ACKS_CONFIG);
            return -1;
        }

        if (idempotenceEnabled && acks != -1) {
            throw new ConfigException("Must set " + ProducerConfig.ACKS_CONFIG + " to all in order to use the idempotent " +
                    "producer. Otherwise we cannot guarantee idempotence.");
        }
        return acks;
    }

    private static int parseAcks(String acksString) {
        try {
            return acksString.trim().equalsIgnoreCase("all") ? -1 : Integer.parseInt(acksString.trim());
        } catch (NumberFormatException e) {
            throw new ConfigException("Invalid configuration value for 'acks': " + acksString);
        }
    }

    /**
     * Needs to be called before any other methods when the transactional.id is set in the configuration.
     *
     * This method does the following:
     *   1. Ensures any transactions initiated by previous instances of the producer with the same
     *      transactional.id are completed. If the previous instance had failed with a transaction in
     *      progress, it will be aborted. If the last transaction had begun completion,
     *      but not yet finished, this method awaits its completion.
     *   2. Gets the internal producer id and epoch, used in all future transactional
     *      messages issued by the producer.
     *
     * Note that this method will raise {@link TimeoutException} if the transactional state cannot
     * be initialized before expiration of {@code max.block.ms}. Additionally, it will raise {@link InterruptException}
     * if interrupted. It is safe to retry in either case, but once the transactional state has been successfully
     * initialized, this method should no longer be used.
     *
     * @throws IllegalStateException if no transactional.id has been configured
     * @throws org.apache.kafka.common.errors.UnsupportedVersionException fatal error indicating the broker
     *         does not support transactions (i.e. if its version is lower than 0.11.0.0)
     * @throws org.apache.kafka.common.errors.AuthorizationException fatal error indicating that the configured
     *         transactional.id is not authorized. See the exception for more details
     * @throws KafkaException if the producer has encountered a previous fatal error or for any other unexpected error
     * @throws TimeoutException if the time taken for initialize the transaction has surpassed <code>max.block.ms</code>.
     * @throws InterruptException if the thread is interrupted while blocked
     */
    public void initTransactions() {
        throwIfNoTransactionManager();
        if (initTransactionsResult == null) {
            initTransactionsResult = transactionManager.initializeTransactions();
            sender.wakeup();
        }

        try {
            if (initTransactionsResult.await(maxBlockTimeMs, TimeUnit.MILLISECONDS)) {
                initTransactionsResult = null;
            } else {
                throw new TimeoutException("Timeout expired while initializing transactional state in " + maxBlockTimeMs + "ms.");
            }
        } catch (InterruptedException e) {
            throw new InterruptException("Initialize transactions interrupted.", e);
        }
    }

    /**
     * Should be called before the start of each new transaction. Note that prior to the first invocation
     * of this method, you must invoke {@link #initTransactions()} exactly one time.
     *
     * @throws IllegalStateException if no transactional.id has been configured or if {@link #initTransactions()}
     *         has not yet been invoked
     * @throws ProducerFencedException if another producer with the same transactional.id is active
     * @throws org.apache.kafka.common.errors.UnsupportedVersionException fatal error indicating the broker
     *         does not support transactions (i.e. if its version is lower than 0.11.0.0)
     * @throws org.apache.kafka.common.errors.AuthorizationException fatal error indicating that the configured
     *         transactional.id is not authorized. See the exception for more details
     * @throws KafkaException if the producer has encountered a previous fatal error or for any other unexpected error
     */
    public void beginTransaction() throws ProducerFencedException {
        throwIfNoTransactionManager();
        transactionManager.beginTransaction();
    }

    /**
     * Sends a list of specified offsets to the consumer group coordinator, and also marks
     * those offsets as part of the current transaction. These offsets will be considered
     * committed only if the transaction is committed successfully. The committed offset should
     * be the next message your application will consume, i.e. lastProcessedMessageOffset + 1.
     * <p>
     * This method should be used when you need to batch consumed and produced messages
     * together, typically in a consume-transform-produce pattern. Thus, the specified
     * {@code consumerGroupId} should be the same as config parameter {@code group.id} of the used
     * {@link KafkaConsumer consumer}. Note, that the consumer should have {@code enable.auto.commit=false}
     * and should also not commit offsets manually (via {@link KafkaConsumer#commitSync(Map) sync} or
     * {@link KafkaConsumer#commitAsync(Map, OffsetCommitCallback) async} commits).
     *
     * @throws IllegalStateException if no transactional.id has been configured or no transaction has been started
     * @throws ProducerFencedException fatal error indicating another producer with the same transactional.id is active
     * @throws org.apache.kafka.common.errors.UnsupportedVersionException fatal error indicating the broker
     *         does not support transactions (i.e. if its version is lower than 0.11.0.0)
     * @throws org.apache.kafka.common.errors.UnsupportedForMessageFormatException  fatal error indicating the message
     *         format used for the offsets topic on the broker does not support transactions
     * @throws org.apache.kafka.common.errors.AuthorizationException fatal error indicating that the configured
     *         transactional.id is not authorized. See the exception for more details
     * @throws KafkaException if the producer has encountered a previous fatal or abortable error, or for any
     *         other unexpected error
     */
    public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets,
                                         String consumerGroupId) throws ProducerFencedException {
        throwIfNoTransactionManager();
        TransactionalRequestResult result = transactionManager.sendOffsetsToTransaction(offsets, consumerGroupId);
        sender.wakeup();
        result.await();
    }

    /**
     * Commits the ongoing transaction. This method will flush any unsent records before actually committing the transaction.
     *
     * Further, if any of the {@link #send(ProducerRecord)} calls which were part of the transaction hit irrecoverable
     * errors, this method will throw the last received exception immediately and the transaction will not be committed.
     * So all {@link #send(ProducerRecord)} calls in a transaction must succeed in order for this method to succeed.
     *
     * @throws IllegalStateException if no transactional.id has been configured or no transaction has been started
     * @throws ProducerFencedException fatal error indicating another producer with the same transactional.id is active
     * @throws org.apache.kafka.common.errors.UnsupportedVersionException fatal error indicating the broker
     *         does not support transactions (i.e. if its version is lower than 0.11.0.0)
     * @throws org.apache.kafka.common.errors.AuthorizationException fatal error indicating that the configured
     *         transactional.id is not authorized. See the exception for more details
     * @throws KafkaException if the producer has encountered a previous fatal or abortable error, or for any
     *         other unexpected error
     */
    public void commitTransaction() throws ProducerFencedException {
        throwIfNoTransactionManager();
        TransactionalRequestResult result = transactionManager.beginCommit();
        sender.wakeup();
        result.await();
    }

    /**
     * Aborts the ongoing transaction. Any unflushed produce messages will be aborted when this call is made.
     * This call will throw an exception immediately if any prior {@link #send(ProducerRecord)} calls failed with a
     * {@link ProducerFencedException} or an instance of {@link org.apache.kafka.common.errors.AuthorizationException}.
     *
     * @throws IllegalStateException if no transactional.id has been configured or no transaction has been started
     * @throws ProducerFencedException fatal error indicating another producer with the same transactional.id is active
     * @throws org.apache.kafka.common.errors.UnsupportedVersionException fatal error indicating the broker
     *         does not support transactions (i.e. if its version is lower than 0.11.0.0)
     * @throws org.apache.kafka.common.errors.AuthorizationException fatal error indicating that the configured
     *         transactional.id is not authorized. See the exception for more details
     * @throws KafkaException if the producer has encountered a previous fatal error or for any other unexpected error
     */
    public void abortTransaction() throws ProducerFencedException {
        throwIfNoTransactionManager();
        TransactionalRequestResult result = transactionManager.beginAbort();
        sender.wakeup();
        result.await();
    }

    /**
     * Asynchronously send a record to a topic. Equivalent to <code>send(record, null)</code>.
     * See {@link #send(ProducerRecord, Callback)} for details.
     */
    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> record) {
        return send(record, null);
    }

    /**
     * Asynchronously send a record to a topic and invoke the provided callback when the send has been acknowledged.
     * <p>
     * The send is asynchronous and this method will return immediately once the record has been stored in the buffer of
     * records waiting to be sent. This allows sending many records in parallel without blocking to wait for the
     * response after each one.
     * <p>
     * The result of the send is a {@link RecordMetadata} specifying the partition the record was sent to, the offset
     * it was assigned and the timestamp of the record. If
     * {@link org.apache.kafka.common.record.TimestampType#CREATE_TIME CreateTime} is used by the topic, the timestamp
     * will be the user provided timestamp or the record send time if the user did not specify a timestamp for the
     * record. If {@link org.apache.kafka.common.record.TimestampType#LOG_APPEND_TIME LogAppendTime} is used for the
     * topic, the timestamp will be the Kafka broker local time when the message is appended.
     * <p>
     * Since the send call is asynchronous it returns a {@link java.util.concurrent.Future Future} for the
     * {@link RecordMetadata} that will be assigned to this record. Invoking {@link java.util.concurrent.Future#get()
     * get()} on this future will block until the associated request completes and then return the metadata for the record
     * or throw any exception that occurred while sending the record.
     * <p>
     * If you want to simulate a simple blocking call you can call the <code>get()</code> method immediately:
     *
     * <pre>
     * {@code
     * byte[] key = "key".getBytes();
     * byte[] value = "value".getBytes();
     * ProducerRecord<byte[],byte[]> record = new ProducerRecord<byte[],byte[]>("my-topic", key, value)
     * producer.send(record).get();
     * }</pre>
     * <p>
     * Fully non-blocking usage can make use of the {@link Callback} parameter to provide a callback that
     * will be invoked when the request is complete.
     *
     * <pre>
     * {@code
     * ProducerRecord<byte[],byte[]> record = new ProducerRecord<byte[],byte[]>("the-topic", key, value);
     * producer.send(myRecord,
     *               new Callback() {
     *                   public void onCompletion(RecordMetadata metadata, Exception e) {
     *                       if(e != null) {
     *                          e.printStackTrace();
     *                       } else {
     *                          System.out.println("The offset of the record we just sent is: " + metadata.offset());
     *                       }
     *                   }
     *               });
     * }
     * </pre>
     *
     * Callbacks for records being sent to the same partition are guaranteed to execute in order. That is, in the
     * following example <code>callback1</code> is guaranteed to execute before <code>callback2</code>:
     *
     * <pre>
     * {@code
     * producer.send(new ProducerRecord<byte[],byte[]>(topic, partition, key1, value1), callback1);
     * producer.send(new ProducerRecord<byte[],byte[]>(topic, partition, key2, value2), callback2);
     * }
     * </pre>
     * <p>
     * When used as part of a transaction, it is not necessary to define a callback or check the result of the future
     * in order to detect errors from <code>send</code>. If any of the send calls failed with an irrecoverable error,
     * the final {@link #commitTransaction()} call will fail and throw the exception from the last failed send. When
     * this happens, your application should call {@link #abortTransaction()} to reset the state and continue to send
     * data.
     * </p>
     * <p>
     * Some transactional send errors cannot be resolved with a call to {@link #abortTransaction()}.  In particular,
     * if a transactional send finishes with a {@link ProducerFencedException}, a {@link org.apache.kafka.common.errors.OutOfOrderSequenceException},
     * a {@link org.apache.kafka.common.errors.UnsupportedVersionException}, or an
     * {@link org.apache.kafka.common.errors.AuthorizationException}, then the only option left is to call {@link #close()}.
     * Fatal errors cause the producer to enter a defunct state in which future API calls will continue to raise
     * the same underyling error wrapped in a new {@link KafkaException}.
     * </p>
     * <p>
     * It is a similar picture when idempotence is enabled, but no <code>transactional.id</code> has been configured.
     * In this case, {@link org.apache.kafka.common.errors.UnsupportedVersionException} and
     * {@link org.apache.kafka.common.errors.AuthorizationException} are considered fatal errors. However,
     * {@link ProducerFencedException} does not need to be handled. Additionally, it is possible to continue
     * sending after receiving an {@link org.apache.kafka.common.errors.OutOfOrderSequenceException}, but doing so
     * can result in out of order delivery of pending messages. To ensure proper ordering, you should close the
     * producer and create a new instance.
     * </p>
     * <p>
     * If the message format of the destination topic is not upgraded to 0.11.0.0, idempotent and transactional
     * produce requests will fail with an {@link org.apache.kafka.common.errors.UnsupportedForMessageFormatException}
     * error. If this is encountered during a transaction, it is possible to abort and continue. But note that future
     * sends to the same topic will continue receiving the same exception until the topic is upgraded.
     * </p>
     * <p>
     * Note that callbacks will generally execute in the I/O thread of the producer and so should be reasonably fast or
     * they will delay the sending of messages from other threads. If you want to execute blocking or computationally
     * expensive callbacks it is recommended to use your own {@link java.util.concurrent.Executor} in the callback body
     * to parallelize processing.
     *
     * @param record The record to send
     * @param callback A user-supplied callback to execute when the record has been acknowledged by the server (null
     *        indicates no callback)
     *
     * @throws AuthenticationException if authentication fails. See the exception for more details
     * @throws AuthorizationException fatal error indicating that the producer is not allowed to write
     * @throws IllegalStateException if a transactional.id has been configured and no transaction has been started, or
     *                               when send is invoked after producer has been closed.
     * @throws InterruptException If the thread is interrupted while blocked
     * @throws SerializationException If the key or value are not valid objects given the configured serializers
     * @throws TimeoutException If the time taken for fetching metadata or allocating memory for the record has surpassed <code>max.block.ms</code>.
     * @throws KafkaException If a Kafka related error occurs that does not belong to the public API exceptions.
     */
    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback) {
        // intercept the record, which can be potentially modified; this method does not throw exceptions
        ProducerRecord<K, V> interceptedRecord = this.interceptors.onSend(record);
        //todo：关键代码
        return doSend(interceptedRecord, callback);
    }

    // Verify that this producer instance has not been closed. This method throws IllegalStateException if the producer
    // has already been closed.
    private void throwIfProducerClosed() {
        if (ioThread == null || !ioThread.isAlive())
            throw new IllegalStateException("Cannot perform operation after producer has been closed");
    }

    /**
     * Implementation of asynchronously send a record to a topic.
     */
    private Future<RecordMetadata> doSend(ProducerRecord<K, V> record, Callback callback) {
        TopicPartition tp = null;
        try {
            throwIfProducerClosed();
            // first make sure the metadata for the topic is available
            ClusterAndWaitTime clusterAndWaitTime;
            try {
                /**
                 *  todo: 第一步：同步等待拉取元数据
                 *  maxBlockTimeMs 最多能等待多久  默认是1分钟
                 */
                clusterAndWaitTime = waitOnMetadata(record.topic(), record.partition(), maxBlockTimeMs);
            } catch (KafkaException e) {
                if (metadata.isClosed())
                    throw new KafkaException("Producer closed while send in progress", e);
                throw e;
            }
            //clusterAndWaitTime.waitedOnMetadataMs 代表的是拉取元数据用了多少时间。
            //maxBlockTimeMs - 用了多少时间 = 还剩余多少时间可以使用
            long remainingWaitMs = Math.max(0, maxBlockTimeMs - clusterAndWaitTime.waitedOnMetadataMs);
            Cluster cluster = clusterAndWaitTime.cluster;

            /**
             * todo:  第二步：对消息的key和value进行序列化
             */
            byte[] serializedKey;
            try {
                serializedKey = keySerializer.serialize(record.topic(), record.headers(), record.key());
            } catch (ClassCastException cce) {
                throw new SerializationException("Can't convert key of class " + record.key().getClass().getName() +
                        " to class " + producerConfig.getClass(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG).getName() +
                        " specified in key.serializer", cce);
            }
            byte[] serializedValue;
            try {
                serializedValue = valueSerializer.serialize(record.topic(), record.headers(), record.value());
            } catch (ClassCastException cce) {
                throw new SerializationException("Can't convert value of class " + record.value().getClass().getName() +
                        " to class " + producerConfig.getClass(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG).getName() +
                        " specified in value.serializer", cce);
            }
            /**
             * todo:  第三步：根据分区器选择消息应该发送的分区
             * 根据元数据信息可以计算出消息应该发送到topic的哪一个分区中
             */
            int partition = partition(record, serializedKey, serializedValue, cluster);

            /**
             *  todo: 第四步：根据元数据来封装分区对象
             */
            tp = new TopicPartition(record.topic(), partition);

            setReadOnly(record.headers());
            Header[] headers = record.headers().toArray();

            /**
             *  todo : 第五步：判断要发送的消息有没有超过消息大小的最大值
             *  KafkaProdcuer初始化的时候，指定了一个参数，代表的是Producer这儿最大能发送的是一条消息能有多大
             *  默认最大是1M，我们一般都去修改它。
             */
            int serializedSize = AbstractRecords.estimateSizeInBytesUpperBound(apiVersions.maxUsableProduceMagic(),
                    compressionType, serializedKey, serializedValue, headers); //经过序列化之后，消息的大小
            //计算消息的大小，后面要进行大小的判断
            ensureValidRecordSize(serializedSize);  // 确定有效消息记录的大小
            long timestamp = record.timestamp() == null ? time.milliseconds() : record.timestamp();
            log.trace("Sending record {} with callback {} to topic {} partition {}", record, callback, record.topic(), partition);

            // producer callback will make sure to call both 'callback' and interceptor callback
            /**
             *  todo: 第六步：给每一条消息都绑定他的回调函数。因为我们使用的是异步的方式发送的消息。
             */
            Callback interceptCallback = new InterceptorCallback<>(callback, this.interceptors, tp);

            if (transactionManager != null && transactionManager.isTransactional())
                transactionManager.maybeAddPartitionToTransaction(tp);

            /**
             *  todo: 第七步：把消息放入Accumulator（32M的一个内存）
             * 然后由accumulator把消息封装成为一个批次一个批次的去发送。
             */
            RecordAccumulator.RecordAppendResult result = accumulator.append(tp, timestamp, serializedKey,
                    serializedValue, headers, interceptCallback, remainingWaitMs);

            // 如果批次满了，或者是新创建的一个批次
            if (result.batchIsFull || result.newBatchCreated) {
                log.trace("Waking up the sender since topic {} partition {} is either full or getting a new batch", record.topic(), partition);

             /**
              * todo: 第八步：唤醒sender线程，它是真正发送数据的线程
              */
                this.sender.wakeup();
            }
            return result.future;
            // handling exceptions and record the errors;
            // for API exceptions return them in the future,
            // for other exceptions throw directly
        } catch (ApiException e) {
            log.debug("Exception occurred during message send:", e);
            if (callback != null)
                callback.onCompletion(null, e);
            this.errors.record();
            this.interceptors.onSendError(record, tp, e);
            return new FutureFailure(e);
        } catch (InterruptedException e) {
            this.errors.record();
            this.interceptors.onSendError(record, tp, e);
            throw new InterruptException(e);
        } catch (BufferExhaustedException e) {
            this.errors.record();
            this.metrics.sensor("buffer-exhausted-records").record();
            this.interceptors.onSendError(record, tp, e);
            throw e;
        } catch (KafkaException e) {
            this.errors.record();
            this.interceptors.onSendError(record, tp, e);
            throw e;
        } catch (Exception e) {
            // we notify interceptor about all exceptions, since onSend is called before anything else in this method
            this.interceptors.onSendError(record, tp, e);
            throw e;
        }
    }

    private void setReadOnly(Headers headers) {
        if (headers instanceof RecordHeaders) {
            ((RecordHeaders) headers).setReadOnly();
        }
    }

    /**
     * Wait for cluster metadata including partitions for the given topic to be available.
     * @param topic The topic we want metadata for
     * @param partition A specific partition expected to exist in metadata, or null if there's no preference
     * @param maxWaitMs The maximum time in ms for waiting on the metadata
     * @return The cluster containing topic metadata and the amount of time we waited in ms
     * @throws KafkaException for all Kafka-related exceptions, including the case where this method is called after producer close
     */
    //todo: 连接上kafka集群，获取topic对应的元数据信息  (topic名称、分区编号，拉取元数据最大的超时时间）
    private ClusterAndWaitTime waitOnMetadata(String topic, Integer partition, long maxWaitMs) throws InterruptedException {
        // add topic to metadata topic list if it is not there already and reset expiry
        //todo: 我们使用的是场景驱动的方式，然后我们目前代码执行到的producer端初始化完成。
        //我们知道这个cluster里面其实没有元数据，只是有我们写代码的时候设置address
        Cluster cluster = metadata.fetch();

        //todo: 该topic未授权
        if (cluster.invalidTopics().contains(topic))
            throw new InvalidTopicException(topic);

        //todo: 把当前topic添加到元数据对象中
        metadata.add(topic);

        //todo: 根据当前的topic从这个集群的cluster元数据信息里面查看分区的信息。
        //因为我们目前是第一次执行这段代码，所以这儿肯定是没有对应的分区的信息的。
        Integer partitionsCount = cluster.partitionCountForTopic(topic);
        // Return cached metadata if we have it, and if the record's partition is either undefined
        // or within the known partition range
        //todo: 如果在元数据里面获取到了分区的信息
        //我们用场景驱动的方式，我们知道如果是第一次代码进来这儿，代码是不会运行这儿。
        if (partitionsCount != null && (partition == null || partition < partitionsCount))
            //todo: 直接返回cluster元数据信息，拉取元数据花的时间。
            return new ClusterAndWaitTime(cluster, 0);

        //todo:如果代码执行到这儿，说明真的需要去服务端拉取元数据。
        //记录当前时间
        long begin = time.milliseconds();
        //剩余多少时间，默认值给的是 最多可以等待的时间。
        long remainingWaitMs = maxWaitMs;
        //已经花了多少时间。
        long elapsed;
        // Issue metadata requests until we have metadata for the topic or maxWaitTimeMs is exceeded.
        // In case we already have cached metadata for the topic, but the requested partition is greater
        // than expected, issue an update request only once. This is necessary in case the metadata
        // is stale and the number of partitions for this topic has increased in the meantime.

        //todo: do while循环就相当于，不断在尝试等待获取元数据，如果有了集群的元数据之后，partitionsCount不为null，就表示有了集群元数据，
        // 然后退出循环
        do {
            log.trace("Requesting metadata update for topic {}.", topic);
            metadata.add(topic);
            //todo: 1)获取当前元数据的版本
            //在Producer管理元数据时候，对于他来说元数据是有版本号的。
            //每次成功更新元数据，都会递增这个版本号。
            //todo: 2)把needUpdate 标识赋值为true
            int version = metadata.requestUpdate();
            //todo: 唤醒sender线程，开始执行拉取元数据操作,拉取元数据的操作是由Sender线程完成的
            //这里会涉及到java线程知识，并发知识，大家一定要掌握，没有掌握好的同学，大家需要补一补
            sender.wakeup();  //唤醒之后，肯定是需要进行元数据的拉取
            try {
                //todo: 同步的等待sender线程拉取元数据
                metadata.awaitUpdate(version, remainingWaitMs);
            } catch (TimeoutException ex) {
                // Rethrow with original maxWaitMs to prevent logging exception with remainingWaitMs
                throw new TimeoutException("Failed to update metadata after " + maxWaitMs + " ms.");
            }
            //todo： 获取一下集群的元数据信息。（上面可能成功更新了集群元数据信息了）
            cluster = metadata.fetch();
            //todo: 计算一下 拉取元数据已经花了多少时间
            elapsed = time.milliseconds() - begin;
            //todo: 如果花的时间大于 最大等待的时间，那么就报超时。
            if (elapsed >= maxWaitMs)
                throw new TimeoutException("Failed to update metadata after " + maxWaitMs + " ms.");
            //todo: 如果已经获取到了元数据，但是发现topic没有授权
            if (cluster.unauthorizedTopics().contains(topic))
                throw new TopicAuthorizationException(topic);
            if (cluster.invalidTopics().contains(topic))
                throw new InvalidTopicException(topic);
            //todo: 计算出来 还可以用的时间。
            remainingWaitMs = maxWaitMs - elapsed;
            //todo:获取该topic的分区数，
            // 如果这个值不为null，说明前面sender线程已经获取到元数据了。
            partitionsCount = cluster.partitionCountForTopic(topic);
        } while (partitionsCount == null);

        //todo: 获取到元数据，发现指定的分区编号比总的分区数要大，报分区号范围越界异常
        if (partition != null && partition >= partitionsCount) {  // 如果分区个数是3， 则分区编号是0，1，2
            throw new KafkaException(
                    String.format("Invalid partition given with record: %d is not in the range [0...%d).", partition, partitionsCount));
        }

        //todo:  返回集群的元数据信息和获取元数据信息需要的时间
        //2个参数
        //第一个参数表示集群的元数据信息
        //第二个参数表示拉取元数据需要的时间
        return new ClusterAndWaitTime(cluster, elapsed);
    }

    /**
     * Validate that the record size isn't too large  确定有效消息记录的大小
     */
    private void ensureValidRecordSize(int size) {
         //maxRequestSize=1M
        if (size > this.maxRequestSize)  // 校验消息大小
            throw new RecordTooLargeException("The message is " + size +
                    " bytes when serialized which is larger than the maximum request size you have configured with the " +
                    ProducerConfig.MAX_REQUEST_SIZE_CONFIG +
                    " configuration.");
        //totalMemorySize=32M
        if (size > this.totalMemorySize)  // 校验内存大小，和 buffer.memory 进行比较
            throw new RecordTooLargeException("The message is " + size +
                    " bytes when serialized which is larger than the total memory buffer you have configured with the " +
                    ProducerConfig.BUFFER_MEMORY_CONFIG +
                    " configuration.");
    }

    /**
     * Invoking this method makes all buffered records immediately available to send (even if <code>linger.ms</code> is
     * greater than 0) and blocks on the completion of the requests associated with these records. The post-condition
     * of <code>flush()</code> is that any previously sent record will have completed (e.g. <code>Future.isDone() == true</code>).
     * A request is considered completed when it is successfully acknowledged
     * according to the <code>acks</code> configuration you have specified or else it results in an error.
     * <p>
     * Other threads can continue sending records while one thread is blocked waiting for a flush call to complete,
     * however no guarantee is made about the completion of records sent after the flush call begins.
     * <p>
     * This method can be useful when consuming from some input system and producing into Kafka. The <code>flush()</code> call
     * gives a convenient way to ensure all previously sent messages have actually completed.
     * <p>
     * This example shows how to consume from one Kafka topic and produce to another Kafka topic:
     * <pre>
     * {@code
     * for(ConsumerRecord<String, String> record: consumer.poll(100))
     *     producer.send(new ProducerRecord("my-topic", record.key(), record.value());
     * producer.flush();
     * consumer.commit();
     * }
     * </pre>
     *
     * Note that the above example may drop records if the produce request fails. If we want to ensure that this does not occur
     * we need to set <code>retries=&lt;large_number&gt;</code> in our config.
     * </p>
     * <p>
     * Applications don't need to call this method for transactional producers, since the {@link #commitTransaction()} will
     * flush all buffered records before performing the commit. This ensures that all the {@link #send(ProducerRecord)}
     * calls made since the previous {@link #beginTransaction()} are completed before the commit.
     * </p>
     *
     * @throws InterruptException If the thread is interrupted while blocked
     */
    @Override
    public void flush() {
        log.trace("Flushing accumulated records in producer.");
        this.accumulator.beginFlush();
        this.sender.wakeup();
        try {
            this.accumulator.awaitFlushCompletion();
        } catch (InterruptedException e) {
            throw new InterruptException("Flush interrupted.", e);
        }
    }

    /**
     * Get the partition metadata for the given topic. This can be used for custom partitioning.
     * @throws AuthenticationException if authentication fails. See the exception for more details
     * @throws AuthorizationException if not authorized to the specified topic. See the exception for more details
     * @throws InterruptException if the thread is interrupted while blocked
     * @throws TimeoutException if metadata could not be refreshed within {@code max.block.ms}
     * @throws KafkaException for all Kafka-related exceptions, including the case where this method is called after producer close
     */
    @Override
    public List<PartitionInfo> partitionsFor(String topic) {
        Objects.requireNonNull(topic, "topic cannot be null");
        try {
            return waitOnMetadata(topic, null, maxBlockTimeMs).cluster.partitionsForTopic(topic);
        } catch (InterruptedException e) {
            throw new InterruptException(e);
        }
    }

    /**
     * Get the full set of internal metrics maintained by the producer.
     */
    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        return Collections.unmodifiableMap(this.metrics.metrics());
    }

    /**
     * Close this producer. This method blocks until all previously sent requests complete.
     * This method is equivalent to <code>close(Long.MAX_VALUE, TimeUnit.MILLISECONDS)</code>.
     * <p>
     * <strong>If close() is called from {@link Callback}, a warning message will be logged and close(0, TimeUnit.MILLISECONDS)
     * will be called instead. We do this because the sender thread would otherwise try to join itself and
     * block forever.</strong>
     * <p>
     *
     * @throws InterruptException If the thread is interrupted while blocked
     */
    @Override
    public void close() {
        close(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    /**
     * This method waits up to <code>timeout</code> for the producer to complete the sending of all incomplete requests.
     * <p>
     * If the producer is unable to complete all requests before the timeout expires, this method will fail
     * any unsent and unacknowledged records immediately.
     * <p>
     * If invoked from within a {@link Callback} this method will not block and will be equivalent to
     * <code>close(0, TimeUnit.MILLISECONDS)</code>. This is done since no further sending will happen while
     * blocking the I/O thread of the producer.
     *
     * @param timeout The maximum time to wait for producer to complete any pending requests. The value should be
     *                non-negative. Specifying a timeout of zero means do not wait for pending send requests to complete.
     * @param timeUnit The time unit for the <code>timeout</code>
     * @throws InterruptException If the thread is interrupted while blocked
     * @throws IllegalArgumentException If the <code>timeout</code> is negative.
     */
    @Override
    public void close(long timeout, TimeUnit timeUnit) {
        close(timeout, timeUnit, false);
    }

    private void close(long timeout, TimeUnit timeUnit, boolean swallowException) {
        if (timeout < 0)
            throw new IllegalArgumentException("The timeout cannot be negative.");

        long timeoutMs = timeUnit.toMillis(timeout);
        log.info("Closing the Kafka producer with timeoutMillis = {} ms.", timeoutMs);

        // this will keep track of the first encountered exception
        AtomicReference<Throwable> firstException = new AtomicReference<>();
        boolean invokedFromCallback = Thread.currentThread() == this.ioThread;
        if (timeoutMs > 0) {
            if (invokedFromCallback) {
                log.warn("Overriding close timeout {} ms to 0 ms in order to prevent useless blocking due to self-join. " +
                        "This means you have incorrectly invoked close with a non-zero timeout from the producer call-back.",
                        timeoutMs);
            } else {
                // Try to close gracefully.
                if (this.sender != null)
                    this.sender.initiateClose();
                if (this.ioThread != null) {
                    try {
                        this.ioThread.join(timeoutMs);
                    } catch (InterruptedException t) {
                        firstException.compareAndSet(null, new InterruptException(t));
                        log.error("Interrupted while joining ioThread", t);
                    }
                }
            }
        }

        if (this.sender != null && this.ioThread != null && this.ioThread.isAlive()) {
            log.info("Proceeding to force close the producer since pending requests could not be completed " +
                    "within timeout {} ms.", timeoutMs);
            this.sender.forceClose();
            // Only join the sender thread when not calling from callback.
            if (!invokedFromCallback) {
                try {
                    this.ioThread.join();
                } catch (InterruptedException e) {
                    firstException.compareAndSet(null, new InterruptException(e));
                }
            }
        }

        ClientUtils.closeQuietly(interceptors, "producer interceptors", firstException);
        ClientUtils.closeQuietly(metrics, "producer metrics", firstException);
        ClientUtils.closeQuietly(keySerializer, "producer keySerializer", firstException);
        ClientUtils.closeQuietly(valueSerializer, "producer valueSerializer", firstException);
        ClientUtils.closeQuietly(partitioner, "producer partitioner", firstException);
        AppInfoParser.unregisterAppInfo(JMX_PREFIX, clientId, metrics);
        log.debug("Kafka producer has been closed");
        Throwable exception = firstException.get();
        if (exception != null && !swallowException) {
            if (exception instanceof InterruptException) {
                throw (InterruptException) exception;
            }
            throw new KafkaException("Failed to close kafka producer", exception);
        }
    }

    private ClusterResourceListeners configureClusterResourceListeners(Serializer<K> keySerializer, Serializer<V> valueSerializer, List<?>... candidateLists) {
        ClusterResourceListeners clusterResourceListeners = new ClusterResourceListeners();
        for (List<?> candidateList: candidateLists)
            clusterResourceListeners.maybeAddAll(candidateList);

        clusterResourceListeners.maybeAdd(keySerializer);
        clusterResourceListeners.maybeAdd(valueSerializer);
        return clusterResourceListeners;
    }

    /**
     * computes partition for given record.
     * if the record has partition returns the value otherwise
     * calls configured partitioner class to compute the partition.
     */
    private int partition(ProducerRecord<K, V> record, byte[] serializedKey, byte[] serializedValue, Cluster cluster) {
        //todo:如果发送消息的过程中，指定了分区编号，直接使用该分区编号就可以了
        //但是正常情况下，消息是没有指定分区编号的
        Integer partition = record.partition();
        return partition != null ?
                partition :
                //todo: 使用分区器进行分区
                partitioner.partition(
                        record.topic(), record.key(), serializedKey, record.value(), serializedValue, cluster);
    }

    private void throwIfNoTransactionManager() {
        if (transactionManager == null)
            throw new IllegalStateException("Cannot use transactional methods without enabling transactions " +
                    "by setting the " + ProducerConfig.TRANSACTIONAL_ID_CONFIG + " configuration property");
    }

    // Visible for testing
    String getClientId() {
        return clientId;
    }

    private static class ClusterAndWaitTime {
        final Cluster cluster;
        final long waitedOnMetadataMs;
        ClusterAndWaitTime(Cluster cluster, long waitedOnMetadataMs) {
            this.cluster = cluster;
            this.waitedOnMetadataMs = waitedOnMetadataMs;
        }
    }

    private static class FutureFailure implements Future<RecordMetadata> {

        private final ExecutionException exception;

        public FutureFailure(Exception exception) {
            this.exception = new ExecutionException(exception);
        }

        @Override
        public boolean cancel(boolean interrupt) {
            return false;
        }

        @Override
        public RecordMetadata get() throws ExecutionException {
            throw this.exception;
        }

        @Override
        public RecordMetadata get(long timeout, TimeUnit unit) throws ExecutionException {
            throw this.exception;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

    }

    /**
     * A callback called when producer request is complete. It in turn calls user-supplied callback (if given) and
     * notifies producer interceptors about the request completion.
     */
    private static class InterceptorCallback<K, V> implements Callback {
        private final Callback userCallback;
        private final ProducerInterceptors<K, V> interceptors;
        private final TopicPartition tp;

        private InterceptorCallback(Callback userCallback, ProducerInterceptors<K, V> interceptors, TopicPartition tp) {
            this.userCallback = userCallback;
            this.interceptors = interceptors;
            this.tp = tp;
        }

        public void onCompletion(RecordMetadata metadata, Exception exception) {
            metadata = metadata != null ? metadata : new RecordMetadata(tp, -1, -1, RecordBatch.NO_TIMESTAMP, Long.valueOf(-1L), -1, -1);
            this.interceptors.onAcknowledgement(metadata, exception);
            if (this.userCallback != null)
                this.userCallback.onCompletion(metadata, exception);
        }
    }
}

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
package org.apache.kafka.clients;

import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.internals.ClusterResourceListeners;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A class encapsulating some of the logic around metadata.
 * <p>
 * This class is shared by the client thread (for partitioning) and the background sender thread.
 *
 * Metadata is maintained for only a subset of topics, which can be added to over time. When we request metadata for a
 * topic we don't have any metadata for it will trigger a metadata update.
 * <p>
 * If topic expiry is enabled for the metadata, any topic that has not been used within the expiry interval
 * is removed from the metadata refresh set after an update. Consumers disable topic expiry since they explicitly
 * manage topics while producers rely on topic expiry to limit the refresh set.
 */
public class Metadata implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(Metadata.class);

    public static final long TOPIC_EXPIRY_MS = 5 * 60 * 1000;
    private static final long TOPIC_EXPIRY_NEEDS_UPDATE = -1L;
    //todo：更新元数据失败重试请求的最小时间间隔，默认值是100ms, 目的是减少网络的压力
    private final long refreshBackoffMs;
    //todo: 多久自动更新一次元数据，默认值是5分钟更新一次。
    private final long metadataExpireMs;
    //todo: 对于producer端来讲，元数据是有版本号,每次更新元数据，都会修改一下这个版本号。
    // version自增加1，主要用来判断metadata是否有更新
    private int version;
    //todo: 上一次更新元数据的时间。
    private long lastRefreshMs;
    //todo: 上一次成功更新元数据的时间。
    //如果正常情况下，如果每次都是更新成功的，那么lastRefreshMs和lastSuccessfulRefreshMs 应该是相同的。
    private long lastSuccessfulRefreshMs;
    // SASL authentication相关， 如果出现该错误，会停止更新
    private AuthenticationException authenticationException;
    //todo: Kafka集群本身的元数据对象。
    private Cluster cluster;
    //todo: 这是一个标识，用来判断是否更新元数据的标识之一。
    private boolean needUpdate;
    /* Topics with expiry time */
    //todo: 记录了当前已有的topics的过期时间
    private final Map<String, Long> topics;
    //todo:  metadata更新时监听对象
    private final List<Listener> listeners;
    //todo: 当接收到 metadata 更新时, ClusterResourceListeners的列表
    private final ClusterResourceListeners clusterResourceListeners;
    //todo:  是否更新所有的 metadata
    private boolean needMetadataForAllTopics;
    //todo: 是否允许自动创建Topic
    private final boolean allowAutoTopicCreation;
    //todo: 默认为 true, Producer会定时移除过期的topic
    private final boolean topicExpiryEnabled;
    private boolean isClosed;

    public Metadata(long refreshBackoffMs, long metadataExpireMs, boolean allowAutoTopicCreation) {
        this(refreshBackoffMs, metadataExpireMs, allowAutoTopicCreation, false, new ClusterResourceListeners());
    }

    /**
     * Create a new Metadata instance
     * @param refreshBackoffMs The minimum amount of time that must expire between metadata refreshes to avoid busy
     *        polling
     * @param metadataExpireMs The maximum amount of time that metadata can be retained without refresh
     * @param allowAutoTopicCreation If this and the broker config 'auto.create.topics.enable' are true, topics that
     *                               don't exist will be created by the broker when a metadata request is sent
     * @param topicExpiryEnabled If true, enable expiry of unused topics
     * @param clusterResourceListeners List of ClusterResourceListeners which will receive metadata updates.
     */
    public Metadata(long refreshBackoffMs, long metadataExpireMs, boolean allowAutoTopicCreation,
                    boolean topicExpiryEnabled, ClusterResourceListeners clusterResourceListeners) {
        this.refreshBackoffMs = refreshBackoffMs;
        this.metadataExpireMs = metadataExpireMs;
        this.allowAutoTopicCreation = allowAutoTopicCreation;
        this.topicExpiryEnabled = topicExpiryEnabled;
        this.lastRefreshMs = 0L;
        this.lastSuccessfulRefreshMs = 0L;
        this.version = 0;
        this.cluster = Cluster.empty();
        this.needUpdate = false;
        this.topics = new HashMap<>();
        this.listeners = new ArrayList<>();
        this.clusterResourceListeners = clusterResourceListeners;
        this.needMetadataForAllTopics = false;
        this.isClosed = false;
    }

    /**
     * Get the current cluster info without blocking
     */
    public synchronized Cluster fetch() {
        return this.cluster;
    }

    /**
     * Add the topic to maintain in the metadata. If topic expiry is enabled, expiry time
     * will be reset on the next update.
     */
    public synchronized void add(String topic) {
        Objects.requireNonNull(topic, "topic cannot be null");
        if (topics.put(topic, TOPIC_EXPIRY_NEEDS_UPDATE) == null) {
            requestUpdateForNewTopics();
        }
    }

    /**
     * Return the next time when the current cluster info can be updated (i.e., backoff time has elapsed).
     *
     * @param nowMs current time in ms
     * @return remaining time in ms till the cluster info can be updated again
     */
    public synchronized long timeToAllowUpdate(long nowMs) {
        return Math.max(this.lastRefreshMs + this.refreshBackoffMs - nowMs, 0);
    }

    /**
     * The next time to update the cluster info is the maximum of the time the current info will expire and the time the
     * current info can be updated (i.e. backoff time has elapsed); If an update has been request then the expiry time
     * is now
     *
     * @param nowMs current time in ms
     * @return remaining time in ms till updating the cluster info
     */
    public synchronized long timeToNextUpdate(long nowMs) {
        long timeToExpire = needUpdate ? 0 : Math.max(this.lastSuccessfulRefreshMs + this.metadataExpireMs - nowMs, 0);
        return Math.max(timeToExpire, timeToAllowUpdate(nowMs));
    }

    /**
     * Request an update of the current cluster metadata info, return the current version before the update
     */
    public synchronized int requestUpdate() {
        this.needUpdate = true;
        return this.version;
    }

    /**
     * Check whether an update has been explicitly requested.
     * @return true if an update was requested, false otherwise
     */
    public synchronized boolean updateRequested() {
        return this.needUpdate;
    }

    /**
     * If any non-retriable authentication exceptions were encountered during
     * metadata update, clear and return the exception.
     */
    public synchronized AuthenticationException getAndClearAuthenticationException() {
        //todo: 如果在元数据更新期间遇到任何不可检索的身份验证异常，请清除并返回该异常。
        if (authenticationException != null) {
            AuthenticationException exception = authenticationException;
            authenticationException = null;
            return exception;
        } else
            return null;
    }

    /**
     * Wait for metadata update until the current version is larger than the last version we know of
     */
    public synchronized void awaitUpdate(final int lastVersion, final long maxWaitMs) throws InterruptedException {
        //todo：最大等待时间小于0 直接抛异常
        if (maxWaitMs < 0)
            throw new IllegalArgumentException("Max time to wait for metadata updates should not be < 0 milliseconds");


        //todo:获取当前时间
        long begin = System.currentTimeMillis();
        //todo 看剩余可以使用的时间，一开始是最大等待的时间。
        long remainingWaitMs = maxWaitMs;
        // todo: 如果元数据的版本号小于等于上一次的version，说明元数据还没有更新成功
        // 因为如果sender线程那儿 更新元数据，如果更新成功了，sender线程肯定回去累加这个version。
        while ((this.version <= lastVersion) && !isClosed()) {
            //如果在元数据更新期间遇到任何不可检索的身份验证异常
            AuthenticationException ex = getAndClearAuthenticationException();
            if (ex != null)
                throw ex;
            //还有剩余时间
            if (remainingWaitMs != 0)
                //todo: 让当前线程阻塞等待。
                //这里虽然还没有去可能sender线程的源码，但是我们应该可以猜想它肯定会有这样的一个操作
                //如果更新元数据成功了，会唤醒该线程
                wait(remainingWaitMs);

            //todo: 如果代码执行到这儿 说明就要么就被唤醒了，要么就到时间了。
            //计算一下花了多少时间。
            long elapsed = System.currentTimeMillis() - begin;

            //todo: 超时了
            if (elapsed >= maxWaitMs)
                //报超时异常
                throw new TimeoutException("Failed to update metadata after " + maxWaitMs + " ms.");


            //todo: 没有超时，就计算还剩的时间
            remainingWaitMs = maxWaitMs - elapsed;
        }
        if (isClosed())
            throw new KafkaException("Requested metadata update after close");
    }

    /**
     * Replace the current set of topics maintained to the one provided.
     * If topic expiry is enabled, expiry time of the topics will be
     * reset on the next update.
     * @param topics
     */
    public synchronized void setTopics(Collection<String> topics) {
        if (!this.topics.keySet().containsAll(topics)) {
            requestUpdateForNewTopics();
        }
        this.topics.clear();
        for (String topic : topics)
            this.topics.put(topic, TOPIC_EXPIRY_NEEDS_UPDATE);
    }

    /**
     * Get the list of topics we are currently maintaining metadata for
     */
    public synchronized Set<String> topics() {
        return new HashSet<>(this.topics.keySet());
    }

    /**
     * Check if a topic is already in the topic set.
     * @param topic topic to check
     * @return true if the topic exists, false otherwise
     */
    public synchronized boolean containsTopic(String topic) {
        return this.topics.containsKey(topic);
    }

    /**
     * Updates the cluster metadata. If topic expiry is enabled, expiry time
     * is set for topics if required and expired topics are removed from the metadata.
     *
     * @param newCluster the cluster containing metadata for topics with valid metadata
     * @param unavailableTopics topics which are non-existent or have one or more partitions whose
     *        leader is not known
     * @param now current time in milliseconds
     */
    public synchronized void update(Cluster newCluster, Set<String> unavailableTopics, long now) {
        Objects.requireNonNull(newCluster, "cluster should not be null");
        if (isClosed())
            throw new IllegalStateException("Update requested after metadata close");

        this.needUpdate = false;
        this.lastRefreshMs = now;
        this.lastSuccessfulRefreshMs = now;
        //更新元数据的版本
        this.version += 1;

        //topicExpiryEnable默认值是true
        if (topicExpiryEnabled) {
            // Handle expiry of topics from the metadata refresh set.
            //todo:  第一次进来的topics为空，下面代码不会运行

            //todo: 如果代码第二次进来, 此时此刻进来，通过Producer的sender方法进来，这个时候已经有topic元数据信息
            //可以运行下面的代码逻辑了
            for (Iterator<Map.Entry<String, Long>> it = topics.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Long> entry = it.next();
                long expireMs = entry.getValue();
                if (expireMs == TOPIC_EXPIRY_NEEDS_UPDATE)
                    entry.setValue(now + TOPIC_EXPIRY_MS);
                else if (expireMs <= now) {
                    it.remove();
                    log.debug("Removing unused topic {} from the metadata list, expiryMs {} now {}", entry.getKey(), expireMs, now);
                }
            }
        }

        for (Listener listener: listeners)
            listener.onMetadataUpdate(newCluster, unavailableTopics);

        String previousClusterId = cluster.clusterResource().clusterId();

        //todo: 这个值默认是false，代码不会进入到该分支中
        if (this.needMetadataForAllTopics) {
            // the listener may change the interested topics, which could cause another metadata refresh.
            // If we have already fetched all topics, however, another fetch should be unnecessary.
            this.needUpdate = false;
            this.cluster = getClusterForCurrentTopics(cluster);
        } else {
            //todo: 直接把传进入来的Cluster对象赋值给cluster，内部仅仅只包含了kafka集群地址
            //所有初始化的时候，update这个方法并没有去服务端去拉取元数据
            this.cluster = cluster;   //address
        }

        // The bootstrap cluster is guaranteed not to have any useful information
        if (!newCluster.isBootstrapConfigured()) {
            String newClusterId = newCluster.clusterResource().clusterId();
            if (newClusterId == null ? previousClusterId != null : !newClusterId.equals(previousClusterId))
                log.info("Cluster ID: {}", newClusterId);
            clusterResourceListeners.onUpdate(newCluster.clusterResource());
        }

        //todo: 该方法最最重要的作业就是唤醒上一讲中处于wait的线程,  metadata.awaitUpdate(version, remainingWaitMs);
        notifyAll();
        log.debug("Updated cluster metadata version {} to {}", this.version, this.cluster);
    }

    /**
     * Record an attempt to update the metadata that failed. We need to keep track of this
     * to avoid retrying immediately.
     */
    public synchronized void failedUpdate(long now, AuthenticationException authenticationException) {
        this.lastRefreshMs = now;
        this.authenticationException = authenticationException;
        if (authenticationException != null)
            this.notifyAll();
    }

    /**
     * @return The current metadata version
     */
    public synchronized int version() {
        return this.version;
    }

    /**
     * The last time metadata was successfully updated.
     */
    public synchronized long lastSuccessfulUpdate() {
        return this.lastSuccessfulRefreshMs;
    }

    public boolean allowAutoTopicCreation() {
        return allowAutoTopicCreation;
    }

    /**
     * Set state to indicate if metadata for all topics in Kafka cluster is required or not.
     * @param needMetadataForAllTopics boolean indicating need for metadata of all topics in cluster.
     */
    public synchronized void needMetadataForAllTopics(boolean needMetadataForAllTopics) {
        if (needMetadataForAllTopics && !this.needMetadataForAllTopics) {
            requestUpdateForNewTopics();
        }
        this.needMetadataForAllTopics = needMetadataForAllTopics;
    }

    /**
     * Get whether metadata for all topics is needed or not
     */
    public synchronized boolean needMetadataForAllTopics() {
        return this.needMetadataForAllTopics;
    }

    /**
     * Add a Metadata listener that gets notified of metadata updates
     */
    public synchronized void addListener(Listener listener) {
        this.listeners.add(listener);
    }

    /**
     * Stop notifying the listener of metadata updates
     */
    public synchronized void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    /**
     * "Close" this metadata instance to indicate that metadata updates are no longer possible. This is typically used
     * when the thread responsible for performing metadata updates is exiting and needs a way to relay this information
     * to any other thread(s) that could potentially wait on metadata update to come through.
     */
    @Override
    public synchronized void close() {
        this.isClosed = true;
        this.notifyAll();
    }

    /**
     * Check if this metadata instance has been closed. See {@link #close()} for more information.
     * @return True if this instance has been closed; false otherwise
     */
    public synchronized boolean isClosed() {
        return this.isClosed;
    }

    /**
     * MetadataUpdate Listener
     */
    public interface Listener {
        /**
         * Callback invoked on metadata update.
         *
         * @param cluster the cluster containing metadata for topics with valid metadata
         * @param unavailableTopics topics which are non-existent or have one or more partitions whose
         *        leader is not known
         */
        void onMetadataUpdate(Cluster cluster, Set<String> unavailableTopics);
    }

    private synchronized void requestUpdateForNewTopics() {
        // Override the timestamp of last refresh to let immediate update.
        this.lastRefreshMs = 0;
        requestUpdate();
    }

    private Cluster getClusterForCurrentTopics(Cluster cluster) {
        Set<String> unauthorizedTopics = new HashSet<>();
        Set<String> invalidTopics = new HashSet<>();
        Collection<PartitionInfo> partitionInfos = new ArrayList<>();
        List<Node> nodes = Collections.emptyList();
        Set<String> internalTopics = Collections.emptySet();
        Node controller = null;
        String clusterId = null;
        if (cluster != null) {
            clusterId = cluster.clusterResource().clusterId();
            internalTopics = cluster.internalTopics();
            unauthorizedTopics.addAll(cluster.unauthorizedTopics());
            unauthorizedTopics.retainAll(this.topics.keySet());

            invalidTopics.addAll(cluster.invalidTopics());
            invalidTopics.addAll(this.cluster.invalidTopics());

            for (String topic : this.topics.keySet()) {
                List<PartitionInfo> partitionInfoList = cluster.partitionsForTopic(topic);
                if (!partitionInfoList.isEmpty()) {
                    partitionInfos.addAll(partitionInfoList);
                }
            }
            nodes = cluster.nodes();
            controller  = cluster.controller();
        }
        return new Cluster(clusterId, nodes, partitionInfos, unauthorizedTopics, invalidTopics, internalTopics, controller);
    }
}

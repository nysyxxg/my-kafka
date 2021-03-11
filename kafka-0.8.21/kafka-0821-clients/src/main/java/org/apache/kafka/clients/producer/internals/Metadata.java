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
package org.apache.kafka.clients.producer.internals;

import java.util.HashSet;
import java.util.Set;

import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.errors.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class encapsulating some of the logic around metadata.
 * <p>
 * This class is shared by the client thread (for partitioning) and the background sender thread.
 * 
 * Metadata is maintained for only a subset of topics, which can be added to over time. When we request metdata for a
 * topic we don't have any metadata for it will trigger a metadata update.
 */
/**
 * Metadata的线程安全性
	从上图可以看出，Metadata是多个producer线程读，一个sender线程更新，因此它必须是线程安全的。
	Kafka的官方文档上也有说明，KafkaProducer是线程安全的，可以在多线程中调用： 它的所有public方法都是synchronized:
 * @author admin
 *
 */
public final class Metadata {

    private static final Logger log = LoggerFactory.getLogger(Metadata.class);
    // 两次更新元数据请求的最小时间间隔
    private final long refreshBackoffMs;//更新失败的情况下，下一次更新的补偿时间（这个变量在代码中意义不是太大）
    private final long metadataExpireMs; //关键值：每隔多久，更新一次元数据信息。缺省是600*1000，也就是10分种，
    private int version;    //每更新成功1次，version递增1。这个变量主要用于在while循环，wait的时候，作为循环判断条件
    private long lastRefreshMs;//上一次更新时间（也包含更新失败的情况）  // 上一次成功更新的时间（如果每次都成功的话，则2者相等。否则，lastSuccessulRefreshMs < lastRefreshMs)
    private Cluster cluster;  //kafka集群元数据配置信息
    private boolean needUpdate; //是否强制刷新
    private final Set<String> topics;

    /**
     * Create a metadata instance with reasonable defaults
     */
    public Metadata() {
        this(100L, 60 * 60 * 1000L);
    }

    /**
     * Create a new Metadata instance
     * @param refreshBackoffMs The minimum amount of time that must expire between metadata refreshes to avoid busy
     *        polling         无需刷新即可保留元数据的最长时间
     * @param metadataExpireMs The maximum amount of time that metadata can be retained without refresh
     */
    public Metadata(long refreshBackoffMs, long metadataExpireMs) {
        this.refreshBackoffMs = refreshBackoffMs;
        this.metadataExpireMs = metadataExpireMs;
        this.lastRefreshMs = 0L;
        this.version = 0;
        this.cluster = Cluster.empty();
        this.needUpdate = false;
        this.topics = new HashSet<String>();
    }

    /**
     * Get the current cluster info without blocking
     */
    public synchronized Cluster fetch() {
        return this.cluster;
    }

    /**
     * Add the topic to maintain in the metadata
     */
    public synchronized void add(String topic) {
        topics.add(topic);
    }

    /**
     * The next time to update the cluster info is the maximum of the time the current info will expire
     * and the time the current info can be updated (i.e. backoff time has elapsed); If an update has
     * been request then the expiry time is now
     */
    public synchronized long timeToNextUpdate(long nowMs) {
        long timeToExpire = needUpdate ? 0 : Math.max(this.lastRefreshMs + this.metadataExpireMs - nowMs, 0);
        long timeToAllowUpdate = this.lastRefreshMs + this.refreshBackoffMs - nowMs;
        return Math.max(timeToExpire, timeToAllowUpdate);
    }

    /**
     * Request an update of the current cluster metadata info, return the current version before the update
     */
    public synchronized int requestUpdate() {
        this.needUpdate = true;
        return this.version;
    }

    /**
     * Wait for metadata update until the current version is larger than the last version we know of
     */
    public synchronized void awaitUpdate(int lastVerison, long maxWaitMs) {
        long begin = System.currentTimeMillis();
        long remainingWaitMs = maxWaitMs;
        while (this.version <= lastVerison) {//当Sender成功更新meatadata之后，version加1。否则会循环，一直wait
            try {
                wait(remainingWaitMs); // 线程的wait机制，wait和synchronized的配合使用
            } catch (InterruptedException e) { /* this is fine */
            }
            long elapsed = System.currentTimeMillis() - begin;
            if (elapsed >= maxWaitMs) //wait时间超出了最长等待时间
                throw new TimeoutException("Failed to update metadata after " + maxWaitMs + " ms.");
            remainingWaitMs = maxWaitMs - elapsed;
        }
    }
    
    /**
     * Record an attempt to update the metadata that failed. We need to keep track of this
     * to avoid retrying immediately.
     */
    public synchronized void failedUpdate(long now) {
        this.lastRefreshMs = now;
    }

    /**
     * Get the list of topics we are currently maintaining metadata for
     */
    public synchronized Set<String> topics() {
        return new HashSet<String>(this.topics);
    }

    /**
     * Update the cluster metadata
     */
    public synchronized void update(Cluster cluster, long now) {
        this.needUpdate = false;
        this.lastRefreshMs = now;
        this.version += 1;
        this.cluster = cluster;
        notifyAll(); // 唤醒等待的线程
        log.debug("Updated cluster metadata version {} to {}", this.version, this.cluster);
    }

    /**
     * The last time metadata was updated.
     */
    public synchronized long lastUpdate() {
        return this.lastRefreshMs;
    }

    /**
     * The metadata refresh backoff in ms
     */
    public long refreshBackoff() {
        return refreshBackoffMs;
    }
}

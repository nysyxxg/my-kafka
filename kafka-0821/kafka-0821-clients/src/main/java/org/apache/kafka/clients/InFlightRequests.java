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
package org.apache.kafka.clients;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 发送请求在从Sender线程发送kafka之前，还会保存到InFlightRequests中，InFlightRequests 保存对象的具体形式为
 *  Map<NodeId, Deque<ClientRequest>>,他的主要作用是缓存了已经发出去的但是还没有收到响应的请求。
 *  NodeId是一个String类型，表示节点的id编码。
 * 已发送或正在发送但尚未收到响应的请求集
 * The set of requests which have been sent or are being sent but haven't yet received a response
 */
final class InFlightRequests {
    // 通过配置参数还可以限制每个链接最多缓存的请求数，也就是客户端与Node之间的连接。这个配置参数为：
    // max.in.flight.requests.per.connection。默认值为5. 即每个连接最多只能缓存5个未响应的请求，超过该参数值之后，
    // 就不会再向这个连接发送更多的请求了。除非有缓存的请求收到了响应。
    private final int maxInFlightRequestsPerConnection;
    private final Map<Integer, Deque<ClientRequest>> requests = new HashMap<Integer, Deque<ClientRequest>>();

    public InFlightRequests(int maxInFlightRequestsPerConnection) {
        this.maxInFlightRequestsPerConnection = maxInFlightRequestsPerConnection;
    }

    /**
     * Add the given request to the queue for the node it was directed to
     */
    public void add(ClientRequest request) {
        Deque<ClientRequest> reqs = this.requests.get(request.request().destination());
        if (reqs == null) {
            reqs = new ArrayDeque<ClientRequest>();
            this.requests.put(request.request().destination(), reqs);
        }
        reqs.addFirst(request);
    }

    /**
     * Get the request queue for the given node
     */
    private Deque<ClientRequest> requestQueue(int node) {
        Deque<ClientRequest> reqs = requests.get(node);
        if (reqs == null || reqs.isEmpty())
            throw new IllegalStateException("Response from server for which there are no in-flight requests.");
        return reqs;
    }

    /**
     * Get the oldest request (the one that that will be completed next) for the given node
     */
    public ClientRequest completeNext(int node) {
        return requestQueue(node).pollLast();
    }

    /**
     * 获取我们发送给给定节点的最后一个请求（但不要将其从队列中删除）
     * Get the last request we sent to the given node (but don't remove it from the queue)
     * @param node The node id
     */
    public ClientRequest lastSent(int node) {
        return requestQueue(node).peekFirst();
    }

    /**
     * Complete the last request that was sent to a particular node.
     * @param node The node the request was sent to
     * @return The request
     */
    public ClientRequest completeLastSent(int node) {
        return requestQueue(node).pollFirst();
    }

    /**
     * Can we send more requests to this node?  判断是否能够向这个节点发送更多的请求?
     *  通过对于queue.size() 和 maxInFlightRequestsPerConnection的大小，
     *  来判断对应Node中是否已经堆积了很多未响应的消息。如果真是如此，那么说明这个Node节点负载较大或者网络连接有问题，
     *  再继续向其发送请求会增大请求超时的可能
     * @param node Node in question
     * @return true iff we have no requests still being sent to the given node
     */
    public boolean canSendMore(int node) {
        Deque<ClientRequest> queue = requests.get(node);
        return queue == null || queue.isEmpty() ||
               (queue.peekFirst().request().completed() && queue.size() < this.maxInFlightRequestsPerConnection);
    }

    /**
     * Return the number of inflight requests directed at the given node
     * @param node The node
     * @return The request count.
     */
    public int inFlightRequestCount(int node) {  // 根据节点Id获取请求大小，就可以判断所有Node中负载最小那一个。
        Deque<ClientRequest> queue = requests.get(node);
        return queue == null ? 0 : queue.size();
    }

    /**
     * Count all in-flight requests for all nodes  //得到所有节点的in-flight 请求
     */
    public int inFlightRequestCount() {
        int total = 0;
        for (Deque<ClientRequest> deque : this.requests.values())
            total += deque.size();
        return total;
    }

    /**
     * Clear out all the in-flight requests for the given node and return them
     * 
     * @param node The node
     * @return All the in-flight requests for that node that have been removed
     */
    public Iterable<ClientRequest> clearAll(int node) {
        Deque<ClientRequest> reqs = requests.get(node);
        if (reqs == null) {
            return Collections.emptyList();
        } else {
            return requests.remove(node);
        }
    }

}
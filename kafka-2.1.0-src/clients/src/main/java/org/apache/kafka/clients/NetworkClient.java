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
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.apache.kafka.common.metrics.Sensor;
import org.apache.kafka.common.network.ChannelState;
import org.apache.kafka.common.network.NetworkReceive;
import org.apache.kafka.common.network.Selectable;
import org.apache.kafka.common.network.Send;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.protocol.CommonFields;
import org.apache.kafka.common.protocol.types.Struct;
import org.apache.kafka.common.requests.AbstractRequest;
import org.apache.kafka.common.requests.AbstractResponse;
import org.apache.kafka.common.requests.ApiVersionsRequest;
import org.apache.kafka.common.requests.ApiVersionsResponse;
import org.apache.kafka.common.requests.MetadataRequest;
import org.apache.kafka.common.requests.MetadataResponse;
import org.apache.kafka.common.requests.RequestHeader;
import org.apache.kafka.common.requests.ResponseHeader;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.Utils;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * A network client for asynchronous request/response network i/o. This is an internal class used to implement the
 * user-facing producer and consumer clients.
 * <p>
 * This class is not thread-safe!
 */
public class NetworkClient implements KafkaClient {

    private final Logger log;

    /* the selector used to perform network i/o */
    private final Selectable selector;

    private final MetadataUpdater metadataUpdater;

    private final Random randOffset;

    /* the state of each node's connection */
    private final ClusterConnectionStates connectionStates;

    /* the set of requests currently being sent or awaiting a response */
    private final InFlightRequests inFlightRequests;

    /* the socket send buffer size in bytes */
    private final int socketSendBuffer;

    /* the socket receive size buffer in bytes */
    private final int socketReceiveBuffer;

    /* the client id used to identify this client in requests to the server */
    private final String clientId;

    /* the current correlation id to use when sending requests to servers */
    private int correlation;

    /* default timeout for individual requests to await acknowledgement from servers */
    private final int defaultRequestTimeoutMs;

    /* time in ms to wait before retrying to create connection to a server */
    private final long reconnectBackoffMs;

    private final ClientDnsLookup clientDnsLookup;

    private final Time time;

    /**
     * True if we should send an ApiVersionRequest when first connecting to a broker.
     */
    private final boolean discoverBrokerVersions;

    private final ApiVersions apiVersions;

    private final Map<String, ApiVersionsRequest.Builder> nodesNeedingApiVersionsFetch = new HashMap<>();

    private final List<ClientResponse> abortedSends = new LinkedList<>();

    private final Sensor throttleTimeSensor;

    public NetworkClient(Selectable selector,
                         Metadata metadata,
                         String clientId,
                         int maxInFlightRequestsPerConnection,
                         long reconnectBackoffMs,
                         long reconnectBackoffMax,
                         int socketSendBuffer,
                         int socketReceiveBuffer,
                         int defaultRequestTimeoutMs,
                         ClientDnsLookup clientDnsLookup,
                         Time time,
                         boolean discoverBrokerVersions,
                         ApiVersions apiVersions,
                         LogContext logContext) {
        this(null,
             metadata,
             selector,
             clientId,
             maxInFlightRequestsPerConnection,
             reconnectBackoffMs,
             reconnectBackoffMax,
             socketSendBuffer,
             socketReceiveBuffer,
             defaultRequestTimeoutMs,
             clientDnsLookup,
             time,
             discoverBrokerVersions,
             apiVersions,
             null,
             logContext);
    }

    public NetworkClient(Selectable selector,
            Metadata metadata,
            String clientId,
            int maxInFlightRequestsPerConnection,
            long reconnectBackoffMs,
            long reconnectBackoffMax,
            int socketSendBuffer,
            int socketReceiveBuffer,
            int defaultRequestTimeoutMs,
            ClientDnsLookup clientDnsLookup,
            Time time,
            boolean discoverBrokerVersions,
            ApiVersions apiVersions,
            Sensor throttleTimeSensor,
            LogContext logContext) {
        this(null,
             metadata,
             selector,
             clientId,
             maxInFlightRequestsPerConnection,
             reconnectBackoffMs,
             reconnectBackoffMax,
             socketSendBuffer,
             socketReceiveBuffer,
             defaultRequestTimeoutMs,
             clientDnsLookup,
             time,
             discoverBrokerVersions,
             apiVersions,
             throttleTimeSensor,
             logContext);
    }

    public NetworkClient(Selectable selector,
                         MetadataUpdater metadataUpdater,
                         String clientId,
                         int maxInFlightRequestsPerConnection,
                         long reconnectBackoffMs,
                         long reconnectBackoffMax,
                         int socketSendBuffer,
                         int socketReceiveBuffer,
                         int defaultRequestTimeoutMs,
                         ClientDnsLookup clientDnsLookup,
                         Time time,
                         boolean discoverBrokerVersions,
                         ApiVersions apiVersions,
                         LogContext logContext) {
        this(metadataUpdater,
             null,
             selector,
             clientId,
             maxInFlightRequestsPerConnection,
             reconnectBackoffMs,
             reconnectBackoffMax,
             socketSendBuffer,
             socketReceiveBuffer,
             defaultRequestTimeoutMs,
             clientDnsLookup,
             time,
             discoverBrokerVersions,
             apiVersions,
             null,
             logContext);
    }

    private NetworkClient(MetadataUpdater metadataUpdater,
                          Metadata metadata,
                          Selectable selector,
                          String clientId,
                          int maxInFlightRequestsPerConnection,
                          long reconnectBackoffMs,
                          long reconnectBackoffMax,
                          int socketSendBuffer,
                          int socketReceiveBuffer,
                          int defaultRequestTimeoutMs,
                          ClientDnsLookup clientDnsLookup,
                          Time time,
                          boolean discoverBrokerVersions,
                          ApiVersions apiVersions,
                          Sensor throttleTimeSensor,
                          LogContext logContext) {
        /* It would be better if we could pass `DefaultMetadataUpdater` from the public constructor, but it's not
         * possible because `DefaultMetadataUpdater` is an inner class and it can only be instantiated after the
         * super constructor is invoked.
         */
        if (metadataUpdater == null) {
            if (metadata == null)
                throw new IllegalArgumentException("`metadata` must not be null");
            this.metadataUpdater = new DefaultMetadataUpdater(metadata);
        } else {
            this.metadataUpdater = metadataUpdater;
        }
        this.selector = selector;
        this.clientId = clientId;
        this.inFlightRequests = new InFlightRequests(maxInFlightRequestsPerConnection);
        this.connectionStates = new ClusterConnectionStates(reconnectBackoffMs, reconnectBackoffMax);
        this.socketSendBuffer = socketSendBuffer;
        this.socketReceiveBuffer = socketReceiveBuffer;
        this.correlation = 0;
        this.randOffset = new Random();
        this.defaultRequestTimeoutMs = defaultRequestTimeoutMs;
        this.reconnectBackoffMs = reconnectBackoffMs;
        this.time = time;
        this.discoverBrokerVersions = discoverBrokerVersions;
        this.apiVersions = apiVersions;
        this.throttleTimeSensor = throttleTimeSensor;
        this.log = logContext.logger(NetworkClient.class);
        this.clientDnsLookup = clientDnsLookup;
    }

    /**
     * Begin connecting to the given node, return true if we are already connected and ready to send to that node.
     *
     * @param node The node to check
     * @param now The current timestamp
     * @return True if we are ready to send to the given node
     */
    @Override
    public boolean ready(Node node, long now) {
        //todo: 节点为空就报异常
        if (node.isEmpty())
            throw new IllegalArgumentException("Cannot connect to empty node " + node);

        //todo: 判断要发送消息的主机，是否具备发送消息的条件
        if (isReady(node, now))
            return true;

        //todo: 第一次进来应该是并没有建立好连接，判断是否可以尝试去建立好网络
        if (connectionStates.canConnect(node.idString(), now))
            // if we are interested in sending to a node and we don't have a connection to it, initiate one
            //todo: 初始化连接
            initiateConnect(node, now);

        return false;
    }

    // Visible for testing
    boolean canConnect(Node node, long now) {
        return connectionStates.canConnect(node.idString(), now);
    }

    /**
     * Disconnects the connection to a particular node, if there is one.
     * Any pending ClientRequests for this connection will receive disconnections.
     *
     * @param nodeId The id of the node
     */
    @Override
    public void disconnect(String nodeId) {
        if (connectionStates.isDisconnected(nodeId))
            return;

        selector.close(nodeId);
        List<ApiKeys> requestTypes = new ArrayList<>();
        long now = time.milliseconds();
        for (InFlightRequest request : inFlightRequests.clearAll(nodeId)) {
            if (request.isInternalRequest) {
                if (request.header.apiKey() == ApiKeys.METADATA) {
                    metadataUpdater.handleDisconnection(request.destination);
                }
            } else {
                requestTypes.add(request.header.apiKey());
                abortedSends.add(new ClientResponse(request.header,
                        request.callback, request.destination, request.createdTimeMs, now,
                        true, null, null, null));
            }
        }
        connectionStates.disconnected(nodeId, now);
        if (log.isDebugEnabled()) {
            log.debug("Manually disconnected from {}. Removed requests: {}.", nodeId,
                Utils.join(requestTypes, ", "));
        }
    }

    /**
     * Closes the connection to a particular node (if there is one).
     * All requests on the connection will be cleared.  ClientRequest callbacks will not be invoked
     * for the cleared requests, nor will they be returned from poll().
     *
     * @param nodeId The id of the node
     */
    @Override
    public void close(String nodeId) {
        selector.close(nodeId);
        for (InFlightRequest request : inFlightRequests.clearAll(nodeId))
            if (request.isInternalRequest && request.header.apiKey() == ApiKeys.METADATA)
                metadataUpdater.handleDisconnection(request.destination);
        connectionStates.remove(nodeId);
    }

    /**
     * Returns the number of milliseconds to wait, based on the connection state, before attempting to send data. When
     * disconnected, this respects the reconnect backoff time. When connecting or connected, this handles slow/stalled
     * connections.
     *
     * @param node The node to check
     * @param now The current timestamp
     * @return The number of milliseconds to wait.
     */
    @Override
    public long connectionDelay(Node node, long now) {
        return connectionStates.connectionDelay(node.idString(), now);
    }

    // Return the remaining throttling delay in milliseconds if throttling is in progress. Return 0, otherwise.
    // This is for testing.
    public long throttleDelayMs(Node node, long now) {
        return connectionStates.throttleDelayMs(node.idString(), now);
    }

    /**
     * Return the poll delay in milliseconds based on both connection and throttle delay.
     * @param node the connection to check
     * @param now the current time in ms
     */
    @Override
    public long pollDelayMs(Node node, long now) {
        return connectionStates.pollDelayMs(node.idString(), now);
    }

    /**
     * Check if the connection of the node has failed, based on the connection state. Such connection failure are
     * usually transient and can be resumed in the next {@link #ready(org.apache.kafka.common.Node, long)} }
     * call, but there are cases where transient failures needs to be caught and re-acted upon.
     *
     * @param node the node to check
     * @return true iff the connection has failed and the node is disconnected
     */
    @Override
    public boolean connectionFailed(Node node) {
        return connectionStates.isDisconnected(node.idString());
    }

    /**
     * Check if authentication to this node has failed, based on the connection state. Authentication failures are
     * propagated without any retries.
     *
     * @param node the node to check
     * @return an AuthenticationException iff authentication has failed, null otherwise
     */
    @Override
    public AuthenticationException authenticationException(Node node) {
        return connectionStates.authenticationException(node.idString());
    }

    /**
     * Check if the node with the given id is ready to send more requests.
     *
     * @param node The node
     * @param now The current time in ms
     * @return true if the node is ready
     */
    @Override
    public boolean isReady(Node node, long now) {
        // if we need to update our metadata now declare all requests unready to make metadata requests first
        // priority
        /**
         *    条件1：!metadataUpdater.isUpdateDue(now)
         *     要发送写数据请求的时候，不能是正在更新元数据的操作
         *
         *  条件2：canSendRequest(node.idString())
         *       对应的broker是否已经建立好连接，并且可以发送数据
         *
         */
        return !metadataUpdater.isUpdateDue(now) && canSendRequest(node.idString(), now);
    }

    /**
     * Are we connected and ready and able to send more requests to the given connection?
     *
     * @param node The node
     * @param now the current timestamp
     */
    private boolean canSendRequest(String node, long now) {
        /**
         * todo: connectionStates.isReady(node) :
         *    生产者缓存了多个连接，一个broker对应一个连接，从缓存中检查是否与节点建立好连接
         *
         * todo: selector.isChannelReady(node)：
         *   一个selector上面绑定了多个KafkaChannel（java socketChannel）,一个kafkaChannel就是一个连接
         *
         * todo: inFlightRequests.canSendMore(node)：
         *   与"max.in.flight.requests.per.connection"参数有关系
         *   表示每个向kafka  broker 节点发送消息的连接，最多能够容忍消息发送出去而没有接受到响应的个数，默认只是5
         *   这一块如果大于1 ，可能会出现分区数据乱序。
         *
         */
        return connectionStates.isReady(node, now) && selector.isChannelReady(node) &&
            inFlightRequests.canSendMore(node);
    }

    /**
     * Queue up the given request for sending. Requests can only be sent out to ready nodes.
     * @param request The request
     * @param now The current timestamp
     */
    @Override
    public void send(ClientRequest request, long now) {
        //todo:核心方法
        doSend(request, false, now);
    }

    private void sendInternalMetadataRequest(MetadataRequest.Builder builder,
                                             String nodeConnectionId, long now) {
        //todo: 构建一个拉取元数据的请求
        ClientRequest clientRequest = newClientRequest(nodeConnectionId, builder, now, true);
        //todo; 发送获取元数据信息的请求
        //todo: 内部中的代码是怎么发送的？我们在分析kafka网络知识的时候解决
        doSend(clientRequest, true, now);
    }

    private void doSend(ClientRequest clientRequest, boolean isInternalRequest, long now) {
        //todo:取得请求发送目的地的nodeId
        String nodeId = clientRequest.destination();
        if (!isInternalRequest) {
            // If this request came from outside the NetworkClient, validate
            // that we can send data.  If the request is internal, we trust
            // that internal code has done this validation.  Validation
            // will be slightly different for some internal requests (for
            // example, ApiVersionsRequests can be sent prior to being in
            // READY state.)
            //检查NetWorkClient状态为激活
            if (!canSendRequest(nodeId, now))
                throw new IllegalStateException("Attempt to send a request to node " + nodeId + " which is not ready.");
        }
        AbstractRequest.Builder<?> builder = clientRequest.requestBuilder();
        try {
            NodeApiVersions versionInfo = apiVersions.get(nodeId);
            short version;
            // Note: if versionInfo is null, we have no server version information. This would be
            // the case when sending the initial ApiVersionRequest which fetches the version
            // information itself.  It is also the case when discoverBrokerVersions is set to false.
            if (versionInfo == null) {
                version = builder.latestAllowedVersion();
                if (discoverBrokerVersions && log.isTraceEnabled())
                    log.trace("No version information found when sending {} with correlation id {} to node {}. " +
                            "Assuming version {}.", clientRequest.apiKey(), clientRequest.correlationId(), nodeId, version);
            } else {
                version = versionInfo.latestUsableVersion(clientRequest.apiKey(), builder.oldestAllowedVersion(),
                        builder.latestAllowedVersion());
            }
            // The call to build may also throw UnsupportedVersionException, if there are essential
            // fields that cannot be represented in the chosen version.
            //todo: 核心方法
            doSend(clientRequest, isInternalRequest, now, builder.build(version));
        } catch (UnsupportedVersionException unsupportedVersionException) {
            // If the version is not supported, skip sending the request over the wire.
            // Instead, simply add it to the local queue of aborted requests.
            log.debug("Version mismatch when attempting to send {} with correlation id {} to {}", builder,
                    clientRequest.correlationId(), clientRequest.destination(), unsupportedVersionException);
            //todo: 对于异常直接封装ClientResponse对象
            ClientResponse clientResponse = new ClientResponse(clientRequest.makeHeader(builder.latestAllowedVersion()),
                    clientRequest.callback(), clientRequest.destination(), now, now,
                    false, unsupportedVersionException, null, null);
            abortedSends.add(clientResponse);
        }
    }

    private void doSend(ClientRequest clientRequest, boolean isInternalRequest, long now, AbstractRequest request) {
        //todo: 获取目标节点nodeid
        String destination = clientRequest.destination();
        //todo: 构建请求头信息
        RequestHeader header = clientRequest.makeHeader(request.version());
        //日志信息打印
        if (log.isDebugEnabled()) {
            int latestClientVersion = clientRequest.apiKey().latestVersion();
            if (header.apiVersion() == latestClientVersion) {
                log.trace("Sending {} {} with correlation id {} to node {}", clientRequest.apiKey(), request,
                        clientRequest.correlationId(), destination);
            } else {
                log.debug("Using older server API v{} to send {} {} with correlation id {} to node {}",
                        header.apiVersion(), clientRequest.apiKey(), request, clientRequest.correlationId(), destination);
            }
        }
        //todo: 封装Send对象,这个send对象封装了目的地和header生成的ByteBuffer对象
        Send send = request.toSend(destination, header);
        //todo: 基于clientRequest构建InFlightRequest对象
        InFlightRequest inFlightRequest = new InFlightRequest(
                clientRequest,
                header,
                isInternalRequest,
                request,
                send,
                now);
        //todo: 添加请求到inFlightRequests中
        //todo: 表示每一个网络连接允许发送出去接受不到请求响应的个数最多是5个。
        //我们可以猜想： 如果正常完成了请求之后，会把请求从inFlightRequests缓存中移除掉

        this.inFlightRequests.add(inFlightRequest);

        //todo: 把send请求加入队列等待随后的poll方法把它发送出去
        selector.send(send);
    }

    /**
     * Do actual reads and writes to sockets.
     *
     * @param timeout The maximum amount of time to wait (in ms) for responses if there are none immediately,
     *                must be non-negative. The actual timeout will be the minimum of timeout, request timeout and
     *                metadata timeout
     * @param now The current time in milliseconds
     * @return The list of responses received
     */
    @Override
    public List<ClientResponse> poll(long timeout, long now) {
        if (!abortedSends.isEmpty()) {
            // If there are aborted sends because of unsupported version exceptions or disconnects,
            // handle them immediately without waiting for Selector#poll.
            //当连接断开，或者版本不支持，需要优先处理这些响应
            List<ClientResponse> responses = new ArrayList<>();
            handleAbortedSends(responses);
            completeResponses(responses);
            return responses;
        }

        //todo: 1、封装了一个要拉取元数据请求, 目前还并没有把请求发送出去，客户端缓存了这些请求
        long metadataTimeout = metadataUpdater.maybeUpdate(now);
        try {

            //todo: 2、发送请求、进行复杂的网络操作
            /**
             * 但是我们目前还没有学习到kafka的网络
             * 所以这儿大家就只需要知道这儿会发送网络请求。
             */
            this.selector.poll(Utils.min(timeout, metadataTimeout, defaultRequestTimeoutMs));
        } catch (IOException e) {
            log.error("Unexpected error during I/O", e);
        }

        // process completed actions
        long updatedNow = this.time.milliseconds();
        List<ClientResponse> responses = new ArrayList<>();
        //处理发送完成的Send对象，不需要响应的请求
        handleCompletedSends(responses, updatedNow);
        /**
         * todo: 3、处理响应，响应里面就会有我们需要的元数据。
         *
         * 这个地方是我们在看生产者是如何获取元数据的时候，看的。
         * 其实Kafak获取元数据的流程跟我们发送消息的流程是一模一样。
         * 获取元数据 ---> 判断网络连接是否建立好 ---> 建立网络连接 ---> 发送请求（获取元数据的请求）---> 服务端发送回来响应（带了集群的元数据信息）
         */
        //处理接受到的响应responses
        handleCompletedReceives(responses, updatedNow);

        //处理关闭的响应
        handleDisconnections(responses, updatedNow);
        //处理连接状态变化
        handleConnections();
        //处理版本协调请求
        handleInitiateApiVersionRequests(updatedNow);
        //处理超时的请求
        handleTimedOutRequests(responses, updatedNow);
        //处理完成的ClientResponse .invoke callback 触发回调函数的调用
        completeResponses(responses);

        return responses;
    }

    //对ClientResponse进行处理
    //invoke callbacks
    private void completeResponses(List<ClientResponse> responses) {
        //todo: 遍历responses
        for (ClientResponse response : responses) {
            try {
                //todo: 调用其回调callback方法，这样生产者就能接受到服务端响应的信息
                //由于我们之前在发送网络请求的过程中，绑定了回调函数
                response.onComplete();
            } catch (Exception e) {
                log.error("Uncaught error in request completion:", e);
            }
        }
    }

    /**
     * Get the number of in-flight requests
     */
    @Override
    public int inFlightRequestCount() {
        return this.inFlightRequests.count();
    }

    @Override
    public boolean hasInFlightRequests() {
        return !this.inFlightRequests.isEmpty();
    }

    /**
     * Get the number of in-flight requests for a given node
     */
    @Override
    public int inFlightRequestCount(String node) {
        return this.inFlightRequests.count(node);
    }

    @Override
    public boolean hasInFlightRequests(String node) {
        return !this.inFlightRequests.isEmpty(node);
    }

    @Override
    public boolean hasReadyNodes(long now) {
        return connectionStates.hasReadyNodes(now);
    }

    /**
     * Interrupt the client if it is blocked waiting on I/O.
     */
    @Override
    public void wakeup() {
        this.selector.wakeup();
    }

    /**
     * Close the network client
     */
    @Override
    public void close() {
        this.selector.close();
        this.metadataUpdater.close();
    }

    /**
     * Choose the node with the fewest outstanding requests which is at least eligible for connection. This method will
     * prefer a node with an existing connection, but will potentially choose a node for which we don't yet have a
     * connection if all existing connections are in use. This method will never choose a node for which there is no
     * existing connection and from which we have disconnected within the reconnect backoff period.
     *
     * @return The node with the fewest in-flight requests.
     */
    @Override
    public Node leastLoadedNode(long now) {
        List<Node> nodes = this.metadataUpdater.fetchNodes();
        if (nodes.isEmpty())
            throw new IllegalStateException("There are no nodes in the Kafka cluster");
        int inflight = Integer.MAX_VALUE;
        Node found = null;

        int offset = this.randOffset.nextInt(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            int idx = (offset + i) % nodes.size();
            Node node = nodes.get(idx);
            int currInflight = this.inFlightRequests.count(node.idString());
            if (currInflight == 0 && isReady(node, now)) {
                // if we find an established connection with no in-flight requests we can stop right away
                log.trace("Found least loaded node {} connected with no in-flight requests", node);
                return node;
            } else if (!this.connectionStates.isBlackedOut(node.idString(), now) && currInflight < inflight) {
                // otherwise if this is the best we have found so far, record that
                inflight = currInflight;
                found = node;
            } else if (log.isTraceEnabled()) {
                log.trace("Removing node {} from least loaded node selection: is-blacked-out: {}, in-flight-requests: {}",
                        node, this.connectionStates.isBlackedOut(node.idString(), now), currInflight);
            }
        }

        if (found != null)
            log.trace("Found least loaded node {}", found);
        else
            log.trace("Least loaded node selection failed to find an available node");

        return found;
    }

    public static AbstractResponse parseResponse(ByteBuffer responseBuffer, RequestHeader requestHeader) {
        Struct responseStruct = parseStructMaybeUpdateThrottleTimeMetrics(responseBuffer, requestHeader, null, 0);
        return AbstractResponse.parseResponse(requestHeader.apiKey(), responseStruct);
    }

    private static Struct parseStructMaybeUpdateThrottleTimeMetrics(ByteBuffer responseBuffer, RequestHeader requestHeader,
                                                                    Sensor throttleTimeSensor, long now) {
        //todo: 解析响应头
        ResponseHeader responseHeader = ResponseHeader.parse(responseBuffer);
        // Always expect the response version id to be the same as the request version id
       //todo: 解析响应体
        Struct responseBody = requestHeader.apiKey().parseResponse(requestHeader.apiVersion(), responseBuffer);
        //todo: 验证请求头与响应头的correlationId 必须相等
        correlate(requestHeader, responseHeader);
        if (throttleTimeSensor != null && responseBody.hasField(CommonFields.THROTTLE_TIME_MS))
            throttleTimeSensor.record(responseBody.get(CommonFields.THROTTLE_TIME_MS), now);
        return responseBody;
    }

    /**
     * Post process disconnection of a node
     *
     * @param responses The list of responses to update
     * @param nodeId Id of the node to be disconnected
     * @param now The current time
     * @param disconnectState The state of the disconnected channel           
     */
    private void processDisconnection(List<ClientResponse> responses,
                                      String nodeId,
                                      long now,
                                      ChannelState disconnectState) {
        //todo； 修改连接状态
        connectionStates.disconnected(nodeId, now);
        apiVersions.remove(nodeId);
        nodesNeedingApiVersionsFetch.remove(nodeId);
        switch (disconnectState.state()) {
            case AUTHENTICATION_FAILED:
                AuthenticationException exception = disconnectState.exception();
                connectionStates.authenticationFailed(nodeId, now, exception);
                metadataUpdater.handleAuthenticationFailure(exception);
                log.error("Connection to node {} ({}) failed authentication due to: {}", nodeId, disconnectState.remoteAddress(), exception.getMessage());
                break;
            case AUTHENTICATE:
                // This warning applies to older brokers which don't provide feedback on authentication failures
                log.warn("Connection to node {} ({}) terminated during authentication. This may indicate " +
                        "that authentication failed due to invalid credentials.", nodeId, disconnectState.remoteAddress());
                break;
            case NOT_CONNECTED:
                log.warn("Connection to node {} ({}) could not be established. Broker may not be available.", nodeId, disconnectState.remoteAddress());
                break;
            default:
                break; // Disconnections in other states are logged at debug level in Selector
        }
        //todo：从inFlightRequests中清空超时的请求
        for (InFlightRequest request : this.inFlightRequests.clearAll(nodeId)) {
            log.trace("Cancelled request {} {} with correlation id {} due to node {} being disconnected",
                    request.header.apiKey(), request.request, request.header.correlationId(), nodeId);
            if (!request.isInternalRequest)
                //todo: 对这些请求进行处理，它是自己封装了一个响应，响应中没有响应消息为null, 失去连接的标识为true
                responses.add(request.disconnected(now, disconnectState.exception()));
            else if (request.header.apiKey() == ApiKeys.METADATA)
                //todo: 修改元数据更新标识，需要更新元数据
                metadataUpdater.handleDisconnection(request.destination);
        }
    }

    /**
     * Iterate over all the inflight requests and expire any requests that have exceeded the configured requestTimeout.
     * The connection to the node associated with the request will be terminated and will be treated as a disconnection.
     *
     * @param responses The list of responses to update
     * @param now The current time
     */
    private void handleTimedOutRequests(List<ClientResponse> responses, long now) {
        //todo: 从inFlightRequests缓存中获取所有超时的节点信息
        List<String> nodeIds = this.inFlightRequests.nodesWithTimedOutRequests(now);
        //todo: 遍历节点
        for (String nodeId : nodeIds) {
            // close connection to the node
            //todo: 关闭节点连接
            this.selector.close(nodeId);
            log.debug("Disconnecting from node {} due to request timeout.", nodeId);
            //todo： 创建一个disconnection类型的clientResponse对象添加到本地集合中
            processDisconnection(responses, nodeId, now, ChannelState.LOCAL_CLOSE);
        }

        // we disconnected, so we should probably refresh our metadata
        if (!nodeIds.isEmpty())
            //todo: 标记需要更新本地缓存的集群元数据信息
            metadataUpdater.requestUpdate();
    }

    private void handleAbortedSends(List<ClientResponse> responses) {
        responses.addAll(abortedSends);
        abortedSends.clear();
    }

    /**
     * Handle any completed request send. In particular if no response is expected consider the request complete.
     *
     * @param responses The list of responses to update
     * @param now The current time
     */
    private void handleCompletedSends(List<ClientResponse> responses, long now) {
        // if no response is expected then when the send is completed, return it
        //todo: 遍历所有的发送完成的send对象
        for (Send send : this.selector.completedSends()) {
            //todo: 找出最近一次在inFlightRequests中发送的请求信息
            InFlightRequest request = this.inFlightRequests.lastSent(send.destination());
            //todo: 发送成功，但是不期望服务端响应的请求
            if (!request.expectResponse) {
                //todo: 从inFlightRequests中移除掉发送成功的send对象
                this.inFlightRequests.completeLastSent(send.destination());
                //todo: 封装一个响应到本地响应队列中
                responses.add(request.completed(null, now));
            }
        }
    }

    /**
     * If a response from a node includes a non-zero throttle delay and client-side throttling has been enabled for
     * the connection to the node, throttle the connection for the specified delay.
     *
     * @param response the response
     * @param apiVersion the API version of the response
     * @param nodeId the id of the node
     * @param now The current time
     */
    private void maybeThrottle(AbstractResponse response, short apiVersion, String nodeId, long now) {
        int throttleTimeMs = response.throttleTimeMs();
        if (throttleTimeMs > 0 && response.shouldClientThrottle(apiVersion)) {
            connectionStates.throttle(nodeId, now + throttleTimeMs);
            log.trace("Connection to node {} is throttled for {} ms until timestamp {}", nodeId, throttleTimeMs,
                      now + throttleTimeMs);
        }
    }

    /**
     * Handle any completed receives and update the response list with the responses received.
     *
     * @param responses The list of responses to update
     * @param now The current time
     */
    //todo: 获取服务端的响应，然后按照响应分类处理
    private void handleCompletedReceives(List<ClientResponse> responses, long now) {
        //todo: 从completedReceives中遍历所有接受到的响应，completedReceives中的信息是在上一层的selector.poll中添加进去的
        for (NetworkReceive receive : this.selector.completedReceives()) {
            //获取响应的节点id
            String source = receive.source();
            //从inFlightRequests中获取缓存的request对象
            InFlightRequest req = inFlightRequests.completeNext(source);
            //解析响应信息，验证响应头，生成Struct对象
            Struct responseStruct = parseStructMaybeUpdateThrottleTimeMetrics(receive.payload(), req.header,
                throttleTimeSensor, now);
            //打印日志
            if (log.isTraceEnabled()) {
                log.trace("Completed receive from node {} for {} with correlation id {}, received {}", req.destination,
                    req.header.apiKey(), req.header.correlationId(), responseStruct);
            }
            // If the received response includes a throttle delay, throttle the connection.
            //生成响应体
            AbstractResponse body = AbstractResponse.parseResponse(req.header.apiKey(), responseStruct);
            maybeThrottle(body, req.header.apiVersion(), req.destination, now);
            //todo: 如果是元数据请求的响应
            if (req.isInternalRequest && body instanceof MetadataResponse)
                //处理metadata的更新响应信息
                metadataUpdater.handleCompletedMetadataResponse(req.header, now, (MetadataResponse) body);
            //todo: 如果是版本协调的响应
            else if (req.isInternalRequest && body instanceof ApiVersionsResponse)
                handleApiVersionsResponse(responses, req, now, (ApiVersionsResponse) body);
            else
                //todo: 其他的响应，就添加响应到本地的响应队列中
                responses.add(req.completed(body, now));
        }
    }

    private void handleApiVersionsResponse(List<ClientResponse> responses,
                                           InFlightRequest req, long now, ApiVersionsResponse apiVersionsResponse) {
        //目标节点
        final String node = req.destination;
        // 判断响应和版本是否协调
        if (apiVersionsResponse.error() != Errors.NONE) {

            //异常处理
            if (req.request.version() == 0 || apiVersionsResponse.error() != Errors.UNSUPPORTED_VERSION) {
                log.warn("Received error {} from node {} when making an ApiVersionsRequest with correlation id {}. Disconnecting.",
                        apiVersionsResponse.error(), node, req.header.correlationId());
                this.selector.close(node);
                processDisconnection(responses, node, now, ChannelState.LOCAL_CLOSE);
            } else {
                nodesNeedingApiVersionsFetch.put(node, new ApiVersionsRequest.Builder((short) 0));
            }
            return;
        }
        NodeApiVersions nodeVersionInfo = new NodeApiVersions(apiVersionsResponse.apiVersions());
        //更新版本
        apiVersions.update(node, nodeVersionInfo);
        //连接状态更新为ready，可以正常发送请求了
        this.connectionStates.ready(node);
        log.debug("Recorded API versions for node {}: {}", node, nodeVersionInfo);
    }

    /**
     * Handle any disconnected connections
     *
     * @param responses The list of responses that completed with the disconnection
     * @param now The current time
     */
    private void handleDisconnections(List<ClientResponse> responses, long now) {
        //todo: 获取断开连接的节点id集合，更新连接状态为DISCONNECTED
        for (Map.Entry<String, ChannelState> entry : this.selector.disconnected().entrySet()) {
            String node = entry.getKey();
            log.debug("Node {} disconnected.", node);
            processDisconnection(responses, node, now, entry.getValue());
        }
        // we got a disconnect so we should probably refresh our metadata and see if that broker is dead

        if (this.selector.disconnected().size() > 0)
            //发现有断开的连接后，需要更新本地缓存的节点元数据信息
            metadataUpdater.requestUpdate();
    }

    /**
     * Record any newly completed connections
     *
     * 处理新建立的那些连接
     */
    private void handleConnections() {
        //todo: 获取连接正常的节点集合
        for (String node : this.selector.connected()) {
            // We are now connected.  Node that we might not still be able to send requests. For instance,
            // if SSL is enabled, the SSL handshake happens after the connection is established.
            // Therefore, it is still necessary to check isChannelReady before attempting to send on this
            // connection.
            //TODO: 如果当前节点是第一次建立连接
            if (discoverBrokerVersions) {
                //todo:获取该节点支持的API版本信息，并且把连接状态置为CHECKING_API_VERSIONS
                this.connectionStates.checkingApiVersions(node);
                // 将请求保存到nodesNeedingApiVersionsFetch集合里
                nodesNeedingApiVersionsFetch.put(node, new ApiVersionsRequest.Builder());
                log.debug("Completed connection to node {}. Fetching API versions.", node);
            } else {
                //todo: 如果不第一次建立的连接节点，更新连接状态为READY
                this.connectionStates.ready(node);
                log.debug("Completed connection to node {}. Ready.", node);
            }
        }
    }

    //发送版本协调请求
    private void handleInitiateApiVersionRequests(long now) {
        //todo: 获取所有需要获取支持的api版本信息的集合
        Iterator<Map.Entry<String, ApiVersionsRequest.Builder>> iter = nodesNeedingApiVersionsFetch.entrySet().iterator();
        //遍历
        while (iter.hasNext()) {
            Map.Entry<String, ApiVersionsRequest.Builder> entry = iter.next();
            String node = entry.getKey();
            // 判断是否允许发送请求
            if (selector.isChannelReady(node) && inFlightRequests.canSendMore(node)) {
                log.debug("Initiating API versions fetch from node {}.", node);
                ApiVersionsRequest.Builder apiVersionRequestBuilder = entry.getValue();
                //封装获取支持api版本的请求
                ClientRequest clientRequest = newClientRequest(node, apiVersionRequestBuilder, now, true);
                //发送请求
                doSend(clientRequest, true, now);
                iter.remove();
            }
        }
    }

    /**
     * Validate that the response corresponds to the request we expect or else explode
     */
    private static void correlate(RequestHeader requestHeader, ResponseHeader responseHeader) {
        if (requestHeader.correlationId() != responseHeader.correlationId())
            throw new IllegalStateException("Correlation id for response (" + responseHeader.correlationId()
                    + ") does not match request (" + requestHeader.correlationId() + "), request header: " + requestHeader);
    }

    /**
     * Initiate a connection to the given node
     */
    private void initiateConnect(Node node, long now) {
        //todo: 获取节点id
        String nodeConnectionId = node.idString();
        try {
            //todo: 更新节点连接状态为连接中CONNECTING
            this.connectionStates.connecting(nodeConnectionId, now, node.host(), clientDnsLookup);
            InetAddress address = this.connectionStates.currentAddress(nodeConnectionId);
            log.debug("Initiating connection to node {} using address {}", node, address);
            //todo:  尝试与节点建立socket网络连接
            selector.connect(nodeConnectionId,
                    new InetSocketAddress(address, node.port()),
                    this.socketSendBuffer,
                    this.socketReceiveBuffer);
        } catch (IOException e) {
            /* attempt failed, we'll try again after the backoff */
            //出现异常把缓存中的连接状态置为DISCONNECTED
            connectionStates.disconnected(nodeConnectionId, now);
            /* maybe the problem is our metadata, update it */
            //标记需要更新集群元数据，可能由于元数据导致建立网络连接失败，那么更新元数据
            metadataUpdater.requestUpdate();
            log.warn("Error connecting to node {}", node, e);
        }
    }

    class DefaultMetadataUpdater implements MetadataUpdater {

        /* the current cluster metadata */
        //元数据
        private final Metadata metadata;

        /* true iff there is a metadata request that has been sent and for which we have not yet received a response */
       //标记是否已经发送了metadata请求，如果已经发送了就不用重复发送
        private boolean metadataFetchInProgress;

        DefaultMetadataUpdater(Metadata metadata) {
            this.metadata = metadata;
            this.metadataFetchInProgress = false;
        }

        @Override
        public List<Node> fetchNodes() {
            return metadata.fetch().nodes();
        }

        @Override
        public boolean isUpdateDue(long now) {
            return !this.metadataFetchInProgress && this.metadata.timeToNextUpdate(now) == 0;
        }

        @Override
        public long maybeUpdate(long now) {
            // should we update our metadata?
            long timeToNextMetadataUpdate = metadata.timeToNextUpdate(now);
            long waitForMetadataFetch = this.metadataFetchInProgress ? defaultRequestTimeoutMs : 0;

            long metadataTimeout = Math.max(timeToNextMetadataUpdate, waitForMetadataFetch);

            if (metadataTimeout > 0) {
                return metadataTimeout;
            }

            // Beware that the behavior of this method and the computation of timeouts for poll() are
            // highly dependent on the behavior of leastLoadedNode.
            Node node = leastLoadedNode(now);
            if (node == null) {
                log.debug("Give up sending metadata request since no node is available");
                return reconnectBackoffMs;
            }
            //todo: 这里会封装请求
            return maybeUpdate(now, node);
        }

        @Override
        public void handleDisconnection(String destination) {
            Cluster cluster = metadata.fetch();
            // 'processDisconnection' generates warnings for misconfigured bootstrap server configuration
            // resulting in 'Connection Refused' and misconfigured security resulting in authentication failures.
            // The warning below handles the case where connection to a broker was established, but was disconnected
            // before metadata could be obtained.
            if (cluster.isBootstrapConfigured()) {
                int nodeId = Integer.parseInt(destination);
                Node node = cluster.nodeById(nodeId);
                if (node != null)
                    log.warn("Bootstrap broker {} disconnected", node);
            }

            //todo: 标识为false，
            metadataFetchInProgress = false;
        }

        @Override
        public void handleAuthenticationFailure(AuthenticationException exception) {
            metadataFetchInProgress = false;
            if (metadata.updateRequested())
                metadata.failedUpdate(time.milliseconds(), exception);
        }

        @Override
        public void handleCompletedMetadataResponse(RequestHeader requestHeader, long now, MetadataResponse response) {
            this.metadataFetchInProgress = false;
            //todo: 从响应中获取到集群的元数据信息
            Cluster cluster = response.cluster();

            // If any partition has leader with missing listeners, log a few for diagnosing broker configuration
            // issues. This could be a transient issue if listeners were added dynamically to brokers.
            List<TopicPartition> missingListenerPartitions = response.topicMetadata().stream().flatMap(topicMetadata ->
                topicMetadata.partitionMetadata().stream()
                    .filter(partitionMetadata -> partitionMetadata.error() == Errors.LISTENER_NOT_FOUND)
                    .map(partitionMetadata -> new TopicPartition(topicMetadata.topic(), partitionMetadata.partition())))
                .collect(Collectors.toList());
            if (!missingListenerPartitions.isEmpty()) {
                int count = missingListenerPartitions.size();
                log.warn("{} partitions have leader brokers without a matching listener, including {}",
                        count, missingListenerPartitions.subList(0, Math.min(10, count)));
            }

            // check if any topics metadata failed to get updated
            Map<String, Errors> errors = response.errors();
            //todo:响应中有异常
            if (!errors.isEmpty())
                log.warn("Error while fetching metadata with correlation id {} : {}", requestHeader.correlationId(), errors);

            // don't update the cluster if there are no valid nodes...the topic we want may still be in the process of being
            // created which means we will get errors and no nodes until it exists
            //todo: 如果集群的元数据信息存在
            if (cluster.nodes().size() > 0) {
                //todo： 更新MetaData集群的元数据信息
                this.metadata.update(cluster, response.unavailableTopics(), now);
            } else {
                log.trace("Ignoring empty metadata response with correlation id {}.", requestHeader.correlationId());
                this.metadata.failedUpdate(now, null);
            }
        }

        @Override
        public void requestUpdate() {
            this.metadata.requestUpdate();
        }

        @Override
        public void close() {
            this.metadata.close();
        }

        /**
         * Return true if there's at least one connection establishment is currently underway
         */
        private boolean isAnyNodeConnecting() {
            for (Node node : fetchNodes()) {
                if (connectionStates.isConnecting(node.idString())) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Add a metadata request to the list of sends if we can make one
         */
        private long maybeUpdate(long now, Node node) {
            //todo: 获取目标主机
            String nodeConnectionId = node.idString();
            //todo: 判断网络连接是否建立好了
            if (canSendRequest(nodeConnectionId, now)) {
                this.metadataFetchInProgress = true;
                MetadataRequest.Builder metadataRequest;
                if (metadata.needMetadataForAllTopics())
                    //获取所有topic的元数据信息
                    //但是我们一般获取元数据的时候，只获取自己要发送消息的topic的元数据信息
                    metadataRequest = MetadataRequest.Builder.allTopics();
                else
                    //默认走的是这个方法
                    //拉取我们发送消息的topic的方法
                    metadataRequest = new MetadataRequest.Builder(new ArrayList<>(metadata.topics()),
                            metadata.allowAutoTopicCreation());


                log.debug("Sending metadata request {} to node {}", metadataRequest, node);
                //todo：创建了一个拉取元数据的请求
                sendInternalMetadataRequest(metadataRequest, nodeConnectionId, now);
                return defaultRequestTimeoutMs;
            }

            // If there's any connection establishment underway, wait until it completes. This prevents
            // the client from unnecessarily connecting to additional nodes while a previous connection
            // attempt has not been completed.
            if (isAnyNodeConnecting()) {
                // Strictly the timeout we should return here is "connect timeout", but as we don't
                // have such application level configuration, using reconnect backoff instead.
                return reconnectBackoffMs;
            }

            if (connectionStates.canConnect(nodeConnectionId, now)) {
                // we don't have a connection to this node right now, make one
                log.debug("Initialize connection to node {} for sending metadata request", node);
                initiateConnect(node, now);
                return reconnectBackoffMs;
            }

            // connected, but can't send more OR connecting
            // In either case, we just need to wait for a network event to let us know the selected
            // connection might be usable again.
            return Long.MAX_VALUE;
        }

    }

    @Override
    public ClientRequest newClientRequest(String nodeId,
                                          AbstractRequest.Builder<?> requestBuilder,
                                          long createdTimeMs,
                                          boolean expectResponse) {
        return newClientRequest(nodeId, requestBuilder, createdTimeMs, expectResponse, defaultRequestTimeoutMs, null);
    }

    @Override
    public ClientRequest newClientRequest(String nodeId,
                                          AbstractRequest.Builder<?> requestBuilder,
                                          long createdTimeMs,
                                          boolean expectResponse,
                                          int requestTimeoutMs,
                                          RequestCompletionHandler callback) {
        return new ClientRequest(nodeId, requestBuilder, correlation++, clientId, createdTimeMs, expectResponse,
                requestTimeoutMs, callback);
    }

    public boolean discoverBrokerVersions() {
        return discoverBrokerVersions;
    }

    static class InFlightRequest {
        //请求头
        final RequestHeader header;
        //请求地址
        final String destination;
        //回调函数
        final RequestCompletionHandler callback;
        //是否需要服务端返回响应
        final boolean expectResponse;
        //请求信息
        final AbstractRequest request;
        //发送前是否需要验证连接信息
        final boolean isInternalRequest; // used to flag requests which are initiated internally by NetworkClient
        //请求的序列化数据
        final Send send;
        //发送时间
        final long sendTimeMs;
        //请求创建时间
        final long createdTimeMs;
        //请求超时时间
        final long requestTimeoutMs;

        public InFlightRequest(ClientRequest clientRequest,
                               RequestHeader header,
                               boolean isInternalRequest,
                               AbstractRequest request,
                               Send send,
                               long sendTimeMs) {
            this(header,
                 clientRequest.requestTimeoutMs(),
                 clientRequest.createdTimeMs(),
                 clientRequest.destination(),
                 clientRequest.callback(),
                 clientRequest.expectResponse(),
                 isInternalRequest,
                 request,
                 send,
                 sendTimeMs);
        }

        public InFlightRequest(RequestHeader header,
                               int requestTimeoutMs,
                               long createdTimeMs,
                               String destination,
                               RequestCompletionHandler callback,
                               boolean expectResponse,
                               boolean isInternalRequest,
                               AbstractRequest request,
                               Send send,
                               long sendTimeMs) {
            this.header = header;
            this.requestTimeoutMs = requestTimeoutMs;
            this.createdTimeMs = createdTimeMs;
            this.destination = destination;
            this.callback = callback;
            this.expectResponse = expectResponse;
            this.isInternalRequest = isInternalRequest;
            this.request = request;
            this.send = send;
            this.sendTimeMs = sendTimeMs;
        }

        public ClientResponse completed(AbstractResponse response, long timeMs) {
            return new ClientResponse(header, callback, destination, createdTimeMs, timeMs,
                    false, null, null, response);
        }

        public ClientResponse disconnected(long timeMs, AuthenticationException authenticationException) {
            return new ClientResponse(header, callback, destination, createdTimeMs, timeMs,
                    true, null, authenticationException, null);
        }

        @Override
        public String toString() {
            return "InFlightRequest(header=" + header +
                    ", destination=" + destination +
                    ", expectResponse=" + expectResponse +
                    ", createdTimeMs=" + createdTimeMs +
                    ", sendTimeMs=" + sendTimeMs +
                    ", isInternalRequest=" + isInternalRequest +
                    ", request=" + request +
                    ", callback=" + callback +
                    ", send=" + send + ")";
        }
    }

}

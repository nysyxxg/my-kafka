package kafka.consumer;

import kafka.api.OffsetRequest;
import kafka.common.InvalidConfigException;
import kafka.utils.Utils;
import kafka.utils.ZKConfig;

import java.util.Properties;

public class ConsumerConfig extends ZKConfig {
    
    static int SocketTimeout = 30 * 1000;
    static int SocketBufferSize = 64 * 1024;
    int FetchSize = 300 * 1024;
    int MaxFetchSize = 10 * FetchSize;
    int BackoffIncrementMs = 1000;
    boolean AutoCommit = true;
    int AutoCommitInterval = 60 * 1000;
    int MaxQueuedChunks = 100;
    String AutoOffsetReset = OffsetRequest.SmallestTimeString;
    int ConsumerTimeoutMs = -1;
    String MirrorTopicsWhitelist = "";
    String MirrorTopicsBlacklist = "";
    int MirrorConsumerNumThreads = 1;
    
    String MirrorTopicsWhitelistProp = "mirror.topics.whitelist";
    String MirrorTopicsBlacklistProp = "mirror.topics.blacklist";
    String MirrorConsumerNumThreadsProp = "mirror.consumer.numthreads";
    private Properties props;
    
    String groupId;
    
    String consumerId;
    
    int socketTimeoutMs;
    
    int socketBufferSize;
    
    int fetchSize;
    
    int maxFetchSize;
    
    long backoffIncrementMs;
    
    Boolean autoCommit;
    
    long autoCommitIntervalMs;
    
    int maxQueuedChunks;
    
    String autoOffsetReset;
    
    int consumerTimeoutMs;
    
    public String mirrorTopicsWhitelist;
    
    public String mirrorTopicsBlackList;
    
    public static int mirrorConsumerNumThreads;
    
    public ConsumerConfig(Properties props) {
        super(props);
        this.props = props;
        
        groupId = Utils.getString(props, "groupid");
        
        consumerId = getConsumerId();
        
        socketTimeoutMs = Utils.getInt(props, "socket.timeout.ms", SocketTimeout);
        
        socketBufferSize = Utils.getInt(props, "socket.buffersize", SocketBufferSize);
        
        fetchSize = Utils.getInt(props, "fetch.size", FetchSize);
        
        maxFetchSize = fetchSize * 10;
        
        backoffIncrementMs = Long.valueOf(Utils.getInt(props, "backoff.increment.ms", BackoffIncrementMs));
        
        autoCommit = Utils.getBoolean(props, "autocommit.enable", AutoCommit);
        
        autoCommitIntervalMs = Long.valueOf(Utils.getInt(props, "autocommit.interval.ms", AutoCommitInterval));
        
        maxQueuedChunks = Utils.getInt(props, "queuedchunks.max", MaxQueuedChunks);
    
        /* what to do if an offset is out of range.
       smallest : automatically reset the offset to the smallest offset
       largest : automatically reset the offset to the largest offset
       anything else: throw exception to the consumer */
        autoOffsetReset = Utils.getString(props, "autooffset.reset", AutoOffsetReset);
        
        /** throw a timeout exception to the consumer if no message is available for consumption after the specified interval */
        consumerTimeoutMs = Utils.getInt(props, "consumer.timeout.ms", ConsumerTimeoutMs);
        
        /** Whitelist of topics for this mirror's embedded consumer to consume. At
         *  most one of whitelist/blacklist may be specified. */
        mirrorTopicsWhitelist = Utils.getString(props, MirrorTopicsWhitelistProp, MirrorTopicsWhitelist);
        
        /** Topics to skip mirroring. At most one of whitelist/blacklist may be specified */
        mirrorTopicsBlackList = Utils.getString(props, MirrorTopicsBlacklistProp, MirrorTopicsBlacklist);
        
        mirrorConsumerNumThreads = Utils.getInt(props, MirrorConsumerNumThreadsProp, MirrorConsumerNumThreads);
        
        if (!mirrorTopicsWhitelist.isEmpty() && !mirrorTopicsBlackList.isEmpty()) {
            throw new InvalidConfigException("The embedded consumer's mirror topics configuration can only contain one of blacklist or whitelist");
        }
        
    }
    
    public int getSocketTimeout() {
        return SocketTimeout;
    }
    
    public void setSocketTimeout(int socketTimeout) {
        SocketTimeout = socketTimeout;
    }
    
    public int getSocketBufferSize() {
        return SocketBufferSize;
    }
    
    public void setSocketBufferSize(int socketBufferSize) {
        SocketBufferSize = socketBufferSize;
    }
    
    public int getFetchSize() {
        return FetchSize;
    }
    
    public void setFetchSize(int fetchSize) {
        FetchSize = fetchSize;
    }
    
    public int getMaxFetchSize() {
        return MaxFetchSize;
    }
    
    public void setMaxFetchSize(int maxFetchSize) {
        MaxFetchSize = maxFetchSize;
    }
    
    public int getBackoffIncrementMs() {
        return BackoffIncrementMs;
    }
    
    public void setBackoffIncrementMs(Long backoffIncrementMs) {
        this.backoffIncrementMs = backoffIncrementMs;
    }
    
    public Boolean getAutoCommit() {
        return autoCommit;
    }
    
    public void setAutoCommit(Boolean autoCommit) {
        this.autoCommit = autoCommit;
    }
    
    public Long getAutoCommitIntervalMs() {
        return autoCommitIntervalMs;
    }
    
    public void setAutoCommitIntervalMs(Long autoCommitIntervalMs) {
        this.autoCommitIntervalMs = autoCommitIntervalMs;
    }
    
    public void setBackoffIncrementMs(int backoffIncrementMs) {
        BackoffIncrementMs = backoffIncrementMs;
    }
    
    public boolean isAutoCommit() {
        return AutoCommit;
    }
    
    public void setAutoCommit(boolean autoCommit) {
        AutoCommit = autoCommit;
    }
    
    public int getAutoCommitInterval() {
        return AutoCommitInterval;
    }
    
    public void setAutoCommitInterval(int autoCommitInterval) {
        AutoCommitInterval = autoCommitInterval;
    }
    
    public int getMaxQueuedChunks() {
        return MaxQueuedChunks;
    }
    
    public void setMaxQueuedChunks(int maxQueuedChunks) {
        MaxQueuedChunks = maxQueuedChunks;
    }
    
    public String getAutoOffsetReset() {
        return AutoOffsetReset;
    }
    
    public void setAutoOffsetReset(String autoOffsetReset) {
        AutoOffsetReset = autoOffsetReset;
    }
    
    public int getConsumerTimeoutMs() {
        return ConsumerTimeoutMs;
    }
    
    public void setConsumerTimeoutMs(int consumerTimeoutMs) {
        ConsumerTimeoutMs = consumerTimeoutMs;
    }
    
    public String getMirrorTopicsWhitelist() {
        return MirrorTopicsWhitelist;
    }
    
    public void setMirrorTopicsWhitelist(String mirrorTopicsWhitelist) {
        MirrorTopicsWhitelist = mirrorTopicsWhitelist;
    }
    
    public String getMirrorTopicsBlackList() {
        return mirrorTopicsBlackList;
    }
    
    public void setMirrorTopicsBlackList(String mirrorTopicsBlackList) {
        this.mirrorTopicsBlackList = mirrorTopicsBlackList;
    }
    
    public String getMirrorTopicsBlacklist() {
        return MirrorTopicsBlacklist;
    }
    
    public void setMirrorTopicsBlacklist(String mirrorTopicsBlacklist) {
        MirrorTopicsBlacklist = mirrorTopicsBlacklist;
    }
    
    public int getMirrorConsumerNumThreads() {
        return MirrorConsumerNumThreads;
    }
    
    public void setMirrorConsumerNumThreads(int mirrorConsumerNumThreads) {
        MirrorConsumerNumThreads = mirrorConsumerNumThreads;
    }
    
    public String getMirrorTopicsWhitelistProp() {
        return MirrorTopicsWhitelistProp;
    }
    
    public void setMirrorTopicsWhitelistProp(String mirrorTopicsWhitelistProp) {
        MirrorTopicsWhitelistProp = mirrorTopicsWhitelistProp;
    }
    
    public String getMirrorTopicsBlacklistProp() {
        return MirrorTopicsBlacklistProp;
    }
    
    public void setMirrorTopicsBlacklistProp(String mirrorTopicsBlacklistProp) {
        MirrorTopicsBlacklistProp = mirrorTopicsBlacklistProp;
    }
    
    public String getMirrorConsumerNumThreadsProp() {
        return MirrorConsumerNumThreadsProp;
    }
    
    public void setMirrorConsumerNumThreadsProp(String mirrorConsumerNumThreadsProp) {
        MirrorConsumerNumThreadsProp = mirrorConsumerNumThreadsProp;
    }
    
    public Properties getProps() {
        return props;
    }
    
    public void setProps(Properties props) {
        this.props = props;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }
    
    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }
    
    public void setSocketTimeoutMs(int socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }
    
    public String getConsumerId() {
        if (Utils.getString(props, "consumerid", null) != null) {
            return Utils.getString(props, "consumerid");
        } else {
            return null;
        }
    }
    
}

package kafka.server;

import kafka.log.LogManager;
import kafka.network.SocketServer;
import kafka.network.SocketServerStats;
import kafka.utils.KafkaScheduler;
import kafka.utils.Mx4jLoader;
import kafka.utils.SystemTime;
import kafka.utils.Utils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaServer {
    
    private Logger logger = Logger.getLogger(KafkaServer.class);
    private KafkaConfig config;
    private String CLEAN_SHUTDOWN_FILE = ".kafka_cleanshutdown";
    private AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private CountDownLatch shutdownLatch = new CountDownLatch(1);
    private String statsMBeanName = "kafka:type=kafka.SocketServerStats";
    private SocketServer socketServer = null;
    private KafkaScheduler scheduler = new KafkaScheduler(1, "kafka-logcleaner-", false);
    private LogManager logManager = null;
    
    public KafkaServer(KafkaConfig serverConfig) {
        this.config = serverConfig;
    }
    
    public LogManager getLogManager() {
        return logManager;
    }
    
    SocketServerStats getStats() {
        return socketServer.stats;
    }
    
    
    public void startup() {
        try {
            logger.info("启动kafka的服务器: Starting Kafka server...");
            boolean needRecovery = true;
            File cleanShutDownFile = new File(new File(config.logDir), CLEAN_SHUTDOWN_FILE);
            if (cleanShutDownFile.exists()) {
                needRecovery = false;
                cleanShutDownFile.delete();
            }
            logManager = new LogManager(config,
                    scheduler,
                    new SystemTime(),
                    1000L * 60 * config.logCleanupIntervalMinutes,
                    1000L * 60 * 60 * config.logRetentionHours,
                    needRecovery);
            
            KafkaRequestHandlers handlers = new KafkaRequestHandlers(logManager);
            socketServer = new SocketServer(config.port,
                    config.numThreads,
                    config.monitoringPeriodSecs,
                    handlers,
                    config.maxSocketRequestSize);
            
            try {
                Utils.registerMBean(socketServer.stats, statsMBeanName);
            } catch (Exception e) {
                e.printStackTrace();
                logger.warn(e.getMessage(), e);
            }
            
            socketServer.startup();
            Mx4jLoader.maybeLoad();
            /**
             *  Registers this broker in ZK. After this, consumers can connect to broker.
             *  So this should happen after socket server start.
             */
            logManager.startup();
            logger.info("Server started.");
        } catch (Exception e) {
            logger.fatal("Fatal error during startup.", e);
            shutdown();
        }
        
    }
    
    public void shutdown() {
        boolean canShutdown = isShuttingDown.compareAndSet(false, true);
        if (canShutdown) {
            logger.info("Shutting down...");
            try {
                scheduler.shutdown();
                if (socketServer != null) {
                    socketServer.shutdown();
                }
                try {
                    Utils.unregisterMBean(statsMBeanName);
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.warn(e.getMessage(), e);
                }
                
                if (logManager != null) {
                    logManager.close();
                }
                
                File cleanShutDownFile = new File(new File(config.logDir), CLEAN_SHUTDOWN_FILE);
                cleanShutDownFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                logger.fatal(e);
                logger.fatal(Utils.stackTrace(e));
            }
            shutdownLatch.countDown();
            logger.info("shut down completed");
        }
        
    }
    
    public void awaitShutdown() {
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

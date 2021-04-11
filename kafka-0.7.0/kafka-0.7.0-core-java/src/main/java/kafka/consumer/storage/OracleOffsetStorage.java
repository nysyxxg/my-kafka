package kafka.consumer.storage;

import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class OracleOffsetStorage implements OffsetStorage {
    private Logger logger = Logger.getLogger(OracleOffsetStorage.class);
    private Object lock = new Object();
    
    private Connection connection;
    
    public OracleOffsetStorage(Connection connection) throws SQLException {
        this.connection = connection;
        try {
            this.connection.setAutoCommit(false);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public Long reserve(int node, String topic) {
        
        Long maybeOffset = selectExistingOffset(connection, node, topic);
        Long offset = null;
        if (maybeOffset == offset) {
            return offset;
        } else {
            maybeInsertZeroOffset(connection, node, topic);
            offset = selectExistingOffset(connection, node, topic);
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Reserved node " + node + " for topic '" + topic + " offset " + offset);
        }
        
        return offset;
    }
    
    
    @Override
    public void commit(int node, String topic, Long offset) {
        boolean success = false;
        try {
            updateOffset(connection, node, topic, offset);
            success = true;
        } finally {
            commitOrRollback(connection, success);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Updated node " + node + " for topic '" + topic + "' to " + offset);
        }
    }
    
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
            logger.error(e.getMessage(), e);
        }
    }
    
    
    private Boolean maybeInsertZeroOffset(Connection connection, int node, String topic) {
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement("insert into kafka_offsets (node, topic, offset) select ?, ?, 0 from dual where not exists (select null from kafka_offsets where node = ? and topic = ?)  ");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        try {
            stmt.setInt(1, node);
            stmt.setString(2, topic);
            stmt.setInt(3, node);
            stmt.setString(4, topic);
            int updated = stmt.executeUpdate();
            if (updated > 1) {
                throw new IllegalStateException("More than one key updated by primary key!");
            } else {
                return updated == 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    
    private Long selectExistingOffset(Connection connection, int node, String topic) {
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement("select offset from kafka_offsets    where node = ? and topic = ?  for update ");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        ResultSet results = null;
        try {
            stmt.setInt(1, node);
            stmt.setString(2, topic);
            results = stmt.executeQuery();
            if (!results.next()) {
                return null;
            } else {
                long offset = results.getLong("offset");
                if (results.next()) {
                    throw new IllegalStateException("More than one entry for primary key!");
                }
                return offset;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(stmt);
            close(results);
        }
        return null;
    }
    
    private void updateOffset(Connection connection,
                              int node,
                              String topic,
                              Long newOffset) {
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement("update kafka_offsets set offset = ? where node = ? and topic = ?");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            stmt.setLong(1, newOffset);
            stmt.setInt(2, node);
            stmt.setString(3, topic);
            int updated = stmt.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException("Unexpected number of keys updated: " + updated);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(stmt);
        }
    }
    
    
    private void commitOrRollback(Connection connection, Boolean commit) {
        if (connection != null) {
            if (commit) {
                try {
                    connection.commit();
                } catch (SQLException e) {
                    e.printStackTrace();
                    logger.error(e.getMessage(), e);
                }
            } else {
                try {
                    connection.rollback();
                } catch (SQLException e) {
                    e.printStackTrace();
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }
    
    private void close(ResultSet rs) {
        if (rs != null)
            try {
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
                logger.error(e.getMessage(), e);
            }
    }
    
    private void close(PreparedStatement stmt) {
        if (stmt != null)
            try {
                stmt.close();
            } catch (SQLException e) {
                e.printStackTrace();
                logger.error(e.getMessage(), e);
            }
    }
    
    private void close(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
                logger.error(e.getMessage(), e);
            }
        }
        
    }
    
}

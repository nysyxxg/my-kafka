package com.lun.kafka.vo;

public class DataVo {
    private String dbType;
    private String database;
    private String tableName;
    private String uuId;
    private String msgId;
    private String dataVer;
    private String dataStamp;
    private Object data;
    
    public String getDbType() {
        return dbType;
    }
    
    public void setDbType(String dbType) {
        this.dbType = dbType;
    }
    
    public String getDatabase() {
        return database;
    }
    
    public void setDatabase(String database) {
        this.database = database;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    public String getUuId() {
        return uuId;
    }
    
    public void setUuId(String uuId) {
        this.uuId = uuId;
    }
    
    public String getMsgId() {
        return msgId;
    }
    
    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }
    
    public String getDataVer() {
        return dataVer;
    }
    
    public void setDataVer(String dataVer) {
        this.dataVer = dataVer;
    }
    
    public String getDataStamp() {
        return dataStamp;
    }
    
    public void setDataStamp(String dataStamp) {
        this.dataStamp = dataStamp;
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
}

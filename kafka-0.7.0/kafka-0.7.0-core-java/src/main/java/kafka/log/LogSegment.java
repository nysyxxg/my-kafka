package kafka.log;

import kafka.message.FileMessageSet;
import kafka.utils.Range;

import java.io.File;

public class LogSegment extends Range {
    File file;
    FileMessageSet messageSet;
    static Long start;
    volatile boolean deleted = false;
    public Long size;
    public LogSegment(File file, FileMessageSet messageSet, Long start) {
        this.file = file;
        this.messageSet = messageSet;
        this.start = start;
        this.size = messageSet.highWaterMark();;
    }
    
    public File getFile() {
        return file;
    }
    
    public void setFile(File file) {
        this.file = file;
    }
    
    public FileMessageSet getMessageSet() {
        return messageSet;
    }
    
    public void setMessageSet(FileMessageSet messageSet) {
        this.messageSet = messageSet;
    }
    
    public static Long getStart() {
        return start;
    }
    
    public static void setStart(Long start) {
        LogSegment.start = start;
    }
    
    public boolean isDeleted() {
        return deleted;
    }
    
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    
    public Long getSize() {
        return size;
    }
    
    public void setSize(Long size) {
        this.size = size;
    }
    
    public String toString() {
        return "(file=" + file + ", start=" + start + ", size=" + size + ")";
    }
    
    
}

package kafka.log;

import kafka.message.FileMessageSet;
import kafka.utils.Range;

import java.io.File;

public class LogSegment extends Range {
    
    public File file;
    public FileMessageSet messageSet;
    public volatile boolean deleted = false;
    public Long start;
    public Long size;
    
    public LogSegment(File file, FileMessageSet messageSet, Long start) {
        this.file = file;
        this.messageSet = messageSet;
        this.start = start;
        this.size = messageSet.highWaterMark();
        System.out.println(this.getClass().getName() + "------------messageSet---highWaterMark---size---" + size);
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
    
    public Long getStart() {
        return start;
    }
    
    public void setStart(Long start) {
        this.start = start;
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
    
    @Override
    public Long start() {
        return start;
    }
    
    @Override
    public Long size() {
        return messageSet.highWaterMark();
    }
    
    public Boolean isEmpty() {
        return size == 0;
    }
    
    public Boolean contains(Long value) {
        if (start == 0 && value == start || (size > 0 && value >= start && value <= start + size - 1))
            return true;
        else
            return false;
    }
    
}

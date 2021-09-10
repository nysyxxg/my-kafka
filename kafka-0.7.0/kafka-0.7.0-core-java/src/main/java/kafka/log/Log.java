package kafka.log;

import kafka.api.OffsetRequest;
import kafka.common.InvalidMessageException;
import kafka.common.OffsetOutOfRangeException;
import kafka.message.FileMessageSet;
import kafka.message.MessageAndOffset;
import kafka.message.MessageSet;
import kafka.utils.Utils;
import org.apache.log4j.Logger;
import scala.Tuple2;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Log {
    
    private Logger logger = Logger.getLogger(Log.class);
    
    static String FileSuffix = ".kafka";
    
    String name;
    File dir;
    Long maxSize;
    int flushInterval;
    Boolean needRecovery;
    
    private Object lock = new Object();
    private AtomicInteger unflushed = new AtomicInteger(0);
    private AtomicLong lastflushedTime = new AtomicLong(System.currentTimeMillis());
    private SegmentList segments;
    private LogStats logStats = new LogStats(this);
    
    public Log() {
    }
    
    public Log(File dir, Long maxSize, int flushInterval, Boolean needRecovery) {
        this.dir = dir;
        this.maxSize = maxSize;
        this.flushInterval = flushInterval;
        this.needRecovery = needRecovery;
        this.name = dir.getName();
        try {
            this.segments = this.loadSegments();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        this.name = dir.getName();
        Utils.registerMBean(logStats, "kafka:type=kafka.logs." + dir.getName());
    }
    
    /**
     * 二分查找 offset
     *
     * @param ranges
     * @param value
     * @param arraySize
     * @return
     */
    public LogSegment findRange(LogSegment ranges[], Long value, int arraySize) {
        if (ranges.length < 1) {
            return null;
        }
        // check out of bounds
        if (value < ranges[0].start || value > ranges[arraySize - 1].start + ranges[arraySize - 1].size) {
            throw new OffsetOutOfRangeException("offset " + value + " is out of range");
        }
        // check at the end
        if (value == ranges[arraySize - 1].start + ranges[arraySize - 1].size) {
            return null;
        }
        int low = 0;
        int high = arraySize - 1;
        while (low <= high) {
            int mid = (high + low) / 2;
            LogSegment found = ranges[mid];
            if (found.contains(value)) {
                return found;
            } else if (value < found.start) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return null;
    }
    
    public LogSegment findRange(LogSegment ranges[], Long value) {
        return findRange(ranges, value, ranges.length);
    }
    
    public static String nameFromOffset(Long offset) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMinimumIntegerDigits(20);
        nf.setMaximumFractionDigits(0);
        nf.setGroupingUsed(false);
        return nf.format(offset) + Log.FileSuffix;
    }
    
    
    private SegmentList loadSegments() throws Throwable {
        // open all the segments read-only
        List<LogSegment> accum = new ArrayList<LogSegment>();
        File[] ls = dir.listFiles();
        if (ls != null) {
            for (File file : ls) {
                if (file.isFile() && file.toString().endsWith(Log.FileSuffix)) {
                    if (!file.canRead()) {
                        throw new IOException("Could not read file " + file);
                    }
                    String filename = file.getName();
                    Long start = Long.parseLong(filename.substring(0, filename.length() - Log.FileSuffix.length()));
                    FileMessageSet messageSet = null;
                    try {
                        messageSet = new FileMessageSet(file, false);
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                    accum.add(new LogSegment(file, messageSet, start));
                }
            }
        }
        
        if (accum.size() == 0) {
            // no existing segments, create a new mutable segment
            File newFile = new File(dir, Log.nameFromOffset(0L));
            FileMessageSet set = new FileMessageSet(newFile, true);
            accum.add(new LogSegment(newFile, set, 0L));
        } else {
            // there is at least one existing segment, validate and recover them/it
            // sort segments into ascending order for fast searching
            Collections.sort(accum, new Comparator<LogSegment>() {
                @Override
                public int compare(LogSegment s1, LogSegment s2) {
                    if (s1.start == s2.start) return 0;
                    else if (s1.start < s2.start) return -1;
                    else return 1;
                }
            });
            validateSegments(accum);
            
            //make the final section mutable and run recovery on it if necessary
            LogSegment last = accum.remove(accum.size() - 1);
            last.messageSet.close();
            logger.info("Loading the last segment " + last.file.getAbsolutePath() + " in mutable mode, recovery " + needRecovery);
            
            FileMessageSet fileMessageSet = new FileMessageSet(last.getFile(), true, new AtomicBoolean(needRecovery));
            LogSegment mutable = new LogSegment(last.file, fileMessageSet, last.start);
            accum.add(mutable);
        }
        
        return new SegmentList(accum);
//        return new SegmentList(accum.toArray(new LogSegment[accum.size()]));
    }
    
    
    private void validateSegments(List<LogSegment> segments) {
        synchronized (lock) {
            for (int i = 0; i < segments.size() - 1; i++) {
                LogSegment curr = segments.get(i);
                LogSegment next = segments.get(i + 1);
                if (curr.start + curr.size != next.start) ;
                throw new IllegalStateException("The following segments don't validate: " +
                        curr.file.getAbsolutePath() + ", " + next.file.getAbsolutePath());
            }
        }
    }
    
    int numberOfSegments() {
        return segments.getView().length;
    }
    
    public Long size() {
        Object[] objects = segments.getView();
        Long size = 0L;
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] instanceof LogSegment) {
                LogSegment logSegment = (LogSegment) objects[i];
                size = i + logSegment.size;  // 存在问题
            }
            
        }
        return size;// .foldLeft(0L)(_ + _.size);
    }
    
    public Long getHighwaterMark() {
        LogSegment[] objects = segments.getView();
        if (objects[objects.length - 1] instanceof LogSegment) {
            LogSegment logSegment = objects[objects.length - 1];
            return logSegment.messageSet.highWaterMark();
        }
        return null;
    }
    
    public void close() {
        synchronized (lock) {
            LogSegment[] logSegments = this.segments.getView();
            for (LogSegment seg : logSegments) {
                try {
                    seg.messageSet.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    public void append(MessageSet messages) {
        // validate the messages
        int numberOfMessages = 0;
        for (MessageAndOffset messageAndOffset : messages) {
            if (!messageAndOffset.message.isValid()) {
                throw new InvalidMessageException();
            }
            numberOfMessages += 1;
        }
        
        logStats.recordAppendedMessages(numberOfMessages);
        
        // they are valid, insert them in the log
        synchronized (lock) {
            LogSegment view[] = segments.view;
            LogSegment segment = view[view.length - 1];
            segment.messageSet.append(messages);
            maybeFlush(numberOfMessages);
            maybeRoll(segment);
        }
    }
    
    private void maybeFlush(int numberOfMessages) {
        if (unflushed.addAndGet(numberOfMessages) >= flushInterval) {
            flush();
        }
    }
    
    public void flush() {
        if (unflushed.get() == 0) {
            return;
        }
        
        synchronized (lock) {
            if (logger.isDebugEnabled()) {
                logger.debug("Flushing log '" + name + "' last flushed: " + getLastFlushedTime() + " current time: " + System.currentTimeMillis());
            }
            LogSegment view[] = segments.view;
            LogSegment lastsegment = view[view.length - 1];
            try {
                lastsegment.messageSet.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            unflushed.set(0);
            lastflushedTime.set(System.currentTimeMillis());
        }
    }
    
    public Long getLastFlushedTime() {
        return lastflushedTime.get();
    }
    
    private void maybeRoll(LogSegment segment) {
        if (segment.messageSet.sizeInBytes() > maxSize)
            roll();
    }
    
    public void roll() {
        synchronized (lock) {
            LogSegment view[] = segments.view;
            LogSegment lastsegment = view[view.length - 1];
            Long newOffset = nextAppendOffset();
            File newFile = new File(dir, Log.nameFromOffset(newOffset));
            if (logger.isDebugEnabled()) {
                logger.debug("Rolling log '" + name + "' to " + newFile.getName());
            }
            try {
                Thread.sleep(30 * 1000);
                System.out.println("-------------------开始追加日志文件--------------------------------------");
                segments.append(new LogSegment(newFile, new FileMessageSet(newFile, true), newOffset));
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }
    
    public Long nextAppendOffset() {
        flush();
        LogSegment view[] = segments.view;
        LogSegment lastsegment = view[view.length - 1];
        return lastsegment.start + lastsegment.size;
    }
    
    public String getTopicName() {
        return name.substring(0, name.lastIndexOf("-"));
    }
    
    public Long[] getOffsetsBefore(OffsetRequest request) {
        LogSegment segsArray[] = segments.view;
        Tuple2<Long, Long> offsetTimeArray[] = new Tuple2[10];
        LogSegment last = segsArray[segsArray.length - 1];
        if (last.size > 0) {
            offsetTimeArray = new Tuple2[segsArray.length + 1];
        } else {
            offsetTimeArray = new Tuple2[segsArray.length];
        }
        for (int i = 0; i < segsArray.length; i++) {
            offsetTimeArray[i] = new Tuple2(segsArray[i].start, segsArray[i].file.lastModified());
        }
        if (last.size > 0) {
            offsetTimeArray[segsArray.length] = new Tuple2(last.start + last.messageSet.highWaterMark(), System.currentTimeMillis());
        }
        int startIndex = -1;
        Long offset = request.offset;
        if (offset == OffsetRequest.LatestTime) {
            startIndex = offsetTimeArray.length - 1;
        } else if (offset == OffsetRequest.EarliestTime) {
            startIndex = 0;
        } else {
            boolean isFound = false;
            if (logger.isDebugEnabled()) {
                logger.debug("Offset time array = " + offsetTimeArray.toString());
            }
            startIndex = offsetTimeArray.length - 1;
            while (startIndex >= 0 && !isFound) {
                if (offsetTimeArray[startIndex]._2 <= request.offset)
                    isFound = true;
                else
                    startIndex -= 1;
            }
        }
        
        int retSize = Math.min(request.maxNumOffsets, (startIndex + 1));
        Long ret[] = new Long[retSize];
        for (int j = 0; j < retSize; j++) {
            ret[j] = offsetTimeArray[startIndex]._1;
            startIndex -= 1;
        }
        return ret;
    }
    
    
    List<LogSegment> markDeletedWhile(Long startMs, Long logCleanupThresholdMS) {
        synchronized (lock) {
            LogSegment view[] = segments.view;
            List<LogSegment> deletable = new ArrayList<>();
            for (int i = 0; i < view.length; i++) {
                LogSegment logSegment = view[i];
                boolean bl = isLogSegment(startMs, logSegment, logCleanupThresholdMS);
                if (!bl) {
                    break;
                } else {
                    deletable.add(logSegment);
                }
            }
            // takeWhile是从第一个元素开始，取满足条件的元素，直到不满足为止
            // val deletable = view.takeWhile(predicate);
            for (LogSegment seg : deletable) {
                seg.deleted = true;
            }
            int numToDelete = deletable.size();
            // if we are deleting everything, create a new empty segment
            if (numToDelete == view.length) {
                roll();
            }
            return segments.trunc(numToDelete);
        }
    }
    
    List<LogSegment> markDeletedWhileV2(long diff) {
        synchronized (lock) {
            LogSegment view[] = segments.view;
            List<LogSegment> deletable = new ArrayList<>();
            for (int i = 0; i < view.length; i++) {
                LogSegment logSegment = view[i];
                boolean bl = shouldDelete(logSegment, diff);
                if (!bl) {
                    break;
                } else {
                    deletable.add(logSegment);
                }
            }
            // takeWhile是从第一个元素开始，取满足条件的元素，直到不满足为止
            // val deletable = view.takeWhile(predicate);
            for (LogSegment seg : deletable) {
                seg.deleted = true;
            }
            int numToDelete = deletable.size();
            // if we are deleting everything, create a new empty segment
            if (numToDelete == view.length) {
                roll();
            }
            return segments.trunc(numToDelete);
        }
    }
    
    boolean shouldDelete(LogSegment segment, long diff) {
        if (diff - segment.size >= 0) {
            diff -= segment.size;
            return true;
        } else {
            return false;
        }
    }
    
    private boolean isLogSegment(Long startMs, LogSegment logSegment, Long logCleanupThresholdMS) {
        return startMs - logSegment.file.lastModified() > logCleanupThresholdMS;
    }
    
    
    public MessageSet read(Long offset, Long length) {
        LogSegment view[] = segments.view;
        LogSegment segment = findRange(view, offset, view.length);
        if (segment != null) {
            try {
                return segment.messageSet.read((offset - segment.start), length);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        } else {
            return MessageSet.Empty;
        }
        return null;
    }
    
    
}

package kafka.log;



import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SegmentList  {
    
    private List<LogSegment> seq;
    public AtomicReference<LogSegment[]> contents;
    public LogSegment view[];
    
    
    public SegmentList(List<LogSegment> seq) {
        this.seq = seq;
        LogSegment[] logSegments = new LogSegment[seq.size()];
        seq.toArray(logSegments);
        this.contents = new AtomicReference(logSegments);
        this.view = contents.get();
    }
    
    public void append(LogSegment... ts) {
        while (true) {
            LogSegment[] curr = contents.get();
            LogSegment[] updated =  new  LogSegment[curr.length + ts.length];
            System.arraycopy(curr, 0, updated, 0, curr.length);
            for (int i = 0; i < ts.length; i++) {
                updated[curr.length + i] = ts[i];
            }
            if (contents.compareAndSet(curr, updated)) {
                return;
            }
        }
    }
    
    
    public List<LogSegment> trunc(int newStart) {
        if (newStart < 0) {
            throw new IllegalArgumentException("Starting index must be positive.");
        }
        LogSegment[] deleted = null;
        boolean done = false;
        while (!done) {
            LogSegment[] curr = contents.get();
            int newLength = Math.max(curr.length - newStart, 0);
            LogSegment[] updated = new LogSegment[newLength];
            System.arraycopy(curr, Math.min(newStart, curr.length - 1), updated, 0, newLength);
            if (contents.compareAndSet(curr, updated)) {
                deleted = new LogSegment[newStart];
                System.arraycopy(curr, 0, deleted, 0, curr.length - newLength);
                done = true;
            }
        }
        return Arrays.asList(deleted);
    }
    
    
    public LogSegment[] getView() {
        return view;
    }
    
    public void setView(LogSegment[] view) {
        this.view = view;
    }
    
    public String toString() {
        return view.toString();
    }
    
}



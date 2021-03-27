package kafka.common;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class ErrorMapping {
    
    private ByteBuffer EmptyByteBuffer = ByteBuffer.allocate(0);
    
    static int UnknownCode = -1;
    public static int NoError = 0;
    static int OffsetOutOfRangeCode = 1;
    static int InvalidMessageCode = 2;
    static int WrongPartitionCode = 3;
    static int InvalidFetchSizeCode = 4;
    
    private static Map<Throwable, Integer> exceptionToCode = new HashMap<>();
    private static Map<Integer, Throwable> codeToException = new HashMap<>();
    
    static {
        exceptionToCode.put(new OffsetOutOfRangeException(), OffsetOutOfRangeCode);
        exceptionToCode.put(new InvalidMessageException(), InvalidMessageCode);
        exceptionToCode.put(new InvalidPartitionException(), WrongPartitionCode);
        exceptionToCode.put(new InvalidMessageSizeException(), InvalidFetchSizeCode);
        exceptionToCode.put(new UnknownException(), UnknownCode);
        
        codeToException.put(OffsetOutOfRangeCode, new OffsetOutOfRangeException());
        codeToException.put(InvalidMessageCode, new InvalidMessageException());
        codeToException.put(WrongPartitionCode, new InvalidPartitionException());
        codeToException.put(InvalidFetchSizeCode, new InvalidMessageSizeException());
        codeToException.put(UnknownCode, new UnknownException());
    }
    
    
    static int codeFor(Throwable exception) {
        return exceptionToCode.get(exception);
    }
    
    public static  void maybeThrowException(int code) throws Throwable {
        if (code != 0)
            throw codeToException.get(code);
    }
    
}

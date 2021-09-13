package kafka.examples.nio;

import java.nio.*;
import java.util.*;

/**
 * java.nio.ByteBuffer类的slice()方法用于创建一个新的字节缓冲区，其内容是给定缓冲区内容的共享子序列。
 * <p>
 * 新缓冲区的内容将从该缓冲区的当前位置开始。对该缓冲区内容的更改将在新缓冲区中可见，反之亦然。这两个缓冲区的位置，限制和标记值将是独立的。
 * <p>
 * 新缓冲区的位置将为零，其容量和限制将为该缓冲区中剩余的浮点数，并且其标记将不确定。当且仅当该缓冲区是直接缓冲区时，新缓冲区才是直接缓冲区；当且仅当该缓冲区是只读缓冲区时，新缓冲区才是只读缓冲区。
 */
public class GFG {
    
    public static void main(String[] args) {
        
        // Declaring the capacity of the ByteBuffer 
        int capacity = 5;
        
        // Creating the ByteBuffer 
        try {
            
            // creating object of ByteBuffer 
            // and allocating size capacity 
            ByteBuffer bb1 = ByteBuffer.allocate(capacity);
            
            // putting the value in ByteBuffer 
            bb1.put((byte) 10);
            bb1.put((byte) 20);
            
            // print the ByteBuffer 
            System.out.println("Original ByteBuffer: " + Arrays.toString(bb1.array()));
            
            // print the ByteBuffer position 
            System.out.println("\nposition:  " + bb1.position());
            
            // print the ByteBuffer capacity 
            System.out.println("\ncapacity:  " + bb1.capacity());
    
            bb1.flip();
            int len = 2;
            byte[] dst = new byte[len];
            ByteBuffer byteBuffer =  bb1.get(dst);
            
            // Creating a shared subsequence buffer 
            // of given ByteBuffer 
            // using slice() method 
            ByteBuffer bb2 = bb1.slice();
            
            // print the shared subsequance buffer 
            System.out.println("\nshared subsequance ByteBuffer  bb2 : " + Arrays.toString(bb2.array()));
            
            // print the ByteBuffer position 
            System.out.println("\nposition:  " + bb2.position());
            
            // print the ByteBuffer capacity 
            System.out.println("\ncapacity:  " + bb2.capacity());
        } catch (IllegalArgumentException e) {
            
            System.out.println("IllegalArgumentException catched");
        } catch (ReadOnlyBufferException e) {
            
            System.out.println("ReadOnlyBufferException catched");
        }
    }
}
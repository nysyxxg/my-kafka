package org.apache.kafka.clients.producer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * ArrayDeque
 * 是Deque接口的一个实现，使用了可变数组，所以没有容量上的限制。
 * 同时，ArrayDeque是线程不安全的，在没有外部同步的情况下，不能再多线程环境下使用。
 * ArrayDeque是Deque的实现类，可以作为栈来使用，效率高于Stack；
 * 也可以作为队列来使用，效率高于LinkedList。
 * 需要注意的是，ArrayDeque不支持null值。
 * <p>
 * 重点：
 * 底层通过循环数组实现 俩个重要属性 head tail
 * 不能添加null值，不然会报空指针
 * 每次扩容都是2的n次方
 * 可以实现普通队列先进先出排序，也可以实现栈先进后出的排序
 * 特别留意，它里面通过二进制方式判断数组是否已满
 * (tail = (tail + 1) & (elements.length - 1)) == head
 * 注意操作插入和移除时，有Throws exception 和 return Special value 俩种情况
 * <p>
 * ArrayDeque 既可实现普通队列 FIFO 先进先出，也可实现栈的先进后出功能
 * 其实也好理解，因为ArrayDeque实现了双端的操作 所以使得这一切都成为了可能
 * <p>
 * 先进先出
 * addFirst() 方法 配合pollLast() 方法
 * addLast() 方法 配合 pollFirst()方法
 * <p>
 * 先进后出（栈）
 * addFirst() 方法配合 pollFirst()方法
 * addLast()方法配合pollLast()方法
 * ————————————————
 * 原文链接：https://blog.csdn.net/ted_cs/article/details/82926423
 */
public class ArrayDequeTest {
    // 用来模拟栈
    private Deque<Integer> data = new ArrayDeque<Integer>();
    
    public void push(Integer element) {
        data.addFirst(element);
    }
    
    public Integer pop() {
        return data.removeFirst();
    }
    
    public Integer peek() {
        return data.peekFirst();
    }
    
    public String toString() {
        return data.toString();
    }
    
    public static void main(String[] args) {
        ArrayDequeTest stack = new ArrayDequeTest();
        for (int i = 0; i < 5; i++) {
            stack.push(i);
        }
        System.out.println("After   pushing   5   elements:   " + stack);
       
        int m = stack.pop();
        System.out.println("Popped   element   =   " + m);
        System.out.println("After   popping   1   element   :   " + stack);
     
        int n = stack.peek();
        System.out.println("Peeked   element   =   " + n);
        System.out.println("After   peeking   1   element   :   " + stack);
    }
    
}

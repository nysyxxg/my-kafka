
import org.junit.Test;

import java.util.function.Consumer;

public class ForkJoinTest {

    @Test
    public void test() {
        //利用函数式接口Consumer的accept方法实现打印，Lambda表达式如下
        Consumer<Object> consumer = this::println;
        consumer.accept("jay");
        printlnJay(consumer);
    }

    private void printlnJay(Consumer<Object> consumer) {
        consumer.accept(11);
    }

    private void println(Object msg) {
        System.out.println(msg.toString());
    }

}
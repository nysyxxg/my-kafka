package kafka.producer;

import java.util.Random;

public class DefaultPartitioner<T>  implements Partitioner<T> {
    
    private Random random = new java.util.Random();
    
    @Override
    public int partition(T key, int numPartitions) {
        if(key == null)
            return  random.nextInt(numPartitions);
        else
            return  Math.abs(key.hashCode()) % numPartitions;
    }
}

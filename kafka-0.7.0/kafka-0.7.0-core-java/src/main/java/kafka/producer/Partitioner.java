package kafka.producer;

public interface Partitioner<T> {
    int partition(T key, int numPartitions);
}

package kafka.cluster;

public class Partition  implements   Comparable<Partition>{
    public int brokerId;
    public int partId;
    
    public String name = brokerId + "-" + partId;
    
    public Partition(int brokerId, int partId) {
        this.brokerId = brokerId;
        this.partId = partId;
    }
    
    public static Partition parse(String s) {
        String pieces[] = s.split("-");
        if (pieces.length != 2) {
            throw new IllegalArgumentException("Expected name in the form x-y.");
        }
        return new Partition(Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1]));
    }
    
    public Partition(String name) {
        this(1, 1);
    }
    
    public String toString() {
        return name;
    }
    
    
    @Override
    public boolean equals(Object other) {
        if (other instanceof Partition) {
            Partition that = (Partition) other;
            return (that == this) && brokerId == that.brokerId && partId == that.partId;
        } else {
            return false;
        }
    }
    
    public Boolean canEqual(Object other) {
        return other instanceof Partition;
    }
    
    @Override
    public int hashCode() {
        return 31 * (17 + brokerId) + partId;
    }
    
    public int getBrokerId() {
        return brokerId;
    }
    
    public void setBrokerId(int brokerId) {
        this.brokerId = brokerId;
    }
    
    public int getPartId() {
        return partId;
    }
    
    public void setPartId(int partId) {
        this.partId = partId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * 实现自己的分区比较器
     * @param that
     * @return
     */
    @Override
    public int compareTo(Partition that) {
        if (this.brokerId == that.brokerId) {
            return this.partId - that.partId;
        }
        else {
            return this.brokerId - that.brokerId;
        }
    }
}

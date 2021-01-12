package demo;

/**
 * 二分查找算法
 */
public class BinSearchFindTest {
    
    public static void main(String[] args) {
        int array[] = {1, 2, 5, 6, 23, 33, 33, 77, 88, 90};
        int target = 2;
        int index = findTarget2(array, target);
        System.out.println("----索引位置---index=" + index);
    }
    
    private static int findTarget(int[] array, int target) {
        int left = 0;
        int right = array.length;
        while (left < right) {
            int mid = (left + right) / 2;
            System.out.println("mid=" + mid);
            if (array[mid] == target) {
                return mid;
            } else if (array[mid] > target) {// 说明中间值大于目标表，需要往左边查找
                right--;
            } else if (array[mid] < target) {
                left++;
            }
        }
        return -1;
    }
    
    
    private static int findTarget2(int[] array, int target) {
        int left = 0;
        int right = array.length;
        while (left <= right) {
            int mid = (left + right) / 2;
            System.out.println("mid=" + mid);
            if (array[mid] == target) {
                return mid;
            } else if (array[mid] > target) {// 说明中间值大于目标表，需要往左边查找
                right = mid -1;
            } else if (array[mid] < target) {
                left = mid +1 ;
            }
        }
        return -1;
    }
}

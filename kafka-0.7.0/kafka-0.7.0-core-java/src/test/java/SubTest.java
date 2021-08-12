public class SubTest {
    
    public static void main(String[] args) {
        ParentClass parentClass = new ParentClass();
        parentClass.test();
        
        SubClass subClass = new SubClass();
        subClass.test();
        System.out.println(parentClass.i  + "----------" + subClass.i);
    }
    
}

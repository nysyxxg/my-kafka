class ParentClass {   
    public int i = 10;
    
    public void test(){
        if(i == 10){
            System.out.println("-----ParentClass----" + i);
        }
    }
    
}   
  
public class SubClass extends ParentClass {   
    public int i = 30;   
  
 
}

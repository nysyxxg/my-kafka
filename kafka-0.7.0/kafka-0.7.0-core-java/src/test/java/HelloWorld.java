public class HelloWorld {
    
    public static void main(String[] args) {
        
        f(() -> System.out.println("OK"));//传lambda表达式 一个匿名函数对应一个方法
        
        g(Math::random);
        
        f(A::f);//双冒号 即直接传方法参数 方法必须为静态方法
    }
    
    public static void f(VoidTest v) {
        v.f();
    }
    
    public static void g(ReturnTest r) {
        System.out.println(r.f());
    }
}

class A {
    public static void f() {
        System.out.println("a.f");
        
    }
}

//可以声明FunctionalInterface注解或者不声明都起作用
@FunctionalInterface
interface ReturnTest {
    double f();
}

interface VoidTest {
    public void f();
}
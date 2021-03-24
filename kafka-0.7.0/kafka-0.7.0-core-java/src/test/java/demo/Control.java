package demo;


import java.lang.reflect.Method;

public class Control {
    
    public void use(Method method) {
        MyTool tool = new MyTool();
        try {
            method.invoke(tool, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void use(Class classes,Method method) {
        try {
            method.invoke(classes.newInstance(), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    public void use(Class classes,Method method, Object... args) {
        try {
            method.invoke(classes.newInstance(), args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void invoke(int flag) {
        try {
            switch (flag) {
                case 0:
                    use(MyTool.class.getMethod("a", null));
                    break;
                default:
                    use(MyTool.class.getMethod("b", null));
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    public void invoke(Class classes, int flag) {
        try {
            switch (flag) {
                case 0:
                    use(classes.getMethod("a", null));
                    break;
                default:
                    use(classes.getMethod("b", null));
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void invoke(Class classes, String methodName) {
        try {
            Method method =  classes.getMethod(methodName, null);
            use(classes,method);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    public void invoke(Class classes, String methodName,Class<?>  parameterType, Object... args) {
        try {
            Method method = null;
            if(parameterType == null){
                  method =  classes.getMethod(methodName, null);
                  use(classes,method);
            }else{
                method =  classes.getMethod(methodName, parameterType);
                use(classes,method,args);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
}
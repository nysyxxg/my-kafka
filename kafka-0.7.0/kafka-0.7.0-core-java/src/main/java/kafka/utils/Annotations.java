package kafka.utils;


import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface threadsafe {
    String value() default "线程安全";
}


@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface nonthreadsafe {
    String value() default "线程不安全";
}

@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface immutable {
    String value() default "不可改变的";
}

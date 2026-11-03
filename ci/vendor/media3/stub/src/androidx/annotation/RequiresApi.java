package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE,ElementType.METHOD,ElementType.FIELD,ElementType.PACKAGE})
public @interface RequiresApi { int api() default 1; int sdk() default 1; }

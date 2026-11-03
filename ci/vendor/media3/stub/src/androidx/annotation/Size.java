package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD,ElementType.PARAMETER,ElementType.FIELD,ElementType.LOCAL_VARIABLE,ElementType.TYPE_USE})
public @interface Size { int value() default -1; int min() default 1; int max() default Integer.MAX_VALUE; int multiple() default 1; double factor() default 1.0; }

package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD,ElementType.PARAMETER})
public @interface IntRange { long from() default Long.MIN_VALUE; long to() default Long.MAX_VALUE; }

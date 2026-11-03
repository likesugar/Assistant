package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD,ElementType.PARAMETER})
public @interface FloatRange { double from() default Double.NEGATIVE_INFINITY; double to() default Double.POSITIVE_INFINITY; }

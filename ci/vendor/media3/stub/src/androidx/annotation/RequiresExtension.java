package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE,ElementType.METHOD,ElementType.FIELD})
public @interface RequiresExtension { int extension() default 0; int sdk() default 0; }

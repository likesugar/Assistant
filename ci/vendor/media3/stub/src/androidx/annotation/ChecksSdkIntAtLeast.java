package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE,ElementType.METHOD,ElementType.FIELD,ElementType.PACKAGE})
public @interface ChecksSdkIntAtLeast { int api() default 1; int codename() default 0; boolean lambda() default false; }

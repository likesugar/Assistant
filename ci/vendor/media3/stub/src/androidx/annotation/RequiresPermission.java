package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE,ElementType.METHOD,ElementType.FIELD,ElementType.PACKAGE})
public @interface RequiresPermission { String value() default ""; String[] values() default {}; }

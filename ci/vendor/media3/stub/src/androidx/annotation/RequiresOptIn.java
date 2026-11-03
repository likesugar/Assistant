package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD,ElementType.TYPE,ElementType.PACKAGE})
public @interface RequiresOptIn { Level level() default Level.WARNING; enum Level { ERROR, WARNING } }

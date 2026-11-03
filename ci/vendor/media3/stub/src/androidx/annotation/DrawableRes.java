package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE,ElementType.METHOD,ElementType.FIELD,ElementType.PARAMETER,ElementType.CONSTRUCTOR,ElementType.PACKAGE,ElementType.TYPE_USE})
public @interface DrawableRes {}

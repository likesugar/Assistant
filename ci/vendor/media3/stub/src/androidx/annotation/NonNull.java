package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE,ElementType.METHOD,ElementType.FIELD,ElementType.PARAMETER,ElementType.LOCAL_VARIABLE,ElementType.CONSTRUCTOR,ElementType.PACKAGE,ElementType.TYPE_USE,ElementType.TYPE_PARAMETER})
public @interface NonNull {}

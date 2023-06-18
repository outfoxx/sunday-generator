package org.jboss.resteasy.reactive;
import java.lang.annotation.*;

/**
 * @hidden
 */
@Inherited
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RestStreamElementType {
    String value();
}

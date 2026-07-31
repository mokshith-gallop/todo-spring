package com.todo.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated user's UUID into a controller method parameter.
 * Currently resolved from the X-User-Id header (dev/test stub).
 * Will be replaced by JWT-based resolution in a future story.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}

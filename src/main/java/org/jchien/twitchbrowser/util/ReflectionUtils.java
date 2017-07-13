package org.jchien.twitchbrowser.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author jchien
 */
public class ReflectionUtils {
    public static <T> T invokeMethodOrDie(Class klazz, Object obj, String methodName, Object... args) {
        try {
            Method m = klazz.getMethod(methodName);
            return (T) m.invoke(obj, args);
        } catch (IllegalAccessException
                | NoSuchMethodException
                | InvocationTargetException e) {
            // blow up
            throw new RuntimeException("unable to call " + methodName + "() on " + klazz.getName(), e);
        }
    }

    public static <T> T invokeStaticMethodOrDie(Class klazz, String methodName, Object... args) {
        return invokeMethodOrDie(klazz, null, methodName, args);
    }
}

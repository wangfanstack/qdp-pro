package com.agsccc.agriledger.framework.common.util.object;

import org.springframework.beans.BeanUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Object 工具类
 *
 * @author 农E链源码
 */
public class ObjectUtils {

    /**
     * 复制对象，并忽略 Id 编号（member 模块单元测试使用）
     *
     * @param object   被复制对象（需可访问无参构造器）
     * @param consumer 消费者，可以二次编辑被复制对象
     * @return 复制后的对象
     */
    public static <T> T cloneIgnoreId(T object, Consumer<T> consumer) {
        if (object == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T result;
        try {
            result = (T) object.getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("复制对象失败：" + object.getClass().getName(), e);
        }
        BeanUtils.copyProperties(object, result);
        // 忽略 id 编号
        Field idField = findIdField(object.getClass());
        if (idField != null) {
            try {
                idField.setAccessible(true);
                idField.set(result, null);
            } catch (IllegalAccessException ignore) {
                // 忽略：无法写入 id 时保留复制值
            }
        }
        // 二次编辑
        if (consumer != null) {
            consumer.accept(result);
        }
        return result;
    }

    private static Field findIdField(Class<?> clazz) {
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField("id");
            } catch (NoSuchFieldException ignore) {
                // 继续向父类查找
            }
        }
        return null;
    }

    public static <T extends Comparable<T>> T max(T obj1, T obj2) {
        if (obj1 == null) {
            return obj2;
        }
        if (obj2 == null) {
            return obj1;
        }
        return obj1.compareTo(obj2) > 0 ? obj1 : obj2;
    }

    @SafeVarargs
    public static <T> T defaultIfNull(T... array) {
        for (T item : array) {
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    @SafeVarargs
    public static <T> boolean equalsAny(T obj, T... array) {
        return Arrays.asList(array).contains(obj);
    }

    @SafeVarargs
    public static <T> boolean notEqualsAny(T obj, T... array) {
        return !Arrays.asList(array).contains(obj);
    }

    public static boolean isNotAllEmpty(Object... objs) {
        for (Object obj : objs) {
            if (obj != null && !obj.toString().isEmpty()) {
                return true;
            }
        }
        return false;
    }

}

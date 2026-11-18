package com.agsccc.agriledger.framework.test.core.util;

import com.agsccc.agriledger.framework.common.exception.ErrorCode;
import com.agsccc.agriledger.framework.common.exception.ServiceException;
import com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 单元测试，assert 断言工具类
 *
 * @author 芋道源码
 */
public class AssertUtils {

    /**
     * 比对两个对象的属性是否一致
     * <p>
     * 注意，如果 expected 存在的属性，actual 不存在的时候，会进行忽略
     *
     * @param expected     期望对象
     * @param actual       实际对象
     * @param ignoreFields 忽略的属性数组
     */
    public static void assertPojoEquals(Object expected, Object actual, String... ignoreFields) {
        List<Field> expectedFields = getAllFields(expected.getClass());
        expectedFields.forEach(expectedField -> {
            // 忽略 jacoco 自动生成的 $jacocoData 属性的情况
            if (expectedField.isSynthetic()) {
                return;
            }
            // 如果是忽略的属性，则不进行比对
            if (ArrayUtils.contains(ignoreFields, expectedField.getName())) {
                return;
            }
            // 忽略不存在的属性
            Field actualField = findField(actual.getClass(), expectedField.getName());
            if (actualField == null) {
                return;
            }
            // 比对
            Assertions.assertEquals(
                    getFieldValue(expected, expectedField),
                    getFieldValue(actual, actualField),
                    String.format("Field(%s) 不匹配", expectedField.getName())
            );
        });
    }

    /**
     * 比对两个对象的属性是否一致
     * <p>
     * 注意，如果 expected 存在的属性，actual 不存在的时候，会进行忽略
     *
     * @param expected     期望对象
     * @param actual       实际对象
     * @param ignoreFields 忽略的属性数组
     * @return 是否一致
     */
    public static boolean isPojoEquals(Object expected, Object actual, String... ignoreFields) {
        List<Field> expectedFields = getAllFields(expected.getClass());
        return expectedFields.stream().allMatch(expectedField -> {
            // 如果是忽略的属性，则不进行比对
            if (ArrayUtils.contains(ignoreFields, expectedField.getName())) {
                return true;
            }
            // 忽略不存在的属性
            Field actualField = findField(actual.getClass(), expectedField.getName());
            if (actualField == null) {
                return true;
            }
            return Objects.equals(getFieldValue(expected, expectedField),
                    getFieldValue(actual, actualField));
        });
    }

    /**
     * 执行方法，校验抛出的 Service 是否符合条件
     *
     * @param executable    业务异常
     * @param errorCode     错误码对象
     * @param messageParams 消息参数
     */
    public static void assertServiceException(Executable executable, ErrorCode errorCode, Object... messageParams) {
        // 调用方法
        ServiceException serviceException = assertThrows(ServiceException.class, executable);
        // 校验错误码
        Assertions.assertEquals(errorCode.getCode(), serviceException.getCode(), "错误码不匹配");
        String message = ServiceExceptionUtils.doFormat(errorCode.getCode(), errorCode.getMsg(), messageParams);
        Assertions.assertEquals(message, serviceException.getMessage(), "错误提示不匹配");
    }

    // ========== 反射工具方法 ==========

    /**
     * 获得一个类及其所有父类的字段
     *
     * @param clazz 类
     * @return 字段数组
     */
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
        }
        return fields;
    }

    /**
     * 查找类及其父类中指定名称的字段
     *
     * @param clazz 类
     * @param name  字段名
     * @return 字段，不存在时返回 null
     */
    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续查找父类
            }
        }
        return null;
    }

    /**
     * 获得字段的值
     *
     * @param target 对象
     * @param field  字段
     * @return 字段的值
     */
    private static Object getFieldValue(Object target, Field field) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(String.format("读取字段(%s)的值失败", field.getName()), e);
        }
    }

}

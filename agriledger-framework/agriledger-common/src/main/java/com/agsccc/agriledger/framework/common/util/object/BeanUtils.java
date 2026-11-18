package com.agsccc.agriledger.framework.common.util.object;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.common.util.collection.CollectionUtils;

import java.util.List;
import java.util.function.Consumer;

/**
 * Bean 工具类
 * <p>
 * 默认使用 Spring BeanUtils 进行属性复制。
 * 复杂对象转换建议使用 MapStruct。
 */
public class BeanUtils {

    /**
     * 将对象转换为指定类型
     */
    public static <T> T toBean(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        T target = org.springframework.beans.BeanUtils.instantiateClass(targetClass);
        org.springframework.beans.BeanUtils.copyProperties(source, target);
        return target;
    }

    /**
     * 将对象转换为指定类型，并执行后置处理
     */
    public static <T> T toBean(Object source, Class<T> targetClass, Consumer<T> peek) {
        T target = toBean(source, targetClass);
        if (target != null && peek != null) {
            peek.accept(target);
        }
        return target;
    }

    /**
     * 批量转换对象
     */
    public static <S, T> List<T> toBean(List<S> source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        return CollectionUtils.convertList(source, s -> toBean(s, targetType));
    }

    /**
     * 批量转换对象，并执行后置处理
     */
    public static <S, T> List<T> toBean(List<S> source, Class<T> targetType, Consumer<T> peek) {
        List<T> list = toBean(source, targetType);
        if (list != null && peek != null) {
            list.forEach(peek);
        }
        return list;
    }

    /**
     * 分页对象转换
     */
    public static <S, T> PageResult<T> toBean(PageResult<S> source, Class<T> targetType) {
        return toBean(source, targetType, null);
    }

    /**
     * 分页对象转换，并执行后置处理
     */
    public static <S, T> PageResult<T> toBean(PageResult<S> source, Class<T> targetType, Consumer<T> peek) {
        if (source == null) {
            return null;
        }

        List<T> list = toBean(source.getList(), targetType);
        if (list != null && peek != null) {
            list.forEach(peek);
        }

        return new PageResult<>(list, source.getTotal());
    }

    /**
     * 复制对象属性
     */
    public static void copyProperties(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        org.springframework.beans.BeanUtils.copyProperties(source, target);
    }

}

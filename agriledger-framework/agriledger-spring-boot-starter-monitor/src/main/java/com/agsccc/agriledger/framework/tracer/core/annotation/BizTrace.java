package com.agsccc.agriledger.framework.tracer.core.annotation;

import java.lang.annotation.*;

/**
 * 打印业务编号 / 业务类型注解
 * <p>
 * 基于 Micrometer Tracing 实现：biz.type / biz.id 会作为 Span 的 tag 记录，
 * 上报后端（如 Zipkin）后可按 tag 检索业务维度链路。
 *
 * @author 麻薯
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface BizTrace {

    /**
     * 业务编号 tag 名
     */
    String ID_TAG = "biz.id";
    /**
     * 业务类型 tag 名
     */
    String TYPE_TAG = "biz.type";

    /**
     * @return 操作名
     */
    String operationName() default "";

    /**
     * @return 业务编号
     */
    String id();

    /**
     * @return 业务类型
     */
    String type();

}

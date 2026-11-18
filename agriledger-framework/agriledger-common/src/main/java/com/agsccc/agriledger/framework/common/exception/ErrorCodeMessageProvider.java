package com.agsccc.agriledger.framework.common.exception;

/**
 * 错误码文案模板提供者 SPI
 * <p>
 * 业务模块实现该接口，并在启动时通过
 * {@link com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils#init(ErrorCodeMessageProvider)}
 * 注入，即可在运行时按错误码覆盖代码中 ErrorCodeConstants 的默认错误提示，实现"改文案不发版"
 *
 * @author Sail
 */
@FunctionalInterface
public interface ErrorCodeMessageProvider {

    /**
     * 按错误码返回自定义的消息模板
     *
     * @param code 错误码
     * @return 自定义消息模板，支持 {} 占位符；无自定义时返回 null，由调用方兜底使用代码中的默认文案
     */
    String getMessageTemplate(Integer code);

}

package com.agsccc.agriledger.framework.common.exception.util;

import com.agsccc.agriledger.framework.common.exception.ErrorCode;
import com.agsccc.agriledger.framework.common.exception.ErrorCodeMessageProvider;
import com.agsccc.agriledger.framework.common.exception.ServiceException;
import com.agsccc.agriledger.framework.common.exception.enums.GlobalErrorCodeConstants;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ServiceException} 工具类
 * <p>
 * 目的在于，格式化异常信息提示。
 * 考虑到 String.format 在参数不正确时会报错，因此使用 {} 作为占位符，并使用 {@link #doFormat(int, String, Object...)} 方法来格式化
 *
 */
@Slf4j
public class ServiceExceptionUtils {

    /**
     * 错误码文案模板提供者，由业务模块启动时通过 {@link #init(ErrorCodeMessageProvider)} 注入
     * <p>
     * hft-common 无法依赖业务模块，所以采用静态注入（同 DictFrameworkUtils 的模式）；
     * volatile 保证注入后对所有读线程可见
     */
    private static volatile ErrorCodeMessageProvider messageProvider;

    public static void init(ErrorCodeMessageProvider messageProvider) {
        ServiceExceptionUtils.messageProvider = messageProvider;
        log.info("[init][初始化 ErrorCodeMessageProvider 成功]");
    }


    // ========== 和 ServiceException 的集成 ==========

    public static ServiceException exception(ErrorCode errorCode) {
        return exception0(errorCode.getCode(), errorCode.getMsg());
    }

    public static ServiceException exception(ErrorCode errorCode, Object... params) {
        return exception0(errorCode.getCode(), errorCode.getMsg(), params);
    }

    public static ServiceException exception0(Integer code, String messagePattern, Object... params) {
        String message = doFormat(code, messagePattern, params);
        return new ServiceException(code, message);
    }

    public static ServiceException invalidParamException(String messagePattern, Object... params) {
        return exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), messagePattern, params);
    }

    // ========== 格式化方法 ==========

    /**
     * 将错误编号对应的消息使用 params 进行格式化。
     *
     * @param code           错误编号
     * @param messagePattern 消息模版
     * @param params         参数
     * @return 格式化后的提示
     */
    @VisibleForTesting
    public static String doFormat(int code, String messagePattern, Object... params) {
        StringBuilder sbuf = new StringBuilder(messagePattern.length() + 50);
        int i = 0;
        int j;
        int l;
        for (l = 0; l < params.length; l++) {
            j = messagePattern.indexOf("{}", i);
            if (j == -1) {
                log.error("[doFormat][参数过多：错误码({})|错误内容({})|参数({})", code, messagePattern, params);
                if (i == 0) {
                    return messagePattern;
                } else {
                    sbuf.append(messagePattern.substring(i));
                    return sbuf.toString();
                }
            } else {
                sbuf.append(messagePattern, i, j);
                sbuf.append(params[l]);
                i = j + 2;
            }
        }
        if (messagePattern.indexOf("{}", i) != -1) {
            log.error("[doFormat][参数过少：错误码({})|错误内容({})|参数({})", code, messagePattern, params);
        }
        sbuf.append(messagePattern.substring(i));
        return sbuf.toString();
    }

}

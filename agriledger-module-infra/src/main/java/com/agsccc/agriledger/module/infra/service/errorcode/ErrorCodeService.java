package com.agsccc.agriledger.module.infra.service.errorcode;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo.ErrorCodePageReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo.ErrorCodeSaveReqVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.errorcode.ErrorCodeDO;

/**
 * 错误码文案 Service 接口
 *
 * @author Sail
 */
public interface ErrorCodeService {

    /**
     * 创建错误码文案
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createErrorCode(ErrorCodeSaveReqVO createReqVO);

    /**
     * 更新错误码文案
     *
     * @param updateReqVO 更新信息
     */
    void updateErrorCode(ErrorCodeSaveReqVO updateReqVO);

    /**
     * 删除错误码文案
     *
     * @param id 编号
     */
    void deleteErrorCode(Long id);

    /**
     * 获得错误码文案
     *
     * @param id 编号
     * @return 错误码文案
     */
    ErrorCodeDO getErrorCode(Long id);

    /**
     * 获得错误码文案分页
     *
     * @param pageReqVO 分页查询
     * @return 错误码文案分页
     */
    PageResult<ErrorCodeDO> getErrorCodePage(ErrorCodePageReqVO pageReqVO);

    /**
     * 按错误码获得自定义的消息模板
     *
     * @param code 错误码
     * @return 消息模板；不存在自定义时返回 null
     */
    String getMessageTemplate(Integer code);

}

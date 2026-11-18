package com.agsccc.agriledger.module.system.service.sms;

import com.agsccc.agriledger.module.system.api.sms.dto.code.SmsCodeSendReqDTO;
import com.agsccc.agriledger.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import com.agsccc.agriledger.module.system.api.sms.dto.code.SmsCodeValidateReqDTO;
import com.agsccc.agriledger.module.system.dal.dataobject.sms.SmsCodeDO;
import com.agsccc.agriledger.module.system.dal.mysql.sms.SmsCodeMapper;
import com.agsccc.agriledger.module.system.enums.sms.SmsSceneEnum;
import com.agsccc.agriledger.module.system.framework.sms.config.SmsCodeProperties;
import com.google.common.hash.Hashing;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import static com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils.exception;
import static com.agsccc.agriledger.framework.common.util.date.DateUtils.isToday;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.*;

/**
 * 短信验证码 Service 实现类
 *
 * @author 农E链源码
 */
@Service
@Validated
public class SmsCodeServiceImpl implements SmsCodeService {

    @Resource
    private SmsCodeProperties smsCodeProperties;

    @Resource
    private SmsCodeMapper smsCodeMapper;

    @Resource
    private SmsSendService smsSendService;

    @Override
    public void sendSmsCode(SmsCodeSendReqDTO reqDTO) {
        SmsSceneEnum sceneEnum = SmsSceneEnum.getCodeByScene(reqDTO.getScene());
        if (sceneEnum == null) {
            throw new IllegalArgumentException(String.format("验证码场景(%s) 查找不到配置", reqDTO.getScene()));
        }
        // 创建验证码
        String code = createSmsCode(reqDTO.getMobile(), reqDTO.getScene(), reqDTO.getCreateIp());
        // 发送验证码
        smsSendService.sendSingleSms(reqDTO.getMobile(), null, null,
                sceneEnum.getTemplateCode(), Collections.singletonMap("code", code));
    }

    private String createSmsCode(String mobile, Integer scene, String ip) {
        // 校验是否可以发送验证码，不用筛选场景
        SmsCodeDO lastSmsCode = smsCodeMapper.selectLastByMobile(mobile, null, null);
        if (lastSmsCode != null) {
            if (Duration.between(lastSmsCode.getCreateTime(), LocalDateTime.now()).toMillis()
                    < smsCodeProperties.getSendFrequency().toMillis()) { // 发送过于频繁
                throw exception(SMS_CODE_SEND_TOO_FAST);
            }
            if (isToday(lastSmsCode.getCreateTime()) && // 必须是今天，才能计算超过当天的上限
                    lastSmsCode.getTodayIndex() >= smsCodeProperties.getSendMaximumQuantityPerDay()) { // 超过当天发送的上限。
                throw exception(SMS_CODE_EXCEED_SEND_MAXIMUM_QUANTITY_PER_DAY);
            }
            // TODO 芋艿：提升，每个 IP 每天可发送数量
            // TODO 芋艿：提升，每个 IP 每小时可发送数量
        }

        // 创建验证码记录：数据库仅存哈希（不落明文），短信发送使用内存中的明文
        String code = String.format("%0" + smsCodeProperties.getEndCode().toString().length() + "d", ThreadLocalRandom.current().nextInt(smsCodeProperties.getBeginCode(), smsCodeProperties.getEndCode() + 1));
        SmsCodeDO newSmsCode = SmsCodeDO.builder().mobile(mobile).code(buildCodeDigest(mobile, scene, code)).scene(scene)
                .todayIndex(lastSmsCode != null && isToday(lastSmsCode.getCreateTime()) ? lastSmsCode.getTodayIndex() + 1 : 1)
                .createIp(ip).used(false).build();
        smsCodeMapper.insert(newSmsCode);
        return code;
    }

    @Override
    public void useSmsCode(SmsCodeUseReqDTO reqDTO) {
        // 检测验证码是否有效
        SmsCodeDO lastSmsCode = validateSmsCode0(reqDTO.getMobile(), reqDTO.getCode(), reqDTO.getScene());
        // 使用验证码
        smsCodeMapper.updateById(SmsCodeDO.builder().id(lastSmsCode.getId())
                .used(true).usedTime(LocalDateTime.now()).usedIp(reqDTO.getUsedIp()).build());
    }

    @Override
    public void validateSmsCode(SmsCodeValidateReqDTO reqDTO) {
        validateSmsCode0(reqDTO.getMobile(), reqDTO.getCode(), reqDTO.getScene());
    }

    private SmsCodeDO validateSmsCode0(String mobile, String code, Integer scene) {
        // 按手机号 + 场景查询最近一条验证码（哈希存储，无法按明文 code 直接查询）
        SmsCodeDO lastSmsCode = smsCodeMapper.selectLastByMobile(mobile, null, scene);
        // 校验验证码：与新哈希匹配，或与存量明文记录匹配（明文记录 10 分钟过期，过渡期后自然消亡）
        if (lastSmsCode == null
                || (!lastSmsCode.getCode().equals(buildCodeDigest(mobile, scene, code))
                        && !lastSmsCode.getCode().equals(code))) {
            throw exception(SMS_CODE_NOT_FOUND);
        }
        // 超过时间
        if (Duration.between(lastSmsCode.getCreateTime(), LocalDateTime.now()).toMillis()
                >= smsCodeProperties.getExpireTimes().toMillis()) { // 验证码已过期
            throw exception(SMS_CODE_EXPIRED);
        }
        // 判断验证码是否已被使用
        if (Boolean.TRUE.equals(lastSmsCode.getUsed())) {
            throw exception(SMS_CODE_USED);
        }
        return lastSmsCode;
    }

    /**
     * 计算验证码摘要：mobile:scene:code 拼接后哈希。
     * 配置 hmac-key 时使用 HMAC-SHA256（密钥经环境变量注入），否则退化为 SHA-256（仍不落明文）
     */
    private String buildCodeDigest(String mobile, Integer scene, String code) {
        if (StringUtils.isNotEmpty(smsCodeProperties.getHmacKey())) {
            return Hashing.hmacSha256(smsCodeProperties.getHmacKey().getBytes(StandardCharsets.UTF_8))
                    .hashString(mobile + ":" + scene + ":" + code, StandardCharsets.UTF_8).toString();
        }
        return Hashing.sha256().hashString(mobile + ":" + scene + ":" + code, StandardCharsets.UTF_8).toString();
    }

}

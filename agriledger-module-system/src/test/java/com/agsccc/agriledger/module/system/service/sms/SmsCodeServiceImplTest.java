package com.agsccc.agriledger.module.system.service.sms;

import com.agsccc.agriledger.framework.test.core.ut.BaseMockitoUnitTest;
import com.agsccc.agriledger.module.system.api.sms.dto.code.SmsCodeSendReqDTO;
import com.agsccc.agriledger.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import com.agsccc.agriledger.module.system.dal.dataobject.sms.SmsCodeDO;
import com.agsccc.agriledger.module.system.dal.mysql.sms.SmsCodeMapper;
import com.agsccc.agriledger.module.system.enums.sms.SmsSceneEnum;
import com.agsccc.agriledger.module.system.framework.sms.config.SmsCodeProperties;
import com.google.common.hash.Hashing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

import static com.agsccc.agriledger.framework.test.core.util.AssertUtils.assertServiceException;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.SMS_CODE_EXPIRED;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.SMS_CODE_NOT_FOUND;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.SMS_CODE_SEND_TOO_FAST;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.SMS_CODE_USED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SmsCodeServiceImpl} 的单元测试（重点覆盖验证码哈希存储与校验）
 *
 * @author 农E链源码
 */
public class SmsCodeServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private SmsCodeServiceImpl smsCodeService;

    @Mock
    private SmsCodeMapper smsCodeMapper;
    @Mock
    private SmsSendService smsSendService;

    private static final String MOBILE = "15601691300";
    private static final Integer SCENE = SmsSceneEnum.ADMIN_MEMBER_LOGIN.getScene();
    private static final String CODE = "102410";

    @BeforeEach
    public void setUp() {
        // 构造真实配置（无 hmac-key，退化为 SHA-256）
        SmsCodeProperties properties = new SmsCodeProperties();
        properties.setExpireTimes(Duration.ofMinutes(10));
        properties.setSendFrequency(Duration.ofMinutes(1));
        properties.setSendMaximumQuantityPerDay(10);
        properties.setBeginCode(0);
        properties.setEndCode(999999);
        properties.setHmacKey("");
        ReflectionTestUtils.setField(smsCodeService, "smsCodeProperties", properties);
    }

    /** 与实现一致的摘要算法（无 hmac-key 时 SHA-256("mobile:scene:code")），用于测试预期 */
    private String digest(String mobile, Integer scene, String code) {
        return Hashing.sha256().hashString(mobile + ":" + scene + ":" + code, StandardCharsets.UTF_8).toString();
    }

    private SmsCodeDO buildSmsCode(String storedCode, LocalDateTime createTime, boolean used) {
        SmsCodeDO smsCode = SmsCodeDO.builder().mobile(MOBILE).code(storedCode).scene(SCENE)
                .todayIndex(1).used(used).build();
        smsCode.setCreateTime(createTime); // BaseDO 的链式 setter 返回父类型，需单独设置
        return smsCode;
    }

    // ========== 发送：哈希存储 ==========

    @Test
    public void testSendSmsCode_storeDigest() {
        // mock：无历史验证码
        when(smsCodeMapper.selectLastByMobile(MOBILE, null, null)).thenReturn(null);
        when(smsCodeMapper.insert(any(SmsCodeDO.class))).thenReturn(1);

        // 调用
        SmsCodeSendReqDTO reqDTO = new SmsCodeSendReqDTO().setMobile(MOBILE).setScene(SCENE);
        smsCodeService.sendSmsCode(reqDTO);

        // 断言：落库的是摘要而非明文；返回给短信通道的是明文
        ArgumentCaptor<SmsCodeDO> captor = ArgumentCaptor.forClass(SmsCodeDO.class);
        verify(smsCodeMapper).insert(captor.capture());
        SmsCodeDO inserted = captor.getValue();
        assertEquals(64, inserted.getCode().length()); // SHA-256 hex，非 6 位明文
        assertNotEquals(CODE, inserted.getCode());
        verify(smsSendService).sendSingleSms(eq(MOBILE), isNull(), isNull(), any(), anyMap());
    }

    @Test
    public void testSendSmsCode_tooFast() {
        // mock：上一条验证码刚发送（1 分钟内）
        when(smsCodeMapper.selectLastByMobile(MOBILE, null, null))
                .thenReturn(buildSmsCode(digest(MOBILE, SCENE, CODE), LocalDateTime.now(), false));

        // 调用 + 断言
        SmsCodeSendReqDTO reqDTO = new SmsCodeSendReqDTO().setMobile(MOBILE).setScene(SCENE);
        assertServiceException(() -> smsCodeService.sendSmsCode(reqDTO), SMS_CODE_SEND_TOO_FAST);
    }

    // ========== 使用：哈希 / 存量明文兼容校验 ==========

    @Test
    public void testUseSmsCode_successHashed() {
        // mock：最近一条为哈希存储的验证码
        when(smsCodeMapper.selectLastByMobile(MOBILE, null, SCENE))
                .thenReturn(buildSmsCode(digest(MOBILE, SCENE, CODE), LocalDateTime.now(), false));

        // 调用：验证通过并标记使用
        smsCodeService.useSmsCode(new SmsCodeUseReqDTO().setMobile(MOBILE).setCode(CODE)
                .setScene(SCENE).setUsedIp("127.0.0.1"));
        ArgumentCaptor<SmsCodeDO> captor = ArgumentCaptor.forClass(SmsCodeDO.class);
        verify(smsCodeMapper).updateById(captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().getUsed());
    }

    @Test
    public void testUseSmsCode_successLegacyPlain() {
        // mock：迁移前落库的明文验证码（10 分钟过期，过渡期自然消亡）
        when(smsCodeMapper.selectLastByMobile(MOBILE, null, SCENE))
                .thenReturn(buildSmsCode(CODE, LocalDateTime.now(), false));

        // 调用：明文兼容校验通过
        smsCodeService.useSmsCode(new SmsCodeUseReqDTO().setMobile(MOBILE).setCode(CODE)
                .setScene(SCENE).setUsedIp("127.0.0.1"));
        verify(smsCodeMapper).updateById(any(SmsCodeDO.class));
    }

    @Test
    public void testUseSmsCode_codeNotMatch() {
        // mock：哈希对应的是另一个验证码
        when(smsCodeMapper.selectLastByMobile(MOBILE, null, SCENE))
                .thenReturn(buildSmsCode(digest(MOBILE, SCENE, "999999"), LocalDateTime.now(), false));

        // 调用 + 断言
        SmsCodeUseReqDTO reqDTO = new SmsCodeUseReqDTO().setMobile(MOBILE).setCode(CODE)
                .setScene(SCENE).setUsedIp("127.0.0.1");
        assertServiceException(() -> smsCodeService.useSmsCode(reqDTO), SMS_CODE_NOT_FOUND);
    }

    @Test
    public void testUseSmsCode_notFound() {
        // mock：无验证码记录
        when(smsCodeMapper.selectLastByMobile(MOBILE, null, SCENE)).thenReturn(null);

        // 调用 + 断言
        SmsCodeUseReqDTO reqDTO = new SmsCodeUseReqDTO().setMobile(MOBILE).setCode(CODE)
                .setScene(SCENE).setUsedIp("127.0.0.1");
        assertServiceException(() -> smsCodeService.useSmsCode(reqDTO), SMS_CODE_NOT_FOUND);
    }

    @Test
    public void testUseSmsCode_expired() {
        // mock：验证码创建于 11 分钟前（过期 10 分钟）
        when(smsCodeMapper.selectLastByMobile(MOBILE, null, SCENE))
                .thenReturn(buildSmsCode(digest(MOBILE, SCENE, CODE), LocalDateTime.now().minusMinutes(11), false));

        // 调用 + 断言
        SmsCodeUseReqDTO reqDTO = new SmsCodeUseReqDTO().setMobile(MOBILE).setCode(CODE)
                .setScene(SCENE).setUsedIp("127.0.0.1");
        assertServiceException(() -> smsCodeService.useSmsCode(reqDTO), SMS_CODE_EXPIRED);
    }

    @Test
    public void testUseSmsCode_used() {
        // mock：验证码已被使用
        when(smsCodeMapper.selectLastByMobile(MOBILE, null, SCENE))
                .thenReturn(buildSmsCode(digest(MOBILE, SCENE, CODE), LocalDateTime.now(), true));

        // 调用 + 断言
        SmsCodeUseReqDTO reqDTO = new SmsCodeUseReqDTO().setMobile(MOBILE).setCode(CODE)
                .setScene(SCENE).setUsedIp("127.0.0.1");
        assertServiceException(() -> smsCodeService.useSmsCode(reqDTO), SMS_CODE_USED);
    }

    @Test
    public void testValidateSmsCode_plainNeverEqualsDigest() {
        // 说明性断言：明文与摘要在算法上不可能相等，保证"不落明文"成立
        assertFalse(digest(MOBILE, SCENE, CODE).equals(CODE));
    }

}

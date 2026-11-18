package com.agsccc.agriledger.framework.test.core.util;

import com.agsccc.agriledger.framework.common.enums.CommonStatusEnum;
import org.apache.commons.lang3.StringUtils;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 随机工具类
 *
 * @author 芋道源码
 */
public class RandomUtils {

    private static final int RANDOM_STRING_LENGTH = 10;

    private static final int TINYINT_MAX = 127;

    private static final int RANDOM_DATE_MAX = 30;

    private static final int RANDOM_COLLECTION_LENGTH = 5;

    /**
     * 随机字符串的字符集（数字 + 小写字母）
     */
    private static final String RANDOM_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz";

    private static final long ONE_DAY_MILLIS = 24L * 60 * 60 * 1000;

    private static final PodamFactory PODAM_FACTORY = new PodamFactoryImpl();

    static {
        // 字符串
        PODAM_FACTORY.getStrategy().addOrReplaceTypeManufacturer(String.class,
                (dataProviderStrategy, attributeMetadata, map) -> randomString());
        // Integer
        PODAM_FACTORY.getStrategy().addOrReplaceTypeManufacturer(Integer.class, (dataProviderStrategy, attributeMetadata, map) -> {
            String attributeName = attributeMetadata.getAttributeName();
            // 如果是 status 的字段，返回 0 或 1
            if ("status".equals(attributeName)) {
                return randomCommonStatus();
            }
            // 如果是 type、status 结尾的字段，返回 tinyint 范围
            if (endsWithAnyIgnoreCase(attributeName, "type", "status", "category", "scope", "result")) {
                return randomInt(0, TINYINT_MAX + 1);
            }
            return randomInt(0, Integer.MAX_VALUE);
        });
        // BigDecimal：限制精度在 DECIMAL(10,2) 范围内，避免 H2 等数据库溢出
        PODAM_FACTORY.getStrategy().addOrReplaceTypeManufacturer(BigDecimal.class,
                (dataProviderStrategy, attributeMetadata, map) ->
                        BigDecimal.valueOf(randomInt(0, 10000000), 2));
        // LocalDateTime
        PODAM_FACTORY.getStrategy().addOrReplaceTypeManufacturer(LocalDateTime.class,
                (dataProviderStrategy, attributeMetadata, map) -> randomLocalDateTime());
        // Boolean
        PODAM_FACTORY.getStrategy().addOrReplaceTypeManufacturer(Boolean.class, (dataProviderStrategy, attributeMetadata, map) -> {
            // 如果是 deleted 的字段，返回非删除
            if ("deleted".equals(attributeMetadata.getAttributeName())) {
                return false;
            }
            return ThreadLocalRandom.current().nextBoolean();
        });
    }

    public static String randomString() {
        StringBuilder sb = new StringBuilder(RANDOM_STRING_LENGTH);
        for (int i = 0; i < RANDOM_STRING_LENGTH; i++) {
            sb.append(RANDOM_CHARS.charAt(randomInt(0, RANDOM_CHARS.length())));
        }
        return sb.toString();
    }

    public static Long randomLongId() {
        return ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE);
    }

    public static Integer randomInteger() {
        return randomInt(0, Integer.MAX_VALUE);
    }

    public static Date randomDate() {
        return new Date(System.currentTimeMillis() + randomInt(0, RANDOM_DATE_MAX) * ONE_DAY_MILLIS);
    }

    public static LocalDateTime randomLocalDateTime() {
        // 设置 Nano 为零的原因，避免 MySQL、H2 存储不到时间戳
        return LocalDateTime.ofInstant(randomDate().toInstant(), ZoneId.systemDefault()).withNano(0);
    }

    public static Short randomShort() {
        return (short) randomInt(0, Short.MAX_VALUE);
    }

    public static <T> Set<T> randomSet(Class<T> clazz) {
        return Stream.iterate(0, i -> i).limit(randomInt(1, RANDOM_COLLECTION_LENGTH))
                .map(i -> randomPojo(clazz)).collect(Collectors.toSet());
    }

    public static Integer randomCommonStatus() {
        CommonStatusEnum[] values = CommonStatusEnum.values();
        return values[randomInt(0, values.length)].getStatus();
    }

    public static String randomEmail() {
        return randomString() + "@qq.com";
    }

    public static String randomMobile() {
        return "13800138" + randomNumbers(3);
    }

    public static String randomURL() {
        return "https://www.iocoder.cn/" + randomString();
    }

    @SafeVarargs
    public static <T> T randomPojo(Class<T> clazz, Consumer<T>... consumers) {
        T pojo = PODAM_FACTORY.manufacturePojo(clazz);
        // 非空时，回调逻辑。通过它，可以实现 Pojo 的进一步处理
        if (consumers != null && consumers.length > 0) {
            Arrays.stream(consumers).forEach(consumer -> consumer.accept(pojo));
        }
        return pojo;
    }

    @SafeVarargs
    public static <T> T randomPojo(Class<T> clazz, Type type, Consumer<T>... consumers) {
        T pojo = PODAM_FACTORY.manufacturePojo(clazz, type);
        // 非空时，回调逻辑。通过它，可以实现 Pojo 的进一步处理
        if (consumers != null && consumers.length > 0) {
            Arrays.stream(consumers).forEach(consumer -> consumer.accept(pojo));
        }
        return pojo;
    }

    @SafeVarargs
    public static <T> List<T> randomPojoList(Class<T> clazz, Consumer<T>... consumers) {
        int size = randomInt(1, RANDOM_COLLECTION_LENGTH);
        return randomPojoList(clazz, size, consumers);
    }

    @SafeVarargs
    public static <T> List<T> randomPojoList(Class<T> clazz, int size, Consumer<T>... consumers) {
        return Stream.iterate(0, i -> i).limit(size).map(o -> randomPojo(clazz, consumers))
                .collect(Collectors.toList());
    }

    // ========== 内部工具方法 ==========

    private static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    private static String randomNumbers(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(randomInt(0, 10));
        }
        return sb.toString();
    }

    private static boolean endsWithAnyIgnoreCase(String str, String... suffixes) {
        if (str == null) {
            return false;
        }
        for (String suffix : suffixes) {
            if (StringUtils.endsWithIgnoreCase(str, suffix)) {
                return true;
            }
        }
        return false;
    }

}

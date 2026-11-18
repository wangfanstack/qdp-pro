package com.agsccc.agriledger.module.infra.framework.file.core.utils;

import com.agsccc.agriledger.module.infra.framework.file.core.enums.FileCategoryEnum;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils.exception;
import static com.agsccc.agriledger.module.infra.enums.ErrorCodeConstants.FILE_PATH_INVALID;

/**
 * 文件路径工具类
 *
 * @author Sail
 */
public class FilePathUtils {

    /**
     * 存储路径的年月目录格式（yyyy/MM）
     */
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");

    /**
     * 无租户上下文时的租户目录段
     */
    private static final String DEFAULT_TENANT_SEGMENT = "0";

    private FilePathUtils() {
    }

    /**
     * 按目录规范生成存储路径：{category}/{tenantId}/{yyyy}/{MM}/{uuid}.{ext}
     *
     * <p>文件名用 uuid 防止同日同名文件互相覆盖；无租户时落缺省段 "0"
     *
     * @param category  文件分类
     * @param tenantId  租户编号，允许空
     * @param extension 文件扩展名，允许空或带前导点（为空时文件名不含后缀）
     * @return 存储相对路径
     */
    public static String buildStoragePath(FileCategoryEnum category, Long tenantId, String extension) {
        String tenantSegment = tenantId != null ? tenantId.toString() : DEFAULT_TENANT_SEGMENT;
        String fileName = UUID.randomUUID().toString().replace("-", "");
        if (StringUtils.isNotEmpty(extension)) {
            fileName = fileName + "." + Strings.CS.removeStart(extension, ".");
        }
        return category.getDirectory() + "/" + tenantSegment + "/"
                + LocalDateTime.now().format(YEAR_MONTH_FORMATTER) + "/" + fileName;
    }

    /**
     * 校验文件名是否合法，禁止携带目录路径。
     *
     * @param name 文件名
     * @return 文件名
     */
    public static String validateFileName(String name) {
        if (StringUtils.isEmpty(name)) {
            return name;
        }
        if (isPathValid(name) || Strings.CS.contains(name, "/") || !Strings.CS.equals(name, new File(name).getName())) {
            throw exception(FILE_PATH_INVALID);
        }
        return name;
    }

    /**
     * 校验文件相对路径是否合法，不合法时抛出业务异常。
     *
     * @param path 文件相对路径
     */
    public static void validatePath(String path) {
        if (StringUtils.isEmpty(path) || isPathValid(path)) {
            throw exception(FILE_PATH_INVALID);
        }
    }

    /**
     * 校验路径是否为安全的相对路径，禁止绝对路径、Windows 盘符、反斜杠、空路径段和目录穿越。
     *
     * @param path 路径
     * @return 是否合法
     */
    private static boolean isPathValid(String path) {
        // 不能以 / 或 \ 开头，避免传入绝对路径
        if (Strings.CS.startsWithAny(path, "/", "\\")) {
            return true;
        }
        // 不能包含反斜杠或空字符，避免绕过不同系统的路径解析
        if (Strings.CS.contains(path, "\\") || path.indexOf('\0') >= 0) {
            return true;
        }
        // 不能是 Windows 盘符路径，例如 C:/test.jpg
        if (path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
            return true;
        }
        try {
            // 使用 JDK Path 再兜底判断一次绝对路径
            if (Paths.get(path).isAbsolute()) {
                return true;
            }
        } catch (InvalidPathException ex) {
            return true;
        }
        // 不能包含空路径段、当前目录或上级目录，避免目录穿越
        for (String segment : path.split("/", -1)) {
            if (StringUtils.isEmpty(segment) || ".".equals(segment) || "..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

}

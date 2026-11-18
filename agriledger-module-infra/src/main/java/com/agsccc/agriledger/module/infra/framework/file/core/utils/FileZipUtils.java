package com.agsccc.agriledger.module.infra.framework.file.core.utils;

import com.agsccc.agriledger.module.infra.dal.dataobject.file.FileDO;
import com.google.common.io.Files;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件批量打包 ZIP 工具（流式内核）
 *
 * <p>逐文件从存储开流、固定缓冲区拷贝写入 zip，全程不将文件整块读入内存，
 * 内存占用 ≈ 缓冲区大小，与文件规模无关（GB 级视频也安全）。
 * 同步流式打包与下载中心异步打包共用。
 *
 * @author Sail
 */
@Slf4j
public final class FileZipUtils {

    /**
     * 拷贝缓冲区大小（64KB）
     */
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    /**
     * 条目内字节进展的回调阈值（32MB）：大文件拷贝期间周期性回报，兼作任务心跳与协作式取消检查点
     */
    private static final long PROGRESS_REPORT_BYTES = 32L * 1024 * 1024;
    /**
     * 已高度压缩、无需二次压缩的默认扩展名集合（DEFLATE 收益极小且白耗 CPU）
     */
    public static final Set<String> DEFAULT_NO_COMPRESS_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "mp4", "mov", "avi", "mkv", "mp3", "aac", "wav",
            "zip", "rar", "7z", "gz", "bz2", "xz",
            "docx", "xlsx", "pptx");

    private FileZipUtils() {
    }

    /**
     * 文件内容流打开器：按文件记录从其所属存储打开内容流
     */
    @FunctionalInterface
    public interface ContentStreamOpener {

        /**
         * 打开文件内容流（由调用方在拷贝完成后关闭）
         *
         * @param file 文件记录
         * @return 内容流，存储中不存在时返回 null
         * @throws Exception 打开失败时抛出
         */
        InputStream open(FileDO file) throws Exception;

    }

    /**
     * 打包进度回调：条目完成与条目内字节进展（大文件心跳）
     */
    public interface StreamProgressListener {

        /**
         * 条目处理完成回调
         *
         * @param file         文件记录
         * @param success      是否成功写入（false 表示内容不存在被跳过）
         * @param bytesWritten 实际写入 zip 的字节数（失败时为 0）
         */
        void onEntryProcessed(FileDO file, boolean success, long bytesWritten);

        /**
         * 条目内字节进展回调：单文件拷贝期间每累计约 32MB 回调一次，
         * 供任务表更新字节进度（兼作心跳与协作式取消检查点）
         *
         * @param deltaBytes 自上次回调以来新写出的字节数
         */
        void onBytesWritten(long deltaBytes);

    }

    /**
     * 文件内容获取失败（存储中不存在），failFast 模式下中止整体打包
     */
    public static final class FileLoadException extends RuntimeException {

        private final String fileName;

        public FileLoadException(String fileName) {
            super("文件获取失败：" + fileName);
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }

    }

    /**
     * 逐文件流式写入 zip 条目：固定缓冲区拷贝，内存占用与文件规模无关。
     * 每个条目按扩展名分流压缩级别（已压缩类型不二次压缩，节省 CPU）。
     *
     * @param files               文件记录（zip 内顺序与其一致）
     * @param zos                 zip 输出流
     * @param opener              内容流打开器
     * @param listener            进度回调，null 表示不关心
     * @param failFast            true：内容不存在的文件立即中止整体打包（异步任务，缺文件即失败）；
     *                            false：跳过该条目继续（同步流式下载的历史行为）
     * @param noCompressExtensions 不做二次压缩的扩展名集合（小写、不含点）
     */
    public static void writeZipEntries(List<FileDO> files, ZipOutputStream zos, ContentStreamOpener opener,
                                       StreamProgressListener listener, boolean failFast,
                                       Set<String> noCompressExtensions) throws Exception {
        Set<String> usedNames = new HashSet<>();
        for (FileDO file : files) {
            String entryName = buildUniqueEntryName(file, usedNames);
            // 打开内容流：不存在时按模式跳过或中止（此时尚未写入任何字节，zip 结构完整）
            InputStream in;
            try {
                in = opener.open(file);
            } catch (Exception e) {
                log.warn("[writeZipEntries][id({}) path({}) 打开失败: {}]", file.getId(), file.getPath(), e.getMessage());
                in = null;
            }
            if (in == null) {
                log.warn("[writeZipEntries][id({}) path({}) 文件内容不存在,已跳过]", file.getId(), file.getPath());
                if (listener != null) {
                    listener.onEntryProcessed(file, false, 0L);
                }
                if (failFast) {
                    throw new FileLoadException(entryName);
                }
                continue;
            }
            // 已压缩类型切换为不压缩级别：DEFLATE level 0 无需预知 size/CRC（真 STORED 需两遍读取），
            // CPU 收益接近且对任意长度流安全
            zos.setLevel(isNoCompress(entryName, noCompressExtensions)
                    ? Deflater.NO_COMPRESSION : Deflater.DEFAULT_COMPRESSION);
            zos.putNextEntry(new ZipEntry(entryName));
            long written = copyWithProgress(in, zos, listener);
            zos.closeEntry();
            if (listener != null) {
                listener.onEntryProcessed(file, true, written);
            }
        }
    }

    /**
     * 固定缓冲区拷贝并在累计约 32MB 时回报进度（大文件期间的心跳来源）
     */
    private static long copyWithProgress(InputStream in, ZipOutputStream zos, StreamProgressListener listener) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long total = 0L;
        long unreported = 0L;
        int len;
        while ((len = in.read(buffer)) != -1) {
            zos.write(buffer, 0, len);
            total += len;
            unreported += len;
            if (unreported >= PROGRESS_REPORT_BYTES) {
                if (listener != null) {
                    listener.onBytesWritten(unreported);
                }
                unreported = 0L;
            }
        }
        if (listener != null && unreported > 0) {
            listener.onBytesWritten(unreported);
        }
        return total;
    }

    private static boolean isNoCompress(String entryName, Set<String> noCompressExtensions) {
        Set<String> extensions = noCompressExtensions != null ? noCompressExtensions : DEFAULT_NO_COMPRESS_EXTENSIONS;
        return extensions.contains(StringUtils.lowerCase(Files.getFileExtension(entryName)));
    }

    /**
     * 生成 zip 内唯一文件名：取原文件名（为空回退到 path 文件名），同名自动加序号。
     * 文件名仅用于展示与保存（zip 条目名），不含目录穿越风险（不参与存储路径构造）
     */
    private static String buildUniqueEntryName(FileDO file, Set<String> usedNames) {
        String originName = StringUtils.isNotEmpty(file.getName()) ? file.getName() : new File(file.getPath()).getName();
        String mainName = Files.getNameWithoutExtension(originName);
        String extName = Files.getFileExtension(originName);
        // 同名条目自动加序号，避免 zip 内覆盖
        String candidate = originName;
        int seq = 1;
        while (!usedNames.add(candidate)) {
            candidate = StringUtils.isEmpty(extName)
                    ? mainName + " (" + seq + ")"
                    : mainName + " (" + seq + ")." + extName;
            seq++;
        }
        return candidate;
    }

}

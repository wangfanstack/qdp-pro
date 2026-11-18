
package com.agsccc.agriledger.framework.excel.core.util;

import com.agsccc.agriledger.framework.common.util.http.HttpUtils;
import com.agsccc.agriledger.framework.excel.core.handler.ColumnWidthMatchStyleStrategy;
import com.agsccc.agriledger.framework.excel.core.handler.SelectSheetWriteHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.converters.Converter;
import org.apache.fesod.sheet.converters.longconverter.LongStringConverter;
import org.apache.fesod.sheet.enums.CellDataTypeEnum;
import org.apache.fesod.sheet.read.listener.ReadListener;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Excel 工具类
 *
 * @author 芋道源码
 */
@Component
public class ExcelUtils {


    /**
     * Long 转 String 的转换器，避免 Long 类型丢失精度
     */
    private static final Converter<Long> LONG_STRING_CONVERTER = new LongStringConverter() {

        @Override
        public CellDataTypeEnum supportExcelTypeKey() {
            return null;
        }

    };

    /**
     * 将列表以 Excel 响应给前端
     *
     * @param response 响应
     * @param filename 文件名
     * @param sheetName Excel sheet 名
     * @param head Excel head 头
     * @param data 数据列表
     */
    public static <T> void write(HttpServletResponse response, String filename, String sheetName,
                                 Class<T> head, List<T> data) throws IOException {
        write(response, filename, sheetName, head, data, null);
    }

    /**
     * 将动态表头和数据以 Excel 响应给前端
     */
    public static <T> void write(HttpServletResponse response, String filename, String sheetName, Class<T> head, List<T> data, SelectSheetWriteHandler handler) throws IOException {
        var writer = FesodSheet.write(response.getOutputStream(), head)
                .autoCloseStream(false)
                .registerWriteHandler(new ColumnWidthMatchStyleStrategy());

        if (handler != null) {
            writer.registerWriteHandler(handler);
        }

        writer.registerConverter(LONG_STRING_CONVERTER).sheet(sheetName).doWrite(data);

        response.addHeader("Content-Disposition", "attachment;filename=" + HttpUtils.encodeUtf8(filename));
        response.setContentType("application/vnd.ms-excel;charset=UTF-8");
    }

    /**
     * 读取 Excel 数据
     */
    public static  <T> List<T> read(MultipartFile file, Class<T> head) throws IOException {
        if (file == null || file.isEmpty()) {
            return Collections.emptyList();
        }

        try (InputStream inputStream = file.getInputStream()) {
            return FesodSheet.read(inputStream, head, null)
                    .autoCloseStream(false)
                    .doReadAllSync();
        }
    }


    /**
     * 读取指定数量以内的 Excel 数据，达到上限后停止继续解析
     */
    public <T> List<T> read(MultipartFile file, Class<T> head,
                            int maxRowCount) throws IOException {
        if (file == null || file.isEmpty()) {
            return Collections.emptyList();
        }

        if (maxRowCount <= 0) {
            throw new IllegalArgumentException("maxRowCount must be greater than 0");
        }

        List<T> rows = new ArrayList<>(Math.min(maxRowCount, 1024));

        try (InputStream inputStream = file.getInputStream()) {
            FesodSheet.read(inputStream, head, new ReadListener<T>() {

                @Override
                public void invoke(T data, AnalysisContext context) {
                    rows.add(data);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 无需处理
                }

                @Override
                public boolean hasNext(AnalysisContext context) {
                    return rows.size() < maxRowCount;
                }

            }).autoCloseStream(false).doReadAll();
        }

        return rows;
    }

    /**
     * 读取动态表头 Excel 数据
     */
    public List<Map<Integer, String>> read(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Collections.emptyList();
        }

        try (InputStream inputStream = file.getInputStream()) {
            return FesodSheet.read(inputStream)
                    .autoCloseStream(false)
                    .headRowNumber(1)
                    .sheet()
                    .doReadSync();
        }
    }

}

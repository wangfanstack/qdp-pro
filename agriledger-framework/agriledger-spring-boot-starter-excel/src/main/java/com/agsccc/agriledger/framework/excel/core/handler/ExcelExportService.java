package com.agsccc.agriledger.framework.excel.core.handler;

import com.agsccc.agriledger.framework.excel.core.util.ExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 表格导出服务
 *
 * @author Sail
 * @date 2026/11/17 16:04
 */
@Component
public class ExcelExportService {

    private final ApplicationContext applicationContext;

    public ExcelExportService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public <T> void write(HttpServletResponse response, String filename, String sheetName, Class<T> head, List<T> data) throws IOException {
        SelectSheetWriteHandler handler = new SelectSheetWriteHandler(head, applicationContext);

        ExcelUtils.write(response, filename, sheetName, head, data, handler);
    }

}
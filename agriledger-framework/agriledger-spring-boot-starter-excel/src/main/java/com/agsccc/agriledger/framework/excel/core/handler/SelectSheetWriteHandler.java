
package com.agsccc.agriledger.framework.excel.core.handler;

import com.agsccc.agriledger.framework.common.core.KeyValue;
import com.agsccc.agriledger.framework.dict.core.DictFrameworkUtils;
import com.agsccc.agriledger.framework.excel.core.annotations.ExcelColumnSelect;
import com.agsccc.agriledger.framework.excel.core.function.ExcelColumnSelectFunction;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.fesod.sheet.annotation.ExcelIgnore;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.write.handler.SheetWriteHandler;
import org.apache.fesod.sheet.write.metadata.holder.WriteSheetHolder;
import org.apache.fesod.sheet.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.hssf.usermodel.HSSFDataValidation;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.agsccc.agriledger.framework.common.util.collection.CollectionUtils.convertList;

/**
 * 基于固定 sheet 实现下拉框
 *
 * @author HUIHUI
 */
public class SelectSheetWriteHandler implements SheetWriteHandler {

    /**
     * 数据起始行从 0 开始
     */
    public static final int FIRST_ROW = 1;

    /**
     * 下拉列需要创建下拉框的行数
     */
    public static final int LAST_ROW = 2000;

    private static final String DICT_SHEET_NAME = "字典sheet";

    /**
     * key: 列 value: 下拉数据源
     */
    private final Map<Integer, List<String>> selectMap = new HashMap<>();

    public SelectSheetWriteHandler(Class<?> head, ApplicationContext applicationContext) {
        // 解析下拉数据
        int colIndex = 0;
        boolean ignoreUnannotated = head.isAnnotationPresent(ExcelIgnoreUnannotated.class);

        for (Field field : head.getDeclaredFields()) {
            // 忽略 static final 或 transient 的字段
            if (isStaticFinalOrTransient(field)) {
                continue;
            }

            // 忽略未标注或被忽略的字段
            if ((ignoreUnannotated && !field.isAnnotationPresent(ExcelProperty.class))
                    || field.isAnnotationPresent(ExcelIgnore.class)) {
                continue;
            }

            // 处理有 ExcelColumnSelect 注解的字段
            if (field.isAnnotationPresent(ExcelColumnSelect.class)) {
                ExcelProperty excelProperty = field.getAnnotation(ExcelProperty.class);
                if (excelProperty != null && excelProperty.index() != -1) {
                    colIndex = excelProperty.index();
                }
                getSelectDataList(colIndex, field, applicationContext);
            }
            colIndex++;
        }
    }

    /**
     * 判断字段是否是静态的、最终的、transient 的
     */
    private boolean isStaticFinalOrTransient(Field field) {
        return (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()))
                || Modifier.isTransient(field.getModifiers());
    }

    /**
     * 获得下拉数据，并添加到 selectMap 中
     */
    private void getSelectDataList(int colIndex, Field field, ApplicationContext applicationContext) {
        ExcelColumnSelect columnSelect = field.getAnnotation(ExcelColumnSelect.class);
        String dictType = columnSelect.dictType();
        String functionName = columnSelect.functionName();

        Validate.isTrue(StringUtils.isNotEmpty(dictType) || StringUtils.isNotEmpty(functionName),
                "Field(%s) 的 @ExcelColumnSelect 注解，dictType 和 functionName 不能同时为空",
                field.getName());

        // 情况一：使用 dictType 获得下拉数据
        if (StringUtils.isNotEmpty(dictType)) {
            selectMap.put(colIndex, DictFrameworkUtils.getDictDataLabelList(dictType));
            return;
        }

        // 情况二：使用 functionName 获得下拉数据
        Map<String, ExcelColumnSelectFunction> functionMap =
                applicationContext.getBeansOfType(ExcelColumnSelectFunction.class);

        ExcelColumnSelectFunction function = functionMap.values().stream()
                .filter(item -> Objects.equals(item.getName(), functionName))
                .findFirst()
                .orElse(null);

        Validate.notNull(function, "未找到对应的 function(%s)", functionName);
        selectMap.put(colIndex, function.getOptions());
    }

    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder,
                                 WriteSheetHolder writeSheetHolder) {
        if (selectMap.isEmpty()) {
            return;
        }

        // 1. 获取相应操作对象
        DataValidationHelper helper = writeSheetHolder.getSheet().getDataValidationHelper();
        Workbook workbook = writeWorkbookHolder.getWorkbook();

        List<KeyValue<Integer, List<String>>> keyValues =
                convertList(selectMap.entrySet(),
                        entry -> new KeyValue<>(entry.getKey(), entry.getValue()));

        // 空字典/空选项源跳过该列下拉
        keyValues.removeIf(item -> item.getValue() == null || item.getValue().isEmpty());

        if (keyValues.isEmpty()) {
            return;
        }

        // 升序排序
        keyValues.sort(Comparator.comparing(item -> item.getValue().size()));

        // 2. 创建数据字典的 sheet 页
        Sheet dictSheet = workbook.createSheet(DICT_SHEET_NAME);
        for (KeyValue<Integer, List<String>> keyValue : keyValues) {
            int rowLength = keyValue.getValue().size();

            // 2.1 设置字典 sheet 页的值，每一列一个字典项
            for (int i = 0; i < rowLength; i++) {
                Row row = dictSheet.getRow(i);
                if (row == null) {
                    row = dictSheet.createRow(i);
                }
                row.createCell(keyValue.getKey()).setCellValue(keyValue.getValue().get(i));
            }

            // 2.2 设置单元格下拉选择
            setColumnSelect(writeSheetHolder, workbook, helper, keyValue);
        }
    }

    /**
     * 设置单元格下拉选择
     */
    private static void setColumnSelect(WriteSheetHolder writeSheetHolder,
                                        Workbook workbook,
                                        DataValidationHelper helper,
                                        KeyValue<Integer, List<String>> keyValue) {
        // 1. 创建可被其他单元格引用的名称
        Name name = workbook.createName();
        String excelColumn = CellReference.convertNumToColString(keyValue.getKey());

        // 2. 下拉框数据来源
        String refers = DICT_SHEET_NAME + "!$" + excelColumn + "$1:$"
                + excelColumn + "$" + keyValue.getValue().size();

        name.setNameName("dict" + keyValue.getKey());
        name.setRefersToFormula(refers);

        // 3. 设置约束
        DataValidationConstraint constraint =
                helper.createFormulaListConstraint("dict" + keyValue.getKey());

        CellRangeAddressList rangeAddressList = new CellRangeAddressList(
                FIRST_ROW, LAST_ROW, keyValue.getKey(), keyValue.getKey());

        DataValidation validation = helper.createValidation(constraint, rangeAddressList);

        if (validation instanceof HSSFDataValidation) {
            validation.setSuppressDropDownArrow(false);
        } else {
            validation.setSuppressDropDownArrow(true);
            validation.setShowErrorBox(true);
        }

        // 4. 阻止输入非下拉框的值
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("提示", "此值不存在于下拉选择中！");

        // 5. 添加下拉框约束
        writeSheetHolder.getSheet().addValidationData(validation);
    }
}

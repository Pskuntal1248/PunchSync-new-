package com.Shreejifacilities.report_generator.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class AttendanceSummaryService {


    private static final double OVERTIME_THRESHOLD = 9.0;
    private static final double HALF_SHIFT_MIN_HOURS = 5.0;
    private static final int DEFAULT_NIGHT_SHIFT_CUTOFF = 4;
    private static final int DUPLICATE_PUNCH_WINDOW_MINUTES = 30;
    private static final Set<String> KAROL_BAGH_NIGHT_SHIFT_IDS = new HashSet<>(Arrays.asList("88023", "87140"));

    private static class Punch {
        String site, empKey;
        Date punchTime;
        Punch(String site, String empKey, Date punchTime) { this.site = site; this.empKey = empKey; this.punchTime = punchTime; }
    }

    private static class Totals {
        int punches = 0, days = 0, full = 0, half = 0, missing = 0;
        double hours = 0.0, ot = 0.0, dutyUnits = 0.0;
    }

    public byte[] generateExcelReport(InputStream inputStream, int reportYear, int reportMonth) throws Exception {
        List<Punch> allPunches = readPunchesFromStream(inputStream);
        Map<String, Map<String, Map<String, List<Date>>>> siteData = groupPunchesByLogicalDay(allPunches, reportYear, reportMonth - 1);
        String reportMonthName = new SimpleDateFormat("MMMM yyyy").format(new GregorianCalendar(reportYear, reportMonth - 1, 1).getTime());

        Map<String, Object> dataFor8HourShift = calculateAttendanceData(siteData, 8.0);
        Map<String, Object> dataFor9HourShift = calculateAttendanceData(siteData, 9.0);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            generateReportSheet(workbook, "Summary (8-Hour Shift)", reportMonthName, dataFor8HourShift);
            generateReportSheet(workbook, "Summary (9-Hour Shift)", reportMonthName, dataFor9HourShift);
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    public String generateJsonReport(InputStream inputStream, int reportYear, int reportMonth) throws Exception {
        List<Punch> allPunches = readPunchesFromStream(inputStream);
        Map<String, Map<String, Map<String, List<Date>>>> siteData = groupPunchesByLogicalDay(allPunches, reportYear, reportMonth - 1);
        String reportMonthName = new SimpleDateFormat("MMMM yyyy").format(new GregorianCalendar(reportYear, reportMonth - 1, 1).getTime());

        Map<String, Object> dataFor8HourShift = calculateAttendanceData(siteData, 8.0);
        Map<String, Object> dataFor9HourShift = calculateAttendanceData(siteData, 9.0);

        Map<String, Object> finalJsonData = new LinkedHashMap<>();
        finalJsonData.put("reportMonth", reportMonthName);
        Map<String, Object> shiftCalculations = new LinkedHashMap<>();
        shiftCalculations.put("8-Hour Shift", dataFor8HourShift);
        shiftCalculations.put("9-Hour Shift", dataFor9HourShift);
        finalJsonData.put("shiftCalculations", shiftCalculations);

        return formatDataAsJson(finalJsonData);
    }

    private Map<String, Object> calculateAttendanceData(Map<String, Map<String, Map<String, List<Date>>>> siteData, double fullShiftHours) {
        Map<String, List<Map<String, Object>>> siteEmployeeData = new TreeMap<>();
        Map<String, Totals> siteTotals = new TreeMap<>();
        for (String site : siteData.keySet()) {
            List<Map<String, Object>> employeeList = new ArrayList<>();
            Map<String, Map<String, List<Date>>> empMap = siteData.get(site);
            for (String empKey : new TreeSet<>(empMap.keySet())) {
                Map<String, List<Date>> datePunches = empMap.get(empKey);
                int punches = 0, full = 0, half = 0;
                double hours = 0, ot = 0;
                List<String> missingDates = new ArrayList<>();
                for (String date : new TreeSet<>(datePunches.keySet())) {
                    List<Date> punchesOnDay = new ArrayList<>(datePunches.get(date));
                    punchesOnDay.sort(Comparator.naturalOrder());
                    List<Date> cleaned = new ArrayList<>();
                    if (!punchesOnDay.isEmpty()) {
                        cleaned.add(punchesOnDay.get(0));
                        for (int i = 1; i < punchesOnDay.size(); i++) {
                            long diff = punchesOnDay.get(i).getTime() - cleaned.get(cleaned.size() - 1).getTime();
                            if (diff > DUPLICATE_PUNCH_WINDOW_MINUTES * 60 * 1000) cleaned.add(punchesOnDay.get(i));
                        }
                    }
                    punches += cleaned.size();
                    if (cleaned.size() < 2) {
                        missingDates.add(date.substring(8)); continue;
                    }
                    double duration = (cleaned.get(cleaned.size() - 1).getTime() - cleaned.get(0).getTime()) / 3_600_000.0;
                    hours += duration;
                    if (duration >= fullShiftHours) {
                        full++; if (duration > OVERTIME_THRESHOLD) ot += duration - OVERTIME_THRESHOLD;
                    } else if (duration >= HALF_SHIFT_MIN_HOURS) {
                        half++;
                    }
                }
                String[] parts = empKey.split("::", 2);
                Map<String, Object> empData = new LinkedHashMap<>();
                empData.put("empId", parts[0]); empData.put("name", parts[1]);
                empData.put("punches", punches); empData.put("days", datePunches.size());
                empData.put("hours", String.format("%.2f", hours)); empData.put("fullDays", full);
                empData.put("halfDays", half); empData.put("overtimeHours", String.format("%.2f", ot));
                empData.put("dutyUnits", String.format("%.2f", full + (half / 2.0)));
                empData.put("missingPunchDays", missingDates);
                employeeList.add(empData);
                Totals t = siteTotals.computeIfAbsent(site, k -> new Totals());
                t.punches += punches; t.days += datePunches.size(); t.hours += hours; t.full += full;
                t.half += half; t.ot += ot; t.dutyUnits += (full + (half / 2.0)); t.missing += missingDates.size();
            }
            siteEmployeeData.put(site, employeeList);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sites", siteEmployeeData);
        result.put("summaries", siteTotals);
        return result;
    }

    private void generateReportSheet(Workbook workbook, String sheetName, String reportMonthName, Map<String, Object> calculatedData) {
        Map<String, List<Map<String, Object>>> siteEmployeeData = (Map<String, List<Map<String, Object>>>) calculatedData.get("sites");
        Map<String, Totals> siteTotals = (Map<String, Totals>) calculatedData.get("summaries");
        double fullShiftHours = sheetName.contains("8-Hour") ? 8.0 : 9.0;
        Sheet sheet = workbook.createSheet(sheetName);
        int rowNum = 0;
        CellStyle titleStyle = createTitleStyle(workbook), headerStyle = createHeaderStyle(workbook), siteTitleStyle = createSiteTitleStyle(workbook);
        CellStyle defaultStyle = createDefaultStyle(workbook), altStyle = createAlternateStyle(workbook), totalStyle = createTotalStyle(workbook);
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(30);
        createCell(titleRow, 0, "Final Attendance Summary for " + reportMonthName + " (" + (int)fullShiftHours + "-Hour Shift)", titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));
        rowNum++;
        String[] headers = {"EmpID", "Name", "Punches", "Days", "Hours", "Full", "Half", "OT", "Duty", "Missing"};
        for (String site : new TreeSet<>(siteEmployeeData.keySet())) {
            Row siteTitleRow = sheet.createRow(rowNum++);
            createCell(siteTitleRow, 0, "Site: " + site, siteTitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 9));
            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.length; i++) createCell(headerRow, i, headers[i], headerStyle);
            List<Map<String, Object>> employeeList = siteEmployeeData.get(site);
            boolean isEvenRow = false;
            for (Map<String, Object> empData : employeeList) {
                Row dataRow = sheet.createRow(rowNum++);
                CellStyle currentStyle = isEvenRow ? altStyle : defaultStyle;
                createCell(dataRow, 0, (String) empData.get("empId"), currentStyle);
                createCell(dataRow, 1, (String) empData.get("name"), currentStyle);
                createCell(dataRow, 2, (int) empData.get("punches"), currentStyle);
                createCell(dataRow, 3, (int) empData.get("days"), currentStyle);
                createCell(dataRow, 4, (String) empData.get("hours"), currentStyle);
                createCell(dataRow, 5, (int) empData.get("fullDays"), currentStyle);
                createCell(dataRow, 6, (int) empData.get("halfDays"), currentStyle);
                createCell(dataRow, 7, (String) empData.get("overtimeHours"), currentStyle);
                createCell(dataRow, 8, (String) empData.get("dutyUnits"), currentStyle);
                List<String> missing = (List<String>) empData.get("missingPunchDays");
                createCell(dataRow, 9, missing.isEmpty() ? "-" : String.join(", ", missing), currentStyle);
                isEvenRow = !isEvenRow;
            }
            rowNum++;
        }
        rowNum++;
        Row summaryTitleRow = sheet.createRow(rowNum++);
        createCell(summaryTitleRow, 0, "Site-wise Summary", siteTitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 9));
        String[] summaryHeaders = {"Site", "Punches", "Days", "Hours", "Full", "Half", "OT", "Duty", "Missing"};
        Row summaryHeaderRow = sheet.createRow(rowNum++);
        for(int i = 0; i < summaryHeaders.length; i++) createCell(summaryHeaderRow, i, summaryHeaders[i], headerStyle);
        Totals grandTotal = new Totals();
        boolean isEvenRow = false;
        for (String site : new TreeSet<>(siteTotals.keySet())) {
            Totals t = siteTotals.get(site);
            Row dataRow = sheet.createRow(rowNum++);
            CellStyle currentStyle = isEvenRow ? altStyle : defaultStyle;
            createCell(dataRow, 0, site, currentStyle);
            createCell(dataRow, 1, t.punches, currentStyle); createCell(dataRow, 2, t.days, currentStyle);
            createCell(dataRow, 3, String.format("%.2f", t.hours), currentStyle); createCell(dataRow, 4, t.full, currentStyle);
            createCell(dataRow, 5, t.half, currentStyle); createCell(dataRow, 6, String.format("%.2f", t.ot), currentStyle);
            createCell(dataRow, 7, String.format("%.2f", t.dutyUnits), currentStyle); createCell(dataRow, 8, t.missing, currentStyle);
            grandTotal.punches += t.punches; grandTotal.days += t.days; grandTotal.hours += t.hours;
            grandTotal.full += t.full; grandTotal.half += t.half; grandTotal.ot += t.ot;
            grandTotal.dutyUnits += t.dutyUnits; grandTotal.missing += t.missing;
            isEvenRow = !isEvenRow;
        }
        Row totalRow = sheet.createRow(rowNum++);
        createCell(totalRow, 0, "GRAND TOTAL", totalStyle);
        createCell(totalRow, 1, grandTotal.punches, totalStyle); createCell(totalRow, 2, grandTotal.days, totalStyle);
        createCell(totalRow, 3, String.format("%.2f", grandTotal.hours), totalStyle); createCell(totalRow, 4, grandTotal.full, totalStyle);
        createCell(totalRow, 5, grandTotal.half, totalStyle); createCell(totalRow, 6, String.format("%.2f", grandTotal.ot), totalStyle);
        createCell(totalRow, 7, String.format("%.2f", grandTotal.dutyUnits), totalStyle); createCell(totalRow, 8, grandTotal.missing, totalStyle);
        for (int i = 0; i <= 9; i++) sheet.autoSizeColumn(i);
    }

    
    private List<Punch> readPunchesFromStream(InputStream inputStream) throws Exception {
        List<Punch> allPunches = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (!sheet.rowIterator().hasNext()) return Collections.emptyList();
            Map<String, Integer> columnIndex = new HashMap<>();
            for (Cell cell : sheet.rowIterator().next()) columnIndex.put(cell.getStringCellValue().trim(), cell.getColumnIndex());
            if (!columnIndex.containsKey("DeviceName") || !columnIndex.containsKey("IDNo") || !columnIndex.containsKey("Name") || !columnIndex.containsKey("PunchTime")) {
                throw new IllegalArgumentException("A required column is missing.");
            }
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                if (row.getCell(columnIndex.get("DeviceName")) == null || row.getCell(columnIndex.get("DeviceName")).getCellType() == CellType.BLANK) continue;
                String site = row.getCell(columnIndex.get("DeviceName")).getStringCellValue().trim();
                String name = row.getCell(columnIndex.get("Name")).getStringCellValue().trim();
                String empId = "";
                Cell idCell = row.getCell(columnIndex.get("IDNo"));
                if (idCell.getCellType() == CellType.NUMERIC) empId = String.valueOf((long) idCell.getNumericCellValue());
                else empId = idCell.getStringCellValue().trim();
                String empKey = empId + "::" + name;
                Date punchTime = null;
                Cell punchCell = row.getCell(columnIndex.get("PunchTime"));
                if (punchCell != null) {
                    try {
                        if (punchCell.getCellType() == CellType.STRING) punchTime = new SimpleDateFormat("M/d/yy H:mm").parse(punchCell.getStringCellValue().trim());
                        else if (DateUtil.isCellDateFormatted(punchCell)) punchTime = punchCell.getDateCellValue();
                    } catch (Exception e) {}
                }
                if (punchTime != null) allPunches.add(new Punch(site, empKey, punchTime));
            }
        }
        return allPunches;
    }
    private Map<String, Map<String, Map<String, List<Date>>>> groupPunchesByLogicalDay(List<Punch> allPunches, int reportYear, int reportCalendarMonth) {
        Map<String, Map<String, Map<String, List<Date>>>> siteData = new HashMap<>();
        for (Punch punch : allPunches) {
            Calendar shiftDateCal = Calendar.getInstance();
            shiftDateCal.setTime(punch.punchTime);
            String empId = punch.empKey.split("::")[0];
            if ("Karol Bagh".equalsIgnoreCase(punch.site) && KAROL_BAGH_NIGHT_SHIFT_IDS.contains(empId)) {
                if (shiftDateCal.get(Calendar.HOUR_OF_DAY) < 16) shiftDateCal.add(Calendar.DATE, -1);
            } else {
                if (shiftDateCal.get(Calendar.HOUR_OF_DAY) < DEFAULT_NIGHT_SHIFT_CUTOFF) shiftDateCal.add(Calendar.DATE, -1);
            }
            if (shiftDateCal.get(Calendar.MONTH) == reportCalendarMonth && shiftDateCal.get(Calendar.YEAR) == reportYear) {
                String punchDate = new SimpleDateFormat("yyyy-MM-dd").format(shiftDateCal.getTime());
                siteData.computeIfAbsent(punch.site, k -> new TreeMap<>())
                        .computeIfAbsent(punch.empKey, k -> new TreeMap<>())
                        .computeIfAbsent(punchDate, k -> new ArrayList<>())
                        .add(punch.punchTime);
            }
        }
        return siteData;
    }
    private String formatDataAsJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        formatMap(data, sb, 0);
        return sb.toString();
    }
    private void formatMap(Map<String, Object> map, StringBuilder sb, int indent) {
        sb.append("{\n");
        Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            addIndent(sb, indent + 1).append("\"").append(entry.getKey()).append("\": ");
            formatValue(entry.getValue(), sb, indent + 1);
            if (iterator.hasNext()) sb.append(",");
            sb.append("\n");
        }
        addIndent(sb, indent).append("}");
    }
    private void formatList(List<?> list, StringBuilder sb, int indent) {
        sb.append("[\n");
        Iterator<?> iterator = list.iterator();
        while (iterator.hasNext()) {
            addIndent(sb, indent + 1);
            formatValue(iterator.next(), sb, indent + 1);
            if (iterator.hasNext()) sb.append(",");
            sb.append("\n");
        }
        addIndent(sb, indent).append("]");
    }
    private void formatValue(Object value, StringBuilder sb, int indent) {
        if (value instanceof Map) {
            formatMap((Map<String, Object>) value, sb, indent);
        } else if (value instanceof List) {
            formatList((List<?>) value, sb, indent);
        } else if (value instanceof String) {
            sb.append("\"").append(value).append("\"");
        } else if (value instanceof Totals) {
            Totals t = (Totals) value;
            sb.append("{\n");
            addIndent(sb, indent + 1).append("\"totalPunches\": ").append(t.punches).append(",\n");
            addIndent(sb, indent + 1).append("\"totalDays\": ").append(t.days).append(",\n");
            addIndent(sb, indent + 1).append("\"totalHours\": \"").append(String.format("%.2f", t.hours)).append("\",\n");
            addIndent(sb, indent + 1).append("\"totalFullDays\": ").append(t.full).append(",\n");
            addIndent(sb, indent + 1).append("\"totalHalfDays\": ").append(t.half).append(",\n");
            addIndent(sb, indent + 1).append("\"totalOvertimeHours\": \"").append(String.format("%.2f", t.ot)).append("\",\n");
            addIndent(sb, indent + 1).append("\"totalDutyUnits\": \"").append(String.format("%.2f", t.dutyUnits)).append("\",\n");
            addIndent(sb, indent + 1).append("\"totalMissingDays\": ").append(t.missing).append("\n");
            addIndent(sb, indent).append("}");
        } else {
            sb.append(value);
        }
    }
    private StringBuilder addIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
        return sb;
    }
    private CellStyle createTitleStyle(Workbook wb) { XSSFFont f = (XSSFFont) wb.createFont(); f.setFontHeightInPoints((short) 18); f.setBold(true); f.setColor(IndexedColors.DARK_BLUE.getIndex()); CellStyle s = wb.createCellStyle(); s.setFont(f); s.setAlignment(HorizontalAlignment.CENTER); s.setVerticalAlignment(VerticalAlignment.CENTER); return s; }
    private CellStyle createHeaderStyle(Workbook wb) { XSSFFont f = (XSSFFont) wb.createFont(); f.setFontHeightInPoints((short) 11); f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex()); CellStyle s = wb.createCellStyle(); s.setFont(f); s.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex()); s.setFillPattern(FillPatternType.SOLID_FOREGROUND); s.setAlignment(HorizontalAlignment.CENTER); s.setBorderBottom(BorderStyle.THIN); s.setBorderTop(BorderStyle.THIN); s.setBorderLeft(BorderStyle.THIN); s.setBorderRight(BorderStyle.THIN); return s; }
    private CellStyle createSiteTitleStyle(Workbook wb) { XSSFFont f = (XSSFFont) wb.createFont(); f.setFontHeightInPoints((short) 14); f.setBold(true); f.setColor(IndexedColors.DARK_TEAL.getIndex()); CellStyle s = wb.createCellStyle(); s.setFont(f); s.setAlignment(HorizontalAlignment.LEFT); return s; }
    private CellStyle createDefaultStyle(Workbook wb) { CellStyle s = wb.createCellStyle(); s.setBorderBottom(BorderStyle.THIN); s.setBorderLeft(BorderStyle.THIN); s.setBorderRight(BorderStyle.THIN); s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex()); s.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex()); s.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex()); return s; }
    private CellStyle createAlternateStyle(Workbook wb) { CellStyle s = createDefaultStyle(wb); s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); s.setFillPattern(FillPatternType.SOLID_FOREGROUND); return s; }
    private CellStyle createTotalStyle(Workbook wb) { XSSFFont f = (XSSFFont) wb.createFont(); f.setBold(true); CellStyle s = createDefaultStyle(wb); s.setFont(f); s.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex()); s.setFillPattern(FillPatternType.SOLID_FOREGROUND); return s; }
    private void createCell(Row r, int c, String v, CellStyle s) { Cell cell = r.createCell(c); cell.setCellValue(v); if (s != null) cell.setCellStyle(s); }
    private void createCell(Row r, int c, int v, CellStyle s) { Cell cell = r.createCell(c); cell.setCellValue(v); if (s != null) cell.setCellStyle(s); }
}

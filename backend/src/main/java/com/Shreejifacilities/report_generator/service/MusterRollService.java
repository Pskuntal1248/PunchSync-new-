package com.Shreejifacilities.report_generator.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class MusterRollService {


    private static final double FULL_SHIFT_HOURS = 8.0;
    private static final double HALF_SHIFT_MIN_HOURS = 5.0;
    private static final int DEFAULT_NIGHT_SHIFT_CUTOFF = 4;
    private static final int DUPLICATE_PUNCH_WINDOW_MINUTES = 30;
    private static final Set<String> KAROL_BAGH_NIGHT_SHIFT_IDS = new HashSet<>(Arrays.asList("88023", "87140"));
    private static final int KAROL_BAGH_NIGHT_SHIFT_CUTOFF = 16;

    private static class Punch {
        String site, empKey;
        Date punchTime;
        Punch(String site, String empKey, Date punchTime) { this.site = site; this.empKey = empKey; this.punchTime = punchTime; }
    }

    public byte[] generateExcelReport(InputStream inputStream, int reportYear, int reportMonth) throws Exception {
        List<Punch> allPunches = readPunchesFromStream(inputStream);
        if (allPunches.isEmpty()) {
            throw new IllegalArgumentException("No valid punch data found in the uploaded file.");
        }

        Map<String, Map<String, Map<String, List<Date>>>> siteData = groupPunchesByLogicalDay(allPunches, reportYear, reportMonth - 1);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (String siteName : new TreeSet<>(siteData.keySet())) {
                Map<String, Object> calculatedData = calculateMusterRollData(siteData.get(siteName), reportYear, reportMonth);
                generateMusterRollSheet(workbook, siteName, calculatedData, reportYear, reportMonth - 1);
            }
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    public String generateJsonReport(InputStream inputStream, int reportYear, int reportMonth) throws Exception {
        List<Punch> allPunches = readPunchesFromStream(inputStream);
        if (allPunches.isEmpty()) {
            throw new IllegalArgumentException("No valid punch data found in the uploaded file.");
        }

        Map<String, Map<String, Map<String, List<Date>>>> siteData = groupPunchesByLogicalDay(allPunches, reportYear, reportMonth - 1);
        String monthName = YearMonth.of(reportYear, reportMonth).getMonth().name();

        Map<String, Object> allSitesCalculatedData = new LinkedHashMap<>();
        for (String siteName : new TreeSet<>(siteData.keySet())) {
            Map<String, Object> calculatedData = calculateMusterRollData(siteData.get(siteName), reportYear, reportMonth);
            allSitesCalculatedData.put(siteName, calculatedData);
        }

        Map<String, Object> finalJson = new LinkedHashMap<>();
        finalJson.put("reportMonth", String.format("%s %d", monthName, reportYear));
        finalJson.put("sites", allSitesCalculatedData);

        return formatDataAsJson(finalJson);
    }

    private List<Punch> readPunchesFromStream(InputStream inputStream) throws Exception {
        List<Punch> allPunches = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();
            if (!rows.hasNext()) { return Collections.emptyList(); }

            Row header = rows.next();
            Map<String, Integer> columnIndex = new HashMap<>();
            for (Cell cell : header) {
                columnIndex.put(cell.getStringCellValue().trim(), cell.getColumnIndex());
            }

            if (!columnIndex.containsKey("DeviceName") || !columnIndex.containsKey("IDNo") ||
                    !columnIndex.containsKey("Name") || !columnIndex.containsKey("PunchTime")) {
                throw new IllegalArgumentException("A required column (DeviceName, IDNo, Name, or PunchTime) is missing.");
            }

            int siteCol = columnIndex.get("DeviceName");
            int idCol = columnIndex.get("IDNo");
            int nameCol = columnIndex.get("Name");
            int punchCol = columnIndex.get("PunchTime");

            while (rows.hasNext()) {
                Row row = rows.next();
                if (row.getCell(siteCol) == null || row.getCell(siteCol).getCellType() == CellType.BLANK) continue;

                String site = row.getCell(siteCol).getStringCellValue().trim();
                String empId = "";
                Cell idCell = row.getCell(idCol);
                if (idCell.getCellType() == CellType.NUMERIC) {
                    empId = String.valueOf((long) idCell.getNumericCellValue());
                } else {
                    empId = idCell.getStringCellValue().trim();
                }
                String name = row.getCell(nameCol).getStringCellValue().trim();
                String empKey = empId + "::" + name;

                Date punchTime = null;
                Cell punchCell = row.getCell(punchCol);
                if (punchCell != null) {
                    try {
                        if (punchCell.getCellType() == CellType.STRING) {
                            punchTime = new SimpleDateFormat("M/d/yy H:mm").parse(punchCell.getStringCellValue().trim());
                        } else if (DateUtil.isCellDateFormatted(punchCell)) {
                            punchTime = punchCell.getDateCellValue();
                        }
                    } catch (Exception e) { /* Ignore */ }
                }

                if (punchTime != null) {
                    allPunches.add(new Punch(site, empKey, punchTime));
                }
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
                if (shiftDateCal.get(Calendar.HOUR_OF_DAY) < KAROL_BAGH_NIGHT_SHIFT_CUTOFF) shiftDateCal.add(Calendar.DATE, -1);
            } else {
                if (shiftDateCal.get(Calendar.HOUR_OF_DAY) < DEFAULT_NIGHT_SHIFT_CUTOFF) shiftDateCal.add(Calendar.DATE, -1);
            }
            if (shiftDateCal.get(Calendar.MONTH) != reportCalendarMonth || shiftDateCal.get(Calendar.YEAR) != reportYear) continue;
            String punchDate = new SimpleDateFormat("yyyy-MM-dd").format(shiftDateCal.getTime());
            siteData.computeIfAbsent(punch.site, k -> new TreeMap<>())
                    .computeIfAbsent(punch.empKey, k -> new TreeMap<>())
                    .computeIfAbsent(punchDate, k -> new ArrayList<>())
                    .add(punch.punchTime);
        }
        return siteData;
    }

    private Map<String, Object> calculateMusterRollData(Map<String, Map<String, List<Date>>> empData, int year, int month) {
        List<Map<String, Object>> employeeResults = new ArrayList<>();
        double siteTotalAttendance = 0;
        int siteTotalHalfDays = 0;
        int siteTotalMissing = 0;
        YearMonth yearMonthObject = YearMonth.of(year, month);
        int daysInMonth = yearMonthObject.lengthOfMonth();
        Set<Integer> weeklyOffDays = getSundaysForMonth(year, month);
        for (String empKey : new TreeSet<>(empData.keySet())) {
            Map<String, Object> employeeData = new LinkedHashMap<>();
            String[] parts = empKey.split("::", 2);
            employeeData.put("empId", parts[0]);
            employeeData.put("name", parts[1]);
            double empTotalAttendance = 0;
            List<String> dailyStatusList = new ArrayList<>();
            Map<String, List<Date>> datePunches = empData.get(empKey);
            for (int day = 1; day <= daysInMonth; day++) {
                String dateStr = String.format("%d-%02d-%02d", year, month, day);
                String status = "A";
                if (datePunches.containsKey(dateStr)) {
                    List<Date> punchesOnDay = new ArrayList<>(datePunches.get(dateStr));
                    punchesOnDay.sort(Comparator.naturalOrder());
                    List<Date> cleaned = new ArrayList<>();
                    if (!punchesOnDay.isEmpty()) {
                        cleaned.add(punchesOnDay.get(0));
                        for (int i = 1; i < punchesOnDay.size(); i++) {
                            long diff = punchesOnDay.get(i).getTime() - cleaned.get(cleaned.size() - 1).getTime();
                            if (diff > DUPLICATE_PUNCH_WINDOW_MINUTES * 60 * 1000) cleaned.add(punchesOnDay.get(i));
                        }
                    }
                    if (cleaned.size() < 2) {
                        status = "M"; siteTotalMissing++;
                    } else {
                        double duration = (cleaned.get(cleaned.size() - 1).getTime() - cleaned.get(0).getTime()) / 3_600_000.0;
                        if (duration >= FULL_SHIFT_HOURS) {
                            status = "P"; empTotalAttendance += 1.0;
                        } else if (duration >= HALF_SHIFT_MIN_HOURS) {
                            status = "H"; empTotalAttendance += 0.5; siteTotalHalfDays++;
                        } else {
                            status = "M"; siteTotalMissing++;
                        }
                    }
                }
                if (weeklyOffDays.contains(day) && "A".equals(status)) status = "WO";
                dailyStatusList.add(status);
            }
            employeeData.put("totalAttendance", empTotalAttendance);
            employeeData.put("dailyStatus", dailyStatusList);
            employeeResults.add(employeeData);
            siteTotalAttendance += empTotalAttendance;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalSiteAttendance", siteTotalAttendance);
        summary.put("totalHalfDays", siteTotalHalfDays);
        summary.put("totalMissingPunches", siteTotalMissing);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employees", employeeResults);
        result.put("summary", summary);
        return result;
    }

    private void generateMusterRollSheet(Workbook workbook, String siteName, Map<String, Object> calculatedData, int year, int month) {
        Sheet sheet = workbook.createSheet(siteName);
        Map<String, CellStyle> styles = createStyles(workbook);
        int rowNum = createCompanyHeader(sheet, styles, siteName, year, month);
        YearMonth yearMonthObject = YearMonth.of(year, month + 1);
        int daysInMonth = yearMonthObject.lengthOfMonth();
        Set<Integer> weeklyOffDays = getSundaysForMonth(year, month + 1);
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(25);
        createCell(headerRow, 0, "Sr. No.", styles.get("header"));
        createCell(headerRow, 1, "NAME", styles.get("header"));
        sheet.setColumnWidth(1, 6000);
        for (int day = 1; day <= daysInMonth; day++) {
            CellStyle style = weeklyOffDays.contains(day) ? styles.get("header_woff") : styles.get("header");
            createCell(headerRow, day + 1, String.valueOf(day), style);
            sheet.setColumnWidth(day + 1, 1000);
        }
        createCell(headerRow, daysInMonth + 2, "Total Attd.", styles.get("header"));
        List<Map<String, Object>> employeeResults = (List<Map<String, Object>>) calculatedData.get("employees");
        int srNo = 1;
        for (Map<String, Object> empData : employeeResults) {
            Row empRow = sheet.createRow(rowNum++);
            createCell(empRow, 0, srNo++, styles.get("default"));
            createCell(empRow, 1, (String) empData.get("name"), styles.get("default_left_align"));
            List<String> dailyStatus = (List<String>) empData.get("dailyStatus");
            for (int day = 0; day < dailyStatus.size(); day++) {
                createCell(empRow, day + 2, dailyStatus.get(day), styles.get("status_" + dailyStatus.get(day)));
            }
            createCell(empRow, daysInMonth + 2, (Double) empData.get("totalAttendance"), styles.get("bold"));
        }
        Map<String, Object> summary = (Map<String, Object>) calculatedData.get("summary");
        createFooter(sheet, rowNum, daysInMonth, (Double) summary.get("totalSiteAttendance"),
                (Integer) summary.get("totalHalfDays"), (Integer) summary.get("totalMissingPunches"), styles);
    }

    // All other private helper methods (createStyles, formatJson, etc.) go below.
    // They are unchanged from the previous version.
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
        } else {
            sb.append(value);
        }
    }
    private StringBuilder addIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
        return sb;
    }
    private Set<Integer> getSundaysForMonth(int year, int month) {
        Set<Integer> sundays = new HashSet<>();
        YearMonth yearMonth = YearMonth.of(year, month);
        int daysInMonth = yearMonth.lengthOfMonth();
        for (int day = 1; day <= daysInMonth; day++) {
            if (yearMonth.atDay(day).getDayOfWeek() == DayOfWeek.SUNDAY) sundays.add(day);
        }
        return sundays;
    }
    private int createCompanyHeader(Sheet sheet, Map<String, CellStyle> styles, String siteName, int year, int month) {
        String monthYear = new SimpleDateFormat("MMMM yyyy").format(new GregorianCalendar(year, month, 1).getTime());
        int lastCol = YearMonth.of(year, month + 1).lengthOfMonth() + 2;
        createCell(sheet.createRow(0), 0, "Shree Ji Facility Services", styles.get("company_name"));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastCol));
        createCell(sheet.createRow(1), 0, "Email: contact@shreefacilities.in | Website: shreefacilities.in | Mobile: 9560411801", styles.get("contact_info"));
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, lastCol));
        createCell(sheet.createRow(2), 0, "Monthly Attendance Report for Haldiram's - " + monthYear, styles.get("report_title"));
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, lastCol));
        createCell(sheet.createRow(4), 0, "MUSTER ROLL SHEET - " + siteName.toUpperCase(), styles.get("sheet_title"));
        sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, lastCol));
        return 6;
    }
    private void createFooter(Sheet sheet, int rowNum, int daysInMonth, double totalAtt, int totalHalf, int totalMissing, Map<String, CellStyle> styles) {
        rowNum += 2;
        int lastCol = daysInMonth + 2;
        int mergeStartCol = Math.max(0, lastCol - 5);
        Row footerRow = sheet.createRow(rowNum++);
        createCell(footerRow, mergeStartCol, "Total Site Attendance: " + totalAtt, styles.get("bold_right"));
        sheet.addMergedRegion(new CellRangeAddress(footerRow.getRowNum(), footerRow.getRowNum(), mergeStartCol, lastCol));
        Row footerRow2 = sheet.createRow(rowNum++);
        createCell(footerRow2, mergeStartCol, "Total Half Days: " + totalHalf + " | Total Missing: " + totalMissing, styles.get("bold_right"));
        sheet.addMergedRegion(new CellRangeAddress(footerRow2.getRowNum(), footerRow2.getRowNum(), mergeStartCol, lastCol));
        rowNum++;
        Row noteRow = sheet.createRow(rowNum++);
        createCell(noteRow, 0, "Note: 'M' (Missing Punch) and 'A' (Absent) days are not included in 'Total Attd.'. 'WO' stands for Weekly Off.", styles.get("note"));
        sheet.addMergedRegion(new CellRangeAddress(noteRow.getRowNum(), noteRow.getRowNum(), 0, lastCol));
    }
    private Map<String, CellStyle> createStyles(Workbook wb) {
        Map<String, CellStyle> styles = new HashMap<>();
        Font headerFont = wb.createFont(); headerFont.setBold(true);
        Font companyFont = wb.createFont(); companyFont.setBold(true); companyFont.setFontHeightInPoints((short)16);
        Font titleFont = wb.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short)12);
        Font boldFont = wb.createFont(); boldFont.setBold(true);
        Font noteFont = wb.createFont(); noteFont.setItalic(true);
        java.util.function.Consumer<CellStyle> addBorders = s -> {
            s.setBorderBottom(BorderStyle.THIN); s.setBorderTop(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN); s.setBorderRight(BorderStyle.THIN);
            s.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
            s.setTopBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
            s.setLeftBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
            s.setRightBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        };
        CellStyle style;
        style = wb.createCellStyle(); style.setFont(companyFont); style.setAlignment(HorizontalAlignment.CENTER); styles.put("company_name", style);
        style = wb.createCellStyle(); style.setAlignment(HorizontalAlignment.CENTER); styles.put("contact_info", style);
        style = wb.createCellStyle(); style.setFont(titleFont); style.setAlignment(HorizontalAlignment.CENTER); styles.put("report_title", style);
        style = wb.createCellStyle(); style.setFont(titleFont); style.setAlignment(HorizontalAlignment.CENTER); styles.put("sheet_title", style);
        style = wb.createCellStyle(); style.setFont(headerFont); style.setAlignment(HorizontalAlignment.CENTER); style.setVerticalAlignment(VerticalAlignment.CENTER); addBorders.accept(style); styles.put("header", style);
        style = wb.createCellStyle(); style.setFont(headerFont); style.setAlignment(HorizontalAlignment.CENTER); style.setVerticalAlignment(VerticalAlignment.CENTER); style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); style.setWrapText(true); addBorders.accept(style); styles.put("header_woff", style);
        style = wb.createCellStyle(); style.setAlignment(HorizontalAlignment.CENTER); style.setFont(boldFont); style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); addBorders.accept(style); styles.put("status_P", style);
        style = wb.createCellStyle(); style.setAlignment(HorizontalAlignment.CENTER); style.setFont(boldFont); style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); addBorders.accept(style); styles.put("status_H", style);
        style = wb.createCellStyle(); style.setAlignment(HorizontalAlignment.CENTER); style.setFont(boldFont); style.setFillForegroundColor(IndexedColors.ROSE.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); addBorders.accept(style); styles.put("status_A", style);
        style = wb.createCellStyle(); style.setAlignment(HorizontalAlignment.CENTER); style.setFont(boldFont); style.setFillForegroundColor(IndexedColors.SKY_BLUE.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); addBorders.accept(style); styles.put("status_M", style);
        style = wb.createCellStyle(); style.setAlignment(HorizontalAlignment.CENTER); style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); addBorders.accept(style); styles.put("status_WO", style);
        style = wb.createCellStyle(); addBorders.accept(style); styles.put("default", style);
        style = wb.createCellStyle(); addBorders.accept(style); style.setAlignment(HorizontalAlignment.LEFT); styles.put("default_left_align", style);
        style = wb.createCellStyle(); style.setFont(boldFont); addBorders.accept(style); styles.put("bold", style);
        style = wb.createCellStyle(); style.setFont(boldFont); style.setAlignment(HorizontalAlignment.RIGHT); styles.put("bold_right", style);
        style = wb.createCellStyle(); style.setFont(noteFont); styles.put("note", style);
        return styles;
    }
    private void createCell(Row row, int col, String value, CellStyle style) { Cell cell = row.createCell(col); cell.setCellValue(value); if (style != null) cell.setCellStyle(style); }
    private void createCell(Row row, int col, double value, CellStyle style) { Cell cell = row.createCell(col); cell.setCellValue(value); if (style != null) cell.setCellStyle(style); }
    private void createCell(Row row, int col, int value, CellStyle style) { Cell cell = row.createCell(col); cell.setCellValue(value); if (style != null) cell.setCellStyle(style); }
}

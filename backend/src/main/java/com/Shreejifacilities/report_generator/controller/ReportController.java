package com.Shreejifacilities.report_generator.controller;

import com.Shreejifacilities.report_generator.service.AttendanceSummaryService;
import com.Shreejifacilities.report_generator.service.DailyWorkService;
import com.Shreejifacilities.report_generator.service.MusterRollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/reports")
public class ReportController {


    @Autowired
    private MusterRollService musterRollService;

    @Autowired
    private AttendanceSummaryService attendanceSummaryService;

    @Autowired
    private DailyWorkService dailyWorkService;



    @PostMapping("/muster-roll/excel")
    public ResponseEntity<byte[]> createMusterRollExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        try {
            byte[] excelContent = musterRollService.generateExcelReport(file.getInputStream(), year, month);
            String fileName = String.format("Muster_Roll_Report_%d_%d.xlsx", month, year);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelContent);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/muster-roll/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createMusterRollJson(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        try {
            String jsonContent = musterRollService.generateJsonReport(file.getInputStream(), year, month);
            return ResponseEntity.ok(jsonContent);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/attendance-summary/excel")
    public ResponseEntity<byte[]> createAttendanceSummaryExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        try {
            byte[] excelContent = attendanceSummaryService.generateExcelReport(file.getInputStream(), year, month);
            String fileName = String.format("Attendance_Summary_Report_%d_%d.xlsx", month, year);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelContent);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/attendance-summary/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createAttendanceSummaryJson(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        try {
            String jsonContent = attendanceSummaryService.generateJsonReport(file.getInputStream(), year, month);
            return ResponseEntity.ok(jsonContent);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    @PostMapping("/daily-work/excel")
    public ResponseEntity<byte[]> createDailyWorkExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        try {
            byte[] excelContent = dailyWorkService.generateExcelReport(file.getInputStream(), year, month);
            String fileName = String.format("Daily_Work_Report_%d_%d.xlsx", month, year);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelContent);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/daily-work/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createDailyWorkJson(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        try {
            String jsonContent = dailyWorkService.generateJsonReport(file.getInputStream(), year, month);
            return ResponseEntity.ok(jsonContent);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}

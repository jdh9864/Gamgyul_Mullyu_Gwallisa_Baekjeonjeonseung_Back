package com.example.demo.dto;

import lombok.Data;

public class AttendanceDto {
    @Data
    public static class Response {
        private String lastAttendanceDate;
        private int consecutiveAttendanceDays;
    }
}

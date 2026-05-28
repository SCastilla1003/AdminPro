package com.adminpro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSummaryDTO {
    private String username;
    private String fullName;
    private long onTimeCount;
    private long lateCount;
    private long absentCount;
    private long totalDays;

    public long getPresentCount() {
        return onTimeCount + lateCount;
    }
}

package com.tattoostudio.dto;

import java.util.List;

import com.tattoostudio.model.DayException;
import com.tattoostudio.model.WorkingHours;

/**
 * An artist's schedule plus {@code conflicts}: upcoming customer bookings that fall outside it (e.g.
 * after adding a day off). They stay booked; the artist or admin decides whether to cancel them.
 */
public record ScheduleResponse(List<WorkingHours> weeklyHours, List<DayException> exceptions, int breakMinutes,
        List<StaffBookingDto> conflicts) {
}

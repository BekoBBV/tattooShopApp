package com.tattoostudio.dto;

import java.util.List;

import com.tattoostudio.model.DayException;
import com.tattoostudio.model.WorkingHours;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An artist as the admin creates or edits them. Hours and exceptions are checked like a
 * artist's own schedule. {@code active} is optional (null keeps the current value).
 */
public record ArtistRequest(
        @NotBlank @Size(max = 60) String name,
        @Size(max = 40) String handle,
        @Size(max = 60) String specialty,
        @Size(max = 500) String bio,
        @Size(max = 500) @Pattern(regexp = "(https?://|/)\\S*", message = "must be a web address or a path starting with /") String photoUrl,
        @NotBlank @Pattern(regexp = "red|orange|yellow|green|cyan|purple", message = "must be red, orange, yellow, green, cyan or purple") String accent,
        @Size(max = 7) List<WorkingHours> weeklyHours,
        @Size(max = 100) List<DayException> exceptions,
        Boolean active) {
}

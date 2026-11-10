package com.tattoostudio.dto;

import java.util.List;

import com.tattoostudio.model.Booking;
import com.tattoostudio.model.ColourChoice;
import com.tattoostudio.model.Image;
import com.tattoostudio.model.Placement;

/** The idea a customer brought to a consultation (null fields once wiped for privacy). */
public record ConsultationDto(String idea, Placement placement, Integer sizeCm, ColourChoice colour, List<Image> photos) {

    public static ConsultationDto from(Booking b) {
        return new ConsultationDto(b.getIdea(), b.getPlacement(), b.getSizeCm(), b.getColour(),
                b.getPhotos() == null ? List.of() : b.getPhotos());
    }
}

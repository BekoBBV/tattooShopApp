package com.tattoostudio.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tattoostudio.model.FlashDesign;

public interface FlashRepository extends MongoRepository<FlashDesign, String> {

    List<FlashDesign> findByArtistIdOrderBySortOrderAsc(String artistId);

    long countByArtistId(String artistId);
}

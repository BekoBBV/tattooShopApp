package com.tattoostudio.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tattoostudio.model.Artist;

public interface ArtistRepository extends MongoRepository<Artist, String> {

    List<Artist> findByActiveTrueOrderBySortOrderAsc();
}

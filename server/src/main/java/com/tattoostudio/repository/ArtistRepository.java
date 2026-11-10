package com.tattoostudio.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tattoostudio.model.Artist;

public interface ArtistRepository extends MongoRepository<Artist, String> {

    List<Artist> findByActiveTrueOrderBySortOrderAsc();

    Optional<Artist> findBySlug(String slug);
}

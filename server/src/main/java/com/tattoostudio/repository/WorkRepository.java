package com.tattoostudio.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tattoostudio.model.Work;

public interface WorkRepository extends MongoRepository<Work, String> {

    /** An artist's portfolio, newest first. */
    List<Work> findByArtistIdOrderByCreatedAtDesc(String artistId);

    long countByArtistId(String artistId);
}

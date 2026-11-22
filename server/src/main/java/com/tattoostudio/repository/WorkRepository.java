package com.tattoostudio.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.tattoostudio.model.Work;

public interface WorkRepository extends MongoRepository<Work, String> {

    /** An artist's portfolio, newest first. */
    List<Work> findByArtistIdOrderByCreatedAtDesc(String artistId);

    long countByArtistId(String artistId);

    /** The newest photos of these artists, newest first, as many as the page holds. */
    List<Work> findByArtistIdInOrderByCreatedAtDesc(Collection<String> artistIds, Pageable page);
}

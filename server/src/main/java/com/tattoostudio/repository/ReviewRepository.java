package com.tattoostudio.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tattoostudio.model.Review;

public interface ReviewRepository extends MongoRepository<Review, String> {

    List<Review> findByApprovedTrueOrderByCreatedAtDesc();
}

package com.tattoostudio.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tattoostudio.model.Message;

public interface MessageRepository extends MongoRepository<Message, String> {

    /** How many messages this sender (see Message.senderKey) sent before (still kept, i.e. last 90 days). */
    long countBySenderKey(String senderKey);

    /** Messages received before {@code cutoff}. */
    List<Message> findByCreatedAtBefore(Instant cutoff);
}

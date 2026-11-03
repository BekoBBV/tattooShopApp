package com.tattoostudio.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByUsername(String username);

    /** The login of an artist (each artist has at most one). */
    Optional<User> findByArtistId(String artistId);

    /** The logins of these artists. */
    List<User> findByArtistIdIn(Collection<String> artistIds);

    boolean existsByRole(Role role);
}

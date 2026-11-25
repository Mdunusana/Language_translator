package com.linguavoice.repository;

import com.linguavoice.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** @param email must already be normalised (trimmed, lower-case) */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** The two columns token validation needs, without loading the whole user. Empty = the account doesn't exist. */
    @Query("select u.id as id, u.passwordChangedAt as passwordChangedAt from User u where u.id = :id")
    Optional<TokenState> findTokenStateById(@Param("id") Long id);

    interface TokenState {
        Long getId();

        LocalDateTime getPasswordChangedAt();
    }
}

package com.linguavoice.repository;

import com.linguavoice.entity.Translation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query is scoped by {@code userId} so one user can never read or delete another user's history.
 * History search/filter queries are added in Phase 6.
 */
public interface TranslationRepository extends JpaRepository<Translation, Long> {

    Optional<Translation> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}

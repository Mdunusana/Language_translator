package com.linguavoice.repository;

import com.linguavoice.entity.Translation;
import com.linguavoice.entity.TranslationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every query is scoped by {@code userId} so one user can never read or delete another user's history.
 * Search and filtering use {@link com.linguavoice.service.HistorySpecifications}, which always includes the
 * user condition.
 */
public interface TranslationRepository extends JpaRepository<Translation, Long>, JpaSpecificationExecutor<Translation> {

    Optional<Translation> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndTranslationType(Long userId, TranslationType translationType);

    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime since);

    List<Translation> findTop20ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @Modifying
    @Query("delete from Translation t where t.id = :id and t.user.id = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying
    @Query("delete from Translation t where t.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    /** [language code, count] pairs for the user's source languages. */
    @Query("select t.sourceLanguage, count(t) from Translation t where t.user.id = :userId group by t.sourceLanguage")
    List<Object[]> countBySourceLanguage(@Param("userId") Long userId);

    /** [language code, count] pairs for the user's target languages. */
    @Query("select t.targetLanguage, count(t) from Translation t where t.user.id = :userId group by t.targetLanguage")
    List<Object[]> countByTargetLanguage(@Param("userId") Long userId);

    /** Creation times since {@code since} (UTC), bucketed into days in the caller's time zone by the service. */
    @Query("select t.createdAt from Translation t where t.user.id = :userId and t.createdAt >= :since")
    List<LocalDateTime> findCreatedAtSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}

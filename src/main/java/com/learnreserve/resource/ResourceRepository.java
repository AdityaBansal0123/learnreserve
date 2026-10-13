package com.learnreserve.resource;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ResourceRepository extends JpaRepository<LearningResource, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LearningResource r where r.id = :id")
    Optional<LearningResource> lockById(Long id);

    @Query("""
            select r from LearningResource r
            where r.active = true and (:type is null or r.type = :type)
            and (:q = '' or locate(lower(:q), lower(r.name)) > 0
                or locate(lower(:q), lower(r.description)) > 0)
            """)
    Page<LearningResource> discover(LearningResource.Type type, String q, Pageable pageable);
}

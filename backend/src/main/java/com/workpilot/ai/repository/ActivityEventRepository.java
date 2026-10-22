package com.workpilot.ai.repository;

import com.workpilot.ai.domain.ActivityEvent;
import com.workpilot.ai.domain.ActivityType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

    @Query("""
            SELECT a FROM ActivityEvent a
            JOIN FETCH a.actor
            WHERE a.organization.id = :organizationId
            ORDER BY a.occurredAt DESC
            """)
    List<ActivityEvent> findRecentByOrganizationId(
            @Param("organizationId") UUID organizationId,
            Pageable pageable);

    @Query("""
            SELECT a FROM ActivityEvent a
            JOIN FETCH a.actor
            WHERE a.organization.id = :organizationId
              AND a.occurredOn = :day
              AND a.type IN :types
            ORDER BY a.occurredAt DESC
            """)
    List<ActivityEvent> findByOrganizationAndDay(
            @Param("organizationId") UUID organizationId,
            @Param("day") LocalDate day,
            @Param("types") Collection<ActivityType> types,
            Pageable pageable);

    @Query("""
            SELECT a.occurredOn, COUNT(a) FROM ActivityEvent a
            WHERE a.organization.id = :organizationId
              AND a.type = :type
              AND a.occurredOn >= :from
              AND a.occurredOn <= :to
            GROUP BY a.occurredOn
            """)
    List<Object[]> countByDay(
            @Param("organizationId") UUID organizationId,
            @Param("type") ActivityType type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}

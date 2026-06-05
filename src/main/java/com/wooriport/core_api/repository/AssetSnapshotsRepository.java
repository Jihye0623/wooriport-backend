package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.AssetSnapshots;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetSnapshotsRepository extends JpaRepository<AssetSnapshots, UUID> {

    @Query("""
        SELECT s FROM AssetSnapshots s
        WHERE s.user.id = :userId
          AND s.snapshotAt >= :from
          AND s.snapshotAt < :to
        ORDER BY s.snapshotAt ASC
        """)
    List<AssetSnapshots> findByUserIdAndMonth(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
        SELECT s FROM AssetSnapshots s
        WHERE s.user.id = :userId
          AND s.snapshotAt < :before
        ORDER BY s.snapshotAt DESC
        LIMIT 1
        """)
    Optional<AssetSnapshots> findLastBefore(
            @Param("userId") UUID userId,
            @Param("before") LocalDateTime before);
}

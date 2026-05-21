package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.Users;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<Users, UUID> {
    Optional<Users> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("""
        SELECT u FROM Users u
        WHERE u.salaryDate = :salaryDate
          AND u.status = 'ACTIVE'
          AND u.deletedAt IS NULL
        """)
    List<Users> findBySalaryDate(@Param("salaryDate") int salaryDate);

    // 월간 리포트 Batch — 전체 활성 사용자
    @Query("""
        SELECT u FROM Users u
        WHERE u.status = 'ACTIVE'
          AND u.deletedAt IS NULL
        """)
    List<Users> findAllActiveUsers();
}

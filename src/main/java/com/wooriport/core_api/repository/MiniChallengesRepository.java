package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.MiniChallenges;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MiniChallengesRepository extends JpaRepository<MiniChallenges, UUID> {
}

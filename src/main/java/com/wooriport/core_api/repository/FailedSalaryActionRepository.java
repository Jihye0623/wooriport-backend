package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.FailedSalaryAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FailedSalaryActionRepository extends JpaRepository<FailedSalaryAction, UUID> {

    List<FailedSalaryAction> findByStatusOrderByCreatedAtDesc(FailedSalaryAction.Status status);
}

package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.DummyMydata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DummyMydataRepository extends JpaRepository<DummyMydata, UUID> {
    List<DummyMydata> findByEmail(String email);
}

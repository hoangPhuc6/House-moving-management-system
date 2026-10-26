package com.example.house_moving_service.modules.catalog.repository;

import com.example.house_moving_service.modules.catalog.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Integer> {

    Optional<ServiceType> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Integer id);

    List<ServiceType> findAllByIsActive(Boolean isActive);
}

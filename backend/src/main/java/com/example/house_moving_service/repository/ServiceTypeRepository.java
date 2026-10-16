package com.example.house_moving_service.repository;

import com.example.house_moving_service.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceTypeRepository extends JpaRepository<ServiceType, Integer> {

    Optional<ServiceType> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Integer id);

    List<ServiceType> findAllByIsActive(Boolean isActive);
}


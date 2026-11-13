package com.example.house_moving_service.modules.scheduling.repository;

import com.example.house_moving_service.modules.scheduling.entity.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleTypeRepository extends JpaRepository<VehicleType, Integer> {
}

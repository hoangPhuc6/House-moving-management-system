package com.example.house_moving_service.modules.scheduling.repository;

import com.example.house_moving_service.modules.scheduling.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Integer> {
}

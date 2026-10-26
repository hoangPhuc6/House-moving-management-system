package com.example.house_moving_service.modules.catalog.repository;

import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Service, Integer> {

    @EntityGraph(attributePaths = { "serviceType" })
    List<Service> findAllByIdIn(Collection<Integer> ids);

    @EntityGraph(attributePaths = { "serviceType" })
    List<Service> findAllByIdInAndStatus(Collection<Integer> ids, ServiceStatus status);

    @EntityGraph(attributePaths = { "serviceType" })
    List<Service> findAllByStatus(ServiceStatus status);

    @EntityGraph(attributePaths = { "serviceType" })
    List<Service> findAllByServiceTypeIdAndStatus(Integer serviceTypeId, ServiceStatus status);

    @EntityGraph(attributePaths = { "serviceType" })
    List<Service> findAllByServiceTypeId(Integer serviceTypeId);

    @Override
    @EntityGraph(attributePaths = { "serviceType" })
    List<Service> findAll();

    @EntityGraph(attributePaths = { "serviceType" })
    List<Service> findAllByStatusAndServiceType_IsActiveTrue(ServiceStatus status);

    Optional<Service> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndServiceTypeId(String name, Integer serviceTypeId);

    boolean existsByNameAndServiceTypeIdAndIdNot(String name, Integer serviceTypeId, Integer id);
}

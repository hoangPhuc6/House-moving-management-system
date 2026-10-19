package com.example.house_moving_service.repository;

import com.example.house_moving_service.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Integer> {

    Optional<Voucher> findByCode(String code);

    Optional<Voucher> findByCodeIgnoreCase(String code);

    Optional<Voucher> findByCodeAndIsActiveTrue(String code);

    boolean existsByCode(String code);
}


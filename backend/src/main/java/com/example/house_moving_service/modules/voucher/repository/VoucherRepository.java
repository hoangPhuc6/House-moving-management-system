package com.example.house_moving_service.modules.voucher.repository;

import com.example.house_moving_service.modules.voucher.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Integer> {

    Optional<Voucher> findByCode(String code);

    Optional<Voucher> findByCodeIgnoreCase(String code);

    Optional<Voucher> findByCodeAndIsActiveTrue(String code);

    boolean existsByCode(String code);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT v FROM Voucher v WHERE UPPER(v.code) = UPPER(:code)")
    Optional<Voucher> findByCodeWithLock(@org.springframework.data.repository.query.Param("code") String code);
}


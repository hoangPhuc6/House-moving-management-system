package com.example.house_moving_service.modules.quotation.repository;

import com.example.house_moving_service.modules.quotation.entity.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuotationItemRepository extends JpaRepository<QuotationItem, Integer> {

    List<QuotationItem> findAllByQuotationIdOrderByIdAsc(Integer quotationId);

    long countByQuotationId(Integer quotationId);

    Optional<QuotationItem> findByIdAndQuotationId(Integer id, Integer quotationId);
}

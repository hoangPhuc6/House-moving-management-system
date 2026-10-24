package com.example.house_moving_service.repository;

import com.example.house_moving_service.entity.BookingVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingVoucherRepository extends JpaRepository<BookingVoucher, Integer> {

    @Query("SELECT COUNT(bv) FROM BookingVoucher bv WHERE bv.voucher.id = :voucherId")
    long countByVoucherId(@Param("voucherId") Integer voucherId);

    @Query("SELECT COUNT(bv) FROM BookingVoucher bv WHERE bv.voucher.id = :voucherId AND bv.booking.customer.id = :customerId")
    long countByVoucherIdAndBooking_CustomerId(@Param("voucherId") Integer voucherId, @Param("customerId") Long customerId);

    List<BookingVoucher> findAllByBookingId(Integer bookingId);
}


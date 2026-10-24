package com.example.house_moving_service.entity;

import com.example.house_moving_service.enums.PropertyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "booking_routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "pickup_address", nullable = false, length = 500)
    private String pickupAddress;

    @Column(name = "pickup_lat", precision = 10, scale = 7)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", precision = 10, scale = 7)
    private BigDecimal pickupLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_property_type", length = 50)
    private PropertyType pickupPropertyType;

    @Column(name = "pickup_note", columnDefinition = "text")
    private String pickupNote;

    @Column(name = "dropoff_address", nullable = false, length = 500)
    private String dropoffAddress;

    @Column(name = "dropoff_lat", precision = 10, scale = 7)
    private BigDecimal dropoffLat;

    @Column(name = "dropoff_lng", precision = 10, scale = 7)
    private BigDecimal dropoffLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "dropoff_property_type", length = 50)
    private PropertyType dropoffPropertyType;

    @Column(name = "dropoff_note", columnDefinition = "text")
    private String dropoffNote;

    @Column(name = "estimated_distance_km", precision = 10, scale = 2)
    private BigDecimal estimatedDistanceKm;
}


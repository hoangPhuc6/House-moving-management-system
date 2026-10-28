package com.example.house_moving_service.modules.catalog.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import com.example.house_moving_service.modules.catalog.enums.FeeCategory;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "service_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "services")
@EqualsAndHashCode(exclude = "services")
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Dịch vụ thuộc loại này được cộng vào nhóm phí nào trong bảng phân rã chi phí
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "fee_category", nullable = false, length = 20)
    private FeeCategory feeCategory = FeeCategory.TRANSPORT;

    @OneToMany(mappedBy = "serviceType", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<Service> services = new ArrayList<>();
}


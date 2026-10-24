package com.example.house_moving_service.security.access;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Chỉ CUSTOMER — dùng cho API thao tác trên dữ liệu của chính khách hàng. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('CUSTOMER')")
public @interface CustomerOnly {
}

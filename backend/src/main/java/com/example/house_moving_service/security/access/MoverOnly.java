package com.example.house_moving_service.security.access;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Chỉ MOVER — dùng cho API nhân viên hiện trường (khảo sát, sau này là vận chuyển). */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('MOVER')")
public @interface MoverOnly {
}

package com.jeontongju.coupon.mapper;

import com.jeontongju.coupon.domain.Coupon;
import com.jeontongju.coupon.domain.CouponReceipt;
import com.jeontongju.coupon.domain.CouponReceiptId;
import com.jeontongju.coupon.dto.response.CurCouponStatusForReceiveResponseDto;
import org.springframework.stereotype.Component;

@Component
public class CouponMapper {

  public CouponReceipt toCouponReceiptEntity(Coupon coupon, Long consumerId) {

    CouponReceiptId build = CouponReceiptId.builder().coupon(coupon).consumerId(consumerId).build();
    return CouponReceipt.builder().id(build).isUse(false).build();
  }

  public CurCouponStatusForReceiveResponseDto toCurCouponStatusDto() {

    return CurCouponStatusForReceiveResponseDto.builder().isSoldOut(false).isOpen(true).build();
  }
}
package com.jeontongju.coupon.exceptionhandler;

import com.jeontongju.coupon.exception.*;
import io.github.bitbox.bitbox.dto.FeignFormat;
import io.github.bitbox.bitbox.enums.FailureTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class CouponFeignControllerAdvice {

  @ExceptionHandler(InsufficientMinOrderPriceException.class)
  public FeignFormat<Void> handleCouponPolicyException() {

    return FeignFormat.<Void>builder()
        .code(HttpStatus.OK.value())
        .failure(FailureTypeEnum.INSUFFICIENT_MIN_ORDER_PRICE)
        .build();
  }

  @ExceptionHandler(AlreadyUseCouponException.class)
  public FeignFormat<Void> handleAlreadyUseCoupon() {

    // TODO
    return FeignFormat.<Void>builder()
        .code(HttpStatus.OK.value())
        .failure(FailureTypeEnum.NOT_FOUND_COUPON)
        .build();
  }

  @ExceptionHandler(CouponExpiredException.class)
  public FeignFormat<Void> handleExpiredCoupon() {

    return FeignFormat.<Void>builder()
        .code(HttpStatus.OK.value())
        .failure(FailureTypeEnum.EXPIRED_COUPON)
        .build();
  }

  @ExceptionHandler(IncorrectCouponDiscountAmountException.class)
  public FeignFormat<Void> handleIncorrectCouponDiscountAmount() {

    return FeignFormat.<Void>builder()
        .code(HttpStatus.OK.value())
        .failure(FailureTypeEnum.INCORRECT_COUPON_DISCOUNT_AMOUNT)
        .build();
  }
}

package com.jeontongju.coupon.domain;

import java.time.LocalDateTime;
import javax.persistence.*;

import io.github.bitbox.bitbox.enums.CouponTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "coupon")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class Coupon {

  @Id
  @Column(name = "coupon_code", nullable = false)
  private String couponCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "coupon_name", nullable = false)
  private CouponTypeEnum couponName;

  @Column(name = "discount_amount", nullable = false)
  private Long discountAmount;

  @Column(name = "issue_limit", nullable = false)
  private Long issueLimit;

  @Column(name = "issued_at", nullable = false)
  private LocalDateTime issuedAt;

  @Column(name = "expired_at", nullable = false)
  private LocalDateTime expiredAt;

  @Column(name = "min_order_price", nullable = false)
  private Long minOrderPrice;

  public void decrease(Long quantity) {
    this.issueLimit -= quantity;
  }

  public void assignIssuedLimit(Long issueLimit) {
    this.issueLimit = issueLimit;
  }
}

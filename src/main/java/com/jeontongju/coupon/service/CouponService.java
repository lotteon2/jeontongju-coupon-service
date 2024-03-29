package com.jeontongju.coupon.service;

import com.jeontongju.coupon.domain.Coupon;
import com.jeontongju.coupon.domain.CouponReceipt;
import com.jeontongju.coupon.domain.CouponReceiptId;
import com.jeontongju.coupon.dto.request.OrderPriceForCheckValidRequestDto;
import com.jeontongju.coupon.dto.response.AvailableCouponInfoForSummaryNDetailsResponseDto;
import com.jeontongju.coupon.dto.response.CouponInfoForSingleInquiryResponseDto;
import com.jeontongju.coupon.dto.response.CurCouponStatusForReceiveResponseDto;
import com.jeontongju.coupon.exception.*;
import com.jeontongju.coupon.mapper.CouponMapper;
import com.jeontongju.coupon.repository.CouponReceiptRepository;
import com.jeontongju.coupon.repository.CouponRepository;
import com.jeontongju.coupon.utils.CustomErrMessage;
import com.jeontongju.coupon.utils.PaginationManager;
import io.github.bitbox.bitbox.dto.*;
import io.github.bitbox.bitbox.enums.CouponTypeEnum;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
// @Transactional(readOnly = true)
@RequiredArgsConstructor
public class CouponService {

  private final CouponRepository couponRepository;
  private final CouponReceiptRepository couponReceiptRepository;
  private final CouponMapper couponMapper;
  private final PaginationManager<CouponInfoForSingleInquiryResponseDto> paginationManager;

  /**
   * 회원 가입시 WELCOME 쿠폰 발급 및 수령 처리
   *
   * @param consumerId 회원 가입한 소비자 식별자
   */
  @Transactional
  public void issueWelcomeCouponByJoin(Long consumerId) {

    String couponCode = generateCouponCode();
    Coupon issuedCoupon =
        couponRepository.save(couponMapper.toWelcomeCouponEntity(couponCode, LocalDateTime.now()));
    couponReceiptRepository.save(couponMapper.toCouponReceiptEntity(issuedCoupon, consumerId));
  }

  /**
   * 주문 시, 주문 및 결제 확정을 위한 쿠폰 사용 처리
   *
   * @param orderInfoDto 주문 정보
   */
  @Transactional
  public void deductCoupon(OrderInfoDto orderInfoDto) {

    UserCouponUpdateDto userCouponUpdateDto = orderInfoDto.getUserCouponUpdateDto();

    if (userCouponUpdateDto.getCouponCode() != null) {
      Coupon foundCoupon = getCoupon(userCouponUpdateDto.getCouponCode());

      checkCouponInfo(userCouponUpdateDto);

      CouponReceipt foundCouponReceipt =
          getCouponReceipt(userCouponUpdateDto.getConsumerId(), foundCoupon);

      // 쿠폰 사용 처리
      foundCouponReceipt.deductCoupon();
    }
  }

  /**
   * 주문 실패 시, 쿠폰 미사용 상태로 처리(복구)
   *
   * @param orderInfoDto 주문 복구 정보
   */
  @Transactional
  public void rollbackCouponUsage(OrderInfoDto orderInfoDto) {

    UserCouponUpdateDto userCouponUpdateDto = orderInfoDto.getUserCouponUpdateDto();

    if (userCouponUpdateDto.getCouponCode() != null) {
      Coupon foundCoupon = getCoupon(userCouponUpdateDto.getCouponCode());
      CouponReceipt foundCouponReceipt =
          getCouponReceipt(userCouponUpdateDto.getConsumerId(), foundCoupon);

      foundCouponReceipt.rollbackCoupon();
    }
  }

  /**
   * 쿠폰 유효성 검증
   *
   * @param userCouponUpdateDto 쿠폰 유효성 검증을 위해 필요한 정보
   */
  public void checkCouponInfo(UserCouponUpdateDto userCouponUpdateDto) {

    Coupon foundCoupon = getCoupon(userCouponUpdateDto.getCouponCode());

    CouponReceipt foundCouponReceipt =
        getCouponReceipt(userCouponUpdateDto.getConsumerId(), foundCoupon);

    if (foundCouponReceipt.getIsUse()) {
      log.error("이미 사용한 쿠폰");
      throw new AlreadyUseCouponException(CustomErrMessage.ALREADY_USE_COUPON);
    }

    // 쿠폰 만료 여부 확인
    if (!isValidCoupon(foundCoupon.getExpiredAt())) {
      log.error("만료된 쿠폰");
      throw new CouponExpiredException(CustomErrMessage.EXPIRED_COUPON);
    }

    // 쿠폰 코드와 할인 금액 일치 여부 확인
    if (!Objects.equals(userCouponUpdateDto.getCouponAmount(), foundCoupon.getDiscountAmount())) {
      log.error("쿠폰 코드와 할인 금액 불일치");
      throw new IncorrectCouponDiscountAmountException(
          CustomErrMessage.INCORRECT_COUPON_DISCOUNT_AMOUNT);
    }

    // 쿠폰 사용을 위한 최소 주문 금액 확인
    if (userCouponUpdateDto.getTotalAmount() < foundCoupon.getMinOrderPrice()) {
      log.error("최소 주문 금액 미달");
      throw new InsufficientMinOrderPriceException(CustomErrMessage.INSUFFICIENT_MIN_ORDER_PRICE);
    }
  }

  /**
   * 해당 쿠폰 만료 여부 확인
   *
   * @param expiredAt 해당 쿠폰 만료 시각
   * @return {Boolean} 해당 쿠폰 만료 여부
   */
  private Boolean isValidCoupon(LocalDateTime expiredAt) {

    return LocalDateTime.now().isBefore(expiredAt);
  }

  /**
   * 주문 취소 시, 해당 쿠폰 미사용 처리(환불)
   *
   * @param orderCancelDto 주문 취소 정보
   */
  @Transactional
  public void refundCouponByOrderCancel(OrderCancelDto orderCancelDto) {

    Coupon foundCoupon = getCoupon(orderCancelDto.getCouponCode());
    CouponReceipt foundCouponReceipt =
        getCouponReceipt(orderCancelDto.getConsumerId(), foundCoupon);
    foundCouponReceipt.rollbackCoupon();
  }

  /**
   * 주문 취소 실패 시, 쿠폰 사용 상태로 처리(복구)
   *
   * @param orderCancelDto 주문 복구 정보
   */
  @Transactional
  public void recoverCouponByFailedOrderCancel(OrderCancelDto orderCancelDto) {

    Coupon foundCoupon = getCoupon(orderCancelDto.getCouponCode());
    CouponReceipt foundCouponReceipt =
        getCouponReceipt(orderCancelDto.getConsumerId(), foundCoupon);
    foundCouponReceipt.deductCoupon();
  }

  /**
   * Promotion 쿠폰 수령을 위한 사전 체크
   *
   * @param consumerId 로그인 한 회원 식별자
   */
  public CurCouponStatusForReceiveResponseDto prevCheck(Long consumerId)
      throws NotOpenPromotionCouponEventException, AlreadyReceivePromotionCouponException {

    //    test를 위해 잠시 주석처리
    //    LocalDateTime now = LocalDateTime.now();
    //    LocalDateTime after5PM =
    //        LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), 17, 0);
    //
    //    if (now.isBefore(after5PM)) {
    //      throw new NotOpenPromotionCouponEventException(
    //          CustomErrMessage.NOT_OPEN_PROMOTION_COUPON_EVENT);
    //    }
    //
    //    LocalDateTime after6PM =
    //        LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), 18, 0);
    //
    //    if (now.isAfter(after6PM)) {
    //      throw new NotOpenPromotionCouponEventException(
    //          CustomErrMessage.NOT_OPEN_PROMOTION_COUPON_EVENT);
    //    }

    Coupon promotionCoupon = getPromotionCoupon();

    Optional<CouponReceipt> foundCouponReceipt =
        couponReceiptRepository.findByCouponReceiptId(consumerId, promotionCoupon);

    // 이미 수령한 회원, 중복 수령 방지
    if (foundCouponReceipt.isPresent()) {
      return couponMapper.toCurCouponStatusDto(false, true, true);
    }

    return couponMapper.toCurCouponStatusDto(false, true, false);
  }

  /**
   * 쿠폰 수량 차감
   *
   * @param coupon Promotion 쿠폰
   * @param quantity 차감할 쿠폰 수량
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void decreasePromotionCoupon(Coupon coupon, Long quantity, Long consumerId) {

    try {
      log.info("[coupon]: " + coupon);
      String couponCode = coupon.getCouponCode();
      log.info("[couponCode]: " + couponCode);
      coupon.decrease(quantity);
      couponRepository.save(coupon);
      CouponReceiptId build =
          CouponReceiptId.builder().coupon(coupon).consumerId(consumerId).build();
      couponReceiptRepository.save(CouponReceipt.builder().id(build).isUse(false).build());
    } catch (Exception e) {
      log.error(
          "[During receiving promotion_coupon]: Error at decreasing coupon_limits={}",
          e.getMessage());
    }
  }

  /**
   * 쿠폰 수령 후, 수령 내역 저장
   *
   * @param consumerId 로그인 한 회원 식별자
   */
  @Transactional
  public void AfterProcessing(Long consumerId) {

    Coupon foundCoupon = getPromotionCoupon();
    couponReceiptRepository.save(couponMapper.toCouponReceiptEntity(foundCoupon, consumerId));
  }

  /**
   * 쿠폰 목록 조회
   *
   * @param consumerId 로그인 한 회원 식별자
   * @param page 페이징 첫 페이지 번호
   * @param size 페이지 당 보여줄 게시물 개수
   * @param search 필터링 기준
   * @return {Page<CouponInfoForSingleInquiryResponseDto>} 한 페이지 만큼의 쿠폰 목록 정보
   */
  public Page<CouponInfoForSingleInquiryResponseDto> getMyCouponsForListLookup(
      Long consumerId, int page, int size, String search) {

    boolean isUsed = false;
    boolean isAvailable = false;

    if ("used".equals(search)) {
      isUsed = true;
    }

    if ("available".equals(search)) {
      isAvailable = true;
    }

    Pageable pageable = paginationManager.getPageableByCreatedAt(page, size);

    Page<CouponReceipt> foundCouponReceipts =
        couponReceiptRepository.findByConsumerId(consumerId, pageable);

    List<CouponInfoForSingleInquiryResponseDto> couponList = new ArrayList<>(); // 사용 가능한 쿠폰 내역
    List<CouponInfoForSingleInquiryResponseDto> usedCouponList = new ArrayList<>(); // 사용 불가능한 쿠폰 내역

    for (CouponReceipt couponReceipt : foundCouponReceipts) {
      Coupon foundCoupon = couponReceipt.getId().getCoupon();
      if (couponReceipt.getIsUse() || !isValidCoupon(foundCoupon.getExpiredAt())) {
        usedCouponList.add(couponMapper.toInquiryDto(foundCoupon));
        continue;
      }
      couponList.add(couponMapper.toInquiryDto(foundCoupon));
    }

    int totalSize = couponReceiptRepository.findByConsumerId(consumerId).size();

    if (!isUsed && !isAvailable) {
      return paginationManager.wrapByPage(new ArrayList<>(), pageable, totalSize);
    }

    return isUsed
        ? paginationManager.wrapByPage(usedCouponList, pageable, totalSize)
        : paginationManager.wrapByPage(couponList, pageable, totalSize);
  }

  /**
   * 주문시, 사용할 수 있는 쿠폰의 개수와 정보 가져오기
   *
   * @param consumerId 로그인 한 소비자 식별자
   * @param checkValidRequestDto 쿠폰 사용 여부 확인을 위해 필요한 총 주문 금액
   * @return {AvailableCouponInfoForSummaryNDetailsResponseDto} 유효한 쿠폰 중 사용 가능한 쿠폰 개수 및 정보
   */
  public AvailableCouponInfoForSummaryNDetailsResponseDto getAvailableCouponsWhenOrdering(
      Long consumerId, OrderPriceForCheckValidRequestDto checkValidRequestDto) {

    List<CouponReceipt> foundCouponReceipts =
        couponReceiptRepository.findByConsumerIdAndIsUse(consumerId, false);

    List<CouponInfoForSingleInquiryResponseDto> availableCouponList = new ArrayList<>();

    int totalValidCounts = foundCouponReceipts.size();
    int unavailableCounts = 0;
    for (CouponReceipt couponReceipt : foundCouponReceipts) {
      Coupon foundCoupon = couponReceipt.getId().getCoupon();
      if (!isValidCoupon(foundCoupon.getExpiredAt())) {

        totalValidCounts -= 1;
        continue;
      }

      if (checkValidRequestDto.getTotalAmount() < foundCoupon.getMinOrderPrice()) {

        unavailableCounts += 1;
        continue;
      }

      availableCouponList.add(couponMapper.toInquiryDto(foundCoupon));
    }
    return couponMapper.toSummaryNDetailsDto(
        (totalValidCounts - unavailableCounts), availableCouponList);
  }

  /**
   * 구독 결제 완료 후, 해당 소비자 구독 전용 쿠폰 자동 수령 처리
   *
   * @param regularPaymentsCouponDto 구독 결제 정보(소비자, 결제 완료 시각)
   */
  @Transactional
  public void giveRegularPaymentsCoupon(ConsumerRegularPaymentsCouponDto regularPaymentsCouponDto) {

    String generatedCouponCode;
    for (int i = 0; i < 5; i++) {
      generatedCouponCode = generateCouponCode();

      Coupon issued1000Coupon =
          couponRepository.save(
              couponMapper.toRegularPaymentsCouponEntity(
                  generatedCouponCode, 1000L, 10000L, regularPaymentsCouponDto.getSuccessedAt()));

      couponReceiptRepository.save(
          couponMapper.toCouponReceiptEntity(
              issued1000Coupon, regularPaymentsCouponDto.getConsumerId()));
    }

    generatedCouponCode = generateCouponCode();
    Coupon issued5000Coupon =
        couponRepository.save(
            couponMapper.toRegularPaymentsCouponEntity(
                generatedCouponCode, 5000L, 20000L, regularPaymentsCouponDto.getSuccessedAt()));

    couponReceiptRepository.save(
        couponMapper.toCouponReceiptEntity(
            issued5000Coupon, regularPaymentsCouponDto.getConsumerId()));
  }

  /** 프로모션 쿠폰 발급 (100개) */
  @Transactional
  public void issuePromotionCoupons() {

    couponRepository.save(couponMapper.toPromotionCouponEntity(generateCouponCode()));
  }

  /**
   * 구독 전용 쿠폰 코드 생성
   *
   * @return {String} 발급된 쿠폰 코드
   */
  public String generateCouponCode() {

    final int CODE_LEN = 14;
    SecureRandom random = new SecureRandom();
    StringBuilder builder = new StringBuilder(CODE_LEN);

    final String CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    for (int i = 0; i < CODE_LEN; i++) {
      int randomIdx = random.nextInt(CHARACTERS.length());
      char randomChar = CHARACTERS.charAt(randomIdx);
      builder.append(randomChar);

      if ((i + 1) % 4 == 0 && (i + 1) != CODE_LEN) {
        builder.append("-");
      }
    }

    return builder.toString();
  }

  /**
   * 멤버십 구독 쿠폰 사용 총액(혜택) 조회
   *
   * @param consumerId 멤버십 구독한 회원 식별자
   * @return {SubscriptionCouponBenefitForInquiryResponseDto} 멤버십 쿠폰 사용 총액(혜택)
   */
  public SubscriptionCouponBenefitForInquiryResponseDto getSubscriptionBenefit(Long consumerId) {

    long couponUse = 0;
    List<CouponReceipt> couponReceipts = couponReceiptRepository.findByConsumerId(consumerId);
    for (CouponReceipt couponReceipt : couponReceipts) {
      if (couponReceipt.getIsUse()) {

        Coupon foundCoupon = getCoupon(couponReceipt.getId().getCoupon().getCouponCode());
        couponUse += foundCoupon.getDiscountAmount();
      }
    }
    return couponMapper.toSubscriptionCouponBenefitDto(couponUse);
  }

  /**
   * consumerId와 coupon으로 CouponReceipt(쿠폰 수령 내역) 찾기
   *
   * @param consumerId 로그인 한 소비자 식별자
   * @param foundCoupon 쿠폰 수령 내역을 확인할 쿠폰 객체
   * @return {CouponReceipt} 해당 쿠폰 수령 내역
   */
  public CouponReceipt getCouponReceipt(Long consumerId, Coupon foundCoupon) {
    return couponReceiptRepository
        .findByCouponReceiptId(consumerId, foundCoupon)
        .orElseThrow(() -> new CouponNotFoundException(CustomErrMessage.NOT_FOUND_COUPON_RECEIPT));
  }

  /**
   * Promotion Coupon 찾기(공통화)
   *
   * @return {Coupon} 찾은 프로모션 쿠폰 객체
   */
  public Coupon getPromotionCoupon() {

    Pageable pageable = paginationManager.getPageableByCreatedAt(0, 1);
    return couponRepository
        .findByCouponNameOrderByIssuedAtDesc(CouponTypeEnum.PROMOTION, pageable)
        .get(0);
  }

  /**
   * couponCode로 Coupon 찾기 (공통화)
   *
   * @param couponCode 쿠폰 코드(식별자)
   * @return {Coupon} 찾은 쿠폰 객체
   */
  public Coupon getCoupon(String couponCode) {

    return couponRepository
        .findByCouponCode(couponCode)
        .orElseThrow(() -> new CouponNotFoundException(CustomErrMessage.NOT_FOUND_COUPON));
  }
}

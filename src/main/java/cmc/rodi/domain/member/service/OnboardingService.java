package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.dto.OnboardingRequest;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.MemberOnboarding;
import cmc.rodi.domain.member.exception.MemberErrorCode;
import cmc.rodi.domain.member.repository.MemberOnboardingRepository;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩 제출 처리. 운전 경험·추가 정보를 {@code member_onboarding}에 저장하고, 노출·활용되는 레벨·운전목표는 {@code member}에 반영한다.
 * 온보딩은 1회성이라 이미 완료한 회원의 재제출은 거부한다.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final MemberRepository memberRepository;
    private final MemberOnboardingRepository memberOnboardingRepository;

    @Transactional
    public void submit(Long memberId, OnboardingRequest request) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        if (memberOnboardingRepository.existsById(memberId)) {
            throw new BusinessException(MemberErrorCode.ALREADY_ONBOARDED);
        }

        memberOnboardingRepository.save(
                MemberOnboarding.builder()
                        .member(member)
                        .drivingPeriod(request.drivingPeriod())
                        .recentFrequency(request.recentFrequency())
                        .soloDrivingRange(request.soloDrivingRange())
                        .soloParkingLevel(request.soloParkingLevel())
                        .roadExperiences(request.roadExperiences())
                        .practiceTypes(
                                request.practiceTypes() != null
                                        ? request.practiceTypes()
                                        : List.of())
                        .carType(request.carType())
                        .onboardedAt(LocalDateTime.now())
                        .build());

        // 레벨·운전목표는 마이페이지·추천에 쓰이므로 member에 저장(레벨은 클라이언트가 보낸 값)
        member.applyOnboarding(request.level(), request.drivingGoal());
    }
}

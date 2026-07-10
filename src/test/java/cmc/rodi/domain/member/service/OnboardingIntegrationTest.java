package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.dto.OnboardingRequest;
import cmc.rodi.domain.member.entity.CarType;
import cmc.rodi.domain.member.entity.DrivingPeriod;
import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.MemberOnboarding;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.entity.RecentFrequency;
import cmc.rodi.domain.member.entity.RoadExperience;
import cmc.rodi.domain.member.entity.SoloDrivingRange;
import cmc.rodi.domain.member.entity.SoloParkingLevel;
import cmc.rodi.domain.member.exception.MemberErrorCode;
import cmc.rodi.domain.member.repository.MemberOnboardingRepository;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.support.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 온보딩 제출의 실제 저장(jsonb 리스트 순서 보존·member 반영)과 재제출 거부를 실제 DB로 검증한다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OnboardingIntegrationTest {

    @Autowired OnboardingService onboardingService;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberOnboardingRepository memberOnboardingRepository;

    private OnboardingRequest request() {
        return new OnboardingRequest(
                DrivingPeriod.YEARS_2_10,
                RecentFrequency.MONTHLY_1_2,
                List.of(RoadExperience.SOLO, RoadExperience.PROFESSIONAL_TRAINING),
                SoloDrivingRange.HIGHWAY_LONG,
                SoloParkingLevel.MOSTLY_POSSIBLE,
                Level.NAVIGATOR,
                List.of(
                        PracticeType.LANE_CHANGE,
                        PracticeType.ROUNDABOUT,
                        PracticeType.NARROW_ROAD),
                CarType.MIDSIZE,
                "강남 운전 자신있게");
    }

    @Test
    @DisplayName("제출: member_onboarding 저장(jsonb 순서 보존) + member에 레벨·목표 반영")
    void 온보딩_저장() {
        Member member = memberRepository.save(Member.createBySocial("onboarding@kakao.com"));

        onboardingService.submit(member.getId(), request());

        // member에는 레벨·운전목표만 반영
        Member reloadedMember = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloadedMember.getLevel()).isEqualTo(Level.NAVIGATOR);
        assertThat(reloadedMember.getDrivingGoal()).isEqualTo("강남 운전 자신있게");

        // 원자료는 member_onboarding(1:1)에 저장, jsonb 리스트는 순서 보존
        MemberOnboarding onboarding =
                memberOnboardingRepository.findById(member.getId()).orElseThrow();
        assertThat(onboarding.getDrivingPeriod()).isEqualTo(DrivingPeriod.YEARS_2_10);
        assertThat(onboarding.getRoadExperiences())
                .containsExactly(RoadExperience.SOLO, RoadExperience.PROFESSIONAL_TRAINING);
        assertThat(onboarding.getPracticeTypes())
                .containsExactly(
                        PracticeType.LANE_CHANGE,
                        PracticeType.ROUNDABOUT,
                        PracticeType.NARROW_ROAD); // 순서 = 우선순위
        assertThat(onboarding.getCarType()).isEqualTo(CarType.MIDSIZE);
        assertThat(onboarding.getOnboardedAt()).isNotNull();
    }

    @Test
    @DisplayName("재제출: 이미 온보딩한 회원이면 ALREADY_ONBOARDED")
    void 재제출_거부() {
        Member member = memberRepository.save(Member.createBySocial("resubmit@kakao.com"));
        onboardingService.submit(member.getId(), request());

        assertThatThrownBy(() -> onboardingService.submit(member.getId(), request()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(MemberErrorCode.ALREADY_ONBOARDED));
    }
}

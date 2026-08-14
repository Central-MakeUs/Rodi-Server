package cmc.rodi.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.DrivingPeriod;
import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.MemberOnboarding;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberOnboardingRepository;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.member.service.MemberHardDeleteService;
import cmc.rodi.domain.place.entity.Bookmark;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.BookmarkRepository;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.practice.repository.MemberPracticeRepository;
import cmc.rodi.domain.practice.service.PracticeService;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.repository.ReviewRepository;
import cmc.rodi.domain.review.service.ReviewService;
import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.repository.SocialAccountRepository;
import cmc.rodi.global.auth.service.RefreshTokenService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import cmc.rodi.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 즉시 탈퇴(내부 테스트용) — CASCADE 없는 테이블까지 지워지고 소셜 식별자가 풀려 재가입이 가능해진다. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class MemberHardDeleteIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);
    private static final String PROVIDER_ID = "kakao-9999";

    @Autowired MemberHardDeleteService memberHardDeleteService;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberOnboardingRepository memberOnboardingRepository;
    @Autowired SocialAccountRepository socialAccountRepository;
    @Autowired BookmarkRepository bookmarkRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired PracticeService practiceService;
    @Autowired ReviewService reviewService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired MemberPracticeRepository memberPracticeRepository;
    @Autowired RefreshTokenService refreshTokenService;

    @PersistenceContext EntityManager em;

    private long countBy(String table, Long memberId) {
        return ((Number)
                        em.createNativeQuery(
                                        "SELECT COUNT(*) FROM " + table + " WHERE member_id = :id")
                                .setParameter("id", memberId)
                                .getSingleResult())
                .longValue();
    }

    @Test
    @DisplayName("연관 데이터까지 전부 지워지고 소셜 식별자가 풀린다")
    void 즉시_탈퇴() {
        Member me = memberRepository.save(Member.createBySocial("hard@kakao.com"));
        me.applyOnboarding(Level.ROOKIE, "고속도로 타기");
        Long memberId = me.getId();
        socialAccountRepository.save(
                SocialAccount.builder()
                        .member(me)
                        .provider(SocialProvider.KAKAO)
                        .providerId(PROVIDER_ID)
                        .email("hard@kakao.com")
                        .build());
        Course course =
                courseRepository.save(
                        Course.builder()
                                .name("탈퇴 코스")
                                .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                                .build());
        practiceService.register(course.getId(), memberId);
        // CASCADE가 없어 직접 지워야 하는 테이블들 — 행이 없으면 아래 단언이 헛돌아 누락을 못 잡는다
        bookmarkRepository.save(Bookmark.builder().member(me).place(course).build());
        refreshTokenService.issue(me);
        // applyOnboarding은 member 컬럼만 채운다 — 온보딩 행은 따로 있어야 한다
        memberOnboardingRepository.save(
                MemberOnboarding.builder()
                        .member(me)
                        .drivingPeriod(DrivingPeriod.MONTHS_1_2)
                        .practiceTypes(List.of(PracticeType.STRAIGHT))
                        .onboardedAt(LocalDateTime.now())
                        .build());
        reviewService.create(
                course.getId(),
                memberId,
                new ReviewRequest(
                        true,
                        Difficulty.EASY,
                        Congestion.NORMAL,
                        PracticeMethod.SOLO,
                        "지워질 후기",
                        null));
        em.flush();

        // 지우기 전에 실제로 있었음을 확인한다
        assertThat(countBy("bookmark", memberId)).isPositive();
        assertThat(countBy("refresh_token", memberId)).isPositive();
        assertThat(countBy("social_account", memberId)).isPositive();
        assertThat(countBy("member_onboarding", memberId)).isPositive();

        memberHardDeleteService.hardDelete(memberId);

        assertThat(memberRepository.findById(memberId)).isEmpty();
        // FK에 CASCADE가 없어 직접 지워야 하는 것들 — 하나라도 빠지면 여기서 FK 위반으로 터진다
        assertThat(countBy("social_account", memberId)).isZero();
        assertThat(countBy("member_onboarding", memberId)).isZero();
        assertThat(countBy("bookmark", memberId)).isZero();
        assertThat(countBy("refresh_token", memberId)).isZero();
        // CASCADE로 함께 사라지는 것들
        assertThat(countBy("member_practice", memberId)).isZero();
        assertThat(countBy("review", memberId)).isZero();

        // 소셜 식별자가 풀려야 같은 계정으로 곧바로 재가입할 수 있다
        assertThat(
                        socialAccountRepository.findByProviderAndProviderId(
                                SocialProvider.KAKAO, PROVIDER_ID))
                .isEmpty();
    }

    @Test
    @DisplayName("없는 회원은 404")
    void 없는_회원() {
        assertThatThrownBy(() -> memberHardDeleteService.hardDelete(999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
    }
}

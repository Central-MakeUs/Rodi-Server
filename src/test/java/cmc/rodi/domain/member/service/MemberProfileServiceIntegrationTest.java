package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.dto.LevelProgressResponse;
import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.dto.MyPageResponse;
import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Bookmark;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.BookmarkRepository;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** U1: 운전 목표 수정(마이페이지) — 반영·빈값 삭제·없는 회원 404를 실제 DB로 검증. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MemberProfileServiceIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired MemberProfileService memberProfileService;
    @Autowired MemberRepository memberRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired BookmarkRepository bookmarkRepository;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    @Test
    @DisplayName("마이페이지 요약: 닉네임·레벨·추천태그·목표 + 저장한 장소 수(place 조합)")
    void 마이페이지_요약() {
        Member member = memberRepository.save(Member.createBySocial("mp@kakao.com"));
        member.assignNickname("성난 초보");
        member.applyOnboarding(Level.ROOKIE, "골목길에 익숙해지기");
        memberRepository.save(member);

        Course c1 =
                courseRepository.save(
                        Course.builder().name("코스1").location(point(37.5, 127.0)).build());
        Course c2 =
                courseRepository.save(
                        Course.builder().name("코스2").location(point(37.6, 127.1)).build());
        bookmarkRepository.save(Bookmark.builder().member(member).place(c1).build());
        bookmarkRepository.save(Bookmark.builder().member(member).place(c2).build());

        MyPageResponse res = memberProfileService.getMyPage(member.getId());

        assertThat(res.nickname()).isEqualTo("성난 초보");
        assertThat(res.level()).isEqualTo(Level.ROOKIE);
        assertThat(res.recommendationTags()).containsExactly("U_TURN", "INTERSECTION", "PARKING");
        assertThat(res.drivingGoal()).isEqualTo("골목길에 익숙해지기");
        assertThat(res.savedPlaceCount()).isEqualTo(2);
        // 온보딩으로 Rookie를 받아 50km에서 시작 — 구간 0%
        assertThat(res.levelProgress().totalDistanceKm()).isEqualTo(50.0);
        assertThat(res.levelProgress().currentLevelStartKm()).isEqualTo(50.0);
        assertThat(res.levelProgress().nextLevelKm()).isEqualTo(150.0);
        assertThat(res.levelProgress().progressPercent()).isZero();
    }

    @Test
    @DisplayName("레벨 게이지: 구간 절반을 달리면 50%, 최상위는 목표 없이 100%")
    void 레벨_게이지() {
        Member rookie = memberRepository.save(Member.createBySocial("gauge@kakao.com"));
        rookie.applyOnboarding(Level.ROOKIE, null);
        rookie.addDistance(50_000); // 50 → 100km, 루키 구간(50~150)의 절반
        memberRepository.save(rookie); // 트랜잭션 밖이라 명시 저장

        LevelProgressResponse progress =
                memberProfileService.getMyPage(rookie.getId()).levelProgress();
        assertThat(progress.totalDistanceKm()).isEqualTo(100.0);
        assertThat(progress.progressPercent()).isEqualTo(50);

        Member top = memberRepository.save(Member.createBySocial("top@kakao.com"));
        top.applyOnboarding(Level.NAVIGATOR, null);
        top.addDistance(212_400); // 600 → 812.4km, 최상위 위로도 계속 쌓인다
        memberRepository.save(top);

        LevelProgressResponse topProgress =
                memberProfileService.getMyPage(top.getId()).levelProgress();
        assertThat(topProgress.totalDistanceKm()).isEqualTo(812.4);
        assertThat(topProgress.currentLevelStartKm()).isEqualTo(600.0);
        assertThat(topProgress.nextLevelKm()).isNull(); // 목표 없음
        assertThat(topProgress.progressPercent()).isEqualTo(100);
    }

    @Test
    @DisplayName("운전 목표 수정이 저장되고 재조회 시 반영된다")
    void 운전목표_수정() {
        Member member = memberRepository.save(Member.createBySocial("goal@kakao.com"));

        memberProfileService.update(member.getId(), new MemberUpdateRequest("고속도로 합류 연습"));

        Member found = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(found.getDrivingGoal()).isEqualTo("고속도로 합류 연습");
    }

    @Test
    @DisplayName("빈값으로 수정하면 운전 목표가 삭제된다(null)")
    void 빈값이면_목표_삭제() {
        Member member = memberRepository.save(Member.createBySocial("clear@kakao.com"));
        member.applyOnboarding(Level.SEED, "기존 목표");
        memberRepository.save(member);

        memberProfileService.update(member.getId(), new MemberUpdateRequest("  ")); // 공백=빈값

        Member found = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(found.getDrivingGoal()).isNull();
    }

    @Test
    @DisplayName("없는 회원 수정 시 404")
    void 없는_회원_404() {
        assertThatThrownBy(
                        () -> memberProfileService.update(999_999L, new MemberUpdateRequest("목표")))
                .isInstanceOf(BusinessException.class);
    }
}

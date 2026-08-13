package cmc.rodi.domain.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.practice.dto.PracticeSkipReasonRequest;
import cmc.rodi.domain.practice.dto.PracticeVisitRequest;
import cmc.rodi.domain.practice.dto.PracticeVisitResponse;
import cmc.rodi.domain.practice.entity.MemberPractice;
import cmc.rodi.domain.practice.entity.PracticeStatus;
import cmc.rodi.domain.practice.entity.SkipReason;
import cmc.rodi.domain.practice.exception.PracticeErrorCode;
import cmc.rodi.domain.practice.repository.MemberPracticeRepository;
import cmc.rodi.domain.practice.service.PracticeService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 방문 기록·미방문 사유 — 횟수 누적, 인증 판정(레벨별), 사유 수정 불가, 소유자 검사. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class PracticeStatusIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired PracticeService practiceService;
    @Autowired MemberPracticeRepository memberPracticeRepository;
    @PersistenceContext EntityManager em;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;

    private Course seedCourse(String name) {
        return courseRepository.save(
                Course.builder()
                        .name(name)
                        .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                        .build());
    }

    private Member seedMember(String email) {
        return memberRepository.save(Member.createBySocial(email));
    }

    private Long seedPractice(Member member, String courseName) {
        return practiceService
                .register(seedCourse(courseName).getId(), member.getId())
                .practiceId();
    }

    private Course seedCourseWithDistance(String name, int distanceMeters) {
        return courseRepository.save(
                Course.builder()
                        .name(name)
                        .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                        .distanceMeters(distanceMeters)
                        .build());
    }

    /** 방문 쿨다운(10분)을 지나간 것으로 만든다. 실제로 기다릴 수 없으니 직전 방문 시각을 뒤로 민다 — 서로 다른 방문을 검증하는 테스트에 필요하다. */
    private void expireCooldown(Long practiceId) {
        em.flush(); // 직전 방문의 변경을 먼저 내보내야 아래 UPDATE가 그 위에 얹힌다
        em.createNativeQuery("UPDATE member_practice SET visited_at = ?1 WHERE id = ?2")
                .setParameter(
                        1,
                        LocalDateTime.now().minusMinutes(MemberPractice.VISIT_COOLDOWN_MINUTES + 1))
                .setParameter(2, practiceId)
                .executeUpdate();
        em.clear();
    }

    private static PracticeVisitRequest visited() {
        return new PracticeVisitRequest(null);
    }

    private static PracticeVisitRequest visited(int certifiedMeters) {
        return new PracticeVisitRequest(certifiedMeters);
    }

    private static PracticeSkipReasonRequest skipReason(SkipReason reason, String detail) {
        return new PracticeSkipReasonRequest(reason, detail);
    }

    @Test
    @DisplayName("방문을 기록하면 상태가 VISITED가 되고 횟수·시각이 남는다 — 다시 보내면 또 오른다")
    void 방문_처리() {
        Member me = seedMember("visit@kakao.com");
        Long practiceId = seedPractice(me, "방문 코스");

        practiceService.recordVisit(practiceId, me.getId(), visited());
        MemberPractice after = memberPracticeRepository.findById(practiceId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PracticeStatus.VISITED);
        assertThat(after.getVisitCount()).isEqualTo(1);
        assertThat(after.getVisitedAt()).isNotNull();

        // 같은 코스 재연습 — 쿨다운이 지난 뒤 다시 다녀오면 횟수가 또 오른다
        expireCooldown(practiceId);
        practiceService.recordVisit(practiceId, me.getId(), visited());
        assertThat(memberPracticeRepository.findById(practiceId).orElseThrow().getVisitCount())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("인정 주행거리가 필요 거리(코스 5km의 40% = 2km)에 도달하면 서버가 인증으로 판정한다")
    void 방문_인증() {
        Member me = seedMember("certify@kakao.com");
        me.applyOnboarding(Level.ROOKIE, null); // 인증은 레벨과 함께 기록된다
        Course course =
                courseRepository.save(
                        Course.builder()
                                .name("인증 코스")
                                .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                                .distanceMeters(5_000)
                                .build());
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        // 1.9km — 필요 거리(2km)에 못 미쳐 인증 안 됨
        PracticeVisitResponse partial =
                practiceService.recordVisit(practiceId, me.getId(), visited(1_900));
        assertThat(partial.requiredDistanceMeters()).isEqualTo(2_000);
        assertThat(partial.certifiedNow()).isFalse();

        // 2km — 도달해 인증
        expireCooldown(practiceId);
        PracticeVisitResponse certified =
                practiceService.recordVisit(practiceId, me.getId(), visited(2_000));
        assertThat(certified.certifiedNow()).isTrue();

        MemberPractice after = memberPracticeRepository.findById(practiceId).orElseThrow();
        assertThat(after.getVerifiedLevel()).isEqualTo(Level.ROOKIE); // 인증받은 레벨이 남는다
        assertThat(after.getCertifiedDistanceMeters()).isEqualTo(3_900); // 1900 + 2000
        assertThat(after.getVisitCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("측정 없이 다녀왔어요만 누르면(거리 생략) 인증되지 않는다")
    void 측정_없는_방문() {
        Member me = seedMember("noGps@kakao.com");
        Course course =
                courseRepository.save(
                        Course.builder()
                                .name("경로만 본 코스")
                                .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                                .distanceMeters(3_000)
                                .build());
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        PracticeVisitResponse response =
                practiceService.recordVisit(practiceId, me.getId(), visited());

        assertThat(response.visitCount()).isEqualTo(1); // 연습기록은 남는다
        assertThat(response.addedCertifiedDistanceMeters()).isZero();
        assertThat(response.certifiedNow()).isFalse();
        assertThat(memberPracticeRepository.findById(practiceId).orElseThrow().getVerifiedLevel())
                .isNull();
    }

    @Test
    @DisplayName("레벨이 그대로면 이후 미인증 방문이 있어도 인증받은 레벨은 유지된다")
    void 인증_유지() {
        Member me = seedMember("keep@kakao.com");
        me.applyOnboarding(Level.ROOKIE, null);
        Course course =
                courseRepository.save(
                        Course.builder()
                                .name("유지 코스")
                                .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                                .distanceMeters(2_000)
                                .build());
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        practiceService.recordVisit(practiceId, me.getId(), visited(800)); // 필요 800m → 인증
        expireCooldown(practiceId);
        PracticeVisitResponse second =
                practiceService.recordVisit(practiceId, me.getId(), visited(100));

        assertThat(second.certifiedNow()).isFalse(); // 이번 회차는 미달
        assertThat(memberPracticeRepository.findById(practiceId).orElseThrow().getVerifiedLevel())
                .isEqualTo(Level.ROOKIE); // 인증받은 레벨은 그대로
    }

    @Test
    @DisplayName("방문을 기록하면 인정 주행거리가 회원에게 누적되고 기준에 닿으면 레벨이 오른다")
    void 레벨_누적과_승급() {
        Member me = seedMember("levelup@kakao.com");
        me.applyOnboarding(Level.SEED, null); // 0km에서 시작
        Course course = seedCourseWithDistance("승급 코스", 40_000);
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        PracticeVisitResponse first =
                practiceService.recordVisit(practiceId, me.getId(), visited(40_000));
        assertThat(first.totalDistanceKm()).isEqualTo(40.0);
        assertThat(first.levelUp()).isFalse(); // 50km 미달
        assertThat(first.newLevel()).isNull();

        expireCooldown(practiceId);
        PracticeVisitResponse second =
                practiceService.recordVisit(practiceId, me.getId(), visited(40_000));
        assertThat(second.totalDistanceKm()).isEqualTo(80.0);
        assertThat(second.levelUp()).isTrue();
        assertThat(second.newLevel()).isEqualTo(Level.ROOKIE);
        assertThat(memberRepository.findById(me.getId()).orElseThrow().getLevel())
                .isEqualTo(Level.ROOKIE);
    }

    @Test
    @DisplayName("주행거리가 없는 장소는 측정값을 보내도 0으로 기록된다")
    void 주차장_측정값_무시() {
        Member me = seedMember("parking@kakao.com");
        me.applyOnboarding(Level.ROOKIE, null);
        Long practiceId = seedPractice(me, "주차장 대체 코스"); // distanceMeters 없음

        PracticeVisitResponse response =
                practiceService.recordVisit(practiceId, me.getId(), visited(3_000));

        assertThat(response.visitCount()).isEqualTo(1); // 방문 자체는 기록된다
        assertThat(response.addedCertifiedDistanceMeters()).isZero();
        assertThat(response.certifiedNow()).isFalse();

        MemberPractice after = memberPracticeRepository.findById(practiceId).orElseThrow();
        // 인증·레벨 어디에도 쓰이지 않는 값이라 컬럼에도 쌓지 않는다
        assertThat(after.getCertifiedDistanceMeters()).isZero();
        assertThat(after.getVerifiedLevel()).isNull();
    }

    @Test
    @DisplayName("인증과 동시에 승급하면 인증은 승급 전 레벨로 기록된다")
    void 승급_전_레벨로_인증() {
        Member me = seedMember("promote@kakao.com");
        me.applyOnboarding(Level.SEED, null);
        Course course = seedCourseWithDistance("승급 유발 코스", 60_000); // 필요 5km(상한), 누적 60km
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        PracticeVisitResponse response =
                practiceService.recordVisit(practiceId, me.getId(), visited(60_000));

        assertThat(response.certifiedNow()).isTrue();
        assertThat(response.levelUp()).isTrue();
        assertThat(response.newLevel()).isEqualTo(Level.ROOKIE);
        // 이 주행은 Seed로 시작했다 — Rookie의 인증은 Rookie가 되어 다시 받아야 한다
        assertThat(memberPracticeRepository.findById(practiceId).orElseThrow().getVerifiedLevel())
                .isEqualTo(Level.SEED);
    }

    @Test
    @DisplayName("레벨 없는 회원(온보딩 미완료)의 인증은 레벨을 남기지 않는다")
    void 레벨_없는_회원_인증() {
        Member me = seedMember("nolevel@kakao.com"); // 온보딩 전이라 level == null
        Course course = seedCourseWithDistance("레벨 없는 코스", 2_000);
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        PracticeVisitResponse response =
                practiceService.recordVisit(practiceId, me.getId(), visited(800));

        // 거리 판정은 통과하지만 남길 레벨이 없다 — 온보딩 후 그 레벨에서 다시 인증받는다
        assertThat(response.certifiedNow()).isTrue();
        assertThat(memberPracticeRepository.findById(practiceId).orElseThrow().getVerifiedLevel())
                .isNull();
    }

    @Test
    @DisplayName("측정값이 코스 거리를 넘어도 코스 거리까지만 누적된다")
    void 누적_상한() {
        Member me = seedMember("cap@kakao.com");
        me.applyOnboarding(Level.SEED, null);
        Course course = seedCourseWithDistance("상한 코스", 5_000);
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        PracticeVisitResponse response =
                practiceService.recordVisit(practiceId, me.getId(), visited(600_000));

        assertThat(response.addedCertifiedDistanceMeters()).isEqualTo(600_000); // 인증 판정용 원값
        assertThat(response.totalDistanceKm()).isEqualTo(5.0); // 누적은 코스 거리까지
        assertThat(response.levelUp()).isFalse(); // 한 방에 Navigator로 뛰지 않는다
    }

    @Test
    @DisplayName("주차장 방문과 측정 없는 방문은 누적 거리를 바꾸지 않는다")
    void 누적되지_않는_방문() {
        Member me = seedMember("nogain@kakao.com");
        me.applyOnboarding(Level.ROOKIE, null); // 50km에서 시작
        Long parkingPracticeId = seedPractice(me, "주차장 대체 코스"); // distanceMeters 없음
        Course course = seedCourseWithDistance("측정 없는 코스", 5_000);
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        assertThat(
                        practiceService
                                .recordVisit(parkingPracticeId, me.getId(), visited(3_000))
                                .totalDistanceKm())
                .isEqualTo(50.0);
        assertThat(practiceService.recordVisit(practiceId, me.getId(), visited()).totalDistanceKm())
                .isEqualTo(50.0);
    }

    @Test
    @DisplayName("직전 방문 직후의 재호출은 아무것도 바꾸지 않고 현재 상태를 돌려준다")
    void 쿨다운() {
        Member me = seedMember("cooldown@kakao.com");
        me.applyOnboarding(Level.SEED, null);
        Course course = seedCourseWithDistance("쿨다운 코스", 5_000);
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        practiceService.recordVisit(practiceId, me.getId(), visited(5_000));

        // 재시도가 곧바로 들어온 상황
        PracticeVisitResponse retry =
                practiceService.recordVisit(practiceId, me.getId(), visited(5_000));

        assertThat(retry.visitCount()).isEqualTo(1); // 횟수 그대로
        assertThat(retry.addedCertifiedDistanceMeters()).isZero();
        assertThat(retry.certifiedNow()).isFalse();
        assertThat(retry.totalDistanceKm()).isEqualTo(5.0); // 두 배로 쌓이지 않는다
        assertThat(retry.levelUp()).isFalse();

        MemberPractice after = memberPracticeRepository.findById(practiceId).orElseThrow();
        assertThat(after.getVisitCount()).isEqualTo(1);
        assertThat(after.getCertifiedDistanceMeters()).isEqualTo(5_000);

        // 쿨다운이 지나면 다시 반영된다
        expireCooldown(practiceId);
        PracticeVisitResponse later =
                practiceService.recordVisit(practiceId, me.getId(), visited(5_000));
        assertThat(later.visitCount()).isEqualTo(2);
        assertThat(later.totalDistanceKm()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("영속성 컨텍스트가 비어 place가 프록시로 와도 코스 거리를 읽어 인증·누적한다")
    void 프록시_코스_거리() {
        Member me = seedMember("proxy@kakao.com");
        me.applyOnboarding(Level.SEED, null);
        Course course = seedCourseWithDistance("프록시 코스", 5_000);
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        // 컨텍스트를 비우면 이후 조회에서 place가 지연 로딩 프록시로 온다 —
        // 하위 타입 검사로 코스 거리를 읽으면 여기서 조용히 0이 된다.
        em.flush();
        em.clear();

        PracticeVisitResponse response =
                practiceService.recordVisit(practiceId, me.getId(), visited(5_000));

        assertThat(response.requiredDistanceMeters()).isEqualTo(2_000); // 5km × 40%
        assertThat(response.certifiedNow()).isTrue();
        assertThat(response.totalDistanceKm()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("사유 제출 한 번으로 미방문 상태와 사유가 함께 저장된다 — 기타면 직접 입력도")
    void 미방문_사유_제출() {
        Member me = seedMember("skip@kakao.com");
        Long practiceId = seedPractice(me, "미방문 코스");

        practiceService.submitSkipReason(
                practiceId, me.getId(), skipReason(SkipReason.OTHER, "차가 정비 중이었어요"));

        MemberPractice after = memberPracticeRepository.findById(practiceId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PracticeStatus.NOT_VISITED);
        assertThat(after.getSkipReason()).isEqualTo(SkipReason.OTHER);
        assertThat(after.getSkipDetail()).isEqualTo("차가 정비 중이었어요");
        assertThat(after.getVisitCount()).isZero();
    }

    @Test
    @DisplayName("이미 저장된 미방문 사유는 덮어쓸 수 없고(409), 다시 다녀오면 비워져 새로 남길 수 있다")
    void 미방문_사유_수정불가() {
        Member me = seedMember("skip2@kakao.com");
        Long practiceId = seedPractice(me, "사유 코스");
        practiceService.submitSkipReason(
                practiceId, me.getId(), skipReason(SkipReason.TOO_FAR, null));

        assertThatThrownBy(
                        () ->
                                practiceService.submitSkipReason(
                                        practiceId,
                                        me.getId(),
                                        skipReason(SkipReason.SCHEDULE_DID_NOT_MATCH, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PracticeErrorCode.SKIP_REASON_ALREADY_SET);

        practiceService.recordVisit(practiceId, me.getId(), visited());
        MemberPractice after = memberPracticeRepository.findById(practiceId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PracticeStatus.VISITED);
        assertThat(after.getSkipReason()).isNull();
        assertThat(after.getSkipDetail()).isNull();
    }

    @Test
    @DisplayName("타인 항목의 방문 기록은 403")
    void 소유자_검사() {
        Member owner = seedMember("owner2@kakao.com");
        Member other = seedMember("other2@kakao.com");
        Long practiceId = seedPractice(owner, "남의 코스");

        assertThatThrownBy(() -> practiceService.recordVisit(practiceId, other.getId(), visited()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PracticeErrorCode.NOT_PRACTICE_OWNER);
    }
}

package cmc.rodi.domain.practice;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.practice.dto.PracticeVisitRequest;
import cmc.rodi.domain.practice.repository.MemberPracticeRepository;
import cmc.rodi.domain.practice.service.PracticeService;
import cmc.rodi.support.TestcontainersConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 방문이 동시에 두 번 들어와도 한 번만 반영되는지 — 쿨다운은 커밋된 {@code visited_at}을 봐야 동작하므로 <b>연습 항목 행 잠금</b>이 함께 있어야
 * 한다.
 *
 * <p>트랜잭션 경계를 각 스레드가 따로 가져야 재현되므로 클래스에 {@code @Transactional}을 붙이지 않고 {@link TransactionTemplate}로
 * 직접 연다. 정리는 {@link #cleanUp()}에서 한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PracticeVisitConcurrencyTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);
    private static final int COURSE_METERS = 5_000;

    @Autowired PracticeService practiceService;
    @Autowired MemberPracticeRepository memberPracticeRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired TransactionTemplate transactionTemplate;

    private Long memberId;
    private Long practiceId;

    @AfterEach
    void cleanUp() {
        memberPracticeRepository.deleteAll();
        courseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    private void seed() {
        transactionTemplate.executeWithoutResult(
                status -> {
                    Member member =
                            memberRepository.save(Member.createBySocial("concurrent@kakao.com"));
                    member.applyOnboarding(Level.SEED, null);
                    memberId = member.getId();
                    Course course =
                            courseRepository.save(
                                    Course.builder()
                                            .name("동시 방문 코스")
                                            .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                                            .distanceMeters(COURSE_METERS)
                                            .build());
                    practiceId = practiceService.register(course.getId(), memberId).practiceId();
                });
    }

    @Test
    @DisplayName("같은 방문이 동시에 두 번 들어와도 거리는 한 번만 누적된다")
    void 동시_방문_기록() throws Exception {
        seed();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        for (int i = 0; i < 2; i++) {
            new Thread(
                            () -> {
                                try {
                                    start.await();
                                    transactionTemplate.executeWithoutResult(
                                            status ->
                                                    practiceService.recordVisit(
                                                            practiceId,
                                                            memberId,
                                                            new PracticeVisitRequest(
                                                                    COURSE_METERS)));
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                } finally {
                                    done.countDown();
                                }
                            })
                    .start();
        }

        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();

        // 잠금이 없으면 둘 다 쿨다운을 통과해 회원 누적이 10km가 된다(레벨은 되돌릴 수 없다).
        assertThat(memberRepository.findById(memberId).orElseThrow().getTotalDistanceMeters())
                .isEqualTo(COURSE_METERS);
        assertThat(memberPracticeRepository.findById(practiceId).orElseThrow().getVisitCount())
                .isEqualTo(1);
    }
}

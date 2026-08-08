package cmc.rodi.domain.practice;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.practice.service.PracticeQueryService;
import cmc.rodi.domain.practice.service.PracticeService;
import cmc.rodi.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연습 목록 조회의 쿼리 수가 <b>항목 수에 비례하지 않는지</b> 확인한다.
 *
 * <p>코스 연습유형(`Course.tags`)은 지연 로딩 컬렉션이라 항목마다 따로 읽히기 쉽다. {@code @BatchSize}를 떼면 항목 수만큼 쿼리가 늘어 이
 * 테스트가 깨진다.
 */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class PracticeListQueryCountTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired PracticeService practiceService;
    @Autowired PracticeQueryService practiceQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManagerFactory entityManagerFactory;
    @PersistenceContext EntityManager em;

    private Member seedPractices(String email, int count) {
        Member member = memberRepository.save(Member.createBySocial(email));
        for (int i = 0; i < count; i++) {
            Course course =
                    Course.builder()
                            .name("코스 " + email + i)
                            .location(GEO.createPoint(new Coordinate(127.0 + i * 0.01, 37.5)))
                            .distanceMeters(2_000)
                            .build();
            course.addTag(PracticeType.STRAIGHT);
            course.addTag(PracticeType.LANE_CHANGE);
            practiceService.register(courseRepository.save(course).getId(), member.getId());
        }
        return member;
    }

    private long countQueriesForList(Long memberId) {
        em.flush();
        em.clear(); // 영속성 컨텍스트 캐시가 쿼리를 가려 수를 왜곡하지 않도록
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        practiceQueryService.getMyPractices(memberId, 20, null);
        return stats.getPrepareStatementCount();
    }

    @Test
    @DisplayName("항목이 3배로 늘어도 쿼리 수는 그대로다(연습유형 N+1 없음)")
    void 쿼리_수가_항목_수에_비례하지_않는다() {
        Member few = seedPractices("few@kakao.com", 2);
        Member many = seedPractices("many@kakao.com", 6);

        long forFew = countQueriesForList(few.getId());
        long forMany = countQueriesForList(many.getId());

        assertThat(forMany).isEqualTo(forFew);
        assertThat(forMany).isLessThanOrEqualTo(5); // 목록·총계·후기여부·태그배치
    }
}

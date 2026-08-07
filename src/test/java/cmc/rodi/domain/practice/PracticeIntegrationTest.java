package cmc.rodi.domain.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.PlaceType;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.practice.dto.PracticeItem;
import cmc.rodi.domain.practice.entity.MemberPractice;
import cmc.rodi.domain.practice.entity.PracticeStatus;
import cmc.rodi.domain.practice.repository.MemberPracticeRepository;
import cmc.rodi.domain.practice.service.PracticeQueryService;
import cmc.rodi.domain.practice.service.PracticeService;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import cmc.rodi.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 연습 목록 담기·조회 — 재등록 시 행 유지, 최근 방문순 정렬, 커서 연속성, 장소 요약 재사용. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class PracticeIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired PracticeService practiceService;
    @Autowired PracticeQueryService practiceQueryService;
    @Autowired MemberPracticeRepository memberPracticeRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MockMvc mockMvc;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    private Course seedCourse(String name) {
        return courseRepository.save(
                Course.builder().name(name).location(point(37.5, 127.0)).build());
    }

    private Member seedMember(String email) {
        return memberRepository.save(Member.createBySocial(email));
    }

    @Test
    @DisplayName("담으면 PLANNED로 저장되고, 없는 장소는 404")
    void 담기() {
        Course course = seedCourse("연습 코스");
        Member me = seedMember("practice@kakao.com");

        var response = practiceService.register(course.getId(), me.getId());

        assertThat(response.status()).isEqualTo(PracticeStatus.PLANNED);
        assertThat(response.visitCount()).isZero();
        assertThat(memberPracticeRepository.countByMemberId(me.getId())).isEqualTo(1);

        assertThatThrownBy(() -> practiceService.register(999_999L, me.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 담긴 장소를 다시 담으면 행이 늘지 않고 상태만 예정으로 되돌아간다(연습 횟수 유지)")
    void 재등록() {
        Course course = seedCourse("재도전 코스");
        Member me = seedMember("replan@kakao.com");
        Long practiceId = practiceService.register(course.getId(), me.getId()).practiceId();

        // 방문 처리 후 다시 담기
        MemberPractice practice = memberPracticeRepository.findById(practiceId).orElseThrow();
        practice.markVisited(LocalDateTime.now());

        var again = practiceService.register(course.getId(), me.getId());

        assertThat(again.practiceId()).isEqualTo(practiceId); // 같은 행
        assertThat(again.status()).isEqualTo(PracticeStatus.PLANNED);
        assertThat(again.visitCount()).isEqualTo(1); // 지난 연습 횟수는 유지
        assertThat(memberPracticeRepository.countByMemberId(me.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("주차장도 담을 수 있고, 목록의 장소 요약은 저장 목록과 같은 형식이다")
    void 주차장_담기와_장소요약() {
        Parking parking =
                parkingRepository.save(
                        Parking.builder()
                                .name("세종로 공영")
                                .address("서울특별시 종로구")
                                .location(point(37.57, 126.97))
                                .capacity(1260)
                                .build());
        Member me = seedMember("parking@kakao.com");
        practiceService.register(parking.getId(), me.getId());

        List<PracticeItem> items =
                practiceQueryService.getMyPractices(me.getId(), 10, null).items();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).place().type()).isEqualTo(PlaceType.PARKING);
        assertThat(items.get(0).place().capacity()).isEqualTo(1260);
        assertThat(items.get(0).place().distanceFromMe()).isNull(); // 현위치를 받지 않는다
    }

    @Test
    @DisplayName("목록은 최근 방문순 — 나중에 담았어도 먼저 다녀온 항목이 위로 온다")
    void 최근_방문순_정렬() {
        Member me = seedMember("order@kakao.com");
        Course older = seedCourse("먼저 담은 코스");
        Course newer = seedCourse("나중에 담은 코스");
        Long olderId = practiceService.register(older.getId(), me.getId()).practiceId();
        Long newerId = practiceService.register(newer.getId(), me.getId()).practiceId();

        // 먼저 담은 항목을 방문 처리 → 정렬 기준값이 최신이 된다
        memberPracticeRepository
                .findById(olderId)
                .orElseThrow()
                .markVisited(LocalDateTime.now().plusMinutes(1));

        List<PracticeItem> items =
                practiceQueryService.getMyPractices(me.getId(), 10, null).items();

        assertThat(items).extracting(PracticeItem::practiceId).containsExactly(olderId, newerId);
        assertThat(items.get(0).status()).isEqualTo(PracticeStatus.VISITED);
        assertThat(items.get(0).visitCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("커서로 2페이지가 중복 없이 이어지고 totalCount는 첫 페이지에만 채워진다")
    void 커서_페이지네이션() {
        Member me = seedMember("cursor@kakao.com");
        for (int i = 1; i <= 5; i++) {
            practiceService.register(seedCourse("코스 " + i).getId(), me.getId());
        }

        CursorPage<PracticeItem> first = practiceQueryService.getMyPractices(me.getId(), 3, null);
        assertThat(first.items()).hasSize(3);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.totalCount()).isEqualTo(5);

        CursorPage<PracticeItem> second =
                practiceQueryService.getMyPractices(me.getId(), 3, first.nextCursor());
        assertThat(second.items()).hasSize(2);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.totalCount()).isNull();

        List<Long> ids =
                java.util.stream.Stream.concat(first.items().stream(), second.items().stream())
                        .map(PracticeItem::practiceId)
                        .toList();
        assertThat(ids).doesNotHaveDuplicates().hasSize(5);
    }

    @Test
    @DisplayName("연습 목록 API는 미인증이면 401")
    void 미인증_401() throws Exception {
        mockMvc.perform(post("/api/v1/places/1/practices")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/members/me/practices")).andExpect(status().isUnauthorized());
    }
}

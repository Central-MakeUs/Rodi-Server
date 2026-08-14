package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.dto.CourseRegisterRequest;
import cmc.rodi.domain.place.dto.CourseRegisterRequest.WaypointRequest;
import cmc.rodi.domain.place.dto.CourseRegisterResponse;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceSearchRequest;
import cmc.rodi.domain.place.entity.ApprovalStatus;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Waypoint;
import cmc.rodi.domain.place.entity.WaypointType;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.service.CourseService;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 코스 등록 저장 결과(스펙 014) — 경로점·태그·코스명 기본값·승인 대기 상태와 등록 직후 비노출. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class CourseRegisterIntegrationTest {

    private static final double START_LAT = 37.5273;
    private static final double START_LNG = 127.0403;

    @Autowired CourseService courseService;
    @Autowired PlaceQueryService placeQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MockMvc mockMvc;
    @PersistenceContext EntityManager em;

    private Member me;

    @BeforeEach
    void setUp() {
        me = memberRepository.save(Member.createBySocial("register@kakao.com"));
    }

    private CourseRegisterRequest request(String name, List<WaypointRequest> waypoints) {
        return new CourseRegisterRequest(
                name,
                "서울특별시 강남구",
                8200,
                waypoints,
                List.of(PracticeType.STRAIGHT, PracticeType.LANE_CHANGE),
                "차선이 넓고, 직선 구간이 길어요.",
                "갑자기 나오는 자전거 주의!");
    }

    private List<WaypointRequest> fullRoute() {
        return List.of(
                new WaypointRequest(WaypointType.START, START_LAT, START_LNG, "압구정로데오역"),
                new WaypointRequest(WaypointType.VIA, 37.5227, 127.0521, "청담사거리"),
                new WaypointRequest(WaypointType.VIA, 37.5266, 127.0670, "영동대교남단"),
                new WaypointRequest(WaypointType.DESTINATION, 37.5133, 127.0533, "삼성중앙역"));
    }

    private Course register(CourseRegisterRequest request) {
        CourseRegisterResponse response = courseService.register(request, me.getId());
        return courseRepository.findById(response.courseId()).orElseThrow();
    }

    @Test
    @DisplayName("등록: 승인 대기 상태로 저장되고 등록자·주소·주행거리·한줄소개가 들어간다")
    void 등록() {
        Course course = register(request("압구정 야경 코스", fullRoute()));

        assertThat(course.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(course.getCreatedBy().getId()).isEqualTo(me.getId());
        assertThat(course.getName()).isEqualTo("압구정 야경 코스");
        assertThat(course.getAddress()).isEqualTo("서울특별시 강남구");
        assertThat(course.getDistanceMeters()).isEqualTo(8200);
        assertThat(course.getDescription()).isEqualTo("차선이 넓고, 직선 구간이 길어요.");
        assertThat(course.getCautions()).containsExactly("갑자기 나오는 자전거 주의!");
        assertThat(course.getTags())
                .containsExactlyInAnyOrder(PracticeType.STRAIGHT, PracticeType.LANE_CHANGE);
    }

    @Test
    @DisplayName("등록: 코스명을 생략하면 출발지 지점명이 코스명이 된다")
    void 코스명_기본값() {
        Course course = register(request(null, fullRoute()));

        assertThat(course.getName()).isEqualTo("압구정로데오역");
    }

    @Test
    @DisplayName("등록: 대표 좌표는 출발지 좌표(SRID 4326)다")
    void 대표_좌표() {
        Course course = register(request(null, fullRoute()));
        Long courseId = course.getId();
        courseRepository.flush();
        em.clear();

        Course reloaded = courseRepository.findById(courseId).orElseThrow();

        assertThat(reloaded.getLocation().getY()).isCloseTo(START_LAT, within(1e-6)); // 위도
        assertThat(reloaded.getLocation().getX()).isCloseTo(START_LNG, within(1e-6)); // 경도
        assertThat(reloaded.getLocation().getSRID()).isEqualTo(4326);
    }

    @Test
    @DisplayName("등록: 경로점이 배열 순서대로 sequence 0부터 저장된다")
    void 경로점_순서() {
        Course course = register(request(null, fullRoute()));

        assertThat(course.getWaypoints())
                .extracting(Waypoint::getSequence, Waypoint::getWaypointType, Waypoint::getName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                (short) 0, WaypointType.START, "압구정로데오역"),
                        org.assertj.core.groups.Tuple.tuple((short) 1, WaypointType.VIA, "청담사거리"),
                        org.assertj.core.groups.Tuple.tuple((short) 2, WaypointType.VIA, "영동대교남단"),
                        org.assertj.core.groups.Tuple.tuple(
                                (short) 3, WaypointType.DESTINATION, "삼성중앙역"));
    }

    @Test
    @DisplayName("등록: 경유지 없이 출발·도착 2개만으로도 저장된다")
    void 경유지_없음() {
        Course course =
                register(
                        request(
                                null,
                                List.of(
                                        new WaypointRequest(
                                                WaypointType.START, START_LAT, START_LNG, "출발"),
                                        new WaypointRequest(
                                                WaypointType.DESTINATION,
                                                37.5133,
                                                127.0533,
                                                "도착"))));

        assertThat(course.getWaypoints()).hasSize(2);
    }

    @Test
    @DisplayName("등록: 주의사항을 생략하면 주의사항 없이 저장된다")
    void 주의사항_없음() {
        CourseRegisterRequest request =
                new CourseRegisterRequest(
                        null,
                        "서울특별시 강남구",
                        8200,
                        fullRoute(),
                        List.of(PracticeType.STRAIGHT),
                        "차선이 넓고, 직선 구간이 길어요.",
                        null);

        assertThat(register(request).getCautions()).isEmpty();
    }

    @Test
    @DisplayName("등록 직후에는 검색에 나오지 않는다 — 승인 전이므로")
    void 등록_직후_비노출() {
        Course course = register(request("검색안됨코스", fullRoute()));

        CursorPage<PlaceListItem> page =
                placeQueryService.searchPlaces(
                        new PlaceSearchRequest("검색안됨코스", START_LAT, START_LNG, 20, null),
                        me.getId());

        assertThat(page.items()).extracting(PlaceListItem::id).doesNotContain(course.getId());
        assertThat(page.totalCount()).isZero();
    }

    @Test
    @DisplayName("등록 폼: 카테고리 5개와 입력 제약을 내려준다")
    void 등록_폼() {
        var form = courseService.getRegistrationForm();

        assertThat(form.maxWaypoints()).isEqualTo(3);
        assertThat(form.practiceType().categories()).hasSize(5);
        assertThat(form.inputs().description().minLength()).isEqualTo(10);
        assertThat(form.inputs().caution().required()).isFalse();
    }

    @Test
    @DisplayName("미인증: 토큰 없이 등록·폼 조회 시 401")
    void 미인증_401() throws Exception {
        mockMvc.perform(
                        post("/api/v1/courses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/courses/registration-form"))
                .andExpect(status().isUnauthorized());
    }
}

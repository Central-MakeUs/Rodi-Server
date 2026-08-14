package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceListRequest;
import cmc.rodi.domain.place.dto.PlaceSearchRequest;
import cmc.rodi.domain.place.dto.PlaceSuggestion;
import cmc.rodi.domain.place.dto.RelatedSearchRequest;
import cmc.rodi.domain.place.entity.ApprovalStatus;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.exception.CourseErrorCode;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import cmc.rodi.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 승인·삭제 노출 필터(스펙 014) — 미승인·삭제된 코스가 <b>목록·필터 목록·검색·연관검색어·마커 좌표</b> 어디에도 새지 않는지, 상세는 소유자만 열리는지 검증한다.
 *
 * <p>다른 테스트(서울·부산 좌표)와 겹치지 않게 제주 좌표로 격리하고, 이름에 고유 접두사를 둬 키워드 검색이 다른 픽스처를 잡지 않게 한다.
 */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class CourseVisibilityIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);
    private static final String PREFIX = "노출테스트";

    // 제주 인근 뷰포트 + 현위치
    private static final double SW_LAT = 33.20, SW_LNG = 126.30, NE_LAT = 33.60, NE_LNG = 126.80;
    private static final double ME_LAT = 33.40, ME_LNG = 126.50;

    @Autowired PlaceQueryService placeQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired MemberRepository memberRepository;

    private Member owner;
    private Course approved;
    private Course pending;
    private Course rejected;
    private Course deleted;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat)); // x=경도, y=위도
    }

    @BeforeEach
    void seed() {
        owner = memberRepository.save(Member.createBySocial("visibility@kakao.com"));

        approved = save("승인", ApprovalStatus.APPROVED, false);
        pending = save("대기", ApprovalStatus.PENDING, false);
        rejected = save("반려", ApprovalStatus.REJECTED, false);
        deleted = save("삭제", ApprovalStatus.APPROVED, true);

        parkingRepository.save(
                Parking.builder()
                        .name(PREFIX + "-주차장")
                        .address("제주특별자치도 제주시")
                        .location(point(33.41, 126.51))
                        .build());
    }

    private Course save(String suffix, ApprovalStatus status, boolean removed) {
        Course course =
                Course.register(
                        PREFIX + "-" + suffix,
                        "설명",
                        "제주특별자치도 제주시",
                        point(33.41, 126.51),
                        3000,
                        owner);
        course.changeApprovalStatus(status, LocalDateTime.now());
        course.addTag(PracticeType.STRAIGHT);
        if (removed) {
            course.delete(LocalDateTime.now());
        }
        return courseRepository.save(course);
    }

    private List<String> namesOf(CursorPage<PlaceListItem> page) {
        return page.items().stream()
                .map(PlaceListItem::name)
                .filter(n -> n.startsWith(PREFIX))
                .toList();
    }

    /** 필터 정렬 경로(findInViewportFiltered)를 타게 하는 회원. */
    private Long memberWithFilter() {
        owner.updateFilterTags(List.of(PracticeType.STRAIGHT));
        memberRepository.save(owner);
        return owner.getId();
    }

    @Test
    @DisplayName("뷰포트 목록: 승인 코스·주차장만 나오고 대기·반려·삭제는 빠진다")
    void 목록() {
        CursorPage<PlaceListItem> page =
                placeQueryService.getPlaces(
                        new PlaceListRequest(
                                SW_LAT, SW_LNG, NE_LAT, NE_LNG, ME_LAT, ME_LNG, 50, null),
                        null);

        assertThat(namesOf(page))
                .containsExactlyInAnyOrder(PREFIX + "-승인", PREFIX + "-주차장")
                .doesNotContain(PREFIX + "-대기", PREFIX + "-반려", PREFIX + "-삭제");
    }

    @Test
    @DisplayName("필터 정렬 목록에서도 대기·반려·삭제는 빠진다")
    void 필터_목록() {
        CursorPage<PlaceListItem> page =
                placeQueryService.getPlaces(
                        new PlaceListRequest(
                                SW_LAT, SW_LNG, NE_LAT, NE_LNG, ME_LAT, ME_LNG, 50, null),
                        memberWithFilter());

        assertThat(namesOf(page))
                .containsExactlyInAnyOrder(PREFIX + "-승인", PREFIX + "-주차장")
                .doesNotContain(PREFIX + "-대기", PREFIX + "-반려", PREFIX + "-삭제");
    }

    @Test
    @DisplayName("키워드 검색: 승인 코스·주차장만 나오고 totalCount도 그만큼이다")
    void 검색() {
        CursorPage<PlaceListItem> page =
                placeQueryService.searchPlaces(
                        new PlaceSearchRequest(PREFIX, ME_LAT, ME_LNG, 50, null), null);

        assertThat(namesOf(page)).containsExactlyInAnyOrder(PREFIX + "-승인", PREFIX + "-주차장");
        assertThat(page.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("필터 정렬 검색에서도 대기·반려·삭제는 빠진다")
    void 필터_검색() {
        CursorPage<PlaceListItem> page =
                placeQueryService.searchPlaces(
                        new PlaceSearchRequest(PREFIX, ME_LAT, ME_LNG, 50, null),
                        memberWithFilter());

        assertThat(namesOf(page)).containsExactlyInAnyOrder(PREFIX + "-승인", PREFIX + "-주차장");
    }

    @Test
    @DisplayName("연관 검색어: 승인 코스·주차장만 제안되고 totalCount도 그만큼이다")
    void 연관검색어() {
        CursorPage<PlaceSuggestion> places =
                placeQueryService
                        .relatedSearch(new RelatedSearchRequest(PREFIX, 50, null))
                        .places();

        assertThat(places.items().stream().map(PlaceSuggestion::name).toList())
                .containsExactlyInAnyOrder(PREFIX + "-승인", PREFIX + "-주차장");
        assertThat(places.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("마커 좌표: 대기·반려·삭제 코스는 내려가지 않는다")
    void 마커_좌표() {
        List<String> names =
                placeQueryService.getAllCoordinates().stream()
                        .map(c -> c.name())
                        .filter(n -> n != null && n.startsWith(PREFIX))
                        .toList();

        assertThat(names).containsExactlyInAnyOrder(PREFIX + "-승인", PREFIX + "-주차장");
    }

    @Test
    @DisplayName("상세: 미승인 코스는 제3자에게 없는 장소와 같은 404다")
    void 상세_미승인_비소유자() {
        Member other = memberRepository.save(Member.createBySocial("other@kakao.com"));

        assertThatThrownBy(() -> placeQueryService.getPlaceDetail(pending.getId(), other.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
    }

    @Test
    @DisplayName("상세: 미승인·반려 코스도 등록자 본인은 볼 수 있다")
    void 상세_미승인_소유자() {
        assertThat(placeQueryService.getPlaceDetail(pending.getId(), owner.getId()).name())
                .isEqualTo(PREFIX + "-대기");
        assertThat(placeQueryService.getPlaceDetail(rejected.getId(), owner.getId()).name())
                .isEqualTo(PREFIX + "-반려");
    }

    @Test
    @DisplayName("상세: 삭제된 코스는 등록자 본인에게도 '삭제된 코스입니다'")
    void 상세_삭제() {
        assertThatThrownBy(() -> placeQueryService.getPlaceDetail(deleted.getId(), owner.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CourseErrorCode.COURSE_DELETED);
    }

    @Test
    @DisplayName("상세: 승인된 코스는 누구나 볼 수 있다")
    void 상세_승인() {
        Member other = memberRepository.save(Member.createBySocial("other2@kakao.com"));

        assertThat(placeQueryService.getPlaceDetail(approved.getId(), other.getId()).name())
                .isEqualTo(PREFIX + "-승인");
    }
}

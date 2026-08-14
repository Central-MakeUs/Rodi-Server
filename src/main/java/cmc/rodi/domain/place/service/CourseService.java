package cmc.rodi.domain.place.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.dto.CourseRegisterRequest;
import cmc.rodi.domain.place.dto.CourseRegisterResponse;
import cmc.rodi.domain.place.dto.CourseRegistrationFormResponse;
import cmc.rodi.domain.place.dto.CourseRegistrationFormResponse.InputSpec;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 코스 등록·등록 폼(스펙 014). */
@Service
@RequiredArgsConstructor
public class CourseService {

    /** 좌표는 SRID 4326(WGS84). 스레드 안전해 인스턴스를 공유한다. */
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private static final String CAUTION_PLACEHOLDER = "예) 갑자기 나오는 자전거 주의!";
    private static final String DESCRIPTION_PLACEHOLDER = "예) 차선이 넓고, 직선 구간이 길어요.";

    private final CourseRepository courseRepository;
    private final MemberRepository memberRepository;

    /**
     * 코스 등록. 승인 대기 상태로 저장되며, 관리자가 승인하기 전까지 전체 목록·검색에 나오지 않는다.
     *
     * <p>대표 좌표(`place.location`)와 코스명은 <b>출발지에서 가져온다</b> — 코스 목록·지도가 시작점을 기준으로 그려지고, 등록 화면에 코스명
     * 입력란이 없기 때문이다.
     */
    @Transactional
    public CourseRegisterResponse register(CourseRegisterRequest request, Long memberId) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        CourseRegisterRequest.WaypointRequest start = request.start();
        Course course =
                Course.register(
                        request.resolveName(),
                        request.description(),
                        request.address(),
                        point(start.lat(), start.lng()),
                        request.distanceMeters(),
                        member);

        request.practiceTypes().forEach(course::addTag);
        if (request.caution() != null) {
            course.addCaution(request.caution());
        }
        short sequence = 0;
        for (CourseRegisterRequest.WaypointRequest waypoint : request.waypoints()) {
            course.addWaypoint(
                    waypoint.type(),
                    sequence++,
                    point(waypoint.lat(), waypoint.lng()),
                    waypoint.name());
        }

        return CourseRegisterResponse.from(courseRepository.save(course));
    }

    /** 등록 폼. 카테고리 트리·입력 제약을 서버가 정의해 내려준다(앱 배포 없이 문구 변경). */
    public CourseRegistrationFormResponse getRegistrationForm() {
        return CourseRegistrationFormResponse.of(
                CourseRegisterRequest.MAX_VIA,
                new InputSpec(
                        false, null, CourseRegisterRequest.CAUTION_MAX_LENGTH, CAUTION_PLACEHOLDER),
                new InputSpec(
                        true,
                        CourseRegisterRequest.DESCRIPTION_MIN_LENGTH,
                        CourseRegisterRequest.DESCRIPTION_MAX_LENGTH,
                        DESCRIPTION_PLACEHOLDER));
    }

    /** PostGIS는 x=경도, y=위도. */
    private static Point point(double lat, double lng) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat));
    }
}

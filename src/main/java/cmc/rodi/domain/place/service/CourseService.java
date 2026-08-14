package cmc.rodi.domain.place.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.dto.CourseApprovalRequest;
import cmc.rodi.domain.place.dto.CourseApprovalResponse;
import cmc.rodi.domain.place.dto.CourseRegisterRequest;
import cmc.rodi.domain.place.dto.CourseRegisterResponse;
import cmc.rodi.domain.place.dto.CourseRegistrationFormResponse;
import cmc.rodi.domain.place.dto.CourseRegistrationFormResponse.InputSpec;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.exception.CourseErrorCode;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.time.LocalDateTime;
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
     * <p>대표 좌표(`place.location`)는 출발지에서 가져온다. 코스명은 요청 {@code name}이 있으면 그 값을 쓰고, 없으면 출발지 지점명을 쓴다.
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

    /**
     * 내 코스 삭제(스펙 015). <b>soft delete</b> — 행을 지우지 않고 {@code deletedAt}만 찍는다.
     *
     * <p>물리 삭제하면 다른 사용자의 북마크·후기·연습기록까지 조용히 사라져, 담아둔 사람은 항목이 없어진 이유를 알 수 없다. 표시만 남겨두면 목록에서 {@code
     * isDeleted}로 구분하고 상세에서 "삭제된 코스입니다"를 띄울 수 있다.
     *
     * <p>승인 상태와 무관하게 지울 수 있고, 이미 삭제된 기존 코스는 멱등하게 200이다. 없는 id는 404다.
     */
    @Transactional
    public void delete(Long courseId, Long memberId) {
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND);
        }
        if (!course.isOwnedBy(memberId)) {
            throw new BusinessException(CourseErrorCode.NOT_COURSE_OWNER);
        }
        course.delete(LocalDateTime.now()); // 이미 삭제됐으면 최초 시각 유지
    }

    /**
     * 관리자용 승인 상태 변경(스펙 016).
     *
     * <p><b>임시 정책</b>: 계정 권한 작업 전이라 호출자가 로그인했는지만 컨트롤러에서 보장하고, 여기서는 관리자 권한을 검사하지 않는다. 권한 체계가 생기면 이
     * 메서드 진입 전에 관리자 검사를 추가한다.
     */
    @Transactional
    public CourseApprovalResponse changeApprovalStatus(
            Long courseId, CourseApprovalRequest request) {
        Course course =
                courseRepository
                        .findById(courseId)
                        .orElseThrow(() -> new BusinessException(CourseErrorCode.COURSE_NOT_FOUND));
        if (course.isDeleted()) {
            throw new BusinessException(CourseErrorCode.COURSE_DELETED);
        }
        course.changeApprovalStatus(request.approvalStatus(), LocalDateTime.now());
        return CourseApprovalResponse.from(course);
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

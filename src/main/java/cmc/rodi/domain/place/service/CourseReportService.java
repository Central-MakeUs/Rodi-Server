package cmc.rodi.domain.place.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.dto.CourseReportRequest;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.CourseIssue;
import cmc.rodi.domain.place.entity.CourseReport;
import cmc.rodi.domain.place.exception.CourseErrorCode;
import cmc.rodi.domain.place.repository.CourseReportRepository;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.global.common.form.FormResponse;
import cmc.rodi.global.common.form.FormType;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 코스 신고(스펙 025). 접수·보관만 하고 코스 노출은 바꾸지 않는다. */
@Service
@RequiredArgsConstructor
public class CourseReportService {

    private final CourseReportRepository courseReportRepository;
    private final CourseRepository courseRepository;
    private final MemberRepository memberRepository;

    /** 신고 폼. 후기 작성 폼의 문제 항목 문항과 같은 정의다. 저장이 없어 트랜잭션도 열지 않는다. */
    public FormResponse getReportForm() {
        return new FormResponse(
                CourseIssue.QUESTION_CODE,
                FormType.MULTI_SELECT,
                CourseIssue.QUESTION_TITLE,
                null, // 화면에 부연 설명 없음
                true,
                CourseIssue.formOptions());
    }

    /**
     * 코스 직접 신고(멱등). 본인이 등록한 코스는 신고할 수 없고, 재신고는 첫 신고를 유지한다.
     *
     * <p>상세를 볼 수 있는 코스만 받는다 — 미승인 코스는 등록자 외에는 없는 코스와 같은 404다(존재를 숨기는 상세 조회와 같은 판단).
     */
    @Transactional
    public void report(Long courseId, Long memberId, CourseReportRequest request) {
        // 신고자 행을 잠가 [중복 확인 → 저장]을 같은 회원의 동시 요청끼리 직렬화한다.
        // 잠금이 없으면 둘 다 "아직 없음"으로 보고 저장하다 unique에 걸려 500이 난다.
        Member reporter =
                memberRepository
                        .findByIdForUpdate(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        Course course = findReportableCourse(courseId, memberId);
        if (course.isOwnedBy(memberId)) {
            throw new BusinessException(CourseErrorCode.SELF_REPORT_NOT_ALLOWED);
        }
        if (courseReportRepository.existsByCourseIdAndReporterIdAndReviewIdIsNull(
                courseId, memberId)) {
            return;
        }
        courseReportRepository.save(
                CourseReport.direct(course, reporter, request.courseIssues(), request.detail()));
    }

    /** 주차장 id는 코스로 조회되지 않아 없는 코스와 같은 404가 된다. */
    private Course findReportableCourse(Long courseId, Long memberId) {
        Course course =
                courseRepository
                        .findById(courseId)
                        .orElseThrow(() -> new BusinessException(CourseErrorCode.COURSE_NOT_FOUND));
        if (course.isDeleted()) {
            throw new BusinessException(CourseErrorCode.COURSE_DELETED);
        }
        if (!course.isApproved() && !course.isOwnedBy(memberId)) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND);
        }
        return course;
    }
}

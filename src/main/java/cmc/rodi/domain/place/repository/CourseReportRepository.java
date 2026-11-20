package cmc.rodi.domain.place.repository;

import cmc.rodi.domain.place.entity.CourseReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseReportRepository extends JpaRepository<CourseReport, Long> {

    /** 이 회원이 이 코스를 이미 직접 신고했는지(후기에서 온 신고는 세지 않는다). */
    boolean existsByCourseIdAndReporterIdAndReviewIdIsNull(Long courseId, Long reporterId);

    /** '별로에요' 후기에 연결된 신고. 후기당 최대 1건이다. */
    Optional<CourseReport> findByReviewId(Long reviewId);
}

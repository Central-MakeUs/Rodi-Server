-- 후기 '별로에요' 코스 문제 항목(스펙 024, 이슈 #85).
-- 별로에요 후기는 난이도·혼잡도 대신 코스 문제 항목을 받으므로 두 컬럼의 NOT NULL을 푼다.
-- 추천 후기는 여전히 둘 다 있어야 한다 — CHECK로 DB에서도 강제한다.
-- 기존 ck_review_difficulty·ck_review_congestion(V17)은 NULL을 통과시키므로 그대로 둔다.
-- 기존 비추천 후기는 난이도·혼잡도 값이 있어 "비추천이면 NULL" 방향 제약은 걸지 않는다.
ALTER TABLE review
    ALTER COLUMN difficulty DROP NOT NULL,
    ALTER COLUMN congestion DROP NOT NULL,
    -- 기타 직접 입력. 길이는 grapheme 기준(스펙 018)이라 컬럼 폭이 아니라 애플리케이션이 강제한다.
    ADD COLUMN course_issue_detail TEXT,
    ADD CONSTRAINT ck_review_recommended_ratings
        CHECK (NOT is_recommended OR (difficulty IS NOT NULL AND congestion IS NOT NULL));

-- 코스 문제 항목(복수 선택). 후기와 생명주기를 같이하는 값 묶음이라 후기 삭제 시 함께 지운다.
-- 값 목록은 Java enum(CourseIssue)과 정확히 일치해야 한다.
CREATE TABLE review_course_issue (
    review_id BIGINT      NOT NULL REFERENCES review (id) ON DELETE CASCADE,
    issue     VARCHAR(30) NOT NULL,
    PRIMARY KEY (review_id, issue),
    CONSTRAINT ck_review_course_issue_issue
        CHECK (issue IN ('VEHICLE_RESTRICTED', 'ROAD_INFO_MISMATCH', 'CONSTRUCTION',
                         'NOT_FOR_PRACTICE', 'OTHER'))
);

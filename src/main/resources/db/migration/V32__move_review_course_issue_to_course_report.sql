-- '별로에요' 후기의 코스 문제 항목을 후기에서 코스 신고로 옮긴다(스펙 025, 이슈 #87).
-- 문제 항목은 이제 course_report(review_id 연결)에만 저장하고, 후기 쪽 테이블·컬럼은 지운다.
-- V30은 develop(pi2)에만 배포돼 운영 DB에는 옮길 행이 없다.

-- 1) 문제 항목이 있는 후기마다 신고 1건. 신고자는 후기 작성자, 시각은 후기 시각을 그대로 쓴다.
--    코스가 아닌 장소(주차장) 후기의 문제 항목은 신고로 옮길 수 없어 버린다(개발 DB 테스트 데이터만 해당).
INSERT INTO course_report (course_id, reporter_id, review_id, detail, created_at, updated_at)
SELECT r.place_id,
       r.member_id,
       r.id,
       -- 기타가 없으면 직접 입력은 의미가 없다(CourseReport 규칙과 맞춘다)
       CASE
           WHEN EXISTS (SELECT 1
                        FROM review_course_issue o
                        WHERE o.review_id = r.id AND o.issue = 'OTHER')
               THEN NULLIF(btrim(r.course_issue_detail), '')
       END,
       r.created_at,
       r.updated_at
FROM review r
JOIN course c ON c.place_id = r.place_id
WHERE EXISTS (SELECT 1 FROM review_course_issue i WHERE i.review_id = r.id);

-- 2) 문제 항목 행
INSERT INTO course_report_issue (course_report_id, issue)
SELECT cr.id, i.issue
FROM review_course_issue i
JOIN course_report cr ON cr.review_id = i.review_id;

-- 3) 후기 쪽 저장소 삭제. 난이도·혼잡도 nullable과 ck_review_recommended_ratings(V30)는 별로에요 후기에 계속 필요해 둔다.
DROP TABLE review_course_issue;

ALTER TABLE review
    DROP COLUMN course_issue_detail;

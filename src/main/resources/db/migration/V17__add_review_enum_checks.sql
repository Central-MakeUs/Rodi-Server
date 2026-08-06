-- 후기·신고의 enum 저장값을 DB에서도 강제한다(애플리케이션 검증의 최종 방어선).
-- V13·V14는 이미 적용된 마이그레이션이라 수정하지 않고 제약만 새로 얹는다.
-- 값 목록은 Java enum(Difficulty·Congestion·PracticeMethod·Level·ReportReason)과 정확히 일치해야 한다.
ALTER TABLE review
    ADD CONSTRAINT ck_review_difficulty
        CHECK (difficulty IN ('VERY_EASY', 'EASY', 'NORMAL', 'HARD', 'VERY_HARD')),
    ADD CONSTRAINT ck_review_congestion
        CHECK (congestion IN ('QUIET', 'NORMAL', 'CROWDED')),
    ADD CONSTRAINT ck_review_practice_method
        CHECK (practice_method IN ('SOLO', 'ACCOMPANIED')),
    ADD CONSTRAINT ck_review_member_level
        CHECK (member_level IN ('SEED', 'ROOKIE', 'OWNER', 'EXPLORER', 'NAVIGATOR'));

ALTER TABLE review_report
    ADD CONSTRAINT ck_review_report_reason
        CHECK (reason IN ('SPAM', 'ABUSE', 'IRRELEVANT', 'FALSE_INFO', 'OTHER'));

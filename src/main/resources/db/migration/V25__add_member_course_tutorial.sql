-- 코스 등록 튜토리얼 완료 여부(스펙 017).
-- boolean 대신 완료 시각을 남긴다. NULL이면 아직 보지 않은 상태다.
ALTER TABLE member
    ADD COLUMN course_tutorial_completed_at TIMESTAMP;

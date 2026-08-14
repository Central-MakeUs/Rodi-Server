-- 사용자 코스 등록(스펙 014). 코스에 승인 상태·등록자·삭제 시각을 둔다.
-- 승인 상태를 별도 접수 테이블이 아니라 course에 두는 이유: 승인 후에도 place.id가 그대로라
-- 북마크·후기·연습기록이 이어지고, place 기준으로 짜인 목록·검색 쿼리를 재구성하지 않아도 된다.

ALTER TABLE course ADD COLUMN approval_status VARCHAR(20);

-- 기존 시딩분은 전부 노출 중이므로 승인 상태로 채운다.
UPDATE course SET approval_status = 'APPROVED';

ALTER TABLE course ALTER COLUMN approval_status SET NOT NULL;

-- DEFAULT는 두지 않는다 — 애플리케이션이 상태를 항상 명시하게 해서 "기본값이 승인"이 되는 사고를 막는다.
ALTER TABLE course ADD CONSTRAINT ck_course_approval_status
    CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'));

-- 등록자. NULL = 운영자 시딩분(사용자가 삭제 불가).
-- ON DELETE SET NULL: 탈퇴는 soft delete라 보통 발생하지 않지만, 즉시 탈퇴 API로 물리 삭제되더라도
-- 다른 사용자가 쓰고 있는 코스가 사라지면 안 된다.
ALTER TABLE course ADD COLUMN created_by_member_id BIGINT
    REFERENCES member (id) ON DELETE SET NULL;

-- 마지막 승인 시각(APPROVED로 바뀔 때 갱신, 다른 상태로 바뀌어도 지우지 않는다).
ALTER TABLE course ADD COLUMN approved_at TIMESTAMP;

-- 삭제 시각(soft delete). 행을 지우지 않으므로 남의 북마크·후기·연습기록이 함께 사라지지 않는다.
ALTER TABLE course ADD COLUMN deleted_at TIMESTAMP;

-- 내 코스 목록(등록자별 최신순) 조회용.
CREATE INDEX idx_course_created_by ON course (created_by_member_id, place_id DESC);

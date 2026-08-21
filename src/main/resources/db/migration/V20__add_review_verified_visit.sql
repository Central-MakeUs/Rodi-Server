-- 후기 방문 인증 배지: 작성 시점에 그 회원·장소의 GPS 방문 인증 이력을 스냅샷으로 남긴다.
-- 스냅샷이라 이후 연습 항목을 지우거나 재등록해도 이미 쓴 후기의 값은 바뀌지 않는다.
ALTER TABLE review
    ADD COLUMN is_verified_visit boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN review.is_verified_visit IS '작성 시점에 GPS 방문 인증 이력이 있었는지(스냅샷)';

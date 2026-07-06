-- 단계적 탈퇴: 유예기간(3일) 경과 후 개인정보를 익명화한 시각.
-- deleted_at(탈퇴 요청 시각)과 함께 상태(PENDING/LOCKED/ANONYMIZED)를 파생한다.
ALTER TABLE member ADD COLUMN anonymized_at TIMESTAMP;

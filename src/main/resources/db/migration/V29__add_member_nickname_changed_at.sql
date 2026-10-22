-- 닉네임 변경 30일 제한(스펙 020, 이슈 #80).
-- 사용자가 마지막으로 닉네임을 직접 바꾼 시각. 가입 때 자동 부여는 기록하지 않으므로,
-- 기존 회원은 모두 NULL = 바로 한 번 바꿀 수 있다(첫 변경은 제한하지 않는다).
ALTER TABLE member ADD COLUMN nickname_changed_at TIMESTAMP;

-- 후기 내용 상한을 화면 확정값(150자)에 맞춘다. 초안의 1000자는 임시값이었다.
-- V13은 이미 적용된 마이그레이션이라 수정하지 않고 ALTER로 줄인다(체크섬 보존).
-- 아직 150자를 넘는 후기가 없으므로 잘림 없이 변환된다.
ALTER TABLE review ALTER COLUMN content TYPE VARCHAR(150);

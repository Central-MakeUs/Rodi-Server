-- 방문 인증(스펙 011): 앱이 GPS로 측정한 "인정 주행거리"(코스 경로선 150m 이내 이동거리)를 받아
-- 서버가 필요 거리(min(코스거리 × 40%, 5km))와 비교해 인증 여부를 판정한다.
-- V18은 이미 적용됐을 수 있어 수정하지 않고 컬럼만 얹는다.
ALTER TABLE member_practice
    ADD COLUMN certified_distance_meters BIGINT  NOT NULL DEFAULT 0,   -- 이 항목에서 누적된 인정 주행거리
    ADD COLUMN verified                  BOOLEAN NOT NULL DEFAULT FALSE; -- 한 번이라도 인증되면 true(내려가지 않음)

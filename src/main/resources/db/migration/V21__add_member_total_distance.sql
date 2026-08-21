-- 레벨 진행도(스펙 012): 레벨을 누적 주행거리로 올린다. 거리는 차감되지 않으므로 단조 증가한다.
ALTER TABLE member
    ADD COLUMN total_distance_meters BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN member.total_distance_meters IS '누적 인정 주행거리(m). 레벨 승급 기준이며 차감되지 않는다';

-- 기존 회원 백필: 이미 배정된 레벨의 최소 기준값으로 맞춘다.
-- 이렇게 하지 않으면 Owner였던 회원이 0km에서 시작해 게이지가 Seed로 보인다(레벨 자체는 내리지 않으므로 표시만 어긋난다).
UPDATE member
SET total_distance_meters = CASE level
                                WHEN 'ROOKIE' THEN 50000
                                WHEN 'OWNER' THEN 150000
                                WHEN 'EXPLORER' THEN 300000
                                WHEN 'NAVIGATOR' THEN 600000
                                ELSE 0 -- SEED·온보딩 미완료(NULL)
    END
WHERE total_distance_meters = 0;

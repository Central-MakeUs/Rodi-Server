-- 기획 개편(#55)으로 Q1(driving_period) 구간 재정의 → 저장된 레거시 값을 새 enum 값으로 backfill.
-- 구간이 1:1 대응하지 않아 근사 매핑한다:
--  - MONTHS_1_3(1~3개월)  → MONTHS_1_2(1~2개월)   : 하한 기준 근사
--  - MONTHS_3_6(3~6개월)  → MONTHS_3_5(3~5개월)
--  - MONTHS_6_12(6~12개월) → MONTHS_6_11(6~11개월)
--  - YEARS_2_10(2~10년)   → YEARS_3_9(3~9년)      : Navigator 강제 배정 의미 유지
-- (UNDER_1_MONTH · YEARS_1_2 · OVER_10_YEARS는 그대로. 레벨은 member.level에 이미 저장되어 영향 없음)
UPDATE member_onboarding
SET driving_period = CASE driving_period
    WHEN 'MONTHS_1_3'  THEN 'MONTHS_1_2'
    WHEN 'MONTHS_3_6'  THEN 'MONTHS_3_5'
    WHEN 'MONTHS_6_12' THEN 'MONTHS_6_11'
    WHEN 'YEARS_2_10'  THEN 'YEARS_3_9'
    END
WHERE driving_period IN ('MONTHS_1_3', 'MONTHS_3_6', 'MONTHS_6_12', 'YEARS_2_10');

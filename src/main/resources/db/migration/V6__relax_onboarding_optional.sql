-- 온보딩 문항 재정의(#41 후속): Q1(driving_period)·레벨만 필수, 나머지는 선택.
--  - Q1이 2~10년/10년 이상이면 클라이언트가 후속 질문을 건너뛰고 Navigator로 배정 → Q2~Q4 값이 없음
--  - Q4-1(운전범위)·Q4-2(주차)는 Q3에서 '혼자 연습'을 골랐을 때만 입력 → 없을 수 있음
-- V5에서 NOT NULL로 만든 컬럼을 선택 허용으로 완화한다(driving_period·onboarded_at은 유지).
ALTER TABLE member_onboarding
    ALTER COLUMN recent_frequency   DROP NOT NULL,
    ALTER COLUMN road_experiences   DROP NOT NULL,
    ALTER COLUMN solo_driving_range DROP NOT NULL,
    ALTER COLUMN solo_parking_level DROP NOT NULL;

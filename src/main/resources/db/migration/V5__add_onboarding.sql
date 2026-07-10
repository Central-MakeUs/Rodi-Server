-- 온보딩(#41): 회원 레벨·운전목표는 노출·활용되므로 member에 두고,
-- 나머지 온보딩 원자료는 저장 목적의 member_onboarding(1:1)로 분리한다.
-- 복수/순위 응답(도로주행 경험·선호 연습유형)은 별도 테이블 없이 jsonb 배열로 한 행에 저장(순서 보존).
-- 레벨은 클라이언트가 점수→레벨 변환해 전송하고 서버는 저장만 한다(점수 미저장).

-- member: 노출·활용되는 값만 추가
ALTER TABLE member
    ADD COLUMN level        VARCHAR(20),   -- 배정 레벨(SEED~NAVIGATOR). 온보딩 전 NULL
    ADD COLUMN driving_goal VARCHAR(30);   -- 운전 목표(마이페이지 노출). 선택

-- 온보딩 원자료 (member와 1:1, member_id 공유 PK)
CREATE TABLE member_onboarding (
    member_id          BIGINT      PRIMARY KEY,
    driving_period     VARCHAR(20) NOT NULL,               -- Q1 실제 운전 기간(단일)
    recent_frequency   VARCHAR(20) NOT NULL,               -- Q2 최근 운전 빈도(단일)
    solo_driving_range VARCHAR(20) NOT NULL,               -- Q4 혼자 운전 범위(단일)
    solo_parking_level VARCHAR(20) NOT NULL,               -- Q5 혼자 주차 수준(단일)
    road_experiences   JSONB       NOT NULL,               -- Q3 도로주행 경험(복수) 예: ["SOLO"]
    practice_types     JSONB       NOT NULL DEFAULT '[]',  -- 선호 연습유형(순서=우선순위, 최대 3). 선택 → 기본 []
    car_type           VARCHAR(20),                        -- 차종(단일). 선택
    onboarded_at       TIMESTAMP   NOT NULL,               -- 온보딩 완료 시각(행 존재=완료 → 재제출 거부)
    created_at         TIMESTAMP   NOT NULL,
    updated_at         TIMESTAMP   NOT NULL,
    CONSTRAINT fk_member_onboarding_member FOREIGN KEY (member_id) REFERENCES member (id)
);

-- 방문 인증을 레벨별로 판정한다(스펙 013). 인증된 후기 배지가 "한 번이라도 인증했는가"였던 탓에,
-- Seed 때 받은 인증으로 Rookie가 된 뒤 쓴 후기까지 배지가 붙었다.
-- 어느 레벨에서 인증했는지를 남겨 "작성 당시 레벨에서 인증했는가"로 판정한다.
ALTER TABLE member_practice
    ADD COLUMN verified_level VARCHAR(20);   -- 마지막으로 인증에 성공했을 때의 회원 레벨(NULL=인증 이력 없음)

-- 기존 인증자가 마이그레이션 직후 배지를 잃지 않도록 회원의 현재 레벨로 채운다.
UPDATE member_practice mp
   SET verified_level = m.level
  FROM member m
 WHERE m.id = mp.member_id
   AND mp.verified
   AND m.level IS NOT NULL;

-- verified_level IS NOT NULL이 곧 "한 번이라도 인증됨"이라 boolean은 같은 사실을 나눠 갖는다.
-- 둘을 함께 두면 verified=true / verified_level=NULL 같은 모순 상태가 생길 수 있다.
ALTER TABLE member_practice
    DROP COLUMN verified;

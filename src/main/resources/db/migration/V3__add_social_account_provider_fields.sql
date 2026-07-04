-- 소셜 계정에 공급자 부가정보 추가
--  provider_refresh_token     : 애플 refresh token(탈퇴 시 revoke용). 애플만 채움
--  provider_nickname          : 공급자 프로필 닉네임(카카오톡 닉네임 등). 서비스 닉네임(member.nickname)과 분리
--  provider_profile_image_url : 공급자 프로필 이미지 URL
ALTER TABLE social_account
    ADD COLUMN provider_refresh_token     VARCHAR(512),
    ADD COLUMN provider_nickname          VARCHAR(50),
    ADD COLUMN provider_profile_image_url VARCHAR(512);

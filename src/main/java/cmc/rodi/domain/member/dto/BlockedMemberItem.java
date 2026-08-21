package cmc.rodi.domain.member.dto;

import cmc.rodi.domain.member.entity.MemberBlock;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 내가 차단한 회원 한 명. 해제 버튼만 있는 화면이라 프로필·레벨은 싣지 않는다. */
public record BlockedMemberItem(
        @Schema(description = "차단한 회원 id(해제 요청 경로에 그대로 쓴다)") Long memberId,
        @Schema(description = "닉네임(탈퇴·익명화 시 null)") String nickname,
        @Schema(description = "차단한 시각") LocalDateTime blockedAt) {

    public static BlockedMemberItem from(MemberBlock block) {
        return new BlockedMemberItem(
                block.getBlocked().getId(), block.getBlocked().getNickname(), block.getCreatedAt());
    }
}

package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.service.MemberHardDeleteService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 즉시 탈퇴(내부 테스트용). {@link MemberController}와 나눠 두는 이유는 <b>프로파일 조건이 빈 단위</b>라서다 — 메서드 하나만 운영에서 빼는 방법이
 * 없어 컨트롤러를 따로 둔다. 덕분에 나중에 통째로 지우기도 쉽다.
 */
@Tag(name = "Member (내부용)", description = "테스트 편의를 위한 비운영 API")
@RestController
@RequestMapping("/api/v1/members")
@Profile("!prod")
@RequiredArgsConstructor
public class MemberHardDeleteController {

    private final MemberHardDeleteService memberHardDeleteService;

    @Operation(
            summary = "즉시 탈퇴 (내부 테스트용, 운영에는 없음)",
            description =
                    "유예기간·익명화를 건너뛰고 회원을 물리 삭제한다. 같은 소셜 계정으로 곧바로 재가입할 수 있어"
                            + " 가입·온보딩을 반복해서 시험할 수 있다. 되돌릴 수 없다."
                            + " 토큰의 본인 계정만 지운다(회원 id를 받지 않는다)."
                            + " 운영 프로파일에서는 이 엔드포인트가 아예 등록되지 않는다. JWT 필요.")
    @DeleteMapping("/me/hard")
    public ApiResponse<Void> hardDelete(@CurrentMember Long memberId) {
        memberHardDeleteService.hardDelete(memberId);
        return ApiResponse.success(null);
    }
}

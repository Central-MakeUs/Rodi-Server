package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.service.MemberHardDeleteService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 즉시 탈퇴(내부 테스트용). {@link MemberController}와 나눠 둬, 필요 없어지면 이 파일만 지우면 되게 한다.
 *
 * <p>배포 환경이 하나뿐이라 프로파일·설정으로 가려도 정작 앱이 붙는 서버에서 쓸 수 없어, 게이트 없이 배포한다. 앱은 debug 빌드에만 이 API를 연결한다. <b>다만
 * 노출은 막히지 않는다</b> — 화면에 버튼이 없을 뿐, 토큰이 있으면 누구나 호출할 수 있다. 본인 계정만 지워지는 것이 유일한 제약이다.
 */
@Tag(name = "Member (내부용)", description = "테스트 편의용 API — 앱 debug 빌드에서만 연결한다")
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberHardDeleteController {

    private final MemberHardDeleteService memberHardDeleteService;

    @Operation(
            summary = "즉시 탈퇴 (내부 테스트용)",
            description =
                    "유예기간·익명화를 건너뛰고 회원을 물리 삭제한다. 같은 소셜 계정으로 곧바로 재가입할 수 있어"
                            + " 가입·온보딩을 반복해서 시험할 수 있다. 되돌릴 수 없다."
                            + " 토큰의 본인 계정만 지운다(회원 id를 받지 않는다)."
                            + " 앱은 debug 빌드에서만 연결한다. JWT 필요.")
    @DeleteMapping("/me/hard")
    public ApiResponse<Void> hardDelete(
            // 토큰에서 꺼내는 값이라 요청 파라미터가 아니다. 감추지 않으면 Swagger가 쿼리 파라미터로 그린다
            @Parameter(hidden = true) @CurrentMember Long memberId) {
        memberHardDeleteService.hardDelete(memberId);
        return ApiResponse.success(null);
    }
}

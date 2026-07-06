package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.service.MemberWithdrawalService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 API. 문서 스펙은 {@link MemberControllerDocs}. */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberControllerDocs {

    private final MemberWithdrawalService memberWithdrawalService;

    @Override
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@CurrentMember Long memberId) {
        memberWithdrawalService.withdraw(memberId);
        return ApiResponse.success(null);
    }
}

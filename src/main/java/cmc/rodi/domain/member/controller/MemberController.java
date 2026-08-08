package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.dto.BlockedMemberItem;
import cmc.rodi.domain.member.dto.FilterTagsRequest;
import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.dto.MyPageResponse;
import cmc.rodi.domain.member.dto.OnboardingRequest;
import cmc.rodi.domain.member.service.MemberBlockService;
import cmc.rodi.domain.member.service.MemberFilterService;
import cmc.rodi.domain.member.service.MemberProfileService;
import cmc.rodi.domain.member.service.MemberWithdrawalService;
import cmc.rodi.domain.member.service.OnboardingService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.common.response.ApiResponse;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 회원 API. 문서 스펙은 {@link MemberControllerDocs}. */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberControllerDocs {

    private static final int MAX_SIZE = 100;

    private final MemberWithdrawalService memberWithdrawalService;
    private final OnboardingService onboardingService;
    private final MemberProfileService memberProfileService;
    private final MemberFilterService memberFilterService;
    private final MemberBlockService memberBlockService;

    @Override
    @GetMapping("/me")
    public ApiResponse<MyPageResponse> getMyPage(@CurrentMember Long memberId) {
        return ApiResponse.success(memberProfileService.getMyPage(memberId));
    }

    @Override
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@CurrentMember Long memberId) {
        memberWithdrawalService.withdraw(memberId);
        return ApiResponse.success(null);
    }

    @Override
    @PatchMapping("/me")
    public ApiResponse<Void> updateMe(
            @CurrentMember Long memberId, @Valid @RequestBody MemberUpdateRequest request) {
        memberProfileService.update(memberId, request);
        return ApiResponse.success(null);
    }

    @Override
    @PostMapping("/me/onboarding")
    public ApiResponse<Void> submitOnboarding(
            @CurrentMember Long memberId, @Valid @RequestBody OnboardingRequest request) {
        onboardingService.submit(memberId, request);
        return ApiResponse.success(null);
    }

    @Override
    @PutMapping("/me/filter-tags")
    public ApiResponse<Void> updateFilterTags(
            @CurrentMember Long memberId, @Valid @RequestBody FilterTagsRequest request) {
        memberFilterService.updateFilterTags(memberId, request.filterTags());
        return ApiResponse.success(null);
    }

    @Override
    @PostMapping("/{memberId}/block")
    public ApiResponse<Void> block(
            @PathVariable Long memberId, @CurrentMember Long currentMemberId) {
        memberBlockService.block(currentMemberId, memberId);
        return ApiResponse.success(null);
    }

    @Override
    @GetMapping("/me/blocks")
    public ApiResponse<CursorPage<BlockedMemberItem>> getMyBlocks(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @CurrentMember Long memberId) {
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return ApiResponse.success(memberBlockService.getMyBlocks(memberId, size, cursor));
    }

    @Override
    @DeleteMapping("/{memberId}/block")
    public ApiResponse<Void> unblock(
            @PathVariable Long memberId, @CurrentMember Long currentMemberId) {
        memberBlockService.unblock(currentMemberId, memberId);
        return ApiResponse.success(null);
    }
}

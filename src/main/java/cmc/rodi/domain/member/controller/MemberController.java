package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.dto.BlockedMemberItem;
import cmc.rodi.domain.member.dto.CourseTutorialCompletionResponse;
import cmc.rodi.domain.member.dto.FilterTagsRequest;
import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.dto.MemberUpdateResponse;
import cmc.rodi.domain.member.dto.MyPageResponse;
import cmc.rodi.domain.member.dto.NicknameAvailabilityResponse;
import cmc.rodi.domain.member.dto.OnboardingFormResponse;
import cmc.rodi.domain.member.dto.OnboardingRequest;
import cmc.rodi.domain.member.dto.SortTagsRequest;
import cmc.rodi.domain.member.service.MemberBlockService;
import cmc.rodi.domain.member.service.MemberCourseTutorialService;
import cmc.rodi.domain.member.service.MemberProfileService;
import cmc.rodi.domain.member.service.MemberSortService;
import cmc.rodi.domain.member.service.MemberWithdrawalService;
import cmc.rodi.domain.member.service.NicknameService;
import cmc.rodi.domain.member.service.OnboardingService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.common.response.ApiResponse;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MemberController.class);
    private static final int MAX_SIZE = 100;

    private final MemberWithdrawalService memberWithdrawalService;
    private final OnboardingService onboardingService;
    private final MemberProfileService memberProfileService;
    private final MemberSortService memberSortService;
    private final MemberBlockService memberBlockService;
    private final MemberCourseTutorialService memberCourseTutorialService;
    private final NicknameService nicknameService;

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
    public ApiResponse<MemberUpdateResponse> updateMe(
            @CurrentMember Long memberId, @Valid @RequestBody MemberUpdateRequest request) {
        return ApiResponse.success(memberProfileService.update(memberId, request));
    }

    @Override
    @GetMapping("/me/onboarding-form")
    public ApiResponse<OnboardingFormResponse> getOnboardingForm() {
        return ApiResponse.success(onboardingService.getForm());
    }

    @Override
    @GetMapping("/nickname/availability")
    public ApiResponse<NicknameAvailabilityResponse> checkNicknameAvailability(
            @CurrentMember Long memberId, @RequestParam String nickname) {
        return ApiResponse.success(nicknameService.checkAvailability(memberId, nickname));
    }

    @Override
    @PostMapping("/me/onboarding")
    public ApiResponse<Void> submitOnboarding(
            @CurrentMember Long memberId, @Valid @RequestBody OnboardingRequest request) {
        onboardingService.submit(memberId, request);
        return ApiResponse.success(null);
    }

    @Override
    @PutMapping("/me/sort-tags")
    public ApiResponse<Void> updateSortTags(
            @CurrentMember Long memberId, @Valid @RequestBody SortTagsRequest request) {
        memberSortService.updateSortTags(memberId, request.sortTags());
        return ApiResponse.success(null);
    }

    /** 옛 경로 별칭(스펙 019). 앱이 sort-tags로 바뀌었는지 확인하려고 호출마다 로그를 남긴다. 앱 전환 후 제거한다. */
    @Override
    @PutMapping("/me/filter-tags")
    public ApiResponse<Void> updateFilterTags(
            @CurrentMember Long memberId, @Valid @RequestBody FilterTagsRequest request) {
        log.info("deprecated PUT /members/me/filter-tags 호출 memberId={}", memberId);
        memberSortService.updateSortTags(memberId, request.filterTags());
        return ApiResponse.success(null);
    }

    @Override
    @PatchMapping("/me/course-tutorial")
    public ApiResponse<CourseTutorialCompletionResponse> completeCourseTutorial(
            @CurrentMember Long memberId) {
        return ApiResponse.success(memberCourseTutorialService.complete(memberId));
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

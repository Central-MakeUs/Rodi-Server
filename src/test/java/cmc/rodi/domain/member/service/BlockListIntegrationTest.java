package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.dto.BlockedMemberItem;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 차단 목록 조회 — 차단한 시각 최신순, 커서 2페이지, 남의 차단 제외, 해제 시 목록에서 사라짐. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class BlockListIntegrationTest {

    @Autowired MemberBlockService memberBlockService;
    @Autowired MemberRepository memberRepository;

    private Member seedMember(String email) {
        return memberRepository.save(Member.createBySocial(email));
    }

    @Test
    @DisplayName("차단한 시각 최신순으로 나오고 커서로 이어지며, 남이 한 차단은 섞이지 않는다")
    void 목록_조회() {
        Member me = seedMember("blocker@kakao.com");
        Member stranger = seedMember("stranger@kakao.com");
        Member first = seedMember("first@kakao.com");
        Member second = seedMember("second@kakao.com");
        Member third = seedMember("third@kakao.com");

        memberBlockService.block(me.getId(), first.getId());
        memberBlockService.block(me.getId(), second.getId());
        memberBlockService.block(me.getId(), third.getId());
        memberBlockService.block(stranger.getId(), first.getId()); // 남의 차단

        CursorPage<BlockedMemberItem> page1 = memberBlockService.getMyBlocks(me.getId(), 2, null);
        assertThat(page1.items())
                .extracting(BlockedMemberItem::memberId)
                .containsExactly(third.getId(), second.getId()); // 최신순
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.totalCount()).isEqualTo(3); // 남의 차단은 세지 않는다

        CursorPage<BlockedMemberItem> page2 =
                memberBlockService.getMyBlocks(me.getId(), 2, page1.nextCursor());
        assertThat(page2.items())
                .extracting(BlockedMemberItem::memberId)
                .containsExactly(first.getId());
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.totalCount()).isNull(); // 이후 페이지는 총계 없음
    }

    @Test
    @DisplayName("해제하면 목록에서 사라지고, 차단이 없으면 빈 목록이다")
    void 해제_반영() {
        Member me = seedMember("unblocker@kakao.com");
        Member other = seedMember("blocked@kakao.com");

        assertThat(memberBlockService.getMyBlocks(me.getId(), 20, null).items()).isEmpty();

        memberBlockService.block(me.getId(), other.getId());
        CursorPage<BlockedMemberItem> page = memberBlockService.getMyBlocks(me.getId(), 20, null);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).memberId()).isEqualTo(other.getId());
        assertThat(page.items().get(0).blockedAt()).isNotNull();

        memberBlockService.unblock(me.getId(), other.getId());
        assertThat(memberBlockService.getMyBlocks(me.getId(), 20, null).items()).isEmpty();
    }
}

package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.dto.BlockedMemberItem;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차단 목록 조회 — 차단한 시각 최신순, 커서 2페이지, 남의 차단 제외, 해제 시 목록에서 사라짐.
 *
 * <p>차단 삽입은 네이티브 쿼리의 {@code now()}(= 트랜잭션 시작 시각)를 쓰므로 한 테스트 안에서 만든 차단은 모두 같은 시각을 갖는다. 그러면 정렬이 id
 * 타이브레이커만으로도 통과해버려 시각 정렬을 검증하지 못한다. 그래서 {@link #shiftBlockedAt}으로 시각을 직접 어긋나게 만들고, <b>id 순서와 시각 순서가
 * 반대</b>가 되도록 배치한다.
 */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class BlockListIntegrationTest {

    @Autowired MemberBlockService memberBlockService;
    @Autowired MemberRepository memberRepository;
    @PersistenceContext EntityManager em;

    private Member seedMember(String email) {
        return memberRepository.save(Member.createBySocial(email));
    }

    /** 차단 시각을 직접 옮긴다(같은 트랜잭션의 now()가 전부 같아서 필요). */
    private void shiftBlockedAt(Long blockerId, Long blockedId, LocalDateTime at) {
        em.createNativeQuery(
                        "UPDATE member_block SET created_at = ?1"
                                + " WHERE blocker_id = ?2 AND blocked_id = ?3")
                .setParameter(1, at)
                .setParameter(2, blockerId)
                .setParameter(3, blockedId)
                .executeUpdate();
        em.clear(); // 영속성 컨텍스트에 남은 옛 값 대신 DB 값을 다시 읽게 한다
    }

    @Test
    @DisplayName("id 순서와 시각 순서가 반대여도 차단한 시각 최신순으로 나온다 — 남의 차단은 섞이지 않는다")
    void 시각_정렬() {
        Member me = seedMember("blocker@kakao.com");
        Member stranger = seedMember("stranger@kakao.com");
        Member oldest = seedMember("oldest@kakao.com");
        Member middle = seedMember("middle@kakao.com");
        Member newest = seedMember("newest@kakao.com");

        // 먼저 차단한 쪽(작은 id)이 가장 최근 시각을 갖도록 뒤집는다 — id로만 정렬하면 여기서 깨진다
        LocalDateTime base = LocalDateTime.of(2026, 8, 1, 12, 0);
        memberBlockService.block(me.getId(), newest.getId());
        shiftBlockedAt(me.getId(), newest.getId(), base.plusHours(2));
        memberBlockService.block(me.getId(), middle.getId());
        shiftBlockedAt(me.getId(), middle.getId(), base.plusHours(1));
        memberBlockService.block(me.getId(), oldest.getId());
        shiftBlockedAt(me.getId(), oldest.getId(), base);
        memberBlockService.block(stranger.getId(), oldest.getId()); // 남의 차단

        CursorPage<BlockedMemberItem> page1 = memberBlockService.getMyBlocks(me.getId(), 2, null);
        assertThat(page1.items())
                .extracting(BlockedMemberItem::memberId)
                .containsExactly(newest.getId(), middle.getId());
        assertThat(page1.items().get(0).blockedAt()).isEqualTo(base.plusHours(2));
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.totalCount()).isEqualTo(3); // 남의 차단은 세지 않는다

        // 2페이지 — 커서의 created_at 비교 분기를 실제로 태운다(시각이 서로 다르므로)
        CursorPage<BlockedMemberItem> page2 =
                memberBlockService.getMyBlocks(me.getId(), 2, page1.nextCursor());
        assertThat(page2.items())
                .extracting(BlockedMemberItem::memberId)
                .containsExactly(oldest.getId());
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.totalCount()).isNull(); // 이후 페이지는 총계 없음
    }

    @Test
    @DisplayName("차단 시각이 같으면 id 내림차순으로 갈리고 커서가 항목을 건너뛰지 않는다")
    void 동시각_타이브레이커() {
        Member me = seedMember("tie@kakao.com");
        Member a = seedMember("tieA@kakao.com");
        Member b = seedMember("tieB@kakao.com");
        Member c = seedMember("tieC@kakao.com");
        // now()가 트랜잭션 시작 시각이라 셋 다 같은 시각이 된다(시각 보정 없음)
        memberBlockService.block(me.getId(), a.getId());
        memberBlockService.block(me.getId(), b.getId());
        memberBlockService.block(me.getId(), c.getId());

        CursorPage<BlockedMemberItem> page1 = memberBlockService.getMyBlocks(me.getId(), 2, null);
        CursorPage<BlockedMemberItem> page2 =
                memberBlockService.getMyBlocks(me.getId(), 2, page1.nextCursor());

        assertThat(page1.items())
                .extracting(BlockedMemberItem::memberId)
                .containsExactly(c.getId(), b.getId());
        assertThat(page2.items())
                .extracting(BlockedMemberItem::memberId)
                .containsExactly(a.getId());
    }

    @Test
    @DisplayName("익명화된 회원을 차단했으면 닉네임만 null로 나오고 항목은 남는다")
    void 익명화_회원() {
        Member me = seedMember("keeper@kakao.com");
        Member gone = seedMember("gone@kakao.com");
        gone.assignNickname("사라질 고양이");
        memberBlockService.block(me.getId(), gone.getId());

        gone.anonymize(LocalDateTime.now());
        em.flush();
        em.clear();

        CursorPage<BlockedMemberItem> page = memberBlockService.getMyBlocks(me.getId(), 20, null);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).memberId()).isEqualTo(gone.getId());
        assertThat(page.items().get(0).nickname()).isNull();
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

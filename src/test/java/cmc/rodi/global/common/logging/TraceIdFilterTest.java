package cmc.rodi.global.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    @Test
    void 필터가_traceId를_MDC와_헤더에_넣고_이후_정리한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] duringChain = new String[1];
        FilterChain chain = (req, res) -> duringChain[0] = MDC.get(TraceIdFilter.TRACE_ID);

        new TraceIdFilter().doFilter(request, response, chain);

        assertThat(duringChain[0]).isNotBlank();                              // 체인 실행 중 MDC에 존재
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(duringChain[0]); // 응답 헤더와 동일
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();                 // 요청 종료 후 정리됨
    }
}

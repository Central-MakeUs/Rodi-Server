package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.PlaceType;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** U3: 전체 좌표 목록(API#1) — 서비스 매핑(위경도)과 공개 접근(인증 없이 200)을 실제로 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceCoordinateIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired PlaceQueryService placeQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired MockMvc mockMvc;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat)); // x=경도, y=위도
    }

    @Test
    @DisplayName("서비스: 전체 place 좌표를 위경도·유형과 함께 반환")
    void 전체_좌표() {
        courseRepository.save(
                Course.builder().name("u3-코스").location(point(37.51, 127.03)).build());
        parkingRepository.save(
                Parking.builder().name("u3-주차장").location(point(37.5734, 126.9759)).build());

        var all = placeQueryService.getAllCoordinates();

        PlaceCoordinateResponse course =
                all.stream().filter(p -> p.name().equals("u3-코스")).findFirst().orElseThrow();
        assertThat(course.type()).isEqualTo(PlaceType.COURSE);
        assertThat(course.lat()).isCloseTo(37.51, within(1e-6));
        assertThat(course.lng()).isCloseTo(127.03, within(1e-6));

        PlaceCoordinateResponse parking =
                all.stream().filter(p -> p.name().equals("u3-주차장")).findFirst().orElseThrow();
        assertThat(parking.type()).isEqualTo(PlaceType.PARKING);
    }

    @Test
    @DisplayName("공개: 인증 없이 GET /places/coordinates 200")
    void 공개_접근() throws Exception {
        courseRepository.save(Course.builder().name("u3-공개").location(point(37.5, 127.0)).build());

        mockMvc.perform(get("/api/v1/places/coordinates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}

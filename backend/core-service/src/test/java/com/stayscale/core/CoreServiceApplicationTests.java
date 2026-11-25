package com.stayscale.core;

import com.stayscale.core.config.TraceIdFilter;
import com.stayscale.core.controller.HotelSearchController;
import com.stayscale.core.dto.CitySummaryResponse;
import com.stayscale.core.exception.GlobalExceptionHandler;
import com.stayscale.core.exception.InvalidSearchException;
import com.stayscale.core.service.HotelCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CoreServiceApplicationTests {

    @Mock
    private HotelCatalogService catalogService;

    @Test
    void controllerReturnsCitiesAndTraceIdHeader() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HotelSearchController(catalogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();

        when(catalogService.listCities()).thenReturn(List.of(
                new CitySummaryResponse("Goa", "India", 16, 340_000L, "INR", "/images/hotels/goa-resort.jpg", "Sun & Sea", 15.54, 73.75)
        ));

        mockMvc.perform(get("/api/v1/hotels/cities"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$[0].city").value("Goa"))
                .andExpect(jsonPath("$[0].hotelCount").value(16));
    }

    @Test
    void returnsRfc7807ProblemDetailOnInvalidDates() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HotelSearchController(catalogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();

        when(catalogService.searchHotels(any(), any(), any(), any(), anyInt(), anyInt(),
                any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new InvalidSearchException("checkOut date must be strictly after checkIn date"));

        mockMvc.perform(get("/api/v1/hotels/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Search Parameters"))
                .andExpect(jsonPath("$.detail").value("checkOut date must be strictly after checkIn date"))
                .andExpect(jsonPath("$.traceId").exists());
    }
}

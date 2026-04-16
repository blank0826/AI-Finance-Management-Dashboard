package com.finance.controller;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finance.dto.Dtos;
import com.finance.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.DashboardService;
import com.finance.service.ExportService;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerIT {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private ExportService exportService;

    @MockBean
    private UserRepository userRepo;

    @Test
    @WithMockUser(username = "bob@example.com")
    void getSummary_returnsOk() throws Exception {
        User u = new User();
        u.setId(42L);
        u.setEmail("bob@example.com");

        when(userRepo.findByEmail("bob@example.com")).thenReturn(Optional.of(u));

        Dtos.DashboardSummary s = new Dtos.DashboardSummary();
        s.setTotalSpending(BigDecimal.ZERO);
        when(dashboardService.getSummary(anyLong(), anyInt(), anyInt())).thenReturn(s);

        mvc.perform(get("/api/dashboard/summary").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }
}

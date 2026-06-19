package in.drdo.gis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.drdo.gis.dto.DeploymentRequestDto;
import in.drdo.gis.dto.DeploymentResponseDto;
import in.drdo.gis.service.DeploymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeploymentController.class)
class DeploymentControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean DeploymentService deploymentService;

    @Test
    void createDeployment_returns201() throws Exception {
        DeploymentRequestDto req = new DeploymentRequestDto();
        req.setCenterLat(28.6139); req.setCenterLon(77.2090);
        req.setFrontageM(200.0);   req.setDepthM(100.0);

        DeploymentResponseDto resp = new DeploymentResponseDto();
        resp.setDeploymentUid("test-uid-123");
        resp.setStatus("READY");

        when(deploymentService.createDeployment(any())).thenReturn(resp);

        mockMvc.perform(post("/v1/deployments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.deploymentUid").value("test-uid-123"))
            .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void listDeployments_returns200() throws Exception {
        when(deploymentService.listDeployments()).thenReturn(List.of());
        mockMvc.perform(get("/v1/deployments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createDeployment_invalidParams_returns400() throws Exception {
        DeploymentRequestDto req = new DeploymentRequestDto();
        // missing required fields
        mockMvc.perform(post("/v1/deployments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }
}
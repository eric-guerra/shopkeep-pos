package com.ericguerra.shopkeep.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void cashierCanSearchAndScanButNotEditCatalog() throws Exception {
        mvc.perform(get("/api/products").param("q", "coffee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
        mvc.perform(get("/api/products/scan/012345678929"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("MUG-LOGO"));
        mvc.perform(post("/api/products").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/daily")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void cashierCannotApplyOrderDiscount() throws Exception {
        mvc.perform(post("/api/sales").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"productId\":1,\"quantity\":1,\"discountPercent\":0}],\"orderDiscountCents\":500,\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MANAGER_REQUIRED"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managerValidationErrorsAreStructured() throws Exception {
        mvc.perform(post("/api/products").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"bad sku\",\"name\":\"\",\"priceCents\":-1,\"costCents\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details.sku").exists())
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.priceCents").exists());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void writesRequireCsrfToken() throws Exception {
        mvc.perform(post("/api/sales/quote").contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[]}"))
                .andExpect(status().isForbidden());
    }
}

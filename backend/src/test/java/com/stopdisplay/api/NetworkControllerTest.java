package com.stopdisplay.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NetworkControllerTest {

    @Test
    void returnsAvailableNetworkMetadata() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new NetworkController()).build();

        mockMvc.perform(get("/api/networks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].id").value("tam"))
                .andExpect(jsonPath("$[0].logo").doesNotExist())
                .andExpect(jsonPath("$[0].name").value("TaM, Métropole de Montpellier"))
                .andExpect(jsonPath("$[0].acronym").value("TaM"))
                .andExpect(jsonPath("$[0].website").value("https://www.tam-voyages.com/"));
    }
}

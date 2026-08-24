package dev.example.jsinjava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.ObjectMapper;

// The controller is plain MVC: view name + model. These tests wire it to the
// VueSsrViewResolver through MockMvc, proving the SSR machinery stays entirely
// in the view layer and the view name selects page component + client entry.
class PageControllerTest {

    private VueSsrRenderer renderer;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        renderer = mock(VueSsrRenderer.class);
        when(renderer.hasPage(anyString())).thenReturn(true);
        mockMvc = MockMvcBuilders.standaloneSetup(new PageController())
                .setViewResolvers(new VueSsrViewResolver(renderer, new ObjectMapper()))
                .build();
    }

    @Test
    void servesTheHomePageWithoutKnowingAboutRendering() throws Exception {
        when(renderer.render(eq("home"), anyString()))
                .thenReturn("<main><h1>rendered</h1></main>");

        var body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("<div id=\"app\"><main><h1>rendered</h1></main></div>")
                .contains("window.__INITIAL_STATE__ = {")
                .contains("\"greeting\":\"Hello from the JVM")
                .contains("\"count\":0")
                .contains("<script type=\"module\" src=\"/assets/home.js\"></script>");
    }

    @Test
    void servesTheAboutPageWithItsOwnComponentAndEntry() throws Exception {
        when(renderer.render(eq("about"), anyString()))
                .thenReturn("<main><h1>about</h1></main>");

        var body = mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("<main><h1>about</h1></main>")
                .contains("\"heading\":\"About this experiment\"")
                .contains("<script type=\"module\" src=\"/assets/about.js\"></script>");
    }
}

package com.tattoostudio.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.tattoostudio.support.ApiTest;

/** robots.txt and sitemap.xml come from the server, with the address the site was reached at. */
class SeoControllerTest extends ApiTest {

    @Test
    void robotsKeepsPrivatePagesOutAndPointsToTheSitemap() throws Exception {
        mvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("Disallow: /admin")))
                .andExpect(content().string(containsString("Disallow: /staff")))
                .andExpect(content().string(containsString("Sitemap: http://localhost/sitemap.xml")));
    }

    @Test
    void sitemapListsThePublicPagesAndEachArtist() throws Exception {
        mvc.perform(get("/sitemap.xml").header("Host", "bladeandbrush.example"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<loc>http://bladeandbrush.example/flash</loc>")))
                .andExpect(content().string(containsString("<loc>http://bladeandbrush.example/privacy</loc>")))
                .andExpect(content().string(containsString("<loc>http://bladeandbrush.example/artists/" + artist("Kai").getSlug() + "</loc>")))
                .andExpect(content().string(not(containsString("/admin"))));
    }
}

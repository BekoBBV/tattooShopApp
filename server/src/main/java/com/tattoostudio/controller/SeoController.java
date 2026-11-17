package com.tattoostudio.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.tattoostudio.model.Artist;
import com.tattoostudio.repository.ArtistRepository;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * What search engines read: robots.txt (index the public site, not the staff, admin, account or booking
 * pages) and sitemap.xml (the public pages, each artist's page included). Both are built from the
 * address the site was reached at, so they hold the right domain wherever the site runs; behind
 * Render's proxy that address comes from its forwarded headers (server.forward-headers-strategy).
 */
@Hidden
@RestController
public class SeoController {

    /** The public pages that are always there, home first. */
    static final List<String> PAGES = List.of("/", "/flash", "/artists", "/aftercare", "/privacy", "/terms", "/credits");

    private final ArtistRepository artists;

    public SeoController(ArtistRepository artists) {
        this.artists = artists;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return """
                User-agent: *
                Disallow: /staff
                Disallow: /admin
                Disallow: /account
                Disallow: /book
                Disallow: /manage
                Disallow: /booking
                Disallow: /api/

                Sitemap: %s/sitemap.xml
                """.formatted(base());
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        String base = base();
        List<String> paths = new ArrayList<>(PAGES);
        for (Artist artist : artists.findByActiveTrueOrderBySortOrderAsc()) {
            paths.add("/artists/" + artist.getSlug());
        }
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String path : paths) {
            xml.append("  <url><loc>").append(escape(base + path)).append("</loc></url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    /** "https://example.com", from this request. */
    private static String base() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

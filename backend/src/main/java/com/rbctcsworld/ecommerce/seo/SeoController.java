package com.rbctcsworld.ecommerce.seo;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * For search engines (ADR-0019). nginx serves them at the site root: /robots.txt and /sitemap.xml.
 * GET /api/seo/robots.txt   public  200 text/plain
 * GET /api/seo/sitemap.xml  public  200 application/xml
 */
@RestController
public class SeoController {

    private static final CacheControl HOUR = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final SeoService service;

    public SeoController(SeoService service) {
        this.service = service;
    }

    @GetMapping("/api/seo/robots.txt")
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).cacheControl(HOUR).body(service.robots());
    }

    @GetMapping("/api/seo/sitemap.xml")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).cacheControl(HOUR).body(service.sitemap());
    }
}

package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Product photos (ADR-0017).
 * POST /api/products/pictures   ADMIN, multipart field "file" (JPEG or PNG, up to 10 MB)
 *        201 {url, width, height, contentType, sizeBytes} a new picture | 200 the same picture was already there
 *        400 not JPEG/PNG, unreadable, under 200 px, over 50 megapixels, demo shop | 401 | 403 | 413 over 10 MB
 *        | 429 two photos are already being processed
 * GET  /api/products/pictures/{sha256}.jpg|png  public  200 the picture, cached for a year (the name is its content)
 *        | 304 If-None-Match | 404
 * The returned url goes into the product's imageUrl like any other picture (PUT /api/products/{id}).
 */
@RestController
@RequestMapping("/api/products/pictures")
public class ProductPictureController {

    private final ProductPictureService service;

    public ProductPictureController(ProductPictureService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Uploaded> upload(@RequestParam("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException(PictureProcessor.UNREADABLE);
        }
        ProductPictureStore.Saved saved = service.upload(bytes);
        ProductPicture p = saved.picture();
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new Uploaded(p.url(), p.width(), p.height(), p.contentType(), p.sizeBytes()));
    }

    // hex name + extension only: "/api/products/pictures/variants" must never compete with "/api/products/{id}/variants"
    @GetMapping("/{fileName:[0-9a-f]+\\.(?:jpg|png)}")
    public ResponseEntity<byte[]> picture(@PathVariable String fileName,
                                          @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        Optional<ProductPicture> known = service.find(fileName);
        if (known.isPresent() && etag(known.get()).equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag(known.get())).cacheControl(YEAR).build();
        }
        ProductPictureService.Served served = service.serve(fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(served.picture().contentType()))
                .contentLength(served.data().length)
                .eTag(etag(served.picture()))
                .cacheControl(YEAR)
                .body(served.data());
    }

    /** The address names the content, so it never changes: browsers and the CDN may keep it for a year. */
    private static final CacheControl YEAR = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private static String etag(ProductPicture p) {
        return "\"" + p.sha256() + "\"";
    }

    public record Uploaded(String url, int width, int height, String contentType, int sizeBytes) {
    }
}

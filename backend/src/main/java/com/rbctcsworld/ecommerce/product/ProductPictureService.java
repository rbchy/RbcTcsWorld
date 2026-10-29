package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.common.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/** Upload and delivery of product photos (ADR-0017). */
@Service
public class ProductPictureService {

    /** Decoding a big photo needs tens of MB of memory: at most two at a time, the rest are asked to retry. */
    static final int PARALLEL_UPLOADS = 2;
    static final String BUSY = "Another photo is being processed; please try again in a moment";

    private final PictureProcessor processor;
    private final ProductPictureStore store;
    private final ProductImagePolicy policy;
    private final Clock clock;
    private final Semaphore slots = new Semaphore(PARALLEL_UPLOADS);

    @Autowired
    public ProductPictureService(ProductPictureStore store, ProductImagePolicy policy, Clock clock) {
        this(new PictureProcessor(), store, policy, clock);
    }

    ProductPictureService(PictureProcessor processor, ProductPictureStore store, ProductImagePolicy policy, Clock clock) {
        this.processor = processor;
        this.store = store;
        this.policy = policy;
        this.clock = clock;
    }

    public ProductPictureStore.Saved upload(byte[] file) {
        policy.checkUpload();
        if (!slots.tryAcquire()) throw new TooManyRequestsException(BUSY, 2);
        try {
            PictureProcessor.Picture picture = processor.process(file);
            return store.save(picture, currentUser(), LocalDateTime.now(clock));
        } finally {
            slots.release();
        }
    }

    /** The picture behind an address like {@code <sha256>.jpg}; 404 for any other name or a wrong extension. */
    public Served serve(String fileName) {
        if (fileName == null || !fileName.matches(ProductPicture.FILE_NAME)) throw notFound();
        String sha256 = fileName.substring(0, 64);
        ProductPicture picture = store.find(sha256).orElseThrow(ProductPictureService::notFound);
        if (!fileName.endsWith(ProductPicture.extension(picture.contentType()))) throw notFound();
        byte[] data = store.data(sha256).orElseThrow(ProductPictureService::notFound);
        return new Served(picture, data);
    }

    /** Only the metadata: answers a cache check (If-None-Match) without reading the bytes. */
    public Optional<ProductPicture> find(String fileName) {
        if (fileName == null || !fileName.matches(ProductPicture.FILE_NAME)) return Optional.empty();
        return store.find(fileName.substring(0, 64))
                .filter(p -> fileName.endsWith(ProductPicture.extension(p.contentType())));
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Picture not found");
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? "system" : auth.getName();
    }

    public record Served(ProductPicture picture, byte[] data) {
    }
}

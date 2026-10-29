package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REQ-CAT-15: upload rules around PictureProcessor - demo, two at a time, who and when, the address of a picture. */
class ProductPictureServiceTest {

    private static final String SHA = "a".repeat(64);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

    private final PictureProcessor processor = mock(PictureProcessor.class);
    private final ProductPictureStore store = mock(ProductPictureStore.class);
    private final ProductImagePolicy policy = mock(ProductImagePolicy.class);
    private final ProductPictureService service = new ProductPictureService(processor, store, policy, CLOCK);

    private final PictureProcessor.Picture picture =
            new PictureProcessor.Picture(new byte[]{1, 2}, "image/jpeg", "jpg", 400, 300, SHA);
    private final ProductPicture stored = new ProductPicture(SHA, "image/jpeg", 400, 300, 2, "owner@shop.test", NOW);

    @AfterEach
    void logOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theUploadIsProcessedAndSavedWithTheUploaderAndTheTime() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner@shop.test", null, List.of()));
        when(processor.process(any())).thenReturn(picture);
        ProductPictureStore.Saved saved = new ProductPictureStore.Saved(stored, true);
        when(store.save(picture, "owner@shop.test", NOW)).thenReturn(saved);

        assertThat(service.upload(new byte[]{9})).isSameAs(saved);
        verify(policy).checkUpload();
    }

    @Test
    void withoutALoginTheUploaderIsTheSystem() {
        when(processor.process(any())).thenReturn(picture);
        service.upload(new byte[]{9});
        verify(store).save(picture, "system", NOW);
    }

    @Test
    void theDemoRefusesBeforeAnythingIsDecodedOrSaved() {
        doThrow(new BusinessRuleException(ProductImagePolicy.DEMO_UPLOAD_MESSAGE)).when(policy).checkUpload();
        assertThatThrownBy(() -> service.upload(new byte[]{9})).hasMessage(ProductImagePolicy.DEMO_UPLOAD_MESSAGE);
        verify(processor, never()).process(any());
        verify(store, never()).save(any(), anyString(), any());
    }

    @Test
    void atMostTwoPhotosAreProcessedAtOnce() {
        AtomicInteger depth = new AtomicInteger();
        when(processor.process(any())).thenAnswer(call -> {
            int level = depth.incrementAndGet();
            if (level == 1) service.upload(new byte[]{2});            // a second upload while the first is busy: fine
            if (level == 2) assertThatThrownBy(() -> service.upload(new byte[]{3}))   // a third one: asked to wait
                    .isInstanceOf(TooManyRequestsException.class).hasMessage(ProductPictureService.BUSY);
            return picture;
        });
        service.upload(new byte[]{1});
        assertThat(depth.get()).isEqualTo(2);
    }

    @Test
    void aRefusedPhotoFreesItsSlot() {
        when(processor.process(any())).thenThrow(new BusinessRuleException(PictureProcessor.NOT_JPEG_OR_PNG));
        for (int i = 0; i < ProductPictureService.PARALLEL_UPLOADS + 3; i++) {
            assertThatThrownBy(() -> service.upload(new byte[]{1})).isInstanceOf(BusinessRuleException.class);
        }
    }

    @Test
    void aPictureIsServedByItsNameAndItsOwnExtension() {
        when(store.find(SHA)).thenReturn(Optional.of(stored));
        when(store.data(SHA)).thenReturn(Optional.of(new byte[]{1, 2}));
        ProductPictureService.Served served = service.serve(SHA + ".jpg");
        assertThat(served.picture()).isSameAs(stored);
        assertThat(served.data()).containsExactly(1, 2);
        assertThat(service.find(SHA + ".jpg")).contains(stored);
        assertThat(stored.url()).isEqualTo("/api/products/pictures/" + SHA + ".jpg");
    }

    @Test
    void aPngIsOnlyFoundWithPng() {
        ProductPicture png = new ProductPicture(SHA, "image/png", 400, 300, 2, "x", NOW);
        when(store.find(SHA)).thenReturn(Optional.of(png));
        when(store.data(SHA)).thenReturn(Optional.of(new byte[]{1}));
        assertThat(png.url()).endsWith(".png");
        assertThat(service.find(SHA + ".png")).contains(png);
        assertThat(service.find(SHA + ".jpg")).isEmpty();
        assertThatThrownBy(() -> service.serve(SHA + ".jpg")).isInstanceOf(NotFoundException.class);
        assertThat(service.serve(SHA + ".png").picture()).isSameAs(png);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc.jpg", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA.jpg", "x.svg", ""})
    void anyOtherNameIsNotFoundWithoutAskingTheDatabase(String name) {
        assertThatThrownBy(() -> service.serve(name)).isInstanceOf(NotFoundException.class);
        assertThat(service.find(name)).isEmpty();
        assertThat(service.find(null)).isEmpty();
        assertThatThrownBy(() -> service.serve(null)).isInstanceOf(NotFoundException.class);
        verify(store, never()).find(anyString());
    }

    @Test
    void anUnknownPictureOrMissingBytesAreNotFound() {
        when(store.find(SHA)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.serve(SHA + ".jpg")).isInstanceOf(NotFoundException.class).hasMessage("Picture not found");
        when(store.find(SHA)).thenReturn(Optional.of(stored));
        when(store.data(SHA)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.serve(SHA + ".jpg")).isInstanceOf(NotFoundException.class);
    }
}

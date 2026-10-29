package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.product.PictureProcessorTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Product photo upload and delivery through the real API and database: REQ-CAT-15 (ADR-0017). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductPictureIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = token(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"));
    }

    private String token(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString()).get("token").asString();
    }

    private ResultActions upload(byte[] bytes, String filename, String bearer) throws Exception {
        var request = multipart("/api/products/pictures").file(new MockMultipartFile("file", filename, "image/jpeg", bytes));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return mvc.perform(request);
    }

    /** A photo nobody uploaded before (a random colour and spot), so the tests do not depend on each other. */
    private static byte[] newPhoto() throws Exception {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        BufferedImage b = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = b.createGraphics();
        g.setColor(new java.awt.Color(r.nextInt(0x1000000)));
        g.fillRect(0, 0, 1200, 900);
        g.setColor(java.awt.Color.RED);
        g.fillRect(r.nextInt(1000), r.nextInt(700), 150, 150);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        ImageIO.write(b, "jpg", out);
        return out.toByteArray();
    }

    @Test
    void theAdminUploadsAPhotoAndItIsStoredOnceAsASafeJpeg() throws Exception {
        byte[] photo = PictureProcessorTest.withOrientation(newPhoto(), 6, true);
        MvcResult first = upload(photo, "phone.jpg", admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(matchesPattern("/api/products/pictures/[0-9a-f]{64}\\.jpg")))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.width").value(900)).andExpect(jsonPath("$.height").value(1200))   // turned upright
                .andReturn();
        String url = mapper.readTree(first.getResponse().getContentAsString()).get("url").asString();

        upload(photo, "again.jpg", admin).andExpect(status().isOk()).andExpect(jsonPath("$.url").value(url));
        String sha = url.substring(url.lastIndexOf('/') + 1, url.length() - 4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_pictures WHERE sha256 = ?", Integer.class, sha)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT uploaded_by FROM product_pictures WHERE sha256 = ?", String.class, sha))
                .isEqualTo("admin@rbctcsworld.com");
    }

    @Test
    void anyoneCanLoadThePictureAndBrowsersMayKeepItForAYear() throws Exception {
        String url = url(upload(newPhoto(), "p.jpg", admin).andExpect(status().isCreated()).andReturn());
        String sha = url.substring(url.lastIndexOf('/') + 1, url.length() - 4);

        MvcResult served = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().string("ETag", "\"" + sha + "\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(served.getResponse().getContentAsByteArray()));
        assertThat(image).as("a real picture comes back").isNotNull();

        mvc.perform(get(url).header("If-None-Match", "\"" + sha + "\"")).andExpect(status().isNotModified());
        mvc.perform(get(url.replace(".jpg", ".png"))).andExpect(status().isNotFound());              // wrong extension
        mvc.perform(get("/api/products/pictures/" + "0".repeat(64) + ".jpg")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/pictures/..%2F..%2Fetc%2Fpasswd")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/products/pictures/abc.jpg")).andExpect(status().isNotFound());
    }

    @Test
    void theUploadedPictureBecomesTheProductsPicture() throws Exception {
        String url = url(upload(newPhoto(), "mug.jpg", admin).andExpect(status().isCreated()).andReturn());
        String sku = "PIC-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode created = mapper.readTree(mvc.perform(post("/api/products").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Photo mug\",\"sku\":\"" + sku + "\",\"price\":9.99,\"stock\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = created.get("id").asLong();

        mvc.perform(put("/api/products/" + id).header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Photo mug\",\"sku\":\"" + sku + "\",\"price\":9.99,\"stock\":3,\"imageUrl\":\"" + url + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(url));
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.imageUrl").value(url));

        // the address must have the exact form: no other path under /api, no upper-case or short names
        for (String bad : new String[]{"/api/products/pictures/ABC.jpg", "/api/admin/orders", "/api/products/pictures/"
                + "a".repeat(64) + ".svg"}) {
            mvc.perform(put("/api/products/" + id).header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Photo mug\",\"sku\":\"" + sku + "\",\"price\":9.99,\"stock\":3,\"imageUrl\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void onlyTheAdminMayUpload() throws Exception {
        String customer = token(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"pic-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"));
        upload(newPhoto(), "x.jpg", customer).andExpect(status().isForbidden());
        upload(newPhoto(), "x.jpg", null).andExpect(status().isUnauthorized());
    }

    @Test
    void filesThatAreNotJpegOrPngOrAreTooSmallAreRefusedWithAClearMessage() throws Exception {
        upload("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(), "evil.jpg", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only JPEG or PNG pictures can be uploaded"));
        upload(PictureProcessorTest.picture(120, 120, "png", false), "tiny.png", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("at least 200 pixels")));
        mvc.perform(multipart("/api/products/pictures").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());                                                     // no file at all
    }

    private String url(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("url").asString();
    }
}

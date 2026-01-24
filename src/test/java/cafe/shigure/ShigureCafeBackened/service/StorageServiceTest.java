package cafe.shigure.ShigureCafeBackened.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @InjectMocks
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "bucket", "test-bucket");
        ReflectionTestUtils.setField(storageService, "publicUrl", "https://pub.example.com");
    }

    @Test
    void generatePresignedUploadUrl_shouldReturnUrls() throws Exception {
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://s3.example.com/upload"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        Map<String, String> result = storageService.generatePresignedUploadUrl("avatars", "image/png");

        assertNotNull(result.get("uploadUrl"));
        assertTrue(result.get("publicUrl").startsWith("https://pub.example.com/avatars/"));
        assertEquals("https://s3.example.com/upload", result.get("uploadUrl"));
    }

    @Test
    void uploadFile_shouldReturnPublicUrl() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.png");
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(100L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[100]));

        String result = storageService.uploadFile(file, "uploads");

        assertTrue(result.startsWith("https://pub.example.com/uploads/"));
        assertTrue(result.endsWith("-test.png"));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}

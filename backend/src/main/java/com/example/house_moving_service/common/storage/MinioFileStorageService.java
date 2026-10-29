package com.example.house_moving_service.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Lưu file vào MinIO (bucket private, đọc qua presigned URL). Chỉ tạo khi app.storage.provider=MINIO.
 * Bucket được tạo ở lần ghi đầu tiên (không tạo lúc khởi động) để app vẫn lên được khi MinIO tạm thời chưa chạy.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "MINIO")
public class MinioFileStorageService implements FileStorageService {

    private final MinioClient minioClient;
    private final String bucket;
    private volatile boolean bucketReady;

    public MinioFileStorageService(StorageProperties properties) {
        StorageProperties.Minio minio = properties.getMinio();
        this.minioClient = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
        this.bucket = minio.getBucket();
    }

    @Override
    public StoredFile upload(String key, InputStream data, long size, String contentType) {
        ensureBucket();
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .build());
            return new StoredFile(key, contentType, size);
        } catch (Exception e) {
            throw new StorageException("Không upload được file lên MinIO: " + key, e);
        }
    }

    @Override
    public String generateAccessUrl(String key, Duration ttl) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Không tạo được URL truy cập cho file: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("Không xóa được file trên MinIO: " + key, e);
        }
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("Created MinIO bucket: {}", bucket);
                }
                bucketReady = true;
            } catch (Exception e) {
                throw new StorageException("Không kết nối / tạo được bucket MinIO: " + bucket, e);
            }
        }
    }
}

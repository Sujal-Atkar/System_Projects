package com.system_project.distributedpaymentsystem.service.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.system_project.distributedpaymentsystem.domain.entity.IdempotencyRecord;
import com.system_project.distributedpaymentsystem.domain.entity.IdempotencyRecordStatus;
import com.system_project.distributedpaymentsystem.domain.entity.Merchant;
import com.system_project.distributedpaymentsystem.domain.entity.Payment;
import com.system_project.distributedpaymentsystem.repository.IdempotencyRecordRepository;
import com.system_project.distributedpaymentsystem.repository.MerchantRepository;
import com.system_project.distributedpaymentsystem.repository.PaymentRepository;

@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "payment:idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final MerchantRepository merchantRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final PaymentRepository paymentRepository;

    public IdempotencyService(StringRedisTemplate redisTemplate,
            MerchantRepository merchantRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            PaymentRepository paymentRepository) {
        this.redisTemplate = redisTemplate;
        this.merchantRepository = merchantRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.paymentRepository = paymentRepository;
    }

    public IdempotencyReservation reserve(UUID merchantId, String idempotencyKey, String requestFingerprint) {
        String stateKey = buildStateKey(merchantId, idempotencyKey);
        String fingerprintKey = buildFingerprintKey(merchantId, idempotencyKey);
        String currentFingerprint = redisTemplate.opsForValue().get(fingerprintKey);

        if (currentFingerprint != null) {
            if (currentFingerprint.equals(requestFingerprint)) {
                return new IdempotencyReservation(false, true, currentFingerprint);
            }
            return new IdempotencyReservation(false, false, currentFingerprint);
        }

        String script = "if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end; "
                + "redis.call('SET', KEYS[1], 'RESERVED', 'EX', ARGV[1], 'NX'); "
                + "redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[1], 'NX'); "
                + "return 1";

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long result = redisTemplate.execute(redisScript, java.util.List.of(stateKey, fingerprintKey), String.valueOf(TTL.getSeconds()), requestFingerprint);

        if (result != null && result == 1L) {
            return new IdempotencyReservation(true, false, null);
        }

        String refreshedFingerprint = redisTemplate.opsForValue().get(fingerprintKey);
        if (refreshedFingerprint == null) {
            return new IdempotencyReservation(false, false, null);
        }
        if (refreshedFingerprint.equals(requestFingerprint)) {
            return new IdempotencyReservation(false, true, refreshedFingerprint);
        }
        return new IdempotencyReservation(false, false, refreshedFingerprint);
    }

    public void attachPayment(UUID merchantId, String idempotencyKey, Payment payment, String requestFingerprint) {
        String stateKey = buildStateKey(merchantId, idempotencyKey);
        String paymentKey = buildPaymentKey(merchantId, idempotencyKey);
        redisTemplate.opsForValue().set(stateKey, "PROCESSING", TTL);
        redisTemplate.opsForValue().set(paymentKey, payment.getPaymentId().toString(), TTL);
        redisTemplate.opsForValue().set(buildFingerprintKey(merchantId, idempotencyKey), requestFingerprint, TTL);
    }

        public void completeInRedis(UUID merchantId, String idempotencyKey, Payment payment, String requestFingerprint,
            int responseStatus, Map<String, Object> responsePayload) {
        String stateKey = buildStateKey(merchantId, idempotencyKey);
        String paymentKey = buildPaymentKey(merchantId, idempotencyKey);
        String fingerprintKey = buildFingerprintKey(merchantId, idempotencyKey);
        String payloadKey = buildPayloadKey(merchantId, idempotencyKey);

        redisTemplate.opsForValue().set(stateKey, "COMPLETED", TTL);
        redisTemplate.opsForValue().set(paymentKey, payment.getPaymentId().toString(), TTL);
        redisTemplate.opsForValue().set(fingerprintKey, requestFingerprint, TTL);
        redisTemplate.opsForValue().set(payloadKey, responsePayload == null ? "{}" : responsePayload.toString(), TTL);
    }
    public void complete(UUID merchantId, String idempotencyKey, Payment payment, String requestFingerprint,
            int responseStatus, Map<String, Object> responsePayload) {
        String stateKey = buildStateKey(merchantId, idempotencyKey);
        String paymentKey = buildPaymentKey(merchantId, idempotencyKey);
        String fingerprintKey = buildFingerprintKey(merchantId, idempotencyKey);
        String payloadKey = buildPayloadKey(merchantId, idempotencyKey);

        redisTemplate.opsForValue().set(stateKey, "COMPLETED", TTL);
        redisTemplate.opsForValue().set(paymentKey, payment.getPaymentId().toString(), TTL);
        redisTemplate.opsForValue().set(fingerprintKey, requestFingerprint, TTL);
        redisTemplate.opsForValue().set(payloadKey, responsePayload == null ? "{}" : responsePayload.toString(), TTL);

        Merchant merchant = merchantRepository.findById(merchantId).orElse(null);
        if (merchant == null || payment == null) {
            return;
        }

        java.util.Optional<IdempotencyRecord> existingByKey = idempotencyRecordRepository
                .findByMerchant_MerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
        if (existingByKey.isPresent()) {
            IdempotencyRecord record = existingByKey.get();
            record.setRequestHash(hashFingerprint(requestFingerprint));
            record.setResponseStatus(responseStatus);
            record.setResponsePayload(responsePayload);
            record.setStatus(IdempotencyRecordStatus.COMPLETED);
            record.setExpiresAt(OffsetDateTime.now(OffsetDateTime.now().getOffset()).plusHours(24));
            idempotencyRecordRepository.save(record);
        } else if (idempotencyRecordRepository.findByPayment_PaymentId(payment.getPaymentId()).isEmpty()) {
            IdempotencyRecord record = new IdempotencyRecord(
                    UUID.randomUUID(),
                    merchant,
                    idempotencyKey,
                    payment,
                    hashFingerprint(requestFingerprint),
                    responseStatus,
                    OffsetDateTime.now(OffsetDateTime.now().getOffset()),
                    OffsetDateTime.now(OffsetDateTime.now().getOffset()).plusHours(24),
                    responsePayload,
                    IdempotencyRecordStatus.COMPLETED);
            idempotencyRecordRepository.save(record);
        }
    }

    public java.util.Optional<Payment> findExistingPayment(UUID merchantId, String idempotencyKey) {
        return paymentRepository.findByMerchant_MerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
    }

    private String buildKeyPrefix(UUID merchantId) {
        return KEY_PREFIX + merchantId;
    }

    private String buildStateKey(UUID merchantId, String idempotencyKey) {
        return buildKeyPrefix(merchantId) + ":" + idempotencyKey + ":state";
    }

    private String buildFingerprintKey(UUID merchantId, String idempotencyKey) {
        return buildKeyPrefix(merchantId) + ":" + idempotencyKey + ":fingerprint";
    }

    private String buildPaymentKey(UUID merchantId, String idempotencyKey) {
        return buildKeyPrefix(merchantId) + ":" + idempotencyKey + ":payment";
    }

    private String buildPayloadKey(UUID merchantId, String idempotencyKey) {
        return buildKeyPrefix(merchantId) + ":" + idempotencyKey + ":payload";
    }

    private String hashFingerprint(String fingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            return fingerprint;
        }
    }
}

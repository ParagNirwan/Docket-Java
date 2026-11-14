package com.docket.app.service;

import com.docket.app.config.DocketWebhookProperties;
import com.docket.app.dto.CreateWebhookRequestDTO;
import com.docket.app.dto.WebhookEndpointDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.WebhookDelivery;
import com.docket.app.entity.WebhookEndpoint;
import com.docket.app.enums.Permission;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.WebhookDeliveryRepository;
import com.docket.app.repository.WebhookEndpointRepository;
import com.docket.app.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;

    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Mock
    private TenantAuthorizationService tenantAuthorizationService;

    @Mock
    private AuditService auditService;

    @Mock
    private DocketWebhookProperties webhookProperties;

    private WebhookService webhookService;

    private Tenant tenant;
    private WebhookEndpoint endpoint;
    private Authentication authentication;
    private String secret = "testsecret123";

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(
                tenantRepository, webhookEndpointRepository, webhookDeliveryRepository,
                tenantAuthorizationService, auditService, webhookProperties
        );

        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setSlug("test-tenant");

        endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setTenantId(tenant.getId());
        endpoint.setUrl("https://example.com/webhook");
        endpoint.setSecret(secret);
        endpoint.setEvents(List.of("document.created"));
        endpoint.setActive(true);
        endpoint.setCreatedAt(Instant.now());

        AuthenticatedUser user = new AuthenticatedUser(
                "test@example.com",
                "password",
                List.of(),
                1
        );
        authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());

        when(webhookProperties.getMaxRetries()).thenReturn(5);
        when(webhookProperties.getRetryDelayMs()).thenReturn(60_000L);
        when(webhookProperties.getMaxRetryDelayMs()).thenReturn(3_600_000L);
        when(webhookProperties.getHttpTimeoutMs()).thenReturn(10_000);
        when(webhookProperties.getBatchSize()).thenReturn(100);
    }

    @Test
    void computeHmacSha256_returnsValidSignature() throws Exception {
        String payload = "{\"event\":\"test\"}";

        // Use reflection to test private method
        var method = WebhookService.class.getDeclaredMethod("computeHmacSha256", String.class, String.class);
        method.setAccessible(true);
        String signature = (String) method.invoke(webhookService, secret, payload);

        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        // Verify it's a valid base64 string
        assertDoesNotThrow(() -> Base64.getDecoder().decode(signature));
    }

    @Test
    void computeHmacSha256_differentPayloadsProduceDifferentSignatures() throws Exception {
        var method = WebhookService.class.getDeclaredMethod("computeHmacSha256", String.class, String.class);
        method.setAccessible(true);

        String sig1 = (String) method.invoke(webhookService, secret, "payload1");
        String sig2 = (String) method.invoke(webhookService, secret, "payload2");

        assertNotEquals(sig1, sig2);
    }

    @Test
    void computeHmacSha256_samePayloadProducesSameSignature() throws Exception {
        var method = WebhookService.class.getDeclaredMethod("computeHmacSha256", String.class, String.class);
        method.setAccessible(true);

        String sig1 = (String) method.invoke(webhookService, secret, "same payload");
        String sig2 = (String) method.invoke(webhookService, secret, "same payload");

        assertEquals(sig1, sig2);
    }

    @Test
    void computeHmacSha256_differentSecretsProduceDifferentSignatures() throws Exception {
        var method = WebhookService.class.getDeclaredMethod("computeHmacSha256", String.class, String.class);
        method.setAccessible(true);

        String sig1 = (String) method.invoke(webhookService, "secret1", "payload");
        String sig2 = (String) method.invoke(webhookService, "secret2", "payload");

        assertNotEquals(sig1, sig2);
    }

    @Test
    void enqueueDelivery_createsPendingDelivery() {
        when(webhookEndpointRepository.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));

        webhookService.enqueueDelivery(endpoint.getId(), "document.created", "{\"id\":\"123\"}");

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(webhookDeliveryRepository).save(captor.capture());

        WebhookDelivery saved = captor.getValue();
        assertEquals(endpoint.getId(), saved.getEndpoint().getId());
        assertEquals("document.created", saved.getEventType());
        assertEquals("{\"id\":\"123\"}", saved.getPayload());
        assertEquals(WebhookDelivery.Status.PENDING.name(), saved.getStatus());
        assertEquals(0, saved.getAttempts());
        assertNotNull(saved.getNextAttemptAt());
    }

    @Test
    void list_requiresApiManagePermission() {
        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));

        webhookService.list("test-tenant", authentication);

        verify(tenantAuthorizationService).requirePermission(authentication, tenant.getId(), Permission.API_MANAGE);
    }

    @Test
    void create_generatesSecret() {
        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));
        // Capture the saved entity to verify secret generation
        ArgumentCaptor<WebhookEndpoint> endpointCaptor = ArgumentCaptor.forClass(WebhookEndpoint.class);
        when(webhookEndpointRepository.save(endpointCaptor.capture())).thenAnswer(invocation -> {
            WebhookEndpoint saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID()); // Simulate JPA ID generation
            return saved;
        });

        CreateWebhookRequestDTO request = new CreateWebhookRequestDTO("https://example.com/webhook", List.of("document.created"));
        WebhookEndpointDTO result = webhookService.create("test-tenant", request, authentication);

        assertNotNull(result.id());
        assertEquals("https://example.com/webhook", result.url());
        assertEquals(List.of("document.created"), result.events());
        assertTrue(result.active());

        // Verify the saved endpoint has a secret
        WebhookEndpoint savedEndpoint = endpointCaptor.getValue();
        assertNotNull(savedEndpoint.getSecret());
        assertFalse(savedEndpoint.getSecret().isEmpty());
    }

    @Test
    void update_togglesActiveStatus() {
        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));
        when(webhookEndpointRepository.findByTenantIdAndId(tenant.getId(), endpoint.getId())).thenReturn(Optional.of(endpoint));

        WebhookEndpointDTO result = webhookService.update("test-tenant", endpoint.getId(), false, authentication);

        assertFalse(result.active());
        assertFalse(endpoint.isActive());
    }

    @Test
    void delete_removesEndpoint() {
        when(tenantRepository.findBySlug("test-tenant")).thenReturn(Optional.of(tenant));
        when(webhookEndpointRepository.findByTenantIdAndId(tenant.getId(), endpoint.getId())).thenReturn(Optional.of(endpoint));

        webhookService.delete("test-tenant", endpoint.getId(), authentication);

        verify(webhookEndpointRepository).delete(endpoint);
    }
}
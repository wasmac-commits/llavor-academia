package academy.llavor.academia.provisioning.api;

import academy.llavor.academia.provisioning.ReceiverFixture;
import academy.llavor.academia.classroom.entity.LocalClassroom;
import academy.llavor.academia.provisioning.config.*;
import academy.llavor.academia.provisioning.dto.*;
import academy.llavor.academia.provisioning.entity.ClassroomProvisioningReceipt;
import academy.llavor.academia.provisioning.security.*;
import academy.llavor.academia.provisioning.service.*;
import jakarta.persistence.PrePersist;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.RuntimeDelegate;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static academy.llavor.academia.provisioning.service.ProvisioningFailure.Code.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** JAX-RS HTTP adapter tests with a minimal mocked response delegate, no server/provider/network. */
class ProvisioningApiTest {
    private ReceiverFixture f;
    private AcademiaProvisioningConfiguration configuration;
    private ProvisioningAuthenticator authenticator;
    private ContainerRequestContext request;
    private HttpServletRequest servlet;
    private ProvisioningAuthenticationFilter filter;
    private AtomicReference<SecurityContext> security;

    @BeforeEach void prepare() {
        RuntimeDelegate delegate = mock(RuntimeDelegate.class);
        when(delegate.createResponseBuilder()).thenAnswer(call -> {
            Response.ResponseBuilder builder = mock(Response.ResponseBuilder.class, RETURNS_SELF);
            AtomicInteger status = new AtomicInteger(200); AtomicReference<Object> entity = new AtomicReference<>();
            Map<String, Object> headers = new HashMap<>();
            when(builder.status(anyInt())).thenAnswer(c -> { status.set(c.getArgument(0)); return builder; });
            when(builder.status(any(Response.Status.class))).thenAnswer(c -> { status.set(((Response.Status)c.getArgument(0)).getStatusCode()); return builder; });
            when(builder.status(any(Response.StatusType.class))).thenAnswer(c -> { status.set(((Response.StatusType)c.getArgument(0)).getStatusCode()); return builder; });
            when(builder.entity(any())).thenAnswer(c -> { entity.set(c.getArgument(0)); return builder; });
            when(builder.header(anyString(), any())).thenAnswer(c -> { headers.put(c.getArgument(0), c.getArgument(1)); return builder; });
            when(builder.build()).thenAnswer(c -> {
                Response response = mock(Response.class);
                when(response.getStatus()).thenReturn(status.get()); when(response.getStatusInfo()).thenReturn(Response.Status.fromStatusCode(status.get())); when(response.getEntity()).thenReturn(entity.get());
                when(response.getHeaderString(anyString())).thenAnswer(h -> Objects.toString(headers.get(h.getArgument(0)), null));
                return response;
            });
            return builder;
        });
        RuntimeDelegate.setInstance(delegate);
        f = new ReceiverFixture();
        configuration = mock(AcademiaProvisioningConfiguration.class);
        doReturn(f.identity).when(configuration).loadEnabled();
        authenticator = spy(new ProvisioningAuthenticator((accepted,key,token) -> accepted.contains(key) && f.token.equals(token)));
        request = mock(ContainerRequestContext.class); servlet = mock(HttpServletRequest.class);
        SecurityContext initial = mock(SecurityContext.class); when(initial.isSecure()).thenReturn(true);
        security = new AtomicReference<>(initial);
        when(request.getSecurityContext()).thenAnswer(c -> security.get());
        doAnswer(c -> { security.set(c.getArgument(0)); return null; }).when(request).setSecurityContext(any());
        var headers = new MultivaluedHashMap<String, String>();
        headers.putSingle("Authorization", "Bearer " + f.token); headers.putSingle("X-Llavor-Key-Id", "K01");
        when(request.getHeaders()).thenReturn(headers);
        UriInfo uri = mock(UriInfo.class); when(uri.getRequestUri()).thenReturn(URI.create("https://example.invalid/internal/v1/test"));
        when(request.getUriInfo()).thenReturn(uri);
        filter = new ProvisioningAuthenticationFilter(configuration, authenticator, servlet);
    }
    @AfterEach void restore() { RuntimeDelegate.setInstance(null); }
    private Response aborted() {
        var response = org.mockito.ArgumentCaptor.forClass(Response.class);
        verify(request).abortWith(response.capture()); return response.getValue();
    }

    @Test void authFilterCreatesMachineContextAndOrdinaryJsFSessionsCannotReplaceIt() {
        filter.filter(request);
        verify(request, never()).abortWith(any());
        assertTrue(security.get().getUserPrincipal() instanceof ProvisioningPrincipal);
        assertTrue(security.get().isUserInRole("LLAVOR_PROVISIONER"));
        request.getHeaders().remove("Authorization");
        clearInvocations(request); filter.filter(request);
        assertEquals(401, aborted().getStatus());
        assertNotNull(aborted().getHeaderString("WWW-Authenticate"));
    }

    @Test void disabledAndDuplicateCredentialsAreRejectedBeforeResources() {
        when(configuration.loadEnabled()).thenThrow(new ProvisioningFailure(PROVISIONING_DISABLED));
        filter.filter(request); assertEquals(503, aborted().getStatus()); verifyNoInteractions(authenticator);
        doReturn(f.identity).when(configuration).loadEnabled();
        request.getHeaders().add("Authorization", "Bearer ignored");
        clearInvocations(request); filter.filter(request);
        assertEquals(401, aborted().getStatus());
    }

    @Test void plaintextRequiresExplicitLoopbackExceptionAndAnActualLoopbackPeer() {
        SecurityContext insecure = mock(SecurityContext.class); security.set(insecure);
        filter.filter(request); assertEquals(403, aborted().getStatus());
        when(configuration.loadEnabled()).thenReturn(new AcademiaIdentity(f.academy, "ACADEMIA_001", f.portal, f.identity.verifierParameters(), true));
        when(request.getUriInfo().getRequestUri()).thenReturn(URI.create("http://localhost:8080/llavor-academia/internal/v1/test"));
        when(servlet.getRemoteAddr()).thenReturn("203.0.113.10");
        clearInvocations(request); filter.filter(request); assertEquals(403, aborted().getStatus());
        when(servlet.getRemoteAddr()).thenReturn("127.0.0.1");
        clearInvocations(request); filter.filter(request); verify(request, never()).abortWith(any());
    }

    @Test void oversizedBodiesAreBoundedEvenWithoutContentLengthAndNoCorsHeadersAreAdded() throws Exception {
        when(request.getHeaderString("Content-Length")).thenReturn("20000");
        filter.filter(request); Response response = aborted();
        assertEquals(400, response.getStatus()); assertNull(response.getHeaderString("Access-Control-Allow-Origin"));
        var body = new ProvisioningAuthenticationFilter.LimitedBody(new ByteArrayInputStream(new byte[20000]));
        assertThrows(IOException.class, body::readAllBytes);
        var skipped = new ProvisioningAuthenticationFilter.LimitedBody(new ByteArrayInputStream(new byte[20000]));
        assertThrows(IOException.class, () -> skipped.skip(20000));
        var valid = new ProvisioningAuthenticationFilter.LimitedBody(new ByteArrayInputStream(new byte[16384]));
        assertEquals(16384, valid.readAllBytes().length);
    }

    private ClassroomProvisioningResult result() {
        var classroom = new LocalClassroom(f.portal, f.classroom, f.academy, "AULA_001", "Aula");
        ReceiverFixture.lifecycle(classroom, PrePersist.class); ReceiverFixture.field(classroom, "id", 1L);
        var receipt = ClassroomProvisioningReceipt.ready(f.operation, f.command().fingerprint(), classroom);
        ReceiverFixture.lifecycle(receipt, PrePersist.class);
        return new ClassroomProvisioningResult(receipt);
    }

    @Test void endpointsReturnDetachedCommittedResultsAndRespectTheApprovedRoutesAndStatusCodes() throws Exception {
        filter.filter(request);
        var service = mock(ClassroomProvisioningService.class);
        var resource = new ClassroomProvisioningResource(service);
        ReceiverFixture.field(resource, "security", security.get());
        var result = result();
        when(service.prepare(any(), anyString(), anyString(), anyString(), any())).thenReturn(new ProvisioningReply(201, result));
        when(service.getOperation(any(), anyString(), anyString(), anyString())).thenReturn(result);
        assertEquals(201, resource.prepare(f.academy.toString(), f.classroom.toString(), f.operation.toString(), f.request()).getStatus());
        assertEquals(200, resource.operation(f.academy.toString(), f.classroom.toString(), f.operation.toString()).getStatus());
        assertEquals("/internal", ProvisioningRestApplication.class.getAnnotation(ApplicationPath.class).value());
        assertEquals("/v1/academies/{academyId}/classrooms/{classroomId}", ClassroomProvisioningResource.class.getAnnotation(Path.class).value());
        assertNotNull(ClassroomProvisioningResource.class.getMethod("classroom", String.class, String.class).getAnnotation(GET.class));
        assertNotNull(ClassroomProvisioningResource.class.getMethod("prepare", String.class, String.class, String.class, ClassroomProvisioningRequest.class).getAnnotation(PUT.class));
        assertEquals("no-store", resource.operation(f.academy.toString(), f.classroom.toString(), f.operation.toString()).getHeaderString("Cache-Control"));
        doThrow(new ProvisioningFailure(TEMPORARILY_UNAVAILABLE)).when(service).prepare(any(), anyString(), anyString(), anyString(), any());
        assertThrows(ProvisioningFailure.class, () -> resource.prepare(f.academy.toString(), f.classroom.toString(), f.operation.toString(), f.request()));
    }

    @Test void resourceRejectsHumanPrincipalAndExceptionMapperNeverEchoesInternalExceptions() {
        var service = mock(ClassroomProvisioningService.class);
        var resource = new ClassroomProvisioningResource(service);
        ReceiverFixture.field(resource, "security", security.get());
        assertThrows(ProvisioningFailure.class, () -> resource.classroom(f.academy.toString(), f.classroom.toString()));
        verifyNoInteractions(service);
        var mapper = new ProvisioningExceptionMapper();
        var response = mapper.toResponse(new IllegalStateException("PRIVATE"));
        assertEquals(503, response.getStatus());
        assertEquals("TEMPORARILY_UNAVAILABLE", ((ProvisioningError) response.getEntity()).getErrorCode());
        assertEquals(400, mapper.toResponse(new BadRequestException()).getStatus());
        for (var code : ProvisioningFailure.Code.values()) {
            var controlled = mapper.toResponse(new ProvisioningFailure(code));
            assertEquals(code.status(), controlled.getStatus());
            assertEquals(code.name(), ((ProvisioningError) controlled.getEntity()).getErrorCode());
        }
    }
    @Test void requestServicesAreCdiManagedAndOnlyTheExplicitInternalResourcesAreRegistered() {
        assertNotNull(ClassroomProvisioningResource.class.getAnnotation(jakarta.enterprise.context.ApplicationScoped.class));
        assertNotNull(ProvisioningAuthenticationFilter.class.getAnnotation(jakarta.enterprise.context.ApplicationScoped.class));
        assertEquals(Set.of(AcademyIdentityResource.class, ClassroomProvisioningResource.class, ProvisioningAuthenticationFilter.class,
            ProvisioningExceptionMapper.class, ClassroomProvisioningRequestReader.class),
            new ProvisioningRestApplication().getClasses());
    }

    private void installedVerifier() throws Exception {
        var reader = mock(academy.llavor.academia.parameter.security.AcademyVerifierParameterReader.class);
        var constructor = academy.llavor.academia.parameter.security.SensitiveParameterValue.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        when(reader.read("K01")).thenReturn(constructor.newInstance(f.verifier));
        authenticator = new ProvisioningAuthenticator(new MysqlProvisioningCredentialVerifier(
            new academy.llavor.academia.parameter.security.LocalCredentialVerifier(reader)));
        filter = new ProvisioningAuthenticationFilter(configuration, authenticator, servlet);
    }

    private AcademyIdentityResource identityResource(ProvisioningTransactions transactions) {
        var resource = new AcademyIdentityResource(new ClassroomProvisioningService(transactions, configuration));
        ReceiverFixture.field(resource, "security", security.get());
        return resource;
    }

    @Test void identityUsesInstalledVerifierAndReturnsExactlyTheFivePublicContractPropertiesWithoutClassrooms() throws Exception {
        installedVerifier();
        filter.filter(request);
        verify(request, never()).abortWith(any());
        var transactions = mock(ProvisioningTransactions.class);
        var response = identityResource(transactions).identity(f.academy.toString());
        assertEquals(200, response.getStatus());
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
        assertEquals("nosniff", response.getHeaderString("X-Content-Type-Options"));
        var identity = (AcademyIdentityResult) response.getEntity();
        assertEquals(1, identity.getContractVersion());
        assertEquals(f.portal.toString(), identity.getPortalInstanceId());
        assertEquals(f.academy.toString(), identity.getAcademyId());
        assertEquals("ACADEMIA_001", identity.getAcademyCode());
        assertEquals("K01", identity.getKeyId());
        // JavaBeans contract used by JSON-B: exact getter names/types, no entity/credential property.
        var properties = java.beans.Introspector.getBeanInfo(AcademyIdentityResult.class, Object.class).getPropertyDescriptors();
        assertEquals(Set.of("contractVersion", "portalInstanceId", "academyId", "academyCode", "keyId"),
            Arrays.stream(properties).map(java.beans.PropertyDescriptor::getName).collect(java.util.stream.Collectors.toSet()));
        for (var property : properties) {
            assertNotNull(property.getReadMethod());
            assertNull(property.getWriteMethod());
            String value = Objects.toString(property.getReadMethod().invoke(identity));
            assertFalse(value.contains(f.token)); assertFalse(value.contains(f.verifier));
        }
        assertEquals("/v1/academies/{academyId}/identity", AcademyIdentityResource.class.getAnnotation(Path.class).value());
        assertNotNull(AcademyIdentityResource.class.getMethod("identity", String.class).getAnnotation(GET.class));
        assertArrayEquals(new String[]{"application/json"}, AcademyIdentityResource.class.getAnnotation(Produces.class).value());
        verifyNoInteractions(transactions);
    }

    @Test void identityRejectsWrongCredentialUnknownKeyAndAnonymousRequestsThroughTheExistingFilter() throws Exception {
        installedVerifier();
        for (int kind = 0; kind < 3; kind++) {
            var headers = request.getHeaders();
            headers.putSingle("Authorization", "Bearer " + f.token);
            headers.putSingle("X-Llavor-Key-Id", "K01");
            if (kind == 0) headers.putSingle("Authorization", "Bearer " + new ReceiverFixture().token);
            if (kind == 1) headers.putSingle("X-Llavor-Key-Id", "K02");
            if (kind == 2) headers.remove("Authorization");
            clearInvocations(request);
            filter.filter(request);
            assertEquals(401, aborted().getStatus());
            assertEquals("UNAUTHORIZED", ((ProvisioningError) aborted().getEntity()).getErrorCode());
        }
    }

    @Test void identityRequiresMachineRoleRejectsWrongRouteAndNeverTouchesWriteTransactions() {
        var transactions = mock(ProvisioningTransactions.class);
        var anonymous = identityResource(transactions);
        assertEquals(UNAUTHORIZED, assertThrows(ProvisioningFailure.class,
            () -> anonymous.identity(f.academy.toString())).getCode());
        ReceiverFixture.field(anonymous, "security", null);
        assertEquals(UNAUTHORIZED, assertThrows(ProvisioningFailure.class,
            () -> anonymous.identity(f.academy.toString())).getCode());
        filter.filter(request);
        var resource = identityResource(transactions);
        final var authenticated = resource;
        assertEquals(ACADEMY_IDENTITY_MISMATCH, assertThrows(ProvisioningFailure.class,
            () -> authenticated.identity(UUID.randomUUID().toString())).getCode());
        assertEquals(INVALID_REQUEST, assertThrows(ProvisioningFailure.class,
            () -> authenticated.identity("invalid")).getCode());
        var noRole = mock(SecurityContext.class);
        when(noRole.getUserPrincipal()).thenReturn(f.principal);
        ReceiverFixture.field(resource, "security", noRole);
        assertEquals(UNAUTHORIZED, assertThrows(ProvisioningFailure.class,
            () -> authenticated.identity(f.academy.toString())).getCode());
        verifyNoInteractions(transactions);
    }

    @Test void identityDisabledReceiverAndTransportPolicyRemainFailClosed() throws Exception {
        installedVerifier();
        when(configuration.loadEnabled()).thenThrow(new ProvisioningFailure(PROVISIONING_DISABLED));
        filter.filter(request);
        assertEquals(503, aborted().getStatus());
        assertEquals("PROVISIONING_DISABLED", ((ProvisioningError) aborted().getEntity()).getErrorCode());
        doReturn(f.identity).when(configuration).loadEnabled();
        security.set(mock(SecurityContext.class));
        clearInvocations(request);
        filter.filter(request);
        assertEquals(403, aborted().getStatus());
    }

    @Test void identityFailureDiagnosticsAndResponsesDoNotExposeSyntheticSecrets() throws Exception {
        installedVerifier();
        var messages = new ArrayList<String>();
        var logger = java.util.logging.Logger.getLogger("");
        var handler = new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record) {
                messages.add(Objects.toString(record.getMessage(), ""));
                if (record.getThrown() != null) messages.add(record.getThrown().toString());
            }
            public void flush() {}
            public void close() {}
        };
        logger.addHandler(handler);
        try {
            request.getHeaders().putSingle("Authorization", "Bearer " + new ReceiverFixture().token);
            filter.filter(request);
            var error = (ProvisioningError) aborted().getEntity();
            assertEquals("UNAUTHORIZED", error.getErrorCode());
            assertFalse(error.getErrorCode().contains(f.token));
            assertFalse(error.getErrorCode().contains(f.verifier));
            var mapper = new ProvisioningExceptionMapper();
            var safe = (ProvisioningError) mapper.toResponse(new IllegalStateException(f.token + f.verifier)).getEntity();
            assertEquals("TEMPORARILY_UNAVAILABLE", safe.getErrorCode());
            assertTrue(messages.stream().noneMatch(message -> message.contains(f.token) || message.contains(f.verifier)));
        } finally { logger.removeHandler(handler); }
    }
}

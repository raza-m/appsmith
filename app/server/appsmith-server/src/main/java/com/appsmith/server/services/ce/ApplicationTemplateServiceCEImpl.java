package com.appsmith.server.services.ce;

import com.appsmith.server.configurations.CloudServicesConfig;
import com.appsmith.server.constants.AnalyticsEvents;
import com.appsmith.server.converters.GsonISOStringToInstantConverter;
import com.appsmith.server.domains.Application;
import com.appsmith.server.domains.ApplicationJson;
import com.appsmith.server.dtos.ApplicationTemplate;
import com.appsmith.server.exceptions.AppsmithError;
import com.appsmith.server.exceptions.AppsmithException;
import com.appsmith.server.services.AnalyticsService;
import com.appsmith.server.solutions.ImportExportApplicationService;
import com.appsmith.server.solutions.ReleaseNotesService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.time.Instant;

@Service
public class ApplicationTemplateServiceCEImpl implements ApplicationTemplateServiceCE {
    private final CloudServicesConfig cloudServicesConfig;
    private final ReleaseNotesService releaseNotesService;
    private final ImportExportApplicationService importExportApplicationService;
    private final AnalyticsService analyticsService;

    public ApplicationTemplateServiceCEImpl(CloudServicesConfig cloudServicesConfig,
                                            ReleaseNotesService releaseNotesService,
                                            ImportExportApplicationService importExportApplicationService,
                                            AnalyticsService analyticsService) {
        this.cloudServicesConfig = cloudServicesConfig;
        this.releaseNotesService = releaseNotesService;
        this.importExportApplicationService = importExportApplicationService;
        this.analyticsService = analyticsService;
    }

    @Override
    public Flux<ApplicationTemplate> getSimilarTemplates(String templateId) {
        final String apiUrl = String.format("%s/api/v1/app-templates/%s/similar?version=%s",
                cloudServicesConfig.getBaseUrl(), templateId, releaseNotesService.getReleasedVersion()
        );
        return WebClient
                .create(apiUrl)
                .get()
                .exchangeToFlux(clientResponse -> {
                    if (clientResponse.statusCode().equals(HttpStatus.OK)) {
                        return clientResponse.bodyToFlux(ApplicationTemplate.class);
                    } else if (clientResponse.statusCode().isError()) {
                        return Flux.error(new AppsmithException(AppsmithError.CLOUD_SERVICES_ERROR, clientResponse.statusCode()));
                    } else {
                        return clientResponse.createException().flatMapMany(Flux::error);
                    }
                });
    }

    @Override
    public Flux<ApplicationTemplate> getActiveTemplates() {
        final String baseUrl = cloudServicesConfig.getBaseUrl();

        return WebClient
                .create(baseUrl + "/api/v1/app-templates?version=" + releaseNotesService.getReleasedVersion())
                .get()
                .exchangeToFlux(clientResponse -> {
                    if (clientResponse.statusCode().equals(HttpStatus.OK)) {
                        return clientResponse.bodyToFlux(ApplicationTemplate.class);
                    } else if (clientResponse.statusCode().isError()) {
                        return Flux.error(new AppsmithException(AppsmithError.CLOUD_SERVICES_ERROR, clientResponse.statusCode()));
                    } else {
                        return clientResponse.createException().flatMapMany(Flux::error);
                    }
                });
    }

    @Override
    public Mono<ApplicationTemplate> getTemplateDetails(String templateId) {
        final String baseUrl = cloudServicesConfig.getBaseUrl();

        return WebClient
                .create(baseUrl + "/api/v1/app-templates/" + templateId)
                .get()
                .exchangeToMono(clientResponse -> {
                    if (clientResponse.statusCode().equals(HttpStatus.OK)) {
                        return clientResponse.bodyToMono(ApplicationTemplate.class);
                    } else if (clientResponse.statusCode().isError()) {
                        return Mono.error(new AppsmithException(AppsmithError.CLOUD_SERVICES_ERROR, clientResponse.statusCode()));
                    } else {
                        return clientResponse.createException().flatMap(Mono::error);
                    }
                });
    }

    private Mono<ApplicationJson> getApplicationJsonFromTemplate(String templateId) {
        return getTemplateDetails(templateId).flatMap(applicationTemplate -> {
            final String templateUrl = applicationTemplate.getAppDataUrl();
            /* using a custom url builder factory because default builder always encodes URL.
             It's expected that the appDataUrl is already encoded, so we don't need to encode that again.
             Encoding an encoded URL will not work and end up resulting a 404 error */
            WebClient webClient = WebClient.builder()
                    .uriBuilderFactory(new NoEncodingUriBuilderFactory(templateUrl))
                    .build();

            return webClient
                    .get()
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(jsonString -> {
                        Gson gson = new GsonBuilder()
                                .registerTypeAdapter(Instant.class, new GsonISOStringToInstantConverter())
                                .create();
                        Type fileType = new TypeToken<ApplicationJson>() {
                        }.getType();
                        ApplicationJson jsonFile = gson.fromJson(jsonString, fileType);
                        return jsonFile;
                    });
        }).switchIfEmpty(Mono.error(new AppsmithException(AppsmithError.NO_RESOURCE_FOUND, "template", templateId)));
    }

    @Override
    public Mono<Application> importApplicationFromTemplate(String templateId, String organizationId) {
        return getApplicationJsonFromTemplate(templateId).flatMap(applicationJson ->
            importExportApplicationService.importApplicationInOrganization(organizationId, applicationJson)
        ).flatMap(application -> {
            ApplicationTemplate applicationTemplate = new ApplicationTemplate();
            applicationTemplate.setId(templateId);
            return analyticsService.sendObjectEvent(AnalyticsEvents.FORK, applicationTemplate, null)
                    .thenReturn(application);
        });
    }

    public static class NoEncodingUriBuilderFactory extends DefaultUriBuilderFactory {
        public NoEncodingUriBuilderFactory(String baseUriTemplate) {
            super(UriComponentsBuilder.fromHttpUrl(baseUriTemplate));
            super.setEncodingMode(EncodingMode.NONE);
        }
    }
}

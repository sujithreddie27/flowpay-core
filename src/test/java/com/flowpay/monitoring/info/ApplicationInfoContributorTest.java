package com.flowpay.monitoring.info;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApplicationInfoContributor Tests")
class ApplicationInfoContributorTest {

    @Test
    @DisplayName("should contribute application details without build properties")
    void shouldContributeWithoutBuildProperties() {
        ApplicationInfoContributor contributor = new ApplicationInfoContributor();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        ReflectionTestUtils.setField(contributor, "environment", env);
        ReflectionTestUtils.setField(contributor, "buildProperties", null);
        ReflectionTestUtils.setField(contributor, "gitProperties", null);

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        @SuppressWarnings("unchecked")
        Map<String, Object> appDetails = (Map<String, Object>) info.getDetails().get("application");
        assertThat(appDetails).containsEntry("name", "FlowPay Core");
        assertThat(appDetails).containsEntry("description", "Real-Time Payment Processing Platform");

        @SuppressWarnings("unchecked")
        Map<String, Object> buildDetails = (Map<String, Object>) info.getDetails().get("build");
        assertThat(buildDetails).containsEntry("version", "1.0.0-SNAPSHOT");

        @SuppressWarnings("unchecked")
        Map<String, Object> gitDetails = (Map<String, Object>) info.getDetails().get("git");
        assertThat(gitDetails).containsKey("note");

        @SuppressWarnings("unchecked")
        Map<String, Object> runtimeDetails = (Map<String, Object>) info.getDetails().get("runtime");
        assertThat(runtimeDetails).containsKey("java.version");
        assertThat(runtimeDetails).containsKey("availableProcessors");
        assertThat(runtimeDetails).containsKey("maxMemoryMB");
    }

    @Test
    @DisplayName("should include active profiles")
    void shouldIncludeActiveProfiles() {
        ApplicationInfoContributor contributor = new ApplicationInfoContributor();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "aws");
        ReflectionTestUtils.setField(contributor, "environment", env);
        ReflectionTestUtils.setField(contributor, "buildProperties", null);
        ReflectionTestUtils.setField(contributor, "gitProperties", null);

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        @SuppressWarnings("unchecked")
        Map<String, Object> appDetails = (Map<String, Object>) info.getDetails().get("application");
        String[] profiles = (String[]) appDetails.get("profiles");
        assertThat(profiles).containsExactly("prod", "aws");
    }
}

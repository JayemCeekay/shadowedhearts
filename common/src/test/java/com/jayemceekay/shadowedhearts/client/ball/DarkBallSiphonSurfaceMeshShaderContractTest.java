package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the raster siphon to the material and fixed-function depth contract
 * shared with the body mesh.
 */
class DarkBallSiphonSurfaceMeshShaderContractTest {
    private static final String RESOURCE_ROOT =
            "assets/shadowedhearts/shaders/core/darkball/";
    private static final String SHADER_NAME =
            "dark_ball_siphon_surface_mesh";

    @Test
    void dedicatedSiphonShaderPreservesTheMaterialAndDepthContract()
            throws IOException {
        ClassLoader loader =
                DarkBallSiphonSurfaceMeshShaderContractTest.class
                        .getClassLoader();
        String json = resource(loader,
                RESOURCE_ROOT + SHADER_NAME + ".json");
        String vertex = resource(loader,
                RESOURCE_ROOT + SHADER_NAME + ".vsh");
        String fragment = resource(loader,
                RESOURCE_ROOT + SHADER_NAME + ".fsh");

        assertTrue(json.contains("\"vertex\": "
                + "\"shadowedhearts:darkball/" + SHADER_NAME + "\""));
        assertTrue(json.contains("\"fragment\": "
                + "\"shadowedhearts:darkball/" + SHADER_NAME + "\""));
        assertTrue(json.contains("\"Position\""));
        assertFalse(json.contains("\"SceneDepthSampler\""),
                "the writable depth attachment must not be sampled by the "
                        + "siphon shader");
        for (String uniform : new String[]{
                "ModelViewMat",
                "ProjMat",
                "VolumeRootCameraRelative",
                "EffectFade"
        }) {
            assertTrue(json.contains("\"name\": \"" + uniform + "\""),
                    "missing siphon mesh uniform " + uniform);
        }
        assertFalse(json.contains("\"name\": \"CameraPos\""));
        assertFalse(json.contains("\"name\": \"VolumeRoot\""));

        assertTrue(vertex.contains("#version 150"));
        assertTrue(vertex.contains("in vec3 Position"));
        assertTrue(vertex.contains(
                "uniform vec3 VolumeRootCameraRelative;"));
        assertTrue(vertex.contains(
                "out vec3 cameraRelativePosition;"));
        assertFalse(vertex.contains("worldPosition - CameraPos"));
        assertFalse(vertex.contains("length(cameraRelative"),
                "vertex-length interpolation is not a fragment distance");
        assertTrue(vertex.contains("siphonWindow"));
        assertTrue(vertex.contains("gl_Position"));

        assertTrue(fragment.contains("#version 150"));
        assertFalse(fragment.contains("SceneDepthSampler"));
        assertTrue(fragment.contains(
                "in vec3 cameraRelativePosition;"));
        assertTrue(fragment.contains(
                "length(cameraRelativePosition)"),
                "surface distance must be evaluated per fragment");
        assertTrue(fragment.contains(
                "const float SIPHON_COVERAGE_DEPTH_CUTOFF = 0.08;"));
        assertTrue(fragment.contains(
                "siphonWindow < SIPHON_COVERAGE_DEPTH_CUTOFF"),
                "sub-composite siphon coverage must not keep writing "
                        + "invisible color and depth");
        String compact = fragment.replaceAll("\\s+", "");
        assertTrue(compact.contains(
                        "fragColor=vec4(0.0,siphonWindow,"
                                + "2.0+siphonLocalRadius,"
                                + "surfaceDistance);"),
                "siphon output must remain R=0, G=window, "
                        + "B=2+radius, A=surface distance");
    }

    @Test
    void rendererSuppliesDoubleDerivedCameraRelativeRoot()
            throws IOException {
        String source = Files.readString(Path.of("src/main/java")
                        .resolve("com/jayemceekay/shadowedhearts/client/ball/"
                                + "DarkBallSiphonSurfaceMeshRenderer.java"),
                StandardCharsets.UTF_8);
        String compact = source.replaceAll("\\s+", "");

        assertTrue(compact.contains(
                "volume.root().subtract(camera.getPosition())"),
                "camera-relative root subtraction must occur while Vec3 "
                        + "components are still doubles");
        assertTrue(compact.contains(
                "setVec3(shader,\"VolumeRootCameraRelative\","
                        + "cameraRelativeRoot);"));
        assertFalse(compact.contains(
                "setVec3(shader,\"CameraPos\""));
    }

    private static String resource(ClassLoader loader, String path)
            throws IOException {
        try (InputStream stream = loader.getResourceAsStream(path)) {
            assertNotNull(stream,
                    "missing siphon mesh shader resource " + path);
            return new String(stream.readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}

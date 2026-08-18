package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallIrisManualVertexFormatContractTest {

    @Test
    void manualEntityFormatIsA36ByteCloneWithDistinctIdentity() {
        VertexFormat format = ModShaders.DARK_BALL_MANUAL_ENTITY_FORMAT;

        assertNotSame(DefaultVertexFormat.NEW_ENTITY, format,
                "Iris substitutes its extended 54-byte layout only for the "
                        + "shared NEW_ENTITY format identity");
        assertEquals(36, format.getVertexSize());
        assertEquals(DefaultVertexFormat.NEW_ENTITY.getVertexSize(),
                format.getVertexSize(),
                "the private format must remain a structural clone of the "
                        + "vanilla entity records Dark Ball packs manually");
        assertEquals(
                List.of("Position", "Color", "UV0", "UV1", "UV2",
                        "Normal"),
                format.getElementAttributeNames());
        assertEquals(0, format.getOffset(VertexFormatElement.POSITION));
        assertEquals(12, format.getOffset(VertexFormatElement.COLOR));
        assertEquals(16, format.getOffset(VertexFormatElement.UV0));
        assertEquals(24, format.getOffset(VertexFormatElement.UV1));
        assertEquals(28, format.getOffset(VertexFormatElement.UV2));
        assertEquals(32, format.getOffset(VertexFormatElement.NORMAL));
    }

    @Test
    void everyManuallyPackedDarkBallOwnerUsesThePrivateFormat()
            throws IOException {
        String surface = javaSource(
                "client/ball/DarkBallSurfaceSplatRenderer.java");
        String siphon = javaSource(
                "client/ball/DarkBallSiphonSurfaceMeshRenderer.java");
        String fabric = loaderSource(
                "fabric",
                "client/fabric/ModShadersPlatformImpl.java");
        String neoforge = loaderSource(
                "neoforge",
                "client/neoforge/ModShadersPlatformImpl.java");

        assertTrue(surface.contains(
                "ModShaders.DARK_BALL_MANUAL_ENTITY_FORMAT"));
        assertTrue(siphon.contains(
                "ModShaders.DARK_BALL_MANUAL_ENTITY_FORMAT"));
        assertFalse(surface.contains("DefaultVertexFormat.NEW_ENTITY"));
        assertFalse(siphon.contains("DefaultVertexFormat.NEW_ENTITY"));

        assertShaderUsesPrivateFormat(fabric,
                "darkball/dark_ball_surface_splat");
        assertShaderUsesPrivateFormat(fabric,
                "darkball/dark_ball_siphon_surface_mesh");
        assertShaderUsesPrivateFormat(neoforge,
                "darkball/dark_ball_surface_splat");
        assertShaderUsesPrivateFormat(neoforge,
                "darkball/dark_ball_siphon_surface_mesh");
    }

    @Test
    void surfaceSubmissionFailsClosedWhenPhysicalStrideDrifts()
            throws IOException {
        String surface = javaSource(
                "client/ball/DarkBallSurfaceSplatRenderer.java");

        assertTrue(surface.contains("validateBoundVertexLayout("));
        assertEquals(3, countOccurrences(
                surface, "if (!validateBoundVertexLayout("),
                "production, ID/overlap diagnostics, and front-depth must "
                        + "all validate the live VAO before drawing");
        assertEquals(3, countOccurrences(
                surface, "vertexBuffer.drawWithShader("));
        assertTrue(surface.contains(
                "GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE"));
        assertTrue(surface.contains("FORMAT.getVertexSize()"));
        assertTrue(surface.contains(
                "validatedVertexLayoutUploadGeneration"));
        assertTrue(surface.contains("validatedVertexLayoutShader"));
        assertTrue(surface.contains("retaining the captured "));
        assertTrue(surface.contains("exact-mask presentation"));
        assertTrue(surface.contains("vertexBuffer.getFormat() == FORMAT"));
        assertTrue(surface.contains("if (program <= 0)"));
        assertTrue(surface.contains(
                "GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED"));
        assertTrue(surface.contains(
                "GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING"));
        assertTrue(surface.contains("attributeBuffer <= 0"));
        assertTrue(surface.contains("attributeBuffer != vertexBuffer"));
    }

    private static void assertShaderUsesPrivateFormat(
            String source,
            String shaderPath) {
        int shader = source.indexOf(shaderPath);
        assertTrue(shader >= 0, shaderPath + " registration is missing");
        int privateFormat = source.indexOf(
                "ModShaders.DARK_BALL_MANUAL_ENTITY_FORMAT", shader);
        assertTrue(privateFormat > shader
                        && privateFormat - shader < 300,
                shaderPath + " must register with the private format");
    }

    private static String javaSource(String relative) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/")
                        .resolve(relative),
                StandardCharsets.UTF_8);
    }

    private static String loaderSource(String loader, String relative)
            throws IOException {
        return Files.readString(
                Path.of("../" + loader
                                + "/src/main/java/com/jayemceekay/"
                                + "shadowedhearts/")
                        .resolve(relative),
                StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }
}

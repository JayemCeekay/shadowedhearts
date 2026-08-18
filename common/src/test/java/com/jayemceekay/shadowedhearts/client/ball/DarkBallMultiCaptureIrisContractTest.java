package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallMultiCaptureIrisContractTest {

    @Test
    void replacementReadinessAndRenderQueueHaveNoSingletonGate()
            throws IOException {
        String captureSource = javaSource(
                "client/ball/DarkBallCaptureVfx.java");
        String readiness = sourceBetween(
                captureSource,
                "public static boolean isReplacementReady(int ballId)",
                "public static java.util.Collection<DarkBallCaptureVfx>"
                        + " getActiveInstances()");
        String denseCaptureSource = dense(captureSource);

        assertFalse(denseCaptureSource.contains("ACTIVE.size()==1"));
        assertFalse(denseCaptureSource.contains("ACTIVE.size()!=1"));
        assertFalse(readiness.contains("ACTIVE.size"),
                "one active capture must not be required for replacement"
                        + " readiness");
        assertTrue(readiness.contains(
                "vfx.snapshotApplied && vfx.directReplacementReady"));
    }

    @Test
    void captureInstallRetiresOnlySameBallOrSamePokemonOwnership()
            throws IOException {
        String captureSource = javaSource(
                "client/ball/DarkBallCaptureVfx.java");
        String startMethod = sourceBetween(
                captureSource,
                "public static void start(EmptyPokeBallEntity ball,"
                        + " PokemonEntity pokemon)",
                "public static void startAtHit(EmptyPokeBallEntity ball,"
                        + " PokemonEntity pokemon)");
        String startAtHitMethod = sourceBetween(
                captureSource,
                "public static void startAtHit(EmptyPokeBallEntity ball,"
                        + " PokemonEntity pokemon)",
                "private static synchronized DarkBallCaptureVfx"
                        + " installCapture(");
        String installer = sourceBetween(
                captureSource,
                "private static synchronized DarkBallCaptureVfx"
                        + " installCapture(",
                "public static void stop(int ballId)");
        String denseInstaller = dense(installer);

        assertEquals(1, countOccurrences(startMethod, "installCapture("));
        assertEquals(1, countOccurrences(
                startAtHitMethod, "installCapture("),
                "start and startAtHit must share one atomic ownership path");
        assertTrue(denseInstaller.contains("ACTIVE.get(ballId)"));
        assertTrue(denseInstaller.contains(".pokemonId==pokemonId"),
                "an exact same-ball/same-Pokemon install must reuse the"
                        + " existing capture instance");
        int sameOwner = denseInstaller.indexOf(".pokemonId==pokemonId");
        int retirementLoop = denseInstaller.indexOf("for(", sameOwner);
        assertTrue(sameOwner >= 0
                        && retirementLoop > sameOwner
                        && denseInstaller.substring(sameOwner, retirementLoop)
                        .contains("return"),
                "the exact owner must be returned before conflicting entries"
                        + " are retired");

        boolean checksSameBall =
                denseInstaller.contains(".ballId==ballId")
                        || denseInstaller.contains("getKey()==ballId")
                        || denseInstaller.contains(
                        "getKey().intValue()==ballId");
        assertTrue(checksSameBall,
                "a reused ball id must retire its previous capture owner");
        assertTrue(denseInstaller.contains(".pokemonId==pokemonId"),
                "a new ball targeting the same Pokemon must retire the old"
                        + " capture before snapshot ownership can overlap");
        assertTrue(installer.contains(".destroy()"),
                "retired captures must release surfel and snapshot resources");
        assertTrue(denseInstaller.contains(
                "newDarkBallCaptureVfx(ball,pokemon)"));
        assertTrue(denseInstaller.contains("ACTIVE.put(ballId,"));
        assertFalse(installer.contains("ACTIVE.clear()"),
                "captures of different Pokemon must remain simultaneously"
                        + " active");
    }

    @Test
    void renderQueueIsImmutableFarToNearAndDeterministicallyTied()
            throws IOException {
        String captureSource = javaSource(
                "client/ball/DarkBallCaptureVfx.java");
        String ordering = sourceBetween(
                captureSource,
                "static List<DarkBallCaptureVfx> orderedActiveCaptures("
                        + "Camera camera)",
                "private double cameraDepth(");
        String denseOrdering = dense(ordering);

        assertTrue(denseOrdering.contains(
                "cameraDepth(cameraPosition,cameraForward)).reversed()"),
                "camera-space depth must be reversed so farther captures"
                        + " composite first");
        int ballTieBreaker = ordering.indexOf(
                ".thenComparingInt(vfx -> vfx.ballId)");
        int generationTieBreaker = ordering.indexOf(
                ".thenComparingLong(vfx -> vfx.captureGeneration)");
        assertTrue(ballTieBreaker >= 0);
        assertTrue(generationTieBreaker > ballTieBreaker,
                "ball id then capture generation must provide stable ties");
        assertEquals(2, countOccurrences(ordering, "List.copyOf(ordered)"),
                "both the fast path and sorted path must be immutable");
    }

    @Test
    void eachCaptureRunsOneCompleteIsolatedFboTransaction()
            throws IOException {
        String emitterSource = javaSource("client/ball/BallEmitters.java");
        String transactionMethod = sourceBetween(
                emitterSource,
                "private static DarkBallPreviewResult"
                        + " renderDarkBallComposites(",
                "private record DarkBallPreviewResult(");
        String loop = sourceBetween(
                transactionMethod,
                "for (int index = 0; index < captures.size(); index++)",
                "return new DarkBallPreviewResult(");

        assertTrue(transactionMethod.contains(
                "DarkBallCaptureVfx.orderedActiveCaptures(camera)"));
        assertTrue(transactionMethod.indexOf(
                "DarkBallCaptureVfx.beginDirectCompositeFrame()")
                < transactionMethod.indexOf("for (int index = 0;"),
                "all readiness flags must clear before the serial queue");

        String[] transactionCalls = {
                "DarkBallCaptureVfx.prepareDensityPass(",
                "DarkBallDensityFBO.beginDensityPass(",
                "DarkBallCaptureVfx.renderDensityPass(",
                "DarkBallDensityFBO.endDensityPass()",
                "DarkBallDensityFBO.composite()"
        };
        int previous = -1;
        for (String call : transactionCalls) {
            assertEquals(1, countOccurrences(loop, call),
                    call + " must occur exactly once in the per-capture loop");
            int position = loop.indexOf(call);
            assertTrue(position > previous,
                    call + " is outside the complete transaction order");
            previous = position;
        }
        assertEquals(1, countOccurrences(
                        loop,
                        "DarkBallCaptureVfx.finishDirectCompositeCapture("),
                "the normal path must publish only its own capture result");
        assertEquals(2, countOccurrences(
                        loop, "return abortDarkBallCapture(capture);"),
                "both unknown-state failure paths must abort through the"
                        + " shared fail-closed publisher");

        assertTrue(dense(loop).contains(
                "DarkBallDensityFBO.beginDensityPass(capture,true,true)"),
                "every capture must clear shared scratch and copy scene depth");
        int finallyBlock = loop.indexOf("finally {");
        int endPass = loop.indexOf("DarkBallDensityFBO.endDensityPass()");
        int composite = loop.indexOf("DarkBallDensityFBO.composite()");
        assertTrue(finallyBlock >= 0
                        && endPass > finallyBlock
                        && composite > endPass,
                "the pass must restore caller state before compositing");
    }

    @Test
    void successfulCompositePublishesReadinessOnlyToItsCapture()
            throws IOException {
        String captureSource = javaSource(
                "client/ball/DarkBallCaptureVfx.java");
        String publication = sourceBetween(
                captureSource,
                "static void finishDirectCompositeCapture(",
                "public static DarkBallCaptureVfx getByPokemonId(");

        assertTrue(publication.contains(
                "ACTIVE.get(vfx.ballId) == vfx"),
                "stale or replaced captures must not receive readiness");
        assertTrue(publication.contains(
                "vfx.directReplacementReady = compositeRendered"));
        assertFalse(publication.contains("for ("),
                "one capture's result must not be broadcast to the ACTIVE map");
    }

    @Test
    void reducedCompositeHysteresisIsPerCaptureAndTargetStaysResident()
            throws IOException {
        String captureSource = javaSource(
                "client/ball/DarkBallCaptureVfx.java");
        String densityFboSource = javaSource(
                "client/ball/DarkBallDensityFBO.java");
        String pipelineSource = javaSource(
                "client/render/DensityFboPipeline.java");
        String densityBegin = sourceBetween(
                densityFboSource,
                "static boolean beginDensityPass(DarkBallCaptureVfx capture,",
                "public static void endDensityPass()");
        String reducedToggle = sourceBetween(
                pipelineSource,
                "public void setReducedCompositeEnabled(boolean enabled)",
                "public void disableReducedCompositeAfterFailure()");
        String ensureTargets = sourceBetween(
                pipelineSource,
                "private boolean ensureTargets(int sourceWidth,"
                        + " int sourceHeight)",
                "private static TextureTarget createFloatColorTarget(");

        assertTrue(captureSource.contains(
                "private boolean mediumReducedCompositeLatched;"),
                "MEDIUM hysteresis must be owned by each capture instance");
        assertFalse(captureSource.contains(
                "private static boolean mediumReducedCompositeLatched;"));
        assertTrue(densityBegin.contains(
                "capture.isMediumReducedCompositeLatched()"));
        assertTrue(densityBegin.contains(
                "capture.setMediumReducedCompositeLatched("));
        assertFalse(densityFboSource.contains(
                "static boolean mediumReducedCompositeLatched"),
                "shared FBO policy must not leak hysteresis between captures");

        assertFalse(reducedToggle.contains("lastWidth = -1"),
                "disabling a per-capture reduced pass must not invalidate"
                        + " the shared target suite");
        assertFalse(reducedToggle.contains("destroyBuffers()"),
                "an already allocated compact target should remain resident"
                        + " for the next eligible capture");
        assertTrue(dense(ensureTargets).contains(
                "(reducedCompositeEnabled&&(reducedCompositeTarget==null"
                        + "||reducedCompositeWidth!=reducedWidth"
                        + "||reducedCompositeHeight!=reducedHeight))"),
                "the optional target should require allocation only when the"
                        + " current capture enables reduced compositing");
        assertTrue(dense(ensureTargets).contains(
                "reducedCompositeTarget=reducedCompositeEnabled?"
                        + "createValidatedCompactColorTarget("),
                "resize/recreation should allocate the compact target only"
                        + " for an eligible transaction");
    }

    @Test
    void irisShadowPassCannotStartOrFinishSnapshotCapture()
            throws IOException {
        String captureSource = javaSource(
                "client/ball/DarkBallCaptureVfx.java");
        String wantsSnapshot = sourceBetween(
                captureSource,
                "public static boolean wantsSnapshotCapture(",
                "public static MultiBufferSource wrapSnapshotBuffer(");
        String wrapSnapshot = sourceBetween(
                captureSource,
                "public static MultiBufferSource wrapSnapshotBuffer(",
                "public static void tickAll(");
        String beginModelSnapshot = sourceBetween(
                captureSource,
                "public static void beginModelSnapshotCapture(",
                "private static List<CapturedBoneNode>"
                        + " capturePosedBoneHierarchy(");
        String finishTexturedSnapshot = sourceBetween(
                captureSource,
                "public static void finishTexturedSnapshotCapture(",
                "private static boolean isIrisShadowRenderActive()");

        assertTrue(wantsSnapshot.indexOf("isIrisShadowRenderActive()")
                        < wantsSnapshot.indexOf("getByPokemonId("),
                "the renderer mixin must decline capture before consulting"
                        + " mutable capture state in an Iris shadow pass");
        assertTrue(wantsSnapshot.contains("return false;"));
        assertTrue(wrapSnapshot.indexOf("isIrisShadowRenderActive()")
                        < wrapSnapshot.indexOf("getByPokemonId("));
        assertTrue(wrapSnapshot.contains("return delegate;"),
                "shadow rendering must retain Iris' original buffer source");
        assertTrue(beginModelSnapshot.indexOf("isShadowRenderActive()")
                        < beginModelSnapshot.indexOf("getByPokemonId("),
                "bone and geometry capture must not begin in a shadow pass");
        assertTrue(finishTexturedSnapshot.indexOf(
                        "isIrisShadowRenderActive()")
                        < finishTexturedSnapshot.indexOf(
                        "activeTexturedSnapshotCapture"),
                "a shadow callback must not finalize or mutate the main-pass"
                        + " textured snapshot");
        assertTrue(finishTexturedSnapshot.contains("return;"));
    }

    @Test
    void failedFramebufferRestoreAbortsRemainingCaptureQueue()
            throws IOException {
        String emitterSource = javaSource("client/ball/BallEmitters.java");
        String transactionMethod = sourceBetween(
                emitterSource,
                "private static DarkBallPreviewResult"
                        + " renderDarkBallComposites(",
                "private record DarkBallPreviewResult(");
        String restoreFailureCatch = sourceBetween(
                transactionMethod,
                "DarkBallDensityFBO.endDensityPass();",
                "            if (stateRestoreFailed) {");
        String abort = sourceBetween(
                transactionMethod,
                "if (stateRestoreFailed) {",
                "            if (passReady) {");

        assertTrue(restoreFailureCatch.contains(
                "stateRestoreFailed = true;"));
        assertTrue(abort.contains(
                "return abortDarkBallCapture(capture);"),
                "unknown restored GL state must abort all later captures in"
                        + " the frame");
        assertFalse(abort.contains("DarkBallDensityFBO.composite()"));

        String abortHelper = sourceBetween(
                emitterSource,
                "private static DarkBallPreviewResult"
                        + " abortDarkBallCapture(",
                "private record DarkBallPreviewResult(");
        assertTrue(dense(abortHelper).contains(
                "finishDirectCompositeCapture(capture,false)"),
                "the failed capture must publish a fail-closed readiness"
                        + " result");
        assertTrue(abortHelper.contains(
                "return DarkBallPreviewResult.EMPTY;"));
    }

    @Test
    void compositeRuntimeFailurePublishesFalseAndAbortsRemainingQueue()
            throws IOException {
        String emitterSource = javaSource("client/ball/BallEmitters.java");
        String transactionMethod = sourceBetween(
                emitterSource,
                "private static DarkBallPreviewResult"
                        + " renderDarkBallComposites(",
                "private record DarkBallPreviewResult(");
        int compositeCall = transactionMethod.indexOf(
                "DarkBallDensityFBO.composite()");
        int compositeCatch = transactionMethod.indexOf(
                "catch (RuntimeException failure)", compositeCall);
        int normalPublication = transactionMethod.indexOf(
                "DarkBallCaptureVfx.finishDirectCompositeCapture(\n"
                        + "                    capture, compositeRendered)",
                compositeCatch);

        assertTrue(compositeCall >= 0);
        assertTrue(compositeCatch > compositeCall,
                "RuntimeException from the composite must be caught at the"
                        + " per-capture boundary");
        assertTrue(normalPublication > compositeCatch);
        String failurePath = transactionMethod.substring(
                compositeCatch, normalPublication);
        assertTrue(failurePath.contains("return "),
                "a failed composite must not fall through to a later capture"
                        + " transaction");

        if (failurePath.contains("abortDarkBallCapture(capture)")) {
            String abortHelper = sourceBetween(
                    emitterSource,
                    "private static DarkBallPreviewResult"
                            + " abortDarkBallCapture(",
                    "private record DarkBallPreviewResult(");
            assertTrue(dense(abortHelper).contains(
                    "finishDirectCompositeCapture(capture,false)"));
            assertTrue(abortHelper.contains(
                    "return DarkBallPreviewResult.EMPTY;"));
        } else {
            assertTrue(dense(failurePath).contains(
                    "finishDirectCompositeCapture(capture,false)"));
            assertTrue(failurePath.contains(
                    "return DarkBallPreviewResult.EMPTY;"));
        }
    }

    @Test
    void loaderAndIrisPathsAreMutuallyExclusiveAndShadowSafe()
            throws IOException {
        String emitterSource = javaSource("client/ball/BallEmitters.java");
        String loaderPath = sourceBetween(
                emitterSource,
                "public static void onRenderFBO(",
                "public static void renderDarkBallIris()");
        String irisEntry = sourceBetween(
                emitterSource,
                "public static void renderDarkBallIris()",
                "private static DarkBallPreviewResult"
                        + " renderDarkBallComposites(");
        String denseLoaderPath = dense(loaderPath);

        assertTrue(denseLoaderPath.contains(
                "if(!irisShaderPackActive){"
                        + "DarkBallPreviewResultpreviewResult="
                        + "renderDarkBallComposites(camera,partialTicks)"),
                "the normal loader hook must not submit Dark Ball while Iris"
                        + " owns the world target");
        assertEquals(1, countOccurrences(
                loaderPath, "renderDarkBallComposites("));
        assertTrue(irisEntry.contains(
                "!ShadowPokemonAuraSystem.isIrisShaderPackActive()"));
        assertTrue(irisEntry.contains(
                "AuraPulseRenderer.IRIS_HANDLER == null"));
        assertTrue(irisEntry.contains(
                "AuraPulseRenderer.IRIS_HANDLER.isShadowRenderActive()"),
                "Iris shadow-map renders must never receive Dark Ball"
                        + " composites");
        assertTrue(irisEntry.indexOf("isShadowRenderActive()")
                < irisEntry.indexOf("renderDarkBallComposites("));
    }

    @Test
    void irisEntryUsesOneValidatedTransformAndRestoresAmbientViewport()
            throws IOException {
        String emitterSource = javaSource("client/ball/BallEmitters.java");
        String irisEntry = sourceBetween(
                emitterSource,
                "public static void renderDarkBallIris()",
                "private static DarkBallPreviewResult"
                        + " renderDarkBallComposites(");
        String compactEntry = dense(irisEntry);

        int validateTransform = compactEntry.indexOf(
                "DarkBallRenderContext.isUsableIrisFrame(");
        int captureBuffer = compactEntry.indexOf(
                "IntBuffersavedViewport=BufferUtils.createIntBuffer(4);");
        int captureViewport = compactEntry.indexOf(
                "GL11.glGetIntegerv(GL11.GL_VIEWPORT,savedViewport);");
        int tryBlock = compactEntry.indexOf("try(");
        assertTrue(validateTransform >= 0,
                "Iris geometry must fail closed before mutating GL state when"
                        + " its world transform is stale or malformed");
        assertTrue(captureBuffer > validateTransform);
        assertTrue(captureViewport > captureBuffer);
        assertTrue(tryBlock > captureViewport,
                "the caller viewport must be captured before any Iris effect"
                        + " state is changed");

        int installPair = compactEntry.indexOf(
                "DarkBallRenderContext.install("
                        + "irisSnapshot.modelViewMatrix,"
                        + "irisSnapshot.projectionMatrix)");
        int effectViewport = compactEntry.indexOf(
                "RenderSystem.viewport(0,0,irisSnapshot.renderWidth,"
                        + "irisSnapshot.renderHeight);");
        int submit = compactEntry.indexOf(
                "renderDarkBallComposites(camera,"
                        + "camera.getPartialTickTime())");
        assertTrue(installPair > tryBlock,
                "the exact Iris model-view/projection pair must be installed"
                        + " for the whole serial capture transaction");
        assertTrue(effectViewport > installPair);
        assertTrue(submit > effectViewport,
                "the effect transaction must see the Iris target dimensions");

        int finallyBlock = compactEntry.indexOf("finally{");
        int restoreViewport = compactEntry.indexOf(
                "RenderSystem.viewport(savedViewport.get(0),"
                        + "savedViewport.get(1),savedViewport.get(2),"
                        + "savedViewport.get(3));",
                finallyBlock);
        assertTrue(finallyBlock > submit);
        assertTrue(restoreViewport > finallyBlock,
                "the exact ambient viewport must be restored on every exit");
    }

    @Test
    void irisSnapshotUsesProjectionViewportRatherThanScaledColorTarget()
            throws IOException {
        String handler = dense(javaSource(
                "client/aura/IrisHandlerImpl.java"));

        int currentWidth = handler.indexOf(
                "intrenderWidth=targets.getCurrentWidth();");
        int currentHeight = handler.indexOf(
                "intrenderHeight=targets.getCurrentHeight();");
        int fallbackWidth = handler.indexOf(
                "renderWidth=diffuseTarget.getWidth();");
        int fallbackHeight = handler.indexOf(
                "renderHeight=diffuseTarget.getHeight();");

        assertTrue(currentWidth >= 0 && currentHeight > currentWidth,
                "Iris base render dimensions must define the projection"
                        + " viewport");
        assertTrue(fallbackWidth > currentHeight
                        && fallbackHeight > fallbackWidth,
                "scaled target dimensions may only be a defensive fallback");
        assertFalse(handler.contains("glBindTexture("),
                "snapshot collection must not mutate texture bindings");
        assertFalse(handler.contains("GL_TEXTURE_WIDTH"));
        assertFalse(handler.contains("GL_TEXTURE_HEIGHT"));
        assertFalse(handler.contains("textureSize("));
    }

    @Test
    void surfelsAndSiphonConsumeTheInstalledMatchedMatrixPair()
            throws IOException {
        String surfels = javaSource(
                "client/ball/DarkBallSurfaceSplatRenderer.java");
        String siphon = javaSource(
                "client/ball/DarkBallSiphonSurfaceMeshRenderer.java");

        assertEquals(4, countOccurrences(
                surfels, "DarkBallRenderContext.modelView(camera)"));
        assertEquals(4, countOccurrences(
                surfels, "DarkBallRenderContext.projection()"));
        assertEquals(1, countOccurrences(
                siphon, "DarkBallRenderContext.modelView(camera)"));
        assertEquals(1, countOccurrences(
                siphon, "DarkBallRenderContext.projection()"));
        assertFalse(surfels.contains(
                        "new Quaternionf(camera.rotation()).conjugate()"),
                "surfel accumulation, diagnostics, and front depth must not"
                        + " silently replace Iris' model-view matrix");
        assertFalse(siphon.contains(
                        "new Quaternionf(camera.rotation()).conjugate()"),
                "the siphon must remain in the same Iris space as the body");
    }

    @Test
    void irisHookSubmitsAfterBindingItsDefaultWorldTarget()
            throws IOException {
        String mixinSource = javaSource(
                "mixin/MixinIrisRenderingPipeline.java");
        String finalWorldColorHook = sourceBetween(
                mixinSource,
                "@Inject(method = \"finalizeLevelRendering\","
                        + " at = @At(value = \"HEAD\"))",
                "@Inject(method = \"finalizeLevelRendering\","
                        + " at = @At(value = \"TAIL\"))");

        int bindDefault = finalWorldColorHook.indexOf(".bindDefault()");
        int submitDarkBall = finalWorldColorHook.indexOf(
                "BallEmitters.renderDarkBallIris()");
        assertTrue(bindDefault >= 0);
        assertTrue(submitDarkBall > bindDefault,
                "Dark Ball must capture and restore Iris' bound world target"
                        + " rather than a stale Minecraft framebuffer");
        assertEquals(1, countOccurrences(
                finalWorldColorHook, "BallEmitters.renderDarkBallIris()"));
        String translucentTail = sourceBetween(
                mixinSource,
                "@Inject(method = \"beginTranslucents\","
                        + " at = @At(value = \"TAIL\"))",
                "@Inject(method = \"finalizeLevelRendering\","
                        + " at = @At(value = \"HEAD\"))");
        assertFalse(translucentTail.contains(
                        "BallEmitters.renderDarkBallIris()"),
                "the earlier translucent hook must not double-submit the"
                        + " effect or let later world translucents cover it");
    }

    private static String javaSource(String relativePath) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/")
                        .resolve(relativePath)
                        .normalize(),
                StandardCharsets.UTF_8);
    }

    private static String sourceBetween(
            String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        assertTrue(start >= 0, "missing source token " + startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue(end > start, "missing source token " + endToken);
        return source.substring(start, end);
    }

    private static String dense(String source) {
        return source.replaceAll("\\s+", "");
    }

    private static int countOccurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}

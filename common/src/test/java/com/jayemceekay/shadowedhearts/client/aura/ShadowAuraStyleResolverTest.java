package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowAuraStyleResolverTest {

    @Test
    void canonicalNamesAndAspectsRemainStable() {
        assertEquals("signature", ShadowAuraStyle.SIGNATURE.serializedName());
        assertEquals("colosseum", ShadowAuraStyle.COLOSSEUM.serializedName());
        assertEquals("xd_faithful", ShadowAuraStyle.XD_FAITHFUL.serializedName());

        assertEquals(SHAspects.AURA_STYLE_SIGNATURE,
                ShadowAuraStyle.SIGNATURE.aspectId());
        assertEquals(SHAspects.AURA_STYLE_COLOSSEUM,
                ShadowAuraStyle.COLOSSEUM.aspectId());
        assertEquals(SHAspects.AURA_STYLE_XD,
                ShadowAuraStyle.XD_FAITHFUL.aspectId());
    }

    @Test
    void configParsingIsCaseInsensitiveAndFailsSafeToSignature() {
        assertEquals(ShadowAuraStyle.SIGNATURE,
                ShadowAuraStyle.parse("signature"));
        assertEquals(ShadowAuraStyle.COLOSSEUM,
                ShadowAuraStyle.parse("  CoLoSsEuM  "));
        assertEquals(ShadowAuraStyle.XD_FAITHFUL,
                ShadowAuraStyle.parse("XD-FAITHFUL"));
        assertEquals(ShadowAuraStyle.XD_FAITHFUL,
                ShadowAuraStyle.fromSerializedName("xd faithful"));

        assertEquals(ShadowAuraStyle.SIGNATURE,
                ShadowAuraStyle.parse(null));
        assertEquals(ShadowAuraStyle.SIGNATURE,
                ShadowAuraStyle.parse(""));
        assertEquals(ShadowAuraStyle.SIGNATURE,
                ShadowAuraStyle.parse("unrecognized"));
    }

    @Test
    void configPredicateAcceptsOnlyKnownSerializedStyles() {
        assertTrue(ShadowAuraStyle.isValidSerializedName("signature"));
        assertTrue(ShadowAuraStyle.isValidSerializedName("COLOSSEUM"));
        assertTrue(ShadowAuraStyle.isValidSerializedName("xd-faithful"));

        assertFalse(ShadowAuraStyle.isValidSerializedName(null));
        assertFalse(ShadowAuraStyle.isValidSerializedName(42));
        assertFalse(ShadowAuraStyle.isValidSerializedName(""));
        assertFalse(ShadowAuraStyle.isValidSerializedName("xd"));
    }

    @Test
    void explicitPokemonAspectOverridesTheClientDefaultInEitherDirection() {
        assertEquals(ShadowAuraStyle.XD_FAITHFUL,
                ShadowAuraStyleResolver.resolve(
                        List.of(SHAspects.AURA_STYLE_XD),
                        ShadowAuraStyle.SIGNATURE));
        assertEquals(ShadowAuraStyle.SIGNATURE,
                ShadowAuraStyleResolver.resolve(
                        List.of(SHAspects.AURA_STYLE_SIGNATURE),
                        ShadowAuraStyle.XD_FAITHFUL));
        assertEquals(ShadowAuraStyle.COLOSSEUM,
                ShadowAuraStyleResolver.resolve(
                        List.of("  SHADOWEDHEARTS:AURA_STYLE_COLOSSEUM  "),
                        ShadowAuraStyle.SIGNATURE));
    }

    @Test
    void conflictingAspectsUseFixedPriorityIndependentOfCollectionOrder() {
        List<String> canonical = List.of(
                SHAspects.AURA_STYLE_SIGNATURE,
                SHAspects.AURA_STYLE_COLOSSEUM,
                SHAspects.AURA_STYLE_XD);
        assertEquals(ShadowAuraStyle.XD_FAITHFUL,
                ShadowAuraStyleResolver.resolve(
                        canonical, ShadowAuraStyle.SIGNATURE));

        List<String> reversed = new ArrayList<>(canonical);
        java.util.Collections.reverse(reversed);
        assertEquals(ShadowAuraStyle.XD_FAITHFUL,
                ShadowAuraStyleResolver.resolve(
                        reversed, ShadowAuraStyle.SIGNATURE));

        assertEquals(ShadowAuraStyle.COLOSSEUM,
                ShadowAuraStyleResolver.resolve(
                        List.of(SHAspects.AURA_STYLE_SIGNATURE,
                                SHAspects.AURA_STYLE_COLOSSEUM),
                        ShadowAuraStyle.XD_FAITHFUL));
        assertThrows(UnsupportedOperationException.class,
                () -> ShadowAuraStyleResolver.ASPECT_PRIORITY.add(
                        ShadowAuraStyle.SIGNATURE));
    }

    @Test
    void separatePokemonCanResolveToDifferentStylesWithoutGlobalState() {
        ShadowAuraStyle first = ShadowAuraStyleResolver.resolve(
                List.of(SHAspects.AURA_STYLE_SIGNATURE),
                ShadowAuraStyle.COLOSSEUM);
        ShadowAuraStyle second = ShadowAuraStyleResolver.resolve(
                List.of(SHAspects.AURA_STYLE_XD),
                ShadowAuraStyle.COLOSSEUM);
        ShadowAuraStyle third = ShadowAuraStyleResolver.resolve(
                List.of(),
                ShadowAuraStyle.COLOSSEUM);

        assertEquals(ShadowAuraStyle.SIGNATURE, first);
        assertEquals(ShadowAuraStyle.XD_FAITHFUL, second);
        assertEquals(ShadowAuraStyle.COLOSSEUM, third);
    }

    @Test
    void absentOrUnknownAspectsUseTheSuppliedDefault() {
        assertEquals(ShadowAuraStyle.COLOSSEUM,
                ShadowAuraStyleResolver.resolve(
                        (Iterable<String>) null,
                        ShadowAuraStyle.COLOSSEUM));
        assertEquals(ShadowAuraStyle.XD_FAITHFUL,
                ShadowAuraStyleResolver.resolve(
                        List.of("othermod:unrelated"),
                        ShadowAuraStyle.XD_FAITHFUL));
        assertEquals(ShadowAuraStyle.SIGNATURE,
                ShadowAuraStyleResolver.resolve(
                        List.of(), null));
    }
}

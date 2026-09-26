package com.otectus.immersiveapotheosis.category;

import net.minecraftforge.fml.ModList;

/**
 * ApothicCurios runs the hook affixes (on hit, when hurt, on block break, on arrows) of every equipped curio,
 * whatever its category. Worn jewelry runs its own through Immersive Jewelry, which honours Jewelry's rules, so a
 * mixin ({@code mixin.ApothicCuriosMixin}) leaves jewelry out of ApothicCurios's list.
 *
 * <p>The mixin config plugin checks the transformed method and records the result as a system property, which every
 * class loader sees. If the exclusion did not apply (a changed ApothicCurios), worn jewelry stops running hooks
 * itself, so nothing fires twice. Attributes are never doubled either way: ApothicCurios adds attribute affixes only
 * for its own {@code curios:*} categories.
 */
public final class ApothicCuriosExclusion {
    public static final String PROPERTY = "immersive_apotheosis.apothiccurios_exclusion";

    public enum Status { ABSENT, APPLIED, FAILED }

    public static Status status() {
        if (!ModList.get().isLoaded("apothiccurios")) return Status.ABSENT;
        return "applied".equals(System.getProperty(PROPERTY)) ? Status.APPLIED : Status.FAILED;
    }

    /** Whether worn jewelry should run its affixes' hooks itself. */
    public static boolean jewelryRunsHooks() {
        return status() != Status.FAILED;
    }

    private ApothicCuriosExclusion() {}
}

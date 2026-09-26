package com.otectus.immersiveapotheosis.mixin;

import com.otectus.immersiveapotheosis.category.ApothicCuriosExclusion;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Records whether the optional ApothicCurios exclusion actually reached its target method. */
public class MixinPlugin implements IMixinConfigPlugin {
    private static final String HANDLER = "excludeJewelry";

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!mixinClassName.endsWith("ApothicCuriosMixin")) return;
        boolean applied = false;
        for (MethodNode method : targetClass.methods) {
            if (!method.name.equals("getEquippedCurios")) continue;
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.contains(HANDLER)) applied = true;
            }
        }
        System.setProperty(ApothicCuriosExclusion.PROPERTY, applied ? "applied" : "failed");
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}

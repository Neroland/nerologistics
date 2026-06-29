package za.co.neroland.nerologistics.filter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A per-face fluid filter for a fluid duct: an ordered list of {@link Rule}s plus a
 * whitelist/blacklist flag, matching by exact fluid id or fluid tag. Persisted in the
 * duct block-entity NBT. (Fluids carry no data components, so there is no NBT-rule
 * analogue to {@link ItemFilter.Kind#COMPONENT}.)
 */
public final class FluidFilter {

    public enum Kind {
        FLUID,
        TAG,
        MOD
    }

    public record Rule(Kind kind, Identifier fluidId, TagKey<Fluid> tag) {

        public static Rule fluid(Identifier fluidId) {
            return new Rule(Kind.FLUID, fluidId, null);
        }

        public static Rule tag(TagKey<Fluid> tag) {
            return new Rule(Kind.TAG, null, tag);
        }

        public static Rule mod(String namespace) {
            return new Rule(Kind.MOD, Identifier.fromNamespaceAndPath(namespace, "any"), null);
        }

        boolean matches(Fluid fluid) {
            Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
            return switch (this.kind) {
                case FLUID -> id.equals(this.fluidId);
                case TAG -> this.tag != null && fluid.builtInRegistryHolder().is(this.tag);
                case MOD -> this.fluidId != null && id.getNamespace().equals(this.fluidId.getNamespace());
            };
        }
    }

    private boolean whitelist = true;
    private final List<Rule> rules = new ArrayList<>();

    public boolean isWhitelist() {
        return this.whitelist;
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelist = whitelist;
    }

    public List<Rule> rules() {
        return this.rules;
    }

    public boolean isEmpty() {
        return this.rules.isEmpty();
    }

    public boolean test(Fluid fluid) {
        if (this.rules.isEmpty()) {
            return !this.whitelist;
        }
        boolean anyMatch = false;
        for (Rule rule : this.rules) {
            if (rule.matches(fluid)) {
                anyMatch = true;
                break;
            }
        }
        return this.whitelist == anyMatch;
    }

    public void save(ValueOutput output, String prefix) {
        output.putInt(prefix + "Whitelist", this.whitelist ? 1 : 0);
        output.putInt(prefix + "RuleCount", this.rules.size());
        for (int i = 0; i < this.rules.size(); i++) {
            Rule rule = this.rules.get(i);
            String p = prefix + "Rule" + i + "_";
            output.putString(p + "Kind", rule.kind().name());
            if (rule.fluidId() != null) {
                output.putString(p + "Fluid", rule.fluidId().toString());
            }
            if (rule.tag() != null) {
                output.putString(p + "Tag", rule.tag().location().toString());
            }
        }
    }

    public void load(ValueInput input, String prefix) {
        this.whitelist = input.getIntOr(prefix + "Whitelist", 1) != 0;
        this.rules.clear();
        int count = input.getIntOr(prefix + "RuleCount", 0);
        for (int i = 0; i < count; i++) {
            String p = prefix + "Rule" + i + "_";
            Kind kind = parseKind(input.getStringOr(p + "Kind", Kind.FLUID.name()));
            String fluidStr = input.getStringOr(p + "Fluid", "");
            Identifier fluidId = fluidStr.isEmpty() ? null : Identifier.parse(fluidStr);
            String tagStr = input.getStringOr(p + "Tag", "");
            TagKey<Fluid> tag = tagStr.isEmpty()
                    ? null
                    : TagKey.create(Registries.FLUID, Identifier.parse(tagStr));
            this.rules.add(new Rule(kind, fluidId, tag));
        }
    }

    private static Kind parseKind(String s) {
        try {
            return Kind.valueOf(s);
        } catch (IllegalArgumentException ignored) {
            return Kind.FLUID;
        }
    }
}

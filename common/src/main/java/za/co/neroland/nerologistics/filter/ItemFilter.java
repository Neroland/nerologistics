package za.co.neroland.nerologistics.filter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A per-face item filter for an item duct: an ordered list of {@link Rule}s plus a
 * whitelist/blacklist flag. A whitelist passes a stack only if some rule matches; a
 * blacklist passes a stack only if no rule matches. An empty whitelist passes nothing;
 * an empty blacklist passes everything.
 *
 * <p>Supports the full Stage-2 rule set: exact item, {@code c:}/namespace tag, mod id,
 * and data-component (NBT) matching. Persisted in the duct block-entity NBT.
 */
public final class ItemFilter {

    /** What a single rule matches against. */
    public enum Kind {
        /** Exact item id, components ignored. */
        ITEM,
        /** Item tag membership (e.g. {@code c:ingots}). */
        TAG,
        /** Mod namespace of the item id. */
        MOD,
        /** Exact item id AND identical data components (NBT). */
        COMPONENT
    }

    /** One match rule. Only the field relevant to {@link #kind} is meaningful. */
    public record Rule(Kind kind, Identifier itemId, TagKey<Item> tag, ItemStack template) {

        public static Rule item(Identifier itemId) {
            return new Rule(Kind.ITEM, itemId, null, ItemStack.EMPTY);
        }

        public static Rule tag(TagKey<Item> tag) {
            return new Rule(Kind.TAG, null, tag, ItemStack.EMPTY);
        }

        public static Rule mod(String namespace) {
            return new Rule(Kind.MOD, Identifier.fromNamespaceAndPath(namespace, "any"), null, ItemStack.EMPTY);
        }

        public static Rule component(ItemStack template) {
            return new Rule(Kind.COMPONENT, BuiltInRegistries.ITEM.getKey(template.getItem()), null, template.copy());
        }

        boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            return switch (this.kind) {
                case ITEM -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(this.itemId);
                case TAG -> this.tag != null && stack.is(this.tag);
                case MOD -> this.itemId != null
                        && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(this.itemId.getNamespace());
                case COMPONENT -> ItemStack.isSameItemSameComponents(this.template, stack);
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

    /** Whether {@code stack} may pass this face. */
    public boolean test(ItemStack stack) {
        if (this.rules.isEmpty()) {
            return !this.whitelist;
        }
        boolean anyMatch = false;
        for (Rule rule : this.rules) {
            if (rule.matches(stack)) {
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
            if (rule.itemId() != null) {
                output.putString(p + "Item", rule.itemId().toString());
            }
            if (rule.tag() != null) {
                output.putString(p + "Tag", rule.tag().location().toString());
            }
            output.store(p + "Template", ItemStack.OPTIONAL_CODEC, rule.template());
        }
    }

    public void load(ValueInput input, String prefix) {
        this.whitelist = input.getIntOr(prefix + "Whitelist", 1) != 0;
        this.rules.clear();
        int count = input.getIntOr(prefix + "RuleCount", 0);
        for (int i = 0; i < count; i++) {
            String p = prefix + "Rule" + i + "_";
            Kind kind = parseKind(input.getStringOr(p + "Kind", Kind.ITEM.name()));
            Identifier itemId = parseId(input.getStringOr(p + "Item", ""));
            String tagStr = input.getStringOr(p + "Tag", "");
            TagKey<Item> tag = tagStr.isEmpty()
                    ? null
                    : TagKey.create(Registries.ITEM, Identifier.parse(tagStr));
            ItemStack template = input.read(p + "Template", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
            this.rules.add(new Rule(kind, itemId, tag, template));
        }
    }

    private static Kind parseKind(String s) {
        try {
            return Kind.valueOf(s);
        } catch (IllegalArgumentException ignored) {
            return Kind.ITEM;
        }
    }

    private static Identifier parseId(String s) {
        return s.isEmpty() ? null : Identifier.parse(s);
    }
}
